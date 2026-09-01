package com.aistudio.netrasensorhub.data.risk

import kotlin.math.abs
import kotlin.math.sqrt

data class SensorTelemetryState(
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val gyroX: Float? = null,
    val gyroY: Float? = null,
    val gyroZ: Float? = null,
    val magneticX: Float? = null,
    val magneticY: Float? = null,
    val magneticZ: Float? = null,
    val ambientLightLux: Float? = null,
    val batteryTempC: Float? = null,
    val isCharging: Boolean? = null,
    val isAccelSupported: Boolean = true,
    val isGyroSupported: Boolean = true,
    val isMagneticSupported: Boolean = true,
    val isLightSupported: Boolean = true,
    val isBatteryTempSupported: Boolean = true,
    val lastTimestampMillis: Long = 0L
) {
    val hasValidMotionData: Boolean
        get() = accelX != null && accelY != null && accelZ != null

    val hasValidThermalData: Boolean
        get() = batteryTempC != null
}

enum class RiskLevel {
    NORMAL, LOW, MODERATE, HIGH, CRITICAL
}

data class RiskScenarioResult(
    val scenarioName: String,
    val riskLevel: RiskLevel,
    val description: String,
    val recommendations: List<String>,
    val isDataAvailable: Boolean = true
)

/**
 * Netra Risk Analysis Engine
 * Uses deterministic safety thresholds based on standard device thermal guidelines (Li-ion battery operating limits)
 * and spatial motion physics (G-force / rotational velocity delta).
 *
 * Strict Compliance:
 * - True Data Only: Returns explicitly unavailable state if sensor inputs are absent.
 * - Never manufactures synthetic temperatures or arbitrary fallback motion.
 */
class RiskAnalysisEngine {

    /**
     * Evaluates 'Device Overheating Under Load' scenario by correlating:
     * - Thermal/Battery temperature (°C): Based on standard lithium-ion battery safety envelopes
     *   (Normal < 35°C, Warm 35-38°C, Elevated 38-42°C, Warning 42-45°C, Critical >= 45°C).
     * - Charging status: Charging generates internal Joule heating which accelerates thermal accumulation.
     * - Ambient light conditions: High lux (>10000) acts as a proxy for direct sunlight/radiant heat exposure.
     */
    fun evaluateOverheatingRisk(state: SensorTelemetryState): RiskScenarioResult {
        if (!state.hasValidThermalData || state.batteryTempC == null) {
            return RiskScenarioResult(
                scenarioName = "Device Overheating Under Load",
                riskLevel = RiskLevel.NORMAL,
                description = "Thermal telemetry unavailable on this device hardware.",
                recommendations = listOf("Device thermal sensors not reporting data."),
                isDataAvailable = false
            )
        }

        val temp = state.batteryTempC
        val charging = state.isCharging == true
        val lux = state.ambientLightLux

        var score = 0f

        when {
            temp >= 45f -> score += 65f
            temp >= 42f -> score += 50f
            temp >= 38f -> score += 30f
            temp >= 35f -> score += 15f
            else -> score += 0f
        }

        if (charging) {
            score += 20f
            if (temp > 38f) score += 15f
        }

        if (lux != null) {
            if (lux > 10000f) {
                score += 15f
            } else if (lux > 5000f) {
                score += 8f
            }
        }

        val riskLevel = when {
            score >= 75f -> RiskLevel.CRITICAL
            score >= 55f -> RiskLevel.HIGH
            score >= 35f -> RiskLevel.MODERATE
            score >= 20f -> RiskLevel.LOW
            else -> RiskLevel.NORMAL
        }

        val description = when (riskLevel) {
            RiskLevel.CRITICAL -> "CRITICAL THERMAL HAZARD: Battery temperature reaches %.1f°C%s. Immediate cooling advised.".format(
                temp,
                if (charging) " while charging" else ""
            )
            RiskLevel.HIGH -> "Elevated thermal stress (%.1f°C).%s Risk of thermal throttling and accelerated battery wear.".format(
                temp,
                if (charging) " Device is charging." else ""
            )
            RiskLevel.MODERATE -> "Moderate thermal state (%.1f°C). Monitor resource-intensive background tasks.".format(temp)
            RiskLevel.LOW -> "Normal operating temperature (%.1f°C) with mild thermal load.".format(temp)
            RiskLevel.NORMAL -> "Thermal status optimal (%.1f°C). No overheating risk detected.".format(temp)
        }

        val recs = mutableListOf<String>()
        if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            recs.add("Disconnect charger to reduce internal Joule heating.")
            recs.add("Move device away from direct sunlight or radiant thermal sources.")
            recs.add("Terminate high-performance background tasks or gaming loops.")
        } else if (riskLevel == RiskLevel.MODERATE) {
            recs.add("Avoid prolonged high-performance usage while charging.")
        } else {
            recs.add("Device operating within standard safe thermal parameters.")
        }

