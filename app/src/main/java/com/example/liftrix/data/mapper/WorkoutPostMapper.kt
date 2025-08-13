package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.MediaItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import com.example.liftrix.domain.model.social.MediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between WorkoutPost domain models and data layer entities.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Singleton
class WorkoutPostMapper @Inject constructor() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Converts WorkoutPostEntity to WorkoutPost domain model
     */
    fun toDomain(
        entity: WorkoutPostEntity,
        isLikedByViewer: Boolean = false,
        isSavedByViewer: Boolean = false,
        authorUsername: String? = null,
        authorDisplayName: String? = null,
        authorProfilePhotoUrl: String? = null,
        relevanceScore: Double = 0.0
    ): WorkoutPost {
        return WorkoutPost(
            id = entity.id,
            userId = entity.userId,
            workoutId = entity.workoutId,
            caption = entity.caption ?: "",
            mediaUrls = parseStringList(entity.mediaUrls),
            mediaThumbnails = parseStringList(entity.mediaThumbnails),
            mediaItems = createMediaItemsFromUrls(entity.mediaUrls, entity.mediaThumbnails),
            workoutDuration = entity.workoutDuration,
            totalVolume = entity.totalVolume,
            exercisesCount = entity.exercisesCount,
            prsCount = entity.prsCount,
            likeCount = entity.likeCount,
            commentCount = entity.commentCount,
            shareCount = entity.shareCount,
            saveCount = entity.saveCount,
            visibility = parseVisibility(entity.visibility),
            isLikedByViewer = isLikedByViewer,
            isSavedByViewer = isSavedByViewer,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            relevanceScore = relevanceScore,
            authorUsername = authorUsername ?: "",
            authorDisplayName = authorDisplayName ?: "",
            authorProfilePhotoUrl = authorProfilePhotoUrl
        )
    }
    
    /**
     * Converts WorkoutPost domain model to WorkoutPostEntity
     */
    fun toEntity(domain: WorkoutPost): WorkoutPostEntity {
        return WorkoutPostEntity(
            id = domain.id,
            userId = domain.userId,
            workoutId = domain.workoutId,
            caption = domain.caption,
            mediaUrls = serializeStringList(domain.mediaUrls),
            mediaThumbnails = serializeStringList(domain.mediaThumbnails),
            workoutDuration = domain.workoutDuration,
            totalVolume = domain.totalVolume,
            exercisesCount = domain.exercisesCount,
            prsCount = domain.prsCount,
            likeCount = domain.likeCount,
            commentCount = domain.commentCount,
            shareCount = domain.shareCount,
            saveCount = domain.saveCount,
            visibility = domain.visibility.name,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isSynced = false,
            syncVersion = 0
        )
    }
    
    /**
     * Creates WorkoutPostEntity from CreateWorkoutPostRequest
     */
    fun createEntityFromRequest(
        id: String,
        userId: String,
        request: CreateWorkoutPostRequest,
        workoutDuration: Int? = null,
        totalVolume: Double? = null,
        exercisesCount: Int? = null,
        prsCount: Int = 0
    ): WorkoutPostEntity {
        val currentTime = System.currentTimeMillis()
        
        return WorkoutPostEntity(
            id = id,
            userId = userId,
            workoutId = request.workoutId,
            caption = request.caption,
            mediaUrls = serializeStringList(request.mediaUrls),
            mediaThumbnails = null, // Will be generated during media processing
            workoutDuration = workoutDuration,
            totalVolume = totalVolume,
            exercisesCount = exercisesCount,
            prsCount = prsCount,
            likeCount = 0,
            commentCount = 0,
            shareCount = 0,
            saveCount = 0,
            visibility = request.visibility.name,
            createdAt = currentTime,
            updatedAt = currentTime,
            isSynced = false,
            syncVersion = 0
        )
    }
    
    /**
     * Parses JSON string array to List<String>
     */
    private fun parseStringList(jsonString: String?): List<String> {
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            json.decodeFromString(ListSerializer(String.serializer()), jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Serializes List<String> to JSON string
     */
    private fun serializeStringList(list: List<String>): String? {
        if (list.isEmpty()) return null
        
        return try {
            json.encodeToString(ListSerializer(String.serializer()), list)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parses string to PostVisibility enum
     */
    private fun parseVisibility(visibility: String): PostVisibility {
        return try {
            PostVisibility.valueOf(visibility)
        } catch (e: Exception) {
            PostVisibility.FOLLOWERS // Default fallback
        }
    }
    
    /**
     * Creates MediaItem list from URL strings
     * This is a temporary method until proper media metadata is stored in the database
     */
    private fun createMediaItemsFromUrls(mediaUrls: String?, mediaThumbnails: String?): List<MediaItem> {
        val urls = parseStringList(mediaUrls)
        val thumbnails = parseStringList(mediaThumbnails)
        
        if (urls.isEmpty()) return emptyList()
        
        return urls.mapIndexed { index, url ->
            MediaItem(
                id = "media_${System.currentTimeMillis()}_$index",
                type = inferMediaType(url),
                originalUrl = url,
                thumbnailUrl = thumbnails.getOrNull(index),
                compressedUrl = null,
                width = null,
                height = null,
                fileSizeBytes = 0L, // Unknown, would need to be fetched
                duration = null,
                uploadedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Infers media type from URL extension
     */
    private fun inferMediaType(url: String): MediaType {
        return when {
            url.contains(".mp4", ignoreCase = true) || 
            url.contains(".mov", ignoreCase = true) || 
            url.contains(".avi", ignoreCase = true) -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
    }
}