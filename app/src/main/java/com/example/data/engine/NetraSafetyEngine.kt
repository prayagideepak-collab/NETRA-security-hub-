package com.example.data.engine

import android.content.Context
import com.example.data.model.CanonicalSafetyEvent
import com.example.data.model.ConfidenceLevel
import com.example.data.model.DataFreshness
import com.example.data.model.DeviceHealthState
import com.example.data.model.EventTransitionAction
import com.example.data.model.RawSensorReading
import com.example.data.model.SafetyDomain
import com.example.data.model.SafetyEngineState
import com.example.data.model.SafetyEventLifecycleState
import com.example.data.model.SafetyRiskState
import com.example.data.model.SensorCategory
import com.example.data.model.SensorFusionState
import com.example.data.model.SubsystemHealth
import com.example.util.LoggingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class NetraSafetyEngine(
    private val context: Context,
    val alertManager: NetraAlertManager = NetraAlertManager(context)
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _safetyEngineState = MutableStateFlow(SafetyEngineState())
    val safetyEngineState: StateFlow<SafetyEngineState> = _safetyEngineState.asStateFlow()

    // Active in-memory tracking of ongoing events by domain
    private val activeEventMap = ConcurrentHashMap<SafetyDomain, CanonicalSafetyEvent>()
    private val eventSequenceCounters = ConcurrentHashMap<String, AtomicInteger>()

    // Thermal Engine State Machine variables
    private var lastObservedTempC: Float = 0f
    private var tempDroppingConsecutiveCount = 0
    private var tempElevatedConsecutiveCount = 0

    // Magnetic Engine State Machine variables
    private var magneticBaselineuT = 45.0f // Dynamic ambient baseline
    private var magneticAnomalyConsecutiveCount = 0
    private var magneticRecoveryConsecutiveCount = 0

    // Freshness Threshold Constants
    companion object {
        const val FRESHNESS_FRESH_MS = 5_000L
        const val FRESHNESS_DELAYED_MS = 15_000L
        const val FRESHNESS_STALE_MS = 60_000L

        const val THERMAL_SAFE_RECOVERY_C = 39.5f
        const val MAGNETIC_NORMAL_DIFF_UT = 25.0f
        const val MAGNETIC_HAZARD_DIFF_UT = 70.0f
        const val MAGNETIC_HAZARD_MIN_MAG_UT = 110.0f
    }

    /**
     * Central Evaluation entry point called synchronously with real telemetry.
     */
    fun evaluateSafetyConditions(
        fusionState: SensorFusionState,
        liveReadings: Map<String, RawSensorReading>,
        thermalThresholdC: Int = 45
    ) {
        val now = System.currentTimeMillis()

        // 1. DATA VALIDATION & FRESHNESS CHECK
        val subsystemHealths = evaluateSubsystemHealth(fusionState, liveReadings, now)
        val overallDeviceHealth = determineOverallDeviceHealth(subsystemHealths)

        // 2. DOMAIN EVENT DETECTION & LIFECYCLE PROCESSING
        val evaluatedEvents = mutableListOf<CanonicalSafetyEvent>()

        // A. Thermal Safety
        val thermalEvent = evaluateThermalSafety(fusionState, subsystemHealths["Thermal"], thermalThresholdC, now)
        if (thermalEvent != null) evaluatedEvents.add(thermalEvent)

        // B. Magnetic Safety
        val magneticEvent = evaluateMagneticSafety(fusionState, subsystemHealths["Magnetometer"], now)
        if (magneticEvent != null) evaluatedEvents.add(magneticEvent)

        // C. Physical Impact Safety
        val impactEvent = evaluateImpactSafety(fusionState, subsystemHealths["Motion"], now)
        if (impactEvent != null) evaluatedEvents.add(impactEvent)

        // D. Charging Risk Safety
        val chargingEvent = evaluateChargingSafety(fusionState, subsystemHealths["Power"], thermalThresholdC, now)
        if (chargingEvent != null) evaluatedEvents.add(chargingEvent)

        // 3. OVERALL SAFETY STATE DETERMINATION
        val activeList = evaluatedEvents.filter { it.lifecycleState != SafetyEventLifecycleState.RESOLVED }
        val highestRisk = activeList.maxOfOrNull { it.severity } ?: SafetyRiskState.SAFE

        _safetyEngineState.value = SafetyEngineState(
            safetyRiskState = highestRisk,
            deviceHealthState = overallDeviceHealth,
            activeEvents = activeList,
            subsystemHealths = subsystemHealths,
            lastEvaluatedTime = now
        )
    }

    // ------------------------------------------------------------------------
    // DATA VALIDATION & FRESHNESS EVALUATION
    // ------------------------------------------------------------------------
    private fun evaluateSubsystemHealth(
        fusionState: SensorFusionState,
        liveReadings: Map<String, RawSensorReading>,
        now: Long
    ): Map<String, SubsystemHealth> {
        val healths = mutableMapOf<String, SubsystemHealth>()

        // 1. Thermal Subsystem
        val thermalReading = liveReadings.values.firstOrNull { it.category == SensorCategory.THERMAL || it.sensorId == "battery_telemetry" }
        val thermalTimestamp = thermalReading?.lastUpdateTimestamp ?: fusionState.lastUpdateTimestamp
        val thermalFreshness = calculateFreshness(thermalTimestamp, now)
        val thermalHealth = when (thermalFreshness) {
            DataFreshness.FRESH -> DeviceHealthState.HEALTHY
            DataFreshness.DELAYED -> DeviceHealthState.HEALTHY
            DataFreshness.STALE -> DeviceHealthState.DEGRADED
            DataFreshness.UNAVAILABLE -> DeviceHealthState.UNAVAILABLE
        }
        healths["Thermal"] = SubsystemHealth(
            name = "Thermal & Battery Temperature",
            healthState = thermalHealth,
            freshness = thermalFreshness,
            lastTimestamp = thermalTimestamp,
            statusMessage = if (thermalFreshness == DataFreshness.UNAVAILABLE) "Thermal sensor telemetry unavailable" else "Telemetry fresh (%.1f°C)".format(fusionState.batteryTempC)
        )

        // 2. Magnetometer Subsystem
        val magReading = liveReadings.values.firstOrNull { it.sensorId.contains("magnetic", ignoreCase = true) }
        val magTimestamp = magReading?.lastUpdateTimestamp ?: 0L
        val magFreshness = calculateFreshness(magTimestamp, now)
        val magHealth = when (magFreshness) {
            DataFreshness.FRESH -> DeviceHealthState.HEALTHY
            DataFreshness.DELAYED -> DeviceHealthState.HEALTHY
            DataFreshness.STALE -> DeviceHealthState.DEGRADED
            DataFreshness.UNAVAILABLE -> DeviceHealthState.UNAVAILABLE
        }
        healths["Magnetometer"] = SubsystemHealth(
            name = "Magnetic Field Sensor",
            healthState = magHealth,
            freshness = magFreshness,
            lastTimestamp = magTimestamp,
            statusMessage = if (magFreshness == DataFreshness.UNAVAILABLE) "Magnetometer reading unavailable" else "Calibrated field: %.1f µT".format(fusionState.magneticMagnitudeuT)
        )

        // 3. Motion & Accelerometer Subsystem
        val motionReading = liveReadings.values.firstOrNull { it.category == SensorCategory.MOTION }
        val motionTimestamp = motionReading?.lastUpdateTimestamp ?: 0L
        val motionFreshness = calculateFreshness(motionTimestamp, now)
        val motionHealth = when (motionFreshness) {
            DataFreshness.FRESH -> DeviceHealthState.HEALTHY
            DataFreshness.DELAYED -> DeviceHealthState.HEALTHY
            DataFreshness.STALE -> DeviceHealthState.DEGRADED
            DataFreshness.UNAVAILABLE -> DeviceHealthState.UNAVAILABLE
        }
        healths["Motion"] = SubsystemHealth(
            name = "Motion & Inertial Sensors",
            healthState = motionHealth,
            freshness = motionFreshness,
            lastTimestamp = motionTimestamp,
            statusMessage = if (motionFreshness == DataFreshness.UNAVAILABLE) "Inertial sensors unavailable" else "Kinematic stream active"
        )

        // 4. Power & Battery Subsystem
        val powerReading = liveReadings.values.firstOrNull { it.category == SensorCategory.POWER }
        val powerTimestamp = powerReading?.lastUpdateTimestamp ?: fusionState.lastUpdateTimestamp
        val powerFreshness = calculateFreshness(powerTimestamp, now)
        healths["Power"] = SubsystemHealth(
            name = "Power HAL & Charging Circuit",
            healthState = if (powerFreshness == DataFreshness.UNAVAILABLE) DeviceHealthState.UNAVAILABLE else DeviceHealthState.HEALTHY,
            freshness = powerFreshness,
            lastTimestamp = powerTimestamp,
            statusMessage = if (powerFreshness == DataFreshness.UNAVAILABLE) "Power HAL telemetry unavailable" else "${fusionState.batteryLevelPercent}% (${if (fusionState.isCharging) "Charging ${fusionState.chargingVoltageMv}mV" else "Discharging"})"
        )

        return healths
    }

    private fun calculateFreshness(timestamp: Long, now: Long): DataFreshness {
        if (timestamp <= 0L) return DataFreshness.UNAVAILABLE
        val delta = now - timestamp
        return when {
            delta < FRESHNESS_FRESH_MS -> DataFreshness.FRESH
            delta < FRESHNESS_DELAYED_MS -> DataFreshness.DELAYED
            delta < FRESHNESS_STALE_MS -> DataFreshness.STALE
            else -> DataFreshness.UNAVAILABLE
        }
    }

    private fun determineOverallDeviceHealth(subsystems: Map<String, SubsystemHealth>): DeviceHealthState {
        val states = subsystems.values.map { it.healthState }
        return when {
            states.all { it == DeviceHealthState.HEALTHY } -> DeviceHealthState.HEALTHY
            states.any { it == DeviceHealthState.UNAVAILABLE } -> DeviceHealthState.DEGRADED
            states.any { it == DeviceHealthState.DEGRADED } -> DeviceHealthState.DEGRADED
            states.any { it == DeviceHealthState.RECOVERING } -> DeviceHealthState.RECOVERING
            else -> DeviceHealthState.HEALTHY
        }
    }

    // ------------------------------------------------------------------------
    // 1. THERMAL SAFETY STATE MACHINE
    // ------------------------------------------------------------------------
    private fun evaluateThermalSafety(
        fusionState: SensorFusionState,
        subsystemHealth: SubsystemHealth?,
        thermalThresholdC: Int,
        now: Long
    ): CanonicalSafetyEvent? {
        val temp = fusionState.batteryTempC
        val freshness = subsystemHealth?.freshness ?: DataFreshness.UNAVAILABLE

        // Data Health Rule: If data is stale or unavailable, report data health issue without fabricating overheat alert
        if (freshness == DataFreshness.UNAVAILABLE || temp <= 0f) {
            val existing = activeEventMap[SafetyDomain.THERMAL]
            if (existing != null && existing.lifecycleState != SafetyEventLifecycleState.RESOLVED) {
                // If previous active hazard exists but data is lost, hold state
                return existing
            }
            return null
        }

        val existingEvent = activeEventMap[SafetyDomain.THERMAL]
        val isWarning = temp >= thermalThresholdC.toFloat()
        val isCritical = temp >= (thermalThresholdC + 3f)
        val isElevated = temp >= 40.0f

        // Trend analysis
        if (lastObservedTempC > 0f) {
            if (temp < lastObservedTempC - 0.2f) {
                tempDroppingConsecutiveCount++
                tempElevatedConsecutiveCount = 0
            } else if (temp > lastObservedTempC + 0.2f) {
                tempElevatedConsecutiveCount++
                tempDroppingConsecutiveCount = 0
            }
        }
        lastObservedTempC = temp

        // Case 1: Active Hazard Condition (Critical / Warning)
        if (isWarning || isCritical) {
            val targetSeverity = if (isCritical) SafetyRiskState.CRITICAL else SafetyRiskState.WARNING
            val currentValStr = "%.1f°C".format(temp)

            if (existingEvent == null || existingEvent.lifecycleState == SafetyEventLifecycleState.RESOLVED) {
                // START NEW EVENT
                val eventId = generateEventId("THERMAL", now)
                val newEvent = CanonicalSafetyEvent(
                    eventId = eventId,
                    domain = SafetyDomain.THERMAL,
                    eventType = if (isCritical) "HIGH_HEAT_CRITICAL" else "HIGH_HEAT_WARNING",
                    lifecycleState = SafetyEventLifecycleState.ACTIVE,
                    severity = targetSeverity,
                    confidence = ConfidenceLevel.HIGH,
                    title = if (isCritical) "Critical Device Overheating" else "High Device Temperature",
                    description = if (isCritical) "Device temperature critical (%.1f°C). Immediate cooldown required.".format(temp) else "Device temperature elevated (%.1f°C) exceeding safe threshold (${thermalThresholdC}°C).".format(temp),
                    startTime = now,
                    lastUpdateTime = now,
                    peakValue = currentValStr,
                    currentValue = currentValStr,
                    thresholdValue = "${thermalThresholdC}°C",
                    sourceSensors = listOf("Battery Thermal Subsystem", "Thermal HAL"),
                    evidence = "Measured temperature: %.1f°C against threshold: %d°C".format(temp, thermalThresholdC),
                    transitionAction = EventTransitionAction.EVENT_STARTED
                )
                activeEventMap[SafetyDomain.THERMAL] = newEvent
                alertManager.dispatchSafetyEvent(newEvent)
                return newEvent
            } else {
                // EXISTING EVENT ACTIVE: Check update or escalate
                val higherPeak = maxTempString(existingEvent.peakValue, currentValStr)
                val isEscalating = (targetSeverity == SafetyRiskState.CRITICAL && existingEvent.severity != SafetyRiskState.CRITICAL)
                
                val updated = existingEvent.copy(
                    lifecycleState = if (tempDroppingConsecutiveCount >= 3) SafetyEventLifecycleState.RECOVERING else SafetyEventLifecycleState.ACTIVE,
                    severity = targetSeverity,
                    peakValue = higherPeak,
                    currentValue = currentValStr,
                    lastUpdateTime = now,
                    description = if (targetSeverity == SafetyRiskState.CRITICAL) "Device temperature critical (%.1f°C).".format(temp) else "Elevated temperature (%.1f°C).".format(temp),
                    transitionAction = if (isEscalating) EventTransitionAction.EVENT_ESCALATED else EventTransitionAction.EVENT_UPDATED
                )
                activeEventMap[SafetyDomain.THERMAL] = updated
                alertManager.dispatchSafetyEvent(updated)
                return updated
            }
        }

        // Case 2: Recovery & Resolution
        if (existingEvent != null && existingEvent.lifecycleState != SafetyEventLifecycleState.RESOLVED) {
            val currentValStr = "%.1f°C".format(temp)
            if (temp <= THERMAL_SAFE_RECOVERY_C) {
                // Fully Resolved
                val resolved = existingEvent.copy(
                    lifecycleState = SafetyEventLifecycleState.RESOLVED,
                    severity = SafetyRiskState.SAFE,
                    currentValue = currentValStr,
                    lastUpdateTime = now,
                    endTime = now,
                    resolution = "Temperature stabilized at %.1f°C below safe threshold (%.1f°C)".format(temp, THERMAL_SAFE_RECOVERY_C),
                    transitionAction = EventTransitionAction.EVENT_RESOLVED
                )
                activeEventMap[SafetyDomain.THERMAL] = resolved
                alertManager.dispatchSafetyEvent(resolved)
                tempDroppingConsecutiveCount = 0
                return resolved
            } else {
                // In recovery state
                val recovering = existingEvent.copy(
                    lifecycleState = SafetyEventLifecycleState.RECOVERING,
                    severity = SafetyRiskState.ATTENTION,
                    currentValue = currentValStr,
                    lastUpdateTime = now,
                    description = "Thermal state recovering (%.1f°C). Awaiting safe threshold.".format(temp),
                    transitionAction = EventTransitionAction.EVENT_RECOVERING
                )
                activeEventMap[SafetyDomain.THERMAL] = recovering
                alertManager.dispatchSafetyEvent(recovering)
                return recovering
            }
        }

        return null
    }

    // ------------------------------------------------------------------------
    // 2. MAGNETIC SAFETY STATE MACHINE & ADAPTIVE BASELINE
    // ------------------------------------------------------------------------
    private fun evaluateMagneticSafety(
        fusionState: SensorFusionState,
        subsystemHealth: SubsystemHealth?,
        now: Long
    ): CanonicalSafetyEvent? {
        val magnitude = fusionState.magneticMagnitudeuT
        val freshness = subsystemHealth?.freshness ?: DataFreshness.UNAVAILABLE

        if (freshness == DataFreshness.UNAVAILABLE || magnitude <= 0f) {
            return activeEventMap[SafetyDomain.MAGNETIC]
        }

        // Adaptive baseline estimation when calm and not charging
        if (!fusionState.isCharging && magnitude in 25.0f..65.0f) {
            magneticBaselineuT = (magneticBaselineuT * 0.95f) + (magnitude * 0.05f)
        }

        val deviation = Math.abs(magnitude - magneticBaselineuT)
        val existingEvent = activeEventMap[SafetyDomain.MAGNETIC]

        // Charging Context Suppression: Wireless or cable charging produces strong localized magnetic fields
        if (fusionState.isCharging) {
            magneticAnomalyConsecutiveCount = 0
            if (existingEvent != null && existingEvent.lifecycleState != SafetyEventLifecycleState.RESOLVED) {
                val resolved = existingEvent.copy(
                    lifecycleState = SafetyEventLifecycleState.RESOLVED,
                    severity = SafetyRiskState.SAFE,
                    lastUpdateTime = now,
                    endTime = now,
                    resolution = "Magnetic anomaly filtered due to active charging induction context.",
                    transitionAction = EventTransitionAction.EVENT_RESOLVED
                )
                activeEventMap[SafetyDomain.MAGNETIC] = resolved
                alertManager.dispatchSafetyEvent(resolved)
                return resolved
            }
            return null
        }

        val isHazardCandidate = (deviation >= MAGNETIC_HAZARD_DIFF_UT && magnitude >= MAGNETIC_HAZARD_MIN_MAG_UT)

        if (isHazardCandidate) {
            magneticAnomalyConsecutiveCount++
            magneticRecoveryConsecutiveCount = 0

            // Persistence Requirement: >= 3 consecutive evaluations (~3 seconds)
            if (magneticAnomalyConsecutiveCount >= 3) {
                val currentValStr = "%.1f µT (Δ %.1f µT)".format(magnitude, deviation)

                if (existingEvent == null || existingEvent.lifecycleState == SafetyEventLifecycleState.RESOLVED) {
                    val eventId = generateEventId("MAGNETIC", now)
                    val newEvent = CanonicalSafetyEvent(
                        eventId = eventId,
                        domain = SafetyDomain.MAGNETIC,
                        eventType = "MAGNETIC_ANOMALY",
                        lifecycleState = SafetyEventLifecycleState.ACTIVE,
                        severity = SafetyRiskState.WARNING,
                        confidence = ConfidenceLevel.HIGH,
                        title = "Persistent Magnetic Anomaly",
                        description = "Sustained strong magnetic field (%.1f µT) detected. Potential high-power magnetic source nearby.".format(magnitude),
                        startTime = now,
                        lastUpdateTime = now,
                        peakValue = currentValStr,
                        currentValue = currentValStr,
                        thresholdValue = "Baseline: %.1f µT / Max Δ: 70.0 µT".format(magneticBaselineuT),
                        sourceSensors = listOf("Magnetometer (TYPE_MAGNETIC_FIELD)"),
                        evidence = "Magnitude: %.1f µT, Baseline: %.1f µT, Persistent for %d ticks".format(magnitude, magneticBaselineuT, magneticAnomalyConsecutiveCount),
                        transitionAction = EventTransitionAction.EVENT_STARTED
                    )
                    activeEventMap[SafetyDomain.MAGNETIC] = newEvent
                    alertManager.dispatchSafetyEvent(newEvent)
                    return newEvent
                } else {
                    val updated = existingEvent.copy(
                        lifecycleState = SafetyEventLifecycleState.ACTIVE,
                        currentValue = currentValStr,
                        lastUpdateTime = now,
                        transitionAction = EventTransitionAction.EVENT_UPDATED
                    )
                    activeEventMap[SafetyDomain.MAGNETIC] = updated
                    alertManager.dispatchSafetyEvent(updated)
                    return updated
                }
            }
        } else {
            magneticAnomalyConsecutiveCount = 0
            if (deviation <= MAGNETIC_NORMAL_DIFF_UT) {
                magneticRecoveryConsecutiveCount++
            }

            if (existingEvent != null && existingEvent.lifecycleState != SafetyEventLifecycleState.RESOLVED) {
                if (magneticRecoveryConsecutiveCount >= 3) {
                    val resolved = existingEvent.copy(
                        lifecycleState = SafetyEventLifecycleState.RESOLVED,
                        severity = SafetyRiskState.SAFE,
                        currentValue = "%.1f µT".format(magnitude),
                        lastUpdateTime = now,
                        endTime = now,
                        resolution = "Magnetic field returned to baseline nominal range (%.1f µT)".format(magnitude),
                        transitionAction = EventTransitionAction.EVENT_RESOLVED
                    )
                    activeEventMap[SafetyDomain.MAGNETIC] = resolved
                    alertManager.dispatchSafetyEvent(resolved)
                    return resolved
                } else {
                    val recovering = existingEvent.copy(
                        lifecycleState = SafetyEventLifecycleState.RECOVERING,
                        severity = SafetyRiskState.ATTENTION,
                        currentValue = "%.1f µT".format(magnitude),
                        lastUpdateTime = now,
                        transitionAction = EventTransitionAction.EVENT_RECOVERING
                    )
                    activeEventMap[SafetyDomain.MAGNETIC] = recovering
                    alertManager.dispatchSafetyEvent(recovering)
                    return recovering
                }
            }
        }

        return null
    }

    // ------------------------------------------------------------------------
    // 3. PHYSICAL IMPACT SAFETY
    // ------------------------------------------------------------------------
    private fun evaluateImpactSafety(
        fusionState: SensorFusionState,
        subsystemHealth: SubsystemHealth?,
        now: Long
    ): CanonicalSafetyEvent? {
        val existing = activeEventMap[SafetyDomain.IMPACT]

        if (fusionState.isImpactConfirmed) {
            val gForceStr = "%.1f G".format(fusionState.impactGForce)
            if (existing == null || existing.lifecycleState == SafetyEventLifecycleState.RESOLVED || (now - existing.lastUpdateTime > 15_000L)) {
                val eventId = generateEventId("IMPACT", now)
                val newEvent = CanonicalSafetyEvent(
                    eventId = eventId,
                    domain = SafetyDomain.IMPACT,
                    eventType = "PHYSICAL_IMPACT",
                    lifecycleState = SafetyEventLifecycleState.ACTIVE,
                    severity = SafetyRiskState.WARNING,
                    confidence = ConfidenceLevel.HIGH,
                    title = "Physical Impact Registered",
                    description = "Sudden high G-force physical impact (%.1f G) registered on device.".format(fusionState.impactGForce),
                    startTime = now,
                    lastUpdateTime = now,
                    peakValue = gForceStr,
                    currentValue = gForceStr,
                    thresholdValue = "2.2 G",
                    sourceSensors = listOf("Accelerometer", "Gyroscope"),
                    evidence = "Measured G-force: %.1f G".format(fusionState.impactGForce),
                    transitionAction = EventTransitionAction.EVENT_STARTED
                )
                activeEventMap[SafetyDomain.IMPACT] = newEvent
                alertManager.dispatchSafetyEvent(newEvent)
                return newEvent
            }
        } else if (existing != null && existing.lifecycleState == SafetyEventLifecycleState.ACTIVE && (now - existing.lastUpdateTime > 15_000L)) {
            val resolved = existing.copy(
                lifecycleState = SafetyEventLifecycleState.RESOLVED,
                severity = SafetyRiskState.SAFE,
                lastUpdateTime = now,
                endTime = now,
                resolution = "Impact monitoring window concluded. Kinematics stabilized.",
                transitionAction = EventTransitionAction.EVENT_RESOLVED
            )
            activeEventMap[SafetyDomain.IMPACT] = resolved
            alertManager.dispatchSafetyEvent(resolved)
            return resolved
        }

        return activeEventMap[SafetyDomain.IMPACT]
    }

    // ------------------------------------------------------------------------
    // 4. CHARGING THERMAL RISK SAFETY
    // ------------------------------------------------------------------------
    private fun evaluateChargingSafety(
        fusionState: SensorFusionState,
        subsystemHealth: SubsystemHealth?,
        thermalThresholdC: Int,
        now: Long
    ): CanonicalSafetyEvent? {
        val isRisk = fusionState.isChargingRiskConfirmed
        val existing = activeEventMap[SafetyDomain.CHARGING]

        if (isRisk) {
            val valueStr = "${fusionState.chargingVoltageMv} mV / %.1f°C".format(fusionState.batteryTempC)
            if (existing == null || existing.lifecycleState == SafetyEventLifecycleState.RESOLVED) {
                val eventId = generateEventId("CHARGING", now)
                val newEvent = CanonicalSafetyEvent(
                    eventId = eventId,
                    domain = SafetyDomain.CHARGING,
                    eventType = "CHARGING_ANOMALY",
                    lifecycleState = SafetyEventLifecycleState.ACTIVE,
                    severity = SafetyRiskState.CRITICAL,
                    confidence = ConfidenceLevel.HIGH,
                    title = "Charging Thermal Overload Risk",
                    description = "Dangerous charging voltage and thermal profile detected. Disconnect charger safely.",
                    startTime = now,
                    lastUpdateTime = now,
                    peakValue = valueStr,
                    currentValue = valueStr,
                    thresholdValue = "4400 mV / ${thermalThresholdC}°C",
                    sourceSensors = listOf("Battery Power HAL", "Thermal Subsystem"),
                    evidence = "Voltage: %d mV, Temp: %.1f°C".format(fusionState.chargingVoltageMv, fusionState.batteryTempC),
                    transitionAction = EventTransitionAction.EVENT_STARTED
                )
                activeEventMap[SafetyDomain.CHARGING] = newEvent
                alertManager.dispatchSafetyEvent(newEvent)
                return newEvent
            }
        } else if (existing != null && existing.lifecycleState != SafetyEventLifecycleState.RESOLVED) {
            val resolved = existing.copy(
                lifecycleState = SafetyEventLifecycleState.RESOLVED,
                severity = SafetyRiskState.SAFE,
                lastUpdateTime = now,
                endTime = now,
                resolution = "Charging parameters returned to nominal specifications.",
                transitionAction = EventTransitionAction.EVENT_RESOLVED
            )
            activeEventMap[SafetyDomain.CHARGING] = resolved
            alertManager.dispatchSafetyEvent(resolved)
            return resolved
        }

        return activeEventMap[SafetyDomain.CHARGING]
    }

    // ------------------------------------------------------------------------
    // HELPER METHODS
    // ------------------------------------------------------------------------
    private fun generateEventId(prefix: String, timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dateStr = dateFormat.format(Date(timestamp))
        val counter = eventSequenceCounters.getOrPut("${prefix}_$dateStr") { AtomicInteger(0) }
        val seq = counter.incrementAndGet()
        return "%s-%s-%03d".format(prefix, dateStr, seq)
    }

    private fun maxTempString(peakStr: String?, currentStr: String): String {
        if (peakStr == null) return currentStr
        val peak = peakStr.replace("°C", "").trim().toFloatOrNull() ?: 0f
        val curr = currentStr.replace("°C", "").trim().toFloatOrNull() ?: 0f
        return if (curr >= peak) currentStr else peakStr
    }
}
