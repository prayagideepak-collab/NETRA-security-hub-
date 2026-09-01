package com.aistudio.netrasensorhub

import com.aistudio.netrasensorhub.data.risk.RiskAnalysisEngine
import com.aistudio.netrasensorhub.data.risk.RiskLevel
import com.aistudio.netrasensorhub.data.risk.SensorTelemetryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAnalysisEngineTest {
    private val engine = RiskAnalysisEngine()

    @Test
    fun testOverheatingNormal() {
        val state = SensorTelemetryState(
            batteryTempC = 30.0f,
            isCharging = false,
            ambientLightLux = 200f
        )
        val result = engine.evaluateOverheatingRisk(state)
        assertTrue(result.isDataAvailable)
        assertEquals(RiskLevel.NORMAL, result.riskLevel)
    }

    @Test
    fun testOverheatingCritical() {
        val state = SensorTelemetryState(
            batteryTempC = 46.0f,
            isCharging = true,
            ambientLightLux = 12000f
        )
        val result = engine.evaluateOverheatingRisk(state)
        assertTrue(result.isDataAvailable)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
    }

    @Test
    fun testOverheatingDataUnavailable() {
        val state = SensorTelemetryState(
            batteryTempC = null
        )
        val result = engine.evaluateOverheatingRisk(state)
        assertFalse(result.isDataAvailable)
        assertEquals("Device Overheating Under Load", result.scenarioName)
    }

    @Test
    fun testSuddenImpactCritical() {
        val state = SensorTelemetryState(
            accelX = 30.0f,
            accelY = 25.0f,
            accelZ = 40.0f,
            gyroX = 20f,
            gyroY = 15f,
            gyroZ = 18f
        )
        val result = engine.evaluateSuddenImpactRisk(state)
        assertTrue(result.isDataAvailable)
        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
    }

    @Test
    fun testSuddenImpactNormal() {
        val state = SensorTelemetryState(
            accelX = 0f,
            accelY = 0f,
            accelZ = 9.81f,
            gyroX = 0f,
            gyroY = 0f,
            gyroZ = 0f
        )
        val result = engine.evaluateSuddenImpactRisk(state)
        assertTrue(result.isDataAvailable)
        assertEquals(RiskLevel.NORMAL, result.riskLevel)
    }

    @Test
    fun testSuddenImpactDataUnavailable() {
        val state = SensorTelemetryState(
            accelX = null,
            accelY = null,
            accelZ = null
        )
        val result = engine.evaluateSuddenImpactRisk(state)
        assertFalse(result.isDataAvailable)
    }
}
