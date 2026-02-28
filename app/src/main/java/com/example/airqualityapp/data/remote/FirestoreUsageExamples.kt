package com.example.airqualityapp.data.remote

import com.example.airqualityapp.data.repository.AqiThreshold
import com.example.airqualityapp.data.repository.HealthRecommendation
import com.example.airqualityapp.data.repository.UserProfile

/**
 * ============================================================================
 *  Firestore Usage Examples for AirQualityApp
 * ============================================================================
 *
 *  This file contains practical usage examples demonstrating every CRUD
 *  operation using FirestoreHelper. These can be called from any
 *  ViewModel, Repository, or Composable (within a coroutine scope).
 *
 *  All methods below are suspend functions — call them from:
 *    - LaunchedEffect { }
 *    - scope.launch { }
 *    - viewModelScope.launch { }
 */
object FirestoreUsageExamples {

    private val helper = FirestoreHelper

    // ====================================================================
    //  CREATE Examples
    // ====================================================================

    /**
     * Example 1: Create a user profile with a specific document ID (UID).
     */
    suspend fun createUserProfile() {
        val profile = UserProfile(
            uid = "user123",
            email = "citizen@example.com",
            name = "Ali Ahmed",
            role = "citizen",
            alertThreshold = 150
        )
        // Uses UID as document ID — so we can easily look up by auth UID
        val result = helper.createDocument("users", profile.uid, profile)
        result.onSuccess { id -> println("Created user: $id") }
        result.onFailure { e -> println("Error: ${e.message}") }
    }

    /**
     * Example 2: Add feedback with auto-generated ID.
     */
    suspend fun submitFeedback() {
        val feedback = mapOf(
            "subject" to "App Crash",
            "message" to "The app crashes when I tap refresh",
            "userId" to "user123",
            "email" to "citizen@example.com",
            "timestamp" to System.currentTimeMillis()
        )
        // Firestore auto-generates the document ID
        val result = helper.addDocument("feedback", feedback)
        result.onSuccess { id -> println("Feedback submitted: $id") }
    }

    /**
     * Example 3: Merge data into an existing document (safe upsert).
     */
    suspend fun updateFcmToken() {
        // Only updates fcmToken — other fields remain untouched
        val result = helper.mergeDocument(
            "users", "user123",
            mapOf("fcmToken" to "new-fcm-token-abc123")
        )
        result.onSuccess { println("Token merged") }
    }

    /**
     * Example 4: Batch create — sync multiple offline readings at once.
     */
    suspend fun syncOfflineReadings() {
        val readings = listOf(
            mapOf("aqi" to 120, "city" to "Lahore", "timestamp" to 1709100000000L),
            mapOf("aqi" to 85, "city" to "Islamabad", "timestamp" to 1709103600000L),
            mapOf("aqi" to 200, "city" to "Karachi", "timestamp" to 1709107200000L)
        )
        val result = helper.batchCreate("aqi_readings", readings)
        result.onSuccess { count -> println("Synced $count readings") }
    }

    // ====================================================================
    //  READ Examples
    // ====================================================================

    /**
     * Example 5: Read a single user profile by UID.
     */
    suspend fun getUserProfile() {
        val result = helper.readDocument("users", "user123", UserProfile::class.java)
        result.onSuccess { profile ->
            profile?.let {
                println("Name: ${it.name}, Role: ${it.role}, Threshold: ${it.alertThreshold}")
            } ?: println("User not found")
        }
    }

    /**
     * Example 6: Read thresholds config as a raw Map.
     */
    suspend fun readThresholds() {
        val result = helper.readDocumentAsMap("config", "thresholds")
        result.onSuccess { data ->
            println("Alert threshold: ${data?.get("alertThreshold")}")
        }
    }

    /**
     * Example 7: Read all users.
     */
    suspend fun getAllUsers() {
        val result = helper.readCollection("users", UserProfile::class.java)
        result.onSuccess { users ->
            println("Total users: ${users.size}")
            users.forEach { println("  - ${it.name} (${it.role})") }
        }
    }

    /**
     * Example 8: Query — find all admin users.
     */
    suspend fun getAdminUsers() {
        val result = helper.queryDocuments(
            collection = "users",
            clazz = UserProfile::class.java,
            field = "role",
            value = "admin"
        )
        result.onSuccess { admins ->
            println("Admins: ${admins.map { it.name }}")
        }
    }

