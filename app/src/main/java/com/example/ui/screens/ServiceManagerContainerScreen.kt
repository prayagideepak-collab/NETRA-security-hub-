package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.*

enum class ServiceManagerSubTab(val title: String, val icon: ImageVector) {
    SERVICE_CONTROL("Service Control", Icons.Default.Hub),
    RUNTIME_HEALTH("Runtime & Health", Icons.Default.HealthAndSafety)
}

@Composable
fun ServiceManagerContainerScreen(
    viewModel: MainViewModel,
    initialSubTab: ServiceManagerSubTab = ServiceManagerSubTab.SERVICE_CONTROL
) {
    var selectedSubTab by remember { mutableStateOf(initialSubTab) }

    Column(modifier = Modifier.fillMaxSize().background(BentoBackground)) {
        // Breadcrumb Navigation Header
        Surface(
            color = BentoCardBg,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Service Manager", style = MaterialTheme.typography.labelMedium, color = BentoGreenPrimary)
                    Text("  >  ", style = MaterialTheme.typography.labelMedium, color = BentoTextMuted)
                    Text(selectedSubTab.title, style = MaterialTheme.typography.titleMedium, color = BentoTextPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sub-section Navigation Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceManagerSubTab.values().forEach { tab ->
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
                ServiceManagerSubTab.SERVICE_CONTROL -> SecurityHubScreen(
                    viewModel = viewModel
                )
                ServiceManagerSubTab.RUNTIME_HEALTH -> RuntimeHealthScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun RuntimeHealthScreen(viewModel: MainViewModel) {
    val healthScore by viewModel.idhmseEngine.systemHealthScore.collectAsState()
    val moduleHealth by viewModel.idhmseEngine.moduleHealthReports.collectAsState()
    val runtimeStatus by viewModel.ibrsEngine.runtimeStatus.collectAsState()

    val moduleHealthList = remember(moduleHealth) { moduleHealth.values.toList() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("runtime_health_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BentoCardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Overall System Health Score", style = MaterialTheme.typography.titleMedium, color = BentoTextSecondary)
                    Text("$healthScore / 100", style = MaterialTheme.typography.displaySmall, color = BentoGreenPrimary)
                    Text("IBRS² & IDHMSE Self-Healing Engine Active", style = MaterialTheme.typography.bodySmall, color = BentoTextMuted)
                }
            }
        }

        item {
            Text("Registered Background Services & Health", style = MaterialTheme.typography.titleMedium, color = BentoTextPrimary)
        }

        if (moduleHealthList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BentoCardBg)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No background services registered yet.", color = BentoTextMuted, fontSize = 14.sp)
                    }
                }
            }
        } else {
            items(moduleHealthList) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BentoHeroCardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(report.moduleName, style = MaterialTheme.typography.bodyLarge, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                            Text("Status: ${report.status.name} | Score: ${report.healthScore}", style = MaterialTheme.typography.bodySmall, color = BentoTextMuted)
                        }
                        Text("${report.healthScore}%", color = BentoGreenPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
