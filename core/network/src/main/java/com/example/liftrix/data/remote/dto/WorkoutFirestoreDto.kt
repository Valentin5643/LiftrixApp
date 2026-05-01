package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

/**
 * Firestore DTO for workout documents with proper field mapping
 * Moved from private inner class to resolve Firestore deserialization warnings
 */
data class WorkoutFirestoreDto(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("userId")
    val userId: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("date")
    val date: Timestamp? = null,
    
    @PropertyName("updatedAt")
    val updatedAt: Timestamp? = null,
    
    @PropertyName("status")
    val status: String = "",
    
    @PropertyName("exercises")
    val exercises: List<Map<String, Any>> = emptyList(),
    
    @PropertyName("syncVersion")
    val syncVersion: Long? = null,
    
    @PropertyName("lastModified")
    val lastModified: Timestamp? = null,
    
    @get:Exclude
    val isSynced: Boolean = false,
    
    @PropertyName("createdAt")
    val createdAt: Timestamp? = null,
    
    @PropertyName("startTime")
    val startTime: Long? = null,
    
    @PropertyName("endTime")
    val endTime: Long? = null,
    
    @PropertyName("notes")
    val notes: String? = null,
    
    @PropertyName("templateId")
    val templateId: String? = null
) {
    // No-argument constructor required by Firestore
    constructor() : this(
        id = "",
        userId = "",
        name = "",
        date = null,
        updatedAt = null,
        status = "",
        exercises = emptyList(),
        syncVersion = null,
        lastModified = null,
        isSynced = false,
        createdAt = null,
        startTime = null,
        endTime = null,
        notes = null,
        templateId = null
    )
}