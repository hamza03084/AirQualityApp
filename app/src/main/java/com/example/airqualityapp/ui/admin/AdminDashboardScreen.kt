package com.example.airqualityapp.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.airqualityapp.data.repository.AdminRepository
import com.example.airqualityapp.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onNavigateToUsers: () -> Unit,
    onNavigateToThresholds: () -> Unit,
    onNavigateToRecommendations: () -> Unit,
    onNavigateToDatasets: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userRepository = remember { UserRepository() }
    val adminRepository = remember { AdminRepository() }

    var totalUsers by remember { mutableIntStateOf(0) }
    var totalFeedback by remember { mutableIntStateOf(0) }
    var totalDatasets by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        totalUsers = userRepository.getUserCount()
        totalFeedback = try { adminRepository.getFeedbackList().size } catch (e: Exception) { 0 }
        totalDatasets = try { adminRepository.getAqiDatasets().size } catch (e: Exception) { 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Admin Dashboard", fontWeight = FontWeight.Bold)
                        Text("Management Panel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            // Clear CredentialManager state so a future Google sign-in
                            // doesn't hang on stale credentials after repeated logouts.
                            runCatching {
                                CredentialManager.create(context)
                                    .clearCredentialState(ClearCredentialStateRequest())
                            }
                            FirebaseAuth.getInstance().signOut()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Summary Cards
            Text("Overview", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Users",
                    value = "$totalUsers",
                    icon = Icons.Default.People,
                    color = Color(0xFF1976D2)
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Datasets",
                    value = "$totalDatasets",
                    icon = Icons.Default.Storage,
                    color = Color(0xFF388E3C)
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Feedback",
                    value = "$totalFeedback",
                    icon = Icons.Default.Feedback,
                    color = Color(0xFFF57C00)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Management Options
            Text("Management", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))

            AdminMenuCard(
                title = "User Management",
                description = "View and manage registered users, monitor activity",
                icon = Icons.Default.People,
                color = Color(0xFF1976D2),
                onClick = onNavigateToUsers
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminMenuCard(
                title = "AQI Thresholds",
                description = "Configure default AQI alert thresholds",
                icon = Icons.Default.Tune,
                color = Color(0xFFE53935),
                onClick = onNavigateToThresholds
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminMenuCard(
                title = "Health Recommendations",
                description = "Update health recommendations for each AQI level",
                icon = Icons.Default.HealthAndSafety,
                color = Color(0xFF43A047),
                onClick = onNavigateToRecommendations
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminMenuCard(
                title = "AQI Datasets",
                description = "Manage historical AQI datasets",
                icon = Icons.Default.Storage,
                color = Color(0xFF7B1FA2),
                onClick = onNavigateToDatasets
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminMenuCard(
                title = "User Feedback",
                description = "View feedback and issue reports from users",
                icon = Icons.Default.Feedback,
                color = Color(0xFFF57C00),
                onClick = onNavigateToFeedback
            )

            Spacer(modifier = Modifier.height(12.dp))

            AdminMenuCard(
                title = "Activity Monitor",
                description = "View recent manual submissions and alerts sent",
                icon = Icons.Default.Timeline,
                color = Color(0xFF00838F),
                onClick = onNavigateToActivity
            )
        }
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
            Text(title, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

