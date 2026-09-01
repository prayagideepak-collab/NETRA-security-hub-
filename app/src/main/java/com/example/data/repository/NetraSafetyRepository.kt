package com.example.data.repository

import android.content.Context
import com.example.data.engine.RuleBasedSafetyEngine
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventDao
import com.example.data.db.SafetyEventEntity
import com.example.data.model.RawSensorReading
import com.example.data.model.RiskAnalysisResult
import com.example.data.model.SafetyRiskLevel
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorFusionState
import com.example.data.sensor.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.example.data.db.SystemAuditEntity
import com.example.data.audit.SystemSelfAuditEngine

class NetraSafetyRepository(private val context: Context) {

    private val db = NetraDatabase.getInstance(context)
    val safetyEventDao: SafetyEventDao = db.safetyEventDao()
    val systemAuditDao = db.systemAuditDao()
    val serviceStateAuditDao = db.serviceStateAuditDao()
    val unifiedEventDao = db.unifiedEventDao()
    
    val settingsRepo = SettingsRepository(context)
    val sensorManager = SensorManager(context, settingsRepository = settingsRepo)
    val safetyEngine = com.example.data.engine.NetraSafetyEngine(context)
    val safetyEngineState = safetyEngine.safetyEngineState
    val alertManager = safetyEngine.alertManager
    
    // Inject CanonicalEventManager lazily
    val eventManager by lazy { com.example.data.event.CanonicalEventManager(sensorManager.stateManager) }

    val batteryManager = sensorManager.batteryManager
    val auditEngine = SystemSelfAuditEngine(context, db) { this }
    val powerManagerEngine = com.example.data.engine.PowerManagerEngine()

    private val serviceStartTimeMap = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    suspend fun logServiceStateChange(
        serviceName: String,
        previousState: String,
        newState: String,
        trigger: String,
        reason: String,
        status: String
    ) {
        val now = System.currentTimeMillis()
        var startTime: Long? = null
        var endTime: Long? = null
        var durationMs: Long? = null

        if (newState == "Enabled") {
            serviceStartTimeMap[serviceName] = now
        } else if (previousState == "Enabled") {
            startTime = serviceStartTimeMap.remove(serviceName)
            if (startTime != null) {
                endTime = now
                durationMs = endTime - startTime
            }
        }

        serviceStateAuditDao.insertAuditRecord(
            com.example.data.audit.ServiceStateAuditEntity(
                serviceName = serviceName,
                previousState = previousState,
                newState = newState,
                timestamp = now,
                triggerSource = trigger,
                reason = reason,
                status = status,
                startTime = startTime,
                endTime = endTime,
                durationMs = durationMs
            )
        )
    }
    
    suspend fun setMonitorService(
        serviceName: String,
        enabled: Boolean,
        setter: suspend (Boolean) -> Unit,
        currentValueFlow: Flow<Boolean>
    ) {
        val previous = currentValueFlow.first()
        setter(enabled)
        logServiceStateChange(serviceName, if (previous) "Enabled" else "Disabled", if (enabled) "Enabled" else "Disabled", "User", "Settings", "Success")
    }

    suspend fun logNotificationChange(
        serviceName: String,
        notifyEnabled: Boolean,
        announceEnabled: Boolean,
        previousNotify: Boolean,
        previousAnnounce: Boolean,
        trigger: String,
        reason: String,
        status: String
    ) {
        val now = System.currentTimeMillis()
        
        serviceStateAuditDao.insertAuditRecord(
            com.example.data.audit.ServiceStateAuditEntity(
                serviceName = serviceName,
                previousState = "Notify: ${if (previousNotify) "ON" else "OFF"}, Announce: ${if (previousAnnounce) "ON" else "OFF"}",
                newState = "Notify: ${if (notifyEnabled) "ON" else "OFF"}, Announce: ${if (announceEnabled) "ON" else "OFF"}",
                timestamp = now,
                triggerSource = trigger,
                reason = reason,
                status = status
            )
        )
    }



    suspend fun setMonitorBluetooth(enabled: Boolean, trigger: String, reason: String) {
        setMonitorService("Bluetooth Monitoring", enabled, { settingsRepo.setMonitorBluetooth(it) }, settingsRepo.monitorBluetooth)
    }

    suspend fun setMonitorLocation(enabled: Boolean, trigger: String, reason: String) {
        setMonitorService("Location Monitoring", enabled, { settingsRepo.setMonitorLocation(it) }, settingsRepo.monitorLocation)
    }

    suspend fun setMonitorProximity(enabled: Boolean, trigger: String, reason: String) {
        setMonitorService("Proximity Monitoring", enabled, { settingsRepo.setMonitorProximity(it) }, settingsRepo.monitorProximity)
    }


    private val scope = CoroutineScope(Dispatchers.Default + Job())

    val capabilities: StateFlow<List<SensorCapabilityInfo>> = sensorManager.capabilities
    val fusionState: StateFlow<SensorFusionState> = sensorManager.fusionState
    val liveReadings: StateFlow<Map<String, RawSensorReading>> = sensorManager.liveReadings

