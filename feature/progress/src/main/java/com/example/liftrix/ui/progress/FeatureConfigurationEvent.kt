package com.example.liftrix.ui.progress

import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class representing all possible events that can be triggered in the FeatureConfigurationViewModel.
 * 
 * This event hierarchy follows the MVI pattern and enables type-safe event handling for
 * feature flag interactions, A/B test variant retrieval, and remote configuration
 * synchronization operations.
 * 
 * Key Events:
 * - LoadFeatureFlags: Load all available feature flags and their current values
 * - RefreshRemoteConfig: Refresh feature flags and A/B test configuration from remote
 * - GetABTestVariant: Retrieve A/B test variant for a specific test key
 * - LoadAllFeatureFlags: Load feature flags and A/B test variants together
 * - CheckFeatureEnabled: Check if a specific feature flag is enabled
 * - LoadInitialData: Initial data loading trigger
 * 
 * Integration with UI:
 * - Events are triggered from Compose UI components
 * - ViewModel processes events and updates state accordingly
 * - Reactive state updates trigger UI recomposition
 * 
 * Usage:
 * ```kotlin
 * // In Compose UI
 * onRefreshConfig = {
 *     viewModel.handleEvent(FeatureConfigurationEvent.RefreshRemoteConfig)
 * }
 * 
 * onCheckFeature = { flagKey ->
 *     viewModel.handleEvent(FeatureConfigurationEvent.CheckFeatureEnabled(flagKey))
 * }
 * 
 * // In ViewModel
 * override fun handleEvent(event: FeatureConfigurationEvent) {
 *     when (event) {
 *         is FeatureConfigurationEvent.LoadFeatureFlags -> loadFeatureFlags()
 *         is FeatureConfigurationEvent.RefreshRemoteConfig -> refreshRemoteConfig()
 *         is FeatureConfigurationEvent.GetABTestVariant -> getABTestVariant(event.testKey)
 *         is FeatureConfigurationEvent.CheckFeatureEnabled -> checkFeatureEnabled(event.flagKey)
 *         is FeatureConfigurationEvent.LoadAllFeatureFlags -> loadAllFeatureFlags()
 *         is FeatureConfigurationEvent.LoadInitialData -> loadInitialData()
 *     }
 * }
 * ```
 */
sealed class FeatureConfigurationEvent : ViewModelEvent {
    
    /**
     * Event triggered to load all available feature flags and their current values.
     * 
     * This event retrieves the complete feature flag configuration from the service,
     * including both enabled and disabled flags. The result is stored in the
     * featureFlags state as a map of flag key to boolean value.
     */
    object LoadFeatureFlags : FeatureConfigurationEvent()
    
    /**
     * Event triggered to manually refresh remote configuration.
     * 
     * This event forces a refresh of feature flags and A/B test configuration
     * from Firebase Remote Config. It updates both feature flags and A/B test
     * variants, and updates the remote config synchronization status.
     */
    object RefreshRemoteConfig : FeatureConfigurationEvent()
    
    /**
     * Event triggered to retrieve the A/B test variant for a specific test key.
     * 
     * This event fetches the assigned variant for a specific A/B test and updates
     * the abTestVariants state. The variant assignment is consistent per user
     * and persists across app sessions.
     * 
     * @param testKey The unique identifier for the A/B test
     */
    data class GetABTestVariant(val testKey: String) : FeatureConfigurationEvent()
    
    /**
     * Event triggered to check if a specific feature flag is enabled.
     * 
     * This event evaluates a single feature flag and can trigger loading of
     * feature flags if they haven't been loaded yet. The result updates the
     * featureFlags state with the current flag status.
     * 
     * @param flagKey The unique identifier for the feature flag
     */
    data class CheckFeatureEnabled(val flagKey: String) : FeatureConfigurationEvent()
    
    /**
     * Event triggered to load both feature flags and A/B test variants together.
     * 
     * This event performs a comprehensive load of all feature configuration data,
     * including feature flags, A/B test variants, and remote config synchronization.
     * It's typically used for initial screen loading or complete data refresh.
     */
    object LoadAllFeatureFlags : FeatureConfigurationEvent()
    
    /**
     * Event triggered to load initial configuration data when the screen is first displayed.
     * 
     * This event is typically called from the ViewModel's initialization block
     * or when the screen becomes visible for the first time. It loads all
     * necessary feature configuration data.
     */
    object LoadInitialData : FeatureConfigurationEvent()
    
    /**
     * Event triggered to clear all cached feature configuration data.
     * 
     * This event resets all feature flags, A/B test variants, and remote config
     * status to their initial not asked state. It's useful for user logout
     * scenarios or when switching between different user contexts.
     */
    object ClearCache : FeatureConfigurationEvent()
    
    /**
     * Event triggered to enable analytics for feature flag usage.
     * 
     * This event configures analytics tracking for feature flag evaluations
     * and A/B test variant assignments. It helps track feature adoption
     * and experiment performance.
     */
    object EnableAnalytics : FeatureConfigurationEvent()
    
    /**
     * Event triggered to disable analytics for feature flag usage.
     * 
     * This event disables analytics tracking for feature flag evaluations
     * and A/B test variant assignments. It's useful for privacy-focused
     * configurations or testing scenarios.
     */
    object DisableAnalytics : FeatureConfigurationEvent()
    
