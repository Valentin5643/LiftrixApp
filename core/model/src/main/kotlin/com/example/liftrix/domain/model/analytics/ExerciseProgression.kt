package com.example.liftrix.domain.model.analytics

/**
 * Data class representing progression for a specific exercise.
 */
data class ExerciseProgression(
    val exerciseId: String,
    val dataPoints: List<OneRmDataPoint>,
    val currentMax: Float,
    val progression: Float, // Percentage change
    val bestSet: OneRmDataPoint?
)