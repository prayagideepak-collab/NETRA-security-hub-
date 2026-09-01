package com.example.data.sensor

import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCapabilityInfo

data class SensorDiagnosticStatus(
    val sensorId: String,
    val sensorName: String,
    val isStale: Boolean,
    val isInvalid: Boolean,
    val isFaulty: Boolean,
    val statusMessage: String,
    val lastTimestamp: Long,
    val status: String // Initializing, Active, Monitoring, Paused, Error, Not Supported
)

class SensorDiagnosticsEngine {

    fun analyzeSensors(
        capabilities: List<SensorCapabilityInfo>,
        liveReadings: Map<String, RawSensorReading>,
        isMonitoringActive: Boolean
    ): List<SensorDiagnosticStatus> {
        val currentTime = System.currentTimeMillis()
        val results = mutableListOf<SensorDiagnosticStatus>()
        
        val STALE_THRESHOLD = 30000L // Increased to 30s
        val FAULTY_THRESHOLD = 90000L // Increased to 90s

        capabilities.forEach { cap ->
            val reading = liveReadings[cap.id] ?: liveReadings["sensor_${cap.type}"]
            val lastTime = reading?.timestamp ?: 0L
            val age = currentTime - lastTime

            var isInvalid = false
            var invalidReason = ""

            if (reading != null && reading.values.isNotEmpty()) {
                for (v in reading.values) {
                    if (v.isNaN() || v.isInfinite()) {
                        isInvalid = true
                        invalidReason = "Contains NaN or Infinity"
                        break
                    }
                }
                // Specific domain checks
                if (!isInvalid) {
                    when (cap.type) {
                        android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE, android.hardware.Sensor.TYPE_TEMPERATURE -> {
                            if (reading.values[0] < -50f || reading.values[0] > 100f) {
                                isInvalid = true
                                invalidReason = "Temperature out of bounds (${reading.values[0]}°C)"
                            }
                        }
                        android.hardware.Sensor.TYPE_LIGHT -> {
                            if (reading.values[0] < 0f) {
                                isInvalid = true
                                invalidReason = "Negative light lux (${reading.values[0]})"
                            }
                        }
                    }
                }
            }

            // Determine accurate state status
            val status = when {
                !cap.isSupported -> "Not Supported"
                isInvalid -> "Error"
                !isMonitoringActive -> "Paused"
                lastTime == 0L -> "Initializing"
                age > STALE_THRESHOLD -> "Paused"
                age <= 1500L -> "Active"
                else -> "Monitoring"
            }

            val isStale = cap.isSupported && isMonitoringActive && lastTime > 0L && age > STALE_THRESHOLD
            val isFaulty = isInvalid || (cap.isSupported && isMonitoringActive && lastTime > 0L && age > FAULTY_THRESHOLD)

            val msg = when {
                !cap.isSupported -> "Not Supported"
                isInvalid -> "Error: $invalidReason"
                !isMonitoringActive -> "Paused"
                lastTime == 0L -> "Initializing stream..."
                age > STALE_THRESHOLD -> "Stream Idle"
                else -> "Nominal & Verified"
            }

            results.add(
                SensorDiagnosticStatus(
                    sensorId = cap.id,
                    sensorName = cap.name,
                    isStale = isStale,
                    isInvalid = isInvalid,
                    isFaulty = isFaulty,
                    statusMessage = msg,
                    lastTimestamp = lastTime,
                    status = status
                )
            )
        }

        return results
    }
}
