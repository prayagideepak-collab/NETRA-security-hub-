package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import com.example.data.sensor.SensorDiagnosticStatus
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun SensorDiagnosticsCard(
    diagnostics: List<SensorDiagnosticStatus>,
    modifier: Modifier = Modifier
) {
    val faultyCount = diagnostics.count { it.isFaulty }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BentoCardBg)
            .border(1.dp, if (faultyCount > 0) BentoRed.copy(alpha = 0.5f) else BentoBorder, RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("sensor_diagnostics_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (faultyCount > 0) BentoRed.copy(alpha = 0.2f) else BentoHeroCardBg)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (faultyCount > 0) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (faultyCount > 0) BentoRed else BentoGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SENSOR HEALTH & DIAGNOSTICS",
                            color = BentoGreenPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = if (faultyCount > 0) "$faultyCount Sensor(s) Flagged Faulty/Stale" else "All Sensors Nominal & Verified",
                            color = BentoTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                diagnostics.forEach { diag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHeroCardBg)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (diag.isFaulty) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (diag.isFaulty) BentoAmber else BentoGreenPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = diag.sensorName,
                                color = BentoTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = diag.statusMessage,
                            color = if (diag.isFaulty) BentoRed else BentoTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
