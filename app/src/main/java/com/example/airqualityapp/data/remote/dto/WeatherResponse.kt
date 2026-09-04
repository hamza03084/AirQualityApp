package com.example.airqualityapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @param:Json(name = "main") val main: WeatherMain?,
    @param:Json(name = "wind") val wind: WeatherWind?,
    @param:Json(name = "name") val cityName: String?
)

@JsonClass(generateAdapter = true)
data class WeatherMain(
    @param:Json(name = "temp") val temp: Double?,
    @param:Json(name = "humidity") val humidity: Double?,
    @param:Json(name = "pressure") val pressure: Double?
)

@JsonClass(generateAdapter = true)
data class WeatherWind(
    @param:Json(name = "speed") val speed: Double?
)

// ── Air Pollution API Response ────────────────────────────────
// Docs: https://openweathermap.org/api/air-pollution (FREE tier)

@JsonClass(generateAdapter = true)
data class AirPollutionResponse(
    @param:Json(name = "list") val list: List<AirPollutionEntry>?
)

@JsonClass(generateAdapter = true)
data class AirPollutionEntry(
    @param:Json(name = "main") val main: AirPollutionMain?,
    @param:Json(name = "components") val components: AirPollutionComponents?,
    @param:Json(name = "dt") val dt: Long?
)

@JsonClass(generateAdapter = true)
data class AirPollutionMain(
    // OpenWeatherMap AQI: 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
    @param:Json(name = "aqi") val aqi: Int?
)

@JsonClass(generateAdapter = true)
data class AirPollutionComponents(
    @param:Json(name = "co") val co: Double?,       // Carbon monoxide (μg/m³)
    @param:Json(name = "no") val no: Double?,       // Nitrogen monoxide (μg/m³)
    @param:Json(name = "no2") val no2: Double?,     // Nitrogen dioxide (μg/m³)
    @param:Json(name = "o3") val o3: Double?,       // Ozone (μg/m³)
    @param:Json(name = "so2") val so2: Double?,     // Sulphur dioxide (μg/m³)
    @param:Json(name = "pm2_5") val pm25: Double?,  // PM2.5 (μg/m³)
    @param:Json(name = "pm10") val pm10: Double?,   // PM10 (μg/m³)
    @param:Json(name = "nh3") val nh3: Double?      // Ammonia (μg/m³)
)

// ── Geocoding API Response ────────────────────────────────────
// Converts city name → lat/lon coordinates

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    @param:Json(name = "name") val name: String?,
    @param:Json(name = "lat") val lat: Double?,
    @param:Json(name = "lon") val lon: Double?,
    @param:Json(name = "country") val country: String?
)

