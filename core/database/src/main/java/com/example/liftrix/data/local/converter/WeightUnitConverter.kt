package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import com.example.liftrix.domain.model.WeightUnit

/**
 * Room type converter for WeightUnit enum
 * Converts between WeightUnit and String for database storage
 */
class WeightUnitConverter {
    
    @TypeConverter
    fun fromWeightUnit(weightUnit: WeightUnit): String {
        return weightUnit.name
    }
    
    @TypeConverter
    fun toWeightUnit(value: String): WeightUnit {
        return try {
            // First try to parse as enum name (KILOGRAMS, POUNDS)
            WeightUnit.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            // Fallback for legacy symbol-based storage (kg, lbs)
            WeightUnit.fromSymbol(value) ?: WeightUnit.getSystemDefault()
        }
    }
}