package com.example.liftrix.core.formatting

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized formatter for weight values across the application.
 * Handles conversion between units and consistent formatting based on user preferences.
 */
@Singleton
class WeightFormatter @Inject constructor() {

    /**
     * Formats a weight value according to the specified unit with appropriate precision
     */
    fun formatWeight(weight: Weight, unit: WeightUnit, precision: Int = 1): String {
        return weight.format(unit, precision)
    }

    /**
     * Formats a weight value in kilograms according to the specified unit
     */
    fun formatWeightFromKg(kilograms: Double, unit: WeightUnit, precision: Int = 1): String {
        return unit.formatWeight(kilograms, precision)
    }

    /**
     * Gets the numeric value of a weight in the specified unit
     */
    fun getWeightValue(weight: Weight, unit: WeightUnit): Double {
        return weight.getValue(unit)
    }

    /**
     * Creates a Weight object from a value in the specified unit
     */
    fun createWeight(value: Double, unit: WeightUnit): Weight {
        return Weight.fromValue(value, unit)
    }

    /**
     * Formats weight for accessibility with full unit name
     */
    fun formatWeightForAccessibility(weight: Weight, unit: WeightUnit): String {
        val value = weight.getValue(unit)
        val formattedValue = if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            "%.1f".format(value)
        }
        return "$formattedValue ${unit.displayName.lowercase()}"
    }

    /**
     * Formats weight range (e.g., "45-90 kg" or "100-200 lbs")
     */
    fun formatWeightRange(minWeight: Weight, maxWeight: Weight, unit: WeightUnit): String {
        val minValue = minWeight.getValue(unit)
        val maxValue = maxWeight.getValue(unit)
        
        val minFormatted = if (minValue == minValue.toInt().toDouble()) {
            minValue.toInt().toString()
        } else {
            "%.1f".format(minValue)
        }
        
        val maxFormatted = if (maxValue == maxValue.toInt().toDouble()) {
            maxValue.toInt().toString()
        } else {
            "%.1f".format(maxValue)
        }
        
        return "$minFormatted-$maxFormatted ${unit.symbol}"
    }

    /**
     * Formats weight with context-appropriate precision
     * Uses integer format for whole numbers, decimal for precise values
     */
    fun formatWeightSmart(weight: Weight, unit: WeightUnit): String {
        val value = weight.getValue(unit)
        return if (value == value.toInt().toDouble()) {
            "${value.toInt()} ${unit.symbol}"
        } else {
            "${"%.1f".format(value)} ${unit.symbol}"
        }
    }

    /**
     * Parses a weight string in the given unit and returns a Weight object
     * Returns null if parsing fails
     */
    fun parseWeight(weightString: String, unit: WeightUnit): Weight? {
        return try {
            // Remove unit symbols and trim
            val cleanedString = weightString
                .replace(unit.symbol, "")
                .replace(unit.displayName, "")
                .trim()
            
            val value = cleanedString.toDoubleOrNull()
            if (value != null && value >= 0.0) {
                Weight.fromValue(value, unit)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}