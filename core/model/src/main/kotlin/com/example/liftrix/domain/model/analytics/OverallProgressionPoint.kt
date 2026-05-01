package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.LocalDate

/**
 * Data class representing overall progression point across all exercises.
 */
data class OverallProgressionPoint(
    val date: LocalDate,
    val averageOneRm: Float,
    val maxOneRm: Float,
    val exerciseCount: Int
)