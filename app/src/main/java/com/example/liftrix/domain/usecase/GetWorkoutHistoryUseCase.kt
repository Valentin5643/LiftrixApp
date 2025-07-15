package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
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
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Retrieves paginated workout history for the current authenticated user
     * 
     * @param limit Maximum number of workouts to return (default 20)
     * @param offset Number of workouts to skip for pagination (default 0)
     * @return Flow of LiftrixResult containing list of WorkoutSummary objects
     */
    suspend fun execute(limit: Int = 20, offset: Int = 0): Flow<LiftrixResult<List<WorkoutSummary>>> {
        return flow {
            val result = liftrixCatching<Unit>(
                errorMapper = { throwable: Throwable ->
                    when (throwable) {
                        is IllegalArgumentException -> LiftrixError.ValidationError(
                            field = "pagination",
                            violations = listOf(throwable.message ?: "Invalid pagination parameters")
                        )
                        is IllegalStateException -> LiftrixError.AuthenticationError(
                            errorMessage = throwable.message ?: "User not authenticated",
                        )
                        else -> LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve workout history",
                            operation = "getUserWorkoutHistory"
                        )
                    }
                }
            ) {
                // Validate pagination parameters
                require(limit > 0) { "Limit must be positive: $limit" }
                require(offset >= 0) { "Offset must be non-negative: $offset" }
                require(limit <= MAX_LIMIT) { "Limit cannot exceed $MAX_LIMIT: $limit" }
                
                // Get current authenticated user ID
                val userId = getCurrentUserIdUseCase() 
                    ?: throw IllegalStateException("User not authenticated")
                
                Timber.d("Retrieving workout history for user: $userId, limit: $limit, offset: $offset")
                
                // Get workout history from repository
                val historyResult = workoutRepository.getUserWorkoutHistory(userId, limit, offset)
                historyResult.fold(
                    onSuccess = { summaries ->
                        emit(LiftrixResult.success(summaries))
                        Timber.v("Retrieved ${summaries.size} workout summaries for user: $userId")
                    },
                    onFailure = { throwable ->
                        throw throwable
                    }
                )
            }
            
            result.fold(
                onSuccess = { /* Already emitted in collection */ },
                onFailure = { error: Throwable ->
                    Timber.e(error, "Failed to retrieve workout history")
                    emit(LiftrixResult.failure(error))
                }
            )
        }
    }
    
    /**
     * Gets the total count of workouts for the current authenticated user
     * 
     * @return LiftrixResult containing the total workout count
     */
    suspend fun getHistoryCount(): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when (throwable) {
                    is IllegalStateException -> LiftrixError.AuthenticationError(
                        errorMessage = throwable.message ?: "User not authenticated",
                    )
                    else -> LiftrixError.DatabaseError(
                        errorMessage = "Failed to get workout history count",
                        operation = "getWorkoutHistoryCount"
                    )
                }
            }
        ) {
            // Get current authenticated user ID
            val userId = getCurrentUserIdUseCase()
                ?: throw IllegalStateException("User not authenticated")
                
            Timber.d("Getting workout history count for user: $userId")
            
            // Get count from repository
            val countResult = workoutRepository.getWorkoutHistoryCount(userId)
            val count = countResult.getOrThrow()
            
            Timber.v("Total workout count for user $userId: $count")
            count
        }
    }
    
    companion object {
        private const val MAX_LIMIT = 100 // Prevent excessive database queries
    }
}