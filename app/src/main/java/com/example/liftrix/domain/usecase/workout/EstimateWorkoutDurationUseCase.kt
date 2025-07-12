package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.*
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Use case for estimating workout duration based on template exercises
 * 
 * Calculates estimated duration by analyzing:
 * - Exercise complexity and type
 * - Number of sets and target reps
 * - Rest time between sets
 * - Transition time between exercises
 * - Warm-up and cool-down buffer
 */
@Singleton
class EstimateWorkoutDurationUseCase @Inject constructor() {
    
    companion object {
        // Base time estimates in seconds
        private const val WARMUP_TIME_MINUTES = 5
        private const val COOLDOWN_TIME_MINUTES = 5
        private const val EXERCISE_TRANSITION_SECONDS = 30
        private const val SETUP_TIME_SECONDS = 20 // Time to set up equipment
        
        // Time per set based on exercise characteristics (seconds)
        private const val COMPOUND_SET_TIME = 45
        private const val ISOLATION_SET_TIME = 30
        private const val BODYWEIGHT_SET_TIME = 25
        private const val CARDIO_SET_TIME = 60 // Per minute of cardio
        private const val TIME_BASED_SET_MULTIPLIER = 1.5f // Factor for time-based exercises
        
        // Default rest times when not specified (seconds)
        private const val DEFAULT_COMPOUND_REST = 120
        private const val DEFAULT_ISOLATION_REST = 90
        private const val DEFAULT_BODYWEIGHT_REST = 60
        private const val DEFAULT_CARDIO_REST = 60
    }
    
    /**
     * Estimates the total duration for a workout template
     * 
     * @param template The workout template to analyze
     * @return Estimated duration as Duration object
     */
    suspend operator fun invoke(template: WorkoutTemplate): Result<Duration> {
        return try {
            if (template.exercises.isEmpty()) {
                Timber.d("Empty template, returning minimum duration")
                return Result.success(Duration.ofMinutes(WARMUP_TIME_MINUTES + COOLDOWN_TIME_MINUTES.toLong()))
            }
            
            Timber.d("Estimating duration for template '${template.name}' with ${template.exercises.size} exercises")
            
            var totalSeconds = 0
            
            // Add warm-up time
            totalSeconds += WARMUP_TIME_MINUTES * 60
            
            // Calculate time for each exercise
            template.exercises.forEachIndexed { index, exercise ->
                val exerciseTime = calculateExerciseTime(exercise)
                totalSeconds += exerciseTime
                
                // Add transition time between exercises (except for last exercise)
                if (index < template.exercises.size - 1) {
                    totalSeconds += EXERCISE_TRANSITION_SECONDS
                }
                
                Timber.v("Exercise '${exercise.name}': ${exerciseTime}s")
            }
            
            // Add cool-down time
            totalSeconds += COOLDOWN_TIME_MINUTES * 60
            
            val duration = Duration.ofSeconds(totalSeconds.toLong())
            
            Timber.d("Estimated total duration: ${duration.toMinutes()} minutes (${totalSeconds}s)")
            
            Result.success(duration)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to estimate workout duration")
            // Return a reasonable fallback duration
            Result.success(Duration.ofMinutes(45))
        }
    }
    
