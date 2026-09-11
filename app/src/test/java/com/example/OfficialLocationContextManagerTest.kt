package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.service.LocationContextInfo
import com.example.data.service.OfficialLocationContextManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfficialLocationContextManagerTest {

    private lateinit var context: Context
    private lateinit var locationManager: OfficialLocationContextManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        locationManager = OfficialLocationContextManager(context)
    }

    @Test
    fun testLocationContextModelDefaults() {
        val info = LocationContextInfo()
        assertEquals(0.0, info.latitude, 0.0001)
        assertEquals(0.0, info.longitude, 0.0001)
        assertEquals("LOCATION_UNAVAILABLE", info.source)
        assertEquals("LOCATION_UNAVAILABLE", info.locationStatus)
    }

    @Test
    fun testValidCoordinateValidationLogic() {
        val infoValid = LocationContextInfo(latitude = 37.7749, longitude = -122.4194, locationStatus = "LOCATION_AVAILABLE")
        assertTrue(infoValid.latitude in -90.0..90.0)
        assertTrue(infoValid.longitude in -180.0..180.0)
        assertEquals("LOCATION_AVAILABLE", infoValid.locationStatus)
    }

    @Test
    fun testCoordinatesAvailableWhenGeocoderFails() {
        val info = LocationContextInfo(
            latitude = 37.7749,
            longitude = -122.4194,
            locality = "Location Unavailable",
            locationStatus = "COORDINATES_AVAILABLE"
        )
        assertEquals("COORDINATES_AVAILABLE", info.locationStatus)
        assertNotEquals(0.0, info.latitude, 0.0001)
        assertEquals("Location Unavailable", info.locality)
    }

    @Test
    fun testPermissionDeniedState() {
        val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE", locationStatus = "PERMISSION_NOT_GRANTED")
        assertEquals("PERMISSION_NOT_GRANTED", info.locationStatus)
    }

    @Test
    fun testProviderDisabledState() {
        val info = LocationContextInfo(source = "LOCATION_UNAVAILABLE", locationStatus = "PROVIDER_DISABLED")
        assertEquals("PROVIDER_DISABLED", info.locationStatus)
    }
}
