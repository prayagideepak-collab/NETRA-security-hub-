package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SafetyRiskLevel
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun RiskMeterGauge(
    score: Int, // Current risk score (0 to 100)
    level: SafetyRiskLevel,
    isAiPowered: Boolean,
    modifier: Modifier = Modifier
) {
    // Safety percentage is inverted: 100% is fully safe (score = 0)
    val safetyScore = (100 - score).coerceIn(0, 100)

    val animatedProgress by animateFloatAsState(
        targetValue = (safetyScore / 100f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "safety_score_anim"
    )

    // Dynamic color reacts from Green (100% safe) -> Deep Red (0% safe)
    val safetyColor = when {
        safetyScore >= 75 -> BentoGreenVibrant
        safetyScore >= 50 -> BentoAmber
        safetyScore >= 25 -> Color(0xFFFF9100) // Orange Warning
        else -> BentoRed // Emergency Deep Red
    }

    val safetyLabel = when {
        safetyScore >= 75 -> "Fully Secure"
        safetyScore >= 50 -> "Elevated Attention"
        safetyScore >= 25 -> "High Risk Warning"
        else -> "Critical Emergency"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(BentoHeroCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(32.dp))
            .padding(22.dp)
            .testTag("risk_meter_gauge")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Row: Netra Safety Index & Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Netra Safety Index",
                        color = BentoTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = safetyLabel,
                        color = BentoTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BentoBackground)
                        .border(1.dp, safetyColor.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TRUTH ENGINE VALIDATED",
                        color = BentoGreenPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gauge Center & Meter Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        // Draw outer background arc
                        drawArc(
                            color = BentoBackground,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        // Draw animated safety progress arc with dynamic color
                        drawArc(
                            color = safetyColor,
                            startAngle = 135f,
                            sweepAngle = 270f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$safetyScore%",
                            color = BentoTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "SAFETY",
                            color = BentoTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ABSOLUTE TRUTH ENGINE",
                        color = BentoGreenPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (safetyScore >= 75) "All sensors nominal. Hardware streams operating in baseline limits." else "Sensor telemetry variance detected. Review active risk factors.",
                        color = BentoTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Track Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(BentoBackground.copy(alpha = 0.7f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(safetyColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (safetyScore >= 75) "No Threats Detected" else "Telemetry Anomalies",
                    color = BentoTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Verifying Hardware...",
                    color = BentoGreenPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
