package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.LoadingState
import com.example.liftrix.ui.common.state.isLoading
import com.example.liftrix.ui.common.state.isSuccess
import com.example.liftrix.ui.common.state.getOrNull

/**
 * UI state for the UserPreferences feature following the standardized UiState pattern.
 * 
 * This state class encapsulates all user preference data and loading states for the
 * preferences management screen. It follows the UiState pattern for consistent state
 * management across the application.
 * 
 * State Structure:
 * - Main preferences data with loading/error states
 * - Individual operation loading states for atomic updates
 * - User level information for configuration context
 * - Layout mode preferences for UI rendering
 * 
 * Usage:
 * ```kotlin
 * val state by viewModel.uiState.collectAsStateWithLifecycle()
 * when (state) {
 *     is UiState.Loading -> ShowLoadingIndicator()
 *     is UiState.Success -> ShowPreferencesContent(state.data)
 *     is UiState.Error -> ShowErrorMessage(state.error)
 *     is UiState.Empty -> ShowEmptyState()
 * }
 * ```
 */
data class UserPreferencesState(
    val preferences: AsyncData<WidgetPreferences> = AsyncData.Loading(),
    val layoutMode: WidgetLayoutMode = WidgetLayoutMode.GRID,
    val userLevel: UserLevel = UserLevel.BEGINNER,
    val loadingState: LoadingState = LoadingState(),
    val isInitialized: Boolean = false,
    val hasUnsavedChanges: Boolean = false
) {
    
    /**
     * Checks if preferences are currently loading.
     * 
     * @return true if any preference operation is in progress
     */
    fun isLoading(): Boolean = loadingState.isLoading() || preferences.isLoading()
    
    /**
     * Checks if preferences data is available.
     * 
     * @return true if preferences are successfully loaded
     */
    fun hasPreferences(): Boolean = preferences.isSuccess()
    
    /**
     * Gets the current widget preferences if available.
     * 
     * @return WidgetPreferences data or null if not loaded
     */
    fun getPreferences(): WidgetPreferences? = preferences.getOrNull()
    
    /**
     * Checks if a specific operation is currently loading.
     * 
     * @param operation the operation name to check
     * @return true if the operation is in progress
     */
    fun isOperationLoading(operation: String): Boolean = loadingState.isLoading(operation)
    
    /**
     * Updates the state with new preferences data.
     * 
     * @param newPreferences the updated preferences
     * @return updated state with new preferences
     */
    fun withPreferences(newPreferences: WidgetPreferences): UserPreferencesState = copy(
        preferences = AsyncData.Success(newPreferences),
        layoutMode = mapDashboardLayoutModeToWidgetLayoutMode(newPreferences.dashboardLayout),
        userLevel = newPreferences.userLevel,
        isInitialized = true,
        hasUnsavedChanges = false
    )
    
    /**
     * Updates the state with loading status for a specific operation.
     * 
     * @param operation the operation name
     * @param isLoading whether the operation is loading
     * @return updated state with loading status
     */
    fun withLoading(operation: String, isLoading: Boolean): UserPreferencesState = copy(
        loadingState = if (isLoading) {
            loadingState.withOperation(operation)
        } else {
            loadingState.withoutOperation(operation)
        }
    )
    
    /**
     * Updates the state with layout mode change.
     * 
     * @param newLayoutMode the new layout mode
     * @return updated state with layout mode change
     */
    fun withLayoutMode(newLayoutMode: WidgetLayoutMode): UserPreferencesState = copy(
        layoutMode = newLayoutMode,
        hasUnsavedChanges = true
    )
    
    /**
     * Updates the state with user level change.
     * 
     * @param newUserLevel the new user level
     * @return updated state with user level change
     */
    fun withUserLevel(newUserLevel: UserLevel): UserPreferencesState = copy(
        userLevel = newUserLevel,
        hasUnsavedChanges = true
    )
    
    /**
     * Updates the state with error for preferences loading.
     * 
     * @param error the error that occurred
     * @return updated state with error
     */
    fun withError(error: com.example.liftrix.domain.model.error.LiftrixError): UserPreferencesState = copy(
        preferences = AsyncData.Failure(error),
        loadingState = LoadingState() // Clear loading states on error
    )
    
    /**
     * Resets the state to initial loading state.
     * 
     * @return reset state for reloading
     */
    fun reset(): UserPreferencesState = copy(
        preferences = AsyncData.Loading(),
        loadingState = LoadingState(),
        isInitialized = false,
        hasUnsavedChanges = false
    )
    
    /**
     * Marks the state as having saved changes.
     * 
     * @return updated state with saved changes
     */
    fun markChangesSaved(): UserPreferencesState = copy(
        hasUnsavedChanges = false
    )
    
    companion object {
        /**
         * Creates initial state for the UserPreferences feature.
         * 
         * @return initial UserPreferencesState
         */
        fun initial(): UserPreferencesState = UserPreferencesState()
        
        /**
         * Maps domain DashboardLayoutMode to UI WidgetLayoutMode.
         * 
         * @param dashboardMode the domain layout mode
         * @return corresponding UI layout mode
         */
        private fun mapDashboardLayoutModeToWidgetLayoutMode(
            dashboardMode: com.example.liftrix.domain.model.analytics.DashboardLayoutMode
        ): WidgetLayoutMode = when (dashboardMode) {
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.AUTO -> WidgetLayoutMode.GRID
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.COMPACT -> WidgetLayoutMode.SECTIONS
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.EXPANDED -> WidgetLayoutMode.LIST
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.CUSTOM -> WidgetLayoutMode.STAGGERED
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.GRID -> WidgetLayoutMode.GRID
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.LIST -> WidgetLayoutMode.LIST
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.SECTIONS -> WidgetLayoutMode.SECTIONS
            com.example.liftrix.domain.model.analytics.DashboardLayoutMode.DEFAULT -> WidgetLayoutMode.GRID
        }
    }
}

/**
 * Convenience extensions for UserPreferencesState.
 */

/**
 * Checks if the preferences are in a state where they can be modified.
 * 
 * @return true if preferences can be modified
 */
fun UserPreferencesState.canModifyPreferences(): Boolean = 
    hasPreferences() && !isLoading()

/**
 * Gets the available layout modes for the current user level.
 * 
 * @return list of available layout modes
 */
fun UserPreferencesState.getAvailableLayoutModes(): List<WidgetLayoutMode> = 
    WidgetLayoutMode.getAvailableForUserLevel(userLevel)

/**
 * Checks if the current layout mode supports collapsible sections.
 * 
 * @return true if sections can be collapsed
 */
fun UserPreferencesState.supportsCollapsibleSections(): Boolean = 
    layoutMode.supportsCollapsibleSections

/**
 * Gets the widget order from preferences if available.
 * 
 * @return widget order list or empty list if not available
 */
fun UserPreferencesState.getWidgetOrder(): List<String> = 
    getPreferences()?.getOrderedVisibleWidgets() ?: emptyList()

/**
 * Checks if a specific widget is visible in the current preferences.
 * 
 * @param widgetName the name of the widget to check
 * @return true if widget is visible
 */
fun UserPreferencesState.isWidgetVisible(widgetName: String): Boolean = 
    getPreferences()?.isWidgetVisible(widgetName) ?: false