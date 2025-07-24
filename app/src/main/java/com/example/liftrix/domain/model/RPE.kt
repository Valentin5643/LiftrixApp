@file:JvmName("RPEKt")

package com.example.liftrix.domain.model

/**
 * Value class representing Rate of Perceived Exertion (RPE) on a scale of 1-10
 */
@JvmInline
value class RPE(val value: Int) {
    init {
        require(value in 1..10) { "RPE must be between 1 and 10: $value" }
    }
    
    companion object {
        const val MIN_RPE = 1
        const val MAX_RPE = 10
        
        fun fromInt(value: Int): RPE = RPE(value)
    }
    
    /**
     * Checks if this RPE indicates high intensity (8-10)
     */
    fun isHigh(): Boolean = value >= 8
    
    /**
     * Checks if this RPE indicates low intensity (1-3)
     */
    fun isLow(): Boolean = value <= 3
    
    /**
     * Checks if this RPE indicates moderate intensity (4-7)
     */
    fun isModerate(): Boolean = value in 4..7
    
    override fun toString(): String = value.toString()
}