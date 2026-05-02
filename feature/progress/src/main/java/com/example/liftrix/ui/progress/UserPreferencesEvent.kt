package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Sealed class hierarchy for UserPreferences events following the MVI pattern.
 * 
 * This event system provides type-safe, exhaustive event handling for user preference
 * operations. Each event represents a specific user action or system trigger that
 * should result in a state change.
 * 
 * Event Categories:
 * - Data Loading: Events for loading and refreshing preferences
 * - Preference Updates: Events for changing user preferences
 * - Layout Management: Events for layout mode and widget organization
 * - User Level: Events for experience level changes
 * - System Actions: Events for resets and validation
 * 
 * Design Principles:
 * - Each event carries all necessary data for processing
 * - Events are immutable and represent single user intents
 * - Validation is performed in the ViewModel, not in events
 * - Events are named clearly to indicate user intent
 * 
 * Usage:
 * ```kotlin
 * // In Compose UI
 * Button(onClick = { 
 *     viewModel.handleEvent(UserPreferencesEvent.UpdateLayoutMode(WidgetLayoutMode.GRID))
 * }) {
 *     Text("Switch to Grid")
 * }
 * 
 * // In ViewModel
 * override fun handleEvent(event: UserPreferencesEvent) {
 *     when (event) {
 *         is UserPreferencesEvent.LoadPreferences -> loadPreferences()
 *         is UserPreferencesEvent.UpdateLayoutMode -> updateLayoutMode(event.mode)
 *         // ... other events
 *     }
 * }
 * ```
 */
sealed class UserPreferencesEvent : ViewModelEvent {
    
    /**
     * Load user preferences from the repository.
     * 
     * Triggers the initial loading of user preferences or reload after errors.
     * This event should be fired when the screen is first displayed or when
     * the user requests a refresh.
     * 
     * Side effects:
     * - Sets loading state
     * - Fetches preferences from ProgressPreferencesPort
     * - Updates UI state with loaded preferences
     */
    object LoadPreferences : UserPreferencesEvent()
    
    /**
     * Refresh user preferences from remote source.
     * 
     * Similar to LoadPreferences but specifically for refresh operations
     * that should show refresh indicators while maintaining existing data.
     * 
     * Side effects:
     * - Sets refresh loading state
     * - Fetches latest preferences from remote
     * - Updates UI state with refreshed preferences
     */
    object RefreshPreferences : UserPreferencesEvent()
    
    /**
     * Update the dashboard layout mode.
     * 
     * Changes the layout mode for the dashboard and persists the change.
     * The UI should immediately reflect the new layout mode.
     * 
     * @property mode The new layout mode to apply
     * 
     * Side effects:
     * - Updates local state immediately
     * - Persists change via ProgressPreferencesPort
     * - Triggers layout recalculation
     */
    data class UpdateLayoutMode(val mode: WidgetLayoutMode) : UserPreferencesEvent()
    
    /**
     * Update the user experience level.
     * 
     * Changes the user's experience level and triggers recalculation of
     * default widget configurations. This may change visible widgets
     * and their order based on the new level.
     * 
     * @property level The new user experience level
     * 
     * Side effects:
     * - Updates user level immediately
     * - Recalculates widget configurations
     * - Persists changes via ProgressPreferencesPort
     * - May trigger widget visibility changes
     */
    data class UpdateUserLevel(val level: UserLevel) : UserPreferencesEvent()
    
    /**
     * Reset all preferences to default values.
     * 
     * Restores all user preferences to their default state based on the
     * current user level. This is useful for recovering from configuration
     * issues or providing a fresh start.
     * 
     * Side effects:
     * - Resets all preferences to defaults
     * - Persists default configuration
     * - Triggers full UI refresh
     * - Clears any unsaved changes
     */
    object ResetToDefaults : UserPreferencesEvent()
    
    /**
     * Update widget visibility for a specific widget.
     * 
     * Toggles or sets the visibility of a specific widget while maintaining
     * widget order consistency. Ensures at least one widget remains visible.
     * 
     * @property widgetName The name of the widget to update
     * @property visible Whether the widget should be visible
     * 
     * Side effects:
     * - Updates widget visibility immediately
     * - Persists visibility change
     * - Maintains widget order consistency
     * - Validates minimum widget count
     */
    data class UpdateWidgetVisibility(
        val widgetName: String,
        val visible: Boolean
    ) : UserPreferencesEvent()
    