    /**
     * Example 9: Query with ordering — get latest 10 feedback entries.
     */
    suspend fun getRecentFeedback() {
        // Note: This uses a data class — for Map-based results, use readCollection
        val result = helper.queryDocumentsOrdered(
            collection = "feedback",
            clazz = HashMap::class.java, // Or create a Feedback data class
            orderByField = "timestamp",
            descending = true,
            limit = 10
        )
        result.onSuccess { feedbackList ->
            println("Recent feedback count: ${feedbackList.size}")
        }
    }

    /**
     * Example 10: Range query — get AQI readings from last 7 days.
     */
    suspend fun getWeeklyReadings() {
        val now = System.currentTimeMillis()
        val weekAgo = now - (7 * 24 * 60 * 60 * 1000L)

        val result = helper.queryDocumentsInRange(
            collection = "aqi_readings",
            clazz = HashMap::class.java,
            field = "timestamp",
            startValue = weekAgo,
            endValue = now
        )
        result.onSuccess { readings ->
            println("Readings this week: ${readings.size}")
        }
    }

    /**
     * Example 11: Real-time listener — observe threshold changes.
     * Call this from a Composable:
     *
     *   val threshold by FirestoreHelper
     *       .listenToDocument("config", "thresholds", AqiThreshold::class.java)
     *       .collectAsState(initial = null)
     */
    fun observeThresholds() = helper.listenToDocument(
        "config", "thresholds", AqiThreshold::class.java
    )

    /**
     * Example 12: Real-time listener — observe all recommendations.
     */
    fun observeRecommendations() = helper.listenToCollection(
        "recommendations", HealthRecommendation::class.java
    )

    // ====================================================================
    //  UPDATE Examples
    // ====================================================================

    /**
     * Example 13: Update multiple fields at once.
     */
    suspend fun promoteToAdmin() {
        val result = helper.updateFields(
            "users", "user123",
            mapOf(
                "role" to "admin",
                "alertThreshold" to 100
            )
        )
        result.onSuccess { println("User promoted to admin") }
    }

    /**
     * Example 14: Update a single field.
     */
    suspend fun updateAlertThreshold() {
        val result = helper.updateField("users", "user123", "alertThreshold", 200)
        result.onSuccess { println("Threshold updated") }
    }

    /**
     * Example 15: Atomic increment.
     */
    suspend fun incrementLoginCount() {
        val result = helper.incrementField("users", "user123", "loginCount", 1)
        result.onSuccess { println("Login count incremented") }
    }

    /**
     * Example 16: Replace an entire document.
     */
    suspend fun replaceThresholds() {
        val newThresholds = AqiThreshold(
            goodMax = 50,
            moderateMax = 100,
            unhealthySensitiveMax = 150,
            unhealthyMax = 200,
            veryUnhealthyMax = 300,
            alertThreshold = 175
        )
        val result = helper.replaceDocument("config", "thresholds", newThresholds)
        result.onSuccess { println("Thresholds replaced") }
    }

    // ====================================================================
    //  DELETE Examples
    // ====================================================================

    /**
     * Example 17: Delete a single document.
     */
    suspend fun deleteFeedback() {
        val result = helper.deleteDocument("feedback", "feedbackDocId123")
        result.onSuccess { println("Feedback deleted") }
    }

    /**
     * Example 18: Delete a specific field from a document.
     */
    suspend fun removeFcmToken() {
        val result = helper.deleteField("users", "user123", "fcmToken")
        result.onSuccess { println("FCM token removed") }
    }

    /**
     * Example 19: Batch delete — remove multiple old readings.
     */
    suspend fun deleteOldReadings() {
        val idsToDelete = listOf("reading1", "reading2", "reading3")
        val result = helper.batchDelete("aqi_readings", idsToDelete)
        result.onSuccess { count -> println("Deleted $count readings") }
    }

    // ====================================================================
    //  UTILITY Examples
    // ====================================================================

    /**
     * Example 20: Check if a user profile exists.
     */
    suspend fun checkUserExists() {
        val exists = helper.documentExists("users", "user123")
        println("User exists: $exists")
    }

    /**
     * Example 21: Get total document count.
     */
    suspend fun getTotalUsers() {
        val count = helper.getCollectionCount("users")
        println("Total users: $count")
    }

    /**
     * Example 22: Run a transaction — safely read and update.
     */
    suspend fun safelyUpdateThreshold() {
        val result = helper.runTransaction { transaction ->
            val docRef = helper.getDocRef("users", "user123")
            val snapshot = transaction.get(docRef)
            val currentThreshold = snapshot.getLong("alertThreshold") ?: 150
            // Only increase, never decrease
            if (currentThreshold < 200) {
                transaction.update(docRef, "alertThreshold", currentThreshold + 25)
            }
            currentThreshold
        }
        result.onSuccess { oldValue -> println("Was: $oldValue, now increased by 25") }
    }
}

