package com.example.liftrix.service

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.service.PRComparison
import com.example.liftrix.domain.service.PRDetectionService
import com.example.liftrix.domain.service.PRSignificance
import com.example.liftrix.domain.service.PRType
import com.example.liftrix.domain.service.PersonalRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.pow
import java.time.LocalDate

/**
 * Implementation of PRDetectionService for identifying personal records
 * Uses various PR formulas and historical data comparison
 */
@Singleton
class PRDetectionServiceImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao
) : PRDetectionService {

    companion object {
        // Minimum improvement thresholds
        private const val MIN_WEIGHT_IMPROVEMENT_KG = 0.5 // 0.5kg minimum
        private const val MIN_REP_IMPROVEMENT = 1 // 1 rep minimum
        private const val MIN_VOLUME_IMPROVEMENT_PERCENT = 0.01 // 1% minimum
        
        // PR validation constants
        private const val MIN_REPS_FOR_PR = 1
        private const val MAX_REPS_FOR_1RM_CALC = 20 // Beyond this, 1RM estimates become unreliable
        private const val MIN_WEIGHT_FOR_PR = 1.0 // 1kg minimum for weighted exercises
    }

    override suspend fun detectPersonalRecords(
        workout: Workout,
        userId: String
    ): LiftrixResult<List<PersonalRecord>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to detect personal records: ${throwable.message}",
                analyticsContext = mapOf<String, String>(
                    "operation" to "DETECT_PERSONAL_RECORDS",
                    "user_id" to userId,
                    "workout_id" to workout.id.value
                )
            )
        }
    ) {
        Timber.d("Detecting PRs for workout: ${workout.id}, user: $userId")
        
        val personalRecords = mutableListOf<PersonalRecord>()
        
        // Process each exercise in the workout
        for (exercise in workout.exercises) {
            val exercisePRs = detectExercisePersonalRecords(exercise, userId, workout.endTime?.toEpochMilli() ?: System.currentTimeMillis())
            personalRecords.addAll(exercisePRs)
        }
        
        Timber.d("Detected ${personalRecords.size} personal records in workout")
        personalRecords
    }

    override suspend fun isPersonalRecord(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String
    ): LiftrixResult<PRComparison> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to check personal record: ${throwable.message}",
                analyticsContext = mapOf<String, String>(
                    "operation" to "CHECK_PERSONAL_RECORD",
                    "user_id" to userId,
                    "exercise_name" to exerciseName
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val comparison = compareWithHistoricalBest(exerciseName, weight, reps, userId)
            comparison
        }
    }

    override suspend fun getHistoricalBest(
        exerciseName: String,
        userId: String,
        prType: PRType
    ): LiftrixResult<PersonalRecord?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get historical best: ${throwable.message}",
                analyticsContext = mapOf<String, String>(
                    "operation" to "GET_HISTORICAL_BEST",
                    "user_id" to userId,
                    "exercise_name" to exerciseName,
                    "pr_type" to prType.name
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            when (prType) {
                PRType.ONE_RM -> getHistoricalBest1RM(exerciseName, userId)
                PRType.VOLUME -> getHistoricalBestVolume(exerciseName, userId)
                PRType.REPS -> getHistoricalBestReps(exerciseName, userId)
                PRType.MAX_WEIGHT -> getHistoricalMaxWeight(exerciseName, userId)
            }
        }
    }

    override fun calculateEstimated1RM(weight: Double, reps: Int): Double {
        if (reps <= 0 || weight <= 0) return 0.0
        if (reps == 1) return weight
        
        // Use Epley formula: 1RM = weight × (1 + reps/30)
        // This is more accurate for higher rep ranges than Brzycki
        return weight * (1 + reps / 30.0)
    }

    override fun calculatePRSignificance(
        currentValue: Double,
        previousBest: Double,
        prType: PRType
    ): PRSignificance {
        if (previousBest <= 0) return PRSignificance.MAJOR // First time achievement
        
        val improvementPercent = (currentValue - previousBest) / previousBest
        
        return when {
            improvementPercent >= PRSignificance.EXCEPTIONAL.threshold -> PRSignificance.EXCEPTIONAL
            improvementPercent >= PRSignificance.MAJOR.threshold -> PRSignificance.MAJOR
            improvementPercent >= PRSignificance.MODERATE.threshold -> PRSignificance.MODERATE
            improvementPercent >= PRSignificance.MINOR.threshold -> PRSignificance.MINOR
            else -> PRSignificance.MINOR
        }
    }

    /**
     * Detects personal records for a specific exercise
     */
    private suspend fun detectExercisePersonalRecords(
        exercise: com.example.liftrix.domain.model.Exercise,
        userId: String,
        achievedAt: Long
    ): List<PersonalRecord> {
        val personalRecords = mutableListOf<PersonalRecord>()
        
        // Check each set for potential PRs
        for (set in exercise.sets) {
            if (set.isCompleted && (set.reps?.count ?: 0) >= MIN_REPS_FOR_PR) {
                val weight = set.weight?.kilograms?.takeIf { it >= MIN_WEIGHT_FOR_PR }
                
                // Check for different types of PRs
                checkFor1RMPR(exercise.libraryExercise.name, weight, set.reps?.count ?: 0, userId, achievedAt)?.let { pr ->
                    personalRecords.add(pr)
                }
                
                checkForVolumeRP(exercise.libraryExercise.name, weight, set.reps?.count ?: 0, userId, achievedAt)?.let { pr ->
                    personalRecords.add(pr)
                }
                
                checkForRepsPR(exercise.libraryExercise.name, weight, set.reps?.count ?: 0, userId, achievedAt)?.let { pr ->
                    personalRecords.add(pr)
                }
                
                checkForMaxWeightPR(exercise.libraryExercise.name, weight, set.reps?.count ?: 0, userId, achievedAt)?.let { pr ->
                    personalRecords.add(pr)
                }
            }
        }
        
        return personalRecords
    }

    /**
     * Compares current performance with historical best
     */
    private suspend fun compareWithHistoricalBest(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String
    ): PRComparison {
        val estimated1RM = weight?.let { calculateEstimated1RM(it, reps) } ?: 0.0
        val volume = weight?.let { it * reps } ?: 0.0
        
        // Get historical bests for comparison
        val historical1RM = getHistoricalBest1RM(exerciseName, userId)
        val historicalVolume = getHistoricalBestVolume(exerciseName, userId)
        val historicalReps = getHistoricalBestReps(exerciseName, userId)
        val historicalMaxWeight = getHistoricalMaxWeight(exerciseName, userId)
        
        // Determine if this is a PR and which type
        val is1RMPR = historical1RM?.estimatedOneRM?.let { estimated1RM > it } == true
        val isVolumeRP = historicalVolume?.volume?.let { volume > it } == true
        val isRepsPR = historicalReps?.reps?.let { reps > it } == true
        val isMaxWeightPR = historicalMaxWeight?.weight?.let { weight != null && weight > it } == true
        
        return when {
            is1RMPR -> {
                val previousBest = historical1RM?.estimatedOneRM ?: 0.0
                val improvement = if (previousBest > 0) (estimated1RM - previousBest) / previousBest else 0.0
                PRComparison(
                    isPersonalRecord = true,
                    prType = PRType.ONE_RM,
                    currentValue = estimated1RM,
                    previousBest = previousBest,
                    improvementPercent = improvement,
                    significance = calculatePRSignificance(estimated1RM, previousBest, PRType.ONE_RM)
                )
            }
            isVolumeRP -> {
                val previousBest = historicalVolume?.volume ?: 0.0
                val improvement = if (previousBest > 0) (volume - previousBest) / previousBest else 0.0
                PRComparison(
                    isPersonalRecord = true,
                    prType = PRType.VOLUME,
                    currentValue = volume,
                    previousBest = previousBest,
                    improvementPercent = improvement,
                    significance = calculatePRSignificance(volume, previousBest, PRType.VOLUME)
                )
            }
            isRepsPR -> {
                val previousBest = historicalReps?.reps?.toDouble() ?: 0.0
                val improvement = if (previousBest > 0) (reps - previousBest) / previousBest else 0.0
                PRComparison(
                    isPersonalRecord = true,
                    prType = PRType.REPS,
                    currentValue = reps.toDouble(),
                    previousBest = previousBest,
                    improvementPercent = improvement,
                    significance = calculatePRSignificance(reps.toDouble(), previousBest, PRType.REPS)
                )
            }
            isMaxWeightPR && weight != null -> {
                val previousBest = historicalMaxWeight?.weight ?: 0.0
                val improvement = if (previousBest > 0) (weight - previousBest) / previousBest else 0.0
                PRComparison(
                    isPersonalRecord = true,
                    prType = PRType.MAX_WEIGHT,
                    currentValue = weight,
                    previousBest = previousBest,
                    improvementPercent = improvement,
                    significance = calculatePRSignificance(weight, previousBest, PRType.MAX_WEIGHT)
                )
            }
            else -> PRComparison(
                isPersonalRecord = false,
                prType = null,
                currentValue = estimated1RM,
                previousBest = historical1RM?.estimatedOneRM,
                improvementPercent = null,
                significance = PRSignificance.MINOR
            )
        }
    }

    /**
     * Checks for 1RM personal record
     */
    private suspend fun checkFor1RMPR(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String,
        achievedAt: Long
    ): PersonalRecord? {
        if (weight == null || reps > MAX_REPS_FOR_1RM_CALC) return null
        
        val estimated1RM = calculateEstimated1RM(weight, reps)
        val historicalBest = getHistoricalBest1RM(exerciseName, userId)
        
        val isNewPR = historicalBest?.estimatedOneRM?.let { estimated1RM > it } == true ||
                     historicalBest == null
        
        if (isNewPR && (historicalBest == null || estimated1RM - (historicalBest.estimatedOneRM ?: 0.0) >= MIN_WEIGHT_IMPROVEMENT_KG)) {
            val previousBest = historicalBest?.estimatedOneRM
            val improvement = previousBest?.let { (estimated1RM - it) / it }
            
            return PersonalRecord(
                exerciseName = exerciseName,
                prType = PRType.ONE_RM,
                weight = weight,
                reps = reps,
                estimatedOneRM = estimated1RM,
                volume = weight * reps,
                achievedAt = achievedAt,
                previousBest = previousBest,
                improvementPercent = improvement
            )
        }
        
        return null
    }

    /**
     * Checks for volume personal record
     */
    private suspend fun checkForVolumeRP(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String,
        achievedAt: Long
    ): PersonalRecord? {
        if (weight == null) return null
        
        val volume = weight * reps
        val historicalBest = getHistoricalBestVolume(exerciseName, userId)
        
        val isNewPR = historicalBest?.volume?.let { volume > it } == true ||
                     historicalBest == null
        
        if (isNewPR) {
            val previousBest = historicalBest?.volume
            val improvement = previousBest?.let { (volume - it) / it }
            
            // Check minimum improvement threshold
            val meetsThreshold = previousBest == null || 
                               improvement!! >= MIN_VOLUME_IMPROVEMENT_PERCENT
            
            if (meetsThreshold) {
                return PersonalRecord(
                    exerciseName = exerciseName,
                    prType = PRType.VOLUME,
                    weight = weight,
                    reps = reps,
                    estimatedOneRM = calculateEstimated1RM(weight, reps),
                    volume = volume,
                    achievedAt = achievedAt,
                    previousBest = previousBest,
                    improvementPercent = improvement
                )
            }
        }
        
        return null
    }

    /**
     * Checks for reps personal record
     */
    private suspend fun checkForRepsPR(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String,
        achievedAt: Long
    ): PersonalRecord? {
        val historicalBest = getHistoricalBestReps(exerciseName, userId)
        
        val isNewPR = historicalBest?.reps?.let { reps > it } == true ||
                     historicalBest == null
        
        if (isNewPR && (historicalBest == null || reps - historicalBest.reps >= MIN_REP_IMPROVEMENT)) {
            val previousBest = historicalBest?.reps?.toDouble()
            val improvement = previousBest?.let { (reps - it) / it }
            
            return PersonalRecord(
                exerciseName = exerciseName,
                prType = PRType.REPS,
                weight = weight,
                reps = reps,
                estimatedOneRM = weight?.let { calculateEstimated1RM(it, reps) },
                volume = weight?.let { it * reps },
                achievedAt = achievedAt,
                previousBest = previousBest,
                improvementPercent = improvement
            )
        }
        
        return null
    }

    /**
     * Checks for max weight personal record
     */
    private suspend fun checkForMaxWeightPR(
        exerciseName: String,
        weight: Double?,
        reps: Int,
        userId: String,
        achievedAt: Long
    ): PersonalRecord? {
        if (weight == null) return null
        
        val historicalBest = getHistoricalMaxWeight(exerciseName, userId)
        
        val isNewPR = historicalBest?.weight?.let { weight > it } == true ||
                     historicalBest == null
        
        if (isNewPR && (historicalBest == null || weight - (historicalBest.weight ?: 0.0) >= MIN_WEIGHT_IMPROVEMENT_KG)) {
            val previousBest = historicalBest?.weight
            val improvement = previousBest?.let { (weight - it) / it }
            
            return PersonalRecord(
                exerciseName = exerciseName,
                prType = PRType.MAX_WEIGHT,
                weight = weight,
                reps = reps,
                estimatedOneRM = calculateEstimated1RM(weight, reps),
                volume = weight * reps,
                achievedAt = achievedAt,
                previousBest = previousBest,
                improvementPercent = improvement
            )
        }
        
        return null
    }

    /**
     * Gets historical best 1RM for an exercise
     */
    private suspend fun getHistoricalBest1RM(exerciseName: String, userId: String): PersonalRecord? {
        try {
            // Use a very wide date range to get all historical data
            val startDate = "2000-01-01"
            val endDate = java.time.LocalDate.now().toString()
            
            val oneRmData = exerciseSetDao.getOneRmDataForExercises(
                userId = userId,
                exerciseLibraryIds = listOf(exerciseName), // Assuming exercise name is used as library ID
                startDate = startDate,
                endDate = endDate,
                limit = 1
            )
            
            val bestResult = oneRmData.maxByOrNull { it.estimated_one_rm }
            
            return bestResult?.let { result ->
                PersonalRecord(
                    exerciseName = exerciseName,
                    prType = PRType.ONE_RM,
                    weight = result.weight_kg.toDouble(),
                    reps = result.reps,
                    estimatedOneRM = result.estimated_one_rm,
                    volume = result.weight_kg.toDouble() * result.reps,
                    achievedAt = result.completed_at,
                    previousBest = null,
                    improvementPercent = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get historical best 1RM for exercise: $exerciseName")
            return null
        }
    }

    /**
     * Gets historical best volume for an exercise
     */
    private suspend fun getHistoricalBestVolume(exerciseName: String, userId: String): PersonalRecord? {
        try {
            // Use a very wide date range to get all historical data
            val startDate = "2000-01-01"
            val endDate = java.time.LocalDate.now().toString()
            
            val oneRmData = exerciseSetDao.getOneRmDataForExercises(
                userId = userId,
                exerciseLibraryIds = listOf(exerciseName),
                startDate = startDate,
                endDate = endDate,
                limit = 5000 // Get more data for volume calculation
            )
            
            val bestVolumeResult = oneRmData.maxByOrNull { result ->
                result.weight_kg.toDouble() * result.reps
            }
            
            return bestVolumeResult?.let { result ->
                val volume = result.weight_kg.toDouble() * result.reps
                PersonalRecord(
                    exerciseName = exerciseName,
                    prType = PRType.VOLUME,
                    weight = result.weight_kg.toDouble(),
                    reps = result.reps,
                    estimatedOneRM = result.estimated_one_rm,
                    volume = volume,
                    achievedAt = result.completed_at,
                    previousBest = null,
                    improvementPercent = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get historical best volume for exercise: $exerciseName")
            return null
        }
    }

    /**
     * Gets historical best reps for an exercise
     */
    private suspend fun getHistoricalBestReps(exerciseName: String, userId: String): PersonalRecord? {
        try {
            // Use a very wide date range to get all historical data
            val startDate = "2000-01-01"
            val endDate = java.time.LocalDate.now().toString()
            
            val oneRmData = exerciseSetDao.getOneRmDataForExercises(
                userId = userId,
                exerciseLibraryIds = listOf(exerciseName),
                startDate = startDate,
                endDate = endDate,
                limit = 5000
            )
            
            val bestRepsResult = oneRmData.maxByOrNull { it.reps }
            
            return bestRepsResult?.let { result ->
                PersonalRecord(
                    exerciseName = exerciseName,
                    prType = PRType.REPS,
                    weight = result.weight_kg.toDouble(),
                    reps = result.reps,
                    estimatedOneRM = result.estimated_one_rm,
                    volume = result.weight_kg.toDouble() * result.reps,
                    achievedAt = result.completed_at,
                    previousBest = null,
                    improvementPercent = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get historical best reps for exercise: $exerciseName")
            return null
        }
    }

    /**
     * Gets historical max weight for an exercise
     */
    private suspend fun getHistoricalMaxWeight(exerciseName: String, userId: String): PersonalRecord? {
        try {
            // Use a very wide date range to get all historical data
            val startDate = "2000-01-01"
            val endDate = java.time.LocalDate.now().toString()
            
            val oneRmData = exerciseSetDao.getOneRmDataForExercises(
                userId = userId,
                exerciseLibraryIds = listOf(exerciseName),
                startDate = startDate,
                endDate = endDate,
                limit = 5000
            )
            
            val bestWeightResult = oneRmData.maxByOrNull { it.weight_kg }
            
            return bestWeightResult?.let { result ->
                PersonalRecord(
                    exerciseName = exerciseName,
                    prType = PRType.MAX_WEIGHT,
                    weight = result.weight_kg.toDouble(),
                    reps = result.reps,
                    estimatedOneRM = result.estimated_one_rm,
                    volume = result.weight_kg.toDouble() * result.reps,
                    achievedAt = result.completed_at,
                    previousBest = null,
                    improvementPercent = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get historical max weight for exercise: $exerciseName")
            return null
        }
    }
}