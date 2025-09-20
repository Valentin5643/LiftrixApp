package com.example.liftrix.domain.model

/**
 * Sealed class hierarchy representing different types of exercise sets for volume calculation.
 * 
 * This type-safe approach allows the VolumeCalculator to handle different set types
 * without string-based branching, making it easier to add new set types and ensuring
 * compile-time safety.
 * 
 * Each set type contains the specific data needed for its volume calculation.
 */
sealed class SetType {
    
    /**
     * Standard weight-based set with fixed weight and reps.
     * 
     * Most common set type: weight × reps = volume
     * 
     * @param weightKg Weight in kilograms
     * @param reps Number of repetitions
     * @param isCompleted Whether the set was completed
     */
    data class Standard(
        val weightKg: Double,
        val reps: Int,
        val isCompleted: Boolean = true
    ) : SetType()
    
    /**
     * Drop set with multiple weight/rep pairs performed consecutively.
     * 
     * Volume = sum of (weight × reps) for each drop
     * Example: 100kg×8 + 80kg×6 + 60kg×8 = 1600kg volume
     * 
     * @param drops List of weight/rep pairs in descending weight order
     * @param isCompleted Whether the entire drop set was completed
     */
    data class Drop(
        val drops: List<Pair<Double, Int>>, // (weightKg, reps)
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(drops.isNotEmpty()) { "Drop set must have at least one drop" }
            require(drops.all { it.first > 0 && it.second > 0 }) { "All drops must have positive weight and reps" }
        }
    }
    
    /**
     * Time-based set for cardio or endurance exercises.
     * 
     * Volume = duration × intensity factor
     * Useful for exercises like planks, wall sits, cardio intervals
     * 
     * @param durationSeconds Duration of the set in seconds
     * @param intensityFactor Relative intensity (0.0 to 1.0, or bodyweight multiplier)
     * @param isCompleted Whether the time duration was completed
     */
    data class TimeBased(
        val durationSeconds: Int,
        val intensityFactor: Double = 1.0,
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(durationSeconds > 0) { "Duration must be positive" }
            require(intensityFactor >= 0.0) { "Intensity factor must be non-negative" }
        }
    }
    
    /**
     * Bodyweight set with rep count and optional weight multiplier.
     * 
     * Volume = bodyweight × multiplier × reps
     * Examples: Pull-ups, push-ups, weighted dips
     * 
     * @param bodyweightKg User's bodyweight in kilograms
     * @param reps Number of repetitions
     * @param weightMultiplier Additional weight as multiplier of bodyweight (default 1.0)
     * @param isCompleted Whether the set was completed
     */
    data class Bodyweight(
        val bodyweightKg: Double,
        val reps: Int,
        val weightMultiplier: Double = 1.0,
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(bodyweightKg > 0) { "Bodyweight must be positive" }
            require(reps > 0) { "Reps must be positive" }
            require(weightMultiplier >= 0.0) { "Weight multiplier must be non-negative" }
        }
    }
    
    /**
     * Cluster set with multiple mini-sets and rest periods within one set.
     * 
     * Volume = sum of (weight × reps) for each cluster
     * Example: 3×(100kg×3) with 15s rest = 900kg volume
     * 
     * @param weightKg Weight used for all clusters
     * @param clusters List of rep counts for each cluster
     * @param isCompleted Whether all clusters were completed
     */
    data class Cluster(
        val weightKg: Double,
        val clusters: List<Int>, // reps for each cluster
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(weightKg > 0) { "Weight must be positive" }
            require(clusters.isNotEmpty()) { "Cluster set must have at least one cluster" }
            require(clusters.all { it > 0 }) { "All clusters must have positive reps" }
        }
        
        val totalReps: Int get() = clusters.sum()
    }
    
    /**
     * Rest-pause set with initial reps then pause-reps sequences.
     * 
     * Volume = weight × (initial_reps + sum of pause_reps)
     * Example: 100kg×8 + pause + 3 + pause + 2 = 100kg×13 = 1300kg volume
     * 
     * @param weightKg Weight used throughout
     * @param initialReps Reps in the main set
     * @param pauseReps Additional reps after rest-pause intervals
     * @param isCompleted Whether the entire rest-pause sequence was completed
     */
    data class RestPause(
        val weightKg: Double,
        val initialReps: Int,
        val pauseReps: List<Int>,
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(weightKg > 0) { "Weight must be positive" }
            require(initialReps > 0) { "Initial reps must be positive" }
        }
        
        val totalReps: Int get() = initialReps + pauseReps.sum()
    }
    
    /**
     * Distance-based set for cardio or movement exercises.
     * 
     * Volume = distance × intensity factor
     * Examples: Running, rowing, cycling
     * 
     * @param distanceMeters Distance covered in meters
     * @param intensityFactor Speed/pace intensity factor
     * @param isCompleted Whether the distance was completed
     */
    data class Distance(
        val distanceMeters: Double,
        val intensityFactor: Double = 1.0,
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(distanceMeters > 0) { "Distance must be positive" }
            require(intensityFactor >= 0.0) { "Intensity factor must be non-negative" }
        }
    }
    
    /**
     * Mixed set combining multiple exercises or set types.
     * 
     * Volume = sum of volumes from all component sets
     * Examples: Supersets, circuits, compound movements
     * 
     * @param components List of component set types
     * @param isCompleted Whether all components were completed
     */
    data class Mixed(
        val components: List<SetType>,
        val isCompleted: Boolean = true
    ) : SetType() {
        init {
            require(components.isNotEmpty()) { "Mixed set must have at least one component" }
        }
    }
}