package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case to get previous workout data for comparison in workout details
 * Provides historical set data for each exercise to show previous performance
 */
@Singleton
class GetPreviousWorkoutDataUseCase @Inject constructor(
    private val exerciseSetDao: ExerciseSetDao
) {
    
    suspend operator fun invoke(request: GetPreviousWorkoutDataRequest): LiftrixResult<PreviousWorkoutData> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to get previous workout data: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_PREVIOUS_WORKOUT_DATA",
                        "user_id" to request.userId,
                        "exercise_library_ids" to request.exerciseLibraryIds.joinToString(",")
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                val previousSetDataMap = mutableMapOf<String, List<PreviousSetData>>()
                
                for (exerciseLibraryId in request.exerciseLibraryIds) {
                    // 🔥 DEBUG: Log query parameters for exercise history
                    Timber.d("[SETS-DEBUG-QUERY] Fetching history for userId='${request.userId}', exerciseLibraryId='$exerciseLibraryId', limit=10")
                    
                    // Get the last workout data for this exercise (excluding current workout)
                    val historicalSets = exerciseSetDao.getExerciseHistory(
                        userId = request.userId,
                        exerciseLibraryId = exerciseLibraryId,
                        limit = 10 // Get recent sets to find the most recent workout
                    )
                    
                    // 🔥 DEBUG: Log query results
                    Timber.d("[SETS-DEBUG-QUERY-RESULT] Query returned ${historicalSets.size} sets for exerciseLibraryId='$exerciseLibraryId'")
                    
                    // Filter out sets from the current workout if workoutId is provided
                    val filteredSets = if (request.excludeWorkoutId != null) {
                        // We need to filter by workout ID, but the entity doesn't directly expose it
                        // For now, we'll use the sets as-is since we're getting exercise history
                        historicalSets
                    } else {
                        historicalSets
                    }
                    
                    // Group by workout date and take the most recent workout
                    val setsByWorkout = filteredSets.groupBy { it.completedAt ?: 0L }
                        .filter { it.key > 0 } // Only completed sets
                        .toList()
                        .sortedByDescending { it.first } // Sort by completion time descending
                    
                    val previousSets = if (setsByWorkout.isNotEmpty()) {
                        // Take sets from the most recent previous workout
                        setsByWorkout.first().second.mapIndexed { index, setEntity ->
                            PreviousSetData(
                                setNumber = index + 1,
                                weight = setEntity.weightKg?.toDouble(),
                                reps = setEntity.reps,
                                completedAt = setEntity.completedAt
                            )
                        }
                    } else {
                        emptyList()
                    }
                    
                    previousSetDataMap[exerciseLibraryId] = previousSets
                }
                
                PreviousWorkoutData(
                    exercisePreviousData = previousSetDataMap
                )
            }
        }
}

/**
 * Request data for getting previous workout data
 */
data class GetPreviousWorkoutDataRequest(
    val userId: String,
    val exerciseLibraryIds: List<String>,
    val excludeWorkoutId: String? = null // Exclude sets from this workout
)

/**
 * Response containing previous workout data for exercises
 */
data class PreviousWorkoutData(
    val exercisePreviousData: Map<String, List<PreviousSetData>>
) {
    
    fun getPreviousSetData(exerciseLibraryId: String, setNumber: Int): PreviousSetData? {
        return exercisePreviousData[exerciseLibraryId]?.getOrNull(setNumber - 1)
    }
}

/**
 * Data class representing a previous set's performance
 */
data class PreviousSetData(
    val setNumber: Int,
    val weight: Double?,
    val reps: Int?,
    val completedAt: Long?
) {
    
    fun formatForDisplay(): String {
        return when {
            weight != null && reps != null -> "${weight.toInt()}kg x $reps"
            weight != null -> "${weight.toInt()}kg"
            reps != null -> "$reps reps"
            else -> "-"
        }
    }
}