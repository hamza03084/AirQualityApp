package com.example.airqualityapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manual_submissions")
data class ManualSubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pm25: Double = 0.0,
    val pm10: Double = 0.0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val windSpeed: Double = 0.0,
    val city: String = "",
    val predictedAqi: Int = 0,
    val category: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

