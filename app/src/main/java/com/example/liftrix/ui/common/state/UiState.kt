package com.example.liftrix.ui.common.state

import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Sealed class hierarchy for standardized UI state management across the application.
 * 
 * This state pattern provides a consistent approach to handling loading, success, error,
 * and empty states throughout all screens. It integrates with the LiftrixError system
 * for type-safe error handling and enables reactive UI updates with Compose.
 * 
 * Key benefits:
 * - Exhaustive when expressions for complete state handling
 * - Type-safe error propagation with LiftrixError integration
 * - Consistent loading and empty state patterns
 * - Reactive data flow with StateFlow/Compose integration
 * - Eliminates boolean flag patterns for cleaner state management
 * 
 * Usage:
 * ```kotlin
 * sealed class MyScreenUiState : UiState<MyData>()
 * 
 * // In ViewModel
 * val uiState: StateFlow<MyScreenUiState> = flow.map { data ->
 *     UiState.Success(data)
 * }.catch { error ->
 *     emit(UiState.Error(LiftrixError.fromThrowable(error)))
 * }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)
 * ```
 * 
 * @param T The type of data contained in success state
 */
sealed class UiState<out T> {
    
    /**
     * Initial loading state when data is being fetched or processed.
     * 
     * Use this state for:
     * - Initial screen loads
     * - Data refresh operations
     * - Long-running operations without existing data
     * 
     * UI should display loading indicators (progress bars, skeletons, etc.)
     */
    object Loading : UiState<Nothing>()
    
    /**
     * Successful state containing the requested data.
     * 
     * @param data The successfully loaded data of type T
     * @param isRefreshing Optional flag for pull-to-refresh or background updates
     * @param timestamp Optional timestamp for cache validation and freshness indicators
     * 
     * Use this state when:
     * - Data has been successfully loaded
     * - Operations completed without errors
     * - Content is ready for display
     */
    data class Success<T>(
        val data: T,
        val isRefreshing: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    ) : UiState<T>()
    
    /**
     * Error state with structured error information and recovery options.
     * 
     * @param error The LiftrixError containing error details and recovery information
     * @param previousData Optional data from previous successful state for graceful degradation
     * 
     * Use this state when:
     * - Network requests fail
     * - Validation errors occur
     * - Database operations fail
     * - Any recoverable or non-recoverable error occurs
     * 
     * The UI should:
     * - Display user-friendly error messages from error.userMessage
     * - Show retry buttons when error.isRecoverable is true
     * - Preserve previous data when available for better UX
     */
    data class Error<T>(
        val error: LiftrixError,
        val previousData: T? = null
    ) : UiState<T>()
    
    /**
     * Empty state when no data is available but the operation was successful.
     * 
     * @param message Optional custom message for the empty state
     * @param actionText Optional text for call-to-action button
     * @param showAction Whether to show action button for creating/adding content
     * 
     * Use this state when:
     * - Lists or collections are empty
     * - Search results return no matches
     * - User has no data yet (first-time experience)
     * - Filters result in no content
     * 
     * UI should display:
     * - Friendly empty state illustrations
     * - Encouraging messages to guide user actions
     * - Clear call-to-action buttons when appropriate
     */
    data class Empty(
        val message: String = "No data available",
        val actionText: String? = null,
        val showAction: Boolean = false
    ) : UiState<Nothing>()
}

/**
 * Convenience functions for common UI state operations and transformations.
 */

/**
 * Maps the data in a Success state to a new type while preserving state metadata.
 * Returns the same state for non-Success states.
 * 
 * @param transform Function to transform the data of type T to type R
 * @return UiState<R> with transformed data or unchanged non-Success state
 */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    UiState.Loading -> UiState.Loading
    is UiState.Success -> UiState.Success(
        data = transform(data),
        isRefreshing = isRefreshing,
        timestamp = timestamp
    )
    is UiState.Error -> UiState.Error(
        error = error,
        previousData = previousData?.let(transform)
    )
    is UiState.Empty -> UiState.Empty(
        message = message,
        actionText = actionText,
        showAction = showAction
    )
    else -> UiState.Loading // Fallback for unknown states
}

/**
 * Maps errors in Error state while preserving Success and other states.
 * Useful for error transformation or enrichment.
 * 
 * @param transform Function to transform the LiftrixError
 * @return UiState<T> with transformed error or unchanged state
 */
