package com.example.airqualityapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import androidx.work.*
import com.example.airqualityapp.data.repository.UserRepository
import com.example.airqualityapp.data.worker.AqiFetchWorker
import com.example.airqualityapp.data.worker.SyncWorker
import com.example.airqualityapp.navigation.AppNavGraph
import com.example.airqualityapp.navigation.Screen
import com.example.airqualityapp.ui.theme.AirQualityAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        askNotificationPermission()

        // Subscribe to FCM topic for AQI alerts
        FirebaseMessaging.getInstance().subscribeToTopic("aqi_alerts")

        // Schedule periodic workers
        schedulePeriodicWorkers()

        setContent {
            AirQualityAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val auth = FirebaseAuth.getInstance()
                    val userRepository = remember { UserRepository() }
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    // Determine start destination based on auth state
                    LaunchedEffect(Unit) {
                        val currentUser = auth.currentUser
                        if (currentUser != null) {
                            val profile = userRepository.getCurrentUserProfile()
                            startDestination = if (profile?.role == "admin") {
                                Screen.AdminDashboard.route
                            } else {
                                Screen.CitizenHome.route
                            }
                        } else {
                            startDestination = Screen.Login.route
                        }
                    }

                    if (startDestination != null) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination!!,
                            context = this@MainActivity
                        )
                    }
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun schedulePeriodicWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Periodic AQI data fetch (every 60 minutes)
        val fetchConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val fetchRequest = PeriodicWorkRequestBuilder<AqiFetchWorker>(60, TimeUnit.MINUTES)
            .setConstraints(fetchConstraints)
            .setInputData(workDataOf("city" to "Lahore", "threshold" to 150))
            .build()

        workManager.enqueueUniquePeriodicWork(
            "aqi_fetch_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            fetchRequest
        )

        // Periodic sync of offline data (every 30 minutes)
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(syncConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "sync_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
