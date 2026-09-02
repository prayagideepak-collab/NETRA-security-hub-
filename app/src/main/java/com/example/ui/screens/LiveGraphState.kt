package com.example.ui.screens

import android.hardware.Sensor
import com.example.data.model.RawSensorReading

data class LiveGraphState(
    val selectedSensorType: Int = Sensor.TYPE_ACCELEROMETER,
    val isPaused: Boolean = false,
    val buffer: List<RawSensorReading> = emptyList(),
    val availableSensors: List<Int> = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_LIGHT
    )
)
