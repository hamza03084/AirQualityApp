package com.example.airqualityapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "citizen", // "citizen" or "admin"
    val fcmToken: String = "",
    val alertThreshold: Int = 150,
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
        val uid = auth.currentUser?.uid ?: return null
        return getUserProfile(uid)
    }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    suspend fun updateUserRole(uid: String, role: String) {
        usersCollection.document(uid).update("role", role).await()
    }

    suspend fun updateAlertThreshold(uid: String, threshold: Int) {
        usersCollection.document(uid).update("alertThreshold", threshold).await()
    }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    suspend fun updateFcmToken(uid: String, token: String) {
        usersCollection.document(uid).update("fcmToken", token).await()
    }

    suspend fun getAllUsers(): List<UserProfile> {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.toObjects(UserProfile::class.java)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUserCount(): Int {
        return try {
            val snapshot = usersCollection.get().await()
            snapshot.size()
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
}
