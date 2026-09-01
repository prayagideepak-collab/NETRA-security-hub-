package com.example.data.event

/**
 * Tracks a continuous condition as a single episode.
 */
data class EventEpisode(
    val episodeId: String,
    val eventType: String,
    val startTime: Long,
    var lastUpdate: Long,
    var peakValue: Float,
    var severity: EventSeverity,
    var isResolved: Boolean = false,
    var resolvedTime: Long? = null
)
