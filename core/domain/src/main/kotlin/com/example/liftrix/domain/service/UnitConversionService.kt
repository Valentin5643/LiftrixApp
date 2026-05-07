package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.util.DomainLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Service for unit conversion based on user preferences.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Provides reactive unit conversion capabilities that automatically adapt
 * to user's weight and distance unit preferences from synced settings.
 */
@Singleton
class UnitConversionService @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    // ========================================
    // Weight Conversion Methods
    // ========================================

    /**
     * Converts weight from kilograms to user's preferred unit
     */
    fun kgToUserUnit(kg: Double, userWeightUnit: WeightUnit): Double {
        return when (userWeightUnit) {
            WeightUnit.KILOGRAMS -> kg
            WeightUnit.POUNDS -> kg * 2.20462
        }
    }

    /**
     * Converts weight from user's unit to kilograms for storage
     */
    fun userUnitToKg(value: Double, userWeightUnit: WeightUnit): Double {
        return when (userWeightUnit) {
            WeightUnit.KILOGRAMS -> value
            WeightUnit.POUNDS -> value / 2.20462
        }
    }

    /**
     * Formats weight value with appropriate unit symbol
     */
    fun formatWeight(kg: Double, userWeightUnit: WeightUnit, precision: Int = 1): String {
        val convertedValue = kgToUserUnit(kg, userWeightUnit)
        return if (convertedValue == convertedValue.toInt().toDouble() && precision == 1) {
            "${convertedValue.toInt()} ${userWeightUnit.symbol}"
        } else {
            "${"%.${precision}f".format(convertedValue)} ${userWeightUnit.symbol}"
        }
    }

    /**
     * Returns a Flow that emits formatted weight strings based on user preferences
     */
    fun observeFormattedWeight(userId: String, kg: Double, precision: Int = 1): Flow<String> {
        return settingsRepository.observeWeightUnit(userId).map { weightUnit ->
            formatWeight(kg, weightUnit, precision)
        }
    }

    // ========================================
    // Distance Conversion Methods
    // ========================================

    /**
     * Converts distance from kilometers to user's preferred unit
     */
    fun kmToUserUnit(km: Double, distanceUnit: String): Double {
        return when (distanceUnit.uppercase()) {
            "MILES" -> km * 0.621371
            else -> km
        }
    }

    /**
     * Converts distance from user's unit to kilometers for storage
     */
    fun userUnitToKm(value: Double, distanceUnit: String): Double {
        return when (distanceUnit.uppercase()) {
            "MILES" -> value / 0.621371
            else -> value
        }
    }

    /**
     * Formats distance value with appropriate unit symbol
     */
    fun formatDistance(km: Double, distanceUnit: String, precision: Int = 1): String {
        val convertedValue = kmToUserUnit(km, distanceUnit)
        val unit = when (distanceUnit.uppercase()) {
            "MILES" -> "mi"
            else -> "km"
        }
        
        return if (convertedValue == convertedValue.toInt().toDouble() && precision == 1) {
            "${convertedValue.toInt()} $unit"
        } else {
            "${"%.${precision}f".format(convertedValue)} $unit"
        }
    }

    // ========================================
    // Volume/Plate Calculation Helpers
    // ========================================

    /**
     * Calculates appropriate plate combinations for a given weight in user's unit
     */
    fun calculatePlateBreakdown(totalWeightKg: Double, userWeightUnit: WeightUnit): Map<Double, Int> {
        val weightInUserUnit = kgToUserUnit(totalWeightKg, userWeightUnit)
        
        val standardPlates = when (userWeightUnit) {
            WeightUnit.KILOGRAMS -> listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)
            WeightUnit.POUNDS -> listOf(45.0, 35.0, 25.0, 15.0, 10.0, 5.0, 2.5, 1.25)
        }
        
        val plateCount = mutableMapOf<Double, Int>()
        var remainingWeight = weightInUserUnit
        
        for (plate in standardPlates) {
            val count = (remainingWeight / plate).toInt()
            if (count > 0) {
                plateCount[plate] = count
                remainingWeight -= count * plate
            }
        }
        
        return plateCount
    }

    // ========================================
    // Reactive Conversion Helpers
    // ========================================

    /**
     * Reactive weight conversion that updates when user preferences change
     */
    fun observeConvertedWeight(userId: String, weightKg: Double): Flow<Double> {
        return settingsRepository.observeWeightUnit(userId).map { weightUnit ->
            kgToUserUnit(weightKg, weightUnit)
        }
    }

    /**
     * Helper for ViewModels to get current weight unit
     */
    suspend fun getCurrentWeightUnit(userId: String): WeightUnit {
        return try {
            val settings = settingsRepository.getUserSettingsSync(userId)
            settings?.weightUnit ?: WeightUnit.getSystemDefault()
        } catch (e: Exception) {
            DomainLogger.e(e, "Failed to get current weight unit for user %s", userId)
            WeightUnit.getSystemDefault()
        }
    }

    /**
     * Helper for ViewModels to get current distance unit
     */
    suspend fun getCurrentDistanceUnit(userId: String): String {
        return try {
            val settings = settingsRepository.getUserSettingsSync(userId)
            // Note: This would need to be added to UserSettings domain model
            // For now, return system default based on weight unit
            when (settings?.weightUnit) {
                WeightUnit.POUNDS -> "MILES"
                else -> "KM"
            }
        } catch (e: Exception) {
            DomainLogger.e(e, "Failed to get current distance unit for user %s", userId)
            "KM"
        }
    }

    // ========================================
    // Validation Helpers
    // ========================================

    /**
     * Validates if a weight value is reasonable for the given unit
     */
    fun isValidWeight(value: Double, weightUnit: WeightUnit): Boolean {
        return when (weightUnit) {
            WeightUnit.KILOGRAMS -> value in 0.1..1000.0
            WeightUnit.POUNDS -> value in 0.2..2200.0
        }
    }

    /**
     * Validates if a distance value is reasonable for the given unit
     */
    fun isValidDistance(value: Double, distanceUnit: String): Boolean {
        return when (distanceUnit.uppercase()) {
            "MILES" -> value in 0.01..1000.0
            else -> value in 0.01..1600.0
        }
    }
}
