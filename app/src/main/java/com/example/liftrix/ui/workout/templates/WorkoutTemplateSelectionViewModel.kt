package com.example.liftrix.ui.workout.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for workout template selection screen.
 * 
 * Manages:
 * - Loading and displaying workout templates
 * - Search functionality with real-time filtering
 * - Tag and difficulty filtering
 * - Template metadata aggregation
 * 
 * Follows established patterns from existing ViewModels with StateFlow for UI state,
 * user authentication integration, and comprehensive error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutTemplateSelectionViewModel @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedDifficulty = MutableStateFlow<Int?>(null)

    private val currentUser = authRepository.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val allTemplates = currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            workoutTemplateRepository.getAllTemplatesForUser(user.uid)
        }
        .catch { e ->
            Timber.e(e, "Failed to load templates")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val filteredTemplates = combine(
        allTemplates,
        _searchQuery,
        _selectedTags,
        _selectedDifficulty
    ) { templates, searchQuery, selectedTags, selectedDifficulty ->
        filterTemplates(templates, searchQuery, selectedTags, selectedDifficulty)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val availableTags = allTemplates
        .map { templates ->
            templates.flatMap { it.tags }.distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState = combine(
        filteredTemplates,
        availableTags,
        _searchQuery,
        _selectedTags,
        _selectedDifficulty
    ) { filtered, tags, searchQuery, selectedTags, selectedDifficulty ->
        WorkoutTemplateSelectionUiState(
            allTemplates = allTemplates.value,
            filteredTemplates = filtered,
            availableTags = tags,
            searchQuery = searchQuery,
            selectedTags = selectedTags,
            selectedDifficulty = selectedDifficulty,
            isLoading = currentUser.value == null,
            errorMessage = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WorkoutTemplateSelectionUiState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleTag(tag: String) {
        _selectedTags.value = if (_selectedTags.value.contains(tag)) {
            _selectedTags.value - tag
        } else {
            _selectedTags.value + tag
        }
    }

    fun selectDifficulty(difficulty: Int?) {
        _selectedDifficulty.value = difficulty
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedTags.value = emptySet()
        _selectedDifficulty.value = null
    }

    private fun filterTemplates(
        templates: List<WorkoutTemplate>,
        searchQuery: String,
        selectedTags: Set<String>,
        selectedDifficulty: Int?
    ): List<WorkoutTemplate> {
        return templates.filter { template ->
            // Search query filter
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                template.name.contains(searchQuery, ignoreCase = true) ||
                template.description?.contains(searchQuery, ignoreCase = true) == true ||
                template.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }

            // Tags filter
            val matchesTags = if (selectedTags.isEmpty()) {
                true
            } else {
                selectedTags.all { selectedTag ->
                    template.tags.contains(selectedTag)
                }
            }

            // Difficulty filter
            val matchesDifficulty = if (selectedDifficulty == null) {
                true
            } else {
                template.difficultyLevel == selectedDifficulty
            }

            matchesSearch && matchesTags && matchesDifficulty
        }
    }
}

/**
 * UI state for workout template selection screen
 */
data class WorkoutTemplateSelectionUiState(
    val allTemplates: List<WorkoutTemplate> = emptyList(),
    val filteredTemplates: List<WorkoutTemplate> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedTags: Set<String> = emptySet(),
    val selectedDifficulty: Int? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val hasActiveFilters: Boolean
        get() = searchQuery.isNotBlank() || selectedTags.isNotEmpty() || selectedDifficulty != null
}