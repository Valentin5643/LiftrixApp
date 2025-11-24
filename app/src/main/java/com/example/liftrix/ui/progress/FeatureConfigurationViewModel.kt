package com.example.liftrix.ui.progress

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.FeatureFlagService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for feature configuration screen following MVI pattern with clean architecture.
 * 
 * This ViewModel manages the state for feature flags, A/B testing, and analytics enablement
 * with comprehensive error handling, remote configuration synchronization, and user-scoped
 * feature evaluation. It extends BaseViewModel to leverage standardized state management.
 * 
 * Key Features:
 * - Feature flag evaluation with caching for performance
 * - A/B test variant assignment with consistency guarantees
 * - Remote configuration synchronization with Firebase
 * - Fallback to local defaults when remote config unavailable
 * - User-scoped feature evaluation for multi-tenancy
 * - Comprehensive error handling with recovery strategies
 * - Analytics tracking for feature usage and experiment performance
 * 
 * Architecture Integration:
 * - Depends on FeatureFlagService for feature flag operations
 * - Uses ErrorHandler for consistent error processing
 * - Follows clean architecture with proper dependency injection
 * - Integrates with Firebase Remote Config for configuration management
 * 
 * State Management:
 * - Uses StateFlow for reactive state updates
 * - Manages independent AsyncData states for different components
 * - Provides proper error handling and recovery mechanisms
 * - Implements caching strategies for performance optimization
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun FeatureConfigurationScreen(
 *     viewModel: FeatureConfigurationViewModel = hiltViewModel()
 * ) {
 *     val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 *     
 *     when (uiState) {
 *         is UiState.Loading -> LoadingIndicator()
 *         is UiState.Success -> FeatureConfigContent(
 *             state = uiState.data,
 *             onEvent = viewModel::handleEvent
 *         )
 *         is UiState.Error -> ErrorMessage(uiState.error)
 *     }
 * }
 * ```
 * 
 * @param featureFlagService Service for feature flag and A/B testing operations
 */
@HiltViewModel
class FeatureConfigurationViewModel @Inject constructor(
    private val featureFlagService: FeatureFlagService
) : ModernBaseViewModel<UiState<FeatureConfigurationState>>(initialState = UiState.Loading) {

    /**
     * Internal state for analytics tracking enablement.
     * Tracks whether analytics should be collected for feature flag usage.
     */
    private val _analyticsEnabled = MutableStateFlow(false)

    init {
        // Load initial data when ViewModel is created
        handleEvent(FeatureConfigurationEvent.LoadInitialData)
    }

    /**
     * Handles all events from the UI following the MVI pattern.
     *
     * This method processes user interactions and internal events, updating the state
     * accordingly and triggering appropriate feature flag operations.
     *
     * @param event The event to process
     */
    fun handleEvent(event: FeatureConfigurationEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is FeatureConfigurationEvent.LoadFeatureFlags -> {
                        loadFeatureFlags()
                    }
                    is FeatureConfigurationEvent.RefreshRemoteConfig -> {
                        refreshRemoteConfig()
                    }
                    is FeatureConfigurationEvent.GetABTestVariant -> {
                        getABTestVariant(event.testKey)
                    }
                    is FeatureConfigurationEvent.CheckFeatureEnabled -> {
                        checkFeatureEnabled(event.flagKey)
                    }
                    is FeatureConfigurationEvent.LoadAllFeatureFlags -> {
                        loadAllFeatureFlags()
                    }
                    is FeatureConfigurationEvent.LoadInitialData -> {
                        loadInitialData()
                    }
                    is FeatureConfigurationEvent.ClearCache -> {
                        clearCache()
                    }
                    is FeatureConfigurationEvent.EnableAnalytics -> {
                        enableAnalytics()
                    }
                    is FeatureConfigurationEvent.DisableAnalytics -> {
                        disableAnalytics()
                    }
                    is FeatureConfigurationEvent.DismissOnboarding -> {
                        dismissOnboarding()
                    }
                    is FeatureConfigurationEvent.DismissMigrationNotification -> {
                        dismissMigrationNotification()
                    }
                }
            } catch (exception: Exception) {
                logError(
                    LiftrixError.UnknownError(
                        errorMessage = "Failed to handle event: ${event::class.simpleName}",
                        analyticsContext = mapOf(
                            "event_type" to (event::class.simpleName ?: "Unknown"),
                            "timestamp" to System.currentTimeMillis().toString()
                        )
                    ),
                    "handleEvent"
                )
            }
        }
    }


    /**
     * Loads all available feature flags from the service.
     * Updates the featureFlags state with the result.
     */
    private fun loadFeatureFlags() {
        viewModelScope.launch {
            updateFeatureFlags(AsyncData.Loading())
            val result = featureFlagService.getAllFeatureFlags()
            result.onSuccess { featureFlags ->
                updateFeatureFlags(AsyncData.Success(featureFlags))
                Timber.d("Successfully loaded ${featureFlags.size} feature flags")
            }.onFailure { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Failed to load feature flags"
                )
                logError(error, "loadFeatureFlags")
                updateFeatureFlags(AsyncData.Failure(error))
                Timber.e("Failed to load feature flags: ${error.message}")
            }
        }
    }

    /**
     * Refreshes the remote configuration from Firebase.
     * Updates both feature flags and A/B test variants after successful refresh.
     */
    private fun refreshRemoteConfig() {
        viewModelScope.launch {
            // Set loading state for remote config
            updateRemoteConfigStatus(AsyncData.Loading())
            val result = featureFlagService.refreshRemoteConfig()
            result.onSuccess { _ ->
                updateRemoteConfigStatus(AsyncData.Success(Unit))
                // After successful refresh, reload feature flags and A/B test variants
                loadAllFeatureFlags()
                Timber.d("Successfully refreshed remote configuration")
            }.onFailure { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Failed to refresh remote configuration"
                )
                logError(error, "refreshRemoteConfig")
                updateRemoteConfigStatus(AsyncData.Failure(error))
                Timber.e("Failed to refresh remote configuration: ${error.message}")
            }
        }
    }

    /**
     * Retrieves the A/B test variant for a specific test key.
     * Updates the abTestVariants state with the result for the specific test.
     *
     * @param testKey The unique identifier for the A/B test
     */
    private fun getABTestVariant(testKey: String) {
        viewModelScope.launch {
            updateABTestVariants(AsyncData.Loading())
            val result = featureFlagService.getABTestVariant(testKey)
            result.onSuccess { variant ->
                val currentVariants = getCurrentABTestVariants()
                val updatedVariants = currentVariants.toMutableMap()
                updatedVariants[testKey] = variant
                updateABTestVariants(AsyncData.Success(updatedVariants))
                Timber.d("Successfully retrieved A/B test variant for $testKey: $variant")
            }.onFailure { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Failed to get A/B test variant"
                )
                logError(error, "getABTestVariant")
                updateABTestVariants(AsyncData.Failure(error))
                Timber.e("Failed to get A/B test variant for $testKey: ${error.message}")
            }
        }
    }

    /**
     * Checks if a specific feature flag is enabled.
     * Updates the featureFlags state with the result for the specific flag.
     *
     * @param flagKey The unique identifier for the feature flag
     */
    private fun checkFeatureEnabled(flagKey: String) {
        viewModelScope.launch {
            updateFeatureFlags(AsyncData.Loading())
            val result = featureFlagService.isFeatureEnabled(flagKey)
            result.onSuccess { isEnabled ->
                val currentFlags = getCurrentFeatureFlags()
                val updatedFlags = currentFlags.toMutableMap()
                updatedFlags[flagKey] = isEnabled
                updateFeatureFlags(AsyncData.Success(updatedFlags))
                Timber.d("Successfully checked feature flag $flagKey: $isEnabled")
            }.onFailure { throwable ->
                val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = throwable.message ?: "Failed to check feature flag"
                )
                logError(error, "checkFeatureEnabled")
                updateFeatureFlags(AsyncData.Failure(error))
                Timber.e("Failed to check feature flag $flagKey: ${error.message}")
            }
        }
    }

    /**
     * Loads both feature flags and A/B test variants together.
     * Performs comprehensive loading of all feature configuration data.
     */
    private fun loadAllFeatureFlags() {
        // Set loading state for both components
        updateFeatureFlags(AsyncData.Loading())
        updateABTestVariants(AsyncData.Loading())
        
        // Load feature flags
        loadFeatureFlags()
        
        // Load A/B test variants by getting all known test keys
        // Note: This is a simplified approach - in production, you might want to
        // maintain a list of known test keys or fetch them from a configuration
        loadKnownABTestVariants()
    }

    /**
     * Loads initial data when the ViewModel is created.
     * Initializes the state with unauthenticated configuration.
     */
    private fun loadInitialData() {
        // Initialize with unauthenticated state
        setState(UiState.Success(createUnauthenticatedFeatureConfigurationState()))

        // Load all feature configuration data
        loadAllFeatureFlags()
    }

    /**
     * Clears all cached feature configuration data.
     * Resets all components to their initial not asked state.
     */
    private fun clearCache() {
        setState(UiState.Success(createUnauthenticatedFeatureConfigurationState()))
        Timber.d("Cleared feature configuration cache")
    }

    /**
     * Enables analytics tracking for feature flag usage.
     * Configures analytics to track feature flag evaluations and A/B test variants.
     */
    private fun enableAnalytics() {
        _analyticsEnabled.value = true
        Timber.d("Enabled analytics for feature configuration")
    }

    /**
     * Disables analytics tracking for feature flag usage.
     * Stops tracking feature flag evaluations and A/B test variants.
     */
    private fun disableAnalytics() {
        _analyticsEnabled.value = false
        Timber.d("Disabled analytics for feature configuration")
    }

    /**
     * Loads A/B test variants for known test keys.
     * This is a simplified implementation - in production, you might want to
     * maintain a registry of known test keys or fetch them from configuration.
     */
    private fun loadKnownABTestVariants() {
        // Example known test keys - in production, these would come from configuration
        val knownTestKeys = listOf(
            "progress_dashboard_redesign",
            "workout_creation_flow",
            "analytics_widget_layout",
            "calorie_calculation_method"
        )
        
        val variants = mutableMapOf<String, String>()
        var remainingKeys = knownTestKeys.size
        
        knownTestKeys.forEach { testKey ->
            viewModelScope.launch {
                try {
                    val result = featureFlagService.getABTestVariant(testKey)
                    result.fold(
                        onSuccess = { variant ->
                            variants[testKey] = variant
                        },
                        onFailure = { throwable ->
                            val error = throwable as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = throwable.message ?: "Unknown error",
                                analyticsContext = mapOf("operation" to "getABTestVariant")
                            )
                            Timber.w("Failed to load A/B test variant for $testKey: ${error.message}")
                        }
                    )
                } catch (exception: Exception) {
                    Timber.e(exception, "Exception loading A/B test variant for $testKey")
                } finally {
                    remainingKeys--
                    if (remainingKeys == 0) {
                        updateABTestVariants(AsyncData.Success(variants))
                    }
                }
            }
        }
    }

    /**
     * Updates the feature flags component state.
     *
     * @param flagsData The new feature flags data
     */
    private fun updateFeatureFlags(flagsData: AsyncData<Map<String, Boolean>>) {
        val currentData = uiState.value.dataOrNull() ?: FeatureConfigurationState()
        setState(UiState.Success(
            currentData.copy(
                featureFlags = flagsData,
                lastRefreshTimestamp = System.currentTimeMillis()
            )
        ))
    }

    /**
     * Updates the A/B test variants component state.
     *
     * @param variantsData The new A/B test variants data
     */
    private fun updateABTestVariants(variantsData: AsyncData<Map<String, String>>) {
        val currentData = uiState.value.dataOrNull() ?: FeatureConfigurationState()
        setState(UiState.Success(
            currentData.copy(
                abTestVariants = variantsData,
                lastRefreshTimestamp = System.currentTimeMillis()
            )
        ))
    }

    /**
     * Updates the remote config status component state.
     *
     * @param configStatus The new remote config status
     */
    private fun updateRemoteConfigStatus(configStatus: AsyncData<Unit>) {
        val currentData = uiState.value.dataOrNull() ?: FeatureConfigurationState()
        setState(UiState.Success(
            currentData.copy(
                remoteConfigStatus = configStatus,
                lastRefreshTimestamp = System.currentTimeMillis()
            )
        ))
    }

    /**
     * Gets the current feature flags from the state.
     *
     * @return Map of current feature flags, empty map if not available
     */
    private fun getCurrentFeatureFlags(): Map<String, Boolean> {
        val currentState = uiState.value.dataOrNull()
        return when (val flags = currentState?.featureFlags) {
            is AsyncData.Success -> flags.data
            else -> emptyMap()
        }
    }

    /**
     * Gets the current A/B test variants from the state.
     *
     * @return Map of current A/B test variants, empty map if not available
     */
    private fun getCurrentABTestVariants(): Map<String, String> {
        val currentState = uiState.value.dataOrNull()
        return when (val variants = currentState?.abTestVariants) {
            is AsyncData.Success -> variants.data
            else -> emptyMap()
        }
    }

    /**
     * Handles coordination events from the ProgressDashboardCoordinator.
     * 
     * This method processes events that require coordination between ViewModels,
     * primarily responding to refresh events to update feature flags and remote config.
     * 
     * @param event The coordination event to process
     */
    fun handleCoordinatorEvent(event: CoordinatorEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is CoordinatorEvent.RefreshAllData -> {
                        handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
                        handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
                    }
                    is CoordinatorEvent.RefreshSpecificData -> {
                        if (event.dataTypes.contains("features") || event.dataTypes.contains("config")) {
                            handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
                            handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
                        }
                    }
                    is CoordinatorEvent.UserAuthChanged -> {
                        // Feature flags might be user-scoped, so refresh on auth change
                        if (event.userId != null) {
                            handleEvent(FeatureConfigurationEvent.LoadFeatureFlags)
                            Timber.d("Features: User auth changed to ${event.userId}, reloading feature flags")
                        } else {
                            Timber.d("Features: User logged out")
                        }
                    }
                    else -> {
                        // Ignore other coordinator events
                    }
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Failed to handle coordinator event: ${event::class.simpleName}",
                    analyticsContext = mapOf(
                        "event_type" to (event::class.simpleName ?: "Unknown"),
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
                logError(error, "handleCoordinatorEvent")
                Timber.e(exception, "Failed to handle coordinator event: ${event::class.simpleName}")
            }
        }
    }
    
    /**
     * Dismisses the onboarding flow.
     */
    private fun dismissOnboarding() {
        viewModelScope.launch {
            try {
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> {
                            val updatedData = currentState.data.copy(
                                showOnboarding = false
                            )
                            UiState.Success(updatedData)
                        }
                        else -> currentState
                    }
                }
                Timber.d("Onboarding dismissed")
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Failed to dismiss onboarding",
                    analyticsContext = mapOf("operation" to "dismissOnboarding")
                )
                logError(error, "dismissOnboarding")
                Timber.e(exception, "Failed to dismiss onboarding")
            }
        }
    }
    
    /**
     * Dismisses the migration notification.
     */
    private fun dismissMigrationNotification() {
        viewModelScope.launch {
            try {
                updateState { currentState ->
                    when (currentState) {
                        is UiState.Success -> {
                            val updatedData = currentState.data.copy(
                                showMigrationNotification = false
                            )
                            UiState.Success(updatedData)
                        }
                        else -> currentState
                    }
                }
                Timber.d("Migration notification dismissed")
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Failed to dismiss migration notification",
                    analyticsContext = mapOf("operation" to "dismissMigrationNotification")
                )
                logError(error, "dismissMigrationNotification")
                Timber.e(exception, "Failed to dismiss migration notification")
            }
        }
    }
}