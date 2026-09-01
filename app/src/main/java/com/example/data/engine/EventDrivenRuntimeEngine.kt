package com.example.data.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

enum class EventPriority {
    CRITICAL,   // Thermal warning, Emergency alert
    HIGH,       // Driving started/stopped, User activity changed
    NORMAL,     // Screen state, Power mode, Digital wellness
    BACKGROUND  // Report maintenance, Log cleanup
}

data class ClassifiedEvent(
    val event: EngineSystemEvent,
    val priority: EventPriority,
    val receivedTimestampMs: Long = System.currentTimeMillis(),
    var dispatchedTimestampMs: Long = 0L,
    var processingLatencyMs: Long = 0L
)

data class EdreRuntimeDiagnostics(
    val totalEventsProcessed: Long,
    val deduplicatedEventsCount: Long,
    val eventReplayBufferSize: Int,
    val averageProcessingLatencyMs: Double,
    val priorityBreakdown: Map<String, Long>,
    val activeWakeCountPerHour: Int,
    val engineStatus: EngineLifecycleState
)

class EventDrivenRuntimeEngine : INetraEngine {
    override val engineName: String = "EventDrivenRuntimeEngine"
    override var isRunning: Boolean = true
        private set

    private var lifecycleState: EngineLifecycleState = EngineLifecycleState.RUNNING

    private val _diagnostics = MutableStateFlow(
        EdreRuntimeDiagnostics(
            totalEventsProcessed = 0L,
            deduplicatedEventsCount = 0L,
            eventReplayBufferSize = 0,
            averageProcessingLatencyMs = 0.0,
            priorityBreakdown = emptyMap(),
            activeWakeCountPerHour = 12,
            engineStatus = EngineLifecycleState.RUNNING
        )
    )
    val diagnostics: StateFlow<EdreRuntimeDiagnostics> = _diagnostics.asStateFlow()

    // Event Replay Buffer (last 200 events)
    private val eventReplayBuffer = ConcurrentLinkedQueue<ClassifiedEvent>()
    private val REPLAY_BUFFER_MAX_SIZE = 200

    // Deduplication tracker (event type string + payload hash -> lastProcessedMs)
    private val eventDeduplicationMap = ConcurrentHashMap<String, Long>()
    private val DEBOUNCE_WINDOW_MS = 250L // 250ms debounce window for identical events

    private var totalProcessedCount = 0L
    private var deduplicatedCount = 0L
    private var totalLatencySumMs = 0L
    private val priorityCounts = ConcurrentHashMap<EventPriority, Long>()

    override fun initialize() {
        lifecycleState = EngineLifecycleState.INITIALIZED
    }

    override fun startEngine() {
        isRunning = true
        lifecycleState = EngineLifecycleState.RUNNING
    }

    override fun pauseEngine() {
        lifecycleState = EngineLifecycleState.PAUSED
    }

    override fun resumeEngine() {
        lifecycleState = EngineLifecycleState.RUNNING
    }

    override fun stopEngine() {
        isRunning = false
        lifecycleState = EngineLifecycleState.STOPPED
    }

    override fun getStatus(): EngineLifecycleState = lifecycleState

    override fun healthCheck(): Boolean = isRunning

    override fun onSystemEvent(event: EngineSystemEvent) {
        processAndDispatch(event)
    }

    init {
        EngineCoordinator.registerEngine(this)
    }

    fun processAndDispatch(event: EngineSystemEvent): Boolean {
        val now = System.currentTimeMillis()
        val priority = classifyEventPriority(event.type)

        // Deduplication & Debounce logic
        val dedupKey = "${event.type}_${event.payload?.hashCode() ?: 0}"
        val lastTimestamp = eventDeduplicationMap[dedupKey] ?: 0L

        if (now - lastTimestamp < DEBOUNCE_WINDOW_MS && priority != EventPriority.CRITICAL) {
            deduplicatedCount++
            updateDiagnostics()
            return false // Event deduplicated
        }

        eventDeduplicationMap[dedupKey] = now

        val classified = ClassifiedEvent(
            event = event,
            priority = priority,
            receivedTimestampMs = now
        )

        // Add to Event Replay Buffer
        eventReplayBuffer.add(classified)
        while (eventReplayBuffer.size > REPLAY_BUFFER_MAX_SIZE) {
            eventReplayBuffer.poll()
        }

        // Latency Measurement
        classified.dispatchedTimestampMs = System.currentTimeMillis()
        classified.processingLatencyMs = classified.dispatchedTimestampMs - classified.receivedTimestampMs

        totalProcessedCount++
        totalLatencySumMs += classified.processingLatencyMs
        priorityCounts[priority] = (priorityCounts[priority] ?: 0L) + 1

        updateDiagnostics()
        return true
    }

    private fun classifyEventPriority(type: EngineSystemEventType): EventPriority {
        return when (type) {
            EngineSystemEventType.EMERGENCY_ALERT,
            EngineSystemEventType.BATTERY_CRITICAL,
            EngineSystemEventType.THERMAL_WARNING -> EventPriority.CRITICAL

            EngineSystemEventType.USER_ACTIVITY_CHANGED -> EventPriority.HIGH

            EngineSystemEventType.SCREEN_STATE_CHANGED,
            EngineSystemEventType.POWER_MODE_CHANGED,
            EngineSystemEventType.DIGITAL_WELLNESS_EVENT -> EventPriority.NORMAL
            else -> EventPriority.NORMAL
        }
    }

    private fun updateDiagnostics() {
        val avgLatency = if (totalProcessedCount > 0) totalLatencySumMs.toDouble() / totalProcessedCount else 0.0
        val pBreakdown = priorityCounts.mapKeys { it.key.name }

        _diagnostics.value = EdreRuntimeDiagnostics(
            totalEventsProcessed = totalProcessedCount,
            deduplicatedEventsCount = deduplicatedCount,
            eventReplayBufferSize = eventReplayBuffer.size,
            averageProcessingLatencyMs = avgLatency,
            priorityBreakdown = pBreakdown,
            activeWakeCountPerHour = calculateEstimatedWakeCount(),
            engineStatus = getStatus()
        )
    }

    private fun calculateEstimatedWakeCount(): Int {
        val criticalAndHigh = (priorityCounts[EventPriority.CRITICAL] ?: 0L) + (priorityCounts[EventPriority.HIGH] ?: 0L)
        return (10 + (criticalAndHigh % 20)).toInt()
    }

    fun getReplayBuffer(): List<ClassifiedEvent> = eventReplayBuffer.toList()
}
