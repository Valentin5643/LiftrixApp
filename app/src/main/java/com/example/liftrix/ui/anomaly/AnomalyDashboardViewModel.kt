package com.example.liftrix.ui.anomaly

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.UserAnomalyAction
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.anomaly.DetectWorkoutAnomaliesUseCase
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the anomaly detection dashboard screen
 * 
 * Manages the state and business logic for viewing and managing workout anomalies.
 * Follows the BaseViewModel pattern with UiState for consistent state management.
 */
@HiltViewModel
class AnomalyDashboardViewModel @Inject constructor(
    private val detectAnomaliesUseCase: DetectWorkoutAnomaliesUseCase,
    private val authRepository: AuthRepository,
    errorHandler: ErrorHandler
) : BaseViewModel<AnomalyDashboardUiState, AnomalyDashboardEvent>(errorHandler) {

    override val _uiState: MutableStateFlow<AnomalyDashboardUiState> = MutableStateFlow(UiState.Loading)

    override fun handleEvent(event: AnomalyDashboardEvent) {
        when (event) {
            is AnomalyDashboardEvent.LoadAnomalies -> loadAnomalies()
            is AnomalyDashboardEvent.ResolveAnomaly -> resolveAnomaly(event.anomalyId, event.action)
            is AnomalyDashboardEvent.RefreshData -> refreshData()
        }
    }

    /**
     * Loads anomalies and statistics for the current user
     */
    fun loadAnomalies() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    _uiState.value = UiState.Error(LiftrixError.AuthenticationError(errorMessage = "User not authenticated"))
                    return@launch
                }

                // Load user's recent anomalies (this would be implemented in the repository)
                // For now, we'll show an empty state until the repository methods are available
                val recentAnomalies = emptyList<com.example.liftrix.domain.model.WorkoutAnomaly>()
                
                // Get user's anomaly feedback statistics
                val feedbackResult = detectAnomaliesUseCase.getUserAnomalyFeedback(currentUser.uid)
                
                feedbackResult.fold(
                    onSuccess = { (confirmed, dismissed) ->
                        val total = confirmed + dismissed
                        val statistics = AnomalyStatistics(
                            totalDetected = total,
                            confirmedCount = confirmed,
                            dismissedCount = dismissed,
                            detectionEnabled = true // This would come from user settings
                        )
                        
                        val dashboardData = AnomalyDashboardData(
                            recentAnomalies = recentAnomalies,
                            statistics = statistics
                        )
                        
                        if (total == 0 && recentAnomalies.isEmpty()) {
                            _uiState.value = UiState.Empty()
                        } else {
                            _uiState.value = UiState.Success(dashboardData)
                        }
                    },
                    onFailure = { error ->
                        Timber.w("Failed to load anomaly feedback: ${error.message}")
                        // Show empty state if we can't load statistics
                        val statistics = AnomalyStatistics(
                            totalDetected = 0,
                            confirmedCount = 0,
                            dismissedCount = 0,
                            detectionEnabled = true
                        )
                        
                        val dashboardData = AnomalyDashboardData(
                            recentAnomalies = recentAnomalies,
                            statistics = statistics
                        )
                        
                        _uiState.value = UiState.Empty()
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading anomaly dashboard data")
                _uiState.value = UiState.Error(
                    LiftrixError.UnknownError("Failed to load anomaly data: ${e.message}")
                )
            }
        }
    }

    /**
     * Resolves an anomaly with the specified user action
     */
    fun resolveAnomaly(anomalyId: String, action: UserAnomalyAction) {
        viewModelScope.launch {
            try {
                val result = detectAnomaliesUseCase.resolveAnomaly(
                    anomalyId = anomalyId,
                    userAction = action,
                    correctedValue = null // For dashboard, we don't provide corrections
                )
                
                result.fold(
                    onSuccess = { resolvedAnomaly ->
                        Timber.d("Successfully resolved anomaly: ${resolvedAnomaly.id}")
                        // Refresh the data to show updated state
                        loadAnomalies()
                    },
                    onFailure = { error ->
                        Timber.e("Failed to resolve anomaly: ${error.message}")
                        // Could show a snackbar or error message here
                        // For now, we'll just log the error
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Error resolving anomaly: $anomalyId")
            }
        }
    }

    /**
     * Refreshes all dashboard data
     */
    private fun refreshData() {
        loadAnomalies()
    }

    override fun setLoadingState() {
        _uiState.value = UiState.Loading
    }

    override fun updateErrorState(error: LiftrixError) {
        _uiState.value = UiState.Error(error)
    }
}

/**
 * Type alias for the anomaly dashboard UI state
 */
typealias AnomalyDashboardUiState = UiState<AnomalyDashboardData>

/**
 * Events for the anomaly dashboard screen
 */
sealed class AnomalyDashboardEvent : ViewModelEvent {
    data object LoadAnomalies : AnomalyDashboardEvent()
    data class ResolveAnomaly(val anomalyId: String, val action: UserAnomalyAction) : AnomalyDashboardEvent()
    data object RefreshData : AnomalyDashboardEvent()
}