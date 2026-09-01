package com.example.data.engine

import com.example.data.model.RiskAnalysisResult
import com.example.data.model.SafetyRiskLevel
import com.example.data.model.SensorFusionState

object RuleBasedSafetyEngine {

    fun evaluateSafety(fusionState: SensorFusionState): RiskAnalysisResult {
        var score = 0
        val recs = mutableListOf<String>()
        val issues = mutableListOf<String>()

        if (fusionState.isImpactConfirmed) {
            score += 65
            issues.add("Spike in G-Force (${"%.1f".format(fusionState.impactGForce)}G) detected")
            recs.add("Inspect device for physical impact damage.")
        }

        if (fusionState.isHighHeatConfirmed) {
            score += 35
            issues.add("Battery temperature high (${fusionState.batteryTempC}°C)")
            recs.add("Remove phone case and halt heavy processing.")
        }

        if (fusionState.isChargingRiskConfirmed) {
            score += 30
            issues.add("Charging thermal anomaly (${fusionState.chargingVoltageMv}mV)")
            recs.add("Unplug charger to prevent battery thermal stress.")
        }

        if (fusionState.isMagneticHazardConfirmed) {
            score += 20
            issues.add("Strong magnetic field (${"%.1f".format(fusionState.magneticMagnitudeuT)} µT)")
            recs.add("Move device away from high-power magnets or motors.")
        }

        if (fusionState.isHighSpeedWarning) {
            score += 10
            issues.add("Excessive travel speed (${"%.1f".format(fusionState.currentSpeedKmH)} km/h)")
            recs.add("Observe speed limits and exercise driving safety.")
        }

        if (fusionState.isPocketConfirmed) {
            issues.add("Device enclosed in pocket/bag")
        }

        if (fusionState.isAnyModuleRefreshing) {
            issues.add("Watchdog active recovery sequence (Refreshing: ${fusionState.refreshingModules.joinToString(", ")})")
            recs.add("Wait for automatic recovery to restore nominal tracking.")
        }

        // Base score when everything is nominal is 0, otherwise at least 5 if there's any minor thing, or coerce.
        if (issues.isNotEmpty() && score == 0) {
            score = 5
        }

        val finalScore = score.coerceIn(0, 100)

        val level = when {
            finalScore >= 75 -> SafetyRiskLevel.EMERGENCY
            finalScore >= 50 -> SafetyRiskLevel.WARNING
            finalScore >= 25 -> SafetyRiskLevel.ATTENTION
            else -> SafetyRiskLevel.SAFE
        }

        val summary = when {
            fusionState.isAnyModuleRefreshing -> "System Recovery Mode Active"
            level == SafetyRiskLevel.EMERGENCY -> "Critical Hardware Safety Warning"
            level == SafetyRiskLevel.WARNING -> "Elevated Risk Anomaly Detected"
            level == SafetyRiskLevel.ATTENTION -> "Minor Telemetry Variance"
            level == SafetyRiskLevel.SAFE -> "Device Telemetry Nominal & Safe"
            else -> "Device Telemetry Nominal & Safe"
        }

        val explanation = when {
            fusionState.isAnyModuleRefreshing -> {
                "The Watchdog Engine is actively refreshing stale modules: ${fusionState.refreshingModules.joinToString(", ")} to prevent UI frozen state."
            }
            issues.isEmpty() -> {
                "Verified real-time hardware telemetry is operating within nominal safety thresholds."
            }
            else -> {
                "Verified sensor anomalies: " + issues.joinToString("; ")
            }
        }

        if (recs.isEmpty()) {
            recs.add("Keep device in well-ventilated location.")
            recs.add("Monitor live telemetry for thermal variances.")
        }

        return RiskAnalysisResult(
            riskScore = finalScore,
            riskLevel = level,
            summary = summary,
            recommendations = recs,
            explanation = explanation,
            isAiPowered = false
        )
    }
}
