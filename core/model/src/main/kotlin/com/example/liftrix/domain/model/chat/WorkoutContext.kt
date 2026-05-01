package com.example.liftrix.domain.model.chat

import kotlinx.serialization.Serializable

/**
 * Workout context data to provide to the AI for better responses.
 */
@Serializable
data class WorkoutContext(
    val currentWorkoutId: String? = null,
    val exerciseName: String? = null,
    val exerciseCategory: String? = null,
    val previousWeight: Double? = null,
    val previousReps: Int? = null,
    val targetWeight: Double? = null,
    val targetReps: Int? = null,
    val muscleGroups: List<String> = emptyList(),
    val workoutDuration: Long? = null,
    val restTime: Int? = null
)