package com.example.liftrix.ui.progress

import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.isLoading
import com.example.liftrix.ui.common.state.isSuccess
import com.example.liftrix.ui.common.state.isFailure
import com.example.liftrix.ui.common.state.isNotAsked

/**
 * Data class representing the complete state of the FeatureConfigurationViewModel.
 * 
 * This state follows the MVI pattern and contains all data necessary for rendering
 * the feature configuration screen, including feature flags, A/B test variants, and
 * remote configuration status.
 * 
 * Key Features:
 * - AsyncData pattern for feature flags with independent loading states
 * - A/B test variant management with consistency guarantees
 * - Remote configuration synchronization status tracking
 * - User scoped feature flag evaluation
 * - Timestamp tracking for cache invalidation
 * - Comprehensive state checking utilities
 * 
 * State Design:
 * - Feature flags are stored as a map of flag key to boolean value
 * - A/B test variants are stored as a map of test key to variant string
 * - Remote config status tracks synchronization state
 * - User authentication state is tracked for scoped feature evaluation
 * - Last refresh timestamp enables cache invalidation strategies
 * 
 * Usage:
 * ```kotlin
 * // In ViewModel
 * private val _uiState = MutableStateFlow(UiState.Loading<FeatureConfigurationState>())
 * 
 * // Update feature flags
 * updateState { currentState ->
 *     currentState.copy(
 *         featureFlags = AsyncData.Success(newFeatureFlags)
 *     )
 * }
 * 
 * // In Compose UI
 * when (val flagsState = state.featureFlags) {
 *     is AsyncData.Loading -> CircularProgressIndicator()
 *     is AsyncData.Success -> FeatureFlagsList(flagsState.data)
 *     is AsyncData.Failure -> ErrorMessage(flagsState.error)
 * }
 * ```
 * 
 * @param featureFlags AsyncData state for all feature flags and their values
 * @param abTestVariants AsyncData state for A/B test variant assignments
 * @param remoteConfigStatus AsyncData state for remote config synchronization
 * @param userId The current user ID for scoped feature evaluation (null if not authenticated)
 * @param lastRefreshTimestamp Timestamp of the last remote config refresh operation
 */
data class FeatureConfigurationState(
    val featureFlags: AsyncData<Map<String, Boolean>> = AsyncData.NotAsked,
    val abTestVariants: AsyncData<Map<String, String>> = AsyncData.NotAsked,
    val remoteConfigStatus: AsyncData<Unit> = AsyncData.NotAsked,
    val userId: String? = null,
    val lastRefreshTimestamp: Long = 0L,
    val exportEnabled: Boolean = false,
    val showOnboarding: Boolean = true,
    val analyticsEnabled: Boolean = true
)

/**
 * State checking utility methods for FeatureConfigurationState.
 * These methods provide convenient ways to check the overall state and individual component states.
 */

/**
 * Checks if any component is currently loading data.
 * 
 * @return true if any component is in loading state, false otherwise
 */
fun FeatureConfigurationState.isAnyComponentLoading(): Boolean = 
    featureFlags.isLoading() || abTestVariants.isLoading() || remoteConfigStatus.isLoading()

/**
 * Checks if all components have successfully loaded data.
 * 
 * @return true if all components have data, false otherwise
 */
fun FeatureConfigurationState.areAllComponentsLoaded(): Boolean = 
    featureFlags.isSuccess() && abTestVariants.isSuccess() && remoteConfigStatus.isSuccess()

/**
 * Checks if any component has failed to load data.
 * 
 * @return true if any component has an error, false otherwise
 */
fun FeatureConfigurationState.hasAnyComponentError(): Boolean = 
    featureFlags.isFailure() || abTestVariants.isFailure() || remoteConfigStatus.isFailure()

/**
 * Checks if all components are in not asked state (initial state).
 * 
 * @return true if all components are in NotAsked state, false otherwise
 */
fun FeatureConfigurationState.areAllComponentsNotAsked(): Boolean = 
    featureFlags.isNotAsked() && abTestVariants.isNotAsked() && remoteConfigStatus.isNotAsked()

/**
 * Checks if the state has a valid user for scoped feature evaluation.
 * 
 * @return true if userId is not null, false otherwise
 */
fun FeatureConfigurationState.hasValidUser(): Boolean = userId != null

/**
 * Checks if a specific feature flag is enabled.
 * 
 * @param flagKey The feature flag key to check
 * @return true if the flag is enabled, false otherwise or if not loaded
 */
fun FeatureConfigurationState.isFeatureEnabled(flagKey: String): Boolean {
    return when (val flags = featureFlags) {
        is AsyncData.Success -> flags.data[flagKey] ?: false
        else -> false
    }
}

/**
 * Gets the A/B test variant for a specific test key.
 * 
 * @param testKey The A/B test key to get variant for
 * @return The variant string if available, null otherwise
 */
fun FeatureConfigurationState.getABTestVariant(testKey: String): String? {
    return when (val variants = abTestVariants) {
        is AsyncData.Success -> variants.data[testKey]
        else -> null
    }
}

