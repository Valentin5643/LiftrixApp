package com.example.liftrix.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.template.DuplicateWorkoutTemplateUseCase
import com.example.liftrix.domain.usecase.template.DeleteWorkoutTemplateUseCase
import com.example.liftrix.domain.usecase.template.GetWorkoutTemplatesUseCase
import com.example.liftrix.domain.usecase.template.ToggleTemplateFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Workout Templates Dashboard.
 * Manages template display, filtering, search, and template operations.
 */
@HiltViewModel
class WorkoutTemplatesDashboardViewModel @Inject constructor(
    private val getWorkoutTemplatesUseCase: GetWorkoutTemplatesUseCase,
    private val duplicateWorkoutTemplateUseCase: DuplicateWorkoutTemplateUseCase,
    private val deleteWorkoutTemplateUseCase: DeleteWorkoutTemplateUseCase,
    private val toggleTemplateFavoriteUseCase: ToggleTemplateFavoriteUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val workoutTemplateRepository: WorkoutTemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutTemplatesDashboardUiState>(
        WorkoutTemplatesDashboardUiState.Loading
    )
    val uiState: StateFlow<WorkoutTemplatesDashboardUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val _allTemplates = MutableStateFlow<List<WorkoutTemplate>>(emptyList())

    init {
        loadTemplates()
        setupFilteredTemplates()
    }

    /**
     * Sets up reactive filtering based on search and filter state
     */
    private fun setupFilteredTemplates() {
        viewModelScope.launch {
            combine(
                _allTemplates,
                _searchQuery
            ) { templates, query ->
                filterTemplates(templates, query)
            }.catch { error ->
                Timber.e(error, "Error filtering templates")
                _uiState.value = WorkoutTemplatesDashboardUiState.Error(
                    error.message ?: "Unknown error occurred"
                )
            }.collect { filteredTemplates ->
                _uiState.value = if (filteredTemplates.isEmpty() && _allTemplates.value.isEmpty()) {
                    WorkoutTemplatesDashboardUiState.Empty
                } else {
                    WorkoutTemplatesDashboardUiState.Success(filteredTemplates)
                }
            }
        }
    }

    /**
     * Loads workout templates for the current user
     */
    private fun loadTemplates() {
        viewModelScope.launch {
            try {
                _uiState.value = WorkoutTemplatesDashboardUiState.Loading
                
                val userId = getCurrentUserIdUseCase() ?: throw IllegalStateException("User not authenticated")
                
                getWorkoutTemplatesUseCase(userId)
                    .catch { error ->
                        Timber.e(error, "Error loading templates")
                        _uiState.value = WorkoutTemplatesDashboardUiState.Error(
                            error.message ?: "Failed to load templates"
                        )
                    }
                    .collect { templates ->
                        _allTemplates.value = templates
                    }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadTemplates")
                _uiState.value = WorkoutTemplatesDashboardUiState.Error(
                    exception.message ?: "Failed to load templates"
                )
            }
        }
    }

    /**
     * Applies search filter to templates
     */
    fun searchTemplates(query: String) {
        _searchQuery.value = query.trim()
    }

    /**
     * Clears the search query
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }


    /**
     * Duplicates a workout template
     */
    fun duplicateTemplate(template: WorkoutTemplate) {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserIdUseCase() ?: return@launch
                
                duplicateWorkoutTemplateUseCase(
                    originalTemplate = template,
                    newName = "${template.name} (Copy)"
                )
                
                // Refresh templates to show the new duplicate
                loadTemplates()
                
                Timber.i("Template duplicated successfully: ${template.name}")
            } catch (exception: Exception) {
                Timber.e(exception, "Error duplicating template")
                // TODO: Show error message to user
            }
        }
    }

    /**
     * Deletes a workout template
     */
    fun deleteTemplate(template: WorkoutTemplate) {
        viewModelScope.launch {
            try {
                deleteWorkoutTemplateUseCase(template.id)
                
                // Refresh templates to remove the deleted one
                loadTemplates()
                
                Timber.i("Template deleted successfully: ${template.name}")
            } catch (exception: Exception) {
                Timber.e(exception, "Error deleting template")
                // TODO: Show error message to user
            }
        }
    }

    /**
     * Toggles favorite status of a template
     */
    fun toggleFavorite(template: WorkoutTemplate) {
        viewModelScope.launch {
            try {
                toggleTemplateFavoriteUseCase(template.id)
                
                // Refresh templates to update favorite status
                loadTemplates()
                
                Timber.i("Template favorite toggled: ${template.name}")
            } catch (exception: Exception) {
                Timber.e(exception, "Error toggling template favorite")
                // TODO: Show error message to user
            }
        }
    }

    /**
     * Retries loading templates after an error
     */
    fun retry() {
        loadTemplates()
    }

    /**
     * Filters templates based on search query
     */
    private fun filterTemplates(
        templates: List<WorkoutTemplate>,
        query: String
    ): List<WorkoutTemplate> {
        var filtered = templates

        // Apply search filter
        if (query.isNotBlank()) {
            filtered = filtered.filter { template ->
                template.name.contains(query, ignoreCase = true) ||
                template.description?.contains(query, ignoreCase = true) == true ||
                template.tags.any { tag -> tag.contains(query, ignoreCase = true) } ||
                template.exercises.any { exercise -> 
                    exercise.name.contains(query, ignoreCase = true)
                }
            }
        }


        // Return templates in simple database order (no custom sorting)
        return filtered
    }

    /**
     * Gets template statistics for analytics
     */
    fun getTemplateStatistics(): TemplateStatistics {
        val templates = _allTemplates.value
        return TemplateStatistics(
            totalTemplates = templates.size,
            totalUsage = templates.sumOf { it.usageCount },
            avgExercisesPerTemplate = if (templates.isNotEmpty()) {
                templates.map { it.exercises.size }.average()
            } else 0.0,
            mostUsedTemplate = templates.maxByOrNull { it.usageCount },
            recentlyCreated = templates.filter { template ->
                val now = System.currentTimeMillis()
                val weekAgo = now - (7 * 24 * 60 * 60 * 1000)
                template.createdAt.toEpochMilli() > weekAgo
            }.size
        )
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
                // Don't show error to user as this is background operation
            }
        }
    }
}

/**
 * UI state for the Workout Templates Dashboard
 */
sealed class WorkoutTemplatesDashboardUiState {
    object Loading : WorkoutTemplatesDashboardUiState()
    object Empty : WorkoutTemplatesDashboardUiState()
    
    data class Success(
        val filteredTemplates: List<WorkoutTemplate>
    ) : WorkoutTemplatesDashboardUiState()
    
    data class Error(
        val error: String
    ) : WorkoutTemplatesDashboardUiState()
}

/**
 * Template statistics for analytics
 */
data class TemplateStatistics(
    val totalTemplates: Int,
    val totalUsage: Int,
    val avgExercisesPerTemplate: Double,
    val mostUsedTemplate: WorkoutTemplate?,
    val recentlyCreated: Int
)