package com.example.airqualityapp.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.remote.RealtimeDatabaseHelper
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin "Activity Monitor": manual data submissions, alerts sent, and user registrations
 * (the three activity streams named in the project brief).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminActivityScreen(onBack: () -> Unit) {
    val tabs = listOf("Submissions", "Alerts Sent", "Users")
    var selectedTab by remember { mutableIntStateOf(0) }

    var submissions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<RealtimeDatabaseHelper.AqiAlert>>(emptyList()) }
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("manual_submissions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
                .documents
                .map { doc -> doc.data?.plus("id" to doc.id) ?: emptyMap() }
        }.onSuccess { submissions = it }

        runCatching { RealtimeDatabaseHelper.readActiveAlerts().getOrNull() ?: emptyList() }
            .onSuccess { alerts = it }

        runCatching { UserRepository().getAllUsers().sortedByDescending { it.createdAt } }
            .onSuccess { users = it }

        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Monitor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActivityStat("Submissions", submissions.size.toString())
                ActivityStat("Active alerts", alerts.size.toString())
                ActivityStat("Users", users.size.toString())
                ActivityStat("Joined 7 days", users.count { System.currentTimeMillis() - it.createdAt < 7L * 24 * 3600 * 1000 }.toString())
            }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> SubmissionsList(submissions)
                    1 -> AlertsList(alerts)
                    2 -> UsersList(users)
                }
            }
        }
    }
}

@Composable
private fun ActivityStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun SubmissionsList(submissions: List<Map<String, Any>>) {
    if (submissions.isEmpty()) {
        EmptyState(icon = Icons.Default.Feedback, text = "No manual submissions yet")
        return
    }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${submissions.size} recent submissions",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(submissions) { entry ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "AQI ${entry["predictedAqi"] ?: "-"} · ${entry["category"] ?: "-"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        (entry["timestamp"] as? Long)?.let {
                            Text(dateFmt.format(Date(it)), fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Text(
                        "City: ${entry["city"] ?: "-"}",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        "PM2.5: ${entry["pm25"] ?: "-"}  PM10: ${entry["pm10"] ?: "-"}  T: ${entry["temperature"] ?: "-"}°C  H: ${entry["humidity"] ?: "-"}%  W: ${entry["windSpeed"] ?: "-"} m/s",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        "By: ${entry["userId"] ?: "unknown"}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertsList(alerts: List<RealtimeDatabaseHelper.AqiAlert>) {
    if (alerts.isEmpty()) {
        EmptyState(icon = Icons.Default.NotificationsActive, text = "No alerts sent yet")
        return
    }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${alerts.size} active alerts",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(alerts) { alert ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${alert.city} · AQI ${alert.aqi}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            dateFmt.format(Date(alert.timestamp)),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Text(alert.category, fontSize = 12.sp, color = Color.DarkGray)
                    Text(alert.message, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun UsersList(users: List<UserProfile>) {
    if (users.isEmpty()) {
        EmptyState(icon = Icons.Default.People, text = "No registered users yet")
        return
    }
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${users.size} registrations (newest first)",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(users) { user ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(user.name.ifBlank { "Unnamed" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            user.role.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color = if (user.role == "admin") Color(0xFFE53935) else Color(0xFF1976D2),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(user.email, fontSize = 12.sp, color = Color.DarkGray)
                    Text(
                        "Joined ${dateFmt.format(Date(user.createdAt))} · city ${user.preferredCity} · threshold ${user.alertThreshold}" +
                            if (!user.notificationsEnabled) " · notifications off" else "",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text, color = Color.Gray)
        }
    }
}
