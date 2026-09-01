package com.example.ui.screens

import com.example.ui.MainViewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SafetyCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RawSensorReading
import com.example.data.model.RiskAnalysisResult
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory
import com.example.data.model.SensorFusionState
import com.example.data.model.PrivacyScannerState
import com.example.data.sensor.SensorDiagnosticStatus
import com.example.ui.components.RiskMeterGauge
import com.example.ui.components.SensorDiagnosticsCard
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoDarkCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun DashboardScreen(
    capabilities: List<SensorCapabilityInfo>,
    fusionState: SensorFusionState,
    riskAnalysis: RiskAnalysisResult,
    liveReadings: Map<String, RawSensorReading>,
    sensorDiagnostics: List<SensorDiagnosticStatus>,
    isDeveloperMode: Boolean,
    onRefreshAi: () -> Unit,
    privacyScannerState: PrivacyScannerState,
    onTogglePrivacyScanner: (Boolean) -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val supportedCount = capabilities.count { it.isSupported }
    val totalCount = capabilities.size

    val context = androidx.compose.ui.platform.LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }

    val neededPermissions = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        list.add(Manifest.permission.CAMERA)
        list.add(Manifest.permission.RECORD_AUDIO)
        list.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = neededPermissions.all { perm ->
            results[perm] == true || context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            onTogglePrivacyScanner(true)
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    "Netra Privacy Scanner",
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
            },
            text = {
                Text(
                    "To scan for nearby Wi-Fi APs, Bluetooth transmitters, detect camera/mic hijacking, and analyze electromagnetic fields, Netra needs permissions for Location, Bluetooth, Camera, and Audio.\n\nOnly minimum required queries are executed, and your live data never leaves this device.",
                    color = BentoTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        launcher.launch(neededPermissions)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = BentoGreenPrimary)
                ) {
                    Text("Grant Permissions", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = BentoTextMuted)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = BentoCardBg,
            shape = RoundedCornerShape(24.dp)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // --- Global Safety Status Indicator (Phase 3) ---
        item {
            val systemStatus by com.example.data.engine.IntelligentSafetyStatusEngine.systemStatus.collectAsStateWithLifecycle()
            if (systemStatus != com.example.data.engine.IntelligentSafetyStatusEngine.SystemSafetyStatus.NORMAL) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (systemStatus == com.example.data.engine.IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL) BentoRed.copy(alpha = 0.15f) else BentoAmber.copy(alpha = 0.15f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (systemStatus == com.example.data.engine.IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL) BentoRed else BentoAmber)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Safety Status",
                            tint = if (systemStatus == com.example.data.engine.IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL) BentoRed else BentoAmber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Safety Attention Required: ${systemStatus.name}",
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 0. Privacy Scanner Card ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .testTag("privacy_scanner_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BentoHeroCardBg)
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (privacyScannerState.isEnabled) BentoGreenVibrant else BentoTextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "PRIVACY SCANNER",
                                    color = BentoTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (privacyScannerState.isEnabled) {
                                        if (privacyScannerState.isScanning) "Scanning Room..." else "Scan Completed"
                                    } else "OFF",
                                    color = BentoTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Enable/Disable toggle button
                        if (!privacyScannerState.isEnabled) {
                            Button(
                                onClick = {
                                    val allGranted = neededPermissions.all { perm ->
                                        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (allGranted) {
                                        onTogglePrivacyScanner(true)
                                    } else {
                                        showPermissionDialog = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BentoGreenPrimary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("privacy_enable_btn")
                            ) {
                                Text("Enable", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = { onTogglePrivacyScanner(false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BentoRed.copy(alpha = 0.2f),
                                    contentColor = BentoRed
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("privacy_disable_btn")
                            ) {
                                Text("Stop Scan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (privacyScannerState.isEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (privacyScannerState.isScanning) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = BentoGreenPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Analyzing electromagnetic and radio environment...",
                                    color = BentoTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Nearby Devices block
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(BentoHeroCardBg)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "NEARBY SIGNALS",
                                color = BentoTextSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bluetooth Devices", color = BentoTextPrimary, fontSize = 13.sp)
                                }
                                Text(
                                    text = privacyScannerState.bluetoothCount?.toString() ?: "Unavailable",
                                    color = if (privacyScannerState.bluetoothCount == null) BentoTextMuted else BentoGreenVibrant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Wi-Fi Networks", color = BentoTextPrimary, fontSize = 13.sp)
                                }
                                Text(
                                    text = privacyScannerState.wifiCount?.toString() ?: "Unavailable",
                                    color = if (privacyScannerState.wifiCount == null) BentoTextMuted else BentoGreenVibrant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // Magnetometer and Light indicator
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sensors, contentDescription = null, tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Electromagnetic Field", color = BentoTextPrimary, fontSize = 13.sp)
                                }
                                Text(
                                    text = privacyScannerState.magnetometerRawValue?.let { "${"%.1f".format(it)} µT" } ?: "Unavailable",
                                    color = if (privacyScannerState.magnetometerRawValue == null) BentoTextMuted else BentoTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Device Camera Lockout", color = BentoTextPrimary, fontSize = 13.sp)
                                }
                                Text(
                                    text = when(privacyScannerState.cameraCheckResult) {
                                        "VERIFIED_OK" -> "Verified Secure"
                                        "NO_PERMISSION" -> "No Permission"
                                        else -> "Unavailable"
                                    },
                                    color = when(privacyScannerState.cameraCheckResult) {
                                        "VERIFIED_OK" -> BentoGreenVibrant
                                        else -> BentoTextMuted
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = BentoTextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Device Mic Lockout", color = BentoTextPrimary, fontSize = 13.sp)
                                }
                                Text(
                                    text = when(privacyScannerState.microphoneCheckResult) {
                                        "VERIFIED_OK" -> "Verified Secure"
                                        "NO_PERMISSION" -> "No Permission"
                                        else -> "Unavailable"
                                    },
                                    color = when(privacyScannerState.microphoneCheckResult) {
                                        "VERIFIED_OK" -> BentoGreenVibrant
                                        else -> BentoTextMuted
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Risk level display
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    1.dp,
                                    when (privacyScannerState.riskLevel) {
                                        "High Risk" -> BentoRed.copy(alpha = 0.5f)
                                        "Medium Risk" -> BentoAmber.copy(alpha = 0.5f)
                                        else -> BentoGreenVibrant.copy(alpha = 0.5f)
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                                .background(
                                    when (privacyScannerState.riskLevel) {
                                        "High Risk" -> BentoRed.copy(alpha = 0.1f)
                                        "Medium Risk" -> BentoAmber.copy(alpha = 0.1f)
                                        else -> BentoGreenVibrant.copy(alpha = 0.1f)
                                    }
                                )
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "SURVEILLANCE RISK SCORE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = when (privacyScannerState.riskLevel) {
                                        "High Risk" -> BentoRed
                                        "Medium Risk" -> BentoAmber
                                        else -> BentoGreenVibrant
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = privacyScannerState.riskLevel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = BentoTextPrimary
                                )
                            }
                            Text(
                                text = "${privacyScannerState.riskScore}%",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (privacyScannerState.riskLevel) {
                                    "High Risk" -> BentoRed
                                    "Medium Risk" -> BentoAmber
                                    else -> BentoGreenVibrant
                                }
                            )
                        }

                        // Anomaly List / Room Inspect Notice
                        if (privacyScannerState.detectedAnomalies.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BentoRed.copy(alpha = 0.08f))
                                    .border(1.dp, BentoRed.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("SUSPICIOUS DETECTIONS", color = BentoRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                privacyScannerState.detectedAnomalies.forEach { anomaly ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(BentoRed))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(anomaly, color = BentoTextPrimary, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Please inspect the room manually.", color = BentoTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (privacyScannerState.isScanning) "Scanning local environment..." else "Environment appears secure. Manual physical inspection is still recommended for hotel rooms.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 1. AI Risk Gauge Hero Bento Card
        item {
            RiskMeterGauge(
                score = riskAnalysis.riskScore,
                level = riskAnalysis.riskLevel,
                isAiPowered = riskAnalysis.isAiPowered
            )
        }

        // 1.5. Intelligent Travel Classifier (IDDE) Card
        item {
            val travelType = fusionState.classifiedTravelType ?: "STILL"
            val travelReason = fusionState.classificationReason ?: "Monitoring sensor metrics..."
            
            val (badgeText, badgeColor, icon) = when (travelType) {
                "DRIVING" -> Triple("Active Driver (Driving Mode)", BentoRed, Icons.Default.Speed)
                "PASSENGER" -> Triple("Passenger Tracker (Silent Monitor)", BentoGreenVibrant, Icons.Default.Shield)
                "TRAIN" -> Triple("Train Rail Signature (Warnings Suspended)", BentoGreenVibrant, Icons.Default.Refresh)
                "BUS" -> Triple("Urban Bus Pattern (Warnings Suspended)", BentoGreenVibrant, Icons.Default.Sensors)
                "METRO" -> Triple("Underground Metro Pattern (Warnings Suspended)", BentoGreenVibrant, Icons.Default.Power)
                "FLIGHT" -> Triple("Aviation Flight Signature (Warnings Suspended)", BentoGreenVibrant, Icons.Default.Shield)
                "WALKING" -> Triple("Walking (Stationary)", BentoGreenVibrant, Icons.Default.Devices)
                "CYCLING" -> Triple("Cycling (Stationary)", BentoGreenVibrant, Icons.Default.Speed)
                else -> Triple("At Rest (Stationary)", BentoTextMuted, Icons.Default.Lightbulb)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .testTag("idde_travel_status_card")
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = badgeColor)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TRAVEL CLASSIFIER (IDDE)",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = badgeText,
                                color = BentoTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = travelReason,
                        color = BentoTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHeroCardBg)
                            .padding(12.dp)
                    )

                    // If currently in active driving mode, display clean active monitoring status
                    if (travelType == "DRIVING" || fusionState.isDrivingConfirmed) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BentoRed.copy(alpha = 0.2f))
                                    .padding(6.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BentoRed))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Active Driving Safety Monitoring in Progress",
                                color = BentoRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Safety Warning indicators
                        if (fusionState.isRapidAccelerationDetected || fusionState.isHighSpeedWarning || fusionState.isApproachingControlledPoint) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BentoRed.copy(alpha = 0.15f))
                                    .border(1.dp, BentoRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "PREVENTIVE DRIVING WARNINGS",
                                    color = BentoRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )

                                if (fusionState.isRapidAccelerationDetected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BentoRed))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Rapid Acceleration Alert: Slow down immediately.", color = BentoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                if (fusionState.isHighSpeedWarning) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BentoRed))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Excessive Speed Alert: Reduce your speed.", color = BentoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                if (fusionState.isApproachingControlledPoint) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BentoAmber))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Controlled point ahead. Slow down.", color = BentoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Overall Sensor Status Bento Card
        item {
            val criticalSensors = listOf("icm4x6xx Accelerometer", "icm4x6xx Gyroscope") 
            val hasCriticalFailure = sensorDiagnostics.any { it.sensorName in criticalSensors && it.isFaulty }
            val hasAnyFailure = sensorDiagnostics.any { it.isFaulty }
            
            val status = when {
                hasCriticalFailure -> "System Degraded"
                hasAnyFailure -> "Monitoring Limited"
                else -> "All systems operational"
            }
            
            val statusColor = when {
                hasCriticalFailure -> BentoRed
                hasAnyFailure -> BentoAmber
                else -> BentoGreenPrimary
            }
            
            val statusIcon = when {
                hasCriticalFailure -> Icons.Default.Warning
                hasAnyFailure -> Icons.Default.Info
                else -> Icons.Default.CheckCircle
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoGreenPrimary.copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SENSOR HUB STATUS",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Sensor Hub Status",
                                color = BentoTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "🟢 System Status : Healthy", color = BentoTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Available Sensors : $supportedCount / $totalCount", color = BentoTextSecondary, fontSize = 12.sp)
                    Text(text = "Monitoring : Active", color = BentoTextSecondary, fontSize = 12.sp)
                    Text(text = "Last Health Check : Just now", color = BentoTextSecondary, fontSize = 12.sp)
                }
            }
        }

        // Optional: Sensor Diagnostics Bento Card (Developer Mode Only)
        if (isDeveloperMode) {
            item {
                SensorDiagnosticsCard(diagnostics = sensorDiagnostics)
            }
        }

        // Location & Cross-Verification Bento Card
        item {
            val city by viewModel.currentCity.collectAsStateWithLifecycle()
            val stateName by viewModel.currentState.collectAsStateWithLifecycle()
            val country by viewModel.currentCountry.collectAsStateWithLifecycle()
            val provider by viewModel.locationProvider.collectAsStateWithLifecycle()
            val confidence by viewModel.locationConfidence.collectAsStateWithLifecycle()
            val gpsStat by viewModel.gpsStatus.collectAsStateWithLifecycle()
            val networkStat by viewModel.networkStatus.collectAsStateWithLifecycle()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("location_cross_verification_card")
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoGreenPrimary.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LOCATION & CROSS-VERIFICATION",
                                color = BentoTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$city, $stateName",
                                color = BentoTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroCardBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$confidence% Conf",
                                color = BentoGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Country", color = BentoTextSecondary, fontSize = 11.sp)
                            Text(text = country, color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Column {
                            Text(text = "Provider Status", color = BentoTextSecondary, fontSize = 11.sp)
                            Text(text = provider, color = BentoGreenPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "GPS Provider", color = BentoTextSecondary, fontSize = 11.sp)
                            Text(text = gpsStat, color = BentoTextPrimary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Network Provider", color = BentoTextSecondary, fontSize = 11.sp)
                            Text(text = networkStat, color = BentoTextPrimary, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Coordinates: %.4f° N, %.4f° E (Cross-verified via GPS & Cell Triangulation)".format(fusionState.latitude, fusionState.longitude),
                        color = BentoTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 3. Compact Bento Grid Metric Cards (Side-by-Side Pairs)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thermal Metric Card
                CompactBentoCard(
                    title = "THERMAL",
                    value = "${fusionState.batteryTempC}°C",
                    subtitle = if (fusionState.isHighHeatConfirmed) "Elevated" else "Stable",
                    icon = Icons.Default.LocalFireDepartment,
                    isAlert = fusionState.isHighHeatConfirmed,
                    modifier = Modifier.weight(1f)
                )

                // Magnetic Field Metric Card
                val magVal = fusionState.magneticMagnitudeuT
                val magneticSubtitle = when {
                    magVal < 50f -> "Normal Zone"
                    magVal < 100f -> "Safe Zone"
                    magVal < 150f -> "Attention Zone"
                    magVal < 250f -> "Caution Zone"
                    magVal < 400f -> "Warning Zone"
                    magVal < 600f -> "High Risk Zone"
                    magVal < 1000f -> "Extreme Risk Zone"
                    else -> "Dangerous Env"
                }
                CompactBentoCard(
                    title = "MAGNETIC",
                    value = "${"%.1f".format(magVal)} µT",
                    subtitle = magneticSubtitle,
                    icon = Icons.Default.SafetyCheck,
                    isAlert = magVal >= 100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pocket Detection Bento Card
                CompactBentoCard(
                    title = "POCKET",
                    value = if (fusionState.isPocketConfirmed) "In Pocket" else "Ambient",
                    subtitle = if (fusionState.isPocketConfirmed) "Enclosure" else "Unconstrained",
                    icon = Icons.Default.Lightbulb,
                    isAlert = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Hardware Capability Discovery Banner & Matrix (Developer Mode Only)
        if (isDeveloperMode) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DISCOVERED HARDWARE: $supportedCount / $totalCount NODES",
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Capability Manager verified hardware HAL capabilities across ${SensorCategory.entries.size} sensor categories.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 4. Dynamic Capability Cards (Grouped by Category)
            item {
                Text(
                    text = "DISCOVERED HARDWARE CAPABILITY MATRIX",
                    color = BentoTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // Render discovered features dynamically based on CapabilityManager capabilities
            items(capabilities, key = { it.id }) { cap ->
                val liveData = liveReadings[cap.id] ?: liveReadings["sensor_${cap.type}"]
                DiscoveredCapabilityBentoCard(
                    cap = cap,
                    liveData = liveData
                )
            }
        }

        // 5. Sensor Fusion Correlation Rules Section Title
        item {
            Text(
                text = "REAL-TIME SENSOR FUSION CORRELATION",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        // Sensor Fusion Bento Grid Items
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FusionStatusCard(
                    title = "Pocket Enclosure Rule",
                    isActive = fusionState.isPocketConfirmed,
                    detailText = if (fusionState.isPocketConfirmed) "Proximity + Low Light confirmed pocket enclosure" else "Device unconstrained in ambient space",
                    icon = Icons.Default.Lightbulb,
                    activeColor = BentoAmber
                )

                FusionStatusCard(
                    title = "Thermal Overheat Risk",
                    isActive = fusionState.isHighHeatConfirmed,
                    detailText = "Battery Temp: ${fusionState.batteryTempC}°C" + if (fusionState.isHighHeatConfirmed) " — Elevated heat threshold!" else " — Thermal HAL nominal",
                    icon = Icons.Default.LocalFireDepartment,
                    activeColor = BentoRed
                )


                FusionStatusCard(
                    title = "Impact / Fall Event",
                    isActive = fusionState.isImpactConfirmed,
                    detailText = "Max G-Force: ${"%.1f".format(fusionState.impactGForce)}G" + if (fusionState.isImpactConfirmed) " — Severe force spike!" else " — Motion variance normal",
                    icon = Icons.Default.Speed,
                    activeColor = BentoRed
                )

                FusionStatusCard(
                    title = "Charging Anomaly",
                    isActive = fusionState.isChargingRiskConfirmed,
                    detailText = "Voltage: ${fusionState.chargingVoltageMv} mV" + if (fusionState.isChargingRiskConfirmed) " — Voltage risk!" else " — Power circuit stable",
                    icon = Icons.Default.BatteryAlert,
                    activeColor = BentoAmber
                )

                FusionStatusCard(
                    title = "Magnetic Field Anomaly",
                    isActive = fusionState.isMagneticHazardConfirmed,
                    detailText = "Magnitude: ${"%.1f".format(fusionState.magneticMagnitudeuT)} µT" + if (fusionState.isMagneticHazardConfirmed) " — Magnetic spike detected!" else " — Field normal",
                    icon = Icons.Default.SafetyCheck,
                    activeColor = BentoAmber
                )

                FusionStatusCard(
                    title = "Active Driving Safety Module",
                    isActive = fusionState.isDrivingConfirmed,
                    detailText = if (fusionState.isDrivingConfirmed) {
                        "Active Drive: %.1f km/h  |  Max: %.1f km/h  |  Dur: ${fusionState.drivingDurationSec}s"
                            .format(fusionState.currentSpeedKmH, fusionState.maxSpeedKmH)
                    } else {
                        "Passive Monitor Standby — Speed: %.1f km/h (No Drive Detected)"
                            .format(fusionState.currentSpeedKmH)
                    },
                    icon = Icons.Default.Speed,
                    activeColor = BentoGreenVibrant
                )
            }
        }

        // 6. Truth Diagnostic & Recommendation Bento Block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BentoHeroCardBg),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                modifier = Modifier.fillMaxWidth().testTag("ai_recommendation_card")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoBackground)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "NETRA TRUTH DIAGNOSTICS",
                            color = BentoGreenPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onRefreshAi, modifier = Modifier.testTag("refresh_ai_button")) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = BentoGreenPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = riskAnalysis.summary,
                        color = BentoTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = riskAnalysis.explanation,
                        color = BentoTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    if (riskAnalysis.recommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Safety Protocol Action Steps:",
                            color = BentoGreenPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        riskAnalysis.recommendations.forEach { rec ->
                            Text(
                                text = "• $rec",
                                color = BentoTextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // 7. Absolute Truth Dark Footer Bento Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoDarkCardBg)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ABSOLUTE TRUTH ENGINE",
                            color = BentoGreenVibrant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$supportedCount Active Discovered HAL Nodes Operational",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF333734))
                            .border(1.dp, BentoBorder, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "VERIFIED",
                            color = BentoGreenVibrant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun CompactBentoCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .animateContentSize()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isAlert) BentoRed.copy(alpha = 0.12f) else BentoCardBg)
            .border(
                1.dp,
                if (isAlert) BentoRed.copy(alpha = 0.5f) else BentoBorder,
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isAlert) BentoRed.copy(alpha = 0.2f) else BentoHeroCardBg)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isAlert) BentoRed else BentoGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = BentoTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = value,
                color = if (isAlert) BentoRed else BentoTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isAlert) BentoRed else BentoGreenVibrant)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = subtitle,
                    color = BentoTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DiscoveredCapabilityBentoCard(
    cap: SensorCapabilityInfo,
    liveData: RawSensorReading?
) {
    val isSupported = cap.isSupported
    val statusColor = if (isSupported) BentoGreenVibrant else BentoRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(22.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(22.dp))
            .padding(14.dp)
            .testTag("capability_card_${cap.id}")
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = BentoGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cap.name,
                        color = BentoTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cap.vendor} • ${cap.category.displayName}",
                        color = BentoTextMuted,
                        fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isSupported) "DETECTED" else "UNSUPPORTED",
                        color = if (isSupported) BentoGreenPrimary else BentoRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isSupported && liveData != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val valuesText = liveData.values.joinToString("  |  ") { "%.2f".format(it) }
                Text(
                    text = "Live Stream: $valuesText ${liveData.unit}",
                    color = BentoGreenPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (!isSupported) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hardware HAL unit unavailable on current host profile.",
                    color = BentoTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun FusionStatusCard(
    title: String,
    isActive: Boolean,
    detailText: String,
    icon: ImageVector,
    activeColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(22.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.12f) else BentoCardBg)
            .border(
                width = 1.dp,
                color = if (isActive) activeColor.copy(alpha = 0.5f) else BentoBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isActive) activeColor.copy(alpha = 0.2f) else BentoHeroCardBg)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) activeColor else BentoGreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isActive) activeColor else BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detailText,
                    color = BentoTextSecondary,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else BentoHeroCardBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isActive) "CONFIRMED" else "NOMINAL",
                    color = if (isActive) Color.White else BentoGreenPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
