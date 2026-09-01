package com.example.data.engine

import android.content.Context
import android.hardware.Sensor
import com.example.data.db.DailyMotionSummaryEntity
import com.example.data.db.MotionDao
import com.example.data.db.MotionEventEntity
import com.example.data.db.NetraDatabase
import com.example.data.model.*
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MotionIntelligenceEngine processes Android sensor telemetry into validated motion states
 * (Standing, Walking, Running, Driving, Unknown) adhering to the Absolute True-Data Principle.
 */
class MotionIntelligenceEngine(
    private val context: Context,
    private val motionDao: MotionDao = NetraDatabase.getInstance(context).motionDao(),
    private val settingsRepository: SettingsRepository = SettingsRepository(context)
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Active session tracking variables
    private var activeDateKey: String = MotionTimeFormatter.formatDateKey(System.currentTimeMillis())
    
    // In-memory accumulators for today
    private var todayWalkingDurationSec: Long = 0L
    private var todayWalkingSteps: Int = 0
    private var todayWalkingDistanceM: Double = 0.0

    private var todayRunningDurationSec: Long = 0L
    private var todayRunningSteps: Int = 0
    private var todayRunningDistanceM: Double = 0.0

    private var todayDrivingDurationSec: Long = 0L
    private var todayDrivingDistanceM: Double = 0.0

    private var todayStandingDurationSec: Long = 0L

    // Current State
    private var currentCategory = MotionCategory.UNKNOWN
    private var currentConfidence = MotionConfidence.LOW
    private var currentSpeedKmH: Float = 0f
    private var currentCadence: Int = 0

    // Feature extraction rolling window
    private val accelHistory = ArrayDeque<FloatArray>(60) // ~3 sec at 50ms interval
    private var lastStepDetectorTime = 0L
    private var stepDetectorIntervals = ArrayDeque<Long>(15)
    private var lastHardwareStepCounterVal: Int? = null
    private var initialBootStepCounterVal: Int? = null

    // Driving verification window
    private var drivingCandidateStartTime = 0L
    private var lastGnssTimestamp = 0L

    // Unattended table detection
    private var continuousStationaryStartTime = System.currentTimeMillis()

    // User profile state
    private var userProfile = UserProfile()
    private var customStepTarget: Int? = null
    private var customStandingTargetSec: Long? = null

    // Available history dates (up to 7 days)
    private val _availableHistoryDates = MutableStateFlow<List<String>>(emptyList())
    val availableHistoryDates: StateFlow<List<String>> = _availableHistoryDates.asStateFlow()

    // Selected date for viewing (null means TODAY active)
    private val _selectedDateKey = MutableStateFlow<String?>(null)
    val selectedDateKey: StateFlow<String?> = _selectedDateKey.asStateFlow()

    // Main dashboard state flow
    private val _motionState = MutableStateFlow(
        DailyMotionDashboardState(
            dateKey = activeDateKey,
            displayDate = MotionTimeFormatter.formatDisplayDate(System.currentTimeMillis())
        )
    )
    val motionState: StateFlow<DailyMotionDashboardState> = _motionState.asStateFlow()

    init {
        // Observe user profile changes
        scope.launch {
            val profileFlow = combine(
                settingsRepository.userDobEpochMs,
                settingsRepository.userHeightCm,
                settingsRepository.userHeightUnit,
                settingsRepository.userGender
            ) { dob, height, unit, gender ->
                UserProfile(dob, height, unit, gender)
            }
            val targetsFlow = combine(
                settingsRepository.customStepTarget,
                settingsRepository.customStandingTargetSec
            ) { cStep, cStand ->
                Pair(cStep, cStand)
            }
            combine(profileFlow, targetsFlow) { prof, targets ->
                userProfile = prof
                customStepTarget = targets.first
                customStandingTargetSec = targets.second
                updateDashboardState()
            }.collect()
        }

        // Restore today's existing summary & refresh history dates
        scope.launch {
            restoreTodaySummary()
            refreshHistoryDates()
            motionDao.pruneOldSummaries()
            motionDao.pruneOldEvents()
        }

        // 1-second cadence timer for active state accumulation & midnight rollover
        scope.launch {
            while (isActive) {
                delay(1000L)
                tickOneSecond()
            }
        }
    }

    private suspend fun restoreTodaySummary() {
        val today = MotionTimeFormatter.formatDateKey(System.currentTimeMillis())
        activeDateKey = today
        val existing = motionDao.getDailySummary(today)
        if (existing != null) {
            todayWalkingDurationSec = existing.walkingDurationSec
            todayWalkingSteps = existing.walkingSteps
            todayWalkingDistanceM = existing.walkingDistanceMeters
            todayRunningDurationSec = existing.runningDurationSec
            todayRunningSteps = existing.runningSteps
            todayRunningDistanceM = existing.runningDistanceMeters
            todayDrivingDurationSec = existing.drivingDurationSec
            todayDrivingDistanceM = existing.drivingDistanceMeters
            todayStandingDurationSec = existing.standingDurationSec
        }
        updateDashboardState()
    }

    private suspend fun refreshHistoryDates() {
        val dates = motionDao.getAvailableHistoryDates()
        _availableHistoryDates.value = dates
    }

    fun selectDateForView(dateKey: String?) {
        _selectedDateKey.value = dateKey
        scope.launch {
            if (dateKey == null || dateKey == activeDateKey) {
                updateDashboardState()
            } else {
                loadHistoricalState(dateKey)
            }
        }
    }

    private suspend fun loadHistoricalState(dateKey: String) {
        val summary = motionDao.getDailySummary(dateKey)
        val events = motionDao.getEventsForDate(dateKey).map {
            MotionEvent(
                eventId = it.eventId,
                category = MotionCategory.values().find { cat -> cat.name == it.category } ?: MotionCategory.UNKNOWN,
                startTimeMs = it.startTimeMs,
                endTimeMs = it.endTimeMs,
                durationSec = it.durationSec,
                confidence = MotionConfidence.values().find { conf -> conf.name == it.confidence } ?: MotionConfidence.MEDIUM,
                sourceSensors = it.sourceSensors.split(","),
                distanceMeters = it.distanceMeters,
                stepCount = it.stepCount,
                timestamp = it.timestamp,
                dataQuality = it.dataQuality,
                dateKey = it.dateKey
            )
        }

        if (summary != null) {
            val totalSteps = if (summary.totalSteps > 0) summary.totalSteps else null
            val totalDist = if (summary.totalDistanceMeters > 0.0) summary.totalDistanceMeters else null
            val strideM = userProfile.calculateStrideMeters()

            val wDist = if (summary.walkingDistanceMeters > 0.0) summary.walkingDistanceMeters else if (strideM != null && summary.walkingSteps > 0) summary.walkingSteps.toDouble() * strideM else null
            val rDist = if (summary.runningDistanceMeters > 0.0) summary.runningDistanceMeters else if (strideM != null && summary.runningSteps > 0) summary.runningSteps.toDouble() * strideM * 1.25 else null
            val dDist = if (summary.drivingDistanceMeters > 0.0) summary.drivingDistanceMeters else null

            val target = ActivityTargetEngine.determineTarget(userProfile, customStepTarget, customStandingTargetSec)
            val targetProgress = ActivityTargetEngine.calculateProgress(target, totalSteps, summary.standingDurationSec)

            _motionState.value = DailyMotionDashboardState(
                dateKey = summary.dateKey,
                displayDate = MotionTimeFormatter.parseDateKeyToDisplay(summary.dateKey),
                currentMotionCategory = MotionCategory.UNKNOWN,
                currentConfidence = MotionConfidence.LOW,
                standingStats = SubActivityStats(
                    category = MotionCategory.STANDING,
                    durationSec = summary.standingDurationSec,
                    isAvailable = summary.standingDurationSec > 0
                ),
                walkingStats = SubActivityStats(
                    category = MotionCategory.WALKING,
                    durationSec = summary.walkingDurationSec,
                    steps = if (summary.walkingSteps > 0) summary.walkingSteps else null,
                    distanceMeters = wDist,
                    isAvailable = summary.walkingDurationSec > 0 || summary.walkingSteps > 0
                ),
                runningStats = SubActivityStats(
                    category = MotionCategory.RUNNING,
                    durationSec = summary.runningDurationSec,
                    steps = if (summary.runningSteps > 0) summary.runningSteps else null,
                    distanceMeters = rDist,
                    isAvailable = summary.runningDurationSec > 0 || summary.runningSteps > 0
                ),
                drivingStats = SubActivityStats(
                    category = MotionCategory.DRIVING,
                    durationSec = summary.drivingDurationSec,
                    distanceMeters = dDist,
                    isAvailable = summary.drivingDurationSec > 0
                ),
                totalActivity = TotalActivityStats(
                    totalSteps = totalSteps,
                    totalDistanceMeters = totalDist,
                    totalActiveTimeSec = summary.totalActiveTimeSec,
                    walkingSteps = if (summary.walkingSteps > 0) summary.walkingSteps else null,
                    runningSteps = if (summary.runningSteps > 0) summary.runningSteps else null,
                    drivingSteps = 0,
                    standingDurationSec = summary.standingDurationSec,
                    isAvailable = true
                ),
                targetProgress = targetProgress,
                isHistorical = true,
                availableHistoryDates = _availableHistoryDates.value,
                recentEvents = events
            )
        } else {
            _motionState.value = DailyMotionDashboardState(
                dateKey = dateKey,
                displayDate = MotionTimeFormatter.parseDateKeyToDisplay(dateKey),
                isHistorical = true,
                availableHistoryDates = _availableHistoryDates.value
            )
        }
    }

    /**
     * Ingests verified raw sensor reading through the Motion Data Pipeline.
     */
    fun processSensorReading(reading: RawSensorReading) {
        val now = reading.timestamp
        when (reading.sensorId) {
            "sensor_1", "sensor_1_accelerometer" -> { // Accelerometer
                if (reading.values.size >= 3) {
                    val x = reading.values[0]
                    val y = reading.values[1]
                    val z = reading.values[2]
                    synchronized(accelHistory) {
                        if (accelHistory.size >= 60) accelHistory.removeFirst()
                        accelHistory.addLast(floatArrayOf(x, y, z))
                    }
                    evaluateMotionClassification(now)
                }
            }
            "sensor_18" -> { // Step Detector (TYPE_STEP_DETECTOR)
                handleStepDetected(now)
            }
            "sensor_19" -> { // Step Counter (TYPE_STEP_COUNTER)
                if (reading.values.isNotEmpty()) {
                    handleHardwareStepCount(reading.values[0].toInt(), now)
                }
            }
            "gnss_location" -> { // GNSS Location & Speed
                if (reading.values.size >= 3) {
                    val speed = reading.values[2] // speed in km/h
                    currentSpeedKmH = speed
                    lastGnssTimestamp = now
                    evaluateMotionClassification(now)
                }
            }
        }
    }

    private fun handleStepDetected(timestamp: Long) {
        if (lastStepDetectorTime > 0L) {
            val delta = timestamp - lastStepDetectorTime
            if (delta in 200..2000) { // Valid human step cadence window (30 to 300 steps/min)
                stepDetectorIntervals.addLast(delta)
                if (stepDetectorIntervals.size > 10) stepDetectorIntervals.removeFirst()
                val avgDelta = stepDetectorIntervals.average()
                currentCadence = if (avgDelta > 0) (60000.0 / avgDelta).toInt() else 0
            }
        }
        lastStepDetectorTime = timestamp
        continuousStationaryStartTime = timestamp

        // Attribute step according to active classification
        val strideM = userProfile.calculateStrideMeters()
        if (currentCategory == MotionCategory.RUNNING) {
            todayRunningSteps++
            if (strideM != null) todayRunningDistanceM += (strideM * 1.25)
        } else {
            todayWalkingSteps++
            if (strideM != null) todayWalkingDistanceM += strideM
        }
        updateDashboardState()
    }

    private fun handleHardwareStepCount(rawCounter: Int, timestamp: Long) {
        if (initialBootStepCounterVal == null) {
            initialBootStepCounterVal = rawCounter
            lastHardwareStepCounterVal = rawCounter
            return
        }
        val lastVal = lastHardwareStepCounterVal ?: rawCounter
        val deltaSteps = rawCounter - lastVal
        if (deltaSteps in 1..200) { // Sanity threshold
            val strideM = userProfile.calculateStrideMeters()
            if (currentCategory == MotionCategory.RUNNING) {
                todayRunningSteps += deltaSteps
                if (strideM != null) todayRunningDistanceM += (deltaSteps.toDouble() * strideM * 1.25)
            } else {
                todayWalkingSteps += deltaSteps
                if (strideM != null) todayWalkingDistanceM += (deltaSteps.toDouble() * strideM)
            }
        }
        lastHardwareStepCounterVal = rawCounter
        continuousStationaryStartTime = timestamp
        updateDashboardState()
    }

    private fun evaluateMotionClassification(now: Long) {
        // 1. Check Driving Condition (Speed >= 20 km/h sustained for >= 15 seconds)
        val isGnssFresh = (now - lastGnssTimestamp) <= 5000L
        if (isGnssFresh && currentSpeedKmH >= 20.0f) {
            if (drivingCandidateStartTime == 0L) {
                drivingCandidateStartTime = now
            }
            val sustainedDrivingDuration = now - drivingCandidateStartTime
            if (sustainedDrivingDuration >= 15000L) { // 15 seconds sustained speed
                currentCategory = MotionCategory.DRIVING
                currentConfidence = if (sustainedDrivingDuration >= 30000L) MotionConfidence.HIGH else MotionConfidence.MEDIUM
                continuousStationaryStartTime = now
                updateDashboardState()
                return
            }
        } else {
            drivingCandidateStartTime = 0L
        }

        // 2. Feature Extraction from Accelerometer Window
        val historySnapshot = synchronized(accelHistory) { accelHistory.toList() }
        if (historySnapshot.size < 10) return

        val norms = historySnapshot.map { v -> sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) }
        val meanNorm = norms.average().toFloat()
        val variance = norms.map { (it - meanNorm) * (it - meanNorm) }.average().toFloat()

        val isStepFresh = (now - lastStepDetectorTime) <= 3000L

        // 3. Classification Decision Tree
        when {
            // Running: high variance + cadence >= 135 or variance > 4.5
            (isStepFresh && currentCadence >= 135) || variance >= 4.5f -> {
                currentCategory = MotionCategory.RUNNING
                currentConfidence = if (isStepFresh && currentCadence >= 135) MotionConfidence.HIGH else MotionConfidence.MEDIUM
                continuousStationaryStartTime = now
            }
            // Walking: moderate variance + regular step rhythm or variance in walking zone
            (isStepFresh && currentCadence in 40..134) || (variance in 0.6f..4.4f) -> {
                currentCategory = MotionCategory.WALKING
                currentConfidence = if (isStepFresh) MotionConfidence.HIGH else MotionConfidence.MEDIUM
                continuousStationaryStartTime = now
            }
            // Stationary / Standing: very low variance
            variance < 0.35f -> {
                // If phone is stationary for > 15 min continuously, classify as UNKNOWN/IDLE to avoid inflating standing time
                val stationaryDuration = now - continuousStationaryStartTime
                if (stationaryDuration < 900000L) { // Within 15 min of user activity
                    currentCategory = MotionCategory.STANDING
                    currentConfidence = MotionConfidence.MEDIUM
                } else {
                    currentCategory = MotionCategory.UNKNOWN
                    currentConfidence = MotionConfidence.LOW
                }
            }
            else -> {
                currentCategory = MotionCategory.UNKNOWN
                currentConfidence = MotionConfidence.LOW
            }
        }

        updateDashboardState()
    }

    private fun tickOneSecond() {
        val now = System.currentTimeMillis()
        val todayDateKey = MotionTimeFormatter.formatDateKey(now)

        // Check midnight rollover
        if (todayDateKey != activeDateKey) {
            handleMidnightRollover(todayDateKey)
            return
        }

        // Increment active duration based on current validated motion state
        when (currentCategory) {
            MotionCategory.STANDING -> todayStandingDurationSec++
            MotionCategory.WALKING -> todayWalkingDurationSec++
            MotionCategory.RUNNING -> todayRunningDurationSec++
            MotionCategory.DRIVING -> {
                todayDrivingDurationSec++
                if (currentSpeedKmH > 0f) {
                    val distanceThisSecM = (currentSpeedKmH * 1000.0) / 3600.0
                    todayDrivingDistanceM += distanceThisSecM
                }
            }
            MotionCategory.UNKNOWN -> {}
        }

        // Periodic checkpoint save every 30 seconds
        if (todayStandingDurationSec % 30L == 0L || todayWalkingDurationSec % 30L == 0L || todayDrivingDurationSec % 30L == 0L) {
            scope.launch {
                saveTodaySummaryCheckpoint()
            }
        }

        updateDashboardState()
    }

    private fun handleMidnightRollover(newDateKey: String) {
        scope.launch {
            // Save completed yesterday summary to DB
            saveTodaySummaryCheckpoint()

            // Reset in-memory counters for new day
            activeDateKey = newDateKey
            todayWalkingDurationSec = 0L
            todayWalkingSteps = 0
            todayWalkingDistanceM = 0.0
            todayRunningDurationSec = 0L
            todayRunningSteps = 0
            todayRunningDistanceM = 0.0
            todayDrivingDurationSec = 0L
            todayDrivingDistanceM = 0.0
            todayStandingDurationSec = 0L
            continuousStationaryStartTime = System.currentTimeMillis()

            refreshHistoryDates()
            motionDao.pruneOldSummaries()
            motionDao.pruneOldEvents()
            updateDashboardState()
        }
    }

    private suspend fun saveTodaySummaryCheckpoint() {
        val totalSteps = todayWalkingSteps + todayRunningSteps
        val totalDistance = todayWalkingDistanceM + todayRunningDistanceM + todayDrivingDistanceM
        val totalActiveSec = todayWalkingDurationSec + todayRunningDurationSec + todayDrivingDurationSec + todayStandingDurationSec

        val summary = DailyMotionSummaryEntity(
            dateKey = activeDateKey,
            totalSteps = totalSteps,
            totalDistanceMeters = totalDistance,
            totalActiveTimeSec = totalActiveSec,
            walkingDurationSec = todayWalkingDurationSec,
            walkingSteps = todayWalkingSteps,
            walkingDistanceMeters = todayWalkingDistanceM,
            runningDurationSec = todayRunningDurationSec,
            runningSteps = todayRunningSteps,
            runningDistanceMeters = todayRunningDistanceM,
            drivingDurationSec = todayDrivingDurationSec,
            drivingDistanceMeters = todayDrivingDistanceM,
            standingDurationSec = todayStandingDurationSec,
            lastUpdatedMs = System.currentTimeMillis()
        )
        motionDao.upsertDailySummary(summary)
        refreshHistoryDates()
    }

    private fun updateDashboardState() {
        // If user is currently viewing a historical date, do not overwrite with today's live ticks
        val sel = _selectedDateKey.value
        if (sel != null && sel != activeDateKey) {
            return
        }

        val totalSteps = (todayWalkingSteps + todayRunningSteps).let { if (it > 0) it else null }
        val strideM = userProfile.calculateStrideMeters()
        
        val walkingDist = if (todayWalkingDistanceM > 0.0) todayWalkingDistanceM else if (strideM != null && todayWalkingSteps > 0) todayWalkingSteps.toDouble() * strideM else null
        val runningDist = if (todayRunningDistanceM > 0.0) todayRunningDistanceM else if (strideM != null && todayRunningSteps > 0) todayRunningSteps.toDouble() * strideM * 1.25 else null
        val drivingDist = if (todayDrivingDistanceM > 0.0) todayDrivingDistanceM else null

        val totalDist = when {
            walkingDist != null || runningDist != null || drivingDist != null ->
                (walkingDist ?: 0.0) + (runningDist ?: 0.0) + (drivingDist ?: 0.0)
            else -> null
        }

        val totalActiveSec = todayWalkingDurationSec + todayRunningDurationSec + todayDrivingDurationSec + todayStandingDurationSec

        val target = ActivityTargetEngine.determineTarget(userProfile, customStepTarget, customStandingTargetSec)
        val targetProgress = ActivityTargetEngine.calculateProgress(target, totalSteps, todayStandingDurationSec)

        val newState = DailyMotionDashboardState(
            dateKey = activeDateKey,
            displayDate = MotionTimeFormatter.formatDisplayDate(System.currentTimeMillis()),
            currentMotionCategory = currentCategory,
            currentConfidence = currentConfidence,
            standingStats = SubActivityStats(
                category = MotionCategory.STANDING,
                durationSec = todayStandingDurationSec,
                isAvailable = true,
                statusDescription = if (currentCategory == MotionCategory.STANDING) "Active" else "Stationary"
            ),
            walkingStats = SubActivityStats(
                category = MotionCategory.WALKING,
                durationSec = todayWalkingDurationSec,
                steps = if (todayWalkingSteps > 0) todayWalkingSteps else null,
                distanceMeters = walkingDist,
                isAvailable = true,
                cadenceStepsPerMin = if (currentCategory == MotionCategory.WALKING && currentCadence > 0) currentCadence else null
            ),
            runningStats = SubActivityStats(
                category = MotionCategory.RUNNING,
                durationSec = todayRunningDurationSec,
                steps = if (todayRunningSteps > 0) todayRunningSteps else null,
                distanceMeters = runningDist,
                isAvailable = true,
                cadenceStepsPerMin = if (currentCategory == MotionCategory.RUNNING && currentCadence > 0) currentCadence else null
            ),
            drivingStats = SubActivityStats(
                category = MotionCategory.DRIVING,
                durationSec = todayDrivingDurationSec,
                distanceMeters = drivingDist,
                currentSpeedKmH = if (currentCategory == MotionCategory.DRIVING) currentSpeedKmH else null,
                isAvailable = true
            ),
            totalActivity = TotalActivityStats(
                totalSteps = totalSteps,
                totalDistanceMeters = totalDist,
                totalActiveTimeSec = totalActiveSec,
                walkingSteps = if (todayWalkingSteps > 0) todayWalkingSteps else null,
                runningSteps = if (todayRunningSteps > 0) todayRunningSteps else null,
                drivingSteps = 0,
                standingDurationSec = todayStandingDurationSec,
                isAvailable = true
            ),
            targetProgress = targetProgress,
            isHistorical = false,
            availableHistoryDates = _availableHistoryDates.value,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        _motionState.value = newState
    }
}