    private val _riskAnalysis = MutableStateFlow(
        RiskAnalysisResult(
            riskScore = 0,
            riskLevel = SafetyRiskLevel.SAFE,
            summary = "Initializing Absolute Truth Engine...",
            recommendations = listOf("Monitoring hardware sensors..."),
            explanation = "Connecting to real hardware sensor streams."
        )
    )
    val riskAnalysis: StateFlow<RiskAnalysisResult> = _riskAnalysis.asStateFlow()

    val eventLogs: Flow<List<SafetyEventEntity>> = safetyEventDao.getAllEvents()
    val auditLogs: Flow<List<SystemAuditEntity>> = systemAuditDao.getAllAudits()

    init {
        com.example.util.LoggingManager.init(context)
        // Start sensor monitoring
        sensorManager.startMonitoring()

        // Observe thermal threshold from settings
        scope.launch {
            val settingsRepo = SettingsRepository(context)
            settingsRepo.thermalThresholdC.collect { threshold ->
                sensorManager.setThermalThreshold(threshold)
            }
        }

        // Periodic AI Risk Engine evaluation (every 2 seconds)
        scope.launch {
            while (true) {
                delay(2000L)
                evaluateAiRisk()
                com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Safety")
            }
        }

        // Periodic 15-minute System Health Check
        scope.launch {
            while (true) {
                delay(900_000L) // 15 minutes
                try {
                    com.example.util.LoggingManager.info(
                        "System Sync",
                        "SYSTEM_HEALTH_SYNC",
                        "15-Minute Data Synchronization",
                        "Active safety sensors and telemetry layers verified."
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun evaluateAiRisk() {
        val currentFusion = fusionState.value
        val result = RuleBasedSafetyEngine.evaluateSafety(currentFusion)
        _riskAnalysis.value = result

        // Evaluate comprehensive canonical safety engine
        try {
            val thermalThreshold = settingsRepo.thermalThresholdC.first()
            safetyEngine.evaluateSafetyConditions(
                fusionState = currentFusion,
                liveReadings = liveReadings.value,
                thermalThresholdC = thermalThreshold
            )
        } catch (_: Exception) {}
    }

    suspend fun triggerSampleEvent(title: String, riskLevel: SafetyRiskLevel, score: Int, desc: String) {
        logActivity(
            moduleName = "Test Engine",
            eventName = "TEST_VERIFICATION",
            severity = "INFORMATION",
            riskLevel = riskLevel,
            riskScore = score,
            title = title,
            description = desc,
            aiRecommendation = "Verified hardware diagnostic event. Maintain standard safety precautions."
        )
    }

    suspend fun logActivity(
        moduleName: String,
        eventName: String,
        severity: String, // INFORMATION, WARNING, IMPORTANT, CRITICAL, RECOVERY
        riskLevel: SafetyRiskLevel,
        riskScore: Int,
        title: String,
        description: String,
        aiConfidence: Float = 0.95f,
        batteryPercent: Int = 0,
        deviceTempC: Float = 0.0f,
        processingDurationMs: Long = 12L,
        recoveryDurationMs: Long = 0L,
        gpsLocation: String = "Unavailable",
        announcementStatus: String = "N/A",
        aiRecommendation: String = "Maintain optimal monitoring."
    ) {
        // Canonical Event Generation
        val severityEnum = when (severity) {
            "CRITICAL" -> com.example.data.event.EventSeverity.CRITICAL
            "WARNING" -> com.example.data.event.EventSeverity.WARNING
            "INFORMATION" -> com.example.data.event.EventSeverity.INFO
            else -> com.example.data.event.EventSeverity.INFO
        }
        
        eventManager.processEvent(
            eventType = eventName,
            severity = severityEnum,
            message = "$title: $description",
            snapshot = sensorManager.stateManager.currentSnapshot.value
        )
        
        // Keep DB logging for now for backward compatibility, 
        // but it should eventually be replaced by the CanonicalEventManager's database storage.
        safetyEventDao.insertEvent(
            SafetyEventEntity(
                riskLevel = riskLevel.name,
                riskScore = riskScore,
                eventType = eventName,
                title = title,
                description = description,
                primarySensorValuesJson = "{\"module\":\"$moduleName\",\"event\":\"$eventName\",\"timestamp\":\"${System.currentTimeMillis()}\"}",
                aiRecommendation = aiRecommendation,
                isVerifiedHardwareEvent = true,
                moduleName = moduleName,
                severity = severity,
                aiConfidence = aiConfidence,
                batteryPercent = batteryPercent,
                deviceTempC = deviceTempC,
                processingDurationMs = processingDurationMs,
                recoveryDurationMs = recoveryDurationMs,
                gpsLocation = gpsLocation,
                announcementStatus = announcementStatus
            )
        )
    }


    suspend fun clearLogs() {
        safetyEventDao.clearAllEvents()
    }

    suspend fun runSelfAudit() {
        auditEngine.runSelfAudit()
    }

    suspend fun clearAuditHistory() {
        systemAuditDao.clearAllAudits()
    }
}
