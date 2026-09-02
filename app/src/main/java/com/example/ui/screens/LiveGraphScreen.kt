package com.example.ui.screens

import android.hardware.Sensor
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
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

    // Screen Background
    Surface(
        modifier = modifier.fillMaxSize(),
        color = NetraDarkBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("live_graph_screen"),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                NetraLiveHeader(state = state)
            }

            item {
                NetraLiveValue(latestReading = latestReading, state = state, onTogglePause = onTogglePause)
            }

            item {
                NetraSensorSelector(state = state, onSelectSensor = onSelectSensor)
            }

            item {
                NetraWaveformCard(buffer = state.buffer)
            }

            item {
                NetraMetricRow(buffer = state.buffer)
            }

            item {
                NetraSessionInfo(latestReading = latestReading, bufferSize = state.buffer.size)
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun NetraLiveHeader(state: LiveGraphState) {
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NETRA",
                    color = BentoGreenPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Human Safety",
                    color = NetraDarkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Live Sensor Monitor",
                color = NetraDarkTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(NetraDarkSurface)
                .border(1.dp, NetraDarkBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = statusText,
                color = NetraDarkTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun NetraLiveValue(latestReading: RawSensorReading?, state: LiveGraphState, onTogglePause: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (latestReading == null) {
            Text(
                text = "Waiting for live sensor data",
                color = NetraDarkTextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            val primaryVal = latestReading.values.firstOrNull() ?: 0f
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "%.2f".format(primaryVal),
                    color = NetraDarkTextPrimary,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = latestReading.unit,
                    color = NetraDarkTextSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = latestReading.name.uppercase(),
                    color = NetraDarkTextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                
                // Redesigned Pause/Resume
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(NetraDarkSurfaceElevated)
                        .clickable { onTogglePause(!state.isPaused) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isPaused) "Resume graph" else "Pause graph",
                        tint = NetraDarkTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NetraSensorSelector(state: LiveGraphState, onSelectSensor: (Int) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.availableSensors) { sensorType ->
            val isSelected = state.selectedSensorType == sensorType
            val sensorName = when (sensorType) {
                Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
                Sensor.TYPE_GYROSCOPE -> "Gyroscope"
                Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic Field"
                Sensor.TYPE_LIGHT -> "Light"
                else -> "Unknown"
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) NetraDarkSurfaceElevated else Color.Transparent)
                    .border(1.dp, if (isSelected) NetraDarkBorder else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelectSensor(sensorType) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = sensorName,
                    color = if (isSelected) NetraDarkTextPrimary else NetraDarkTextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun NetraWaveformCard(buffer: List<RawSensorReading>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NetraDarkSurface)
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (buffer.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No graph data",
                    color = NetraDarkTextMuted,
                    fontSize = 14.sp
                )
            }
            return@Box
        }

        val axisColors = remember { listOf(BentoGreenVibrant, BentoBlue, BentoAmber) }

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Grid Lines
            val horizontalLines = 4
            for (i in 0..horizontalLines) {
                val y = i * (height / horizontalLines)
                drawLine(
                    color = NetraDarkBorder,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
            val verticalLines = 6
            for (i in 0..verticalLines) {
                val x = i * (width / verticalLines)
                drawLine(
                    color = NetraDarkBorder,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }

            // Adaptive Scale Calculation
            val allValues = buffer.flatMap { it.values.toList() }
            val globalMin = allValues.minOrNull() ?: 0f
            val globalMax = allValues.maxOrNull() ?: 0f
            var range = globalMax - globalMin
            if (range < 0.01f) range = 0.01f
            
            val visibleMin = globalMin - (range * 0.1f)
            val visibleMax = globalMax + (range * 0.1f)
            val visibleRange = visibleMax - visibleMin

            // Draw Paths
            val pointsCount = buffer.size
            val stepX = if (pointsCount > 1) width / (pointsCount - 1) else width

            // Number of axes to draw (some sensors have 1, some have 3)
            val numAxes = buffer.firstOrNull()?.values?.size ?: 0

            for (axis in 0 until numAxes) {
                val color = axisColors.getOrElse(axis % axisColors.size) { BentoGreenVibrant }
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
                        drawLine(
                            color = color,
                            start = pathPoints[p],
                            end = pathPoints[p + 1],
                            strokeWidth = 3f
                        )
                    }
                } else if (pathPoints.size == 1) {
                    drawCircle(
                        color = color,
                        radius = 3f,
                        center = pathPoints[0]
                    )
                }
            }
        }
    }
}

@Composable
fun NetraMetricRow(buffer: List<RawSensorReading>) {
    if (buffer.isEmpty()) return

    val allValues = buffer.flatMap { it.values.toList() }
    val primaryVal = buffer.lastOrNull()?.values?.firstOrNull() ?: 0f
    val minVal = allValues.minOrNull() ?: primaryVal
    val maxVal = allValues.maxOrNull() ?: primaryVal
    val avgVal = if (allValues.isNotEmpty()) allValues.average().toFloat() else primaryVal

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        NetraMetricItem("CURRENT", "%.2f".format(primaryVal))
        NetraMetricItem("MIN", "%.2f".format(minVal))
        NetraMetricItem("MAX", "%.2f".format(maxVal))
        NetraMetricItem("AVG", "%.2f".format(avgVal))
    }
}

@Composable
fun NetraMetricItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NetraDarkSurface)
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = NetraDarkTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = NetraDarkTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun NetraSessionInfo(latestReading: RawSensorReading?, bufferSize: Int) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timestamp = latestReading?.timestamp?.let { timeFormatter.format(Date(it)) } ?: "--"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NetraDarkSurface)
            .border(1.dp, NetraDarkBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoRow(label = "Sensor", value = latestReading?.name ?: "None")
        InfoRow(label = "Mode", value = "Foreground")
        InfoRow(label = "Buffer Size", value = "$bufferSize samples")
        InfoRow(label = "Last Update", value = timestamp)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = NetraDarkTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = NetraDarkTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
