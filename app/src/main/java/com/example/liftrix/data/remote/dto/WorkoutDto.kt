package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore DTO representing a workout document
 */
data class WorkoutDto(
    @DocumentId
    val id: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("date")
    val date: String = "", // ISO date string (yyyy-MM-dd)
    
    @PropertyName("exercises")
    val exercises: List<ExerciseDto> = emptyList(),
    
    @PropertyName("status")
    val status: String = "",
    
    @PropertyName("start_time")
    val startTime: Timestamp? = null,
    
    @PropertyName("end_time")
    val endTime: Timestamp? = null,
    
    @PropertyName("notes")
    val notes: String? = null,
    
    @PropertyName("template_id")
    val templateId: String? = null,
    
    @PropertyName("created_at")
    val createdAt: Timestamp = Timestamp.now(),
    
    @PropertyName("updated_at")
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    
    @PropertyName("user_id")
    val userId: String = "",
    
    @PropertyName("version")
    val version: Long = 1L
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = "",
        name = "",
        date = "",
        exercises = emptyList(),
        status = "",
        startTime = null,
        endTime = null,
        notes = null,
        templateId = null,
        createdAt = Timestamp.now(),
        updatedAt = null,
        userId = "",
        version = 1L
    )
} 