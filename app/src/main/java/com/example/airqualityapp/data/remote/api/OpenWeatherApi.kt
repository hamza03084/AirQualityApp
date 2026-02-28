package com.example.airqualityapp.data.remote.api

import com.example.airqualityapp.data.remote.dto.AirPollutionResponse
import com.example.airqualityapp.data.remote.dto.GeocodingResponse
import com.example.airqualityapp.data.remote.dto.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApi {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    @GET("data/2.5/weather")
    suspend fun getWeatherByCoords(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    // Air Pollution API — FREE, returns PM2.5, PM10, NO2, SO2, CO, O3, AQI
    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): AirPollutionResponse

    // Geocoding API — Convert city name to lat/lon (FREE)
    @GET("geo/1.0/direct")
    suspend fun geocodeCity(
        @Query("q") city: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<GeocodingResponse>

    companion object {
        const val BASE_URL = "https://api.openweathermap.org/"
    }
}

