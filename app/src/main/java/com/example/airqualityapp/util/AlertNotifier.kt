package com.example.airqualityapp.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.airqualityapp.MainActivity
import com.example.airqualityapp.R
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.remote.RealtimeDatabaseHelper
import com.example.airqualityapp.data.repository.UserProfile
import kotlinx.coroutines.flow.first

private val Context.alertStateStore: DataStore<Preferences> by preferencesDataStore(name = "alert_state")

/**
 * Single place that decides whether an AQI reading must raise an alert.
 *
 * Used by the Home screen (live fetch) and by AqiFetchWorker (background fetch):
 *  1. compare AQI with the user's threshold (fallback: admin default threshold),
 *  2. suppress duplicates – one alert per city and category per hour (DataStore),
 *  3. show a local notification if the user has notifications enabled,
 *  4. always record the alert in Realtime Database for the admin audit trail.
 */
object AlertNotifier {

    const val CHANNEL_ID = "aqi_alerts"
    private const val TAG = "AlertNotifier"
    private const val COOLDOWN_MS = 60 * 60 * 1000L

    /** @return true when an alert was raised. */
    suspend fun checkAndNotify(
        context: Context,
        reading: AqiReadingEntity,
        profile: UserProfile?
    ): Boolean {
        val threshold = profile?.alertThreshold ?: AqiConfig.thresholds.alertThreshold
        if (reading.aqi <= threshold) return false

        val app = context.applicationContext
        val category = AqiClassifier.classify(reading.aqi)
        val cityKey = reading.city.lowercase().replace(Regex("[^a-z0-9]"), "_")
        val timeKey = longPreferencesKey("last_alert_time_$cityKey")
        val categoryKey = stringPreferencesKey("last_alert_category_$cityKey")

        val prefs = runCatching { app.alertStateStore.data.first() }.getOrNull()
        val lastTime = prefs?.get(timeKey) ?: 0L
        val lastCategory = prefs?.get(categoryKey)
        val now = System.currentTimeMillis()
        if (now - lastTime < COOLDOWN_MS && lastCategory == category.label) {
            Log.d(TAG, "Alert for ${reading.city} suppressed (cool-down)")
            return false
        }

        val message = "Current AQI is ${reading.aqi} in ${reading.city}. ${category.recommendation}"
        if (profile?.notificationsEnabled != false) {
            showNotification(app, "⚠️ AQI Alert: ${category.label}", message)
        }

        RealtimeDatabaseHelper.createAlert(
            RealtimeDatabaseHelper.AqiAlert(
                city = reading.city,
                aqi = reading.aqi,
                category = category.label,
                message = "AQI is ${reading.aqi} — ${category.recommendation}",
                timestamp = now
            )
        )

        runCatching {
            app.alertStateStore.edit {
                it[timeKey] = now
                it[categoryKey] = category.label
            }
        }
        return true
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "AQI Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for air quality alerts"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, title: String, body: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted – alert recorded but not shown")
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(System.currentTimeMillis().toInt(), notification)
        }.onFailure { Log.e(TAG, "Failed to show notification", it) }
    }
}
