@file:JvmName("WorkoutIdKt")

package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing a unique workout identifier
 */
@JvmInline
value class WorkoutId(val value: String) {
    companion object {
        fun generate(): WorkoutId = WorkoutId(UUID.randomUUID().toString())
        fun fromString(value: String): WorkoutId = WorkoutId(value)
    }
    
    override fun toString(): String = value
} 