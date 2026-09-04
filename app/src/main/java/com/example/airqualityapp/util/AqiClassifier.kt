package com.example.airqualityapp.util

import androidx.compose.ui.graphics.Color

data class AqiCategory(
    val label: String,
    val color: Color,
    val healthImpact: String,
    val recommendation: String
)

/**
 * Classifies an AQI value into one of six health categories.
 *
 * Category boundaries come from the admin-configured thresholds and the
 * health texts from the admin-edited recommendations (both via [AqiConfig]);
 * the US-EPA defaults are used until the admin changes them.
 */
object AqiClassifier {

    val AqiGreen = Color(0xFF4CAF50)
    val AqiYellow = Color(0xFFFFC107)
    val AqiOrange = Color(0xFFFF9800)
    val AqiRed = Color(0xFFF44336)
    val AqiPurple = Color(0xFF9C27B0)
    val AqiMaroon = Color(0xFF800000)

    private val defaults = listOf(
        AqiCategory("Good", AqiGreen,
            "No health risk.",
            "Safe to enjoy outdoor activities."),
        AqiCategory("Moderate", AqiYellow,
            "Minor risk for sensitive groups (children, elderly, people with asthma).",
            "Sensitive people should reduce long outdoor stays."),
        AqiCategory("Unhealthy for Sensitive Groups", AqiOrange,
            "Some risk for sensitive groups.",
            "Limit outdoor activity if you're in a sensitive group."),
        AqiCategory("Unhealthy", AqiRed,
            "Health effects possible for all; worse for sensitive groups.",
            "Avoid outdoor exercise; stay indoors if possible."),
        AqiCategory("Very Unhealthy", AqiPurple,
            "Serious health risks for everyone.",
            "Stay indoors; use air purifiers if available."),
        AqiCategory("Hazardous", AqiMaroon,
            "Emergency: high risk for all.",
            "Avoid all outdoor activities; follow government alerts.")
    )

    /** Upper bound of each band (index 0..4); the last band is open-ended. */
    private fun upperBounds(): List<Int> {
        val t = AqiConfig.thresholds
        return listOf(t.goodMax, t.moderateMax, t.unhealthySensitiveMax, t.unhealthyMax, t.veryUnhealthyMax)
    }

    fun classify(aqi: Int): AqiCategory {
        val bounds = upperBounds()
        val index = bounds.indexOfFirst { aqi <= it }.let { if (it == -1) 5 else it }
        return withAdminText(defaults[index])
    }

    /** Category label, inclusive range and full category info for every band. */
    fun getAllCategories(): List<Triple<String, IntRange, AqiCategory>> {
        val bounds = upperBounds()
        val result = mutableListOf<Triple<String, IntRange, AqiCategory>>()
        var low = 0
        bounds.forEachIndexed { i, high ->
            result += Triple("$low – $high", low..high, withAdminText(defaults[i]))
            low = high + 1
        }
        result += Triple("$low+", low..500, withAdminText(defaults[5]))
        return result
    }

    private fun withAdminText(base: AqiCategory): AqiCategory {
        val custom = AqiConfig.recommendations[base.label] ?: return base
        return base.copy(
            healthImpact = custom.healthImpact.ifBlank { base.healthImpact },
            recommendation = custom.recommendation.ifBlank { base.recommendation }
        )
    }
}
