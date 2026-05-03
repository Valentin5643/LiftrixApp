package com.example.liftrix.data.model

import kotlinx.serialization.Serializable

/**
 * Type-safe payload system for sync operations using sealed classes.
 * 
 * This replaces the problematic `Any` type usage in OfflineQueueManager,
 * ensuring compile-time type safety and proper kotlinx.serialization support.
 * 
 * Architecture:
 * - Each entity type has its own payload wrapper
 * - All payloads use @Serializable DTOs for data transfer
 * - Sealed class hierarchy provides exhaustive type checking
 */
@Serializable
sealed class SyncPayload {
    
    @Serializable
    data class WorkoutPayload(
        val workout: WorkoutSyncDto
    ) : SyncPayload()
    
    @Serializable
    data class ProfilePayload(
        val userId: String,
        val displayName: String? = null,
        val email: String? = null,
        val profileImageUrl: String? = null,
        val goals: List<String> = emptyList(),
        val preferences: Map<String, String> = emptyMap(),
        val syncVersion: Long,
        val lastModified: Long
    ) : SyncPayload()
    
    @Serializable
    data class TemplatePayload(
        val templateId: String,
        val userId: String,
        val name: String,
        val description: String? = null,
        val exercises: List<ExerciseDto> = emptyList(),
        val isPublic: Boolean = false,
        val syncVersion: Long,
        val lastModified: Long
    ) : SyncPayload()
    
    @Serializable
    data class AchievementPayload(
        val achievementId: String,
        val userId: String,
        val type: String,
        val title: String,
        val description: String,
        val unlockedAt: Long,
        val syncVersion: Long
    ) : SyncPayload()
    
    @Serializable
    data class SocialProfilePayload(
        val userId: String,
        val username: String,
        val displayName: String,
        val bio: String? = null,
        val isPrivate: Boolean = false,
        val followerCount: Int = 0,
        val followingCount: Int = 0,
        val syncVersion: Long,
        val lastModified: Long
    ) : SyncPayload()
    
    /**
     * 🔥 NEW: Payload for FETCH operations to download remote data
     */
    @Serializable
    data class FetchPayload(
        val userId: String,
        val entityType: String,
        val lastSyncTimestamp: Long = 0L,
        val fetchAll: Boolean = true
    ) : SyncPayload()
}

/**
 * Helper functions for type-safe payload creation
 */
object SyncPayloadFactory {
    
    fun createWorkoutPayload(workoutDto: WorkoutSyncDto): SyncPayload.WorkoutPayload {
        return SyncPayload.WorkoutPayload(workout = workoutDto)
    }
    
    fun createProfilePayload(
        userId: String,
        displayName: String?,
        email: String?,
        profileImageUrl: String?,
        goals: List<String>,
        preferences: Map<String, String>,
        syncVersion: Long,
        lastModified: Long
    ): SyncPayload.ProfilePayload {
        return SyncPayload.ProfilePayload(
            userId = userId,
            displayName = displayName,
            email = email,
            profileImageUrl = profileImageUrl,
            goals = goals,
            preferences = preferences,
            syncVersion = syncVersion,
            lastModified = lastModified
        )
    }
    
    fun createTemplatePayload(
        templateId: String,
        userId: String,
        name: String,
        description: String?,
        exercises: List<ExerciseDto>,
        isPublic: Boolean,
        syncVersion: Long,
        lastModified: Long
    ): SyncPayload.TemplatePayload {
        return SyncPayload.TemplatePayload(
            templateId = templateId,
            userId = userId,
            name = name,
            description = description,
            exercises = exercises,
            isPublic = isPublic,
            syncVersion = syncVersion,
            lastModified = lastModified
        )
    }
    
    /**
     * 🔥 NEW: Creates a FETCH payload for downloading remote data
     */
    fun createFetchPayload(
        userId: String,
        entityType: String,
        lastSyncTimestamp: Long = 0L,
        fetchAll: Boolean = true
    ): SyncPayload.FetchPayload {
        return SyncPayload.FetchPayload(
            userId = userId,
            entityType = entityType,
            lastSyncTimestamp = lastSyncTimestamp,
            fetchAll = fetchAll
        )
    }
}