package com.example.data.engine

import android.content.Context
import com.example.data.db.*
import com.example.data.model.*
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MotionIntelligenceEngine processes Android sensor telemetry into validated motion states
 * (Standing, Walking, Running, Driving, Unknown) adhering to the Absolute True-Data Principle.
 *
 * ABSOLUTE RULES:
 * 1. True Data Only: Never invent, simulate, or hardcode steps, distance, coordinates, speed, or duration.
 * 2. No Continuous GPS: Location acquisition is event-based (Motion Start, Significant Route/Speed Drop, Motion End).
 * 3. Driving requires sustained qualifying evidence (>=20 km/h for >=15s). Temporary slowdowns below 20 km/h do not instantly terminate Driving.
 * 4. Weather/Rain context is completely eliminated.
 * 5. Sensor identities are explicitly mapped via SensorTypeConstants.
 */
class MotionIntelligenceEngine(
    private val context: Context,
    private val motionDao: MotionDao = NetraDatabase.getInstance(context).motionDao(),
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    val locationSnapshotProvider: MotionLocationSnapshotProvider = MotionLocationSnapshotProvider(context)
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

    // Feature extraction rolling window (~1.5s at 50ms interval)
    private val accelHistory = ArrayDeque<FloatArray>(30)
    private var lastStepDetectorTime = 0L
    private var stepDetectorIntervals = ArrayDeque<Long>(15)
    private var lastHardwareStepCounterVal: Int? = null
    private var initialBootStepCounterVal: Int? = null

    // Driving verification & exit window
    private var drivingCandidateStartTime = 0L
    private var lastGnssTimestamp = 0L
    private var drivingExitCandidateStartTime = 0L

    // State transition hysteresis
    private var pendingCategory: MotionCategory = MotionCategory.UNKNOWN
    private var candidateConsecutiveCount: Int = 0

    // Unattended table detection (stationary without user activity)
    private var continuousStationaryStartTime = System.currentTimeMillis()

    // Route Snapshot & Session State
    private var activeSessionId: String? = null
    private var activeSessionCategory: MotionCategory? = null
    private var activeSessionStartTimeMs: Long = 0L
    private var activeSessionStartLocation: LocationSnapshot? = null
    private val activeSessionEvents = mutableListOf<RouteEventRecord>()
    private val todayRouteSessions = mutableListOf<MotionRouteSession>()

    // Speed-Drop & Orientation Telemetry Fusion
    private var confirmedPreviousSpeedKmH: Float = 0f
    private var lastIntermediateSnapshotTimeMs: Long = 0L
    private var currentHeadingDeg: Float? = null
    private var previousHeadingDeg: Float? = null

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

        // Restore today's existing summary & refresh history dates & enforce 7-day retention
        scope.launch {
            restoreTodaySummary()
            refreshHistoryDates()
            pruneHistoryRetention()
        }

        // 1-second cadence timer for active state accumulation & midnight rollover
        scope.launch {
            while (isActive) {
                delay(1000L)
                tickOneSecond()
            }
        }
    }

    private suspend fun pruneHistoryRetention() {
        motionDao.pruneOldSummaries()
        motionDao.pruneOldEvents()
        motionDao.pruneOldRouteSessions()
        motionDao.pruneOldRouteEvents()
        motionDao.pruneOrphanRouteEvents()
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

        // Load today's route sessions from DB
        val sessionEntities = motionDao.getRouteSessionsForDate(today)
        todayRouteSessions.clear()
        for (se in sessionEntities) {
            val events = motionDao.getRouteEventsForSession(se.sessionId).map { it.toModel() }
            todayRouteSessions.add(se.toModel(events))
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

        val sessionEntities = motionDao.getRouteSessionsForDate(dateKey)
        val routeSessions = sessionEntities.map { se ->
            val rEvents = motionDao.getRouteEventsForSession(se.sessionId).map { it.toModel() }
            se.toModel(rEvents)
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
                recentEvents = events,
                routeSessions = routeSessions,
                activeRouteSession = null
            )
        } else {
            _motionState.value = DailyMotionDashboardState(
                dateKey = dateKey,
                displayDate = MotionTimeFormatter.parseDateKeyToDisplay(dateKey),
                isHistorical = true,
                availableHistoryDates = _availableHistoryDates.value,
                routeSessions = routeSessions,
                activeRouteSession = null
            )
        }
    }

    /**
     * Ingests verified raw sensor reading through the Motion Data Pipeline.
     * Explicitly maps sensor types via SensorTypeConstants.
     */
    fun processSensorReading(reading: RawSensorReading) {
        val now = reading.timestamp
        val sensorType = SensorTypeConstants.normalizeSensorType(reading.sensorId, reading.name)

        when (sensorType) {
            SensorTypeConstants.ACCELEROMETER -> {
                if (reading.values.size >= 3) {
                    val x = reading.values[0]
                    val y = reading.values[1]
                    val z = reading.values[2]
                    synchronized(accelHistory) {
                        if (accelHistory.size >= 30) accelHistory.removeFirst()
                        accelHistory.addLast(floatArrayOf(x, y, z))
                    }
                    evaluateMotionClassification(now)
                }
            }
            SensorTypeConstants.STEP_DETECTOR -> {
                handleStepDetected(now)
            }
            SensorTypeConstants.STEP_COUNTER -> {
                if (reading.values.isNotEmpty()) {
                    handleHardwareStepCount(reading.values[0].toInt(), now)
                }
            }
            SensorTypeConstants.ORIENTATION, SensorTypeConstants.ROTATION_VECTOR -> {
                if (reading.values.isNotEmpty()) {
                    val heading = reading.values[0]
                    if (heading in 0.0f..360.0f) {
                        previousHeadingDeg = currentHeadingDeg
                        currentHeadingDeg = heading
                    }
                }
            }
            SensorTypeConstants.MAGNETIC_FIELD, SensorTypeConstants.GYROSCOPE -> {
                // Multi-axis field readings; do NOT invent or treat raw magnetic flux as heading directly
            }
            SensorTypeConstants.LOCATION -> {
                if (reading.values.size >= 3) {
                    val lat = reading.values[0].toDouble()
                    val lng = reading.values[1].toDouble()
                    val speed = reading.values[2] // speed in km/h
                    val accuracy = if (reading.values.size >= 4) reading.values[3] else null

                    currentSpeedKmH = speed
                    lastGnssTimestamp = now
                    evaluateMotionClassification(now)
                    evaluateSpeedDropEvent(speed, lat, lng, accuracy, now)
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
        if (currentCategory == MotionCategory.RUNNING) {
            todayRunningSteps++
        } else {
            todayWalkingSteps++
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
            if (currentCategory == MotionCategory.RUNNING) {
                todayRunningSteps += deltaSteps
            } else {
                todayWalkingSteps += deltaSteps
            }
        }
        lastHardwareStepCounterVal = rawCounter
        continuousStationaryStartTime = timestamp
        updateDashboardState()
    }

    private fun evaluateMotionClassification(now: Long) {
        val isGnssFresh = (now - lastGnssTimestamp) <= 5000L

        // 1. Evaluate Driving State Machine Entry & Maintenance
        if (isGnssFresh && currentSpeedKmH >= 20.0f) {
            if (drivingCandidateStartTime == 0L) {
                drivingCandidateStartTime = now
            }
            val sustainedDrivingDuration = now - drivingCandidateStartTime
            if (sustainedDrivingDuration >= 15000L) { // 15 seconds sustained qualifying speed
                drivingExitCandidateStartTime = 0L
                applyCategoryTransition(
                    MotionCategory.DRIVING,
                    if (sustainedDrivingDuration >= 30000L) MotionConfidence.HIGH else MotionConfidence.MEDIUM,
                    now
                )
                continuousStationaryStartTime = now
                updateDashboardState()
                return
            }
        } else {
            drivingCandidateStartTime = 0L
        }

        // If already in Driving, handle temporary slowdowns gracefully (e.g. traffic lights, turns)
        if (currentCategory == MotionCategory.DRIVING) {
            val isStepActive = (now - lastStepDetectorTime) <= 3000L
            if (isStepActive && currentCadence >= 50) {
                // Genuine walking or running detected -> exit driving
                drivingExitCandidateStartTime = 0L
            } else if (currentSpeedKmH < 5.0f && !isGnssFresh) {
                // Prolonged stoppage without movement
                if (drivingExitCandidateStartTime == 0L) {
                    drivingExitCandidateStartTime = now
                }
                if (now - drivingExitCandidateStartTime >= 60000L) { // 1 minute stationary -> exit driving
                    applyCategoryTransition(MotionCategory.UNKNOWN, MotionConfidence.LOW, now)
                    return
                }
                return // Remain in Driving during temporary traffic stop
            } else {
                // Temporary slowdown below 20 km/h while still moving (traffic/turns) -> keep Driving
                return
            }
        }

        // 2. Feature Extraction from Accelerometer Window
        val historySnapshot = synchronized(accelHistory) { accelHistory.toList() }
        if (historySnapshot.size < 10) return

        val norms = historySnapshot.map { v -> sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) }
        val meanNorm = norms.average().toFloat()
        val variance = norms.map { (it - meanNorm) * (it - meanNorm) }.average().toFloat()

        val isStepFresh = (now - lastStepDetectorTime) <= 3000L

        // 3. Classification Candidate Evaluation
        val candidateCategory: MotionCategory
        val candidateConfidence: MotionConfidence

        when {
            // Running: high variance or high cadence
            (isStepFresh && currentCadence >= 135) || variance >= 4.5f -> {
                candidateCategory = MotionCategory.RUNNING
                candidateConfidence = if (isStepFresh && currentCadence >= 135) MotionConfidence.HIGH else MotionConfidence.MEDIUM
            }
            // Walking: moderate variance or cadence in human walking range
            (isStepFresh && currentCadence in 40..134) || (variance in 0.35f..4.4f) -> {
                candidateCategory = MotionCategory.WALKING
                candidateConfidence = if (isStepFresh) MotionConfidence.HIGH else MotionConfidence.MEDIUM
            }
            // Stationary: very low variance
            variance < 0.35f -> {
                candidateCategory = MotionCategory.UNKNOWN
                candidateConfidence = MotionConfidence.LOW
            }
            else -> {
                candidateCategory = MotionCategory.UNKNOWN
                candidateConfidence = MotionConfidence.LOW
            }
        }

        // Hysteresis & Stability Confirmation to prevent noise flutter
        if (candidateCategory == currentCategory) {
            candidateConsecutiveCount = 0
            currentConfidence = candidateConfidence
        } else {
            if (candidateCategory == pendingCategory) {
                candidateConsecutiveCount++
                val requiredCount = when (candidateCategory) {
                    MotionCategory.RUNNING -> 5
                    MotionCategory.WALKING -> 3
                    else -> 2
                }
                if (candidateConsecutiveCount >= requiredCount) {
                    applyCategoryTransition(candidateCategory, candidateConfidence, now)
                    candidateConsecutiveCount = 0
                    if (candidateCategory != MotionCategory.STANDING) {
                        continuousStationaryStartTime = now
                    }
                }
            } else {
                pendingCategory = candidateCategory
                candidateConsecutiveCount = 1
            }
        }

        updateDashboardState()
    }

    private fun applyCategoryTransition(newCategory: MotionCategory, confidence: MotionConfidence, now: Long) {
        val oldCategory = currentCategory
        currentCategory = newCategory
        currentConfidence = confidence

        if (oldCategory != newCategory) {
            // Manage Route Session Lifecycles on transition
            val wasMoving = oldCategory in listOf(MotionCategory.WALKING, MotionCategory.RUNNING, MotionCategory.DRIVING)
            val isNowMoving = newCategory in listOf(MotionCategory.WALKING, MotionCategory.RUNNING, MotionCategory.DRIVING)

            if (wasMoving && (!isNowMoving || oldCategory != newCategory)) {
                finalizeActiveRouteSession(oldCategory, now)
            }
            if (isNowMoving && (!wasMoving || oldCategory != newCategory)) {
                startNewRouteSession(newCategory, now)
            }
        }
    }

    private fun startNewRouteSession(category: MotionCategory, startTimeMs: Long) {
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        activeSessionCategory = category
        activeSessionStartTimeMs = startTimeMs
        activeSessionStartLocation = null
        activeSessionEvents.clear()
        confirmedPreviousSpeedKmH = currentSpeedKmH
        lastIntermediateSnapshotTimeMs = startTimeMs

        // Single-shot on-demand location fix for start point (Battery saving policy)
        scope.launch {
            val snapshot = locationSnapshotProvider.requestSingleLocationFix(isStartingPoint = true)
            if (snapshot != null && activeSessionId == sessionId) {
                activeSessionStartLocation = snapshot
                updateDashboardState()
            }
        }
    }

    private fun finalizeActiveRouteSession(category: MotionCategory, endTimeMs: Long) {
        val sessionId = activeSessionId ?: return
        val startLoc = activeSessionStartLocation
        val events = activeSessionEvents.toList()
        val sTime = activeSessionStartTimeMs

        // Clear active session pointers immediately
        activeSessionId = null
        activeSessionCategory = null
        activeSessionEvents.clear()

        // Single-shot on-demand location fix for end point (Battery saving policy)
        scope.launch {
            val endSnapshot = locationSnapshotProvider.requestSingleLocationFix(isEndingPoint = true)
            
            // Calculate cumulative route distance if intermediate events exist, or straight-line distance
            val hasValidIntermediateEvents = events.any { locationSnapshotProvider.isValidCoordinate(it.latitude, it.longitude) }
            val dist = if (hasValidIntermediateEvents) {
                locationSnapshotProvider.calculateCumulativeRouteDistanceMeters(startLoc, events, endSnapshot)
            } else {
                locationSnapshotProvider.calculateGeographicDistanceMeters(startLoc, endSnapshot)
            }

            val session = MotionRouteSession(
                sessionId = sessionId,
                dateKey = activeDateKey,
                activityCategory = category,
                startTimeMs = sTime,
                endTimeMs = endTimeMs,
                startLocation = startLoc,
                endLocation = endSnapshot,
                snapshotDistanceMeters = dist,
                isCumulativeDistance = hasValidIntermediateEvents,
                locationAccuracyMeters = endSnapshot?.accuracyMeters ?: startLoc?.accuracyMeters,
                intermediateEvents = events
            )

            when (category) {
                MotionCategory.WALKING -> todayWalkingDistanceM += (dist ?: 0.0)
                MotionCategory.RUNNING -> todayRunningDistanceM += (dist ?: 0.0)
                MotionCategory.DRIVING -> todayDrivingDistanceM += (dist ?: 0.0)
                else -> {}
            }

            todayRouteSessions.add(0, session)
            motionDao.upsertRouteSession(session.toEntity())
            updateDashboardState()
        }
    }

    private fun evaluateSpeedDropEvent(
        speed: Float,
        lat: Double,
        lng: Double,
        accuracy: Float?,
        now: Long
    ) {
        val prevSpeed = confirmedPreviousSpeedKmH
        if (speed > prevSpeed || prevSpeed < 15.0f) {
            confirmedPreviousSpeedKmH = speed
            return
        }

        val speedDelta = prevSpeed - speed
        // Meaningful speed drop qualification: >=5 km/h delta or >=20% drop on >=20 km/h speed
        val isSignificantDrop = speedDelta >= 5.0f || (prevSpeed >= 20.0f && (speedDelta / prevSpeed) >= 0.20f)
        val hasMinTimeSeparation = (now - lastIntermediateSnapshotTimeMs) >= 20000L

        if (isSignificantDrop && hasMinTimeSeparation && locationSnapshotProvider.isValidCoordinate(lat, lng)) {
            lastIntermediateSnapshotTimeMs = now
            confirmedPreviousSpeedKmH = speed

            val curHead = currentHeadingDeg
            val prevHead = previousHeadingDeg
            val headingDiff = if (curHead != null && prevHead != null) {
                abs(curHead - prevHead).let { if (it > 180f) 360f - it else it }
            } else null

            val classification: RouteEventClassification
            val confidence: MotionConfidence

            when {
                speed <= 2.0f -> {
                    classification = RouteEventClassification.STOP_PAUSE
                    confidence = MotionConfidence.HIGH
                }
                headingDiff != null && headingDiff >= 35.0f && speed >= 5.0f -> {
                    classification = RouteEventClassification.TURN
                    confidence = if (headingDiff >= 50.0f) MotionConfidence.HIGH else MotionConfidence.MEDIUM
                }
                headingDiff != null && headingDiff < 20.0f && speedDelta in 5.0f..18.0f -> {
                    classification = RouteEventClassification.POSSIBLE_SPEED_BREAKER
                    confidence = MotionConfidence.MEDIUM
                }
                headingDiff != null && headingDiff < 20.0f && speedDelta > 18.0f -> {
                    classification = RouteEventClassification.SPEED_REDUCTION
                    confidence = MotionConfidence.HIGH
                }
                else -> {
                    classification = RouteEventClassification.SIGNIFICANT_SPEED_DROP
                    confidence = MotionConfidence.MEDIUM
                }
            }

            val sId = activeSessionId ?: "session_${activeDateKey}_${now}"
            val record = RouteEventRecord(
                eventId = UUID.randomUUID().toString(),
                sessionId = sId,
                timestamp = now,
                latitude = lat,
                longitude = lng,
                accuracyMeters = accuracy,
                previousSpeedKmH = prevSpeed,
                currentSpeedKmH = speed,
                speedDeltaKmH = speedDelta,
                headingBeforeDeg = prevHead,
                headingAfterDeg = curHead,
                motionType = currentCategory,
                classification = classification,
                confidence = confidence
            )

            activeSessionEvents.add(record)
            scope.launch {
                motionDao.insertRouteEvent(record.toEntity(activeDateKey))
                updateDashboardState()
            }
        }
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

            todayRouteSessions.clear()
            activeSessionId = null
            activeSessionEvents.clear()

            refreshHistoryDates()
            pruneHistoryRetention()
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
        
        val walkingDist = if (todayWalkingDistanceM > 0.0) todayWalkingDistanceM else null
        val runningDist = if (todayRunningDistanceM > 0.0) todayRunningDistanceM else null
        val drivingDist = if (todayDrivingDistanceM > 0.0) todayDrivingDistanceM else null

        val totalDist = when {
            walkingDist != null || runningDist != null || drivingDist != null ->
                (walkingDist ?: 0.0) + (runningDist ?: 0.0) + (drivingDist ?: 0.0)
            else -> null
        }

        val totalActiveSec = todayWalkingDurationSec + todayRunningDurationSec + todayDrivingDurationSec + todayStandingDurationSec

        val target = ActivityTargetEngine.determineTarget(userProfile, customStepTarget, customStandingTargetSec)
        val targetProgress = ActivityTargetEngine.calculateProgress(target, totalSteps, todayStandingDurationSec)

        val activeSession = if (activeSessionId != null && activeSessionCategory != null) {
            MotionRouteSession(
                sessionId = activeSessionId!!,
                dateKey = activeDateKey,
                activityCategory = activeSessionCategory!!,
                startTimeMs = activeSessionStartTimeMs,
                endTimeMs = null,
                startLocation = activeSessionStartLocation,
                endLocation = null,
                snapshotDistanceMeters = null,
                isCumulativeDistance = false,
                locationAccuracyMeters = activeSessionStartLocation?.accuracyMeters,
                intermediateEvents = activeSessionEvents.toList()
            )
        } else null

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
            routeSessions = todayRouteSessions.toList(),
            activeRouteSession = activeSession,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )

        _motionState.value = newState
    }

    /**
     * Idempotently synchronizes imported Android activity/step data.
     * Prevents duplicate records using deterministic event IDs.
     */
    suspend fun syncDeviceActivityData(
        dateKey: String,
        category: MotionCategory,
        importedSteps: Int?,
        importedDistanceM: Double?,
        importedDurationSec: Long?,
        sourceSensor: String = "android_activity_recognition"
    ) {
        val deterministicEventId = "sync_${dateKey}_${category.name}"
        
        // If syncing for active today, update in-memory state if greater
        if (dateKey == activeDateKey) {
            when (category) {
                MotionCategory.WALKING -> {
                    if (importedSteps != null && importedSteps > todayWalkingSteps) todayWalkingSteps = importedSteps
                    if (importedDistanceM != null && importedDistanceM > todayWalkingDistanceM) todayWalkingDistanceM = importedDistanceM
                    if (importedDurationSec != null && importedDurationSec > todayWalkingDurationSec) todayWalkingDurationSec = importedDurationSec
                }
                MotionCategory.RUNNING -> {
                    if (importedSteps != null && importedSteps > todayRunningSteps) todayRunningSteps = importedSteps
                    if (importedDistanceM != null && importedDistanceM > todayRunningDistanceM) todayRunningDistanceM = importedDistanceM
                    if (importedDurationSec != null && importedDurationSec > todayRunningDurationSec) todayRunningDurationSec = importedDurationSec
                }
                MotionCategory.DRIVING -> {
                    if (importedDistanceM != null && importedDistanceM > todayDrivingDistanceM) todayDrivingDistanceM = importedDistanceM
                    if (importedDurationSec != null && importedDurationSec > todayDrivingDurationSec) todayDrivingDurationSec = importedDurationSec
                }
                MotionCategory.STANDING -> {
                    if (importedDurationSec != null && importedDurationSec > todayStandingDurationSec) todayStandingDurationSec = importedDurationSec
                }
                MotionCategory.UNKNOWN -> {}
            }
            saveTodaySummaryCheckpoint()
            updateDashboardState()
        } else {
            // Updating historical summary
            val existing = motionDao.getDailySummary(dateKey)
            val wSteps = if (category == MotionCategory.WALKING) (importedSteps ?: existing?.walkingSteps ?: 0) else (existing?.walkingSteps ?: 0)
            val rSteps = if (category == MotionCategory.RUNNING) (importedSteps ?: existing?.runningSteps ?: 0) else (existing?.runningSteps ?: 0)
            val wDist = if (category == MotionCategory.WALKING) (importedDistanceM ?: existing?.walkingDistanceMeters ?: 0.0) else (existing?.walkingDistanceMeters ?: 0.0)
            val rDist = if (category == MotionCategory.RUNNING) (importedDistanceM ?: existing?.runningDistanceMeters ?: 0.0) else (existing?.runningDistanceMeters ?: 0.0)
            val dDist = if (category == MotionCategory.DRIVING) (importedDistanceM ?: existing?.drivingDistanceMeters ?: 0.0) else (existing?.drivingDistanceMeters ?: 0.0)
            val wDur = if (category == MotionCategory.WALKING) (importedDurationSec ?: existing?.walkingDurationSec ?: 0L) else (existing?.walkingDurationSec ?: 0L)
            val rDur = if (category == MotionCategory.RUNNING) (importedDurationSec ?: existing?.runningDurationSec ?: 0L) else (existing?.runningDurationSec ?: 0L)
            val dDur = if (category == MotionCategory.DRIVING) (importedDurationSec ?: existing?.drivingDurationSec ?: 0L) else (existing?.drivingDurationSec ?: 0L)
            val sDur = if (category == MotionCategory.STANDING) (importedDurationSec ?: existing?.standingDurationSec ?: 0L) else (existing?.standingDurationSec ?: 0L)

            val updated = DailyMotionSummaryEntity(
                dateKey = dateKey,
                totalSteps = wSteps + rSteps,
                totalDistanceMeters = wDist + rDist + dDist,
                totalActiveTimeSec = wDur + rDur + dDur + sDur,
                walkingDurationSec = wDur,
                walkingSteps = wSteps,
                walkingDistanceMeters = wDist,
                runningDurationSec = rDur,
                runningSteps = rSteps,
                runningDistanceMeters = rDist,
                drivingDurationSec = dDur,
                drivingDistanceMeters = dDist,
                standingDurationSec = sDur,
                lastUpdatedMs = System.currentTimeMillis()
            )
            motionDao.upsertDailySummary(updated)
            refreshHistoryDates()
        }

        // Insert idempotent event record
        val ev = MotionEventEntity(
            eventId = deterministicEventId,
            category = category.name,
            startTimeMs = System.currentTimeMillis() - ((importedDurationSec ?: 0L) * 1000L),
            endTimeMs = System.currentTimeMillis(),
            durationSec = importedDurationSec ?: 0L,
            confidence = MotionConfidence.HIGH.name,
            sourceSensors = sourceSensor,
            distanceMeters = importedDistanceM,
            stepCount = importedSteps,
            timestamp = System.currentTimeMillis(),
            dataQuality = "VERIFIED_IMPORTED",
            dateKey = dateKey
        )
        motionDao.insertMotionEvent(ev)
    }
}

