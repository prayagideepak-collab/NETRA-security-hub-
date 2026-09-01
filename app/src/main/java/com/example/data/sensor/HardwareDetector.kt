package com.example.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory

class HardwareDetector(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun discoverAllCapabilities(): List<SensorCapabilityInfo> {
        val list = mutableListOf<SensorCapabilityInfo>()

        // 1. Motion Sensors
        list.add(checkSensor(Sensor.TYPE_ACCELEROMETER, "Accelerometer", SensorCategory.MOTION, "3-axis linear acceleration reader"))
        list.add(checkSensor(Sensor.TYPE_GYROSCOPE, "Gyroscope", SensorCategory.MOTION, "3-axis rotational velocity reader"))
        list.add(checkSensor(Sensor.TYPE_GRAVITY, "Gravity Sensor", SensorCategory.MOTION, "Gravity vector component"))
        list.add(checkSensor(Sensor.TYPE_LINEAR_ACCELERATION, "Linear Acceleration", SensorCategory.MOTION, "Acceleration excluding gravity"))
        list.add(checkSensor(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector", SensorCategory.MOTION, "Device orientation quaternion"))
        list.add(checkSensor(Sensor.TYPE_GAME_ROTATION_VECTOR, "Game Rotation Vector", SensorCategory.MOTION, "Uncalibrated rotational vector"))
        list.add(checkSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "Geomagnetic Rotation Vector", SensorCategory.MOTION, "Magnetic & gravity orientation"))
        list.add(checkSensor(Sensor.TYPE_STEP_COUNTER, "Step Counter", SensorCategory.MOTION, "Hardware step tally since boot"))
        list.add(checkSensor(Sensor.TYPE_STEP_DETECTOR, "Step Detector", SensorCategory.MOTION, "Hardware step trigger pulse"))
        list.add(checkSensor(Sensor.TYPE_SIGNIFICANT_MOTION, "Significant Motion", SensorCategory.MOTION, "Triggers on significant physical movement"))

        // 2. Environmental Sensors
        list.add(checkSensor(Sensor.TYPE_LIGHT, "Ambient Light Sensor", SensorCategory.ENVIRONMENTAL, "Measures illuminance in Lux"))
        list.add(checkSensor(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", SensorCategory.ENVIRONMENTAL, "Measures ambient magnetic field in µT"))
        list.add(checkSensor(Sensor.TYPE_PROXIMITY, "Proximity Sensor", SensorCategory.ENVIRONMENTAL, "Measures object distance in cm"))
        list.add(checkSensor(Sensor.TYPE_PRESSURE, "Barometer", SensorCategory.ENVIRONMENTAL, "Measures atmospheric pressure in hPa"))
        list.add(checkSensor(Sensor.TYPE_AMBIENT_TEMPERATURE, "Ambient Temperature", SensorCategory.ENVIRONMENTAL, "Measures air temperature in °C"))
        list.add(checkSensor(Sensor.TYPE_RELATIVE_HUMIDITY, "Relative Humidity", SensorCategory.ENVIRONMENTAL, "Measures relative air humidity in %"))

        // 3. Power & Battery Hardware
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val hasBattery = bm != null
        list.add(
            SensorCapabilityInfo(
                id = "battery_telemetry",
                name = "Battery & Charging Telemetry",
                vendor = Build.MANUFACTURER,
                type = -1,
                category = SensorCategory.POWER,
                isSupported = hasBattery,
                description = "Monitors battery temperature, voltage, health, and current draw"
            )
        )

        // 4. Thermal Subsystem
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val hasThermalApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null
        list.add(
            SensorCapabilityInfo(
                id = "thermal_subsystem",
                name = "Android Thermal Throttling API",
                vendor = "Android OS Hardware Abstraction Layer",
                type = -2,
                category = SensorCategory.THERMAL,
                isSupported = hasThermalApi,
                description = "Provides hardware thermal headroom and throttling status"
            )
        )

        // 5. Location & GNSS
        list.add(
            SensorCapabilityInfo(
                id = "gnss_location",
                name = "GNSS / GPS Hardware",
                vendor = "Chipset Integrated GNSS",
                type = -3,
                category = SensorCategory.LOCATION,
                isSupported = true,
                description = "Provides geographic position, speed, and accuracy"
            )
        )

        return list
    }

    private fun checkSensor(type: Int, name: String, category: SensorCategory, defaultDesc: String): SensorCapabilityInfo {
        val sensor = sensorManager.getDefaultSensor(type)
        return if (sensor != null) {
            SensorCapabilityInfo(
                id = "sensor_${sensor.type}_${sensor.name.lowercase().replace(" ", "_")}",
                name = sensor.name.ifBlank { name },
                vendor = sensor.vendor.ifBlank { "System Hardware" },
                type = sensor.type,
                category = category,
                isSupported = true,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayUs = sensor.minDelay,
                description = defaultDesc
            )
        } else {
            SensorCapabilityInfo(
                id = "sensor_${type}_unsupported",
                name = name,
                vendor = "Unavailable",
                type = type,
                category = category,
                isSupported = false,
                description = defaultDesc
            )
        }
    }
}
