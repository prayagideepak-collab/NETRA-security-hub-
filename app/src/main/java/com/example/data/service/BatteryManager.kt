package com.example.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCategory
import com.example.data.model.DataClassification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryState(
    val levelPercent: Int = 100,
    val temperatureC: Float = 0f,
    val voltageMv: Int = 0,
    val isCharging: Boolean = false,
    val plugType: String = "Discharging",
    val health: String = "NORMAL",
    val currentMa: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

class BatteryManager(private val context: Context) {

    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState: StateFlow<BatteryState> = _batteryState.asStateFlow()

    private var lastChargingState: Boolean? = null
    private var lastChargingEventTime = 0L
    private val DEBOUNCE_TIME_MS = 3000L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val tempTenths = intent.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = tempTenths / 10.0f
            val voltageMv = intent.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 0)
            val level = intent.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (scale > 0) (level * 100f / scale) else 100f

            val status = intent.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                    status == AndroidBatteryManager.BATTERY_STATUS_FULL

            val currentTime = System.currentTimeMillis()
            if (isCharging == lastChargingState && (currentTime - lastChargingEventTime) < DEBOUNCE_TIME_MS) {
                return // Debounce duplicate event
            }
            lastChargingState = isCharging
            lastChargingEventTime = currentTime

            val plugged = intent.getIntExtra(AndroidBatteryManager.EXTRA_PLUGGED, -1)
            val plugType = when (plugged) {
                AndroidBatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                AndroidBatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                AndroidBatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> if (isCharging) "Connected" else "Discharging"
            }

            val health = intent.getIntExtra(AndroidBatteryManager.EXTRA_HEALTH, AndroidBatteryManager.BATTERY_HEALTH_UNKNOWN)
            val healthStr = when (health) {
                AndroidBatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
                AndroidBatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
                AndroidBatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
                AndroidBatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLTAGE"
                else -> "NORMAL"
            }

            val bm = context?.getSystemService(Context.BATTERY_SERVICE) as? AndroidBatteryManager
            val currentNowMicroAmps = bm?.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val currentMa = currentNowMicroAmps / 1000f

            _batteryState.value = BatteryState(
                levelPercent = batteryPct.toInt(),
                temperatureC = tempC,
                voltageMv = voltageMv,
                isCharging = isCharging,
                plugType = plugType,
                health = healthStr,
                currentMa = currentMa,
                timestamp = System.currentTimeMillis()
            )
            com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Battery")
            com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Charging")
            com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Temperature")
        }
    }

    private var isRegistered = false

    internal fun getReceiver(): BroadcastReceiver = batteryReceiver

    fun startMonitoring() {
        if (isRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(batteryReceiver, filter)
        if (stickyIntent != null) {
            batteryReceiver.onReceive(context, stickyIntent)
        }
        isRegistered = true
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        isRegistered = false
    }

    fun getAsRawReading(): RawSensorReading {
        val state = _batteryState.value
        val extras = mapOf(
            "batteryPct" to "${state.levelPercent}.0%",
            "voltageMv" to "${state.voltageMv} mV",
            "plugType" to state.plugType,
            "health" to state.health,
            "currentMa" to "%.0f mA".format(state.currentMa)
        )
        return RawSensorReading(
            sensorId = "battery_telemetry",
            name = "Battery & Power Subsystem",
            category = SensorCategory.POWER,
            values = floatArrayOf(state.temperatureC, state.levelPercent.toFloat(), state.voltageMv.toFloat(), state.currentMa),
            unit = "°C",
            timestamp = state.timestamp,
            classification = DataClassification.VERIFIED,
            extraDetails = extras
        )
    }
}
