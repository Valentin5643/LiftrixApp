package com.example.liftrix.data.service

import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for efficient exercise metadata operations with caching.
 * Part of Phase 1: User Profile Data Integration from SPEC-20250901-todo-implementation.
 */
@Singleton
class ExerciseMetadataService @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) {
    
    // In-memory cache for exercise metadata
    private val exerciseCache = ConcurrentHashMap<String, ExerciseLibrary>()
    
    // Cache expiry time in milliseconds (30 minutes)
    private val cacheExpiryTime = 30 * 60 * 1000L
    private val exerciseCacheTimestamps = ConcurrentHashMap<String, Long>()
    
    /**
     * Gets exercise name by ID with caching.
     * @param exerciseId The exercise ID to fetch name for
     * @param userId The user ID for analytics context (mandatory for user scoping)
     * @return LiftrixResult with exercise name or "Unknown" if not found
     */
    suspend fun getExerciseName(exerciseId: String, userId: String): LiftrixResult<String> =
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "EXERCISE_NAME_FETCH_FAILED",
                    errorMessage = "Failed to fetch exercise name",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "exercise_id" to exerciseId
                    )
                )
            }
        ) {
            require(exerciseId.isNotBlank()) { "Exercise ID cannot be blank" }
            require(userId.isNotBlank()) { "User ID cannot be blank for analytics" }
            
            // Check cache first
            val cachedExercise = exerciseCache[exerciseId]
            val cacheTimestamp = exerciseCacheTimestamps[exerciseId]
            
            if (cachedExercise != null && cacheTimestamp != null && 
                !isCacheExpired(cacheTimestamp)) {
                Timber.d("ExerciseMetadataService: Returning cached exercise name for $exerciseId")
                return@liftrixCatching cachedExercise.name
            }
            
            // Fetch from repository if not in cache or expired
            val exercise = exerciseRepository.getExerciseById(exerciseId).fold(
                onSuccess = { it },
                onFailure = { 
                    Timber.w("ExerciseMetadataService: Failed to fetch exercise $exerciseId")
                    null 
                }
            )
            
            if (exercise != null) {
                // Update cache
                exerciseCache[exerciseId] = exercise
                exerciseCacheTimestamps[exerciseId] = System.currentTimeMillis()
                
                Timber.d("ExerciseMetadataService: Cached exercise data for $exerciseId")
                exercise.name
            } else {
                "Unknown Exercise"
            }
        }
    
    /**
     * Gets full exercise metadata by ID with caching.
     * @param exerciseId The exercise ID to fetch metadata for
     * @param userId The user ID for analytics context (mandatory for user scoping)
     * @return LiftrixResult with ExerciseLibrary or null if not found
     */
    suspend fun getExerciseMetadata(exerciseId: String, userId: String): LiftrixResult<ExerciseLibrary?> =
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "EXERCISE_METADATA_FETCH_FAILED",
                    errorMessage = "Failed to fetch exercise metadata",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "exercise_id" to exerciseId
                    )
                )
            }
        ) {
            require(exerciseId.isNotBlank()) { "Exercise ID cannot be blank" }
            require(userId.isNotBlank()) { "User ID cannot be blank for analytics" }
            
            // Check cache first
            val cachedExercise = exerciseCache[exerciseId]
            val cacheTimestamp = exerciseCacheTimestamps[exerciseId]
            
            if (cachedExercise != null && cacheTimestamp != null && 
                !isCacheExpired(cacheTimestamp)) {
                Timber.d("ExerciseMetadataService: Returning cached exercise metadata for $exerciseId")
                return@liftrixCatching cachedExercise
            }
            
            // Fetch from repository if not in cache or expired
            val exercise = exerciseRepository.getExerciseById(exerciseId).fold(
                onSuccess = { it },
                onFailure = { 
                    Timber.w("ExerciseMetadataService: Failed to fetch exercise metadata $exerciseId")
                    null 
                }
            )
            
            if (exercise != null) {
                // Update cache
                exerciseCache[exerciseId] = exercise
                exerciseCacheTimestamps[exerciseId] = System.currentTimeMillis()
                
                Timber.d("ExerciseMetadataService: Cached exercise metadata for $exerciseId")
            }
            
            exercise
        }
    
    /**
     * Gets exercise display name with fallback strategy.
     * @param exerciseId The exercise ID
     * @param userId The user ID for analytics context
     * @return Display name or "Unknown Exercise" if not found
     */
    suspend fun getExerciseDisplayName(exerciseId: String, userId: String): String {
        return getExerciseName(exerciseId, userId).fold(
            onSuccess = { it },
            onFailure = { "Unknown Exercise" }
        )
    }
    
    /**
     * Batch fetches exercise names for multiple IDs.
     * @param exerciseIds List of exercise IDs to fetch names for
     * @param userId The user ID for analytics context (mandatory for user scoping)
     * @return LiftrixResult with map of exerciseId to exercise name
     */
    suspend fun getExerciseNames(exerciseIds: List<String>, userId: String): LiftrixResult<Map<String, String>> =
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "BATCH_EXERCISE_NAMES_FETCH_FAILED",
                    errorMessage = "Failed to batch fetch exercise names",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "exercise_count" to exerciseIds.size.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank for analytics" }
            
            val exerciseNames = mutableMapOf<String, String>()
            
            exerciseIds.forEach { exerciseId ->
                if (exerciseId.isNotBlank()) {
                    val name = getExerciseName(exerciseId, userId).fold(
                        onSuccess = { it },
                        onFailure = { "Unknown Exercise" }
                    )
                    exerciseNames[exerciseId] = name
                }
            }
            
            exerciseNames
        }
    
    /**
     * Invalidates cache for a specific exercise.
     * @param exerciseId The exercise ID to invalidate cache for
     */
    fun invalidateExerciseCache(exerciseId: String) {
        exerciseCache.remove(exerciseId)
        exerciseCacheTimestamps.remove(exerciseId)
        Timber.d("ExerciseMetadataService: Invalidated cache for exercise $exerciseId")
    }
    
    /**
     * Clears all cached exercise metadata.
     * Used for memory management and testing.
     */
    fun clearCache() {
        exerciseCache.clear()
        exerciseCacheTimestamps.clear()
        Timber.d("ExerciseMetadataService: Cleared all cached exercise metadata")
    }
    
    /**
     * Gets cache statistics for monitoring.
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            cacheSize = exerciseCache.size,
            totalRequests = exerciseCache.size + exerciseCacheTimestamps.size,
            hitRate = if (exerciseCache.size > 0) 
                exerciseCache.size.toDouble() / (exerciseCache.size + exerciseCacheTimestamps.size) 
                else 0.0
        )
    }
    
    /**
     * Checks if cache entry has expired.
     */
    private fun isCacheExpired(cacheTimestamp: Long): Boolean {
        return System.currentTimeMillis() - cacheTimestamp > cacheExpiryTime
    }
}

/**
 * Data class for cache statistics.
 */
data class CacheStats(
    val cacheSize: Int,
    val totalRequests: Int,
    val hitRate: Double
)