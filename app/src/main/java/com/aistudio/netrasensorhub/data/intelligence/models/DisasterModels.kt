package com.aistudio.netrasensorhub.data.intelligence.models

enum class AlertSeverity {
    INFO,
    WATCH,
    WARNING,
    CRITICAL
}

enum class DisasterCategory {
    WEATHER,
    SEISMIC,
    FLOOD,
    CYCLONE,
    HEATWAVE,
    AIR_QUALITY,
    LANDSLIDE,
    GENERAL_SAFETY
}

data class DisasterAlert(
    val eventId: String,
    val title: String,
    val category: DisasterCategory,
    val severity: AlertSeverity,
    val locationName: String,
    val latitude: Double?,
    val longitude: Double?,
    val distanceKmFromCurrent: Double?,
    val impactRadiusKm: Double?,
    val localImpactLevel: AlertSeverity,
    val description: String,
    val officialSource: String,
    val confidence: Float,
    val timestamp: Long,
    val isUnconfirmed: Boolean
)

data class SeismicEvent(
    val eventId: String,
    val magnitude: Double,
    val depthKm: Double,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val originTimeMillis: Long,
    val distanceKmFromCurrent: Double,
    val potentialLocalImpact: AlertSeverity,
    val officialConfirmation: String
)
