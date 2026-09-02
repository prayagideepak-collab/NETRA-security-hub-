import re

with open('app/src/main/java/com/example/ui/screens/LiveGraphScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.example.ui.components.SensorWaveformChart', '')

# Replace SensorWaveformChart call
pattern = r'                        // We pass the buffer to our chart to draw\n                        SensorWaveformChart\(\n                            buffer = state.buffer,\n                            unit = latestReading.unit\n                        \)'
replacement = '''                        // We pass the buffer to our chart to draw
                        LiveGraphWaveformChart(
                            buffer = state.buffer,
                            unit = latestReading.unit
                        )'''
content = re.sub(pattern, replacement, content)

# Append LiveGraphWaveformChart
content += '''
@Composable
fun LiveGraphWaveformChart(
    buffer: List<RawSensorReading>,
    unit: String,
    modifier: Modifier = Modifier
) {
    if (buffer.isEmpty()) return
    
    val lineColors = androidx.compose.runtime.remember { listOf(BentoGreenPrimary, BentoGreenVibrant, BentoAmber, BentoRed) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val width = size.width
            val height = size.height

            // 1. Grid Lines
            val gridStepX = width / 6
            for (i in 1..5) {
                drawLine(
                    color = BentoBorder,
                    start = androidx.compose.ui.geometry.Offset(gridStepX * i, 0f),
                    end = androidx.compose.ui.geometry.Offset(gridStepX * i, height),
                    strokeWidth = 1f
                )
            }
            drawLine(
                color = BentoBorder,
                start = androidx.compose.ui.geometry.Offset(0f, height / 2),
                end = androidx.compose.ui.geometry.Offset(width, height / 2),
                strokeWidth = 1f
            )

            // 2. Render Rolling Buffer Waveform
            if (buffer.isNotEmpty()) {
                val pointsCount = buffer.size
                val stepX = if (pointsCount > 1) width / (pointsCount - 1) else width
                
                for (axis in 0 until 3) {
                    val color = lineColors.getOrElse(axis) { BentoGreenPrimary }
                    val pathPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
                    for (i in buffer.indices) {
                        val frameValues = buffer[i].values
                        if (axis < frameValues.size) {
                            val v = frameValues[axis]
                            val centerY = height / 2
                            val mappedY = (centerY - (v * 2.5f)).coerceIn(4f, height - 4f)
                            val posX = i * stepX
                            pathPoints.add(androidx.compose.ui.geometry.Offset(posX, mappedY))
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
'''
with open('app/src/main/java/com/example/ui/screens/LiveGraphScreen.kt', 'w') as f:
    f.write(content)

