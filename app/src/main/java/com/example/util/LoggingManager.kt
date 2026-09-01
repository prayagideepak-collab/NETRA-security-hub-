package com.example.util

import android.content.Context
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.model.SafetyRiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

object LoggingManager {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var appContext: Context? = null
    val sessionId: String = UUID.randomUUID().toString().take(8)

    fun init(context: Context) {
        appContext = context.applicationContext
        info("System", "BOOT_SESSION", "Netra system session initialized with ID: $sessionId")
    }

    private var lastEventName: String? = null
    private var lastEventDescription: String? = null
    private var lastEventTimestamp: Long = 0L

    fun logActivity(
        moduleName: String,
        eventName: String,
        severity: String = "INFORMATION", // INFORMATION, WARNING, IMPORTANT, CRITICAL, RECOVERY
        riskLevel: SafetyRiskLevel = SafetyRiskLevel.SAFE,
        riskScore: Int = 0,
        title: String,
        description: String,
        aiConfidence: Float = 0.95f,
        batteryPercent: Int = 0,
        deviceTempC: Float = 0.0f,
        processingDurationMs: Long = 12L,
        recoveryDurationMs: Long = 0L,
        gpsLocation: String = "Unavailable",
        announcementStatus: String = "N/A",
        aiRecommendation: String = "Maintain optimal monitoring."
    ) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (eventName == lastEventName && description == lastEventDescription && (now - lastEventTimestamp < 5000L)) {
                return // Duplicate log detected, skipping
            }
            lastEventName = eventName
            lastEventDescription = description
            lastEventTimestamp = now
        }
        val ctx = appContext
        if (ctx == null) {
            // Fallback if not initialized yet
            return
        }
        scope.launch {
            try {
                val db = NetraDatabase.getInstance(ctx)
                db.safetyEventDao().insertEvent(
                    SafetyEventEntity(
                        riskLevel = riskLevel.name,
                        riskScore = riskScore,
                        eventType = eventName,
                        title = title,
                        description = description,
                        primarySensorValuesJson = "{\"module\":\"$moduleName\",\"event\":\"$eventName\",\"session\":\"$sessionId\",\"timestamp\":\"${System.currentTimeMillis()}\"}",
                        aiRecommendation = aiRecommendation,
                        isVerifiedHardwareEvent = true,
                        moduleName = moduleName,
                        severity = severity,
                        aiConfidence = aiConfidence,
                        batteryPercent = batteryPercent,
                        deviceTempC = deviceTempC,
                        processingDurationMs = processingDurationMs,
                        recoveryDurationMs = recoveryDurationMs,
                        gpsLocation = gpsLocation,
                        announcementStatus = announcementStatus
                    )
                )
                com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Logs")
            } catch (_: Exception) {}
        }
    }

    // Requested static severity shortcuts
    fun info(module: String, event: String, title: String, description: String = title) {
        logActivity(moduleName = module, eventName = event, severity = "INFORMATION", riskLevel = SafetyRiskLevel.SAFE, title = title, description = description)
    }

    fun warning(module: String, event: String, title: String, description: String = title, riskScore: Int = 30) {
        logActivity(moduleName = module, eventName = event, severity = "WARNING", riskLevel = SafetyRiskLevel.WARNING, riskScore = riskScore, title = title, description = description)
    }

    fun important(module: String, event: String, title: String, description: String = title, riskScore: Int = 60) {
        logActivity(moduleName = module, eventName = event, severity = "IMPORTANT", riskLevel = SafetyRiskLevel.ATTENTION, riskScore = riskScore, title = title, description = description)
    }

    fun critical(module: String, event: String, title: String, description: String = title, riskScore: Int = 90) {
        logActivity(moduleName = module, eventName = event, severity = "CRITICAL", riskLevel = SafetyRiskLevel.EMERGENCY, riskScore = riskScore, title = title, description = description)
    }

    fun recovery(module: String, event: String, title: String, description: String = title, recoveryDuration: Long = 150L) {
        logActivity(moduleName = module, eventName = event, severity = "RECOVERY", riskLevel = SafetyRiskLevel.SAFE, recoveryDurationMs = recoveryDuration, title = title, description = description)
    }

    fun logWatchdogRecoveryAttempt(
        moduleName: String,
        state: String, // "TRIGGERED", "RETRY", "SUCCESS", "FAILED"
        attemptCount: Int,
        durationMs: Long = 0L,
        errorMessage: String? = null
    ) {
        val title = "Watchdog $state: $moduleName"
        val desc = when (state.uppercase()) {
            "TRIGGERED" -> "Watchdog triggered recovery sequence for $moduleName due to stale data stream."
            "RETRY" -> "Watchdog recovery retry #$attemptCount for $moduleName after timeout."
            "SUCCESS" -> "Watchdog recovery succeeded for $moduleName. Stream active after ${durationMs}ms."
            "FAILED" -> "Watchdog recovery failed for $moduleName. Error: ${errorMessage ?: "Unknown error"}"
            else -> "Watchdog recovery state $state for $moduleName"
        }
        val severity = when (state.uppercase()) {
            "SUCCESS" -> "RECOVERY"
            "FAILED" -> "CRITICAL"
            "RETRY" -> "WARNING"
            else -> "WARNING"
        }
        val riskLevel = when (state.uppercase()) {
            "FAILED" -> SafetyRiskLevel.EMERGENCY
            "SUCCESS" -> SafetyRiskLevel.SAFE
            else -> SafetyRiskLevel.WARNING
        }
        val riskScore = when (state.uppercase()) {
            "FAILED" -> 80
            "SUCCESS" -> 0
            "RETRY" -> 25
            else -> 20
        }
        logActivity(
            moduleName = "Watchdog Engine",
            eventName = "WATCHDOG_${state.uppercase()}",
            severity = severity,
            riskLevel = riskLevel,
            riskScore = riskScore,
            title = title,
            description = desc,
            recoveryDurationMs = durationMs
        )
    }

    // Category specific static API methods
    fun aiDecision(eventName: String, title: String, description: String, confidence: Float = 0.98f) {
        logActivity(moduleName = "AI Engine", eventName = eventName, severity = "INFORMATION", aiConfidence = confidence, title = title, description = description)
    }

    fun sensor(eventName: String, title: String, description: String, severity: String = "INFORMATION", riskLevel: SafetyRiskLevel = SafetyRiskLevel.SAFE) {
        logActivity(moduleName = "Sensor Engine", eventName = eventName, severity = severity, riskLevel = riskLevel, title = title, description = description)
    }

    fun driving(eventName: String, title: String, description: String, severity: String = "INFORMATION", riskLevel: SafetyRiskLevel = SafetyRiskLevel.SAFE) {
        logActivity(moduleName = "Driving Engine", eventName = eventName, severity = severity, riskLevel = riskLevel, title = title, description = description)
    }

    fun bluetooth(eventName: String, title: String, description: String, severity: String = "INFORMATION") {
        logActivity(moduleName = "Bluetooth", eventName = eventName, severity = severity, title = title, description = description)
    }

    fun notification(eventName: String, title: String, description: String) {
        logActivity(moduleName = "Notification", eventName = eventName, severity = "INFORMATION", title = title, description = description)
    }

    fun temperature(eventName: String, title: String, description: String, severity: String = "INFORMATION", temp: Float = 36.0f) {
        logActivity(moduleName = "Temperature Engine", eventName = eventName, severity = severity, deviceTempC = temp, title = title, description = description)
    }

    fun background(eventName: String, title: String, description: String, severity: String = "INFORMATION") {
        logActivity(moduleName = "Background Protection", eventName = eventName, severity = severity, title = title, description = description)
    }

    fun security(eventName: String, title: String, description: String, severity: String = "WARNING", riskLevel: SafetyRiskLevel = SafetyRiskLevel.WARNING) {
        logActivity(moduleName = "Anti-Theft Engine", eventName = eventName, severity = severity, riskLevel = riskLevel, title = title, description = description)
    }

    fun announcement(eventName: String, title: String, description: String, status: String) {
        logActivity(moduleName = "Announcement", eventName = eventName, severity = "INFORMATION", announcementStatus = status, title = title, description = description)
    }
}

