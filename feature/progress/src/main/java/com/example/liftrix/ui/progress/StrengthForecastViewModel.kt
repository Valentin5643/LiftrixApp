package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.progress.ProgressDetailAnalyticsGateway
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StrengthForecastViewModel @Inject constructor(
    private val analyticsGateway: ProgressDetailAnalyticsGateway
) : ModernBaseViewModel<UiState<StrengthForecastState>>(initialState = UiState.Success(StrengthForecastState())) {

    fun handleEvent(event: StrengthForecastEvent) {
        when (event) {
            StrengthForecastEvent.Load,
            StrengthForecastEvent.Refresh -> loadForecast()
            StrengthForecastEvent.ClearError -> updateForecastState { copy(errorMessage = null) }
            is StrengthForecastEvent.SelectExercise -> selectExercise(event.exerciseId)
        }
    }

    fun handleCoordinatorEvent(event: CoordinatorEvent) {
        when (event) {
            is CoordinatorEvent.UserAuthChanged -> {
                updateForecastState { copy(userId = event.userId, forecast = null, selectedExerciseId = null, errorMessage = null) }
                if (event.userId != null) loadForecast()
            }
            is CoordinatorEvent.RefreshAllData -> loadForecast(force = true)
            is CoordinatorEvent.RefreshSpecificData -> {
                if (event.dataTypes.any { it == "charts" || it == "analytics" || it == "strength_forecast" }) {
                    loadForecast(force = true)
                }
            }
            else -> Unit
        }
    }

    private fun loadForecast(force: Boolean = false) {
        val state = currentState() ?: return
        val userId = state.userId ?: return
        if (state.isLoading && !force) return

        viewModelScope.launch {
            updateForecastState { copy(isLoading = true, errorMessage = null) }
            analyticsGateway.getStrengthForecast(
                userId = userId,
                selectedExerciseId = null,
                historyDays = 30,
                forecastDays = 14
            ).fold(
                onSuccess = { result ->
                    val currentSelection = currentState()?.selectedExerciseId
                    val nextSelection = currentSelection?.takeIf { selected ->
                        result.exercises.any { it.exerciseId == selected }
                    } ?: result.selectedExerciseId
                    updateForecastState {
                        copy(
                            forecast = result,
                            selectedExerciseId = nextSelection,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { throwable ->
                    Timber.e(throwable, "Failed to load strength forecast")
                    updateForecastState {
                        copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Unable to load strength forecast"
                        )
                    }
                }
            )
        }
    }

    private fun selectExercise(exerciseId: String) {
        updateForecastState { copy(selectedExerciseId = exerciseId) }
    }

    private fun currentState(): StrengthForecastState? =
        (uiState.value as? UiState.Success)?.data

    private fun updateForecastState(reducer: StrengthForecastState.() -> StrengthForecastState) {
        val state = currentState() ?: StrengthForecastState()
        _uiState.value = UiState.Success(state.reducer())
    }
}
