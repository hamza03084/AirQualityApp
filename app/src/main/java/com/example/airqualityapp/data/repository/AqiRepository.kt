package com.example.airqualityapp.data.repository

import android.util.Log
import com.example.airqualityapp.data.local.dao.AqiDao
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.remote.NetworkModule
import com.example.airqualityapp.util.AqiMath
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow

class AqiRepository(private val aqiDao: AqiDao) {

    companion object {
        private const val TAG = "AqiRepository"

        // OpenWeatherMap FREE API key
        // Provides: Air Pollution (PM2.5, PM10, CO, NO2, SO2, O3) + Weather + Geocoding
        // Free tier: 1,000 calls/day — more than enough
        const val OPENWEATHER_KEY = "ddb18440bce8b28c52566841ddbe9c21"
    }

    /**
     * Fetch AQI data using OpenWeatherMap (100% free).
     * Flow: City name → Geocode to lat/lon → Air Pollution API + Weather API → Calculate EPA AQI
     */
    suspend fun fetchAndSaveAqi(city: String): Result<AqiReadingEntity> {
        return try {
            Log.d(TAG, "Fetching AQI for: $city")

            // Step 1: Convert city name to coordinates
            val geoResponse = NetworkModule.openWeatherApi.geocodeCity(city, 1, OPENWEATHER_KEY)
            if (geoResponse.isEmpty() || geoResponse[0].lat == null) {
                return Result.failure(Exception("City '$city' not found. Check city name."))
            }
            val lat = geoResponse[0].lat!!
            val lon = geoResponse[0].lon!!
            Log.d(TAG, "Geocoded $city → lat=$lat, lon=$lon")

            // Step 2: Fetch Air Pollution data (PM2.5, PM10, CO, NO2, SO2, O3)
            val pollutionResponse = NetworkModule.openWeatherApi.getAirPollution(lat, lon, OPENWEATHER_KEY)
            val pollution = pollutionResponse.list?.firstOrNull()
            val components = pollution?.components

            if (components == null) {
                return Result.failure(Exception("No air pollution data available for $city"))
            }

            // Step 3: Fetch Weather data (temperature, humidity, wind speed)
            val weatherResponse = try {
                NetworkModule.openWeatherApi.getCurrentWeather(city, OPENWEATHER_KEY)
            } catch (e: Exception) {
                Log.w(TAG, "Weather fetch failed, using defaults", e)
                null
            }

            // Step 4: Calculate US EPA AQI from PM2.5 concentration
            val pm25 = components.pm25 ?: 0.0
            val pm10 = components.pm10 ?: 0.0
            val calculatedAqi = AqiMath.aqiFromPm25(pm25)

            val reading = AqiReadingEntity(
                aqi = calculatedAqi,
                pm25 = pm25,
                pm10 = pm10,
                temperature = weatherResponse?.main?.temp ?: 0.0,
                humidity = weatherResponse?.main?.humidity ?: 0.0,
                windSpeed = weatherResponse?.wind?.speed ?: 0.0,
                city = geoResponse[0].name ?: city,
                source = "api",
                timestamp = System.currentTimeMillis(),
                // synced = false so SyncWorker backs the reading up to Firestore (cloud history)
                synced = false,
                userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            )

            aqiDao.insert(reading)
            Log.d(TAG, "✅ ${reading.city} — AQI=${reading.aqi}, PM2.5=${reading.pm25}, Temp=${reading.temperature}°C")
            Result.success(reading)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch AQI for $city: ${e.message}", e)
            Result.failure(Exception("Failed to fetch AQI for $city. Check your internet connection."))
        }
    }

    // US-EPA AQI calculation lives in util.AqiMath (shared with the CSV importer).

    // ── Local DB queries ──────────────────────────────────────────

    suspend fun getLatestReading(): AqiReadingEntity? = aqiDao.getLatestReading()

    fun getLatestReadingFlow(): Flow<AqiReadingEntity?> = aqiDao.getLatestReadingFlow()

    fun getReadingsByDateRange(startTime: Long, endTime: Long): Flow<List<AqiReadingEntity>> =
        aqiDao.getReadingsByDateRange(startTime, endTime)

    fun getAllReadings(): Flow<List<AqiReadingEntity>> = aqiDao.getAllReadings()

    fun getRecentReadings(limit: Int = 24): Flow<List<AqiReadingEntity>> =
        aqiDao.getRecentReadings(limit)

    suspend fun saveManualReading(reading: AqiReadingEntity) {
        aqiDao.insert(reading)
    }

    suspend fun getUnsyncedReadings(): List<AqiReadingEntity> = aqiDao.getUnsyncedReadings()

    suspend fun markAsSynced(ids: List<Long>) = aqiDao.markAsSynced(ids)
}
