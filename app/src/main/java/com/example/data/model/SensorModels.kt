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

object SensorTypeConstants {
    const val ACCELEROMETER = "ACCELEROMETER"
    const val GYROSCOPE = "GYROSCOPE"
    const val MAGNETIC_FIELD = "MAGNETIC_FIELD"
    const val LIGHT = "LIGHT"
    const val PROXIMITY = "PROXIMITY"
    const val STEP_DETECTOR = "STEP_DETECTOR"
    const val STEP_COUNTER = "STEP_COUNTER"
    const val LOCATION = "LOCATION"
    const val ORIENTATION = "ORIENTATION"
    const val ROTATION_VECTOR = "ROTATION_VECTOR"
    const val AMBIENT_TEMPERATURE = "AMBIENT_TEMPERATURE"
    const val UNKNOWN = "UNKNOWN"

    fun normalizeSensorType(sensorId: String, name: String = ""): String {
        val idLower = sensorId.lowercase()
        val nameLower = name.lowercase()
        return when {
            idLower.contains("accel") || idLower == "sensor_1" || idLower.startsWith("sensor_1_") || nameLower.contains("accel") -> ACCELEROMETER
            idLower.contains("gyro") || idLower == "sensor_4" || idLower.startsWith("sensor_4_") || nameLower.contains("gyro") -> GYROSCOPE
            idLower.contains("mag") || idLower == "sensor_2" || idLower.startsWith("sensor_2_") || nameLower.contains("mag") || nameLower.contains("compass") -> MAGNETIC_FIELD
            idLower.contains("step_det") || idLower == "sensor_18" || idLower.startsWith("sensor_18_") || nameLower.contains("step detector") -> STEP_DETECTOR
            idLower.contains("step_count") || idLower == "sensor_19" || idLower.startsWith("sensor_19_") || nameLower.contains("step counter") || nameLower.contains("pedometer") -> STEP_COUNTER
            idLower.contains("light") || idLower == "sensor_5" || idLower.startsWith("sensor_5_") || nameLower.contains("light") -> LIGHT
            idLower.contains("prox") || idLower == "sensor_8" || idLower.startsWith("sensor_8_") || nameLower.contains("proximity") -> PROXIMITY
            idLower.contains("orient") || idLower == "sensor_3" || idLower.startsWith("sensor_3_") || nameLower.contains("orientation") -> ORIENTATION
            idLower.contains("rotation") || idLower == "sensor_11" || idLower.startsWith("sensor_11_") || nameLower.contains("rotation vector") -> ROTATION_VECTOR
            idLower.contains("temp") || idLower == "sensor_13" || idLower == "sensor_7" || nameLower.contains("temperature") -> AMBIENT_TEMPERATURE
            idLower.contains("location") || idLower == "gnss_location" -> LOCATION
            else -> sensorId
        }
    }
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

    fun freshness(now: Long = System.currentTimeMillis()): DataFreshness {
        val age = now - lastUpdateTimestamp
        return when {
            age < 5_000L -> DataFreshness.FRESH
            age < 15_000L -> DataFreshness.DELAYED
            age < 60_000L -> DataFreshness.STALE
            else -> DataFreshness.UNAVAILABLE
        }
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

