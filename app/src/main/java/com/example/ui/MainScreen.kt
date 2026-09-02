package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk // Let me try this.

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.event.SensorEventBus
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SensorCenterContainerScreen
import com.example.ui.screens.HistoryLogsContainerScreen
import com.example.ui.screens.ServiceManagerContainerScreen
import com.example.ui.screens.SettingsContainerScreen
import com.example.ui.screens.LiveGraphScreen
import com.example.ui.screens.PinChangeScreen
import com.example.ui.screens.SystemAuditScreen
import com.example.ui.components.PermissionHandler
import androidx.compose.material.icons.filled.Timeline
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoRed

enum class NavigationTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    SENSOR_CENTER("Sensor Hub", Icons.Default.Sensors),
    LIVE_GRAPH("Live Graph", Icons.Default.Timeline),
    HISTORY_LOGS("History & Logs", Icons.Default.Analytics),
    SERVICE_MANAGER("Service Mgr", Icons.Default.Hub),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun GeminiSummaryCard(viewModel: MainViewModel) {
    val status by viewModel.safetyStatus.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.updateSafetyStatus() }
    
    Card(modifier = Modifier.padding(16.dp).fillMaxWidth(),
         colors = CardDefaults.cardColors(containerColor = BentoCardBg)) {
        Text(text = status, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = BentoTextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    PermissionHandler()

    var selectedTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    var showAuditScreen by remember { mutableStateOf(false) }
    var showPinChangeScreen by remember { mutableStateOf(false) }

    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val fusionState by viewModel.fusionState.collectAsStateWithLifecycle()
    val riskAnalysis by viewModel.riskAnalysis.collectAsStateWithLifecycle()
    val liveReadings by viewModel.liveReadings.collectAsStateWithLifecycle()
    val sensorDiagnostics by viewModel.sensorDiagnostics.collectAsStateWithLifecycle()
    val eventLogs by viewModel.eventLogs.collectAsStateWithLifecycle()
    val watchdogStates by viewModel.watchdogModuleStates.collectAsStateWithLifecycle()

    val monitorThermal by viewModel.monitorThermal.collectAsStateWithLifecycle()
    val monitorWeather by viewModel.monitorWeather.collectAsStateWithLifecycle()
    val notifyWeather by viewModel.notifyWeather.collectAsStateWithLifecycle()
    val announceWeather by viewModel.announceWeather.collectAsStateWithLifecycle()
    val monitorLight by viewModel.monitorLight.collectAsStateWithLifecycle()
    val notifyLight by viewModel.notifyLight.collectAsStateWithLifecycle()
    val announceLight by viewModel.announceLight.collectAsStateWithLifecycle()
    val monitorBluetooth by viewModel.monitorBluetooth.collectAsStateWithLifecycle()
    val notifyBluetooth by viewModel.notifyBluetooth.collectAsStateWithLifecycle()
    val announceBluetooth by viewModel.announceBluetooth.collectAsStateWithLifecycle()
    val monitorLocation by viewModel.monitorLocation.collectAsStateWithLifecycle()
    val notifyLocation by viewModel.notifyLocation.collectAsStateWithLifecycle()
    val announceLocation by viewModel.announceLocation.collectAsStateWithLifecycle()

    val monitorMagnetic by viewModel.monitorMagnetic.collectAsStateWithLifecycle()
    val notifyMagnetic by viewModel.notifyMagnetic.collectAsStateWithLifecycle()
    val announceMagnetic by viewModel.announceMagnetic.collectAsStateWithLifecycle()
    val monitorProximity by viewModel.monitorProximity.collectAsStateWithLifecycle()
    val notifyProximity by viewModel.notifyProximity.collectAsStateWithLifecycle()
    val announceProximity by viewModel.announceProximity.collectAsStateWithLifecycle()
    val refreshIntervalMs by viewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    val thermalThresholdC by viewModel.thermalThresholdC.collectAsStateWithLifecycle()
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsStateWithLifecycle()
    val travelMode by viewModel.travelMode.collectAsStateWithLifecycle()
    val developerMode by viewModel.developerMode.collectAsStateWithLifecycle()
    val privacyScannerState by viewModel.privacyScannerState.collectAsStateWithLifecycle()

    val isDeveloperAuthenticated by viewModel.isDeveloperAuthenticated.collectAsStateWithLifecycle()
    val lockoutUntil by viewModel.lockoutUntil.collectAsStateWithLifecycle()
    val developerPinHash by viewModel.developerPinHash.collectAsStateWithLifecycle()
    val developerPinStrength by viewModel.developerPinStrength.collectAsStateWithLifecycle()
    val developerPinChangedDate by viewModel.developerPinChangedDate.collectAsStateWithLifecycle()
    val developerPinFailedAttempts by viewModel.developerPinFailedAttempts.collectAsStateWithLifecycle()
    val developerPinRecoveryKey by viewModel.developerPinRecoveryKey.collectAsStateWithLifecycle()
    val moduleHealth by viewModel.moduleHealth.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.shareFileEvent.collect { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file!!)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Log"))
        }
    }

    val persistentAlert by SensorEventBus.persistentAlert.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (!showAuditScreen && !showPinChangeScreen) {
                TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Column {
                            Text(
                                text = "SYSTEM ENGINE",
                                color = BentoTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "Netra Sensor Hub",
                                color = BentoTextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        // Glowing Green Dot Indicator
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(BentoGreenVibrant)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BentoHeroCardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "User Account",
                                tint = BentoGreenPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoBackground,
                    titleContentColor = BentoTextPrimary
                ),
                modifier = Modifier.statusBarsPadding()
            )
            }
        },
        bottomBar = {
            if (!showAuditScreen && !showPinChangeScreen) {
                NavigationBar(
                containerColor = BentoCardBg,
                contentColor = BentoTextPrimary,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(androidx.compose.foundation.BorderStroke(1.dp, BentoBorder))
                    .navigationBarsPadding()
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isCritical = viewModel.systemSafetyStatus.collectAsStateWithLifecycle().value == com.example.data.engine.IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL
                    val showIndicator = isCritical && tab == NavigationTab.DASHBOARD
                    
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { 
                            Box {
                                Icon(tab.icon, contentDescription = tab.title)
                                if (showIndicator) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(BentoRed)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BentoGreenPrimary,
                            selectedTextColor = BentoGreenPrimary,
                            indicatorColor = BentoHeroCardBg,
                            unselectedIconColor = BentoTextMuted,
                            unselectedTextColor = BentoTextMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
            }
        },
        containerColor = BentoBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BentoBackground)
        ) {
            if (persistentAlert != null) {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, contentDescription = "Alert", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${persistentAlert!!.priority}: ${persistentAlert!!.data}",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { SensorEventBus.clearAlert() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Alert", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            GeminiSummaryCard(viewModel)
            if (showPinChangeScreen) {
                PinChangeScreen(
                    onNavigateBack = { showPinChangeScreen = false }
                )
            } else if (showAuditScreen) {
                SystemAuditScreen(
                    viewModel = viewModel,
                    onBack = { showAuditScreen = false }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        NavigationTab.DASHBOARD -> DashboardScreen(
                            capabilities = capabilities,
                            fusionState = fusionState,
                            riskAnalysis = riskAnalysis,
                            liveReadings = liveReadings,
                            sensorDiagnostics = sensorDiagnostics,
                            isDeveloperMode = developerMode && isDeveloperAuthenticated,
                            onRefreshAi = { viewModel.refreshAiAnalysis() },
                            privacyScannerState = privacyScannerState,
                            onTogglePrivacyScanner = { enabled -> viewModel.togglePrivacyScanner(enabled) },
                            viewModel = viewModel
                        )
                        NavigationTab.SENSOR_CENTER -> SensorCenterContainerScreen(
                            capabilities = capabilities,
                            liveReadings = liveReadings,
                            watchdogStates = watchdogStates,
                            fusionState = fusionState,
                            riskAnalysis = riskAnalysis,
                            viewModel = viewModel
                        )
                        NavigationTab.LIVE_GRAPH -> {
                            val liveGraphState by viewModel.liveGraphState.collectAsStateWithLifecycle()
                            LiveGraphScreen(
                                state = liveGraphState,
                                onSelectSensor = { viewModel.selectLiveGraphSensor(it) },
                                onTogglePause = { viewModel.setLiveGraphPaused(it) },
                                onStartSession = { viewModel.startLiveGraphSession() },
                                onStopSession = { viewModel.stopLiveGraphSession() }
                            )
                        }
                        NavigationTab.HISTORY_LOGS -> HistoryLogsContainerScreen(
                            eventLogs = eventLogs,
                            viewModel = viewModel
                        )
                        NavigationTab.SERVICE_MANAGER -> ServiceManagerContainerScreen(
                            viewModel = viewModel
                        )
                        NavigationTab.SETTINGS -> SettingsContainerScreen(
                            viewModel = viewModel,
                            monitorThermal = monitorThermal,
                            monitorWeather = monitorWeather,
                            notifyWeather = notifyWeather,
                            announceWeather = announceWeather,
                            monitorLight = monitorLight,
                            notifyLight = notifyLight,
                            announceLight = announceLight,
                            monitorBluetooth = monitorBluetooth,
                            notifyBluetooth = notifyBluetooth,
                            announceBluetooth = announceBluetooth,
                            monitorLocation = monitorLocation,
                            notifyLocation = notifyLocation,
                            announceLocation = announceLocation,
                            monitorMagnetic = monitorMagnetic,
                            notifyMagnetic = notifyMagnetic,
                            announceMagnetic = announceMagnetic,
                            monitorProximity = monitorProximity,
                            notifyProximity = notifyProximity,
                            announceProximity = announceProximity,
                            refreshIntervalMs = refreshIntervalMs,
                            thermalThresholdC = thermalThresholdC,
                            encryptionEnabled = encryptionEnabled,
                            travelMode = travelMode,
                            isDeveloperMode = developerMode,
                            isDeveloperAuthenticated = isDeveloperAuthenticated,
                            lockoutUntil = lockoutUntil,
                            developerPinHash = developerPinHash,
                            developerPinStrength = developerPinStrength,
                            developerPinChangedDate = developerPinChangedDate,
                            developerPinFailedAttempts = developerPinFailedAttempts,
                            developerPinRecoveryKey = developerPinRecoveryKey,
                            onToggleThermal = { viewModel.setMonitorThermal(it) },
                            onToggleWeather = { viewModel.setMonitorWeather(it) },
                            onToggleNotifyWeather = { viewModel.setNotifyWeather(it) },
                            onToggleAnnounceWeather = { viewModel.setAnnounceWeather(it) },
                            onToggleLight = { viewModel.setMonitorLight(it) },
                            onToggleNotifyLight = { viewModel.setNotifyLight(it) },
                            onToggleAnnounceLight = { viewModel.setAnnounceLight(it) },
                            onToggleBluetooth = { viewModel.setMonitorBluetooth(it) },
                            onToggleNotifyBluetooth = { viewModel.setNotifyBluetooth(it) },
                            onToggleAnnounceBluetooth = { viewModel.setAnnounceBluetooth(it) },
                            onToggleLocation = { viewModel.setMonitorLocation(it) },
                            onToggleNotifyLocation = { viewModel.setNotifyLocation(it) },
                            onToggleAnnounceLocation = { viewModel.setAnnounceLocation(it) },
                            onToggleMagnetic = { viewModel.setMonitorMagnetic(it) },
                            onToggleNotifyMagnetic = { viewModel.setNotifyMagnetic(it) },
                            onToggleAnnounceMagnetic = { viewModel.setAnnounceMagnetic(it) },
                            onToggleProximity = { viewModel.setMonitorProximity(it) },
                            onToggleNotifyProximity = { viewModel.setNotifyProximity(it) },
                            onToggleAnnounceProximity = { viewModel.setAnnounceProximity(it) },
                            onChangeRefreshInterval = { viewModel.setRefreshIntervalMs(it) },
                            onChangeThermalThreshold = { viewModel.setThermalThresholdC(it) },
                            onToggleEncryption = { viewModel.setEncryptionEnabled(it) },
                            onToggleDeveloperMode = { viewModel.setDeveloperMode(it) },
                            onAuthenticateDeveloper = { viewModel.authenticateDeveloper(it) },
                            onChangeDeveloperPin = { current, new -> viewModel.changeDeveloperPin(current, new) },
                            onResetDeveloperPin = { viewModel.resetDeveloperPinWithRecoveryKey(it) },
                            onLockDeveloperMode = { viewModel.lockDeveloperMode() },
                            onNavigateToPinChange = { showPinChangeScreen = true },
                            onExportLogs = { callback -> viewModel.exportLogsToJson(callback) },
                            onOpenAuditScreen = { showAuditScreen = true },
                            onTravelModeChange = { viewModel.setTravelMode(it) },
                            onNavigateToSection = { section ->
                                when (section) {
                                    "SENSOR_CENTER" -> selectedTab = NavigationTab.SENSOR_CENTER
                                    "SERVICE_MANAGER" -> selectedTab = NavigationTab.SERVICE_MANAGER
                                    "HISTORY_LOGS" -> selectedTab = NavigationTab.HISTORY_LOGS
                                    "SETTINGS" -> selectedTab = NavigationTab.SETTINGS
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
