package com.example.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.Row
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class NetraBatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempInt = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempC = tempInt / 10f
        val isCharging = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        val isWarning = batteryTempC >= 40.0f

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF1F1F1F)))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = "Netra Battery Thermal",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    
                    Text(
                        text = if (batteryTempC > 0f) String.format("%.1f°C", batteryTempC) else "N/A",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    
                    Row(
                        modifier = GlanceModifier
                            .background(ColorProvider(if (isWarning) Color(0xFFD32F2F) else Color(0xFF388E3C)))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                    ) {
                        Text(
                            text = if (isWarning) "WARNING: HIGH TEMP" else "STATUS: SAFE",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = if (isCharging) "Charging" else "Discharging",
                        style = TextStyle(
                            color = ColorProvider(Color.LightGray),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
