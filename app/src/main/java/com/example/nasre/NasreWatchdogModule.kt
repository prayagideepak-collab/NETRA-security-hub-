package com.example.nasre

import android.content.Context
import com.example.data.engine.SecurityEngine
import com.example.data.sensor.SensorManager
import com.example.data.service.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class NasreWatchdogModule(
    private val context: Context,
    private val batteryManager: BatteryManager?,
    private val sensorManager: SensorManager?,
    private val securityEngine: SecurityEngine?
) : INasreModule {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    // Wrap existing WatchdogEngine
    private val engine = com.example.data.engine.WatchdogEngine(context, batteryManager, sensorManager, securityEngine)

    override fun initialize() {
        // Initialization logic if any
    }

    override fun start() {
        // Existing engine is already starting on init, we might want to refactor that
    }

    override fun stop() {
        engine.stop()
        scope.cancel()
    }

    override fun getStatus(): String {
        return "Active"
    }
}
