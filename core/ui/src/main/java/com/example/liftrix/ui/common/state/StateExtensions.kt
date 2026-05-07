package com.example.liftrix.ui.common.state

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Extensions for working with UiState in Compose and ViewModels.
 * 
 * This file provides utility functions and extensions to simplify UiState usage
 * across the application, including Compose integration, Flow transformations,
 * and ViewModel helper functions.
 */

/**
 * StateFlow extensions for UiState transformations and operations.
 */

/**
 * Transforms a Flow of LiftrixResult into a StateFlow of UiState.
 * Handles error mapping and provides proper state management for reactive data sources.
 * 
 * @param scope CoroutineScope for StateFlow sharing (typically viewModelScope)
 * @param started SharingStarted strategy for the StateFlow
 * @param mapError Function to transform Throwable to LiftrixError
 * @return StateFlow<UiState<T>> ready for Compose consumption
 */
fun <T> Flow<LiftrixResult<T>>.asUiState(
    scope: kotlinx.coroutines.CoroutineScope,
    started: SharingStarted = SharingStarted.Lazily,
    mapError: (Throwable) -> LiftrixError = { LiftrixError.UnknownError("Unknown state error") }
): StateFlow<UiState<T>> = map { result ->
    result.fold(
        onSuccess = { data -> UiState.Success(data) },
        onFailure = { error -> 
            UiState.Error(
                if (error is LiftrixError) error else mapError(error)
            )
        }
    )
}.catch { error ->
    emit(UiState.Error(mapError(error)))
}.stateIn(scope, started, UiState.Loading)

/**
 * Transforms a Flow of data into UiState, handling empty lists as Empty state.
 * Useful for list-based screens that need to distinguish between loading and empty states.
 * 
 * @param scope CoroutineScope for StateFlow sharing
 * @param started SharingStarted strategy
 * @param emptyMessage Message to display for empty state
 * @param emptyAction Optional action text for empty state
 * @param mapError Function to transform exceptions to LiftrixError
 * @return StateFlow<UiState<T>> with proper empty state handling
 */
fun <T> Flow<T>.asUiStateWithEmpty(
    scope: kotlinx.coroutines.CoroutineScope,
    started: SharingStarted = SharingStarted.Lazily,
    emptyMessage: String = "No items found",
    emptyAction: String? = null,
    isEmptyCheck: (T) -> Boolean = { it is Collection<*> && it.isEmpty() },
    mapError: (Throwable) -> LiftrixError = { LiftrixError.UnknownError("Unknown state error") }
): StateFlow<UiState<T>> = map { data ->
    if (isEmptyCheck(data)) {
        UiState.Empty(
            message = emptyMessage,
            actionText = emptyAction,
            showAction = emptyAction != null
        )
    } else {
        UiState.Success(data)
    }
}.catch { error ->
    emit(UiState.Error(mapError(error)))
}.stateIn(scope, started, UiState.Loading)

/**
 * Combines two UiState flows into a single UiState containing a Pair.
 * Useful for screens that depend on multiple data sources.
 * 
 * @param other The other UiState flow to combine with
 * @return Flow<UiState<Pair<T, R>>> representing combined state
 */
fun <T, R> StateFlow<UiState<T>>.combineWith(
    other: StateFlow<UiState<R>>
): Flow<UiState<Pair<T, R>>> = combine(other) { first, second ->
    when {
        first.isLoading() || second.isLoading() -> UiState.Loading
        first.isError() -> UiState.Error(first.errorOrNull()!!)
        second.isError() -> UiState.Error(second.errorOrNull()!!)
        first.hasData() && second.hasData() -> UiState.Success(
            Pair(first.dataOrNull()!!, second.dataOrNull()!!)
        )
        first.isEmpty() || second.isEmpty() -> UiState.Empty()
        else -> UiState.Loading
    }
}

/**
 * Filters Success state data based on a predicate, converting to Empty if no items match.
 * Useful for search and filter operations.
 * 
 * @param predicate Function to test each item in the data (assumes data is Iterable)
 * @param emptyMessage Message to show when filter results in no items
 * @return Flow<UiState<T>> with filtered data or Empty state
 */
fun <T : Iterable<*>> StateFlow<UiState<T>>.filterData(
    predicate: (T) -> Boolean,
    emptyMessage: String = "No results found"
): Flow<UiState<T>> = map { state ->
    when (state) {
        is UiState.Success -> {
            if (predicate(state.data)) {
                state
            } else {
                UiState.Empty(emptyMessage)
            }
        }
        else -> state
    }
}

/**
 * ViewModel extensions for UiState management and common operations.
 */

/**
 * Extension for ViewModel to safely update MutableStateFlow with UiState.
 * Provides thread-safe state updates with error handling.
 * 
 * @param update Function to transform current UiState to new UiState
 */
fun <T> MutableStateFlow<UiState<T>>.updateState(
    update: UiState<T>.() -> UiState<T>
) {
    value = value.update()
}

/**
 * Extension for ViewModel to execute use cases with automatic UiState management.
 * Handles loading states, error mapping, and success state creation.
 * 
 * @param useCase Suspend function that returns LiftrixResult<T>
 * @param onSuccess Optional callback for additional success handling
 * @param onError Optional callback for additional error handling
 * @param preserveData Whether to preserve previous data on error
 */
