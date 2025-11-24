package com.example.liftrix.ui.workout

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplatePreview
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.usecase.workout.WorkoutCommandUseCase
import com.example.liftrix.domain.usecase.folder.FolderOperationsUseCase
import com.example.liftrix.domain.usecase.analytics.LogWorkoutEventUseCase
import com.example.liftrix.domain.usecase.template.TemplateQueryUseCase
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.analytics.UxMetricsTracker
import com.example.liftrix.analytics.TaskCompletionTracker
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.WorkoutScreenData
import com.example.liftrix.ui.common.state.WorkoutUiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val folderRepository: FolderRepository,
    private val authRepository: AuthRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    private val workoutCommandUseCase: WorkoutCommandUseCase,
    private val folderOperationsUseCase: FolderOperationsUseCase,
    private val templateQueryUseCase: TemplateQueryUseCase,
    private val templateCommandUseCase: TemplateCommandUseCase,
    private val syncManager: SyncManager,
    private val logWorkoutEventUseCase: LogWorkoutEventUseCase,
    private val analyticsService: AnalyticsService,
    private val uxMetricsTracker: UxMetricsTracker,
    private val taskCompletionTracker: TaskCompletionTracker,
    private val sessionManager: com.example.liftrix.service.UnifiedWorkoutSessionManager
) : ModernBaseViewModel<WorkoutUiState>(
    initialState = WorkoutUiState.Loading
) {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        observeAuthState()
        observeWorkouts()
        observeTemplates()
        observeFolders()
        observeSyncStatus()
        
        observeSessionCompletion()
    }

    /**
     * Handles events from the UI following MVI pattern
     */
    fun handleEvent(event: WorkoutEvent) {
        when (event) {
            is WorkoutEvent.StartWorkout -> startWorkout(event.workout)
            is WorkoutEvent.CompleteWorkout -> completeWorkout(event.workout)
            is WorkoutEvent.SaveWorkout -> saveWorkout(event.workout)
            is WorkoutEvent.NavigateToEdit -> {
                // Navigation will be handled by the screen
            }
            is WorkoutEvent.CreateFolder -> createFolder(event.folderName)
            is WorkoutEvent.DeleteFolder -> deleteFolder(event.folder)
            is WorkoutEvent.RenameFolder -> renameFolder(event.folder, event.newName)
            is WorkoutEvent.ReorderFolders -> safeReorderFolders(event.orderedFolderIds)
            is WorkoutEvent.SelectFolder -> selectFolder(event.folderId)
            is WorkoutEvent.MoveWorkout -> moveWorkoutToFolder(event.workoutTemplate, event.targetFolderId)
            WorkoutEvent.ClearError -> clearError()
            WorkoutEvent.RefreshData -> refreshData()
        }
    }




    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _currentUser.value = user
                if (user != null) {
                    Timber.d("User authenticated in WorkoutViewModel: ${user.uid}")
                    analyticsService.setUserProperties(user)
                        .onFailure { exception ->
                            Timber.e(exception, "Failed to set user properties for analytics")
                        }
                } else {
                    Timber.d("User not authenticated in WorkoutViewModel")
                    // Clear workout data when user logs out
                    setState(WorkoutUiState.Success(data = WorkoutScreenData()))
                    // Clear analytics user properties
                    analyticsService.clearUserProperties()
                        .onFailure { exception ->
                            Timber.e(exception, "Failed to clear user properties for analytics")
                        }
                }
            }
        }
    }

    private fun observeWorkouts() {
        viewModelScope.launch {
            // 🔥 FIX: Only restart workout observation when userId actually changes
            // This prevents workouts from "disappearing" after profile updates that recreate User objects
            var previousUserId: String? = null
            
            authRepository.currentUser
                .filterNotNull()
                .collect { user ->
                    // Only restart flow if userId actually changed
                    if (user.uid != previousUserId) {
                        previousUserId = user.uid
                        Timber.d("🔥 WORKOUT-ASSOCIATION-DEBUG: Starting workout observation for userId: ${user.uid}")
                        
                        combine(
                            workoutRepository.getAllWorkoutsForUser(user.uid),
                            syncManager.getSyncStatus()
                        ) { workouts, syncStatus ->
                            Timber.d("🔥 WORKOUT-ASSOCIATION-DEBUG: Workout observation emitted ${workouts.size} workouts for userId: ${user.uid}")
                            val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                            setState(WorkoutUiState.Success(
                                currentData.copy(
                                    workouts = workouts,
                                    syncStatus = syncStatus
                                )
                            ))
                        }.collect { /* Updates handled in combine block */ }
                    } else {
                        Timber.d("🔥 WORKOUT-ASSOCIATION-DEBUG: Skipping workout observation restart - userId unchanged: ${user.uid}")
                    }
                }
        }
    }

    /**
     * 🔥 FIXED: Combined templates and folders observation to prevent race conditions
     * This ensures both templates and folders are loaded together, preventing UI rendering issues
     * where templates can't find their matching folders or vice versa.
     * 
     * FIX for disappearing folders/templates after quick workout completion:
     * Using flatMapLatest to ensure the flow continues observing even after session changes
     */
    private fun observeTemplates() {
        viewModelScope.launch {
            // 🔥 FIX: Only restart template observation when userId actually changes
            // This prevents templates from "disappearing" after profile updates that recreate User objects
            var previousUserId: String? = null
            
            authRepository.currentUser
                .filterNotNull()
                .collect { user ->
                    // Only restart flow if userId actually changed
                    if (user.uid != previousUserId) {
                        previousUserId = user.uid
                        Timber.d("🔥 TEMPLATE-ASSOCIATION-DEBUG: Starting template observation for userId: ${user.uid}")
                        
                        // Ensure default folder exists for the user
                        folderRepository.getOrCreateDefaultFolder(user.uid)
                            .onFailure { exception ->
                                Timber.e(exception, "Failed to create default folder for user ${user.uid}")
                            }
                        
                        // Start combined observation of folders and templates
                        combine(
                            folderOperationsUseCase.invoke(user.uid),
                            templateQueryUseCase(user.uid).map { Result.success(it) }
                        ) { foldersResult, templatesResult ->
                            // Process both results together
                            val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()

                            when {
                                foldersResult.isSuccess && templatesResult.isSuccess -> {
                                    val folders = foldersResult.getOrThrow()
                                    val templates = templatesResult.getOrThrow()

                                    Timber.d("WorkoutViewModel: Loaded ${folders.size} folders and ${templates.size} templates")

                                    setState(WorkoutUiState.Success(
                                        currentData.copy(
                                            folders = folders,
                                            templates = templates,
                                            selectedFolderId = null // Reset folder selection when loading all
                                        )
                                    ))
                                }
                                foldersResult.isFailure -> {
                                    Timber.e("Failed to load folders: ${foldersResult.exceptionOrNull()?.message}")
                                    // Try to load templates only if folders fail
                                    if (templatesResult.isSuccess) {
                                        val templates = templatesResult.getOrThrow()
                                        setState(WorkoutUiState.Success(
                                            currentData.copy(
                                                templates = templates,
                                                folders = emptyList() // Clear folders on failure
                                            )
                                        ))
                                    }
                                }
                                templatesResult.isFailure -> {
                                    Timber.e("Failed to load templates: ${templatesResult.exceptionOrNull()?.message}")
                                    // Load folders only if templates fail
                                    if (foldersResult.isSuccess) {
                                        val folders = foldersResult.getOrThrow()
                                        setState(WorkoutUiState.Success(
                                            currentData.copy(
                                                folders = folders,
                                                templates = emptyList() // Clear templates on failure
                                            )
                                        ))
                                    }
                                }
                            }
                        }.collect { /* Updates handled in combine block */ }
                    } else {
                        Timber.d("🔥 TEMPLATE-ASSOCIATION-DEBUG: Skipping template observation restart - userId unchanged: ${user.uid}")
                    }
                }
        }
    }

    /**
     * 🔥 SIMPLIFIED: observeFolders() is now handled in observeTemplates() via combine()
     * This prevents race conditions between folder and template loading.
     * Left as a stub for any folder-specific operations if needed in the future.
     */
    private fun observeFolders() {
        // This prevents race conditions where templates load before folders or vice versa
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncManager.getSyncStatus().collect { status ->
                val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                setState(WorkoutUiState.Success(
                    currentData.copy(syncStatus = status)
                ))
                
                when (status) {
                    is SyncStatus.Success -> {
                        Timber.d("Sync completed successfully: ${status.syncedCount} workouts synced")
                    }
                    is SyncStatus.Error -> {
                        Timber.e("Sync failed: ${status.message}")
                        val error = com.example.liftrix.domain.model.error.LiftrixError.NetworkError(
                            errorMessage = "Sync failed: ${status.message}"
                        )
                        setState(WorkoutUiState.Error(error, currentData))
                    }
                    else -> { /* Handle other states if needed */ }
                }
            }
        }
    }
    
    /**
     * 🔥 FIX: Observes session completion to refresh data after workout ends
     * This prevents folders/templates from disappearing after quick workout completion
     * by ensuring data is reloaded after cache invalidation
     */
    private fun observeSessionCompletion() {
        viewModelScope.launch {
            var previousSession: com.example.liftrix.domain.model.UnifiedWorkoutSession? = null
            
            sessionManager.currentSession.collect { currentSession ->
                // Detect when session transitions from active/completed to null (cleared after completion)
                if (previousSession != null && currentSession == null) {
                    Timber.d("WorkoutViewModel: Session completed and cleared, refreshing folders/templates data")
                    
                    // Add a small delay to ensure cache invalidation has completed
                    kotlinx.coroutines.delay(200)
                    
                    // Trigger a refresh of folders and templates data
                    refreshData()
                }
                
                previousSession = currentSession
            }
        }
    }

    fun saveWorkout(workout: Workout) {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            // Track previous status for analytics
            val previousStatus = workout.status
            val result = workoutCommandUseCase.saveWorkout(workout)

            // Log analytics events based on workout status changes
            result.onSuccess {
                logWorkoutEventUseCase.logWorkoutStatusChange(workout, previousStatus)
                    .onFailure { exception ->
                        Timber.e(exception, "Failed to log workout analytics event")
                        // Don't fail the save operation if analytics fails
                    }

                Timber.d("Workout saved successfully: ${workout.name}")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Success(currentData)
                }
            }.onFailure { error ->
                Timber.e(error.toString(), "Failed to save workout")
                logError(error, "saveWorkout")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to save workout"
                        ),
                        currentData
                    )
                }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            val userIdResult = authQueryUseCase(waitForAuth = false)
            userIdResult.onSuccess { userId ->
                val result = workoutRepository.syncNowForUser(userId)

                result.onSuccess {
                    Timber.d("Sync started successfully")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState { WorkoutUiState.Success(currentData) }
                }.onFailure { error ->
                    Timber.e("Failed to start sync: ${error.message}")
                    logError(error, "syncNow")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState {
                        WorkoutUiState.Error(
                            error as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = error.message ?: "Failed to start sync"
                            ),
                            currentData
                        )
                    }
                }
            }.onFailure { error ->
                Timber.e("Failed to get user ID: ${error.message}")
                logError(error, "syncNow")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to get user ID"
                        ),
                        currentData
                    )
                }
            }
        }
    }

    fun getUnsyncedCount() {
        viewModelScope.launch {
            // Note: No loading state since showLoading was false in original

            val userIdResult = authQueryUseCase(waitForAuth = false)
            userIdResult.onSuccess { userId ->
                val result = workoutRepository.getUnsyncedCountForUser(userId)

                result.onSuccess { count ->
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState {
                        WorkoutUiState.Success(
                            currentData.copy(unsyncedCount = count)
                        )
                    }
                }.onFailure { error ->
                    Timber.e("Failed to get unsynced count: ${error.message}")
                    logError(error, "getUnsyncedCount")
                }
            }.onFailure { error ->
                Timber.e("Failed to get user ID: ${error.message}")
                logError(error, "getUnsyncedCount")
            }
        }
    }

    fun startWorkout(workout: Workout) {
        // Track workflow start for PRD metrics
        val workflowId = "workout_start_${workout.id.value}_${System.currentTimeMillis()}"
        uxMetricsTracker.startWorkflowTracking(workflowId)
        uxMetricsTracker.trackInteraction(workflowId, "workout_start_button")
        
        // Track task completion for PRD metrics  
        val taskId = "start_task_${workout.id.value}_${System.currentTimeMillis()}"
        taskCompletionTracker.trackTaskStart(taskId, TaskCompletionTracker.TASK_WORKOUT_START)
        
        val startedWorkout = workout.start()
        saveWorkout(startedWorkout)
        
        // Track successful completion
        uxMetricsTracker.completeWorkflowTracking(workflowId, successful = true)
        taskCompletionTracker.trackTaskCompletion(
            taskId, 
            TaskCompletionTracker.TASK_WORKOUT_START,
            com.example.liftrix.analytics.TaskCompletionResult(
                status = com.example.liftrix.analytics.CompletionStatus.SUCCESS,
                completionTime = 1000L, // Quick action
                errorCount = 0,
                retryCount = 0
            )
        )
    }
    
    fun completeWorkout(workout: Workout) {
        val completedWorkout = workout.complete()
        saveWorkout(completedWorkout)
    }
    
    private fun clearError() {
        updateState { currentState ->
            when (currentState) {
                is WorkoutUiState.Error -> WorkoutUiState.Success(currentState.previousData ?: WorkoutScreenData())
                else -> currentState
            }
        }
    }

    private fun refreshData() {
        // Refresh all data by re-observing
        observeWorkouts()
        observeTemplates() // This now triggers combined folders + templates loading
        // observeFolders() - No longer needed, handled in observeTemplates()
        observeSyncStatus()
    }

    /**
     * Select a folder to filter templates (null = show all templates)
     */
    fun selectFolder(folderId: String?) {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = {
                        Timber.e(it, "Failed to get user ID")
                        return@launch
                    }
                )
                loadTemplatesForUser(userId, selectedFolderId = folderId)
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to select folder: $folderId")
            }
        }
    }
    
    /**
     * Load templates with folder filtering - now works with combined loading approach
     * This method is used when users explicitly select a folder to filter templates
     */
    private fun loadTemplatesForUser(userId: String, selectedFolderId: String?) {
        viewModelScope.launch {
            try {
                // Use repository directly for folder filtering
                workoutTemplateRepository.getTemplatesByFolder(userId, selectedFolderId ?: "").collect { result ->
                    result.fold(
                        onSuccess = { templates ->
                            val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()

                            setState(WorkoutUiState.Success(
                                currentData.copy(
                                    templates = templates,
                                    selectedFolderId = selectedFolderId
                                )
                            ))
                        },
                        onFailure = { exception ->
                            Timber.e(exception, "Failed to load filtered templates for user $userId, folder: $selectedFolderId")
                            val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                            setState(WorkoutUiState.Success(
                                currentData.copy(
                                    templates = emptyList(),
                                    selectedFolderId = selectedFolderId
                                )
                            ))
                        }
                    )
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Error in loadTemplatesForUser folder filtering")
            }
        }
    }

    /**
     * Refreshes folder state by directly querying the repository
     */
    private fun refreshFolderState() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it },
                    onFailure = {
                        Timber.e(it, "Failed to get user ID")
                        return@launch
                    }
                )

                // Direct repository access to avoid use case Flow complications
                folderRepository.getAllFoldersForUser(userId)
                    .first() // Get just the first emission
                    .let { folders ->
                        val currentData = _uiState.value.dataOrNull() ?: WorkoutScreenData()
                        setState(WorkoutUiState.Success(
                            currentData.copy(folders = folders)
                        ))
                    }
            } catch (e: Exception) {
                Timber.e(e, "Exception in refreshFolderState")
            }
        }
    }

    fun createFolder(folderName: String) {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            val userIdResult = authQueryUseCase(waitForAuth = false)
            userIdResult.onSuccess { userId ->
                if (userId.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot create folder"
                    )
                    logError(error, "createFolder")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState { WorkoutUiState.Error(error, currentData) }
                    return@launch
                }

                val result = folderOperationsUseCase.create(userId, folderName)

                result.onSuccess { folder ->
                    Timber.d("New folder created: ${folder.name} (${folder.id.value})")

                    // Refresh the combined loading to include the new folder
                    // This ensures the new folder appears alongside existing templates
                    refreshData()
                }.onFailure { error ->
                    Timber.e("Failed to create folder: ${error.message}")
                    logError(error, "createFolder")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState {
                        WorkoutUiState.Error(
                            error as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = error.message ?: "Failed to create folder"
                            ),
                            currentData
                        )
                    }
                }
            }.onFailure { error ->
                Timber.e("Failed to get user ID: ${error.message}")
                logError(error, "createFolder")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to get user ID"
                        ),
                        currentData
                    )
                }
            }
        }
    }

    fun moveWorkoutToFolder(workoutTemplate: com.example.liftrix.domain.model.WorkoutTemplate, targetFolderId: String) {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            val result = templateCommandUseCase.moveToFolder(workoutTemplate, targetFolderId)

            result.onSuccess { updatedTemplate ->
                Timber.d("Workout '${updatedTemplate.name}' moved to folder '$targetFolderId'")

                // Refresh the data to show the workout in its new folder
                refreshData()
            }.onFailure { error ->
                Timber.e("Failed to move workout to folder: ${error.message}")
                logError(error, "moveWorkoutToFolder")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to move workout to folder"
                        ),
                        currentData
                    )
                }
            }
        }
    }

    fun deleteFolder(folder: com.example.liftrix.domain.model.Folder) {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            val userIdResult = authQueryUseCase(waitForAuth = false)
            userIdResult.onSuccess { userId ->
                if (userId.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot delete folder"
                    )
                    logError(error, "deleteFolder")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState { WorkoutUiState.Error(error, currentData) }
                    return@launch
                }

                val result = folderOperationsUseCase.delete(userId, folder.id)

                result.onSuccess {
                    Timber.d("Folder '${folder.name}' deleted successfully")

                    // Refresh the data to remove the deleted folder and show relocated templates
                    refreshData()
                }.onFailure { error ->
                    Timber.e("Failed to delete folder '${folder.name}': ${error.message}")
                    logError(error, "deleteFolder")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState {
                        WorkoutUiState.Error(
                            error as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = error.message ?: "Failed to delete folder"
                            ),
                            currentData
                        )
                    }
                }
            }.onFailure { error ->
                Timber.e("Failed to get user ID: ${error.message}")
                logError(error, "deleteFolder")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to get user ID"
                        ),
                        currentData
                    )
                }
            }
        }
    }

    fun renameFolder(folder: com.example.liftrix.domain.model.Folder, newName: String) {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            val userIdResult = authQueryUseCase(waitForAuth = false)
            userIdResult.onSuccess { userId ->
                if (userId.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot rename folder"
                    )
                    logError(error, "renameFolder")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState { WorkoutUiState.Error(error, currentData) }
                    return@launch
                }

                // Create updated folder with new name
                val updatedFolder = folder.copy(
                    name = com.example.liftrix.domain.model.FolderName(newName.trim())
                )

                val result = folderRepository.updateFolder(updatedFolder)

                result.onSuccess { folder ->
                    Timber.d("Folder '${folder.name}' renamed to '$newName'")

                    // Refresh the data to show the updated folder name
                    refreshData()
                }.onFailure { error ->
                    Timber.e("Failed to rename folder '${folder.name}' to '$newName': ${error.message}")
                    logError(error, "renameFolder")
                    val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                    updateState {
                        WorkoutUiState.Error(
                            error as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = error.message ?: "Failed to rename folder"
                            ),
                            currentData
                        )
                    }
                }
            }.onFailure { error ->
                Timber.e("Failed to get user ID: ${error.message}")
                logError(error, "renameFolder")
                val currentData = uiState.value.dataOrNull() ?: WorkoutScreenData()
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to get user ID"
                        ),
                        currentData
                    )
                }
            }
        }
    }

    /**
     * ✅ RACE CONDITION FIX: Waits for folders to stabilize before reordering
     * This prevents reordering during Loading states caused by parallel operations
     */
    fun safeReorderFolders(orderedFolderIds: List<com.example.liftrix.domain.model.FolderId>) {
        viewModelScope.launch {
            try {
                // ✅ CRITICAL FIX: Wait for folders to stabilize before reordering
                waitForFoldersToStabilize()
                
                val successState = _uiState.value as WorkoutUiState.Success
                
                reorderFoldersWithState(orderedFolderIds, successState)
            } catch (exception: Exception) {
                Timber.e("Safe reorder failed: ${exception.message}")
            }
        }
    }

    /**
     * ✅ STABILIZATION HELPER: Suspends until folders UI state is Success
     * Prevents race conditions with parallel folder operations
     */
    private suspend fun waitForFoldersToStabilize() {
        uiState
            .filter { it is WorkoutUiState.Success }
            .first()
    }

    /**
     * ✅ CACHED STATE REORDER: Uses pre-confirmed Success state to avoid race conditions
     * This eliminates the double state check that was causing "UI state is not Success" errors
     */
    private fun reorderFoldersWithState(
        orderedFolderIds: List<com.example.liftrix.domain.model.FolderId>,
        confirmedSuccessState: WorkoutUiState.Success
    ) {
        viewModelScope.launch {
            updateState { WorkoutUiState.Loading }

            val userIdResult = authQueryUseCase(waitForAuth = false)
            userIdResult.onSuccess { userId ->
                if (userId.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot reorder folders"
                    )
                    logError(error, "reorderFoldersWithState")
                    updateState { WorkoutUiState.Error(error, confirmedSuccessState.data) }
                    return@launch
                }

                // ✅ RACE CONDITION FIX: Use cached state directly, no re-checking UI state
                val currentFolders = confirmedSuccessState.data.folders

                // Defensive validation before calling use case
                if (orderedFolderIds.isEmpty()) {
                    val error = LiftrixError.ValidationError(
                        field = "orderedFolderIds",
                        violations = listOf("Cannot reorder folders: ordered folder IDs list is empty")
                    )
                    logError(error, "reorderFoldersWithState")
                    updateState { WorkoutUiState.Error(error, confirmedSuccessState.data) }
                    return@launch
                }

                if (currentFolders.isEmpty()) {
                    val error = LiftrixError.ValidationError(
                        field = "currentFolders",
                        violations = listOf("Cannot reorder folders: no folders available to reorder")
                    )
                    logError(error, "reorderFoldersWithState")
                    updateState { WorkoutUiState.Error(error, confirmedSuccessState.data) }
                    return@launch
                }

                val result = folderOperationsUseCase.reorder(userId, currentFolders, orderedFolderIds)

                result.onSuccess { reorderedFolders ->
                    // ✅ RACE CONDITION FIX: Use cached state instead of re-checking UI state
                    val updatedData = confirmedSuccessState.data.copy(folders = reorderedFolders)
                    updateState { WorkoutUiState.Success(data = updatedData) }
                }.onFailure { error ->
                    Timber.e("Failed to reorder folders: ${error.message}")
                    logError(error, "reorderFoldersWithState")
                    updateState {
                        WorkoutUiState.Error(
                            error as? LiftrixError ?: LiftrixError.UnknownError(
                                errorMessage = error.message ?: "Folder reorder failed"
                            ),
                            confirmedSuccessState.data
                        )
                    }
                }
            }.onFailure { error ->
                Timber.e("Failed to get user ID: ${error.message}")
                logError(error, "reorderFoldersWithState")
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to get user ID"
                        ),
                        confirmedSuccessState.data
                    )
                }
            }
        }
    }


    
    /**
     * Enhanced UI template preview for workout creation flow
     * Transforms workout data into structured preview format
     */
    val templatePreview: StateFlow<WorkoutTemplatePreview?> = uiState.map { state ->
        when (state) {
            is WorkoutUiState.Success -> {
                state.data.workouts.firstOrNull()?.let { workout ->
                    WorkoutTemplatePreview(
                        name = workout.name,
                        description = workout.notes,
                        exerciseCount = workout.exercises.size,
                        estimatedDuration = workout.getDuration()?.toMinutes()?.toString() + "m" ?: "Unknown",
                        targetMuscleGroups = workout.exercises.map { it.libraryExercise.primaryMuscleGroup.displayName }.distinct(),
                        difficulty = when {
                            workout.exercises.size <= 3 -> "Beginner"
                            workout.exercises.size <= 6 -> "Intermediate" 
                            else -> "Advanced"
                        },
                        lastUsed = null,
                        isPopular = false
                    )
                }
            }
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}

// WorkoutUiState is now defined in ViewModelState.kt as a proper sealed class hierarchy

/**
 * Events that can be triggered from the workout screen UI
 * 
 * Follows the MVI pattern for reactive state management.
 */
sealed class WorkoutEvent : ViewModelEvent {
    data class StartWorkout(val workout: Workout) : WorkoutEvent()
    data class CompleteWorkout(val workout: Workout) : WorkoutEvent()
    data class SaveWorkout(val workout: Workout) : WorkoutEvent()
    data class NavigateToEdit(val workoutId: WorkoutId) : WorkoutEvent()
    data class CreateFolder(val folderName: String) : WorkoutEvent()
    data class DeleteFolder(val folder: com.example.liftrix.domain.model.Folder) : WorkoutEvent()
    data class RenameFolder(val folder: com.example.liftrix.domain.model.Folder, val newName: String) : WorkoutEvent()
    data class ReorderFolders(val orderedFolderIds: List<com.example.liftrix.domain.model.FolderId>) : WorkoutEvent()
    data class SelectFolder(val folderId: String?) : WorkoutEvent()
    data class MoveWorkout(val workoutTemplate: com.example.liftrix.domain.model.WorkoutTemplate, val targetFolderId: String) : WorkoutEvent()
    object ClearError : WorkoutEvent()
    object RefreshData : WorkoutEvent()
}