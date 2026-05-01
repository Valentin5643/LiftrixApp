package com.example.liftrix.ui.workout

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.workout.WorkoutHistoryRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.workout.WorkoutSyncStatusRepository
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplatePreview
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.template.TemplateRepository
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
import com.example.liftrix.sync.SyncManager
import com.example.liftrix.sync.SyncStatus
import com.example.liftrix.sync.StartupRestoreGate
import com.example.liftrix.sync.TemplateRestoreNotifier
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutHistoryRepository: WorkoutHistoryRepository,
    private val workoutSyncStatusRepository: WorkoutSyncStatusRepository,
    private val workoutTemplateRepository: TemplateRepository,
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
    private val sessionManager: com.example.liftrix.service.UnifiedWorkoutSessionManager,
    private val startupRestoreGate: StartupRestoreGate,
    private val templateRestoreNotifier: TemplateRestoreNotifier
) : ModernBaseViewModel<WorkoutUiState>(
    initialState = WorkoutUiState.Loading
) {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    private var workoutObservationJob: Job? = null
    private var templateObservationJob: Job? = null
    private var syncStatusObservationJob: Job? = null
    private var latestScreenData: WorkoutScreenData = WorkoutScreenData()

    init {
        observeAuthState()
        observeWorkouts()
        observeTemplates()
        observeFolders()
        observeSyncStatus()
        observeTemplateRestoreCompletions()

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
            is WorkoutEvent.DeleteWorkout -> deleteWorkout(event.workoutTemplate)
            WorkoutEvent.ClearError -> clearError()
            WorkoutEvent.RefreshData -> refreshData()
        }
    }

    private fun WorkoutUiState.workoutDataOrNull(): WorkoutScreenData? = when (this) {
        is WorkoutUiState.Success -> data
        is WorkoutUiState.Error -> previousData
        WorkoutUiState.Loading -> null
        else -> null
    }

    private fun currentWorkoutData(): WorkoutScreenData =
        _uiState.value.workoutDataOrNull() ?: latestScreenData

    private fun setWorkoutData(data: WorkoutScreenData) {
        latestScreenData = data
        setState(WorkoutUiState.Success(data))
    }

    private fun updateWorkoutData(transform: (WorkoutScreenData) -> WorkoutScreenData) {
        var updatedData: WorkoutScreenData? = null
        updateState { currentState ->
            val baseData = currentState.workoutDataOrNull() ?: latestScreenData
            val newData = transform(baseData)
            updatedData = newData
            WorkoutUiState.Success(newData)
        }
        updatedData?.let { latestScreenData = it }
    }


    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _currentUser.value = user
                if (user != null) {
                    Timber.d("[WORKOUT-DEBUG] Auth user available in WorkoutViewModel: userId=${user.uid}")
                    analyticsService.setUserProperties(user)
                        .onFailure { exception ->
                            Timber.e(exception, "Failed to set user properties for analytics")
                        }
                } else {
                    Timber.w("[WORKOUT-DEBUG] Auth emitted null in WorkoutViewModel; delaying state clear to avoid transient data wipe")
                    delay(500)
                    val confirmedCurrentUser = authRepository.getCurrentUser()
                    if (confirmedCurrentUser == null) {
                        Timber.w("[WORKOUT-DEBUG] Auth null confirmed; clearing workout screen state for logout")
                        setWorkoutData(WorkoutScreenData())
                        analyticsService.clearUserProperties()
                            .onFailure { exception ->
                                Timber.e(exception, "Failed to clear user properties for analytics")
                            }
                    } else {
                        Timber.d("[WORKOUT-DEBUG] Auth null was transient; preserving workout screen state for userId=${confirmedCurrentUser.uid}")
                        _currentUser.value = confirmedCurrentUser
                    }
                }
            }
        }
    }

    private fun observeWorkouts() {
        workoutObservationJob?.let {
            Timber.d("[WORKOUT-DEBUG] Cancelling existing workout observation before restart")
            it.cancel()
        }
        workoutObservationJob = viewModelScope.launch {
            // 🔥 FIX: Only restart workout observation when userId actually changes
            // This prevents workouts from "disappearing" after profile updates that recreate User objects
            var previousUserId: String? = null

            authRepository.currentUser
                .filterNotNull()
                .collect { user ->
                    // Only restart flow if userId actually changed
                    if (user.uid != previousUserId) {
                        previousUserId = user.uid
                        Timber.tag("FreshLoginRestoreDebug").d(
                            "operation=UI_ROOM_OBSERVATION_START screen=WorkoutViewModel userId=${user.uid} timestamp=${System.currentTimeMillis()}"
                        )
                        Timber.d("[WORKOUT-DEBUG] Starting workout observation for userId=${user.uid}")

                        combine(
                            workoutHistoryRepository.getAllWorkoutsForUser(user.uid),
                            syncManager.getSyncStatus()
                        ) { workouts, syncStatus ->
                            Timber.d("[WORKOUT-DEBUG] Workout read emitted count=${workouts.size} userId=${user.uid} syncStatus=${syncStatus::class.simpleName}")
                            val statusCounts = workouts.groupingBy { it.status }.eachCount()
                            val completedWithoutEndTime = workouts.count { it.status.name == "COMPLETED" && it.endTime == null }
                            if (workouts.isEmpty() && !startupRestoreGate.isRestoreComplete(user.uid)) {
                                Timber.tag("StartupRestoreFix").d(
                                    "operation=UI_EMPTY_WORKOUTS_SUPPRESSED screen=WorkoutViewModel userId=${user.uid} gateState=${startupRestoreGate.currentState(user.uid)} finalEmptyState=false timestamp=${System.currentTimeMillis()}"
                                )
                                return@combine
                            }
                            Timber.tag("FreshLoginRestoreDebug").d(
                                "operation=UI_WORKOUTS_EMIT screen=WorkoutViewModel userId=${user.uid} emittedCount=${workouts.size} displayedCount=${workouts.size} filteredOutCount=0 statusCounts=$statusCounts completedWithoutEndTime=$completedWithoutEndTime syncStatus=${syncStatus::class.simpleName} timestamp=${System.currentTimeMillis()}"
                            )
                            Timber.tag("StartupRestoreFix").d(
                                "operation=UI_WORKOUT_STATE_SOURCE screen=WorkoutViewModel userId=${user.uid} source=${if (workouts.isEmpty()) "LocalRoomEmptyAwaitingRestoreOrTrulyEmpty" else "LocalRoom"} workouts=${workouts.size} templates=${currentWorkoutData().templates.size} folders=${currentWorkoutData().folders.size} timestamp=${System.currentTimeMillis()}"
                            )
                            Timber.tag("WorkoutSyncDebug").d(
                                "[DATABASE-DEBUG] operation=WORKOUT_VIEWMODEL_DISPLAY_INPUT source=Room userId=${user.uid} timestamp=${System.currentTimeMillis()} count=${workouts.size} statusCounts=$statusCounts completedWithoutEndTime=$completedWithoutEndTime syncStatus=${syncStatus::class.simpleName}"
                            )
                            updateWorkoutData { currentData ->
                                currentData.copy(
                                    workouts = workouts,
                                    syncStatus = syncStatus
                                )
                            }
                        }.collect { /* Updates handled in combine block */ }
                    } else {
                        Timber.d("[WORKOUT-DEBUG] Skipping workout observation restart; userId unchanged=${user.uid}")
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
        templateObservationJob?.let {
            Timber.d("[WORKOUT-DEBUG] Cancelling existing template/folder observation before restart")
            it.cancel()
        }
        templateObservationJob = viewModelScope.launch {
            // 🔥 FIX: Only restart template observation when userId actually changes
            // This prevents templates from "disappearing" after profile updates that recreate User objects
            var previousUserId: String? = null

            authRepository.currentUser
                .filterNotNull()
                .collect { user ->
                    // Only restart flow if userId actually changed
                    if (user.uid != previousUserId) {
                        previousUserId = user.uid
                        Timber.d("[WORKOUT-DEBUG] Starting template/folder observation for userId=${user.uid}")

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
                            when {
                                foldersResult.isSuccess && templatesResult.isSuccess -> {
                                    val folders = foldersResult.getOrThrow()
                                    val templates = templatesResult.getOrThrow()

                                    Timber.tag("StartupRestoreFix").d(
                                        "[TEMPLATE-LOAD] operation=TEMPLATE_VIEWMODEL_FLOW_RECEIVED screen=WorkoutViewModel userId=${user.uid} templates=${templates.size} folders=${folders.size} gateState=${startupRestoreGate.currentState(user.uid)} timestamp=${System.currentTimeMillis()}"
                                    )
                                    Timber.d("[WORKOUT-DEBUG] Template/folder read emitted folders=${folders.size} templates=${templates.size} userId=${user.uid}")
                                    if (templates.isEmpty() && !startupRestoreGate.isRestoreComplete(user.uid)) {
                                        Timber.tag("StartupRestoreFix").d(
                                            "[TEMPLATE-LOAD] operation=UI_EMPTY_TEMPLATES_SUPPRESSED screen=WorkoutViewModel userId=${user.uid} gateState=${startupRestoreGate.currentState(user.uid)} folders=${folders.size} finalEmptyState=false timestamp=${System.currentTimeMillis()}"
                                        )
                                        return@combine
                                    }
                                    Timber.tag("StartupRestoreFix").d(
                                        "[TEMPLATE-LOAD] operation=UI_TEMPLATE_FOLDER_STATE_SOURCE screen=WorkoutViewModel userId=${user.uid} source=${if (templates.isEmpty()) "LocalRoomEmptyAwaitingRestoreOrTrulyEmpty" else "LocalRoom"} templates=${templates.size} folders=${folders.size} workouts=${currentWorkoutData().workouts.size} timestamp=${System.currentTimeMillis()}"
                                    )

                                    updateWorkoutData { currentData ->
                                        currentData.copy(
                                            folders = folders,
                                            templates = templates,
                                            selectedFolderId = null // Reset folder selection when loading all
                                        )
                                    }
                                    Timber.tag("StartupRestoreFix").d(
                                        "[TEMPLATE-LOAD] operation=TEMPLATE_UI_STATE_UPDATED screen=WorkoutViewModel userId=${user.uid} templates=${templates.size} folders=${folders.size} source=room_flow timestamp=${System.currentTimeMillis()}"
                                    )
                                }
                                foldersResult.isFailure -> {
                                    Timber.e("[WORKOUT-DEBUG] Failed to load folders: ${foldersResult.exceptionOrNull()?.message}")
                                    // Try to load templates only if folders fail
                                    if (templatesResult.isSuccess) {
                                        val templates = templatesResult.getOrThrow()
                                        updateWorkoutData { currentData ->
                                            currentData.copy(
                                                templates = templates,
                                                folders = emptyList() // Clear folders on failure
                                            )
                                        }
                                    }
                                }
                                templatesResult.isFailure -> {
                                    Timber.e("[WORKOUT-DEBUG] Failed to load templates: ${templatesResult.exceptionOrNull()?.message}")
                                    // Load folders only if templates fail
                                    if (foldersResult.isSuccess) {
                                        val folders = foldersResult.getOrThrow()
                                        updateWorkoutData { currentData ->
                                            currentData.copy(
                                                folders = folders,
                                                templates = emptyList() // Clear templates on failure
                                            )
                                        }
                                    }
                                }
                            }
                        }.collect { /* Updates handled in combine block */ }
                    } else {
                        Timber.d("[WORKOUT-DEBUG] Skipping template/folder observation restart; userId unchanged=${user.uid}")
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

    private fun observeTemplateRestoreCompletions() {
        viewModelScope.launch {
            templateRestoreNotifier.events.collect { event ->
                val currentUser = _currentUser.value
                if (currentUser?.uid != event.userId) {
                    Timber.tag("StartupRestoreFix").d(
                        "[TEMPLATE-LOAD] operation=TEMPLATE_VIEWMODEL_RESTORE_EVENT_IGNORED screen=WorkoutViewModel eventUserId=${event.userId} currentUserId=${currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                    )
                    return@collect
                }

                try {
                    Timber.tag("StartupRestoreFix").d(
                        "[TEMPLATE-LOAD] operation=TEMPLATE_VIEWMODEL_RESTORE_EVENT_RECEIVED screen=WorkoutViewModel userId=${event.userId} restoredCount=${event.templateCount} restoreFinishedAt=${event.finishedAtMs} timestamp=${System.currentTimeMillis()}"
                    )
                    val templates = templateQueryUseCase(event.userId).first()
                    val folders = folderOperationsUseCase.invoke(event.userId).first().getOrElse {
                        Timber.e(it, "Failed to load folders after template restore for user ${event.userId}")
                        currentWorkoutData().folders
                    }
                    Timber.tag("StartupRestoreFix").d(
                        "[TEMPLATE-LOAD] operation=TEMPLATE_VIEWMODEL_FLOW_RECEIVED screen=WorkoutViewModel userId=${event.userId} templates=${templates.size} folders=${folders.size} source=restore_notifier timestamp=${System.currentTimeMillis()}"
                    )
                    updateWorkoutData { currentData ->
                        currentData.copy(
                            folders = folders,
                            templates = templates,
                            selectedFolderId = null
                        )
                    }
                    val now = System.currentTimeMillis()
                    Timber.tag("StartupRestoreFix").i(
                        "[TEMPLATE-LOAD] operation=TEMPLATE_UI_STATE_UPDATED screen=WorkoutViewModel userId=${event.userId} templates=${templates.size} folders=${folders.size} source=restore_notifier timestamp=$now"
                    )
                    Timber.tag("StartupRestoreFix").i(
                        "[TEMPLATE-LOAD] operation=TEMPLATE_RESTORE_TO_UI_LATENCY_MS screen=WorkoutViewModel userId=${event.userId} restoredCount=${event.templateCount} displayedTemplates=${templates.size} latencyMs=${now - event.finishedAtMs} timestamp=$now"
                    )
                } catch (exception: Exception) {
                    Timber.tag("StartupRestoreFix").e(
                        exception,
                        "[TEMPLATE-LOAD] operation=TEMPLATE_UI_STATE_UPDATE_FAILED screen=WorkoutViewModel userId=${event.userId} timestamp=${System.currentTimeMillis()}"
                    )
                }
            }
        }
    }

    private fun observeSyncStatus() {
        syncStatusObservationJob?.let {
            Timber.d("[WORKOUT-DEBUG] Cancelling existing sync status observation before restart")
            it.cancel()
        }
        syncStatusObservationJob = viewModelScope.launch {
            syncManager.getSyncStatus().collect { status ->
                val currentState = _uiState.value
                val currentData = currentState.workoutDataOrNull()
                if (currentData == null) {
                    Timber.d("[WORKOUT-DEBUG] Sync status update skipped because workout data is not loaded yet status=${status::class.simpleName} state=${currentState::class.simpleName}")
                } else {
                    Timber.d("[WORKOUT-DEBUG] Sync status state update status=${status::class.simpleName} workouts=${currentData.workouts.size} templates=${currentData.templates.size} folders=${currentData.folders.size}")
                    if (currentData.workouts.isEmpty() && currentData.templates.isEmpty() && currentData.folders.isEmpty()) {
                        Timber.w("[WORKOUT-DEBUG] Sync status is updating an already-empty workout screen state status=${status::class.simpleName}")
                    }
                    updateWorkoutData { it.copy(syncStatus = status) }
                }

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
                val clearedSession = previousSession
                if (clearedSession != null && currentSession == null) {
                    val shouldRefreshAfterClear = clearedSession.sessionStatus == com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.COMPLETED ||
                        clearedSession.sessionStatus == com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
                    Timber.d("[WORKOUT-DEBUG] Session cleared observed previousStatus=${clearedSession.sessionStatus} sessionId=${clearedSession.id.value} shouldRefresh=$shouldRefreshAfterClear")

                    if (shouldRefreshAfterClear) {
                        // Add a small delay to ensure cache invalidation has completed
                        kotlinx.coroutines.delay(200)

                        // Trigger a refresh of folders and templates data
                        refreshData()
                    }
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
                setWorkoutData(currentWorkoutData())
            }.onFailure { error ->
                Timber.e(error.toString(), "Failed to save workout")
                logError(error, "saveWorkout")
                val currentData = currentWorkoutData()
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
                val result = workoutSyncStatusRepository.syncNowForUser(userId.value)

                result.onSuccess {
                    Timber.d("Sync started successfully")
                    setWorkoutData(currentWorkoutData())
                }.onFailure { error ->
                    Timber.e("Failed to start sync: ${error.message}")
                    logError(error, "syncNow")
                    val currentData = currentWorkoutData()
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
                val currentData = currentWorkoutData()
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
                val result = workoutSyncStatusRepository.getUnsyncedCountForUser(userId.value)

                result.onSuccess { count ->
                    updateWorkoutData { it.copy(unsyncedCount = count) }
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
        val currentState = _uiState.value
        if (currentState is WorkoutUiState.Error) {
            setWorkoutData(currentState.previousData ?: latestScreenData)
        }
    }

    private fun refreshData() {
        val currentData = _uiState.value.workoutDataOrNull() ?: latestScreenData
        Timber.d("[WORKOUT-DEBUG] refreshData requested currentWorkouts=${currentData.workouts.size} currentTemplates=${currentData.templates.size} currentFolders=${currentData.folders.size}")
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
                    onSuccess = { it.value },
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
                            updateWorkoutData { currentData ->
                                currentData.copy(
                                    templates = templates,
                                    selectedFolderId = selectedFolderId
                                )
                            }
                        },
                        onFailure = { exception ->
                            Timber.e(exception, "Failed to load filtered templates for user $userId, folder: $selectedFolderId")
                            updateWorkoutData { currentData ->
                                currentData.copy(
                                    templates = emptyList(),
                                    selectedFolderId = selectedFolderId
                                )
                            }
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
                    onSuccess = { it.value },
                    onFailure = {
                        Timber.e(it, "Failed to get user ID")
                        return@launch
                    }
                )

                // Direct repository access to avoid use case Flow complications
                folderRepository.getAllFoldersForUser(userId)
                    .first() // Get just the first emission
                    .let { folders ->
                        updateWorkoutData { it.copy(folders = folders) }
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
                if (userId.value.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot create folder"
                    )
                    logError(error, "createFolder")
                    val currentData = currentWorkoutData()
                    updateState { WorkoutUiState.Error(error, currentData) }
                    return@launch
                }

                val result = folderOperationsUseCase.create(userId.value, folderName)

                result.onSuccess { folder ->
                    Timber.d("New folder created: ${folder.name} (${folder.id.value})")

                    // Refresh the combined loading to include the new folder
                    // This ensures the new folder appears alongside existing templates
                    refreshData()
                }.onFailure { error ->
                    Timber.e("Failed to create folder: ${error.message}")
                    logError(error, "createFolder")
                    val currentData = currentWorkoutData()
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
                val currentData = currentWorkoutData()
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
                val currentData = currentWorkoutData()
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

    fun deleteWorkout(workoutTemplate: com.example.liftrix.domain.model.WorkoutTemplate) {
        viewModelScope.launch {
            val currentData = currentWorkoutData()
            updateState { WorkoutUiState.Loading }

            val result = templateCommandUseCase.delete(workoutTemplate.id)

            result.onSuccess {
                Timber.d("Workout '${workoutTemplate.name}' deleted successfully")
                updateWorkoutData { data ->
                    data.copy(templates = data.templates.filterNot { it.id == workoutTemplate.id })
                }
                refreshData()
            }.onFailure { error ->
                Timber.e("Failed to delete workout: ${error.message}")
                logError(error, "deleteWorkout")
                updateState {
                    WorkoutUiState.Error(
                        error as? LiftrixError ?: LiftrixError.UnknownError(
                            errorMessage = error.message ?: "Failed to delete workout"
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
                if (userId.value.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot delete folder"
                    )
                    logError(error, "deleteFolder")
                    val currentData = currentWorkoutData()
                    updateState { WorkoutUiState.Error(error, currentData) }
                    return@launch
                }

                val result = folderOperationsUseCase.delete(userId.value, folder.id)

                result.onSuccess {
                    Timber.d("Folder '${folder.name}' deleted successfully")

                    // Refresh the data to remove the deleted folder and show relocated templates
                    refreshData()
                }.onFailure { error ->
                    Timber.e("Failed to delete folder '${folder.name}': ${error.message}")
                    logError(error, "deleteFolder")
                    val currentData = currentWorkoutData()
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
                val currentData = currentWorkoutData()
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
                if (userId.value.isBlank()) {
                    val error = LiftrixError.UnknownError(
                        errorMessage = "User not authenticated - cannot rename folder"
                    )
                    logError(error, "renameFolder")
                    val currentData = currentWorkoutData()
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
                    val currentData = currentWorkoutData()
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
                val currentData = currentWorkoutData()
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
                if (userId.value.isBlank()) {
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

                val result = folderOperationsUseCase.reorder(userId.value, currentFolders, orderedFolderIds)

                result.onSuccess { reorderedFolders ->
                    // ✅ RACE CONDITION FIX: Use cached state instead of re-checking UI state
                    updateWorkoutData { it.copy(folders = reorderedFolders) }
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
    data class DeleteWorkout(val workoutTemplate: com.example.liftrix.domain.model.WorkoutTemplate) : WorkoutEvent()
    object ClearError : WorkoutEvent()
    object RefreshData : WorkoutEvent()
}
