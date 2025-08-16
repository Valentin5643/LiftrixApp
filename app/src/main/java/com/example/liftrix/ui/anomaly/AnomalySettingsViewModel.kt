package com.example.liftrix.ui.anomaly

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.anomaly.DetectWorkoutAnomaliesUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the anomaly detection settings screen
 * 
 * Manages the state and business logic for configuring anomaly detection settings.
 * Follows the BaseViewModel pattern with UiState for consistent state management.
 */
// @HiltViewModel
class AnomalySettingsViewModel @Inject constructor(
    private val detectAnomaliesUseCase: DetectWorkoutAnomaliesUseCase,
    private val authRepository: AuthRepository,
    errorHandler: ErrorHandler
) : BaseViewModel<AnomalySettingsUiState, AnomalySettingsEvent>(errorHandler) {

    override val _uiState: MutableStateFlow<AnomalySettingsUiState> = MutableStateFlow(UiState.Loading)

    override fun handleEvent(event: AnomalySettingsEvent) {
        when (event) {
            is AnomalySettingsEvent.LoadSettings -> loadSettings()
            is AnomalySettingsEvent.UpdateSettings -> updateSettings(event.settings)
            is AnomalySettingsEvent.ResetToDefaults -> resetToDefaults()
        }
    }

    /**
     * Loads the current anomaly detection settings for the user
     */
    fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    _uiState.value = UiState.Error(LiftrixError.AuthenticationError(errorMessage = "User not authenticated"))
                    return@launch
                }

                val settings = detectAnomaliesUseCase.getDetectionSettings(currentUser.uid)
                _uiState.value = UiState.Success(settings)
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading anomaly detection settings")
                _uiState.value = UiState.Error(
                    LiftrixError.UnknownError("Failed to load settings: ${e.message}")
                )
            }
        }
    }

    /**
     * Updates the anomaly detection settings
     */
    fun updateSettings(updatedSettings: AnomalyDetectionSettings) {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    Timber.w("Cannot update settings - user not authenticated")
                    return@launch
                }

                // Update the UI immediately for responsiveness
                _uiState.value = UiState.Success(updatedSettings)
                
                // Save settings in the background
                // Note: This would typically use a repository method to save settings
                // For now, we'll just log the update since the repository method isn't implemented yet
                Timber.d("Updating anomaly detection settings for user: ${currentUser.uid}")
                Timber.d("Settings: $updatedSettings")
                
                // In a real implementation, you would call something like:
                // val result = anomalyRepository.saveDetectionSettings(updatedSettings)
                // Handle the result appropriately
                
            } catch (e: Exception) {
                Timber.e(e, "Error updating anomaly detection settings")
                // Revert to previous state on error
                loadSettings()
            }
        }
    }

    /**
     * Resets settings to default values
     */
    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                val currentUser = authRepository.getCurrentUser()
                if (currentUser == null) {
                    Timber.w("Cannot reset settings - user not authenticated")
                    return@launch
                }

                val defaultSettings = AnomalyDetectionSettings.createDefault(currentUser.uid)
                updateSettings(defaultSettings)
                
                Timber.d("Reset anomaly detection settings to defaults for user: ${currentUser.uid}")
                
            } catch (e: Exception) {
                Timber.e(e, "Error resetting anomaly detection settings")
                _uiState.value = UiState.Error(
                    LiftrixError.UnknownError("Failed to reset settings: ${e.message}")
                )
            }
        }
    }

    override fun setLoadingState() {
        _uiState.value = UiState.Loading
    }

    override fun updateErrorState(error: LiftrixError) {
        _uiState.value = UiState.Error(error)
    }
}

/**
 * Type alias for the anomaly settings UI state
 */
typealias AnomalySettingsUiState = UiState<AnomalyDetectionSettings>

/**
 * Events for the anomaly settings screen
 */
sealed class AnomalySettingsEvent : ViewModelEvent {
    data object LoadSettings : AnomalySettingsEvent()
    data class UpdateSettings(val settings: AnomalyDetectionSettings) : AnomalySettingsEvent()
    data object ResetToDefaults : AnomalySettingsEvent()
}