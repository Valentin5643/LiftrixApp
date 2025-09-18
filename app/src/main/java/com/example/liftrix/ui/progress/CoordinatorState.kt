package com.example.liftrix.ui.progress

import androidx.compose.runtime.Stable
import kotlinx.datetime.Instant
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.isSuccess
import com.example.liftrix.ui.common.state.getOrNull
import com.example.liftrix.domain.model.User
import com.example.liftrix.service.export.ExportProgress

/**
 * State for the ProgressDashboardCoordinator managing inter-ViewModel communication.
 * 
 * This data class represents the coordinator's state for managing communication between
 * specialized ViewModels, workout session coordination, and real-time update management.
 * 
 * Key Features:
 * - User authentication state tracking
 * - Real-time update management
 * - Workout session coordination
 * - Network connectivity awareness
 * - Error state coordination
 * - Last activity tracking
 * 
 * State Management:
 * - Uses AsyncData for user state to handle authentication flows
 * - Tracks real-time updates and their configuration
 * - Monitors workout session lifecycle
 * - Manages network-dependent features
 * 
 * Usage:
 * ```kotlin
 * val state = CoordinatorState(
 *     currentUser = AsyncData.Success(user),
 *     realtimeUpdates = true,
 *     sessionActive = true,
 *     networkConnected = true
 * )
 * ```
 */
