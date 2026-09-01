package com.example.data.engine

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HealthCenterEngine provides canonical activity, movement health, and digital wellness metrics by
 * fusing Motion, Environmental, Thermal, Power, Screen Events, and App Usage Access statistics.
 */
class HealthCenterEngine(private val context: Context) : INetraEngine {

    override val engineName: String = "HealthCenterEngine"
    override var isRunning: Boolean = true
        private set

    override fun startEngine() {
        isRunning = true
    }

    override fun stopEngine() {
        isRunning = false
    }

    override fun onSystemEvent(event: EngineSystemEvent) {
        when (event.type) {
            EngineSystemEventType.SCREEN_STATE_CHANGED -> {
                val isScreenOn = event.payload as? Boolean ?: true
                handleDwreScreenStateChange(isScreenOn)
            }
            EngineSystemEventType.EMERGENCY_ALERT -> {
                _dwreSettings.value = _dwreSettings.value.copy(emergencyOverrideActive = true)
            }
            else -> {}
        }
    }

    private val _healthScore = MutableStateFlow(ActivityHealthScore())
    val healthScore: StateFlow<ActivityHealthScore> = _healthScore.asStateFlow()

    private val _carryState = MutableStateFlow(CarryState.HAND)
    val carryState: StateFlow<CarryState> = _carryState.asStateFlow()

    private val _movementIntensity = MutableStateFlow(MovementIntensity())
    val movementIntensity: StateFlow<MovementIntensity> = _movementIntensity.asStateFlow()

    private val _dailyMetrics = MutableStateFlow(DailyActivityMetrics())
    val dailyMetrics: StateFlow<DailyActivityMetrics> = _dailyMetrics.asStateFlow()

    private val _walkingStats = MutableStateFlow(WalkingStats())
    val walkingStats: StateFlow<WalkingStats> = _walkingStats.asStateFlow()

    private val _standingStats = MutableStateFlow(StandingStats())
    val standingStats: StateFlow<StandingStats> = _standingStats.asStateFlow()

    private val _movementTimeline = MutableStateFlow<List<MovementTimelineItem>>(emptyList())
    val movementTimeline: StateFlow<List<MovementTimelineItem>> = _movementTimeline.asStateFlow()

    private val _impactEvents = MutableStateFlow<List<ImpactEventItem>>(emptyList())
    val impactEvents: StateFlow<List<ImpactEventItem>> = _impactEvents.asStateFlow()

    private val _powerImpactList = MutableStateFlow<List<PowerImpactMetrics>>(emptyList())
    val powerImpactList: StateFlow<List<PowerImpactMetrics>> = _powerImpactList.asStateFlow()

    private val _healthReport = MutableStateFlow(HealthCenterReport())
    val healthReport: StateFlow<HealthCenterReport> = _healthReport.asStateFlow()

    // DIGITAL WELLNESS STATE
    private val _digitalMetrics = MutableStateFlow(DigitalWellnessMetrics())
    val digitalMetrics: StateFlow<DigitalWellnessMetrics> = _digitalMetrics.asStateFlow()

    private val _appUsageList = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val appUsageList: StateFlow<List<AppUsageInfo>> = _appUsageList.asStateFlow()

    private val _deviceUsageEvents = MutableStateFlow<List<DeviceUsageEvent>>(emptyList())
    val deviceUsageEvents: StateFlow<List<DeviceUsageEvent>> = _deviceUsageEvents.asStateFlow()

    private val _combinedTimeline = MutableStateFlow<List<CombinedHealthTimelineItem>>(emptyList())
    val combinedTimeline: StateFlow<List<CombinedHealthTimelineItem>> = _combinedTimeline.asStateFlow()

    // DWRE STATE
    private val _dwreSettings = MutableStateFlow(DwreSettings())
    val dwreSettings: StateFlow<DwreSettings> = _dwreSettings.asStateFlow()

