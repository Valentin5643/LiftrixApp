package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Firestore DTO representing an exercise within a workout
 */
data class ExerciseDto(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("category")
    val category: String = "",
    
    @PropertyName("sets")
    val sets: List<ExerciseSetDto> = emptyList(),
    
    @PropertyName("notes")
    val notes: String? = null,
    
    @PropertyName("target_sets")
    val targetSets: Int? = null,
    
    @PropertyName("target_reps")
    val targetReps: Int? = null,
    
    @PropertyName("target_weight_kg")
    val targetWeightKg: Double? = null,
    
    @PropertyName("created_at")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("updated_at")
    val updatedAt: Timestamp = Timestamp.now()
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = "",
        name = "",
        category = "",
        sets = emptyList(),
        notes = null,
        targetSets = null,
        targetReps = null,
        targetWeightKg = null,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now()
    )
} 