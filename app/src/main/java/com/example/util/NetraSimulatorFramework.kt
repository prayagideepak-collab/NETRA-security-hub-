package com.example.util

import com.example.data.engine.*
import com.example.data.model.RawSensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimulationScenario(
    val id: String,
    val name: String,
    val description: String,
    val batteryPct: Int = 100,
    val batteryTempC: Float = 28.0f,
    val isScreenOn: Boolean = true,
    val isEmergency: Boolean = false
)

object NetraSimulatorFramework {

    private val _currentScenario = MutableStateFlow<SimulationScenario?>(null)
    val currentScenario: StateFlow<SimulationScenario?> = _currentScenario.asStateFlow()

    private val presetScenarios = listOf(
        SimulationScenario(
            id = "SCENARIO_NORMAL_DESK",
            name = "Normal Stationary Office Desk Work",
            description = "Battery 85%, Temp 29°C, Stationary standing/sitting, Screen ON.",
            batteryPct = 85,
            batteryTempC = 29.0f,
            isScreenOn = true
        ),
        SimulationScenario(
            id = "SCENARIO_THERMAL_STRESS",
            name = "Thermal Stress Alert (>42°C)",
            description = "Battery 18%, Temp 44.5°C, Thermal throttling & Lite Mode trigger.",
            batteryPct = 18,
            batteryTempC = 44.5f,
            isScreenOn = false
        ),
        SimulationScenario(
            id = "SCENARIO_CRITICAL_EMERGENCY",
            name = "Critical System Security Alert",
            description = "Emergency Alert trigger with highest event priority.",
            batteryPct = 40,
            batteryTempC = 35.0f,
            isEmergency = true
        )
    )

    fun getPresetScenarios(): List<SimulationScenario> = presetScenarios

    fun applyScenario(
        scenario: SimulationScenario,
        powerManager: GlobalPowerBudgetManager? = null
    ) {
        _currentScenario.value = scenario

        powerManager?.updateBatteryAndThermal(
            batteryPct = scenario.batteryPct,
            batteryTempC = scenario.batteryTempC
        )

        EngineCoordinator.dispatchEvent(
            EngineSystemEvent(
                type = EngineSystemEventType.SCREEN_STATE_CHANGED,
                payload = scenario.isScreenOn
            )
        )

        if (scenario.isEmergency) {
            EngineCoordinator.dispatchEvent(
                EngineSystemEvent(
                    type = EngineSystemEventType.EMERGENCY_ALERT,
                    payload = scenario
                )
            )
        }
    }
}
