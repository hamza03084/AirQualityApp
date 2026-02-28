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

