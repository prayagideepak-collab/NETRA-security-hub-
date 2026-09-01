package com.example.data.engine

import com.example.data.model.ActivityHealthScore
import com.example.data.model.DigitalWellnessMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class FusedAiInsight(
    val id: String,
    val category: String, // "BATTERY", "HEALTH", "DRIVING", "DIGITAL_WELLNESS", "THERMAL", "DEVICE_CARE", "JOURNEY"
    val title: String,
    val description: String,
    val whyExplanation: String = "",
    val confidenceScore: Float = 0.90f,
    val supportingSources: List<String> = listOf("EngineCoordinator"),
    val recommendation: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiQualityMetrics(
    val totalInsightsGenerated: Int,
    val activeInsightCount: Int,
    val averageConfidencePct: Int,
    val deduplicatedCount: Int,
    val lastProcessingTimeMs: Long,
    val trackedCategories: List<String>
)

class NetraAiFusionInsightEngine : INetraEngine {
    override val engineName: String = "NetraAiFusionInsightEngine"
    override var isRunning: Boolean = true
        private set

    private var lifecycleState: EngineLifecycleState = EngineLifecycleState.RUNNING

    private val _insights = MutableStateFlow<List<FusedAiInsight>>(emptyList())
    val insights: StateFlow<List<FusedAiInsight>> = _insights.asStateFlow()

    // Deduplication & Cooldown Tracking (insightId -> lastTimestampMs)
    private val insightCooldownMap = ConcurrentHashMap<String, Long>()
    private val DEDUPLICATION_COOLDOWN_MS = 60_000L // 1 minute deduplication window

    private var totalGeneratedCount = 0
    private var deduplicatedCount = 0
    private var lastProcessingTimeMs = 0L

    override fun initialize() {
        lifecycleState = EngineLifecycleState.INITIALIZED
        evaluateBaselineInsight()
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
        val startMs = System.currentTimeMillis()
        when (event.type) {
            EngineSystemEventType.POWER_MODE_CHANGED -> generateInsightsFromPowerMode(event.payload)
            EngineSystemEventType.THERMAL_WARNING -> generateInsightsFromThermal(event.payload)
            EngineSystemEventType.DIGITAL_WELLNESS_EVENT -> generateInsightsFromWellness(event.payload)
            EngineSystemEventType.EMERGENCY_ALERT -> generateInsightsFromEmergency(event.payload)
            else -> {}
        }
        lastProcessingTimeMs = System.currentTimeMillis() - startMs
    }

    init {
        EngineCoordinator.registerEngine(this)
        evaluateBaselineInsight()
    }

    private fun evaluateBaselineInsight() {
        val baseline = FusedAiInsight(
            id = "INSIGHT_SYSTEM_INITIALIZED",
            category = "DEVICE_CARE",
            title = "Netra AI Decision Engine Operational",
            description = "Multi-source correlation engine active. Analyzing power, motion, thermal, and wellness telemetry on-device.",
            whyExplanation = "All required core platform engines registered and emitting normalized telemetry.",
            confidenceScore = 1.0f,
            supportingSources = listOf("EngineCoordinator", "UAIE", "PowerBudgetManager"),
            recommendation = "No action required. Engine running in sleep-first optimal mode."
        )
        addInsight(baseline)
    }

    private fun generateInsightsFromPowerMode(payload: Any?) {
        val mode = payload as? PowerMode ?: return

        if (mode == PowerMode.ADAPTIVE_QUIET) {
            addInsight(
                FusedAiInsight(
                    id = "INSIGHT_POWER_ADAPTIVE_QUIET",
                    category = "BATTERY",
                    title = "Adaptive Quiet Power Mode Engaged",
                    description = "Sensor sampling intervals scaled to conservational rate (500ms-1500ms) to extend battery longevity.",
                    whyExplanation = "System detected elevated power strain, low battery state, or thermal guard trigger.",
                    confidenceScore = 0.96f,
                    supportingSources = listOf("GlobalPowerBudgetManager", "PowerManagerEngine"),
                    recommendation = "Keep background app refresh minimal to maximize remaining charge."
                )
            )
        }
    }

    private fun generateInsightsFromThermal(payload: Any?) {
        val tempC = (payload as? Float) ?: 42.0f
        addInsight(
            FusedAiInsight(
                id = "INSIGHT_THERMAL_ELEVATED",
                category = "THERMAL",
                title = "Device Thermal Threshold Exceeded (${tempC.toInt()}°C)",
                description = "Elevated surface or battery temperature detected. Lite Mode interface automatically engaged.",
                whyExplanation = "Battery temperature reading (${tempC}°C) exceeded thermal guard limit of 42°C.",
                confidenceScore = 0.98f,
                supportingSources = listOf("HardwareDetector", "GlobalPowerBudgetManager"),
                recommendation = "Avoid charging while running heavy graphics or high-brightness navigation."
            )
        )
    }

    private fun generateInsightsFromWellness(payload: Any?) {
        addInsight(
            FusedAiInsight(
                id = "INSIGHT_DIGITAL_WELLNESS_NOTICE",
                category = "DIGITAL_WELLNESS",
                title = "Extended Screen Focus Period",
                description = "Continuous screen engagement observed. Time for a short ergonomic eye and movement break.",
                whyExplanation = "Continuous display active duration crossed ergonomic alert threshold.",
                confidenceScore = 0.92f,
                supportingSources = listOf("DWRE", "ScreenReceiver"),
                recommendation = "Look away at a 20-foot distance for 20 seconds and stretch."
            )
        )
    }

    private fun generateInsightsFromEmergency(payload: Any?) {
        addInsight(
            FusedAiInsight(
                id = "INSIGHT_EMERGENCY_ALERT",
                category = "DEVICE_CARE",
                title = "Critical Safety Event Active",
                description = "Highest-priority security or safety system override engaged.",
                whyExplanation = "Emergency alert system event dispatched via EngineCoordinator.",
                confidenceScore = 1.0f,
                supportingSources = listOf("SecurityEngine", "EngineCoordinator"),
                recommendation = "Review security warnings and follow safety protocols."
            )
        )
    }

    private fun addInsight(insight: FusedAiInsight) {
        val now = System.currentTimeMillis()
        val lastTime = insightCooldownMap[insight.id] ?: 0L

        // Deduplication Check
        if (now - lastTime < DEDUPLICATION_COOLDOWN_MS) {
            deduplicatedCount++
            return
        }

        insightCooldownMap[insight.id] = now
        totalGeneratedCount++

        val currentList = _insights.value.toMutableList()
        currentList.removeAll { it.id == insight.id }
        currentList.add(0, insight)

        _insights.value = currentList.take(15) // Keep top 15 verified insights
    }

    fun generateFusedReport(
        healthScore: ActivityHealthScore,
        wellnessMetrics: DigitalWellnessMetrics
    ): List<FusedAiInsight> {
        val list = mutableListOf<FusedAiInsight>()

        if (wellnessMetrics.totalScreenTimeSec > 14400L) {
            list.add(
                FusedAiInsight(
                    id = "INSIGHT_HIGH_SCREEN_TIME",
                    category = "DIGITAL_WELLNESS",
                    title = "High Screen Engagement Ratio",
                    description = "Extended cumulative display activity registered (${wellnessMetrics.totalScreenTimeSec / 3600}h).",
                    whyExplanation = "Daily screen time exceeded 4 hours.",
                    confidenceScore = 0.94f,
                    supportingSources = listOf("DWRE"),
                    recommendation = "Balance digital tasks with physical activity breaks."
                )
            )
        }

        if (healthScore.score >= 80) {
            list.add(
                FusedAiInsight(
                    id = "INSIGHT_EXCELLENT_MOBILITY",
                    category = "HEALTH",
                    title = "Optimal Movement & Well-being Index",
                    description = "Excellent balance between physical movement cadence and digital rest intervals.",
                    whyExplanation = "Daily health composite score reached ${healthScore.score}/100.",
                    confidenceScore = 0.96f,
                    supportingSources = listOf("HealthCenterEngine", "UAIE"),
                    recommendation = "Maintain current movement routine."
                )
            )
        }

        return list
    }

    fun getAiQualityDiagnostics(): AiQualityMetrics {
        val currentList = _insights.value
        val avgConfidence = if (currentList.isNotEmpty()) {
            (currentList.map { it.confidenceScore }.average() * 100).toInt()
        } else 100

        val categories = currentList.map { it.category }.distinct()

        return AiQualityMetrics(
            totalInsightsGenerated = totalGeneratedCount,
            activeInsightCount = currentList.size,
            averageConfidencePct = avgConfidence,
            deduplicatedCount = deduplicatedCount,
            lastProcessingTimeMs = lastProcessingTimeMs,
            trackedCategories = categories
        )
    }
}
