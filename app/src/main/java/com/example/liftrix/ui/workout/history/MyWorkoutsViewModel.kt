package com.example.liftrix.ui.workout.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.WorkoutSummary
import com.example.liftrix.domain.usecase.GetWorkoutHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for My Workouts screen with MVI pattern and pagination support.
 * 
 * Manages:
 * - Paginated workout history loading
 * - Loading states for initial and pagination
 * - Error handling with user-friendly messages
 * - Refresh functionality
 * 
 * Follows established MVI patterns with StateFlow for reactive UI updates,
 * comprehensive error handling, and authentication integration via use cases.
 */
@HiltViewModel
class MyWorkoutsViewModel @Inject constructor(
    private val getWorkoutHistoryUseCase: GetWorkoutHistoryUseCase,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = combine(
        _uiState,
        workoutRepository.isOffline
    ) { state, isOffline ->
        state.copy(isOffline = isOffline)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    private var currentOffset = 0
    private var isInitialized = false

    init {
        loadWorkouts()
    }

    /**
     * Handles UI events following MVI pattern
     */
    fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.LoadWorkouts -> {
                loadWorkouts(refresh = false)
            }
            is UiEvent.LoadMoreWorkouts -> {
                loadMoreWorkouts()
            }
            is UiEvent.RefreshWorkouts -> {
                loadWorkouts(refresh = true)
            }
            is UiEvent.ClearError -> {
                clearError()
            }
        }
    }

    /**
     * Loads workout history with pagination support
     * 
     * @param refresh Whether to refresh data from the beginning
     */
    private fun loadWorkouts(refresh: Boolean = false) {
        if (refresh) {
            currentOffset = 0
            _uiState.value = _uiState.value.copy(
                workouts = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMoreData = true,
                error = null
            )
        } else if (!isInitialized) {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                Timber.d("Loading workouts - offset: $currentOffset, refresh: $refresh")
                
                getWorkoutHistoryUseCase.execute(
                    limit = PAGE_SIZE,
                    offset = currentOffset
                ).catch { exception ->
                    Timber.e(exception, "Failed to load workout history")
                    handleLoadingError(exception, refresh)
                }.collect { result ->
                    result.fold(
                        onSuccess = { workouts ->
                            handleLoadingSuccess(workouts, refresh)
                        },
                        onFailure = { exception ->
                            handleLoadingError(exception, refresh)
                        }
                    )
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Unexpected error loading workouts")
                handleLoadingError(exception, refresh)
            }
        }
    }

    /**
     * Loads more workouts for pagination
     */
    private fun loadMoreWorkouts() {
        val currentState = _uiState.value
        
        // Prevent loading if already loading or no more data
        if (currentState.isLoading || currentState.isLoadingMore || !currentState.hasMoreData) {
            Timber.d("Load more rejected - loading: ${currentState.isLoading}, loadingMore: ${currentState.isLoadingMore}, hasMore: ${currentState.hasMoreData}")
            return
        }

        _uiState.value = currentState.copy(
            isLoadingMore = true,
            error = null
        )

        val nextOffset = currentOffset + PAGE_SIZE
        Timber.d("Loading more workouts - nextOffset: $nextOffset")

        viewModelScope.launch {
            try {
                getWorkoutHistoryUseCase.execute(
                    limit = PAGE_SIZE,
                    offset = nextOffset
                ).catch { exception ->
                    Timber.e(exception, "Failed to load more workouts")
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = "Failed to load more workouts: ${exception.message}"
                    )
                }.collect { result ->
                    result.fold(
                        onSuccess = { newWorkouts ->
                            val updatedWorkouts = _uiState.value.workouts + newWorkouts
                            val hasMore = newWorkouts.size == PAGE_SIZE
                            
                            currentOffset = nextOffset
                            
                            _uiState.value = _uiState.value.copy(
                                workouts = updatedWorkouts,
                                isLoadingMore = false,
                                hasMoreData = hasMore,
                                error = null
                            )
                            
                            Timber.d("Loaded ${newWorkouts.size} more workouts, total: ${updatedWorkouts.size}, hasMore: $hasMore")
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(
                                isLoadingMore = false,
                                error = "Failed to load more workouts: ${exception.message}"
                            )
                            Timber.e(exception, "Failed to load more workouts")
                        }
                    )
                }
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = "Unexpected error: ${exception.message}"
                )
                Timber.e(exception, "Unexpected error loading more workouts")
            }
        }
    }

    /**
     * Handles successful workout loading
     */
    private fun handleLoadingSuccess(workouts: List<WorkoutSummary>, refresh: Boolean) {
        val hasMore = workouts.size == PAGE_SIZE
        
        if (refresh || !isInitialized) {
            currentOffset = 0
        }
        
        _uiState.value = _uiState.value.copy(
            workouts = workouts,
            isLoading = false,
            isLoadingMore = false,
            hasMoreData = hasMore,
            error = null
        )
        
        isInitialized = true
        Timber.d("Loaded ${workouts.size} workouts successfully, hasMore: $hasMore")
    }

    /**
     * Handles loading errors with appropriate state updates and offline awareness
     */
    private fun handleLoadingError(exception: Throwable, refresh: Boolean) {
        val isCurrentlyOffline = workoutRepository.isCurrentlyOffline()
        
        val errorMessage = when {
            isCurrentlyOffline -> 
                "You're offline. Showing recent workouts from your device"
            exception.message?.contains("not authenticated") == true -> 
                "Please sign in to view your workout history"
            exception.message?.contains("network") == true || 
            exception.message?.contains("connectivity") == true -> 
                "Network error. Showing cached workouts"
            else -> 
                "Failed to load workouts: ${exception.message}"
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isLoadingMore = false,
            error = if (isCurrentlyOffline && _uiState.value.workouts.isNotEmpty()) null else errorMessage
        )

        if (!isInitialized) {
            isInitialized = true
        }
    }

    /**
     * Clears the current error message
     */
    private fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}