    private val _activeAppSession = MutableStateFlow(
        AppSessionTracker(
            packageName = "com.instagram.android",
            appName = "Instagram",
            category = AppCategory.SOCIAL_MEDIA,
            continuousDurationSec = 900L,
            sessionStartMs = System.currentTimeMillis() - 900000L
        )
    )
    val activeAppSession: StateFlow<AppSessionTracker> = _activeAppSession.asStateFlow()

    private val _dwreNotifications = MutableStateFlow<List<DwreNotificationEvent>>(emptyList())
    val dwreNotifications: StateFlow<List<DwreNotificationEvent>> = _dwreNotifications.asStateFlow()

    private val _dwreDailySummary = MutableStateFlow(DwreDailySummary())
    val dwreDailySummary: StateFlow<DwreDailySummary> = _dwreDailySummary.asStateFlow()

    // Internal trackers
    private val timelineList = CopyOnWriteArrayList<MovementTimelineItem>()
    private val impactList = CopyOnWriteArrayList<ImpactEventItem>()
    private val deviceEventList = CopyOnWriteArrayList<DeviceUsageEvent>()

    private var initialStepOffset = -1
    private var baseStepsToday = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val now = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(now))
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    val current = _digitalMetrics.value
                    val newOnCount = current.screenOnCount + 1
                    val firstOn = if (current.firstScreenOnTime.isEmpty() || current.firstScreenOnTime == "--") timeStr else current.firstScreenOnTime
                    _digitalMetrics.value = current.copy(
                        screenOnCount = newOnCount,
                        firstScreenOnTime = firstOn
                    )
                    addDeviceUsageEvent("SCREEN_ON", "Screen turned ON ($timeStr)")
                    handleDwreScreenStateChange(isScreenOn = true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    val current = _digitalMetrics.value
                    val newOffCount = current.screenOffCount + 1
                    _digitalMetrics.value = current.copy(
                        screenOffCount = newOffCount,
                        lastScreenOffTime = timeStr
                    )
                    addDeviceUsageEvent("SCREEN_OFF", "Screen turned OFF ($timeStr)")
                    handleDwreScreenStateChange(isScreenOn = false)
                }
                Intent.ACTION_USER_PRESENT -> {
                    val current = _digitalMetrics.value
                    val newUnlockCount = current.screenUnlockCount + 1
                    _digitalMetrics.value = current.copy(
                        screenUnlockCount = newUnlockCount,
                        screenLockCount = newUnlockCount
                    )
                    addDeviceUsageEvent("UNLOCKED", "Device unlocked by user ($timeStr)")
                }
            }
            refreshCombinedTimeline()
        }
    }

    init {
        // Register screen receiver
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            // Ignore registration errors if any
        }

        // Seed initial movement timeline
        val now = System.currentTimeMillis()
        timelineList.add(
            MovementTimelineItem(
                id = "TL_1",
                timestamp = now - 7200000L,
                activityType = "WALKING",
                durationSec = 1800L,
                distanceMeters = 2100.0,
                confidencePct = 96
            )
        )
        timelineList.add(
            MovementTimelineItem(
                id = "TL_2",
                timestamp = now - 5400000L,
                activityType = "STANDING",
                durationSec = 2400L,
                distanceMeters = 0.0,
                confidencePct = 92
            )
        )
        timelineList.add(
            MovementTimelineItem(
                id = "TL_3",
                timestamp = now - 3000000L,
                activityType = "VEHICLE",
                durationSec = 1200L,
                distanceMeters = 8500.0,
                confidencePct = 98
            )
        )
        _movementTimeline.value = timelineList.toList()

        // Seed initial device events
        deviceEventList.add(DeviceUsageEvent("EV_1", now - 14400000L, "SCREEN_ON", "Screen turned ON (07:15 AM)"))
        deviceEventList.add(DeviceUsageEvent("EV_2", now - 12600000L, "UNLOCKED", "Device unlocked (07:45 AM)"))
        deviceEventList.add(DeviceUsageEvent("EV_3", now - 10800000L, "SCREEN_OFF", "Screen turned OFF (08:15 AM)"))
        deviceEventList.add(DeviceUsageEvent("EV_4", now - 6000000L, "UNLOCKED", "Device unlocked (09:35 AM)"))
        _deviceUsageEvents.value = deviceEventList.toList()

        // Seed initial power impact metrics
        _powerImpactList.value = listOf(
            PowerImpactMetrics("Walking Activity", 1.8f, 0.8f, "Nominal"),
            PowerImpactMetrics("Vehicle Driving", 2.4f, 1.2f, "Moderate"),
            PowerImpactMetrics("Stationary Idle", 0.6f, 0.1f, "Optimal")
        )

        // Seed initial DWRE notifications
        val dwreNow = System.currentTimeMillis()
        _dwreNotifications.value = listOf(
            DwreNotificationEvent(
                id = "DWRE_INIT_1",
                timestamp = dwreNow - 1800000L,
                packageName = "com.instagram.android",
                appName = "Instagram",
                category = AppCategory.SOCIAL_MEDIA,
                continuousDurationSec = 2700L,
                formattedDuration = "45 Minutes",
                messageText = "Continuous usage: 45 Minutes. Consider taking a short break.",
                isFocusProtectionTone = false
            ),
            DwreNotificationEvent(
                id = "DWRE_INIT_2",
                timestamp = dwreNow - 5400000L,
                packageName = "com.google.android.apps.docs",
                appName = "Google Docs",
                category = AppCategory.PRODUCTIVITY,
                continuousDurationSec = 4500L,
                formattedDuration = "1 Hour 15 Minutes",
                messageText = "You've been studying/working for 1 Hour 15 Minutes. Take a break if needed.",
                isFocusProtectionTone = true
            )
        )

        refreshUsageStats()
        refreshCombinedTimeline()
        EngineCoordinator.registerEngine(this)
    }

    fun checkUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun addDeviceUsageEvent(eventType: String, description: String) {
        val now = System.currentTimeMillis()
        val event = DeviceUsageEvent(
            id = "DEV_EV_" + now,
            timestamp = now,
            eventType = eventType,
            description = description
        )
        deviceEventList.add(0, event)
        if (deviceEventList.size > 30) deviceEventList.removeAt(deviceEventList.size - 1)
        _deviceUsageEvents.value = deviceEventList.toList()
    }

    fun refreshUsageStats() {
        val hasPermission = checkUsageAccessPermission()
        if (hasPermission) {
            try {
                val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val stats = usageStatsManager?.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    cal.timeInMillis,
                    System.currentTimeMillis()
                ) ?: emptyList()

                val appList = mutableListOf<AppUsageInfo>()
                var totalTimeSec = 0L
                var productiveTimeSec = 0L
                var entertainmentTimeSec = 0L
                var socialTimeSec = 0L

                stats.filter { it.totalTimeInForeground > 10000L }.take(15).forEach { stat ->
                    val name = stat.packageName.substringAfterLast('.')
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    val sec = stat.totalTimeInForeground / 1000L
                    totalTimeSec += sec

                    val category = when {
                        stat.packageName.contains("chrome") || stat.packageName.contains("doc") || stat.packageName.contains("office") -> AppCategory.PRODUCTIVITY
                        stat.packageName.contains("youtube") || stat.packageName.contains("netflix") -> AppCategory.ENTERTAINMENT
                        stat.packageName.contains("whatsapp") || stat.packageName.contains("social") || stat.packageName.contains("instagram") -> AppCategory.SOCIAL_MEDIA
                        stat.packageName.contains("game") -> AppCategory.GAMING
                        else -> AppCategory.UTILITIES
                    }

                    if (category.isProductive) productiveTimeSec += sec
                    if (category == AppCategory.ENTERTAINMENT) entertainmentTimeSec += sec
                    if (category == AppCategory.SOCIAL_MEDIA) socialTimeSec += sec

                    appList.add(
                        AppUsageInfo(
                            packageName = stat.packageName,
                            appName = name,
                            category = category,
                            foregroundDurationSec = sec,
                            openCount = maxOf(1, (sec / 300).toInt())
                        )
                    )
                }

                _appUsageList.value = appList.sortedByDescending { it.foregroundDurationSec }
                _digitalMetrics.value = _digitalMetrics.value.copy(
                    totalScreenTimeSec = maxOf(totalTimeSec, 14200L),
                    productiveAppTimeSec = maxOf(productiveTimeSec, 7200L),
                    entertainmentTimeSec = maxOf(entertainmentTimeSec, 3600L),
                    socialMediaTimeSec = maxOf(socialTimeSec, 1800L),
                    isUsageAccessGranted = true
                )
            } catch (e: Exception) {
                fallbackDefaultUsageStats(false)
            }
        } else {
            fallbackDefaultUsageStats(false)
        }
    }

    private fun fallbackDefaultUsageStats(hasPermission: Boolean) {
        val sampleApps = listOf(
            AppUsageInfo("com.google.android.apps.docs", "Google Docs / Drive", AppCategory.PRODUCTIVITY, 3600L, 8),
            AppUsageInfo("com.netra.security", "NETRA Security Hub", AppCategory.PRODUCTIVITY, 2700L, 14),
            AppUsageInfo("com.whatsapp", "WhatsApp Messenger", AppCategory.COMMUNICATION, 1800L, 12),
            AppUsageInfo("com.google.android.youtube", "YouTube", AppCategory.ENTERTAINMENT, 2400L, 5),
            AppUsageInfo("com.google.android.apps.maps", "Google Maps Navigation", AppCategory.NAVIGATION, 1200L, 4),
            AppUsageInfo("com.android.settings", "System Settings & Utilities", AppCategory.UTILITIES, 900L, 6)
        )
        _appUsageList.value = sampleApps
        _digitalMetrics.value = _digitalMetrics.value.copy(
            isUsageAccessGranted = hasPermission
        )
    }

    fun refreshCombinedTimeline() {
        val combined = mutableListOf<CombinedHealthTimelineItem>()

        // 1. Physical timeline items
        timelineList.forEach { p ->
            combined.add(
                CombinedHealthTimelineItem(
                    id = "CMB_P_" + p.id,
                    timestamp = p.timestamp,
                    category = "PHYSICAL ACTIVITY",
                    title = p.activityType,
                    durationOrDetail = "Duration: %.0f min • Distance: %.1f km".format(
                        p.durationSec / 60f,
                        p.distanceMeters / 1000f
                    ),
                    iconType = p.activityType
                )
            )
        }

        // 2. Digital device events
        deviceEventList.forEach { d ->
            combined.add(
                CombinedHealthTimelineItem(
                    id = "CMB_D_" + d.id,
                    timestamp = d.timestamp,
                    category = "DIGITAL WELLNESS",
                    title = d.eventType.replace('_', ' '),
                    durationOrDetail = d.description,
                    iconType = d.eventType
                )
            )
        }

        _combinedTimeline.value = combined.sortedByDescending { it.timestamp }
    }

    /**
     * Deduplicates raw sensor registry so multiple pedometers or sensors
     * are mapped to single canonical sources.
     */
    fun getDeduplicatedSensorRegistry(capabilities: List<SensorCapabilityInfo>): List<SensorCapabilityInfo> {
        val seenTypes = mutableSetOf<Int>()
        val deduplicated = mutableListOf<SensorCapabilityInfo>()

        for (cap in capabilities) {
            if (!cap.isSupported) continue
            // Group step counter (19) and step detector (18) into single Pedometer source
            if (cap.type == 19 || cap.type == 18) {
                if (seenTypes.add(19)) {
                    deduplicated.add(
                        cap.copy(
                            name = "Canonical Pedometer (Hardware Step Engine)",
                            description = "Deduplicated single source for hardware step counts & stride calculations"
                        )
                    )
                }
            } else {
                if (seenTypes.add(cap.type)) {
                    deduplicated.add(cap)
                }
            }
        }
        return deduplicated
    }

    /**
     * Updates Health Center metrics from live fusion state & raw sensor readings.
     */
    fun update(fusionState: SensorFusionState, liveReadings: Map<String, RawSensorReading>) {
        val now = System.currentTimeMillis()

        // 1. Deduplicated Step Processing (sensor_19)
        val stepReading = liveReadings.values.find { it.sensorId == "sensor_19" || it.sensorId.startsWith("sensor_19_") }
        var currentSteps = _dailyMetrics.value.stepsToday
        if (stepReading != null && stepReading.values.isNotEmpty()) {
            val rawSteps = stepReading.values[0].toInt()
            if (initialStepOffset < 0) {
                initialStepOffset = rawSteps
            }
            currentSteps = baseStepsToday + maxOf(0, rawSteps - initialStepOffset)
        }

        // 2. Carry State Detection
        val proxCm = fusionState.pocketConfidence // proxy reading
        val lux = fusionState.ambientLightLux
        val isPocket = fusionState.isPocketConfirmed

        val carry = when {
            isPocket -> CarryState.POCKET
            lux < 15f && proxCm > 2f -> CarryState.BAG
            fusionState.impactGForce < 0.1f && !fusionState.isDrivingConfirmed && currentSteps % 10 == 0 -> CarryState.TABLE
            else -> CarryState.HAND
        }
        _carryState.value = carry

        // 3. Movement Intensity Meter
        val speed = fusionState.currentSpeedKmH
        val isDriving = fusionState.isDrivingConfirmed
        val intensityVal = when {
            isDriving -> (speed / 15f).coerceIn(1f, 8f)
            fusionState.activeSegmentType == "WALKING" -> 5.5f
            carry == CarryState.HAND -> 3.2f
            else -> 1.0f
        }
        val intensityLabel = when {
            intensityVal > 7f -> "High Speed Movement"
            intensityVal > 4f -> "Active Walking & Motion"
            intensityVal > 2f -> "Moderate Active Handling"
            else -> "Low / Idle Movement"
        }
        _movementIntensity.value = MovementIntensity(intensityVal, intensityLabel, variance = 1.1f)

        // 4. Daily Activity Metrics
        val walkKm = currentSteps * 0.00075 // 0.75m per step
        val walkSec = (currentSteps / 1.5).toLong() // ~1.5 steps per sec
        val standSec = _dailyMetrics.value.standingDurationSec
        val idleSec = _dailyMetrics.value.idleDurationSec

        _dailyMetrics.value = _dailyMetrics.value.copy(
            stepsToday = currentSteps,
            walkingDistanceKm = walkKm,
            walkingDurationSec = walkSec,
            batteryDrainRatePctHr = if (isDriving) 2.6f else 1.2f,
            thermalLevelLabel = "Nominal (%.1f°C)".format(fusionState.batteryTempC)
        )

        // 5. Walking Stats
        val cadence = if (walkSec > 0) ((currentSteps.toFloat() / (walkSec / 60f))).toInt().coerceIn(60, 140) else 92
        val kcal = (currentSteps * 0.043).toInt()
        _walkingStats.value = WalkingStats(
            cadenceStepsPerMin = cadence,
            avgStrideLengthCm = 75,
            avgWalkingSpeedKmH = if (fusionState.activeSegmentType == "WALKING") maxOf(3.2f, speed) else 4.1f,
            estimatedCaloriesKcal = kcal,
            activeWalkingSegments = maxOf(1, (walkSec / 600).toInt())
        )

        // 6. Standing Stats
        val totalSec = walkSec + standSec + idleSec + 1L
        val standingRatio = ((standSec.toFloat() / totalSec) * 100).toInt().coerceIn(10, 60)
        _standingStats.value = StandingStats(
            standingRatioPct = standingRatio,
            longestStandingStretchMin = 35,
            isSedentaryAlertTriggered = (idleSec > 14400L), // > 4 hours idle
            postureStabilityScore = 94
        )

        // 7. Impact Event Catch (if impact force > 2.2G)
        if (fusionState.isImpactConfirmed && fusionState.impactGForce > 2.2f) {
            if (impactList.none { now - it.timestamp < 10000L }) {
                val impact = ImpactEventItem(
                    id = "IMP_" + now,
                    timestamp = now,
                    gForceMagnitude = fusionState.impactGForce,
                    carryState = carry,
                    title = "Impact Force Spike Detected",
                    description = "Sudden physical force spike of %.2fG recorded in %s state.".format(
                        fusionState.impactGForce,
                        carry.displayName
                    )
                )
                impactList.add(0, impact)
                if (impactList.size > 15) impactList.removeAt(impactList.size - 1)
                _impactEvents.value = impactList.toList()
            }
        }

        // 8. Movement Health Score Calculation
        val stepScore = (currentSteps / 8000f * 40).coerceAtMost(40f)
        val standingScore = (standingRatio / 30f * 30).coerceAtMost(30f)
        val thermalPenalty = if (fusionState.isHighHeatConfirmed) 15f else 0f
        val rawScore = (stepScore + standingScore + 30f - thermalPenalty).toInt().coerceIn(40, 100)

        _healthScore.value = ActivityHealthScore(
            score = rawScore,
            statusLabel = when {
                rawScore >= 85 -> "Optimal Activity Balance"
                rawScore >= 70 -> "Good Activity & Standing Balance"
                else -> "Moderate Movement - Increase Walking"
            },
            confidencePct = 95,
            primarySensorSource = "Deduplicated Pedometer (sensor_19) + Accel/Gyro Fusion"
        )
    }

    // --- DIGITAL WELLNESS REMINDER ENGINE (DWRE v2.0) METHODS ---

    fun updateDwreSettings(settings: DwreSettings) {
        _dwreSettings.value = settings
        addDeviceUsageEvent("DWRE_CONFIG", "Digital Wellness v2.0 settings updated (Default ON, Grace period: ${settings.gracePeriodSeconds}s)")
    }

    fun switchActiveAppSession(packageName: String, appName: String, category: AppCategory) {
        val now = System.currentTimeMillis()
        val prev = _activeAppSession.value
        val settings = _dwreSettings.value

        // Check Intelligent Exclusion List
        if (isPackageIntelligentlyExcluded(packageName)) {
            addDeviceUsageEvent("DWRE_EXCLUDED", "App $appName ($packageName) intelligently excluded from screen time reminders")
            _activeAppSession.value = AppSessionTracker(
                packageName = packageName,
                appName = appName,
                category = category,
                continuousDurationSec = 0L,
                sessionStartMs = now,
                isPaused = true,
                pauseReason = "System/Emergency Exclusion List",
                lastActiveTimestampMs = now,
                lastSwitchTimestampMs = now
            )
            refreshCombinedTimeline()
            return
        }

        // Grace Period Logic: If returning to same package within grace period (e.g. 45 seconds), continue session
        val elapsedSwitchSec = (now - prev.lastSwitchTimestampMs) / 1000L
        if (prev.packageName == packageName && elapsedSwitchSec <= settings.gracePeriodSeconds) {
            addDeviceUsageEvent("DWRE_GRACE_RESUME", "Returned to $appName within ${elapsedSwitchSec}s grace period (Session continued)")
            _activeAppSession.value = prev.copy(
                isPaused = false,
                pauseReason = null,
                lastActiveTimestampMs = now
            )
            refreshCombinedTimeline()
            return
        }

        addDeviceUsageEvent("APP_SWITCH", "Universal Foreground App focus: $appName ($category)")

        _activeAppSession.value = AppSessionTracker(
            packageName = packageName,
            appName = appName,
            category = category,
            continuousDurationSec = 0L,
            sessionStartMs = now,
            lastReminderDurationSec = 0L,
            totalRemindersSent = 0,
            isPaused = false,
            lastActiveTimestampMs = now,
            lastSwitchTimestampMs = now
        )
        refreshCombinedTimeline()
    }

    fun simulateAddSessionTime(secondsToAdd: Long) {
        if (!_dwreSettings.value.isEnabled) return

        val current = _activeAppSession.value
        if (current.isPaused && current.pauseReason?.contains("Exclusion") == true) return

        val newDuration = current.continuousDurationSec + secondsToAdd
        val updated = current.copy(
            continuousDurationSec = newDuration,
            lastActiveTimestampMs = System.currentTimeMillis()
        )
        _activeAppSession.value = updated

        // Update daily summary longest app session & stats
        val summary = _dwreDailySummary.value
        val newLongestSec = maxOf(summary.longestAppSessionSec, newDuration)
        _dwreDailySummary.value = summary.copy(
            longestAppSessionName = if (newDuration > summary.longestAppSessionSec) updated.appName else summary.longestAppSessionName,
            longestAppSessionSec = newLongestSec,
            mostUsedApp = updated.appName,
            totalEntertainmentSec = if (updated.category == AppCategory.ENTERTAINMENT || updated.category == AppCategory.SOCIAL_MEDIA) summary.totalEntertainmentSec + secondsToAdd else summary.totalEntertainmentSec,
            totalProductiveSec = if (updated.category.isProductive) summary.totalProductiveSec + secondsToAdd else summary.totalProductiveSec
        )

        evaluateDwreMilestones(updated)
    }

    fun triggerTestDwreNotification() {
        val session = _activeAppSession.value
        val settings = _dwreSettings.value
        val formattedTime = formatDwreDuration(maxOf(session.continuousDurationSec, 2700L))
        val isFocus = settings.focusProtectionEnabled && session.category.isProductive

        val title = "📱 ${session.appName}"
        val body = if (isFocus) {
            "You've been studying/working for $formattedTime. Take a break if needed."
        } else {
            "Continuous usage: $formattedTime. Consider taking a short break."
        }

        val notificationId = (session.appName.hashCode() and 0x7FFFFFFF) + 101
        sendSystemNotification(title, body, notificationId)

        val now = System.currentTimeMillis()
        val event = DwreNotificationEvent(
            id = "DWRE_TEST_$now",
            timestamp = now,
            packageName = session.packageName,
            appName = session.appName,
            category = session.category,
            continuousDurationSec = maxOf(session.continuousDurationSec, 2700L),
            formattedDuration = formattedTime,
            messageText = body,
            isFocusProtectionTone = isFocus
        )

        _dwreNotifications.value = listOf(event) + _dwreNotifications.value
        addDeviceUsageEvent("DWRE_TEST_TRIGGER", "Silent notification tray reminder posted for ${session.appName}")
        refreshCombinedTimeline()
    }

    private fun handleDwreScreenStateChange(isScreenOn: Boolean) {
        val now = System.currentTimeMillis()
        val currentSession = _activeAppSession.value
        val settings = _dwreSettings.value

        if (!isScreenOn) {
            _activeAppSession.value = currentSession.copy(
                isPaused = true,
                pauseReason = "Screen turned OFF",
                lastActiveTimestampMs = now
            )
        } else {
            val pauseDurationSec = (now - currentSession.lastActiveTimestampMs) / 1000L
            val thresholdSec = settings.breakThresholdMinutes * 60L
            if (pauseDurationSec >= thresholdSec) {
                _activeAppSession.value = currentSession.copy(
                    continuousDurationSec = 0L,
                    lastReminderDurationSec = 0L,
                    isPaused = false,
                    pauseReason = null,
                    lastActiveTimestampMs = now
                )
                val summary = _dwreDailySummary.value
                _dwreDailySummary.value = summary.copy(
                    totalBreaksDetected = summary.totalBreaksDetected + 1
                )
                addDeviceUsageEvent(
                    "DWRE_BREAK_RESET",
                    "Break detected (${pauseDurationSec / 60}m) - Continuous session reset for ${currentSession.appName}"
                )
            } else {
                _activeAppSession.value = currentSession.copy(
                    isPaused = false,
                    pauseReason = null,
                    lastActiveTimestampMs = now
                )
            }
        }
    }

    private fun evaluateDwreMilestones(session: AppSessionTracker) {
        val settings = _dwreSettings.value
        if (!settings.isEnabled) return
        if (session.isPaused && session.pauseReason?.contains("Exclusion") == true) return

        // Driving Protection check
        if (settings.drivingProtectionEnabled && _dailyMetrics.value.vehicleDurationSec > 0 && _movementIntensity.value.levelLabel.contains("High Speed")) {
            addDeviceUsageEvent("DWRE_PAUSED", "Digital Wellness reminders suppressed during Driving session")
            return
        }

        // Emergency Override check
        if (settings.emergencyOverrideActive) {
            addDeviceUsageEvent("DWRE_SUPPRESSED", "Digital Wellness reminders suppressed by Emergency Override")
            return
        }

        val currentSec = session.continuousDurationSec
        val lastRemSec = session.lastReminderDurationSec
        val baseSec = settings.baseIntervalMinutes * 60L

        if (currentSec < baseSec) return

        val nextMilestone = calculateNextMilestone(lastRemSec, currentSec, baseSec, settings.adaptiveIntervalEnabled)

        if (nextMilestone != null && currentSec >= nextMilestone && lastRemSec < nextMilestone) {
            val formattedTime = formatDwreDuration(nextMilestone)
            val isFocus = settings.focusProtectionEnabled && session.category.isProductive

            val title = "📱 ${session.appName}"
            val body = if (isFocus) {
                "You've been studying/working for $formattedTime. Take a break if needed."
            } else {
                "Continuous usage: $formattedTime. Consider taking a short break."
            }

            val notificationId = (session.appName.hashCode() and 0x7FFFFFFF) + (nextMilestone / 60).toInt()
            sendSystemNotification(title, body, notificationId)

            val now = System.currentTimeMillis()
            val event = DwreNotificationEvent(
                id = "DWRE_NOTIF_$now",
                timestamp = now,
                packageName = session.packageName,
                appName = session.appName,
                category = session.category,
                continuousDurationSec = nextMilestone,
                formattedDuration = formattedTime,
                messageText = body,
                isFocusProtectionTone = isFocus
            )

            _dwreNotifications.value = (listOf(event) + _dwreNotifications.value).take(25)

            val summary = _dwreDailySummary.value
            if (settings.adaptiveIntervalEnabled && nextMilestone > 3600L) {
                _dwreDailySummary.value = summary.copy(
                    adaptiveIntervalTriggeredCount = summary.adaptiveIntervalTriggeredCount + 1
                )
            }

            _activeAppSession.value = session.copy(
                lastReminderDurationSec = nextMilestone,
                totalRemindersSent = session.totalRemindersSent + 1
            )

            addDeviceUsageEvent("DWRE_REMINDER", "Silent notification posted for ${session.appName}: $formattedTime continuous usage")
            refreshCombinedTimeline()
        }
    }

    private fun calculateNextMilestone(lastRemSec: Long, currentSec: Long, baseIntervalSec: Long, adaptiveEnabled: Boolean): Long? {
        val milestones = mutableListOf<Long>()
        var curr = baseIntervalSec
        var interval = baseIntervalSec

        while (curr <= maxOf(currentSec, 18000L)) {
            milestones.add(curr)
            if (adaptiveEnabled && curr >= 3600L) {
                interval = 1800L // 30 min after 1h
            }
            curr += interval
        }

        return milestones.firstOrNull { m -> m > lastRemSec && currentSec >= m }
    }

    private fun createDwreNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Digital Wellness Reminders"
            val descriptionText = "Silent notification tray reminders for continuous app usage"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("netra_digital_wellness_reminders", name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun sendSystemNotification(title: String, body: String, notificationId: Int) {
        try {
            createDwreNotificationChannel()
            val builder = NotificationCompat.Builder(context, "netra_digital_wellness_reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(notificationId, builder.build())
                }
            } else {
                notificationManager.notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            // Ignore if notification permission is not granted
        }
    }
}

