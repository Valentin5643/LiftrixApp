package com.example.liftrix.ui.social

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.usecase.social.SearchUsersUseCase
import com.example.liftrix.domain.usecase.social.SearchUsersRequest
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for user search functionality with debounced queries and caching
 * 
 * Manages search state, handles user interactions, and coordinates with the domain layer
 * for user discovery. Implements debounced search for performance optimization and
 * provides advanced filtering capabilities.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class UserSearchViewModel @Inject constructor(
    private val searchUsersUseCase: SearchUsersUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<UserSearchUiState, UserSearchEvent>(errorHandler) {

    override val _uiState = MutableStateFlow(
        UserSearchUiState(
            searchQuery = "",
            searchResults = emptyList(),
            appliedFilters = SearchFilters(),
            isSearching = false,
            error = null,
            hasSearched = false,
            isCachedResult = false
        )
    )

    init {
        setupDebouncedSearch()
    }

    override fun handleEvent(event: UserSearchEvent) {
        when (event) {
            is UserSearchEvent.UpdateSearchQuery -> {
                updateSearchQuery(event.query)
            }
            is UserSearchEvent.ApplyFilters -> {
                applyFilters(event.filters)
            }
            is UserSearchEvent.ClearFilters -> {
                clearFilters()
            }
            is UserSearchEvent.RetrySearch -> {
                retrySearch()
            }
            is UserSearchEvent.ClearSearch -> {
                clearSearch()
            }
            is UserSearchEvent.SelectUser -> {
                // This event is handled by the UI navigation
                Timber.d("User selected: ${event.userId}")
            }
        }
    }

    /**
     * Updates the search query and triggers debounced search
     */
    private fun updateSearchQuery(query: String) {
        updateState { currentState ->
            currentState.copy(
                searchQuery = query,
                error = null
            )
        }
    }

    /**
     * Applies search filters and triggers search if query exists
     */
    private fun applyFilters(filters: SearchFilters) {
        updateState { currentState ->
            currentState.copy(
                appliedFilters = filters,
                error = null
            )
        }
        
        // Trigger search with new filters if we have a query
        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery, filters)
        }
    }

    /**
     * Clears all applied filters
     */
    private fun clearFilters() {
        updateState { currentState ->
            currentState.copy(
                appliedFilters = SearchFilters(),
                error = null
            )
        }
        
        // Re-search with cleared filters if we have a query
        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery, SearchFilters())
        }
    }

    /**
     * Retries the last search
     */
    private fun retrySearch() {
        val currentState = _uiState.value
        if (currentState.searchQuery.isNotBlank()) {
            performSearch(currentState.searchQuery, currentState.appliedFilters)
        }
    }

    /**
     * Clears search query and results
     */
    private fun clearSearch() {
        updateState { currentState ->
            currentState.copy(
                searchQuery = "",
                searchResults = emptyList(),
                error = null,
                hasSearched = false,
                isCachedResult = false
            )
        }
    }

    /**
     * Sets up debounced search to avoid excessive API calls
     */
    private fun setupDebouncedSearch() {
        viewModelScope.launch {
            _uiState
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged { old, new ->
                    old.searchQuery == new.searchQuery && old.appliedFilters == new.appliedFilters
                }
                .filterNot { it.searchQuery.isBlank() }
                .collect { state ->
                    if (state.searchQuery.length >= MIN_SEARCH_LENGTH) {
                        performSearch(state.searchQuery, state.appliedFilters)
                    }
                }
        }
    }

    /**
     * Performs the actual search operation
     */
    private fun performSearch(query: String, filters: SearchFilters) {
        updateState { currentState ->
            currentState.copy(
                isSearching = true,
                error = null
            )
        }

        executeUseCase(
            useCase = {
                searchUsersUseCase(
                    SearchUsersRequest(
                        query = query,
                        filters = filters,
                        limit = SEARCH_RESULTS_LIMIT,
                        useCache = true
                    )
                )
            },
            onSuccess = { result ->
                updateState { currentState ->
                    currentState.copy(
                        searchResults = result.users,
                        isSearching = false,
                        hasSearched = true,
                        isCachedResult = result.isCachedResult,
                        error = null
                    )
                }
                
                Timber.d("Search completed: ${result.users.size} results (cached: ${result.isCachedResult})")
            },
            onError = { error ->
                updateState { currentState ->
                    currentState.copy(
                        isSearching = false,
                        hasSearched = true,
                        error = error,
                        isCachedResult = false
                    )
                }
                
                Timber.e("Search failed: ${error.message}")
            },
            showLoading = false // We handle loading state manually
        )
    }

    override fun setLoadingState() {
        updateState { currentState ->
            currentState.copy(isSearching = true)
        }
    }

    override fun updateErrorState(error: LiftrixError) {
        updateState { currentState ->
            currentState.copy(
                error = error,
                isSearching = false
            )
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val MIN_SEARCH_LENGTH = 2
        private const val SEARCH_RESULTS_LIMIT = 20
    }
}

/**
 * UI state for user search screen
 */
data class UserSearchUiState(
    val searchQuery: String,
    val searchResults: List<UserSearchResult>,
    val appliedFilters: SearchFilters,
    val isSearching: Boolean,
    val error: LiftrixError?,
    val hasSearched: Boolean,
    val isCachedResult: Boolean
) {
    
    val canShowResults: Boolean
        get() = hasSearched && searchResults.isNotEmpty()
    
    val canShowEmptyState: Boolean
        get() = hasSearched && searchResults.isEmpty() && error == null
    
    val hasActiveFilters: Boolean
        get() = appliedFilters.fitnessLevel != null ||
                appliedFilters.equipment.isNotEmpty() ||
                appliedFilters.goals.isNotEmpty() ||
                appliedFilters.minWorkouts != null ||
                appliedFilters.maxWorkouts != null
}

/**
 * Events for user search screen
 */
sealed class UserSearchEvent : ViewModelEvent {
    
    /**
     * Update the search query
     */
    data class UpdateSearchQuery(val query: String) : UserSearchEvent()
    
    /**
     * Apply search filters
     */
    data class ApplyFilters(val filters: SearchFilters) : UserSearchEvent()
    
    /**
     * Clear all applied filters
     */
    object ClearFilters : UserSearchEvent()
    
    /**
     * Retry the last search
     */
    object RetrySearch : UserSearchEvent()
    
    /**
     * Clear search query and results
     */
    object ClearSearch : UserSearchEvent()
    
    /**
     * User selected from search results
     */
    data class SelectUser(val userId: String) : UserSearchEvent()
}

/**
 * Filter options UI state for easier filter management
 */
data class FilterOptions(
    val availableFitnessLevels: List<FitnessLevel> = FitnessLevel.values().toList(),
    val availableEquipment: List<Equipment> = Equipment.values().toList(),
    val availableGoals: List<FitnessGoal> = FitnessGoal.values().toList(),
    val workoutRanges: List<WorkoutRange> = listOf(
        WorkoutRange("Beginner", 0, 10),
        WorkoutRange("Intermediate", 11, 50),
        WorkoutRange("Advanced", 51, 150),
        WorkoutRange("Expert", 151, Int.MAX_VALUE)
    )
)

/**
 * Workout range for filter options
 */
data class WorkoutRange(
    val label: String,
    val min: Int,
    val max: Int
)