package com.example.data.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PowerManagerEngine : INetraEngine {
    override val engineName: String = "PowerManagerEngine"
    override var isRunning: Boolean = true
        private set

    override fun startEngine() {
        isRunning = true
    }

    override fun stopEngine() {
        isRunning = false
    }

    override fun onSystemEvent(event: EngineSystemEvent) {
        if (event.type == EngineSystemEventType.SCREEN_STATE_CHANGED) {
            val isScreenOn = event.payload as? Boolean ?: true
            val mode = determineMode(isScreenOn = isScreenOn, isCharging = false, isMotionDetected = false)
            updateMode(mode)
        }
    }

    init {
        EngineCoordinator.registerEngine(this)
    }

    private val _powerMode = MutableStateFlow(PowerMode.ACTIVE)
    val powerMode: StateFlow<PowerMode> = _powerMode.asStateFlow()

    fun updateMode(newMode: PowerMode) {
        if (_powerMode.value != newMode) {
            _powerMode.value = newMode
            EngineCoordinator.dispatchEvent(
                EngineSystemEvent(
                    type = EngineSystemEventType.POWER_MODE_CHANGED,
                    payload = newMode
                )
            )
        }
    }

    // Logic to determine mode based on context
    fun determineMode(isScreenOn: Boolean, isCharging: Boolean, isMotionDetected: Boolean): PowerMode {
        return when {
            isScreenOn -> PowerMode.ACTIVE
            isCharging && !isMotionDetected -> PowerMode.ADAPTIVE_QUIET
            isMotionDetected -> PowerMode.PASSIVE
            else -> PowerMode.IDLE
        }
    }
}
