package com.example.airqualityapp.util

/**
 * US-EPA Air Quality Index maths shared by the API repository, the CSV importer
 * and the training pipeline (same breakpoints as the Python script).
 *
 * Reference: https://www.airnow.gov/aqi/aqi-basics/
 */
object AqiMath {

    /** Calculate US-EPA AQI (0–500) from a PM2.5 concentration in µg/m³. */
    fun aqiFromPm25(pm25: Double): Int {
        return when {
            pm25 < 0.0     -> 0
            pm25 <= 12.0   -> linearScale(pm25, 0.0, 12.0, 0, 50)
            pm25 <= 35.4   -> linearScale(pm25, 12.1, 35.4, 51, 100)
            pm25 <= 55.4   -> linearScale(pm25, 35.5, 55.4, 101, 150)
            pm25 <= 150.4  -> linearScale(pm25, 55.5, 150.4, 151, 200)
            pm25 <= 250.4  -> linearScale(pm25, 150.5, 250.4, 201, 300)
            pm25 <= 500.4  -> linearScale(pm25, 250.5, 500.4, 301, 500)
            else           -> 500
        }
    }

    private fun linearScale(value: Double, cLow: Double, cHigh: Double, iLow: Int, iHigh: Int): Int {
        val scaled = (iHigh - iLow).toDouble() / (cHigh - cLow) * (value - cLow) + iLow
        // Round (not truncate) so the app matches the labels used to train the model.
        return Math.round(scaled).toInt().coerceIn(iLow, iHigh)
    }
}
