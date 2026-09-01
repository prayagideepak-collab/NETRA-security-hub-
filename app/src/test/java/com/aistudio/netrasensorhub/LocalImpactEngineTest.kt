package com.aistudio.netrasensorhub

import com.aistudio.netrasensorhub.data.intelligence.engine.LocalImpactEngine
import com.aistudio.netrasensorhub.data.intelligence.models.AlertSeverity
import com.aistudio.netrasensorhub.data.intelligence.models.WeatherIntelligence
import com.aistudio.netrasensorhub.data.intelligence.models.WeatherStatus
import org.junit.Assert.*
import org.junit.Test

class LocalImpactEngineTest {

    @Test
    fun testHaversineDistanceAccuracy() {
        // Distance between Prayagraj (25.4358, 81.8463) and Varanasi (25.3176, 82.9739) ~115-125 km
        val distance = LocalImpactEngine.calculateDistanceKm(25.4358, 81.8463, 25.3176, 82.9739)
        assertTrue("Distance should be approx 115-125 km, got $distance", distance in 110.0..130.0)
    }

    @Test
    fun testDirectionCalculation() {
        // North
        val dirNorth = LocalImpactEngine.calculateDirection(25.0, 80.0, 26.0, 80.0)
        assertEquals("N", dirNorth)

        // East
        val dirEast = LocalImpactEngine.calculateDirection(25.0, 80.0, 25.0, 81.0)
        assertEquals("E", dirEast)
    }

    @Test
    fun testSeismicImpactCriticalNearby() {
        // Major earthquake M 6.8 at distance 30 km -> Must be CRITICAL
        val impact = LocalImpactEngine.evaluateSeismicImpact(
            currentLat = 25.43,
            currentLon = 81.84,
            eventLat = 25.65,
            eventLon = 81.84, // ~24 km away
            magnitude = 6.8,
            depthKm = 10.0
        )
        assertEquals(AlertSeverity.CRITICAL, impact)
    }

    @Test
    fun testSeismicImpactSuppressedDistant() {
        // Minor/Moderate earthquake M 4.0 at distance 500 km -> Must be INFO (No local panic)
        val impact = LocalImpactEngine.evaluateSeismicImpact(
            currentLat = 25.43,
            currentLon = 81.84,
            eventLat = 29.00,
            eventLon = 81.84, // ~396 km away
            magnitude = 4.0,
            depthKm = 15.0
        )
        assertEquals(AlertSeverity.INFO, impact)
    }

    @Test
    fun testSevereWeatherThunderstormAndHighWindHazard() {
        val severeWeather = WeatherIntelligence(
            temperatureC = 34.0f,
            apparentTemperatureC = 38.0f,
            weatherCondition = "Severe Thunderstorm with Hail",
            weatherCode = 96,
            windSpeedKmh = 75.0f,
            windDirectionDeg = 180.0f,
            relativeHumidity = 85.0f,
            precipitationMm = 35.0f,
            uvIndex = null,
            lastUpdatedMillis = System.currentTimeMillis(),
            source = "Open-Meteo",
            status = WeatherStatus.LIVE
        )

        val alerts = LocalImpactEngine.evaluateWeatherHazards(
            currentAreaName = "Kanpur",
            currentLat = 26.4499,
            currentLon = 80.3319,
            weather = severeWeather
        )

        assertTrue("Should detect thunderstorm and wind hazards", alerts.isNotEmpty())
        assertTrue("Should have WARNING severity", alerts.any { it.severity == AlertSeverity.WARNING })
    }

    @Test
    fun testNormalWeatherNoFalseAlerts() {
        val normalWeather = WeatherIntelligence(
            temperatureC = 26.0f,
            apparentTemperatureC = 27.0f,
            weatherCondition = "Mainly Clear",
            weatherCode = 1,
            windSpeedKmh = 12.0f,
            windDirectionDeg = 90.0f,
            relativeHumidity = 50.0f,
            precipitationMm = 0.0f,
            uvIndex = null,
            lastUpdatedMillis = System.currentTimeMillis(),
            source = "Open-Meteo",
            status = WeatherStatus.LIVE
        )

        val alerts = LocalImpactEngine.evaluateWeatherHazards(
            currentAreaName = "Lucknow",
            currentLat = 26.8467,
            currentLon = 80.9462,
            weather = normalWeather
        )

        assertTrue("Normal weather should produce 0 alerts", alerts.isEmpty())
    }

    @Test
    fun testDynamicNearbyAreasGeneration() {
        val nearby = LocalImpactEngine.computeDynamicNearbyAreas(28.6139, 77.2090, "Delhi")
        assertEquals(6, nearby.size)
        assertTrue(nearby.all { it.distanceKm > 0.0 })
    }
}