@Stable
data class CoordinatorState(
    /**
     * Current user authentication state.
     * 
     * States:
     * - NotAsked: Initial state or after logout
     * - Loading: Authentication in progress
     * - Success: User authenticated successfully
     * - Failure: Authentication failed or user not found
     */
    val currentUser: AsyncData<User> = AsyncData.NotAsked,
    
    /**
     * Whether real-time updates are enabled and active.
     * 
     * When true, the coordinator actively listens for and broadcasts
     * real-time changes to all connected ViewModels.
     */
    val realtimeUpdates: Boolean = false,
    
    /**
     * Whether a workout session is currently active.
     * 
     * This state helps coordinate ViewModels to show/hide session-specific
     * UI elements and handle session-related data updates.
     */
    val sessionActive: Boolean = false,
    
    /**
     * ID of the current workout session, if any.
     * 
     * Null when no session is active. Used for session-specific
     * coordination and cleanup operations.
     */
    val currentSessionId: String? = null,
    
    /**
     * Whether the device has network connectivity.
     * 
     * This state helps ViewModels adapt their behavior based on
     * network availability (e.g., showing cached data, disabling sync).
     */
    val networkConnected: Boolean = true,
    
    /**
     * Timestamp of the last workout completion.
     * 
     * Used to coordinate refresh operations and determine when
     * ViewModels should update their data after workout completion.
     */
    val lastWorkoutCompletion: Instant? = null,
    
    /**
     * Global error state that affects multiple ViewModels.
     * 
     * When set, this error should be handled by all affected ViewModels
     * to maintain consistent error state across the dashboard.
     */
    val globalError: String? = null,
    
    /**
     * Set of ViewModels that are currently being refreshed.
     * 
     * Helps prevent duplicate refresh operations and provides
     * visibility into which ViewModels are actively updating.
     */
    val refreshingViewModels: Set<String> = emptySet(),
    
    /**
     * Timestamp of the last global data refresh.
     * 
     * Used to coordinate refresh operations and prevent excessive
     * refresh requests within short time periods.
     */
    val lastGlobalRefresh: Instant? = null,
    
    /**
     * Whether the coordinator is actively listening for events.
     * 
     * When false, the coordinator ignores most events and stops
     * broadcasting updates. Used during cleanup and lifecycle management.
     */
    val isActive: Boolean = true,
    
    /**
     * Number of connected ViewModels.
     * 
     * Helps with coordination efficiency and resource management.
     * When zero, the coordinator can optimize by pausing some operations.
     */
    val connectedViewModels: Int = 0,
    
    /**
     * Current user preferences that affect coordination behavior.
     * 
     * Map of preference keys to values that influence how the coordinator
     * manages updates and communication between ViewModels.
     */
    val coordinatorPreferences: Map<String, Any> = emptyMap(),
    
    /**
     * Current export operation progress.
     * 
     * Tracks the progress of export operations initiated through the dashboard.
     * Null when no export operation is active.
     */
    val exportProgress: ExportProgress? = null
) {
    
    /**
     * Checks if the coordinator is in a state where it can process events.
     * 
     * @return true if coordinator is active and has a valid user
     */
    fun canProcessEvents(): Boolean {
        return isActive && currentUser.isSuccess()
    }
    
    /**
     * Checks if real-time updates should be active.
     * 
     * @return true if real-time updates are enabled, user is authenticated, and network is connected
     */
    fun shouldEnableRealtimeUpdates(): Boolean {
        return realtimeUpdates && currentUser.isSuccess() && networkConnected
    }
    
    /**
     * Checks if a global refresh is needed based on the last refresh time.
     * 
     * @param maxAge Maximum age in milliseconds before refresh is needed
     * @return true if a global refresh is needed
     */
    fun needsGlobalRefresh(maxAge: Long): Boolean {
        val lastRefresh = lastGlobalRefresh ?: return true
        val now = kotlinx.datetime.Clock.System.now()
        return (now.toEpochMilliseconds() - lastRefresh.toEpochMilliseconds()) > maxAge
    }
    
    /**
     * Checks if a specific ViewModel is currently being refreshed.
     * 
     * @param viewModelName Name of the ViewModel to check
     * @return true if the ViewModel is currently refreshing
     */
    fun isViewModelRefreshing(viewModelName: String): Boolean {
        return refreshingViewModels.contains(viewModelName)
    }
    
    /**
     * Gets the current user ID if available.
     * 
     * @return User ID if user is authenticated, null otherwise
     */
    fun getCurrentUserId(): String? {
        return currentUser.getOrNull()?.uid
    }
    
    /**
     * Checks if there's a global error that needs to be handled.
     * 
     * @return true if there's a global error, false otherwise
     */
    fun hasGlobalError(): Boolean {
        return !globalError.isNullOrBlank()
    }
    
    /**
     * Gets the time since the last workout completion in milliseconds.
     * 
     * @return Time in milliseconds since last workout completion, or null if no completion recorded
     */
    fun getTimeSinceLastWorkoutCompletion(): Long? {
        val completion = lastWorkoutCompletion ?: return null
        val now = kotlinx.datetime.Clock.System.now()
        return now.toEpochMilliseconds() - completion.toEpochMilliseconds()
    }
    
    /**
     * Checks if the coordinator should coordinate a refresh based on recent workout completion.
     * 
     * @param maxAge Maximum age in milliseconds to consider a completion "recent"
     * @return true if there was a recent workout completion that should trigger refresh
     */
    fun shouldRefreshAfterWorkoutCompletion(maxAge: Long = 30000): Boolean {
        val timeSinceCompletion = getTimeSinceLastWorkoutCompletion()
        return timeSinceCompletion != null && timeSinceCompletion <= maxAge
    }
    
    /**
     * Creates a copy of the state with a new refreshing ViewModel added.
     * 
     * @param viewModelName Name of the ViewModel to add to refreshing set
     * @return New state with the ViewModel added to refreshing set
     */
    fun withRefreshingViewModel(viewModelName: String): CoordinatorState {
        return copy(refreshingViewModels = refreshingViewModels + viewModelName)
    }
    
    /**
     * Creates a copy of the state with a refreshing ViewModel removed.
     * 
     * @param viewModelName Name of the ViewModel to remove from refreshing set
     * @return New state with the ViewModel removed from refreshing set
     */
    fun withoutRefreshingViewModel(viewModelName: String): CoordinatorState {
        return copy(refreshingViewModels = refreshingViewModels - viewModelName)
    }
    
    /**
     * Creates a copy of the state with updated coordinator preferences.
     * 
     * @param preferences New preferences to merge with existing ones
     * @return New state with updated preferences
     */
    fun withUpdatedPreferences(preferences: Map<String, Any>): CoordinatorState {
        return copy(coordinatorPreferences = coordinatorPreferences + preferences)
    }
    
    /**
     * Creates a copy of the state with the global error cleared.
     * 
     * @return New state with global error set to null
     */
    fun withClearedGlobalError(): CoordinatorState {
        return copy(globalError = null)
    }
}