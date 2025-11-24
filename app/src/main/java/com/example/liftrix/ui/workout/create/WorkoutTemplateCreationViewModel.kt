package com.example.liftrix.ui.workout.create

import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.WorkoutTemplateCreationUiState
import com.example.liftrix.ui.common.state.WorkoutTemplateCreationData
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.common.event.ViewModelEvent
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseDefaults
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.model.WorkoutTemplateId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the workout creation screen.
 * 
 * Manages the state for creating a new workout routine,
 * including exercise selection, workout metadata, and
 * the workout routine creation process.
 * 
 * Uses BaseViewModel<S,E> pattern with workflow-based terminology
 * for user-friendly language ("Creating a workout" vs technical "template").
 */
@HiltViewModel
class WorkoutTemplateCreationViewModel @Inject constructor(
    private val templateCommandUseCase: TemplateCommandUseCase,
    private val authQueryUseCase: AuthQueryUseCase,
    private val exerciseQueryUseCase: ExerciseQueryUseCase,
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository
) : ModernBaseViewModel<WorkoutTemplateCreationUiState>(
    initialState = WorkoutTemplateCreationUiState.Loading
) {
    
    private val _loadedTemplate = MutableStateFlow<WorkoutTemplate?>(null)
    val loadedTemplate: StateFlow<WorkoutTemplate?> = _loadedTemplate.asStateFlow()
    
    // Navigation event flow for handling navigation after operations
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents = _navigationEvents.asSharedFlow()
    
    // 🔥 CRITICAL FIX: Backup state to prevent data loss during state transitions
    private var cachedExercises = mutableListOf<TemplateExercise>()
    private var cachedTemplateData: WorkoutTemplateCreationData? = null
    
    init {
        initializeWhenAuthenticated()
    }

    /**
     * Handles events from the UI following MVI pattern
     */
    fun handleEvent(event: WorkoutTemplateCreationEvent) {
        when (event) {
            is WorkoutTemplateCreationEvent.CreateWorkout -> createWorkout(event.name, event.description, event.exercises, event.tags)
            is WorkoutTemplateCreationEvent.UpdateTemplate -> updateTemplate(event.templateId, event.name, event.description, event.exercises, event.tags)
            is WorkoutTemplateCreationEvent.AddExercise -> addExercise(event.exercise)
            is WorkoutTemplateCreationEvent.AddExerciseFromSelector -> addExerciseFromSelector(event.exercise)
            is WorkoutTemplateCreationEvent.AddExerciseById -> addExerciseById(event.exerciseId, event.isCustomExercise)
            is WorkoutTemplateCreationEvent.AddExerciseFromLibrary -> addExerciseFromLibrary(event.exerciseLibrary)
            is WorkoutTemplateCreationEvent.RemoveExercise -> removeExercise(event.exercise)
            is WorkoutTemplateCreationEvent.ReorderExercises -> reorderExercises(event.fromIndex, event.toIndex)
            is WorkoutTemplateCreationEvent.UpdateExercise -> updateExercise(event.exercise)
            is WorkoutTemplateCreationEvent.SearchExercises -> onExerciseSearchQueryChanged(event.query)
            is WorkoutTemplateCreationEvent.SelectExercise -> onExerciseSelected(event.exercise)
            is WorkoutTemplateCreationEvent.ExpandSelector -> onExerciseSelectorExpandedChanged(event.expanded)
            is WorkoutTemplateCreationEvent.LoadTemplate -> loadTemplateForEditing(event.templateId)
            WorkoutTemplateCreationEvent.ResetToEditing -> resetToEditing()
            is WorkoutTemplateCreationEvent.SelectFolder -> selectFolder(event.folderId)
            WorkoutTemplateCreationEvent.LoadFolders -> loadFolders()
            WorkoutTemplateCreationEvent.NavigateToFolderSelection -> onNavigateToFolderSelection()
        }
    }

    /**
     * Handle error state updates
     */
    private fun updateErrorState(error: com.example.liftrix.domain.model.error.LiftrixError) {
        val currentData = uiState.value.dataOrNull()
        val preservedData = currentData ?: cachedTemplateData ?: WorkoutTemplateCreationData()

        updateState {
            WorkoutTemplateCreationUiState.Error(
                error = error,
                previousData = preservedData
            )
        }
    }

    /**
     * Handle error with logging
     */
    private fun handleError(error: com.example.liftrix.domain.model.error.LiftrixError) {
        logError(error, "WorkoutTemplateCreation")
        updateErrorState(error)
    }
    
    /**
     * 🔥 CRITICAL FIX: Custom setState with backup data before state changes
     */
    private fun setStateWithBackup(state: WorkoutTemplateCreationUiState) {
        // Backup current data before state transition
        val currentData = uiState.value.dataOrNull()
        if (currentData != null && currentData.exercises.isNotEmpty()) {
            cachedTemplateData = currentData
            cachedExercises.clear()
            cachedExercises.addAll(currentData.exercises)
            Timber.d("🔥 STATE-BACKUP: Cached ${cachedExercises.size} exercises before state transition")
        }

        updateState { state }

        // Restore exercises from backup if they were lost during state transition
        val newData = state.dataOrNull()
        if (newData != null && newData.exercises.isEmpty() && cachedExercises.isNotEmpty()) {
            Timber.d("🔥 STATE-RESTORE: Restoring ${cachedExercises.size} exercises from cache")
            val restoredState = WorkoutTemplateCreationUiState.Success(
                data = newData.copy(exercises = cachedExercises.toList())
            )
            updateState { restoredState }
        }
    }
    
    /**
     * Initialize ViewModel after authentication completes
     * 🔥 FIXED: Enhanced error handling for folder initialization issues
     */
    private fun initializeWhenAuthenticated() {
        viewModelScope.launch {
            try {
                // Wait for authentication to complete
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.e(error, "Failed to get current user ID")
                        val authError = error as? LiftrixError ?: LiftrixError.AuthenticationError(
                            errorMessage = "Failed to get current user ID",
                            errorCode = "AUTH_QUERY_FAILED"
                        )
                        updateState { WorkoutTemplateCreationUiState.Error(authError) }
                        return@launch
                    }
                )
                Timber.d("🔥 INIT-DEBUG: User authenticated, initializing template creation for user: $userId")
                
                // Transition to success state with initial data
                updateState {
                    WorkoutTemplateCreationUiState.Success(
                        data = WorkoutTemplateCreationData(
                            exercises = emptyList(),
                            availableExercises = emptyList(),
                            exerciseSearchQuery = "",
                            selectedExercise = null,
                            isExerciseSelectorExpanded = false,
                            availableFolders = emptyList(),
                            selectedFolderId = null,
                            defaultFolderId = null,
                            templateName = "",
                            templateDescription = "",
                            targetFolderId = null
                        )
                    )
                }
                Timber.d("🔥 INIT-DEBUG: Initial state set successfully")
                
                // 🔥 CRITICAL FIX: Initialize components synchronously to prevent state races
                Timber.d("🔥 SYNC-INIT: Starting synchronous initialization")
                
                // Initialize folder first and wait for completion
                val defaultFolderId = initializeDefaultFolderSync()
                Timber.d("🔥 SYNC-INIT: Default folder initialized: $defaultFolderId")
                
                // Initialize exercises and wait for completion
                val availableExercises = initializeAvailableExercisesSync()
                Timber.d("🔥 SYNC-INIT: Available exercises initialized: ${availableExercises.size}")
                
                // Update state with all initialized data at once
                val finalData = WorkoutTemplateCreationData(
                    exercises = emptyList(),
                    availableExercises = availableExercises,
                    exerciseSearchQuery = "",
                    selectedExercise = null,
                    isExerciseSelectorExpanded = false,
                    availableFolders = emptyList(), // Will be populated by loadFolders
                    selectedFolderId = defaultFolderId,
                    defaultFolderId = defaultFolderId,
                    templateName = "",
                    templateDescription = "",
                    targetFolderId = null
                )
                
                setStateWithBackup(WorkoutTemplateCreationUiState.Success(data = finalData))
                Timber.d("🔥 SYNC-INIT: Final state set with selectedFolderId: ${defaultFolderId?.value}")
                
                // 🔥 CRITICAL FIX: Verify state was actually set correctly
                kotlinx.coroutines.delay(100) // Small delay to ensure state propagation
                val verificationData = uiState.value.dataOrNull()
                Timber.d("🔥 STATE-VERIFY: Post-init verification - defaultFolderId: ${verificationData?.defaultFolderId?.value}, selectedFolderId: ${verificationData?.selectedFolderId?.value}")
                Timber.d("🔥 STATE-VERIFY: Post-init verification - availableExercises: ${verificationData?.availableExercises?.size}, exercises: ${verificationData?.exercises?.size}")
                
                if (verificationData?.defaultFolderId == null && defaultFolderId != null) {
                    Timber.w("🔥 STATE-VERIFY: State not properly set, retrying...")
                    // Retry state setting if it failed
                    setStateWithBackup(WorkoutTemplateCreationUiState.Success(data = finalData))
                    kotlinx.coroutines.delay(50)
                    val retryData = uiState.value.dataOrNull()
                    Timber.d("🔥 STATE-VERIFY: Retry verification - defaultFolderId: ${retryData?.defaultFolderId?.value}")
                }
                
                // Load folder list asynchronously (this won't affect core functionality)
                initializeFolders()
                
                Timber.d("🔥 INIT-DEBUG: All initialization steps completed")
            } catch (exception: Exception) {
                val error = com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                    errorMessage = "Authentication failed: ${exception.message}"
                )
                updateState { WorkoutTemplateCreationUiState.Error(error) }
                Timber.e(exception, "🔥 INIT-DEBUG: Error in initializeWhenAuthenticated")
            }
        }
    }
    
    /**
     * Initialize available exercises with enhanced error handling
     * 🔥 FIXED: Separate initialization with specific error logging
     */
    private fun initializeAvailableExercises() {
        viewModelScope.launch {
            try {
                Timber.d("🔥 INIT-DEBUG: Starting exercise initialization")
                loadAvailableExercises()
                Timber.d("🔥 INIT-DEBUG: Exercise initialization completed")
            } catch (e: Exception) {
                Timber.e(e, "🔥 INIT-DEBUG: Failed to initialize available exercises - continuing anyway")
                // Don't fail entire initialization for exercise loading issues
            }
        }
    }
    
    /**
     * Initialize default folder with enhanced error handling and fallbacks
     * 🔥 FIXED: Comprehensive error handling for folder creation issues
     */
    private fun initializeDefaultFolder() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = {
                        Timber.e("🔥 INIT-DEBUG: Failed to get userId for folder init")
                        return@launch
                    }
                )

                val result = folderRepository.getOrCreateDefaultFolder(userId)
                if (result.isSuccess) {
                    val defaultFolder = result.getOrNull()

                    // Update state with default folder
                    val currentData = uiState.value.dataOrNull()
                    if (currentData != null && defaultFolder != null) {
                        val updatedData = currentData.copy(
                            defaultFolderId = defaultFolder.id,
                            selectedFolderId = currentData.selectedFolderId ?: defaultFolder.id
                        )
                        updateState { WorkoutTemplateCreationUiState.Success(data = updatedData) }

                        // Verify the state was actually set
                        kotlinx.coroutines.delay(50) // Small delay to let state propagate
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    Timber.e(exception, "Failed to create default folder for user $userId")
                    
                    when {
                        exception?.message?.contains("FOREIGN KEY constraint", true) == true -> {
                            // Continue without default folder - user can select folder manually
                        }
                        exception?.message?.contains("profile setup", true) == true -> {
                            // Don't fail initialization, just log and continue
                        }
                        else -> {
                            Timber.e("Unexpected folder creation error: ${exception?.message}")
                        }
                    }
                    
                    // Don't create phantom fallback folders
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in initializeDefaultFolder")
                // Don't create phantom fallback folders on exception
                // Let UI handle the case where no folders are available
            }
        }
    }
    
    /**
     * Synchronous version of default folder initialization with enhanced error handling
     * 🔥 ENHANCED: Robust folder creation with multiple fallback strategies
     */
    private suspend fun initializeDefaultFolderSync(): com.example.liftrix.domain.model.FolderId? {
        return try {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = {
                    Timber.e("🔥 FOLDER-INIT: Failed to get userId")
                    return null
                }
            )
            Timber.d("🔥 FOLDER-INIT: Starting default folder initialization for user: $userId")
            
            // Strategy 1: Try to get existing folders first (faster and safer)
            val existingFolders = try {
                folderRepository.getAllFoldersForUser(userId).first()
            } catch (e: Exception) {
                Timber.w(e, "🔥 FOLDER-INIT: Failed to query existing folders, will try creation")
                emptyList()
            }
            
            // If folders already exist, use the default or first one
            val existingDefault = existingFolders.firstOrNull { it.isDefault() } ?: existingFolders.firstOrNull()
            if (existingDefault != null) {
                Timber.d("🔥 FOLDER-INIT: Using existing folder: ${existingDefault.id.value}")
                return existingDefault.id
            }
            
            // Strategy 2: Try to create default folder with retry logic
            Timber.d("🔥 FOLDER-INIT: No existing folders, attempting to create default folder")
            var createAttempts = 0
            val maxAttempts = 3
            
            while (createAttempts < maxAttempts) {
                createAttempts++
                val userIdForFolder = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = {
                        Timber.e("🔥 FOLDER-INIT: Failed to get userId in retry loop")
                        return null
                    }
                )
                val result = folderRepository.getOrCreateDefaultFolder(userIdForFolder)
                
                if (result.isSuccess) {
                    val defaultFolder = result.getOrNull()
                    Timber.d("🔥 FOLDER-INIT: Successfully created default folder on attempt $createAttempts: ${defaultFolder?.id?.value}")
                    return defaultFolder?.id
                }
                
                val exception = result.exceptionOrNull()
                val errorMessage = exception?.message ?: "Unknown error"
                
                Timber.w("🔥 FOLDER-INIT: Attempt $createAttempts failed: $errorMessage")
                
                // Strategy 3: Handle specific error cases
                when {
                    errorMessage.contains("profile setup", ignoreCase = true) -> {
                        Timber.d("🔥 FOLDER-INIT: User profile not complete, trying with short delay")
                        kotlinx.coroutines.delay(500L * createAttempts) // Exponential backoff
                    }
                    errorMessage.contains("FOREIGN KEY", ignoreCase = true) -> {
                        Timber.d("🔥 FOLDER-INIT: FK constraint issue, retrying with delay")
                        kotlinx.coroutines.delay(300L * createAttempts)
                    }
                    errorMessage.contains("already exists", ignoreCase = true) -> {
                        Timber.d("🔥 FOLDER-INIT: Folder already exists, re-querying")
                        // Re-query folders since one apparently exists now
                        val retryFolders = try {
                            folderRepository.getAllFoldersForUser(userId).first()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val retryDefault = retryFolders.firstOrNull { it.isDefault() } ?: retryFolders.firstOrNull()
                        if (retryDefault != null) {
                            return retryDefault.id
                        }
                    }
                    else -> {
                        Timber.w("🔥 FOLDER-INIT: Unexpected error: $errorMessage")
                        kotlinx.coroutines.delay(200L * createAttempts)
                    }
                }
            }
            
            // Strategy 4: Final fallback - allow the system to continue without a default folder
            Timber.w("🔥 FOLDER-INIT: Failed to create default folder after $maxAttempts attempts")
            Timber.w("🔥 FOLDER-INIT: Template creation will prompt user to create a folder")
            null
            
        } catch (e: Exception) {
            Timber.e(e, "🔥 FOLDER-INIT: Exception in default folder initialization")
            null
        }
    }
    
    /**
     * Synchronous version of available exercises initialization
     * 🔥 CRITICAL FIX: Returns exercises directly to prevent state race conditions
     */
    private suspend fun initializeAvailableExercisesSync(): List<com.example.liftrix.domain.usecase.exercise.SearchableExercise> {
        return try {
            val exercises = exerciseQueryUseCase.search("", Equipment.entries.toSet()).first()
            exercises
        } catch (e: Exception) {
            Timber.e(e, "Failed to load exercises, returning empty list")
            emptyList()
        }
    }
    
    /**
     * Initialize folders list with enhanced error handling
     * 🔥 FIXED: Separate folder list loading with error resilience
     */
    private fun initializeFolders() {
        viewModelScope.launch {
            try {
                Timber.d("🔥 INIT-DEBUG: Starting folder list initialization")
                loadFolders()
                Timber.d("🔥 INIT-DEBUG: Folder list initialization completed")
            } catch (e: Exception) {
                Timber.e(e, "🔥 INIT-DEBUG: Failed to initialize folder list - continuing anyway")
                // Don't fail entire initialization for folder list issues
            }
        }
    }
    
    /**
     * Legacy method - kept for backward compatibility
     * @deprecated Use initializeDefaultFolder() instead
     */
    @Deprecated("Use initializeDefaultFolder() instead", ReplaceWith("initializeDefaultFolder()"))
    private fun ensureDefaultFolderExists() {
        initializeDefaultFolder()
    }
    
    /**
     * Loads available exercises for selection
     */
    private fun loadAvailableExercises() {
        viewModelScope.launch {
            exerciseQueryUseCase.search("", Equipment.entries.toSet())
                .catch { error ->
                    Timber.e(error, "Error loading available exercises")
                }
                .collect { exercises ->
                    val currentData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
                    updateState {
                        WorkoutTemplateCreationUiState.Success(
                            data = currentData.copy(availableExercises = exercises)
                        )
                    }
                }
        }
    }

    /**
     * Creates a new workout routine with the specified parameters
     */
    fun createWorkout(
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        // 🔥 FIXED: Don't preserve context - we want UI to show the newly created workout
        createTemplate(name, description, exercises, tags)
    }
    
    /**
     * 🔥 NEW: Creates a workout in a specific folder with explicit folder context
     * This method preserves folder context and prevents default folder fallback
     */
    fun createWorkoutInFolder(
        folderId: String,
        name: String = "",
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList()
    ) {
        Timber.d("🔥 CREATE-IN-FOLDER: Creating workout in folder: $folderId")
        
        // Set the target folder context in state first
        val currentData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
        val targetFolderId = com.example.liftrix.domain.model.FolderId(folderId)
        
        setStateWithBackup(WorkoutTemplateCreationUiState.Success(
            data = currentData.copy(
                targetFolderId = targetFolderId,
                templateName = name,
                templateDescription = description ?: "",
                exercises = exercises
            )
        ))
        
        // If we have enough data, create immediately, otherwise let user fill in details
        if (name.isNotBlank()) {
            createTemplate(name, description, exercises)
        } else {
            Timber.d("🔥 CREATE-IN-FOLDER: Template name empty, waiting for user input")
        }
    }
    
    /**
     * Creates a new workout routine with the specified parameters (backend compatibility)
     * @deprecated Use createWorkout() instead for user-friendly workflow terminology
     */
    @Deprecated("Use createWorkout() instead", ReplaceWith("createWorkout(name, description, exercises, tags)"))
    fun createTemplate(
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.e("🔥 CREATE-DEBUG: Failed to get userId: $error")
                        throw IllegalStateException("Authentication failed")
                    }
                )
                val currentData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
                
                // 🔥 CRITICAL FIX: Use exercises from parameter, UI state, or cached backup
                val exercisesToSave = when {
                    exercises.isNotEmpty() -> {
                        Timber.d("🔥 CREATE-DEBUG: Using ${exercises.size} exercises from parameter")
                        exercises
                    }
                    currentData.exercises.isNotEmpty() -> {
                        Timber.d("🔥 CREATE-DEBUG: Using ${currentData.exercises.size} exercises from UI state")
                        currentData.exercises
                    }
                    cachedExercises.isNotEmpty() -> {
                        Timber.d("🔥 CREATE-DEBUG: Using ${cachedExercises.size} exercises from cache backup")
                        cachedExercises.toList()
                    }
                    else -> {
                        Timber.w("🔥 CREATE-DEBUG: No exercises available from any source")
                        emptyList()
                    }
                }
                Timber.d("🔥 CREATE-DEBUG: Creating template with ${exercisesToSave.size} exercises")
                
                // 🔥 ENHANCED: Use template metadata from ViewModel state
                val templateName = if (name.isNotBlank()) name else currentData.templateName
                val templateDescription = description ?: currentData.templateDescription.takeIf { it.isNotBlank() }
                
                Timber.d("🔥 CREATE-DEBUG: Using templateName='$templateName' from ${if (name.isNotBlank()) "parameter" else "ViewModel state"}")
                Timber.d("🔥 CREATE-DEBUG: Using templateDescription='$templateDescription'")
                
                // 🔥 ENHANCED: Robust folder resolution with multiple fallback strategies
                timber.log.Timber.d("🔥 FOLDER-RESOLUTION: Starting folder resolution")
                timber.log.Timber.d("🔥 FOLDER-RESOLUTION: currentData.targetFolderId=${currentData.targetFolderId?.value}")
                timber.log.Timber.d("🔥 FOLDER-RESOLUTION: currentData.selectedFolderId=${currentData.selectedFolderId?.value}")
                timber.log.Timber.d("🔥 FOLDER-RESOLUTION: currentData.defaultFolderId=${currentData.defaultFolderId?.value}")
                timber.log.Timber.d("🔥 FOLDER-RESOLUTION: currentData.effectiveFolderId=${currentData.effectiveFolderId?.value}")
                
                val effectiveFolderId = currentData.effectiveFolderId?.value 
                    ?: run {
                        Timber.d("🔥 CREATE-DEBUG: No folder selected, resolving folder for template creation")
                        
                        try {
                            // Strategy 1: Check for existing folders first
                            val userIdForFolder = authQueryUseCase(waitForAuth = false).fold(
                                onSuccess = { it },
                                onFailure = {
                                    Timber.e("🔥 CREATE-DEBUG: Failed to get userId for folder resolution")
                                    throw IllegalStateException("Authentication failed")
                                }
                            )
                            val existingFolders = folderRepository.getAllFoldersForUser(userIdForFolder).first()
                            
                            if (existingFolders.isNotEmpty()) {
                                // Use default folder or first available folder
                                val selectedFolder = existingFolders.firstOrNull { it.isDefault() } 
                                    ?: existingFolders.first()
                                Timber.d("🔥 CREATE-DEBUG: Using existing folder: ${selectedFolder.id.value}")
                                selectedFolder.id.value
                            } else {
                                // Strategy 2: Try to create a default folder with retries
                                Timber.d("🔥 CREATE-DEBUG: No existing folders, creating default folder")
                                
                                var createAttempts = 0
                                val maxAttempts = 2
                                
                                while (createAttempts < maxAttempts) {
                                    createAttempts++
                                    val userIdForCreate = authQueryUseCase(waitForAuth = false).fold(
                                        onSuccess = { it },
                                        onFailure = {
                                            Timber.e("🔥 CREATE-DEBUG: Failed to get userId for folder creation")
                                            throw IllegalStateException("Authentication failed")
                                        }
                                    )
                                    val defaultResult = folderRepository.getOrCreateDefaultFolder(userIdForCreate)
                                    
                                    if (defaultResult.isSuccess) {
                                        val defaultFolder = defaultResult.getOrNull()
                                        Timber.d("🔥 CREATE-DEBUG: Created default folder on attempt $createAttempts: ${defaultFolder?.id?.value}")
                                        return@run defaultFolder?.id?.value 
                                            ?: throw IllegalStateException("Default folder creation returned null")
                                    }
                                    
                                    val exception = defaultResult.exceptionOrNull()
                                    val errorMessage = exception?.message ?: "Unknown error"
                                    Timber.w("🔥 CREATE-DEBUG: Folder creation attempt $createAttempts failed: $errorMessage")
                                    
                                    if (createAttempts < maxAttempts) {
                                        kotlinx.coroutines.delay(300L)
                                    }
                                }
                                
                                // Strategy 3: Final fallback - create a temporary folder or show user-friendly error
                                val finalError = "Unable to create a folder for your template. Please try creating a folder manually first, then create your template."
                                Timber.e("🔥 CREATE-DEBUG: $finalError")
                                throw IllegalStateException(finalError)
                            }
                        } catch (e: Exception) {
                            when {
                                e.message?.contains("Please try creating a folder manually") == true -> {
                                    // Re-throw user-friendly errors as-is
                                    throw e
                                }
                                else -> {
                                    Timber.e(e, "🔥 CREATE-DEBUG: Unexpected error during folder resolution")
                                    throw IllegalStateException("There was an issue setting up your workout folders. Please try again in a moment.", e)
                                }
                            }
                        }
                    }
                
                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: About to call TemplateCommandUseCase.create()")
                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Final parameters - userId: $userId, name: $templateName, folderId: $effectiveFolderId, exercises: ${exercisesToSave.size}")

                val result = templateCommandUseCase.create(
                    userId = userId,
                    name = templateName,
                    folderId = effectiveFolderId,
                    description = templateDescription,
                    exercises = exercisesToSave
                )

                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: TemplateCommandUseCase.create() completed")
                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Result isSuccess: ${result.isSuccess}")
                timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Result isFailure: ${result.isFailure}")

                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    timber.log.Timber.e("🔥 TEMPLATE-VIEWMODEL-DEBUG: TemplateCommandUseCase.create() returned failure: ${error?.message}")
                    timber.log.Timber.e("🔥 TEMPLATE-VIEWMODEL-DEBUG: Error type: ${error?.javaClass?.simpleName}")

                    val liftrixError = error as? LiftrixError ?: LiftrixError.UnknownError(
                        errorMessage = error?.message ?: "Failed to create workout routine"
                    )
                    Timber.e("Failed to create workout routine: ${liftrixError.message}")
                    handleError(liftrixError)
                    return@launch
                }

                val template = result.getOrNull()!!

                // 🔥 STATE SYNC FIX: Update selectedFolderId to match where workout was created
                val successData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
                val createdFolderId = template.folderId?.let { com.example.liftrix.domain.model.FolderId(it) }

                Timber.d("🔥 SUCCESS-DEBUG: Template created successfully: ${template.name}")
                Timber.d("🔥 SUCCESS-DEBUG: Template assigned to folder: ${template.folderId}")
                Timber.d("🔥 SUCCESS-DEBUG: Current available folders: ${successData.availableFolders.size}")
                Timber.d("🔥 SUCCESS-DEBUG: Preserving all state data in success callback")

                setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                    data = successData.copy(
                        template = template,
                        exercises = template.exercises,
                        selectedFolderId = createdFolderId, // 🔥 KEY FIX: Sync UI to show newly created workout
                        // 🔥 CRITICAL FIX: Preserve all existing state data
                        availableExercises = successData.availableExercises,
                        availableFolders = successData.availableFolders, // Keep folders visible!
                        defaultFolderId = successData.defaultFolderId,
                        exerciseSearchQuery = successData.exerciseSearchQuery,
                        selectedExercise = successData.selectedExercise,
                        isExerciseSelectorExpanded = successData.isExerciseSelectorExpanded
                    )
                ))

                Timber.i("🔥 STATE-SYNC: Workout created in folder ${template.folderId}, updated selectedFolderId to match")
                Timber.i("🔥 STATE-SYNC: Preserved ${successData.availableFolders.size} folders in state")
                Timber.i("Workout routine created successfully: ${template.name} in folder ${template.folderId}")

                // Emit navigation event to go back after successful creation
                _navigationEvents.emit(NavigationEvent.NavigateBack)
            } catch (e: Exception) {
                val liftrixError = e as? LiftrixError ?: LiftrixError.UnknownError(
                    errorMessage = e.message ?: "Failed to create workout routine"
                )
                Timber.e("Failed to create workout routine: ${liftrixError.message}")
                handleError(liftrixError)
            }
        }
    }
    
    /**
     * Updates an existing workout routine with the specified parameters
     */
    fun updateTemplate(
        templateId: String,
        name: String,
        description: String? = null,
        exercises: List<TemplateExercise> = emptyList(),
        tags: Set<String> = emptySet()
    ) {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        logError(error, "updateTemplate_auth")
                        val authError = error as? LiftrixError ?: LiftrixError.AuthenticationError(
                            errorMessage = "Authentication failed",
                            errorCode = "AUTH_FAILED"
                        )
                        updateState { WorkoutTemplateCreationUiState.Error(authError) }
                        return@launch
                    }
                )

                val loadedTemplate = _loadedTemplate.value
                if (loadedTemplate == null) {
                    val error = LiftrixError.ValidationError(
                        field = "template",
                        violations = listOf("No template loaded for editing")
                    )
                    logError(error, "updateTemplate_noTemplate")
                    updateState { WorkoutTemplateCreationUiState.Error(error) }
                    return@launch
                }

                // Create updated workout routine preserving metadata
                val updatedTemplate = loadedTemplate.copy(
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotBlank() },
                    exercises = exercises.mapIndexed { index, exercise ->
                        exercise.copy(
                            orderIndex = index,
                            instanceId = exercise.instanceId // Preserve instanceId
                        )
                    },
                    updatedAt = java.time.Instant.now()
                )

                val result = workoutTemplateRepository.updateTemplate(updatedTemplate)

                result.onSuccess { template ->
                    // 🔥 CRITICAL FIX: Preserve existing state data during template update
                    val currentData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
                    setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                        data = currentData.copy(
                            template = template,
                            exercises = template.exercises // Update with saved template exercises
                        )
                    ))
                    Timber.i("Workout routine updated successfully: ${template.name}")

                    // Emit navigation event to go back after successful update
                    _navigationEvents.emit(NavigationEvent.NavigateBack)
                }.onFailure { error ->
                    logError(error, "updateTemplate")
                    val liftrixError = error as? LiftrixError ?: LiftrixError.UnknownError(
                        errorMessage = error.message ?: "Failed to update workout routine"
                    )
                    updateState { WorkoutTemplateCreationUiState.Error(liftrixError) }
                }
            } catch (e: Exception) {
                logError(e, "updateTemplate_exception")
                val error = LiftrixError.UnknownError(
                    errorMessage = e.message ?: "Failed to update workout routine"
                )
                updateState { WorkoutTemplateCreationUiState.Error(error) }
            }
        }
    }
    
    /**
     * Adds an exercise to the workout routine
     */
    fun addExercise(exercise: TemplateExercise) {
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Adding exercise: ${exercise.name}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Exercise instanceId: ${exercise.instanceId}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Exercise targetSets: ${exercise.targetSets}")
        val currentData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Current exercises before add: ${currentData.exercises.size}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Current exercises instanceIds: ${currentData.exercises.map { "${it.name}: ${it.instanceId}" }}")

        val newExercise = exercise.copy(
            orderIndex = currentData.exercises.size,
            instanceId = exercise.instanceId // Explicitly preserve instanceId
        )
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: New exercise instanceId after copy: ${newExercise.instanceId}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: New exercise targetSets after copy: ${newExercise.targetSets}")

        val updatedExercises = currentData.exercises + newExercise
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: Updated exercises after add: ${updatedExercises.size}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: All exercise instanceIds: ${updatedExercises.map { "${it.name}: ${it.instanceId}" }}")

        // Use backup-aware setState method for consistency
        setStateWithBackup(WorkoutTemplateCreationUiState.Success(
            data = currentData.copy(exercises = updatedExercises)
        ))
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: State update completed using setState()")

        // Verify state was set correctly
        val verifyData = uiState.value.dataOrNull()
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: State verification - exercises count: ${verifyData?.exercises?.size}")
        timber.log.Timber.d("🔥 ADD-EXERCISE-DEBUG: State verification - exercises list: ${verifyData?.exercises?.map { it.name }}")
    }
    
    /**
     * Adds an exercise from the search selector to the workout routine
     */
    fun addExerciseFromSelector(searchableExercise: SearchableExercise) {
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Adding exercise from selector")
        val templateExercise = convertSearchableExerciseToTemplateExercise(searchableExercise)
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Converted to template exercise: ${templateExercise.name}")

        addExercise(templateExercise)
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: addExercise completed")

        // Clear selection after adding - but preserve exercises!
        val currentData = uiState.value.dataOrNull()
        timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Current data after addExercise: exercises=${currentData?.exercises?.size}")
        if (currentData != null) {
            // Use backup-aware setState method for consistency
            setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(
                    selectedExercise = null,
                    exerciseSearchQuery = "",
                    isExerciseSelectorExpanded = false
                )
            ))
            timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: State cleared using setState(), exercises preserved: ${currentData.exercises.size}")

            // Final verification
            val finalData = uiState.value.dataOrNull()
            timber.log.Timber.d("🔥 ADD-FROM-SELECTOR-DEBUG: Final verification - exercises count: ${finalData?.exercises?.size}")
        }
    }
    
    /**
     * Adds an exercise by ID (supports both library and custom exercises)
     */
    fun addExerciseById(exerciseId: String, isCustomExercise: Boolean) {
        viewModelScope.launch {
            try {
                val exercises = exerciseQueryUseCase.search("", Equipment.entries.toSet()).first()
                val searchableExercise = exercises.find { searchable ->
                    when (searchable) {
                        is SearchableExercise.LibraryExercise -> !isCustomExercise && searchable.exercise.id == exerciseId
                        is SearchableExercise.CustomExercise -> isCustomExercise && searchable.exercise.id.value == exerciseId
                    }
                }
                
                searchableExercise?.let { exercise ->
                    addExerciseFromSelector(exercise)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to add exercise by ID: $exerciseId")
                val error = com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                    errorMessage = "Failed to add exercise: ${e.message}"
                )
                handleError(error)
            }
        }
    }
    
    /**
     * Adds a custom exercise by ID
     */
    fun addCustomExercise(exerciseId: String) {
        addExerciseById(exerciseId, isCustomExercise = true)
    }
    
    /**
     * Adds an exercise from the exercise library to the workout routine
     */
    fun addExerciseFromLibrary(exerciseLibrary: ExerciseLibrary) {
        timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: addExerciseFromLibrary called")
        timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Exercise: ${exerciseLibrary.name} (ID: ${exerciseLibrary.id})")
        
        try {
            val templateExercise = convertExerciseLibraryToTemplateExercise(exerciseLibrary)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Converted to TemplateExercise: ${templateExercise.name}")
            
            // Add the exercise to state first
            addExercise(templateExercise)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: addExercise completed successfully")
            
            // Apply smart defaults AFTER the exercise is added to state
            applySmartDefaults(templateExercise, exerciseLibrary)
            timber.log.Timber.d("🔥 TEMPLATE-VIEWMODEL-DEBUG: Smart defaults application started")
            
            // Debug current state
            val currentData = uiState.value.dataOrNull()
            if (currentData != null) {
                timber.log.Timber.d("🔥 WORKOUT-VIEWMODEL-DEBUG: Current workout routine exercises count: ${currentData.exercises.size}")
                timber.log.Timber.d("🔥 WORKOUT-VIEWMODEL-DEBUG: Workout routine exercises: ${currentData.exercises.map { it.name }}")
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e(e, "🔥 TEMPLATE-VIEWMODEL-DEBUG: Error in addExerciseFromLibrary")
        }
    }
    
    /**
     * Converts ExerciseLibrary to TemplateExercise with smart defaults applied
     * Fixed to prevent race conditions by deferring smart defaults until after exercise is added
     */
    private fun convertExerciseLibraryToTemplateExercise(exerciseLibrary: ExerciseLibrary): TemplateExercise {
        val baseExercise = TemplateExercise(
            exerciseId = ExerciseId.fromString(exerciseLibrary.id),
            name = exerciseLibrary.name,
            primaryMuscle = exerciseLibrary.primaryMuscleGroup,
            equipment = exerciseLibrary.equipment,
            targetSets = null,
            targetReps = null,
            targetWeight = null,
            restTimeSeconds = null,
            notes = null,
            orderIndex = 0, // Will be updated when added
            isCustomExercise = false,
            customExerciseId = null
        )
        
        timber.log.Timber.d("🔥 CONVERT-DEBUG: Created base exercise ${baseExercise.name}, will apply defaults after adding to state")
        
        return baseExercise
    }
    
    /**
     * Applies smart defaults to a template exercise based on user history and exercise type
     * Fixed to prevent race conditions by ensuring exercise exists before applying defaults
     */
    private fun applySmartDefaults(templateExercise: TemplateExercise, exerciseLibrary: ExerciseLibrary) {
        viewModelScope.launch {
            try {
                // Add a small delay to ensure the exercise is properly added to state first
                kotlinx.coroutines.delay(50)

                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.w("Failed to get userId for smart defaults: $error")
                        return@launch
                    }
                )

                timber.log.Timber.d("🔥 SMART-DEFAULTS-DEBUG: Starting smart defaults for ${exerciseLibrary.name}")

                // Verify the exercise still exists in the current state before applying defaults
                val currentData = uiState.value.dataOrNull()
                val exerciseExists = currentData?.exercises?.any { it.exerciseId == templateExercise.exerciseId } == true
                
                if (!exerciseExists) {
                    timber.log.Timber.w("🔥 SMART-DEFAULTS-DEBUG: Exercise ${exerciseLibrary.name} no longer exists in state, skipping defaults")
                    return@launch
                }
                
                val defaultsResult = exerciseQueryUseCase.getExerciseDefaults(
                    exerciseId = templateExercise.exerciseId,
                    userId = userId,
                    exerciseLibrary = exerciseLibrary
                )
                
                defaultsResult.fold(
                    onSuccess = { defaults ->
                        updateExerciseWithDefaults(templateExercise.exerciseId, defaults)
                        updateEstimatedDuration()
                        Timber.d("Applied smart defaults for ${exerciseLibrary.name}: ${defaults.getSourceDescription()}")
                    },
                    onFailure = { error ->
                        Timber.w(error, "Failed to get smart defaults for ${exerciseLibrary.name}")
                        // Exercise will keep the null defaults
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error applying smart defaults for ${exerciseLibrary.name}")
            }
        }
    }
    
    /**
     * Updates an exercise with smart defaults
     * Fixed to prevent race conditions with concurrent state updates
     */
    private fun updateExerciseWithDefaults(exerciseId: ExerciseId, defaults: ExerciseDefaults) {
        timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Updating exercise ${exerciseId.value} with defaults")
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Current exercises before defaults: ${currentData.exercises.size}")
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Exercise names: ${currentData.exercises.map { it.name }}")

            val updatedExercises = currentData.exercises.map { exercise ->
                if (exercise.exerciseId == exerciseId) {
                    timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Applying defaults to ${exercise.name}")
                    defaults.applyToTemplateExercise(exercise)
                } else {
                    exercise
                }
            }

            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Updated exercises after defaults: ${updatedExercises.size}")
            setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exercises = updatedExercises)
            ))

            // Verify the update didn't lose exercises
            val verifyData = uiState.value.dataOrNull()
            timber.log.Timber.d("🔥 DEFAULTS-DEBUG: Verification - exercises count: ${verifyData?.exercises?.size}")
        } else {
            timber.log.Timber.w("🔥 DEFAULTS-DEBUG: No current data available for defaults update")
        }
    }
    
    /**
     * Updates the estimated duration for the current template
     */
    private fun updateEstimatedDuration() {
        viewModelScope.launch {
            try {
                val currentData = uiState.value.dataOrNull()
                if (currentData != null && currentData.exercises.isNotEmpty()) {
                    val userId = authQueryUseCase(waitForAuth = false).fold(
                        onSuccess = { it },
                        onFailure = { error ->
                            Timber.w("Failed to get userId for duration estimation: $error")
                            return@launch
                        }
                    )

                    // Create a temporary template for duration estimation
                    val tempTemplate = WorkoutTemplate.create(
                        userId = userId,
                        name = "Temporary",
                        folderId = "temp",
                        exercises = currentData.exercises
                    )
                    
                    val durationResult = workoutQueryUseCase.estimateDuration(tempTemplate)
                    durationResult.fold(
                        onSuccess = { duration ->
                            val minutes = duration.toMinutes().toInt()
                            Timber.d("Updated estimated duration: ${minutes} minutes")
                            // Could update UI state with estimated duration if needed
                        },
                        onFailure = { error ->
                            Timber.w(error, "Failed to estimate workout duration")
                        }
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating estimated duration")
            }
        }
    }
    
    /**
     * Converts SearchableExercise to TemplateExercise
     */
    private fun convertSearchableExerciseToTemplateExercise(searchableExercise: SearchableExercise): TemplateExercise {
        return when (searchableExercise) {
            is SearchableExercise.LibraryExercise -> {
                val exercise = searchableExercise.exercise
                TemplateExercise(
                    exerciseId = ExerciseId.fromString(exercise.id),
                    name = exercise.name,
                    primaryMuscle = exercise.primaryMuscleGroup,
                    equipment = exercise.equipment,
                    targetSets = null,
                    targetReps = null,
                    targetWeight = null,
                    restTimeSeconds = null,
                    notes = null,
                    orderIndex = 0, // Will be updated when added
                    isCustomExercise = false,
                    customExerciseId = null
                )
            }
            is SearchableExercise.CustomExercise -> {
                val exercise = searchableExercise.exercise
                TemplateExercise(
                    exerciseId = ExerciseId.fromString(exercise.id.value),
                    name = exercise.name,
                    primaryMuscle = exercise.primaryMuscle,
                    equipment = exercise.equipment,
                    targetSets = null,
                    targetReps = null,
                    targetWeight = null,
                    restTimeSeconds = null,
                    notes = exercise.notes,
                    orderIndex = 0, // Will be updated when added
                    isCustomExercise = true,
                    customExerciseId = exercise.id
                )
            }
        }
    }
    
    /**
     * Updates exercise search query
     */
    fun onExerciseSearchQueryChanged(query: String) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            updateState {
                WorkoutTemplateCreationUiState.Success(
                    data = currentData.copy(exerciseSearchQuery = query)
                )
            }

            // Perform search
            viewModelScope.launch {
                exerciseQueryUseCase.search(query, Equipment.entries.toSet())
                    .catch { error ->
                        Timber.e(error, "Error searching exercises")
                    }
                    .collect { exercises ->
                        val updatedData = uiState.value.dataOrNull()
                        if (updatedData != null) {
                            updateState {
                                WorkoutTemplateCreationUiState.Success(
                                    data = updatedData.copy(availableExercises = exercises)
                                )
                            }
                        }
                    }
            }
        }
    }
    
    /**
     * Updates selected exercise
     */
    fun onExerciseSelected(exercise: SearchableExercise?) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            updateState {
                WorkoutTemplateCreationUiState.Success(
                    data = currentData.copy(selectedExercise = exercise)
                )
            }
        }
    }
    
    /**
     * 🔥 NEW: Updates template name in ViewModel state
     */
    fun updateTemplateName(name: String) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(templateName = name)
            ))
            Timber.d("🔥 TEMPLATE-NAME: Updated template name to: '$name'")
        }
    }
    
    /**
     * 🔥 NEW: Updates template description in ViewModel state
     */
    fun updateTemplateDescription(description: String) {
        val currentData = _uiState.value.dataOrNull()
        if (currentData != null) {
            setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(templateDescription = description)
            ))
            Timber.d("🔥 TEMPLATE-DESC: Updated template description")
        }
    }
    
    /**
     * Updates exercise selector expanded state
     */
    fun onExerciseSelectorExpandedChanged(expanded: Boolean) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            updateState {
                WorkoutTemplateCreationUiState.Success(
                    data = currentData.copy(isExerciseSelectorExpanded = expanded)
                )
            }
        }
    }
    
    /**
     * Removes an exercise from the template
     */
    fun removeExercise(exercise: TemplateExercise) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.exercises
                .filter { it.instanceId != exercise.instanceId }
                .mapIndexed { index, ex -> ex.copy(orderIndex = index) }

            // Update cache to prevent data loss
            cachedExercises.clear()
            cachedExercises.addAll(updatedExercises)

            setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exercises = updatedExercises)
            ))
            Timber.d("🔥 REMOVE-DEBUG: Removed exercise ${exercise.name}, ${updatedExercises.size} exercises remaining")
        }
    }
    
    /**
     * Reorders exercises in the template
     */
    fun reorderExercises(fromIndex: Int, toIndex: Int) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            val exercises = currentData.exercises.toMutableList()
            if (fromIndex in exercises.indices && toIndex in exercises.indices) {
                val item = exercises.removeAt(fromIndex)
                exercises.add(toIndex, item)
                val reorderedExercises = exercises.mapIndexed { index, exercise ->
                    exercise.copy(
                        orderIndex = index,
                        instanceId = exercise.instanceId // Preserve instanceId
                    )
                }

                // Update cache to prevent data loss
                cachedExercises.clear()
                cachedExercises.addAll(reorderedExercises)

                setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                    data = currentData.copy(exercises = reorderedExercises)
                ))
                Timber.d("🔥 REORDER-DEBUG: Reordered exercises from $fromIndex to $toIndex")
            }
        }
    }
    
    /**
     * Updates an exercise in the template
     */
    fun updateExercise(updatedExercise: TemplateExercise) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            val updatedExercises = currentData.exercises.map { exercise ->
                // Use instanceId for unique identification instead of just exerciseId
                // This allows multiple instances of the same exercise to be updated independently
                if (exercise.instanceId == updatedExercise.instanceId) {
                    updatedExercise
                } else {
                    exercise
                }
            }

            // Update cache to prevent data loss
            cachedExercises.clear()
            cachedExercises.addAll(updatedExercises)

            setStateWithBackup(WorkoutTemplateCreationUiState.Success(
                data = currentData.copy(exercises = updatedExercises)
            ))
            Timber.d("🔥 UPDATE-DEBUG: Updated exercise ${updatedExercise.name}")
        }
    }
    
    /**
     * Resets the creation state back to editing
     */
    fun resetToEditing() {
        val currentData = uiState.value.dataOrNull()
        val exercises = currentData?.exercises ?: emptyList()

        updateState {
            WorkoutTemplateCreationUiState.Success(
                data = WorkoutTemplateCreationData(
                    exercises = exercises,
                    availableExercises = emptyList(),
                    exerciseSearchQuery = "",
                    selectedExercise = null,
                    isExerciseSelectorExpanded = false,
                    availableFolders = currentData?.availableFolders ?: emptyList(),
                    selectedFolderId = currentData?.selectedFolderId,
                    defaultFolderId = currentData?.defaultFolderId
                )
            )
        }
        loadAvailableExercises()
    }
    
    /**
     * Validates if the current template state is valid for creation
     * 🔥 FIXED: Use WorkoutTemplateCreationData validation logic (allows empty templates)
     */
    fun isValidForCreation(name: String): Boolean {
        val currentData = uiState.value.dataOrNull()
        return currentData?.isValidForCreation(name) == true
    }
    
    /**
     * Loads an existing template for editing
     */
    fun loadTemplateForEditing(templateId: String) {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.e("Failed to get userId for loading template: $error")
                        val authError = com.example.liftrix.domain.model.error.LiftrixError.AuthenticationError(
                            errorMessage = "Authentication failed"
                        )
                        handleError(authError)
                        return@launch
                    }
                )
                val templateResult = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId(templateId),
                    userId
                )
                
                if (templateResult.isSuccess) {
                    val template = templateResult.getOrNull()
                    if (template != null) {
                        _loadedTemplate.value = template
                        val currentData = uiState.value.dataOrNull()
                        updateState {
                            WorkoutTemplateCreationUiState.Success(
                                data = WorkoutTemplateCreationData(
                                    exercises = template.exercises,
                                    availableExercises = currentData?.availableExercises ?: emptyList(),
                                    exerciseSearchQuery = "",
                                    selectedExercise = null,
                                    isExerciseSelectorExpanded = false,
                                    availableFolders = currentData?.availableFolders ?: emptyList(),
                                    selectedFolderId = template.folderId?.let { com.example.liftrix.domain.model.FolderId(it) },
                                    defaultFolderId = currentData?.defaultFolderId
                                )
                            )
                        }
                    } else {
                        val error = com.example.liftrix.domain.model.error.LiftrixError.NotFoundError(
                            errorMessage = "Template not found",
                            resourceType = "template",
                            resourceId = templateId
                        )
                        handleError(error)
                    }
                } else {
                    val error = com.example.liftrix.domain.model.error.LiftrixError.DataRetrievalError(
                        errorMessage = templateResult.exceptionOrNull()?.message ?: "Failed to load template"
                    )
                    handleError(error)
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error loading template for editing")
                val error = com.example.liftrix.domain.model.error.LiftrixError.UnknownError(
                    errorMessage = exception.message ?: "Failed to load template"
                )
                handleError(error)
            }
        }
    }
    
    // 🔥 CRITICAL FIX: Add job management to prevent cancellation issues
    private var folderLoadingJob: kotlinx.coroutines.Job? = null
    
    // Removed: preserveUserFolderContext - simplified approach with direct state sync
    
    /**
     * Loads available folders for folder selection
     * 🔥 FIXED: Enhanced error handling and job management to prevent initialization failures
     */
    fun loadFolders() {
        // Cancel existing job to prevent conflicts
        folderLoadingJob?.cancel()

        folderLoadingJob = viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        Timber.e("🔥 FOLDERS-DEBUG: Failed to get userId: $error")
                        return@launch
                    }
                )
                Timber.d("🔥 FOLDERS-DEBUG: Starting folder loading for user: $userId")

                folderRepository.getAllFoldersForUser(userId)
                    .catch { error ->
                        Timber.e(error, "🔥 FOLDERS-DEBUG: Error in folder flow - continuing with empty list")
                        // Emit empty list instead of crashing
                        emit(emptyList())
                    }
                    .collect { folders ->
                        // Check if job was cancelled before updating state
                        if (!isActive) {
                            Timber.d("🔥 FOLDERS-DEBUG: Job cancelled, skipping state update")
                            return@collect
                        }

                        val currentData = uiState.value.dataOrNull() ?: WorkoutTemplateCreationData()
                        val defaultFolder = folders.firstOrNull { it.isDefault() }
                        
                        Timber.d("🔥 FOLDERS-DEBUG: Loaded ${folders.size} folders, default: ${defaultFolder?.id?.value}")
                        
                        // 🔥 FIXED: True folder context preservation - only reset when absolutely necessary
                        val preservedDefaultId = when {
                            currentData.defaultFolderId == null -> {
                                val realDefault = defaultFolder ?: folders.firstOrNull()
                                Timber.d("🔥 FOLDERS-DEBUG: No defaultFolderId, setting: ${realDefault?.id?.value}")
                                realDefault?.id
                            }
                            // Always preserve existing defaultFolderId if it exists in database
                            folders.any { it.id == currentData.defaultFolderId } -> {
                                Timber.d("🔥 FOLDERS-DEBUG: Preserving valid defaultFolderId: ${currentData.defaultFolderId?.value}")
                                currentData.defaultFolderId
                            }
                            // Only fallback if default folder was truly deleted
                            else -> {
                                val realDefault = defaultFolder ?: folders.firstOrNull()
                                Timber.d("🔥 FOLDERS-DEBUG: Default folder ${currentData.defaultFolderId?.value} was deleted, using: ${realDefault?.id?.value}")
                                realDefault?.id
                            }
                        }
                        
                        val preservedSelectedId = when {
                            // If no selection, use default folder
                            currentData.selectedFolderId == null -> {
                                val fallbackSelected = preservedDefaultId ?: folders.firstOrNull()?.id
                                Timber.d("🔥 FOLDERS-DEBUG: No user folder selection, using fallback: ${fallbackSelected?.value}")
                                fallbackSelected
                            }
                            // 🔥 PRESERVE USER SELECTION: Keep user's selection if folder still exists
                            folders.any { it.id == currentData.selectedFolderId } -> {
                                Timber.d("🔥 FOLDERS-DEBUG: PRESERVING user selection: ${currentData.selectedFolderId?.value}")
                                currentData.selectedFolderId
                            }
                            // Only reset if user's selected folder was actually deleted
                            else -> {
                                Timber.d("🔥 FOLDERS-DEBUG: User's selected folder ${currentData.selectedFolderId?.value} was deleted, falling back to default")
                                preservedDefaultId ?: folders.firstOrNull()?.id
                            }
                        }
                        
                        val updatedData = currentData.copy(
                            availableFolders = folders,
                            defaultFolderId = preservedDefaultId,
                            selectedFolderId = preservedSelectedId
                        )

                        updateState { WorkoutTemplateCreationUiState.Success(data = updatedData) }
                        
                        Timber.d("🔥 FOLDERS-DEBUG: Preserved folder IDs - defaultFolderId: ${preservedDefaultId?.value}, selectedFolderId: ${preservedSelectedId?.value}")
                        Timber.d("🔥 FOLDERS-DEBUG: Folder state updated successfully")
                    }
            } catch (exception: kotlinx.coroutines.CancellationException) {
                Timber.d("🔥 FOLDERS-DEBUG: Folder loading job cancelled - this is normal")
                // Don't treat cancellation as an error
            } catch (exception: Exception) {
                Timber.e(exception, "🔥 FOLDERS-DEBUG: Exception loading folders - using graceful fallback")
                
                // 🔥 FIXED: Don't create phantom fallback folders - let UI handle gracefully
                Timber.d("🔥 FOLDERS-DEBUG: Using graceful error handling - no phantom folders")
                val currentData = uiState.value.dataOrNull()
                if (currentData != null) {
                    // Set empty folder list and null IDs - UI should show "create folder" options
                    updateState {
                        WorkoutTemplateCreationUiState.Success(
                            data = currentData.copy(
                                availableFolders = emptyList(),
                                defaultFolderId = null,
                                selectedFolderId = null
                            )
                        )
                    }
                    Timber.d("🔥 FOLDERS-DEBUG: Set empty folder state - UI will handle folder creation")
                }
            }
        }
    }
    
    /**
     * Selects a folder for the template
     */
    fun selectFolder(folderId: com.example.liftrix.domain.model.FolderId) {
        val currentData = uiState.value.dataOrNull()
        if (currentData != null) {
            updateState {
                WorkoutTemplateCreationUiState.Success(
                    data = currentData.copy(selectedFolderId = folderId)
                )
            }
            Timber.d("Selected folder: ${folderId.value}")
        }
    }
    
    /**
     * Handles navigation to folder selection screen
     * This method can be extended to include navigation logic if needed
     */
    fun onNavigateToFolderSelection() {
        // This method is called when user wants to navigate to folder selection
        // The actual navigation will be handled by the Screen composable
        Timber.d("Navigating to folder selection")
    }
}

