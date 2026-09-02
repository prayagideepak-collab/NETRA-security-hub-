import re

with open('app/src/main/java/com/example/ui/components/SensorWaveformChart.kt', 'r') as f:
    content = f.read()

# Change it back to original SensorWaveformChart
content = """package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextSecondary

@Composable
fun SensorWaveformChart(
    values: FloatArray,
    unit: String,
    modifier: Modifier = Modifier
) {
    // Volatile in-memory rolling window buffer (RAM-only, zero DB writes for waveform ticks)
    val rollingBuffer = remember { mutableStateListOf<FloatArray>() }

    DisposableEffect(values) {
        if (values.isNotEmpty()) {
            if (rollingBuffer.size >= 25) {
                rollingBuffer.removeAt(0)
            }
            rollingBuffer.add(values)
        }
        onDispose {
            // Foreground active only: clear buffer on disposal / background transition
            rollingBuffer.clear()
        }
    }

    val currentPrimaryVal = values.firstOrNull() ?: 0f
    
    val allRecordedValues = rollingBuffer.flatMap { it.toList() }
    val minVal = if (allRecordedValues.isNotEmpty()) allRecordedValues.minOrNull() ?: currentPrimaryVal else currentPrimaryVal
    val maxVal = if (allRecordedValues.isNotEmpty()) allRecordedValues.maxOrNull() ?: currentPrimaryVal else currentPrimaryVal

    val trendIndicator = if (rollingBuffer.size >= 2) {
        val prev = rollingBuffer[rollingBuffer.size - 2].firstOrNull() ?: currentPrimaryVal
        when {
            currentPrimaryVal > prev -> "↗ RISING"
            currentPrimaryVal < prev -> "↘ FALLING"
            else -> "→ STABLE"
        }
    } else "• LIVE"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header stats: Current | Min | Max | Trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT: %.2f $unit".format(currentPrimaryVal),
                        color = BentoGreenPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Min: %.2f | Max: %.2f".format(minVal, maxVal),
                        color = BentoTextMuted,
                        fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BentoCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$trendIndicator (RAM Buffer)",
                        color = BentoTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val lineColors = remember { listOf(BentoGreenPrimary, BentoGreenVibrant, BentoAmber, BentoRed) }

            // Live Canvas Waveform Renderer (Foreground active only)
            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                val width = size.width
                val height = size.height

                // 1. Grid Lines
                val gridStepX = width / 6
                for (i in 1..5) {
                    drawLine(
                        color = BentoBorder,
                        start = Offset(gridStepX * i, 0f),
                        end = Offset(gridStepX * i, height),
                        strokeWidth = 1f
                    )
                }
                drawLine(
                    color = BentoBorder,
                    start = Offset(0f, height / 2),
                    end = Offset(width, height / 2),
                    strokeWidth = 1f
                )

                // 2. Render Rolling Buffer Waveform
                if (rollingBuffer.isNotEmpty()) {
                    val pointsCount = rollingBuffer.size
                    val stepX = if (pointsCount > 1) width / (pointsCount - 1) else width
                    
                    for (axis in 0 until 3) {
                        val color = lineColors.getOrElse(axis) { BentoGreenPrimary }
                        val pathPoints = mutableListOf<Offset>()
                        for (i in rollingBuffer.indices) {
                            val frameValues = rollingBuffer[i]
                            if (axis < frameValues.size) {
                                val v = frameValues[axis]
                                val centerY = height / 2
                                val mappedY = (centerY - (v * 2.5f)).coerceIn(4f, height - 4f)
                                val posX = i * stepX
                                pathPoints.add(Offset(posX, mappedY))
                            }
                        }

                        if (pathPoints.size >= 2) {
                            for (p in 0 until pathPoints.size - 1) {
                                drawLine(
                                    color = color,
                                    start = pathPoints[p],
                                    end = pathPoints[p + 1],
                                    strokeWidth = 2.5f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

with open('app/src/main/java/com/example/ui/components/SensorWaveformChart.kt', 'w') as f:
    f.write(content)

