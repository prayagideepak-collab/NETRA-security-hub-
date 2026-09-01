package com.example.nasre

import android.content.Context
import android.util.Log
import com.example.data.repository.NetraSafetyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors the health of all systems
 */
class NasreHealthMonitor(
    private val context: Context
) : INasreModule {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var repository: NetraSafetyRepository? = null

    override fun initialize() {
        repository = NetraSafetyRepository(context)
    }

    override fun start() {
        scope.launch {
            while (isActive) {
                performHealthCheck()
                delay(30_000L) // 30 second health check interval
            }
        }
    }

    private fun performHealthCheck() {
        Log.d("NasreHealthMonitor", "Performing health check...")
        
        if (repository == null) {
            repository = NetraSafetyRepository(context)
        }
        
        repository?.sensorManager?.let { sensorMgr ->
            // Ensure sensor manager is active
            if (!sensorMgr.isMonitoringActive.value) {
                Log.w("NasreHealthMonitor", "Health check detected sensor manager inactive. Restoring monitoring...")
                sensorMgr.startMonitoring()
            }
        }
        // Add more health checks as needed
    }

    override fun stop() {
        // Stop monitoring
    }

    override fun getStatus(): String {
        return "Active"
    }
}
