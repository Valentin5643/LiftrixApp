@file:JvmName("VolumeKt")

package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

/**
 * Value class representing aggregated training volume in kilograms
 * 
 * Designed for daily/weekly/monthly training volume totals that can exceed
 * individual exercise weight limits. Separate from Weight domain type which
 * is constrained to single exercise loads.
 * 
 * Use cases:
 * - Daily training volume (can be 15,000+ kg)
 * - Weekly/monthly volume aggregations
 * - Volume calendar heat maps
 * - Progress tracking analytics
 */
@JvmInline
@Serializable
value class Volume(val kilograms: Double) {
    init {
        require(kilograms >= 0.0) { "Volume cannot be negative: $kilograms" }
        require(kilograms <= MAX_VOLUME_KG) { "Volume cannot exceed $MAX_VOLUME_KG kg: $kilograms" }
    }
    
    companion object {
        // Generous limit for aggregated training volume (100 tons)
        const val MAX_VOLUME_KG: Double = 100_000.0
        
        // Threshold for flagging unusually high volume days
        const val HIGH_VOLUME_THRESHOLD_KG: Double = 50_000.0
        
        /**
         * Creates Volume safely - if over limit, caps at max
         */
        fun fromKilograms(kg: Double): Volume {
            return when {
                kg < 0.0 -> Volume(0.0)
                kg > MAX_VOLUME_KG -> Volume(MAX_VOLUME_KG)
                else -> Volume(kg)
            }
        }
        
        /**
         * Creates Volume safely from pounds
         */
        fun fromPounds(lbs: Double): Volume {
            val kg = lbs * 0.453592
            return fromKilograms(kg)
        }
        
        /**
         * Creates Volume safely from any weight unit
         */
        fun fromValue(value: Double, unit: WeightUnit): Volume {
            val kg = unit.convertToKilograms(value)
            return fromKilograms(kg)
        }
        
        /**
         * Converts Weight to Volume (for individual exercise → aggregate totals)
         */
        fun fromWeight(weight: Weight): Volume = Volume(weight.kilograms)
        
        /**
         * Checks if volume is unusually high and should trigger review
         */
        fun isHighVolume(kg: Double): Boolean = kg > HIGH_VOLUME_THRESHOLD_KG
        
        val ZERO: Volume = Volume(0.0)
    }
    
    /**
     * Converts to pounds
     */
    fun toPounds(): Double = kilograms / 0.453592
    
    /**
     * Gets the volume value in the specified unit
     */
    fun getValue(unit: WeightUnit): Double = unit.convertFromKilograms(kilograms)
    
    /**
     * Formats volume with appropriate precision for large numbers
     */
    fun format(): String = when {
        kilograms >= 10_000.0 -> "%.0f kg".format(kilograms)
        kilograms >= 1_000.0 -> "%.1f kg".format(kilograms)
        else -> "%.1f kg".format(kilograms)
    }
    
    /**
     * Formats volume in the specified unit with appropriate precision
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
    
    /**
     * Converts to Weight (if within Weight constraints)
     * Useful for individual exercise calculations
     */
    fun toWeightOrNull(): Weight? = if (kilograms <= Weight.MAX_WEIGHT_KG) {
        Weight(kilograms)
    } else null
    
    operator fun plus(other: Volume): Volume = Volume(kilograms + other.kilograms)
    operator fun minus(other: Volume): Volume = Volume(maxOf(0.0, kilograms - other.kilograms))
    operator fun times(multiplier: Double): Volume = Volume(kilograms * multiplier)
    operator fun div(divisor: Double): Volume = Volume(kilograms / divisor)
    
    operator fun compareTo(other: Volume): Int = kilograms.compareTo(other.kilograms)
} 