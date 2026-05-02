package com.example.liftrix.ui.progress

import com.example.liftrix.ui.common.event.ViewModelEvent

/**
 * Events for the ProgressDashboardCoordinator to coordinate between specialized ViewModels.
 * 
 * This sealed class defines all possible events that can be sent to the coordinator
 * to manage inter-ViewModel communication, workout session coordination, and
 * real-time update management.
 * 
 * Key Features:
 * - Workout completion event coordination
 * - Real-time update management
 * - Centralized refresh coordination
 * - Error propagation between ViewModels
 * - User session state changes
 * 
 * Usage:
 * ```kotlin
 * // From UI components
 * coordinator.handleEvent(CoordinatorEvent.RefreshAllData)
 * 
 * // From other ViewModels via coordinator
 * coordinator.handleEvent(CoordinatorEvent.WorkoutCompleted(workoutId))
 * ```
 */
sealed class CoordinatorEvent : ViewModelEvent {
    
    /**
     * Event triggered when a workout is completed.
     * Coordinates refresh of all relevant ViewModels with new workout data.
     * 
     * @property workoutId The ID of the completed workout
     */
    data class WorkoutCompleted(val workoutId: String) : CoordinatorEvent()
    
    /**
     * Event to refresh all data across all ViewModels.
     * Triggers coordinated refresh of charts, widgets, summary, and calorie data.
     */
    object RefreshAllData : CoordinatorEvent()
    
    /**
     * Event to enable or disable real-time updates.
     * Controls whether the coordinator should listen for and broadcast real-time changes.
     * 
     * @property enabled Whether real-time updates should be enabled
     */
    data class ToggleRealtimeUpdates(val enabled: Boolean) : CoordinatorEvent()
    
    /**
     * Event triggered when user authentication state changes.
     * Coordinates ViewModels to handle user login/logout scenarios.
     * 
     * @property userId The new user ID, or null if user logged out
     */
    data class UserAuthChanged(val userId: String?) : CoordinatorEvent()
    
    /**
     * Event to broadcast an error that affects multiple ViewModels.
     * Allows centralized error handling and consistent error state management.
     * 
     * @property error The error message to broadcast
     * @property affectedViewModels List of ViewModels that should handle this error
     */
    data class BroadcastError(
        val error: String,
        val affectedViewModels: List<String> = emptyList()
    ) : CoordinatorEvent()
    
    /**
     * Event triggered when workout session state changes.
     * Coordinates ViewModels to reflect current session status.
     * 
     * @property sessionActive Whether a workout session is currently active
     * @property sessionId The ID of the current session, or null if no session
     */
    data class SessionStateChanged(
        val sessionActive: Boolean,
        val sessionId: String? = null
    ) : CoordinatorEvent()
    
    /**
     * Event to coordinate data refresh after preferences change.
     * Triggers refresh of ViewModels that depend on user preferences.
     * 
     * @property preferencesChanged Map of preference keys that changed
     */
    data class PreferencesChanged(val preferencesChanged: Map<String, Any>) : CoordinatorEvent()
    
    /**
     * Event to coordinate refresh of specific data types.
     * Allows targeted refresh of specific ViewModels without full refresh.
     * 
     * @property dataTypes Set of data types to refresh (e.g., "charts", "widgets", "summary")
     */
    data class RefreshSpecificData(val dataTypes: Set<String>) : CoordinatorEvent()
    
    /**
     * Event triggered when network connectivity changes.
     * Coordinates ViewModels to handle offline/online scenarios.
     * 
     * @property isConnected Whether network connectivity is available
     */
    data class NetworkConnectivityChanged(val isConnected: Boolean) : CoordinatorEvent()
    
    /**
     * Event triggered when the time period selection changes.
     * Coordinates ViewModels to update their data for the new time period.
     * 
     * @property timeRange The new time range to apply across ViewModels
     */
    data class TimePeriodChanged(val timeRange: com.example.liftrix.domain.model.analytics.TimeRange) : CoordinatorEvent()
    
    /**
     * Event to clear global error states across all ViewModels.
     * Coordinates error dismissal and state cleanup.
     */
    object ClearError : CoordinatorEvent()
    
    /**
     * Event to coordinate cleanup when leaving the progress dashboard.
     * Ensures proper cleanup of resources and listeners.
     */
    object CleanupCoordinator : CoordinatorEvent()
    
    /**
     * Event to export data to PDF format.
     * Coordinates data collection and export across ViewModels.
     */
    object ExportToPdf : CoordinatorEvent()
    
    /**
     * Event to export data to CSV format.
     * Coordinates data collection and export across ViewModels.
     */
    object ExportToCsv : CoordinatorEvent()
    
    /**
     * Event to export raw data.
     * Coordinates raw data collection and export across ViewModels.
     */
    data class ExportRawData(val configuration: Any) : CoordinatorEvent()
    
    /**
     * Event to cancel ongoing export operations.
     * Coordinates cancellation across all ViewModels.
     */
    object CancelExport : CoordinatorEvent()
}