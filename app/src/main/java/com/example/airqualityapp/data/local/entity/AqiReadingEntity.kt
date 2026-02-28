package com.example.airqualityapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aqi_readings")
data class AqiReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aqi: Int,
    val pm25: Double = 0.0,
    val pm10: Double = 0.0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val windSpeed: Double = 0.0,
    val city: String = "",
    val source: String = "api", // "api" or "manual"
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
    val userId: String = ""
)

