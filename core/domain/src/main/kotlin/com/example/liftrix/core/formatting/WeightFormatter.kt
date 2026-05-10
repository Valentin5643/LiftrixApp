package com.example.liftrix.core.formatting

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import java.util.Locale
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

    fun formatWeightText(text: String, targetUnit: WeightUnit, precision: Int = 1): String {
        return WeightTextConverter.convertText(text, targetUnit, precision)
    }

    fun parseWeight(weightString: String, unit: WeightUnit): Weight? {
        val parsed = WeightTextConverter.parseFirst(weightString)
        if (parsed != null && parsed.value >= 0.0) {
            return Weight.fromValue(parsed.value, parsed.unit)
        }

        return try {
            val value = weightString.trim().toDoubleOrNull()
            if (value != null && value >= 0.0) Weight.fromValue(value, unit) else null
        } catch (e: Exception) {
            null
        }
    }
}

object WeightTextConverter {
    private const val KG_TO_LBS = 2.20462

    private val weightPattern = Regex(
        pattern = """(?<![\p{L}\p{N}.])([+-]?(?:\d+(?:\.\d+)?|\.\d+))\s*(kg|kgs|kilogram|kilograms|lb|lbs|pound|pounds)\b""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    data class ParsedWeight(
        val value: Double,
        val unit: WeightUnit,
        val valueText: String,
        val unitText: String
    )

    fun parseFirst(text: String): ParsedWeight? {
        return weightPattern.find(text)?.toParsedWeight()
    }

    fun convertText(text: String, targetUnit: WeightUnit, precision: Int = 1): String {
        return weightPattern.replace(text) { match ->
            val parsed = match.toParsedWeight() ?: return@replace match.value
            if (parsed.value < 0.0 || parsed.unit == targetUnit) {
                match.value
            } else {
                val convertedValue = when (targetUnit) {
                    WeightUnit.KILOGRAMS -> parsed.value / KG_TO_LBS
                    WeightUnit.POUNDS -> parsed.value * KG_TO_LBS
                }
                "${formatValue(convertedValue, precision)} ${targetUnit.symbol}"
            }
        }
    }

    private fun MatchResult.toParsedWeight(): ParsedWeight? {
        val valueText = groups[1]?.value ?: return null
        val unitText = groups[2]?.value ?: return null
        val value = valueText.toDoubleOrNull() ?: return null
        val unit = WeightUnit.fromSymbol(unitText) ?: return null
        return ParsedWeight(value, unit, valueText, unitText)
    }

    private fun formatValue(value: Double, precision: Int): String {
        val normalized = if (kotlin.math.abs(value) < 0.0000001) 0.0 else value
        return if (normalized == normalized.toLong().toDouble()) {
            normalized.toLong().toString()
        } else {
            "%.${precision}f".format(Locale.US, normalized)
        }
    }
}
