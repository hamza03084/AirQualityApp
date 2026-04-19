package com.example.airqualityapp.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps the ML-Model-Binding generated [AqiModel] class (built from
 * app/src/main/ml/aqi_model.tflite at compile time).
 *
 * Input order (must match Colab training):
 *   [components_pm2_5, components_pm10, temperature_2m,
 *    relative_humidity_2m, wind_speed_10m]
 * Output: EPA AQI on 0–500 scale.
 *
 * Inputs are StandardScaler-normalized with the exact mean/std from training —
 * without this the model receives out-of-distribution values and predictions are garbage.
 */
class AqiPredictor(context: Context) {

    private val model: AqiModel? = try {
        AqiModel.newInstance(context.applicationContext)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load AqiModel", e)
        null
    }

    fun predict(
        pm25: Double,
        pm10: Double,
        temperature: Double,
        humidity: Double,
        windSpeed: Double
    ): Int {
        val m = model ?: run {
            Log.w(TAG, "Model unavailable — returning 0")
            return 0
        }

        val raw = doubleArrayOf(pm25, pm10, temperature, humidity, windSpeed)
        val buf = ByteBuffer
            .allocateDirect(Float.SIZE_BYTES * INPUT_SIZE)
            .order(ByteOrder.nativeOrder())
        for (i in 0 until INPUT_SIZE) {
            buf.putFloat(((raw[i] - MEAN[i]) / STD[i]).toFloat())
        }
        buf.rewind()

        val input = TensorBuffer.createFixedSize(intArrayOf(1, INPUT_SIZE), DataType.FLOAT32)
        input.loadBuffer(buf)

        val aqi = m.process(input).outputFeature0AsTensorBuffer.floatArray[0]
        Log.d(
            TAG,
            "pm25=$pm25 pm10=$pm10 t=$temperature h=$humidity w=$windSpeed → aqi=$aqi"
        )
        return aqi.toInt().coerceIn(0, 500)
    }

    fun close() {
        model?.close()
    }

    companion object {
        private const val TAG = "AqiPredictor"
        private const val INPUT_SIZE = 5

        // StandardScaler params from Colab (2026-04-19 training run).
        // Order: pm2_5, pm10, temperature_2m, relative_humidity_2m, wind_speed_10m.
        private val MEAN = doubleArrayOf(
            101.02524159048461,
            142.4897337924916,
            21.509872731772134,
            55.030612929681304,
            6.305674323786199
        )
        private val STD = doubleArrayOf(
            137.87763260174566,
            162.3220703296476,
            8.697193992370968,
            24.177242284404937,
            3.8572198475622246
        )
    }
}
