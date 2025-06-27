@file:JvmName("WeightKt")

package com.example.liftrix.domain.model

/**
 * Value class representing weight in kilograms with validation
 */
@JvmInline
value class Weight(val kilograms: Double) {
    init {
        require(kilograms >= 0.0) { "Weight cannot be negative: $kilograms" }
        require(kilograms <= MAX_WEIGHT_KG) { "Weight cannot exceed $MAX_WEIGHT_KG kg: $kilograms" }
    }
    
    companion object {
        const val MAX_WEIGHT_KG: Double = 1000.0
        
        fun fromKilograms(kg: Double): Weight = Weight(kg)
        fun fromPounds(lbs: Double): Weight = Weight(lbs * 0.453592)
        
        val ZERO: Weight = Weight(0.0)
    }
    
    fun toPounds(): Double = kilograms / 0.453592
    
    /**
     * Formats weight with appropriate precision
     */
    fun format(): String = "%.1f kg".format(kilograms)
    
    operator fun plus(other: Weight): Weight = Weight(kilograms + other.kilograms)
    operator fun minus(other: Weight): Weight = Weight(kilograms - other.kilograms)
    operator fun times(multiplier: Double): Weight = Weight(kilograms * multiplier)
    operator fun div(divisor: Double): Weight = Weight(kilograms / divisor)
    
    operator fun compareTo(other: Weight): Int = kilograms.compareTo(other.kilograms)
} 