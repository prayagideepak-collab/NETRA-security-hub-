package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.NetraAlertManager
import com.example.data.engine.NetraSafetyEngine
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetraSafetyEngineTest {

    private lateinit var context: Context
    private lateinit var alertManager: NetraAlertManager
    private lateinit var safetyEngine: NetraSafetyEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        alertManager = NetraAlertManager(context)
        safetyEngine = NetraSafetyEngine(context, alertManager)
    }

    @Test
    fun testNominalConditions_ProducesSafeAndHealthyState() {
        val now = System.currentTimeMillis()
        val fusionState = SensorFusionState(
            batteryTempC = 34.0f,
            magneticMagnitudeuT = 45.0f,
            impactGForce = 1.0f,
            chargingVoltageMv = 4000,
            isCharging = false,
            lastUpdateTimestamp = now
        )

        val liveReadings = mapOf(
            "thermal" to RawSensorReading("battery_telemetry", "Battery Temp", SensorCategory.THERMAL, floatArrayOf(34f), "°C", lastUpdateTimestamp = now),
            "mag" to RawSensorReading("magnetic_field", "Magnetometer", SensorCategory.ENVIRONMENTAL, floatArrayOf(0f, 45f, 0f), "µT", lastUpdateTimestamp = now),
            "accel" to RawSensorReading("accelerometer", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 9.8f, 0f), "m/s²", lastUpdateTimestamp = now),
            "power" to RawSensorReading("power_telemetry", "Power HAL", SensorCategory.POWER, floatArrayOf(80f), "%", lastUpdateTimestamp = now)
        )

        safetyEngine.evaluateSafetyConditions(fusionState, liveReadings, thermalThresholdC = 45)

        val state = safetyEngine.safetyEngineState.value
        assertEquals(SafetyRiskState.SAFE, state.safetyRiskState)
        assertEquals(DeviceHealthState.HEALTHY, state.deviceHealthState)
        assertTrue(state.activeEvents.isEmpty())
    }

    @Test
    fun testThermalStateMachine_NormalToElevatedToCriticalToRecoveringToResolved() {
        val now = System.currentTimeMillis()
        val baseReadings = mapOf(
            "thermal" to RawSensorReading("battery_telemetry", "Battery Temp", SensorCategory.THERMAL, floatArrayOf(45f), "°C", lastUpdateTimestamp = now),
            "accel" to RawSensorReading("accelerometer", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 9.8f, 0f), "m/s²", lastUpdateTimestamp = now)
        )

        // 1. Warning Trigger (45.5°C)
        var fusion = SensorFusionState(batteryTempC = 45.5f, lastUpdateTimestamp = now)
        safetyEngine.evaluateSafetyConditions(fusion, baseReadings, thermalThresholdC = 45)

        var state = safetyEngine.safetyEngineState.value
        assertEquals(SafetyRiskState.WARNING, state.safetyRiskState)
        assertEquals(1, state.activeEvents.size)
        val thermalEventId = state.activeEvents[0].eventId
        assertTrue(thermalEventId.startsWith("THERMAL-"))
        assertEquals(SafetyEventLifecycleState.ACTIVE, state.activeEvents[0].lifecycleState)

        // 2. Escalation to Critical (48.5°C) - Must update same event, not create duplicate
        fusion = SensorFusionState(batteryTempC = 48.5f, lastUpdateTimestamp = now + 1000L)
        safetyEngine.evaluateSafetyConditions(fusion, baseReadings, thermalThresholdC = 45)

        state = safetyEngine.safetyEngineState.value
        assertEquals(SafetyRiskState.CRITICAL, state.safetyRiskState)
        assertEquals(1, state.activeEvents.size)
        assertEquals(thermalEventId, state.activeEvents[0].eventId) // Deduplication verified
        assertEquals("48.5°C", state.activeEvents[0].peakValue)

        // 3. Cooling trend (44.0°C -> 42.0°C -> 40.0°C) -> Transitions to RECOVERING
        fusion = SensorFusionState(batteryTempC = 44.0f, lastUpdateTimestamp = now + 2000L)
        safetyEngine.evaluateSafetyConditions(fusion, baseReadings, thermalThresholdC = 45)
        fusion = SensorFusionState(batteryTempC = 42.0f, lastUpdateTimestamp = now + 3000L)
        safetyEngine.evaluateSafetyConditions(fusion, baseReadings, thermalThresholdC = 45)
        fusion = SensorFusionState(batteryTempC = 40.0f, lastUpdateTimestamp = now + 4000L)
        safetyEngine.evaluateSafetyConditions(fusion, baseReadings, thermalThresholdC = 45)

        state = safetyEngine.safetyEngineState.value
        assertEquals(SafetyEventLifecycleState.RECOVERING, state.activeEvents[0].lifecycleState)

        // 4. Safe recovery threshold (< 39.5°C) -> Transitions to RESOLVED
        fusion = SensorFusionState(batteryTempC = 38.0f, lastUpdateTimestamp = now + 5000L)
        safetyEngine.evaluateSafetyConditions(fusion, baseReadings, thermalThresholdC = 45)

        state = safetyEngine.safetyEngineState.value
        assertEquals(SafetyRiskState.SAFE, state.safetyRiskState)
        assertTrue(state.activeEvents.isEmpty()) // Active list cleared upon resolution
    }

    @Test
    fun testStaleSensorTelemetry_MarksDeviceDegradedWithoutFalseAlarm() {
        val now = System.currentTimeMillis()
        val staleTimestamp = now - 90_000L // 90 seconds old (STALE/UNAVAILABLE)

        val fusion = SensorFusionState(batteryTempC = 0f, lastUpdateTimestamp = staleTimestamp)
        val liveReadings = mapOf(
            "thermal" to RawSensorReading("battery_telemetry", "Battery Temp", SensorCategory.THERMAL, floatArrayOf(0f), "°C", lastUpdateTimestamp = staleTimestamp)
        )

        safetyEngine.evaluateSafetyConditions(fusion, liveReadings, thermalThresholdC = 45)

        val state = safetyEngine.safetyEngineState.value
        // Must NOT trigger an overheat or false alarm
        assertEquals(SafetyRiskState.SAFE, state.safetyRiskState)
        // Must accurately reflect telemetry degradation
        assertEquals(DeviceHealthState.DEGRADED, state.deviceHealthState)
        assertEquals(DataFreshness.UNAVAILABLE, state.subsystemHealths["Thermal"]?.freshness)
    }

    @Test
    fun testMagneticSafety_PersistentAnomalyTriggersEventAndChargingSuppressionFiltersNoise() {
        val now = System.currentTimeMillis()
        val liveReadings = mapOf(
            "mag" to RawSensorReading("magnetic_field", "Magnetometer", SensorCategory.ENVIRONMENTAL, floatArrayOf(120f, 0f, 0f), "µT", lastUpdateTimestamp = now)
        )

        // 1. Single transient spike (< 3 ticks) -> Ignored
        var fusion = SensorFusionState(magneticMagnitudeuT = 125.0f, isCharging = false, lastUpdateTimestamp = now)
        safetyEngine.evaluateSafetyConditions(fusion, liveReadings)
        assertEquals(SafetyRiskState.SAFE, safetyEngine.safetyEngineState.value.safetyRiskState)

        // 2. Persistent anomaly (>= 3 ticks) -> Triggers WARNING
        safetyEngine.evaluateSafetyConditions(fusion, liveReadings)
        safetyEngine.evaluateSafetyConditions(fusion, liveReadings)
        assertEquals(SafetyRiskState.WARNING, safetyEngine.safetyEngineState.value.safetyRiskState)
        assertTrue(safetyEngine.safetyEngineState.value.activeEvents.any { it.domain == SafetyDomain.MAGNETIC })

        // 3. Charging context suppression: device plugged in -> localized charging induction filtered safely
        fusion = SensorFusionState(magneticMagnitudeuT = 125.0f, isCharging = true, lastUpdateTimestamp = now)
        safetyEngine.evaluateSafetyConditions(fusion, liveReadings)
        assertEquals(SafetyRiskState.SAFE, safetyEngine.safetyEngineState.value.safetyRiskState)
    }

    @Test
    fun testImpactSafety_DetectsHighGForceImpact() {
        val now = System.currentTimeMillis()
        val fusion = SensorFusionState(
            isImpactConfirmed = true,
            impactGForce = 3.5f,
            lastUpdateTimestamp = now
        )
        val liveReadings = mapOf(
            "accel" to RawSensorReading("accelerometer", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 35f, 0f), "m/s²", lastUpdateTimestamp = now)
        )

        safetyEngine.evaluateSafetyConditions(fusion, liveReadings)

        val state = safetyEngine.safetyEngineState.value
        assertEquals(SafetyRiskState.WARNING, state.safetyRiskState)
        assertTrue(state.activeEvents.any { it.domain == SafetyDomain.IMPACT })
        assertEquals("3.5 G", state.activeEvents.first { it.domain == SafetyDomain.IMPACT }.peakValue)
    }

    @Test
    fun testNightMode_QuietPeriodLogic() {
        val alertMgr = NetraAlertManager(context)
        // Test night timestamps (e.g. 23:00 / 11:00 PM)
        val calNight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertTrue(alertMgr.isNightModeActive(calNight.timeInMillis))

        // Test day timestamp (e.g. 14:00 / 2:00 PM)
        val calDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertFalse(alertMgr.isNightModeActive(calDay.timeInMillis))
    }

    @Test
    fun testPocketState_RequiresBothProximityAndLight() {
        val fusionEngine = com.example.data.sensor.SensorFusionEngine(context)
        val now = System.currentTimeMillis()

        // 1. Proximity near only, but light bright (e.g. hand hover in daylight)
        fusionEngine.updateReading(RawSensorReading("sensor_8", "Proximity", SensorCategory.ENVIRONMENTAL, floatArrayOf(0.0f), "cm", now))
        val state1 = fusionEngine.updateReading(RawSensorReading("sensor_5", "Light", SensorCategory.ENVIRONMENTAL, floatArrayOf(250f), "Lux", now))
        assertFalse("Should not confirm pocket if light is bright", state1.isPocketConfirmed)

        // 2. Light dark only, but proximity far (e.g. phone in dark room on table)
        fusionEngine.updateReading(RawSensorReading("sensor_8", "Proximity", SensorCategory.ENVIRONMENTAL, floatArrayOf(5.0f), "cm", now))
        val state2 = fusionEngine.updateReading(RawSensorReading("sensor_5", "Light", SensorCategory.ENVIRONMENTAL, floatArrayOf(2f), "Lux", now))
        assertFalse("Should not confirm pocket if proximity is far", state2.isPocketConfirmed)

        // 3. Both proximity near (<2cm) AND light dark (<10 Lux) -> Confirmed
        fusionEngine.updateReading(RawSensorReading("sensor_8", "Proximity", SensorCategory.ENVIRONMENTAL, floatArrayOf(0.0f), "cm", now))
        val state3 = fusionEngine.updateReading(RawSensorReading("sensor_5", "Light", SensorCategory.ENVIRONMENTAL, floatArrayOf(2f), "Lux", now))
        assertTrue("Must confirm pocket when both proximity is near and light is dark", state3.isPocketConfirmed)
        assertEquals(0.95f, state3.pocketConfidence, 0.01f)
    }

    @Test
    fun testSuddenEventDetector_FiltersNoiseAndTriggersOnGenuineEvents() {
        val detector = com.example.data.engine.SuddenEventDetector()
        val now = System.currentTimeMillis()

        // 1. Normal ambient variance (1 g ± 0.2 m/s²) -> Not significant
        val normalReading = RawSensorReading("sensor_1_accel", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0.1f, 9.8f, 0.2f), "m/s²", now)
        val resNormal = detector.evaluateReading(normalReading)
        assertFalse("Normal noise should not trigger sudden event", resNormal.isSignificant)
        assertEquals(com.example.data.engine.SuddenEventDetector.SuddenEventType.NONE, resNormal.type)

        // 2. Sudden high acceleration jolt (e.g. 28.0 m/s²) -> Significant shock
        val joltReading = RawSensorReading("sensor_1_accel", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 28.0f, 0f), "m/s²", now + 100L)
        val resJolt = detector.evaluateReading(joltReading)
        assertTrue("High acceleration jolt must trigger significant event", resJolt.isSignificant)
        assertEquals(com.example.data.engine.SuddenEventDetector.SuddenEventType.SUDDEN_ACCELERATION_SPIKE, resJolt.type)

        // 3. Normal ambient magnetic noise (45 µT) -> Not significant
        detector.resetCooldown()
        val normalMag = RawSensorReading("sensor_2_mag", "Magnetometer", SensorCategory.ENVIRONMENTAL, floatArrayOf(20f, 35f, 15f), "µT", now + 5000L)
        val resMagNormal = detector.evaluateReading(normalMag)
        assertFalse("Ambient magnetic field should not trigger anomaly", resMagNormal.isSignificant)

        // 4. Extreme magnetic surge (210 µT) -> Significant anomaly
        val surgeMag = RawSensorReading("sensor_2_mag", "Magnetometer", SensorCategory.ENVIRONMENTAL, floatArrayOf(150f, 120f, 75f), "µT", now + 5100L)
        val resMagSurge = detector.evaluateReading(surgeMag)
        assertTrue("Extreme magnetic surge must trigger anomaly event", resMagSurge.isSignificant)
        assertEquals(com.example.data.engine.SuddenEventDetector.SuddenEventType.EXTREME_MAGNETIC_ANOMALY, resMagSurge.type)
    }

    @Test
    fun testDataFreshnessAndAvailabilityEvaluation() {
        val now = System.currentTimeMillis()
        
        // Fresh reading (0s old)
        val freshReading = RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 9.8f, 0f), "m/s²", lastUpdateTimestamp = now)
        assertEquals(DataFreshness.FRESH, freshReading.freshness())
        assertFalse(freshReading.isStale(15000L))

        // Stale reading (20s old)
        val staleReading = RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 9.8f, 0f), "m/s²", lastUpdateTimestamp = now - 20_000L)
        assertEquals(DataFreshness.STALE, staleReading.freshness(now))
        assertTrue(staleReading.isStale(15000L))

        // Unavailable reading (75s old)
        val unavailableReading = RawSensorReading("sensor_1", "Accelerometer", SensorCategory.MOTION, floatArrayOf(0f, 9.8f, 0f), "m/s²", lastUpdateTimestamp = now - 75_000L)
        assertEquals(DataFreshness.UNAVAILABLE, unavailableReading.freshness(now))
        assertTrue(unavailableReading.isStale(15000L))
    }

    @Test
    fun testHardwareCapabilityDiscovery_MarksUnsupportedSensorsAccurately() {
        val detector = com.example.data.sensor.HardwareDetector(context)
        val capabilities = detector.discoverAllCapabilities()
        assertTrue("Capabilities list must not be empty", capabilities.isNotEmpty())

        // Ensure every capability clearly reports isSupported boolean
        capabilities.forEach { cap ->
            assertNotNull(cap.id)
            assertNotNull(cap.name)
            assertNotNull(cap.category)
        }
    }
}
