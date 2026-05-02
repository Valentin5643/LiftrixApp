package com.example.liftrix.domain.model.analytics

import com.example.liftrix.domain.model.Weight
import kotlinx.datetime.LocalDate

/**
 * Unified volume point used by analytics charts and progress detail screens.
 */
data class VolumeDataPoint(
    val date: LocalDate,
    val volume: Weight,
    val workoutCount: Int = 1,
    val exerciseCount: Int = 0,
    val label: String = ""
) {
    fun getVolumeInKg(): Float = volume.kilograms.toFloat()

    fun getVolumeAsDouble(): Double = volume.kilograms

    fun hasWorkoutData(): Boolean = workoutCount > 0 && volume.kilograms > 0.0

    fun getDisplayLabel(): String = label.ifBlank {
        "${volume.displayValue} (${workoutCount}x)"
    }

    fun getAverageVolumePerWorkout(): Weight = if (workoutCount > 0) {
        Weight.fromKilograms(volume.kilograms / workoutCount)
    } else {
        Weight.ZERO
    }

    companion object {
        fun fromKgFloat(
            date: LocalDate,
            volumeKg: Float,
            workoutCount: Int = 1,
            exerciseCount: Int = 0,
            label: String = ""
        ): VolumeDataPoint = fromKgDouble(
            date = date,
            volumeKg = volumeKg.toDouble(),
            workoutCount = workoutCount,
            exerciseCount = exerciseCount,
            label = label
        )

        fun fromKgDouble(
            date: LocalDate,
            volumeKg: Double,
            workoutCount: Int = 1,
            exerciseCount: Int = 0,
            label: String = ""
        ): VolumeDataPoint = VolumeDataPoint(
            date = date,
            volume = Weight.fromKilograms(volumeKg),
            workoutCount = workoutCount,
            exerciseCount = exerciseCount,
            label = label
        )

        fun empty(date: LocalDate): VolumeDataPoint = VolumeDataPoint(
            date = date,
            volume = Weight.ZERO,
            workoutCount = 0,
            exerciseCount = 0,
            label = "No data"
        )
    }
}
