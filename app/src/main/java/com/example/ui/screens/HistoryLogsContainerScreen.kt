package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SafetyEventEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.*

enum class HistoryLogsSubTab(val title: String, val icon: ImageVector) {
    ANALYTICS("Analytics", Icons.Default.Analytics),
    REPORTS_EXPORT("Export & Reports", Icons.Default.Assessment),
    UNIFIED_LOG("Unified Log", Icons.Default.ListAlt)
}

@Composable
fun HistoryLogsContainerScreen(
    eventLogs: List<SafetyEventEntity>,
    viewModel: MainViewModel,
    initialSubTab: HistoryLogsSubTab = HistoryLogsSubTab.ANALYTICS
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
                    Text("History & Logs", style = MaterialTheme.typography.labelSmall, color = BentoGreenPrimary)
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
                    HistoryLogsSubTab.values().forEach { tab ->
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

        // Sub-screen Content
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (selectedSubTab) {
                HistoryLogsSubTab.ANALYTICS -> AnalyticsScreen(
                    eventLogs = eventLogs
                )
                HistoryLogsSubTab.REPORTS_EXPORT -> ReportsScreen(
                    eventLogs = eventLogs,
                    onTriggerTestEvent = { title, risk, score, desc ->
                        viewModel.triggerTestEvent(title, risk, score, desc)
                    },
                    onClearLogs = { viewModel.clearLogs() },
                    onExportTxt = { date, callback -> viewModel.exportLogsToTxtForDate(date, callback) },
                    onExportCsv = { date, callback -> viewModel.exportLogsToCsvForDate(date, callback) },
                    onExportJson = { date, callback -> viewModel.exportLogsToJsonForDate(date, callback) },
                    onSync = { isExtended -> viewModel.runManualSync(isExtended) },
                    onShareFile = {}
                )
                HistoryLogsSubTab.UNIFIED_LOG -> HistoryScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
