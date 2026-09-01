package com.aistudio.netrasensorhub.data.intelligence.models

enum class WeatherStatus {
    LIVE,
    CACHED,
    FETCHING,
    UNAVAILABLE,
    NETWORK_ERROR
}

data class WeatherIntelligence(
    val temperatureC: Float?,
    val apparentTemperatureC: Float?,
    val weatherCondition: String?,
    val weatherCode: Int?,
    val windSpeedKmh: Float?,
    val windDirectionDeg: Float?,
    val relativeHumidity: Float?,
    val precipitationMm: Float?,
    val uvIndex: Float?,
    val lastUpdatedMillis: Long,
    val source: String,
    val status: WeatherStatus
)

object WeatherConditionHelper {
    /**
     * Translates WMO Weather interpretation codes (WW) to human-readable condition string.
     * Standardized by the World Meteorological Organization (WMO).
     */
    fun getConditionName(code: Int?): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog / Depositing Rime Fog"
            51, 53, 55 -> "Drizzle (Light/Moderate/Dense)"
            56, 57 -> "Freezing Drizzle"
            61, 63, 65 -> "Rain (Slight/Moderate/Heavy)"
            66, 67 -> "Freezing Rain"
            71, 73, 75 -> "Snow Fall (Slight/Moderate/Heavy)"
            77 -> "Snow Grains"
            80, 81, 82 -> "Rain Showers (Slight/Moderate/Violent)"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorm (Slight/Moderate)"
            96, 99 -> "Severe Thunderstorm with Hail"
            null -> "Unknown"
            else -> "Condition $code"
        }
    }
}
