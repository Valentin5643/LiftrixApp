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
    val label: String = "",
    val volumeKg: Double = volume.kilograms
) {
    fun getVolumeInKg(): Float = volumeKg.toFloat()

    fun getVolumeAsDouble(): Double = volumeKg

    fun hasWorkoutData(): Boolean = workoutCount > 0 && volumeKg > 0.0

    fun getDisplayLabel(): String = label.ifBlank {
        "${"%.1f kg".format(volumeKg)} (${workoutCount}x)"
    }

    fun getAverageVolumePerWorkout(): Weight = if (workoutCount > 0) {
        Weight.fromKilograms(volumeKg / workoutCount)
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
        ): VolumeDataPoint {
            val safeVolumeKg = sanitizeVolumeKg(volumeKg)
            return VolumeDataPoint(
                date = date,
                volume = Weight.fromKilograms(safeVolumeKg),
                workoutCount = workoutCount,
                exerciseCount = exerciseCount,
                label = label,
                volumeKg = safeVolumeKg
            )
        }

        fun empty(date: LocalDate): VolumeDataPoint = VolumeDataPoint(
            date = date,
            volume = Weight.ZERO,
            workoutCount = 0,
            exerciseCount = 0,
            label = "No data",
            volumeKg = 0.0
        )

        private fun sanitizeVolumeKg(volumeKg: Double): Double =
            if (volumeKg.isFinite()) volumeKg.coerceAtLeast(0.0) else 0.0
    }
}
