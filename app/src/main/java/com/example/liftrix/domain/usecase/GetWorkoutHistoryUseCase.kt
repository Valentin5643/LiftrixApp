package com.example.liftrix.domain.usecase

import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for retrieving paginated workout history with proper error handling
 * Integrates with authentication system and repository layer
 */
@Singleton
class GetWorkoutHistoryUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    
    /**
     * Retrieves paginated workout history for the current authenticated user
     * 
     * @param limit Maximum number of workouts to return (default 20)
     * @param offset Number of workouts to skip for pagination (default 0)
     * @return Flow of Result containing list of WorkoutSummary objects
     */
    suspend fun execute(limit: Int = 20, offset: Int = 0): Flow<Result<List<WorkoutSummary>>> {
        return flow {
            // Validate pagination parameters
            require(limit > 0) { "Limit must be positive: $limit" }
            require(offset >= 0) { "Offset must be non-negative: $offset" }
            require(limit <= MAX_LIMIT) { "Limit cannot exceed $MAX_LIMIT: $limit" }
            
            // Get current authenticated user ID
            val userId = getCurrentUserIdUseCase() 
                ?: throw IllegalStateException("User not authenticated")
            
            Timber.d("Retrieving workout history for user: $userId, limit: $limit, offset: $offset")
            
            // Get workout history from repository
            workoutRepository.getUserWorkoutHistory(userId, limit, offset)
                .collect { summaries ->
                    emit(Result.success(summaries))
                    Timber.v("Retrieved ${summaries.size} workout summaries for user: $userId")
                }
                
        }.catch { throwable ->
            Timber.e(throwable, "Failed to retrieve workout history")
            emit(Result.failure(throwable))
        }
    }
    
    /**
     * Gets the total count of workouts for the current authenticated user
     * 
     * @return Result containing the total workout count
     */
    suspend fun getHistoryCount(): Result<Int> {
        return try {
            // Get current authenticated user ID
            val userId = getCurrentUserIdUseCase()
                ?: return Result.failure(IllegalStateException("User not authenticated"))
                
            Timber.d("Getting workout history count for user: $userId")
            
            // Get count from repository
            val count = workoutRepository.getWorkoutHistoryCount(userId)
            
            Timber.v("Total workout count for user $userId: $count")
            Result.success(count)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get workout history count")
            Result.failure(e)
        }
    }
    
    companion object {
        private const val MAX_LIMIT = 100 // Prevent excessive database queries
    }
}