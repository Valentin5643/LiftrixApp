package com.example.liftrix.domain.model.analytics

import kotlinx.datetime.LocalDate

/**
 * Unified data class representing individual 1RM data point.
 * This is the single source of truth for 1RM progression tracking.
 */
data class OneRmDataPoint(
    val date: LocalDate,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val actualOneRm: Float? = null, // Actual 1RM from single rep
    val estimatedOneRm: Float, // Estimated using Epley formula
    val weight: Float? = null,
    val reps: Int? = null,
    val isEstimated: Boolean = true
) {
    /**
     * Gets the best available 1RM value (actual if available, otherwise estimated)
     */
    val bestOneRm: Float
        get() = actualOneRm ?: estimatedOneRm
}