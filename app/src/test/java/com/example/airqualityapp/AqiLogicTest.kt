package com.example.airqualityapp

import com.example.airqualityapp.data.local.entity.AqiReadingEntity
import com.example.airqualityapp.data.repository.AqiThreshold
import com.example.airqualityapp.ui.citizen.buildTrendPoints
import com.example.airqualityapp.util.AqiClassifier
import com.example.airqualityapp.util.AqiConfig
import com.example.airqualityapp.util.AqiMath
import com.example.airqualityapp.util.CsvDatasetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * JVM unit tests for the pure logic added in the final feature pass:
 * EPA AQI maths, category classification, threshold validation,
 * CSV dataset parsing and history trend aggregation.
 */
class AqiLogicTest {

    // ── AqiMath ─────────────────────────────────────────────────────────

    @Test
    fun epaAqi_matchesOfficialBreakpoints() {
        assertEquals(0, AqiMath.aqiFromPm25(0.0))
        assertEquals(33, AqiMath.aqiFromPm25(8.0))       // Good
        assertEquals(50, AqiMath.aqiFromPm25(12.0))      // top of Good
        assertEquals(89, AqiMath.aqiFromPm25(30.0))      // Moderate
        assertEquals(100, AqiMath.aqiFromPm25(35.4))     // top of Moderate
        assertEquals(153, AqiMath.aqiFromPm25(60.0))     // Unhealthy
        assertEquals(250, AqiMath.aqiFromPm25(200.0))    // Very Unhealthy
        assertEquals(380, AqiMath.aqiFromPm25(350.0))    // Hazardous
        assertEquals(500, AqiMath.aqiFromPm25(600.0))    // capped
        assertEquals(0, AqiMath.aqiFromPm25(-5.0))       // negative guarded
    }

    // ── AqiClassifier with default thresholds ──────────────────────────

    @Test
    fun classifier_usesSixEpaBandsByDefault() {
        assertEquals("Good", AqiClassifier.classify(0).label)
        assertEquals("Good", AqiClassifier.classify(50).label)
        assertEquals("Moderate", AqiClassifier.classify(51).label)
        assertEquals("Moderate", AqiClassifier.classify(100).label)
        assertEquals("Unhealthy for Sensitive Groups", AqiClassifier.classify(125).label)
        assertEquals("Unhealthy", AqiClassifier.classify(151).label)
        assertEquals("Very Unhealthy", AqiClassifier.classify(300).label)
        assertEquals("Hazardous", AqiClassifier.classify(301).label)
        assertEquals("Hazardous", AqiClassifier.classify(500).label)
    }

    @Test
    fun classifier_legendListsSixRangesInOrder() {
        val categories = AqiClassifier.getAllCategories()
        assertEquals(6, categories.size)
        assertEquals("0 – 50", categories[0].first)
        assertEquals("51 – 100", categories[1].first)
        assertEquals("301+", categories[5].first)
        assertEquals("Safe to enjoy outdoor activities.", categories[0].third.recommendation)
    }

    // ── AqiConfig threshold validation ─────────────────────────────────

    @Test
    fun thresholds_mustBePositiveAndStrictlyIncreasing() {
        assertTrue(AqiConfig.isValid(AqiThreshold()))
        assertTrue(AqiConfig.isValid(AqiThreshold(30, 80, 120, 180, 250, 100)))
        assertFalse(AqiConfig.isValid(AqiThreshold(goodMax = 100, moderateMax = 50)))      // descending
        assertFalse(AqiConfig.isValid(AqiThreshold(goodMax = 50, moderateMax = 50)))       // equal
        assertFalse(AqiConfig.isValid(AqiThreshold(goodMax = 0)))                          // zero
        assertFalse(AqiConfig.isValid(AqiThreshold(alertThreshold = -1)))                  // negative alert
    }

    // ── CsvDatasetParser ───────────────────────────────────────────────

