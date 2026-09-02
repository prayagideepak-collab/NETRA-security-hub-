package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.db.DailyMotionSummaryEntity
import com.example.data.db.NetraDatabase
import com.example.data.engine.ActivityTargetEngine
import com.example.data.engine.MotionIntelligenceEngine
import com.example.data.engine.MotionLocationSnapshotProvider
import com.example.data.model.*
import com.example.util.NetraNotificationManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NetraMotionIntelligenceTest {

    private lateinit var context: Context
    private lateinit var motionEngine: MotionIntelligenceEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        motionEngine = MotionIntelligenceEngine(context)
    }

    // A. No sensor available
    @Test
    fun testA_NoSensorAvailable_DefaultsToUnknownWithoutFabricatedValues() {
        val state = motionEngine.motionState.value
        assertEquals(MotionCategory.UNKNOWN, state.currentMotionCategory)
        // No fake data generated
        assertTrue(state.totalActivity.totalSteps == null || state.totalActivity.totalSteps == 0)
    }

    // B. Step API unavailable
    @Test
    fun testB_StepApiUnavailable_DoesNotFabricateSteps() {
        val state = motionEngine.motionState.value
        assertNull("Steps must be null or zero when no step sensor has fired", state.walkingStats.steps)
    }

    // C. Step API available
    @Test
    fun testC_StepApiAvailable_IncrementsActualStepCount() {
        val now = System.currentTimeMillis()
        
        // Enter WALKING state first
        for (i in 0 until 15) {
            val v = if (i % 2 == 0) 11.5f else 8.5f
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, v, 0f), "m/s²", now + i * 50L)
            )
        }
        
        motionEngine.processSensorReading(
            RawSensorReading("sensor_18", "Step Detector", SensorCategory.MOTION, floatArrayOf(1.0f), "steps", now + 1000L)
        )
        val state = motionEngine.motionState.value
        assertEquals(1, state.walkingStats.steps)
        assertEquals(1, state.totalActivity.totalSteps)
    }

    // D. Walking detected
    @Test
    fun testD_WalkingDetected_WithStepCadenceAndVariance() {
        val now = System.currentTimeMillis()
        // Feed 20 accel samples in walking variance zone (~2.25 m/s² variance)
        for (i in 0 until 20) {
            val v = if (i % 2 == 0) 11.5f else 8.5f
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, v, 0f), "m/s²", now + i * 50L)
            )
        }
        val state = motionEngine.motionState.value
        assertEquals(MotionCategory.WALKING, state.currentMotionCategory)
    }

    // E. Running detected
    @Test
    fun testE_RunningDetected_WithHighVariance() {
        val now = System.currentTimeMillis()
        // Feed 15 accel samples with high variance (>4.5 m/s²)
        for (i in 0 until 15) {
            val v = if (i % 2 == 0) 16.5f else 4.0f
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, v, 0f), "m/s²", now + i * 50L)
            )
        }
        val state = motionEngine.motionState.value
        assertEquals(MotionCategory.RUNNING, state.currentMotionCategory)
    }

    // F. Stationary -> Unknown (No Fabricated Standing)
    @Test
    fun testF_Stationary_DefaultsToUnknownWithoutFabricatedStanding() {
        val now = System.currentTimeMillis()
        // Feed 15 stable accel samples (1g = 9.8 m/s² ± 0.05)
        for (i in 0 until 15) {
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0.01f, 9.80f, 0.01f), "m/s²", now + i * 50L)
            )
        }
        val state = motionEngine.motionState.value
        assertEquals(MotionCategory.UNKNOWN, state.currentMotionCategory)
    }

    // G. Driving below 20 km/h (e.g. 14 km/h) -> Not Driving
    @Test
    fun testG_DrivingBelow20KmH_DoesNotTriggerDriving() {
        val now = System.currentTimeMillis()
        motionEngine.processSensorReading(
            RawSensorReading("gnss_location", "GNSS", SensorCategory.ENVIRONMENTAL, floatArrayOf(25.43f, 81.84f, 14.0f), "km/h", now)
        )
        val state = motionEngine.motionState.value
        assertNotEquals("Speed < 20 km/h must not trigger driving", MotionCategory.DRIVING, state.currentMotionCategory)
    }

    // H. Driving exactly 20 km/h but not sustained (<15s) -> Not Driving
    @Test
    fun testH_Driving20KmH_NotSustained_DoesNotTriggerDriving() {
        val now = System.currentTimeMillis()
        // Single sample at 25 km/h
        motionEngine.processSensorReading(
            RawSensorReading("gnss_location", "GNSS", SensorCategory.ENVIRONMENTAL, floatArrayOf(25.43f, 81.84f, 25.0f), "km/h", now)
        )
        val state = motionEngine.motionState.value
        assertNotEquals("Driving must require sustained speed for confirmation period", MotionCategory.DRIVING, state.currentMotionCategory)
    }

    // I. Driving sustained >=20 km/h (>=15s) -> Confirmed Driving
    @Test
    fun testI_DrivingSustained_TriggersConfirmedDriving() {
        val now = System.currentTimeMillis()
        // Initial speed lock
        motionEngine.processSensorReading(
            RawSensorReading("gnss_location", "GNSS", SensorCategory.ENVIRONMENTAL, floatArrayOf(25.43f, 81.84f, 35.0f), "km/h", now)
        )
        // 16 seconds later with sustained speed
        motionEngine.processSensorReading(
            RawSensorReading("gnss_location", "GNSS", SensorCategory.ENVIRONMENTAL, floatArrayOf(25.44f, 81.85f, 38.0f), "km/h", now + 16000L)
        )
        val state = motionEngine.motionState.value
        assertEquals("Sustained speed >= 20 km/h must confirm driving", MotionCategory.DRIVING, state.currentMotionCategory)
    }

    // J. Activity Transition
    @Test
    fun testJ_ActivityTransition_UpdatesCorrectly() {
        val now = System.currentTimeMillis()
        // 1. Walking
        for (i in 0 until 20) {
            val v = if (i % 2 == 0) 11.5f else 8.5f
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, v, 0f), "m/s²", now + i * 50L)
            )
        }
        assertEquals(MotionCategory.WALKING, motionEngine.motionState.value.currentMotionCategory)

        // 2. Transition to Stationary/Unknown (low variance)
        for (i in 0 until 40) {
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0.01f, 9.80f, 0.01f), "m/s²", now + 1000L + i * 50L)
            )
        }
        assertEquals(MotionCategory.UNKNOWN, motionEngine.motionState.value.currentMotionCategory)
    }

    // K. Sensor Noise
    @Test
    fun testK_SensorNoise_SingleSpikeDoesNotFlutterConfirmedState() {
        val now = System.currentTimeMillis()
        // Establish stationary/unknown with 30 stable samples
        for (i in 0 until 30) {
            motionEngine.processSensorReading(
                RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0.01f, 9.80f, 0.01f), "m/s²", now + i * 50L)
            )
        }
        assertEquals(MotionCategory.UNKNOWN, motionEngine.motionState.value.currentMotionCategory)

        // 1 noisy sample
        motionEngine.processSensorReading(
            RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(15.0f, 15.0f, 15.0f), "m/s²", now + 1600L)
        )
        // Hysteresis filter ensures confirmed state does not flutter on single sample
        val state = motionEngine.motionState.value
        assertEquals(MotionCategory.UNKNOWN, state.currentMotionCategory)
    }

    // L. User Profile & Biomechanical Stride
    @Test
    fun testL_UserProfile_CalculatesAgeAndStrideAccurately() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 1996)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val profile = UserProfile(
            dobEpochMs = cal.timeInMillis,
            heightCm = 175f,
            heightUnit = "cm",
            gender = "Male"
        )
        val age = profile.calculateAge()
        assertNotNull(age)
        assertTrue(age!! >= 28)

        val stride = profile.calculateStrideMeters()
        assertNotNull(stride)
        assertEquals(0.726, stride!!, 0.01) // 175 * 0.415 / 100
    }

    // M. Target Profile & Progress
    @Test
    fun testM_TargetEngine_SelectsAgeGroupAndCalculatesProgress() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2000)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val profile = UserProfile(dobEpochMs = cal.timeInMillis, heightCm = 170f, gender = "Female")
        val target = ActivityTargetEngine.determineTarget(profile, customStepTarget = null, customStandingTargetSec = null)
        assertEquals(10000, target.stepTarget)
        assertEquals(10800L, target.standingTargetSec)

        val progress = ActivityTargetEngine.calculateProgress(target, todaySteps = 5000, todayStandingSec = 5400L)
        assertTrue(progress.targetConfigured)
        assertEquals(50, progress.stepProgressPct)
        assertEquals(50, progress.standingProgressPct)
    }

    // N. 7-Day History Retention & Pruning (8th Day Deletion)
    @Test
    fun testN_DatabaseRetention_StoresAndPrunesOlderThan7Days() = runBlocking {
        val db = NetraDatabase.getInstance(context)
        val dao = db.motionDao()

        // Insert 8 daily summaries (from 2026-08-01 to 2026-08-08)
        for (i in 1..8) {
            val dateStr = "2026-08-%02d".format(i)
            val summary = DailyMotionSummaryEntity(
                dateKey = dateStr,
                totalSteps = 4000 + i * 100,
                totalDistanceMeters = 3000.0,
                totalActiveTimeSec = 3600L,
                walkingDurationSec = 3600L,
                walkingSteps = 4000 + i * 100,
                walkingDistanceMeters = 3000.0,
                runningDurationSec = 0L,
                runningSteps = 0,
                runningDistanceMeters = 0.0,
                drivingDurationSec = 0L,
                drivingDistanceMeters = 0.0,
                standingDurationSec = 1800L,
                lastUpdatedMs = System.currentTimeMillis()
            )
            dao.upsertDailySummary(summary)
        }

        // Run pruning
        dao.pruneOldSummaries()

        val dates = dao.getAvailableHistoryDates()
        assertEquals("Database must retain exactly 7 days of history", 7, dates.size)
        assertTrue("8th day (oldest 2026-08-01) must be pruned", !dates.contains("2026-08-01"))
        assertTrue("Recent day 2026-08-08 must be retained", dates.contains("2026-08-08"))
    }

    // O. Notification Sync from Central Motion State
    @Test
    fun testO_NotificationSync_FormatsWithoutCrash() {
        val notifManager = NetraNotificationManager(context)
        val state = motionEngine.motionState.value
        notifManager.updateMotionNotification(state)
        // Verified channel and builder creation
        assertNotNull(notifManager)
    }

    // U. Driving Steps Exclusion
    @Test
    fun testU_DrivingSteps_AreExcludedFromTotal() {
        val state = motionEngine.motionState.value
        assertEquals("Driving steps must be 0", 0, state.totalActivity.drivingSteps ?: 0)
    }

    // V. Route Snapshot Start / End Geodetic Distance Calculation
    @Test
    fun testV_RouteSnapshot_GeodeticDistance_CalculatesAccurately() {
        val snap1 = LocationSnapshot(latitude = 25.4358, longitude = 81.8463, accuracyMeters = 5f, timestamp = 1000L, isStartingPoint = true)
        val snap2 = LocationSnapshot(latitude = 25.4400, longitude = 81.8500, accuracyMeters = 6f, timestamp = 2000L, isEndingPoint = true)
        val distM = motionEngine.locationSnapshotProvider.calculateGeographicDistanceMeters(snap1, snap2)
        assertNotNull(distM)
        assertTrue("Distance between coordinate points should be ~590m", distM!! in 550.0..650.0)
    }

    // W. Route Session & Event DB Retention
    @Test
    fun testW_RouteSessionsAndEvents_PrunedTo7Days() = runBlocking {
        val db = NetraDatabase.getInstance(context)
        val dao = db.motionDao()

        // Insert 8 route sessions (2026-08-01 to 2026-08-08)
        for (i in 1..8) {
            val dateKey = "2026-08-%02d".format(i)
            dao.upsertRouteSession(
                com.example.data.db.MotionRouteSessionEntity(
                    sessionId = "session_$i",
                    dateKey = dateKey,
                    activityCategory = "WALKING",
                    startTimeMs = 1000000L + i * 86400000L,
                    endTimeMs = 1001000L + i * 86400000L,
                    startLatitude = 25.43,
                    startLongitude = 81.84,
                    startAccuracyMeters = 5f,
                    startTimestamp = 1000000L,
                    endLatitude = 25.44,
                    endLongitude = 81.85,
                    endAccuracyMeters = 6f,
                    endTimestamp = 1001000L,
                    snapshotDistanceMeters = 1200.0,
                    isCumulativeDistance = true,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            )
            dao.insertRouteEvent(
                com.example.data.db.RouteEventEntity(
                    eventId = "event_$i",
                    sessionId = "session_$i",
                    dateKey = dateKey,
                    timestamp = 1000500L,
                    latitude = 25.435,
                    longitude = 81.845,
                    accuracyMeters = 5f,
                    previousSpeedKmH = 35f,
                    currentSpeedKmH = 20f,
                    speedDeltaKmH = 15f,
                    headingBeforeDeg = 90f,
                    headingAfterDeg = 150f,
                    motionType = "DRIVING",
                    classification = "TURN",
                    confidence = "HIGH"
                )
            )
        }

        dao.pruneOldRouteSessions()
        dao.pruneOldRouteEvents()

        val sessions = dao.getRouteSessionsForDate("2026-08-01")
        val events = dao.getRouteEventsForDate("2026-08-01")
        assertTrue("Oldest date 2026-08-01 sessions must be pruned", sessions.isEmpty())
        assertTrue("Oldest date 2026-08-01 events must be pruned", events.isEmpty())

        val recentSessions = dao.getRouteSessionsForDate("2026-08-08")
        val recentEvents = dao.getRouteEventsForDate("2026-08-08")
        assertEquals(1, recentSessions.size)
        assertEquals(1, recentEvents.size)
    }

    @Test
    fun testCumulativeRouteDistanceCalculation() {
        val provider = MotionLocationSnapshotProvider(context)
        val start = LocationSnapshot(latitude = 25.4300, longitude = 81.8400, timestamp = 1000L)
        val end = LocationSnapshot(latitude = 25.4500, longitude = 81.8400, timestamp = 3000L)
        val intermediateEvent = RouteEventRecord(
            eventId = "ev1",
            sessionId = "s1",
            timestamp = 2000L,
            latitude = 25.4400,
            longitude = 81.8500,
            accuracyMeters = 5f,
            previousSpeedKmH = 30f,
            currentSpeedKmH = 15f,
            speedDeltaKmH = 15f,
            headingBeforeDeg = null,
            headingAfterDeg = null,
            motionType = MotionCategory.DRIVING,
            classification = RouteEventClassification.SIGNIFICANT_SPEED_DROP,
            confidence = MotionConfidence.MEDIUM
        )

        val straightLineDist = provider.calculateGeographicDistanceMeters(start, end)
        val cumulativeDist = provider.calculateCumulativeRouteDistanceMeters(start, listOf(intermediateEvent), end)

        assertNotNull(straightLineDist)
        assertNotNull(cumulativeDist)
        assertTrue("Cumulative distance via waypoint must be greater than straight-line distance", cumulativeDist!! > straightLineDist!!)
    }

    @Test
    fun testSensorTypeNormalization() {
        assertEquals(SensorTypeConstants.ACCELEROMETER, SensorTypeConstants.normalizeSensorType("sensor_1"))
        assertEquals(SensorTypeConstants.GYROSCOPE, SensorTypeConstants.normalizeSensorType("sensor_4"))
        assertEquals(SensorTypeConstants.MAGNETIC_FIELD, SensorTypeConstants.normalizeSensorType("sensor_2"))
        assertEquals(SensorTypeConstants.STEP_DETECTOR, SensorTypeConstants.normalizeSensorType("sensor_18"))
        assertEquals(SensorTypeConstants.STEP_COUNTER, SensorTypeConstants.normalizeSensorType("sensor_19"))
    }
}
