package com.example.data.sensor

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.BatteryManager
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Build
import android.os.PowerManager
import com.example.data.model.DataClassification
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCategory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.sqrt

class SensorObservers(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Observe any motion or environmental hardware sensor by type.
     * Refresh interval set to SENSOR_DELAY_UI (~60ms to 300ms max).
     */
    fun observeHardwareSensor(sensorType: Int, sensorName: String, category: SensorCategory, unit: String, refreshIntervalMs: Long = 150L): Flow<RawSensorReading> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        var lastEmitTime = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val now = System.currentTimeMillis()
                // Ensure max refresh interval
                if (now - lastEmitTime >= refreshIntervalMs) {
                    lastEmitTime = now
                    val values = event.values.clone()
                    val accuracyStr = when (event.accuracy) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                        SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
                        else -> "Standard"
                    }
                    trySend(
                        RawSensorReading(
                            sensorId = "sensor_$sensorType",
                            name = sensorName,
                            category = category,
                            values = values,
                            unit = unit,
                            timestamp = now,
                            classification = DataClassification.VERIFIED,
                            extraDetails = mapOf("accuracy" to accuracyStr)
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Observe Battery, Charging, Power & Temperature telemetry.
     */
    fun observeBatteryPower(): Flow<RawSensorReading> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val tempC = tempTenths / 10.0f
                val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (scale > 0) (level * 100f / scale) else 0f

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val plugType = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> if (isCharging) "Connected" else "Discharging"
                }

                val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
                    else -> "NORMAL"
                }

                // BatteryManager Instantaneous Current Draw (if supported)
                val bm = context?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val currentNowMicroAmps = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                val currentMa = currentNowMicroAmps / 1000f

                val extras = mapOf(
                    "batteryPct" to "%.1f%%".format(batteryPct),
                    "voltageMv" to "$voltageMv mV",
                    "plugType" to plugType,
                    "health" to healthStr,
                    "currentMa" to "%.0f mA".format(currentMa)
                )

                trySend(
                    RawSensorReading(
                        sensorId = "battery_telemetry",
                        name = "Battery & Power Subsystem",
                        category = SensorCategory.POWER,
                        values = floatArrayOf(tempC, batteryPct, voltageMv.toFloat(), currentMa),
                        unit = "°C",
                        timestamp = System.currentTimeMillis(),
                        classification = DataClassification.VERIFIED,
                        extraDetails = extras
                    )
                )
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        // Emit immediately if sticky intent exists
        receiver.onReceive(context, stickyIntent)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    /**
     * Observe Thermal Throttling Status
     */
    fun observeThermalState(): Flow<RawSensorReading> = callbackFlow {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                val statusStr = when (status) {
                    PowerManager.THERMAL_STATUS_NONE -> "NONE (Normal)"
                    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT (Minor Heat)"
                    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE (Throttling Warning)"
                    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE (Performance Reduced)"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL (Emergency Cooling Needed)"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY (Shutdown Imminent)"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                    else -> "UNKNOWN"
                }
                val valueFloat = status.toFloat()
                trySend(
                    RawSensorReading(
                        sensorId = "thermal_subsystem",
                        name = "Thermal Throttling HAL",
                        category = SensorCategory.THERMAL,
                        values = floatArrayOf(valueFloat),
                        unit = "Level",
                        timestamp = System.currentTimeMillis(),
                        classification = DataClassification.VERIFIED,
                        extraDetails = mapOf("thermalStatus" to statusStr)
                    )
                )
            }

            pm.addThermalStatusListener(listener)
            // Initial emit
            val initialStatus = pm.currentThermalStatus
            listener.onThermalStatusChanged(initialStatus)

            awaitClose {
                pm.removeThermalStatusListener(listener)
            }
        } else {
            // Unsupported fallback emission
            trySend(
                RawSensorReading(
                    sensorId = "thermal_subsystem",
                    name = "Thermal Throttling HAL",
                    category = SensorCategory.THERMAL,
                    values = floatArrayOf(0f),
                    unit = "Level",
                    timestamp = System.currentTimeMillis(),
                    classification = DataClassification.VERIFIED,
                    extraDetails = mapOf("thermalStatus" to "System Thermal API N/A")
                )
            )
            awaitClose {}
        }
    }

    /**
     * Observe Location / GPS Speed & Position using Play Services Location client
     */
    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<RawSensorReading> = callbackFlow {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val permissionPollerJob = launch {
            while (true) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (!hasFine) {
                    trySend(
                        RawSensorReading(
                            sensorId = "gnss_location",
                            name = "GNSS Positioning",
                            category = SensorCategory.LOCATION,
                            values = floatArrayOf(0f, 0f, 0f, 0f),
                            unit = "Lat/Lng",
                            timestamp = System.currentTimeMillis(),
                            classification = DataClassification.VERIFIED,
                            extraDetails = mapOf(
                                "latitude" to "Verified data is currently unavailable.",
                                "longitude" to "Verified data is currently unavailable.",
                                "speedKmH" to "Verified data is currently unavailable.",
                                "accuracy" to "Verified data is currently unavailable."
                            )
                        )
                    )
                    kotlinx.coroutines.delay(2000L)
                    continue
                }

                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val speedKmH = location.speed * 3.6f
                            trySend(
                                RawSensorReading(
                                    sensorId = "gnss_location",
                                    name = "GNSS Positioning",
                                    category = SensorCategory.LOCATION,
                                    values = floatArrayOf(location.latitude.toFloat(), location.longitude.toFloat(), speedKmH, location.accuracy),
                                    unit = "Lat/Lng",
                                    timestamp = System.currentTimeMillis(),
                                    classification = DataClassification.VERIFIED,
                                    extraDetails = mapOf(
                                        "latitude" to "%.5f".format(location.latitude),
                                        "longitude" to "%.5f".format(location.longitude),
                                        "speedKmH" to "%.1f km/h".format(speedKmH),
                                        "accuracy" to "±%.1f m".format(location.accuracy)
                                    )
                                )
                            )
                        } else {
                            trySend(
                                RawSensorReading(
                                    sensorId = "gnss_location",
                                    name = "GNSS Positioning",
                                    category = SensorCategory.LOCATION,
                                    values = floatArrayOf(0f, 0f, 0f, 0f),
                                    unit = "Lat/Lng",
                                    timestamp = System.currentTimeMillis(),
                                    classification = DataClassification.VERIFIED,
                                    extraDetails = mapOf(
                                        "latitude" to "Verified data is currently unavailable.",
                                        "longitude" to "Verified data is currently unavailable.",
                                        "speedKmH" to "Verified data is currently unavailable.",
                                        "accuracy" to "Verified data is currently unavailable."
                                    )
                                )
                            )
                        }
                    }.addOnFailureListener {
                        trySend(
                            RawSensorReading(
                                sensorId = "gnss_location",
                                name = "GNSS Positioning",
                                category = SensorCategory.LOCATION,
                                values = floatArrayOf(0f, 0f, 0f, 0f),
                                unit = "Lat/Lng",
                                timestamp = System.currentTimeMillis(),
                                classification = DataClassification.VERIFIED,
                                extraDetails = mapOf(
                                    "latitude" to "Verified data is currently unavailable.",
                                    "longitude" to "Verified data is currently unavailable.",
                                    "speedKmH" to "Verified data is currently unavailable.",
                                    "accuracy" to "Verified data is currently unavailable."
                                )
                            )
                        )
                    }
                } catch (_: Exception) {
                    trySend(
                        RawSensorReading(
                            sensorId = "gnss_location",
                            name = "GNSS Positioning",
                            category = SensorCategory.LOCATION,
                            values = floatArrayOf(0f, 0f, 0f, 0f),
                            unit = "Lat/Lng",
                            timestamp = System.currentTimeMillis(),
                            classification = DataClassification.VERIFIED,
                            extraDetails = mapOf(
                                "latitude" to "Verified data is currently unavailable.",
                                "longitude" to "Verified data is currently unavailable.",
                                "speedKmH" to "Verified data is currently unavailable.",
                                "accuracy" to "Verified data is currently unavailable."
                            )
                        )
                    )
                }

                kotlinx.coroutines.delay(2000L)
            }
        }

        awaitClose {
            permissionPollerJob.cancel()
        }
    }
}
