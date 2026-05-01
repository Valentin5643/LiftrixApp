package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Exclude

/**
 * Firestore DTO representing a workout document
 * Supports both Timestamp and Long (epoch millis) for backward compatibility
 *
 * @IgnoreExtraProperties: Suppresses warnings for legacy fields (e.g., "synced")
 * that exist in old Firestore documents but are no longer used in current schema
 */
@IgnoreExtraProperties
data class WorkoutDto(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("date")
    val date: Any? = null, // Can be Timestamp or Long epoch millis
    
    @PropertyName("exercises")
    val exercises: List<ExerciseDto> = emptyList(),
    
    @PropertyName("status")
    val status: String = "",
    
    @PropertyName("startTime")
    val startTime: Any? = null, // Can be Timestamp or Long epoch millis
    
    @PropertyName("endTime")
    val endTime: Any? = null, // Can be Timestamp or Long epoch millis
    
    @PropertyName("notes")
    val notes: String? = null,
    
    @PropertyName("templateId")
    val templateId: String? = null,
    
    @PropertyName("createdAt")
    val createdAt: Any = Timestamp.now(), // Can be Timestamp or Long epoch millis
    
    @PropertyName("updatedAt")
    @ServerTimestamp
    val updatedAt: Timestamp? = null, // Server-managed timestamp
    
    @PropertyName("userId")
    val userId: String = "",
    
    @PropertyName("version")
    val version: Long = 1L,
    
    // Sync metadata fields for Firestore sync compatibility
    @PropertyName("syncVersion")
    val syncVersion: Long = 1L,

    // 🔥 FIX (P0-1): Renamed from `isSynced` to `synced` to avoid Kotlin getter conflict
    // Firestore reflection generates both isSynced() and getIsSynced() causing:
    // RuntimeException: Found conflicting getters for name isSynced
    @PropertyName("is_synced")
    val synced: Boolean = false,

    @PropertyName("lastModified")
    val lastModified: Any? = null, // Can be Timestamp or Long epoch millis

    // Legacy field removed - no longer needed after renaming above field
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = "",
        name = "",
        date = null,
        exercises = emptyList(),
        status = "",
        startTime = null,
        endTime = null,
        notes = null,
        templateId = null,
        createdAt = Timestamp.now(),
        updatedAt = null,
        userId = "",
        version = 1L,
        syncVersion = 1L,
        synced = false,
        lastModified = null
    )
} 