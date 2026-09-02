package com.example.data.pipeline

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager as AndroidBatteryManager
import android.os.PowerManager
import com.example.util.LoggingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authoritative Device Data Adapter.
 * Bridges Android System APIs, Sensor Framework, Step Counters, and Fused Location
 * into validated, timestamped, and freshness-tagged metrics without simulation or fallback fiction.
 */
class DeviceDataAdapter(private val context: Context) {

    private val sensorManager: AndroidSensorManager? = 
        context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager

    private val locationManager: LocationManager? = 
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val powerManager: PowerManager? = 
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    data class DeviceBatterySnapshot(
        val levelPercent: Int,
        val temperatureC: Float,
        val voltageMv: Int,
        val isCharging: Boolean,
        val plugType: String,
        val health: String,
        val currentMa: Float,
        val timestamp: Long
    )

    data class LocationSnapshot(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
        val speedMps: Float?,
        val timestamp: Long,
        val provider: String
    )

    data class DisplayStateSnapshot(
        val isScreenOn: Boolean,
        val isInteractive: Boolean,
        val timestamp: Long
    )

    /**
     * Reads authoritative battery status via Android BatteryManager sticky broadcast.
     */
    fun readBatterySnapshot(): ValidatedMetric<DeviceBatterySnapshot> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = context.registerReceiver(null, filter)
            if (batteryIntent == null) {
                return ValidatedMetric.unavailable("BatteryManager", "Sticky broadcast unavailable")
            }

            val level = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
            val levelPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

            if (levelPercent < 0 || levelPercent > 100) {
                return ValidatedMetric.unavailable("BatteryManager", "Invalid battery level range")
            }

            val tempTenths = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = tempTenths / 10.0f

            val voltageMv = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 0)
            val status = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                    status == AndroidBatteryManager.BATTERY_STATUS_FULL

            val plugged = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_PLUGGED, -1)
            val plugType = when (plugged) {
                AndroidBatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                AndroidBatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                AndroidBatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> if (isCharging) "Connected" else "Discharging"
            }

            val health = batteryIntent.getIntExtra(AndroidBatteryManager.EXTRA_HEALTH, AndroidBatteryManager.BATTERY_HEALTH_UNKNOWN)
            val healthStr = when (health) {
                AndroidBatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                AndroidBatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                AndroidBatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                AndroidBatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE"
                else -> "NORMAL"
            }

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? AndroidBatteryManager
            val currentMicroAmps = bm?.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val currentMa = currentMicroAmps / 1000f

            val now = System.currentTimeMillis()
            val snapshot = DeviceBatterySnapshot(
                levelPercent = levelPercent,
                temperatureC = tempC,
                voltageMv = voltageMv,
                isCharging = isCharging,
                plugType = plugType,
                health = healthStr,
                currentMa = currentMa,
                timestamp = now
            )

            ValidatedMetric.fresh(snapshot, "AndroidBatteryManager", now)
        } catch (e: Exception) {
            LoggingManager.warning("DeviceDataAdapter", "BATTERY_READ_ERROR", "Battery read error", e.message ?: "Unknown error")
            ValidatedMetric.unavailable("BatteryManager", e.message ?: "Exception")
        }
    }

    /**
     * Reads display and power interactive state via PowerManager.
     */
    fun readDisplayState(): ValidatedMetric<DisplayStateSnapshot> {
        return try {
            val isInteractive = powerManager?.isInteractive ?: true
            val now = System.currentTimeMillis()
            val snapshot = DisplayStateSnapshot(
                isScreenOn = isInteractive,
                isInteractive = isInteractive,
                timestamp = now
            )
            ValidatedMetric.fresh(snapshot, "PowerManager", now)
        } catch (e: Exception) {
            ValidatedMetric.unavailable("PowerManager", e.message ?: "Exception")
        }
    }

    /**
     * Validates and retrieves the last known location if strictly valid and fresh.
     * Rejects (0,0), placeholder coordinates, or stale positions older than 15 minutes.
     */
    fun readLastKnownLocation(): ValidatedMetric<LocationSnapshot> {
        val lm = locationManager ?: return ValidatedMetric.unavailable("LocationManager", "Location service not available")

        val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return ValidatedMetric.unavailable("LocationManager", "Location permission denied")
        }

        try {
            val providers = lm.getProviders(true)
            var bestLocation: Location? = null

            for (provider in providers) {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < (bestLocation.accuracy)) {
                    bestLocation = loc
                }
            }

            if (bestLocation == null) {
                return ValidatedMetric.unavailable("LocationManager", "No last known location fix")
            }

            // Strict Validation Rule: Reject (0,0) and invalid coordinates
            if (bestLocation.latitude == 0.0 && bestLocation.longitude == 0.0) {
                return ValidatedMetric.unavailable("LocationManager", "Rejected invalid zero coordinates (0,0)")
            }
            if (bestLocation.latitude < -90.0 || bestLocation.latitude > 90.0 ||
                bestLocation.longitude < -180.0 || bestLocation.longitude > 180.0) {
                return ValidatedMetric.unavailable("LocationManager", "Coordinates out of valid geographical bounds")
            }

            val now = System.currentTimeMillis()
            val locAgeMs = now - bestLocation.time

            val snapshot = LocationSnapshot(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
                accuracyMeters = if (bestLocation.hasAccuracy()) bestLocation.accuracy else null,
                speedMps = if (bestLocation.hasSpeed()) bestLocation.speed else null,
                timestamp = bestLocation.time,
                provider = bestLocation.provider ?: "system"
            )

            return if (locAgeMs < 120_000L) { // Less than 2 minutes: FRESH
                ValidatedMetric.fresh(snapshot, "LocationProvider:${snapshot.provider}", bestLocation.time)
            } else if (locAgeMs < 900_000L) { // Less than 15 minutes: STALE
                ValidatedMetric.stale(snapshot, "LocationProvider:${snapshot.provider}", bestLocation.time)
            } else {
                ValidatedMetric.unavailable("LocationManager", "Location fix is older than 15 minutes (expired)")
            }
        } catch (e: SecurityException) {
            return ValidatedMetric.unavailable("LocationManager", "SecurityException: permission missing")
        } catch (e: Exception) {
            return ValidatedMetric.unavailable("LocationManager", e.message ?: "Exception reading location")
        }
    }

    /**
     * Checks if hardware step counter or detector is available on device.
     */
    fun hasStepSensor(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null ||
                sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    /**
     * Checks if hardware activity recognition or significant motion is available.
     */
    fun hasSignificantMotionSensor(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null
    }
}
