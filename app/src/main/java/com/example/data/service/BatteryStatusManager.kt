package com.example.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PowerThreshold {
    CRITICAL, // < 15%
    LOW,      // 15% - 30%
    NORMAL    // > 30%
}

data class BatteryStatusInfo(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val voltageMv: Int = 0,
    val temperatureC: Float = 0f,
    val powerThreshold: PowerThreshold = PowerThreshold.NORMAL,
    val recommendedPollingIntervalMs: Long = 1000L,
    val timestamp: Long = System.currentTimeMillis()
)

class BatteryStatusManager(private val context: Context) {

    private val _batteryStatus = MutableStateFlow(BatteryStatusInfo())
    val batteryStatus: StateFlow<BatteryStatusInfo> = _batteryStatus.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, -1)
            val levelPercent = if (scale > 0) (level * 100 / scale) else 100

            val status = intent.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                    status == AndroidBatteryManager.BATTERY_STATUS_FULL

            val voltageMv = intent.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 0)
            val tempTenths = intent.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 0)
            val temperatureC = tempTenths / 10.0f

            val threshold = when {
                levelPercent < 15 -> PowerThreshold.CRITICAL
                levelPercent < 30 -> PowerThreshold.LOW
                else -> PowerThreshold.NORMAL
            }

            // Dynamically adjust sensor polling frequency based on power thresholds and charging state
            val pollingInterval = when {
                isCharging -> 500L          // Fast polling when charging
                threshold == PowerThreshold.CRITICAL -> 15000L // Conservative polling under critical battery
                threshold == PowerThreshold.LOW -> 5000L       // Moderate polling under low battery
                else -> 1000L                                   // Standard normal polling
            }

            _batteryStatus.value = BatteryStatusInfo(
                levelPercent = levelPercent,
                isCharging = isCharging,
                voltageMv = voltageMv,
                temperatureC = temperatureC,
                powerThreshold = threshold,
                recommendedPollingIntervalMs = pollingInterval,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private var isRegistered = false

    fun startMonitoring() {
        if (isRegistered) return
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            isRegistered = true
        } catch (e: Exception) {
            // Fallback or ignore if context not ready
        }
    }

    fun stopMonitoring() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(batteryReceiver)
            isRegistered = false
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }
}
