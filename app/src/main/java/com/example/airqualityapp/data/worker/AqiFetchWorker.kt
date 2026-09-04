package com.example.airqualityapp.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.airqualityapp.data.local.AppDatabase
import com.example.airqualityapp.data.remote.RealtimeDatabaseHelper
import com.example.airqualityapp.data.repository.AqiRepository
import com.example.airqualityapp.data.repository.UserRepository
import com.example.airqualityapp.util.AlertNotifier
import com.example.airqualityapp.util.AqiClassifier
import com.example.airqualityapp.util.AqiConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * WorkManager worker that periodically (every 60 minutes) fetches AQI data for the
 * user's preferred city, publishes it to the Realtime Database and raises an alert
 * when the AQI crosses the configured threshold.
 */
class AqiFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
            currentUser.getIdToken(false).await()

            // Load admin thresholds / recommendations (no live listener in a worker).
            AqiConfig.loadOnce()

            val db = AppDatabase.getInstance(applicationContext)
            val repository = AqiRepository(db.aqiDao())
            val profile = UserRepository().getCurrentUserProfile()

            val city = profile?.preferredCity?.takeIf { it.isNotBlank() }
                ?: inputData.getString("city")
                ?: "Lahore"

            val reading = repository.fetchAndSaveAqi(city).getOrNull() ?: return Result.retry()

            // Publish to Realtime Database for live sync across devices
            val category = AqiClassifier.classify(reading.aqi)
            val liveData = RealtimeDatabaseHelper.LiveAqiData(
                aqi = reading.aqi,
                pm25 = reading.pm25,
                pm10 = reading.pm10,
                temperature = reading.temperature,
                humidity = reading.humidity,
                windSpeed = reading.windSpeed,
                city = reading.city,
                category = category.label,
                source = "api",
                timestamp = reading.timestamp
            )
            RealtimeDatabaseHelper.writeLiveAqi(liveData)
            RealtimeDatabaseHelper.pushAqiHistory(reading.city, liveData)

            // Threshold check, de-duplication, notification and audit record
            AlertNotifier.checkAndNotify(applicationContext, reading, profile)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
