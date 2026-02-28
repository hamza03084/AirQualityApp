package com.example.airqualityapp.data.local.dao

import androidx.room.*
import com.example.airqualityapp.data.local.entity.ManualSubmissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(submission: ManualSubmissionEntity): Long

    @Query("SELECT * FROM manual_submissions ORDER BY timestamp DESC")
    fun getAllSubmissions(): Flow<List<ManualSubmissionEntity>>

    @Query("SELECT * FROM manual_submissions WHERE synced = 0")
    suspend fun getUnsyncedSubmissions(): List<ManualSubmissionEntity>

    @Query("UPDATE manual_submissions SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}

