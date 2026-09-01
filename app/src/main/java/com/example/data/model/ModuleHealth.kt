package com.example.data.model

enum class ModuleState {
    HEALTHY, WARNING, DELAYED, OFFLINE, DISABLED, PERMISSION_MISSING, RECOVERING
}

data class ModuleHealth(
    val name: String,
    val state: ModuleState,
    val lastCheck: Long,
    val lastRecovery: Long? = null,
    val heartbeat: Long = System.currentTimeMillis()
)
