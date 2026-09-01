package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.*

data class SearchableFeature(
    val title: String,
    val description: String,
    val category: String,
    val onSelect: () -> Unit
)

@Composable
fun SettingsContainerScreen(
    viewModel: MainViewModel,
    monitorThermal: Boolean,
    monitorWeather: Boolean,
    notifyWeather: Boolean,
    announceWeather: Boolean,
    monitorLight: Boolean,
    notifyLight: Boolean,
    announceLight: Boolean,
    monitorBluetooth: Boolean,
    notifyBluetooth: Boolean,
    announceBluetooth: Boolean,
    monitorLocation: Boolean,
    notifyLocation: Boolean,
    announceLocation: Boolean,
    monitorMagnetic: Boolean,
    notifyMagnetic: Boolean,
    announceMagnetic: Boolean,
    monitorProximity: Boolean,
    notifyProximity: Boolean,
    announceProximity: Boolean,
    refreshIntervalMs: Int,
    thermalThresholdC: Int,
    encryptionEnabled: Boolean,
    travelMode: String,
    isDeveloperMode: Boolean,
    isDeveloperAuthenticated: Boolean,
    lockoutUntil: Long,
    developerPinHash: String?,
    developerPinStrength: String?,
    developerPinChangedDate: String?,
    developerPinFailedAttempts: Int,
    developerPinRecoveryKey: String?,
    onToggleThermal: (Boolean) -> Unit,
    onToggleWeather: (Boolean) -> Unit,
    onToggleNotifyWeather: (Boolean) -> Unit,
    onToggleAnnounceWeather: (Boolean) -> Unit,
    onToggleLight: (Boolean) -> Unit,
    onToggleNotifyLight: (Boolean) -> Unit,
    onToggleAnnounceLight: (Boolean) -> Unit,
    onToggleBluetooth: (Boolean) -> Unit,
    onToggleNotifyBluetooth: (Boolean) -> Unit,
    onToggleAnnounceBluetooth: (Boolean) -> Unit,
    onToggleLocation: (Boolean) -> Unit,
    onToggleNotifyLocation: (Boolean) -> Unit,
    onToggleAnnounceLocation: (Boolean) -> Unit,
    onToggleMagnetic: (Boolean) -> Unit,
    onToggleNotifyMagnetic: (Boolean) -> Unit,
    onToggleAnnounceMagnetic: (Boolean) -> Unit,
    onToggleProximity: (Boolean) -> Unit,
    onToggleNotifyProximity: (Boolean) -> Unit,
    onToggleAnnounceProximity: (Boolean) -> Unit,
    onChangeRefreshInterval: (Int) -> Unit,
    onChangeThermalThreshold: (Int) -> Unit,
    onToggleEncryption: (Boolean) -> Unit,
    onToggleDeveloperMode: (Boolean) -> Unit,
    onAuthenticateDeveloper: (String) -> Boolean,
    onChangeDeveloperPin: (String, String) -> Boolean,
    onResetDeveloperPin: (String) -> Boolean,
    onLockDeveloperMode: () -> Unit,
    onNavigateToPinChange: () -> Unit,
    onExportLogs: ((Boolean, String) -> Unit) -> Unit,
    onOpenAuditScreen: () -> Unit,
    onTravelModeChange: (String) -> Unit,
    onNavigateToSection: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val allSearchableFeatures = remember {
        listOf(
            SearchableFeature("Driving Monitoring", "Toggle driving detection & alerts", "Sensors") { onNavigateToSection("SENSOR_CENTER") },
            SearchableFeature("Magnetic Sensor", "Magnetic anomaly monitoring & calibration", "Sensors") { onNavigateToSection("SENSOR_CENTER") },
            SearchableFeature("Thermal Monitoring", "Overheat and battery thermal safety", "Sensors") { onNavigateToSection("SETTINGS") },
            SearchableFeature("Bluetooth Safety", "Bluetooth nearby device detection", "Sensors") { onNavigateToSection("SETTINGS") },
            SearchableFeature("AI Fusion & Analytics", "Sensor fusion & risk level estimation", "AI") { onNavigateToSection("SENSOR_CENTER") },
            SearchableFeature("Backup & Restore", "IBSDCE encrypted local and cloud backup", "Security") { onNavigateToSection("SETTINGS") },
            SearchableFeature("Privacy & Permissions", "ISPPE permission dependencies & data policies", "Security") { onNavigateToSection("SETTINGS") },
            SearchableFeature("Service Manager", "IBRS2 runtime service controller & health", "System") { onNavigateToSection("SERVICE_MANAGER") },
            SearchableFeature("Event History & Export", "View event logs and export reports", "Logs") { onNavigateToSection("HISTORY_LOGS") },
            SearchableFeature("Diagnostics & Self-Healing", "IDHMSE system health monitoring", "System") { onNavigateToSection("SERVICE_MANAGER") }
        )
    }

    val filteredResults = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else allSearchableFeatures.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BentoBackground)) {
        // Breadcrumb & Global Search Bar
        Surface(
            color = BentoCardBg,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings > Global Search & Configuration", style = MaterialTheme.typography.titleMedium, color = BentoGreenPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Smart Search (e.g. Magnetic, Driving, Backup)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BentoGreenPrimary,
                        unfocusedBorderColor = BentoBorder
                    )
                )
            }
        }

        if (filteredResults.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                items(filteredResults) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { item.onSelect() },
                        colors = CardDefaults.cardColors(containerColor = BentoHeroCardBg)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium, color = BentoTextPrimary)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = BentoTextSecondary)
                            Text("Category: ${item.category}", style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary)
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                SettingsScreen(
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
                    isDeveloperMode = isDeveloperMode,
                    isDeveloperAuthenticated = isDeveloperAuthenticated,
                    lockoutUntil = lockoutUntil,
                    developerPinHash = developerPinHash,
                    developerPinStrength = developerPinStrength,
                    developerPinChangedDate = developerPinChangedDate,
                    developerPinFailedAttempts = developerPinFailedAttempts,
                    developerPinRecoveryKey = developerPinRecoveryKey,
                    onToggleThermal = onToggleThermal,
                    onToggleWeather = onToggleWeather,
                    onToggleNotifyWeather = onToggleNotifyWeather,
                    onToggleAnnounceWeather = onToggleAnnounceWeather,
                    onToggleLight = onToggleLight,
                    onToggleNotifyLight = onToggleNotifyLight,
                    onToggleAnnounceLight = onToggleAnnounceLight,
                    onToggleBluetooth = onToggleBluetooth,
                    onToggleNotifyBluetooth = onToggleNotifyBluetooth,
                    onToggleAnnounceBluetooth = onToggleAnnounceBluetooth,
                    onToggleLocation = onToggleLocation,
                    onToggleNotifyLocation = onToggleNotifyLocation,
                    onToggleAnnounceLocation = onToggleAnnounceLocation,
                    onToggleMagnetic = onToggleMagnetic,
                    onToggleNotifyMagnetic = onToggleNotifyMagnetic,
                    onToggleAnnounceMagnetic = onToggleAnnounceMagnetic,
                    onToggleProximity = onToggleProximity,
                    onToggleNotifyProximity = onToggleNotifyProximity,
                    onToggleAnnounceProximity = onToggleAnnounceProximity,
                    onChangeRefreshInterval = onChangeRefreshInterval,
                    onChangeThermalThreshold = onChangeThermalThreshold,
                    onToggleEncryption = onToggleEncryption,
                    onToggleDeveloperMode = onToggleDeveloperMode,
                    onAuthenticateDeveloper = onAuthenticateDeveloper,
                    onChangeDeveloperPin = onChangeDeveloperPin,
                    onResetDeveloperPin = onResetDeveloperPin,
                    onLockDeveloperMode = onLockDeveloperMode,
                    onNavigateToPinChange = onNavigateToPinChange,
                    onExportLogs = onExportLogs,
                    onOpenAuditScreen = onOpenAuditScreen,
                    onTravelModeChange = onTravelModeChange
                )
            }
        }
    }
}
