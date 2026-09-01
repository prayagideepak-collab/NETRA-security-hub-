package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.repository.NetraSafetyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetraForegroundService : Service() {

    private val CHANNEL_ID = "NetraForegroundServiceChannel"
    private var repository: NetraSafetyRepository? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)


    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        repository = NetraSafetyRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (hasLocationPermission) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(1, notification)
                }
            } catch (e: Exception) {
                try {
                    startForeground(1, notification)
                } catch (_: Exception) {}
            }
        } else {
            startForeground(1, notification)
        }

        if (!isStarted) {
            isStarted = true
            com.example.data.engine.EngineCoordinator.startAllEngines()
            startHeartbeatLoop()
        }
        
        Log.d("NetraForegroundService", "NetraForegroundService started with START_STICKY heartbeat active.")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Perform heartbeat health check & sensor monitoring check
                    Log.d("NetraForegroundService", "Heartbeat tick: Background monitoring active, sensors and AI engine running.")
                    
                    if (repository == null) {
                        repository = NetraSafetyRepository(applicationContext)
                    }
                    
                    repository?.sensorManager?.let { sensorMgr ->
                        // Ensure sensor manager is active
                        if (!sensorMgr.isMonitoringActive.value) {
                            Log.w("NetraForegroundService", "Heartbeat detected sensor manager inactive. Restoring monitoring...")
                            sensorMgr.startMonitoring()
                        }
                    }

                    // Adaptive Power Intelligence Engine (NAPIE) context analysis
                    val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    val isScreenOn = powerManager.isInteractive
                    val isCharging = repository?.batteryManager?.batteryState?.value?.isCharging ?: false
                    val isMotionDetected = repository?.sensorManager?.fusionState?.value?.isDrivingConfirmed ?: false
                    
                    val newMode = repository?.powerManagerEngine?.determineMode(isScreenOn, isCharging, isMotionDetected)
                    newMode?.let { repository?.powerManagerEngine?.updateMode(it) }

                } catch (e: Exception) {
                    Log.e("NetraForegroundService", "Error in heartbeat loop: ${e.message}", e)
                }
                delay(30_000L) // 30 second heartbeat interval
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d("NetraForegroundService", "NetraForegroundService destroyed. Attempting cleanup.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Netra Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netra System Sentinel")
            .setContentText("Continuous AI Sensor Hub & Background Protection Active")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setOngoing(true)
            .build()
    }
}