/**
 * Gets all enabled feature flags.
 * 
 * @return Map of enabled feature flags, empty map if not loaded
 */
fun FeatureConfigurationState.getEnabledFeatureFlags(): Map<String, Boolean> {
    return when (val flags = featureFlags) {
        is AsyncData.Success -> flags.data.filterValues { it }
        else -> emptyMap()
    }
}

/**
 * Gets all A/B test variants.
 * 
 * @return Map of A/B test variants, empty map if not loaded
 */
fun FeatureConfigurationState.getAllABTestVariants(): Map<String, String> {
    return when (val variants = abTestVariants) {
        is AsyncData.Success -> variants.data
        else -> emptyMap()
    }
}

/**
 * Checks if remote config is synchronized.
 * 
 * @return true if remote config is successfully synchronized, false otherwise
 */
fun FeatureConfigurationState.isRemoteConfigSynchronized(): Boolean {
    return remoteConfigStatus.isSuccess()
}

/**
 * Checks if the configuration data is fresh based on a 5-minute cache policy.
 * 
 * @return true if data is fresh, false if it should be refreshed
 */
fun FeatureConfigurationState.isDataFresh(): Boolean {
    if (lastRefreshTimestamp == 0L) return false
    val cacheExpirationMs = 5 * 60 * 1000L // 5 minutes
    return (System.currentTimeMillis() - lastRefreshTimestamp) < cacheExpirationMs
}

/**
 * Gets the age of the data since last refresh in minutes.
 * 
 * @return Age in minutes, or 0 if never refreshed
 */
fun FeatureConfigurationState.getDataAgeMinutes(): Long {
    if (lastRefreshTimestamp == 0L) return 0L
    return (System.currentTimeMillis() - lastRefreshTimestamp) / (60 * 1000L)
}

/**
 * Factory methods for creating FeatureConfigurationState instances.
 */

/**
 * Creates an initial loading state for all components.
 * 
 * @param userId The user ID for scoped feature evaluation
 * @return FeatureConfigurationState with all components in loading state
 */
fun createLoadingFeatureConfigurationState(userId: String): FeatureConfigurationState =
    FeatureConfigurationState(
        featureFlags = AsyncData.Loading(),
        abTestVariants = AsyncData.Loading(),
        remoteConfigStatus = AsyncData.Loading(),
        userId = userId,
        lastRefreshTimestamp = System.currentTimeMillis()
    )

/**
 * Creates an initial state for unauthenticated users.
 * 
 * @return FeatureConfigurationState with all components in not asked state
 */
fun createUnauthenticatedFeatureConfigurationState(): FeatureConfigurationState =
    FeatureConfigurationState(
        featureFlags = AsyncData.NotAsked,
        abTestVariants = AsyncData.NotAsked,
        remoteConfigStatus = AsyncData.NotAsked,
        userId = null,
        lastRefreshTimestamp = 0L
    )

/**
 * Extension functions for functional state transformations.
 */

/**
 * Updates the feature flags data while preserving other state.
 * 
 * @param flagsData The new feature flags data
 * @return New state with updated feature flags
 */
fun FeatureConfigurationState.withFeatureFlags(flagsData: AsyncData<Map<String, Boolean>>): FeatureConfigurationState =
    copy(featureFlags = flagsData)

/**
 * Updates the A/B test variants data while preserving other state.
 * 
 * @param variantsData The new A/B test variants data
 * @return New state with updated A/B test variants
 */
fun FeatureConfigurationState.withABTestVariants(variantsData: AsyncData<Map<String, String>>): FeatureConfigurationState =
    copy(abTestVariants = variantsData)

/**
 * Updates the remote config status while preserving other state.
 * 
 * @param configStatus The new remote config status
 * @return New state with updated remote config status
 */
fun FeatureConfigurationState.withRemoteConfigStatus(configStatus: AsyncData<Unit>): FeatureConfigurationState =
    copy(remoteConfigStatus = configStatus)

/**
 * Updates the user ID and resets all components to not asked state.
 * 
 * @param userId The new user ID (null for unauthenticated)
 * @return New state with updated user ID and reset component states
 */
fun FeatureConfigurationState.withUserId(userId: String?): FeatureConfigurationState =
    copy(
        userId = userId,
        featureFlags = if (userId != null) AsyncData.NotAsked else AsyncData.NotAsked,
        abTestVariants = if (userId != null) AsyncData.NotAsked else AsyncData.NotAsked,
        remoteConfigStatus = if (userId != null) AsyncData.NotAsked else AsyncData.NotAsked,
        lastRefreshTimestamp = 0L
    )

/**
 * Updates the last refresh timestamp.
 * 
 * @param timestamp The new timestamp (defaults to current time)
 * @return New state with updated timestamp
 */
fun FeatureConfigurationState.withRefreshTimestamp(timestamp: Long = System.currentTimeMillis()): FeatureConfigurationState =
    copy(lastRefreshTimestamp = timestamp)