    /**
     * Event triggered to dismiss the analytics onboarding card.
     * 
     * This event marks the analytics onboarding as dismissed and updates
     * the showOnboarding flag to false.
     */
    object DismissOnboarding : FeatureConfigurationEvent()
    
    /**
     * Event triggered to dismiss the analytics migration notification.
     * 
     * This event marks the analytics migration notification as dismissed and updates
     * the showMigrationNotification flag to false.
     */
    object DismissMigrationNotification : FeatureConfigurationEvent()
}

/**
 * Extension functions for FeatureConfigurationEvent validation and processing.
 */

/**
 * Checks if the event requires data loading operations.
 * 
 * @return true if the event triggers data loading, false otherwise
 */
fun FeatureConfigurationEvent.requiresDataLoading(): Boolean = when (this) {
    is FeatureConfigurationEvent.LoadFeatureFlags,
    is FeatureConfigurationEvent.RefreshRemoteConfig,
    is FeatureConfigurationEvent.GetABTestVariant,
    is FeatureConfigurationEvent.CheckFeatureEnabled,
    is FeatureConfigurationEvent.LoadAllFeatureFlags,
    is FeatureConfigurationEvent.LoadInitialData -> true
    is FeatureConfigurationEvent.ClearCache,
    is FeatureConfigurationEvent.EnableAnalytics,
    is FeatureConfigurationEvent.DisableAnalytics,
    is FeatureConfigurationEvent.DismissOnboarding,
    is FeatureConfigurationEvent.DismissMigrationNotification -> false
}

/**
 * Checks if the event affects all feature configuration components.
 * 
 * @return true if the event affects all components, false if it affects only specific components
 */
fun FeatureConfigurationEvent.affectsAllComponents(): Boolean = when (this) {
    is FeatureConfigurationEvent.RefreshRemoteConfig,
    is FeatureConfigurationEvent.LoadAllFeatureFlags,
    is FeatureConfigurationEvent.LoadInitialData,
    is FeatureConfigurationEvent.ClearCache -> true
    is FeatureConfigurationEvent.LoadFeatureFlags,
    is FeatureConfigurationEvent.GetABTestVariant,
    is FeatureConfigurationEvent.CheckFeatureEnabled,
    is FeatureConfigurationEvent.EnableAnalytics,
    is FeatureConfigurationEvent.DisableAnalytics,
    is FeatureConfigurationEvent.DismissOnboarding,
    is FeatureConfigurationEvent.DismissMigrationNotification -> false
}

/**
 * Checks if the event is specific to a particular feature flag or A/B test.
 * 
 * @return true if the event is specific to a key, false otherwise
 */
fun FeatureConfigurationEvent.isKeySpecific(): Boolean = when (this) {
    is FeatureConfigurationEvent.GetABTestVariant,
    is FeatureConfigurationEvent.CheckFeatureEnabled -> true
    else -> false
}

/**
 * Gets the key associated with this event, if applicable.
 * 
 * @return The key string if the event is key-specific, null otherwise
 */
fun FeatureConfigurationEvent.getKey(): String? = when (this) {
    is FeatureConfigurationEvent.GetABTestVariant -> testKey
    is FeatureConfigurationEvent.CheckFeatureEnabled -> flagKey
    else -> null
}

/**
 * Checks if the event requires remote network access.
 * 
 * @return true if the event requires network access, false otherwise
 */
fun FeatureConfigurationEvent.requiresNetworkAccess(): Boolean = when (this) {
    is FeatureConfigurationEvent.RefreshRemoteConfig,
    is FeatureConfigurationEvent.LoadAllFeatureFlags,
    is FeatureConfigurationEvent.LoadInitialData -> true
    is FeatureConfigurationEvent.LoadFeatureFlags,
    is FeatureConfigurationEvent.GetABTestVariant,
    is FeatureConfigurationEvent.CheckFeatureEnabled -> false // May use cached data
    is FeatureConfigurationEvent.ClearCache,
    is FeatureConfigurationEvent.EnableAnalytics,
    is FeatureConfigurationEvent.DisableAnalytics,
    is FeatureConfigurationEvent.DismissOnboarding,
    is FeatureConfigurationEvent.DismissMigrationNotification -> false
}

/**
 * Checks if the event modifies configuration state.
 * 
 * @return true if the event modifies state, false if it only reads state
 */
fun FeatureConfigurationEvent.modifiesState(): Boolean = when (this) {
    is FeatureConfigurationEvent.LoadFeatureFlags,
    is FeatureConfigurationEvent.RefreshRemoteConfig,
    is FeatureConfigurationEvent.GetABTestVariant,
    is FeatureConfigurationEvent.CheckFeatureEnabled,
    is FeatureConfigurationEvent.LoadAllFeatureFlags,
    is FeatureConfigurationEvent.LoadInitialData,
    is FeatureConfigurationEvent.ClearCache,
    is FeatureConfigurationEvent.EnableAnalytics,
    is FeatureConfigurationEvent.DisableAnalytics,
    is FeatureConfigurationEvent.DismissOnboarding,
    is FeatureConfigurationEvent.DismissMigrationNotification -> true
}