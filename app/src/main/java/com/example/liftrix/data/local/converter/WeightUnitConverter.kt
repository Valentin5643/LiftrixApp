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
        return weightUnit.symbol
    }
    
    @TypeConverter
    fun toWeightUnit(symbol: String): WeightUnit {
        return WeightUnit.fromSymbol(symbol) ?: WeightUnit.getSystemDefault()
    }
}