package com.example.airqualityapp.ui.citizen

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.local.AppDatabase
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.remote.RealtimeDatabaseHelper
import com.example.airqualityapp.data.repository.AqiRepository
import com.example.airqualityapp.ml.AqiPredictor
import com.example.airqualityapp.util.AqiClassifier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenHomeScreen(
    context: Context,
    onNavigateToHistory: () -> Unit,
    onNavigateToManualInput: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val repository = remember { AqiRepository(db.aqiDao()) }
    val predictor = remember { AqiPredictor(context) }

    var currentReading by remember { mutableStateOf<AqiReadingEntity?>(null) }
    var predictedAqi by remember { mutableStateOf<Int?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("Lahore") }
    var selectedNavItem by remember { mutableIntStateOf(0) }

    val userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"

    // Auto-fetch from API every time the city changes
    LaunchedEffect(selectedCity) {
        isRefreshing = true
        errorMessage = ""
        val result = repository.fetchAndSaveAqi(selectedCity)
        result.onSuccess { reading ->
            currentReading = reading
            predictedAqi = predictor.predict(
                reading.pm25, reading.pm10,
                reading.temperature, reading.humidity, reading.windSpeed
            )
            // Push to Realtime Database for live sync across devices
            val category = AqiClassifier.classify(reading.aqi)
            val liveData = RealtimeDatabaseHelper.LiveAqiData(
                aqi = reading.aqi,
                pm25 = reading.pm25,
                pm10 = reading.pm10,
                temperature = reading.temperature,
                humidity = reading.humidity,
                windSpeed = reading.windSpeed,
                city = reading.city,
                category = category.label,
                source = "api",
                timestamp = reading.timestamp
            )
            RealtimeDatabaseHelper.writeLiveAqi(liveData)
            RealtimeDatabaseHelper.pushAqiHistory(reading.city, liveData)
        }
        result.onFailure {
            errorMessage = it.message ?: "Failed to fetch data"
            // Fall back to latest cached data
            val latest = repository.getLatestReading()
            if (latest != null) currentReading = latest
        }
        isRefreshing = false
    }

    fun refreshData() {
        scope.launch {
            isRefreshing = true
            errorMessage = ""
            val result = repository.fetchAndSaveAqi(selectedCity)
            result.onSuccess { reading ->
                currentReading = reading
                predictedAqi = predictor.predict(
                    reading.pm25, reading.pm10,
                    reading.temperature, reading.humidity, reading.windSpeed
                )
                // Push to Realtime Database
                val category = AqiClassifier.classify(reading.aqi)
                val liveData = RealtimeDatabaseHelper.LiveAqiData(
                    aqi = reading.aqi,
                    pm25 = reading.pm25,
                    pm10 = reading.pm10,
                    temperature = reading.temperature,
                    humidity = reading.humidity,
                    windSpeed = reading.windSpeed,
                    city = reading.city,
                    category = category.label,
                    source = "api",
                    timestamp = reading.timestamp
                )
                RealtimeDatabaseHelper.writeLiveAqi(liveData)
                RealtimeDatabaseHelper.pushAqiHistory(reading.city, liveData)
            }
            result.onFailure {
                errorMessage = it.message ?: "Failed to fetch data"
                val latest = repository.getLatestReading()
                if (latest != null) currentReading = latest
            }
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Air Quality Monitor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Hello, $userName", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedNavItem == 0,
                    onClick = { selectedNavItem = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                    label = { Text("History") },
                    selected = selectedNavItem == 1,
                    onClick = { onNavigateToHistory() }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Manual Input") },
                    label = { Text("Input") },
                    selected = selectedNavItem == 2,
                    onClick = { onNavigateToManualInput() }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Help") },
                    label = { Text("Help") },
                    selected = selectedNavItem == 3,
                    onClick = { onNavigateToHelp() }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = selectedNavItem == 4,
                    onClick = { onNavigateToProfile() }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // City selector
            CitySelector(selectedCity) { selectedCity = it; refreshData() }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentReading != null) {
                val reading = currentReading!!
                val category = AqiClassifier.classify(reading.aqi)

                // AQI Gauge
                AqiGauge(
                    aqi = reading.aqi,
                    color = category.color,
                    category = category.label
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Health Impact Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = category.color.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Health Impact", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(category.healthImpact, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("💡 Recommendation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(category.recommendation, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Weather Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Weather & Pollutant Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WeatherInfoChip("🌡️", "Temp", "${reading.temperature}°C")
                            WeatherInfoChip("💧", "Humidity", "${reading.humidity}%")
                            WeatherInfoChip("💨", "Wind", "${reading.windSpeed} m/s")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            WeatherInfoChip("🔴", "PM2.5", "${reading.pm25}")
                            WeatherInfoChip("🟠", "PM10", "${reading.pm10}")
                            WeatherInfoChip("📍", "City", reading.city)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // AI Prediction Card
                if (predictedAqi != null) {
                    val predCategory = AqiClassifier.classify(predictedAqi!!)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("AI Predicted AQI (Next Hour)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Predicted: ${predictedAqi} (${predCategory.label})",
                                    color = predCategory.color,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                // No data yet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No AQI data available", fontWeight = FontWeight.Medium)
                        Text("Tap Refresh to fetch current data", color = Color.Gray)
                    }
                }
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Refresh Button
            Button(
                onClick = { refreshData() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                enabled = !isRefreshing,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh AQI Data", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AQI Scale Legend
            AqiLegendCard()
        }
    }
}

@Composable
fun AqiGauge(aqi: Int, color: Color, category: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = (aqi.toFloat() / 500f).coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "aqi_progress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeWidth = 20f
            val startAngle = 135f
            val sweepAngle = 270f

            // Background arc
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
                topLeft = Offset.Zero
            )

            // Foreground arc
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width, size.height),
                topLeft = Offset.Zero
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 30.dp)
        ) {
            Text("AQI", fontSize = 14.sp, color = Color.Gray)
            Text(
                text = "$aqi",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = category,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WeatherInfoChip(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 24.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitySelector(selectedCity: String, onCitySelected: (String) -> Unit) {
    val cities = listOf("Lahore", "Karachi", "Islamabad", "Rawalpindi", "Faisalabad", "Multan", "Peshawar", "Quetta")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedCity,
            onValueChange = {},
            readOnly = true,
            label = { Text("Select City") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cities.forEach { city ->
                DropdownMenuItem(
                    text = { Text(city) },
                    onClick = {
                        onCitySelected(city)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun AqiLegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AQI Scale", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AqiClassifier.getAllCategories().forEach { (range, _, cat) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(cat.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(range, modifier = Modifier.width(60.dp), fontSize = 12.sp)
                    Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

