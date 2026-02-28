package com.example.airqualityapp.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDatasetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val adminRepository = remember { AdminRepository() }
    val scope = rememberCoroutineScope()

    var datasets by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Add form fields
    var newCity by remember { mutableStateOf("") }
    var newAqi by remember { mutableStateOf("") }
    var newPm25 by remember { mutableStateOf("") }
    var newPm10 by remember { mutableStateOf("") }
    var newTemp by remember { mutableStateOf("") }
    var newHumidity by remember { mutableStateOf("") }
    var newWindSpeed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        datasets = adminRepository.getAqiDatasets()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQI Datasets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Entry")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Historical AQI Datasets",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(
                "Manage AQI data entries stored in Firebase (${datasets.size} entries)",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (datasets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No datasets yet", color = Color.Gray)
                        Text("Tap + to add AQI data entries", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(datasets) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "City: ${entry["city"] ?: "N/A"}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "AQI: ${entry["aqi"] ?: "N/A"} | PM2.5: ${entry["pm25"] ?: "N/A"} | PM10: ${entry["pm10"] ?: "N/A"}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        "Temp: ${entry["temperature"] ?: "N/A"}°C | Humidity: ${entry["humidity"] ?: "N/A"}% | Wind: ${entry["windSpeed"] ?: "N/A"} m/s",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    val timestamp = entry["timestamp"]
                                    if (timestamp is Long) {
                                        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                        Text(dateFormat.format(Date(timestamp)), fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val id = entry["id"]?.toString() ?: return@IconButton
                                        scope.launch {
                                            try {
                                                adminRepository.deleteAqiDataEntry(id)
                                                datasets = adminRepository.getAqiDatasets()
                                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add AQI Data Entry") },
            text = {
                Column {
                    OutlinedTextField(value = newCity, onValueChange = { newCity = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newAqi, onValueChange = { newAqi = it }, label = { Text("AQI") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newPm25, onValueChange = { newPm25 = it }, label = { Text("PM2.5") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = newPm10, onValueChange = { newPm10 = it }, label = { Text("PM10") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newTemp, onValueChange = { newTemp = it }, label = { Text("Temp °C") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = newHumidity, onValueChange = { newHumidity = it }, label = { Text("Humidity %") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newWindSpeed, onValueChange = { newWindSpeed = it }, label = { Text("Wind Speed m/s") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                adminRepository.addAqiDataEntry(mapOf(
                                    "city" to newCity,
                                    "aqi" to (newAqi.toIntOrNull() ?: 0),
                                    "pm25" to (newPm25.toDoubleOrNull() ?: 0.0),
                                    "pm10" to (newPm10.toDoubleOrNull() ?: 0.0),
                                    "temperature" to (newTemp.toDoubleOrNull() ?: 0.0),
                                    "humidity" to (newHumidity.toDoubleOrNull() ?: 0.0),
                                    "windSpeed" to (newWindSpeed.toDoubleOrNull() ?: 0.0),
                                    "timestamp" to System.currentTimeMillis()
                                ))
                                datasets = adminRepository.getAqiDatasets()
                                showAddDialog = false
                                newCity = ""; newAqi = ""; newPm25 = ""; newPm10 = ""
                                newTemp = ""; newHumidity = ""; newWindSpeed = ""
                                Toast.makeText(context, "Added!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

