package com.example.airqualityapp.ui.citizen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.local.AppDatabase
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.util.AqiClassifier
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    context: Context,
    onBack: () -> Unit
) {
    val db = remember { AppDatabase.getInstance(context) }
    val readings by db.aqiDao().getAllReadings().collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Today", "Week", "Month")

    val filteredReadings = remember(readings, selectedFilter) {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        when (selectedFilter) {
            "Today" -> readings.filter { now - it.timestamp < dayMs }
            "Week" -> readings.filter { now - it.timestamp < 7 * dayMs }
            "Month" -> readings.filter { now - it.timestamp < 30 * dayMs }
            else -> readings
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQI History & Trends") },
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
                .padding(horizontal = 16.dp)
        ) {
            // Date filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simple Chart - Bar representation
            if (filteredReadings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AQI Trend", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Show last 10 readings as bars
                        val chartData = filteredReadings.take(10).reversed()
                        val maxAqi = (chartData.maxOfOrNull { it.aqi } ?: 500).coerceAtLeast(1)

                        chartData.forEach { reading ->
                            val category = AqiClassifier.classify(reading.aqi)
                            val fraction = reading.aqi.toFloat() / maxAqi.toFloat()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatTime(reading.timestamp),
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.width(50.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(category.color)
                                    )
                                }
                                Text(
                                    "${reading.aqi}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(35.dp),
                                    color = category.color
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Statistics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Statistics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Average", "${filteredReadings.map { it.aqi }.average().toInt()}")
                            StatItem("Highest", "${filteredReadings.maxOfOrNull { it.aqi } ?: 0}")
                            StatItem("Lowest", "${filteredReadings.minOfOrNull { it.aqi } ?: 0}")
                            StatItem("Records", "${filteredReadings.size}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reading list
            Text(
                "All Readings (${filteredReadings.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredReadings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No readings found", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredReadings) { reading ->
                        ReadingCard(reading)
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingCard(reading: AqiReadingEntity) {
    val category = AqiClassifier.classify(reading.aqi)
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AQI Badge
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(category.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${reading.aqi}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(category.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = category.color)
                Text(reading.city, fontSize = 12.sp, color = Color.Gray)
                Text(dateFormat.format(Date(reading.timestamp)), fontSize = 11.sp, color = Color.Gray)
            }

            // Source badge
            Text(
                if (reading.source == "manual") "📝" else "📡",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

private fun formatTime(timestamp: Long): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

