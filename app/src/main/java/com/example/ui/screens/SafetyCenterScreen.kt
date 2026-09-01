package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.data.db.SafetyEventEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SafetyCenterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val safetyState by viewModel.safetyEngineState.collectAsStateWithLifecycle()
    val eventLogs by viewModel.repository.eventLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentAnnouncements by viewModel.repository.alertManager.recentAlertMessages.collectAsStateWithLifecycle()
    val isNightMode = remember { viewModel.repository.alertManager.isNightModeActive() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BentoBackground)
            .padding(16.dp)
            .testTag("safety_center_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. DUAL STATUS HERO CARD (Safety State + Device Health)
        item {
            SafetyDualStatusCard(
                safetyState = safetyState,
                isNightMode = isNightMode,
                onRefresh = { viewModel.evaluateSafetyConditions() }
            )
        }

        // 2. ACTIVE HAZARDS SECTION
        item {
            Text(
                text = "Active Safety Hazards",
                style = MaterialTheme.typography.titleMedium,
                color = BentoTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        if (safetyState.activeEvents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("no_active_hazards_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BentoGreenVibrant.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Safe",
                                tint = BentoGreenVibrant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "All Conditions Nominal",
                                color = BentoTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Zero active physical, thermal, or magnetic safety hazards.",
                                color = BentoTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            items(safetyState.activeEvents, key = { it.eventId }) { event ->
                ActiveHazardCard(event = event)
            }
        }

        // 3. SUBSYSTEMS TELEMETRY & FRESHNESS GRID
        item {
            Text(
                text = "Subsystems Telemetry & Freshness",
                style = MaterialTheme.typography.titleMedium,
                color = BentoTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SubsystemTelemetryMatrix(subsystems = safetyState.subsystemHealths)
        }

        // 4. RECENT SAFETY ANNOUNCEMENTS & ALERTS STREAM
        if (recentAnnouncements.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Safety Alert Stream",
                    style = MaterialTheme.typography.titleMedium,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentAnnouncements.take(4).forEach { msg ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = BentoAmber,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    color = BentoTextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. SAFETY EVENT AUDIT LOG & RETENTION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Safety Event History (7-Day Log)",
                    style = MaterialTheme.typography.titleMedium,
                    color = BentoTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${eventLogs.size} records",
                    style = MaterialTheme.typography.labelMedium,
                    color = BentoTextMuted
                )
            }
        }

        if (eventLogs.isEmpty()) {
            item {
                Text(
                    "No safety events recorded in history database.",
                    color = BentoTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(eventLogs.take(15), key = { it.id }) { logItem ->
                SafetyLogItemCard(log = logItem)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SafetyDualStatusCard(
    safetyState: SafetyEngineState,
    isNightMode: Boolean,
    onRefresh: () -> Unit
) {
    val safetyColor = when (safetyState.safetyRiskState) {
        SafetyRiskState.CRITICAL -> BentoRed
        SafetyRiskState.WARNING -> BentoAmber
        SafetyRiskState.ATTENTION -> BentoAmber
        SafetyRiskState.SAFE -> BentoGreenVibrant
    }

    val healthColor = when (safetyState.deviceHealthState) {
        DeviceHealthState.HEALTHY -> BentoGreenVibrant
        DeviceHealthState.DEGRADED -> BentoAmber
        DeviceHealthState.RECOVERING -> Color(0xFF00E5FF)
        DeviceHealthState.UNAVAILABLE -> BentoTextMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("safety_dual_status_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(safetyColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "NETRA SAFETY ENGINE v4.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = BentoTextMuted,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp).testTag("refresh_safety_button")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = BentoTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Dual Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Safety Risk State Badge
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = safetyColor.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, safetyColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "SAFETY RISK",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            safetyState.safetyRiskState.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = safetyColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (safetyState.safetyRiskState == SafetyRiskState.SAFE) "Zero physical threats" else "${safetyState.activeEvents.size} Active Hazard(s)",
                            fontSize = 11.sp,
                            color = BentoTextSecondary
                        )
                    }
                }

                // Device Health State Badge
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = healthColor.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, healthColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "DEVICE HEALTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = BentoTextMuted,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            safetyState.deviceHealthState.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = healthColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (safetyState.deviceHealthState == DeviceHealthState.HEALTHY) "Sensors synchronized" else "Degraded telemetry",
                            fontSize = 11.sp,
                            color = BentoTextSecondary
                        )
                    }
                }
            }

            // Night Mode & Rules Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isNightMode) Icons.Default.Nightlight else Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = if (isNightMode) BentoAmber else BentoGreenVibrant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isNightMode) "Night Mode: Quiet (10PM-6AM)" else "Daytime: Full Alerts Active",
                        fontSize = 12.sp,
                        color = BentoTextSecondary
                    )
                }

                Text(
                    "Absolute Truth Rule",
                    fontSize = 11.sp,
                    color = BentoGreenPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ActiveHazardCard(event: CanonicalSafetyEvent) {
    val hazardColor = when (event.severity) {
        SafetyRiskState.CRITICAL -> BentoRed
        SafetyRiskState.WARNING -> BentoAmber
        SafetyRiskState.ATTENTION -> BentoAmber
        SafetyRiskState.SAFE -> BentoGreenVibrant
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("active_hazard_${event.eventId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = hazardColor.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, hazardColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.eventId,
                    fontWeight = FontWeight.Bold,
                    color = hazardColor,
                    fontSize = 14.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = hazardColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = event.lifecycleState.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = hazardColor
                    )
                }
            }

            Text(
                text = event.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = BentoTextPrimary
            )

            Text(
                text = event.description,
                fontSize = 13.sp,
                color = BentoTextSecondary,
                lineHeight = 18.sp
            )

            Divider(color = hazardColor.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Peak Telemetry", fontSize = 11.sp, color = BentoTextMuted)
                    Text(event.peakValue ?: "N/A", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BentoTextPrimary)
                }

                Column {
                    Text("Threshold", fontSize = 11.sp, color = BentoTextMuted)
                    Text(event.thresholdValue ?: "N/A", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BentoTextPrimary)
                }

                Column {
                    Text("Confidence", fontSize = 11.sp, color = BentoTextMuted)
                    Text(event.confidence.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BentoGreenPrimary)
                }
            }
        }
    }
}

