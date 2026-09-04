package com.example.airqualityapp.ui.admin

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.airqualityapp.util.CsvDatasetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/** Form state shared by the Add and Edit dialogs. */
private data class DatasetForm(
    val id: String? = null,
    val city: String = "",
    val aqi: String = "",
    val pm25: String = "",
    val pm10: String = "",
    val temperature: String = "",
    val humidity: String = "",
    val windSpeed: String = ""
) {
    fun isValid() = city.isNotBlank() && aqi.toIntOrNull() != null

    fun toMap(): Map<String, Any> = mapOf(
        "city" to city.trim(),
        "aqi" to (aqi.toIntOrNull() ?: 0).coerceIn(0, 500),
        "pm25" to (pm25.toDoubleOrNull() ?: 0.0),
        "pm10" to (pm10.toDoubleOrNull() ?: 0.0),
        "temperature" to (temperature.toDoubleOrNull() ?: 0.0),
        "humidity" to (humidity.toDoubleOrNull() ?: 0.0),
        "windSpeed" to (windSpeed.toDoubleOrNull() ?: 0.0)
    )

    companion object {
        fun from(entry: Map<String, Any>) = DatasetForm(
            id = entry["id"]?.toString(),
            city = entry["city"]?.toString() ?: "",
            aqi = entry["aqi"]?.toString() ?: "",
            pm25 = entry["pm25"]?.toString() ?: "",
            pm10 = entry["pm10"]?.toString() ?: "",
            temperature = entry["temperature"]?.toString() ?: "",
            humidity = entry["humidity"]?.toString() ?: "",
            windSpeed = entry["windSpeed"]?.toString() ?: ""
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDatasetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val adminRepository = remember { AdminRepository() }
    val scope = rememberCoroutineScope()

    var datasets by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var form by remember { mutableStateOf<DatasetForm?>(null) }      // non-null → dialog open
    var pendingImport by remember { mutableStateOf<CsvDatasetParser.ParseResult?>(null) }
    var importProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // done / total
    var importCity by remember { mutableStateOf("") }

    suspend fun reload() {
        datasets = adminRepository.getAqiDatasets()
        totalCount = adminRepository.getDatasetCount()
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    // File picker for CSV import (Storage Access Framework – no storage permission needed)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { CsvDatasetParser.parse(it, importCity) }
                        ?: CsvDatasetParser.ParseResult(emptyList(), 0, 0, false, "Could not open file")
                }.getOrElse { CsvDatasetParser.ParseResult(emptyList(), 0, 0, false, it.message ?: "Failed to read file") }
            }
            if (result.error != null) {
                Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
            } else {
                pendingImport = result
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQI Datasets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "text/plain",
                                "application/vnd.ms-excel", "application/csv", "*/*")
                        )
                    },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                    text = { Text("Import CSV") }
                )
                FloatingActionButton(onClick = { form = DatasetForm() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                }
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
                "$totalCount entries stored in Firebase" +
                    if (datasets.size < totalCount) " (showing latest ${datasets.size})" else "",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Import a CSV (city, aqi, pm25, pm10, temperature, humidity, windSpeed, timestamp) " +
                    "or a Kaggle export – AQI is calculated from PM2.5 when missing.",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = importCity,
                onValueChange = { importCity = it },
                label = { Text("Default city for CSV rows without a city column") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            importProgress?.let { (done, total) ->
                Column {
                    Text("Importing… $done / $total", fontSize = 12.sp, color = Color.Gray)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

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
                        Text("Import a CSV file or tap + to add an entry", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    items(datasets) { entry ->
                        DatasetEntryCard(
                            entry = entry,
                            onEdit = { form = DatasetForm.from(entry) },
                            onDelete = {
                                val id = entry["id"]?.toString() ?: return@DatasetEntryCard
                                scope.launch {
                                    try {
                                        adminRepository.deleteAqiDataEntry(id)
                                        reload()
                                        Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit dialog
    form?.let { current ->
        DatasetFormDialog(
            form = current,
            onChange = { form = it },
            onDismiss = { form = null },
            onSave = {
                if (!current.isValid()) {
                    Toast.makeText(context, "City and a numeric AQI are required", Toast.LENGTH_SHORT).show()
                    return@DatasetFormDialog
                }
                scope.launch {
                    try {
                        val data = current.toMap() + ("timestamp" to System.currentTimeMillis())
                        if (current.id == null) {
                            adminRepository.addAqiDataEntry(data + ("source" to "manual"))
                            Toast.makeText(context, "Added!", Toast.LENGTH_SHORT).show()
                        } else {
                            adminRepository.updateAqiDataEntry(current.id, current.toMap())
                            Toast.makeText(context, "Updated!", Toast.LENGTH_SHORT).show()
                        }
                        form = null
                        reload()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // CSV import confirmation
    pendingImport?.let { result ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import CSV dataset") },
            text = {
                Column {
                    Text("Rows found: ${result.totalRows}")
                    Text("Valid rows to import: ${result.entries.size}")
                    if (result.skipped > 0) Text("Skipped (no AQI / PM2.5): ${result.skipped}", color = Color.Gray)
                    if (result.truncated) Text(
                        "Only the first ${CsvDatasetParser.MAX_ROWS} rows are imported per file (free-tier quota).",
                        color = Color(0xFFEF6C00), fontSize = 12.sp
                    )
                    result.entries.firstOrNull()?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("First row → city ${it["city"]}, AQI ${it["aqi"]}, PM2.5 ${it["pm25"]}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = result.entries.isNotEmpty(),
                    onClick = {
                        val entries = result.entries
                        pendingImport = null
                        scope.launch {
                            importProgress = 0 to entries.size
                            try {
                                adminRepository.addAqiDataEntries(entries) { done -> importProgress = done to entries.size }
                                Toast.makeText(context, "Imported ${entries.size} rows", Toast.LENGTH_LONG).show()
                                reload()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                importProgress = null
                            }
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DatasetEntryCard(entry: Map<String, Any>, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("City: ${entry["city"] ?: "N/A"}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "AQI: ${entry["aqi"] ?: "N/A"} | PM2.5: ${entry["pm25"] ?: "N/A"} | PM10: ${entry["pm10"] ?: "N/A"}",
                    fontSize = 12.sp, color = Color.Gray
                )
                Text(
                    "Temp: ${entry["temperature"] ?: "N/A"}°C | Humidity: ${entry["humidity"] ?: "N/A"}% | Wind: ${entry["windSpeed"] ?: "N/A"} m/s",
                    fontSize = 12.sp, color = Color.Gray
                )
                val timestamp = entry["timestamp"]
                if (timestamp is Long) {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    Text(
                        dateFormat.format(Date(timestamp)) +
                            (entry["source"]?.let { " · $it" } ?: ""),
                        fontSize = 11.sp, color = Color.Gray
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF1976D2))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        }
    }
}

@Composable
private fun DatasetFormDialog(
    form: DatasetForm,
    onChange: (DatasetForm) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.id == null) "Add AQI Data Entry" else "Edit AQI Data Entry") },
        text = {
            Column {
                OutlinedTextField(value = form.city, onValueChange = { onChange(form.copy(city = it)) }, label = { Text("City") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = form.aqi, onValueChange = { onChange(form.copy(aqi = it)) }, label = { Text("AQI") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = form.pm25, onValueChange = { onChange(form.copy(pm25 = it)) }, label = { Text("PM2.5") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = form.pm10, onValueChange = { onChange(form.copy(pm10 = it)) }, label = { Text("PM10") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = form.temperature, onValueChange = { onChange(form.copy(temperature = it)) }, label = { Text("Temp °C") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = form.humidity, onValueChange = { onChange(form.copy(humidity = it)) }, label = { Text("Humidity %") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = form.windSpeed, onValueChange = { onChange(form.copy(windSpeed = it)) }, label = { Text("Wind Speed m/s") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = onSave) { Text(if (form.id == null) "Add" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
