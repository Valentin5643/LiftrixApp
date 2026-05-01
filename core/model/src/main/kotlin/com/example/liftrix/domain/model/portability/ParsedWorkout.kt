package com.example.liftrix.domain.model.portability

import java.time.LocalDateTime

data class ParsedWorkout(
    val id: String? = null,
    val name: String,
    val date: LocalDateTime,
    val duration: Long? = null, // Duration in seconds
    val exercises: List<ParsedExercise>,
    val notes: String? = null,
    val sourceApp: String? = null,
    val originalFormat: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ParsedExercise(
    val name: String,
    val category: String? = null,
    val sets: List<ParsedSet>,
    val notes: String? = null,
    val restTime: Long? = null, // Rest time in seconds
    val equipment: String? = null
)

data class ParsedSet(
    val reps: Int? = null,
    val weight: Double? = null, // Weight in kg
    val distance: Double? = null, // Distance in meters
    val duration: Long? = null, // Duration in seconds
    val completed: Boolean = true,
    val notes: String? = null,
    val restAfter: Long? = null // Rest after this set in seconds
)

enum class ExternalExerciseType {
    STRENGTH,
    CARDIO,
    FLEXIBILITY,
    SPORT,
    OTHER
}

data class ImportValidationError(
    val field: String,
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity {
    WARNING,
    ERROR,
    CRITICAL
}