package com.example.data.engine

enum class EngineLifecycleState {
    INITIALIZED,
    RUNNING,
    PAUSED,
    STOPPED,
    DESTROYED
}

interface INetraEngine {
    val engineName: String
    val isRunning: Boolean
        get() = getStatus() == EngineLifecycleState.RUNNING

    fun initialize() {}
    fun startEngine()
    fun pauseEngine() {}
    fun resumeEngine() {}
    fun stopEngine()
    fun destroy() {}
    fun healthCheck(): Boolean = true
    fun getStatus(): EngineLifecycleState = if (isRunning) EngineLifecycleState.RUNNING else EngineLifecycleState.STOPPED

    fun onSystemEvent(event: EngineSystemEvent)
}

enum class EngineSystemEventType {
    POWER_MODE_CHANGED,
    BATTERY_CRITICAL,
    SCREEN_STATE_CHANGED,
    USER_ACTIVITY_CHANGED,
    EMERGENCY_ALERT,
    DIGITAL_WELLNESS_EVENT,
    THERMAL_WARNING
}

data class EngineSystemEvent(
    val type: EngineSystemEventType,
    val payload: Any? = null,
    val timestamp: Long = System.currentTimeMillis()
)

