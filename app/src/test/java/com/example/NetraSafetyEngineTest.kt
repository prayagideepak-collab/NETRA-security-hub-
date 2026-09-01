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
}
