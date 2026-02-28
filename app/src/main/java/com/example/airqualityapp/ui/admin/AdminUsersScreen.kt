package com.example.airqualityapp.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(onBack: () -> Unit) {
    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filterRole by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        users = userRepository.getAllUsers()
        isLoading = false
    }

    val filteredUsers = remember(users, filterRole) {
        when (filterRole) {
            "Admin" -> users.filter { it.role == "admin" }
            "Citizen" -> users.filter { it.role == "citizen" }
            else -> users
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Citizen", "Admin").forEach { role ->
                    FilterChip(
                        selected = filterRole == role,
                        onClick = { filterRole = role },
                        label = { Text(role) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Total: ${filteredUsers.size} users", color = Color.Gray, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredUsers) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (user.role == "admin") Color(0xFFE53935) else Color(0xFF1976D2),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        user.name.ifBlank { "Unnamed" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(user.email, fontSize = 14.sp, color = Color.Gray)
                                    Text(
                                        "Role: ${user.role.replaceFirstChar { it.uppercase() }} | Threshold: ${user.alertThreshold}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                AssistChip(
                                    onClick = { },
                                    label = { Text(user.role.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

