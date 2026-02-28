package com.example.airqualityapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AqicnResponse(
    @Json(name = "status") val status: String?,
    @Json(name = "data") val data: AqicnData?
)

@JsonClass(generateAdapter = true)
data class AqicnData(
    @Json(name = "aqi") val aqi: Int?,
    @Json(name = "city") val city: AqicnCity?,
    @Json(name = "iaqi") val iaqi: AqicnIaqi?
)

@JsonClass(generateAdapter = true)
data class AqicnCity(
    @Json(name = "name") val name: String?
)

@JsonClass(generateAdapter = true)
data class AqicnIaqi(
    @Json(name = "pm25") val pm25: AqicnValue?,
    @Json(name = "pm10") val pm10: AqicnValue?,
    @Json(name = "t") val temperature: AqicnValue?,
    @Json(name = "h") val humidity: AqicnValue?,
    @Json(name = "w") val windSpeed: AqicnValue?
)

@JsonClass(generateAdapter = true)
data class AqicnValue(
    @Json(name = "v") val value: Double?
)

