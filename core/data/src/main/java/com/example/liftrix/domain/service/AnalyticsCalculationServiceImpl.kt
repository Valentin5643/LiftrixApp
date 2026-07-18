package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.analytics.WorkoutMetrics
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData
import com.example.liftrix.domain.usecase.analytics.ExerciseRanking
import com.example.liftrix.domain.usecase.analytics.PerformanceTrend
import com.example.liftrix.domain.usecase.analytics.PlateauStatus
import com.example.liftrix.domain.usecase.analytics.RankedExercise
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Implementation of AnalyticsCalculationService providing pure calculation logic.
 *
 * This service consolidates analytics calculations previously duplicated across
 * multiple use cases, ensuring consistency and reducing code duplication.
 *
 * **Thread Safety**: All methods are stateless and thread-safe.
 * **Performance**: Optimized for calculation speed with minimal allocations.
 * **Accuracy**: Uses industry-standard fitness formulas (MET, Epley, etc.).
 */
@Singleton
class AnalyticsCalculationServiceImpl @Inject constructor() : AnalyticsCalculationService {

    override suspend fun calculateCaloriesBurned(
        workout: Workout,
        userWeightKg: Double
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CalculationError(
                errorMessage = "Failed to calculate calories for workout ${workout.id}: ${throwable.message}",
                operation = "calculateCaloriesBurned"
            )
        }
    ) {
        Timber.d("Calculating calories for workout: ${workout.id}")

        require(userWeightKg > 0) { "User weight must be positive" }
        require(workout.exercises.isNotEmpty()) { "Workout must contain exercises" }

        // Calculate workout duration in hours
        val duration = workout.getDuration()
        val durationMinutes = duration?.toMinutes()?.toInt() ?: estimateDuration(workout.exercises)
        val durationHours = durationMinutes / 60.0

        // Use average MET value for strength training
        val averageStrengthMET = 6.0 // Moderate intensity strength training

        // Calories = MET × weight(kg) × duration(hours)
        val calories = (averageStrengthMET * userWeightKg * durationHours).toInt()

        // Adjust based on workout intensity factors
        val intensityMultiplier = calculateIntensityMultiplier(workout)
        val adjustedCalories = (calories * intensityMultiplier).toInt()

        Timber.d("Calculated ${adjustedCalories} calories for workout ${workout.id}")
        adjustedCalories
    }

    override suspend fun calculateMultipleWorkoutCalories(
        workouts: List<Workout>,
        userWeightKg: Double
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CalculationError(
                errorMessage = "Failed to calculate calories for ${workouts.size} workouts: ${throwable.message}",
                operation = "calculateMultipleWorkoutCalories"
            )
        }
    ) {
        Timber.d("Calculating calories for ${workouts.size} workouts")

        var totalCalories = 0

        workouts.forEach { workout ->
            val workoutCalories = calculateCaloriesBurned(workout, userWeightKg)
                .getOrElse { 0 }
            totalCalories += workoutCalories
        }

        Timber.d("Total calories for ${workouts.size} workouts: $totalCalories")
        totalCalories
    }

    override suspend fun estimateWorkoutCalories(
        exerciseCount: Int,
        estimatedDurationMinutes: Int,
        userWeightKg: Double
    ): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CalculationError(
                errorMessage = "Failed to estimate calories: ${throwable.message}",
                operation = "estimateWorkoutCalories"
            )
        }
    ) {
        Timber.d("Estimating calories for planned workout: $exerciseCount exercises, $estimatedDurationMinutes minutes")

        require(exerciseCount > 0) { "Exercise count must be positive" }
        require(estimatedDurationMinutes > 0) { "Duration must be positive" }
        require(userWeightKg > 0) { "User weight must be positive" }

        // Estimation based on average MET values for strength training
        val averageStrengthMET = 6.0
        val durationHours = estimatedDurationMinutes / 60.0
        val estimatedCalories = (averageStrengthMET * userWeightKg * durationHours).toInt()

        // Adjust based on exercise count complexity
        val complexityMultiplier = when {
            exerciseCount <= 3 -> 0.8
            exerciseCount <= 6 -> 1.0
            else -> 1.2
        }

        (estimatedCalories * complexityMultiplier).toInt()
    }

    override suspend fun calculateExerciseRanking(
        performanceData: List<ExercisePerformanceData>,
        limit: Int
    ): LiftrixResult<List<ExerciseRanking>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CalculationError(
                errorMessage = "Failed to calculate exercise rankings: ${throwable.message}",
                operation = "calculateExerciseRanking"
            )
        }
    ) {
        Timber.d("Calculating exercise rankings for ${performanceData.size} exercises")

        if (performanceData.isEmpty()) {
            return@liftrixCatching emptyList()
        }

        // Calculate performance score rankings (combined volume + strength growth)
        val rankings = performanceData.mapNotNull { exercise ->
            val volumeGrowth = calculateVolumeGrowthPercentage(exercise)
            val strengthGrowth = calculateStrengthGrowthPercentage(exercise)

            if (volumeGrowth != null && strengthGrowth != null) {
                val performanceScore = calculatePerformanceScore(volumeGrowth, strengthGrowth)

                ExerciseRanking(
                    exerciseId = exercise.exerciseId,
                    exerciseName = exercise.exerciseName,
                    muscleGroup = exercise.muscleGroup,
                    performanceScore = performanceScore,
                    rank = 0  // Will be set after sorting
                )
            } else null
        }.sortedByDescending { it.performanceScore }
            .mapIndexed { index, ranking -> ranking.copy(rank = index + 1) }

        rankings.take(limit)
    }

    override suspend fun calculateWorkoutMetrics(
        workout: Workout
    ): LiftrixResult<WorkoutMetrics> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.CalculationError(
                errorMessage = "Failed to calculate workout metrics: ${throwable.message}",
                operation = "calculateWorkoutMetrics"
            )
        }
    ) {
        Timber.d("Calculating workout metrics for workout: ${workout.id}")

        val totalVolume = workout.calculateTotalVolume()
        val totalSets = workout.getTotalSets()
        val completedSets = workout.getCompletedSets()
        val totalRepsCount = workout.exercises.sumOf { exercise ->
            exercise.sets.sumOf { set -> set.reps?.count ?: 0 }
        }
        val averageIntensity = calculateAverageIntensity(workout.exercises)
        val duration = workout.getDuration()
        val completionPercentage = if (totalSets > 0) (completedSets.toDouble() / totalSets) * 100.0 else 0.0
        val durationMinutes = duration?.toMinutes()?.toDouble() ?: 0.0
        val volumeEfficiency = if (durationMinutes > 0.0) {
            (totalVolume.kilograms / durationMinutes).toFloat()
        } else {
            0.0f
        }
        val workoutDate = kotlinx.datetime.LocalDate(
            workout.date.year,
            workout.date.monthValue,
            workout.date.dayOfMonth
        )
        val categories = workout.exercises
            .map { it.libraryExercise.primaryMuscleGroup }
            .toSet()

        Timber.d("Constructed metrics: volume=$totalVolume, sets=$totalSets, duration=$duration, reps=$totalRepsCount")
        WorkoutMetrics(
            workoutId = workout.id.value,
            userId = workout.userId,
            date = workoutDate,
            totalVolume = totalVolume,
            sessionDuration = duration,
            caloriesBurned = 0,
            exerciseCount = workout.exercises.size,
            totalSets = totalSets,
            completedSets = completedSets,
            totalReps = Reps(totalRepsCount),
            completionPercentage = completionPercentage,
            averageIntensity = averageIntensity.toFloat(),
            volumeEfficiency = volumeEfficiency,
            categories = categories
        )
    }

    override fun calculateOneRepMax(weight: Double, reps: Int): Double {
        require(weight > 0) { "Weight must be positive" }
        require(reps > 0) { "Reps must be positive" }

        // Epley formula: 1RM = weight × (1 + reps/30)
        return weight * (1 + reps / 30.0)
    }

    override fun calculateVolume(sets: Int, reps: Int, weight: Double): Double {
        require(sets > 0) { "Sets must be positive" }
        require(reps > 0) { "Reps must be positive" }
        require(weight >= 0) { "Weight must be non-negative" }

        return sets * reps * weight
    }

    override fun calculatePerformanceScore(
        volumeGrowthPercent: Float,
        strengthGrowthPercent: Float
    ): Float {
        return (volumeGrowthPercent + strengthGrowthPercent) / 2f
    }

    // Private helper methods

    private fun estimateDuration(exercises: List<Exercise>): Int {
        // Estimate ~3 minutes per set + 1 minute between exercises
        val totalSets = exercises.sumOf { it.sets.size }
        val estimatedMinutes = (totalSets * 3) + (exercises.size - 1)
        return estimatedMinutes.coerceAtLeast(10) // Minimum 10 minutes
    }

    private fun calculateIntensityMultiplier(workout: Workout): Double {
        val exerciseCount = workout.exercises.size
        val totalSets = workout.exercises.sumOf { it.sets.size }

        // Higher intensity for more sets and exercises
        return when {
            totalSets >= 20 -> 1.2
            totalSets >= 12 -> 1.0
            else -> 0.9
        }
    }

    private fun calculateTotalVolume(exercises: List<Exercise>): Double {
        return exercises.sumOf { exercise ->
            exercise.sets.sumOf { set ->
                val weight = set.weight?.kilograms ?: 0.0
                val repsCount = set.reps?.count ?: 0
                (weight * repsCount).toDouble()
            }
        }
    }

    private fun calculateAverageIntensity(exercises: List<Exercise>): Double {
        if (exercises.isEmpty()) return 0.0

        val normalizedRpeValues = exercises
            .flatMap { it.sets }
            .mapNotNull { set -> set.rpe?.value?.div(10.0) }

        return normalizedRpeValues.takeIf { it.isNotEmpty() }?.average() ?: 0.5
    }

    private fun calculateVolumeGrowthPercentage(exercise: ExercisePerformanceData): Float? {
        if (exercise.volumeHistory.size < 2) return null

        val sortedHistory = exercise.volumeHistory.sortedBy { it.date }
        val firstVolume = sortedHistory.first().volume
        val lastVolume = sortedHistory.last().volume

        return if (firstVolume > 0) {
            ((lastVolume - firstVolume) / firstVolume * 100f).toFloat()
        } else null
    }

    private fun calculateStrengthGrowthPercentage(exercise: ExercisePerformanceData): Float? {
        if (exercise.oneRmHistory.size < 2) return null

        val sortedHistory = exercise.oneRmHistory.sortedBy { it.date }
        val firstOneRm = sortedHistory.first().oneRm
        val lastOneRm = sortedHistory.last().oneRm

        return if (firstOneRm > 0) {
            (((lastOneRm - firstOneRm) / firstOneRm) * 100f).toFloat()
        } else null
    }

    private fun detectPlateauStatus(exercise: ExercisePerformanceData): PlateauStatus {
        // Simplified plateau detection based on workout frequency
        return when {
            exercise.workoutDays < 3 -> PlateauStatus.INSUFFICIENT_DATA
            exercise.totalVolume < 1000 && exercise.maxEstimated1RM < 50 -> PlateauStatus.STAGNANT
            exercise.performanceScore > 100 -> PlateauStatus.PROGRESSING
            else -> PlateauStatus.STABLE
        }
    }

    private fun generateRecommendations(status: PlateauStatus, exercise: ExercisePerformanceData): List<String> {
        return when (status) {
            PlateauStatus.INSUFFICIENT_DATA -> listOf("Perform this exercise more frequently to track progress")
            PlateauStatus.DECLINING -> listOf("Focus on form and consider reducing weight")
            PlateauStatus.STAGNANT -> listOf("Try increasing intensity or changing rep ranges")
            PlateauStatus.STABLE -> listOf("Maintain current programming - steady progress")
            PlateauStatus.PROGRESSING -> listOf("Excellent progress! Continue your approach")
        }
    }

    private fun calculateVariancePercentage(values: List<Float>): Float {
        if (values.isEmpty()) return 0f

        val mean = values.average().toFloat()
        if (mean == 0f) return 0f

        val variance = values.map { abs(it - mean) }.average().toFloat()
        return (variance / kotlin.math.abs(mean)) * 100f
    }

    private fun determineTrend(score: Float): PerformanceTrend {
        return when {
            score > IMPROVING_THRESHOLD -> PerformanceTrend.IMPROVING
            score < DECLINING_THRESHOLD -> PerformanceTrend.DECLINING
            else -> PerformanceTrend.STABLE
        }
    }

    companion object {
        private const val PLATEAU_VARIANCE_THRESHOLD = 5f // 5% variance threshold
        private const val IMPROVING_THRESHOLD = 10f // 10% growth threshold
        private const val DECLINING_THRESHOLD = -5f // -5% threshold
    }
}
