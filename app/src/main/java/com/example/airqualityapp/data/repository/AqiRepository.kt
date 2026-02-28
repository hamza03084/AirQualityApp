package com.example.airqualityapp.data.repository

import com.example.airqualityapp.data.local.dao.AqiDao
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.remote.NetworkModule
import kotlinx.coroutines.flow.Flow

class AqiRepository(private val aqiDao: AqiDao) {

    // API Keys - Replace with your own keys
    companion object {
        const val AQICN_TOKEN = "demo" // Replace with your AQICN API token
        const val OPENWEATHER_KEY = "demo" // Replace with your OpenWeatherMap API key
    }

    // Fetch AQI from AQICN API and save to local DB
    suspend fun fetchAndSaveAqi(city: String): Result<AqiReadingEntity> {
        return try {
            val aqicnResponse = NetworkModule.aqicnApi.getAqiByCity(city, AQICN_TOKEN)
            val weatherResponse = try {
                NetworkModule.openWeatherApi.getCurrentWeather(city, OPENWEATHER_KEY)
            } catch (e: Exception) {
                null
            }

            if (aqicnResponse.status == "ok" && aqicnResponse.data != null) {
                val data = aqicnResponse.data
                val reading = AqiReadingEntity(
                    aqi = data.aqi ?: 0,
                    pm25 = data.iaqi?.pm25?.value ?: 0.0,
                    pm10 = data.iaqi?.pm10?.value ?: 0.0,
                    temperature = weatherResponse?.main?.temp ?: data.iaqi?.temperature?.value ?: 0.0,
                    humidity = weatherResponse?.main?.humidity ?: data.iaqi?.humidity?.value ?: 0.0,
                    windSpeed = weatherResponse?.wind?.speed ?: data.iaqi?.windSpeed?.value ?: 0.0,
                    city = data.city?.name ?: city,
                    source = "api",
                    timestamp = System.currentTimeMillis(),
                    synced = true
                )
                aqiDao.insert(reading)
                Result.success(reading)
            } else {
                Result.failure(Exception("Failed to fetch AQI data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get latest reading from local DB
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

