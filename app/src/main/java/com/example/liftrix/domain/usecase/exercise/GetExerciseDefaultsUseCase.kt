package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.*
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Use case for calculating intelligent default values for exercise parameters
 * based on user's workout history and exercise characteristics.
 * 
 * Provides smart defaults for sets, reps, weight, and rest time by analyzing:
 * 1. User's historical performance with the specific exercise
 * 2. Exercise type characteristics and muscle group patterns
 * 3. Sensible fallback defaults for new users
 */
@Singleton
class GetExerciseDefaultsUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao
) {
    
    companion object {
        private const val HISTORY_LIMIT = 10 // Look at last 10 instances
        private const val MIN_HISTORY_ENTRIES = 3 // Minimum entries to use history
        private const val RECENCY_WEIGHT = 0.7f // Weight recent entries more heavily
    }
    
    /**
     * Gets intelligent defaults for an exercise based on user history and exercise characteristics
     * 
     * @param exerciseId The exercise to get defaults for
     * @param userId The user's ID to analyze history for
     * @param exerciseLibrary The exercise library entry for type analysis
     * @return ExerciseDefaults with intelligent suggestions
     */
    suspend operator fun invoke(
        exerciseId: ExerciseId,
        userId: String,
        exerciseLibrary: ExerciseLibrary
    ): Result<ExerciseDefaults> {
        return try {
            Timber.d("Getting exercise defaults for exercise: ${exerciseLibrary.name}, user: $userId")
            
            // First try to get defaults from user's history
            val historyDefaults = calculateHistoryBasedDefaults(exerciseId, userId, exerciseLibrary)
            
            if (historyDefaults != null) {
                Timber.d("Using history-based defaults for ${exerciseLibrary.name}")
                Result.success(historyDefaults)
            } else {
                // Fall back to exercise type-based defaults
                Timber.d("No sufficient history found, using exercise type defaults for ${exerciseLibrary.name}")
                val typeDefaults = getExerciseTypeDefaults(exerciseLibrary)
                Result.success(typeDefaults)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get exercise defaults for ${exerciseLibrary.name}")
            // Return fallback defaults on any error
            Result.success(ExerciseDefaults.createFallback())
        }
    }
    
    /**
     * Calculates defaults based on user's historical performance with this exercise
     */
    private suspend fun calculateHistoryBasedDefaults(
        exerciseId: ExerciseId,
        userId: String,
        exerciseLibrary: ExerciseLibrary
    ): ExerciseDefaults? {
        try {
            // Get recent exercise history
            val exerciseHistory = exerciseDao.getExerciseHistory(
                userId = userId,
                exerciseLibraryId = exerciseLibrary.id,
                limit = HISTORY_LIMIT
            )
            
            if (exerciseHistory.size < MIN_HISTORY_ENTRIES) {
                Timber.d("Insufficient history entries: ${exerciseHistory.size} < $MIN_HISTORY_ENTRIES")
                return null
            }
            
            // Analyze historical sets for each exercise instance
            val historicalSetsData = mutableListOf<ExerciseHistoryData>()
            
            exerciseHistory.forEach { exerciseEntity ->
                val sets = exerciseSetDao.getSetsByExercise(exerciseEntity.id)
                val completedSets = sets.filter { it.reps != null && it.reps > 0 }
                
                if (completedSets.isNotEmpty()) {
                    val avgReps = completedSets.map { it.reps!! }.average().roundToInt()
                    val avgWeight = completedSets.mapNotNull { it.weightKg }.takeIf { it.isNotEmpty() }?.average()
                    
                    historicalSetsData.add(
                        ExerciseHistoryData(
                            sets = completedSets.size,
                            averageReps = avgReps,
                            averageWeight = avgWeight,
                            timestamp = exerciseEntity.createdAt
                        )
                    )
                }
            }
            
            if (historicalSetsData.isEmpty()) {
                Timber.d("No usable historical data found")
                return null
            }
            
            // Calculate weighted averages with recency bias
            val weightedSets = calculateWeightedAverage(
                historicalSetsData.map { it.sets.toDouble() },
                historicalSetsData.map { it.timestamp }
            ).roundToInt().coerceIn(ExerciseDefaults.MIN_SETS, ExerciseDefaults.MAX_SETS)
            
            val weightedReps = calculateWeightedAverage(
                historicalSetsData.map { it.averageReps.toDouble() },
                historicalSetsData.map { it.timestamp }
            ).roundToInt().coerceIn(1, 50)
            
            val weightedWeight = historicalSetsData.mapNotNull { it.averageWeight }.takeIf { it.isNotEmpty() }?.let { weights ->
                val weightValues = weights.zip(historicalSetsData.map { it.timestamp }) { weight, timestamp ->
                    weight to timestamp
                }
                calculateWeightedAverage(
                    weightValues.map { it.first },
                    weightValues.map { it.second }
                )
            }
            
            // Determine rest time based on exercise characteristics and weight
            val restTime = determineRestTimeFromHistory(exerciseLibrary, weightedWeight)
            
            Timber.d("History-based defaults: sets=$weightedSets, reps=$weightedReps, weight=$weightedWeight, rest=$restTime")
            
            return ExerciseDefaults(
                sets = weightedSets,
                reps = Reps(weightedReps),
                weight = weightedWeight?.let { Weight(it) },
                restTimeSeconds = restTime,
                source = DefaultSource.HISTORY
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating history-based defaults")
            return null
        }
    }
    
    /**
     * Gets defaults based on exercise type and characteristics
     */
    private fun getExerciseTypeDefaults(exerciseLibrary: ExerciseLibrary): ExerciseDefaults {
        val exerciseType = ExerciseType.fromLibraryExercise(exerciseLibrary)
        
        return ExerciseDefaults.fromExerciseType(
            exerciseType = exerciseType,
            primaryMuscle = exerciseLibrary.primaryMuscleGroup,
            isCompound = exerciseLibrary.isCompound
        )
    }
    
    /**
     * Calculates weighted average with recency bias
     */
    private fun calculateWeightedAverage(values: List<Double>, timestamps: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values.first()
        
        val now = System.currentTimeMillis()
        val maxAge = timestamps.maxOf { now - it }
        
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        values.forEachIndexed { index, value ->
            val age = now - timestamps[index]
            val recencyFactor = if (maxAge > 0) {
                1.0 - (age.toDouble() / maxAge) * (1.0 - RECENCY_WEIGHT)
            } else {
                1.0
            }
            
            weightedSum += value * recencyFactor
            totalWeight += recencyFactor
        }
        
        return if (totalWeight > 0) weightedSum / totalWeight else values.average()
    }
    
    /**
     * Determines rest time based on exercise characteristics and weight intensity
     */
    private fun determineRestTimeFromHistory(
        exerciseLibrary: ExerciseLibrary,
        averageWeight: Double?
    ): Int {
        return when {
            exerciseLibrary.isCompound -> {
                // Compound movements need longer rest
                when (exerciseLibrary.primaryMuscleGroup) {
                    ExerciseCategory.LEGS -> 180 // Leg compounds are most taxing
                    ExerciseCategory.BACK, ExerciseCategory.CHEST -> 150
                    else -> 120
                }
            }
            exerciseLibrary.equipment == Equipment.BODYWEIGHT_ONLY -> {
                // Bodyweight exercises typically need less rest
                when (exerciseLibrary.primaryMuscleGroup) {
                    ExerciseCategory.CORE -> 45
                    ExerciseCategory.CARDIO -> 30
                    else -> 60
                }
            }
            averageWeight != null && averageWeight > 0 -> {
                // Weight-based exercises with heavier loads need more rest
                if (averageWeight > 50.0) 120 else 90
            }
            else -> {
                // Default rest times by muscle group
                when (exerciseLibrary.primaryMuscleGroup) {
                    ExerciseCategory.ARMS, ExerciseCategory.BICEPS, ExerciseCategory.TRICEPS -> 60
                    ExerciseCategory.SHOULDERS -> 75
                    ExerciseCategory.CORE -> 45
                    ExerciseCategory.CARDIO -> 30
                    else -> 90
                }
            }
        }.coerceIn(ExerciseDefaults.MIN_REST_SECONDS, ExerciseDefaults.MAX_REST_SECONDS)
    }
}

/**
 * Data class to hold historical exercise performance data
 */
private data class ExerciseHistoryData(
    val sets: Int,
    val averageReps: Int,
    val averageWeight: Double?,
    val timestamp: Long
) 