@Composable
fun SubsystemTelemetryMatrix(subsystems: Map<String, SubsystemHealth>) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("subsystem_telemetry_matrix"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (subsystems.isEmpty()) {
                Text(
                    "Connecting to hardware HAL subsystems...",
                    color = BentoTextMuted,
                    fontSize = 13.sp
                )
            } else {
                subsystems.values.forEach { sub ->
                    SubsystemRow(sub = sub)
                }
            }
        }
    }
}

@Composable
fun SubsystemRow(sub: SubsystemHealth) {
    val freshnessColor = when (sub.freshness) {
        DataFreshness.FRESH -> BentoGreenVibrant
        DataFreshness.DELAYED -> BentoAmber
        DataFreshness.STALE -> BentoAmber
        DataFreshness.UNAVAILABLE -> BentoTextMuted
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(sub.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BentoTextPrimary)
            Text(sub.statusMessage, fontSize = 11.sp, color = BentoTextSecondary)
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = freshnessColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = sub.freshness.name,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = freshnessColor
            )
        }
    }
}

@Composable
fun SafetyLogItemCard(log: SafetyEventEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(log.timestamp) { dateFormat.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("safety_log_item_${log.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.eventId.ifEmpty { log.eventType },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BentoGreenPrimary
                )
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = BentoTextMuted
                )
            }

            Text(
                text = log.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = BentoTextPrimary
            )

            if (log.description.isNotEmpty()) {
                Text(
                    text = log.description,
                    fontSize = 12.sp,
                    color = BentoTextSecondary
                )
            }

            if (!log.resolution.isNullOrEmpty()) {
                Text(
                    text = "Resolution: ${log.resolution}",
                    fontSize = 11.sp,
                    color = BentoGreenVibrant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
