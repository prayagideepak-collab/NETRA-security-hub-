package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.SystemAuditEntity
import com.example.data.audit.SystemSelfAuditEngine
import com.example.ui.MainViewModel
import com.example.ui.components.SensorDiagnosticsCard
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAuditScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val isAuditing by viewModel.isAuditing.collectAsStateWithLifecycle()
    val lastReport by viewModel.lastAuditReport.collectAsStateWithLifecycle()
    val sensorDiagnostics by viewModel.sensorDiagnostics.collectAsStateWithLifecycle()

    var activeScreenTab by remember { mutableStateOf(0) } // 0 = Live Status, 1 = Historical Reports
    var selectedServiceForDetails by remember { mutableStateOf<SystemSelfAuditEngine.ServiceStat?>(null) }
    var selectedHistoricalAudit by remember { mutableStateOf<SystemAuditEntity?>(null) }
    var historyFilter by remember { mutableStateOf("All") } // All, Failed, Restarted, Critical

    // Refresh rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "audit_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DIAGNOSTICS HUB",
                            color = BentoTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "Self-Audit & Health",
                            color = BentoTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("audit_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = BentoGreenPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runSelfAudit() },
                        enabled = !isAuditing,
                        modifier = Modifier.testTag("trigger_audit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Trigger Diagnostic Audit",
                            tint = BentoGreenPrimary,
                            modifier = Modifier.rotate(if (isAuditing) rotationAngle else 0f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoBackground,
                    titleContentColor = BentoTextPrimary
                )
            )
        },
        containerColor = BentoBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BentoBackground)
        ) {
            // Screen tabs selector (Live Diagnostics vs Audit History)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .padding(4.dp)
            ) {
                listOf("Live Status", "Audit History").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (activeScreenTab == index) BentoHeroCardBg else Color.Transparent)
                            .clickable {
                                activeScreenTab = index
                                selectedServiceForDetails = null
                                selectedHistoricalAudit = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (activeScreenTab == index) BentoGreenPrimary else BentoTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (activeScreenTab == 0) {
                // LIVE STATUS VIEW
                val liveStats = remember(isAuditing, lastReport) { viewModel.getLiveServiceStats() }
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Overall System Health Score Hero Card
                    item {
                        val score = lastReport?.overallSystemHealthScore ?: 100
                        val scoreColor = when {
                            score >= 90 -> BentoGreenPrimary
                            score >= 75 -> BentoAmber
                            else -> BentoRed
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(BentoHeroCardBg)
                                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "OVERALL HEALTH SCORE",
                                        color = BentoTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when {
                                            score >= 90 -> "System Running Optimal"
                                            score >= 75 -> "Suboptimal Telemetry"
                                            else -> "Warning: Core Failures"
                                        },
                                        color = BentoTextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Checked: ${lastReport?.totalServicesChecked ?: 27} modules. Healthy: ${lastReport?.healthyServices ?: 25}. Failed: ${lastReport?.failedServices ?: 0}.",
                                        color = BentoTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    if (lastReport?.recoveryActionsPerformed != null && lastReport?.recoveryActionsPerformed != "None") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Recovery: ${lastReport?.recoveryActionsPerformed}",
                                            color = BentoGreenPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(BentoCardBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$score%",
                                        color = scoreColor,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    // Selected Service Detail Expandable Card
                    selectedServiceForDetails?.let { stat ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(BentoDarkCardBg)
                                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                                    .padding(20.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stat.name.uppercase(),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { selectedServiceForDetails = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close details",
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Detail fields
                                    DetailRow(label = "Status", value = stat.status)
                                    DetailRow(label = "Start Time", value = formatMillis(stat.startTime))
                                    DetailRow(label = "Last Active", value = formatMillis(stat.lastSuccessfulActivity))
                                    DetailRow(label = "Restart Count", value = "${stat.restartCount} times")
                                    
                                    // Live CPU/Memory Usage Metrics
                                    val runtime = Runtime.getRuntime()
                                    val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                                    val maxMem = runtime.maxMemory() / (1024 * 1024)
                                    DetailRow(label = "JVM Memory Used", value = "$usedMem MB / $maxMem MB")
                                    DetailRow(label = "Active Threads", value = "${Thread.activeCount()} threads (${runtime.availableProcessors()} cores)")

                                    if (stat.lastError.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(BentoRed.copy(alpha = 0.15f))
                                                .border(1.dp, BentoRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "LAST SYSTEM EXCEPTION:",
                                                    color = BentoRed,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stat.lastError,
                                                    color = BentoRed,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section Header: Core Services
                    item {
                        SectionHeader(title = "Core System Services")
                    }

                    items(liveStats.filter { isCoreService(it.name) }) { stat ->
                        ServiceItemRow(stat = stat) {
                            selectedServiceForDetails = stat
                        }
                    }

                    // Section Header: Hardware Sensor Nodes
                    item {
                        SectionHeader(title = "Hardware Sensor Nodes")
                    }

                    items(liveStats.filter { isSensorService(it.name) }) { stat ->
                        ServiceItemRow(stat = stat) {
                            selectedServiceForDetails = stat
                        }
                    }

                    // Section Header: Background Components
                    item {
                        SectionHeader(title = "Telemetry & Background Services")
                    }

                    items(liveStats.filter { isBackgroundComponent(it.name) }) { stat ->
                        ServiceItemRow(stat = stat) {
                            selectedServiceForDetails = stat
                        }
                    }

                    // Section Header: Sensor Diagnostics
                    item {
                        SectionHeader(title = "Detailed Sensor Diagnostics")
                    }

                    item {
                        SensorDiagnosticsCard(diagnostics = sensorDiagnostics)
                    }
                }
            } else {
                // HISTORICAL AUDIT HISTORY VIEW
                val filteredAudits = remember(auditLogs, historyFilter) {
                    when (historyFilter) {
                        "Failed" -> auditLogs.filter { it.failedServices > 0 }
                        "Restarted" -> auditLogs.filter { it.restartedServices > 0 }
                        "Critical" -> auditLogs.filter { it.overallSystemHealthScore < 85 }
                        else -> auditLogs
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Failed", "Restarted", "Critical").forEach { filter ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (historyFilter == filter) BentoGreenPrimary else BentoCardBg)
                                    .clickable {
                                        historyFilter = filter
                                        selectedHistoricalAudit = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = filter,
                                    color = if (historyFilter == filter) Color.White else BentoTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Clear History Option
                        if (auditLogs.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear audit logs",
                                tint = BentoRed,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { viewModel.clearAuditHistory() }
                            )
                        }
                    }

                    // Selected Historical Audit Details Panel
                    selectedHistoricalAudit?.let { audit ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(BentoDarkCardBg)
                                .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "AUDIT #${audit.id} REPORT",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { selectedHistoricalAudit = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Report",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                DetailRow(label = "Audit Score", value = "${audit.overallSystemHealthScore}%")
                                DetailRow(label = "Date/Time", value = formatTime(audit.timestamp))
                                DetailRow(label = "Audit Duration", value = "${audit.durationMs} ms")
                                DetailRow(label = "Checked Modules", value = "${audit.totalServicesChecked}")
                                DetailRow(label = "Healthy / Failed", value = "${audit.healthyServices} healthy / ${audit.failedServices} failed")
                                DetailRow(label = "Unsupported Modules", value = "${audit.unsupportedComponents}")
                                DetailRow(label = "Recovery Action", value = audit.recoveryActionsPerformed)

                                // Mini list of failed services inside that audit
                                val failedServicesList = remember(audit) {
                                    getFailedServicesFromJson(audit.servicesDetailsJson)
                                }
                                if (failedServicesList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "FAILED IN THIS RUN:",
                                        color = BentoRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    failedServicesList.forEach { serviceName ->
                                        Text(
                                            text = "• $serviceName",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredAudits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = BentoTextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No self-audit reports found.",
                                    color = BentoTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredAudits) { audit ->
                                val scoreColor = when {
                                    audit.overallSystemHealthScore >= 90 -> BentoGreenPrimary
                                    audit.overallSystemHealthScore >= 75 -> BentoAmber
                                    else -> BentoRed
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BentoCardBg)
                                        .border(
                                            1.dp,
                                            if (selectedHistoricalAudit?.id == audit.id) BentoGreenPrimary else BentoBorder,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { selectedHistoricalAudit = audit }
                                        .padding(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Audit #${audit.id}",
                                                    color = BentoTextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(scoreColor.copy(alpha = 0.15f))
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "${audit.overallSystemHealthScore}%",
                                                        color = scoreColor,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = formatTime(audit.timestamp),
                                                color = BentoTextSecondary,
                                                fontSize = 11.sp
                                            )
                                            if (audit.failedServices > 0 || audit.restartedServices > 0) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Failed: ${audit.failedServices} | Restarted: ${audit.restartedServices}",
                                                    color = if (audit.failedServices > 0) BentoRed else BentoAmber,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = "View Report",
                                            tint = BentoTextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceItemRow(
    stat: SystemSelfAuditEngine.ServiceStat,
    onClick: () -> Unit
) {
    val statusColor = when (stat.status) {
        "✅ Running Normally" -> BentoGreenPrimary
        "🟡 Warning" -> BentoAmber
        "🔴 Failed" -> BentoRed
        else -> BentoTextMuted
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.name,
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stat.lastError.isNotEmpty() && stat.status != "✅ Running Normally") {
                    Text(
                        text = stat.lastError,
                        color = BentoRed,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = stat.status.replace("✅ ", "").replace("🟡 ", "").replace("🔴 ", "").replace("⚪ ", ""),
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = BentoTextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun formatMillis(timestamp: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun isCoreService(name: String): Boolean {
    return name in listOf(
        "Background Monitoring Service", "Sensor Manager", "Sensor Fusion Engine",
        "Event Detection Engine", "Event Logging Service", "Notification Service",
        "Voice Announcement Service", "Database Service", "Capability Manager",
        "Permission Manager", "Settings Manager"
    )
}

private fun isSensorService(name: String): Boolean {
    return name in listOf(
        "Magnetic Field Sensor", "Ambient Light Sensor", "Proximity Sensor",
        "Accelerometer", "Gyroscope", "Pressure Sensor", "Battery Temperature",
        "Thermal API", "Battery Manager"
    )
}

private fun isBackgroundComponent(name: String): Boolean {
    return name in listOf(
        "BroadcastReceivers", "Sensor Listeners", "Foreground Service",
        "Scheduled Workers", "Background Tasks", "Event Queue", "Notification Queue"
    )
}

private fun getFailedServicesFromJson(jsonStr: String): List<String> {
    val list = mutableListOf<String>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val status = obj.optString("status", "")
            if (status.contains("Failed") || status.contains("🔴")) {
                list.add(obj.optString("name", ""))
            }
        }
    } catch (e: Exception) {
        // Safe catch
    }
    return list
}
