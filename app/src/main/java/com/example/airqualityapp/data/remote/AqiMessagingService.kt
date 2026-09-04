package com.example.airqualityapp.data.remote

import com.example.airqualityapp.util.AlertNotifier
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AqiMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "AQI Alert"
        val body = message.notification?.body ?: message.data["body"] ?: "Air quality has changed."
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        AlertNotifier.showNotification(applicationContext, title, body)
    }
}

