package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing a unique identifier for a workout template
 */
@JvmInline
value class WorkoutTemplateId(val value: String) {
    
    companion object {
        /**
         * Generates a new unique WorkoutTemplateId
         */
        fun generate(): WorkoutTemplateId = WorkoutTemplateId("template-${UUID.randomUUID()}")
        
        /**
         * Creates a WorkoutTemplateId from an existing string value
         */
        fun fromString(value: String): WorkoutTemplateId = WorkoutTemplateId(value)
    }
    
    override fun toString(): String = value
} 