package com.example.nasre

import android.content.Context
import com.example.data.engine.SecurityEngine
import com.example.data.sensor.SensorManager
import com.example.data.service.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Netra Autonomous Self-Repair Engine (NASRE)
 * Core Engine Orchestrator
 */
class NasreEngine(
    private val context: Context,
    private val batteryManager: BatteryManager?,
    private val sensorManager: SensorManager?,
    private val securityEngine: SecurityEngine?
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Core Modules
    private val healthMonitor = NasreHealthMonitor(context)
    private val watchdog = NasreWatchdogModule(context, batteryManager, sensorManager, securityEngine)
    private val analyzer = RootCauseAnalyzer(context)
    private val repairEngine = NasreSelfRepairEngine(context)
    private val optimizer = ResourceOptimizer(context)
    private val logger = DiagnosticLogger(context)
    private val powerModule = NasrePowerModule(context)

    companion object {
        @Volatile
        private var INSTANCE: NasreEngine? = null

        fun getInstance(
            context: Context,
            batteryManager: BatteryManager?,
            sensorManager: SensorManager?,
            securityEngine: SecurityEngine?
        ): NasreEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NasreEngine(context, batteryManager, sensorManager, securityEngine).also { INSTANCE = it }
            }
        }
    }

    init {
        healthMonitor.initialize()
        watchdog.initialize()
        analyzer.initialize()
        repairEngine.initialize()
        optimizer.initialize()
        logger.initialize()
        powerModule.initialize()
    }

    fun start() {
        healthMonitor.start()
        watchdog.start()
        analyzer.start()
        repairEngine.start()
        optimizer.start()
        logger.start()
        powerModule.start()
    }

    fun stop() {
        healthMonitor.stop()
        watchdog.stop()
        analyzer.stop()
        repairEngine.stop()
        optimizer.stop()
        logger.stop()
        powerModule.stop()
    }
}
