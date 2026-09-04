package com.example.airqualityapp.ui.citizen

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airqualityapp.data.local.AppDatabase
import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.util.AqiClassifier
import com.example.airqualityapp.util.AqiConfig
import java.text.SimpleDateFormat
import java.util.*

/** One point on the trend chart (a reading, or a daily average). */
data class TrendPoint(val label: String, val aqi: Int, val timestamp: Long, val samples: Int = 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    context: Context,
    onBack: () -> Unit
) {
    val db = remember { AppDatabase.getInstance(context) }
    val readings by db.aqiDao().getAllReadings().collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("Week") }
    val filters = listOf("Today", "Week", "Month", "All")

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

    val trendPoints = remember(filteredReadings, selectedFilter) {
        buildTrendPoints(filteredReadings, selectedFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AQI History & Trends") },
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
                .padding(horizontal = 16.dp)
        ) {
            // Period filter chips (daily / weekly / monthly / all)
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

            if (filteredReadings.isNotEmpty()) {
                // Trend chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AQI Trend", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                when (selectedFilter) {
                                    "Today" -> "hourly readings"
                                    else -> "daily averages"
                                },
                                fontSize = 11.sp, color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        AqiTrendChart(
                            points = trendPoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TrendSummaryRow(trendPoints)
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

/**
 * Builds chart points: individual readings for "Today", daily averages otherwise
 * (the last 30 days for "All" so the chart stays readable).
 */
fun buildTrendPoints(readings: List<AqiReadingEntity>, filter: String): List<TrendPoint> {
    if (readings.isEmpty()) return emptyList()
    val ascending = readings.sortedBy { it.timestamp }
    if (filter == "Today") {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return ascending.map { TrendPoint(fmt.format(Date(it.timestamp)), it.aqi, it.timestamp) }
    }
    val labelFmt = SimpleDateFormat(if (filter == "Week") "EEE" else "dd MMM", Locale.getDefault())
    val cal = Calendar.getInstance()
    val byDay = ascending.groupBy { r ->
        cal.timeInMillis = r.timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    return byDay.entries
        .sortedBy { it.key }
        .takeLast(30)
        .map { (day, rows) ->
            TrendPoint(
                label = labelFmt.format(Date(day)),
                aqi = rows.map { it.aqi }.average().toInt(),
                timestamp = day,
                samples = rows.size
            )
        }
}

/** Line chart drawn with Compose Canvas – no third-party charting library needed. */
@Composable
fun AqiTrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = Color.Gray)
    val thresholds = AqiConfig.thresholds
    val bands = listOf(
        thresholds.goodMax to AqiClassifier.AqiGreen,
        thresholds.moderateMax to AqiClassifier.AqiYellow,
        thresholds.unhealthySensitiveMax to AqiClassifier.AqiOrange,
        thresholds.unhealthyMax to AqiClassifier.AqiRed,
        thresholds.veryUnhealthyMax to AqiClassifier.AqiPurple
    )

    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val leftPad = 34.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - leftPad - 6.dp.toPx()
        val chartH = size.height - bottomPad - topPad
        val maxAqi = points.maxOf { it.aqi }
        val yMax = (((maxAqi + 25) / 50 + 1) * 50).coerceAtLeast(100).coerceAtMost(500).toFloat()

        fun yFor(v: Float) = topPad + chartH - (v / yMax) * chartH
        fun xFor(i: Int) = leftPad + if (points.size == 1) chartW / 2 else chartW * i / (points.size - 1)

        // Baseline + category boundary lines with value labels
        drawLine(Color.LightGray, Offset(leftPad, yFor(0f)), Offset(leftPad + chartW, yFor(0f)), 1.dp.toPx())
        bands.filter { it.first <= yMax }.forEach { (value, color) ->
            val y = yFor(value.toFloat())
            drawLine(color.copy(alpha = 0.35f), Offset(leftPad, y), Offset(leftPad + chartW, y), 1.dp.toPx())
            val text = value.toString()
            val measured = textMeasurer.measure(text, labelStyle)
            drawText(textMeasurer, text, Offset(leftPad - measured.size.width - 4.dp.toPx(), y - measured.size.height / 2), labelStyle)
        }

        // Area fill + line
        val linePath = Path()
        val areaPath = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i); val y = yFor(p.aqi.toFloat())
            if (i == 0) { linePath.moveTo(x, y); areaPath.moveTo(x, yFor(0f)); areaPath.lineTo(x, y) }
            else { linePath.lineTo(x, y); areaPath.lineTo(x, y) }
        }
        areaPath.lineTo(xFor(points.size - 1), yFor(0f)); areaPath.close()
        drawPath(areaPath, Color(0xFF1976D2).copy(alpha = 0.10f))
        drawPath(linePath, Color(0xFF1976D2), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Points coloured by category
        points.forEachIndexed { i, p ->
            drawCircle(AqiClassifier.classify(p.aqi).color, radius = 4.dp.toPx(), center = Offset(xFor(i), yFor(p.aqi.toFloat())))
        }

        // X labels: first, middle, last (avoid overlap)
        val labelIdx = listOf(0, points.size / 2, points.size - 1).distinct()
        labelIdx.forEach { i ->
            val text = points[i].label
            val measured = textMeasurer.measure(text, labelStyle)
            val x = (xFor(i) - measured.size.width / 2).coerceIn(leftPad, size.width - measured.size.width)
            drawText(textMeasurer, text, Offset(x, topPad + chartH + 4.dp.toPx()), labelStyle)
        }
    }
}

@Composable
private fun TrendSummaryRow(points: List<TrendPoint>) {
    if (points.size < 2) {
        Text("Add more readings to see the trend direction.", fontSize = 12.sp, color = Color.Gray)
        return
    }
    val half = points.size / 2
    val first = points.take(half).map { it.aqi }.average()
    val second = points.drop(half).map { it.aqi }.average()
    val change = second - first
    val (icon, text, color) = when {
        change > 10 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, "Air quality is worsening (AQI up ${change.toInt()} on average)", AqiClassifier.AqiRed)
        change < -10 -> Triple(Icons.AutoMirrored.Filled.TrendingDown, "Air quality is improving (AQI down ${(-change).toInt()} on average)", AqiClassifier.AqiGreen)
        else -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, "Air quality is stable over this period", Color.Gray)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Source badge
                Text(if (reading.source == "manual") "📝" else "📡", fontSize = 20.sp)
                Text(
                    if (reading.synced) "synced" else "pending",
                    fontSize = 9.sp,
                    color = if (reading.synced) AqiClassifier.AqiGreen else Color.Gray
                )
            }
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
