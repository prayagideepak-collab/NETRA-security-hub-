package com.example.data.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

data class EngineStatusRecord(
    val name: String,
    val state: EngineLifecycleState,
    val isHealthy: Boolean,
    val registeredAtMs: Long = System.currentTimeMillis()
)

object EngineCoordinator {
    private val registeredEngines = ConcurrentHashMap<String, INetraEngine>()
    private val engineStatuses = ConcurrentHashMap<String, EngineStatusRecord>()
    private val _eventFlow = MutableSharedFlow<EngineSystemEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<EngineSystemEvent> = _eventFlow.asSharedFlow()

    fun registerEngine(engine: INetraEngine): Boolean {
        if (registeredEngines.containsKey(engine.engineName)) {
            // Reject duplicate engine instance registration
            return false
        }
        registeredEngines[engine.engineName] = engine
        engine.initialize()
        
        val initialRecord = EngineStatusRecord(
            name = engine.engineName,
            state = EngineLifecycleState.INITIALIZED,
            isHealthy = engine.healthCheck()
        )
        engineStatuses[engine.engineName] = initialRecord

        ModuleRegistry.registerModule(
            ModuleMetadata(
                name = engine.engineName,
                type = ModuleType.EVENT_BASED,
                status = ModuleStatus.IDLE
            )
        )
        return true
    }

    fun unregisterEngine(engineName: String) {
        registeredEngines[engineName]?.stopEngine()
        registeredEngines[engineName]?.destroy()
        registeredEngines.remove(engineName)
        engineStatuses.remove(engineName)
    }

    fun dispatchEvent(event: EngineSystemEvent) {
        _eventFlow.tryEmit(event)
        registeredEngines.values.forEach { engine ->
            try {
                engine.onSystemEvent(event)
            } catch (e: Exception) {
                // Log isolated engine event failure without interrupting coordinate loop
            }
        }
    }

    fun startAllEngines() {
        registeredEngines.values.forEach { engine ->
            try {
                engine.startEngine()
                engineStatuses[engine.engineName] = EngineStatusRecord(
                    name = engine.engineName,
                    state = EngineLifecycleState.RUNNING,
                    isHealthy = engine.healthCheck()
                )
                ModuleRegistry.updateStatus(engine.engineName, ModuleStatus.RUNNING)
            } catch (e: Exception) {
                ModuleRegistry.updateStatus(engine.engineName, ModuleStatus.RECOVERING)
            }
        }
    }

    fun pauseAllEngines() {
        registeredEngines.values.forEach { engine ->
            try {
                engine.pauseEngine()
                engineStatuses[engine.engineName] = EngineStatusRecord(
                    name = engine.engineName,
                    state = EngineLifecycleState.PAUSED,
                    isHealthy = engine.healthCheck()
                )
            } catch (e: Exception) {
                // Isolated handle
            }
        }
    }

    fun resumeAllEngines() {
        registeredEngines.values.forEach { engine ->
            try {
                engine.resumeEngine()
                engineStatuses[engine.engineName] = EngineStatusRecord(
                    name = engine.engineName,
                    state = EngineLifecycleState.RUNNING,
                    isHealthy = engine.healthCheck()
                )
            } catch (e: Exception) {
                // Isolated handle
            }
        }
    }

    fun stopAllEngines() {
        registeredEngines.values.forEach { engine ->
            try {
                engine.stopEngine()
                engineStatuses[engine.engineName] = EngineStatusRecord(
                    name = engine.engineName,
                    state = EngineLifecycleState.STOPPED,
                    isHealthy = true
                )
                ModuleRegistry.updateStatus(engine.engineName, ModuleStatus.STOPPED)
            } catch (e: Exception) {
                // Safe shutdown
            }
        }
    }

    fun performHealthChecks(): Map<String, Boolean> {
        val healthMap = mutableMapOf<String, Boolean>()
        registeredEngines.values.forEach { engine ->
            val healthy = try { engine.healthCheck() } catch (e: Exception) { false }
            healthMap[engine.engineName] = healthy
            val current = engineStatuses[engine.engineName]
            if (current != null) {
                engineStatuses[engine.engineName] = current.copy(isHealthy = healthy)
            }
        }
        return healthMap
    }

    fun getEngine(name: String): INetraEngine? = registeredEngines[name]
    fun getAllEngines(): Map<String, INetraEngine> = registeredEngines
    fun getEngineStatuses(): Map<String, EngineStatusRecord> = engineStatuses
}