inline fun <T> UiState<T>.mapError(transform: (LiftrixError) -> LiftrixError): UiState<T> = when (this) {
    is UiState.Error -> copy(error = transform(error))
    UiState.Loading -> this
    is UiState.Success -> this
    is UiState.Empty -> this
    else -> this // Fallback for unknown states
}

/**
 * Returns the data if in Success state, null otherwise.
 * Useful for conditional access to data across all states.
 */
fun <T> UiState<T>.dataOrNull(): T? = when (this) {
    is UiState.Success -> data
    is UiState.Error -> previousData
    UiState.Loading -> null
    is UiState.Empty -> null
    else -> null // Fallback for unknown states
}

/**
 * Returns the error if in Error state, null otherwise.
 * Useful for conditional error handling.
 */
fun <T> UiState<T>.errorOrNull(): LiftrixError? = when (this) {
    is UiState.Error -> error
    UiState.Loading -> null
    is UiState.Success -> null
    is UiState.Empty -> null
    else -> null // Fallback for unknown states
}

/**
 * Checks if the state represents a loading condition.
 * True for Loading state or Success state with isRefreshing = true.
 */
fun <T> UiState<T>.isLoading(): Boolean = when (this) {
    UiState.Loading -> true
    is UiState.Success -> isRefreshing
    is UiState.Error -> false
    is UiState.Empty -> false
    else -> false // Fallback for unknown states
}

/**
 * Checks if the state contains successful data (regardless of refresh status).
 */
fun <T> UiState<T>.hasData(): Boolean = when (this) {
    is UiState.Success -> true
    is UiState.Error -> previousData != null
    UiState.Loading -> false
    is UiState.Empty -> false
    else -> false // Fallback for unknown states
}

/**
 * Checks if the state represents an error condition.
 */
fun <T> UiState<T>.isError(): Boolean = this is UiState.Error

/**
 * Checks if the state represents an empty condition.
 */
fun <T> UiState<T>.isEmpty(): Boolean = this is UiState.Empty

/**
 * Checks if the state can be retried (has recoverable error).
 */
fun <T> UiState<T>.canRetry(): Boolean = when (this) {
    is UiState.Error -> error.isRecoverable
    UiState.Loading -> false
    is UiState.Success -> false
    is UiState.Empty -> false
    else -> false // Fallback for unknown states
}

/**
 * Returns a user-friendly message for the current state.
 * Useful for displaying status messages in UI.
 */
fun <T> UiState<T>.getDisplayMessage(): String? = when (this) {
    UiState.Loading -> "Loading..."
    is UiState.Success -> if (isRefreshing) "Refreshing..." else null
    is UiState.Error -> error.message
    is UiState.Empty -> message
    else -> null // Fallback for unknown states
}

/**
 * Combines multiple UiState instances for screens with multiple data sources.
 * Returns Loading if any state is loading, Error if any has error, Success if all successful.
 * 
 * @param states Collection of UiState instances to combine
 * @return Combined UiState representing the overall loading/error/success state
 */
fun combineUiStates(vararg states: UiState<*>): UiState<Unit> {
    // Check for loading states first
    if (states.any { it.isLoading() }) {
        return UiState.Loading
    }
    
    // Check for error states
    val firstError = states.firstOrNull { it.isError() } as? UiState.Error
    if (firstError != null) {
        return UiState.Error(
            error = firstError.error,
            previousData = Unit
        )
    }
    
    // Check if all states are empty
    if (states.all { it.isEmpty() }) {
        return UiState.Empty()
    }
    
    // All states are successful or have data
    return UiState.Success(Unit)
}

/**
 * Creates a Success state with the given data.
 * Convenience function for cleaner state creation.
 */
fun <T> T.asSuccessState(isRefreshing: Boolean = false): UiState<T> = 
    UiState.Success(this, isRefreshing)

/**
 * Creates an Error state with the given LiftrixError.
 * Convenience function for cleaner error state creation.
 */
fun <T> LiftrixError.asErrorState(previousData: T? = null): UiState<T> = 
    UiState.Error(this, previousData)

/**
 * Creates an Empty state with optional message and action.
 * Convenience function for cleaner empty state creation.
 */
fun emptyUiState(
    message: String = "No data available",
    actionText: String? = null,
    showAction: Boolean = false
): UiState<Nothing> = UiState.Empty(message, actionText, showAction)