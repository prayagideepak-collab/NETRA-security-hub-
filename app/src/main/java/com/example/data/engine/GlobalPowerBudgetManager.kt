package com.example.data.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PowerBudgetState(
    val currentPowerMode: PowerMode = PowerMode.ACTIVE,
    val sensorSamplingIntervalMs: Long = 100L,
    val isThermalThrottling: Boolean = false,
    val isBatterySaverActive: Boolean = false,
    val batteryLevelPct: Int = 100,
    val batteryTempC: Float = 28.0f
)

class GlobalPowerBudgetManager : INetraEngine {
    override val engineName: String = "GlobalPowerBudgetManager"
    override var isRunning: Boolean = true
        private set

    private val _budgetState = MutableStateFlow(PowerBudgetState())
    val budgetState: StateFlow<PowerBudgetState> = _budgetState.asStateFlow()

    override fun startEngine() {
        isRunning = true
    }

    override fun stopEngine() {
        isRunning = false
    }

    override fun onSystemEvent(event: EngineSystemEvent) {
        when (event.type) {
            EngineSystemEventType.POWER_MODE_CHANGED -> {
                val mode = event.payload as? PowerMode ?: PowerMode.ACTIVE
                updatePowerMode(mode)
            }
            EngineSystemEventType.BATTERY_CRITICAL -> {
                updateBatteryStatus(levelPct = 15, isCritical = true)
            }
            else -> {}
        }
    }

    init {
        EngineCoordinator.registerEngine(this)
    }

    fun updatePowerMode(mode: PowerMode) {
        val interval = when (mode) {
            PowerMode.ACTIVE -> 100L
            PowerMode.PASSIVE -> 250L
            PowerMode.ADAPTIVE_QUIET -> 500L
            PowerMode.IDLE -> 2000L
        }
        _budgetState.value = _budgetState.value.copy(
            currentPowerMode = mode,
            sensorSamplingIntervalMs = interval
        )
    }

    fun updateBatteryAndThermal(batteryPct: Int, batteryTempC: Float) {
        val isThermalThrottling = batteryTempC >= 42.0f
        val isBatterySaverActive = batteryPct <= 20

        val mode = when {
            isThermalThrottling || isBatterySaverActive -> PowerMode.ADAPTIVE_QUIET
            else -> _budgetState.value.currentPowerMode
        }

        val interval = when {
            isThermalThrottling -> 1000L
            isBatterySaverActive -> 1500L
            else -> when (mode) {
                PowerMode.ACTIVE -> 100L
                PowerMode.PASSIVE -> 250L
                PowerMode.ADAPTIVE_QUIET -> 500L
                PowerMode.IDLE -> 2000L
            }
        }

        _budgetState.value = _budgetState.value.copy(
            batteryLevelPct = batteryPct,
            batteryTempC = batteryTempC,
            isThermalThrottling = isThermalThrottling,
            isBatterySaverActive = isBatterySaverActive,
            currentPowerMode = mode,
            sensorSamplingIntervalMs = interval
        )
    }

    fun updateBatteryStatus(levelPct: Int, isCritical: Boolean) {
        if (isCritical) {
            updatePowerMode(PowerMode.ADAPTIVE_QUIET)
        }
    }
}