// Mapper extension functions
fun MotionRouteSessionEntity.toModel(events: List<RouteEventRecord>): MotionRouteSession {
    val startLoc = if (startLatitude != null && startLongitude != null) {
        LocationSnapshot(
            latitude = startLatitude,
            longitude = startLongitude,
            accuracyMeters = startAccuracyMeters,
            timestamp = startTimestamp ?: startTimeMs,
            isStartingPoint = true
        )
    } else null

    val endLoc = if (endLatitude != null && endLongitude != null) {
        LocationSnapshot(
            latitude = endLatitude,
            longitude = endLongitude,
            accuracyMeters = endAccuracyMeters,
            timestamp = endTimestamp ?: (endTimeMs ?: startTimeMs),
            isEndingPoint = true
        )
    } else null

    return MotionRouteSession(
        sessionId = sessionId,
        dateKey = dateKey,
        activityCategory = MotionCategory.values().find { it.name == activityCategory } ?: MotionCategory.UNKNOWN,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        startLocation = startLoc,
        endLocation = endLoc,
        snapshotDistanceMeters = snapshotDistanceMeters,
        isCumulativeDistance = isCumulativeDistance,
        locationAccuracyMeters = endAccuracyMeters ?: startAccuracyMeters,
        intermediateEvents = events
    )
}

