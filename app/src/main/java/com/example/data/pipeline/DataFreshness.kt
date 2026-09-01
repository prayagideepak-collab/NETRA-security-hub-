package com.example.data.pipeline

/**
 * Data freshness classifications representing the temporal validity
 * of live and imported system metrics.
 */
enum class DataFreshness {
    FRESH,        // Recently acquired from authoritative hardware/API
    DELAYED,      // Within acceptable historical/periodic sampling window
    STALE,        // Exceeded freshness interval, clearly labeled as last-known/cached
    UNAVAILABLE   // Sensor absent, permission missing, or no data recorded yet
}

/**
 * Encapsulates a validated piece of device telemetry with origin metadata,
 * temporal timestamps, quality ranking, and freshness assessment.
 */
data class ValidatedMetric<T>(
    val value: T?,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val receivedAt: Long = System.currentTimeMillis(),
    val dataAgeMs: Long = 0L,
    val quality: String = "VERIFIED_HARDWARE",
    val freshness: DataFreshness = if (value != null) DataFreshness.FRESH else DataFreshness.UNAVAILABLE
) {
    val isAvailable: Boolean
        get() = value != null && freshness != DataFreshness.UNAVAILABLE

    companion object {
        fun <T> unavailable(source: String, reason: String = "No data"): ValidatedMetric<T> {
            return ValidatedMetric(
                value = null,
                source = source,
                quality = reason,
                freshness = DataFreshness.UNAVAILABLE
            )
        }

        fun <T> fresh(value: T, source: String, timestamp: Long = System.currentTimeMillis()): ValidatedMetric<T> {
            val now = System.currentTimeMillis()
            return ValidatedMetric(
                value = value,
                source = source,
                timestamp = timestamp,
                receivedAt = now,
                dataAgeMs = (now - timestamp).coerceAtLeast(0L),
                quality = "VERIFIED_HARDWARE",
                freshness = DataFreshness.FRESH
            )
        }

        fun <T> stale(value: T, source: String, timestamp: Long): ValidatedMetric<T> {
            val now = System.currentTimeMillis()
            return ValidatedMetric(
                value = value,
                source = source,
                timestamp = timestamp,
                receivedAt = now,
                dataAgeMs = (now - timestamp).coerceAtLeast(0L),
                quality = "CACHED_LAST_KNOWN",
                freshness = DataFreshness.STALE
            )
        }
    }
}
