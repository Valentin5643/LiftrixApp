@file:JvmName("RepsKt")

package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

/**
 * Value class representing repetitions with validation
 */
@JvmInline
@Serializable
value class Reps(val count: Int) {
    init {
        require(count >= 0) { "Reps cannot be negative: $count" }
        require(count <= MAX_REPS) { "Reps cannot exceed $MAX_REPS: $count" }
    }
    
    companion object {
        const val MAX_REPS: Int = 1000
        
        fun of(count: Int): Reps = Reps(count)
        
        val ZERO: Reps = Reps(0)
    }
    
    operator fun plus(other: Reps): Reps = Reps(count + other.count)
    operator fun minus(other: Reps): Reps = Reps(count - other.count)
    operator fun times(multiplier: Int): Reps = Reps(count * multiplier)
    
    operator fun compareTo(other: Reps): Int = count.compareTo(other.count)
} 