package com.example.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

enum class SensorCategory(val displayName: String) {
    MOTION("Motion"),
    ENVIRONMENTAL("Environmental"),
    POWER("Power & Battery"),
    THERMAL("Thermal"),
    CONNECTIVITY("Connectivity & Network"),
    LOCATION("Location & GNSS")
}

data class SensorCapabilityInfo(
    val id: String,
    val name: String,
    val vendor: String,
    val type: Int,
    val category: SensorCategory,
    val isSupported: Boolean,
    val maxRange: Float = 0f,
    val resolution: Float = 0f,
    val powerMa: Float = 0f,
    val minDelayUs: Int = 0,
    val description: String = ""
)

enum class DataClassification(val label: String) {
    VERIFIED("VERIFIED"),
    COMPUTED("COMPUTED")
}

data class RawSensorReading(
    val sensorId: String,
    val name: String,
    val category: SensorCategory,
    val values: FloatArray,
    val unit: String,
    val timestamp: Long = System.currentTimeMillis(),
    val classification: DataClassification = DataClassification.VERIFIED,
    val extraDetails: Map<String, String> = emptyMap(),
    val lastUpdateTimestamp: Long = timestamp,
    val sequenceNumber: Int = 0
) {
    fun isStale(thresholdMs: Long = 5000L): Boolean {
        return (System.currentTimeMillis() - lastUpdateTimestamp) > thresholdMs
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RawSensorReading
        return sensorId == other.sensorId &&
                values.contentEquals(other.values) &&
                timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = sensorId.hashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

enum class SafetyRiskLevel(val label: String, val colorHex: Long) {
    SAFE("SAFE", 0xFF00F5A0),
    ATTENTION("ATTENTION", 0xFFFFD600),
    WARNING("WARNING", 0xFFFF9100),
    EMERGENCY("EMERGENCY", 0xFFFF3366)
}

enum class FusionEventType(val title: String) {
    POCKET_DETECTION("Pocket Detection"),
    HIGH_HEAT_RISK("High Heat Risk"),
    IMPACT_DETECTION("Impact / Fall Event"),
    CHARGING_RISK("Charging Anomaly Risk"),
    MAGNETIC_HAZARD("Magnetic Anomaly Hazard"),
    DRIVING_DETECTION("Active Driving Detection")
}

@Serializable
data class SensorFusionState(
    val isPocketConfirmed: Boolean = false,
    val pocketConfidence: Float = 0f,

    val isHighHeatConfirmed: Boolean = false,
    val heatConfidence: Float = 0f,
    val batteryTempC: Float = 0f,
    val ambientLightLux: Float = 0f,

    val isImpactConfirmed: Boolean = false,
    val impactGForce: Float = 0f,

    val isChargingRiskConfirmed: Boolean = false,
    val chargingVoltageMv: Int = 0,
    val batteryLevelPercent: Int = 100,
    val isCharging: Boolean = false,

    val isMagneticHazardConfirmed: Boolean = false,
    val magneticMagnitudeuT: Float = 0f,
    val ambientTemperatureC: Float = 25f,
    val temperatureDifference: Float = 0f,

    // Driving State properties
    val isDrivingConfirmed: Boolean = false,
    val drivingConfidence: Float = 0f,
    val currentSpeedKmH: Float = 0f,
    val maxSpeedKmH: Float = 0f,
    val avgSpeedKmH: Float = 0f,
    val drivingDurationSec: Long = 0,
    val isRapidAccelerationDetected: Boolean = false,
    val isHighSpeedWarning: Boolean = false,
    val isApproachingControlledPoint: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val travelDirectionDeg: Float = 0f,
    val distanceTraveledM: Float = 0f,
    val isNavigationAppActive: Boolean = false,
    val classifiedTravelType: String = "UNKNOWN", // AUTO, DRIVING, PASSENGER, TRAIN, BUS, METRO, FLIGHT
    val classificationReason: String = "Monitoring signals...",
    val activeJourneyId: String? = null,
    val activeSegmentType: String = "IDLE", // DRIVING, WALKING, STOPPED, IDLE
    val journeyConfidenceScore: Int = 92,
    val batterySamplingMode: String = "LOW_POWER",

    val activeEventsCount: Int = 0,
    val isAnyModuleRefreshing: Boolean = false,
    val refreshingModules: List<String> = emptyList(),
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)

data class RiskAnalysisResult(
    val riskScore: Int, // 0 to 100
    val riskLevel: SafetyRiskLevel,
    val summary: String,
    val recommendations: List<String>,
    val explanation: String,
    val isAiPowered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class PrivacyScannerState(
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val bluetoothCount: Int? = null,
    val wifiCount: Int? = null,
    val magnetometerRawValue: Float? = null,
    val ambientLightValue: Float? = null,
    val cameraCheckResult: String? = null,
    val microphoneCheckResult: String? = null,
    val riskScore: Int = 0,
    val riskLevel: String = "Low",
    val detectedAnomalies: List<String> = emptyList(),
    val isFinished: Boolean = false,
    val scanStartedTime: Long = 0L
)

