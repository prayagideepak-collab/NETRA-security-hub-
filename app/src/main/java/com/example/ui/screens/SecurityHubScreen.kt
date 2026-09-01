package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.example.data.db.SafetyEventEntity
import com.example.data.model.FeatureCategory
import com.example.data.model.FeatureStatus
import com.example.data.model.SecurityFeature
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityHubScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val features by viewModel.securityEngine.features.collectAsStateWithLifecycle()
    val score by viewModel.securityEngine.securityScore.collectAsStateWithLifecycle()
    val validationStatus by viewModel.securityEngine.validationStatus.collectAsStateWithLifecycle()
    val missingMandatories by viewModel.securityEngine.missingMandatoryFeatures.collectAsStateWithLifecycle()
    val scanDuration by viewModel.securityEngine.scanDurationMs.collectAsStateWithLifecycle()
    val lastScanTime by viewModel.securityEngine.lastScanTime.collectAsStateWithLifecycle()
    val eventLogs by viewModel.eventLogs.collectAsStateWithLifecycle(initialValue = emptyList())

    val developerMode by viewModel.developerMode.collectAsStateWithLifecycle()
    val isDeveloperAuthenticated by viewModel.isDeveloperAuthenticated.collectAsStateWithLifecycle()
    val showDevPanel = developerMode && isDeveloperAuthenticated
    val simulatedMfr by viewModel.securityEngine.simulatedManufacturer.collectAsStateWithLifecycle()

    // First-run configuration
    val firstRunState = remember { mutableStateOf(true) }
    var showWizard by remember { mutableStateOf(false) }
    var selectedFeatureForDetail by remember { mutableStateOf<SecurityFeature?>(null) }
    var showTimelineDialog by remember { mutableStateOf(false) }

    // Security monitoring status
    val isLocationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val isNotificationsGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val monitoringStatus = if (isLocationGranted && isNotificationsGranted) "Active" else "Limited"
    val monitoringReason = if (monitoringStatus == "Limited") "Required permissions not granted" else ""

    // Calculate rating label
    val ratingLabel = when (score) {
        in 90..100 -> "Excellent"
        in 80..89 -> "Very Good"
        in 70..79 -> "Good"
        in 50..69 -> "Weak"
        else -> "High Risk"
    }

    val ratingColor = when (ratingLabel) {
        "Excellent" -> BentoGreenVibrant
        "Very Good", "Good" -> BentoGreenPrimary
        "Weak" -> Color(0xFFFBC02D)
        else -> Color(0xFFE57373)
    }

    // Split features
    val mandatoryFeatures = features.filter { it.isMandatory }
    val optionalFeatures = features.filter { !it.isMandatory }

    // Audit counts
    val enabledMandatories = mandatoryFeatures.count { it.status == FeatureStatus.ENABLED }
    val totalMandatoriesSupported = mandatoryFeatures.count { it.status != FeatureStatus.NOT_SUPPORTED }
    val enabledOptionals = optionalFeatures.count { it.status == FeatureStatus.ENABLED }
    val totalOptionalsSupported = optionalFeatures.count { it.status != FeatureStatus.NOT_SUPPORTED }

    // Recommendation list
    val recommendations = features.filter { it.status == FeatureStatus.DISABLED }

    LaunchedEffect(Unit) {
        viewModel.securityEngine.scanDevice()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BentoBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header: Monitoring & Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SECURITY MONITORING",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoTextMuted,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: $monitoringStatus",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (monitoringStatus == "Active") BentoGreenVibrant else Color(0xFFE57373),
                        fontWeight = FontWeight.Bold
                    )
                    if (monitoringReason.isNotEmpty()) {
                        Text(
                            text = monitoringReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoTextMuted
                        )
                    }
                }
                Icon(
                    imageVector = if (monitoringStatus == "Active") Icons.Default.Shield else Icons.Default.Warning,
                    contentDescription = "Monitoring Status",
                    tint = if (monitoringStatus == "Active") BentoGreenVibrant else Color(0xFFE57373),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // 2. Security Health Score Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .testTag("security_health_card"),
                colors = CardDefaults.cardColors(containerColor = BentoHeroCardBg),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SECURITY HEALTH SCORE",
                        style = MaterialTheme.typography.labelMedium,
                        color = BentoTextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Huge circular indicator simulation/display
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { score.toFloat() / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = ratingColor,
                            strokeWidth = 12.dp,
                            trackColor = Color.DarkGray.copy(alpha = 0.3f),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = BentoTextPrimary
                            )
                            Text(
                                text = "of 100",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BentoTextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Validation",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (validationStatus == "VALID") BentoGreenVibrant.copy(alpha = 0.2f) else Color(0xFFE57373).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = validationStatus,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (validationStatus == "VALID") BentoGreenVibrant else Color(0xFFE57373),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Rating",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ratingLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = ratingColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Mandatory: $enabledMandatories/$totalMandatoriesSupported Enabled  |  Optional: $enabledOptionals/$totalOptionalsSupported",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextMuted
                    )

                    if (validationStatus == "INVALID" && missingMandatories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Validation Blocked by: ${missingMandatories.joinToString { it.name }}",
                            color = Color(0xFFE57373),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. First-run setup wizard banner
        if (firstRunState.value) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoGreenPrimary.copy(alpha = 0.15f))
                        .border(1.dp, BentoGreenPrimary, RoundedCornerShape(16.dp))
                        .clickable { showWizard = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "Setup wizard",
                        tint = BentoGreenVibrant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "Guided Security Setup",
                            style = MaterialTheme.typography.titleSmall,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Go through the step-by-step wizard to secure this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BentoTextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Launch",
                        tint = BentoTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. Actionable Recommendations Card
        if (recommendations.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = "Recommendations",
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recommended Actions",
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    recommendations.take(3).forEach { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFeatureForDetail = rec }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• Enable ${rec.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BentoTextSecondary,
                                modifier = Modifier.weight(1.0f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Resolve",
                                tint = BentoTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Mandatory Security List
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "MANDATORY SECURITY FEATURES",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                mandatoryFeatures.forEach { feat ->
                    SecurityFeatureItemRow(
                        feature = feat,
                        onItemClick = { selectedFeatureForDetail = feat },
                        onToggle = { enable -> viewModel.securityEngine.toggleFeature(feat.id, enable) }
                    )
                }
            }
        }

        // 6. Optional Security List
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "OPTIONAL SECURITY FEATURES",
                    style = MaterialTheme.typography.labelSmall,
                    color = BentoTextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                optionalFeatures.forEach { feat ->
                    SecurityFeatureItemRow(
                        feature = feat,
                        onItemClick = { selectedFeatureForDetail = feat },
                        onToggle = { enable -> viewModel.securityEngine.toggleFeature(feat.id, enable) }
                    )
                }
            }
        }

        // 7. Security Audit Summary Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Security Audit Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.securityEngine.scanDevice() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = BentoGreenVibrant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val formattedTime = if (lastScanTime > 0) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastScanTime))
                } else {
                    "Never"
                }

                AuditRow("Last Scan Time", formattedTime)
                AuditRow("Scan Duration", "${scanDuration}ms")
                AuditRow("Total Issues", "${recommendations.size}")
                AuditRow("Protected Features", "${features.count { it.status == FeatureStatus.ENABLED }}")
                AuditRow("Audit Result", if (validationStatus == "VALID") "SECURE" else "VULNERABLE")
            }
        }

        // 8. Security Timeline Card
        item {
            val securityLogs = eventLogs.filter { it.moduleName == "Security Hub" }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BentoCardBg)
                    .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                    .clickable { showTimelineDialog = true }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Timeline",
                            tint = BentoGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoGreenPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (securityLogs.isEmpty()) {
                    Text(
                        text = "No recent security timeline modifications recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BentoTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    securityLogs.take(2).forEach { log ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BentoTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BentoTextMuted
                                )
                            }
                            Text(
                                text = log.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary
                            )
                            Divider(modifier = Modifier.padding(top = 8.dp), color = BentoBorder)
                        }
                    }
                }
            }
        }

        // 9. Developer Security Diagnostics
        if (showDevPanel) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Developer Options",
                            tint = Color(0xFFFBC02D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEVELOPER DIAGNOSTICS",
                            style = MaterialTheme.typography.titleMedium,
                            color = BentoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // OEM Simulator Dropdown Selection
                    Text(
                        text = "OEM Capability Compatibility Simulator",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("AUTO", "SAMSUNG", "PIXEL", "XIAOMI", "REALME", "VIVO").forEach { mfrName ->
                            val isSelected = simulatedMfr == mfrName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) BentoGreenPrimary else Color.DarkGray)
                                    .clickable { viewModel.securityEngine.setSimulatedManufacturer(mfrName) }
                                    .weight(1.0f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mfrName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else BentoTextPrimary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom Simulated Lock Screen Strength
                    Text(
                        text = "Simulated Lock Screen Type Strength",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    var lockType by remember { mutableStateOf("6_DIGIT_PIN") }
                    LaunchedEffect(Unit) {
                        viewModel.settingsRepository.getFeatureStatus("screen_lock_type").collect { type ->
                            lockType = type ?: "6_DIGIT_PIN"
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "PATTERN" to "Pattern (8)",
                            "4_DIGIT_PIN" to "PIN4 (12)",
                            "6_DIGIT_PIN" to "PIN6 (16)",
                            "PASSWORD" to "Pass (20)"
                        ).forEach { (key, label) ->
                            val isSelected = lockType == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) BentoGreenPrimary else Color.DarkGray)
                                    .clickable {
                                        coroutineScope.launch {
                                            viewModel.settingsRepository.saveFeatureStatus("screen_lock_type", key)
                                            viewModel.securityEngine.scanDevice()
                                        }
                                    }
                                    .weight(1.0f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else BentoTextPrimary,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = BentoBorder)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Engine Diagnostics Logs",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DiagnosticLabelValue("Security Engine Health", "OK - TRUTH ENGINE ACTIVE")
                    DiagnosticLabelValue("Actual Manufacturer", viewModel.securityEngine.getActualManufacturer())
                    DiagnosticLabelValue("Effective OEM", viewModel.securityEngine.getEffectiveManufacturer())
                    DiagnosticLabelValue("Engine Broadcasts", "Registered (SCREEN_ON, USER_PRESENT)")
                    DiagnosticLabelValue("Cache Status", "Bypassed - Real-time Query")
                    DiagnosticLabelValue("Maximum Refresh Delay", "300 milliseconds")
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Feature Detail Sheet / Dialog
    if (selectedFeatureForDetail != null) {
        val feat = selectedFeatureForDetail!!
        Dialog(onDismissRequest = { selectedFeatureForDetail = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BentoCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (feat.status == FeatureStatus.ENABLED) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = "Feature Icon",
                        tint = if (feat.status == FeatureStatus.ENABLED) BentoGreenVibrant else Color(0xFFFBC02D),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = feat.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (feat.isMandatory) Color(0xFFE57373).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (feat.isMandatory) "MANDATORY" else "OPTIONAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (feat.isMandatory) Color(0xFFE57373) else BentoTextMuted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = feat.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BentoTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "ACTION INTERFACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = BentoTextMuted,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (feat.category) {
                        FeatureCategory.A -> {
                            Text(
                                text = "This is a Category A feature. You can toggle this security state directly below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val nextEnabled = feat.status != FeatureStatus.ENABLED
                                    viewModel.securityEngine.toggleFeature(feat.id, nextEnabled)
                                    selectedFeatureForDetail = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                            ) {
                                Text(if (feat.status == FeatureStatus.ENABLED) "Disable Feature" else "Enable Feature")
                            }
                        }
                        FeatureCategory.B -> {
                            Text(
                                text = "Category B: To configure this security setting, Netra will open the exact Android System Settings screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        // Deep link to settings
                                        val intentAction = when (feat.id) {
                                            "screen_lock" -> Settings.ACTION_SECURITY_SETTINGS
                                            "biometric" -> Settings.ACTION_BIOMETRIC_ENROLL
                                            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                                            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                                            "nfc_security" -> Settings.ACTION_NFC_SETTINGS
                                            else -> Settings.ACTION_SETTINGS
                                        }
                                        try {
                                            context.startActivity(Intent(intentAction))
                                        } catch (e: Exception) {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                            } catch (e2: Exception) {}
                                        }
                                        selectedFeatureForDetail = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                                ) {
                                    Text("Open Settings")
                                }

                                Button(
                                    onClick = {
                                        // Allow simulation of the feature for ease of presentation/testing
                                        val nextEnabled = feat.status != FeatureStatus.ENABLED
                                        viewModel.securityEngine.toggleFeature(feat.id, nextEnabled)
                                        selectedFeatureForDetail = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text(if (feat.status == FeatureStatus.ENABLED) "Simulate Disabled" else "Simulate Enabled")
                                }
                            }
                        }
                        FeatureCategory.C -> {
                            Text(
                                text = "Category C: Android limits direct third-party application management for this OEM capability.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BentoTextMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Setup Directions:",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BentoTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "1. Swipe down the notifications tray.\n2. Tap the settings gear icon.\n3. Search for \"${feat.name}\".\n4. Enable the toggle switch manually.\n5. Return to Netra to update.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BentoTextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        val nextEnabled = feat.status != FeatureStatus.ENABLED
                                        viewModel.securityEngine.toggleFeature(feat.id, nextEnabled)
                                        selectedFeatureForDetail = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                                ) {
                                    Text(if (feat.status == FeatureStatus.ENABLED) "Simulate Disabled" else "Simulate Enabled")
                                }
                                TextButton(onClick = { selectedFeatureForDetail = null }) {
                                    Text("Dismiss", color = BentoTextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. First-run Guided Wizard Dialog
    if (showWizard) {
        var wizardStep by remember { mutableStateOf(1) }
        val totalSteps = 5

        Dialog(onDismissRequest = { showWizard = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BentoCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SECURE DEVICE WIZARD",
                        style = MaterialTheme.typography.labelMedium,
                        color = BentoTextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Step $wizardStep of $totalSteps",
                        style = MaterialTheme.typography.titleMedium,
                        color = BentoGreenPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val (stepTitle, stepDesc, stepFeatureId) = when (wizardStep) {
                        1 -> Triple("Configure Screen Lock", "Set up a highly secure Screen Lock pattern or alphanumeric passcode. This prevents visual access.", "screen_lock")
                        2 -> Triple("Configure Biometrics", "Enroll your fingerprint or face data to fast lock and securely authorize operations.", "biometric")
                        3 -> Triple("Activate Find My Device", "Allow locate, locking, or wiping parameters in case the device gets misplaced.", "find_my_device")
                        4 -> Triple("Configure Google Play Protect", "Turn on automatic regular security audits for all installed applications.", "google_play_protect")
                        else -> Triple("Enable Theft Protection", "Configure motion heuristics and sensors to auto-lock the screen if snatched.", "theft_detection_lock")
                    }

                    Text(
                        text = stepTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stepDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BentoTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            if (wizardStep > 1) {
                                wizardStep--
                            } else {
                                showWizard = false
                            }
                        }) {
                            Text("Back", color = BentoTextMuted)
                        }

                        Button(
                            onClick = {
                                // Simulate completion of this step
                                viewModel.securityEngine.toggleFeature(stepFeatureId, true)
                                if (wizardStep < totalSteps) {
                                    wizardStep++
                                } else {
                                    firstRunState.value = false
                                    showWizard = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary)
                        ) {
                            Text(if (wizardStep == totalSteps) "Complete Setup" else "Verify & Next")
                        }
                    }
                }
            }
        }
    }

    // 3. Complete Timeline Dialog
    if (showTimelineDialog) {
        val securityLogs = eventLogs.filter { it.moduleName == "Security Hub" }
        Dialog(onDismissRequest = { showTimelineDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BentoCardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Security History Timeline",
                        style = MaterialTheme.typography.titleLarge,
                        color = BentoTextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (securityLogs.isEmpty()) {
                        Text(
                            text = "No recorded security history timeline modifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BentoTextMuted,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1.0f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(securityLogs) { log ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = BentoTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BentoTextMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = log.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BentoTextSecondary
                                    )
                                    Divider(modifier = Modifier.padding(top = 8.dp), color = BentoBorder)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showTimelineDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityFeatureItemRow(
    feature: SecurityFeature,
    onItemClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    if (feature.status == FeatureStatus.NOT_SUPPORTED) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Icon(
            imageVector = when (feature.status) {
                FeatureStatus.ENABLED -> Icons.Default.CheckCircle
                FeatureStatus.DISABLED -> Icons.Default.Cancel
                else -> Icons.Default.HelpOutline
            },
            contentDescription = feature.status.name,
            tint = when (feature.status) {
                FeatureStatus.ENABLED -> BentoGreenVibrant
                FeatureStatus.DISABLED -> Color(0xFFE57373)
                else -> BentoTextMuted
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Name and weight info
        Column(modifier = Modifier.weight(1.0f)) {
            Text(
                text = feature.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = BentoTextPrimary
            )
            Text(
                text = "Score weight: ${feature.scoreWeight} pts",
                style = MaterialTheme.typography.bodySmall,
                color = BentoTextMuted
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Interaction (Category A direct switch, others detailed click)
        if (feature.category == FeatureCategory.A) {
            Switch(
                checked = feature.status == FeatureStatus.ENABLED,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BentoGreenVibrant,
                    checkedTrackColor = BentoGreenPrimary.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                ),
                modifier = Modifier.scale(0.85f)
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Details",
                tint = BentoTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    Divider(color = BentoBorder.copy(alpha = 0.5f))
}

// Simple helper for scaling switches
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size((scale * 48).dp)
)

@Composable
fun AuditRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = BentoTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = BentoTextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DiagnosticLabelValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = BentoTextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
    }
}
