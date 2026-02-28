package com.example.airqualityapp.data.remote

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * ============================================================================
 *  RealtimeDatabaseHelper — Firebase Realtime Database CRUD Operations
 * ============================================================================
 *
 *  Realtime Database URL: https://airqualityapp-e7fb2-default-rtdb.firebaseio.com
 *
 *  DATABASE STRUCTURE:
 *  -------------------
 *  {
 *    "live_aqi": {                          ← Real-time AQI per city
 *      "Lahore":  { aqi, pm25, pm10, temp, humidity, windSpeed, timestamp, source },
 *      "Karachi": { ... },
 *      "Islamabad": { ... }
 *    },
 *    "aqi_history": {                       ← Historical readings per city
 *      "Lahore": {
 *        "-NxAutoId1": { aqi, pm25, pm10, temp, humidity, windSpeed, timestamp },
 *        "-NxAutoId2": { ... }
 *      }
 *    },
 *    "alerts": {                            ← Active alerts
 *      "-NxAutoId": { city, aqi, category, message, timestamp, active }
 *    },
 *    "user_settings": {                     ← Per-user alert preferences
 *      "userId1": { alertThreshold, notificationsEnabled, preferredCity },
 *      "userId2": { ... }
 *    }
 *  }
 *
 *  WHY USE REALTIME DB (vs Firestore):
 *  - Live AQI data needs instant sync (< 100ms latency)
 *  - Simpler read/write for flat data structures
 *  - Better for frequently changing values (live sensor data)
 *  - Cost-effective for high-frequency reads
 */
object RealtimeDatabaseHelper {

    private const val TAG = "RealtimeDBHelper"

