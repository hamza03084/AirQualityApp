package com.example.airqualityapp.data.repository

import com.google.firebase.firestore.FirebaseFirestore
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
    suspend fun getAqiDatasets(): List<Map<String, Any>> {
        return try {
            val snapshot = firestore.collection("aqi_datasets").get().await()
            snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addAqiDataEntry(data: Map<String, Any>) {
        firestore.collection("aqi_datasets").add(data).await()
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

