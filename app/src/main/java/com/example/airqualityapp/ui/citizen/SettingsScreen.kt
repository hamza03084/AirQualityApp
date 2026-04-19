package com.example.airqualityapp.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    val trainedCities = remember {
        runCatching {
            context.assets.open("model_cities.txt").bufferedReader().use { it.readText() }
                .lines().map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var alertThreshold by remember { mutableStateOf(150f) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var preferredCity by remember { mutableStateOf("Lahore") }
    var cityMenuExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val profile = userRepository.getCurrentUserProfile()
        userProfile = profile
        alertThreshold = (profile?.alertThreshold ?: 150).toFloat()
        notificationsEnabled = profile?.notificationsEnabled ?: true
        preferredCity = profile?.preferredCity?.takeIf { it.isNotBlank() } ?: "Lahore"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // Delete confirmation dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Profile", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to permanently delete your account? " +
                        "This action cannot be undone and all your data will be lost."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            isDeleting = true
                            deleteError = ""
                            scope.launch {
                                try {
                                    userRepository.deleteUserAccount()
                                    isDeleting = false
                                    onLogout()
                                } catch (e: Exception) {
                                    isDeleting = false
                                    deleteError = e.message ?: "Failed to delete account. Please re-login and try again."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // User Profile Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            userProfile?.name ?: auth.currentUser?.displayName ?: "User",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            auth.currentUser?.email ?: "",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            "Role: ${userProfile?.role?.replaceFirstChar { it.uppercase() } ?: "Citizen"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!userProfile?.phone.isNullOrBlank()) {
                            Text(
                                "Phone: ${userProfile?.phone}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Alert Settings
            Text("Alert Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Push Notifications")
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("AQI Alert Threshold: ${alertThreshold.toInt()}", fontWeight = FontWeight.Medium)
                    Text(
                        "You'll receive alerts when AQI exceeds this value",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Slider(
                        value = alertThreshold,
                        onValueChange = { alertThreshold = it },
                        valueRange = 50f..300f,
                        steps = 5
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Preferred City", fontWeight = FontWeight.Medium)
                    Text(
                        "Background AQI fetches use this city",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val cityOptions = trainedCities.ifEmpty {
                        listOf("Islamabad", "Karachi", "Lahore", "Peshawar", "Quetta")
                    }
                    ExposedDropdownMenuBox(
                        expanded = cityMenuExpanded,
                        onExpandedChange = { cityMenuExpanded = !cityMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = preferredCity,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = cityMenuExpanded,
                            onDismissRequest = { cityMenuExpanded = false }
                        ) {
                            cityOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        preferredCity = option
                                        cityMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val uid = auth.currentUser?.uid ?: ""
                                if (uid.isNotEmpty()) {
                                    userRepository.updatePreferences(
                                        uid,
                                        alertThreshold.toInt(),
                                        notificationsEnabled,
                                        preferredCity
                                    )
                                }
                                isSaving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    ) {
                        Text(if (isSaving) "Saving..." else "Save Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About Section
            Text("About", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Air Quality Monitor", fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Smart AQI Prediction and Alert System for Android. " +
                        "Uses machine learning to predict Air Quality Index based on " +
                        "weather parameters and historical data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (trainedCities.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Trained on: ${trainedCities.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Delete Profile – citizens only (admins cannot delete their profile)
            if (userProfile?.role == "citizen") {
                if (deleteError.isNotEmpty()) {
                    Text(
                        text = deleteError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    enabled = !isDeleting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete My Profile", color = Color.White, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Logout Button
            OutlinedButton(
                onClick = {
                    auth.signOut()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", color = Color.Red, fontSize = 16.sp)
            }
        }
    }
}

