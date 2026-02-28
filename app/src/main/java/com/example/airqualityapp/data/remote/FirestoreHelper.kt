package com.example.airqualityapp.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * ============================================================================
 *  FirestoreHelper — Comprehensive Firestore CRUD Operations
 * ============================================================================
 *
 *  This singleton provides reusable methods for all Firestore operations
 *  used throughout the AirQualityApp.
 *
 *  INITIALIZATION:
 *  ----------------
 *  Firebase Firestore is automatically initialized when you call
 *  FirebaseFirestore.getInstance() — no manual init is needed as long as
 *  google-services.json is in app/ and the google-services plugin is applied.
 *
 *  DATA MODEL DESIGN (for this app):
 *  ----------------------------------
 *  Firestore Structure:
 *
 *  users/                          ← Collection
 *    ├── {userId}/                 ← Document (keyed by Firebase Auth UID)
 *    │     ├── uid: String
 *    │     ├── email: String
 *    │     ├── name: String
 *    │     ├── role: "citizen" | "admin"
 *    │     ├── fcmToken: String
 *    │     ├── alertThreshold: Int
 *    │     └── createdAt: Long
 *    │
 *  aqi_readings/                   ← Collection
 *    ├── {autoId}/                 ← Document (auto-generated ID)
 *    │     ├── aqi: Int
 *    │     ├── pm25: Double
 *    │     ├── pm10: Double
 *    │     ├── temperature: Double
 *    │     ├── humidity: Double
 *    │     ├── windSpeed: Double
 *    │     ├── city: String
 *    │     ├── source: "api" | "manual"
 *    │     ├── userId: String
 *    │     └── timestamp: Long
 *    │
 *  manual_submissions/             ← Collection
 *    ├── {autoId}/
 *    │     ├── pm25, pm10, temperature, humidity, windSpeed
 *    │     ├── city: String
 *    │     ├── predictedAqi: Int
 *    │     ├── category: String
 *    │     ├── userId: String
 *    │     └── timestamp: Long
 *    │
 *  config/                         ← Collection (singleton docs)
 *    └── thresholds/               ← Document
 *          ├── goodMax: Int
 *          ├── moderateMax: Int
 *          ├── unhealthySensitiveMax: Int
 *          ├── unhealthyMax: Int
 *          ├── veryUnhealthyMax: Int
 *          └── alertThreshold: Int
 *    │
 *  recommendations/                ← Collection
 *    ├── Good/                     ← Document (keyed by category name)
 *    │     ├── category: String
 *    │     ├── recommendation: String
 *    │     └── healthImpact: String
 *    ├── Moderate/ ...
 *    └── Hazardous/ ...
 *    │
 *  feedback/                       ← Collection
 *    ├── {autoId}/
 *    │     ├── subject: String
 *    │     ├── message: String
 *    │     ├── userId: String
 *    │     ├── email: String
 *    │     └── timestamp: Long
 *    │
 *  aqi_datasets/                   ← Collection (admin-managed historical data)
 *    ├── {autoId}/
 *    │     ├── city, aqi, pm25, pm10, temperature, humidity, windSpeed
 *    │     └── timestamp: Long
 */
object FirestoreHelper {

    private const val TAG = "FirestoreHelper"

    // Get Firestore instance (auto-initialized via google-services.json)
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ====================================================================
    //  1. CREATE Operations
    // ====================================================================

