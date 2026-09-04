package com.example.airqualityapp.ui.citizen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.local.AppDatabase
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.local.entity.ManualSubmissionEntity
import com.example.airqualityapp.ml.AqiPredictor
import com.example.airqualityapp.util.AqiClassifier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInputScreen(
    context: Context,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val predictor = remember { AqiPredictor(context) }

    var pm25 by remember { mutableStateOf("") }
    var pm10 by remember { mutableStateOf("") }
    var temperature by remember { mutableStateOf("") }
    var humidity by remember { mutableStateOf("") }
    var windSpeed by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    var predictedAqi by remember { mutableStateOf<Int?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual AQI Input") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Enter Environmental Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Input local measurements to get an instant AQI prediction",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // City
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City / Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // PM2.5
            OutlinedTextField(
                value = pm25,
                onValueChange = { pm25 = it },
                label = { Text("PM2.5 (µg/m³)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // PM10
            OutlinedTextField(
                value = pm10,
                onValueChange = { pm10 = it },
                label = { Text("PM10 (µg/m³)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Temperature
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature (°C)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Humidity
            OutlinedTextField(
                value = humidity,
                onValueChange = { humidity = it },
                label = { Text("Humidity (%)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Wind Speed
            OutlinedTextField(
                value = windSpeed,
                onValueChange = { windSpeed = it },
                label = { Text("Wind Speed (m/s)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Predict Button
            Button(
                onClick = {
                    val pm25Val = pm25.toDoubleOrNull()
                    val pm10Val = pm10.toDoubleOrNull()
                    val tempVal = temperature.toDoubleOrNull()
                    val humVal = humidity.toDoubleOrNull()
                    val windVal = windSpeed.toDoubleOrNull()

                    if (pm25Val == null || pm10Val == null || tempVal == null || humVal == null || windVal == null) {
                        Toast.makeText(context, "Please enter valid numeric values", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true

                    val aqi = predictor.predict(pm25Val, pm10Val, tempVal, humVal, windVal)
                    predictedAqi = aqi

                    val category = AqiClassifier.classify(aqi)
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                    scope.launch {
                        // Save as manual reading
                        db.aqiDao().insert(
                            AqiReadingEntity(
                                aqi = aqi,
                                pm25 = pm25Val,
                                pm10 = pm10Val,
                                temperature = tempVal,
                                humidity = humVal,
                                windSpeed = windVal,
                                city = city.ifBlank { "Manual Entry" },
                                source = "manual",
                                synced = false,
                                userId = uid
                            )
                        )

                        // Save manual submission
                        db.manualSubmissionDao().insert(
                            ManualSubmissionEntity(
                                pm25 = pm25Val,
                                pm10 = pm10Val,
                                temperature = tempVal,
                                humidity = humVal,
                                windSpeed = windVal,
                                city = city.ifBlank { "Manual Entry" },
                                predictedAqi = aqi,
                                category = category.label,
                                userId = uid,
                                synced = false
                            )
                        )

                        isSubmitting = false
                        showResult = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Default.Calculate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Predict AQI", fontSize = 16.sp)
                }
            }

            // Show prediction result
            if (showResult && predictedAqi != null) {
                Spacer(modifier = Modifier.height(24.dp))

                val aqi = predictedAqi!!
                val category = AqiClassifier.classify(aqi)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = category.color.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Prediction Result", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "$aqi",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = category.color
                        )
                        Text(
                            text = category.label,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = category.color
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Health Impact:", fontWeight = FontWeight.Medium)
                        Text(category.healthImpact, style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Recommendation:", fontWeight = FontWeight.Medium)
                        Text(category.recommendation, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "📡 Data will sync to cloud when connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

