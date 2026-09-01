package com.example.data.sensor

import android.content.Context
import com.example.data.model.SensorFusionState
import com.example.data.model.RawSensorReading
import kotlin.math.sqrt

class SensorFusionEngine(private val context: Context) {

    // Internal active reading cache
    private var lastProximityDistance: Float? = null
    private var lastLightLux: Float? = null
    private var lastAccelNorm: Float = 9.81f
    private var lastGyroNorm: Float = 0f
    private var lastBatteryTempC: Float = 0f
    private var lastBatteryLevelPct: Int = 100
    private var lastBatteryVoltageMv: Int = 0
    private var lastBatteryPlugged: Boolean = false
    private var lastMagneticuT: Float = 0f
    private var magneticStartTime = 0L
    private var lastThermalStatusLevel: Float = 0f
    private var lastAmbientTempC: Float = 25f
    private var lastReadingTimestamp = 0L

    var thermalThresholdC: Int = 45
    
    fun setAmbientTemperature(temp: Float) {
        lastAmbientTempC = temp
    }

    fun updateReading(reading: RawSensorReading): SensorFusionState {
        lastReadingTimestamp = reading.timestamp

        val id = reading.sensorId
        when {
            id == "sensor_8" || id.startsWith("sensor_8_") -> { // TYPE_PROXIMITY
                if (reading.values.isNotEmpty()) {
                    lastProximityDistance = reading.values[0]
                }
            }
            id == "sensor_5" || id.startsWith("sensor_5_") -> { // TYPE_LIGHT
                if (reading.values.isNotEmpty()) {
                    lastLightLux = reading.values[0]
                }
            }
            id == "sensor_1" || id.startsWith("sensor_1_") -> { // TYPE_ACCELEROMETER
                if (reading.values.size >= 3) {
                    val x = reading.values[0]
                    val y = reading.values[1]
                    val z = reading.values[2]
                    lastAccelNorm = sqrt(x * x + y * y + z * z)
                }
            }
            id == "sensor_4" || id.startsWith("sensor_4_") -> { // TYPE_GYROSCOPE
                if (reading.values.size >= 3) {
                    val gx = reading.values[0]
                    val gy = reading.values[1]
                    val gz = reading.values[2]
                    lastGyroNorm = sqrt(gx * gx + gy * gy + gz * gz)
                }
            }
            id == "sensor_2" || id.startsWith("sensor_2_") -> { // TYPE_MAGNETIC_FIELD
                if (reading.values.size >= 3) {
                    val mx = reading.values[0]
                    val my = reading.values[1]
                    val mz = reading.values[2]
                    val mag = sqrt(mx * mx + my * my + mz * mz)
                    lastMagneticuT = mag

                    val now = reading.timestamp
                    if (mag >= 100.0f) {
                        if (magneticStartTime == 0L) {
                            magneticStartTime = now
                        }
                    } else {
                        magneticStartTime = 0L
                    }

                    if (lastBatteryPlugged && mag >= 100.0f) {
                        com.example.util.LoggingManager.info(
                            "Magnetic Subsystem",
                            "MAGNETIC_CHARGING_EXCEPTION",
                            "Ignored During Charging",
                            "Strength: ${"%.1f".format(mag)} µT. Device is charging. Notifications and announcements suppressed."
                        )
                    }
                }
            }
            id == "battery_telemetry" -> {
                if (reading.values.size >= 3) {
                    val temp = reading.values[0]
                    lastBatteryTempC = temp
                    lastBatteryLevelPct = reading.values[1].toInt()
                    lastBatteryVoltageMv = reading.values[2].toInt()
                    lastBatteryPlugged = reading.extraDetails["plugType"]?.contains("Discharging") == false
                }
            }
            id == "thermal_subsystem" -> {
                if (reading.values.isNotEmpty()) {
                    lastThermalStatusLevel = reading.values[0]
                }
            }
        }

        return evaluateFusionState()
    }

    private fun evaluateFusionState(): SensorFusionState {
        // 1. Pocket Detection Logic (Proximity < 2cm AND Light < 10 Lux)
        val isProxNear = (lastProximityDistance != null && lastProximityDistance!! < 2f)
        val isDark = (lastLightLux != null && lastLightLux!! < 10f)
        val isPocketConfirmed = isProxNear && isDark
        val pocketConfidence = when {
            isProxNear && isDark -> 0.95f
            isProxNear || isDark -> 0.50f
            else -> 0.0f
        }

        // 2. Temperature Monitoring & High Heat Risk Logic
        val weatherTemp = lastAmbientTempC
        val deviceTemp = lastBatteryTempC
        val tempDiff = deviceTemp - weatherTemp

        val isRedZone = deviceTemp > 40.0f || weatherTemp >= 40.0f || deviceTemp < 25.0f
        val isNormalRange = (deviceTemp in 35.0f..40.0f) || (weatherTemp in 35.0f..40.0f)
        val isHeatHigh = (isRedZone && !isNormalRange) || (deviceTemp >= thermalThresholdC)
        
        val heatConfidence = when {
            isHeatHigh -> 0.98f
            else -> 0.0f
        }

        // 3. Impact / Fall Detection Logic (Accel spike > 22.0 m/s² AND Gyro > 3.0 rad/s)
        val isImpactConfirmed = lastAccelNorm > 22.0f && lastGyroNorm > 3.0f

        // 4. Charging Risk Logic (Plugged AND Temp >= threshold OR Voltage > 4400mV)
        val threshold = thermalThresholdC.toFloat()
        val isChargingRiskConfirmed = lastBatteryPlugged && (lastBatteryTempC >= threshold || lastBatteryVoltageMv > 4400)

        // 5. Magnetic Anomaly Hazard Logic (Magnetometer >= 100 µT for >= 5 seconds, ignored if charging)
        val magneticDuration = if (magneticStartTime > 0L) lastReadingTimestamp - magneticStartTime else 0L
        val isPersistentMagnetic = lastMagneticuT >= 100.0f && magneticDuration >= 5000L
        val isMagneticHazardConfirmed = !lastBatteryPlugged && isPersistentMagnetic

        var count = 0
        if (isPocketConfirmed) count++
        if (isHeatHigh) count++
        if (isImpactConfirmed) count++
        if (isChargingRiskConfirmed) count++
        if (isMagneticHazardConfirmed) count++

        return SensorFusionState(
            isPocketConfirmed = isPocketConfirmed,
            pocketConfidence = pocketConfidence,
            isHighHeatConfirmed = isHeatHigh,
            heatConfidence = heatConfidence,
            batteryTempC = lastBatteryTempC,
            ambientLightLux = lastLightLux ?: 0f,
            isImpactConfirmed = isImpactConfirmed,
            impactGForce = lastAccelNorm / 9.81f,
            isChargingRiskConfirmed = isChargingRiskConfirmed,
            chargingVoltageMv = lastBatteryVoltageMv,
            batteryLevelPercent = lastBatteryLevelPct,
            isCharging = lastBatteryPlugged,
            isMagneticHazardConfirmed = isMagneticHazardConfirmed,
            magneticMagnitudeuT = lastMagneticuT,
            ambientTemperatureC = lastAmbientTempC,
            temperatureDifference = tempDiff,
            activeEventsCount = count,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }
}
