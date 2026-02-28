package com.example.airqualityapp.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * AQI Predictor using TensorFlow Lite model.
 *
 * The model expects input features: [pm25, pm10, temperature, humidity, windSpeed]
 * and outputs a predicted AQI value.
 *
 * Place your trained .tflite model file as "aqi_model.tflite" in the assets folder.
 * If no model is found, a simple heuristic-based prediction is used as fallback.
 */
class AqiPredictor(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var modelLoaded = false

    init {
        try {
            val model = loadModelFile("aqi_model.tflite")
            interpreter = Interpreter(model)
            modelLoaded = true
        } catch (e: Exception) {
            // Model not found - will use fallback heuristic
            modelLoaded = false
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Predict AQI from weather and pollutant data.
     * @param pm25 PM2.5 concentration
     * @param pm10 PM10 concentration
     * @param temperature Temperature in Celsius
     * @param humidity Humidity percentage
     * @param windSpeed Wind speed in m/s
     * @return Predicted AQI value
     */
    fun predict(
        pm25: Double,
        pm10: Double,
        temperature: Double,
        humidity: Double,
        windSpeed: Double
    ): Int {
        if (modelLoaded && interpreter != null) {
            return predictWithModel(pm25, pm10, temperature, humidity, windSpeed)
        }
        return predictHeuristic(pm25, pm10, temperature, humidity, windSpeed)
    }

    private fun predictWithModel(
        pm25: Double, pm10: Double,
        temperature: Double, humidity: Double, windSpeed: Double
    ): Int {
        val inputBuffer = ByteBuffer.allocateDirect(5 * 4).apply {
            order(ByteOrder.nativeOrder())
            putFloat(pm25.toFloat())
            putFloat(pm10.toFloat())
            putFloat(temperature.toFloat())
            putFloat(humidity.toFloat())
            putFloat(windSpeed.toFloat())
        }

        val outputBuffer = ByteBuffer.allocateDirect(4).apply {
            order(ByteOrder.nativeOrder())
        }

        try {
            interpreter?.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            return outputBuffer.float.toInt().coerceIn(0, 500)
        } catch (e: Exception) {
            return predictHeuristic(pm25, pm10, temperature, humidity, windSpeed)
        }
    }

    /**
     * Fallback heuristic-based AQI prediction when TFLite model is not available.
     * Based on EPA breakpoint formula for PM2.5.
     */
    private fun predictHeuristic(
        pm25: Double, pm10: Double,
        temperature: Double, humidity: Double, windSpeed: Double
    ): Int {
        // Primary: PM2.5 based AQI calculation (EPA breakpoints)
        val pm25Aqi = when {
            pm25 <= 12.0 -> linearScale(pm25, 0.0, 12.0, 0.0, 50.0)
            pm25 <= 35.4 -> linearScale(pm25, 12.1, 35.4, 51.0, 100.0)
            pm25 <= 55.4 -> linearScale(pm25, 35.5, 55.4, 101.0, 150.0)
            pm25 <= 150.4 -> linearScale(pm25, 55.5, 150.4, 151.0, 200.0)
            pm25 <= 250.4 -> linearScale(pm25, 150.5, 250.4, 201.0, 300.0)
            pm25 <= 500.4 -> linearScale(pm25, 250.5, 500.4, 301.0, 500.0)
            else -> 500.0
        }

        // Secondary: PM10 based AQI
        val pm10Aqi = when {
            pm10 <= 54.0 -> linearScale(pm10, 0.0, 54.0, 0.0, 50.0)
            pm10 <= 154.0 -> linearScale(pm10, 55.0, 154.0, 51.0, 100.0)
            pm10 <= 254.0 -> linearScale(pm10, 155.0, 254.0, 101.0, 150.0)
            pm10 <= 354.0 -> linearScale(pm10, 255.0, 354.0, 151.0, 200.0)
            pm10 <= 424.0 -> linearScale(pm10, 355.0, 424.0, 201.0, 300.0)
            pm10 <= 604.0 -> linearScale(pm10, 425.0, 604.0, 301.0, 500.0)
            else -> 500.0
        }

        // Use the higher of PM2.5 and PM10 AQI
        var aqi = maxOf(pm25Aqi, pm10Aqi)

        // Weather adjustments
        if (humidity > 80) aqi *= 1.05
        if (windSpeed < 2.0) aqi *= 1.1
        if (temperature > 35) aqi *= 1.05

        return aqi.toInt().coerceIn(0, 500)
    }

    private fun linearScale(value: Double, inLow: Double, inHigh: Double, outLow: Double, outHigh: Double): Double {
        return ((value - inLow) / (inHigh - inLow)) * (outHigh - outLow) + outLow
    }

    fun close() {
        interpreter?.close()
    }
}

