package com.example.ui.screens

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RawSensorReading
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveGraphScreen(
    state: LiveGraphState,
    onSelectSensor: (Int) -> Unit,
    onTogglePause: (Boolean) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onStartSession()
        onDispose {
            onStopSession()
        }
    }

    val latestReading = state.buffer.lastOrNull()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NetraDarkBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("live_graph_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            // 1. Sleek Technical Console Header
            item {
                NetraConsoleHeader(state = state)
            }

            // 2. High-Contrast Premium Value Readout Panel
            item {
                NetraConsoleReadout(latestReading = latestReading, state = state, onTogglePause = onTogglePause)
            }

            // 3. Futuristic Channel Selector Deck
            item {
                NetraSensorDeckSelector(state = state, onSelectSensor = onSelectSensor)
            }

            // 4. Oscilloscope Waveform Display
            item {
                NetraOscilloscopeWaveformCard(buffer = state.buffer)
            }

            // 5. Technical Diagnostic Metric Grid
            item {
                NetraConsoleMetricsGrid(buffer = state.buffer)
            }

            // 6. HUD System Status Information
            item {
                NetraHUDMetadataInfo(latestReading = latestReading, bufferSize = state.buffer.size)
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun NetraConsoleHeader(state: LiveGraphState) {
    val statusText = when {
        state.buffer.isEmpty() && state.isPaused -> "PAUSED"
        state.buffer.isEmpty() -> "WAITING"
        state.isPaused -> "PAUSED"
        else -> "LIVE"
    }
    
    val statusColor = when (statusText) {
        "LIVE" -> BentoGreenVibrant
        "PAUSED" -> BentoAmber
        else -> NetraDarkTextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NetraDarkBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(NetraDarkSurface.copy(alpha = 0.4f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NETRA AI",
                    color = BentoGreenPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(NetraDarkTextMuted.copy(alpha = 0.5f), CircleShape)
                )
                Text(
                    text = "HUMAN SAFETY SYSTEM",
                    color = NetraDarkTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "REAL-TIME DIAGNOSTIC TELEMETRY // CORE_ENGINE",
                color = NetraDarkTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(8.dp)
            )
            Text(
                text = statusText,
                color = NetraDarkTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun NetraConsoleReadout(latestReading: RawSensorReading?, state: LiveGraphState, onTogglePause: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(12.dp))
            .background(NetraDarkSurface)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE READOUT [CH_01]",
                    color = NetraDarkTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                
                // Redesigned Console Play/Pause Trigger
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NetraDarkSurfaceElevated)
                        .border(1.dp, NetraDarkBorder, RoundedCornerShape(6.dp))
                        .clickable { onTogglePause(!state.isPaused) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isPaused) "Resume" else "Pause",
                        tint = if (state.isPaused) BentoGreenVibrant else NetraDarkTextPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (state.isPaused) "RESUME" else "FREEZE",
                        color = if (state.isPaused) BentoGreenVibrant else NetraDarkTextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (latestReading == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WAITING FOR RAW TELEMETRY DATA...",
                        color = NetraDarkTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                val primaryVal = latestReading.values.firstOrNull() ?: 0f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "%.3f".format(primaryVal),
                            color = NetraDarkTextPrimary,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1.5).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = latestReading.unit,
                            color = BentoGreenPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "REGISTERED: ${latestReading.name.uppercase()}",
                            color = NetraDarkTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "STATUS: ACTIVE_FEED",
                            color = BentoGreenVibrant,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetraSensorDeckSelector(state: LiveGraphState, onSelectSensor: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "SELECT DATA CHANNEL",
            color = NetraDarkTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp)
        )
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NetraDarkBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .background(NetraDarkSurface.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.availableSensors) { sensorType ->
                val isSelected = state.selectedSensorType == sensorType
                val sensorName = when (sensorType) {
                    Sensor.TYPE_ACCELEROMETER -> "ACCEL"
                    Sensor.TYPE_GYROSCOPE -> "GYRO"
                    Sensor.TYPE_MAGNETIC_FIELD -> "MAG_FIELD"
                    Sensor.TYPE_LIGHT -> "LUX_METER"
                    else -> "CH_UNKNOWN"
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BentoGreenPrimary.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (isSelected) BentoGreenPrimary.copy(alpha = 0.6f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectSensor(sensorType) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = sensorName,
                        color = if (isSelected) BentoGreenPrimary else NetraDarkTextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NetraOscilloscopeWaveformCard(buffer: List<RawSensorReading>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070B09)) // Futuristic, ultra-dark green terminal slate
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (buffer.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO INCOMING WAVEFORM SIGNAL",
                    color = NetraDarkTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
            return@Box
        }

        val axisColors = remember { listOf(BentoGreenVibrant, BentoBlue, BentoAmber) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Sleek Technical Corner Brackets
            val bracketLen = 16f
            val bracketColor = NetraDarkBorder.copy(alpha = 0.7f)
            // Top-Left
            drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLen, 0f), 1.5f)
            drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bracketLen), 1.5f)
            // Top-Right
            drawLine(bracketColor, Offset(width, 0f), Offset(width - bracketLen, 0f), 1.5f)
            drawLine(bracketColor, Offset(width, 0f), Offset(width, bracketLen), 1.5f)
            // Bottom-Left
            drawLine(bracketColor, Offset(0f, height), Offset(bracketLen, height), 1.5f)
            drawLine(bracketColor, Offset(0f, height), Offset(0f, height - bracketLen), 1.5f)
            // Bottom-Right
            drawLine(bracketColor, Offset(width, height), Offset(width - bracketLen, height), 1.5f)
            drawLine(bracketColor, Offset(width, height), Offset(width, height - bracketLen), 1.5f)

            // 2. High-Fidelity Dotted Grid Layout (Oscilloscope style)
            val horizontalLines = 8
            val gridColor = NetraDarkBorder.copy(alpha = 0.2f)
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)

            for (i in 1 until horizontalLines) {
                val y = i * (height / horizontalLines)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = pathEffect
                )
            }
            
            val verticalLines = 10
            for (i in 1 until verticalLines) {
                val x = i * (width / verticalLines)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                    pathEffect = pathEffect
                )
            }

            // Adaptive Scale Calculation
            val allValues = buffer.flatMap { it.values.toList() }
            val globalMin = allValues.minOrNull() ?: 0f
            val globalMax = allValues.maxOrNull() ?: 0f
            var range = globalMax - globalMin
            if (range < 0.01f) range = 0.01f
            
            val visibleMin = globalMin - (range * 0.12f)
            val visibleMax = globalMax + (range * 0.12f)
            val visibleRange = visibleMax - visibleMin

            // 3. Draw Neon Glowing Waveforms
            val pointsCount = buffer.size
            val stepX = if (pointsCount > 1) width / (pointsCount - 1) else width
            val numAxes = buffer.firstOrNull()?.values?.size ?: 0

            for (axis in 0 until numAxes) {
                val baseColor = axisColors.getOrElse(axis % axisColors.size) { BentoGreenVibrant }
                val pathPoints = mutableListOf<Offset>()

                for (i in buffer.indices) {
                    val frameValues = buffer[i].values
                    if (axis < frameValues.size) {
                        val v = frameValues[axis]
                        val normalized = (v - visibleMin) / visibleRange
                        val mappedY = height - (normalized * height).toFloat()
                        val posX = i * stepX
                        pathPoints.add(Offset(posX, mappedY))
                    }
                }

                if (pathPoints.size >= 2) {
                    for (p in 0 until pathPoints.size - 1) {
                        // Neon Glow Pass
                        drawLine(
                            color = baseColor.copy(alpha = 0.18f),
                            start = pathPoints[p],
                            end = pathPoints[p + 1],
                            strokeWidth = 8f
                        )
                        // Sharp Wire Trace Core Pass
                        drawLine(
                            color = baseColor,
                            start = pathPoints[p],
                            end = pathPoints[p + 1],
                            strokeWidth = 2.2f
                        )
                    }
                }
            }

            // 4. Draw Center Reference Reticle Axis Line
            drawLine(
                color = BentoGreenPrimary.copy(alpha = 0.15f),
                start = Offset(0f, height / 2f),
                end = Offset(width, height / 2f),
                strokeWidth = 1.5f
            )
        }

        // Top Header overlays inside oscilloscope canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "OSCILLOSCOPE // GAIN: AUTO",
                color = BentoGreenPrimary.copy(alpha = 0.6f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Text(
                text = "SIGNAL_STABLE",
                color = BentoGreenVibrant.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun NetraConsoleMetricsGrid(buffer: List<RawSensorReading>) {
    if (buffer.isEmpty()) return

    val allValues = buffer.flatMap { it.values.toList() }
    val primaryVal = buffer.lastOrNull()?.values?.firstOrNull() ?: 0f
    val minVal = allValues.minOrNull() ?: primaryVal
    val maxVal = allValues.maxOrNull() ?: primaryVal
    val avgVal = if (allValues.isNotEmpty()) allValues.average().toFloat() else primaryVal

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "TELEMETRY METRICS SUMMARY",
            color = NetraDarkTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val weightModifier = Modifier.weight(1f)
            NetraConsoleMetricCard(weightModifier, "CURRENT", "%.3f".format(primaryVal), BentoGreenPrimary)
            NetraConsoleMetricCard(weightModifier, "MAX_PEAK", "%.3f".format(maxVal), BentoAmber)
            NetraConsoleMetricCard(weightModifier, "MIN_PEAK", "%.3f".format(minVal), BentoBlue)
            NetraConsoleMetricCard(weightModifier, "AVERAGE", "%.3f".format(avgVal), NetraDarkTextSecondary)
        }
    }
}

