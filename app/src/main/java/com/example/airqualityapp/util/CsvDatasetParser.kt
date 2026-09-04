package com.example.airqualityapp.util

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses a CSV file of historical AQI data into Firestore-ready maps.
 *
 * Header names are matched flexibly (case-insensitive, punctuation ignored), so
 * both hand-made files (city, aqi, pm25, pm10, temperature, humidity, windSpeed,
 * timestamp) and the Kaggle Pakistan Air Quality export
 * (datetime, components_pm2_5, components_pm10, temperature_2m,
 * relative_humidity_2m, wind_speed_10m, ...) are accepted.
 * When no AQI column exists it is calculated from PM2.5 with the EPA formula.
 */
object CsvDatasetParser {

    const val MAX_ROWS = 2000

    data class ParseResult(
        val entries: List<Map<String, Any>>,
        val totalRows: Int,
        val skipped: Int,
        val truncated: Boolean,
        val error: String? = null
    )

    private val dateFormats = listOf(
        "M/d/yyyy H:mm", "M/d/yyyy H:mm:ss", "d/M/yyyy HH:mm:ss", "d/M/yyyy HH:mm",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd", "dd-MM-yyyy HH:mm", "dd-MM-yyyy"
    )

    fun parse(input: InputStream, defaultCity: String = ""): ParseResult {
        val lines = input.bufferedReader().useLines { seq ->
            seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
        if (lines.size < 2) {
            return ParseResult(emptyList(), 0, 0, false, "File has no data rows")
        }

        val header = splitCsv(lines[0]).map { normalise(it) }
        fun col(predicate: (String) -> Boolean): Int = header.indexOfFirst(predicate)

        val cityIdx = col { it in setOf("city", "location", "station", "cityname") }
        val aqiIdx = col { it in setOf("aqi", "aqivalue", "usaqi", "epaaqi") }
        val pm25Idx = col { it.endsWith("pm25") }
        val pm10Idx = col { it.endsWith("pm10") }
        val tempIdx = col { it.startsWith("temp") }
        val humIdx = col { it.contains("humidity") || it == "rh" }
        val windIdx = col { it.startsWith("wind") && !it.contains("direction") }
        val timeIdx = col { it in setOf("timestamp", "time", "datetime", "date", "recordedat") }

        if (aqiIdx == -1 && pm25Idx == -1) {
            return ParseResult(
                emptyList(), lines.size - 1, lines.size - 1, false,
                "CSV must contain an 'aqi' or a 'pm25' / 'components_pm2_5' column"
            )
        }

        val entries = mutableListOf<Map<String, Any>>()
        var skipped = 0
        var truncated = false
        val dataLines = lines.drop(1)

        for (line in dataLines) {
            if (entries.size >= MAX_ROWS) { truncated = true; break }
            val cells = splitCsv(line)
            fun num(i: Int): Double? = cells.getOrNull(i)?.trim()?.toDoubleOrNull()
            fun str(i: Int): String? = cells.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }

            val pm25 = num(pm25Idx)
            val aqi = num(aqiIdx)?.toInt() ?: pm25?.let { AqiMath.aqiFromPm25(it) }
            if (aqi == null) { skipped++; continue }

            entries += mapOf(
                "city" to (str(cityIdx) ?: defaultCity.ifBlank { "Unknown" }),
                "aqi" to aqi.coerceIn(0, 500),
                "pm25" to (pm25 ?: 0.0),
                "pm10" to (num(pm10Idx) ?: 0.0),
                "temperature" to (num(tempIdx) ?: 0.0),
                "humidity" to (num(humIdx) ?: 0.0),
                "windSpeed" to (num(windIdx) ?: 0.0),
                "timestamp" to parseTimestamp(str(timeIdx)),
                "source" to "csv_import"
            )
        }
        return ParseResult(entries, dataLines.size, skipped, truncated)
    }

    private fun normalise(h: String) = h.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Minimal CSV splitter that honours double-quoted cells. */
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
        }
        out += sb.toString()
        return out
    }

    private fun parseTimestamp(raw: String?): Long {
        if (raw == null) return System.currentTimeMillis()
        raw.toLongOrNull()?.let { return if (it < 100_000_000_000L) it * 1000 else it }
        for (fmt in dateFormats) {
            val parsed = runCatching {
                SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }.parse(raw)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return System.currentTimeMillis()
    }
}
