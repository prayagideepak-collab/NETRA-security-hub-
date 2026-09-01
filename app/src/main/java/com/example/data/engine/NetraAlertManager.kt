package com.example.data.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventDao
import com.example.data.db.SafetyEventEntity
import com.example.data.model.CanonicalSafetyEvent
import com.example.data.model.ConfidenceLevel
import com.example.data.model.EventTransitionAction
import com.example.data.model.SafetyDomain
import com.example.data.model.SafetyEventLifecycleState
import com.example.data.model.SafetyRiskState
import com.example.util.LoggingManager
import com.example.util.NetraTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class NetraAlertManager(
    private val context: Context,
    private val safetyEventDao: SafetyEventDao = NetraDatabase.getInstance(context).safetyEventDao(),
    private val ttsManager: NetraTtsManager? = null
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        const val CHANNEL_ID_CRITICAL = "netra_safety_critical_channel"
        const val CHANNEL_ID_WARNING = "netra_safety_warning_channel"
        const val CHANNEL_ID_INFO = "netra_safety_info_channel"

        const val NOTIFICATION_ID_CRITICAL = 2001
        const val NOTIFICATION_ID_WARNING = 2002
        const val NOTIFICATION_ID_INFO = 2003

        const val SAFETY_REPEAT_INTERVAL_MS = 60_000L // 60s repeat cooldown for active hazard
        const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L
    }

    private val lastAnnouncedTimestamps = ConcurrentHashMap<String, Long>()
    private val lastNotificationTimestamps = ConcurrentHashMap<String, Long>()
    private val oscillationCounters = ConcurrentHashMap<String, MutableList<Long>>()

    private val _recentAlertMessages = MutableStateFlow<List<String>>(emptyList())
    val recentAlertMessages: StateFlow<List<String>> = _recentAlertMessages.asStateFlow()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (notificationManager != null) {
                // 1. Critical Channel
                val criticalChan = NotificationChannel(
                    CHANNEL_ID_CRITICAL,
                    "Netra Critical Safety Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent safety alerts requiring immediate action"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                    setBypassDnd(true)
                    setShowBadge(true)
                }

                // 2. Warning Channel
                val warningChan = NotificationChannel(
                    CHANNEL_ID_WARNING,
                    "Netra Safety Warnings",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Elevated safety anomaly warnings and threshold notifications"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    setShowBadge(true)
                }

                // 3. Info & Recovery Channel
                val infoChan = NotificationChannel(
                    CHANNEL_ID_INFO,
                    "Netra Safety Information & Recovery",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Safety status recovery notices and telemetry information"
                    enableVibration(false)
                    setShowBadge(false)
                }

                notificationManager.createNotificationChannel(criticalChan)
                notificationManager.createNotificationChannel(warningChan)
                notificationManager.createNotificationChannel(infoChan)
            }
        }
    }

    /**
     * Central Dispatcher for Canonical Safety Events.
     */
    fun dispatchSafetyEvent(event: CanonicalSafetyEvent) {
        val now = System.currentTimeMillis()

        // Oscillation protection check
        if (isOscillationExceeded(event.eventId, now)) {
            LoggingManager.warning(
                "NetraAlertManager",
                "OSCILLATION_SUPPRESSED",
                "Alert Damping Active",
                "Rapid state flapping detected for ${event.eventId}. Throttling alerts."
            )
            return
        }

        // 1. Persist/Update to Database with lifecycle deduplication
        persistOrUpdateEvent(event)

        // 2. Evaluate Notification Delivery
        evaluateNotification(event, now)

        // 3. Evaluate Voice Announcement Delivery
        evaluateVoiceAnnouncement(event, now)
    }

    private fun evaluateNotification(event: CanonicalSafetyEvent, now: Long) {
        val key = "${event.eventId}_${event.lifecycleState}"
        val lastNotif = lastNotificationTimestamps[key] ?: 0L

        val shouldNotify = when (event.transitionAction) {
            EventTransitionAction.EVENT_STARTED -> true
            EventTransitionAction.EVENT_ESCALATED -> true
            EventTransitionAction.EVENT_RESOLVED -> true
            EventTransitionAction.EVENT_UPDATED -> (now - lastNotif >= SAFETY_REPEAT_INTERVAL_MS)
            EventTransitionAction.EVENT_RECOVERING -> false
        }

        if (!shouldNotify) return

        lastNotificationTimestamps[key] = now

        val (channelId, notifId, priority) = when (event.severity) {
            SafetyRiskState.CRITICAL -> Triple(CHANNEL_ID_CRITICAL, NOTIFICATION_ID_CRITICAL, NotificationCompat.PRIORITY_MAX)
            SafetyRiskState.WARNING -> Triple(CHANNEL_ID_WARNING, NOTIFICATION_ID_WARNING, NotificationCompat.PRIORITY_HIGH)
            SafetyRiskState.ATTENTION -> Triple(CHANNEL_ID_WARNING, NOTIFICATION_ID_WARNING, NotificationCompat.PRIORITY_DEFAULT)
            SafetyRiskState.SAFE -> Triple(CHANNEL_ID_INFO, NOTIFICATION_ID_INFO, NotificationCompat.PRIORITY_LOW)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NETRA SAFETY: ${event.title}")
            .setContentText(event.description)
            .setPriority(priority)
            .setAutoCancel(true)

        if (event.severity == SafetyRiskState.CRITICAL) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            builder.setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not granted
        }
    }

    private fun evaluateVoiceAnnouncement(event: CanonicalSafetyEvent, now: Long) {
        // Step 1: Is event real & verified?
        if (event.confidence == ConfidenceLevel.LOW && event.severity != SafetyRiskState.CRITICAL) {
            return
        }

        // Step 2: Check deduplication & repeat cooldown
        val lastTime = lastAnnouncedTimestamps[event.eventId] ?: 0L
        val isCooldownPassed = (now - lastTime) >= SAFETY_REPEAT_INTERVAL_MS

        val shouldAnnounce = when (event.transitionAction) {
            EventTransitionAction.EVENT_STARTED -> (event.severity >= SafetyRiskState.WARNING)
            EventTransitionAction.EVENT_ESCALATED -> true
            EventTransitionAction.EVENT_RESOLVED -> true
            EventTransitionAction.EVENT_UPDATED -> (isCooldownPassed && event.severity == SafetyRiskState.CRITICAL)
            EventTransitionAction.EVENT_RECOVERING -> false
        }

        if (!shouldAnnounce) return

        // Step 3: Night Mode quiet period policy (10:00 PM to 6:00 AM)
        if (isNightModeActive(now)) {
            // In Night Mode, only CRITICAL safety warnings are spoken. Ordinary/warning announcements are suppressed.
            if (event.severity != SafetyRiskState.CRITICAL) {
                return
            }
        }

        // Step 4: Audio Interruption Protection (do not interrupt phone calls)
        if (isUserOnPhoneCall()) {
            return
        }

        // Step 5: Format spoken text with ZERO battery percentage / charging announcements
        val spokenMessage = when (event.transitionAction) {
            EventTransitionAction.EVENT_RESOLVED -> {
                "Safety condition resolved. ${event.resolution ?: "Sensors returned to nominal range."}"
            }
            EventTransitionAction.EVENT_ESCALATED -> {
                "Safety alert escalated. ${event.description}"
            }
            else -> {
                if (event.severity == SafetyRiskState.CRITICAL) {
                    "Critical safety alert. ${event.description}"
                } else {
                    "Safety warning. ${event.description}"
                }
            }
        }

        lastAnnouncedTimestamps[event.eventId] = now
        ttsManager?.speakAlert(spokenMessage)

        // Keep a rolling buffer of recent alerts
        val currentList = _recentAlertMessages.value.toMutableList()
        currentList.add(0, spokenMessage)
        if (currentList.size > 10) {
            _recentAlertMessages.value = currentList.take(10)
        } else {
            _recentAlertMessages.value = currentList
        }
    }

    fun isNightModeActive(now: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        // 10:00 PM (22) to 6:00 AM (6)
        return hour >= 22 || hour < 6
    }

    private fun isUserOnPhoneCall(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
            false
        }
    }

    private fun isOscillationExceeded(eventId: String, now: Long): Boolean {
        val timestamps = oscillationCounters.getOrPut(eventId) { mutableListOf() }
        synchronized(timestamps) {
            timestamps.add(now)
            // Retain entries from the last 2 minutes
            timestamps.removeAll { now - it > 120_000L }
            return timestamps.size > 4 // More than 4 rapid transitions in 2 minutes
        }
    }

    private fun persistOrUpdateEvent(event: CanonicalSafetyEvent) {
        scope.launch {
            try {
                val existing = safetyEventDao.getEventByEventId(event.eventId)
                if (existing == null) {
                    safetyEventDao.insertEvent(
                        SafetyEventEntity(
                            eventId = event.eventId,
                            domain = event.domain.name,
                            lifecycleState = event.lifecycleState.name,
                            timestamp = event.lastUpdateTime,
                            startTime = event.startTime,
                            lastUpdateTime = event.lastUpdateTime,
                            endTime = event.endTime,
                            riskLevel = when (event.severity) {
                                SafetyRiskState.CRITICAL -> "CRITICAL"
                                SafetyRiskState.WARNING -> "WARNING"
                                SafetyRiskState.ATTENTION -> "ATTENTION"
                                SafetyRiskState.SAFE -> "SAFE"
                            },
                            riskScore = when (event.severity) {
                                SafetyRiskState.CRITICAL -> 90
                                SafetyRiskState.WARNING -> 65
                                SafetyRiskState.ATTENTION -> 40
                                SafetyRiskState.SAFE -> 10
                            },
                            eventType = event.eventType,
                            title = event.title,
                            description = event.description,
                            peakValue = event.peakValue,
                            currentValue = event.currentValue,
                            thresholdValue = event.thresholdValue,
                            primarySensorValuesJson = "{\"sources\":\"${event.sourceSensors.joinToString(",")}\"}",
                            aiRecommendation = "Verified hardware safety event. Maintain device safety precautions.",
                            isVerifiedHardwareEvent = true,
                            moduleName = "NetraSafetyEngine",
                            severity = event.severity.name,
                            aiConfidence = when (event.confidence) {
                                ConfidenceLevel.HIGH -> 0.95f
                                ConfidenceLevel.MEDIUM -> 0.75f
                                ConfidenceLevel.LOW -> 0.50f
                            },
                            evidence = event.evidence,
                            resolution = event.resolution
                        )
                    )
                } else {
                    val updated = existing.copy(
                        lifecycleState = event.lifecycleState.name,
                        lastUpdateTime = event.lastUpdateTime,
                        endTime = event.endTime ?: existing.endTime,
                        riskLevel = when (event.severity) {
                            SafetyRiskState.CRITICAL -> "CRITICAL"
                            SafetyRiskState.WARNING -> "WARNING"
                            SafetyRiskState.ATTENTION -> "ATTENTION"
                            SafetyRiskState.SAFE -> "SAFE"
                        },
                        riskScore = when (event.severity) {
                            SafetyRiskState.CRITICAL -> 90
                            SafetyRiskState.WARNING -> 65
                            SafetyRiskState.ATTENTION -> 40
                            SafetyRiskState.SAFE -> 10
                        },
                        peakValue = event.peakValue ?: existing.peakValue,
                        currentValue = event.currentValue ?: existing.currentValue,
                        evidence = event.evidence.ifEmpty { existing.evidence },
                        resolution = event.resolution ?: existing.resolution,
                        severity = event.severity.name
                    )
                    safetyEventDao.updateEvent(updated)
                }

                // If event resolved, perform 7-day retention pruning
                if (event.lifecycleState == SafetyEventLifecycleState.RESOLVED) {
                    val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MS
                    safetyEventDao.pruneOldResolvedEvents(cutoff)
                }
            } catch (_: Exception) {}
        }
    }
}
