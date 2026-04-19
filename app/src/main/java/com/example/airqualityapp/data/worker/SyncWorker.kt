package com.example.airqualityapp.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.airqualityapp.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * WorkManager worker that syncs unsynced local AQI readings to Firebase Firestore.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
            currentUser.getIdToken(false).await()

            val db = AppDatabase.getInstance(applicationContext)
            val aqiDao = db.aqiDao()
            val manualSubmissionDao = db.manualSubmissionDao()
            val firestore = FirebaseFirestore.getInstance()

            // Sync unsynced AQI readings
            val unsyncedReadings = aqiDao.getUnsyncedReadings()
            if (unsyncedReadings.isNotEmpty()) {
                val batch = firestore.batch()
                unsyncedReadings.forEach { reading ->
                    val docRef = firestore.collection("aqi_readings").document()
                    batch.set(docRef, mapOf(
                        "aqi" to reading.aqi,
                        "pm25" to reading.pm25,
                        "pm10" to reading.pm10,
                        "temperature" to reading.temperature,
                        "humidity" to reading.humidity,
                        "windSpeed" to reading.windSpeed,
                        "city" to reading.city,
                        "source" to reading.source,
                        "timestamp" to reading.timestamp,
                        "userId" to reading.userId
                    ))
                }
                batch.commit().await()
                aqiDao.markAsSynced(unsyncedReadings.map { it.id })
            }

            // Sync unsynced manual submissions
            val unsyncedSubmissions = manualSubmissionDao.getUnsyncedSubmissions()
            if (unsyncedSubmissions.isNotEmpty()) {
                val batch = firestore.batch()
                unsyncedSubmissions.forEach { submission ->
                    val docRef = firestore.collection("manual_submissions").document()
                    batch.set(docRef, mapOf(
                        "pm25" to submission.pm25,
                        "pm10" to submission.pm10,
                        "temperature" to submission.temperature,
                        "humidity" to submission.humidity,
                        "windSpeed" to submission.windSpeed,
                        "city" to submission.city,
                        "predictedAqi" to submission.predictedAqi,
                        "category" to submission.category,
                        "userId" to submission.userId,
                        "timestamp" to submission.timestamp
                    ))
                }
                batch.commit().await()
                manualSubmissionDao.markAsSynced(unsyncedSubmissions.map { it.id })
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