fun MotionRouteSession.toEntity(): MotionRouteSessionEntity {
    return MotionRouteSessionEntity(
        sessionId = sessionId,
        dateKey = dateKey,
        activityCategory = activityCategory.name,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        startLatitude = startLocation?.latitude,
        startLongitude = startLocation?.longitude,
        startAccuracyMeters = startLocation?.accuracyMeters,
        startTimestamp = startLocation?.timestamp,
        endLatitude = endLocation?.latitude,
        endLongitude = endLocation?.longitude,
        endAccuracyMeters = endLocation?.accuracyMeters,
        endTimestamp = endLocation?.timestamp,
        snapshotDistanceMeters = snapshotDistanceMeters,
        isCumulativeDistance = isCumulativeDistance,
        lastUpdatedMs = System.currentTimeMillis()
    )
}

fun RouteEventEntity.toModel(): RouteEventRecord {
    return RouteEventRecord(
        eventId = eventId,
        sessionId = sessionId,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        previousSpeedKmH = previousSpeedKmH,
        currentSpeedKmH = currentSpeedKmH,
        speedDeltaKmH = speedDeltaKmH,
        headingBeforeDeg = headingBeforeDeg,
        headingAfterDeg = headingAfterDeg,
        motionType = MotionCategory.values().find { it.name == motionType } ?: MotionCategory.UNKNOWN,
        classification = RouteEventClassification.values().find { it.name == classification } ?: RouteEventClassification.UNKNOWN_ROUTE_EVENT,
        confidence = MotionConfidence.values().find { it.name == confidence } ?: MotionConfidence.LOW
    )
}

fun RouteEventRecord.toEntity(dateKey: String): RouteEventEntity {
    return RouteEventEntity(
        eventId = eventId,
        sessionId = sessionId,
        dateKey = dateKey,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        previousSpeedKmH = previousSpeedKmH,
        currentSpeedKmH = currentSpeedKmH,
        speedDeltaKmH = speedDeltaKmH,
        headingBeforeDeg = headingBeforeDeg,
        headingAfterDeg = headingAfterDeg,
        motionType = motionType.name,
        classification = classification.name,
        confidence = confidence.name
    )
}
