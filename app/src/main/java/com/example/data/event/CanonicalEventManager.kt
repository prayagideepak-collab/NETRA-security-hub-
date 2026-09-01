package com.example.data.event

import com.example.data.model.SensorSnapshot
import com.example.data.sensor.SensorStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest

/**
 * Handles deduplication, state-change-only logging, and event creation.
 * This is the gatekeeper for user-facing History.
 */
class CanonicalEventManager(
    private val sensorStateManager: SensorStateManager
) {
    private val activeEpisodes = mutableMapOf<String, EventEpisode>()
    
    // Simple deduplication memory
    private val lastFingerprints = mutableMapOf<String, String>()

    fun processEvent(
        eventType: String,
        severity: EventSeverity,
        message: String,
        snapshot: SensorSnapshot
    ) {
        val episodeId = generateEpisodeId(eventType)
        val fingerprint = generateFingerprint(eventType, severity, message)
        
        // 1. Deduplication Check
        if (lastFingerprints[episodeId] == fingerprint) {
            // Ignore if it's the same state/message, unless it's a reminder
            return
        }
        
        // 2. State Change Logging only
        val event = CanonicalEvent(
            eventId = fingerprint,
            eventType = eventType,
            severity = severity,
            state = EventState.UPDATED, // Simplified for now
            episodeId = episodeId,
            message = message,
            snapshot = snapshot
        )
        
        lastFingerprints[episodeId] = fingerprint
        
        // 3. Dispatch to History Database/UI
        // (Implementation will link to NetraDatabase)
    }

    private fun generateEpisodeId(eventType: String): String {
        return eventType // Simple for now
    }

    private fun generateFingerprint(eventType: String, severity: EventSeverity, message: String): String {
        val input = "$eventType|$severity|$message"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