    @Test
    fun csvParser_readsKaggleExportAndComputesAqiFromPm25() {
        val csv = """
            datetime,main_aqi,components_co,components_no,components_no2,components_o3,components_so2,components_pm2_5,components_pm10,components_nh3,temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,surface_pressure,wind_speed_10m,wind_direction_10m,shortwave_radiation
            8/24/2021 0:00,5,1014.71,0,17.14,35.76,6.44,59.59,66.81,5.95,28.1,80,24.3,0,976.6,6.2,170,0
            8/24/2021 1:00,5,1054.76,0.01,19.36,32.19,6.68,66.96,74.88,6.08,27.8,83,24.7,0,976.4,6.5,180,0
        """.trimIndent()
        val result = CsvDatasetParser.parse(csv.byteInputStream(), defaultCity = "Lahore")

        assertNull(result.error)
        assertEquals(2, result.totalRows)
        assertEquals(2, result.entries.size)
        assertEquals(0, result.skipped)
        assertFalse(result.truncated)

        val first = result.entries[0]
        assertEquals("Lahore", first["city"])                       // default city applied
        assertEquals(AqiMath.aqiFromPm25(59.59), first["aqi"])      // AQI derived from PM2.5
        assertEquals(59.59, first["pm25"] as Double, 0.0001)
        assertEquals(66.81, first["pm10"] as Double, 0.0001)
        assertEquals(28.1, first["temperature"] as Double, 0.0001)
        assertEquals(80.0, first["humidity"] as Double, 0.0001)
        assertEquals(6.2, first["windSpeed"] as Double, 0.0001)
        assertEquals("csv_import", first["source"])

        val cal = Calendar.getInstance().apply { timeInMillis = first["timestamp"] as Long }
        assertEquals(2021, cal.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
        assertEquals(24, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun csvParser_readsHandMadeFileWithAqiColumnAndSkipsBadRows() {
        val csv = """
            City,AQI,PM2.5,PM10,Temperature,Humidity,Wind Speed,Timestamp
            Karachi,120,45.2,80,31,70,4.5,1700000000
            "Islamabad, Capital",abc,,,,,,
            Quetta,80,,,20,40,3,2024-01-15 10:30:00
        """.trimIndent()
        val result = CsvDatasetParser.parse(csv.byteInputStream())

        assertNull(result.error)
        assertEquals(3, result.totalRows)
        assertEquals(2, result.entries.size)
        assertEquals(1, result.skipped)                                // row with no AQI and no PM2.5
        assertEquals("Karachi", result.entries[0]["city"])
        assertEquals(120, result.entries[0]["aqi"])
        assertEquals(1700000000L * 1000, result.entries[0]["timestamp"])  // epoch seconds → millis
        assertEquals("Quetta", result.entries[1]["city"])
        assertNotNull(result.entries[1]["timestamp"])
    }

    @Test
    fun csvParser_rejectsFileWithoutAqiOrPm25() {
        val csv = "city,temperature\nLahore,30\n"
        val result = CsvDatasetParser.parse(csv.byteInputStream())
        assertNotNull(result.error)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun csvParser_capsRowsPerFile() {
        val sb = StringBuilder("aqi\n")
        repeat(CsvDatasetParser.MAX_ROWS + 50) { sb.append("100\n") }
        val result = CsvDatasetParser.parse(sb.toString().byteInputStream())
        assertEquals(CsvDatasetParser.MAX_ROWS, result.entries.size)
        assertTrue(result.truncated)
    }

    // ── History trend aggregation ──────────────────────────────────────

    private fun reading(aqi: Int, daysAgo: Int, hour: Int): AqiReadingEntity {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        return AqiReadingEntity(aqi = aqi, city = "Lahore", timestamp = cal.timeInMillis)
    }

    @Test
    fun trend_todayKeepsEveryReadingInTimeOrder() {
        val readings = listOf(reading(150, 0, 9), reading(120, 0, 7), reading(180, 0, 11))
        val points = buildTrendPoints(readings, "Today")
        assertEquals(listOf(120, 150, 180), points.map { it.aqi })
        assertTrue(points.all { it.samples == 1 })
    }

    @Test
    fun trend_weekAveragesPerDay() {
        val readings = listOf(
            reading(100, 2, 8), reading(200, 2, 20),   // day -2 → avg 150
            reading(90, 1, 8),                          // day -1 → 90
            reading(300, 0, 8)                          // today → 300
        )
        val points = buildTrendPoints(readings, "Week")
        assertEquals(3, points.size)
        assertEquals(150, points[0].aqi)
        assertEquals(2, points[0].samples)
        assertEquals(90, points[1].aqi)
        assertEquals(300, points[2].aqi)
    }

    @Test
    fun trend_emptyInputGivesNoPoints() {
        assertTrue(buildTrendPoints(emptyList(), "Month").isEmpty())
    }
}
