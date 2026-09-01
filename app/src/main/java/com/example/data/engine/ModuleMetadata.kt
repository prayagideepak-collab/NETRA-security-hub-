package com.example.data.engine

enum class ModuleType { CONTINUOUS, EVENT_BASED, SCHEDULED, PASSIVE, MANUAL }

enum class ModuleStatus { INITIALIZING, RUNNING, IDLE, SLEEPING, WAITING, VERIFYING, RECOVERING, RECOVERED, STOPPED }

data class ModuleMetadata(
    val name: String,
    val type: ModuleType,
    var status: ModuleStatus = ModuleStatus.RUNNING,
    var lastHeartbeat: Long = System.currentTimeMillis(),
    var lastSensorRead: Long = System.currentTimeMillis(),
    var lastUiUpdate: Long = System.currentTimeMillis(),
    var lastBroadcast: Long = System.currentTimeMillis(),
    var failureLevel: Int = 0, // 0: Healthy, 1: Warning, 2: Verify, 3: Recovery
    var lastRecoveryTime: Long = 0L,
    var recoveryCountToday: Int = 0
)
