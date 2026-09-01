package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.WatchdogModuleState
import com.example.data.model.RawSensorReading
import com.example.data.model.RiskAnalysisResult
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorFusionState
import com.example.ui.MainViewModel
import com.example.ui.theme.*

import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Security

enum class SensorCenterSubTab(val title: String, val icon: ImageVector) {
    LIVE_SENSORS("Live Telemetry", Icons.Default.Sensors),
    MOTION_INTELLIGENCE("Motion & Activity", Icons.Default.DirectionsWalk),
    TAMPER_SHIELD("Tamper Shield", Icons.Default.Security),
    AI_FUSION("AI Fusion", Icons.Default.Hub),
    HEALTH_CENTER("Health Center", Icons.Default.HealthAndSafety),
    DIAGNOSTICS("Diagnostics", Icons.Default.BugReport)
}

@Composable
fun SensorCenterContainerScreen(
    capabilities: List<SensorCapabilityInfo>,
    liveReadings: Map<String, RawSensorReading>,
    watchdogStates: Map<String, WatchdogModuleState>,
    fusionState: SensorFusionState,
    riskAnalysis: RiskAnalysisResult,
    viewModel: MainViewModel,
    initialSubTab: SensorCenterSubTab = SensorCenterSubTab.LIVE_SENSORS
) {
    var selectedSubTab by remember { mutableStateOf(initialSubTab) }

    Column(modifier = Modifier.fillMaxSize().background(BentoBackground)) {
        // Breadcrumb Navigation Header
        Surface(
            color = BentoCardBg,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sensor Center", style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary)
                    Text("  >  ", style = MaterialTheme.typography.labelSmall, color = BentoTextMuted)
                    Text(selectedSubTab.title, style = MaterialTheme.typography.titleSmall, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Sub-section Navigation Chips (Horizontally Scrollable)
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensorCenterSubTab.values().forEach { tab ->
                        FilterChip(
                            selected = (selectedSubTab == tab),
                            onClick = { selectedSubTab = tab },
                            label = { Text(tab.title, fontSize = 12.sp) },
                            leadingIcon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BentoGreenPrimary,
                                selectedLabelColor = BentoBackground,
                                containerColor = BentoHeroCardBg,
                                labelColor = BentoTextSecondary
                            )
                        )
                    }
                }
            }
        }

        Divider(color = BentoBorder)

        // Sub-screen Lazy Content
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (selectedSubTab) {
                SensorCenterSubTab.LIVE_SENSORS -> SensorsScreen(
                    capabilities = capabilities,
                    liveReadings = liveReadings,
                    watchdogStates = watchdogStates
                )
                SensorCenterSubTab.MOTION_INTELLIGENCE -> MotionIntelligenceScreen(
                    viewModel = viewModel
                )
                SensorCenterSubTab.TAMPER_SHIELD -> SensorDashboardScreen(
                    viewModel = viewModel
                )
                SensorCenterSubTab.AI_FUSION -> SensorFusionScreen(
                    fusionState = fusionState,
                    riskAnalysis = riskAnalysis,
                    onTriggerAiDiagnostic = { viewModel.refreshAiAnalysis() }
                )
                SensorCenterSubTab.HEALTH_CENTER -> AiFusionHealthCenterScreen(
                    viewModel = viewModel,
                    capabilities = capabilities
                )
                SensorCenterSubTab.DIAGNOSTICS -> SensorDiagnosticsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