@Composable
fun NetraConsoleMetricCard(modifier: Modifier, label: String, value: String, accentColor: Color) {
    Column(
        modifier = modifier
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(8.dp))
            .background(NetraDarkSurface)
            .padding(10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(5.dp).background(accentColor, CircleShape))
            Text(
                text = label,
                color = NetraDarkTextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = value,
            color = NetraDarkTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun NetraHUDMetadataInfo(latestReading: RawSensorReading?, bufferSize: Int) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timestamp = latestReading?.timestamp?.let { timeFormatter.format(Date(it)) } ?: "--"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(8.dp))
            .background(NetraDarkSurface.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "SYSTEM REGISTER METADATA",
            color = NetraDarkTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        
        HUDMetadataRow(label = "SYS.SENSOR_IDENTITY", value = latestReading?.name ?: "INITIALIZING")
        HUDMetadataRow(label = "SYS.OPERATING_MODE", value = "INTELLIGENT_FOREGROUND")
        HUDMetadataRow(label = "SYS.BUFFER_CAPACITY", value = "$bufferSize / 100 SAMPLES")
        HUDMetadataRow(label = "SYS.TELEMETRY_STAMP", value = timestamp)
    }
}

@Composable
fun HUDMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = NetraDarkTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value.uppercase(),
            color = NetraDarkTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