/**
 * UI State for My Workouts screen with pagination support and offline awareness
 * 
 * @param workouts List of workout summaries to display
 * @param isLoading True when loading initial data
 * @param isLoadingMore True when loading additional pages
 * @param hasMoreData True if more data can be loaded via pagination
 * @param error Error message to display, null if no error
 * @param isOffline True when device is offline, affects UI behavior
 */
data class UiState(
    val workouts: List<WorkoutSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreData: Boolean = true,
    val error: String? = null,
    val isOffline: Boolean = false
) {
    /**
     * Indicates if the screen should show empty state
     * Empty when there are no workouts and not loading
     */
    val shouldShowEmptyState: Boolean
        get() = workouts.isEmpty() && !isLoading && error == null

    /**
     * Indicates if the screen should show error state
     * Shows error when there's an error message and no data
     */
    val shouldShowError: Boolean
        get() = error != null && workouts.isEmpty()

    /**
     * Indicates if the screen should show content
     * Shows content when there are workouts, regardless of loading or error states
     */
    val shouldShowContent: Boolean
        get() = workouts.isNotEmpty()

    /**
     * Gets the total number of workouts loaded
     */
    val workoutCount: Int
        get() = workouts.size

    /**
     * Indicates if offline indicator should be shown
     * Shows when offline and has workouts (indicating cached data)
     */
    val shouldShowOfflineIndicator: Boolean
        get() = isOffline && workouts.isNotEmpty()

    /**
     * Indicates if pagination should be disabled due to offline status
     * Disables when offline and attempting to load more data
     */
    val shouldDisablePagination: Boolean
        get() = isOffline && hasMoreData
}

/**
 * UI Events for My Workouts screen following MVI pattern
 */
sealed class UiEvent {
    /**
     * Load initial workouts
     */
    data object LoadWorkouts : UiEvent()

    /**
     * Load more workouts for pagination
     */
    data object LoadMoreWorkouts : UiEvent()

    /**
     * Refresh workouts from the beginning
     */
    data object RefreshWorkouts : UiEvent()

    /**
     * Clear current error message
     */
    data object ClearError : UiEvent()
} 