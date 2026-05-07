package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.LocalDate
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.core.extensions.toKilograms
import com.example.liftrix.core.extensions.toDisplayString

/**
 * Unified VolumeDataPoint model for analytics calculations and UI display
 *
 * Represents a single data point for volume tracking across time periods.
 * Used consistently across charts, analytics calculations, and data repositories.
 *
 * @property date The date for this data point
 * @property volume The total volume lifted (using Weight model for proper unit conversion)
 * @property workoutCount The number of workouts performed on this date
 * @property exerciseCount The number of unique exercises performed (optional)
 * @property label Optional display label for this data point
 */
data class VolumeDataPoint(
    val date: LocalDate,
    val volume: Weight,
    val workoutCount: Int = 1,
    val exerciseCount: Int = 0,
    val label: String = ""
) {
    /**
     * Get volume as Float in kilograms for calculations
     */
    fun getVolumeInKg(): Float = volume.toKilograms().toFloat()
    
    /**
     * Get volume as Double for chart display
     */
    fun getVolumeAsDouble(): Double = volume.toKilograms()
    
    /**
     * Check if this data point has workout data
     */
    fun hasWorkoutData(): Boolean = workoutCount > 0 && volume.toKilograms() > 0.0
    
    /**
     * Get display-friendly label or generate default
     */
    fun getDisplayLabel(): String = label.ifBlank { 
        "${volume.toDisplayString()} (${workoutCount}x)" 
    }
    
    /**
     * Calculate average volume per workout
     */
    fun getAverageVolumePerWorkout(): Weight = if (workoutCount > 0) {
        Weight.fromKilograms(volume.toKilograms() / workoutCount)
    } else {
        Weight.fromKilograms(0.0)
    }
    
    companion object {
        /**
         * Create VolumeDataPoint from Float volume in kg
         */
        fun fromKgFloat(date: LocalDate, volumeKg: Float, workoutCount: Int = 1, exerciseCount: Int = 0, label: String = ""): VolumeDataPoint {
            return VolumeDataPoint(
                date = date,
                volume = Weight.fromKilograms(volumeKg.toDouble()),
                workoutCount = workoutCount,
                exerciseCount = exerciseCount,
                label = label
            )
        }
        
        /**
         * Create VolumeDataPoint from Double volume in kg
         */
        fun fromKgDouble(date: LocalDate, volumeKg: Double, workoutCount: Int = 1, exerciseCount: Int = 0, label: String = ""): VolumeDataPoint {
            return VolumeDataPoint(
                date = date,
                volume = Weight.fromKilograms(volumeKg),
                workoutCount = workoutCount,
                exerciseCount = exerciseCount,
                label = label
            )
        }
        
        /**
         * Create empty VolumeDataPoint for placeholder data
         */
        fun empty(date: LocalDate): VolumeDataPoint {
            return VolumeDataPoint(
                date = date,
                volume = Weight.fromKilograms(0.0),
                workoutCount = 0,
                exerciseCount = 0,
                label = "No data"
            )
        }
    }
}