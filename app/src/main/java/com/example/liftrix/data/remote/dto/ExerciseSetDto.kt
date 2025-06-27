package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Firestore DTO representing an exercise set
 */
data class ExerciseSetDto(
    @PropertyName("set_number")
    val setNumber: Int = 0,
    
    @PropertyName("weight_kg")
    val weightKg: Double = 0.0,
    
    @PropertyName("reps")
    val reps: Int = 0,
    
    @PropertyName("is_completed")
    val isCompleted: Boolean = false,
    
    @PropertyName("rest_time_seconds")
    val restTimeSeconds: Int? = null,
    
    @PropertyName("notes")
    val notes: String? = null,
    
    @PropertyName("time_seconds")
    val timeSeconds: Int? = null,
    
    @PropertyName("distance_meters") 
    val distanceMeters: Float? = null,
    
    @PropertyName("rpe")
    val rpe: Int? = null,
    
    @PropertyName("completed_at")
    val completedAt: Timestamp? = null
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        setNumber = 0,
        weightKg = 0.0,
        reps = 0,
        isCompleted = false,
        restTimeSeconds = null,
        timeSeconds = null,
        distanceMeters = null,
        rpe = null,
        notes = null,
        completedAt = null
    )
} 