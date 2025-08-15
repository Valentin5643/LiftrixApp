package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving the complete exercise library.
 * 
 * This use case provides access to all available exercises in the library,
 * which can be used for exercise selection, filtering, and metadata lookup
 * in various features like progress tracking and workout creation.
 * 
 * Features:
 * - Retrieves all exercises from the exercise library
 * - Provides complete exercise metadata (name, muscle groups, equipment)
 * - User-scoped access where applicable
 * - Error handling with proper context
 * - Performance optimized for UI components
 * 
 * Usage:
 * ```
 * val result = getExerciseLibraryUseCase()
 * result.fold(
 *     onSuccess = { exercises -> updateUI(exercises) },
 *     onFailure = { error -> handleError(error) }
 * )
 * ```
 */
@Singleton
class GetExerciseLibraryUseCase @Inject constructor(
    private val exerciseLibraryRepository: ExerciseLibraryRepository
) {
    
    /**
     * Retrieves all exercises from the exercise library.
     * 
     * @return LiftrixResult containing list of all available exercises or error
     */
    suspend operator fun invoke(): LiftrixResult<List<ExerciseLibrary>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "EXERCISE_LIBRARY_FETCH_FAILED",
                    errorMessage = "Failed to retrieve exercise library: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_EXERCISE_LIBRARY"
                    )
                )
            }
        ) {
            Timber.d("Retrieving exercise library")
            
            val exercises = exerciseLibraryRepository.getAllExercises().first()
            
            Timber.d("Retrieved ${exercises.size} exercises from library")
            exercises
        }
    }
    
    /**
     * Retrieves exercises filtered by user's recent usage.
     * 
     * @param userId User ID for scoping recent exercises
     * @param limit Maximum number of exercises to return
     * @return LiftrixResult containing list of recent exercises or error
     */
    suspend fun getRecentExercises(userId: String, limit: Int = 10): LiftrixResult<List<ExerciseLibrary>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "RECENT_EXERCISES_FETCH_FAILED",
                    errorMessage = "Failed to retrieve recent exercises for user $userId: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_RECENT_EXERCISES",
                        "userId" to userId,
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            // Validate user ID to prevent data leakage
            if (userId.isBlank()) {
                throw IllegalArgumentException("User ID cannot be blank")
            }
            
            Timber.d("Retrieving recent exercises for user: $userId, limit: $limit")
            
            val result = exerciseLibraryRepository.getRecentExercises(userId, limit)
            result.fold(
                onSuccess = { exercises ->
                    Timber.d("Retrieved ${exercises.size} recent exercises for user: $userId")
                    exercises
                },
                onFailure = { error ->
                    Timber.e("Failed to get recent exercises: $error")
                    throw Exception("Failed to get recent exercises: $error")
                }
            )
        }
    }
}