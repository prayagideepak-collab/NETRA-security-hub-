package com.example.data.event

import com.example.data.model.SensorSnapshot

enum class EventSeverity {
    DEBUG, INFO, WARNING, CRITICAL
}

enum class EventState {
    STARTED, UPDATED, RESOLVED
}

/**
 * A standardized event that goes into the User-facing History.
 */
data class CanonicalEvent(
    val eventId: String, // SHA256 fingerprint
    val eventType: String,
    val severity: EventSeverity,
    val state: EventState,
    val episodeId: String,
    val message: String,
    val snapshot: SensorSnapshot,
    val timestamp: Long = System.currentTimeMillis()
)
