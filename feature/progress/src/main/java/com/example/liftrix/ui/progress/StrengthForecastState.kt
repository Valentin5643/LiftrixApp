package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.StrengthForecastResult

data class StrengthForecastState(
    val userId: String? = null,
    val forecast: StrengthForecastResult? = null,
    val selectedExerciseId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val hasData: Boolean = forecast?.exercises?.isNotEmpty() == true
    val selectedExercise = forecast?.exercises?.firstOrNull { it.exerciseId == selectedExerciseId }
        ?: forecast?.exercises?.firstOrNull()
}

sealed interface StrengthForecastEvent {
    data object Load : StrengthForecastEvent
    data object Refresh : StrengthForecastEvent
    data object ClearError : StrengthForecastEvent
    data class SelectExercise(val exerciseId: String) : StrengthForecastEvent
}
