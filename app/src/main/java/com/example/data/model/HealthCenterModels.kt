package com.example.data.model

import com.example.util.TimeManager

enum class CarryState(val displayName: String, val iconName: String) {
    POCKET("In Pocket", "pocket"),
    HAND("In Hand / Active", "hand"),
    BAG("In Bag / Backpack", "bag"),
    TABLE("On Desk / Table", "table"),
    UNKNOWN("Detecting Carry State...", "unknown")
}

enum class AppCategory(val displayName: String, val isProductive: Boolean) {
    PRODUCTIVITY("Productivity", true),
    EDUCATION("Education", true),
    COMMUNICATION("Communication", true),
    FINANCE("Finance", true),
    NAVIGATION("Navigation", true),
    HEALTH_FITNESS("Health & Fitness", true),
    ENTERTAINMENT("Entertainment", false),
    SOCIAL_MEDIA("Social Media", false),
    GAMING("Gaming", false),
    UTILITIES("Utilities", true),
    OTHER("Other", false)
}

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val foregroundDurationSec: Long,
    val openCount: Int
)

data class DeviceUsageEvent(
    val id: String,
    val timestamp: Long,
    val eventType: String, // "SCREEN_ON", "SCREEN_OFF", "UNLOCKED", "LOCKED", "APP_LAUNCHED"
    val description: String
)

data class DigitalWellnessMetrics(
    val totalScreenTimeSec: Long = 14200L, // ~3.9 hours
    val screenOnCount: Int = 42,
    val screenOffCount: Int = 42,
    val screenUnlockCount: Int = 31,
    val screenLockCount: Int = 31,
    val longestContinuousSessionSec: Long = 2700L, // 45 min
    val avgSessionDurationSec: Long = 680L, // 11.3 min
    val firstScreenOnTime: String = "07:15 AM",
    val lastScreenOffTime: String = "11:05 PM",

    val digitalWellnessScore: Int = 84, // 0 to 100
    val wellnessStatusLabel: String = "Balanced Digital Routine",

    val productiveAppTimeSec: Long = 7200L, // 2 hours
    val entertainmentTimeSec: Long = 3600L, // 1 hour
    val socialMediaTimeSec: Long = 1800L, // 30 min
    val gamingTimeSec: Long = 900L, // 15 min
    val communicationTimeSec: Long = 1200L, // 20 min
    val idleScreenTimeSec: Long = 600L, // 10 min

    val totalFocusTimeSec: Long = 5400L, // 1.5 hours
    val longestFocusSessionSec: Long = 3000L, // 50 min
    val focusSessionCount: Int = 3,

    val screenBatteryConsumptionPct: Float = 14.2f,
    val avgThermalDuringScreenOnC: Float = 31.8f,
    val batteryInsight: String = "Screen activity accounted for ~14.2% of total battery drain today.",

    val isUsageAccessGranted: Boolean = false
)

data class CombinedHealthTimelineItem(
    val id: String,
    val timestamp: Long,
    val category: String, // "PHYSICAL" or "DIGITAL"
    val title: String,
    val durationOrDetail: String,
    val iconType: String // "WALKING", "DRIVING", "SCREEN_ON", "APP_USAGE", "UNLOCK", "STANDING"
)

data class ActivityHealthScore(
    val score: Int = 92, // 0 to 100
    val statusLabel: String = "Optimal Activity Balance",
    val confidencePct: Int = 94,
    val primarySensorSource: String = "Pedometer (sensor_19) + Accel/Gyro Fusion"
)

data class MovementIntensity(
    val value: Float = 3.5f, // 0.0 to 10.0
    val levelLabel: String = "Moderate Active Movement",
    val variance: Float = 1.25f
)

data class DailyActivityMetrics(
    val stepsToday: Int = 4280,
    val walkingDistanceKm: Double = 3.21,
    val walkingDurationSec: Long = 2700L, // 45 min
    val standingDurationSec: Long = 5400L, // 90 min
    val idleDurationSec: Long = 18000L, // 5 hours
    val stopDurationSec: Long = 1200L, // 20 min
    val vehicleDurationSec: Long = 1800L, // 30 min
    val batteryDrainRatePctHr: Float = 1.8f,
    val thermalLevelLabel: String = "Nominal (29.5°C)"
)

data class WalkingStats(
    val cadenceStepsPerMin: Int = 95,
    val avgStrideLengthCm: Int = 75,
    val avgWalkingSpeedKmH: Float = 4.2f,
    val estimatedCaloriesKcal: Int = 185,
    val activeWalkingSegments: Int = 4
)

