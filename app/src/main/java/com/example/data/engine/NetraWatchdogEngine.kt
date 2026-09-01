package com.example.data.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class WatchdogModuleState(
    val name: String,
    val lastUpdateTimestamp: Long,
    val sequenceNumber: Int,
    val sensorEventTimestamp: Long,
    val status: String, // Active, Refreshing, Error, Recovery Failed
    val isRefreshing: Boolean = false,
    val lastErrorMessage: String? = null
)

object NetraWatchdogEngine {
    private var instance: WatchdogEngine? = null

    private val _emptyStates = MutableStateFlow<Map<String, WatchdogModuleState>>(emptyMap())

    val moduleStates: StateFlow<Map<String, WatchdogModuleState>>
        get() = instance?.moduleStates ?: _emptyStates

    fun initialize(
        ctx: Context,
        batteryManager: com.example.data.service.BatteryManager?,
        sensorManager: com.example.data.sensor.SensorManager?,
        securityEngine: com.example.data.engine.SecurityEngine?
    ) {
        val engine = WatchdogEngine(ctx, batteryManager, sensorManager, securityEngine)
        instance = engine
    }

    fun notifyUpdate(moduleName: String, sensorEventTime: Long = System.currentTimeMillis()) {
        instance?.notifyUpdate(moduleName, sensorEventTime)
    }

    fun forceStale(moduleName: String) {
        instance?.forceStale(moduleName)
    }
}
