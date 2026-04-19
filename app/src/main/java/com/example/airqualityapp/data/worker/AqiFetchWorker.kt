package com.example.airqualityapp.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.airqualityapp.R
import com.example.airqualityapp.data.local.AppDatabase
import com.example.airqualityapp.data.remote.RealtimeDatabaseHelper
import com.example.airqualityapp.data.repository.AqiRepository
import com.example.airqualityapp.util.AqiClassifier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * WorkManager worker that periodically fetches AQI data and sends alerts if thresholds are crossed.
 */
class AqiFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
            currentUser.getIdToken(false).await()

            val db = AppDatabase.getInstance(applicationContext)
            val repository = AqiRepository(db.aqiDao())

            // Default city - can be made configurable via DataStore
            val city = inputData.getString("city") ?: "Lahore"
            val alertThreshold = inputData.getInt("threshold", 150)

            val result = repository.fetchAndSaveAqi(city)

            result.onSuccess { reading ->
                // Push to Realtime Database for live sync
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

                // Send alert if threshold crossed
                if (reading.aqi > alertThreshold) {
                    sendAlert(
                        "⚠️ AQI Alert: ${category.label}",
                        "Current AQI is ${reading.aqi} in ${reading.city}. ${category.recommendation}"
                    )
                    // Also create alert in Realtime Database
                    RealtimeDatabaseHelper.createAlert(
                        RealtimeDatabaseHelper.AqiAlert(
                            city = reading.city,
                            aqi = reading.aqi,
                            category = category.label,
                            message = "AQI is ${reading.aqi} — ${category.recommendation}",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun sendAlert(title: String, body: String) {
        val channelId = "aqi_alerts"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AQI Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

