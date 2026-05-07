package com.example.liftrix.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wrapper for enhanced workout JSON data that includes metadata
 * Resolves JSON structure mismatch between storage and deserialization
 * Uses JsonElement for flexible parsing of complex domain objects
 */
@Serializable
data class EnhancedWorkoutData(
    val exercises: JsonElement,
    val totalVolume: Double? = null,
    val exercisesWithVolume: JsonElement? = null
)

/**
 * Backwards compatibility wrappers for parsing different JSON formats
 */
@Serializable
data class LegacyExerciseWrapper(
    val exercises: List<ExerciseDto>
)

/**
 * Simple wrapper for direct exercise list in sync operations
 */
@Serializable  
data class DirectExerciseListWrapper(
    val exercises: List<ExerciseDto>
)