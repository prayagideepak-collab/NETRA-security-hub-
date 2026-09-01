package com.example.util

import android.content.Context
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object CoordinationManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastAnnouncementTimestamp: Long = 0L
    private const val ANNOUNCEMENT_LOCK_DURATION_MS = 5000L // 5 seconds lock
    private val eventCounter = AtomicInteger(100)

    fun generateEventId(categoryCode: String): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dateStr = dateFormat.format(Date())
        val count = eventCounter.incrementAndGet()
        return "$categoryCode-$dateStr-%05d".format(count)
    }

    fun requestAnnouncement(
        appSource: String, // "Sensor Hub" or "Battery Sentinel"
        primaryApp: String, // "Sensor Hub" or "Battery Sentinel"
        eventType: String,
        title: String,
        description: String,
        isBluetoothConnected: Boolean,
        hasHigherPriorityActive: Boolean
    ) {
        val currentTime = System.currentTimeMillis()
        val eventId = generateEventId("ANN")

        // 1. Check Announcement Lock
        if (currentTime - lastAnnouncementTimestamp < ANNOUNCEMENT_LOCK_DURATION_MS) {
            LoggingManager.announcement(
                eventName = "ANNOUNCEMENT_SUPPRESSED",
                title = "Announcement Suppressed: $title",
                description = "Reason: Announcement Lock Active (within 5s window). Event ID: $eventId",
                status = "SUPPRESSED_LOCK"
            )
            return
        }

        // 2. Check Bluetooth
        if (!isBluetoothConnected) {
            LoggingManager.announcement(
                eventName = "ANNOUNCEMENT_SUPPRESSED",
                title = "Announcement Suppressed: $title",
                description = "Reason: Bluetooth Device Not Connected. Event ID: $eventId",
                status = "SUPPRESSED_NO_BT"
            )
            return
        }

        // 3. Check Higher Priority
        if (hasHigherPriorityActive) {
            LoggingManager.announcement(
                eventName = "ANNOUNCEMENT_SUPPRESSED",
                title = "Announcement Suppressed: $title",
                description = "Reason: Higher Priority Event Active. Event ID: $eventId",
                status = "SUPPRESSED_PRIORITY"
            )
            return
        }

        // 4. Primary Ownership Arbitration
        if (appSource != primaryApp) {
            LoggingManager.announcement(
                eventName = "ANNOUNCEMENT_SUPPRESSED",
                title = "Announcement Suppressed: $title",
                description = "Reason: Primary App ($primaryApp) Responsible. Event ID: $eventId",
                status = "SUPPRESSED_PRIMARY"
            )
            return
        }

        // If all checks pass, play announcement & lock
        lastAnnouncementTimestamp = currentTime
        LoggingManager.announcement(
            eventName = "ANNOUNCEMENT_PLAYED",
            title = "Announcement Played: $title",
            description = "Source: $appSource | Event ID: $eventId | Description: $description",
            status = "PLAYED"
        )
    }

    fun sendHeartbeat(moduleName: String) {
        LoggingManager.info(
            module = "Coordination Service",
            event = "HEARTBEAT_SYNC",
            title = "$moduleName Heartbeat Active",
            description = "Unified Ecosystem Synchronization operational. Session ID: ${LoggingManager.sessionId}"
        )
    }
}
