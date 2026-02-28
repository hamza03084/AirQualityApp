package com.example.airqualityapp.util

import androidx.compose.ui.graphics.Color

data class AqiCategory(
    val label: String,
    val color: Color,
    val healthImpact: String,
    val recommendation: String
)

object AqiClassifier {

    val AqiGreen = Color(0xFF4CAF50)
    val AqiYellow = Color(0xFFFFC107)
    val AqiOrange = Color(0xFFFF9800)
    val AqiRed = Color(0xFFF44336)
    val AqiPurple = Color(0xFF9C27B0)
    val AqiMaroon = Color(0xFF800000)

    fun classify(aqi: Int): AqiCategory {
        return when {
            aqi in 0..50 -> AqiCategory(
                label = "Good",
                color = AqiGreen,
                healthImpact = "No health risk.",
                recommendation = "Safe to enjoy outdoor activities."
            )
            aqi in 51..100 -> AqiCategory(
                label = "Moderate",
                color = AqiYellow,
                healthImpact = "Minor risk for sensitive groups (children, elderly, people with asthma).",
                recommendation = "Sensitive people should reduce long outdoor stays."
            )
            aqi in 101..150 -> AqiCategory(
                label = "Unhealthy for Sensitive Groups",
                color = AqiOrange,
                healthImpact = "Some risk for sensitive groups.",
                recommendation = "Limit outdoor activity if you're in a sensitive group."
            )
            aqi in 151..200 -> AqiCategory(
                label = "Unhealthy",
                color = AqiRed,
                healthImpact = "Health effects possible for all; worse for sensitive groups.",
                recommendation = "Avoid outdoor exercise; stay indoors if possible."
            )
            aqi in 201..300 -> AqiCategory(
                label = "Very Unhealthy",
                color = AqiPurple,
                healthImpact = "Serious health risks for everyone.",
                recommendation = "Stay indoors; use air purifiers if available."
            )
            else -> AqiCategory(
                label = "Hazardous",
                color = AqiMaroon,
                healthImpact = "Emergency: high risk for all.",
                recommendation = "Avoid all outdoor activities; follow government alerts."
            )
        }
    }

    fun getAllCategories(): List<Triple<String, IntRange, AqiCategory>> {
        return listOf(
            Triple("0 – 50", 0..50, classify(25)),
            Triple("51 – 100", 51..100, classify(75)),
            Triple("101 – 150", 101..150, classify(125)),
            Triple("151 – 200", 151..200, classify(175)),
            Triple("201 – 300", 201..300, classify(250)),
            Triple("301+", 301..500, classify(350))
        )
    }
}

