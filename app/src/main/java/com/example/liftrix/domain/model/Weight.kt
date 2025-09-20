@file:JvmName("WeightKt")

package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

/**
 * Value class representing weight in kilograms with validation
 */
@JvmInline
@Serializable
value class Weight(val kilograms: Double) {
    init {
        // Validation for constructor only - companion methods handle safety
        require(kilograms >= 0.0) { "Weight cannot be negative: $kilograms" }
        require(kilograms <= MAX_WEIGHT_KG) { "Weight cannot exceed $MAX_WEIGHT_KG kg: $kilograms" }
    }
    
    companion object {
        const val MAX_WEIGHT_KG: Double = 5000.0
        const val ANOMALY_THRESHOLD_KG: Double = 1000.0
        
        /**
         * Creates Weight safely - if over limit, caps at max and triggers anomaly detection
         */
        fun fromKilograms(kg: Double): Weight {
            return when {
                kg < 0.0 -> Weight(0.0)
                kg > MAX_WEIGHT_KG -> Weight(MAX_WEIGHT_KG) // Cap at max, don't crash
                else -> Weight(kg)
            }
        }
        
        /**
         * Creates Weight safely from pounds
         */
        fun fromPounds(lbs: Double): Weight {
            val kg = lbs * 0.453592
            return fromKilograms(kg)
        }
        
        /**
         * Creates Weight safely from any unit
         */
        fun fromValue(value: Double, unit: WeightUnit): Weight {
            val kg = unit.convertToKilograms(value)
            return fromKilograms(kg)
        }
        
        /**
         * Checks if weight is anomalously high and should trigger warning
         */
        fun isAnomalousWeight(kg: Double): Boolean {
            return kg > ANOMALY_THRESHOLD_KG
        }
        
        val ZERO: Weight = Weight(0.0)
    }
    
    fun toPounds(): Double = kilograms / 0.453592
    
    /**
     * Gets the weight value in the specified unit
     */
    fun getValue(unit: WeightUnit): Double = unit.convertFromKilograms(kilograms)
    
    /**
     * Formats weight with appropriate precision in kilograms (backward compatibility)
     */
    fun format(): String = "%.1f kg".format(kilograms)
    
    /**
     * Formats weight in the specified unit with appropriate precision
     */
    fun format(unit: WeightUnit, precision: Int = 1): String = unit.formatWeight(kilograms, precision)
    
    /**
     * Gets the numeric value in kilograms (alias for backward compatibility)
     */
    val value: Double get() = kilograms
    
    /**
     * Gets formatted display value (alias for backward compatibility)
     */
    val displayValue: String get() = format()
    
    operator fun plus(other: Weight): Weight = fromKilograms(kilograms + other.kilograms)
    operator fun minus(other: Weight): Weight = fromKilograms((kilograms - other.kilograms).coerceAtLeast(0.0))
    operator fun times(multiplier: Double): Weight = fromKilograms(kilograms * multiplier)
    operator fun div(divisor: Double): Weight = fromKilograms(kilograms / divisor)
    
    operator fun compareTo(other: Weight): Int = kilograms.compareTo(other.kilograms)
} 