    /**
     * Update the order of widgets in the dashboard.
     * 
     * Reorders widgets according to the provided list while maintaining
     * visibility constraints. All visible widgets must be included in the order.
     * 
     * @property widgetOrder List of widget names in the desired order
     * 
     * Side effects:
     * - Updates widget order immediately
     * - Persists order change
     * - Validates order completeness
     * - Maintains visibility constraints
     */
    data class UpdateWidgetOrder(val widgetOrder: List<String>) : UserPreferencesEvent()
    
    /**
     * Update auto-refresh settings for widgets.
     * 
     * Configures automatic refresh behavior including enable/disable state
     * and refresh interval. Validates interval within acceptable range.
     * 
     * @property enabled Whether auto-refresh should be enabled
     * @property intervalMinutes Refresh interval in minutes (1-60)
     * 
     * Side effects:
     * - Updates auto-refresh settings immediately
     * - Persists settings change
     * - Validates interval range
     * - Starts/stops auto-refresh timer
     */
    data class UpdateAutoRefreshSettings(
        val enabled: Boolean,
        val intervalMinutes: Int
    ) : UserPreferencesEvent()
    
    /**
     * Toggle the collapsed state of a dashboard section.
     * 
     * Manages section visibility in sectioned layout modes. Only applicable
     * when the layout mode supports collapsible sections.
     * 
     * @property sectionName The name of the section to toggle
     * 
     * Side effects:
     * - Updates section collapsed state immediately
     * - Persists section state
     * - Validates layout mode compatibility
     * - Triggers section animation
     */
    data class ToggleSection(val sectionName: String) : UserPreferencesEvent()
    
    /**
     * Update widget display size preference.
     * 
     * Changes the display size for a specific widget. Only applicable in
     * layout modes that support variable widget sizes.
     * 
     * @property widgetName The name of the widget to resize
     * @property size The new display size
     * 
     * Side effects:
     * - Updates widget size immediately
     * - Persists size preference
     * - Validates layout mode compatibility
     * - Triggers widget re-layout
     */
    data class UpdateWidgetSize(
        val widgetName: String,
        val size: com.example.liftrix.domain.model.analytics.WidgetDisplaySize
    ) : UserPreferencesEvent()
    
    /**
     * Validate current preferences state.
     * 
     * Triggers validation of the current preferences to ensure they are
     * in a valid state. This is typically used before saving or after
     * bulk operations.
     * 
     * Side effects:
     * - Validates current preferences
     * - Updates validation state
     * - May trigger error messages
     * - Prevents invalid operations
     */
    object ValidatePreferences : UserPreferencesEvent()
    
    /**
     * Save pending preference changes.
     * 
     * Explicitly saves any pending changes to preferences. This is useful
     * for forms or settings screens where changes are batched.
     * 
     * Side effects:
     * - Saves all pending changes
     * - Clears unsaved changes flag
     * - Triggers success/error feedback
     * - Updates last modified timestamp
     */
    object SaveChanges : UserPreferencesEvent()
    
    /**
     * Discard unsaved preference changes.
     * 
     * Reverts any unsaved changes and restores the last saved state.
     * This is useful for cancel operations or error recovery.
     * 
     * Side effects:
     * - Reverts unsaved changes
     * - Reloads last saved state
     * - Clears unsaved changes flag
     * - Triggers UI refresh
     */
    object DiscardChanges : UserPreferencesEvent()
    
    /**
     * Dismiss any current error messages.
     * 
     * Clears error states and returns to normal operation. This allows
     * users to dismiss error messages and continue using the app.
     * 
     * Side effects:
     * - Clears error states
     * - Returns to normal operation
     * - May trigger retry logic
     */
    object DismissError : UserPreferencesEvent()
    
