package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing a unique identifier for a custom exercise
 */
@JvmInline
value class CustomExerciseId(val value: String) {
    
    companion object {
        /**
         * Generates a new unique CustomExerciseId
         */
        fun generate(): CustomExerciseId = CustomExerciseId("custom-${UUID.randomUUID()}")
        
        /**
         * Creates a CustomExerciseId from an existing string value
         */
        fun fromString(value: String): CustomExerciseId = CustomExerciseId(value)
    }
    
    override fun toString(): String = value
} 