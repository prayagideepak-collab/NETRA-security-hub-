package com.example

import android.content.Context
import android.content.Intent
import android.os.BatteryManager as AndroidBatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.service.BatteryManager
import com.example.data.model.SensorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatteryManagerUnitTest {

    @Test
    fun `test battery manager default state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batteryManager = BatteryManager(context)

        val state = batteryManager.batteryState.value
        assertEquals(100, state.levelPercent)
        assertEquals(0f, state.temperatureC)
        assertEquals(0, state.voltageMv)
        assertFalse(state.isCharging)
    }

    @Test
    fun `test battery manager monitors intent changes correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batteryManager = BatteryManager(context)

        // Start monitoring
        batteryManager.startMonitoring()

        // Construct mock battery changed intent
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(AndroidBatteryManager.EXTRA_LEVEL, 85)
            putExtra(AndroidBatteryManager.EXTRA_SCALE, 100)
            putExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 375) // 37.5 C
            putExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 4100) // 4100 mV
            putExtra(AndroidBatteryManager.EXTRA_STATUS, AndroidBatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(AndroidBatteryManager.EXTRA_PLUGGED, AndroidBatteryManager.BATTERY_PLUGGED_AC)
        }

        // Trigger receiver directly to guarantee synchronous test execution
        batteryManager.getReceiver().onReceive(context, intent)

        val state = batteryManager.batteryState.value
        assertEquals(85, state.levelPercent)
        assertEquals(37.5f, state.temperatureC)
        assertEquals(4100, state.voltageMv)
        assertTrue(state.isCharging)
        assertEquals("AC Charger", state.plugType)

        // Convert to legacy raw sensor reading for fusion engine
        val rawReading = batteryManager.getAsRawReading()
        assertEquals("battery_telemetry", rawReading.sensorId)
        assertEquals(SensorCategory.POWER, rawReading.category)
        assertEquals(37.5f, rawReading.values[0])
        assertEquals(85.0f, rawReading.values[1])
        assertEquals(4100.0f, rawReading.values[2])

        // Stop monitoring
        batteryManager.stopMonitoring()
    }
}
