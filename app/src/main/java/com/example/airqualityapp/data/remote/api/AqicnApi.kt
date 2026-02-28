package com.example.airqualityapp.data.remote.api

import com.example.airqualityapp.data.remote.dto.AqicnResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AqicnApi {

    @GET("feed/{city}/")
    suspend fun getAqiByCity(
        @Path("city") city: String,
        @Query("token") token: String
    ): AqicnResponse

    @GET("feed/geo:{lat};{lon}/")
    suspend fun getAqiByCoords(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("token") token: String
    ): AqicnResponse

    companion object {
        const val BASE_URL = "https://api.waqi.info/"
    }
}

