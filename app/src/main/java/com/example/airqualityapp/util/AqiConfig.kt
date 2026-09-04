package com.example.airqualityapp.util

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.airqualityapp.data.repository.AdminRepository
import com.example.airqualityapp.data.repository.AqiThreshold
import com.example.airqualityapp.data.repository.HealthRecommendation
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Admin-managed configuration that the whole app reads at runtime:
 *  - AQI category thresholds   (Firestore config/thresholds)
 *  - health recommendations    (Firestore recommendations/{Category})
 *
 * The values are held in Compose state, so every screen that classifies an AQI
 * value re-renders automatically when the admin changes them. Firestore's offline
 * cache keeps the last known values available without a network connection.
 */
object AqiConfig {

    private const val TAG = "AqiConfig"

    var thresholds: AqiThreshold by mutableStateOf(AqiThreshold())
        private set

    var recommendations: Map<String, HealthRecommendation> by mutableStateOf(emptyMap())
        private set

    private var thresholdListener: ListenerRegistration? = null
    private var recommendationListener: ListenerRegistration? = null

    /** Attach real-time listeners (call once the user is signed in). */
    fun start() {
        if (thresholdListener != null) return
        val db = FirebaseFirestore.getInstance()

        thresholdListener = db.collection("config").document("thresholds")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Threshold listener error", error)
                    return@addSnapshotListener
                }
                snapshot?.toObject(AqiThreshold::class.java)?.let { thresholds = sanitize(it) }
            }

        recommendationListener = db.collection("recommendations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Recommendation listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    recommendations = snapshot.toObjects(HealthRecommendation::class.java)
                        .filter { it.category.isNotBlank() }
                        .associateBy { it.category }
                }
            }
        Log.d(TAG, "Config listeners started")
    }

    /** Detach listeners (on sign-out). Keeps the last values in memory. */
    fun stop() {
        thresholdListener?.remove(); thresholdListener = null
        recommendationListener?.remove(); recommendationListener = null
    }

    /** One-shot load for background workers where no listener is running. */
    suspend fun loadOnce() {
        val repo = AdminRepository()
        runCatching { thresholds = sanitize(repo.getThresholds()) }
            .onFailure { Log.w(TAG, "loadOnce thresholds failed", it) }
        runCatching {
            recommendations = repo.getRecommendations()
                .filter { it.category.isNotBlank() }
                .associateBy { it.category }
        }.onFailure { Log.w(TAG, "loadOnce recommendations failed", it) }
    }

    /** Thresholds must be positive and strictly ascending to define non-overlapping bands. */
    fun isValid(t: AqiThreshold): Boolean =
        t.goodMax > 0 &&
            t.goodMax < t.moderateMax &&
            t.moderateMax < t.unhealthySensitiveMax &&
            t.unhealthySensitiveMax < t.unhealthyMax &&
            t.unhealthyMax < t.veryUnhealthyMax &&
            t.alertThreshold > 0

    private fun sanitize(t: AqiThreshold): AqiThreshold = if (isValid(t)) t else AqiThreshold()
}
