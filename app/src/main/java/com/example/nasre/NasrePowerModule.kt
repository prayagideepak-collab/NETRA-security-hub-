package com.example.nasre

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.example.NetraForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Monitors power state and restores services on charger connection.
 */
class NasrePowerModule(
    private val context: Context
) : INasreModule {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.d("NasrePowerModule", "Power connected. Restoring services...")
                    restoreServices()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.d("NasrePowerModule", "Power disconnected.")
                }
            }
        }
    }

    private fun restoreServices() {
        scope.launch {
            // Restore required components
            val serviceIntent = Intent(context, NetraForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("NasrePowerModule", "Failed to restore service: ${e.message}")
            }
            // Additional components can be checked here
        }
    }

    override fun initialize() {
        // Initialization
    }

    override fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(powerReceiver, filter)
    }

    override fun stop() {
        try {
            context.unregisterReceiver(powerReceiver)
        } catch (e: Exception) {
            Log.e("NasrePowerModule", "Failed to unregister receiver: ${e.message}")
        }
    }

    override fun getStatus(): String = "Active"
}
