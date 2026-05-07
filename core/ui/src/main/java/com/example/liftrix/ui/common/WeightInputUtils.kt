package com.example.liftrix.ui.common

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit

/**
 * Utility functions for safe weight input handling in UI
 */
object WeightInputUtils {
    
    /**
     * Safely creates Weight from text input without crashing
     * Returns null if input is invalid
     */
    fun safeCreateWeight(
        inputText: String,
        unit: WeightUnit = WeightUnit.KILOGRAMS
    ): Weight? {
        return try {
            val value = inputText.toDoubleOrNull() ?: return null
            if (value < 0) return null
            
            // Use safe creation method that caps at max
            Weight.fromValue(value, unit)
        } catch (e: Exception) {
            // Fallback - should not happen with new safe methods
            null
        }
    }
    
    /**
     * Validates weight input and returns validation message if needed
     */
    fun validateWeightInput(
        inputText: String,
        unit: WeightUnit = WeightUnit.KILOGRAMS
    ): String? {
        val value = inputText.toDoubleOrNull()
        
        return when {
            inputText.isBlank() -> null // Allow empty for optional fields
            value == null -> "Please enter a valid number"
            value < 0 -> "Weight cannot be negative"
            unit.convertToKilograms(value) > Weight.ANOMALY_THRESHOLD_KG -> 
                "This weight seems unusually high - please double-check"
            else -> null
        }
    }
    
    /**
     * Checks if weight input should trigger anomaly detection
     */
    fun shouldTriggerAnomaly(
        inputText: String,
        unit: WeightUnit = WeightUnit.KILOGRAMS
    ): Boolean {
        val value = inputText.toDoubleOrNull() ?: return false
        val kgValue = unit.convertToKilograms(value)
        return Weight.isAnomalousWeight(kgValue)
    }
}