package com.example.ui.screens

import android.hardware.Sensor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

    val currentTimeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    
    // Calculate stats from buffer
    val allRecordedValues = state.buffer.flatMap { it.values.toList() }
    val primaryVal = state.buffer.lastOrNull()?.values?.firstOrNull() ?: 0f
    val minVal = if (allRecordedValues.isNotEmpty()) allRecordedValues.minOrNull() ?: primaryVal else primaryVal
    val maxVal = if (allRecordedValues.isNotEmpty()) allRecordedValues.maxOrNull() ?: primaryVal else primaryVal
    val avgVal = if (allRecordedValues.isNotEmpty()) allRecordedValues.average().toFloat() else primaryVal

    val latestReading = state.buffer.lastOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("live_graph_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "LIVE GRAPH",
                                color = BentoTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BentoHeroCardBg)
                                    .border(1.dp, BentoGreenPrimary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = if (state.isPaused) BentoAmber else BentoGreenVibrant,
                                        modifier = Modifier.size(8.dp)
                                    )
                                    Text(
                                        text = if (state.isPaused) "PAUSED" else "LIVE",
                                        color = if (state.isPaused) BentoAmber else BentoGreenPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Last Update: $currentTimeStr",
                                color = BentoTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Sensor Selection Row
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.availableSensors) { sensorType ->
                            val isSelected = state.selectedSensorType == sensorType
                            val sensorName = when(sensorType) {
                                Sensor.TYPE_ACCELEROMETER -> "ACCEL"
                                Sensor.TYPE_GYROSCOPE -> "GYRO"
                                Sensor.TYPE_MAGNETIC_FIELD -> "MAG"
                                Sensor.TYPE_LIGHT -> "LIGHT"
                                else -> "UNKNOWN"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) BentoGreenPrimary.copy(alpha = 0.2f) else BentoHeroCardBg)
                                    .border(1.dp, if (isSelected) BentoGreenPrimary else BentoBorder, RoundedCornerShape(12.dp))
                                    .clickable { onSelectSensor(sensorType) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = sensorName,
                                    color = if (isSelected) BentoGreenPrimary else BentoTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (latestReading == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = BentoTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (state.isPaused) "Paused" else "Waiting for live sensor data",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            item {
                val accentColor = when (latestReading.category) {
                    com.example.data.model.SensorCategory.MOTION -> BentoGreenPrimary
                    com.example.data.model.SensorCategory.ENVIRONMENTAL -> Color(0xFF00E5FF)
                    else -> BentoGreenPrimary
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, BentoBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Text(
                                    text = latestReading.name.uppercase(),
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Unit: ${latestReading.unit}",
                                    color = BentoTextMuted,
                                    fontSize = 11.sp
                                )
                                IconButton(onClick = { onTogglePause(!state.isPaused) }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (state.isPaused) "Resume" else "Pause",
                                        tint = BentoTextPrimary
                                    )
                                }
                            }
                        }

                        // 4 Metric Tiles: Current | Min | Max | Avg
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroCardBg)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricTile("CURRENT", "%.2f".format(primaryVal), accentColor)
                            MetricTile("MIN", "%.2f".format(minVal), BentoTextSecondary)
                            MetricTile("MAX", "%.2f".format(maxVal), BentoRed)
                            MetricTile("AVG", "%.2f".format(avgVal), BentoGreenPrimary)
                        }

                        // We pass the buffer to our chart to draw
                        LiveGraphWaveformChart(
                            buffer = state.buffer,
                            unit = latestReading.unit
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun MetricTile(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = BentoTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LiveGraphWaveformChart(
    buffer: List<RawSensorReading>,
    unit: String,
    modifier: Modifier = Modifier
) {
    if (buffer.isEmpty()) return
    
    val lineColors = androidx.compose.runtime.remember { listOf(BentoGreenPrimary, BentoGreenVibrant, BentoAmber, BentoRed) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val width = size.width
            val height = size.height

            // 1. Grid Lines
            val gridStepX = width / 6
            for (i in 1..5) {
                drawLine(
                    color = BentoBorder,
                    start = androidx.compose.ui.geometry.Offset(gridStepX * i, 0f),
                    end = androidx.compose.ui.geometry.Offset(gridStepX * i, height),
                    strokeWidth = 1f
                )
            }
            drawLine(
                color = BentoBorder,
                start = androidx.compose.ui.geometry.Offset(0f, height / 2),
                end = androidx.compose.ui.geometry.Offset(width, height / 2),
                strokeWidth = 1f
            )

            // 2. Render Rolling Buffer Waveform
            if (buffer.isNotEmpty()) {
                val pointsCount = buffer.size
                val stepX = if (pointsCount > 1) width / (pointsCount - 1) else width
                
                for (axis in 0 until 3) {
                    val color = lineColors.getOrElse(axis) { BentoGreenPrimary }
                    val pathPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
                    for (i in buffer.indices) {
                        val frameValues = buffer[i].values
                        if (axis < frameValues.size) {
                            val v = frameValues[axis]
                            val centerY = height / 2
                            val mappedY = (centerY - (v * 2.5f)).coerceIn(4f, height - 4f)
                            val posX = i * stepX
                            pathPoints.add(androidx.compose.ui.geometry.Offset(posX, mappedY))
                        }
                    }

                    if (pathPoints.size >= 2) {
                        for (p in 0 until pathPoints.size - 1) {
                            drawLine(
                                color = color,
                                start = pathPoints[p],
                                end = pathPoints[p + 1],
                                strokeWidth = 2.5f
                            )
                        }
                    }
                }
            }
        }
    }
}
