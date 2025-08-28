package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.PostExercise
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.CustomExercise
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import com.example.liftrix.domain.model.social.MediaType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between WorkoutPost domain models and data layer entities.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Singleton
class WorkoutPostMapper @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val customExerciseDao: CustomExerciseDao
) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Converts WorkoutPostEntity to WorkoutPost domain model
     */
    suspend fun toDomain(
        entity: WorkoutPostEntity,
        isLikedByViewer: Boolean = false,
        isSavedByViewer: Boolean = false,
        authorUsername: String? = null,
        authorDisplayName: String? = null,
        authorProfilePhotoUrl: String? = null,
        relevanceScore: Double = 0.0
    ): WorkoutPost {
        // INT-004: Extract exercise details for display with custom exercise indicators
        val exercises = try {
            extractExercisesFromWorkout(entity.userId, entity.workoutId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract exercise details for workout post ${entity.id}")
            emptyList()
        }
        
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
            authorProfilePhotoUrl = authorProfilePhotoUrl,
            exercises = exercises
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
    
    /**
     * INT-004: Extracts exercise details from workout for social post display
     * with custom exercise indicators
     */
    private suspend fun extractExercisesFromWorkout(userId: String, workoutId: String): List<PostExercise> {
        try {
            // Get the workout entity
            val workout = workoutDao.getWorkoutByIdForUser(workoutId, userId)
                ?: return emptyList()
            
            // Parse exercises from JSON using the same logic as analytics
            val gson = Gson()
            val exercises = parseExercisesFromWorkoutJson(workout.exercisesJson, gson)
            
            // Convert to PostExercise objects with custom exercise detection
            return exercises.map { exercise ->
                val isCustom = isCustomExercise(exercise.name, userId)
                val customExerciseData = if (isCustom) {
                    getCustomExerciseData(exercise.name, userId)
                } else null
                
                PostExercise(
                    name = exercise.name,
                    isCustomExercise = isCustom,
                    customExerciseId = customExerciseData?.id?.value,
                    primaryMuscleGroup = customExerciseData?.primaryMuscle ?: inferMuscleGroupFromName(exercise.name),
                    setsCount = exercise.sets.size,
                    maxWeight = exercise.sets.mapNotNull { it.weight }.maxOrNull(),
                    isPR = false // PR detection handled by PRDetectionService
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract exercises for workout $workoutId")
            return emptyList()
        }
    }
    
    /**
     * Simple exercise data class for JSON parsing
     */
    private data class SimpleWorkoutExercise(
        val name: String,
        val sets: List<SimpleWorkoutSet> = emptyList()
    )
    
    private data class SimpleWorkoutSet(
        val weight: Double?,
        val reps: Int?
    )
    
    /**
     * Parse exercises from workout JSON
     */
    private fun parseExercisesFromWorkoutJson(exercisesJson: String, gson: Gson): List<SimpleWorkoutExercise> {
        if (exercisesJson.isBlank()) return emptyList()
        
        return try {
            val type = object : TypeToken<List<SimpleWorkoutExercise>>() {}.type
            gson.fromJson<List<SimpleWorkoutExercise>>(exercisesJson, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse exercises from JSON, returning empty list")
            emptyList()
        }
    }
    
    /**
     * Detect if an exercise is custom by checking naming patterns and database
     */
    private suspend fun isCustomExercise(exerciseName: String, userId: String): Boolean {
        return try {
            // Try to find this exercise in custom exercises
            val customExercises = customExerciseDao.searchCustomExercises(userId, exerciseName).firstOrNull()
            customExercises?.any { it.name.equals(exerciseName, ignoreCase = true) } == true
        } catch (e: Exception) {
            // Fallback: check for common custom exercise indicators
            exerciseName.contains("custom", ignoreCase = true) ||
            exerciseName.contains("modified", ignoreCase = true) ||
            exerciseName.contains("variation", ignoreCase = true)
        }
    }
    
    /**
     * Get custom exercise data if available
     */
    private suspend fun getCustomExerciseData(exerciseName: String, userId: String): CustomExercise? {
        return try {
            val customExercises = customExerciseDao.searchCustomExercises(userId, exerciseName).firstOrNull()
            val matchingEntity = customExercises?.find { it.name.equals(exerciseName, ignoreCase = true) }
            
            // Convert entity to domain model (simplified)
            matchingEntity?.let { entity ->
                CustomExercise(
                    id = com.example.liftrix.domain.model.CustomExerciseId(entity.id),
                    userId = entity.userId,
                    name = entity.name,
                    description = entity.description,
                    exerciseType = entity.exerciseType,
                    primaryMuscle = entity.primaryMuscleGroup,
                    secondaryMuscles = entity.secondaryMuscleGroups?.toSet() ?: emptySet(),
                    equipment = entity.equipment,
                    difficulty = entity.difficulty,
                    instructions = entity.instructions ?: emptyList(),
                    mainImageUrl = entity.mainImageUrl,
                    additionalImageUrls = entity.additionalImageUrls ?: emptyList(),
                    videoUrl = entity.videoUrl,
                    tags = entity.tags ?: emptyList(),
                    categories = entity.categories ?: emptyList(),
                    notes = entity.notes,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get custom exercise data for: $exerciseName")
            null
        }
    }
    
    /**
     * Simple inference of muscle group from exercise name
     */
    private fun inferMuscleGroupFromName(exerciseName: String): com.example.liftrix.domain.model.ExerciseCategory? {
        val lowerName = exerciseName.lowercase()
        return when {
            lowerName.contains("squat") || lowerName.contains("leg") -> com.example.liftrix.domain.model.ExerciseCategory.LEGS
            lowerName.contains("bench") || lowerName.contains("chest") -> com.example.liftrix.domain.model.ExerciseCategory.CHEST
            lowerName.contains("deadlift") || lowerName.contains("row") -> com.example.liftrix.domain.model.ExerciseCategory.BACK
            lowerName.contains("press") && lowerName.contains("shoulder") -> com.example.liftrix.domain.model.ExerciseCategory.SHOULDERS
            lowerName.contains("curl") -> com.example.liftrix.domain.model.ExerciseCategory.BICEPS
            else -> null
        }
    }
}