        return RiskScenarioResult(
            scenarioName = "Device Overheating Under Load",
            riskLevel = riskLevel,
            description = description,
            recommendations = recs,
            isDataAvailable = true
        )
    }

    /**
     * Evaluates 'Sudden Impact or Drop' scenario using accelerometer and gyroscope data.
     * Computes total G-force magnitude and rotational velocity delta.
     */
    fun evaluateSuddenImpactRisk(state: SensorTelemetryState): RiskScenarioResult {
        if (!state.hasValidMotionData || state.accelX == null || state.accelY == null || state.accelZ == null) {
            return RiskScenarioResult(
                scenarioName = "Sudden Impact or Drop",
                riskLevel = RiskLevel.NORMAL,
                description = "Motion telemetry unavailable (Accelerometer hardware not detected or inactive).",
                recommendations = emptyList(),
                isDataAvailable = false
            )
        }

        val ax = state.accelX
        val ay = state.accelY
        val az = state.accelZ
        val accelMagnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val gForce = accelMagnitude / 9.81f

        val hasGyro = state.gyroX != null && state.gyroY != null && state.gyroZ != null
        val gyroMagnitude = if (hasGyro) {
            val gx = state.gyroX!!
            val gy = state.gyroY!!
            val gz = state.gyroZ!!
            sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        } else 0f

        val gDelta = abs(gForce - 1.0f)

        val riskLevel = when {
            gDelta > 5.0f || gyroMagnitude > 15f -> RiskLevel.CRITICAL
            gDelta > 3.0f || gyroMagnitude > 8f -> RiskLevel.HIGH
            gDelta > 1.8f || gyroMagnitude > 4f -> RiskLevel.MODERATE
            gDelta > 0.8f || gyroMagnitude > 2f -> RiskLevel.LOW
            else -> RiskLevel.NORMAL
        }

        val description = when (riskLevel) {
            RiskLevel.CRITICAL -> "SEVERE IMPACT DETECTED! G-Force spike (%.1fG)%s. Potential hardware shock.".format(
                gForce,
                if (hasGyro) " with angular rotation (%.1f rad/s)".format(gyroMagnitude) else ""
            )
            RiskLevel.HIGH -> "Hard Drop / Impact identified (%.1fG acceleration delta). Inspect device integrity.".format(gDelta)
            RiskLevel.MODERATE -> "Moderate jolt or sudden movement registered (%.1fG).".format(gForce)
            RiskLevel.LOW -> "Minor motion shift detected (%.1fG).".format(gForce)
            RiskLevel.NORMAL -> "Stable spatial positioning (%.1fG). No impact detected.".format(gForce)
        }

        val recs = mutableListOf<String>()
        if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            recs.add("Inspect physical casing and display glass for impact damage.")
            recs.add("Verify internal sensor calibration if movement anomalies persist.")
        } else {
            recs.add("Device motion stable.")
        }

        return RiskScenarioResult(
            scenarioName = "Sudden Impact or Drop",
            riskLevel = riskLevel,
            description = description,
            recommendations = recs,
            isDataAvailable = true
        )
    }
}
