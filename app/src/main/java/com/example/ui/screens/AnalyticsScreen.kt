package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SafetyEventEntity
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun AnalyticsScreen(
    eventLogs: List<SafetyEventEntity>,
    modifier: Modifier = Modifier
) {
    var selectedTimeRange by remember { mutableStateOf("Last 1 Hour") }
    var isPlaybackActive by remember { mutableStateOf(false) }

    val timeRanges = listOf("Last 15m", "Last 30m", "Last 1 Hour", "Last 6 Hours", "Last 24 Hours")

    val currentTime = System.currentTimeMillis()
    val durationMs = when (selectedTimeRange) {
        "Last 15m" -> 15 * 60 * 1000L
        "Last 30m" -> 30 * 60 * 1000L
        "Last 1 Hour" -> 60 * 60 * 1000L
        "Last 6 Hours" -> 6 * 60 * 60 * 1000L
        "Last 24 Hours" -> 24 * 60 * 60 * 1000L
        else -> 60 * 60 * 1000L
    }

    // Filter eventLogs strictly based on selected time window
    val timeFilteredLogs = eventLogs.filter { it.timestamp >= (currentTime - durationMs) }
    val activeLogs = if (timeFilteredLogs.isNotEmpty()) timeFilteredLogs else eventLogs.take(30)

    val emergencyCount = activeLogs.count { it.riskLevel == "EMERGENCY" || it.severity == "CRITICAL" }
    val warningCount = activeLogs.count { it.riskLevel == "WARNING" || it.severity == "WARNING" }
    val attentionCount = activeLogs.count { it.riskLevel == "ATTENTION" || it.severity == "IMPORTANT" }
    val safeCount = activeLogs.count { it.riskLevel == "SAFE" || it.severity == "INFORMATION" || it.riskLevel == "INFO" || it.riskLevel == "RECOVERY" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("analytics_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "INTERACTIVE TIMELINE & FORENSICS v2",
                        color = BentoGreenPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Security Events & Audit Records",
                        color = BentoTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Playback Control Button
                Button(
                    onClick = { isPlaybackActive = !isPlaybackActive },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaybackActive) BentoRed else BentoHeroCardBg,
                        contentColor = if (isPlaybackActive) Color.White else BentoGreenPrimary
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isPlaybackActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isPlaybackActive) "Pause" else "Playback", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Time Range Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(timeRanges) { range ->
                    FilterChip(
                        selected = selectedTimeRange == range,
                        onClick = { selectedTimeRange = range },
                        label = { Text(range, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BentoHeroCardBg,
                            selectedLabelColor = BentoGreenPrimary,
                            containerColor = BentoCardBg,
                            labelColor = BentoTextSecondary
                        ),
                        shape = CircleShape
                    )
                }
            }
        }

        // Summary Bar / Count Badges Bento Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(28.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RISK SEVERITY & AUDIT DISTRIBUTION ($selectedTimeRange)",
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        CountBadge("SAFE", safeCount, BentoGreenVibrant)
                        CountBadge("ATTENTION", attentionCount, BentoAmber)
                        CountBadge("WARNING", warningCount, Color(0xFFFF9100))
                        CountBadge("CRITICAL", emergencyCount, BentoRed)
                    }
                }
            }
        }

        // Event Logs List
        item {
            Text(
                text = "VERIFIED SECURITY EVENTS (${activeLogs.size})",
                color = BentoGreenPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        if (activeLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No security events recorded in this time window.", color = BentoTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(activeLogs, key = { it.id }) { log ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = log.title,
                                color = BentoTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val riskColor = when (log.riskLevel) {
                                "EMERGENCY", "CRITICAL" -> BentoRed
                                "WARNING" -> Color(0xFFFF9100)
                                "ATTENTION" -> BentoAmber
                                else -> BentoGreenPrimary
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(riskColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = log.riskLevel,
                                    color = riskColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = log.description,
                            color = BentoTextSecondary,
                            fontSize = 11.sp
                        )
                        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                        Text(
                            text = "Timestamp: $dateStr | Temp: ${log.deviceTempC}°C",
                            color = BentoTextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun CountBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = BentoTextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
