package com.example.liftrix.domain.util

import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Weight

/**
 * Centralized utility for calculating workout volume across different data sources.
 * 
 * Provides consistent volume calculation logic for:
 * - Domain model exercises (Exercise.getTotalVolume)
 * - JSON parsed exercises (FeedRepositoryImpl)
 * - Session exercises (active workouts)
 * - Future set types (drop sets, time-based sets, etc.)
 * 
 * Volume = Sum of (Weight × Reps) for all completed sets
 */
object VolumeCalculator {
    
    /**
     * Calculates total volume from domain model ExerciseSet objects.
     * 
     * @param sets List of exercise sets
     * @param includeIncomplete Whether to include incomplete sets (default: false)
     * @return Total volume in kilograms
     */
    fun calculateVolumeFromSets(
        sets: List<ExerciseSet>, 
        includeIncomplete: Boolean = false
    ): Double {
        return sets
            .filter { includeIncomplete || it.isCompleted }
            .sumOf { set ->
                val weightKg = set.weight?.kilograms ?: 0.0
                val repsCount = set.reps?.count ?: 0
                weightKg * repsCount
            }
    }
    
    /**
     * Calculates volume from JSON parsed set data.
     * 
     * Used by FeedRepositoryImpl when parsing workout JSON.
     * 
     * @param effectiveWeight Weight in kg (nullable)
     * @param effectiveReps Rep count (nullable) 
     * @param isCompleted Whether the set is completed
     * @return Volume for this set (0.0 if incomplete or missing data)
     */
    fun calculateSetVolume(
        effectiveWeight: Double?,
        effectiveReps: Int?,
        isCompleted: Boolean
    ): Double {
        return if (isCompleted && effectiveWeight != null && effectiveReps != null) {
            effectiveWeight * effectiveReps
        } else {
            0.0
        }
    }
    
    /**
     * Calculates volume from raw weight/reps data with completion status.
     * 
     * Generic method for any data source that has weight, reps, and completion.
     * 
     * @param weightKg Weight in kilograms
     * @param reps Number of repetitions
     * @param isCompleted Whether the set is completed
     * @return Volume for this set
     */
    fun calculateVolumeFromRawData(
        weightKg: Double,
        reps: Int,
        isCompleted: Boolean
    ): Double {
        return if (isCompleted && weightKg > 0.0 && reps > 0) {
            weightKg * reps
        } else {
            0.0
        }
    }
    
    /**
     * Calculates volume with debug logging for troubleshooting.
     * 
     * @param sets List of sets to analyze
     * @param exerciseName Name for logging context
     * @param includeIncomplete Whether to include incomplete sets
     * @return Total volume with detailed logging
     */
    fun calculateVolumeWithDebug(
        sets: List<ExerciseSet>,
        exerciseName: String,
        includeIncomplete: Boolean = false
    ): Double {
        val totalVolume = sets
            .filter { includeIncomplete || it.isCompleted }
            .sumOf { set ->
                val weightKg = set.weight?.kilograms ?: 0.0
                val repsCount = set.reps?.count ?: 0
                weightKg * repsCount
            }

        return totalVolume
    }
    
    /**
     * Converts volume to Weight object for domain model compatibility.
     * 
     * @param volumeKg Volume in kilograms
     * @return Weight object or null if volume is 0
     */
    fun toWeightOrNull(volumeKg: Double): Weight? {
        return if (volumeKg > 0.0) Weight.fromKilograms(volumeKg) else null
    }
    
    /**
     * Type-safe volume calculation for different set types using sealed classes.
     * 
     * This approach provides compile-time safety and makes it easy to add new set types
     * without breaking existing functionality.
     * 
     * @param setType The specific set type with its data
     * @return Volume calculated based on the set type's algorithm
     */
    fun calculateVolumeForSetType(setType: com.example.liftrix.domain.model.SetType): Double {
        return when (setType) {
            is com.example.liftrix.domain.model.SetType.Standard -> {
                if (setType.isCompleted) {
                    setType.weightKg * setType.reps
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.Drop -> {
                if (setType.isCompleted) {
                    setType.drops.sumOf { (weightKg, reps) -> weightKg * reps }
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.TimeBased -> {
                if (setType.isCompleted) {
                    // Convert time to volume using duration × intensity
                    // This creates a volume-like metric for time-based exercises
                    setType.durationSeconds * setType.intensityFactor
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.Bodyweight -> {
                if (setType.isCompleted) {
                    setType.bodyweightKg * setType.weightMultiplier * setType.reps
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.Cluster -> {
                if (setType.isCompleted) {
                    setType.weightKg * setType.totalReps
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.RestPause -> {
                if (setType.isCompleted) {
                    setType.weightKg * setType.totalReps
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.Distance -> {
                if (setType.isCompleted) {
                    // Convert distance to volume using distance × intensity
                    setType.distanceMeters * setType.intensityFactor
                } else 0.0
            }
            
            is com.example.liftrix.domain.model.SetType.Mixed -> {
                if (setType.isCompleted) {
                    // Recursively calculate volume for all components
                    setType.components.sumOf { component ->
                        calculateVolumeForSetType(component)
                    }
                } else 0.0
            }
        }
    }
    
    /**
     * Convenience method to convert ExerciseSet to SetType for calculation.
     * 
     * @param set ExerciseSet from domain model
     * @return SetType.Standard representation
     */
    fun exerciseSetToSetType(set: ExerciseSet): com.example.liftrix.domain.model.SetType.Standard {
        return com.example.liftrix.domain.model.SetType.Standard(
            weightKg = set.weight?.kilograms ?: 0.0,
            reps = set.reps?.count ?: 0,
            isCompleted = set.isCompleted
        )
    }
    
    /**
     * Calculates volume from a list of SetType objects.
     * 
     * @param setTypes List of different set types
     * @return Total volume across all sets
     */
    fun calculateVolumeFromSetTypes(setTypes: List<com.example.liftrix.domain.model.SetType>): Double {
        return setTypes.sumOf { setType -> calculateVolumeForSetType(setType) }
    }
}
