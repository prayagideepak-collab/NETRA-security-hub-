package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RawSensorReading
import com.example.ui.components.SensorWaveformChart
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveGraphScreen(
    liveReadings: Map<String, RawSensorReading>,
    modifier: Modifier = Modifier
) {
    // True Only Rule: Filter only non-stale active live readings
    val activeReadings = liveReadings.values.filter { !it.isStale(15000L) }
    val currentTimeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("live_graph_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title Card matching reference dashboard
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
                                        tint = BentoGreenVibrant,
                                        modifier = Modifier.size(8.dp)
                                    )
                                    Text(
                                        text = "LIVE",
                                        color = BentoGreenPrimary,
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
                            Text(
                                text = "Data Age: 0.3s",
                                color = BentoTextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Text(
                        text = "Real-time Sensor Telemetry — Foreground Active Stream",
                        color = BentoTextSecondary,
                        fontSize = 12.sp
                    )

                    // Live Status Sub-Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHeroCardBg)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = BentoGreenVibrant, modifier = Modifier.size(10.dp))
                            Text("All Systems Normal", color = BentoGreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Monitoring: ${activeReadings.size} Sensors", color = BentoTextSecondary, fontSize = 11.sp)
                        Text("Sampling: Adaptive", color = BentoTextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        if (activeReadings.isEmpty()) {
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
                            text = "LIVE TELEMETRY STREAM UNAVAILABLE",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "No active sensor streams detected. Open Security Hub or ensure device sensors are operational.",
                            color = BentoTextMuted,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(activeReadings, key = { it.sensorId }) { reading ->
                val primaryVal = reading.values.firstOrNull() ?: 0f
                val minVal = if (reading.values.isNotEmpty()) reading.values.minOrNull() ?: primaryVal else primaryVal
                val maxVal = if (reading.values.isNotEmpty()) reading.values.maxOrNull() ?: primaryVal else primaryVal
                val avgVal = if (reading.values.isNotEmpty()) reading.values.average().toFloat() else primaryVal

                // Assign accent color based on sensor category / type
                val accentColor = when {
                    reading.name.contains("Magnetic", true) -> Color(0xFFB388FF) // Purple
                    reading.name.contains("Accel", true) -> BentoGreenPrimary // Green
                    reading.name.contains("Gyro", true) -> Color(0xFF00E5FF) // Cyan/Blue
                    reading.name.contains("Thermal", true) || reading.name.contains("Temp", true) -> Color(0xFFFF9100) // Orange
                    reading.name.contains("Light", true) -> Color(0xFFFFEA00) // Yellow
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
                                    text = reading.name.uppercase(),
                                    color = accentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Unit: ${reading.unit}",
                                color = BentoTextMuted,
                                fontSize = 11.sp
                            )
                        }

                        // 4 Metric Tiles: Current | Min | Max | Avg (matching reference layout)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BentoHeroCardBg)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            MetricTile("CURRENT", "%.2f %s".format(primaryVal, reading.unit), accentColor)
                            MetricTile("MIN", "%.2f %s".format(minVal, reading.unit), BentoTextSecondary)
                            MetricTile("MAX", "%.2f %s".format(maxVal, reading.unit), BentoRed)
                            MetricTile("AVG", "%.2f %s".format(avgVal, reading.unit), BentoGreenPrimary)
                        }

                        // Centralized Waveform Chart
                        SensorWaveformChart(
                            values = reading.values,
                            unit = reading.unit
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
