package com.example.airqualityapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "main") val main: WeatherMain?,
    @Json(name = "wind") val wind: WeatherWind?,
    @Json(name = "name") val cityName: String?
)

@JsonClass(generateAdapter = true)
data class WeatherMain(
    @Json(name = "temp") val temp: Double?,
    @Json(name = "humidity") val humidity: Double?,
    @Json(name = "pressure") val pressure: Double?
)

@JsonClass(generateAdapter = true)
data class WeatherWind(
    @Json(name = "speed") val speed: Double?
)

// ── Air Pollution API Response ────────────────────────────────
// Docs: https://openweathermap.org/api/air-pollution (FREE tier)

@JsonClass(generateAdapter = true)
data class AirPollutionResponse(
    @Json(name = "list") val list: List<AirPollutionEntry>?
)

@JsonClass(generateAdapter = true)
data class AirPollutionEntry(
    @Json(name = "main") val main: AirPollutionMain?,
    @Json(name = "components") val components: AirPollutionComponents?,
    @Json(name = "dt") val dt: Long?
)

@JsonClass(generateAdapter = true)
data class AirPollutionMain(
    // OpenWeatherMap AQI: 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
    @Json(name = "aqi") val aqi: Int?
)

@JsonClass(generateAdapter = true)
data class AirPollutionComponents(
    @Json(name = "co") val co: Double?,       // Carbon monoxide (μg/m³)
    @Json(name = "no") val no: Double?,       // Nitrogen monoxide (μg/m³)
    @Json(name = "no2") val no2: Double?,     // Nitrogen dioxide (μg/m³)
    @Json(name = "o3") val o3: Double?,       // Ozone (μg/m³)
    @Json(name = "so2") val so2: Double?,     // Sulphur dioxide (μg/m³)
    @Json(name = "pm2_5") val pm25: Double?,  // PM2.5 (μg/m³)
    @Json(name = "pm10") val pm10: Double?,   // PM10 (μg/m³)
    @Json(name = "nh3") val nh3: Double?      // Ammonia (μg/m³)
)

// ── Geocoding API Response ────────────────────────────────────
// Converts city name → lat/lon coordinates

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @Json(name = "name") val name: String?,
    @Json(name = "lat") val lat: Double?,
    @Json(name = "lon") val lon: Double?,
    @Json(name = "country") val country: String?
)

