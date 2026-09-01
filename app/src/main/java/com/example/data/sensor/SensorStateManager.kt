package com.example.data.sensor

import com.example.data.model.SensorSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the "Single Source of Truth" for Netra sensor and system states.
 */
class SensorStateManager {
    private val _currentSnapshot = MutableStateFlow(SensorSnapshot())
    val currentSnapshot: StateFlow<SensorSnapshot> = _currentSnapshot.asStateFlow()

    fun updateSnapshot(updater: (SensorSnapshot) -> SensorSnapshot) {
        _currentSnapshot.update(updater)
    }
}
