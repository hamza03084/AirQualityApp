package com.example.airqualityapp.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.repository.AdminRepository
import com.example.airqualityapp.data.repository.HealthRecommendation
import com.example.airqualityapp.util.AqiClassifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRecommendationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val adminRepository = remember { AdminRepository() }
    val scope = rememberCoroutineScope()

    var recommendations by remember { mutableStateOf<List<HealthRecommendation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var editRecommendation by remember { mutableStateOf("") }
    var editHealthImpact by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        recommendations = adminRepository.getRecommendations()
        if (recommendations.isEmpty()) {
            adminRepository.initializeDefaultRecommendations()
            recommendations = adminRepository.getRecommendations()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Recommendations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Update Health Recommendations",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Edit recommendations shown to citizens for each AQI level",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(recommendations) { rec ->
                    val categoryInfo = try {
                        AqiClassifier.getAllCategories().find { it.third.label == rec.category }
                    } catch (e: Exception) { null }
                    val categoryColor = categoryInfo?.third?.color ?: Color.Gray

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = categoryColor.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    rec.category,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = categoryColor
                                )
                                IconButton(onClick = {
                                    editingCategory = rec.category
                                    editRecommendation = rec.recommendation
                                    editHealthImpact = rec.healthImpact
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = categoryColor)
                                }
                            }

                            if (editingCategory == rec.category) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editHealthImpact,
                                    onValueChange = { editHealthImpact = it },
                                    label = { Text("Health Impact") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editRecommendation,
                                    onValueChange = { editRecommendation = it },
                                    label = { Text("Recommendation") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (editRecommendation.isBlank() || editHealthImpact.isBlank()) {
                                                Toast.makeText(context, "Health impact and recommendation cannot be empty", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                try {
                                                    adminRepository.updateRecommendation(
                                                        rec.category,
                                                        HealthRecommendation(
                                                            category = rec.category,
                                                            recommendation = editRecommendation,
                                                            healthImpact = editHealthImpact
                                                        )
                                                    )
                                                    recommendations = adminRepository.getRecommendations()
                                                    editingCategory = null
                                                    Toast.makeText(context, "Updated – citizens now see this text", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save")
                                    }
                                    TextButton(onClick = { editingCategory = null }) {
                                        Text("Cancel")
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Health Impact: ${rec.healthImpact}", fontSize = 13.sp)
                                Text("Recommendation: ${rec.recommendation}", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

