package com.example.liftrix.core.formatting

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightFormatter @Inject constructor() {
    fun formatWeight(weight: Weight, unit: WeightUnit, precision: Int = 1): String =
        weight.format(unit, precision)

    fun formatWeightFromKg(kilograms: Double, unit: WeightUnit, precision: Int = 1): String =
        unit.formatWeight(kilograms, precision)

    fun getWeightValue(weight: Weight, unit: WeightUnit): Double =
        weight.getValue(unit)

    fun createWeight(value: Double, unit: WeightUnit): Weight =
        Weight.fromValue(value, unit)

    fun formatWeightForAccessibility(weight: Weight, unit: WeightUnit): String {
        val value = weight.getValue(unit)
        val formattedValue = if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            "%.1f".format(value)
        }
        return "$formattedValue ${unit.displayName.lowercase()}"
    }

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

    fun formatWeightSmart(weight: Weight, unit: WeightUnit): String {
        val value = weight.getValue(unit)
        return if (value == value.toInt().toDouble()) {
            "${value.toInt()} ${unit.symbol}"
        } else {
            "${"%.1f".format(value)} ${unit.symbol}"
        }
    }

    fun parseWeight(weightString: String, unit: WeightUnit): Weight? {
        return try {
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
