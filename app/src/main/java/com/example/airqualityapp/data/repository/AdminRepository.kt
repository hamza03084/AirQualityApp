package com.example.airqualityapp.data.repository

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class AqiThreshold(
    val goodMax: Int = 50,
    val moderateMax: Int = 100,
    val unhealthySensitiveMax: Int = 150,
    val unhealthyMax: Int = 200,
    val veryUnhealthyMax: Int = 300,
    val alertThreshold: Int = 150
)

data class HealthRecommendation(
    val category: String = "",
    val recommendation: String = "",
    val healthImpact: String = ""
)

class AdminRepository {

    private val firestore = FirebaseFirestore.getInstance()

    // Threshold management
    suspend fun getThresholds(): AqiThreshold {
        return try {
            val doc = firestore.collection("config").document("thresholds").get().await()
            doc.toObject(AqiThreshold::class.java) ?: AqiThreshold()
        } catch (e: Exception) {
            AqiThreshold()
        }
    }

    suspend fun updateThresholds(thresholds: AqiThreshold) {
        firestore.collection("config").document("thresholds").set(thresholds).await()
    }

    // Health recommendations
    suspend fun getRecommendations(): List<HealthRecommendation> {
        return try {
            val snapshot = firestore.collection("recommendations").get().await()
            snapshot.toObjects(HealthRecommendation::class.java)
        } catch (e: Exception) {
            getDefaultRecommendations()
        }
    }

    suspend fun updateRecommendation(category: String, recommendation: HealthRecommendation) {
        firestore.collection("recommendations").document(category).set(recommendation).await()
    }

    suspend fun initializeDefaultRecommendations() {
        val defaults = getDefaultRecommendations()
        defaults.forEach { rec ->
            firestore.collection("recommendations")
                .document(rec.category)
                .set(rec)
                .await()
        }
    }

    private fun getDefaultRecommendations(): List<HealthRecommendation> {
        return listOf(
            HealthRecommendation("Good", "Safe to enjoy outdoor activities.", "No health risk."),
            HealthRecommendation("Moderate", "Sensitive people should reduce long outdoor stays.", "Minor risk for sensitive groups."),
            HealthRecommendation("Unhealthy for Sensitive Groups", "Limit outdoor activity if you're in a sensitive group.", "Some risk for sensitive groups."),
            HealthRecommendation("Unhealthy", "Avoid outdoor exercise; stay indoors if possible.", "Health effects possible for all."),
            HealthRecommendation("Very Unhealthy", "Stay indoors; use air purifiers if available.", "Serious health risks for everyone."),
            HealthRecommendation("Hazardous", "Avoid all outdoor activities; follow government alerts.", "Emergency: high risk for all.")
        )
    }

    // AQI Dataset management for admin
    /** Latest dataset entries (newest first, capped so large imports stay responsive). */
    suspend fun getAqiDatasets(limit: Long = 500): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection("aqi_datasets")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
            snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Server-side count of dataset entries (no documents downloaded). */
    suspend fun getDatasetCount(): Int = countCollection("aqi_datasets")

    /** Server-side count of feedback documents. */
    suspend fun getFeedbackCount(): Int = countCollection("feedback")

    private suspend fun countCollection(name: String): Int {
        return try {
            firestore.collection(name).count().get(AggregateSource.SERVER).await().count.toInt()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun addAqiDataEntry(data: Map<String, Any>) {
        firestore.collection("aqi_datasets").add(data).await()
    }

    /** Update (merge) an existing dataset entry. */
    suspend fun updateAqiDataEntry(id: String, data: Map<String, Any>) {
        firestore.collection("aqi_datasets").document(id).set(data, SetOptions.merge()).await()
    }

    /**
     * Bulk insert (CSV import) using atomic write batches of 400 documents.
     * @return number of documents written.
     */
    suspend fun addAqiDataEntries(entries: List<Map<String, Any>>, onProgress: (Int) -> Unit = {}): Int {
        var done = 0
        entries.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            val collection = firestore.collection("aqi_datasets")
            chunk.forEach { batch.set(collection.document(), it) }
            batch.commit().await()
            done += chunk.size
            onProgress(done)
        }
        return done
    }

    suspend fun deleteAqiDataEntry(id: String) {
        firestore.collection("aqi_datasets").document(id).delete().await()
    }

    // Feedback management
    suspend fun getFeedbackList(): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection("feedback")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get().await()
            snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

