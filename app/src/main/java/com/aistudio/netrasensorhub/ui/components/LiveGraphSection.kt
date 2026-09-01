package com.aistudio.netrasensorhub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.netrasensorhub.data.risk.SensorTelemetryState

enum class SensorTypeOption(val displayName: String, val unit: String, val isThreeAxis: Boolean) {
    ACCELEROMETER("Accelerometer", "m/s²", true),
    GYROSCOPE("Gyroscope", "rad/s", true),
    MAGNETIC("Magnetic Field", "μT", true),
    LIGHT("Ambient Light", "lux", false)
}

@Composable
fun LiveGraphSection(
    telemetryState: SensorTelemetryState,
    isLiveActive: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val cardBg = if (isDark) Color(0xFF0D1117) else Color(0xFFF6F8FA)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)
    val gridColor = if (isDark) Color(0xFF21262D) else Color(0xFFE1E4E8)
    val textColor = if (isDark) Color(0xFFC9D1D9) else Color(0xFF24292F)
    val mutedColor = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A)

    // Available sensor options dynamically filtered by hardware support
    val availableOptions = remember(
        telemetryState.isAccelSupported,
        telemetryState.isGyroSupported,
        telemetryState.isMagneticSupported,
        telemetryState.isLightSupported
    ) {
        listOfNotNull(
            if (telemetryState.isAccelSupported) SensorTypeOption.ACCELEROMETER else null,
            if (telemetryState.isGyroSupported) SensorTypeOption.GYROSCOPE else null,
            if (telemetryState.isMagneticSupported) SensorTypeOption.MAGNETIC else null,
            if (telemetryState.isLightSupported) SensorTypeOption.LIGHT else null
        )
    }

    var selectedOption by remember { mutableStateOf<SensorTypeOption?>(null) }

    // Ensure selectedOption is valid among available options
    LaunchedEffect(availableOptions) {
        if (selectedOption == null || !availableOptions.contains(selectedOption)) {
            selectedOption = availableOptions.firstOrNull()
        }
    }

    // Rolling buffers for X, Y, Z history (max 30 samples)
    val xHistory = remember { mutableStateListOf<Float>() }
    val yHistory = remember { mutableStateListOf<Float>() }
    val zHistory = remember { mutableStateListOf<Float>() }

    val currentValues: Triple<Float?, Float?, Float?> = when (selectedOption) {
        SensorTypeOption.ACCELEROMETER -> Triple(telemetryState.accelX, telemetryState.accelY, telemetryState.accelZ)
        SensorTypeOption.GYROSCOPE -> Triple(telemetryState.gyroX, telemetryState.gyroY, telemetryState.gyroZ)
        SensorTypeOption.MAGNETIC -> Triple(telemetryState.magneticX, telemetryState.magneticY, telemetryState.magneticZ)
        SensorTypeOption.LIGHT -> Triple(telemetryState.ambientLightLux, null, null)
        null -> Triple(null, null, null)
    }

    val isCurrentSensorDataAvailable = currentValues.first != null

    // Sample collection bound strictly to live active state and genuine data availability
    DisposableEffect(selectedOption, isLiveActive, currentValues.first, currentValues.second, currentValues.third) {
        if (isLiveActive && selectedOption != null && currentValues.first != null) {
            val vx = currentValues.first!!
            val vy = currentValues.second
            val vz = currentValues.third

            if (xHistory.size >= 30) {
                xHistory.removeAt(0)
                if (selectedOption!!.isThreeAxis && yHistory.isNotEmpty() && zHistory.isNotEmpty()) {
                    yHistory.removeAt(0)
                    zHistory.removeAt(0)
                }
            }
            xHistory.add(vx)
            if (selectedOption!!.isThreeAxis && vy != null && vz != null) {
                yHistory.add(vy)
                zHistory.add(vz)
            }
        }
        onDispose {
            if (!isLiveActive) {
                xHistory.clear()
                yHistory.clear()
                zHistory.clear()
            }
        }
    }

    // Calculate real stats strictly from genuine history
    val currentVal = currentValues.first
    val minVal = if (xHistory.isNotEmpty()) xHistory.minOrNull() else null
    val maxVal = if (xHistory.isNotEmpty()) xHistory.maxOrNull() else null
    val avgVal = if (xHistory.isNotEmpty()) xHistory.average().toFloat() else null

    // Accent color per sensor type
    val accentColor = when (selectedOption) {
        SensorTypeOption.ACCELEROMETER -> if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37) // Green
        SensorTypeOption.GYROSCOPE -> if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA)    // Blue/Cyan
        SensorTypeOption.MAGNETIC -> if (isDark) Color(0xFFBC8CFF) else Color(0xFF8250DF)    // Purple
        SensorTypeOption.LIGHT -> if (isDark) Color(0xFFD29922) else Color(0xFF9A6700)       // Yellow/Orange
        null -> mutedColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Section Header & Live Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Live Graph Section",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "LIVE GRAPH",
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                // Status Badge: REAL LIVE vs PAUSED vs DATA UNAVAILABLE
                val statusText = when {
                    !isLiveActive -> "PAUSED"
                    availableOptions.isEmpty() || selectedOption == null -> "DATA UNAVAILABLE"
                    !isCurrentSensorDataAvailable -> "AWAITING DATA"
                    else -> "LIVE"
                }
                val statusColor = when (statusText) {
                    "LIVE" -> Color(0xFF3FB950)
                    "PAUSED", "AWAITING DATA" -> Color(0xFFD29922)
                    else -> Color(0xFFDA3633)
                }
                val statusBg = when (statusText) {
                    "LIVE" -> if (isDark) Color(0xFF033A16) else Color(0xFFDAFBE1)
                    "PAUSED", "AWAITING DATA" -> if (isDark) Color(0xFF3B2300) else Color(0xFFFFF8C5)
                    else -> if (isDark) Color(0xFF3B1212) else Color(0xFFFFEBE9)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBg)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sensor Selector Tabs (Only show physically supported sensors)
            if (availableOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableOptions.forEach { option ->
                        val isSelected = option == selectedOption
                        Button(
                            onClick = {
                                selectedOption = option
                                xHistory.clear()
                                yHistory.clear()
                                zHistory.clear()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) accentColor else (if (isDark) Color(0xFF161B22) else Color(0xFFEAEEF2))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = option.displayName,
                                color = if (isSelected) Color.White else textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Statistics Row: Current, Min, Max, Average (Strictly Real calculations or "Data unavailable")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Current",
                    value = if (isLiveActive && selectedOption != null && currentVal != null) "%.2f %s".format(currentVal, selectedOption!!.unit) else "Data unavailable",
                    color = if (currentVal != null) accentColor else mutedColor,
                    isDark = isDark
                )
                StatItem(
                    label = "Min",
                    value = if (minVal != null) "%.2f".format(minVal) else "Data unavailable",
                    color = if (minVal != null) textColor else mutedColor,
                    isDark = isDark
                )
                StatItem(
                    label = "Max",
                    value = if (maxVal != null) "%.2f".format(maxVal) else "Data unavailable",
                    color = if (maxVal != null) textColor else mutedColor,
                    isDark = isDark
                )
                StatItem(
                    label = "Average",
                    value = if (avgVal != null) "%.2f".format(avgVal) else "Data unavailable",
                    color = if (avgVal != null) textColor else mutedColor,
                    isDark = isDark
                )
            }

            // Graph Visualization or Clean Explicit Fallback
            if (availableOptions.isEmpty() || selectedOption == null || !isLiveActive || !isCurrentSensorDataAvailable || xHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF161B22) else Color(0xFFEAEEF2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            !isLiveActive -> "Live Graph Paused (Foreground only)"
                            availableOptions.isEmpty() -> "Hardware sensors unavailable on this device"
                            !isCurrentSensorDataAvailable -> "Awaiting real-time sensor events from hardware..."
                            else -> "Collecting sensor telemetry..."
                        },
                        color = mutedColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Polished Rolling Canvas Graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF161B22) else Color(0xFFEAEEF2))
                        .padding(8.dp)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val width = size.width
                        val height = size.height

                        // Grid lines
                        val stepX = width / 6
                        for (i in 1..5) {
                            drawLine(
                                color = gridColor,
                                start = Offset(stepX * i, 0f),
                                end = Offset(stepX * i, height),
                                strokeWidth = 1f
                            )
                        }
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, height / 2),
                            end = Offset(width, height / 2),
                            strokeWidth = 1.5f
                        )

                        if (xHistory.size >= 2) {
                            val dx = width / (xHistory.size - 1).coerceAtLeast(1)
                            val centerY = height / 2

                            val xPoints = xHistory.indices.map { i ->
                                Offset(i * dx, (centerY - (xHistory[i] * 1.5f)).coerceIn(10f, height - 10f))
                            }

                            // Draw X series line
                            for (i in 0 until xPoints.size - 1) {
                                drawLine(
                                    color = accentColor,
                                    start = xPoints[i],
                                    end = xPoints[i + 1],
                                    strokeWidth = 2.5f
                                )
                            }

                            // If 3-axis, draw Y and Z with distinct accents
                            if (selectedOption!!.isThreeAxis && yHistory.size == xHistory.size && zHistory.size == xHistory.size) {
                                val yPoints = yHistory.indices.map { i ->
                                    Offset(i * dx, (centerY - (yHistory[i] * 1.5f)).coerceIn(10f, height - 10f))
                                }
                                val zPoints = zHistory.indices.map { i ->
                                    Offset(i * dx, (centerY - (zHistory[i] * 1.5f)).coerceIn(10f, height - 10f))
                                }

                                val yColor = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37)
                                val zColor = if (isDark) Color(0xFFD29922) else Color(0xFF9A6700)

                                for (i in 0 until yPoints.size - 1) {
                                    drawLine(
                                        color = yColor,
                                        start = yPoints[i],
                                        end = yPoints[i + 1],
                                        strokeWidth = 2f
                                    )
                                }
                                for (i in 0 until zPoints.size - 1) {
                                    drawLine(
                                        color = zColor,
                                        start = zPoints[i],
                                        end = zPoints[i + 1],
                                        strokeWidth = 2f
                                    )
                                }
                            }
                        }
                    }
                }

                // Axis legend if 3-axis
                if (selectedOption!!.isThreeAxis) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(label = "X Axis", color = accentColor)
                        LegendItem(label = "Y Axis", color = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37))
                        LegendItem(label = "Z Axis", color = if (isDark) Color(0xFFD29922) else Color(0xFF9A6700))
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color, isDark: Boolean) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label.uppercase(),
            color = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(
            text = label,
            color = if (isSystemInDarkTheme()) Color(0xFFC9D1D9) else Color(0xFF24292F),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
