package com.example.data.pipeline

import com.example.util.LoggingManager

/**
 * Handles real-data anomaly detection for implausible battery jumps
 * and evaluates discrete charging/discharging percentage milestones silently.
 */
class BatteryAnomalyDetector {

    data class BatteryReadingSnapshot(
        val percentage: Int,
        val isCharging: Boolean,
        val plugType: String,
        val timestamp: Long
    )

    data class BatteryAnomalyEvent(
        val eventId: String,
        val previousPercentage: Int,
        val currentPercentage: Int,
        val deltaPercentage: Int,
        val elapsedSeconds: Long,
        val isCharging: Boolean,
        val timestamp: Long,
        val description: String
    )

    data class MilestoneEvent(
        val milestonePercent: Int,
        val isCharging: Boolean,
        val timestamp: Long
    )

    companion object {
        // Charging milestones including 99%
        val CHARGING_MILESTONES = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 99)
        // Discharging milestones (95 is final discharge milestone, 99 excluded)
        val DISCHARGING_MILESTONES = listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95)
    }

    private var lastSnapshot: BatteryReadingSnapshot? = null
    private var lastTriggeredMilestone: Int? = null
    private var lastTriggeredWasCharging: Boolean? = null

    /**
     * Evaluates a new battery reading against historical snapshots.
     * Returns a [BatteryAnomalyEvent] if an implausible jump is detected, or null.
     */
    fun evaluateReading(
        currentPct: Int,
        isCharging: Boolean,
        plugType: String,
        timestamp: Long = System.currentTimeMillis()
    ): BatteryAnomalyEvent? {
        val prev = lastSnapshot
        val currentSnapshot = BatteryReadingSnapshot(currentPct, isCharging, plugType, timestamp)
        lastSnapshot = currentSnapshot

        if (prev == null) return null

        val delta = currentPct - prev.percentage
        val elapsedSec = ((timestamp - prev.timestamp) / 1000L).coerceAtLeast(1L)

        // If charging status switched (e.g., charger plugged/unplugged), battery voltage recalculation
        // can cause a 1-2% re-estimation jump. We only flag rapid unexplainable jumps.
        val isPlugStatusChanged = prev.isCharging != isCharging || prev.plugType != plugType

        // Anomaly condition: absolute change >= 4% within < 10 seconds under continuous power state
        if (!isPlugStatusChanged && Math.abs(delta) >= 4 && elapsedSec < 10L) {
            val eventId = "BATTERY_JUMP_${timestamp}"
            val desc = "Implausible battery jump detected: ${prev.percentage}% -> ${currentPct}% in ${elapsedSec}s (Delta: ${delta}%)."
            LoggingManager.warning(
                module = "BatteryDiagnostics",
                event = "BATTERY_REPORTING_ANOMALY",
                title = "Battery Telemetry Anomaly",
                description = desc
            )
            return BatteryAnomalyEvent(
                eventId = eventId,
                previousPercentage = prev.percentage,
                currentPercentage = currentPct,
                deltaPercentage = delta,
                elapsedSeconds = elapsedSec,
                isCharging = isCharging,
                timestamp = timestamp,
                description = desc
            )
        }

        return null
    }

    /**
     * Checks if the current battery percentage TOUCHES/reaches any defined milestone.
     * Evaluates charging (5..99) or discharging (5..95) milestones.
     * Returns the [MilestoneEvent] if a milestone was reached, or null.
     */
    fun checkMilestone(
        currentPct: Int,
        isCharging: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): MilestoneEvent? {
        val applicableMilestones = if (isCharging) CHARGING_MILESTONES else DISCHARGING_MILESTONES

        // Check if the current percentage matches an exact milestone
        if (applicableMilestones.contains(currentPct)) {
            // Avoid re-triggering the same milestone multiple times in the same power state
            if (lastTriggeredMilestone == currentPct && lastTriggeredWasCharging == isCharging) {
                return null
            }

            lastTriggeredMilestone = currentPct
            lastTriggeredWasCharging = isCharging

            return MilestoneEvent(
                milestonePercent = currentPct,
                isCharging = isCharging,
                timestamp = timestamp
            )
        }

        return null
    }

    fun reset() {
        lastSnapshot = null
        lastTriggeredMilestone = null
        lastTriggeredWasCharging = null
    }
}
