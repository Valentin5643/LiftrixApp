@file:JvmName("ExerciseIdKt")

package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Value class representing a unique exercise identifier
 */
@JvmInline
@Serializable
value class ExerciseId(val value: String) {
    companion object {
        fun generate(): ExerciseId = ExerciseId(UUID.randomUUID().toString())
        fun fromString(value: String): ExerciseId = ExerciseId(value)
    }
    
    override fun toString(): String = value
} 