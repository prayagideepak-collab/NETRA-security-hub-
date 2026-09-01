package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.pipeline.BatteryAnomalyDetector
import com.example.data.pipeline.DataFreshness
import com.example.data.pipeline.DeviceDataAdapter
import com.example.data.pipeline.DeviceDataSyncManager
import com.example.data.pipeline.ValidatedMetric
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataPipelineAndSyncUnitTest {

    @Test
    fun `test DataFreshness classifications and ValidatedMetric`() {
        val now = System.currentTimeMillis()
        val freshMetric = ValidatedMetric.fresh(42, "TestSensor", now)
        assertTrue(freshMetric.isAvailable)
        assertEquals(DataFreshness.FRESH, freshMetric.freshness)
        assertEquals("VERIFIED_HARDWARE", freshMetric.quality)

        val staleMetric = ValidatedMetric.stale(42, "TestSensor", now - 600_000L)
        assertTrue(staleMetric.isAvailable)
        assertEquals(DataFreshness.STALE, staleMetric.freshness)
        assertEquals("CACHED_LAST_KNOWN", staleMetric.quality)

        val unavailableMetric = ValidatedMetric.unavailable<Int>("TestSensor", "Hardware not supported")
        assertFalse(unavailableMetric.isAvailable)
        assertEquals(DataFreshness.UNAVAILABLE, unavailableMetric.freshness)
        assertNull(unavailableMetric.value)
    }

    @Test
    fun `test Battery Anomaly Jump Detection`() {
        val detector = BatteryAnomalyDetector()
        val t0 = 1000000000L

        // Initial normal baseline at 80%
        val event0 = detector.evaluateReading(80, true, "AC Charger", t0)
        assertNull("Initial reading should establish baseline without anomaly", event0)

        // Normal gradual increment: 80% -> 81% after 60s
        val event1 = detector.evaluateReading(81, true, "AC Charger", t0 + 60_000L)
        assertNull("Normal 1% charging increase is not an anomaly", event1)

        // Implausible jump: 81% -> 90% in 2 seconds without state change
        val anomaly = detector.evaluateReading(90, true, "AC Charger", t0 + 62_000L)
        assertNotNull("Rapid 9% jump in 2 seconds must trigger anomaly event", anomaly)
        assertEquals(81, anomaly?.previousPercentage)
        assertEquals(90, anomaly?.currentPercentage)
        assertEquals(9, anomaly?.deltaPercentage)
    }

    @Test
    fun `test Charging and Discharging Milestones`() {
        val detector = BatteryAnomalyDetector()
        val now = System.currentTimeMillis()

        // Test non-milestone value
        val nonMilestone = detector.checkMilestone(43, true, now)
        assertNull(nonMilestone)

        // Test charging milestone 80%
        val m80Charging = detector.checkMilestone(80, true, now)
        assertNotNull(m80Charging)
        assertEquals(80, m80Charging?.milestonePercent)
        assertTrue(m80Charging?.isCharging == true)

        // Same milestone again should be deduplicated
        val m80Dup = detector.checkMilestone(80, true, now + 1000L)
        assertNull("Duplicate milestone in same state should be suppressed", m80Dup)

        // Test 99% charging milestone (valid for charging)
        val m99Charging = detector.checkMilestone(99, true, now + 2000L)
        assertNotNull("99% is valid for charging milestone", m99Charging)

        // Reset detector for discharge testing
        detector.reset()

        // Test 99% discharge milestone (MUST NOT TRIGGER on discharge)
        val m99Discharge = detector.checkMilestone(99, false, now + 3000L)
        assertNull("99% must NOT trigger during discharge", m99Discharge)

        // Test 95% discharge milestone (final discharge milestone)
        val m95Discharge = detector.checkMilestone(95, false, now + 4000L)
        assertNotNull("95% is the valid final discharge milestone", m95Discharge)
    }

    @Test
    fun `test DeviceDataSyncManager startup and periodic sync`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val syncManager = DeviceDataSyncManager(context)

        val result = syncManager.performStartupSync()
        assertTrue("Startup sync must succeed", result.isSuccess)
        assertNotNull("Last sync timestamp must be recorded", syncManager.lastSyncTimestamp.value)

        val periodicResult = syncManager.performPeriodicSync()
        assertTrue("Periodic sync must succeed", periodicResult.isSuccess)
    }
}
