package com.aistudio.netrasensorhub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistudio.netrasensorhub.data.db.AppVersionEntity
import com.aistudio.netrasensorhub.data.db.NetraDatabase
import com.aistudio.netrasensorhub.data.intelligence.sync.IntelligenceSyncManager
import com.aistudio.netrasensorhub.data.risk.RiskAnalysisEngine
import com.aistudio.netrasensorhub.data.telemetry.SensorTelemetryManager
import com.aistudio.netrasensorhub.ui.components.LiveGraphSection
import com.aistudio.netrasensorhub.ui.components.LocalIntelligenceSection
import com.aistudio.netrasensorhub.ui.components.RiskDashboardCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Netra Security Hub - Primary Console Activity
 *
 * Architecture:
 * - Single Source of Truth: Centralized SensorTelemetryManager for genuine hardware telemetry.
 * - True Data Only: Zero manufactured/fake/simulation values.
 * - Foreground-Safe: Sensors active strictly while in foreground.
 * - Internal Safety Only: Battery data used solely for thermal risk & load reduction decisions.
 */
class MainActivity : ComponentActivity() {

    private lateinit var telemetryManager: SensorTelemetryManager
    private lateinit var intelligenceSyncManager: IntelligenceSyncManager
    private val riskAnalysisEngine = RiskAnalysisEngine()

    // Authoritative Database Version State
    private val _appVersionInfo = mutableStateOf<AppVersionEntity?>(null)

    // Foreground Activity State for Lifecycle Safety
    private val _isForegroundActive = mutableStateOf(false)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            intelligenceSyncManager.triggerManualRefresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telemetryManager = SensorTelemetryManager(applicationContext)
        intelligenceSyncManager = IntelligenceSyncManager(applicationContext)

        // Sync derived motion state to IntelligenceSyncManager
        CoroutineScope(Dispatchers.Main).launch {
            telemetryManager.derivedMotionState.collect { motion ->
                intelligenceSyncManager.updateMotionState(motion)
            }
        }

        // Check if location permission is already granted on startup
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        // Initialize and read authoritative version from Room database on startup
        val db = NetraDatabase.getDatabase(applicationContext)
        val versionDao = db.appVersionDao()

        CoroutineScope(Dispatchers.IO).launch {
            var versionEntity = versionDao.getAppVersion()
            if (versionEntity == null) {
                versionEntity = AppVersionEntity(
                    versionName = "1.0.0",
                    versionCode = 1,
                    buildTag = "Netra-Security-Production-2026"
                )
                versionDao.insertVersion(versionEntity)
            }
            withContext(Dispatchers.Main) {
                _appVersionInfo.value = versionEntity
            }
        }

        setContent {
            val isDark = isSystemInDarkTheme()
            val bgColor = if (isDark) Color(0xFF010409) else Color(0xFFF0F2F5)
            val textColor = if (isDark) Color(0xFFC9D1D9) else Color(0xFF24292F)

            val telemetryState by telemetryManager.telemetryState.collectAsState()
            val intelligenceSnapshot by intelligenceSyncManager.snapshot.collectAsState()

            val overheatingResult = remember(telemetryState) { riskAnalysisEngine.evaluateOverheatingRisk(telemetryState) }
            val impactResult = remember(telemetryState) { riskAnalysisEngine.evaluateSuddenImpactRisk(telemetryState) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = bgColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header & Authoritative Version Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "NETRA SECURITY HUB",
                                    color = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Security Telemetry & Intelligence Console",
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                val ver = _appVersionInfo.value
                                if (ver != null) {
                                    Text(
                                        text = "v${ver.versionName} (${ver.buildTag}) [DB Verified]",
                                        color = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Dynamic Local Disaster & Weather Intelligence Section
                        LocalIntelligenceSection(
                            snapshot = intelligenceSnapshot,
                            onRequestLocationPermission = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onManualRefresh = {
                                intelligenceSyncManager.triggerManualRefresh()
                            }
                        )

                        // Risk Analysis Engine Dashboard Card (Security Assessment)
                        RiskDashboardCard(
                            overheatingResult = overheatingResult,
                            impactResult = impactResult
                        )

                        // ONE DEDICATED SECTION NAMED "LIVE GRAPH" (No other graphs exist in the app)
                        LiveGraphSection(
                            telemetryState = telemetryState,
                            isLiveActive = _isForegroundActive.value
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _isForegroundActive.value = true
        telemetryManager.startListening()
        intelligenceSyncManager.start()
    }

    override fun onPause() {
        super.onPause()
        _isForegroundActive.value = false
        telemetryManager.stopListening()
        intelligenceSyncManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isForegroundActive.value = false
        telemetryManager.stopListening()
        intelligenceSyncManager.stop()
    }
}
