package com.example.airqualityapp.data.local.dao

import androidx.room.*
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AqiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: AqiReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<AqiReadingEntity>)

    @Query("SELECT * FROM aqi_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(): AqiReadingEntity?

    @Query("SELECT * FROM aqi_readings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestReadingFlow(): Flow<AqiReadingEntity?>

    @Query("SELECT * FROM aqi_readings WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    fun getReadingsByDateRange(startTime: Long, endTime: Long): Flow<List<AqiReadingEntity>>

    @Query("SELECT * FROM aqi_readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<AqiReadingEntity>>

    @Query("SELECT * FROM aqi_readings WHERE synced = 0")
    suspend fun getUnsyncedReadings(): List<AqiReadingEntity>

    @Query("UPDATE aqi_readings SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT * FROM aqi_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReadings(limit: Int): Flow<List<AqiReadingEntity>>

    @Query("DELETE FROM aqi_readings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM aqi_readings")
    suspend fun getCount(): Int
}

