package com.example.airqualityapp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Note: When AQICN returns an error, "data" is a String like "Invalid key"
// instead of an object. We use a separate response class to handle this.
// The repository catches the Moshi parse error gracefully.

@JsonClass(generateAdapter = true)
data class AqicnResponse(
    @param:Json(name = "status") val status: String?,
    @param:Json(name = "data") val data: AqicnData?
)

@JsonClass(generateAdapter = true)
data class AqicnData(
    @param:Json(name = "aqi") val aqi: Int?,
    @param:Json(name = "city") val city: AqicnCity?,
    @param:Json(name = "iaqi") val iaqi: AqicnIaqi?
)

@JsonClass(generateAdapter = true)
data class AqicnCity(
    @param:Json(name = "name") val name: String?
)

@JsonClass(generateAdapter = true)
data class AqicnIaqi(
    @param:Json(name = "pm25") val pm25: AqicnValue?,
    @param:Json(name = "pm10") val pm10: AqicnValue?,
    @param:Json(name = "t") val temperature: AqicnValue?,
    @param:Json(name = "h") val humidity: AqicnValue?,
    @param:Json(name = "w") val windSpeed: AqicnValue?
)

@JsonClass(generateAdapter = true)
data class AqicnValue(
    @param:Json(name = "v") val value: Double?
)

