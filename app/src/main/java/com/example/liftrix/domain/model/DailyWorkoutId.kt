package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing a unique identifier for a daily workout
 */
@JvmInline
value class DailyWorkoutId(val value: String) {
    
    companion object {
        /**
         * Generates a new unique DailyWorkoutId
         */
        fun generate(): DailyWorkoutId = DailyWorkoutId("daily-${UUID.randomUUID()}")
        
        /**
         * Creates a DailyWorkoutId from an existing string value
         */
        fun fromString(value: String): DailyWorkoutId = DailyWorkoutId(value)
    }
    
    override fun toString(): String = value
} 