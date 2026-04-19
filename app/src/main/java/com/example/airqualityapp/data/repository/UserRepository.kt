package com.example.airqualityapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val age: Int = 0,
    val gender: String = "",
    val phone: String = "",
    val role: String = "citizen", // "citizen" or "admin"
    val fcmToken: String = "",
    val alertThreshold: Int = 150,
    val notificationsEnabled: Boolean = true,
    val preferredCity: String = "Lahore",
    val createdAt: Long = System.currentTimeMillis()
)

class UserRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = firestore.collection("users")

    suspend fun createUserProfile(profile: UserProfile) {
        usersCollection.document(profile.uid).set(profile).await()
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val doc = usersCollection.document(uid).get().await()
            doc.toObject(UserProfile::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getCurrentUserProfile(): UserProfile? {
        val user = awaitAuthenticatedUser() ?: return null
        return getUserProfile(user.uid)
    }

    suspend fun getOrCreateCurrentUserProfile(defaultRole: String = "citizen"): UserProfile? {
        val user = awaitAuthenticatedUser() ?: return null
        return getUserProfile(user.uid) ?: UserProfile(
            uid = user.uid,
            email = user.email ?: "",
            name = user.displayName ?: "",
            role = defaultRole
        ).also { createUserProfile(it) }
    }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    suspend fun updateUserRole(uid: String, role: String) {
        usersCollection.document(uid)
            .set(mapOf("role" to role), SetOptions.merge())
            .await()
    }

    suspend fun updateAlertThreshold(uid: String, threshold: Int) {
        usersCollection.document(uid)
            .set(mapOf("alertThreshold" to threshold), SetOptions.merge())
            .await()
    }

    suspend fun updatePreferences(
        uid: String,
        threshold: Int,
        notificationsEnabled: Boolean,
        preferredCity: String
    ) {
        usersCollection.document(uid).set(
            mapOf(
                "alertThreshold" to threshold,
                "notificationsEnabled" to notificationsEnabled,
                "preferredCity" to preferredCity
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun updateUserProfile(uid: String, name: String, age: Int, gender: String, phone: String) {
        usersCollection.document(uid).set(
            mapOf(
                "name" to name,
                "age" to age,
                "gender" to gender,
                "phone" to phone
            ),
            SetOptions.merge()
        ).await()
    }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    suspend fun updateFcmToken(uid: String, token: String) {
        usersCollection.document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .await()
    }

    suspend fun getAllUsers(): List<UserProfile> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.toObjects(UserProfile::class.java)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAllCitizens(): List<UserProfile> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("role", "citizen")
                .get()
                .await()
            snapshot.toObjects(UserProfile::class.java)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUserCount(): Int {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("role", "citizen")
                .count()
                .get(AggregateSource.SERVER)
                .await()
            snapshot.count.toInt()
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Deletes the current user's Firestore profile document and their Firebase Auth account.
     * Should only be called for citizens.
     */
    suspend fun deleteUserAccount() {
        val user = auth.currentUser ?: throw Exception("No user logged in")
        // Delete Firestore profile
        usersCollection.document(user.uid).delete().await()
        // Delete Firebase Auth account
        user.delete().await()
    }

    private suspend fun awaitAuthenticatedUser(): FirebaseUser? {
        val currentUser = auth.currentUser ?: return null
        currentUser.getIdToken(false).await()
        return currentUser
    }
}
