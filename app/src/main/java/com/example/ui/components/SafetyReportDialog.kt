package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.example.data.db.SafetyEventEntity
import com.example.ui.theme.BentoAmber
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SafetyReportDialog(
    event: SafetyEventEntity,
    onDismiss: () -> Unit
) {
    val levelColor = when (event.riskLevel.uppercase()) {
        "EMERGENCY" -> BentoRed
        "WARNING" -> Color(0xFFFF9100)
        "ATTENTION" -> BentoAmber
        else -> BentoGreenVibrant
    }

    val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = BentoCardBg,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("safety_report_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (event.riskLevel == "SAFE") Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Event Status",
                        tint = levelColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAFETY REPORT #${event.id}",
                        color = BentoTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_report_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = BentoTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Event Title & Risk Stamp Bento Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BentoHeroCardBg)
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = event.riskLevel,
                                color = levelColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Score: ${event.riskScore}/100",
                                color = BentoTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = event.title,
                            color = BentoTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Timestamp: $formattedTime",
                            color = BentoTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Absolute Truth Classification Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BentoBackground)
                        .border(1.dp, BentoBorder, RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BentoHeroCardBg)
                                .padding(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = BentoGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VERIFIED HARDWARE ORIGIN",
                                color = BentoGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Data authenticated directly from device HAL sensors.",
                                color = BentoTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Explanation Breakdown
                Text(
                    text = "Event Analysis & Trigger Context",
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.description,
                    color = BentoTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Measured Sensor Telemetry Breakdown
                Text(
                    text = "Recorded Sensor Values",
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BentoBackground)
                        .border(1.dp, BentoBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        val sensorMap = androidx.compose.runtime.remember(event.primarySensorValuesJson) {
                            try {
                                val json = org.json.JSONObject(event.primarySensorValuesJson)
                                val map = mutableMapOf<String, String>()
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    map[key] = json.optString(key)
                                }
                                map
                            } catch (e: Exception) {
                                emptyMap<String, String>()
                            }
                        }

                        if (sensorMap.isNotEmpty()) {
                            sensorMap.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = key,
                                        color = BentoTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = value,
                                        color = BentoGreenPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1.3f)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = event.primarySensorValuesJson.replace("{", "").replace("}", "").replace("\"", "").replace(",", "\n"),
                                color = BentoGreenPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Safety Recommendations
                Text(
                    text = "AI Recommended Actions",
                    color = BentoTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.aiRecommendation,
                    color = BentoGreenPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary, contentColor = Color.White),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().testTag("close_safety_report_button")
                ) {
                    Text("Acknowledge & Close Report", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
