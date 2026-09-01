package com.example.data.engine

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.location.LocationManager
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.model.FeatureCategory
import com.example.data.model.FeatureStatus
import com.example.data.model.SecurityFeature
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SecurityEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : INetraEngine {
    override val engineName: String = "SecurityEngine"
    override var isRunning: Boolean = true
        private set

    override fun startEngine() {
        isRunning = true
    }

    override fun stopEngine() {
        isRunning = false
    }

    override fun onSystemEvent(event: EngineSystemEvent) {
        if (event.type == EngineSystemEventType.EMERGENCY_ALERT) {
            scanDevice()
        }
    }

    init {
        EngineCoordinator.registerEngine(this)
    }
    private val _features = MutableStateFlow<List<SecurityFeature>>(emptyList())
    val features: StateFlow<List<SecurityFeature>> = _features.asStateFlow()

    private val _securityScore = MutableStateFlow(0)
    val securityScore: StateFlow<Int> = _securityScore.asStateFlow()

    private val _validationStatus = MutableStateFlow("INVALID")
    val validationStatus: StateFlow<String> = _validationStatus.asStateFlow()

    private val _missingMandatoryFeatures = MutableStateFlow<List<SecurityFeature>>(emptyList())
    val missingMandatoryFeatures: StateFlow<List<SecurityFeature>> = _missingMandatoryFeatures.asStateFlow()

    private val _simulatedManufacturer = MutableStateFlow("AUTO")
    val simulatedManufacturer: StateFlow<String> = _simulatedManufacturer.asStateFlow()

    private val _scanDurationMs = MutableStateFlow(0L)
    val scanDurationMs: StateFlow<Long> = _scanDurationMs.asStateFlow()

    private val _lastScanTime = MutableStateFlow(0L)
    val lastScanTime: StateFlow<Long> = _lastScanTime.asStateFlow()

    private val db = NetraDatabase.getInstance(context)
    private val safetyEventDao = db.safetyEventDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            // Read simulated manufacturer if any
            settingsRepository.getFeatureStatus("simulated_manufacturer").collect { sim ->
                _simulatedManufacturer.value = sim ?: "AUTO"
                scanDevice()
            }
        }
    }

    fun setSimulatedManufacturer(mfr: String) {
        scope.launch {
            settingsRepository.saveFeatureStatus("simulated_manufacturer", mfr)
            _simulatedManufacturer.value = mfr
            scanDevice()
        }
    }

    fun getActualManufacturer(): String {
        return Build.MANUFACTURER
    }

    fun getEffectiveManufacturer(): String {
        val sim = _simulatedManufacturer.value
        return if (sim == "AUTO") {
            getActualManufacturer()
        } else {
            sim
        }
    }

    // Direct Category A toggles
    fun toggleFeature(featureId: String, enable: Boolean) {
        scope.launch {
            val beforeScore = _securityScore.value
            val beforeStatus = _validationStatus.value
            
            val statusStr = if (enable) FeatureStatus.ENABLED.name else FeatureStatus.DISABLED.name
            settingsRepository.saveFeatureStatus(featureId, statusStr)
            
            scanDevice()
            
            val afterScore = _securityScore.value
            val afterStatus = _validationStatus.value
            
            if (beforeScore != afterScore || beforeStatus != afterStatus) {
                logSecurityEvent(
                    title = "Security Option Toggled",
                    description = "Feature '$featureId' toggled to ${if (enable) "ENABLED" else "DISABLED"}.",
                    beforeScore = beforeScore,
                    afterScore = afterScore,
                    beforeStatus = beforeStatus,
                    afterStatus = afterStatus
                )
            }
        }
    }

    fun scanDevice() {
        scope.launch {
            val startTime = System.currentTimeMillis()
            
            val beforeScore = _securityScore.value
            val beforeStatus = _validationStatus.value
            val beforeBiometric = _features.value.find { it.id == "biometric" }?.status
            
            // Load saved states for custom features or manual overrides
            val savedStatuses = mutableMapOf<String, String>()
            val list = listOf(
                "biometric", "find_my_device", "google_play_protect", "theft_detection_lock",
                "offline_device_lock", "sim_lock", "emergency_sos", "trusted_devices",
                "remote_lock", "lock_network_security", "power_menu_lock", "factory_reset_protection",
                "monitor_driving", "monitor_weather", "monitor_light", "monitor_bluetooth",
                "monitor_location", "monitor_magnetic"
            )
            for (id in list) {
                try {
                    savedStatuses[id] = settingsRepository.getFeatureStatus(id).first() ?: ""
                } catch (e: Exception) {
                    savedStatuses[id] = ""
                }
            }

            fun getSavedStatus(id: String, defaultStatus: FeatureStatus): FeatureStatus {
                val saved = savedStatuses[id] ?: ""
                return if (saved.isNotEmpty()) {
                    try {
                        FeatureStatus.valueOf(saved)
                    } catch (e: Exception) {
                        defaultStatus
                    }
                } else {
                    defaultStatus
                }
            }

            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            
            // 1. Screen Lock
            val actualSecure = try {
                keyguardManager?.isDeviceSecure ?: false
            } catch (e: Exception) {
                false
            }
            val screenLockType = try {
                settingsRepository.getFeatureStatus("screen_lock_type").first() ?: "6_DIGIT_PIN"
            } catch (e: Exception) {
                "6_DIGIT_PIN"
            }
            val isScreenLockEnabled = actualSecure
            
            // 2. Biometrics
            var biometricStatus = FeatureStatus.UNKNOWN
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bm = context.getSystemService(BiometricManager::class.java)
                    if (bm != null) {
                        val canAuthStrong = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        val canAuthWeak = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        
                        biometricStatus = when {
                            canAuthStrong == BiometricManager.BIOMETRIC_SUCCESS || canAuthWeak == BiometricManager.BIOMETRIC_SUCCESS -> FeatureStatus.ENABLED
                            canAuthStrong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED || canAuthWeak == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> FeatureStatus.DISABLED
                            canAuthStrong == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE && canAuthWeak == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> FeatureStatus.NOT_SUPPORTED
                            canAuthStrong == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE || canAuthWeak == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> FeatureStatus.UNKNOWN
                            else -> FeatureStatus.UNKNOWN
                        }
                    } else {
                        biometricStatus = FeatureStatus.NOT_SUPPORTED
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val bm = context.getSystemService(BiometricManager::class.java)
                    if (bm != null) {
                        val canAuth = bm.canAuthenticate()
                        biometricStatus = when (canAuth) {
                            BiometricManager.BIOMETRIC_SUCCESS -> FeatureStatus.ENABLED
                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> FeatureStatus.DISABLED
                            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> FeatureStatus.NOT_SUPPORTED
                            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> FeatureStatus.UNKNOWN
                            else -> FeatureStatus.UNKNOWN
                        }
                    } else {
                        biometricStatus = FeatureStatus.NOT_SUPPORTED
                    }
                } else {
                    val fingerprintManager = context.getSystemService(Context.FINGERPRINT_SERVICE) as? android.hardware.fingerprint.FingerprintManager
                    if (fingerprintManager != null) {
                        biometricStatus = if (fingerprintManager.isHardwareDetected) {
                            if (fingerprintManager.hasEnrolledFingerprints()) {
                                FeatureStatus.ENABLED
                            } else {
                                FeatureStatus.DISABLED
                            }
                        } else {
                            FeatureStatus.NOT_SUPPORTED
                        }
                    } else {
                        biometricStatus = FeatureStatus.NOT_SUPPORTED
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("SecurityEngine", "Biometric permission not granted: ${e.message}")
                biometricStatus = FeatureStatus.UNKNOWN
            } catch (e: Exception) {
                android.util.Log.e("SecurityEngine", "Biometric status detection issue: ", e)
                biometricStatus = FeatureStatus.UNKNOWN
            }
            
            // Check for manual biometric overrides
            val savedBiometric = getSavedStatus("biometric", FeatureStatus.UNKNOWN)
            val finalBiometricStatus = if (savedBiometric != FeatureStatus.UNKNOWN) {
                savedBiometric
            } else {
                biometricStatus
            }
            
            // 3. Device Encryption
            var actualEncrypted = false
            try {
                val encStatus = devicePolicyManager?.storageEncryptionStatus
                actualEncrypted = encStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                        encStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
            } catch (e: Exception) {
                // Fallback
            }

            // 4. USB Debugging
            val isUsbDebuggingEnabled = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            } catch (e: Exception) {
                false
            }
            
            // 5. Unknown Apps
            val isUnknownAppsAllowed = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.packageManager.canRequestPackageInstalls()
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }

            // 6. Location
            val isLocationOn = try {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                        locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            } catch (e: Exception) {
                false
            }

            // 7. Bluetooth
            val isBluetoothOn = try {
                BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
            } catch (e: Exception) {
                false
            }

            // 8. NFC
            val isNfcOn = try {
                NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
            } catch (e: Exception) {
                false
            }

            // Resolve OEM compatibility
            val mfr = getEffectiveManufacturer().uppercase()
            val isSamsung = mfr.contains("SAMSUNG")
            val isPixel = mfr.contains("GOOGLE") || mfr.contains("PIXEL")
            val isXiaomi = mfr.contains("XIAOMI") || mfr.contains("REDMI")
            val isRealme = mfr.contains("REALME")
            val isVivo = mfr.contains("VIVO")

            val featuresList = listOf(
                // 1. Screen Lock (Mandatory, 20 pts)
                SecurityFeature(
                    id = "screen_lock",
                    name = "Screen Lock",
                    category = FeatureCategory.B,
                    isMandatory = true,
                    scoreWeight = 20,
                    status = if (isScreenLockEnabled) FeatureStatus.ENABLED else FeatureStatus.DISABLED,
                    description = "Requires Pattern, PIN, or Password to secure device access. Currently active: ${if (isScreenLockEnabled) screenLockType.replace("_", " ") else "None"}."
                ),
                // 2. Biometric (Bonus, 15 pts)
                SecurityFeature(
                    id = "biometric",
                    name = "Biometric (Fingerprint/Face)",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 15,
                    status = finalBiometricStatus,
                    description = when (finalBiometricStatus) {
                        FeatureStatus.ENABLED -> "Secure biometric authentication (fingerprint or facial recognition) is active and verified."
                        FeatureStatus.DISABLED -> "Biometric hardware is present, but no fingerprint or face data is enrolled. Enrolling biometrics will improve safety."
                        FeatureStatus.NOT_SUPPORTED -> "Biometric authentication hardware is not supported on this device. (Feature excluded from total security score calculations)."
                        else -> "Biometric status is unknown or temporarily unavailable."
                    }
                ),
                // 3. Find My Device (Mandatory, 10 pts)
                SecurityFeature(
                    id = "find_my_device",
                    name = "Find My Device / Find Hub",
                    category = FeatureCategory.B,
                    isMandatory = true,
                    scoreWeight = 10,
                    status = getSavedStatus("find_my_device", FeatureStatus.ENABLED), // Defaults to enabled for simulated dashboard simplicity
                    description = "Locate, lock, or wipe your lost or stolen device remotely via Google Account."
                ),
                // 4. Device Encryption (Mandatory, 10 pts)
                SecurityFeature(
                    id = "device_encryption",
                    name = "Device Encryption",
                    category = FeatureCategory.B,
                    isMandatory = true,
                    scoreWeight = 10,
                    status = if (actualEncrypted || getSavedStatus("device_encryption", FeatureStatus.ENABLED) == FeatureStatus.ENABLED) FeatureStatus.ENABLED else FeatureStatus.DISABLED,
                    description = "Encrypt your device's internal storage so files are unreadable without your credentials."
                ),
                // 5. Google Play Protect (Mandatory, 10 pts)
                SecurityFeature(
                    id = "google_play_protect",
                    name = "Google Play Protect",
                    category = FeatureCategory.B,
                    isMandatory = true,
                    scoreWeight = 10,
                    status = getSavedStatus("google_play_protect", FeatureStatus.ENABLED),
                    description = "Scans apps before installation and regularly checks existing apps for safety compliance."
                ),
                // 6. Theft Protection (Mandatory, 10 pts)
                SecurityFeature(
                    id = "theft_detection_lock",
                    name = "Theft Detection Lock",
                    category = FeatureCategory.C,
                    isMandatory = true,
                    scoreWeight = 10,
                    status = if (!isPixel && !isSamsung && !isRealme) FeatureStatus.NOT_SUPPORTED else getSavedStatus("theft_detection_lock", FeatureStatus.ENABLED),
                    description = "Automatically locks your device screen when physical snatching or running patterns are detected."
                ),
                // 7. Offline Device Lock (Mandatory, 5 pts)
                SecurityFeature(
                    id = "offline_device_lock",
                    name = "Offline Device Lock",
                    category = FeatureCategory.C,
                    isMandatory = true,
                    scoreWeight = 5,
                    status = if (!isPixel) FeatureStatus.NOT_SUPPORTED else getSavedStatus("offline_device_lock", FeatureStatus.ENABLED),
                    description = "Locks the screen instantly if the device goes fully offline for a prolonged period."
                ),
                // 8. SIM Lock (PIN) (Optional, 8 pts)
                SecurityFeature(
                    id = "sim_lock",
                    name = "SIM Lock (SIM PIN)",
                    category = FeatureCategory.C,
                    isMandatory = false,
                    scoreWeight = 8,
                    status = if (isXiaomi) FeatureStatus.ENABLED else getSavedStatus("sim_lock", FeatureStatus.DISABLED),
                    description = "Protects your mobile line with a SIM PIN to block phone identity theft."
                ),
                // 9. Emergency SOS (Optional, 4 pts)
                SecurityFeature(
                    id = "emergency_sos",
                    name = "Emergency SOS",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 4,
                    status = getSavedStatus("emergency_sos", FeatureStatus.ENABLED),
                    description = "Quickly press the power button 5 times to call emergency services and share location."
                ),
                // 10. USB Debugging Disabled (Optional, 3 pts)
                SecurityFeature(
                    id = "usb_debugging",
                    name = "USB Debugging Disabled",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 3,
                    status = if (!isUsbDebuggingEnabled) FeatureStatus.ENABLED else FeatureStatus.DISABLED,
                    description = "Keep ADB/USB Debugging disabled to prevent custom code execution and device manipulation."
                ),
                // 11. NFC Security (Optional, 2 pts)
                SecurityFeature(
                    id = "nfc_security",
                    name = "NFC Security",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 2,
                    status = if (!isNfcOn) FeatureStatus.ENABLED else FeatureStatus.DISABLED, // Disabled is secure
                    description = "Ensure NFC is disabled or locked down when not in use to avoid terminal exploits."
                ),
                // 12. Trusted Device Review (Optional, 3 pts)
                SecurityFeature(
                    id = "trusted_devices",
                    name = "Trusted Device Review",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 3,
                    status = getSavedStatus("trusted_devices", FeatureStatus.ENABLED),
                    description = "Regularly audits Smart Lock and secure Bluetooth attachments connected to your account."
                ),
                // 13. Remote Lock (Optional, 0 pts)
                SecurityFeature(
                    id = "remote_lock",
                    name = "Remote Lock",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("remote_lock", FeatureStatus.ENABLED),
                    description = "Allows quick remote-locking via specific browser panels if the device is lost."
                ),
                // 14. Lock Network & Security (Optional, 0 pts)
                SecurityFeature(
                    id = "lock_network_security",
                    name = "Lock Network & Security",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("lock_network_security", FeatureStatus.ENABLED),
                    description = "Prevents turning off Wi-Fi, Mobile Data, or Location from the lock screen."
                ),
                // 15. Power Menu Lock (Optional, 0 pts, OEM-dependent)
                SecurityFeature(
                    id = "power_menu_lock",
                    name = "Power Menu Lock",
                    category = FeatureCategory.C,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = if (!isSamsung) FeatureStatus.NOT_SUPPORTED else getSavedStatus("power_menu_lock", FeatureStatus.ENABLED),
                    description = "Blocks access to power options while the device is locked, preventing unauthorized shut-downs."
                ),
                // 16. Unknown Apps (Optional, 0 pts)
                SecurityFeature(
                    id = "unknown_apps",
                    name = "Unknown Apps Installation",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = if (!isUnknownAppsAllowed) FeatureStatus.ENABLED else FeatureStatus.DISABLED,
                    description = "Secures your operating system against installations of unknown APKs from files/browsers."
                ),
                // 17. Location (Optional, 0 pts)
                SecurityFeature(
                    id = "location",
                    name = "Location Security",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = if (isLocationOn) FeatureStatus.ENABLED else FeatureStatus.DISABLED,
                    description = "Maintains high accuracy location tracking for emergency recovery and anti-theft services."
                ),
                // 18. Bluetooth (Optional, 0 pts)
                SecurityFeature(
                    id = "bluetooth",
                    name = "Bluetooth Security",
                    category = FeatureCategory.B,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = if (isBluetoothOn) FeatureStatus.ENABLED else FeatureStatus.DISABLED,
                    description = "Keep Bluetooth secured or disabled to prevent over-the-air pairing requests."
                ),
                // 19. Factory Reset Protection (Optional, 0 pts)
                SecurityFeature(
                    id = "factory_reset_protection",
                    name = "Factory Reset Protection Status",
                    category = FeatureCategory.C,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("factory_reset_protection", FeatureStatus.ENABLED),
                    description = "Blocks unauthorized setup wizard initialization after a full factory recovery reset."
                ),
                // 20. Driving Monitoring
                SecurityFeature(
                    id = "monitor_driving",
                    name = "Driving Monitoring",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("monitor_driving", FeatureStatus.ENABLED),
                    description = "Monitors driving behavior to improve safety alerts."
                ),
                // 21. Weather Monitoring
                SecurityFeature(
                    id = "monitor_weather",
                    name = "Weather Monitoring",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("monitor_weather", FeatureStatus.ENABLED),
                    description = "Monitors local weather to adjust safety thresholds."
                ),
                // 22. Light Sensor Monitoring
                SecurityFeature(
                    id = "monitor_light",
                    name = "Light Sensor Monitoring",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("monitor_light", FeatureStatus.ENABLED),
                    description = "Monitors ambient light to adjust dashboard and alerts."
                ),
                // 23. Bluetooth Monitoring
                SecurityFeature(
                    id = "monitor_bluetooth",
                    name = "Bluetooth Monitoring",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("monitor_bluetooth", FeatureStatus.ENABLED),
                    description = "Monitors Bluetooth connections for security."
                ),
                // 24. Location Monitoring
                SecurityFeature(
                    id = "monitor_location",
                    name = "Location Monitoring",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("monitor_location", FeatureStatus.ENABLED),
                    description = "Monitors location for security events."
                ),
                // 26. Magnetic Monitoring
                SecurityFeature(
                    id = "monitor_magnetic",
                    name = "Magnetic Monitoring",
                    category = FeatureCategory.A,
                    isMandatory = false,
                    scoreWeight = 0,
                    status = getSavedStatus("monitor_magnetic", FeatureStatus.ENABLED),
                    description = "Monitors magnetic sensor data."
                )
            )

            _features.value = featuresList

            // Calculate Security Score (0 to 100)
            // Score logic:
            // Screen Lock: Up to 20 pts based on type
            // Other scored features contribute their weight if ENABLED. NOT_SUPPORTED features are excluded from total score weight,
            // or we can calculate score normalized over total active weight. Let's do standard summation:
            var earnedScore = 0
            var totalWeight = 0

            for (feat in featuresList) {
                if (feat.status == FeatureStatus.NOT_SUPPORTED) continue
                
                totalWeight += feat.scoreWeight
                if (feat.status == FeatureStatus.ENABLED) {
                    if (feat.id == "screen_lock") {
                        // Screen lock strength scoring
                        val pts = when (screenLockType) {
                            "PATTERN" -> 8
                            "4_DIGIT_PIN" -> 12
                            "6_DIGIT_PIN" -> 16
                            "PASSWORD" -> 20
                            else -> 16
                        }
                        earnedScore += pts
                    } else {
                        earnedScore += feat.scoreWeight
                    }
                }
            }

            val rawScore = if (totalWeight > 0) {
                (earnedScore.toFloat() / totalWeight.toFloat() * 100f).toInt()
            } else {
                100
            }
            val finalScore = rawScore.coerceIn(0, 100)
            _securityScore.value = finalScore

            // Validate Layer 2 - Mandatory features
            // Mandatory items must be ENABLED. If any is DISABLED, validation is INVALID.
            val missing = featuresList.filter { it.isMandatory && it.status == FeatureStatus.DISABLED }
            _missingMandatoryFeatures.value = missing
            
            val finalStatus = if (missing.isEmpty()) "VALID" else "INVALID"
            _validationStatus.value = finalStatus

            _lastScanTime.value = System.currentTimeMillis()
            _scanDurationMs.value = System.currentTimeMillis() - startTime

            // Log changes automatically
            val afterBiometric = featuresList.find { it.id == "biometric" }?.status
            if (beforeBiometric != null && (beforeScore != finalScore || beforeStatus != finalStatus || beforeBiometric != afterBiometric)) {
                val changes = mutableListOf<String>()
                if (beforeBiometric != afterBiometric) {
                    changes.add("Biometric: $beforeBiometric → $afterBiometric")
                }
                if (beforeScore != finalScore) {
                    changes.add("Security Score: $beforeScore → $finalScore")
                }
                if (beforeStatus != finalStatus) {
                    changes.add("Validation: $beforeStatus → $finalStatus")
                }
                
                if (changes.isNotEmpty()) {
                    logSecurityEvent(
                        title = "Security State Scan Update",
                        description = "System auto-scan detected updates: " + changes.joinToString(", "),
                        beforeScore = beforeScore,
                        afterScore = finalScore,
                        beforeStatus = beforeStatus,
                        afterStatus = finalStatus
                    )
                }
            }
        }
    }

    private fun logSecurityEvent(
        title: String,
        description: String,
        beforeScore: Int,
        afterScore: Int,
        beforeStatus: String,
        afterStatus: String
    ) {
        scope.launch {
            try {
                val event = SafetyEventEntity(
                    timestamp = System.currentTimeMillis(),
                    riskLevel = if (afterStatus == "VALID") "SAFE" else "WARNING",
                    riskScore = afterScore,
                    eventType = "SECURITY_HUB_EVENT",
                    title = title,
                    description = "$description Score: $beforeScore → $afterScore, Validation: $beforeStatus → $afterStatus",
                    primarySensorValuesJson = "{\"score\":$afterScore, \"status\":\"$afterStatus\"}",
                    aiRecommendation = "Check Security Hub dashboard regularly to maintain absolute verification.",
                    isVerifiedHardwareEvent = true,
                    moduleName = "Security Hub",
                    severity = if (afterStatus == "VALID") "INFORMATION" else "IMPORTANT",
                    processingDurationMs = _scanDurationMs.value
                )
                safetyEventDao.insertEvent(event)
            } catch (e: Exception) {
                // Ignore DB logging failures
            }
        }
    }
}
