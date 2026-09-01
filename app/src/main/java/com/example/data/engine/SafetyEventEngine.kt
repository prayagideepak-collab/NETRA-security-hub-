package com.example.data.engine

import android.content.Context
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventDao
import com.example.data.db.SafetyEventEntity
import com.example.data.model.MotionConfidence
import com.example.data.model.SafetyRiskLevel
import com.example.data.model.SensorFusionState
import com.example.util.LoggingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

enum class SafetySeverity {
    NORMAL,
    ATTENTION,
    WARNING,
    CRITICAL
}

data class SafetyEngineEvent(
    val eventId: String,
    val eventType: String,
    val severity: SafetySeverity,
    val confidence: MotionConfidence,
    val sourceSensors: List<String>,
    val triggerValue: String,
    val thresholdValue: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deduplicationKey: String,
    val description: String
)

class SafetyEventEngine(
    private val context: Context,
    private val safetyEventDao: SafetyEventDao = NetraDatabase.getInstance(context).safetyEventDao()
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeSafetyEvents = MutableStateFlow<Map<String, SafetyEngineEvent>>(emptyMap())
    val activeSafetyEvents: StateFlow<Map<String, SafetyEngineEvent>> = _activeSafetyEvents.asStateFlow()

    private val lastAlertTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val ALERT_REPEAT_INTERVAL_MS = 60000L // 60 seconds repeat for ongoing unresolved hazard

    fun evaluateSafetyConditions(
        fusionState: SensorFusionState,
        thermalThresholdC: Int = 45
    ) {
        val now = System.currentTimeMillis()
        val currentEvents = mutableMapOf<String, SafetyEngineEvent>()

        // 1. Thermal Safety Analysis
        val temp = fusionState.batteryTempC
        if (temp > 0f) {
            when {
                temp >= thermalThresholdC + 3f -> { // CRITICAL (e.g. >= 48°C)
                    val key = "thermal_critical"
                    currentEvents[key] = SafetyEngineEvent(
                        eventId = UUID.randomUUID().toString(),
                        eventType = "HIGH_HEAT_CRITICAL",
                        severity = SafetySeverity.CRITICAL,
                        confidence = MotionConfidence.HIGH,
                        sourceSensors = listOf("Battery Thermal Subsystem"),
                        triggerValue = "%.1f°C".format(temp),
                        thresholdValue = "${thermalThresholdC}°C",
                        timestamp = now,
                        deduplicationKey = key,
                        description = "Device thermal state critical (%.1f°C). Immediate cooldown required.".format(temp)
                    )
                }
                temp >= thermalThresholdC.toFloat() -> { // WARNING (e.g. >= 45°C)
                    val key = "thermal_warning"
                    currentEvents[key] = SafetyEngineEvent(
                        eventId = UUID.randomUUID().toString(),
                        eventType = "HIGH_HEAT_WARNING",
                        severity = SafetySeverity.WARNING,
                        confidence = MotionConfidence.HIGH,
                        sourceSensors = listOf("Battery Thermal Subsystem"),
                        triggerValue = "%.1f°C".format(temp),
                        thresholdValue = "${thermalThresholdC}°C",
                        timestamp = now,
                        deduplicationKey = key,
                        description = "Elevated thermal level (%.1f°C) exceeding safe threshold.".format(temp)
                    )
                }
                temp >= 40f -> { // ATTENTION (40°C - 44°C)
                    val key = "thermal_attention"
                    currentEvents[key] = SafetyEngineEvent(
                        eventId = UUID.randomUUID().toString(),
                        eventType = "HEAT_ATTENTION",
                        severity = SafetySeverity.ATTENTION,
                        confidence = MotionConfidence.MEDIUM,
                        sourceSensors = listOf("Battery Thermal Subsystem"),
                        triggerValue = "%.1f°C".format(temp),
                        thresholdValue = "40.0°C",
                        timestamp = now,
                        deduplicationKey = key,
                        description = "Battery temperature is moderately warm (%.1f°C).".format(temp)
                    )
                }
            }
        }

        // 2. Magnetic Anomaly Evaluation (Suppressed if device is charging)
        if (fusionState.isMagneticHazardConfirmed && !fusionState.isCharging) {
            val key = "magnetic_anomaly_hazard"
            currentEvents[key] = SafetyEngineEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = "MAGNETIC_ANOMALY",
                severity = SafetySeverity.WARNING,
                confidence = MotionConfidence.HIGH,
                sourceSensors = listOf("Magnetometer (TYPE_MAGNETIC_FIELD)"),
                triggerValue = "%.1f µT".format(fusionState.magneticMagnitudeuT),
                thresholdValue = "100.0 µT",
                timestamp = now,
                deduplicationKey = key,
                description = "Sustained strong magnetic field (%.1f µT) detected nearby.".format(fusionState.magneticMagnitudeuT)
            )
        }

        // 3. Impact / Fall Event Evaluation
        if (fusionState.isImpactConfirmed) {
            val key = "impact_event_${now / 5000L}" // 5-second window deduplication
            currentEvents[key] = SafetyEngineEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = "PHYSICAL_IMPACT",
                severity = SafetySeverity.WARNING,
                confidence = MotionConfidence.HIGH,
                sourceSensors = listOf("Accelerometer", "Gyroscope"),
                triggerValue = "%.1f G".format(fusionState.impactGForce),
                thresholdValue = "2.2 G",
                timestamp = now,
                deduplicationKey = key,
                description = "Sudden high G-force physical impact (%.1f G) registered.".format(fusionState.impactGForce)
            )
        }

        // 4. Charging Risk Anomaly
        if (fusionState.isChargingRiskConfirmed) {
            val key = "charging_thermal_risk"
            currentEvents[key] = SafetyEngineEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = "CHARGING_ANOMALY",
                severity = SafetySeverity.CRITICAL,
                confidence = MotionConfidence.HIGH,
                sourceSensors = listOf("Battery Power HAL"),
                triggerValue = "${fusionState.chargingVoltageMv} mV / %.1f°C".format(fusionState.batteryTempC),
                thresholdValue = "4400 mV / ${thermalThresholdC}°C",
                timestamp = now,
                deduplicationKey = key,
                description = "Charging thermal overload risk. Disconnect charger safely."
            )
        }

        _activeSafetyEvents.value = currentEvents

        // Persist newly triggered events to DB
        currentEvents.values.forEach { event ->
            val lastTime = lastAlertTimestamps[event.deduplicationKey] ?: 0L
            if (now - lastTime >= ALERT_REPEAT_INTERVAL_MS) {
                lastAlertTimestamps[event.deduplicationKey] = now
                persistSafetyEvent(event)
            }
        }
    }

    fun isNightModeActive(now: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // Night mode quiet period: 10:00 PM (22) to 6:00 AM (6)
        return hour >= 22 || hour < 6
    }

    private fun persistSafetyEvent(event: SafetyEngineEvent) {
        scope.launch {
            try {
                safetyEventDao.insertEvent(
                    SafetyEventEntity(
                        timestamp = event.timestamp,
                        riskLevel = when (event.severity) {
                            SafetySeverity.CRITICAL -> "EMERGENCY"
                            SafetySeverity.WARNING -> "WARNING"
                            SafetySeverity.ATTENTION -> "ATTENTION"
                            SafetySeverity.NORMAL -> "SAFE"
                        },
                        riskScore = when (event.severity) {
                            SafetySeverity.CRITICAL -> 90
                            SafetySeverity.WARNING -> 65
                            SafetySeverity.ATTENTION -> 40
                            SafetySeverity.NORMAL -> 10
                        },
                        eventType = event.eventType,
                        title = event.eventType.replace("_", " "),
                        description = event.description,
                        primarySensorValuesJson = "{\"sources\":\"${event.sourceSensors.joinToString(",")}\"}",
                        aiRecommendation = "Hardware safety event detected. Maintain standard device precautions.",
                        isVerifiedHardwareEvent = true,
                        moduleName = "SafetyEventEngine",
                        severity = event.severity.name,
                        aiConfidence = if (event.confidence == MotionConfidence.HIGH) 0.95f else 0.70f
                    )
                )
            } catch (_: Exception) {}
        }
    }
}