// WorkoutTemplateCreationData and WorkoutTemplateCreationUiState are now defined in ViewModelState.kt

/**
 * Events for workout creation (updated terminology)
 */
sealed class WorkoutTemplateCreationEvent : ViewModelEvent {
    data class CreateWorkout(
        val name: String,
        val description: String? = null,
        val exercises: List<TemplateExercise> = emptyList(),
        val tags: Set<String> = emptySet()
    ) : WorkoutTemplateCreationEvent()
    
    data class UpdateTemplate(
        val templateId: String,
        val name: String,
        val description: String? = null,
        val exercises: List<TemplateExercise> = emptyList(),
        val tags: Set<String> = emptySet()
    ) : WorkoutTemplateCreationEvent()
    
    data class AddExercise(val exercise: TemplateExercise) : WorkoutTemplateCreationEvent()
    data class AddExerciseFromSelector(val exercise: SearchableExercise) : WorkoutTemplateCreationEvent()
    data class AddExerciseById(val exerciseId: String, val isCustomExercise: Boolean) : WorkoutTemplateCreationEvent()
    data class AddExerciseFromLibrary(val exerciseLibrary: ExerciseLibrary) : WorkoutTemplateCreationEvent()
    data class RemoveExercise(val exercise: TemplateExercise) : WorkoutTemplateCreationEvent()
    data class ReorderExercises(val fromIndex: Int, val toIndex: Int) : WorkoutTemplateCreationEvent()
    data class UpdateExercise(val exercise: TemplateExercise) : WorkoutTemplateCreationEvent()
    data class SearchExercises(val query: String) : WorkoutTemplateCreationEvent()
    data class SelectExercise(val exercise: SearchableExercise?) : WorkoutTemplateCreationEvent()
    data class ExpandSelector(val expanded: Boolean) : WorkoutTemplateCreationEvent()
    data class LoadTemplate(val templateId: String) : WorkoutTemplateCreationEvent()
    object ResetToEditing : WorkoutTemplateCreationEvent()
    
    // Folder Selection Events
    data class SelectFolder(val folderId: com.example.liftrix.domain.model.FolderId) : WorkoutTemplateCreationEvent()
    object LoadFolders : WorkoutTemplateCreationEvent()
    object NavigateToFolderSelection : WorkoutTemplateCreationEvent()
}

/**
 * Navigation events for workout creation flow
 */
sealed class NavigationEvent {
    object NavigateBack : NavigationEvent()
    data class NavigateToWorkout(val workoutId: String) : NavigationEvent()
}