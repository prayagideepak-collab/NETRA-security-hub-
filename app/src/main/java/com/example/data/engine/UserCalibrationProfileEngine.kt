package com.example.data.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CalibrationProfile(
    val age: Int = 30,
    val gender: String = "Not Specified",
    val heightCm: Float = 170.0f
)

class UserCalibrationProfileEngine : INetraEngine {
    override val engineName: String = "UserCalibrationProfileEngine"
    override var isRunning: Boolean = true
        private set

    private var lifecycleState: EngineLifecycleState = EngineLifecycleState.RUNNING

    private val _userProfile = MutableStateFlow(CalibrationProfile())
    val userProfile: StateFlow<CalibrationProfile> = _userProfile.asStateFlow()

    override fun initialize() {
        lifecycleState = EngineLifecycleState.INITIALIZED
    }

    override fun startEngine() {
        isRunning = true
        lifecycleState = EngineLifecycleState.RUNNING
    }

    override fun pauseEngine() {
        lifecycleState = EngineLifecycleState.PAUSED
    }

    override fun resumeEngine() {
        lifecycleState = EngineLifecycleState.RUNNING
    }

    override fun stopEngine() {
        isRunning = false
        lifecycleState = EngineLifecycleState.STOPPED
    }

    override fun getStatus(): EngineLifecycleState = lifecycleState

    override fun healthCheck(): Boolean = isRunning

    override fun onSystemEvent(event: EngineSystemEvent) {
        // Handle profile updates or recalibrations if needed
    }

    init {
        EngineCoordinator.registerEngine(this)
    }

    fun updateProfile(age: Int, gender: String, heightCm: Float) {
        _userProfile.value = CalibrationProfile(age, gender, heightCm)
    }
}
