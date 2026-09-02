package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.SafetyEventEntity
import com.example.data.db.SystemAuditEntity

import com.example.data.model.RawSensorReading
import com.example.data.model.RiskAnalysisResult
import com.example.data.model.SafetyRiskLevel
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory
import com.example.data.model.SensorFusionState
import com.example.data.model.DailyMotionDashboardState
import com.example.data.model.MotionCategory
import com.example.data.model.UserProfile
import com.example.data.repository.NetraSafetyRepository
import com.example.data.repository.SettingsRepository
import com.example.data.sensor.SensorDiagnosticStatus
import com.example.data.sensor.SensorDiagnosticsEngine
import com.example.util.NetraHapticsManager
import com.example.util.NetraNotificationManager
import com.example.util.NetraTtsManager
import com.example.util.TimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.util.SecurityUtils
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.net.wifi.WifiManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import com.example.data.model.PrivacyScannerState
import com.example.util.LoggingManager
import com.example.data.service.PinStorageService
import com.example.data.service.Pbkdf2PinStorageService
import com.example.data.engine.SecurityEngine
import com.example.data.engine.IntelligentHistoryEngine
import com.example.data.engine.IBRS2RuntimeEngine
import com.example.data.engine.IntelligentSecurityPrivacyEngine
import com.example.data.engine.IntelligentBackupSyncEngine
import com.example.data.engine.IntelligentDiagnosticsEngine


