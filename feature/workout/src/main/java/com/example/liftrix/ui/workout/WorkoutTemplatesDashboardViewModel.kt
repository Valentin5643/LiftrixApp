package com.example.liftrix.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.interactor.auth.AuthInteractor
import com.example.liftrix.domain.interactor.workout.TemplateInteractor
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.repository.template.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for WorkoutTemplatesDashboard
 * 🔥 ENHANCED: Added folder filtering support
 */
sealed class WorkoutTemplatesDashboardUiState {
    object WaitingForAuth : WorkoutTemplatesDashboardUiState()
    object Loading : WorkoutTemplatesDashboardUiState()
    data class Success(
        val templates: List<WorkoutTemplate> = emptyList(),
        val filteredTemplates: List<WorkoutTemplate> = emptyList(),
        val searchQuery: String = "",
        val selectedFolderId: String? = null, // 🔥 NEW: Track selected folder
        val isShowingFolderContents: Boolean = false // 🔥 NEW: Track folder view mode
    ) : WorkoutTemplatesDashboardUiState()
    data class Error(val message: String) : WorkoutTemplatesDashboardUiState()
}

/**
 * ViewModel for the Workout Templates Dashboard.
 * 🔥 ENHANCED: Now supports folder-based template filtering using optimized database queries.
 */
@HiltViewModel
class WorkoutTemplatesDashboardViewModel @Inject constructor(
    private val authInteractor: AuthInteractor,
    private val workoutTemplateRepository: TemplateRepository,
    private val templateInteractor: TemplateInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<WorkoutTemplatesDashboardUiState>(
        WorkoutTemplatesDashboardUiState.WaitingForAuth
    )
    val uiState: StateFlow<WorkoutTemplatesDashboardUiState> = _uiState.asStateFlow()

    init {
        loadTemplatesWhenAuthenticated()
    }

    /**
     * Load templates once user is authenticated
     */
    private fun loadTemplatesWhenAuthenticated() {
        viewModelScope.launch {
            try {
                _uiState.value = WorkoutTemplatesDashboardUiState.Loading

                // Wait for authentication to complete, then load templates
                val userId = authInteractor.currentUser(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.e(error, "Failed to get current user ID")
                        _uiState.value = WorkoutTemplatesDashboardUiState.Error("Authentication failed")
                        return@launch
                    }
                )
                Timber.d("User authenticated, loading templates for user: $userId")
                
                workoutTemplateRepository.getAllTemplatesForUser(userId.value).collect { result ->
                    if (result.isSuccess) {
                        val templates = result.getOrElse { emptyList() }
                        _uiState.value = WorkoutTemplatesDashboardUiState.Success(
                            templates = templates,
                            filteredTemplates = templates,
                            selectedFolderId = null, // 🔥 FIXED: Initialize folder fields
                            isShowingFolderContents = false
                        )
                        Timber.d("Successfully loaded ${templates.size} templates for user $userId")
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to load templates"
                        _uiState.value = WorkoutTemplatesDashboardUiState.Error(errorMessage)
                        Timber.e("Failed to load templates: $errorMessage")
                    }
                }
            } catch (exception: Exception) {
                val errorMessage = "Authentication or template loading failed: ${exception.message}"
                _uiState.value = WorkoutTemplatesDashboardUiState.Error(errorMessage)
                Timber.e(exception, "Error in loadTemplatesWhenAuthenticated")
            }
        }
    }

    /**
     * 🔥 NEW: Load templates for a specific folder using optimized database query
     */
    fun loadTemplatesForFolder(folderId: String) {
        viewModelScope.launch {
            try {
                Timber.d("🔥 FOLDER-DASHBOARD: Loading templates for folder: $folderId")
                _uiState.value = WorkoutTemplatesDashboardUiState.Loading

                val userId = authInteractor.currentUser(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.e(error, "Failed to get current user ID")
                        _uiState.value = WorkoutTemplatesDashboardUiState.Error("Authentication failed")
                        return@launch
                    }
                )

                // Use repository directly for folder-specific templates
                workoutTemplateRepository.getTemplatesByFolder(userId.value, folderId).collect { result ->
                    if (result.isSuccess) {
                        val templates = result.getOrThrow()

                        _uiState.value = WorkoutTemplatesDashboardUiState.Success(
                            templates = templates,
                            filteredTemplates = templates,
                            selectedFolderId = folderId,
                            isShowingFolderContents = true
                        )
                        Timber.d("🔥 FOLDER-DASHBOARD: Successfully loaded ${templates.size} templates for folder $folderId")
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Failed to load folder templates"
                        _uiState.value = WorkoutTemplatesDashboardUiState.Error(errorMessage)
                        Timber.e("Failed to load templates for folder $folderId: $errorMessage")
                    }
                }
            } catch (exception: Exception) {
                val errorMessage = "Error loading folder templates: ${exception.message}"
                _uiState.value = WorkoutTemplatesDashboardUiState.Error(errorMessage)
                Timber.e(exception, "Error in loadTemplatesForFolder")
            }
        }
    }
    
    /**
     * 🔥 NEW: Return to showing all templates (exit folder view)
     */
    fun showAllTemplates() {
        loadTemplatesWhenAuthenticated()
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
                val userId = authInteractor.currentUser(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { return@launch }
                )
                workoutTemplateRepository.recordTemplateUsage(template.id, userId.value)
                Timber.i("Template usage recorded: ${template.name}")
            } catch (exception: Exception) {
                Timber.e(exception, "Error recording template usage")
            }
        }
    }


}