data class StandingStats(
    val standingRatioPct: Int = 28,
    val longestStandingStretchMin: Int = 32,
    val isSedentaryAlertTriggered: Boolean = false,
    val postureStabilityScore: Int = 95
)

data class MovementTimelineItem(
    val id: String,
    val timestamp: Long,
    val activityType: String, // "WALKING", "STANDING", "VEHICLE", "IDLE", "IMPACT"
    val durationSec: Long,
    val distanceMeters: Double,
    val confidencePct: Int
)

data class ImpactEventItem(
    val id: String,
    val timestamp: Long,
    val gForceMagnitude: Float,
    val carryState: CarryState,
    val title: String,
    val description: String
)

data class PowerImpactMetrics(
    val activityType: String,
    val batteryDrainRatePctHr: Float,
    val thermalRiseCPerHr: Float,
    val thermalStatus: String
)

data class HealthCenterReport(
    val todaySteps: Int = 4280,
    val yesterdaySteps: Int = 3850,
    val weeklyAvgSteps: Int = 4120,
    val todayWalkingKm: Double = 3.21,
    val weeklyAvgWalkingKm: Double = 3.05,
    val healthScoreTrend: String = "Upwards (+4% vs last week)",
    val insightsSummary: List<String> = listOf(
        "Optimal walking cadence achieved during morning session (98 steps/min).",
        "Standing ratio is healthy (28%), preventing prolonged sedentary fatigue.",
        "Thermal impact during walking remains within nominal range (+0.8°C/hr)."
    ),
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

data class DwreSettings(
    val isEnabled: Boolean = true,
    val gracePeriodSeconds: Int = 45,
    val baseIntervalMinutes: Int = 15,
    val adaptiveIntervalEnabled: Boolean = true,
    val breakThresholdMinutes: Int = 2,
    val focusProtectionEnabled: Boolean = true,
    val drivingProtectionEnabled: Boolean = true,
    val emergencyOverrideActive: Boolean = false,
    val notifyInTrayOnly: Boolean = true,
    val silentNotificationsOnly: Boolean = true
)

val SYSTEM_EXCLUDED_PACKAGES = setOf(
    "com.android.dialer",
    "com.samsung.android.incallui",
    "com.android.incallui",
    "com.google.android.dialer",
    "com.android.settings",
    "com.google.android.setupwizard",
    "com.android.systemui",
    "com.android.keyguard",
    "com.example",
    "com.example.aistudio",
    "com.aistudio.netrasecurityhub"
)

fun isPackageIntelligentlyExcluded(packageName: String): Boolean {
    val pkgLower = packageName.lowercase()
    if (SYSTEM_EXCLUDED_PACKAGES.contains(pkgLower)) return true
    if (pkgLower.contains("dialer") ||
        pkgLower.contains("incallui") ||
        pkgLower.contains("setupwizard") ||
        pkgLower.contains("keyguard") ||
        pkgLower.contains("biometric") ||
        pkgLower.contains("lockscreen")
    ) {
        return true
    }
    return false
}

data class AppSessionTracker(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val continuousDurationSec: Long = 0L,
    val sessionStartMs: Long = System.currentTimeMillis(),
    val lastReminderDurationSec: Long = 0L,
    val totalRemindersSent: Int = 0,
    val isPaused: Boolean = false,
    val pauseReason: String? = null,
    val lastActiveTimestampMs: Long = System.currentTimeMillis(),
    val lastSwitchTimestampMs: Long = System.currentTimeMillis()
)

data class DwreNotificationEvent(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val continuousDurationSec: Long,
    val formattedDuration: String,
    val messageText: String,
    val isFocusProtectionTone: Boolean
)

data class DwreDailySummary(
    val longestAppSessionName: String = "Instagram",
    val longestAppSessionSec: Long = 4500L,
    val totalContinuousSessions: Int = 8,
    val totalBreaksDetected: Int = 12,
    val mostUsedApp: String = "Instagram",
    val totalProductiveSec: Long = 7200L,
    val totalEntertainmentSec: Long = 3600L,
    val adaptiveIntervalTriggeredCount: Int = 3,
    val avgSessionDurationSec: Long = 1050L
)

fun formatDwreDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    if (totalMinutes < 60) {
        return if (totalMinutes <= 1) "1 Minute" else "$totalMinutes Minutes"
    }
    val hours = totalMinutes / 60
    val remMin = totalMinutes % 60
    val hStr = if (hours == 1L) "1 Hour" else "$hours Hours"
    if (remMin == 0L) return hStr
    val mStr = if (remMin == 1L) "1 Minute" else "$remMin Minutes"
    return "$hStr $mStr"
}

