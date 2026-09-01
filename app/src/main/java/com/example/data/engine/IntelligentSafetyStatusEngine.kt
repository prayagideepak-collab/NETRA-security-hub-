package com.example.data.engine

import com.example.data.model.ModuleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Intelligent Safety Status Engine (ISSE)
 * 
 * Evaluates the health and status of all monitored safety modules.
 */
object IntelligentSafetyStatusEngine {

    // Represents the overall status
    enum class SystemSafetyStatus {
        NORMAL, WARNING, CRITICAL
    }

    // Individual module health tracking
    private val _moduleStates = MutableStateFlow<Map<String, ModuleState>>(emptyMap())
    val moduleStates: StateFlow<Map<String, ModuleState>> = _moduleStates.asStateFlow()

    // Aggregate system status
    private val _systemStatus = MutableStateFlow(SystemSafetyStatus.NORMAL)
    val systemStatus: StateFlow<SystemSafetyStatus> = _systemStatus.asStateFlow()

    fun updateModuleState(moduleName: String, state: ModuleState) {
        _moduleStates.update { currentMap ->
            currentMap.toMutableMap().apply { this[moduleName] = state }
        }
        recalculateSystemStatus()
    }

    private fun recalculateSystemStatus() {
        val states = _moduleStates.value.values
        val newStatus = when {
            states.any { it == ModuleState.OFFLINE || it == ModuleState.PERMISSION_MISSING } -> SystemSafetyStatus.CRITICAL
            states.any { it == ModuleState.WARNING || it == ModuleState.DELAYED } -> SystemSafetyStatus.WARNING
            else -> SystemSafetyStatus.NORMAL
        }
        _systemStatus.value = newStatus
    }
}
