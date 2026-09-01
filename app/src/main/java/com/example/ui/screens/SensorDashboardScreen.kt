package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RawSensorReading
import com.example.ui.MainViewModel
import com.example.ui.components.SensorWaveformChart
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun SensorDashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val liveReadings by viewModel.liveReadings.collectAsStateWithLifecycle()
    val fusionState by viewModel.fusionState.collectAsStateWithLifecycle()

    // Extract Accel & Gyro
    val accelReading = liveReadings["sensor_1"] ?: liveReadings.values.find { 
        it.sensorId == "sensor_1" || it.sensorId.startsWith("sensor_1_") 
    }
    val gyroReading = liveReadings["sensor_4"] ?: liveReadings.values.find { 
        it.sensorId == "sensor_4" || it.sensorId.startsWith("sensor_4_") 
    }

    val ax = accelReading?.values?.getOrNull(0) ?: 0f
    val ay = accelReading?.values?.getOrNull(1) ?: 0f
    val az = accelReading?.values?.getOrNull(2) ?: 9.81f
    val accelNorm = sqrt(ax * ax + ay * ay + az * az)
    val gForce = accelNorm / 9.81f

    val gx = gyroReading?.values?.getOrNull(0) ?: 0f
    val gy = gyroReading?.values?.getOrNull(1) ?: 0f
    val gz = gyroReading?.values?.getOrNull(2) ?: 0f
    val gyroNorm = sqrt(gx * gx + gy * gy + gz * gz)

    // Security Tamper Shield States
    var isShieldArmed by rememberSaveable { mutableStateOf(false) }
    var sensitivityThreshold by remember { mutableStateOf(12.5f) } // m/s^2 motion delta above standard gravity
    var isAlarmTriggered by remember { mutableStateOf(false) }
    val recentLogs = remember { mutableStateListOf<String>() }

    // Floating/Rolling Max G-Force
    var peakGForce by remember { mutableStateOf(1.0f) }
    LaunchedEffect(gForce) {
        if (gForce > peakGForce) {
            peakGForce = gForce
        }
    }

    // Helper to log dynamic events
    val addLog = { event: String ->
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        recentLogs.add(0, "[$timestamp] $event")
        if (recentLogs.size > 8) {
            recentLogs.removeAt(recentLogs.size - 1)
        }
    }

    // Watch for Tamper Anomalies
    LaunchedEffect(ax, ay, az, gx, gy, gz, isShieldArmed) {
        if (isShieldArmed) {
            val accelDelta = kotlin.math.abs(accelNorm - 9.81f)
            val isViolated = accelDelta > sensitivityThreshold || gyroNorm > 2.0f

            if (isViolated && !isAlarmTriggered) {
                isAlarmTriggered = true
                val reason = if (accelDelta > sensitivityThreshold) {
                    "Sudden acceleration shift detected (Delta: %.2f m/s²)".format(accelDelta)
                } else {
                    "Suspicious rotation velocity detected (%.2f rad/s)".format(gyroNorm)
                }
                addLog("⚠️ SECURITY VIOLATION: $reason")
                viewModel.speakText("Warning. Sensor tampering detected. Physical security threshold exceeded.")
            }
        }
    }

    // Auto-populate logs based on state shifts
    LaunchedEffect(accelNorm) {
        val accelDelta = kotlin.math.abs(accelNorm - 9.81f)
        if (accelDelta < 0.3f && gyroNorm < 0.05f && recentLogs.firstOrNull()?.contains("Device Stable") == false) {
            addLog("Device Stable (At rest on flat surface)")
        } else if (accelDelta > 5f && accelDelta <= sensitivityThreshold) {
            addLog("Normal handling movement detected")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("sensor_dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Alarm Banner if Intruders/Tampering triggers it
        if (isAlarmTriggered) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BentoRed.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, BentoRed, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(BentoRed)
                                    .padding(8.dp)
                             ) {
                                Icon(Icons.Default.Warning, contentDescription = "Alarm", tint = Color.White)
                             }
                            Column {
                                Text(
                                    text = "SHIELD ALARM ACTIVE",
                                    color = BentoRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Physical displacement detected!",
                                    color = BentoTextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Button(
                            onClick = {
                                isAlarmTriggered = false
                                addLog("Tamper shield alarm manually reset.")
                                viewModel.speakText("Tamper shield reset. Secure monitoring resume.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Main Security Header block
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TAMPER SHIELD",
                                color = if (isShieldArmed) BentoGreenPrimary else BentoTextSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isShieldArmed) BentoGreenPrimary.copy(alpha = 0.15f) else BentoHeroCardBg)
                                    .border(1.dp, if (isShieldArmed) BentoGreenPrimary else BentoBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isShieldArmed) "ARMED" else "OFFLINE",
                                    color = if (isShieldArmed) BentoGreenPrimary else BentoTextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                isShieldArmed = !isShieldArmed
                                if (isShieldArmed) {
                                    isAlarmTriggered = false
                                    addLog("Shield Armed. Absolute security mode engaged.")
                                    viewModel.speakText("Tamper shield armed. Active sensor observation initialized.")
                                } else {
                                    addLog("Shield Disarmed. Background monitoring active.")
                                    viewModel.speakText("Tamper shield disarmed.")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isShieldArmed) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                contentDescription = "Arm Shield",
                                tint = if (isShieldArmed) BentoGreenPrimary else BentoTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "Real-time 3D displacement matrix fused with gyroscopic correlation models to identify suspicious mechanical physical tampering.",
                        color = BentoTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Divider(color = BentoBorder)

                    // Compact Telemetry Info Rows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ACCELEROMETER", fontSize = 10.sp, color = BentoTextMuted)
                            Text("%.2f m/s²".format(accelNorm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                        }
                        Column {
                            Text("ROTATION SPEED", fontSize = 10.sp, color = BentoTextMuted)
                            Text("%.2f rad/s".format(gyroNorm), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                        }
                        Column {
                            Text("PEAK FORCE", fontSize = 10.sp, color = BentoTextMuted)
                            Text("%.2f G".format(peakGForce), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BentoGreenPrimary)
                        }
                    }
                }
            }
        }

        // Live Interactive Graphics Canvas
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Accelerometer Bubble Level Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ORIENTATION BUBBLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary
                        )

                        // Bubble Level Canvas
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val radius = size.width / 2f
                                val center = Offset(radius, radius)

                                // Target Bullseye circles
                                drawCircle(
                                    color = BentoBorder,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                                drawCircle(
                                    color = BentoBorder,
                                    radius = radius * 0.5f,
                                    center = center,
                                    style = Stroke(width = 1f)
                                )
                                drawCircle(
                                    color = BentoBorder,
                                    radius = radius * 0.15f,
                                    center = center,
                                    style = Stroke(width = 1f)
                                )

                                // Axes Crosshairs
                                drawLine(BentoBorder, Offset(0f, radius), Offset(size.width, radius), 1f)
                                drawLine(BentoBorder, Offset(radius, 0f), Offset(radius, size.height), 1f)

                                // Bubble offset mapped to ax and ay
                                // Gravity exerts vector forces: raw flat ax ≈ 0, ay ≈ 0, az ≈ 9.8
                                val maxRange = 9.81f
                                val mappedX = (-ax / maxRange) * (radius - 12.dp.toPx())
                                val mappedY = (ay / maxRange) * (radius - 12.dp.toPx())

                                val clampedOffset = Offset(
                                    (center.x + mappedX).coerceIn(12.dp.toPx(), size.width - 12.dp.toPx()),
                                    (center.y + mappedY).coerceIn(12.dp.toPx(), size.height - 12.dp.toPx())
                                )

                                // Render Bubble
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(BentoGreenVibrant, BentoGreenPrimary),
                                        center = clampedOffset,
                                        radius = 10.dp.toPx()
                                    ),
                                    radius = 10.dp.toPx(),
                                    center = clampedOffset
                                )
                            }
                        }

                        Text(
                            text = "Tilt: X=%.1f° Y=%.1f°".format(ax * 10f, ay * 10f),
                            fontSize = 10.sp,
                            color = BentoTextMuted
                        )
                    }
                }

                // Gyroscope Direction Swirl Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ROTATIONAL MOMENTUM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary
                        )

                        // Gyroscopic Swirl Dial Canvas
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            var currentAngle by remember { mutableStateOf(0f) }
                            LaunchedEffect(gyroNorm) {
                                if (gyroNorm > 0.01f) {
                                    currentAngle = (currentAngle + gyroNorm * 12f) % 360f
                                }
                            }

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val radius = size.width / 2f
                                val center = Offset(radius, radius)

                                // Outer boundary
                                drawCircle(
                                    color = BentoBorder,
                                    radius = radius,
                                    center = center,
                                    style = Stroke(width = 1.5f)
                                )

                                rotate(currentAngle, pivot = center) {
                                    // Rotational dial marks
                                    val markCount = 12
                                    for (i in 0 until markCount) {
                                        val angleRad = Math.toRadians((360f / markCount) * i.toDouble())
                                        val startX = (center.x + (radius - 12.dp.toPx()) * cos(angleRad)).toFloat()
                                        val startY = (center.y + (radius - 12.dp.toPx()) * sin(angleRad)).toFloat()
                                        val endX = (center.x + radius * cos(angleRad)).toFloat()
                                        val endY = (center.y + radius * sin(angleRad)).toFloat()

                                        drawLine(
                                            color = if (i % 3 == 0) BentoGreenPrimary else BentoTextMuted,
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                            strokeWidth = if (i % 3 == 0) 3f else 1.5f
                                        )
                                    }

                                    // Render a beautiful directional dynamic vector needle
                                    drawLine(
                                        color = BentoGreenPrimary,
                                        start = center,
                                        end = Offset(center.x, center.y - radius + 14.dp.toPx()),
                                        strokeWidth = 3f
                                    )
                                }

                                // Interactive energy ring expanding based on gyro magnitude
                                val energyRadius = (gyroNorm * 22.dp.toPx()).coerceAtMost(radius - 2.dp.toPx())
                                if (energyRadius > 2.dp.toPx()) {
                                    drawCircle(
                                        color = BentoGreenVibrant.copy(alpha = 0.35f),
                                        radius = energyRadius,
                                        center = center,
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Velocity: %.2f rad/s".format(gyroNorm),
                            fontSize = 10.sp,
                            color = BentoTextMuted
                        )
                    }
                }
            }
        }

        // Adjustable Sensitivity Slider (Interactive Settings)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SECURITY SHIELD CONFIG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BentoHeroCardBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "%.1f m/s²".format(sensitivityThreshold),
                                color = BentoGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Define the physical deviation required to trigger alarms when armed (lower values represent higher pickpocket sensitivity).",
                        fontSize = 11.sp,
                        color = BentoTextMuted
                    )

                    Slider(
                        value = sensitivityThreshold,
                        onValueChange = { sensitivityThreshold = it },
                        valueRange = 2f..25f,
                        colors = SliderDefaults.colors(
                            thumbColor = BentoGreenPrimary,
                            activeTrackColor = BentoGreenPrimary,
                            inactiveTrackColor = BentoBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Live Real-Time Waveform plots
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACCELERATION WAVEFORM (X, Y, Z)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextSecondary
                )
                val accelVals = accelReading?.values ?: floatArrayOf(ax, ay, az)
                SensorWaveformChart(
                    values = accelVals,
                    unit = "m/s²",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ANGULAR ROTATION WAVEFORM (X, Y, Z)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextSecondary
                )
                val gyroVals = gyroReading?.values ?: floatArrayOf(gx, gy, gz)
                SensorWaveformChart(
                    values = gyroVals,
                    unit = "rad/s",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Recent Physical Events Logs
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PHYSICAL SECURITY SHIELD LOG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary
                        )
                        Text(
                            text = "Session Active",
                            fontSize = 10.sp,
                            color = BentoTextMuted
                        )
                    }

                    if (recentLogs.isEmpty()) {
                        Text(
                            text = "Awaiting physical events telemetry...",
                            fontSize = 12.sp,
                            color = BentoTextMuted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        recentLogs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (log.contains("SECURITY VIOLATION")) BentoRed else BentoGreenPrimary)
                                )
                                Text(
                                    text = log,
                                    fontSize = 11.sp,
                                    color = if (log.contains("SECURITY VIOLATION")) BentoRed else BentoTextPrimary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
