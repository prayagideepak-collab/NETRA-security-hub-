package com.aistudio.netrasensorhub.data.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import com.aistudio.netrasensorhub.data.intelligence.models.MotionState
import com.aistudio.netrasensorhub.data.risk.SensorTelemetryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Centralized Single Source of Truth for raw device telemetry in Netra Security Hub.
 *
 * Enforces:
 * - True Data Only: Nullable sensor states initialized to null until real hardware events fire.
 * - Zero Manufactured/Fake/Simulation Values.
 * - Battery data used solely as internal physical safety inputs (thermal protection / power scaling).
 * - Lifecycle-bounded listener management (foreground only).
 */
class SensorTelemetryManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val magneticField: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _telemetryState = MutableStateFlow(
        SensorTelemetryState(
            accelX = null,
            accelY = null,
            accelZ = null,
            gyroX = null,
            gyroY = null,
            gyroZ = null,
            magneticX = null,
            magneticY = null,
            magneticZ = null,
            ambientLightLux = null,
            batteryTempC = null,
            isCharging = null,
            isAccelSupported = accelerometer != null,
            isGyroSupported = gyroscope != null,
            isMagneticSupported = magneticField != null,
            isLightSupported = lightSensor != null,
            isBatteryTempSupported = true,
            lastTimestampMillis = System.currentTimeMillis()
        )
    )
    val telemetryState: StateFlow<SensorTelemetryState> = _telemetryState.asStateFlow()

    private val _derivedMotionState = MutableStateFlow(MotionState.STATIONARY)
    val derivedMotionState: StateFlow<MotionState> = _derivedMotionState.asStateFlow()

    private var isListening = false

    // Motion calculation accumulators from genuine accelerometer feed
    private var lastMotionCalculationTime = 0L
    private var accelVarianceAccumulator = 0f
    private var accelSampleCount = 0

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent?.let {
                val tempInt = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val tempC = if (tempInt > 0) tempInt / 10.0f else null

                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = if (status != -1) {
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                } else null

                _telemetryState.value = _telemetryState.value.copy(
                    batteryTempC = tempC,
                    isCharging = isCharging,
                    lastTimestampMillis = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Starts listening to hardware sensors and battery safety broadcasts.
     * MUST only be invoked when the UI is in the foreground.
     */
    fun startListening() {
        if (isListening) return
        isListening = true

        try {
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {}

        sensorManager?.let { sm ->
            accelerometer?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            gyroscope?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magneticField?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            lightSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    /**
     * Immediately unregisters all sensor listeners and receivers to save battery in background.
     */
    fun stopListening() {
        if (!isListening) return
        isListening = false

        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}

        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isListening || event == null) return

        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                _telemetryState.value = _telemetryState.value.copy(
                    accelX = ax,
                    accelY = ay,
                    accelZ = az,
                    lastTimestampMillis = now
                )

                // Compute real sensor-to-motion state
                val mag = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
                val deltaG = abs(mag - 9.81f)
                accelVarianceAccumulator += deltaG
                accelSampleCount++

                if (now - lastMotionCalculationTime >= 2000L) {
                    val avgDelta = if (accelSampleCount > 0) accelVarianceAccumulator / accelSampleCount else 0f
                    val derivedMotion = when {
                        avgDelta > 4.5f -> MotionState.RUNNING
                        avgDelta > 1.8f -> MotionState.WALKING
                        avgDelta > 0.8f -> MotionState.VEHICLE
                        else -> MotionState.STATIONARY
                    }
                    _derivedMotionState.value = derivedMotion
                    accelVarianceAccumulator = 0f
                    accelSampleCount = 0
                    lastMotionCalculationTime = now
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                _telemetryState.value = _telemetryState.value.copy(
                    gyroX = event.values[0],
                    gyroY = event.values[1],
                    gyroZ = event.values[2],
                    lastTimestampMillis = now
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                _telemetryState.value = _telemetryState.value.copy(
                    magneticX = event.values[0],
                    magneticY = event.values[1],
                    magneticZ = event.values[2],
                    lastTimestampMillis = now
                )
            }
            Sensor.TYPE_LIGHT -> {
                _telemetryState.value = _telemetryState.value.copy(
                    ambientLightLux = event.values[0],
                    lastTimestampMillis = now
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
