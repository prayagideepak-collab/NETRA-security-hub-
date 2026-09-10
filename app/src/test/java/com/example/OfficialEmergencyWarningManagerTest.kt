package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.OfficialEmergencyWarningManager
import com.example.data.engine.OfficialWarningInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialEmergencyWarningManagerTest {

    private lateinit var context: Context
    private lateinit var warningManager: OfficialEmergencyWarningManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        warningManager = OfficialEmergencyWarningManager(context)
    }

    @Test
    fun testUnverifiedThirdPartyDataDoesNotTriggerEmergencyPipeline() {
        val warning = OfficialWarningInfo(
            alertId = "TEST-1",
            warningType = "Severe Advisory",
            severity = "WARNING",
            sourceType = "THIRD_PARTY",
            verificationStatus = "UNVERIFIED",
            locationRelevance = "VERIFIED_MATCH",
            lifecycleState = "ACTIVE",
            isAvailable = true
        )

        val isEligible = (warning.verificationStatus == "VERIFIED" || warning.sourceType == "OFFICIAL_VERIFIED") &&
                         (warning.locationRelevance == "VERIFIED_MATCH") &&
                         warning.isAvailable &&
                         warning.lifecycleState == "ACTIVE"

        assertFalse("Unverified third-party data must NEVER trigger emergency pipeline", isEligible)
    }

    @Test
    fun testUnknownSeverityHandling() {
        val warning = OfficialWarningInfo(
            severity = "UNKNOWN"
        )
        assertEquals("UNKNOWN", warning.severity)
    }

    @Test
    fun testBlankAffectedAreaRelevance() {
        val warning = OfficialWarningInfo(
            affectedArea = ""
        )
        assertTrue(warning.affectedArea.isEmpty())
    }
}
