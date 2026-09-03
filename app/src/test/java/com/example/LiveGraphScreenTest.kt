package com.example

import android.hardware.Sensor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.screens.LiveGraphScreen
import com.example.ui.screens.LiveGraphState
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCategory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LiveGraphScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun liveGraphScreen_waitingState_whenBufferEmpty() {
        composeTestRule.setContent {
            LiveGraphScreen(
                state = LiveGraphState(buffer = emptyList(), isPaused = false),
                onSelectSensor = {},
                onTogglePause = {},
                onStartSession = {},
                onStopSession = {}
            )
        }

        composeTestRule.onNodeWithText("WAITING").assertIsDisplayed()
        composeTestRule.onNodeWithText("WAITING FOR RAW TELEMETRY DATA...").assertIsDisplayed()
    }

    @Test
    fun liveGraphScreen_liveState_whenBufferNotEmpty() {
        val reading = RawSensorReading(
            sensorId = "test",
            name = "Test Sensor",
            category = SensorCategory.THERMAL,
            values = floatArrayOf(9.82f, 0f, 0f),
            unit = "m/s²"
        )
        composeTestRule.setContent {
            LiveGraphScreen(
                state = LiveGraphState(buffer = listOf(reading), isPaused = false),
                onSelectSensor = {},
                onTogglePause = {},
                onStartSession = {},
                onStopSession = {}
            )
        }

        composeTestRule.onNodeWithText("LIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("REGISTERED: TEST SENSOR").assertIsDisplayed()
        composeTestRule.onNodeWithText("9.820").assertIsDisplayed()
    }

    @Test
    fun liveGraphScreen_pausedState_whenPaused() {
        val reading = RawSensorReading(
            sensorId = "test",
            name = "Test Sensor",
            category = SensorCategory.THERMAL,
            values = floatArrayOf(9.82f),
            unit = "m/s²"
        )
        composeTestRule.setContent {
            LiveGraphScreen(
                state = LiveGraphState(buffer = listOf(reading), isPaused = true),
                onSelectSensor = {},
                onTogglePause = {},
                onStartSession = {},
                onStopSession = {}
            )
        }

        composeTestRule.onNodeWithText("PAUSED").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Resume").assertIsDisplayed()
    }
}