    /**
     * Create a document with a SPECIFIC ID.
     * Use when you want to control the document ID (e.g., user profile keyed by UID).
     *
     * Example: createDocument("users", userId, userProfile)
     */
    suspend fun createDocument(
        collection: String,
        documentId: String,
        data: Any
    ): Result<String> {
        return try {
            db.collection(collection).document(documentId).set(data).await()
            Log.d(TAG, "Document created: $collection/$documentId")
            Result.success(documentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating document: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Create a document with an AUTO-GENERATED ID.
     * Use for collections like feedback, aqi_readings, etc.
     *
     * Example: addDocument("feedback", feedbackMap)
     */
    suspend fun addDocument(
        collection: String,
        data: Any
    ): Result<String> {
        return try {
            val docRef = db.collection(collection).add(data).await()
            Log.d(TAG, "Document added: $collection/${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding document to $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Create a document with MERGE option (won't overwrite existing fields).
     * Use when updating a document that may or may not exist.
     *
     * Example: mergeDocument("users", uid, mapOf("fcmToken" to token))
     */
    suspend fun mergeDocument(
        collection: String,
        documentId: String,
        data: Map<String, Any>
    ): Result<String> {
        return try {
            db.collection(collection).document(documentId)
                .set(data, SetOptions.merge()).await()
            Log.d(TAG, "Document merged: $collection/$documentId")
            Result.success(documentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error merging document: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Batch write multiple documents at once (atomic operation).
     * Use for syncing offline data to Firestore.
     *
     * Example:
     *   val items = listOf(mapOf("aqi" to 150), mapOf("aqi" to 200))
     *   batchCreate("aqi_readings", items)
     */
    suspend fun batchCreate(
        collection: String,
        dataList: List<Map<String, Any>>
    ): Result<Int> {
        return try {
            val batch = db.batch()
            dataList.forEach { data ->
                val docRef = db.collection(collection).document()
                batch.set(docRef, data)
            }
            batch.commit().await()
            Log.d(TAG, "Batch created ${dataList.size} docs in $collection")
            Result.success(dataList.size)
        } catch (e: Exception) {
            Log.e(TAG, "Batch create failed for $collection", e)
            Result.failure(e)
        }
    }

    // ====================================================================
    //  2. READ Operations
    // ====================================================================

    /**
     * Read a SINGLE document by ID.
     *
     * Example:
     *   val user = readDocument("users", uid, UserProfile::class.java)
     */
    suspend fun <T> readDocument(
        collection: String,
        documentId: String,
        clazz: Class<T>
    ): Result<T?> {
        return try {
            val snapshot = db.collection(collection).document(documentId).get().await()
            val obj = snapshot.toObject(clazz)
            Result.success(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading document: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Read a single document as a raw Map.
     *
     * Example:
     *   val data = readDocumentAsMap("config", "thresholds")
     */
    suspend fun readDocumentAsMap(
        collection: String,
        documentId: String
    ): Result<Map<String, Any>?> {
        return try {
            val snapshot = db.collection(collection).document(documentId).get().await()
            Result.success(snapshot.data)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading document: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Read ALL documents in a collection.
     *
     * Example:
     *   val users = readCollection("users", UserProfile::class.java)
     */
    suspend fun <T> readCollection(
        collection: String,
        clazz: Class<T>
    ): Result<List<T>> {
        return try {
            val snapshot = db.collection(collection).get().await()
            val list = snapshot.toObjects(clazz)
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading collection: $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Query documents with conditions.
     *
     * Example:
     *   val admins = queryDocuments(
     *       collection = "users",
     *       clazz = UserProfile::class.java,
     *       field = "role",
     *       value = "admin"
     *   )
     */
    suspend fun <T> queryDocuments(
        collection: String,
        clazz: Class<T>,
        field: String,
        value: Any
    ): Result<List<T>> {
        return try {
            val snapshot = db.collection(collection)
                .whereEqualTo(field, value)
                .get().await()
            val list = snapshot.toObjects(clazz)
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying $collection where $field == $value", e)
            Result.failure(e)
        }
    }

    /**
     * Query documents with ordering and limit.
     *
     * Example:
     *   val recentReadings = queryDocumentsOrdered(
     *       collection = "aqi_readings",
     *       clazz = AqiReadingData::class.java,
     *       orderByField = "timestamp",
     *       descending = true,
     *       limit = 24
     *   )
     */
    suspend fun <T> queryDocumentsOrdered(
        collection: String,
        clazz: Class<T>,
        orderByField: String,
        descending: Boolean = true,
        limit: Long = 50
    ): Result<List<T>> {
        return try {
            val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
            val snapshot = db.collection(collection)
                .orderBy(orderByField, direction)
                .limit(limit)
                .get().await()
            val list = snapshot.toObjects(clazz)
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying ordered $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Query documents within a range (e.g., timestamp-based).
     *
     * Example:
     *   val weekData = queryDocumentsInRange(
     *       collection = "aqi_readings",
     *       clazz = AqiReadingData::class.java,
     *       field = "timestamp",
     *       startValue = weekAgoMillis,
     *       endValue = nowMillis
     *   )
     */
    suspend fun <T> queryDocumentsInRange(
        collection: String,
        clazz: Class<T>,
        field: String,
        startValue: Any,
        endValue: Any
    ): Result<List<T>> {
        return try {
            val snapshot = db.collection(collection)
                .whereGreaterThanOrEqualTo(field, startValue)
                .whereLessThanOrEqualTo(field, endValue)
                .orderBy(field, Query.Direction.ASCENDING)
                .get().await()
            val list = snapshot.toObjects(clazz)
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error range querying $collection", e)
            Result.failure(e)
        }
    }

    /**
     * Listen to a document in REAL-TIME (returns a Flow).
     *
     * Example:
     *   firestoreHelper.listenToDocument("config", "thresholds", AqiThreshold::class.java)
     *       .collect { threshold -> updateUI(threshold) }
     */
    fun <T> listenToDocument(
        collection: String,
        documentId: String,
        clazz: Class<T>
    ): Flow<T?> = callbackFlow {
        val registration: ListenerRegistration = db.collection(collection)
            .document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen error: $collection/$documentId", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                val obj = snapshot?.toObject(clazz)
                trySend(obj)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Listen to a collection in REAL-TIME (returns a Flow).
     *
     * Example:
     *   firestoreHelper.listenToCollection("feedback", FeedbackData::class.java)
     *       .collect { feedbackList -> updateUI(feedbackList) }
     */
    fun <T> listenToCollection(
        collection: String,
        clazz: Class<T>
    ): Flow<List<T>> = callbackFlow {
        val registration: ListenerRegistration = db.collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen error: $collection", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(clazz) ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    // ====================================================================
    //  3. UPDATE Operations
    // ====================================================================

    /**
     * Update SPECIFIC fields of a document (doesn't overwrite other fields).
     *
     * Example:
     *   updateFields("users", uid, mapOf("role" to "admin", "alertThreshold" to 100))
     */
    suspend fun updateFields(
        collection: String,
        documentId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        return try {
            db.collection(collection).document(documentId).update(fields).await()
            Log.d(TAG, "Fields updated: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating fields: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Update a SINGLE field.
     *
     * Example:
     *   updateField("users", uid, "alertThreshold", 200)
     */
    suspend fun updateField(
        collection: String,
        documentId: String,
        field: String,
        value: Any
    ): Result<Unit> {
        return try {
            db.collection(collection).document(documentId).update(field, value).await()
            Log.d(TAG, "Field updated: $collection/$documentId.$field")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating field: $collection/$documentId.$field", e)
            Result.failure(e)
        }
    }

    /**
     * Increment a numeric field atomically.
     *
     * Example:
     *   incrementField("users", uid, "loginCount", 1)
     */
    suspend fun incrementField(
        collection: String,
        documentId: String,
        field: String,
        incrementBy: Long
    ): Result<Unit> {
        return try {
            db.collection(collection).document(documentId)
                .update(field, FieldValue.increment(incrementBy)).await()
            Log.d(TAG, "Field incremented: $collection/$documentId.$field += $incrementBy")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing: $collection/$documentId.$field", e)
            Result.failure(e)
        }
    }

    /**
     * Replace an ENTIRE document (overwrites all fields).
     *
     * Example:
     *   replaceDocument("config", "thresholds", newThresholds)
     */
    suspend fun replaceDocument(
        collection: String,
        documentId: String,
        data: Any
    ): Result<Unit> {
        return try {
            db.collection(collection).document(documentId).set(data).await()
            Log.d(TAG, "Document replaced: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing document: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    // ====================================================================
    //  4. DELETE Operations
    // ====================================================================

    /**
     * Delete a single document.
     *
     * Example:
     *   deleteDocument("aqi_datasets", datasetId)
     */
    suspend fun deleteDocument(
        collection: String,
        documentId: String
    ): Result<Unit> {
        return try {
            db.collection(collection).document(documentId).delete().await()
            Log.d(TAG, "Document deleted: $collection/$documentId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting document: $collection/$documentId", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a specific field from a document.
     *
     * Example:
     *   deleteField("users", uid, "fcmToken")
     */
    suspend fun deleteField(
        collection: String,
        documentId: String,
        field: String
    ): Result<Unit> {
        return try {
            db.collection(collection).document(documentId)
                .update(field, FieldValue.delete()).await()
            Log.d(TAG, "Field deleted: $collection/$documentId.$field")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting field: $collection/$documentId.$field", e)
            Result.failure(e)
        }
    }

    /**
     * Batch delete multiple documents.
     *
     * Example:
     *   batchDelete("aqi_readings", listOf("docId1", "docId2", "docId3"))
     */
    suspend fun batchDelete(
        collection: String,
        documentIds: List<String>
    ): Result<Int> {
        return try {
            val batch = db.batch()
            documentIds.forEach { id ->
                batch.delete(db.collection(collection).document(id))
            }
            batch.commit().await()
            Log.d(TAG, "Batch deleted ${documentIds.size} docs from $collection")
            Result.success(documentIds.size)
        } catch (e: Exception) {
            Log.e(TAG, "Batch delete failed for $collection", e)
            Result.failure(e)
        }
    }

    // ====================================================================
    //  5. UTILITY Operations
    // ====================================================================

    /**
     * Check if a document exists.
     *
     * Example:
     *   val exists = documentExists("users", uid)
     */
    suspend fun documentExists(
        collection: String,
        documentId: String
    ): Boolean {
        return try {
            val snapshot = db.collection(collection).document(documentId).get().await()
            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the count of documents in a collection.
     *
     * Example:
     *   val count = getCollectionCount("users")
     */
    suspend fun getCollectionCount(collection: String): Int {
        return try {
            val snapshot = db.collection(collection).get().await()
            snapshot.size()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get a reference to a document (useful for transactions).
     */
    fun getDocRef(collection: String, documentId: String): DocumentReference {
        return db.collection(collection).document(documentId)
    }

    /**
     * Run a Firestore transaction (atomic read + write).
     *
     * Example:
     *   runTransaction { transaction ->
     *       val docRef = getDocRef("users", uid)
     *       val snapshot = transaction.get(docRef)
     *       val currentThreshold = snapshot.getLong("alertThreshold") ?: 150
     *       transaction.update(docRef, "alertThreshold", currentThreshold + 10)
     *   }
     */
    suspend fun <T> runTransaction(
        block: (com.google.firebase.firestore.Transaction) -> T
    ): Result<T> {
        return try {
            val result = db.runTransaction(block).await()
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Transaction failed", e)
            Result.failure(e)
        }
    }
}

