@file:JvmName("ExerciseSetIdKt")

package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing a unique exercise set identifier
 */
@JvmInline
value class ExerciseSetId(val value: String) {
    companion object {
        fun generate(): ExerciseSetId = ExerciseSetId(UUID.randomUUID().toString())
        fun fromString(value: String): ExerciseSetId = ExerciseSetId(value)
    }
    
    override fun toString(): String = value
}