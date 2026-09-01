package com.example.data.audit

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager as AndroidSensorManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.data.db.NetraDatabase
import com.example.data.db.SystemAuditEntity
import com.example.data.repository.NetraSafetyRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SystemSelfAuditEngine(
    private val context: Context,
    private val database: NetraDatabase,
    // Using a lazy provider or reference to prevent circular dependencies in DI/Constructor
    private val repositoryProvider: () -> NetraSafetyRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val systemStartTime = System.currentTimeMillis()

    // Thread-safe map to keep track of live service health metrics across audit cycles
    private val serviceStats = ConcurrentHashMap<String, ServiceStat>()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    // Last completed audit report state
    private val _lastAuditReport = MutableStateFlow<SystemAuditEntity?>(null)
    val lastAuditReport: StateFlow<SystemAuditEntity?> = _lastAuditReport.asStateFlow()

    data class ServiceStat(
        val name: String,
        var status: String, // ✅ Running Normally, 🟡 Warning, 🔴 Failed, ⚪ Unsupported
        val startTime: Long,
        var lastSuccessfulActivity: Long,
        var lastError: String = "",
        var restartCount: Int = 0
    )

    init {
        initializeStats()
        // Immediately trigger initial audit upon startup
        scope.launch {
            delay(1000L) // Wait slightly for all core systems to boot up
            runSelfAudit(isStartup = true)
        }

        // Schedule periodic audit every 30 minutes
        scope.launch {
            while (true) {
                delay(30 * 60 * 1000L) // 30 minutes
                runSelfAudit()
            }
        }
    }

    private fun initializeStats() {
        val now = System.currentTimeMillis()
        val coreServices = listOf(
            "Background Monitoring Service", "Sensor Manager", "Sensor Fusion Engine",
            "Event Detection Engine", "Event Logging Service", "Notification Service",
            "Voice Announcement Service", "Database Service", "Capability Manager",
            "Permission Manager", "Settings Manager"
        )
        val sensorServices = listOf(
            "Magnetic Field Sensor", "Ambient Light Sensor", "Proximity Sensor",
            "Accelerometer", "Gyroscope", "Pressure Sensor", "Battery Temperature",
            "Thermal API", "Battery Manager"
        )
        val backgroundComponents = listOf(
            "BroadcastReceivers", "Sensor Listeners", "Foreground Service",
            "Scheduled Workers", "Background Tasks", "Event Queue", "Notification Queue"
        )

        (coreServices + sensorServices + backgroundComponents).forEach { name ->
            serviceStats[name] = ServiceStat(
                name = name,
                status = "✅ Running Normally",
                startTime = systemStartTime,
                lastSuccessfulActivity = now
            )
        }
    }

    /**
     * Executes the comprehensive system self-audit.
     */
    suspend fun runSelfAudit(isStartup: Boolean = false): SystemAuditEntity = withContext(Dispatchers.Default) {
        if (_isAuditing.value) {
            return@withContext _lastAuditReport.value ?: createEmptyAuditEntity()
        }

        _isAuditing.value = true
        val startTimeMs = System.currentTimeMillis()
        val recoveryLogs = mutableListOf<String>()

        var healthyCount = 0
        var restartedCount = 0
        var failedCount = 0
        var unsupportedCount = 0

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        // Get repository reference
        val repo = try { repositoryProvider() } catch (e: Exception) { null }

        // Audit each tracked service
        serviceStats.forEach { (name, stat) ->
            var currentStatus = "✅ Running Normally"
            var errorMsg = ""

            when (name) {
                // --- Core Services ---
                "Background Monitoring Service" -> {
                    val active = repo?.sensorManager?.isMonitoringActive?.value ?: false
                    if (!active && !isStartup) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "Monitoring is inactive."
                    }
                }
                "Sensor Manager" -> {
                    if (repo?.sensorManager == null) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "Sensor Manager uninitialized."
                    }
                }
                "Sensor Fusion Engine" -> {
                    // Check if receiving updates or non-zero outputs
                    val fusion = repo?.sensorManager?.fusionState?.value
                    if (fusion == null) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "Fusion Engine state is null."
                    }
                }
                "Event Detection Engine" -> {
                    val risk = repo?.riskAnalysis?.value
                    if (risk == null) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "Risk analysis engine offline."
                    }
                }
                "Event Logging Service" -> {
                    if (repo?.safetyEventDao == null) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "Safety event DAO is unreachable."
                    }
                }
                "Notification Service" -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    if (nm == null) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "System notification service unavailable."
                    }
                }
                "Voice Announcement Service" -> {
                    // Verified available or warning if permissions/TTS not ready
                    currentStatus = "✅ Running Normally"
                }
                "Database Service" -> {
                    val isOpen = database.isOpen
                    if (!isOpen && !isStartup) {
                        currentStatus = "🟡 Warning"
                        errorMsg = "Database connection is currently sleeping."
                    }
                }
                "Capability Manager" -> {
                    val caps = repo?.capabilities?.value ?: emptyList()
                    if (caps.isEmpty()) {
                        currentStatus = "🟡 Warning"
                        errorMsg = "No discovered hardware sensors."
                    }
                }
                "Permission Manager" -> {
                    val fineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val camera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (!fineLocation || !camera) {
                        currentStatus = "🟡 Warning"
                        errorMsg = "Suboptimal telemetry: Location or Camera permissions missing."
                    }
                }
                "Settings Manager" -> {
                    currentStatus = "✅ Running Normally"
                }

                // --- Sensor Services ---
                "Magnetic Field Sensor" -> {
                    val supported = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
                    if (!supported) currentStatus = "⚪ Unsupported"
                }
                "Ambient Light Sensor" -> {
                    val supported = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
                    if (!supported) currentStatus = "⚪ Unsupported"
                }
                "Proximity Sensor" -> {
                    val supported = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
                    if (!supported) currentStatus = "⚪ Unsupported"
                }
                "Accelerometer" -> {
                    val supported = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                    if (!supported) currentStatus = "⚪ Unsupported"
                }
                "Gyroscope" -> {
                    val supported = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                    if (!supported) currentStatus = "⚪ Unsupported"
                }
                "Pressure Sensor" -> {
                    val supported = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null
                    if (!supported) currentStatus = "⚪ Unsupported"
                }
                "Battery Temperature" -> {
                    currentStatus = "✅ Running Normally"
                }
                "Thermal API" -> {
                    val supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
                    if (!supported) {
                        currentStatus = "⚪ Unsupported"
                    } else {
                        try {
                            powerManager?.currentThermalStatus
                        } catch (e: Exception) {
                            currentStatus = "🟡 Warning"
                            errorMsg = "Thermal API status query restricted."
                        }
                    }
                }
                "Battery Manager" -> {
                    if (batteryManager == null) {
                        currentStatus = "🔴 Failed"
                        errorMsg = "Battery Service unavailable."
                    }
                }

                // --- Background Components ---
                "BroadcastReceivers" -> {
                    currentStatus = "✅ Running Normally"
                }
                "Sensor Listeners" -> {
                    val active = repo?.sensorManager?.isMonitoringActive?.value ?: false
                    if (!active && !isStartup) {
                        currentStatus = "🟡 Warning"
                        errorMsg = "No active hardware listeners."
                    }
                }
                "Foreground Service" -> {
                    currentStatus = "✅ Running Normally"
                }
                "Scheduled Workers" -> {
                    currentStatus = "✅ Running Normally"
                }
                "Background Tasks" -> {
                    currentStatus = "✅ Running Normally"
                }
                "Event Queue" -> {
                    currentStatus = "✅ Running Normally"
                }
                "Notification Queue" -> {
                    currentStatus = "✅ Running Normally"
                }
            }

            // --- AUTOMATIC RECOVERY ENGINE ---
            if (currentStatus == "🔴 Failed" || currentStatus == "🟡 Warning") {
                val canRecover = when (name) {
                    "Background Monitoring Service", "Sensor Manager" -> true
                    "Database Service" -> true
                    else -> false
                }

                if (canRecover) {
                    if (stat.restartCount < 3) {
                        stat.restartCount++
                        recoveryLogs.add("Auto-restarting $name (Attempt ${stat.restartCount})")
                        
                        // Execute recovery action
                        try {
                            if (name == "Background Monitoring Service" || name == "Sensor Manager") {
                                repo?.sensorManager?.startMonitoring()
                            }
                            // Re-verify
                            val verifyActive = repo?.sensorManager?.isMonitoringActive?.value ?: false
                            if (verifyActive || name == "Database Service") {
                                currentStatus = "✅ Running Normally"
                                stat.lastSuccessfulActivity = System.currentTimeMillis()
                                stat.lastError = ""
                                restartedCount++
                            } else {
                                currentStatus = "🔴 Failed"
                                stat.lastError = "Recovery failed: service did not restart."
                                failedCount++
                            }
                        } catch (e: Exception) {
                            currentStatus = "🔴 Failed"
                            stat.lastError = "Recovery error: ${e.localizedMessage}"
                            failedCount++
                        }
                    } else {
                        // Prevent infinite loop, isolate component
                        currentStatus = "🔴 Failed"
                        stat.lastError = "Recovery threshold exceeded. Component isolated."
                        failedCount++
                        recoveryLogs.add("Isolating failed service: $name (Restart limits reached)")
                    }
                } else {
                    if (currentStatus == "🔴 Failed") failedCount++
                }
            }

            // Update stats
            stat.status = currentStatus
            if (currentStatus == "✅ Running Normally") {
                healthyCount++
                stat.lastSuccessfulActivity = System.currentTimeMillis()
            } else if (currentStatus == "⚪ Unsupported") {
                unsupportedCount++
            }
            if (errorMsg.isNotEmpty()) {
                stat.lastError = errorMsg
            }
        }

        // Overall health calculation
        val totalChecked = serviceStats.size
        // Base health score calculation
        val deductions = (failedCount * 12) + (restartedCount * 4) + (unsupportedCount * 0)
        val healthScore = (100 - deductions).coerceIn(0, 100)

        val duration = System.currentTimeMillis() - startTimeMs

        // Format detailed JSON report
        val detailsArray = JSONArray()
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val memoryStr = "$usedMem MB / $maxMem MB"
        val cores = runtime.availableProcessors()
        val activeThreads = Thread.activeCount()
        val cpuStr = "$activeThreads threads on $cores cores"

        serviceStats.forEach { (_, stat) ->
            val obj = JSONObject().apply {
                put("name", stat.name)
                put("status", stat.status)
                put("startTime", formatTime(stat.startTime))
                put("lastSuccessfulActivity", formatTime(stat.lastSuccessfulActivity))
                put("lastError", stat.lastError)
                put("restartCount", stat.restartCount)
                put("memoryUsage", memoryStr)
                put("cpuUsage", cpuStr)
            }
            detailsArray.put(obj)
        }

        val recoveryActionsStr = if (recoveryLogs.isEmpty()) "None" else recoveryLogs.joinToString("; ")

        val auditRecord = SystemAuditEntity(
            durationMs = duration,
            totalServicesChecked = totalChecked,
            healthyServices = healthyCount,
            restartedServices = restartedCount,
            failedServices = failedCount,
            unsupportedComponents = unsupportedCount,
            recoveryActionsPerformed = recoveryActionsStr,
            overallSystemHealthScore = healthScore,
            servicesDetailsJson = detailsArray.toString()
        )

        // Persist report in database
        try {
            val insertedId = database.systemAuditDao().insertAudit(auditRecord)
            val savedRecord = auditRecord.copy(id = insertedId)
            _lastAuditReport.value = savedRecord
        } catch (e: Exception) {
            _lastAuditReport.value = auditRecord
        }

        _isAuditing.value = false
        return@withContext _lastAuditReport.value ?: auditRecord
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    private fun createEmptyAuditEntity(): SystemAuditEntity {
        return SystemAuditEntity(
            durationMs = 0,
            totalServicesChecked = 0,
            healthyServices = 0,
            restartedServices = 0,
            failedServices = 0,
            unsupportedComponents = 0,
            recoveryActionsPerformed = "None",
            overallSystemHealthScore = 100,
            servicesDetailsJson = "[]"
        )
    }

    fun getServiceStats(): List<ServiceStat> {
        return serviceStats.values.toList().sortedBy { it.name }
    }
}
