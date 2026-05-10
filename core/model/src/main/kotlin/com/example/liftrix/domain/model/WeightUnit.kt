package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class WeightUnit(
    val symbol: String,
    val displayName: String,
    val conversionFactorToKg: Double
) {
    KILOGRAMS("kg", "Kilograms", 1.0),
    POUNDS("lbs", "Pounds", 0.453592);

    fun convertFromKilograms(kilograms: Double): Double {
        return when (this) {
            KILOGRAMS -> kilograms
            POUNDS -> kilograms / conversionFactorToKg
        }
    }

    fun convertToKilograms(value: Double): Double {
        return when (this) {
            KILOGRAMS -> value
            POUNDS -> value * conversionFactorToKg
        }
    }

    fun formatWeight(kilograms: Double, precision: Int = 1): String {
        val convertedValue = convertFromKilograms(kilograms)
        return if (convertedValue == convertedValue.toInt().toDouble() && precision == 1) {
            "${convertedValue.toInt()} $symbol"
        } else {
            "${"%.${precision}f".format(convertedValue)} $symbol"
        }
    }

    companion object {
        fun getSystemDefault(): WeightUnit {
            return when (Locale.getDefault().country) {
                "US", "MM", "LR" -> POUNDS
                else -> KILOGRAMS
            }
        }

        fun fromSymbol(symbol: String): WeightUnit? {
            return when (symbol.trim().lowercase(Locale.US)) {
                "kg", "kgs", "kilogram", "kilograms" -> KILOGRAMS
                "lb", "lbs", "pound", "pounds" -> POUNDS
                else -> values().find { it.symbol.equals(symbol, ignoreCase = true) }
            }
        }
    }
}
