package com.example.airqualityapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.airqualityapp.data.local.dao.AqiDao
import com.example.airqualityapp.data.local.dao.ManualSubmissionDao
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.local.entity.ManualSubmissionEntity

@Database(
    entities = [AqiReadingEntity::class, ManualSubmissionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun aqiDao(): AqiDao
    abstract fun manualSubmissionDao(): ManualSubmissionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "air_quality_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

