package com.example.data.model

enum class SafetyRiskState(val label: String, val colorHex: Long) {
    SAFE("SAFE", 0xFF00F5A0),
    ATTENTION("ATTENTION", 0xFFFFD600),
    WARNING("WARNING", 0xFFFF9100),
    CRITICAL("CRITICAL", 0xFFFF3366)
}

enum class DeviceHealthState(val label: String, val colorHex: Long) {
    HEALTHY("HEALTHY", 0xFF00F5A0),
    DEGRADED("DEGRADED", 0xFFFFD600),
    RECOVERING("RECOVERING", 0xFF00E5FF),
    UNAVAILABLE("UNAVAILABLE", 0xFF9E9E9E)
}

enum class DataFreshness(val label: String) {
    FRESH("FRESH"),       // < 5s old
    DELAYED("DELAYED"),   // 5s - 15s old
    STALE("STALE"),       // 15s - 60s old
    UNAVAILABLE("UNAVAILABLE") // > 60s or uninitialized
}

enum class SafetyEventLifecycleState {
    NORMAL,
    DETECTED,
    CONFIRMED,
    ACTIVE,
    ESCALATED,
    RECOVERING,
    RESOLVED
}

enum class EventTransitionAction {
    EVENT_STARTED,
    EVENT_UPDATED,
    EVENT_ESCALATED,
    EVENT_RECOVERING,
    EVENT_RESOLVED
}

enum class ConfidenceLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class SafetyDomain {
    THERMAL,
    MAGNETIC,
    MOTION,
    IMPACT,
    CHARGING
}

data class SubsystemHealth(
    val name: String,
    val healthState: DeviceHealthState,
    val freshness: DataFreshness,
    val lastTimestamp: Long,
    val statusMessage: String
)

data class CanonicalSafetyEvent(
    val eventId: String,               // e.g. THERMAL-20260901-001
    val domain: SafetyDomain,
    val eventType: String,
    val lifecycleState: SafetyEventLifecycleState,
    val severity: SafetyRiskState,
    val confidence: ConfidenceLevel,
    val title: String,
    val description: String,
    val startTime: Long = System.currentTimeMillis(),
    val lastUpdateTime: Long = startTime,
    val endTime: Long? = null,
    val peakValue: String? = null,
    val currentValue: String? = null,
    val thresholdValue: String? = null,
    val sourceSensors: List<String> = emptyList(),
    val evidence: String = "",
    val resolution: String? = null,
    val isAnnounced: Boolean = false,
    val lastAnnouncedTime: Long = 0L,
    val transitionAction: EventTransitionAction = EventTransitionAction.EVENT_STARTED
)

data class SafetyEngineState(
    val safetyRiskState: SafetyRiskState = SafetyRiskState.SAFE,
    val deviceHealthState: DeviceHealthState = DeviceHealthState.HEALTHY,
    val activeEvents: List<CanonicalSafetyEvent> = emptyList(),
    val subsystemHealths: Map<String, SubsystemHealth> = emptyMap(),
    val lastEvaluatedTime: Long = System.currentTimeMillis()
)