    val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance().apply {
            // Enable offline persistence — data available when offline
            setPersistenceEnabled(true)
        }
    }

    // ====================================================================
    //  DATA CLASSES
    // ====================================================================

    data class LiveAqiData(
        val aqi: Int = 0,
        val pm25: Double = 0.0,
        val pm10: Double = 0.0,
        val temperature: Double = 0.0,
        val humidity: Double = 0.0,
        val windSpeed: Double = 0.0,
        val city: String = "",
        val category: String = "",
        val source: String = "api",
        val timestamp: Long = 0L
    )

    data class AqiAlert(
        val city: String = "",
        val aqi: Int = 0,
        val category: String = "",
        val message: String = "",
        val timestamp: Long = 0L,
        val active: Boolean = true
    )

    data class UserSettings(
        val alertThreshold: Int = 150,
        val notificationsEnabled: Boolean = true,
        val preferredCity: String = "Lahore"
    )

    // ====================================================================
    //  1. INITIALIZE — Set default database structure
    // ====================================================================

    /**
     * Initialize database with default structure for Pakistani cities.
     * Call once from admin or on first app launch.
     */
    suspend fun initializeDatabase() {
        val cities = listOf("Lahore", "Karachi", "Islamabad", "Rawalpindi",
            "Faisalabad", "Multan", "Peshawar", "Quetta")

        cities.forEach { city ->
            val ref = db.getReference("live_aqi/$city")
            val snapshot = ref.get().await()
            if (!snapshot.exists()) {
                ref.setValue(
                    LiveAqiData(
                        city = city,
                        aqi = 0,
                        category = "No Data",
                        source = "init",
                        timestamp = System.currentTimeMillis()
                    )
                ).await()
            }
        }
        Log.d(TAG, "Database initialized with ${cities.size} cities")
    }

    // ====================================================================
    //  2. CREATE Operations
    // ====================================================================

    /**
     * Write live AQI data for a city (overwrites previous).
     *
     * Usage:
     *   writeLiveAqi(LiveAqiData(aqi = 156, city = "Lahore", ...))
     */
    suspend fun writeLiveAqi(data: LiveAqiData): Result<Unit> {
        return try {
            db.getReference("live_aqi/${data.city}").setValue(data).await()
            Log.d(TAG, "Live AQI updated: ${data.city} = ${data.aqi}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing live AQI for ${data.city}", e)
            Result.failure(e)
        }
    }

    /**
     * Push a new historical AQI reading (auto-generated key).
     *
     * Usage:
     *   pushAqiHistory("Lahore", LiveAqiData(aqi = 156, ...))
     */
    suspend fun pushAqiHistory(city: String, data: LiveAqiData): Result<String> {
        return try {
            val ref = db.getReference("aqi_history/$city").push()
            ref.setValue(data).await()
            Log.d(TAG, "History pushed: $city/${ref.key}")
            Result.success(ref.key ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing history for $city", e)
            Result.failure(e)
        }
    }

    /**
     * Create an AQI alert.
     */
    suspend fun createAlert(alert: AqiAlert): Result<String> {
        return try {
            val ref = db.getReference("alerts").push()
            ref.setValue(alert).await()
            Log.d(TAG, "Alert created: ${ref.key}")
            Result.success(ref.key ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating alert", e)
            Result.failure(e)
        }
    }

    /**
     * Save user alert settings.
     */
    suspend fun saveUserSettings(userId: String, settings: UserSettings): Result<Unit> {
        return try {
            db.getReference("user_settings/$userId").setValue(settings).await()
            Log.d(TAG, "Settings saved for $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings for $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Write data using server timestamp.
     */
    suspend fun writeLiveAqiWithServerTimestamp(city: String, data: Map<String, Any>): Result<Unit> {
        return try {
            val dataWithTimestamp = data.toMutableMap()
            dataWithTimestamp["timestamp"] = ServerValue.TIMESTAMP
            db.getReference("live_aqi/$city").setValue(dataWithTimestamp).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====================================================================
    //  3. READ Operations
    // ====================================================================

    /**
     * Read live AQI for a specific city (one-time read).
     */
    suspend fun readLiveAqi(city: String): Result<LiveAqiData?> {
        return try {
            val snapshot = db.getReference("live_aqi/$city").get().await()
            val data = snapshot.getValue(LiveAqiData::class.java)
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading live AQI for $city", e)
            Result.failure(e)
        }
    }

    /**
     * Read live AQI for ALL cities (one-time read).
     */
    suspend fun readAllLiveAqi(): Result<Map<String, LiveAqiData>> {
        return try {
            val snapshot = db.getReference("live_aqi").get().await()
            val result = mutableMapOf<String, LiveAqiData>()
            snapshot.children.forEach { child ->
                val data = child.getValue(LiveAqiData::class.java)
                if (data != null) {
                    result[child.key ?: ""] = data
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading all live AQI", e)
            Result.failure(e)
        }
    }

    /**
     * Read AQI history for a city (latest N records).
     */
    suspend fun readAqiHistory(city: String, limit: Int = 24): Result<List<LiveAqiData>> {
        return try {
            val snapshot = db.getReference("aqi_history/$city")
                .orderByChild("timestamp")
                .limitToLast(limit)
                .get().await()

            val readings = mutableListOf<LiveAqiData>()
            snapshot.children.forEach { child ->
                child.getValue(LiveAqiData::class.java)?.let { readings.add(it) }
            }
            Result.success(readings.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Log.e(TAG, "Error reading history for $city", e)
            Result.failure(e)
        }
    }

    /**
     * Read user settings.
     */
    suspend fun readUserSettings(userId: String): Result<UserSettings?> {
        return try {
            val snapshot = db.getReference("user_settings/$userId").get().await()
            Result.success(snapshot.getValue(UserSettings::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read active alerts.
     */
    suspend fun readActiveAlerts(): Result<List<AqiAlert>> {
        return try {
            val snapshot = db.getReference("alerts")
                .orderByChild("active")
                .equalTo(true)
                .get().await()

            val alerts = mutableListOf<AqiAlert>()
            snapshot.children.forEach { child ->
                child.getValue(AqiAlert::class.java)?.let { alerts.add(it) }
            }
            Result.success(alerts.sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====================================================================
    //  4. REAL-TIME LISTENERS (Flow-based)
    // ====================================================================

    /**
     * Listen to live AQI for a city in real-time.
     * Returns a Flow that emits every time the value changes.
     *
     * Usage in Composable:
     *   val liveAqi by RealtimeDatabaseHelper
     *       .observeLiveAqi("Lahore")
     *       .collectAsState(initial = null)
     */
    fun observeLiveAqi(city: String): Flow<LiveAqiData?> = callbackFlow {
        val ref = db.getReference("live_aqi/$city")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(LiveAqiData::class.java)
                trySend(data)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listen cancelled: live_aqi/$city", error.toException())
                trySend(null)
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Listen to ALL cities' live AQI in real-time.
     */
    fun observeAllLiveAqi(): Flow<Map<String, LiveAqiData>> = callbackFlow {
        val ref = db.getReference("live_aqi")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val result = mutableMapOf<String, LiveAqiData>()
                snapshot.children.forEach { child ->
                    val data = child.getValue(LiveAqiData::class.java)
                    if (data != null) {
                        result[child.key ?: ""] = data
                    }
                }
                trySend(result)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Listen cancelled: live_aqi", error.toException())
                trySend(emptyMap())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Listen to active alerts in real-time.
     */
    fun observeAlerts(): Flow<List<AqiAlert>> = callbackFlow {
        val ref = db.getReference("alerts")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<AqiAlert>()
                snapshot.children.forEach { child ->
                    val alert = child.getValue(AqiAlert::class.java)
                    if (alert != null && alert.active) {
                        alerts.add(alert)
                    }
                }
                trySend(alerts.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ====================================================================
    //  5. UPDATE Operations
    // ====================================================================

    /**
     * Update specific fields of live AQI data.
     */
    suspend fun updateLiveAqiFields(city: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.getReference("live_aqi/$city").updateChildren(updates).await()
            Log.d(TAG, "Live AQI fields updated: $city")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user settings partially.
     */
    suspend fun updateUserSettings(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.getReference("user_settings/$userId").updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deactivate an alert.
     */
    suspend fun deactivateAlert(alertKey: String): Result<Unit> {
        return try {
            db.getReference("alerts/$alertKey/active").setValue(false).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Multi-path update — update multiple locations atomically.
     *
     * Usage:
     *   multiPathUpdate(mapOf(
     *       "live_aqi/Lahore/aqi" to 175,
     *       "live_aqi/Lahore/category" to "Unhealthy",
     *       "live_aqi/Lahore/timestamp" to System.currentTimeMillis()
     *   ))
     */
    suspend fun multiPathUpdate(updates: Map<String, Any>): Result<Unit> {
        return try {
            db.reference.updateChildren(updates).await()
            Log.d(TAG, "Multi-path update: ${updates.size} paths")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ====================================================================
    //  6. DELETE Operations
    // ====================================================================

    /**
     * Delete a specific node.
     */
    suspend fun deleteNode(path: String): Result<Unit> {
        return try {
            db.getReference(path).removeValue().await()
            Log.d(TAG, "Deleted: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete old history entries for a city (keep only latest N).
     */
    suspend fun pruneHistory(city: String, keepCount: Int = 100): Result<Int> {
        return try {
            val snapshot = db.getReference("aqi_history/$city")
                .orderByChild("timestamp")
                .get().await()

            val totalCount = snapshot.childrenCount.toInt()
            if (totalCount <= keepCount) return Result.success(0)

            var deleteCount = 0
            val toDelete = totalCount - keepCount
            val updates = mutableMapOf<String, Any?>()

            snapshot.children.take(toDelete).forEach { child ->
                updates["aqi_history/$city/${child.key}"] = null
                deleteCount++
            }

            db.reference.updateChildren(updates).await()
            Log.d(TAG, "Pruned $deleteCount old entries for $city")
            Result.success(deleteCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear all alerts.
     */
    suspend fun clearAllAlerts(): Result<Unit> {
        return deleteNode("alerts")
    }

    // ====================================================================
    //  7. OFFLINE SUPPORT
    // ====================================================================

    /**
     * Keep specific data synced offline (even when app restarts).
     * Call this once during app initialization.
     */
    fun enableOfflineSync() {
        // Keep live AQI data cached offline
        db.getReference("live_aqi").keepSynced(true)
        // Keep alerts cached offline
        db.getReference("alerts").keepSynced(true)
        Log.d(TAG, "Offline sync enabled for live_aqi and alerts")
    }

    /**
     * Force go offline (for testing or saving bandwidth).
     */
    fun goOffline() {
        db.goOffline()
    }

    /**
     * Force go online.
     */
    fun goOnline() {
        db.goOnline()
    }
}

