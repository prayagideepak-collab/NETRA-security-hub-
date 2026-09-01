package com.example.data.engine

import java.util.concurrent.ConcurrentHashMap

object ModuleRegistry {
    private val modules = ConcurrentHashMap<String, ModuleMetadata>()

    fun registerModule(metadata: ModuleMetadata) {
        modules[metadata.name] = metadata
    }

    fun getModule(name: String): ModuleMetadata? = modules[name]
    fun getAllModules(): Map<String, ModuleMetadata> = modules
    fun updateStatus(name: String, status: ModuleStatus) {
        modules[name]?.status = status
    }
    fun updateHeartbeat(name: String) {
        modules[name]?.lastHeartbeat = System.currentTimeMillis()
    }
}
