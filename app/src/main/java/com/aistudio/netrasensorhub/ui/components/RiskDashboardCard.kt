package com.aistudio.netrasensorhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.netrasensorhub.data.risk.RiskLevel
import com.aistudio.netrasensorhub.data.risk.RiskScenarioResult

@Composable
fun RiskDashboardCard(
    overheatingResult: RiskScenarioResult,
    impactResult: RiskScenarioResult,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val cardBg = if (isDark) Color(0xFF0D1117) else Color(0xFFF6F8FA)
    val borderColor = if (isDark) Color(0xFF30363D) else Color(0xFFD0D7DE)
    val textColor = if (isDark) Color(0xFFC9D1D9) else Color(0xFF24292F)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Risk Analysis Engine",
                        tint = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "RISK ANALYSIS & SCENARIOS ENGINE",
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF161B22) else Color(0xFFEAEEF2))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Rule Engine Active",
                        color = if (isDark) Color(0xFF3FB950) else Color(0xFF1A7F37),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Scenario 1: Device Overheating Under Load
            RiskScenarioItem(
                title = overheatingResult.scenarioName,
                icon = Icons.Default.Warning,
                result = overheatingResult,
                isDark = isDark
            )

            HorizontalDivider(color = borderColor, thickness = 1.dp)

            // Scenario 2: Sudden Impact or Drop
            RiskScenarioItem(
                title = impactResult.scenarioName,
                icon = Icons.Default.Info,
                result = impactResult,
                isDark = isDark
            )
        }
    }
}

@Composable
fun RiskScenarioItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    result: RiskScenarioResult,
    isDark: Boolean
) {
    val isAvailable = result.isDataAvailable

    val riskColor = if (!isAvailable) {
        if (isDark) Color(0xFF8B949E) else Color(0xFF57606A)
    } else when (result.riskLevel) {
        RiskLevel.CRITICAL -> Color(0xFFDA3633)
        RiskLevel.HIGH -> Color(0xFFD29922)
        RiskLevel.MODERATE -> Color(0xFFDB6D28)
        RiskLevel.LOW -> Color(0xFF3FB950)
        RiskLevel.NORMAL -> Color(0xFF238636)
    }

    val riskBg = if (!isAvailable) {
        if (isDark) Color(0xFF21262D) else Color(0xFFEAEEF2)
    } else when (result.riskLevel) {
        RiskLevel.CRITICAL -> if (isDark) Color(0xFF490202) else Color(0xFFFFEBE9)
        RiskLevel.HIGH -> if (isDark) Color(0xFF3B2300) else Color(0xFFFFF8C5)
        RiskLevel.MODERATE -> if (isDark) Color(0xFF381D00) else Color(0xFFFFCC99)
        else -> if (isDark) Color(0xFF033A16) else Color(0xFFDAFBE1)
    }

    val textColor = if (isDark) Color(0xFFC9D1D9) else Color(0xFF24292F)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = icon, contentDescription = title, tint = riskColor, modifier = Modifier.size(18.dp))
                Text(text = title, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(riskBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isAvailable) result.riskLevel.name else "DATA UNAVAILABLE",
                    color = riskColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Text(
            text = result.description,
            color = if (isDark) Color(0xFF8B949E) else Color(0xFF57606A),
            fontSize = 12.sp
        )

        if (result.recommendations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                result.recommendations.forEach { rec ->
                    Text(
                        text = "• $rec",
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
