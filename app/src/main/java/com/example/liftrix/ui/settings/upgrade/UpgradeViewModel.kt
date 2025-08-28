package com.example.liftrix.ui.settings.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.settings.GetSubscriptionStatusUseCase
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
 * - Premium feature display with benefits and pricing
 * - Subscription status checking
 * - Purchase flow initiation (placeholder implementation)
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
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val getSubscriptionStatusUseCase: GetSubscriptionStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpgradeUiState())
    val uiState: StateFlow<UpgradeUiState> = _uiState.asStateFlow()

    private val _selectedPlan = MutableStateFlow(PremiumPlan.MONTHLY)
    val selectedPlan: StateFlow<PremiumPlan> = _selectedPlan.asStateFlow()

    init {
        loadUpgradeData()
    }

    fun onEvent(event: UpgradeEvent) {
        when (event) {
            is UpgradeEvent.SelectPlan -> {
                _selectedPlan.value = event.plan
                Timber.d("Selected plan: ${event.plan}")
                // Analytics could be tracked here
            }
            
            is UpgradeEvent.StartPurchase -> {
                startPurchaseFlow(event.plan)
            }
            
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
                val userId = getCurrentUserIdUseCase()
                
                if (userId != null) {
                    // For now, just load static data since subscription use case needs implementation
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentSubscriptionTier = "FREE",
                        hasActiveSubscription = false,
                        premiumFeatures = getPremiumFeatures(),
                        availablePlans = getAvailablePlans(),
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
     * Start the purchase flow for the selected plan
     * This is a placeholder implementation until billing integration is added
     */
    private fun startPurchaseFlow(plan: PremiumPlan) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPurchasing = true, error = null)
            
            try {
                Timber.d("Starting purchase flow for plan: $plan")
                
                // Placeholder for actual purchase implementation
                // This would integrate with Google Play Billing Library
                // For now, we'll simulate the flow
                
                kotlinx.coroutines.delay(2000) // Simulate purchase delay
                
                _uiState.value = _uiState.value.copy(
                    isPurchasing = false,
                    showPurchaseSuccess = true
                )
                
                Timber.d("Purchase flow completed for plan: $plan")
                
            } catch (exception: Exception) {
                Timber.e(exception, "Purchase flow failed")
                _uiState.value = _uiState.value.copy(isPurchasing = false)
                handleError(
                    LiftrixError.BusinessLogicError(
                        code = "PURCHASE_FAILED",
                        errorMessage = "Purchase could not be completed. Please try again."
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
            isPurchasing = false,
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

    /**
     * Get available subscription plans with pricing
     */
    private fun getAvailablePlans(): List<PremiumPlan> {
        return listOf(
            PremiumPlan.MONTHLY,
            PremiumPlan.YEARLY
        )
    }
}

/**
 * UI state for upgrade screen
 */
data class UpgradeUiState(
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val showPurchaseSuccess: Boolean = false,
    val currentSubscriptionTier: String? = null,
    val hasActiveSubscription: Boolean = false,
    val premiumFeatures: List<PremiumFeature> = emptyList(),
    val availablePlans: List<PremiumPlan> = emptyList(),
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
    data class SelectPlan(val plan: PremiumPlan) : UpgradeEvent()
    data class StartPurchase(val plan: PremiumPlan) : UpgradeEvent()
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

/**
 * Premium subscription plans
 */
enum class PremiumPlan(
    val displayName: String,
    val price: String,
    val monthlyPrice: String,
    val savings: String? = null,
    val isPopular: Boolean = false
) {
    MONTHLY(
        displayName = "Monthly",
        price = "$9.99",
        monthlyPrice = "$9.99",
        isPopular = false
    ),
    YEARLY(
        displayName = "Annual",
        price = "$79.99",
        monthlyPrice = "$6.67",
        savings = "Save 33%",
        isPopular = true
    )
}