    /**
     * Calculates estimated time for a single exercise including all sets and rest
     */
    private fun calculateExerciseTime(exercise: TemplateExercise): Int {
        val sets = exercise.targetSets ?: 3
        val reps = exercise.targetReps?.count ?: 10
        val restTime = exercise.restTimeSeconds ?: getDefaultRestTime(exercise)
        
        // Calculate time per set based on exercise characteristics
        val timePerSet = when {
            isCompoundExercise(exercise) -> {
                // Compound exercises take longer per rep
                val baseTime = COMPOUND_SET_TIME
                val repFactor = (reps.toFloat() / 8.0f).coerceIn(0.5f, 2.0f)
                (baseTime * repFactor).roundToInt()
            }
            
            isCardioExercise(exercise) -> {
                // Cardio exercises: reps typically represent minutes
                reps * CARDIO_SET_TIME
            }
            
            isTimeBasedExercise(exercise) -> {
                // Time-based exercises (planks, holds): reps represent seconds
                (reps * TIME_BASED_SET_MULTIPLIER).roundToInt()
            }
            
            isBodyweightExercise(exercise) -> {
                // Bodyweight exercises scale with rep count
                val baseTime = BODYWEIGHT_SET_TIME
                val repFactor = (reps.toFloat() / 12.0f).coerceIn(0.5f, 1.5f)
                (baseTime * repFactor).roundToInt()
            }
            
            else -> {
                // Isolation/standard exercises
                val baseTime = ISOLATION_SET_TIME
                val repFactor = (reps.toFloat() / 10.0f).coerceIn(0.5f, 1.5f)
                (baseTime * repFactor).roundToInt()
            }
        }
        
        // Add setup time for the exercise
        var totalTime = SETUP_TIME_SECONDS
        
        // Calculate total time for all sets including rest
        repeat(sets) { setIndex ->
            totalTime += timePerSet
            
            // Add rest time between sets (not after the last set)
            if (setIndex < sets - 1) {
                totalTime += restTime
            }
        }
        
        return totalTime
    }
    
    /**
     * Gets default rest time based on exercise characteristics
     */
    private fun getDefaultRestTime(exercise: TemplateExercise): Int {
        return when {
            isCompoundExercise(exercise) -> DEFAULT_COMPOUND_REST
            isCardioExercise(exercise) -> DEFAULT_CARDIO_REST
            isBodyweightExercise(exercise) -> DEFAULT_BODYWEIGHT_REST
            else -> DEFAULT_ISOLATION_REST
        }
    }
    
    /**
     * Determines if an exercise is a compound movement
     */
    private fun isCompoundExercise(exercise: TemplateExercise): Boolean {
        return when (exercise.primaryMuscle) {
            ExerciseCategory.LEGS -> true // Most leg exercises are compound
            ExerciseCategory.BACK -> exercise.equipment in setOf(Equipment.BARBELL, Equipment.DUMBBELLS)
            ExerciseCategory.CHEST -> exercise.equipment == Equipment.BARBELL // Bench press, etc.
            else -> false
        } || exercise.name.lowercase().contains(Regex("squat|deadlift|press|row|pull.?up|chin.?up"))
    }
    
    /**
     * Determines if an exercise is cardio-based
     */
    private fun isCardioExercise(exercise: TemplateExercise): Boolean {
        return exercise.primaryMuscle == ExerciseCategory.CARDIO ||
                exercise.name.lowercase().contains(Regex("run|bike|cycle|treadmill|elliptical|rowing"))
    }
    
    /**
     * Determines if an exercise is time-based (holds, isometric)
     */
    private fun isTimeBasedExercise(exercise: TemplateExercise): Boolean {
        return exercise.name.lowercase().contains(Regex("plank|hold|wall.?sit|static|isometric"))
    }
    
    /**
     * Determines if an exercise is bodyweight-only
     */
    private fun isBodyweightExercise(exercise: TemplateExercise): Boolean {
        return exercise.equipment == Equipment.BODYWEIGHT_ONLY ||
                exercise.name.lowercase().contains(Regex("push.?up|sit.?up|burpee|jumping|mountain.?climber"))
    }
    
    /**
     * Estimates duration in minutes for display purposes
     */
    suspend fun estimateDurationMinutes(template: WorkoutTemplate): Int {
        return runCatching {
            val duration = invoke(template).getOrNull() ?: Duration.ofMinutes(45)
            duration.toMinutes().toInt()
        }.getOrDefault(45)
    }
    
    /**
     * Gets a formatted duration string for display
     */
    suspend fun getFormattedDuration(template: WorkoutTemplate): String {
        val minutes = estimateDurationMinutes(template)
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "${hours}h ${remainingMinutes}m" else "${hours}h"
            }
            else -> "${minutes}m"
        }
    }
} 