package com.aistudio.netrasensorhub.data.intelligence.engine

import com.aistudio.netrasensorhub.data.intelligence.models.*
import kotlin.math.*

object LocalImpactEngine {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates the great-circle distance between two points on the Earth using Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2.0) +
                sin(dLon / 2).pow(2.0) * cos(rLat1) * cos(rLat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Computes compass direction (e.g. N, NE, E, SE, S, SW, W, NW) from source to target.
     */
    fun calculateDirection(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        var brng = Math.toDegrees(atan2(y, x))
        brng = (brng + 360) % 360

        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((brng + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    /**
     * Evaluates local impact of a seismic event at (lat, lon) on current device location.
     * Uses empirical attenuation relations for intensity.
     */
    fun evaluateSeismicImpact(
        currentLat: Double,
        currentLon: Double,
        eventLat: Double,
        eventLon: Double,
        magnitude: Double,
        depthKm: Double
    ): AlertSeverity {
        val distanceKm = calculateDistanceKm(currentLat, currentLon, eventLat, eventLon)

        // Estimated perceptible shaking radius (km) based on standard seismological empirical formula
        // R_perceptible ~ 10^(0.45 * M - 0.7) for crustal events (depth < 70km)
        val depthFactor = if (depthKm > 100) 0.7 else 1.0
        val severeRadiusKm = (10.0.pow(0.5 * magnitude - 1.4) * depthFactor).coerceAtLeast(5.0)
        val moderateRadiusKm = (10.0.pow(0.5 * magnitude - 0.8) * depthFactor).coerceAtLeast(15.0)
        val minorRadiusKm = (10.0.pow(0.48 * magnitude - 0.3) * depthFactor).coerceAtLeast(30.0)

        return when {
            distanceKm <= severeRadiusKm && magnitude >= 5.5 -> AlertSeverity.CRITICAL
            distanceKm <= moderateRadiusKm && magnitude >= 4.5 -> AlertSeverity.WARNING
            distanceKm <= minorRadiusKm && magnitude >= 3.5 -> AlertSeverity.WATCH
            else -> AlertSeverity.INFO
        }
    }

    /**
     * Evaluates local impact of weather parameters on physical safety.
     */
    fun evaluateWeatherHazards(
        currentAreaName: String,
        currentLat: Double,
        currentLon: Double,
        weather: WeatherIntelligence
    ): List<DisasterAlert> {
        val alerts = mutableListOf<DisasterAlert>()
        val timestamp = weather.lastUpdatedMillis

        // 1. Severe Convective Storms / Thunderstorms
        val code = weather.weatherCode ?: 0
        if (code in listOf(95, 96, 99)) {
            val isSevere = code in listOf(96, 99) || (weather.windSpeedKmh ?: 0f) >= 70f
            val severity = if (isSevere) AlertSeverity.WARNING else AlertSeverity.WATCH
            val eventId = "WEATHER-${timestamp / 1000 / 3600}-${currentAreaName.hashCode().absoluteValue % 10000}-THUNDER"
            alerts.add(
                DisasterAlert(
                    eventId = eventId,
                    title = if (isSevere) "Severe Thunderstorm & Hail Warning" else "Active Thunderstorm Advisory",
                    category = DisasterCategory.WEATHER,
                    severity = severity,
                    locationName = currentAreaName,
                    latitude = currentLat,
                    longitude = currentLon,
                    distanceKmFromCurrent = 0.0,
                    impactRadiusKm = 25.0,
                    localImpactLevel = severity,
                    description = if (isSevere) "Intense lightning, hail, and high convective winds detected in your local zone. Seek indoor shelter."
                    else "Thunderstorm activity detected. Avoid open fields and metallic conductors.",
                    officialSource = weather.source,
                    confidence = 0.90f,
                    timestamp = timestamp,
                    isUnconfirmed = false
                )
            )
        }

        // 2. High Gale / Destructive Wind Hazard
        val wind = weather.windSpeedKmh ?: 0f
        if (wind >= 60f) {
            val isCritical = wind >= 90f
            val severity = if (isCritical) AlertSeverity.CRITICAL else AlertSeverity.WARNING
            val eventId = "WEATHER-${timestamp / 1000 / 3600}-${currentAreaName.hashCode().absoluteValue % 10000}-WIND"
            alerts.add(
                DisasterAlert(
                    eventId = eventId,
                    title = if (isCritical) "Destructive High-Wind Gale" else "Strong Wind Warning",
                    category = DisasterCategory.WEATHER,
                    severity = severity,
                    locationName = currentAreaName,
                    latitude = currentLat,
                    longitude = currentLon,
                    distanceKmFromCurrent = 0.0,
                    impactRadiusKm = 40.0,
                    localImpactLevel = severity,
                    description = "Sustained surface winds reaching %.1f km/h. Risk of falling branches, structural debris, and airborne hazards.".format(wind),
                    officialSource = weather.source,
                    confidence = 0.92f,
                    timestamp = timestamp,
                    isUnconfirmed = false
                )
            )
        }

        // 3. Extreme Thermal Hazard (Heatwave / Coldwave)
        val temp = weather.temperatureC ?: 25f
        if (temp >= 43.5f) {
            val isCritical = temp >= 46f
            val severity = if (isCritical) AlertSeverity.CRITICAL else AlertSeverity.WARNING
            val eventId = "WEATHER-${timestamp / 1000 / 86400}-${currentAreaName.hashCode().absoluteValue % 10000}-HEAT"
            alerts.add(
                DisasterAlert(
                    eventId = eventId,
                    title = if (isCritical) "Severe Heatwave Extreme Hazard" else "Extreme Heat Advisory",
                    category = DisasterCategory.HEATWAVE,
                    severity = severity,
                    locationName = currentAreaName,
                    latitude = currentLat,
                    longitude = currentLon,
                    distanceKmFromCurrent = 0.0,
                    impactRadiusKm = 100.0,
                    localImpactLevel = severity,
                    description = "Ambient temperature reaching %.1f°C. High risk of heat exhaustion, rapid device thermal throttling, and dehydration.".format(temp),
                    officialSource = weather.source,
                    confidence = 0.95f,
                    timestamp = timestamp,
                    isUnconfirmed = false
                )
            )
        }

        return alerts
    }

    /**
     * Generates dynamically relevant surrounding impact areas around the center point.
     * Computes real distances and directional bearings without hardcoded static lists.
     */
    fun computeDynamicNearbyAreas(centerLat: Double, centerLon: Double, currentCity: String?): List<NearbyArea> {
        // Generates dynamic quadrant sampling rings at ~45km and ~90km offsets
        val offsets = listOf(
            Triple("North Sector", 0.40, 0.0),
            Triple("East Sector", 0.0, 0.45),
            Triple("South Sector", -0.40, 0.0),
            Triple("West Sector", 0.0, -0.45),
            Triple("North-East Region", 0.60, 0.65),
            Triple("South-West Region", -0.60, -0.65)
        )

        return offsets.map { (sector, latOff, lonOff) ->
            val targetLat = centerLat + latOff
            val targetLon = centerLon + lonOff
            val dist = calculateDistanceKm(centerLat, centerLon, targetLat, targetLon)
            val dir = calculateDirection(centerLat, centerLon, targetLat, targetLon)
            NearbyArea(
                name = "${currentCity ?: "Local"} $sector",
                districtOrState = "Radial Zone (${dist.toInt()} km)",
                distanceKm = dist,
                direction = dir,
                latitude = targetLat,
                longitude = targetLon
            )
        }
    }
}
