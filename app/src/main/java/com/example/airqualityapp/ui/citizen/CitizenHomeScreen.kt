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
import com.example.airqualityapp.data.repository.UserProfile
import com.example.airqualityapp.data.repository.UserRepository
import com.example.airqualityapp.ml.AqiPredictor
import com.example.airqualityapp.util.AlertNotifier
import com.example.airqualityapp.util.AqiClassifier
import com.example.airqualityapp.util.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HOME_CITIES = listOf(
    "Lahore", "Karachi", "Islamabad", "Rawalpindi", "Faisalabad", "Multan", "Peshawar", "Quetta"
)

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
    val userRepository = remember { UserRepository() }

    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var currentReading by remember { mutableStateOf<AqiReadingEntity?>(null) }
    var predictedAqi by remember { mutableStateOf<Int?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(false) }
    var selectedCity by remember { mutableStateOf<String?>(null) }
    var selectedNavItem by remember { mutableIntStateOf(0) }

    val userName = FirebaseAuth.getInstance().currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: profile?.name?.takeIf { it.isNotBlank() }
        ?: "User"

    DisposableEffect(Unit) {
        onDispose { predictor.close() }
    }

    fun predictFor(reading: AqiReadingEntity) = predictor.predict(
        reading.pm25, reading.pm10, reading.temperature, reading.humidity, reading.windSpeed
    )

    suspend fun loadCity(city: String) {
        isRefreshing = true
        errorMessage = ""
        val result = repository.fetchAndSaveAqi(city)
        result.getOrNull()?.let { reading ->
            currentReading = reading
            isOffline = false
            predictedAqi = predictFor(reading)

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

            // Alert immediately if the live value crosses the user's threshold
            AlertNotifier.checkAndNotify(context, reading, profile)
        }
        result.exceptionOrNull()?.let { e ->
            isOffline = !NetworkUtils.isOnline(context)
            errorMessage = e.message ?: "Failed to fetch data"
            // Offline fallback: show the last fetched reading
            if (currentReading == null) currentReading = repository.getLatestReading()
            currentReading?.let { predictedAqi = predictFor(it) }
        }
        isRefreshing = false
    }

    // 1. Show cached data instantly, load profile, then pick the preferred city
    LaunchedEffect(Unit) {
        repository.getLatestReading()?.let { cached ->
            currentReading = cached
            predictedAqi = predictFor(cached)
        }
        profile = userRepository.getCurrentUserProfile()
        selectedCity = profile?.preferredCity?.takeIf { it in HOME_CITIES } ?: "Lahore"
    }

    // 2. Auto-fetch from the API every time the city changes
    LaunchedEffect(selectedCity) {
        selectedCity?.let { loadCity(it) }
    }

    fun refreshData() {
        val city = selectedCity ?: return
        scope.launch { loadCity(city) }
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
            // City selector (changing the city triggers a fetch through LaunchedEffect)
            CitySelector(selectedCity ?: "Lahore") { selectedCity = it }

            Spacer(modifier = Modifier.height(12.dp))

            // Offline / error status banner
            if (errorMessage.isNotEmpty()) {
                ConnectionStatusBanner(
                    isOffline = isOffline,
                    message = errorMessage,
                    hasCachedData = currentReading != null
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (currentReading != null) {
                val reading = currentReading!!
                val category = AqiClassifier.classify(reading.aqi)

                // AQI Gauge
                AqiGauge(
                    aqi = reading.aqi,
                    color = category.color,
                    category = category.label
                )

                // Last-updated line (required for offline mode)
                LastUpdatedRow(reading = reading, isLive = errorMessage.isEmpty())

                Spacer(modifier = Modifier.height(12.dp))

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
                                Text(
                                    "On-device TensorFlow Lite model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            } else if (!isRefreshing) {
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
                        Text("Connect to the internet and tap Refresh", color = Color.Gray)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Refresh Button
            Button(
                onClick = { refreshData() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                enabled = !isRefreshing && selectedCity != null,
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
private fun ConnectionStatusBanner(isOffline: Boolean, message: String, hasCachedData: Boolean) {
    val (icon, title, detail) = when {
        isOffline && hasCachedData -> Triple(
            Icons.Default.CloudOff, "You are offline",
            "Showing the last fetched AQI. Data will refresh automatically when you reconnect."
        )
        isOffline -> Triple(
            Icons.Default.CloudOff, "You are offline",
            "No cached AQI data yet. Connect to the internet and tap Refresh."
        )
        hasCachedData -> Triple(
            Icons.Default.Warning, "Could not refresh",
            "$message Showing the last fetched AQI."
        )
        else -> Triple(Icons.Default.Warning, "Could not fetch data", message)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFFEF6C00))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFEF6C00))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
        }
    }
}

@Composable
private fun LastUpdatedRow(reading: AqiReadingEntity, isLive: Boolean) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val sourceLabel = when {
        reading.source == "manual" -> "📝 manual entry"
        isLive -> "📡 live data"
        else -> "💾 cached data"
    }
    Text(
        text = "Last updated: ${fmt.format(Date(reading.timestamp))} · $sourceLabel",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray,
        textAlign = TextAlign.Center
    )
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HOME_CITIES.forEach { city ->
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
                    Text(range, modifier = Modifier.width(70.dp), fontSize = 12.sp)
                    Text(cat.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