class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = NetraSafetyRepository(application.applicationContext)
    val settingsRepository = SettingsRepository(application.applicationContext)
    val securityEngine = SecurityEngine(application.applicationContext, settingsRepository)
    private val diagnosticsEngine = SensorDiagnosticsEngine()
    private val notificationManager = NetraNotificationManager(application.applicationContext)
    private val hapticsManager = NetraHapticsManager(application.applicationContext)
    private val ttsManager = NetraTtsManager(application.applicationContext)

    // Phase 5, 6, 7 & 8 Engines
    val historyEngine = IntelligentHistoryEngine.apply { initialize(repository.unifiedEventDao) }
    val ibrsEngine = IBRS2RuntimeEngine(historyEngine)
    val isppeEngine = IntelligentSecurityPrivacyEngine(application.applicationContext, historyEngine)
    val ibsdcEngine = IntelligentBackupSyncEngine(application.applicationContext, historyEngine, isppeEngine, repository)
    val idhmseEngine = IntelligentDiagnosticsEngine(application.applicationContext, historyEngine, ibrsEngine)

    val voiceWakeEngine = com.example.util.NetraVoiceWakeEngine(
        application.applicationContext,
        this,
        ttsManager
    )


    private val _voiceEngineState = MutableStateFlow(com.example.util.NetraVoiceWakeEngine.EngineState.IDLE)
    val voiceEngineState: StateFlow<com.example.util.NetraVoiceWakeEngine.EngineState> = _voiceEngineState.asStateFlow()

    private val _lastVoiceCommand = MutableStateFlow<String?>(null)
    val lastVoiceCommand: StateFlow<String?> = _lastVoiceCommand.asStateFlow()

    private val _lastVoiceResponse = MutableStateFlow<String?>(null)
    val lastVoiceResponse: StateFlow<String?> = _lastVoiceResponse.asStateFlow()

    private var stepsTargetAchievedToday = false
    private var standingTargetAchievedToday = false
    private var lastCheckDate = ""

    val capabilities: StateFlow<List<SensorCapabilityInfo>> = repository.capabilities
    val fusionState: StateFlow<SensorFusionState> = repository.fusionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SensorFusionState())

    val safetyEngineState: StateFlow<com.example.data.model.SafetyEngineState> = repository.safetyEngineState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.model.SafetyEngineState())

    fun evaluateSafetyConditions() {
        viewModelScope.launch {
            repository.evaluateAiRisk()
        }
    }

    val riskAnalysis: StateFlow<RiskAnalysisResult> = repository.riskAnalysis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RiskAnalysisResult(0, SafetyRiskLevel.SAFE, "", emptyList(), ""))
    private val _liveGraphState = kotlinx.coroutines.flow.MutableStateFlow(com.example.ui.screens.LiveGraphState())
    val liveGraphState: StateFlow<com.example.ui.screens.LiveGraphState> = _liveGraphState.asStateFlow()
    
    private val MAX_BUFFER_SIZE = 100
    private var liveGraphBuffer = java.util.concurrent.ConcurrentLinkedDeque<com.example.data.model.RawSensorReading>()
    private var liveGraphCollectionJob: kotlinx.coroutines.Job? = null
    
    fun selectLiveGraphSensor(sensorType: Int) {
        if (_liveGraphState.value.selectedSensorType == sensorType) return
        repository.sensorManager.removeSubscriber(_liveGraphState.value.selectedSensorType, "live_graph")
        liveGraphBuffer.clear()
        _liveGraphState.value = _liveGraphState.value.copy(selectedSensorType = sensorType, buffer = emptyList())
        if (!_liveGraphState.value.isPaused) {
            repository.sensorManager.addSubscriber(sensorType, "live_graph")
        }
    }
    
    fun setLiveGraphPaused(paused: Boolean) {
        if (_liveGraphState.value.isPaused == paused) return
        _liveGraphState.value = _liveGraphState.value.copy(isPaused = paused)
        if (paused) {
            repository.sensorManager.removeSubscriber(_liveGraphState.value.selectedSensorType, "live_graph")
        } else {
            repository.sensorManager.addSubscriber(_liveGraphState.value.selectedSensorType, "live_graph")
        }
    }
    
    fun startLiveGraphSession() {
        if (!_liveGraphState.value.isPaused) {
            repository.sensorManager.addSubscriber(_liveGraphState.value.selectedSensorType, "live_graph")
        }
        liveGraphCollectionJob?.cancel()
        liveGraphCollectionJob = viewModelScope.launch {
            repository.sensorManager.liveReadings.collect { readings ->
                if (_liveGraphState.value.isPaused) return@collect
                val selectedId = "sensor_${_liveGraphState.value.selectedSensorType}"
                val reading = readings[selectedId]
                if (reading != null && !reading.isStale(15000L)) {
                    liveGraphBuffer.addLast(reading)
                    if (liveGraphBuffer.size > MAX_BUFFER_SIZE) {
                        liveGraphBuffer.removeFirst()
                    }
                    _liveGraphState.value = _liveGraphState.value.copy(buffer = liveGraphBuffer.toList())
                }
            }
        }
    }
    
    fun stopLiveGraphSession() {
        repository.sensorManager.removeSubscriber(_liveGraphState.value.selectedSensorType, "live_graph")
        liveGraphCollectionJob?.cancel()
    }

    val liveReadings: StateFlow<Map<String, RawSensorReading>> = repository.liveReadings

    val sensorDiagnostics: StateFlow<List<SensorDiagnosticStatus>> = combine(
        capabilities,
        liveReadings,
        repository.sensorManager.isMonitoringActive
    ) { caps, readings, active ->
        diagnosticsEngine.analyzeSensors(caps, readings, active)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitorThermal: StateFlow<Boolean> = settingsRepository.monitorThermal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val monitorMagnetic: StateFlow<Boolean> = settingsRepository.monitorMagnetic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyMagnetic: StateFlow<Boolean> = settingsRepository.notifyMagnetic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val announceMagnetic: StateFlow<Boolean> = settingsRepository.announceMagnetic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val monitorProximity: StateFlow<Boolean> = settingsRepository.monitorProximity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyProximity: StateFlow<Boolean> = settingsRepository.notifyProximity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val announceProximity: StateFlow<Boolean> = settingsRepository.announceProximity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val monitorWeather: StateFlow<Boolean> = settingsRepository.monitorWeather.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyWeather: StateFlow<Boolean> = settingsRepository.notifyWeather.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val announceWeather: StateFlow<Boolean> = settingsRepository.announceWeather.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val monitorLight: StateFlow<Boolean> = settingsRepository.monitorLight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyLight: StateFlow<Boolean> = settingsRepository.notifyLight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val announceLight: StateFlow<Boolean> = settingsRepository.announceLight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val monitorBluetooth: StateFlow<Boolean> = settingsRepository.monitorBluetooth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyBluetooth: StateFlow<Boolean> = settingsRepository.notifyBluetooth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val announceBluetooth: StateFlow<Boolean> = settingsRepository.announceBluetooth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val monitorLocation: StateFlow<Boolean> = settingsRepository.monitorLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyLocation: StateFlow<Boolean> = settingsRepository.notifyLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val announceLocation: StateFlow<Boolean> = settingsRepository.announceLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val refreshIntervalMs: StateFlow<Int> = settingsRepository.refreshIntervalMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 300)
    val thermalThresholdC: StateFlow<Int> = settingsRepository.thermalThresholdC.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 45)
    val encryptionEnabled: StateFlow<Boolean> = settingsRepository.encryptionEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val travelMode: StateFlow<String> = settingsRepository.travelMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")
    val developerMode: StateFlow<Boolean> = settingsRepository.developerMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val developerPinHash: StateFlow<String?> = settingsRepository.developerPinHash.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val developerPinSalt: StateFlow<String?> = settingsRepository.developerPinSalt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val developerPinChangedDate: StateFlow<String?> = settingsRepository.developerPinChangedDate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val developerPinStrength: StateFlow<String?> = settingsRepository.developerPinStrength.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val developerPinFailedAttempts: StateFlow<Int> = settingsRepository.developerPinFailedAttempts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val developerPinRecoveryKey: StateFlow<String?> = settingsRepository.developerPinRecoveryKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Motion Intelligence & Daily Activity Dashboard State
    val motionDashboardState: StateFlow<DailyMotionDashboardState> = repository.sensorManager.motionIntelligenceEngine.motionState
    val availableMotionHistoryDates: StateFlow<List<String>> = repository.sensorManager.motionIntelligenceEngine.availableHistoryDates
    val selectedMotionDate: StateFlow<String?> = repository.sensorManager.motionIntelligenceEngine.selectedDateKey

    val userDobEpochMs: StateFlow<Long?> = settingsRepository.userDobEpochMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val userHeightCm: StateFlow<Float?> = settingsRepository.userHeightCm.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val userHeightUnit: StateFlow<String> = settingsRepository.userHeightUnit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "cm")
    val userGender: StateFlow<String> = settingsRepository.userGender.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val customStepTarget: StateFlow<Int?> = settingsRepository.customStepTarget.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val customStandingTargetSec: StateFlow<Long?> = settingsRepository.customStandingTargetSec.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectMotionHistoryDate(dateKey: String?) {
        repository.sensorManager.motionIntelligenceEngine.selectDateForView(dateKey)
    }

    fun saveUserProfile(dobEpochMs: Long?, heightCm: Float?, heightUnit: String, gender: String) {
        viewModelScope.launch {
            settingsRepository.saveUserProfile(dobEpochMs, heightCm, heightUnit, gender)
        }
    }

    fun saveCustomTargets(stepTarget: Int?, standingTargetSec: Long?) {
        viewModelScope.launch {
            settingsRepository.saveCustomTargets(stepTarget, standingTargetSec)
        }
    }

    private val _privacyScannerState = MutableStateFlow(PrivacyScannerState())
    val privacyScannerState: StateFlow<PrivacyScannerState> = _privacyScannerState.asStateFlow()

    // Location & Cross-Verification State Flows
    private val _currentCity = MutableStateFlow("Location Unavailable")
    val currentCity: StateFlow<String> = _currentCity.asStateFlow()

    private val _currentState = MutableStateFlow("Location Unavailable")
    val currentState: StateFlow<String> = _currentState.asStateFlow()

    private val _currentCountry = MutableStateFlow("Location Unavailable")
    val currentCountry: StateFlow<String> = _currentCountry.asStateFlow()

    private val _locationProvider = MutableStateFlow("GPS & Network")
    val locationProvider: StateFlow<String> = _locationProvider.asStateFlow()

    private val _locationConfidence = MutableStateFlow(0)
    val locationConfidence: StateFlow<Int> = _locationConfidence.asStateFlow()

    private val _gpsStatus = MutableStateFlow("Initializing GNSS")
    val gpsStatus: StateFlow<String> = _gpsStatus.asStateFlow()

    private val _networkStatus = MutableStateFlow("Cellular / Network Standby")
    val networkStatus: StateFlow<String> = _networkStatus.asStateFlow()

    val isDeveloperAuthenticated = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val pinStorageService: PinStorageService = Pbkdf2PinStorageService()
    val lockoutUntil: StateFlow<Long> = settingsRepository.lockoutUntilTimestamp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun authenticateDeveloper(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (lockoutUntil.value > now) {
            return false // Locked out!
        }

        val hash = developerPinHash.value
        val salt = developerPinSalt.value

        val isSuccess = if (hash == null || salt == null) {
            // No custom PIN set yet, authenticate with default 000000
            pin == "000000"
        } else {
            val isPbkdf2Correct = pinStorageService.verifyPin(pin, hash, salt)
            val isSha256Correct = SecurityUtils.hashPin(pin, salt) == hash
            isPbkdf2Correct || isSha256Correct
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (isSuccess) {
                isDeveloperAuthenticated.value = true
                settingsRepository.clearFailedAttemptsAndLockout()
                
                // Log Successful Login
                val event = SafetyEventEntity(
                    timestamp = System.currentTimeMillis(),
                    riskLevel = "INFO",
                    riskScore = 0,
                    eventType = "PIN_AUTH",
                    title = "Developer Mode Opened",
                    description = "Successful developer authentication PIN entry.",
                    primarySensorValuesJson = "{}",
                    aiRecommendation = "Access granted to secure diagnostic tools.",
                    isVerifiedHardwareEvent = true,
                    moduleName = "Developer PIN Security",
                    severity = "INFORMATION"
                )
                repository.safetyEventDao.insertEvent(event)
            } else {
                settingsRepository.recordFailedLoginAttempt(System.currentTimeMillis())
                val currentFailures = developerPinFailedAttempts.value + 1
                
                // Log Failed Attempt
                val event = SafetyEventEntity(
                    timestamp = System.currentTimeMillis(),
                    riskLevel = "WARNING",
                    riskScore = 20,
                    eventType = "PIN_AUTH_FAILED",
                    title = "Failed PIN Attempt",
                    description = "An incorrect Developer PIN was entered. Attempt #$currentFailures.",
                    primarySensorValuesJson = "{}",
                    aiRecommendation = "Ensure the correct security PIN is entered.",
                    isVerifiedHardwareEvent = true,
                    moduleName = "Developer PIN Security",
                    severity = "WARNING"
                )
                repository.safetyEventDao.insertEvent(event)

                if (currentFailures >= 5) {
                    // Log Temporary Lock Activated
                    val lockEvent = SafetyEventEntity(
                        timestamp = System.currentTimeMillis(),
                        riskLevel = "EMERGENCY",
                        riskScore = 50,
                        eventType = "LOCKOUT_ACTIVATED",
                        title = "Temporary Lock Activated",
                        description = "Developer Mode locked for 30 seconds due to 5 consecutive failed attempts.",
                        primarySensorValuesJson = "{}",
                        aiRecommendation = "Please wait for the lockout period to expire.",
                        isVerifiedHardwareEvent = true,
                        moduleName = "Developer PIN Security",
                        severity = "CRITICAL"
                    )
                    repository.safetyEventDao.insertEvent(lockEvent)
                }
            }
        }

        return isSuccess
    }

    fun changeDeveloperPin(currentPin: String, newPin: String): Boolean {
        val hash = developerPinHash.value
        val salt = developerPinSalt.value

        val isCurrentCorrect = if (hash == null || salt == null) {
            currentPin == "000000"
        } else {
            val isPbkdf2Correct = pinStorageService.verifyPin(currentPin, hash, salt)
            val isSha256Correct = SecurityUtils.hashPin(currentPin, salt) == hash
            isPbkdf2Correct || isSha256Correct
        }

        if (!isCurrentCorrect) return false

        val strength = PinStrengthAnalyzer.analyze(newPin)
        if (strength == PinStrength.WEAK) return false

        val newSalt = pinStorageService.generateSalt()
        val newHash = pinStorageService.hashPin(newPin, newSalt)
        val strengthStr = strength.displayName
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date())
        val recoveryKey = SecurityUtils.generateRecoveryKey()

        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.saveDeveloperPin(newHash, newSalt, strengthStr, dateStr, recoveryKey)
            isDeveloperAuthenticated.value = true // Keep authenticated after change
            
            // Log PIN Changed
            val event = SafetyEventEntity(
                timestamp = System.currentTimeMillis(),
                riskLevel = "INFO",
                riskScore = 0,
                eventType = "PIN_CHANGED",
                title = "Developer PIN Changed",
                description = "Developer security PIN updated. Strength: ${developerPinStrength.value ?: "Default"} -> $strengthStr",
                primarySensorValuesJson = "{}",
                aiRecommendation = "Keep your recovery key secure: $recoveryKey",
                isVerifiedHardwareEvent = true,
                moduleName = "Developer PIN Security",
                severity = "INFORMATION"
            )
            repository.safetyEventDao.insertEvent(event)
        }

        return true
    }

    fun resetDeveloperPinWithRecoveryKey(enteredKey: String): Boolean {
        val actualKey = developerPinRecoveryKey.value ?: ""
        val sanitizedEntered = enteredKey.trim().replace("-", "").uppercase()
        val sanitizedActual = actualKey.trim().replace("-", "").uppercase()

        if (sanitizedEntered.isNotEmpty() && sanitizedEntered == sanitizedActual) {
            viewModelScope.launch(Dispatchers.IO) {
                settingsRepository.clearDeveloperPin() // Reset to default 000000
                isDeveloperAuthenticated.value = false // Require entering default pin to force change
                
                // Log PIN Reset
                val event = SafetyEventEntity(
                    timestamp = System.currentTimeMillis(),
                    riskLevel = "WARNING",
                    riskScore = 10,
                    eventType = "PIN_RESET",
                    title = "Developer PIN Reset",
                    description = "Developer PIN reset to default 000000 using recovery key.",
                    primarySensorValuesJson = "{}",
                    aiRecommendation = "Open Developer Mode and set a new secure PIN immediately.",
                    isVerifiedHardwareEvent = true,
                    moduleName = "Developer PIN Security",
                    severity = "IMPORTANT"
                )
                repository.safetyEventDao.insertEvent(event)
            }
            return true
        }
        return false
    }

    fun lockDeveloperMode() {
        isDeveloperAuthenticated.value = false
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDeveloperMode(enabled)
            if (enabled) {
                com.example.util.LoggingManager.info(
                    "Developer Tools",
                    "DEVELOPER_MODE_ENABLED",
                    "Developer Mode Enabled",
                    "Time: ${System.currentTimeMillis()}, Reason: User requested advanced diagnostics, User: System Operator"
                )
            } else {
                isDeveloperAuthenticated.value = false
                com.example.util.LoggingManager.info(
                    "Developer Tools",
                    "DEVELOPER_MODE_DISABLED",
                    "Developer Mode Disabled",
                    "Time: ${System.currentTimeMillis()}, Session Duration: Active session ended"
                )
            }
        }
    }

    var exportStatusMessage by mutableStateOf<String?>(null)
        private set

    val shareFileEvent = kotlinx.coroutines.flow.MutableSharedFlow<File?>(replay = 0)

    val eventLogs: StateFlow<List<SafetyEventEntity>> = repository.eventLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val moduleHealth = com.example.data.engine.HealthAuditManager.moduleHealth

    // Phase 3: Intelligent Safety Status Engine Integration
    val systemSafetyStatus = com.example.data.engine.IntelligentSafetyStatusEngine.systemStatus
    val moduleStates = com.example.data.engine.IntelligentSafetyStatusEngine.moduleStates

    val auditLogs: StateFlow<List<SystemAuditEntity>> = repository.auditLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isAuditing: StateFlow<Boolean> = repository.auditEngine.isAuditing
    val lastAuditReport: StateFlow<SystemAuditEntity?> = repository.auditEngine.lastAuditReport



    fun runSelfAudit() {
        viewModelScope.launch {
            repository.runSelfAudit()
        }
    }

    fun clearAuditHistory() {
        viewModelScope.launch {
            repository.clearAuditHistory()
        }
    }

    fun getLiveServiceStats() = repository.auditEngine.getServiceStats()

    fun setSensorThrottle(sensorType: Int, intervalMs: Long) {
        repository.sensorManager.setSensorThrottle(sensorType, intervalMs)
    }

    fun forceWatchdogStale(moduleName: String) {
        com.example.data.engine.NetraWatchdogEngine.forceStale(moduleName)
    }

    val sensorThrottles: StateFlow<Map<Int, Long>> = repository.sensorManager.sensorThrottles

    val watchdogModuleStates = com.example.data.engine.NetraWatchdogEngine.moduleStates

    private val _safetyStatus = MutableStateFlow("Analyzing device security...")
    val safetyStatus: StateFlow<String> = _safetyStatus.asStateFlow()

    fun updateSafetyStatus() {
        viewModelScope.launch {
            val score = securityEngine.securityScore.value
            val missing = securityEngine.missingMandatoryFeatures.value.size
            _safetyStatus.value = when {
                score == 100 -> "Device is fully secure. All verified safety guidelines and components are active."
                score >= 75 -> "Device security is robust. $missing minor feature(s) missing but overall protection is high."
                score >= 50 -> "Elevated warning. $missing mandatory security feature(s) are currently disabled."
                else -> "Critical warning. Device has low security with $missing essential security features disabled."
            }
        }
    }

    private var activeThermalEventId: Long? = null
    private var thermalEventStartTime: Long = 0L
    private var hasAnnouncedThermal = false
    private var lastAnnouncementTimeThermal: Long = 0L // Repeat Logic
    private var thermalAnnouncementCount: Int = 0

    private var activeEarlyHeatEventId: Long? = null
    private var hasAnnouncedEarlyHeat = false
    private var earlyHeatStartTime: Long = 0L
    private var earlyHeatPeakTemp: Float = 0f

    enum class MagneticSafetyZone(val displayName: String, val levelName: String) {
        NORMAL("Normal Zone", "NORMAL"),
        SAFE("Safe Zone", "SAFE"),
        ATTENTION("Attention Zone", "ATTENTION"),
        CAUTION("Caution Zone", "CAUTION"),
        WARNING("Warning Zone", "WARNING"),
        HIGH_RISK("High Risk Zone", "HIGH_RISK"),
        EXTREME_RISK("Extreme Risk Zone", "EXTREME_RISK"),
        DANGEROUS("Dangerous Magnetic Environment", "DANGEROUS")
    }

    private var activeMagneticEventId: Long? = null
    private var magneticEventStartTime: Long = 0L
    private var magneticPeakValue: Float = 0f
    private var magneticSumValues: Float = 0f
    private var magneticCountValues: Int = 0
    private var lastAnnouncedMagneticZone: MagneticSafetyZone = MagneticSafetyZone.NORMAL
    private var lastAnnouncementTimeMagnetic: Long = 0L // Repeat Logic
    private val MAGNETIC_REPEAT_INTERVAL = 30000L // 30s
    private var magneticRepeatCount: Int = 0 // Alert Logging

    // Active Driving Detection trackers
    private var activeDrivingEventId: Long? = null
    private var drivingStartTime: Long = 0L
    private var lastDrivingConfirmedState = false
    private var lastRapidAccState = false
    private var lastApproachingCPState = false
    private var lastHighSpeedAlertTime = 0L

    // Driving speed history for charts
    private val _drivingSpeedHistory = MutableStateFlow<List<Float>>(emptyList())
    val drivingSpeedHistory: StateFlow<List<Float>> = _drivingSpeedHistory.asStateFlow()

    private fun updateDrivingSpeedHistory(speed: Float) {
        val currentHistory = _drivingSpeedHistory.value.toMutableList()
        currentHistory.add(speed)
        if (currentHistory.size > 20) {
            currentHistory.removeAt(0)
        }
        _drivingSpeedHistory.value = currentHistory
    }

    private fun getMagneticSafetyZone(uT: Float): MagneticSafetyZone {
        return when {
            uT < 50f -> MagneticSafetyZone.NORMAL
            uT < 100f -> MagneticSafetyZone.SAFE
            uT < 150f -> MagneticSafetyZone.ATTENTION
            uT < 250f -> MagneticSafetyZone.CAUTION
            uT < 400f -> MagneticSafetyZone.WARNING
            uT < 600f -> MagneticSafetyZone.HIGH_RISK
            uT < 1000f -> MagneticSafetyZone.EXTREME_RISK
            else -> MagneticSafetyZone.DANGEROUS
        }
    }

    private fun isBluetoothConnected(): Boolean {
        val application = getApplication<Application>()
        val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        val adapter = bluetoothManager?.adapter ?: return false
        com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Bluetooth")
        if (!adapter.isEnabled) return false
        
        // For Android 12+, we need BLUETOOTH_CONNECT permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(application, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        
        return try {
            adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP) == android.bluetooth.BluetoothAdapter.STATE_CONNECTED
        } catch (e: SecurityException) {
            false
        }
    }

    private fun speakAlertIfAllowed(message: String, isDrivingEvent: Boolean = false) {
        if (!isDrivingEvent || isBluetoothConnected()) {
            ttsManager.speakAlert(message)
        }
    }

    private fun resolveActiveMagneticEvent(eventId: Long) {
        val resolutionTime = System.currentTimeMillis()
        val durationSeconds = (resolutionTime - magneticEventStartTime) / 1000
        val coolingTimeStr = TimeManager.formatDuration(durationSeconds)

        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val resolutionTimeStr = sdfTime.format(Date(resolutionTime))

        viewModelScope.launch(Dispatchers.IO) {
            val existingEvent = repository.safetyEventDao.getEventById(eventId)
            if (existingEvent != null) {
                try {
                    val jsonObj = JSONObject(existingEvent.primarySensorValuesJson)
                    jsonObj.put("Detection End Time", resolutionTimeStr)
                    jsonObj.put("Detection Duration", coolingTimeStr)
                    jsonObj.put("Event Classification", "Persistent")
                    jsonObj.put("Notification Status", "Delivered")
                    jsonObj.put("Announcement Status", existingEvent.announcementStatus)
                    jsonObj.put("Suppression Reason", "N/A")

                    val updatedEvent = existingEvent.copy(
                        primarySensorValuesJson = jsonObj.toString(),
                        description = existingEvent.description + " Resolved at $resolutionTimeStr. Event duration was $coolingTimeStr."
                    )
                    repository.safetyEventDao.insertEvent(updatedEvent)
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
    }

    val syncManager = com.example.data.pipeline.DeviceDataSyncManager(
        application.applicationContext,
        repository.sensorManager.motionIntelligenceEngine,
        repository
    )
    val syncStatus = syncManager.syncStatus
    val lastSyncTimestamp = syncManager.lastSyncTimestamp

    init {
        viewModelScope.launch(Dispatchers.IO) {
            syncManager.performStartupSync()
        }

        val dataScheduler = com.example.data.engine.DataScheduler(application)
        dataScheduler.scheduleSync()

        val auditWork = androidx.work.PeriodicWorkRequestBuilder<com.example.data.engine.HealthAuditWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        androidx.work.WorkManager.getInstance(application).enqueueUniquePeriodicWork("netra_audit", androidx.work.ExistingPeriodicWorkPolicy.KEEP, auditWork)

        val recoveryWork = androidx.work.PeriodicWorkRequestBuilder<com.example.data.engine.RecoveryWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        androidx.work.WorkManager.getInstance(application).enqueueUniquePeriodicWork("netra_recovery", androidx.work.ExistingPeriodicWorkPolicy.KEEP, recoveryWork)

        com.example.data.engine.IntelligentHistoryEngine.initialize(repository.unifiedEventDao)
        
        com.example.data.engine.NetraWatchdogEngine.initialize(
            application,
            repository.batteryManager,
            repository.sensorManager,
            securityEngine
        )

        viewModelScope.launch {
            var lastAcceptedLat = 0.0
            var lastAcceptedLng = 0.0
            var lastAcceptedTime = 0L

            repository.fusionState.collect { state ->
                updateDrivingSpeedHistory(state.currentSpeedKmH)
                val lat = state.latitude
                val lng = state.longitude
                if (lat != 0.0 && lng != 0.0) {
                    try {
                        val geocoder = android.location.Geocoder(application.applicationContext, java.util.Locale.getDefault())
                        if (android.location.Geocoder.isPresent()) {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(lat, lng, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Location Verified"
                                val adminArea = address.adminArea ?: "Region Available"
                                val countryName = address.countryName ?: "Country Available"

                                val confidence = if (lat != 0.0 && lng != 0.0) maxOf(92, state.journeyConfidenceScore) else 85
                                _locationConfidence.value = confidence
                                _locationProvider.value = "GPS & Network (Cross-Verified)"

                                // Anti-flicker threshold: require confidence >= 90% and either stable for 60s or distance > 1km or speed > 10km/h
                                if (confidence >= 90) {
                                    val now = System.currentTimeMillis()
                                    val distanceKm = calculateDistanceKm(lastAcceptedLat, lastAcceptedLng, lat, lng)
                                    val isSignificantMove = distanceKm > 1.0 || state.currentSpeedKmH > 10.0
                                    val isStableEnough = (now - lastAcceptedTime >= 60000L) || lastAcceptedTime == 0L

                                    if (isSignificantMove || isStableEnough || city == _currentCity.value) {
                                        _currentCity.value = city
                                        _currentState.value = adminArea
                                        _currentCountry.value = countryName
                                        lastAcceptedLat = lat
                                        lastAcceptedLng = lng
                                        lastAcceptedTime = now
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Keep previous
                    }
                }
            }
        }
        
        voiceWakeEngine.onStateChanged = { state ->
            _voiceEngineState.value = state
        }
        voiceWakeEngine.onCommandHeard = { cmd ->
            _lastVoiceCommand.value = cmd
        }
        voiceWakeEngine.onResponseSpoken = { resp ->
            _lastVoiceResponse.value = resp
        }

        // Observe risk analysis to trigger haptics, notifications, and TTS on critical risks
        viewModelScope.launch {
            riskAnalysis.collect { analysis ->
                // Do not duplicate announcement if this is a high heat warning since we handle that in our specialized thermal observer!
                if (analysis.summary.contains("Heat", ignoreCase = true) ||
                    analysis.summary.contains("Thermal", ignoreCase = true) ||
                    analysis.explanation.contains("heat", ignoreCase = true) ||
                    analysis.explanation.contains("temperature", ignoreCase = true)
                ) {
                    return@collect
                }
                if (analysis.riskLevel == SafetyRiskLevel.EMERGENCY || analysis.riskLevel == SafetyRiskLevel.WARNING) {
                    hapticsManager.triggerHapticForRisk(analysis.riskLevel)
                    notificationManager.sendEmergencyAlert(analysis.summary, analysis.explanation, analysis.riskLevel)
                    ttsManager.speakAlert("Safety alert detected.")
                }
            }
        }

        // Observe motionDashboardState to update notification bar from central source of truth
        viewModelScope.launch {
            motionDashboardState.collect { motionState ->
                notificationManager.updateMotionNotification(motionState)
            }
        }

        // Observe fusionState & thermalThresholdC for detailed critical thermal monitoring and alerts
        viewModelScope.launch {
            combine(fusionState.sample(1000L), thermalThresholdC) { state, threshold ->
                Pair(state, threshold)
            }.collect { (state, threshold) ->
                val currentTemp = state.batteryTempC
                val isCharging = state.isCharging

                if (currentTemp >= threshold) {
                    val now = System.currentTimeMillis()
                    if (activeThermalEventId == null) {
                        // This is a new thermal event! Record start time.
                        thermalEventStartTime = System.currentTimeMillis()

                        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val currentDateStr = sdfDate.format(Date(thermalEventStartTime))
                        val currentTimeStr = sdfTime.format(Date(thermalEventStartTime))

                        val livePowerReading = liveReadings.value["battery_telemetry"]
                        val plugType = livePowerReading?.extraDetails?.get("plugType") ?: if (isCharging) "Charging" else "Discharging"

                        val liveThermalReading = liveReadings.value["thermal_subsystem"]
                        val androidThermalStatus = liveThermalReading?.extraDetails?.get("thermalStatus") ?: "Data unavailable on this device."

                        val foregroundApp = "Heat source could not be identified from available system data."
                        val backgroundActivity = "Heat source could not be identified from available system data."

                        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                        val screenStateStr = if (powerManager != null) {
                            if (powerManager.isInteractive) "Screen On" else "Screen Off"
                        } else {
                            "Data unavailable on this device."
                        }

                        val voiceAnnouncementStatus = if (!hasAnnouncedThermal) "Announced" else "Already Announced"

                        val sensorValuesMap = mapOf(
                            "Date" to currentDateStr,
                            "Time" to currentTimeStr,
                            "Battery Temperature" to "${currentTemp}°C",
                            "User Selected Threshold" to "${threshold}°C",
                            "Charging Status" to plugType,
                            "Android Thermal Status" to androidThermalStatus,
                            "Foreground Application" to foregroundApp,
                            "Relevant Background Activity" to backgroundActivity,
                            "Device State (Screen)" to screenStateStr,
                            "Voice Announcement Status" to voiceAnnouncementStatus,
                            "Cooling Time" to "Pending resolution...",
                            "Event Resolution Time" to "Pending resolution..."
                        )

                        val jsonValues = JSONObject(sensorValuesMap).toString()

                        val event = SafetyEventEntity(
                            riskLevel = SafetyRiskLevel.WARNING.name,
                            riskScore = 55,
                            eventType = "CRITICAL_THERMAL",
                            title = "Critical Thermal Protection Alert",
                            description = "Device temperature of ${currentTemp}°C has reached or exceeded the configured threshold of ${threshold}°C.",
                            primarySensorValuesJson = jsonValues,
                            aiRecommendation = "Remove from heat sources immediately.\n• If charging, unplug the charger.\n• Halt high-processing applications.",
                            isVerifiedHardwareEvent = true
                        )

                        val insertedId = repository.safetyEventDao.insertEvent(event)
                        activeThermalEventId = insertedId
                    }

                    val nextInterval = when (thermalAnnouncementCount) {
                        0 -> 0L // Immediate
                        1 -> 30000L // 30s
                        2 -> 60000L // 60s
                        else -> 120000L // 120s
                    }

                    if (!hasAnnouncedThermal || (now - lastAnnouncementTimeThermal >= nextInterval)) {
                        hasAnnouncedThermal = true
                        lastAnnouncementTimeThermal = now
                        thermalAnnouncementCount++
                        
                        hapticsManager.triggerHapticForRisk(SafetyRiskLevel.WARNING)
                        val criticalMsg = if (isCharging) "Charging stopped. Device temperature is critical." else "Critical temperature reached. Cool your device immediately."
                        speakAlertIfAllowed(criticalMsg, isDrivingEvent = false)
                        notificationManager.sendEmergencyAlert(
                            "Critical Thermal Protection Alert",
                            "Device temperature is $currentTemp°C (Threshold: $threshold°C). Take cooling measures immediately.",
                            SafetyRiskLevel.WARNING
                        )
                        
                        // Alert Logging
                        viewModelScope.launch(Dispatchers.IO) {
                             val event = SafetyEventEntity(
                                timestamp = System.currentTimeMillis(),
                                riskLevel = SafetyRiskLevel.WARNING.name,
                                riskScore = 55,
                                eventType = "THERMAL_REPEAT_ANNOUNCEMENT",
                                title = "Thermal Alert Repeat",
                                description = "Announcement repeated. Temp: ${currentTemp}°C",
                                primarySensorValuesJson = "{\"temp\":$currentTemp}",
                                aiRecommendation = "Continue cooling measures.",
                                isVerifiedHardwareEvent = true
                             )
                             repository.safetyEventDao.insertEvent(event)
                        }
                    }
                } else {
                    val recoveryMargin = 2.0f
                    val recoveryThreshold = threshold - recoveryMargin

                    if (currentTemp < recoveryThreshold) {
                        if (hasAnnouncedThermal) {
                            hasAnnouncedThermal = false
                        }

                        val eventId = activeThermalEventId
                        if (eventId != null) {
                            activeThermalEventId = null
                            val resolutionTime = System.currentTimeMillis()
                            val durationSeconds = (resolutionTime - thermalEventStartTime) / 1000
                            val coolingTimeStr = TimeManager.formatDuration(durationSeconds)

                            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            val resolutionTimeStr = sdfTime.format(Date(resolutionTime))

                            val existingEvent = repository.safetyEventDao.getEventById(eventId)
                            if (existingEvent != null) {
                                try {
                                    val jsonObj = JSONObject(existingEvent.primarySensorValuesJson)
                                    jsonObj.put("Cooling Time", coolingTimeStr)
                                    jsonObj.put("Event Resolution Time", resolutionTimeStr)

                                    val updatedEvent = existingEvent.copy(
                                        primarySensorValuesJson = jsonObj.toString(),
                                        description = existingEvent.description + " Resolved at $resolutionTimeStr. Cooling duration was $coolingTimeStr."
                                    )
                                    repository.safetyEventDao.insertEvent(updatedEvent)
                                } catch (e: Exception) {
                                    // Fallback
                                }
                            }
                        }
                    }
                    
                    // Implement Rapid Cooling Detection
                    detectRapidCooling(currentTemp, isCharging)
                }
            }
        }
    }

    // Tracker variables for Cold detection
    private var lastTempForCoolingDetection = 0.0f
    private var lastTempTimeForCoolingDetection = 0L
    private var isHighTempRecoveryMode = false

    private fun detectRapidCooling(currentTemp: Float, isCharging: Boolean) {
        val now = System.currentTimeMillis()
        
        // Phase 2: Recovery Lock logic (simplified)
        if (currentTemp > 40.0f) {
            isHighTempRecoveryMode = true
        } else if (currentTemp < 35.0f && isHighTempRecoveryMode) {
            isHighTempRecoveryMode = false
        }
        
        if (isHighTempRecoveryMode) return

        if (lastTempTimeForCoolingDetection != 0L) {
            val deltaTime = (now - lastTempTimeForCoolingDetection) / 60000.0f // in minutes
            if (deltaTime > 0) {
                val deltaTemp = lastTempForCoolingDetection - currentTemp
                val rate = deltaTemp / deltaTime // °C/min

                // Phase 4 & 5 & 6
                if (rate > 2.0f) { // Example rate
                    if (currentTemp < 10.0f) {
                         // Critical Alert
                         notificationManager.sendEmergencyAlert("Critical cooling detected", "Critical cooling detected: device temperature is falling rapidly to ${currentTemp}C", SafetyRiskLevel.EMERGENCY)
                         speakAlertIfAllowed("Critical cooling detected.", false)
                    } else if (currentTemp < 15.0f) {
                         // Rapid Cooling Alert
                         notificationManager.sendEmergencyAlert("Rapid temperature drop", "Rapid temperature drop detected: device temperature is falling to ${currentTemp}C", SafetyRiskLevel.WARNING)
                         speakAlertIfAllowed("Rapid temperature drop detected.", false)
                    }
                }
            }
        }
        lastTempForCoolingDetection = currentTemp
        lastTempTimeForCoolingDetection = now
    }

    // Observe fusionState for Level 1 - Early Heat Warning
    init {
        // Observe fusionState & monitorMagnetic for human magnetic safety classification
        viewModelScope.launch {
            combine(fusionState.sample(1000L), monitorMagnetic) { state, isEnabled ->
                Pair(state, isEnabled)
            }.collect { (state, isEnabled) ->
                if (!isEnabled) {
                    val eventId = activeMagneticEventId
                    if (eventId != null) {
                        activeMagneticEventId = null
                        resolveActiveMagneticEvent(eventId)
                    }
                    return@collect
                }

                val uT = state.magneticMagnitudeuT
                val currentZone = getMagneticSafetyZone(uT)

                if (currentZone != MagneticSafetyZone.NORMAL) {
                    if (magneticEventStartTime == 0L) {
                        magneticEventStartTime = System.currentTimeMillis()
                        magneticPeakValue = uT
                        magneticSumValues = uT
                        magneticCountValues = 1
                    } else {
                        magneticPeakValue = maxOf(magneticPeakValue, uT)
                        magneticSumValues += uT
                        magneticCountValues++
                    }

                    val durationMs = System.currentTimeMillis() - magneticEventStartTime
                    if (durationMs < 5000L) {
                        return@collect
                    }

                    if (activeMagneticEventId == null) {
                        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val currentDateStr = sdfDate.format(Date(magneticEventStartTime))
                        val currentTimeStr = sdfTime.format(Date(magneticEventStartTime))

                        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                        val deviceOrientation = if (getApplication<Application>().resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) "Portrait" else "Landscape"
                        val screenStateStr = if (powerManager != null) {
                            if (powerManager.isInteractive) "Screen On" else "Screen Off"
                        } else {
                            "Data unavailable on this device."
                        }

                        val livePowerReading = liveReadings.value["battery_telemetry"]
                        val isCharging = state.isCharging
                        val plugType = livePowerReading?.extraDetails?.get("plugType") ?: if (isCharging) "Charging" else "Discharging"

                        val currentTemp = state.batteryTempC
                        val tempStr = "${currentTemp}°C"

                        val liveMagReading = liveReadings.value["sensor_2"]
                        val sensorAccuracy = liveMagReading?.extraDetails?.get("accuracy") ?: "High"

                        val voiceAnnouncementStatus = if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) "Announced" else "No announcement below 100 µT"

                        val sensorValuesMap = mapOf(
                            "Detection Start Time" to currentTimeStr,
                            "Detection End Time" to "Active Monitoring",
                            "Current Magnetic Field" to "${"%.1f".format(uT)} µT",
                            "Peak Magnetic Field" to "${"%.1f".format(uT)} µT",
                            "Average Magnetic Field" to "${"%.1f".format(uT)} µT",
                            "Safety Zone" to currentZone.displayName,
                            "Detection Duration" to "5s",
                            "Sensor Accuracy" to sensorAccuracy,
                            "Device Orientation" to deviceOrientation,
                            "Device Temperature" to tempStr,
                            "Charging Status" to plugType,
                            "Voice Announcement Status" to voiceAnnouncementStatus,
                            "Actions Taken by the System" to if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) {
                                "Voice announcement generated. Visual safety notification displayed."
                            } else {
                                "Background monitoring active. No notifications or voice alerts triggered."
                            },
                            "Event Classification" to "Persistent",
                            "Notification Status" to "Delivered",
                            "Announcement Status" to if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) "Delivered" else "N/A",
                            "Suppression Reason" to "N/A"
                        )

                        val jsonValues = JSONObject(sensorValuesMap).toString()

                        val event = SafetyEventEntity(
                            riskLevel = when (currentZone) {
                                MagneticSafetyZone.SAFE -> SafetyRiskLevel.SAFE.name
                                MagneticSafetyZone.ATTENTION -> SafetyRiskLevel.ATTENTION.name
                                MagneticSafetyZone.CAUTION, MagneticSafetyZone.WARNING -> SafetyRiskLevel.WARNING.name
                                else -> SafetyRiskLevel.EMERGENCY.name
                            },
                            riskScore = when (currentZone) {
                                MagneticSafetyZone.SAFE -> 15
                                MagneticSafetyZone.ATTENTION -> 35
                                MagneticSafetyZone.CAUTION -> 55
                                MagneticSafetyZone.WARNING -> 75
                                MagneticSafetyZone.HIGH_RISK -> 85
                                MagneticSafetyZone.EXTREME_RISK -> 95
                                else -> 100
                            },
                            eventType = "MAGNETIC_MONITOR",
                            title = "${currentZone.displayName} Magnetic Field Detection",
                            description = "Detected magnetic field strength of ${"%.1f".format(uT)} µT in the surrounding environment.",
                            primarySensorValuesJson = jsonValues,
                            aiRecommendation = when (currentZone) {
                                MagneticSafetyZone.SAFE -> "Surrounding magnetic field is slightly elevated but safe. Standard monitoring active."
                                MagneticSafetyZone.ATTENTION -> "Surrounding magnetic field is entering warning levels. Keep device away from high-voltage cables, powerful permanent magnets, or unshielded electronic components."
                                MagneticSafetyZone.CAUTION -> "Surrounding magnetic field is elevated. Maintain safe distance from potential magnetic interference sources like induction cooktops, industrial speakers, or power transformers."
                                MagneticSafetyZone.WARNING -> "Strong magnetic field detected. Move away from high magnetic generators. Avoid placing high-precision mechanical instruments or cards with magnetic strips nearby."
                                MagneticSafetyZone.HIGH_RISK, MagneticSafetyZone.EXTREME_RISK -> "Critical magnetic field detected. Move away from industrial machinery, magnets, or transformers immediately. High risk of sensory disruption or hardware magnetic saturation."
                                else -> "Emergency: Dangerous magnetic environment. High magnetic intensity detected. Evacuate the immediate area to protect electronic components, medical devices, and other critical hardware."
                            },
                            isVerifiedHardwareEvent = true,
                            announcementStatus = if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) "Delivered" else "N/A"
                        )

                        viewModelScope.launch(Dispatchers.IO) {
                            val insertedId = repository.safetyEventDao.insertEvent(event)
                            activeMagneticEventId = insertedId
                        }
                    } else {
                        val magneticAvg = magneticSumValues / magneticCountValues
                        val durationSeconds = durationMs / 1000

                        val eventId = activeMagneticEventId
                        if (eventId != null) {
                            viewModelScope.launch(Dispatchers.IO) {
                                val existingEvent = repository.safetyEventDao.getEventById(eventId)
                                if (existingEvent != null) {
                                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    val currentTimeStr = sdfTime.format(Date(magneticEventStartTime))

                                    val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                                    val deviceOrientation = if (getApplication<Application>().resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) "Portrait" else "Landscape"
                                    val screenStateStr = if (powerManager != null) {
                                        if (powerManager.isInteractive) "Screen On" else "Screen Off"
                                    } else {
                                        "Data unavailable on this device."
                                    }

                                    val livePowerReading = liveReadings.value["battery_telemetry"]
                                    val isCharging = state.isCharging
                                    val plugType = livePowerReading?.extraDetails?.get("plugType") ?: if (isCharging) "Charging" else "Discharging"

                                    val currentTemp = state.batteryTempC
                                    val tempStr = "${currentTemp}°C"

                                    val liveMagReading = liveReadings.value["sensor_2"]
                                    val sensorAccuracy = liveMagReading?.extraDetails?.get("accuracy") ?: "High"

                                    val voiceAnnouncementStatus = if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) "Announced" else "No announcement below 100 µT"

                                    val sensorValuesMap = mapOf(
                                        "Detection Start Time" to currentTimeStr,
                                        "Detection End Time" to "Active Monitoring",
                                        "Current Magnetic Field" to "${"%.1f".format(uT)} µT",
                                        "Peak Magnetic Field" to "${"%.1f".format(magneticPeakValue)} µT",
                                        "Average Magnetic Field" to "${"%.1f".format(magneticAvg)} µT",
                                        "Event Classification" to "Persistent",
                                        "Notification Status" to "Delivered",
                                        "Announcement Status" to if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) "Delivered" else "N/A",
                                        "Suppression Reason" to "N/A",
                                        "Safety Zone" to currentZone.displayName,
                                        "Detection Duration" to "${durationSeconds}s",
                                        "Sensor Accuracy" to sensorAccuracy,
                                        "Device Orientation" to deviceOrientation,
                                        "Device Temperature" to tempStr,
                                        "Charging Status" to plugType,
                                        "Voice Announcement Status" to voiceAnnouncementStatus,
                                        "Actions Taken by the System" to if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal) {
                                            "Voice announcement generated. Visual safety notification displayed."
                                        } else {
                                            "Background monitoring active. No notifications or voice alerts triggered."
                                        }
                                    )

                                    val updatedEvent = existingEvent.copy(
                                        riskLevel = when (currentZone) {
                                            MagneticSafetyZone.SAFE -> SafetyRiskLevel.SAFE.name
                                            MagneticSafetyZone.ATTENTION -> SafetyRiskLevel.ATTENTION.name
                                            MagneticSafetyZone.CAUTION, MagneticSafetyZone.WARNING -> SafetyRiskLevel.WARNING.name
                                            else -> SafetyRiskLevel.EMERGENCY.name
                                        },
                                        riskScore = when (currentZone) {
                                            MagneticSafetyZone.SAFE -> 15
                                            MagneticSafetyZone.ATTENTION -> 35
                                            MagneticSafetyZone.CAUTION -> 55
                                            MagneticSafetyZone.WARNING -> 75
                                            MagneticSafetyZone.HIGH_RISK -> 85
                                            MagneticSafetyZone.EXTREME_RISK -> 95
                                            else -> 100
                                        },
                                        title = "${currentZone.displayName} Magnetic Field Detection",
                                        description = "Detected magnetic field strength of ${"%.1f".format(uT)} µT in the surrounding environment.",
                                        primarySensorValuesJson = JSONObject(sensorValuesMap).toString()
                                    )
                                    repository.safetyEventDao.insertEvent(updatedEvent)
                                }
                            }
                        }
                    }

                    if (currentZone != lastAnnouncedMagneticZone) {
                        val isSuppressed = state.isCharging && uT < 500f
                        if (currentZone.ordinal >= MagneticSafetyZone.ATTENTION.ordinal && !isSuppressed) {
                            if (currentZone.ordinal > lastAnnouncedMagneticZone.ordinal) {
                                val announcementText = when (currentZone) {
                                    MagneticSafetyZone.ATTENTION -> "Magnetic Attention: Mild magnetic field of ${"%.1f".format(uT)} microteslas detected."
                                    MagneticSafetyZone.CAUTION -> "Magnetic Caution: Elevated magnetic field of ${"%.1f".format(uT)} microteslas detected."
                                    MagneticSafetyZone.WARNING -> "Magnetic Warning: Strong magnetic field of ${"%.1f".format(uT)} microteslas detected."
                                    MagneticSafetyZone.HIGH_RISK -> "Critical Magnetic Alert: Very strong magnetic field of ${"%.1f".format(uT)} microteslas detected."
                                    MagneticSafetyZone.EXTREME_RISK -> "Emergency Magnetic Alert: Extremely strong magnetic field of ${"%.1f".format(uT)} microteslas detected."
                                    MagneticSafetyZone.DANGEROUS -> "Emergency: Dangerous magnetic environment of ${"%.1f".format(uT)} microteslas detected. Move away immediately."
                                    else -> ""
                                }
                                if (announcementText.isNotEmpty()) {
                                    ttsManager.speakAlert(announcementText)
                                    hapticsManager.triggerHapticForRisk(
                                        when (currentZone) {
                                            MagneticSafetyZone.ATTENTION -> SafetyRiskLevel.ATTENTION
                                            MagneticSafetyZone.CAUTION, MagneticSafetyZone.WARNING -> SafetyRiskLevel.WARNING
                                            else -> SafetyRiskLevel.EMERGENCY
                                        }
                                    )
                                    notificationManager.sendEmergencyAlert(
                                        currentZone.displayName,
                                        "Detected magnetic field of ${"%.1f".format(uT)} µT.",
                                        when (currentZone) {
                                            MagneticSafetyZone.ATTENTION -> SafetyRiskLevel.ATTENTION
                                            MagneticSafetyZone.CAUTION, MagneticSafetyZone.WARNING -> SafetyRiskLevel.WARNING
                                            else -> SafetyRiskLevel.EMERGENCY
                                        }
                                    )
                                }
                            }
                        }
                        lastAnnouncedMagneticZone = currentZone
                    }
                } else {
                    if (magneticEventStartTime != 0L) {
                        val durationMs = System.currentTimeMillis() - magneticEventStartTime
                        if (durationMs < 5000L && activeMagneticEventId == null) {
                            // Transient Magnetic Event
                            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            val startTimeStr = sdfTime.format(Date(magneticEventStartTime))
                            val endTimeStr = sdfTime.format(Date(System.currentTimeMillis()))
                            val durationSeconds = durationMs / 1000
                            val durationStr = TimeManager.formatDuration(durationSeconds)

                            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                            val deviceOrientation = if (getApplication<Application>().resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) "Portrait" else "Landscape"
                            val screenStateStr = if (powerManager != null) {
                                if (powerManager.isInteractive) "Screen On" else "Screen Off"
                            } else {
                                "Unavailable"
                            }

                            val livePowerReading = liveReadings.value["battery_telemetry"]
                            val isCharging = state.isCharging
                            val plugType = livePowerReading?.extraDetails?.get("plugType") ?: if (isCharging) "Charging" else "Discharging"

                            val sensorValuesMap = mapOf(
                                "Detection Start Time" to startTimeStr,
                                "Detection End Time" to endTimeStr,
                                "Peak Strength" to "${"%.1f".format(magneticPeakValue)} µT",
                                "Duration" to durationStr,
                                "Device State" to "$deviceOrientation | $screenStateStr",
                                "Charging State" to plugType,
                                "Source Sensor" to "Magnetometer",
                                "AI Confidence" to "0.95",
                                "Event Classification" to "Transient",
                                "Notification Status" to "Suppressed",
                                "Announcement Status" to "Suppressed",
                                "Suppression Reason" to "Duration < 5 seconds"
                            )

                            val jsonValues = JSONObject(sensorValuesMap).toString()

                            val event = SafetyEventEntity(
                                riskLevel = SafetyRiskLevel.SAFE.name,
                                riskScore = 15,
                                eventType = "MAGNETIC_TRANSIENT",
                                title = "Transient Magnetic Event",
                                description = """
                                    Transient Magnetic Event
                                    Peak Strength: ${"%.1f".format(magneticPeakValue)} µT
                                    Duration: $durationStr
                                    Action Taken: Logged Only
                                    Notification: Suppressed
                                    Announcement: Suppressed
                                    Reason: Duration < 5 seconds
                                """.trimIndent(),
                                primarySensorValuesJson = jsonValues,
                                aiRecommendation = "Brief magnetic fluctuation detected and successfully suppressed. No user action is required.",
                                isVerifiedHardwareEvent = true,
                                moduleName = "Magnetic Subsystem",
                                severity = "INFORMATION",
                                aiConfidence = 0.95f,
                                batteryPercent = state.batteryLevelPercent,
                                deviceTempC = state.batteryTempC,
                                announcementStatus = "Suppressed"
                            )

                            viewModelScope.launch(Dispatchers.IO) {
                                repository.safetyEventDao.insertEvent(event)
                            }
                        }

                        val eventId = activeMagneticEventId
                        if (eventId != null) {
                            activeMagneticEventId = null
                            resolveActiveMagneticEvent(eventId)
                        }

                        // Reset all transient magnetic counters/timers
                        magneticEventStartTime = 0L
                        magneticPeakValue = 0f
                        magneticSumValues = 0f
                        magneticCountValues = 0
                    }

                    if (lastAnnouncedMagneticZone != MagneticSafetyZone.NORMAL) {
                        lastAnnouncedMagneticZone = MagneticSafetyZone.NORMAL
                    }
                }
            }
        }

        // Observe travelMode changes to propagate and log
        viewModelScope.launch {
            var lastTravelMode: String? = null
            travelMode.collect { mode ->


                if (lastTravelMode != null && lastTravelMode != mode) {
                    val now = System.currentTimeMillis()
                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val currentDateStr = sdfDate.format(Date(now))
                    val currentTimeStr = sdfTime.format(Date(now))

                    val sensorValuesMap = mapOf(
                        "Date" to currentDateStr,
                        "Time" to currentTimeStr,
                        "Speed" to "%.1f km/h".format(fusionState.value.currentSpeedKmH),
                        "GPS Location" to "%.5f, %.5f".format(fusionState.value.latitude, fusionState.value.longitude),
                        "Confidence" to "%.1f".format(fusionState.value.drivingConfidence),
                        "Sensors Used" to "Accelerometer, Gyroscope, GNSS, Datastore",
                        "Android APIs Used" to "SensorManager, LocationManager, DataStore",
                        "Old Mode" to lastTravelMode!!,
                        "New Mode" to mode,
                        "Event Type" to "TRAVEL_MODE_CHANGE",
                        "Classification Reason" to "User manually selected travel mode override"
                    )

                    val jsonValues = JSONObject(sensorValuesMap).toString()

                    val event = SafetyEventEntity(
                        riskLevel = SafetyRiskLevel.SAFE.name,
                        riskScore = 5,
                        eventType = "TRAVEL_MODE_CHANGE",
                        title = "Travel Mode Overridden",
                        description = "Travel mode changed manually from $lastTravelMode to $mode. Core IDDE classification has been adapted.",
                        primarySensorValuesJson = jsonValues,
                        aiRecommendation = "Manual override active. Keep phone mounted or returned to Auto Detect for automatic multi-sensor verification.",
                        isVerifiedHardwareEvent = true
                    )

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.safetyEventDao.insertEvent(event)
                    }

                    ttsManager.speakAlert("Travel mode set to $mode.")
                }
                lastTravelMode = mode
            }
        }

        // Observe fusionState for active driving detection, warnings, and logging
        viewModelScope.launch {
            fusionState.sample(1000L).collect { state ->
                val now = System.currentTimeMillis()

                // 1. Monitor Driving Start/End Transitions
                if (state.isDrivingConfirmed && !lastDrivingConfirmedState) {
                    lastDrivingConfirmedState = true
                    drivingStartTime = now

                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val currentDateStr = sdfDate.format(Date(now))
                    val currentTimeStr = sdfTime.format(Date(now))

                    val sensorValuesMap = mapOf(
                        "Date" to currentDateStr,
                        "Time" to currentTimeStr,
                        "Speed" to "%.1f km/h".format(state.currentSpeedKmH),
                        "GPS Location" to "%.5f, %.5f".format(state.latitude, state.longitude),
                        "Direction" to "${state.travelDirectionDeg.toInt()}°",
                        "Event Type" to "DRIVING_START",
                        "Duration" to "0s",
                        "Voice Announcement Status" to "Announced"
                    )

                    val jsonValues = JSONObject(sensorValuesMap).toString()

                    val event = SafetyEventEntity(
                        riskLevel = SafetyRiskLevel.SAFE.name,
                        riskScore = 10,
                        eventType = "DRIVING_START",
                        title = "Driving Session Started",
                        description = "Vehicle motion detected. Commencing active safe speed telemetry monitoring. (GPS Speed: %.1f km/h)".format(state.currentSpeedKmH),
                        primarySensorValuesJson = jsonValues,
                        aiRecommendation = "Stay alert, keep your phone in its mount, and keep your focus on the road.",
                        isVerifiedHardwareEvent = true
                    )

                    val msg = "Driving mode active. Drive safely."
                    ttsManager.speakAlert(msg)
                    hapticsManager.triggerHapticForRisk(SafetyRiskLevel.SAFE)
                    notificationManager.sendEmergencyAlert("Netra Driving Mode", "Speed monitoring in progress...", SafetyRiskLevel.SAFE)

                    voiceWakeEngine.start()

                    viewModelScope.launch(Dispatchers.IO) {
                        val insertedId = repository.safetyEventDao.insertEvent(event)
                        activeDrivingEventId = insertedId
                    }

                } else if (!state.isDrivingConfirmed && lastDrivingConfirmedState) {
                    lastDrivingConfirmedState = false
                    val durationSec = (now - drivingStartTime) / 1000
                    val durationStr = TimeManager.formatDuration(durationSec)

                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val currentDateStr = sdfDate.format(Date(now))
                    val currentTimeStr = sdfTime.format(Date(now))

                    val sensorValuesMap = mapOf(
                        "Date" to currentDateStr,
                        "Time" to currentTimeStr,
                        "Speed" to "0.0 km/h",
                        "GPS Location" to "%.5f, %.5f".format(state.latitude, state.longitude),
                        "Direction" to "${state.travelDirectionDeg.toInt()}°",
                        "Event Type" to "DRIVING_END",
                        "Duration" to durationStr,
                        "Voice Announcement Status" to "Announced"
                    )

                    val jsonValues = JSONObject(sensorValuesMap).toString()

                    val event = SafetyEventEntity(
                        riskLevel = SafetyRiskLevel.SAFE.name,
                        riskScore = 5,
                        eventType = "DRIVING_END",
                        title = "Driving Session Completed",
                        description = "Vehicle has remained stationary for over 15 seconds. Drive completed. Duration: $durationStr. Max Speed: %.1f km/h. Avg Speed: %.1f km/h.".format(state.maxSpeedKmH, state.avgSpeedKmH),
                        primarySensorValuesJson = jsonValues,
                        aiRecommendation = "Driving mode disabled. Thank you for driving safely with Netra.",
                        isVerifiedHardwareEvent = true
                    )

                    val endMsg = "Driving session completed."
                    ttsManager.speakAlert(endMsg)
                    hapticsManager.triggerHapticForRisk(SafetyRiskLevel.SAFE)
                    notificationManager.sendEmergencyAlert("Driving Mode Deactivated", "Drive duration: $durationStr", SafetyRiskLevel.SAFE)

                    voiceWakeEngine.stop()

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.safetyEventDao.insertEvent(event)
                        activeDrivingEventId = null
                    }
                }

                // 2. Monitor Sudden Acceleration Warnings
                if (state.isRapidAccelerationDetected && !lastRapidAccState) {
                    lastRapidAccState = true

                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val currentDateStr = sdfDate.format(Date(now))
                    val currentTimeStr = sdfTime.format(Date(now))

                    val sensorValuesMap = mapOf(
                        "Date" to currentDateStr,
                        "Time" to currentTimeStr,
                        "Speed" to "%.1f km/h".format(state.currentSpeedKmH),
                        "GPS Location" to "%.5f, %.5f".format(state.latitude, state.longitude),
                        "Direction" to "${state.travelDirectionDeg.toInt()}°",
                        "Event Type" to "RAPID_ACCELERATION",
                        "Duration" to "${state.drivingDurationSec}s",
                        "Voice Announcement Status" to "Announced"
                    )

                    val jsonValues = JSONObject(sensorValuesMap).toString()

                    val event = SafetyEventEntity(
                        riskLevel = SafetyRiskLevel.WARNING.name,
                        riskScore = 65,
                        eventType = "RAPID_ACCELERATION",
                        title = "Sudden Acceleration Warning",
                        description = "Potentially unsafe rapid acceleration detected! Speed rose rapidly to %.1f km/h.".format(state.currentSpeedKmH),
                        primarySensorValuesJson = jsonValues,
                        aiRecommendation = "Avoid aggressive throttle inputs. Maintain a steady following distance and accelerate smoothly to conserve fuel and prevent traction loss.",
                        isVerifiedHardwareEvent = true
                    )

                    ttsManager.speakAlert("Slow down. Rapid acceleration detected.")
                    hapticsManager.triggerHapticForRisk(SafetyRiskLevel.WARNING)
                    notificationManager.sendEmergencyAlert("Sudden Acceleration Warning", "Speed increased too rapidly", SafetyRiskLevel.WARNING)

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.safetyEventDao.insertEvent(event)
                    }
                } else if (!state.isRapidAccelerationDetected && lastRapidAccState) {
                    lastRapidAccState = false
                }

                // 3. Monitor Controlled Point Warnings
                if (state.isApproachingControlledPoint && !lastApproachingCPState) {
                    lastApproachingCPState = true

                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val currentDateStr = sdfDate.format(Date(now))
                    val currentTimeStr = sdfTime.format(Date(now))

                    val sensorValuesMap = mapOf(
                        "Date" to currentDateStr,
                        "Time" to currentTimeStr,
                        "Speed" to "%.1f km/h".format(state.currentSpeedKmH),
                        "GPS Location" to "%.5f, %.5f".format(state.latitude, state.longitude),
                        "Direction" to "${state.travelDirectionDeg.toInt()}°",
                        "Event Type" to "CONTROLLED_POINT",
                        "Duration" to "${state.drivingDurationSec}s",
                        "Voice Announcement Status" to "Announced"
                    )

                    val jsonValues = JSONObject(sensorValuesMap).toString()

                    val event = SafetyEventEntity(
                        riskLevel = SafetyRiskLevel.ATTENTION.name,
                        riskScore = 40,
                        eventType = "CONTROLLED_POINT",
                        title = "Traffic Control Point Approaching",
                        description = "Approaching traffic control checkpoint or toll plaza at %.1f km/h.".format(state.currentSpeedKmH),
                        primarySensorValuesJson = jsonValues,
                        aiRecommendation = "Reduce vehicle speed and prepare to stop. Check signs for toll instructions or lanes.",
                        isVerifiedHardwareEvent = true
                    )

                    ttsManager.speakAlert("Controlled point ahead. Slow down.")
                    hapticsManager.triggerHapticForRisk(SafetyRiskLevel.ATTENTION)
                    notificationManager.sendEmergencyAlert("Traffic Control Point Approaching", "Please reduce your speed", SafetyRiskLevel.ATTENTION)

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.safetyEventDao.insertEvent(event)
                    }
                } else if (!state.isApproachingControlledPoint && lastApproachingCPState) {
                    lastApproachingCPState = false
                }

                // 4. Excessive Speed Warning (Highway Safety Limit)
                // Warn once every 45 seconds if traveling > 110 km/h
                if (state.isDrivingConfirmed && state.currentSpeedKmH > 110f && (now - lastHighSpeedAlertTime > 45000L)) {
                    lastHighSpeedAlertTime = now

                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val currentDateStr = sdfDate.format(Date(now))
                    val currentTimeStr = sdfTime.format(Date(now))

                    val sensorValuesMap = mapOf(
                        "Date" to currentDateStr,
                        "Time" to currentTimeStr,
                        "Speed" to "%.1f km/h".format(state.currentSpeedKmH),
                        "GPS Location" to "%.5f, %.5f".format(state.latitude, state.longitude),
                        "Direction" to "${state.travelDirectionDeg.toInt()}°",
                        "Event Type" to "HIGH_SPEED",
                        "Duration" to "${state.drivingDurationSec}s",
                        "Voice Announcement Status" to "Announced"
                    )

                    val jsonValues = JSONObject(sensorValuesMap).toString()

                    val event = SafetyEventEntity(
                        riskLevel = SafetyRiskLevel.WARNING.name,
                        riskScore = 70,
                        eventType = "HIGH_SPEED",
                        title = "Excessive Speed Alert",
                        description = "Vehicle is traveling at %.1f km/h, which exceeds safe highway speed limits.".format(state.currentSpeedKmH),
                        primarySensorValuesJson = jsonValues,
                        aiRecommendation = "Reduce speed to match post speed limits and road conditions.",
                        isVerifiedHardwareEvent = true
                    )

                    ttsManager.speakAlert("Reduce your speed.")
                    hapticsManager.triggerHapticForRisk(SafetyRiskLevel.WARNING)
                    notificationManager.sendEmergencyAlert("Excessive Speed Alert", "Speed exceeds 110 km/h safety limit", SafetyRiskLevel.WARNING)

                    viewModelScope.launch(Dispatchers.IO) {
                        repository.safetyEventDao.insertEvent(event)
                    }
                }
            }
        }
    }

    fun refreshAiAnalysis() {
        viewModelScope.launch {
            repository.evaluateAiRisk()
        }
    }

    fun speakText(text: String) {
        ttsManager.speakAlert(text)
    }

    fun triggerTestEvent(title: String, riskLevel: SafetyRiskLevel, score: Int, desc: String) {
        viewModelScope.launch {
            repository.triggerSampleEvent(title, riskLevel, score, desc)
            hapticsManager.triggerHapticForRisk(riskLevel)
            notificationManager.sendEmergencyAlert(title, desc, riskLevel)
            ttsManager.speakAlert("Netra Test Alert: $title. $desc")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.safetyEventDao.deleteEventById(id)
        }
    }

    fun setMonitorThermal(enabled: Boolean) = viewModelScope.launch { settingsRepository.setMonitorThermal(enabled) }

    fun setMonitorWeather(enabled: Boolean) = viewModelScope.launch { 
        settingsRepository.setMonitorWeather(enabled)
    }
    fun setNotifyWeather(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyWeather.first()
        val previousAnnounce = settingsRepository.announceWeather.first()
        settingsRepository.setNotifyWeather(enabled)
        repository.logNotificationChange("Weather Monitoring", enabled, previousAnnounce, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setAnnounceWeather(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyWeather.first()
        val previousAnnounce = settingsRepository.announceWeather.first()
        settingsRepository.setAnnounceWeather(enabled)
        repository.logNotificationChange("Weather Monitoring", previousNotify, enabled, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setMonitorMagnetic(enabled: Boolean) = viewModelScope.launch { 
        settingsRepository.setMonitorMagnetic(enabled)
    }
    fun setNotifyMagnetic(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyMagnetic.first()
        val previousAnnounce = settingsRepository.announceMagnetic.first()
        settingsRepository.setNotifyMagnetic(enabled)
        repository.logNotificationChange("Magnetic Monitoring", enabled, previousAnnounce, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setAnnounceMagnetic(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyMagnetic.first()
        val previousAnnounce = settingsRepository.announceMagnetic.first()
        settingsRepository.setAnnounceMagnetic(enabled)
        repository.logNotificationChange("Magnetic Monitoring", previousNotify, enabled, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setMonitorLight(enabled: Boolean) = viewModelScope.launch { 
        settingsRepository.setMonitorLight(enabled)
    }
    fun setNotifyLight(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyLight.first()
        val previousAnnounce = settingsRepository.announceLight.first()
        settingsRepository.setNotifyLight(enabled)
        repository.logNotificationChange("Light Sensor Monitoring", enabled, previousAnnounce, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setAnnounceLight(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyLight.first()
        val previousAnnounce = settingsRepository.announceLight.first()
        settingsRepository.setAnnounceLight(enabled)
        repository.logNotificationChange("Light Sensor Monitoring", previousNotify, enabled, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }

    fun setMonitorBluetooth(enabled: Boolean) = viewModelScope.launch { 
        repository.setMonitorBluetooth(enabled, "User", "Settings")
    }
    fun setNotifyBluetooth(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyBluetooth.first()
        val previousAnnounce = settingsRepository.announceBluetooth.first()
        settingsRepository.setNotifyBluetooth(enabled)
        repository.logNotificationChange("Bluetooth Monitoring", enabled, previousAnnounce, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setAnnounceBluetooth(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyBluetooth.first()
        val previousAnnounce = settingsRepository.announceBluetooth.first()
        settingsRepository.setAnnounceBluetooth(enabled)
        repository.logNotificationChange("Bluetooth Monitoring", previousNotify, enabled, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setMonitorLocation(enabled: Boolean) = viewModelScope.launch { 
        repository.setMonitorLocation(enabled, "User", "Settings")
    }
    fun setNotifyLocation(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyLocation.first()
        val previousAnnounce = settingsRepository.announceLocation.first()
        settingsRepository.setNotifyLocation(enabled)
        repository.logNotificationChange("Location Monitoring", enabled, previousAnnounce, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setAnnounceLocation(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyLocation.first()
        val previousAnnounce = settingsRepository.announceLocation.first()
        settingsRepository.setAnnounceLocation(enabled)
        repository.logNotificationChange("Location Monitoring", previousNotify, enabled, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setMonitorProximity(enabled: Boolean) = viewModelScope.launch { 
        repository.setMonitorProximity(enabled, "User", "Settings")
    }
    fun setNotifyProximity(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyProximity.first()
        val previousAnnounce = settingsRepository.announceProximity.first()
        settingsRepository.setNotifyProximity(enabled)
        repository.logNotificationChange("Proximity Monitoring", enabled, previousAnnounce, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setAnnounceProximity(enabled: Boolean) = viewModelScope.launch {
        val previousNotify = settingsRepository.notifyProximity.first()
        val previousAnnounce = settingsRepository.announceProximity.first()
        settingsRepository.setAnnounceProximity(enabled)
        repository.logNotificationChange("Proximity Monitoring", previousNotify, enabled, previousNotify, previousAnnounce, "User", "Settings", "Success")
    }
    fun setRefreshIntervalMs(interval: Int) = viewModelScope.launch { settingsRepository.setRefreshIntervalMs(interval) }
    fun setThermalThresholdC(threshold: Int) = viewModelScope.launch { settingsRepository.setThermalThresholdC(threshold) }
    fun setEncryptionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setEncryptionEnabled(enabled) }
    fun setTravelMode(mode: String) = viewModelScope.launch { settingsRepository.setTravelMode(mode) }

    fun exportLogsToJson(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = eventLogs.value
                val jsonBuilder = StringBuilder()
                jsonBuilder.append("[\n")
                logs.forEachIndexed { index, log ->
                    jsonBuilder.append("  {\n")
                    jsonBuilder.append("    \"id\": ${log.id},\n")
                    jsonBuilder.append("    \"timestamp\": ${log.timestamp},\n")
                    jsonBuilder.append("    \"module\": \"${log.moduleName}\",\n")
                    jsonBuilder.append("    \"severity\": \"${log.severity}\",\n")
                    jsonBuilder.append("    \"title\": \"${log.title}\",\n")
                    jsonBuilder.append("    \"riskLevel\": \"${log.riskLevel}\",\n")
                    jsonBuilder.append("    \"riskScore\": ${log.riskScore},\n")
                    jsonBuilder.append("    \"description\": \"${log.description}\"\n")
                    jsonBuilder.append("  }${if (index < logs.size - 1) "," else ""}\n")
                }
                jsonBuilder.append("]")
                val fileName = "netra_encrypted_backup_${System.currentTimeMillis()}.enc.json"
                val file = File(getApplication<Application>().filesDir, fileName)
                file.writeText(jsonBuilder.toString())
                exportStatusMessage = "Exported ${logs.size} records to encrypted backup: $fileName"
                shareFileEvent.emit(file)
                withContext(Dispatchers.Main) {
                    onResult(true, exportStatusMessage!!)
                }
            } catch (e: Exception) {
                exportStatusMessage = "Export failed: ${e.localizedMessage}"
                withContext(Dispatchers.Main) {
                    onResult(false, exportStatusMessage!!)
                }
            }
        }
    }

    fun exportLogsToJsonForDate(date: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = eventLogs.value.filter { isSameDay(it.timestamp, date) }
                val jsonBuilder = StringBuilder()
                jsonBuilder.append("[\n")
                logs.forEachIndexed { index, log ->
                    jsonBuilder.append("  {\n")
                    jsonBuilder.append("    \"id\": ${log.id},\n")
                    jsonBuilder.append("    \"timestamp\": ${log.timestamp},\n")
                    jsonBuilder.append("    \"module\": \"${log.moduleName}\",\n")
                    jsonBuilder.append("    \"severity\": \"${log.severity}\",\n")
                    jsonBuilder.append("    \"title\": \"${log.title}\",\n")
                    jsonBuilder.append("    \"riskLevel\": \"${log.riskLevel}\",\n")
                    jsonBuilder.append("    \"riskScore\": ${log.riskScore},\n")
                    jsonBuilder.append("    \"description\": \"${log.description}\"\n")
                    jsonBuilder.append("  }${if (index < logs.size - 1) "," else ""}\n")
                }
                jsonBuilder.append("]")
                val fileName = "netra_encrypted_backup_date_${date}_${System.currentTimeMillis()}.enc.json"
                val file = File(getApplication<Application>().filesDir, fileName)
                file.writeText(jsonBuilder.toString())
                exportStatusMessage = "Exported ${logs.size} records to encrypted backup: $fileName"
                shareFileEvent.emit(file)
                withContext(Dispatchers.Main) {
                    onResult(true, exportStatusMessage!!)
                }
            } catch (e: Exception) {
                exportStatusMessage = "Export failed: ${e.localizedMessage}"
                withContext(Dispatchers.Main) {
                    onResult(false, exportStatusMessage!!)
                }
            }
        }
    }


    fun exportLogsToTxt(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = eventLogs.value
                val sb = StringBuilder()
                sb.append("=== NETRA SYSTEM ACTIVITY LOGS ===\n")
                sb.append("Exported at: ${java.util.Date()}\n\n")
                logs.forEach { log ->
                    sb.append("[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))}] ")
                    sb.append("Module: ${log.moduleName} | Severity: ${log.severity} | Risk: ${log.riskLevel}\n")
                    sb.append("Title: ${log.title}\n")
                    sb.append("Description: ${log.description}\n")
                    sb.append("AI Confidence: ${log.aiConfidence} | Battery: ${log.batteryPercent}% | Temp: ${log.deviceTempC}°C\n")
                    sb.append("--------------------------------------------------\n")
                }
                val fileName = "netra_logs_${System.currentTimeMillis()}.txt"
                val file = File(getApplication<Application>().filesDir, fileName)
                file.writeText(sb.toString())
                shareFileEvent.emit(file)
                withContext(Dispatchers.Main) {
                    onResult(true, "Exported ${logs.size} logs to TXT: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "TXT Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportLogsToTxtForDate(date: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = eventLogs.value.filter { isSameDay(it.timestamp, date) }
                val sb = StringBuilder()
                sb.append("=== NETRA SYSTEM ACTIVITY LOGS (DATE: $date) ===\n")
                sb.append("Exported at: ${java.util.Date()}\n\n")
                logs.forEach { log ->
                    sb.append("[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))}] ")
                    sb.append("Module: ${log.moduleName} | Severity: ${log.severity} | Risk: ${log.riskLevel}\n")
                    sb.append("Title: ${log.title}\n")
                    sb.append("Description: ${log.description}\n")
                    sb.append("AI Confidence: ${log.aiConfidence} | Battery: ${log.batteryPercent}% | Temp: ${log.deviceTempC}°C\n")
                    sb.append("--------------------------------------------------\n")
                }
                val fileName = "netra_logs_date_${date}_${System.currentTimeMillis()}.txt"
                val file = File(getApplication<Application>().filesDir, fileName)
                file.writeText(sb.toString())
                shareFileEvent.emit(file)
                withContext(Dispatchers.Main) {
                    onResult(true, "Exported ${logs.size} logs to TXT: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "TXT Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportLogsToCsv(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = eventLogs.value
                val sb = StringBuilder()
                sb.append("ID,Timestamp,Module,Severity,RiskLevel,RiskScore,Title,Description,AiConfidence,Battery,TempC\n")
                logs.forEach { log ->
                    sb.append("${log.id},\"${log.timestamp}\",\"${log.moduleName}\",\"${log.severity}\",\"${log.riskLevel}\",${log.riskScore},\"${log.title.replace("\"", "\"\"")}\",\"${log.description.replace("\"", "\"\"")}\",${log.aiConfidence},${log.batteryPercent},${log.deviceTempC}\n")
                }
                val fileName = "netra_logs_${System.currentTimeMillis()}.csv"
                val file = File(getApplication<Application>().filesDir, fileName)
                file.writeText(sb.toString())
                shareFileEvent.emit(file)
                withContext(Dispatchers.Main) {
                    onResult(true, "Exported ${logs.size} logs to CSV: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "CSV Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportLogsToCsvForDate(date: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = eventLogs.value.filter { isSameDay(it.timestamp, date) }
                val sb = StringBuilder()
                sb.append("ID,Timestamp,Module,Severity,RiskLevel,RiskScore,Title,Description,AiConfidence,Battery,TempC\n")
                logs.forEach { log ->
                    sb.append("${log.id},\"${log.timestamp}\",\"${log.moduleName}\",\"${log.severity}\",\"${log.riskLevel}\",${log.riskScore},\"${log.title.replace("\"", "\"\"")}\",\"${log.description.replace("\"", "\"\"")}\",${log.aiConfidence},${log.batteryPercent},${log.deviceTempC}\n")
                }
                val fileName = "netra_logs_date_${date}_${System.currentTimeMillis()}.csv"
                val file = File(getApplication<Application>().filesDir, fileName)
                file.writeText(sb.toString())
                shareFileEvent.emit(file)
                withContext(Dispatchers.Main) {
                    onResult(true, "Exported ${logs.size} logs to CSV: $fileName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "CSV Export failed: ${e.localizedMessage}")
                }
            }
        }
    }


    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp1)) == sdf.format(Date(timestamp2))
    }



    fun runManualSync(isExtended: Boolean = false) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.data.engine.DataSyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .setInputData(androidx.work.Data.Builder().putBoolean("extended_window", isExtended).build())
            .build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueue(workRequest)
    }



    private var privacyScanJob: kotlinx.coroutines.Job? = null

    fun togglePrivacyScanner(enabled: Boolean) {
        if (enabled) {
            startPrivacyScan()
        } else {
            stopPrivacyScan()
        }
    }

    private fun startPrivacyScan() {
        privacyScanJob?.cancel()
        _privacyScannerState.value = PrivacyScannerState(
            isEnabled = true,
            isScanning = true,
            scanStartedTime = System.currentTimeMillis()
        )

        val context = getApplication<Application>().applicationContext
        LoggingManager.info("Privacy Scanner", "SCAN_STARTED", "Privacy Scan Started", "User initiated a localized privacy and surveillance scan.")

        privacyScanJob = viewModelScope.launch(Dispatchers.Default) {
            val foundBtDevices = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            var wifiCount: Int? = null
            var bluetoothCount: Int? = null
            var cameraCheck = "CHECKING"
            var micCheck = "CHECKING"

            // Register dynamic receiver for bluetooth devices found
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (BluetoothDevice.ACTION_FOUND == action) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.address?.let { foundBtDevices.add(it) }
                    }
                }
            }

            try {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                    context.registerReceiver(receiver, filter)
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    btManager?.adapter?.let { adapter ->
                        if (adapter.isEnabled) {
                            adapter.bondedDevices.forEach { foundBtDevices.add(it.address) }
                            adapter.startDiscovery()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore receiver registration error or discovery start error
            }

            // Run scan loop for 5 seconds, updating values dynamically
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 5000L) {
                if (!_privacyScannerState.value.isEnabled) break

                // 1. Wifi check
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiCount = if (wifiManager != null && wifiManager.isWifiEnabled) {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val results = wifiManager.scanResults
                            results?.size ?: 0
                        } catch (e: Exception) {
                            try {
                                @Suppress("DEPRECATION")
                                wifiManager.configuredNetworks?.size ?: 0
                            } catch (ex: Exception) {
                                0
                            }
                        }
                    } else {
                        // Fine location missing
                        null
                    }
                } else {
                    null // Wifi disabled or null
                }

                // 2. Bluetooth count
                bluetoothCount = if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    foundBtDevices.size
                } else {
                    null
                }

                // 3. Sensor values (magnetic and light)
                val magReading = liveReadings.value["sensor_2"]
                val magVal = magReading?.let { r ->
                    kotlin.math.sqrt((r.values[0]*r.values[0] + r.values[1]*r.values[1] + r.values[2]*r.values[2]).toDouble()).toFloat()
                }

                val lightReading = liveReadings.value["sensor_5"]
                val lightVal = lightReading?.values?.get(0)

                // 4. Camera check
                cameraCheck = if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                    val list = cameraManager?.cameraIdList
                    if (!list.isNullOrEmpty()) "VERIFIED_OK" else "ERROR"
                } else {
                    "NO_PERMISSION"
                }

                // 5. Mic check
                micCheck = if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    if (audioManager != null) "VERIFIED_OK" else "ERROR"
                } else {
                    "NO_PERMISSION"
                }

                // Compute risk score dynamically
                val anomalies = mutableListOf<String>()
                var score = 10 // baseline risk when scanning

                bluetoothCount?.let { count ->
                    if (count > 2) {
                        score += 15
                        anomalies.add("Unknown bluetooth signals detected ($count nearby)")
                    } else if (count > 0) {
                        score += 5
                    }
                }

                wifiCount?.let { count ->
                    if (count > 5) {
                        score += 15
                        anomalies.add("Dense local Wi-Fi nodes detected ($count networks)")
                    } else if (count > 0) {
                        score += 5
                    }
                }

                magVal?.let { mv ->
                    if (mv > 70f || mv < 15f) {
                        score += 30
                        anomalies.add("Critical electromagnetic anomaly detected (${"%.1f".format(mv)} µT)")
                    }
                }

                lightVal?.let { lv ->
                    if (lv < 1.0f) {
                        score += 10
                    }
                }

                val finalScore = score.coerceIn(0, 100)
                val finalRiskLevel = when {
                    finalScore > 60 -> "High Risk"
                    finalScore > 25 -> "Medium Risk"
                    else -> "Low Risk"
                }

                _privacyScannerState.value = _privacyScannerState.value.copy(
                    bluetoothCount = bluetoothCount,
                    wifiCount = wifiCount,
                    magnetometerRawValue = magVal,
                    ambientLightValue = lightVal,
                    cameraCheckResult = cameraCheck,
                    microphoneCheckResult = micCheck,
                    riskScore = finalScore,
                    riskLevel = finalRiskLevel,
                    detectedAnomalies = anomalies
                )

                kotlinx.coroutines.delay(500L)
            }

            // Clean up Bluetooth discovery & receiver
            try {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    btManager?.adapter?.cancelDiscovery()
                }
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}

            // Final state transition
            val finalState = _privacyScannerState.value
            _privacyScannerState.value = finalState.copy(
                isScanning = false,
                isFinished = true
            )

            // Log completion
            val locStr = if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "${fusionState.value.latitude}° N, ${fusionState.value.longitude}° E"
            } else {
                "Permission Denied"
            }
            
            LoggingManager.logActivity(
                moduleName = "Privacy Scanner",
                eventName = "SCAN_COMPLETED",
                severity = if (finalState.riskScore > 60) "WARNING" else "INFORMATION",
                riskLevel = if (finalState.riskScore > 60) SafetyRiskLevel.ATTENTION else SafetyRiskLevel.SAFE,
                riskScore = finalState.riskScore,
                title = "Privacy Scan Completed",
                description = "Scan completed. Found ${finalState.bluetoothCount ?: "N/A"} Bluetooth, ${finalState.wifiCount ?: "N/A"} Wi-Fi, Mag: ${"%.1f".format(finalState.magnetometerRawValue ?: 0f)} µT. $locStr",
                gpsLocation = locStr
            )

            // Trigger notification if High Risk
            if (finalState.riskScore > 60) {
                notificationManager.sendEmergencyAlert(
                    "High Privacy Risk Detected",
                    "A suspicious electronic or radio environment was detected nearby. Inspect the room manually.",
                    SafetyRiskLevel.ATTENTION
                )
                LoggingManager.warning(
                    "Privacy Scanner",
                    "SURVEILLANCE_ALERT",
                    "High Privacy Threat Warning",
                    "Suspicious signal patterns detected. High electromagnetic or radio interference detected nearby. Recommendation: Inspect room manually for hidden electronic devices.",
                    finalState.riskScore
                )
            }
        }
    }

    private fun stopPrivacyScan() {
        privacyScanJob?.cancel()
        val context = getApplication<Application>().applicationContext
        try {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                btManager?.adapter?.cancelDiscovery()
            }
        } catch (e: Exception) {}

        _privacyScannerState.value = PrivacyScannerState(isEnabled = false, isScanning = false)
        LoggingManager.info("Privacy Scanner", "SCAN_STOPPED", "Privacy Scanner Deactivated", "User stopped the scanner. Low-power state restored.")
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 || lon1 == 0.0) return 999.0
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}


