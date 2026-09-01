package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorDiagnosticsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val throttles by viewModel.sensorThrottles.collectAsStateWithLifecycle()
    val watchdogStates by viewModel.watchdogModuleStates.collectAsStateWithLifecycle()

    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BentoBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. WATCHDOG MONITOR & AUTO-RECOVERY Title Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = BentoHeroCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Watchdog Security",
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "NETRA RECOVERY WATCHDOG",
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The background Watchdog Engine monitors 10 real-time modules every 0.5 seconds. If any tracker reports no new updates for 60 consecutive seconds, Netra executes a silent restart of the affected data stream to prevent frozen dashboard states.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 2. WATCHDOG ACTIVE LIVE TRACKERS
        item {
            Text(
                text = "AUTO RECOVERY TRACKERS",
                style = MaterialTheme.typography.titleLarge,
                color = BentoTextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        items(watchdogStates.values.toList()) { state ->
            val statusColor = when (state.status) {
                "Active" -> BentoGreenPrimary
                "Refreshing" -> Color(0xFFFF9100) // Attention orange
                else -> Color(0xFFFF3366) // Error/Failure pink-red
            }

            val statusIcon = when (state.status) {
                "Active" -> Icons.Default.CheckCircle
                "Refreshing" -> Icons.Default.Cached
                else -> Icons.Default.Warning
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                    .testTag("watchdog_card_${state.name.lowercase().replace(" ", "_")}"),
                colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = state.name.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = BentoTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sequence No: #${state.sequenceNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextMuted
                            )
                        }

                        // Status Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = state.status,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = state.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Timestamps details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LAST DATA SYNC",
                                style = MaterialTheme.typography.labelSmall,
                                color = BentoTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormatter.format(Date(state.lastUpdateTimestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SENSOR EVENT EPOCH",
                                style = MaterialTheme.typography.labelSmall,
                                color = BentoTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.sensorEventTimestamp.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary
                            )
                        }
                    }

                    if (state.lastErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Error: ${state.lastErrorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF3366),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Developer Simulation Control (Accessible touch target height >= 48dp)
                    Button(
                        onClick = { viewModel.forceWatchdogStale(state.name) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("simulate_stale_${state.name.lowercase().replace(" ", "_")}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isRefreshing) Color.DarkGray else Color.DarkGray.copy(alpha = 0.5f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isRefreshing) Icons.Default.Cached else Icons.Default.PlayArrow,
                                contentDescription = "Simulate Timeout",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (state.isRefreshing) "REFRESHING STREAM..." else "SIMULATE FROZEN DATA (TIMEOUT)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. SENSOR THROTTLING CONTROL
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "HARDWARE THROTTLE CONTROL",
                style = MaterialTheme.typography.titleLarge,
                color = BentoTextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        items(capabilities) { cap ->
            val currentThrottle = throttles[cap.type] ?: 150L
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = cap.name, style = MaterialTheme.typography.titleMedium, color = BentoTextPrimary)
                    Text(text = "Throttle Interval: ${currentThrottle}ms", style = MaterialTheme.typography.bodyMedium, color = BentoTextMuted)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(150L, 500L, 1000L, 2000L).forEach { interval ->
                            Button(
                                onClick = { viewModel.setSensorThrottle(cap.type, interval) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp), // 48dp accessible minimum touch height
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (currentThrottle == interval) BentoGreenPrimary else Color.DarkGray
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${interval}ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
