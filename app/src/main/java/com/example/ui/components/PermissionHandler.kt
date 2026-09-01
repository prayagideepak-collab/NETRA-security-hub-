package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.NetraForegroundService
import com.example.ui.theme.*

@Composable
fun PermissionHandler(
    modifier: Modifier = Modifier,
    onPermissionsStatusChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    
    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    var showDialog by remember {
        mutableStateOf(
            requiredPermissions.any {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var locationGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: locationGranted
        cameraGranted = results[Manifest.permission.CAMERA] ?: cameraGranted
        audioGranted = results[Manifest.permission.RECORD_AUDIO] ?: audioGranted

        val allGranted = locationGranted && cameraGranted
        onPermissionsStatusChanged(allGranted)
        showDialog = !allGranted
    }

    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            val serviceIntent = Intent(context, NetraForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = { /* Force response or handle gracefully */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BentoBackground),
                modifier = modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // System Security Shield Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BentoHeroCardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Core Permission Icon",
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "System Access Required",
                        color = BentoTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Netra Sensor Hub processes real-time ambient environment logs. Please authorize the following permissions:",
                        color = BentoTextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Permission Indicators
                    PermissionItem(
                        icon = Icons.Default.LocationOn,
                        title = "Location Access",
                        description = "Enables Fused Location Client geographic telemetry.",
                        isGranted = locationGranted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera Access",
                        description = "Enables CameraX live visual telemetry.",
                        isGranted = cameraGranted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        icon = Icons.Default.Mic,
                        title = "Audio Recording",
                        description = "Enables high-frequency noise decibel analysis.",
                        isGranted = audioGranted
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons with touch targets >= 48dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showDialog = false
                                onPermissionsStatusChanged(false)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BentoTextSecondary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Skip",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                launcher.launch(requiredPermissions)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BentoGreenPrimary,
                                contentColor = BentoBackground
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Grant Access",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isGranted) BentoHeroCardBg else BentoBorder),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$title Icon",
                tint = if (isGranted) BentoGreenPrimary else BentoTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = BentoTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = BentoTextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Granted status indicator
        Text(
            text = if (isGranted) "Granted" else "Required",
            color = if (isGranted) BentoGreenPrimary else BentoAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    color = if (isGranted) BentoHeroCardBg else BentoBorder,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