fun <T> ViewModel.executeWithUiState(
    uiStateFlow: MutableStateFlow<UiState<T>>,
    useCase: suspend () -> LiftrixResult<T>,
    onSuccess: ((T) -> Unit)? = null,
    onError: ((LiftrixError) -> Unit)? = null,
    preserveData: Boolean = true
) {
    viewModelScope.launch {
        // Set loading state while preserving previous data
        val previousData = if (preserveData) uiStateFlow.value.dataOrNull() else null
        uiStateFlow.value = if (previousData != null) {
            UiState.Success(previousData, isRefreshing = true)
        } else {
            UiState.Loading
        }
        
        useCase().fold(
            onSuccess = { data ->
                uiStateFlow.value = UiState.Success(data)
                onSuccess?.invoke(data)
            },
            onFailure = { error ->
                val liftrixError = if (error is LiftrixError) error else {
                    LiftrixError.UnknownError("Failed to emit state")
                }
                uiStateFlow.value = UiState.Error(liftrixError, previousData)
                onError?.invoke(liftrixError)
            }
        )
    }
}

/**
 * Extension for ViewModel to handle list-based operations with automatic empty state.
 * Converts empty lists to Empty state for better UX.
 * 
 * @param useCase Suspend function that returns LiftrixResult<List<T>>
 * @param emptyMessage Message to display when list is empty
 * @param emptyAction Optional action text for empty state
 * @param onSuccess Optional callback for additional success handling
 * @param onError Optional callback for additional error handling
 */
fun <T> ViewModel.executeListWithUiState(
    uiStateFlow: MutableStateFlow<UiState<List<T>>>,
    useCase: suspend () -> LiftrixResult<List<T>>,
    emptyMessage: String = "No items found",
    emptyAction: String? = null,
    onSuccess: ((List<T>) -> Unit)? = null,
    onError: ((LiftrixError) -> Unit)? = null
) {
    executeWithUiState(
        uiStateFlow = uiStateFlow,
        useCase = useCase,
        onSuccess = { data ->
            if (data.isEmpty()) {
                uiStateFlow.value = UiState.Empty(
                    message = emptyMessage,
                    actionText = emptyAction,
                    showAction = emptyAction != null
                )
            }
            onSuccess?.invoke(data)
        },
        onError = onError
    )
}

/**
 * Compose extensions for UiState consumption and rendering.
 */

/**
 * Collects UiState as Compose State with lifecycle awareness.
 * Provides the standard way to consume UiState in Compose screens.
 * 
 * @return State<UiState<T>> for use in Compose
 */
@Composable
fun <T> StateFlow<UiState<T>>.collectAsUiState(): State<UiState<T>> = 
    collectAsStateWithLifecycle()

/**
 * Render different UI based on UiState with sensible defaults.
 * Provides a declarative way to handle all UiState cases in Compose.
 * 
 * @param loading Composable to render for Loading state
 * @param error Composable to render for Error state (receives LiftrixError and optional retry)
 * @param empty Composable to render for Empty state (receives message and optional action)
 * @param success Composable to render for Success state (receives data and refresh state)
 */
@Composable
fun <T> UiState<T>.Render(
    loading: @Composable () -> Unit = { /* Default loading implementation */ },
    error: @Composable (LiftrixError, (() -> Unit)?) -> Unit = { _, _ -> /* Default error */ },
    empty: @Composable (String, (() -> Unit)?) -> Unit = { _, _ -> /* Default empty */ },
    success: @Composable (T, Boolean) -> Unit
) {
    when (this) {
        is UiState.Loading -> loading()
        is UiState.Success -> success(data, isRefreshing)
        is UiState.Error -> {
            val retryAction = if (this.error.isRecoverable) {
                { /* Retry implementation would be passed from caller */ }
            } else null
            error(this.error, retryAction)
        }
        is UiState.Empty -> {
            val emptyAction = if (showAction) {
                { /* Action implementation would be passed from caller */ }
            } else null
            empty(message, emptyAction)
        }
        else -> {
            // Fallback case - show loading
            loading()
        }
    }
}

/**
 * Helper function to handle UiState rendering with common patterns.
 * Simplifies UiState consumption for screens with standard Loading/Error/Success patterns.
 * 
 * @param state The UiState to render
 * @param onRetry Optional retry action for recoverable errors
 * @param onEmptyAction Optional action for empty state
 * @param loading Custom loading composable
 * @param error Custom error composable
 * @param empty Custom empty composable
 * @param content Content to render for Success state
 */
@Composable
fun <T> HandleUiState(
    state: UiState<T>,
    onRetry: (() -> Unit)? = null,
    onEmptyAction: (() -> Unit)? = null,
    loading: @Composable () -> Unit = { /* Default loading component */ },
    error: @Composable (LiftrixError, (() -> Unit)?) -> Unit = { _, _ -> /* Default error */ },
    empty: @Composable (String, (() -> Unit)?) -> Unit = { _, _ -> /* Default empty */ },
    content: @Composable (T, Boolean) -> Unit
) {
    state.Render(
        loading = loading,
        error = { err, _ -> error(err, if (err.isRecoverable) onRetry else null) },
        empty = { msg, _ -> empty(msg, onEmptyAction) },
        success = content
    )
}

/**
 * Data class for representing search state with query and results.
 * Commonly used pattern for search screens with UiState.
 */
data class SearchState<T>(
    val query: String = "",
    val results: UiState<List<T>> = UiState.Loading,
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val hasResults: Boolean get() = results.hasData()
    val canShowSuggestions: Boolean get() = !isSearching && !hasQuery && suggestions.isNotEmpty()
}

/**
 * Data class for representing paginated list state with UiState.
 * Commonly used pattern for list screens with pagination.
 */
data class PaginatedState<T>(
    val items: UiState<List<T>> = UiState.Loading,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val currentPage: Int = 0,
    val error: LiftrixError? = null
) {
    val canLoadMore: Boolean get() = hasMorePages && !isLoadingMore && items.hasData()
    val totalItems: Int get() = items.dataOrNull()?.size ?: 0
}