    /**
     * Mark widget migration notice as seen by the user.
     * 
     * This event is triggered when the user acknowledges the widget migration
     * notification dialog. It updates the user's preferences to prevent showing
     * the notice again in future sessions.
     * 
     * Effects:
     * - Updates hasSeenWidgetMigrationNotice to true
     * - Saves preferences to persist the change
     * - Dismisses migration notification UI
     */
    object MarkMigrationNoticeSeen : UserPreferencesEvent()
}

/**
 * Extension functions for UserPreferencesEvent validation and processing.
 */

/**
 * Validates that an UpdateAutoRefreshSettings event has valid parameters.
 * 
 * @return true if the event parameters are valid
 */
fun UserPreferencesEvent.UpdateAutoRefreshSettings.isValid(): Boolean = 
    intervalMinutes in 1..60

/**
 * Validates that an UpdateWidgetOrder event has a valid order list.
 * 
 * @return true if the widget order is valid
 */
fun UserPreferencesEvent.UpdateWidgetOrder.isValid(): Boolean = 
    widgetOrder.isNotEmpty() && widgetOrder.all { it.isNotBlank() }

/**
 * Validates that an UpdateWidgetVisibility event has valid parameters.
 * 
 * @return true if the event parameters are valid
 */
fun UserPreferencesEvent.UpdateWidgetVisibility.isValid(): Boolean = 
    widgetName.isNotBlank()

/**
 * Validates that a ToggleSection event has valid parameters.
 * 
 * @return true if the event parameters are valid
 */
fun UserPreferencesEvent.ToggleSection.isValid(): Boolean = 
    sectionName.isNotBlank()

/**
 * Validates that an UpdateWidgetSize event has valid parameters.
 * 
 * @return true if the event parameters are valid
 */
fun UserPreferencesEvent.UpdateWidgetSize.isValid(): Boolean = 
    widgetName.isNotBlank()

/**
 * Checks if an event requires network connectivity.
 * 
 * @return true if the event needs network access
 */
fun UserPreferencesEvent.requiresNetwork(): Boolean = when (this) {
    is UserPreferencesEvent.LoadPreferences,
    is UserPreferencesEvent.RefreshPreferences,
    is UserPreferencesEvent.SaveChanges -> true
    else -> false
}

/**
 * Checks if an event modifies user preferences.
 * 
 * @return true if the event changes preferences
 */
fun UserPreferencesEvent.modifiesPreferences(): Boolean = when (this) {
    is UserPreferencesEvent.UpdateLayoutMode,
    is UserPreferencesEvent.UpdateUserLevel,
    is UserPreferencesEvent.ResetToDefaults,
    is UserPreferencesEvent.UpdateWidgetVisibility,
    is UserPreferencesEvent.UpdateWidgetOrder,
    is UserPreferencesEvent.UpdateAutoRefreshSettings,
    is UserPreferencesEvent.ToggleSection,
    is UserPreferencesEvent.UpdateWidgetSize -> true
    else -> false
}

/**
 * Gets the operation name for analytics and logging.
 * 
 * @return operation name string
 */
fun UserPreferencesEvent.getOperationName(): String = when (this) {
    is UserPreferencesEvent.LoadPreferences -> "load_preferences"
    is UserPreferencesEvent.RefreshPreferences -> "refresh_preferences"
    is UserPreferencesEvent.UpdateLayoutMode -> "update_layout_mode"
    is UserPreferencesEvent.UpdateUserLevel -> "update_user_level"
    is UserPreferencesEvent.ResetToDefaults -> "reset_to_defaults"
    is UserPreferencesEvent.UpdateWidgetVisibility -> "update_widget_visibility"
    is UserPreferencesEvent.UpdateWidgetOrder -> "update_widget_order"
    is UserPreferencesEvent.UpdateAutoRefreshSettings -> "update_auto_refresh"
    is UserPreferencesEvent.ToggleSection -> "toggle_section"
    is UserPreferencesEvent.UpdateWidgetSize -> "update_widget_size"
    is UserPreferencesEvent.ValidatePreferences -> "validate_preferences"
    is UserPreferencesEvent.SaveChanges -> "save_changes"
    is UserPreferencesEvent.DiscardChanges -> "discard_changes"
    is UserPreferencesEvent.DismissError -> "dismiss_error"
    is UserPreferencesEvent.MarkMigrationNoticeSeen -> "mark_migration_notice_seen"
}