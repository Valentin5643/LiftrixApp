package com.example.liftrix.data.model

import kotlinx.serialization.Serializable

/**
 * Properly serializable data transfer objects for exercise sync operations.
 * These replace the usage of Map<String, Any> which cannot be serialized.
 */
@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val muscleGroup: String? = null,
    val sets: List<SetDto> = emptyList(),
    val notes: String? = null,
    val restTimeSeconds: Int? = null,
    val orderIndex: Int = 0
)

@Serializable
data class SetDto(
    val setNumber: Int,
    val targetReps: Int? = null,
    val actualReps: Int? = null,
    val targetWeight: Double? = null,
    val actualWeight: Double? = null,
    val completed: Boolean = false,
    val notes: String? = null,
    val rpe: Double? = null,
    val dropSet: Boolean = false,
    val superSet: Boolean = false
)

@Serializable
data class WorkoutSyncDto(
    val id: String,
    val userId: String,
    val name: String,
    val date: Long, // Epoch millis
    val status: String,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val exercises: List<ExerciseDto> = emptyList(),
    val notes: String? = null,
    val templateId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncVersion: Long,
    val isSynced: Boolean = true
)