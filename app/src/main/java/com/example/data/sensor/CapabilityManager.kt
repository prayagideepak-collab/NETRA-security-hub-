package com.example.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager as AndroidSensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CapabilityManager establishes the hardware capability discovery process across all Android
 * hardware abstraction layers (HAL), including motion, environmental, power, thermal, and location sensors.
 */
class CapabilityManager(private val context: Context) {

    private val androidSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as AndroidSensorManager

    private val _capabilities = MutableStateFlow<List<SensorCapabilityInfo>>(emptyList())
    val capabilities: StateFlow<List<SensorCapabilityInfo>> = _capabilities.asStateFlow()

    private val _isDiscoveryComplete = MutableStateFlow(false)
    val isDiscoveryComplete: StateFlow<Boolean> = _isDiscoveryComplete.asStateFlow()

    /**
     * Executes capability discovery across system HAL and hardware subsystems.
     */
    fun discoverCapabilities(): List<SensorCapabilityInfo> {
        val discoveredList = mutableListOf<SensorCapabilityInfo>()

        // 1. Motion Sensors Discovery
        discoveredList.add(inspectSensor(Sensor.TYPE_ACCELEROMETER, "Accelerometer", SensorCategory.MOTION, "3-axis linear acceleration reader"))
        discoveredList.add(inspectSensor(Sensor.TYPE_GYROSCOPE, "Gyroscope", SensorCategory.MOTION, "3-axis rotational velocity reader"))
        discoveredList.add(inspectSensor(Sensor.TYPE_GRAVITY, "Gravity Sensor", SensorCategory.MOTION, "Gravity vector component reader"))
        discoveredList.add(inspectSensor(Sensor.TYPE_LINEAR_ACCELERATION, "Linear Acceleration", SensorCategory.MOTION, "Acceleration excluding gravity component"))
        discoveredList.add(inspectSensor(Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector", SensorCategory.MOTION, "Device orientation quaternion sensor"))
        discoveredList.add(inspectSensor(Sensor.TYPE_GAME_ROTATION_VECTOR, "Game Rotation Vector", SensorCategory.MOTION, "Uncalibrated rotational vector sensor"))
        discoveredList.add(inspectSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, "Geomagnetic Rotation Vector", SensorCategory.MOTION, "Magnetic & gravity vector fusion"))
        discoveredList.add(inspectSensor(Sensor.TYPE_STEP_COUNTER, "Step Counter", SensorCategory.MOTION, "Hardware step tally since device boot"))
        discoveredList.add(inspectSensor(Sensor.TYPE_STEP_DETECTOR, "Step Detector", SensorCategory.MOTION, "Hardware step event pulse trigger"))
        discoveredList.add(inspectSensor(Sensor.TYPE_SIGNIFICANT_MOTION, "Significant Motion", SensorCategory.MOTION, "Triggers event on major physical displacement"))

        // 2. Environmental Sensors Discovery
        discoveredList.add(inspectSensor(Sensor.TYPE_LIGHT, "Ambient Light Sensor", SensorCategory.ENVIRONMENTAL, "Measures illuminance in Lux"))
        discoveredList.add(inspectSensor(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", SensorCategory.ENVIRONMENTAL, "Measures ambient magnetic field vector in µT"))
        discoveredList.add(inspectSensor(Sensor.TYPE_PROXIMITY, "Proximity Sensor", SensorCategory.ENVIRONMENTAL, "Measures object distance in centimeters"))
        discoveredList.add(inspectSensor(Sensor.TYPE_PRESSURE, "Barometer", SensorCategory.ENVIRONMENTAL, "Measures atmospheric pressure in hPa"))
        discoveredList.add(inspectSensor(Sensor.TYPE_AMBIENT_TEMPERATURE, "Ambient Temperature", SensorCategory.ENVIRONMENTAL, "Measures air temperature in °C"))
        discoveredList.add(inspectSensor(Sensor.TYPE_RELATIVE_HUMIDITY, "Relative Humidity", SensorCategory.ENVIRONMENTAL, "Measures relative humidity percentage"))

        // 3. Power & Battery Hardware Subsystem Discovery
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val hasBatteryManager = bm != null
        discoveredList.add(
            SensorCapabilityInfo(
                id = "battery_telemetry",
                name = "Battery & Power Subsystem",
                vendor = Build.MANUFACTURER.ifBlank { "Device Hardware" },
                type = -1,
                category = SensorCategory.POWER,
                isSupported = hasBatteryManager,
                description = "Monitors battery temperature, voltage, health, and real-time current draw"
            )
        )

        // 4. Thermal HAL Subsystem Discovery
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val hasThermalApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null
        discoveredList.add(
            SensorCapabilityInfo(
                id = "thermal_subsystem",
                name = "Android Thermal Throttling API",
                vendor = "Android HAL Service",
                type = -2,
                category = SensorCategory.THERMAL,
                isSupported = hasThermalApi,
                description = "Provides hardware thermal headroom, status levels, and throttling telemetry"
            )
        )

        // 5. Location & GNSS Hardware Discovery
        discoveredList.add(
            SensorCapabilityInfo(
                id = "gnss_location",
                name = "GNSS / GPS Hardware",
                vendor = "Chipset Integrated GNSS",
                type = -3,
                category = SensorCategory.LOCATION,
                isSupported = true,
                description = "Provides real-time latitude, longitude, movement speed, and positioning accuracy"
            )
        )

        _capabilities.value = discoveredList
        _isDiscoveryComplete.value = true
        return discoveredList
    }

    private fun inspectSensor(
        type: Int,
        defaultName: String,
        category: SensorCategory,
        description: String
    ): SensorCapabilityInfo {
        val sensor = androidSensorManager.getDefaultSensor(type)
        return if (sensor != null) {
            SensorCapabilityInfo(
                id = "sensor_${sensor.type}_${sensor.name.lowercase().replace(" ", "_")}",
                name = sensor.name.ifBlank { defaultName },
                vendor = sensor.vendor.ifBlank { "System HAL" },
                type = sensor.type,
                category = category,
                isSupported = true,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution,
                powerMa = sensor.power,
                minDelayUs = sensor.minDelay,
                description = description
            )
        } else {
            SensorCapabilityInfo(
                id = "sensor_${type}_unsupported",
                name = defaultName,
                vendor = "Unavailable",
                type = type,
                category = category,
                isSupported = false,
                description = description
            )
        }
    }

    fun getSupportedCapabilities(): List<SensorCapabilityInfo> {
        return _capabilities.value.filter { it.isSupported }
    }

    fun getCapabilitiesByCategory(category: SensorCategory): List<SensorCapabilityInfo> {
        return _capabilities.value.filter { it.category == category }
    }
}
