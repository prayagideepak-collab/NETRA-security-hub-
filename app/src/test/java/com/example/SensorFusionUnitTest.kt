package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.RuleBasedSafetyEngine
import com.example.data.model.DataClassification
import com.example.data.model.RawSensorReading
import com.example.data.model.SafetyRiskLevel
import com.example.data.model.SensorCategory
import com.example.data.sensor.SensorFusionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SensorFusionUnitTest {

    @Test
    fun `test sensor fusion nominal telemetry yields safe state and no voice trigger`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SensorFusionEngine(context)

        val reading = RawSensorReading(
            sensorId = "battery_telemetry",
            name = "Battery",
            category = SensorCategory.POWER,
            values = floatArrayOf(25.0f, 80f, 3800f),
            unit = "%",
            extraDetails = mapOf("plugType" to "Discharging"),
            timestamp = System.currentTimeMillis()
        )

        val fusionState = engine.updateReading(reading)
        assertFalse(fusionState.isHighHeatConfirmed)
        assertFalse(fusionState.isImpactConfirmed)
        assertFalse(fusionState.isChargingRiskConfirmed)
        assertFalse(fusionState.isMagneticHazardConfirmed)
        assertEquals(0, fusionState.activeEventsCount)

        runBlocking {
            val analysis = RuleBasedSafetyEngine.evaluateSafety(fusionState)
            assertEquals(SafetyRiskLevel.SAFE, analysis.riskLevel)
            assertTrue(analysis.riskLevel != SafetyRiskLevel.EMERGENCY && analysis.riskLevel != SafetyRiskLevel.WARNING)
        }
    }

    @Test
    fun `test sensor fusion critical impact and high heat triggers emergency and voice alert`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SensorFusionEngine(context)

        val accelReading = RawSensorReading(
            sensorId = "sensor_1",
            name = "Accelerometer",
            category = SensorCategory.MOTION,
            values = floatArrayOf(20.0f, 15.0f, 10.0f),
            unit = "m/s²",
            timestamp = System.currentTimeMillis()
        )
        val gyroReading = RawSensorReading(
            sensorId = "sensor_4",
            name = "Gyroscope",
            category = SensorCategory.MOTION,
            values = floatArrayOf(2.5f, 2.0f, 1.0f),
            unit = "rad/s",
            timestamp = System.currentTimeMillis()
        )
        val batteryReading = RawSensorReading(
            sensorId = "battery_telemetry",
            name = "Battery",
            category = SensorCategory.POWER,
            values = floatArrayOf(46.0f, 90f, 4500f),
            unit = "%",
            extraDetails = mapOf("plugType" to "AC"),
            timestamp = System.currentTimeMillis()
        )

        engine.updateReading(accelReading)
        engine.updateReading(gyroReading)
        val fusionState = engine.updateReading(batteryReading)

        assertTrue(fusionState.isImpactConfirmed)
        assertTrue(fusionState.isHighHeatConfirmed)

        runBlocking {
            val analysis = RuleBasedSafetyEngine.evaluateSafety(fusionState)
            assertTrue(analysis.riskLevel == SafetyRiskLevel.EMERGENCY || analysis.riskLevel == SafetyRiskLevel.WARNING)
            assertTrue(analysis.riskScore >= 50)
        }
    }

    @Test
    fun `test magnetic hazard anomaly triggers alert condition`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SensorFusionEngine(context)

        val magReading = RawSensorReading(
            sensorId = "sensor_2",
            name = "Magnetometer",
            category = SensorCategory.ENVIRONMENTAL,
            values = floatArrayOf(80f, 80f, 0f),
            unit = "µT",
            timestamp = System.currentTimeMillis()
        )

        // 1. Initial detection should be transient and not confirmed (suppressed)
        var fusionState = engine.updateReading(magReading)
        assertFalse(fusionState.isMagneticHazardConfirmed)

        // 2. Sending a reading 5.5 seconds later should trigger the persistent anomaly
        val futureReading = magReading.copy(timestamp = magReading.timestamp + 5500L)
        fusionState = engine.updateReading(futureReading)
        assertTrue(fusionState.isMagneticHazardConfirmed)

        runBlocking {
            val analysis = RuleBasedSafetyEngine.evaluateSafety(fusionState)
            assertTrue(analysis.riskScore > 0)
        }
    }

    @Test
    fun `test custom thermal alert threshold triggers based on user selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SensorFusionEngine(context)

        // Nominal battery reading of 38.0°C
        val batteryReading = RawSensorReading(
            sensorId = "battery_telemetry",
            name = "Battery",
            category = SensorCategory.POWER,
            values = floatArrayOf(38.0f, 90f, 4000f),
            unit = "%",
            timestamp = System.currentTimeMillis()
        )

        // 1. With default threshold of 45°C, 38°C is not high heat
        engine.thermalThresholdC = 45
        var fusionState = engine.updateReading(batteryReading)
        assertFalse(fusionState.isHighHeatConfirmed)

        // 2. Change user selected threshold to 35°C. Now 38°C is high heat!
        engine.thermalThresholdC = 35
        fusionState = engine.updateReading(batteryReading)
        assertTrue(fusionState.isHighHeatConfirmed)
    }

    @Test
    fun `test external heat protection engine and thermal fusion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = SensorFusionEngine(context)
        
        val batteryReading = RawSensorReading(
            sensorId = "battery_telemetry",
            name = "Battery",
            category = SensorCategory.POWER,
            values = floatArrayOf(46.0f, 90f, 4000f),
            unit = "%",
            extraDetails = mapOf("plugType" to "Discharging"),
            timestamp = System.currentTimeMillis()
        )
        val lightReading = RawSensorReading(
            sensorId = "sensor_5",
            name = "Light",
            category = SensorCategory.ENVIRONMENTAL,
            values = floatArrayOf(12000f),
            unit = "Lux",
            timestamp = System.currentTimeMillis()
        )
        val thermalReading = RawSensorReading(
            sensorId = "thermal_subsystem",
            name = "Thermal Status",
            category = SensorCategory.ENVIRONMENTAL,
            values = floatArrayOf(3f),
            unit = "level",
            timestamp = System.currentTimeMillis()
        )

        engine.updateReading(lightReading)
        engine.updateReading(thermalReading)
        var fusionState = engine.updateReading(batteryReading)

        assertTrue(fusionState.isHighHeatConfirmed)
        assertTrue(fusionState.heatConfidence >= 0.65f)
        assertEquals(46.0f, fusionState.batteryTempC)
        assertEquals(12000f, fusionState.ambientLightLux)
        assertFalse(fusionState.isCharging)

        // Make it charging
        val batteryReadingCharging = batteryReading.copy(extraDetails = mapOf("plugType" to "AC"))
        fusionState = engine.updateReading(batteryReadingCharging)
        
        assertTrue(fusionState.isCharging)
    }

    @Test
    fun `test privacy scanner state structure and initialization`() {
        val state = com.example.data.model.PrivacyScannerState(
            isEnabled = true,
            isScanning = false,
            bluetoothCount = 3,
            wifiCount = 5,
            riskScore = 40,
            riskLevel = "Medium Risk"
        )
        assertTrue(state.isEnabled)
        assertFalse(state.isScanning)
        assertEquals(3, state.bluetoothCount)
        assertEquals(5, state.wifiCount)
        assertEquals(40, state.riskScore)
        assertEquals("Medium Risk", state.riskLevel)
    }
}
