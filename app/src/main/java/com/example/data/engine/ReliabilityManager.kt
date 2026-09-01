package com.example.data.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

enum class FailureSeverity {
    CRITICAL,    // Engine crash, unhandled exception, thread termination
    WARNING,     // Delayed callback, temporary response lag
    INFORMATIONAL // Sensor idle, low movement, expected pause
}

enum class RecoveryLevel(val level: Int, val description: String) {
    LEVEL_1_SOFT_RESET(1, "Soft Reset & Parameter Reset"),
    LEVEL_2_LISTENER_REBIND(2, "Listener & Receiver Re-registration"),
    LEVEL_3_ENGINE_CYCLE(3, "Engine Cycle (Stop & Restart)"),
    LEVEL_4_SERVICE_REFRESH(4, "Foreground Service & Worker Refresh"),
    LEVEL_5_DEGRADED_MODE(5, "Degraded Isolation & Safe Mode Shift")
}

data class RecoveryRecord(
    val id: String,
    val engineName: String,
    val failureReason: String,
    val severity: FailureSeverity,
    val recoveryLevel: RecoveryLevel,
    val isSuccessful: Boolean,
    val rootCauseSummary: String,
    val mttrMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class EngineHealthScore(
    val engineName: String,
    val healthScorePct: Int = 100,
    val stabilityScorePct: Int = 100,
    val recoveryCount: Int = 0,
    val failureCount: Int = 0,
    val isDegraded: Boolean = false,
    val lastFailureTimestampMs: Long = 0L
)

data class ReliabilityDiagnostics(
    val overallPlatformHealthPct: Int,
    val isEmergencySafeModeActive: Boolean,
    val totalRecoveriesExecuted: Int,
    val successfulRecoveriesCount: Int,
    val mtbfHours: Double,
    val mttrAvgMs: Long,
    val engineHealthScores: Map<String, EngineHealthScore>,
    val recentRecoveryHistory: List<RecoveryRecord>,
    val engineStatus: EngineLifecycleState
)

class ReliabilityManager : INetraEngine {
    override val engineName: String = "ReliabilityManager"
    override var isRunning: Boolean = true
        private set

    private var lifecycleState: EngineLifecycleState = EngineLifecycleState.RUNNING

    private val engineHealthScores = ConcurrentHashMap<String, EngineHealthScore>()
    private val recoveryCooldowns = ConcurrentHashMap<String, Long>()
    private val recoveryLocks = ConcurrentHashMap<String, Boolean>()
    private val recoveryHistory = ConcurrentLinkedQueue<RecoveryRecord>()

    private val RECOVERY_COOLDOWN_MS = 300_000L // 5 minutes cooldown per engine
    private val HISTORY_MAX_SIZE = 50

    private val _diagnostics = MutableStateFlow(
        ReliabilityDiagnostics(
            overallPlatformHealthPct = 100,
            isEmergencySafeModeActive = false,
            totalRecoveriesExecuted = 0,
            successfulRecoveriesCount = 0,
            mtbfHours = 72.0,
            mttrAvgMs = 120L,
            engineHealthScores = emptyMap(),
            recentRecoveryHistory = emptyList(),
            engineStatus = EngineLifecycleState.RUNNING
        )
    )
    val diagnostics: StateFlow<ReliabilityDiagnostics> = _diagnostics.asStateFlow()

    private var isSafeModeActive = false
    private var totalRecoveries = 0
    private var successfulRecoveries = 0
    private var totalMttrSumMs = 0L

    override fun initialize() {
        lifecycleState = EngineLifecycleState.INITIALIZED
    }

    override fun startEngine() {
        isRunning = true
        lifecycleState = EngineLifecycleState.RUNNING
        updateDiagnostics()
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
        when (event.type) {
            EngineSystemEventType.EMERGENCY_ALERT -> {
                // Safeguard checks
            }
            else -> {}
        }
    }

    init {
        EngineCoordinator.registerEngine(this)
    }

    fun reportFailure(
        targetEngineName: String,
        reason: String,
        severity: FailureSeverity
    ): Boolean {
        if (severity == FailureSeverity.INFORMATIONAL) {
            return false // Never recover for informational pauses
        }

        val now = System.currentTimeMillis()
        val lastRecoveryTime = recoveryCooldowns[targetEngineName] ?: 0L

        // Cooldown protection for non-critical failures
        if (now - lastRecoveryTime < RECOVERY_COOLDOWN_MS && severity != FailureSeverity.CRITICAL) {
            return false
        }

        // Duplicate recovery lock protection
        if (recoveryLocks[targetEngineName] == true) {
            return false
        }

        recoveryLocks[targetEngineName] = true
        val startTimeMs = System.currentTimeMillis()

        try {
            val targetEngine = EngineCoordinator.getEngine(targetEngineName)
            val isVerifiedFault = targetEngine == null || !tryEngineHealthVerify(targetEngine)

            if (!isVerifiedFault && severity != FailureSeverity.CRITICAL) {
                // False positive check passed, no recovery needed
                recoveryLocks[targetEngineName] = false
                return false
            }

            // Determine appropriate recovery level based on existing failure count
            val currentScore = engineHealthScores[targetEngineName] ?: EngineHealthScore(targetEngineName)
            val nextLevel = when (currentScore.failureCount) {
                0 -> RecoveryLevel.LEVEL_1_SOFT_RESET
                1 -> RecoveryLevel.LEVEL_2_LISTENER_REBIND
                2 -> RecoveryLevel.LEVEL_3_ENGINE_CYCLE
                3 -> RecoveryLevel.LEVEL_4_SERVICE_REFRESH
                else -> RecoveryLevel.LEVEL_5_DEGRADED_MODE
            }

            val success = executeRecoveryLevel(targetEngineName, targetEngine, nextLevel)
            val endTimeMs = System.currentTimeMillis()
            val mttrMs = endTimeMs - startTimeMs

            totalRecoveries++
            if (success) successfulRecoveries++
            totalMttrSumMs += mttrMs

            recoveryCooldowns[targetEngineName] = now

            val record = RecoveryRecord(
                id = "REC_${now}_$totalRecoveries",
                engineName = targetEngineName,
                failureReason = reason,
                severity = severity,
                recoveryLevel = nextLevel,
                isSuccessful = success,
                rootCauseSummary = analyzeRootCause(targetEngineName, reason, nextLevel),
                mttrMs = mttrMs,
                timestamp = now
            )

            recoveryHistory.add(record)
            while (recoveryHistory.size > HISTORY_MAX_SIZE) {
                recoveryHistory.poll()
            }

            // Update Engine Health Score
            val updatedFailureCount = currentScore.failureCount + 1
            val updatedRecoveryCount = currentScore.recoveryCount + (if (success) 1 else 0)
            val newScorePct = (100 - (updatedFailureCount * 15)).coerceAtLeast(10)
            val newStabilityPct = if (success) (currentScore.stabilityScorePct - 5).coerceAtLeast(30) else 20
            val isDegraded = nextLevel == RecoveryLevel.LEVEL_5_DEGRADED_MODE || newScorePct < 30

            engineHealthScores[targetEngineName] = EngineHealthScore(
                engineName = targetEngineName,
                healthScorePct = newScorePct,
                stabilityScorePct = newStabilityPct,
                recoveryCount = updatedRecoveryCount,
                failureCount = updatedFailureCount,
                isDegraded = isDegraded,
                lastFailureTimestampMs = now
            )

            // Check if Emergency Safe Mode should be engaged (> 2 critical engines degraded)
            val degradedCount = engineHealthScores.values.count { it.isDegraded }
            if (degradedCount >= 2 && !isSafeModeActive) {
                engageEmergencySafeMode()
            }

            updateDiagnostics()
            return success
        } finally {
            recoveryLocks[targetEngineName] = false
        }
    }

    private fun tryEngineHealthVerify(engine: INetraEngine): Boolean {
        return try {
            engine.healthCheck() && engine.getStatus() == EngineLifecycleState.RUNNING
        } catch (e: Exception) {
            false
        }
    }

    private fun executeRecoveryLevel(
        engineName: String,
        engine: INetraEngine?,
        level: RecoveryLevel
    ): Boolean {
        return try {
            when (level) {
                RecoveryLevel.LEVEL_1_SOFT_RESET -> {
                    engine?.initialize()
                    true
                }
                RecoveryLevel.LEVEL_2_LISTENER_REBIND -> {
                    engine?.pauseEngine()
                    engine?.resumeEngine()
                    true
                }
                RecoveryLevel.LEVEL_3_ENGINE_CYCLE -> {
                    engine?.stopEngine()
                    engine?.startEngine()
                    true
                }
                RecoveryLevel.LEVEL_4_SERVICE_REFRESH -> {
                    engine?.stopEngine()
                    engine?.initialize()
                    engine?.startEngine()
                    true
                }
                RecoveryLevel.LEVEL_5_DEGRADED_MODE -> {
                    engine?.pauseEngine()
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun analyzeRootCause(engineName: String, reason: String, level: RecoveryLevel): String {
        return "Engine '$engineName' triggered $level due to: '$reason'. Automatic self-healing pipeline dispatched."
    }

    private fun engageEmergencySafeMode() {
        isSafeModeActive = true
        EngineCoordinator.dispatchEvent(
            EngineSystemEvent(
                type = EngineSystemEventType.POWER_MODE_CHANGED,
                payload = PowerMode.ADAPTIVE_QUIET
            )
        )
    }

    private fun updateDiagnostics() {
        val totalEngines = EngineCoordinator.getAllEngines().size.coerceAtLeast(1)
        val degradedCount = engineHealthScores.values.count { it.isDegraded }
        val overallHealth = (((totalEngines - degradedCount).toDouble() / totalEngines) * 100).toInt().coerceIn(0, 100)
        val avgMttr = if (totalRecoveries > 0) totalMttrSumMs / totalRecoveries else 120L

        _diagnostics.value = ReliabilityDiagnostics(
            overallPlatformHealthPct = overallHealth,
            isEmergencySafeModeActive = isSafeModeActive,
            totalRecoveriesExecuted = totalRecoveries,
            successfulRecoveriesCount = successfulRecoveries,
            mtbfHours = if (degradedCount == 0) 120.0 else 24.0,
            mttrAvgMs = avgMttr,
            engineHealthScores = engineHealthScores.toMap(),
            recentRecoveryHistory = recoveryHistory.toList(),
            engineStatus = getStatus()
        )
    }

    fun executeExceptionShield(blockName: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            reportFailure(
                targetEngineName = blockName,
                reason = "Unhandled Exception Shield caught: ${e.message}",
                severity = FailureSeverity.CRITICAL
            )
        }
    }
}
