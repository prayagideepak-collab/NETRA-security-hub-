package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.data.model.RiskAnalysisResult
import com.example.data.model.SensorFusionState
import com.example.ui.theme.BentoBackground
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
fun SensorFusionScreen(
    fusionState: SensorFusionState,
    riskAnalysis: RiskAnalysisResult,
    onTriggerAiDiagnostic: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("sensor_fusion_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header Title
        item {
            Column {
                Text(
                    text = "MULTI-SENSOR FUSION PIPELINE",
                    color = BentoGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Event Correlation & AI Safety Engine",
                    color = BentoTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // Fusion Matrix Items Title
        item {
            Text(
                text = "CORRELATION MATRIX RULES",
                color = BentoTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RuleMatrixCard(
                    ruleName = "Pocket Enclosure Rule",
                    formula = "Proximity (<2cm) ∩ Ambient Light (<10Lux)",
                    confidence = fusionState.pocketConfidence,
                    isTriggered = fusionState.isPocketConfirmed
                )

                RuleMatrixCard(
                    ruleName = "Thermal Overheat Rule",
                    formula = "Battery Temp (>45°C) ∪ Thermal HAL Status",
                    confidence = if (fusionState.isHighHeatConfirmed) 0.95f else 0.05f,
                    isTriggered = fusionState.isHighHeatConfirmed
                )

                RuleMatrixCard(
                    ruleName = "Impact Force Spike Rule",
                    formula = "Accelerometer Magnitude (>2.2G) ∩ Gyroscope (>3.0rad/s)",
                    confidence = if (fusionState.isImpactConfirmed) 0.99f else 0.02f,
                    isTriggered = fusionState.isImpactConfirmed
                )

                RuleMatrixCard(
                    ruleName = "Charging Overheat Anomaly Rule",
                    formula = "Power Connected ∩ (Temp >45°C ∪ Voltage >4400mV)",
                    confidence = if (fusionState.isChargingRiskConfirmed) 0.90f else 0.01f,
                    isTriggered = fusionState.isChargingRiskConfirmed
                )

                RuleMatrixCard(
                    ruleName = "Magnetic Field Anomaly Rule",
                    formula = "Magnetometer Field Magnitude (>100 µT)",
                    confidence = if (fusionState.isMagneticHazardConfirmed) 0.95f else 0.01f,
                    isTriggered = fusionState.isMagneticHazardConfirmed
                )
            }
        }

        // AI Deep Scan Action Panel Bento Card
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
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GEMINI AI DEEP REASONING",
                            color = BentoGreenPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = riskAnalysis.explanation,
                        color = BentoTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onTriggerAiDiagnostic,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary, contentColor = Color.White),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().testTag("trigger_ai_scan_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Live Rule Diagnostic Scan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun RuleMatrixCard(
    ruleName: String,
    formula: String,
    confidence: Float,
    isTriggered: Boolean
) {
    val barColor = if (isTriggered) BentoGreenPrimary else BentoGreenVibrant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BentoCardBg)
            .border(1.dp, if (isTriggered) BentoGreenPrimary.copy(alpha = 0.5f) else BentoBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ruleName,
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoHeroCardBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isTriggered) "CONFIRMED" else "NOMINAL",
                        color = BentoGreenPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formula,
                color = BentoTextMuted,
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { confidence.coerceIn(0f, 1f) },
                    color = barColor,
                    trackColor = BentoHeroCardBg,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${(confidence * 100).toInt()}%",
                    color = BentoTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
