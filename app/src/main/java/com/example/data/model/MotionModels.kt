package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MotionCategory(val displayName: String, val iconName: String) {
    STANDING("Standing", "accessibility"),
    WALKING("Walking", "directions_walk"),
    RUNNING("Running", "directions_run"),
    DRIVING("Driving", "directions_car"),
    UNKNOWN("Unknown / Other", "help_outline")
}

enum class MotionConfidence(val displayName: String) {
    LOW("Low Confidence"),
    MEDIUM("Medium Confidence"),
    HIGH("High Confidence")
}

data class MotionEvent(
    val eventId: String,
    val category: MotionCategory,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSec: Long,
    val confidence: MotionConfidence,
    val sourceSensors: List<String>,
    val distanceMeters: Double?,
    val stepCount: Int?,
    val timestamp: Long = System.currentTimeMillis(),
    val dataQuality: String = "VERIFIED",
    val dateKey: String
)

data class SubActivityStats(
    val category: MotionCategory,
    val durationSec: Long = 0L,
    val steps: Int? = null,
    val distanceMeters: Double? = null,
    val isAvailable: Boolean = true,
    val statusDescription: String = "",
    val averageSpeedKmH: Float? = null,
    val currentSpeedKmH: Float? = null,
    val cadenceStepsPerMin: Int? = null
)

data class TotalActivityStats(
    val totalSteps: Int? = null,
    val totalDistanceMeters: Double? = null,
    val totalActiveTimeSec: Long = 0L,
    val walkingSteps: Int? = null,
    val runningSteps: Int? = null,
    val drivingSteps: Int? = null,
    val standingDurationSec: Long = 0L,
    val isAvailable: Boolean = true
)

data class UserProfile(
    val dobEpochMs: Long? = null,
    val heightCm: Float? = null,
    val heightUnit: String = "cm", // "cm" or "ft/in"
    val gender: String = "" // "Male", "Female", "Other"
) {
    fun calculateAge(currentEpochMs: Long = System.currentTimeMillis()): Int? {
        if (dobEpochMs == null || dobEpochMs <= 0L) return null
        val dobCal = Calendar.getInstance().apply { timeInMillis = dobEpochMs }
        val nowCal = Calendar.getInstance().apply { timeInMillis = currentEpochMs }
        if (dobCal.after(nowCal)) return null

        var age = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
        if (nowCal.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return if (age >= 0) age else null
    }

    fun calculateStrideMeters(): Double? {
        val h = heightCm ?: return null
        if (h <= 0f) return null
        // Biomechanical stride length formula based on gender
        val factor = when (gender.lowercase()) {
            "female" -> 0.413
            "male" -> 0.415
            else -> 0.414
        }
        return (h * factor) / 100.0
    }

    val isConfigured: Boolean
        get() = dobEpochMs != null && heightCm != null && heightCm > 0f
}

data class ActivityTarget(
    val ageGroup: String,
    val stepTarget: Int?,
    val standingTargetSec: Long?,
    val targetSource: String,
    val effectiveDate: String
)

data class ActivityTargetProgress(
    val todaySteps: Int? = null,
    val targetSteps: Int? = null,
    val stepProgressPct: Int? = null,
    val todayStandingSec: Long = 0L,
    val targetStandingSec: Long? = null,
    val standingProgressPct: Int? = null,
    val targetConfigured: Boolean = false,
    val ageGroupLabel: String = ""
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestamp: Long,
    val isStartingPoint: Boolean = false,
    val isEndingPoint: Boolean = false
)

enum class RouteEventClassification(val displayName: String) {
    TURN("Turn"),
    SPEED_REDUCTION("Speed Reduction"),
    POSSIBLE_SPEED_BREAKER("Possible Speed Breaker"),
    STOP_PAUSE("Stop / Pause"),
    SIGNIFICANT_SPEED_DROP("Significant Speed Drop"),
    UNKNOWN_ROUTE_EVENT("Unknown Route Event")
}

enum class RainContext(val displayName: String) {
    RAIN_DETECTED("Rain Detected"),
    NO_RAIN("No Rain Detected"),
    UNAVAILABLE("Weather Unavailable")
}

data class RouteEventRecord(
    val eventId: String,
    val sessionId: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val previousSpeedKmH: Float?,
    val currentSpeedKmH: Float?,
    val speedDeltaKmH: Float?,
    val headingBeforeDeg: Float?,
    val headingAfterDeg: Float?,
    val motionType: MotionCategory,
    val classification: RouteEventClassification,
    val confidence: MotionConfidence
)

data class MotionRouteSession(
    val sessionId: String,
    val dateKey: String,
    val activityCategory: MotionCategory,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val startLocation: LocationSnapshot?,
    val endLocation: LocationSnapshot?,
    val snapshotDistanceMeters: Double?,
    val locationAccuracyMeters: Float?,
    val rainContext: RainContext = RainContext.UNAVAILABLE,
    val intermediateEvents: List<RouteEventRecord> = emptyList()
)

data class DailyMotionDashboardState(
    val dateKey: String,
    val displayDate: String,
    val currentMotionCategory: MotionCategory = MotionCategory.UNKNOWN,
    val currentConfidence: MotionConfidence = MotionConfidence.LOW,
    val standingStats: SubActivityStats = SubActivityStats(category = MotionCategory.STANDING),
    val walkingStats: SubActivityStats = SubActivityStats(category = MotionCategory.WALKING),
    val runningStats: SubActivityStats = SubActivityStats(category = MotionCategory.RUNNING),
    val drivingStats: SubActivityStats = SubActivityStats(category = MotionCategory.DRIVING),
    val totalActivity: TotalActivityStats = TotalActivityStats(),
    val targetProgress: ActivityTargetProgress = ActivityTargetProgress(),
    val isHistorical: Boolean = false,
    val availableHistoryDates: List<String> = emptyList(),
    val recentEvents: List<MotionEvent> = emptyList(),
    val routeSessions: List<MotionRouteSession> = emptyList(),
    val activeRouteSession: MotionRouteSession? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

object MotionTimeFormatter {
    fun formatTimelineDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatDisplayDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatDisplayTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        return sdf.format(Date(epochMs)).uppercase(Locale.getDefault())
    }

    fun formatDateKey(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun parseDateKeyToDisplay(dateKey: String): String {
        return try {
            val src = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = src.parse(dateKey)
            if (date != null) {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
            } else dateKey
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0 sec"
        val hours = seconds / 3600
        val remainder = seconds % 3600
        val minutes = remainder / 60
        val secs = remainder % 60

        return when {
            hours > 0 -> "${hours} hr ${minutes} min ${secs} sec"
            minutes > 0 -> "${minutes} min ${secs} sec"
            else -> "${secs} sec"
        }
    }

    fun formatDistance(meters: Double?): String {
        if (meters == null) return "Unavailable"
        return if (meters >= 1000.0) {
            "%.2f km".format(meters / 1000.0)
        } else {
            "%.0f m".format(meters)
        }
    }
}
