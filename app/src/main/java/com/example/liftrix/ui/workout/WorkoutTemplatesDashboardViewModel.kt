package com.example.liftrix.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for WorkoutTemplatesDashboard
 */
sealed class WorkoutTemplatesDashboardUiState {
    object Loading : WorkoutTemplatesDashboardUiState()
    data class Success(
        val templates: List<WorkoutTemplate> = emptyList(),
        val filteredTemplates: List<WorkoutTemplate> = emptyList(),
        val searchQuery: String = ""
    ) : WorkoutTemplatesDashboardUiState()
    data class Error(val message: String) : WorkoutTemplatesDashboardUiState()
}

/**
 * ViewModel for the Workout Templates Dashboard.
 * Simple ViewModel for folder-based navigation without template management.
 */
@HiltViewModel
class WorkoutTemplatesDashboardViewModel @Inject constructor(
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val workoutTemplateRepository: WorkoutTemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutTemplatesDashboardUiState>(
        WorkoutTemplatesDashboardUiState.Loading
    )
    val uiState: StateFlow<WorkoutTemplatesDashboardUiState> = _uiState.asStateFlow()

    init {
        loadTemplates()
    }

    /**
     * Load all templates for the current user
     */
    private fun loadTemplates() {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase() ?: return@launch
                _uiState.value = WorkoutTemplatesDashboardUiState.Loading
                
                workoutTemplateRepository.getAllTemplatesForUser(userId).collect { templates ->
                    _uiState.value = WorkoutTemplatesDashboardUiState.Success(
                        templates = templates,
                        filteredTemplates = templates
                    )
                }
            } catch (exception: Exception) {
                _uiState.value = WorkoutTemplatesDashboardUiState.Error(
                    "Failed to load templates: ${exception.message}"
                )
                Timber.e(exception, "Error loading templates")
            }
        }
    }

    /**
     * Search templates by query with immediate UI update
     */
    fun searchTemplates(query: String) {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplatesDashboardUiState.Success) {
            // Immediately update search query to prevent text input issues
            _uiState.value = currentState.copy(searchQuery = query)
            
            // Then perform filtering
            val filteredTemplates = if (query.isBlank()) {
                currentState.templates
            } else {
                currentState.templates.filter { template ->
                    template.name.contains(query, ignoreCase = true) ||
                    template.description?.contains(query, ignoreCase = true) == true
                }
            }
            _uiState.value = currentState.copy(
                filteredTemplates = filteredTemplates,
                searchQuery = query
            )
        }
    }

    /**
     * Clear search and show all templates
     */
    fun clearSearch() {
        val currentState = _uiState.value
        if (currentState is WorkoutTemplatesDashboardUiState.Success) {
            _uiState.value = currentState.copy(
                filteredTemplates = currentState.templates,
                searchQuery = ""
            )
        }
    }





    /**
     * Records template usage when starting a workout
     */
    fun recordTemplateUsage(template: WorkoutTemplate) {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase() ?: return@launch
                workoutTemplateRepository.recordTemplateUsage(template.id, userId)
                Timber.i("Template usage recorded: ${template.name}")
            } catch (exception: Exception) {
                Timber.e(exception, "Error recording template usage")
            }
        }
    }


}

