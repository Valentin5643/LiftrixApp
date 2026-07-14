package com.example.liftrix.ui.settings.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.settings.SettingsQueryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for upgrade to premium screen following MVI pattern.
 * 
 * Handles:
 * - Premium feature preview
 * - Subscription status checking
 * - Error handling with user-friendly messages
 * - Analytics tracking for upgrade funnel
 * 
 * Features Liftrix architecture patterns:
 * - LiftrixResult<T> error handling
 * - Comprehensive logging for debugging
 * - Use case dependency injection
 */
@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val authQueryUseCase: AuthQueryUseCase,
    private val settingsQueryUseCase: SettingsQueryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpgradeUiState())
    val uiState: StateFlow<UpgradeUiState> = _uiState.asStateFlow()

    init {
        loadUpgradeData()
    }

    fun onEvent(event: UpgradeEvent) {
        when (event) {
            is UpgradeEvent.RefreshSubscription -> {
                loadUpgradeData()
            }
            
            is UpgradeEvent.DismissError -> {
                _uiState.value = _uiState.value.copy(error = null)
            }
            
            is UpgradeEvent.ContactSupport -> {
                Timber.d("User requested support contact from upgrade screen")
                // Analytics tracking for support requests from upgrade funnel
            }
        }
    }

    /**
     * Load subscription status and premium features data
     */
    private fun loadUpgradeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { null }
                )

                if (userId != null) {
                    // For now, just load static data since subscription use case needs implementation
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentSubscriptionTier = "FREE",
                        hasActiveSubscription = false,
                        premiumFeatures = getPremiumFeatures(),
                        error = null
                    )
                    
                    Timber.d("Loaded upgrade data for user: $userId")
                } else {
                    handleError(LiftrixError.UnknownError("Failed to get user ID"))
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Unexpected error loading upgrade data")
                handleError(
                    LiftrixError.NetworkError(
                        errorMessage = "Failed to load upgrade information"
                    )
                )
            }
        }
    }

    /**
     * Handle errors with appropriate user messaging
     */
    private fun handleError(error: LiftrixError) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = error
        )
        
        Timber.e("Upgrade screen error: $error")
    }

    /**
     * Get premium features list with benefits
     */
    private fun getPremiumFeatures(): List<PremiumFeature> {
        return listOf(
            PremiumFeature(
                title = "Advanced Analytics",
                description = "Detailed progress tracking with comprehensive charts and insights",
                iconName = "analytics"
            ),
            PremiumFeature(
                title = "Unlimited Workouts",
                description = "Create and save unlimited workout routines and templates",
                iconName = "unlimited"
            ),
            PremiumFeature(
                title = "AI Workout Coach",
                description = "Personalized workout recommendations and form guidance",
                iconName = "ai_coach"
            ),
            PremiumFeature(
                title = "Social Features",
                description = "Connect with friends, share workouts, and compete on leaderboards",
                iconName = "social"
            ),
            PremiumFeature(
                title = "Data Export",
                description = "Export all your workout data in multiple formats",
                iconName = "export"
            ),
            PremiumFeature(
                title = "Premium Support",
                description = "Priority customer support with faster response times",
                iconName = "support"
            )
        )
    }

}

/**
 * UI state for upgrade screen
 */
data class UpgradeUiState(
    val isLoading: Boolean = false,
    val currentSubscriptionTier: String? = null,
    val hasActiveSubscription: Boolean = false,
    val premiumFeatures: List<PremiumFeature> = emptyList(),
    val error: LiftrixError? = null
) {
    val shouldShowError: Boolean
        get() = error != null && !isLoading

    val shouldShowContent: Boolean
        get() = !isLoading && error == null

    val shouldShowLoading: Boolean
        get() = isLoading && error == null
}

/**
 * Events for upgrade screen
 */
sealed class UpgradeEvent {
    data object RefreshSubscription : UpgradeEvent()
    data object DismissError : UpgradeEvent()
    data object ContactSupport : UpgradeEvent()
}

/**
 * Premium feature data class
 */
data class PremiumFeature(
    val title: String,
    val description: String,
    val iconName: String
)
