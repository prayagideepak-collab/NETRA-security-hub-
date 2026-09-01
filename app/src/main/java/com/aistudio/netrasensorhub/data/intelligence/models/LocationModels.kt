package com.aistudio.netrasensorhub.data.intelligence.models

enum class LocationStatus {
    ACQUIRING,
    VERIFIED,
    PERMISSION_REQUIRED,
    UNAVAILABLE
}

enum class MotionState {
    STATIONARY,
    WALKING,
    RUNNING,
    VEHICLE
}

data class LocationRecord(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val timestamp: Long,
    val city: String?,
    val district: String?,
    val state: String?,
    val country: String?,
    val locationConfidence: Float,
    val isVerified: Boolean
)

data class NearbyArea(
    val name: String,
    val districtOrState: String,
    val distanceKm: Double,
    val direction: String,
    val latitude: Double,
    val longitude: Double
)
