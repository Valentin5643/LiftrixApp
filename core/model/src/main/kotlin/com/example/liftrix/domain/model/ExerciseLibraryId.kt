@file:JvmName("ExerciseLibraryIdKt")

package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing an exercise library identifier
 */
@JvmInline
value class ExerciseLibraryId(val value: String) {
    companion object {
        fun generate(): ExerciseLibraryId = ExerciseLibraryId("el_${UUID.randomUUID()}")
        fun fromString(value: String): ExerciseLibraryId = ExerciseLibraryId(value)
    }
} 