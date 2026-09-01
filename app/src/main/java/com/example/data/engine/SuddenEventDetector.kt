package com.example.data.engine

import com.example.data.model.DataClassification
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCategory
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SuddenEventDetector isolates high-significance physical/environmental events
 * (such as sudden high acceleration, rapid angular flip, or extreme magnetic anomaly)
 * from standard sensor noise, ensuring normal background variance never generates spurious alerts.
 */
class SuddenEventDetector {

    enum class SuddenEventType {
        SUDDEN_ACCELERATION_SPIKE,
        RAPID_ORIENTATION_FLIP,
        EXTREME_MAGNETIC_ANOMALY,
        FREE_FALL_DROP,
        NONE
    }

    data class SuddenEventResult(
        val type: SuddenEventType,
        val isSignificant: Boolean,
        val magnitude: Float,
        val description: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Baseline filtering state
    private var lastAccelNorm = 9.81f
    private var lastGyroNorm = 0.0f
    private var lastMagneticMag = 45.0f
    private var lastEventTriggerTime = 0L
    private val EVENT_COOLDOWN_MS = 3000L // Prevent event spam

    /**
     * Evaluates whether a raw sensor reading constitutes a genuine sudden event.
     */
    fun evaluateReading(reading: RawSensorReading): SuddenEventResult {
        val now = reading.timestamp
        val values = reading.values

        if (values.isEmpty()) {
            return SuddenEventResult(SuddenEventType.NONE, false, 0f, "Empty reading", now)
        }

        when {
            // 1. Accelerometer (3-axis)
            reading.sensorId.contains("accel", true) || reading.category == SensorCategory.MOTION && values.size >= 3 -> {
                val x = values[0]
                val y = values[1]
                val z = values[2]
                val norm = sqrt(x * x + y * y + z * z)
                val delta = abs(norm - lastAccelNorm)
                lastAccelNorm = norm

                // Free-fall threshold (near zero gravity < 1.5 m/s² followed by impact)
                if (norm < 1.5f && (now - lastEventTriggerTime > EVENT_COOLDOWN_MS)) {
                    lastEventTriggerTime = now
                    return SuddenEventResult(
                        type = SuddenEventType.FREE_FALL_DROP,
                        isSignificant = true,
                        magnitude = norm,
                        description = "Free-fall microgravity detected: %.2f m/s²".format(norm),
                        timestamp = now
                    )
                }

                // Sudden high shock / impact (> 25 m/s² or delta > 18 m/s²)
                if ((norm >= 25.0f || delta >= 18.0f) && (now - lastEventTriggerTime > EVENT_COOLDOWN_MS)) {
                    lastEventTriggerTime = now
                    return SuddenEventResult(
                        type = SuddenEventType.SUDDEN_ACCELERATION_SPIKE,
                        isSignificant = true,
                        magnitude = norm,
                        description = "Sudden acceleration jolt: %.2f m/s² (G-Force: %.1f G)".format(norm, norm / 9.81f),
                        timestamp = now
                    )
                }
            }

            // 2. Gyroscope (3-axis angular velocity in rad/s)
            reading.sensorId.contains("gyro", true) && values.size >= 3 -> {
                val gx = values[0]
                val gy = values[1]
                val gz = values[2]
                val gyroNorm = sqrt(gx * gx + gy * gy + gz * gz)
                val deltaGyro = abs(gyroNorm - lastGyroNorm)
                lastGyroNorm = gyroNorm

                // Rapid rotation (> 6.0 rad/s (~340 deg/s))
                if (gyroNorm >= 6.0f && deltaGyro >= 4.0f && (now - lastEventTriggerTime > EVENT_COOLDOWN_MS)) {
                    lastEventTriggerTime = now
                    return SuddenEventResult(
                        type = SuddenEventType.RAPID_ORIENTATION_FLIP,
                        isSignificant = true,
                        magnitude = gyroNorm,
                        description = "Rapid orientation flip: %.2f rad/s".format(gyroNorm),
                        timestamp = now
                    )
                }
            }

            // 3. Magnetic Field (3-axis in µT)
            reading.sensorId.contains("mag", true) || reading.category == SensorCategory.ENVIRONMENTAL && values.size >= 3 -> {
                val mx = values[0]
                val my = values[1]
                val mz = values[2]
                val mag = sqrt(mx * mx + my * my + mz * mz)
                val deltaMag = abs(mag - lastMagneticMag)
                lastMagneticMag = mag

                // Extreme magnetic spike (> 180 µT with rapid delta > 100 µT)
                if (mag >= 180.0f && deltaMag >= 100.0f && (now - lastEventTriggerTime > EVENT_COOLDOWN_MS)) {
                    lastEventTriggerTime = now
                    return SuddenEventResult(
                        type = SuddenEventType.EXTREME_MAGNETIC_ANOMALY,
                        isSignificant = true,
                        magnitude = mag,
                        description = "Extreme localized magnetic surge: %.1f µT".format(mag),
                        timestamp = now
                    )
                }
            }
        }

        return SuddenEventResult(SuddenEventType.NONE, false, 0f, "Normal sensor variance", now)
    }

    fun resetCooldown() {
        lastEventTriggerTime = 0L
    }
}
