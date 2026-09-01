package com.aistudio.netrasensorhub.data.intelligence.network

import com.aistudio.netrasensorhub.data.intelligence.engine.LocalImpactEngine
import com.aistudio.netrasensorhub.data.intelligence.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AuthoritativeIntelligenceClient {

    private val connectTimeoutMs = 6000
    private val readTimeoutMs = 8000

    /**
     * Fetches real-time verified weather telemetry from authoritative Open-Meteo meteorological feed.
     */
    suspend fun fetchRealtimeWeather(latitude: Double, longitude: Double): Result<WeatherIntelligence> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m&timezone=auto"
                .format(java.util.Locale.US, latitude, longitude)

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NetraSecurityHub/1.0 (Android)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                val json = JSONObject(response)

                if (json.has("current")) {
                    val current = json.getJSONObject("current")
                    val temp = current.optDouble("temperature_2m", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val apparentTemp = current.optDouble("apparent_temperature", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val code = if (current.has("weather_code")) current.getInt("weather_code") else null
                    val windSpeed = current.optDouble("wind_speed_10m", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val windDir = current.optDouble("wind_direction_10m", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val humidity = current.optDouble("relative_humidity_2m", Double.NaN).toFloat().takeIf { !it.isNaN() }
                    val precip = current.optDouble("precipitation", Double.NaN).toFloat().takeIf { !it.isNaN() }

                    val weather = WeatherIntelligence(
                        temperatureC = temp,
                        apparentTemperatureC = apparentTemp,
                        weatherCondition = WeatherConditionHelper.getConditionName(code),
                        weatherCode = code,
                        windSpeedKmh = windSpeed,
                        windDirectionDeg = windDir,
                        relativeHumidity = humidity,
                        precipitationMm = precip,
                        uvIndex = null,
                        lastUpdatedMillis = System.currentTimeMillis(),
                        source = "Open-Meteo Meteorological Service",
                        status = WeatherStatus.LIVE
                    )
                    Result.success(weather)
                } else {
                    Result.failure(IllegalStateException("Malformed weather response payload"))
                }
            } else {
                Result.failure(IllegalStateException("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches real verified seismic events within geo-radius from authoritative USGS Earthquake catalog.
     */
    suspend fun fetchAuthoritativeSeismicEvents(latitude: Double, longitude: Double): Result<List<SeismicEvent>> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=%.4f&longitude=%.4f&maxradiuskm=1500&minmagnitude=3.0&limit=10"
                .format(java.util.Locale.US, latitude, longitude)

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NetraSecurityHub/1.0 (Android)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.use { it.readText() }
                val json = JSONObject(response)

                val features = json.optJSONArray("features")
                val events = mutableListOf<SeismicEvent>()

                if (features != null) {
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val id = feature.optString("id", "SEISMIC-${System.currentTimeMillis()}-$i")
                        val properties = feature.getJSONObject("properties")
                        val geometry = feature.getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")

                        val mag = properties.optDouble("mag", 0.0)
                        val place = properties.optString("place", "Regional Seismic Anomaly")
                        val time = properties.optLong("time", System.currentTimeMillis())
                        val status = properties.optString("status", "reviewed")

                        val eventLon = coords.getDouble(0)
                        val eventLat = coords.getDouble(1)
                        val depthKm = coords.optDouble(2, 10.0)

                        val distKm = LocalImpactEngine.calculateDistanceKm(latitude, longitude, eventLat, eventLon)
                        val impact = LocalImpactEngine.evaluateSeismicImpact(latitude, longitude, eventLat, eventLon, mag, depthKm)

                        events.add(
                            SeismicEvent(
                                eventId = "USGS-$id",
                                magnitude = mag,
                                depthKm = depthKm,
                                place = place,
                                latitude = eventLat,
                                longitude = eventLon,
                                originTimeMillis = time,
                                distanceKmFromCurrent = distKm,
                                potentialLocalImpact = impact,
                                officialConfirmation = "USGS Earthquake Hazards Program ($status)"
                            )
                        )
                    }
                }
                Result.success(events)
            } else {
                Result.failure(IllegalStateException("HTTP ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
