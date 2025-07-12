package com.example.liftrix.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.data.repository.WorkoutRepository
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutExercise
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Distance
import com.example.liftrix.domain.model.RPE
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.ActiveWorkoutSessionRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.service.FirebasePresenceService
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.service.LiveWorkoutSessionManager
import com.example.liftrix.domain.usecase.template.CreateTemplateFromSessionUseCase
import com.example.liftrix.domain.usecase.exercise.SearchExercisesUseCase
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.domain.model.Equipment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for active workout session management with timer service integration.
 * 
 * Manages workout state, timer integration, exercise tracking, and session persistence
 * following the MVI pattern with reactive state management.
 */
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val timerServiceManager: TimerServiceManager,
    private val workoutRepository: WorkoutRepository,
    private val activeWorkoutSessionRepository: ActiveWorkoutSessionRepository,
    private val authRepository: AuthRepository,
    private val presenceService: FirebasePresenceService,
    private val createTemplateFromSessionUseCase: CreateTemplateFromSessionUseCase,
    private val searchExercisesUseCase: SearchExercisesUseCase,
    private val templateRepository: WorkoutTemplateRepository,
    private val liveWorkoutSessionManager: LiveWorkoutSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutState())
    val uiState: StateFlow<ActiveWorkoutState> = _uiState.asStateFlow()

    init {
        bindTimerService()
        observeTimerState()
        observeLiveSessionState()
        loadActiveWorkout()
        loadAvailableExercises()
    }
    
    /**
     * Loads available exercises for selection
     */
    private fun loadAvailableExercises() {
        viewModelScope.launch {
            try {
                searchExercisesUseCase.search("", Equipment.values().toSet())
                    .collect { exercises ->
                        updateState { copy(availableExercises = exercises) }
                    }
            } catch (error: Exception) {
                Timber.e(error, "Error loading available exercises")
            }
        }
    }

    /**
     * Handles all user events following MVI pattern
     */
    fun onEvent(event: ActiveWorkoutEvent) {
        when (event) {
            is ActiveWorkoutEvent.StartSession -> startWorkoutSession()
            is ActiveWorkoutEvent.PauseSession -> pauseWorkoutSession()
            is ActiveWorkoutEvent.ResumeSession -> resumeWorkoutSession()
            is ActiveWorkoutEvent.StopSession -> stopWorkoutSession()
            is ActiveWorkoutEvent.StartSessionFromTemplate -> startSessionFromTemplateInternal(event.templateId)
            is ActiveWorkoutEvent.StartBlankSession -> startBlankSessionInternal()
            is ActiveWorkoutEvent.ResumeExistingSession -> resumeExistingSessionInternal(event.sessionId)
            is ActiveWorkoutEvent.StartRest -> startRestTimer(event.restTimer)
            is ActiveWorkoutEvent.SkipRest -> skipRestTimer()
            is ActiveWorkoutEvent.AddExercise -> addExercise(event.exercise)
            is ActiveWorkoutEvent.AddExerciseFromSelector -> addExerciseFromSelector(event.searchableExercise)
            is ActiveWorkoutEvent.OnExerciseSearchQueryChanged -> onExerciseSearchQueryChanged(event.query)
            is ActiveWorkoutEvent.OnExerciseSelected -> onExerciseSelected(event.exercise)
            is ActiveWorkoutEvent.OnExerciseSelectorExpandedChanged -> onExerciseSelectorExpandedChanged(event.expanded)
            is ActiveWorkoutEvent.RemoveExercise -> removeExercise(event.exerciseIndex)
            is ActiveWorkoutEvent.AddSet -> addSetToExercise(event.exerciseIndex)
            is ActiveWorkoutEvent.UpdateSet -> updateExerciseSet(event.exerciseIndex, event.setIndex, event.set)
            is ActiveWorkoutEvent.RemoveSet -> removeSetFromExercise(event.exerciseIndex, event.setIndex)
            is ActiveWorkoutEvent.SaveWorkout -> saveCurrentWorkout()
            is ActiveWorkoutEvent.SaveAsTemplate -> saveCurrentWorkoutAsTemplate(event.templateName, event.templateDescription)
            is ActiveWorkoutEvent.LoadWorkout -> loadWorkout(event.workoutId)
            is ActiveWorkoutEvent.ShowExitConfirmation -> showExitConfirmationInternal()
            is ActiveWorkoutEvent.HideExitConfirmation -> hideExitConfirmationInternal()
            is ActiveWorkoutEvent.ClearError -> clearError()
            is ActiveWorkoutEvent.DismissMessage -> dismissMessage()
        }
    }

    // Convenience methods for WorkoutFlow compatibility
    fun startSessionFromTemplate(templateId: String) {
        onEvent(ActiveWorkoutEvent.StartSessionFromTemplate(templateId))
    }

    fun startBlankSession() {
        onEvent(ActiveWorkoutEvent.StartBlankSession)
    }

    fun resumeSession(sessionId: String) {
        onEvent(ActiveWorkoutEvent.ResumeExistingSession(sessionId))
    }

    fun hasUnsavedChanges(): Boolean {
        return _uiState.value.hasUnsavedChanges
    }

    fun showExitConfirmation() {
        onEvent(ActiveWorkoutEvent.ShowExitConfirmation)
    }
    
    /**
     * Adds an exercise from the exercise library to the current session
     */
    fun addExerciseToSession(exerciseLibrary: ExerciseLibrary) {
        val exercise = convertExerciseLibraryToExercise(exerciseLibrary)
        addExercise(exercise)
    }
    
    /**
     * Adds an exercise by ID (supports both library and custom exercises)
     * 🔥 ENHANCED: Added comprehensive deduplication and debouncing to prevent ghost exercise re-additions
     */
    fun addExerciseById(exerciseId: String, isCustomExercise: Boolean) {
        viewModelScope.launch {
            // 🔥 CRITICAL FIX: Use mutex to prevent concurrent exercise additions
            exerciseAdditionMutex.withLock {
                try {
                    Timber.i("🔥 NAVIGATION-FLOW: addExerciseById called for exerciseId: $exerciseId, isCustom: $isCustomExercise")
                    
                    // 🔥 GHOST-PREVENTION: Check if this exercise was recently deleted
                    if (recentlyDeletedExercises.contains(exerciseId)) {
                        Timber.w("🔥 GHOST-GUARD: Exercise '$exerciseId' was recently deleted, preventing re-addition")
                        return@withLock
                    }
                    
                    // 🔥 DUPLICATE-PREVENTION: Check if this exercise was recently added
                    if (recentlyAddedExercises.contains(exerciseId)) {
                        Timber.w("🔥 DUPLICATE-GUARD: Exercise '$exerciseId' was recently added, preventing duplicate")
                        return@withLock
                    }
                    
                    // Check current workout state for diagnostics
                    val currentState = _uiState.value
                    Timber.d("🔍 STATE-CHECK: currentWorkout=${currentState.currentWorkout?.name}, pendingCount=${currentState.pendingExercises.size}, isLoading=${currentState.isLoading}")
                    
                    // 🔥 ENHANCED-EXERCISE-GUARD: Check if exercise with same NAME already exists in current session/workout
                    // Note: We now check by name instead of ID since session exercises have unique IDs
                    val currentSession = currentState.currentSession
                    val currentWorkout = currentState.currentWorkout
                    
                    // Get all available exercises and check for duplicates
                    val searchableExercises = searchExercisesUseCase.search("", Equipment.values().toSet()).first()
                    Timber.d("🔍 SEARCH-RESULT: Found ${searchableExercises.size} total exercises to search through")
                    
                    val targetExerciseName = searchableExercises.find { searchable ->
                        when (searchable) {
                            is SearchableExercise.LibraryExercise -> !isCustomExercise && searchable.exercise.id == exerciseId
                            is SearchableExercise.CustomExercise -> isCustomExercise && searchable.exercise.id.value == exerciseId
                        }
                    }?.name
                    
                    if (targetExerciseName != null) {
                        val existsInSession = currentSession?.exercises?.any { it.name == targetExerciseName } ?: false
                        val existsInWorkout = currentWorkout?.exercises?.any { it.libraryExercise.name == targetExerciseName } ?: false
                        
                        if (existsInSession || existsInWorkout) {
                            Timber.w("🔥 EXISTING-GUARD: Exercise '$targetExerciseName' already exists in current session (session: $existsInSession, workout: $existsInWorkout)")
                            return@withLock
                        }
                    }
                    
                    val searchableExercise = searchableExercises.find { searchable ->
                        when (searchable) {
                            is SearchableExercise.LibraryExercise -> !isCustomExercise && searchable.exercise.id == exerciseId
                            is SearchableExercise.CustomExercise -> isCustomExercise && searchable.exercise.id.value == exerciseId
                        }
                    }
                    
                    if (searchableExercise == null) {
                        Timber.e("🔍 ERROR: Exercise not found with ID: $exerciseId, isCustom: $isCustomExercise")
                        Timber.e("🔍 ERROR-DETAIL: Available exercise IDs: ${searchableExercises.take(5).map { 
                            when(it) {
                                is SearchableExercise.LibraryExercise -> "LIB:${it.exercise.id}"
                                is SearchableExercise.CustomExercise -> "CUSTOM:${it.exercise.id.value}"
                            }
                        }}")
                        updateState { copy(error = "Exercise not found: $exerciseId") }
                        return@withLock
                    }
                    
                    Timber.i("🔥 SUCCESS: Found exercise '${searchableExercise.name}', adding to workout")
                    
                    // 🔥 TRACKING: Add to recently added set to prevent rapid duplicates
                    recentlyAddedExercises.add(exerciseId)
                    
                    // 🔥 CLEANUP: Remove from recently deleted set if it was there
                    recentlyDeletedExercises.remove(exerciseId)
                    
                    addExerciseFromSelector(searchableExercise)
                    
                    // 🔥 CLEANUP: Clear from recently added after a delay to allow for proper state settling
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000) // 2 second grace period
                        recentlyAddedExercises.remove(exerciseId)
                    }
                
                    // Enhanced retry logic for workout loading race conditions
                    val updatedState = _uiState.value
                    if (updatedState.currentWorkout == null && updatedState.pendingExercises.isNotEmpty()) {
                        Timber.w("🔍 RACE-CONDITION: Workout still loading, scheduling enhanced retry (pending: ${updatedState.pendingExercises.size})")
                        
                        // Multiple retry attempts with increasing delays
                        repeat(3) { attempt ->
                            kotlinx.coroutines.delay(200L * (attempt + 1)) // 200ms, 400ms, 600ms
                            val retryState = _uiState.value
                            if (retryState.currentWorkout != null) {
                                Timber.i("🔥 RETRY-SUCCESS: Workout loaded on attempt ${attempt + 1}, processing pending exercises")
                                processPendingExercises()
                                return@repeat
                            }
                        }
                        
                        // Final attempt
                        Timber.w("🔍 FINAL-RETRY: Processing pending exercises anyway after all retries")
                        processPendingExercises()
                    }
                    
                } catch (e: Exception) {
                    Timber.e(e, "🔥 CRITICAL-ERROR: Failed to add exercise by ID: $exerciseId")
                    updateState { copy(error = "Failed to add exercise: ${e.message}") }
                    // 🔥 CLEANUP: Remove from tracking sets on error
                    recentlyAddedExercises.remove(exerciseId)
                }
            }
        }
    }

    val sessionId: String?
        get() = _uiState.value.sessionId

    /**
     * Starts a session from a workout template
     * 🔥 FIXED: Added mutex synchronization and enhanced guards to prevent duplicate session creation
     */
    private fun startSessionFromTemplateInternal(templateId: String) {
        viewModelScope.launch {
            // 🔥 CRITICAL FIX: Use mutex to prevent concurrent template session creation
            templateCreationMutex.withLock {
                try {
                    val currentState = _uiState.value
                    
                    // 🔥 GUARD 1: Don't create if we already have an active session
                    if (currentState.currentSession != null) {
                        Timber.d("🔍 GUARD: Session already exists (${currentState.sessionId}), skipping template session creation")
                        return@withLock
                    }
                    
                    // 🔥 GUARD 2: Don't create if already loading
                    if (currentState.isLoading) {
                        Timber.d("🔍 GUARD: Already loading session, skipping duplicate creation")
                        return@withLock
                    }
                    
                    // 🔥 GUARD 3: Don't create if we already have a workout from this template
                    if (currentState.isWorkoutFromTemplate && currentState.hasActiveWorkout) {
                        Timber.d("🔍 GUARD: Template workout already active, skipping recreation")
                        return@withLock
                    }
                    
                    // 🔥 GUARD 4: Don't create if this exact template is already initialized
                    if (currentState.templateId == templateId) {
                        Timber.d("🔍 GUARD: Template $templateId already initialized, skipping duplicate")
                        return@withLock
                    }
                    
                    // 🔥 NEW GUARD 5: Check if we have exercises that match this template pattern
                    if (currentState.currentWorkout?.exercises?.isNotEmpty() == true && 
                        currentState.isWorkoutFromTemplate) {
                        Timber.d("🔍 GUARD: Template workout with exercises already exists, skipping recreation")
                        return@withLock
                    }
                    
                    Timber.i("🔥 CREATING: Starting new session from template: $templateId (with mutex protection)")
                    Timber.d("🔍 TEMPLATE-DEBUG: Current state before creation - hasActiveWorkout=${currentState.hasActiveWorkout}, sessionId=${currentState.sessionId}, exerciseCount=${currentState.currentWorkout?.exercises?.size}")
                    
                    // 🔥 FIX: Clear previous workout state when starting new session
                    clearWorkoutState()
                    
                    updateState { copy(isLoading = true, templateId = templateId) }
                    
                    val sessionId = "session_${System.currentTimeMillis()}"
                    createNewWorkoutFromTemplate(templateId, sessionId)
                    
                    // Don't start the timer session automatically for template workouts
                    // The simple timer will start when the screen loads
                    
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start session from template: $templateId")
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to start session from template: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Starts a blank workout session
     */
    private fun startBlankSessionInternal() {
        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                if (user == null) {
                    updateState { 
                        copy(error = "User not authenticated")
                    }
                    return@launch
                }
                
                // 🔥 FIX: Clear previous workout state when starting new session
                clearWorkoutState()
                
                // Create blank active workout session
                val newSession = ActiveWorkoutSession.createBlank(
                    userId = user.uid,
                    name = "Active Workout"
                )
                
                // Create and save the session using the repository
                val createResult = activeWorkoutSessionRepository.createSession(newSession)
                if (createResult.isFailure) {
                    updateState { 
                        copy(error = "Failed to create session: ${createResult.exceptionOrNull()?.message}")
                    }
                    return@launch
                }
                
                val createdSession = createResult.getOrNull()!!
                
                // Start the live session through the manager
                liveWorkoutSessionManager.startLiveSession(createdSession)
                
                // Also create a workout representation for UI compatibility (legacy support)
                val now = java.time.Instant.now()
                val newWorkout = Workout(
                    userId = user.uid,
                    id = WorkoutId.generate(),
                    name = "Active Workout",
                    date = java.time.LocalDate.now(),
                    exercises = emptyList(),
                    status = WorkoutStatus.IN_PROGRESS,
                    startTime = now,
                    endTime = null,
                    createdAt = now,
                    updatedAt = now
                )
                
                updateState { 
                    copy(
                        currentSession = createdSession,
                        currentWorkout = newWorkout,
                        hasActiveWorkout = true,
                        hasUnsavedChanges = true,
                        sessionId = createdSession.id.value
                    )
                }
                
                // Start the timer session
                startWorkoutSession()
                
                Timber.i("🔍 DEBUG: Blank workout session created. Session ID: ${createdSession.id.value}. Checking for pending exercises...")
                
                // Ensure state is propagated before processing pending exercises
                kotlinx.coroutines.delay(50) // Small delay to ensure state update is processed
                
                // Process any exercises that were added while session was loading
                processPendingExercises()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to start blank session")
                updateState { 
                    copy(error = "Failed to start blank session: ${e.message}")
                }
            }
        }
    }

    /**
     * Resumes an existing workout session
     */
    private fun resumeExistingSessionInternal(sessionId: String) {
        viewModelScope.launch {
            try {
                // 🔥 FIX: Clear previous workout state when resuming session
                clearWorkoutState()
                
                updateState { copy(isLoading = true, sessionId = sessionId) }
                
                // Load the existing session by ID
                val sessionResult = activeWorkoutSessionRepository.getSessionById(
                    WorkoutSessionId(sessionId)
                )
                
                if (sessionResult.isFailure) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Failed to load session: ${sessionResult.exceptionOrNull()?.message}"
                        )
                    }
                    return@launch
                }
                
                val session = sessionResult.getOrNull()
                if (session == null) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "Session not found: $sessionId"
                        )
                    }
                    return@launch
                }
                
                // Convert session to workout representation for UI compatibility
                val workoutFromSession = session.toCompletedWorkout().copy(
                    status = WorkoutStatus.IN_PROGRESS,
                    endTime = null
                )
                
                updateState { 
                    copy(
                        isLoading = false,
                        currentSession = session,
                        currentWorkout = workoutFromSession,
                        hasActiveWorkout = true,
                        hasUnsavedChanges = true,
                        sessionId = session.id.value,
                        isWorkoutFromTemplate = session.templateId != null,
                        templateId = session.templateId?.value // 🔥 TRACK: Template ID from session
                    )
                }
                
                Timber.i("Successfully resumed session: $sessionId with ${session.exercises.size} exercises")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume session: $sessionId")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to resume session: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Shows exit confirmation dialog
     */
    private fun showExitConfirmationInternal() {
        updateState { copy(showExitConfirmation = true) }
    }

    /**
     * Hides exit confirmation dialog
     */
    private fun hideExitConfirmationInternal() {
        updateState { copy(showExitConfirmation = false) }
    }

    /**
     * Creates a new workout from a template
     */
    private suspend fun createNewWorkoutFromTemplate(templateId: String, sessionId: String) {
        try {
            val user = authRepository.currentUser.first()
            if (user == null) {
                updateState { 
                    copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                }
                return
            }
            
            // Load the actual template
            val template = templateRepository.getTemplateById(WorkoutTemplateId(templateId), user.uid)
            if (template == null) {
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Template not found"
                    )
                }
                return
            }
            
            // Create active workout session from template
            val newSession = ActiveWorkoutSession.fromTemplate(
                userId = user.uid,
                template = template,
                customName = null // Will use template name with timestamp
            )
            
            // Create and save the session using the repository
            val createResult = activeWorkoutSessionRepository.createSession(newSession)
            if (createResult.isFailure) {
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to create session: ${createResult.exceptionOrNull()?.message}"
                    )
                }
                return
            }
            
            val createdSession = createResult.getOrNull()!!
            
            // Start the live session through the manager
            liveWorkoutSessionManager.startLiveSession(createdSession)
            
            // Also create a workout representation for UI compatibility (legacy support)
            val now = java.time.Instant.now()
            val workoutId = WorkoutId.generate()
            
            // Convert template exercises to workout exercises for UI display
            val workoutExercises = template.exercises.mapIndexed { index, templateExercise ->
                templateExercise.toExercise(workoutId).copy(orderIndex = index)
            }
            
            val newWorkout = Workout(
                userId = user.uid,
                id = workoutId,
                name = template.name,
                date = java.time.LocalDate.now(),
                exercises = workoutExercises,
                status = WorkoutStatus.IN_PROGRESS,
                startTime = now,
                endTime = null,
                createdAt = now,
                updatedAt = now
            )
            
            updateState { 
                copy(
                    isLoading = false,
                    currentSession = createdSession,
                    currentWorkout = newWorkout,
                    hasActiveWorkout = true,
                    hasUnsavedChanges = true,
                    sessionId = createdSession.id.value,
                    isWorkoutFromTemplate = true,
                    templateId = templateId // 🔥 NEW: Track template ID
                )
            }
            
            Timber.i("🔍 DEBUG: Template workout session created with ${createdSession.exercises.size} exercises. Session ID: ${createdSession.id.value}.")
            Timber.d("🔍 TEMPLATE-CREATION: Session exercises: ${createdSession.exercises.map { "${it.name}(${it.exerciseId})" }}")
            Timber.d("🔍 TEMPLATE-CREATION: Workout exercises: ${newWorkout.exercises.map { "${it.libraryExercise.name}(${it.id})" }}")
            Timber.d("🔍 TEMPLATE-CREATION: Checking for pending exercises...")
            // Process any exercises that were added while template was loading
            processPendingExercises()
        } catch (e: Exception) {
            Timber.e(e, "Failed to create workout from template: $templateId")
            updateState { 
                copy(
                    isLoading = false,
                    error = "Failed to create workout from template: ${e.message}"
                )
            }
        }
    }


    /**
     * Binds to the timer service for reactive state updates
     */
    private fun bindTimerService() {
        viewModelScope.launch {
            val result = timerServiceManager.bindService()
            if (result.isFailure) {
                updateState { 
                    copy(
                        error = "Failed to connect to timer service: ${result.exceptionOrNull()?.message}",
                        isTimerServiceConnected = false
                    )
                }
            }
        }
    }

    /**
     * Observes timer service state for reactive UI updates
     */
    private fun observeTimerState() {
        viewModelScope.launch {
            combine(
                timerServiceManager.connectionState,
                timerServiceManager.timerState
            ) { connectionState, timerState ->
                val isConnected = connectionState is TimerServiceManager.ConnectionState.Connected
                updateState {
                    copy(
                        isTimerServiceConnected = isConnected,
                        timerState = timerState,
                        formattedSessionTime = formatSessionTime(timerState),
                        formattedRestTime = formatRestTime(timerState),
                        isSessionActive = timerState.isRunning,
                        connectionError = if (connectionState is TimerServiceManager.ConnectionState.Error) {
                            connectionState.exception.message
                        } else null
                    )
                }
            }.collect { }
        }
    }

    /**
     * Observes live session state for persistent workout sessions
     */
    private fun observeLiveSessionState() {
        viewModelScope.launch {
            liveWorkoutSessionManager.liveSessionState.collect { liveSessionState ->
                when (liveSessionState) {
                    is LiveWorkoutSessionManager.LiveSessionState.ActiveSession -> {
                        // Update UI state with active session
                        val session = liveSessionState.session
                        val workoutFromSession = session.toCompletedWorkout().copy(
                            status = WorkoutStatus.IN_PROGRESS,
                            endTime = null
                        )
                        
                        updateState {
                            copy(
                                currentSession = session,
                                currentWorkout = workoutFromSession,
                                hasActiveWorkout = true,
                                sessionId = session.id.value,
                                isWorkoutFromTemplate = session.templateId != null,
                                templateId = session.templateId?.value
                            )
                        }
                    }
                    is LiveWorkoutSessionManager.LiveSessionState.NoSession -> {
                        // Only clear if we don't have a local session
                        val currentState = _uiState.value
                        if (currentState.currentSession != null) {
                            updateState {
                                copy(
                                    currentSession = null,
                                    sessionId = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads the active workout for the current user
     */
    private fun loadActiveWorkout() {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val user = authRepository.currentUser.first()
                if (user == null) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }

                // Check for active session first
                val activeSessionResult = activeWorkoutSessionRepository.getCurrentSessionForUser(user.uid)
                
                if (activeSessionResult.isSuccess) {
                    val activeSession = activeSessionResult.getOrNull()
                    if (activeSession != null) {
                        // Convert session to workout for UI compatibility
                        val workoutFromSession = activeSession.toCompletedWorkout().copy(
                            status = WorkoutStatus.IN_PROGRESS,
                            endTime = null
                        )
                        
                        updateState {
                            copy(
                                isLoading = false,
                                currentSession = activeSession,
                                currentWorkout = workoutFromSession,
                                hasActiveWorkout = true,
                                sessionId = activeSession.id.value,
                                isWorkoutFromTemplate = activeSession.templateId != null,
                                templateId = activeSession.templateId?.value // 🔥 TRACK: Template ID from loaded session
                            )
                        }
                        return@launch
                    }
                }
                
                // Fall back to loading active workout if no session found
                val activeWorkout = workoutRepository.getActiveWorkoutForUser(user.uid)
                updateState {
                    copy(
                        isLoading = false,
                        currentWorkout = activeWorkout,
                        hasActiveWorkout = activeWorkout != null,
                        currentSession = null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load active workout")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to load active workout: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Starts a new workout session with timer
     */
    private fun startWorkoutSession() {
        viewModelScope.launch {
            val result = timerServiceManager.startSession()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to start session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Create new workout if none exists
                if (_uiState.value.currentWorkout == null) {
                    createNewWorkout()
                }
                
                // Update presence status to WORKING_OUT
                _uiState.value.currentWorkout?.let { workout ->
                    val presenceResult = presenceService.updateWorkoutStatus(workout.id.value)
                    if (presenceResult.isFailure) {
                        Timber.w(presenceResult.exceptionOrNull(), "Failed to update presence status - continuing workout")
                        // Don't block workout functionality if presence update fails
                    }
                }
            }
        }
    }

    /**
     * Pauses the current workout session
     */
    private fun pauseWorkoutSession() {
        viewModelScope.launch {
            // Pause the live session
            if (liveWorkoutSessionManager.hasActiveSession()) {
                liveWorkoutSessionManager.pauseLiveSession()
            }
            
            val result = timerServiceManager.pauseTimer()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to pause session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Update presence to ONLINE when paused (user not actively working out)
                val presenceResult = presenceService.updateWorkoutStatus(null)
                if (presenceResult.isFailure) {
                    Timber.w(presenceResult.exceptionOrNull(), "Failed to update presence on pause - continuing")
                }
            }
        }
    }

    /**
     * Resumes the paused workout session
     */
    private fun resumeWorkoutSession() {
        viewModelScope.launch {
            // Resume the live session
            if (liveWorkoutSessionManager.hasActiveSession()) {
                liveWorkoutSessionManager.resumeLiveSession()
            }
            
            val result = timerServiceManager.resumeTimer()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to resume session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Update presence back to WORKING_OUT when resumed
                _uiState.value.currentWorkout?.let { workout ->
                    val presenceResult = presenceService.updateWorkoutStatus(workout.id.value)
                    if (presenceResult.isFailure) {
                        Timber.w(presenceResult.exceptionOrNull(), "Failed to update presence on resume - continuing")
                    }
                }
            }
        }
    }

    /**
     * Stops the workout session and timer
     */
    private fun stopWorkoutSession() {
        viewModelScope.launch {
            val result = timerServiceManager.stopTimer()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to stop session: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Clear presence status to ONLINE
                val presenceResult = presenceService.updateWorkoutStatus(null)
                if (presenceResult.isFailure) {
                    Timber.w(presenceResult.exceptionOrNull(), "Failed to clear presence status - continuing workout stop")
                    // Don't block workout functionality if presence update fails
                }
                
                // Auto-save workout before stopping
                saveCurrentWorkout()
            }
        }
    }

    /**
     * Starts a rest timer with the specified configuration
     */
    private fun startRestTimer(restTimer: RestTimer) {
        viewModelScope.launch {
            val result = timerServiceManager.startRestTimer(restTimer)
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to start rest timer: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Skips the current rest timer
     */
    private fun skipRestTimer() {
        viewModelScope.launch {
            val result = timerServiceManager.skipRest()
            if (result.isFailure) {
                updateState { 
                    copy(error = "Failed to skip rest: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Adds an exercise to the current workout
     * 🔥 FIXED: Now properly synchronizes both session and workout exercise lists
     */
    private fun addExercise(exercise: Exercise) {
        Timber.d("🔍 DEBUG: addExercise called for '${exercise.libraryExercise.name}'")
        
        // Check if we have an active session - if so, use session-based addition
        val currentState = _uiState.value
        val currentSession = currentState.currentSession
        
        if (currentSession != null) {
            // Session-based workout: Add to both session and UI state
            Timber.i("🔥 FIXED: Adding exercise '${exercise.libraryExercise.name}' to active session '${currentSession.id}'")
            addExerciseToActiveSession(exercise, currentSession)
        } else {
            // Legacy workout or pending exercise: Use original logic
            updateState {
                val currentWorkout = this.currentWorkout
                
                if (currentWorkout == null) {
                    Timber.w("🔍 DEBUG: currentWorkout is null, queueing exercise '${exercise.libraryExercise.name}' in pendingExercises. Current pending count: ${pendingExercises.size}")
                    // Queue the exercise to be added when workout is loaded
                    copy(pendingExercises = pendingExercises + exercise)
                } else {
                    Timber.i("🔍 DEBUG: Adding exercise '${exercise.libraryExercise.name}' to workout '${currentWorkout.name}'. Current exercise count: ${currentWorkout.exercises.size}")
                    // Set the order index and workout ID for the new exercise
                    val exerciseWithCorrectIds = exercise.copy(
                        workoutId = currentWorkout.id,
                        orderIndex = currentWorkout.exercises.size
                    )
                    val updatedExercises = currentWorkout.exercises + exerciseWithCorrectIds
                    
                    copy(
                        currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                        hasUnsavedChanges = true
                    )
                }
            }
        }
    }
    
    /**
     * 🔥 NEW: Adds an exercise to both the active session and UI state
     * This ensures proper synchronization between session and workout representations
     * 🔥 FIXED: Added duplicate exercise prevention guards
     */
    private fun addExerciseToActiveSession(exercise: Exercise, session: ActiveWorkoutSession) {
        viewModelScope.launch {
            try {
                // 🔥 ENHANCED GUARD: Check if exercise with same name already exists (prevents duplicates within same session)
                // Note: We check by name since each session exercise now has unique IDs
                val duplicateByName = session.exercises.any { it.name == exercise.libraryExercise.name }
                if (duplicateByName) {
                    Timber.w("🔥 NAME-DUPLICATE-GUARD: Exercise with name '${exercise.libraryExercise.name}' already exists in session. Skipping addition.")
                    return@launch
                }
                
                // 🔥 ENHANCED GUARD: Check if exercise already exists in current workout UI state by name
                val currentWorkout = _uiState.value.currentWorkout
                val exerciseInWorkout = currentWorkout?.exercises?.any { it.libraryExercise.name == exercise.libraryExercise.name } ?: false
                if (exerciseInWorkout) {
                    Timber.w("🔥 DUPLICATE-GUARD: Exercise '${exercise.libraryExercise.name}' already exists in workout UI. Skipping addition.")
                    return@launch
                }
                
                Timber.i("🔥 DUPLICATE-CHECK: Exercise '${exercise.libraryExercise.name}' (${exercise.id}) is unique, proceeding with addition")
                
                // 🔥 DEBUG: Log state before addition
                debugLogSessionState("BEFORE_EXERCISE_ADD")
                Timber.d("🔥 ADDING-EXERCISE: Adding '${exercise.libraryExercise.name}' to session with ${session.exercises.size} existing exercises")
                
                // Convert Exercise to SessionExercise for the session
                val sessionExercise = convertExerciseToSessionExercise(exercise, session.exercises.size)
                
                // Add to session via repository (authoritative source)
                val result = activeWorkoutSessionRepository.addExerciseToSession(session.id, sessionExercise)
                
                if (result.isSuccess) {
                    val updatedSession = result.getOrNull()!!
                    
                    // Convert updated session back to workout for UI compatibility
                    val updatedWorkout = updatedSession.toCompletedWorkout().copy(
                        status = WorkoutStatus.IN_PROGRESS,
                        endTime = null
                    )
                    
                    // Update UI state with synchronized data
                    updateState {
                        copy(
                            currentSession = updatedSession,
                            currentWorkout = updatedWorkout,
                            hasUnsavedChanges = true
                        )
                    }
                    
                    Timber.i("🔥 SUCCESS: Exercise '${exercise.libraryExercise.name}' added to session. Total exercises: ${updatedSession.exercises.size}")
                    Timber.d("🔥 FINAL-EXERCISE-LIST: ${updatedSession.exercises.map { "${it.name}(${it.sets.size} sets)" }}")
                    
                    // 🔥 DEBUG: Log state after addition
                    debugLogSessionState("AFTER_EXERCISE_ADD")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    Timber.e("🔥 ERROR: Failed to add exercise to session: $error")
                    updateState { copy(error = "Failed to add exercise: $error") }
                }
            } catch (e: Exception) {
                Timber.e(e, "🔥 CRITICAL-ERROR: Exception adding exercise to active session")
                updateState { copy(error = "Failed to add exercise: ${e.message}") }
            }
        }
    }
    
    /**
     * 🔥 DEBUG TOOL: Logs current session state for debugging
     */
    private fun debugLogSessionState(context: String) {
        val currentState = _uiState.value
        val session = currentState.currentSession
        val workout = currentState.currentWorkout
        
        Timber.d("🔍 DEBUG-SESSION-STATE [$context]:")
        Timber.d("  │ Session ID: ${session?.id}")
        Timber.d("  │ Session exercises: ${session?.exercises?.size} [${session?.exercises?.map { "${it.name}(${it.exerciseId})" }?.joinToString(", ")}]")
        Timber.d("  │ Workout ID: ${workout?.id}")
        Timber.d("  │ Workout exercises: ${workout?.exercises?.size} [${workout?.exercises?.map { "${it.libraryExercise.name}(${it.id})" }?.joinToString(", ")}]")
        Timber.d("  │ Pending exercises: ${currentState.pendingExercises.size} [${currentState.pendingExercises.map { "${it.libraryExercise.name}(${it.id})" }.joinToString(", ")}]")
        Timber.d("  └ Processing flag: $isProcessingPending")
        
        // 🔥 NEW: Add synchronization validation
        validateSessionWorkoutSync(session, workout)
    }
    
    /**
     * 🔥 NEW: Validates that session and workout representations are synchronized
     */
    private fun validateSessionWorkoutSync(session: ActiveWorkoutSession?, workout: Workout?) {
        if (session == null || workout == null) return
        
        val sessionExerciseCount = session.exercises.size
        val workoutExerciseCount = workout.exercises.size
        
        if (sessionExerciseCount != workoutExerciseCount) {
            Timber.w("🔥 SYNC-WARNING: Exercise count mismatch - Session: $sessionExerciseCount, Workout: $workoutExerciseCount")
        }
        
        // Validate exercise names match
        session.exercises.forEachIndexed { index, sessionExercise ->
            val workoutExercise = workout.exercises.getOrNull(index)
            if (workoutExercise != null && sessionExercise.name != workoutExercise.libraryExercise.name) {
                Timber.w("🔥 SYNC-WARNING: Exercise name mismatch at index $index - Session: '${sessionExercise.name}', Workout: '${workoutExercise.libraryExercise.name}'")
            }
        }
        
        // Validate set counts for template exercises
        session.exercises.forEachIndexed { index, sessionExercise ->
            val workoutExercise = workout.exercises.getOrNull(index)
            if (workoutExercise != null) {
                val sessionSets = sessionExercise.sets.size
                val workoutSets = workoutExercise.sets.size
                if (sessionSets != workoutSets) {
                    Timber.w("🔥 SYNC-WARNING: Set count mismatch for '${sessionExercise.name}' - Session: $sessionSets sets, Workout: $workoutSets sets")
                }
            }
        }
    }
    
    /**
     * 🔥 NEW: Converts Exercise domain model to SessionExercise for session persistence
     */
    private fun convertExerciseToSessionExercise(exercise: Exercise, orderIndex: Int): SessionExercise {
        return SessionExercise.createBlank(
            exerciseId = exercise.id,
            name = exercise.libraryExercise.name,
            category = exercise.libraryExercise.primaryMuscleGroup,
            primaryMuscle = exercise.libraryExercise.primaryMuscleGroup,
            orderIndex = orderIndex,
            initialSets = exercise.sets.size.takeIf { it > 0 } ?: 1
        ).copy(
            notes = exercise.notes
        )
    }
    
    // Flag to prevent concurrent processing
    private var isProcessingPending = false
    
    // Mutex to prevent concurrent template session creation
    private val templateCreationMutex = Mutex()
    
    // Track recently deleted exercises to prevent ghost re-additions
    private val recentlyDeletedExercises = mutableSetOf<String>()
    
    // Track recently added exercises to prevent rapid duplicates 
    private val recentlyAddedExercises = mutableSetOf<String>()
    
    // Debounce mutex for exercise addition
    private val exerciseAdditionMutex = Mutex()
    
    /**
     * 🔥 FIX: Clears all workout state when starting a new session
     * This prevents exercises from previous sessions from appearing in new workouts
     */
    private fun clearWorkoutState() {
        Timber.i("🔥 STATE-CLEAR: Clearing workout state for new session")
        
        // Clear exercise tracking sets
        recentlyAddedExercises.clear()
        recentlyDeletedExercises.clear()
        
        // Reset processing flag
        isProcessingPending = false
        
        // Clear UI state
        updateState {
            copy(
                currentWorkout = null,
                currentSession = null,
                hasActiveWorkout = false,
                hasUnsavedChanges = false,
                sessionId = null,
                pendingExercises = emptyList(),
                isWorkoutFromTemplate = false,
                templateId = null,
                error = null,
                successMessage = null
            )
        }
        
        Timber.d("🔥 STATE-CLEAR: Workout state cleared successfully")
    }
    
    /**
     * Processes any pending exercises that were queued while workout was loading
     * Enhanced with retry mechanism to handle race conditions reliably
     */
    private fun processPendingExercises() {
        if (isProcessingPending) {
            Timber.d("🔍 PENDING-SKIP: Already processing pending exercises")
            return
        }
        
        Timber.i("🔥 PENDING-PROCESS: processPendingExercises called")
        isProcessingPending = true
        
        viewModelScope.launch {
            try {
                // Retry mechanism: Wait for workout to load with exponential backoff
            val maxRetries = 10
            var retryCount = 0
            
            while (retryCount < maxRetries) {
                val currentState = _uiState.value
                val currentWorkout = currentState.currentWorkout
                val pendingExercises = currentState.pendingExercises
                
                if (pendingExercises.isEmpty()) {
                    Timber.d("🔍 PENDING-EMPTY: No pending exercises to process")
                    return@launch
                }
                
                if (currentWorkout != null) {
                    // Workout loaded successfully, process pending exercises
                    Timber.i("🔥 PENDING-SUCCESS: Processing ${pendingExercises.size} pending exercises for workout '${currentWorkout.name}' (current exercises: ${currentWorkout.exercises.size})")
                    
                    // 🔥 FIXED: Check if we have an active session and use session-based processing
                    val currentSession = currentState.currentSession
                    if (currentSession != null) {
                        // Process pending exercises via session for proper synchronization
                        Timber.i("🔥 PENDING-SESSION: Processing ${pendingExercises.size} pending exercises via active session")
                        
                        // Clear pending exercises first to prevent reprocessing
                        updateState { copy(pendingExercises = emptyList()) }
                        
                        // 🔥 FIXED: Filter out exercises that already exist by name to prevent re-adding deleted exercises
                        val exercisesToAdd = pendingExercises.filter { pendingExercise ->
                            val exists = currentSession.exercises.any { it.name == pendingExercise.libraryExercise.name }
                            if (exists) {
                                Timber.w("🔥 PENDING-DUPLICATE: Exercise '${pendingExercise.libraryExercise.name}' already exists in session, skipping")
                            }
                            !exists
                        }
                        
                        Timber.i("🔥 PENDING-FILTERED: ${exercisesToAdd.size}/${pendingExercises.size} exercises will be added (duplicates filtered)")
                        
                        // Add each unique pending exercise via session (this ensures proper sync)
                        exercisesToAdd.forEach { exercise ->
                            addExerciseToActiveSession(exercise, currentSession)
                        }
                        return@launch
                    } else {
                        // Legacy processing for workouts without sessions
                        updateState {
                            // 🔥 FIXED: Filter out exercises that already exist by name to prevent re-adding deleted exercises
                            val uniquePendingExercises = pendingExercises.filter { pendingExercise ->
                                val exists = currentWorkout.exercises.any { it.libraryExercise.name == pendingExercise.libraryExercise.name }
                                if (exists) {
                                    Timber.w("🔥 PENDING-DUPLICATE: Exercise '${pendingExercise.libraryExercise.name}' already exists in workout, skipping")
                                }
                                !exists
                            }
                            
                            Timber.i("🔥 PENDING-FILTERED: ${uniquePendingExercises.size}/${pendingExercises.size} exercises will be added (duplicates filtered)")
                            
                            // Add all unique pending exercises to the workout with proper ordering
                            val exercisesWithCorrectIds = uniquePendingExercises.mapIndexed { index, exercise ->
                                val newOrderIndex = currentWorkout.exercises.size + index
                                val updatedExercise = exercise.copy(
                                    workoutId = currentWorkout.id,
                                    orderIndex = newOrderIndex
                                )
                                Timber.d("🔍 PENDING-ITEM: Mapping pending exercise '${exercise.libraryExercise.name}' to order index $newOrderIndex")
                                updatedExercise
                            }
                            
                            val updatedExercises = currentWorkout.exercises + exercisesWithCorrectIds
                            Timber.i("🔥 PENDING-COMPLETE: Successfully processed ${uniquePendingExercises.size} pending exercises. Final exercise count: ${updatedExercises.size}")
                            
                            copy(
                                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                                pendingExercises = emptyList(),
                                hasUnsavedChanges = true
                            )
                        }
                        return@launch
                    }
                }
                
                // Workout not ready yet, wait with exponential backoff
                retryCount++
                val delayMs = minOf(100L * (1 shl retryCount), 2000L) // 200ms, 400ms, 800ms, 1600ms, 2000ms max
                Timber.d("🔍 PENDING-RETRY: Attempt $retryCount/$maxRetries - currentWorkout still null, waiting ${delayMs}ms (pending count: ${pendingExercises.size})")
                kotlinx.coroutines.delay(delayMs)
            }
            
            // Max retries reached, log error and clear pending exercises to prevent infinite queue
            Timber.e("🔥 PENDING-TIMEOUT: Failed to process ${_uiState.value.pendingExercises.size} pending exercises after $maxRetries retries. Clearing pending queue.")
            updateState {
                copy(
                    pendingExercises = emptyList(),
                    error = "Failed to add exercises - workout session not ready"
                )
            }
            } finally {
                isProcessingPending = false
            }
        }
    }
    
    /**
     * Adds an exercise from the search selector to the workout
     */
    private fun addExerciseFromSelector(searchableExercise: SearchableExercise) {
        val exercise = convertSearchableExerciseToExercise(searchableExercise)
        addExercise(exercise)
        
        // Clear selection after adding
        updateState {
            copy(
                selectedExercise = null,
                exerciseSearchQuery = "",
                isExerciseSelectorExpanded = false
            )
        }
    }
    
    /**
     * Converts ExerciseLibrary to Exercise domain model
     */
    private fun convertExerciseLibraryToExercise(exerciseLibrary: ExerciseLibrary): Exercise {
        val currentWorkout = _uiState.value.currentWorkout
        val workoutId = currentWorkout?.id ?: WorkoutId.generate()
        
        return Exercise(
            id = com.example.liftrix.domain.model.ExerciseId.generate(),
            workoutId = workoutId,
            libraryExercise = exerciseLibrary,
            orderIndex = currentWorkout?.exercises?.size ?: 0,
            targetSets = null,
            targetReps = null,
            targetWeight = null,
            targetTime = null,
            targetDistance = null,
            sets = createInitialSetsForExercise(exerciseLibrary),
            notes = null,
            createdAt = java.time.Instant.now()
        )
    }
    
    
    /**
     * Creates exercise-type-aware initial sets with appropriate default metrics
     * 🔥 ENHANCED: Adapts defaults based on exercise characteristics
     */
    private fun createInitialSetsForExercise(exerciseLibrary: ExerciseLibrary): List<ExerciseSet> {
        return (1..1).map { setNumber ->
            when {
                // Cardio exercises: default to time-based
                exerciseLibrary.movementPattern.contains("cardio", ignoreCase = true) ||
                exerciseLibrary.equipment in listOf(Equipment.TREADMILL, Equipment.EXERCISE_BIKE) -> {
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = setNumber,
                        reps = null,
                        weight = null,
                        time = java.time.Duration.ofMinutes(5), // Default 5 minutes
                        distance = null,
                        completedAt = null,
                        notes = null
                    )
                }
                // Distance-based exercises
                exerciseLibrary.name.contains("run", ignoreCase = true) ||
                exerciseLibrary.name.contains("walk", ignoreCase = true) -> {
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = setNumber,
                        reps = null,
                        weight = null,
                        time = null,
                        distance = Distance.fromKilometers(1f), // Default 1km
                        completedAt = null,
                        notes = null
                    )
                }
                // Strength exercises: default to reps-based
                else -> {
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = setNumber,
                        reps = Reps(10), // Default 10 reps
                        weight = null,
                        time = null,
                        distance = null,
                        completedAt = null,
                        notes = null
                    )
                }
            }
        }
    }
    
    /**
     * Converts SearchableExercise to Exercise domain model
     */
    private fun convertSearchableExerciseToExercise(searchableExercise: SearchableExercise): Exercise {
        val currentWorkout = _uiState.value.currentWorkout
        val workoutId = currentWorkout?.id ?: WorkoutId.generate()
        
        return when (searchableExercise) {
            is SearchableExercise.LibraryExercise -> {
                val libraryExercise = searchableExercise.exercise
                Exercise(
                    id = com.example.liftrix.domain.model.ExerciseId.generate(),
                    workoutId = workoutId,
                    libraryExercise = libraryExercise,
                    orderIndex = currentWorkout?.exercises?.size ?: 0,
                    targetSets = null,
                    targetReps = null,
                    targetWeight = null,
                    targetTime = null,
                    targetDistance = null,
                    sets = createInitialSetsForExercise(libraryExercise),
                    notes = null,
                    createdAt = java.time.Instant.now()
                )
            }
            is SearchableExercise.CustomExercise -> {
                val customExercise = searchableExercise.exercise
                Exercise(
                    id = com.example.liftrix.domain.model.ExerciseId.generate(),
                    workoutId = workoutId,
                    libraryExercise = com.example.liftrix.domain.model.ExerciseLibrary(
                        id = customExercise.id.value,
                        name = customExercise.name,
                        primaryMuscleGroup = customExercise.primaryMuscle,
                        equipment = customExercise.equipment,
                        secondaryMuscleGroups = emptyList(),
                        movementPattern = "Custom Exercise",
                        difficultyLevel = 3,
                        instructions = customExercise.notes ?: "Custom exercise added by user",
                        isCompound = false,
                        searchableTerms = listOf(customExercise.name.lowercase())
                    ),
                    orderIndex = currentWorkout?.exercises?.size ?: 0,
                    targetSets = null,
                    targetReps = null,
                    targetWeight = null,
                    targetTime = null,
                    targetDistance = null,
                    sets = createInitialSetsForExercise(com.example.liftrix.domain.model.ExerciseLibrary(
                        id = customExercise.id.value,
                        name = customExercise.name,
                        primaryMuscleGroup = customExercise.primaryMuscle,
                        equipment = customExercise.equipment,
                        secondaryMuscleGroups = emptyList(),
                        movementPattern = "Custom Exercise",
                        difficultyLevel = 3,
                        instructions = customExercise.notes ?: "Custom exercise added by user",
                        isCompound = false,
                        searchableTerms = listOf(customExercise.name.lowercase())
                    )),
                    notes = customExercise.notes,
                    createdAt = java.time.Instant.now()
                )
            }
        }
    }
    
    /**
     * Updates exercise search query
     */
    private fun onExerciseSearchQueryChanged(query: String) {
        updateState { copy(exerciseSearchQuery = query) }
        
        // Perform search
        viewModelScope.launch {
            try {
                searchExercisesUseCase.search(query, Equipment.values().toSet())
                    .collect { exercises ->
                        updateState { copy(availableExercises = exercises) }
                    }
            } catch (error: Exception) {
                Timber.e(error, "Error searching exercises")
            }
        }
    }
    
    /**
     * Updates selected exercise
     */
    private fun onExerciseSelected(exercise: SearchableExercise?) {
        updateState { copy(selectedExercise = exercise) }
    }
    
    /**
     * Updates exercise selector expanded state
     */
    private fun onExerciseSelectorExpandedChanged(expanded: Boolean) {
        updateState { copy(isExerciseSelectorExpanded = expanded) }
    }

    /**
     * Removes an exercise from the current workout
     * 🔥 FIXED: Now persists exercise removal to database session to prevent reappearing
     */
    private fun removeExercise(exerciseIndex: Int) {
        val currentState = _uiState.value
        val currentSession = currentState.currentSession
        val currentWorkout = currentState.currentWorkout
        
        if (currentWorkout == null || exerciseIndex !in currentWorkout.exercises.indices) {
            Timber.w("🔥 REMOVE-GUARD: Invalid exercise index $exerciseIndex or no current workout")
            return
        }
        
        val exerciseToRemove = currentWorkout.exercises[exerciseIndex]
        val exerciseId = exerciseToRemove.id.value
        
        // 🔥 TRACKING: Add to recently deleted set to prevent ghost re-additions
        recentlyDeletedExercises.add(exerciseId)
        
        // 🔥 CLEANUP: Remove from recently added set if it was there
        recentlyAddedExercises.remove(exerciseId)
        
        // 🔥 CLEANUP: Clear from recently deleted after a delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(30000) // 30 second grace period for deletions (increased from 10s)
            recentlyDeletedExercises.remove(exerciseId)
            Timber.d("🔥 CLEANUP: Removed '${exerciseToRemove.libraryExercise.name}' from deleted tracking after grace period")
        }
        
        if (currentSession != null) {
            // Session-based workout: Remove from both session and UI state
            Timber.i("🔥 REMOVE-SESSION: Removing exercise '${exerciseToRemove.libraryExercise.name}' (ID: $exerciseId) from session ${currentSession.id}")
            
            viewModelScope.launch {
                try {
                    // 🔥 DEBUG: Log state before removal
                    debugLogSessionState("BEFORE_EXERCISE_REMOVE")
                    
                    // Find the corresponding session exercise by exerciseId
                    val sessionExercise = currentSession.exercises.find { it.exerciseId == exerciseToRemove.id }
                    if (sessionExercise == null) {
                        Timber.w("🔥 REMOVE-WARNING: Exercise '${exerciseToRemove.libraryExercise.name}' not found in session exercises")
                        // Fall back to UI-only removal
                        removeExerciseFromUI(exerciseIndex)
                        return@launch
                    }
                    
                    // Remove from session via repository (authoritative source)
                    val result = activeWorkoutSessionRepository.removeExerciseFromSession(
                        sessionId = currentSession.id,
                        exerciseId = sessionExercise.exerciseId
                    )
                    
                    if (result.isSuccess) {
                        val updatedSession = result.getOrNull()!!
                        
                        // Convert updated session back to workout for UI compatibility
                        val updatedWorkout = updatedSession.toCompletedWorkout().copy(
                            status = WorkoutStatus.IN_PROGRESS,
                            endTime = null
                        )
                        
                        // Update UI state with synchronized data
                        updateState {
                            copy(
                                currentSession = updatedSession,
                                currentWorkout = updatedWorkout,
                                hasUnsavedChanges = true
                            )
                        }
                        
                        Timber.i("🔥 REMOVE-SUCCESS: Exercise '${exerciseToRemove.libraryExercise.name}' removed from session. Remaining exercises: ${updatedSession.exercises.size}")
                        
                        // 🔥 DEBUG: Log state after removal
                        debugLogSessionState("AFTER_EXERCISE_REMOVE")
                        
                        // 🔥 PERSISTENCE: Trigger immediate session save to ensure changes are persisted
                        try {
                            activeWorkoutSessionRepository.updateSession(updatedSession)
                            Timber.d("🔥 SAVE-SUCCESS: Session changes persisted to database")
                        } catch (e: Exception) {
                            Timber.w(e, "🔥 SAVE-WARNING: Failed to immediately save session, but auto-save will handle it")
                        }
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        Timber.e("🔥 REMOVE-ERROR: Failed to remove exercise from session: $error")
                        updateState { copy(error = "Failed to remove exercise: $error") }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🔥 REMOVE-EXCEPTION: Exception removing exercise from session")
                    updateState { copy(error = "Failed to remove exercise: ${e.message}") }
                }
            }
        } else {
            // Legacy workout: Remove from UI only (existing behavior)
            Timber.i("🔥 REMOVE-LEGACY: Removing exercise '${exerciseToRemove.libraryExercise.name}' from UI state only")
            removeExerciseFromUI(exerciseIndex)
        }
    }
    
    /**
     * 🔥 NEW: Helper function for UI-only exercise removal (legacy behavior)
     */
    private fun removeExerciseFromUI(exerciseIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                removeAt(exerciseIndex)
            }
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Adds a set to the specified exercise
     * 🔥 FIXED: Provides appropriate default metrics based on exercise type
     */
    private fun addSetToExercise(exerciseIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            val exerciseName = exercise.libraryExercise.name
            
            // 🔥 FIX: Smart default metrics based on exercise type
            val newSet = when {
                // Time-based exercises
                exerciseName.contains("plank", ignoreCase = true) ||
                exerciseName.contains("hold", ignoreCase = true) -> {
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = exercise.sets.size + 1,
                        time = java.time.Duration.ofSeconds(30) // Default 30 seconds
                    )
                }
                // Distance-based exercises  
                exerciseName.contains("run", ignoreCase = true) ||
                exerciseName.contains("walk", ignoreCase = true) -> {
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = exercise.sets.size + 1,
                        distance = Distance.fromKilometers(1f) // Default 1km
                    )
                }
                // Default to reps-based exercises
                else -> {
                    ExerciseSet(
                        id = ExerciseSetId.generate(),
                        setNumber = exercise.sets.size + 1,
                        reps = Reps(10) // Default 10 reps
                    )
                }
            }
            
            val updatedSets = exercise.sets + newSet
            val updatedExercise = exercise.copy(sets = updatedSets)
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                set(exerciseIndex, updatedExercise)
            }
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Updates a specific set in an exercise
     */
    private fun updateExerciseSet(exerciseIndex: Int, setIndex: Int, set: ExerciseSet) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            if (setIndex !in exercise.sets.indices) return@updateState this
            
            val updatedSets = exercise.sets.toMutableList().apply {
                set(setIndex, set)
            }
            val updatedExercise = exercise.copy(sets = updatedSets)
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                set(exerciseIndex, updatedExercise)
            }
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Removes a set from the specified exercise
     * 🔥 FIXED: Now uses proper domain method to ensure set number validation
     */
    private fun removeSetFromExercise(exerciseIndex: Int, setIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            if (setIndex !in exercise.sets.indices) return@updateState this
            
            // 🔥 FIX: Get the set ID to remove and use proper domain method
            val setToRemove = exercise.sets[setIndex]
            val updatedExercise = try {
                exercise.removeSet(setToRemove.id)  // Uses proper domain method with renumbering
            } catch (e: Exception) {
                // 🔥 FALLBACK: If domain method fails, manually normalize set numbers
                Timber.w(e, "🔥 FALLBACK: Domain removeSet failed, using manual normalization")
                val updatedSets = exercise.sets.toMutableList().apply {
                    removeAt(setIndex)
                }.mapIndexed { index, set -> set.copy(setNumber = index + 1) }  // Manual renumbering
                exercise.copy(sets = updatedSets)
            }
            
            val updatedExercises = currentWorkout.exercises.toMutableList().apply {
                set(exerciseIndex, updatedExercise)
            }
            
            Timber.i("🔥 SET-REMOVE: Removed set ${setIndex + 1} from exercise '${exercise.libraryExercise.name}'. Remaining sets: ${updatedExercise.sets.size}")
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                hasUnsavedChanges = true
            )
        }
    }

    /**
     * Creates a new empty workout for the current session
     */
    private suspend fun createNewWorkout(sessionId: String? = null) {
        try {
            val user = authRepository.currentUser.first() ?: return
            
            val now = java.time.Instant.now()
            val newWorkout = Workout(
                userId = user.uid,
                id = WorkoutId.generate(),
                name = "Active Workout",
                date = java.time.LocalDate.now(),
                exercises = emptyList(),
                status = WorkoutStatus.IN_PROGRESS,
                startTime = now,
                endTime = null,
                createdAt = now,
                updatedAt = now
            )
            
            updateState { 
                copy(
                    currentWorkout = newWorkout,
                    hasActiveWorkout = true,
                    hasUnsavedChanges = true,
                    sessionId = sessionId ?: "session_${System.currentTimeMillis()}"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create new workout")
            updateState { copy(error = "Failed to create workout: ${e.message}") }
        }
    }

    /**
     * Saves the current workout to the repository
     */
    private fun saveCurrentWorkout() {
        viewModelScope.launch {
            try {
                val currentSession = _uiState.value.currentSession
                val currentWorkout = _uiState.value.currentWorkout
                
                if (currentSession == null && currentWorkout == null) {
                    updateState { copy(error = "No workout to save") }
                    return@launch
                }
                
                updateState { copy(isSaving = true) }
                
                // Use proper session completion flow if we have an active session
                if (currentSession != null) {
                    Timber.d("Completing workout session: ${currentSession.id}")
                    val result = activeWorkoutSessionRepository.completeSession(currentSession.id)
                    
                    if (result.isSuccess) {
                        val completedWorkout = result.getOrNull()
                        
                        // End the live session
                        liveWorkoutSessionManager.endLiveSession()
                        
                        updateState { 
                            copy(
                                isSaving = false,
                                hasUnsavedChanges = false,
                                currentWorkout = completedWorkout,
                                currentSession = null, // Clear session after completion
                                successMessage = "Workout saved successfully"
                            )
                        }
                        Timber.i("Workout session completed successfully: ${currentSession.id}")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        updateState { 
                            copy(
                                isSaving = false,
                                error = "Failed to complete workout session: $error"
                            )
                        }
                        Timber.e("Failed to complete workout session: ${currentSession.id}, error: $error")
                    }
                } else {
                    // Fallback to direct workout save for legacy workouts without sessions
                    val workout = currentWorkout!!
                    
                    // For template workouts, allow saving even without exercises
                    val isTemplateWorkout = _uiState.value.isWorkoutFromTemplate
                    if (workout.exercises.isEmpty() && !isTemplateWorkout) {
                        updateState { copy(error = "Cannot save workout without exercises", isSaving = false) }
                        return@launch
                    }
                    
                    // Update workout status and end time
                    val completedWorkout = workout.copy(
                        status = WorkoutStatus.COMPLETED,
                        endTime = java.time.Instant.now(),
                        updatedAt = java.time.Instant.now()
                    )
                    
                    val result = workoutRepository.saveWorkout(completedWorkout)
                    if (result.isSuccess) {
                        updateState { 
                            copy(
                                isSaving = false,
                                hasUnsavedChanges = false,
                                currentWorkout = completedWorkout,
                                successMessage = "Workout saved successfully"
                            )
                        }
                        Timber.i("Workout saved successfully (legacy mode): ${completedWorkout.id}")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Unknown error"
                        updateState { 
                            copy(
                                isSaving = false,
                                error = "Failed to save workout: $error"
                            )
                        }
                        Timber.e("Failed to save workout: ${completedWorkout.id}, error: $error")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save workout")
                updateState { 
                    copy(
                        isSaving = false,
                        error = "Failed to save workout: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Saves the current session as a workout template
     */
    private fun saveCurrentWorkoutAsTemplate(templateName: String, templateDescription: String?) {
        viewModelScope.launch {
            try {
                val session = _uiState.value.currentSession ?: return@launch
                
                updateState { copy(isSaving = true) }
                
                val result = createTemplateFromSessionUseCase(session, templateName, templateDescription)
                if (result.isSuccess) {
                    // Also save the workout to history
                    _uiState.value.currentWorkout?.let { workout ->
                        workoutRepository.saveWorkout(workout)
                    }
                    
                    updateState { 
                        copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            successMessage = "Template '$templateName' created successfully"
                        )
                    }
                } else {
                    updateState { 
                        copy(
                            isSaving = false,
                            error = "Failed to create template: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save workout as template")
                updateState { 
                    copy(
                        isSaving = false,
                        error = "Failed to create template: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Loads a specific workout by ID
     */
    private fun loadWorkout(workoutId: WorkoutId) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
                val user = authRepository.currentUser.first()
                if (user == null) {
                    updateState { 
                        copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }

                val workout = workoutRepository.getWorkoutByIdForUser(workoutId, user.uid)
                updateState {
                    copy(
                        isLoading = false,
                        currentWorkout = workout,
                        hasActiveWorkout = workout != null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load workout $workoutId")
                updateState { 
                    copy(
                        isLoading = false,
                        error = "Failed to load workout: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Formats session time for display
     */
    private fun formatSessionTime(timerState: WorkoutTimerService.TimerServiceState): String {
        return when (val state = timerState.timerState) {
            is WorkoutTimerService.TimerState.SessionRunning -> formatTime(state.elapsedSeconds)
            is WorkoutTimerService.TimerState.SessionPaused -> formatTime(state.pausedAtSeconds)
            else -> "00:00"
        }
    }

    /**
     * Formats rest time for display
     */
    private fun formatRestTime(timerState: WorkoutTimerService.TimerServiceState): String {
        return when (val state = timerState.timerState) {
            is WorkoutTimerService.TimerState.RestActive -> formatTime(state.remainingSeconds.toLong())
            is WorkoutTimerService.TimerState.RestPaused -> formatTime(state.remainingSeconds.toLong())
            else -> ""
        }
    }

    /**
     * Formats time in seconds to HH:MM:SS or MM:SS format
     */
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    /**
     * Clears the current error state
     */
    private fun clearError() {
        updateState { copy(error = null, connectionError = null) }
    }

    /**
     * Dismisses the current success message
     */
    private fun dismissMessage() {
        updateState { copy(successMessage = null) }
    }

    /**
     * Updates the UI state using the provided transform function
     */
    private fun updateState(transform: ActiveWorkoutState.() -> ActiveWorkoutState) {
        _uiState.value = _uiState.value.transform()
    }

    override fun onCleared() {
        super.onCleared()
        // Unbind from timer service when ViewModel is cleared
        timerServiceManager.unbindService()
    }
}

/**
 * UI state for the active workout screen
 */
data class ActiveWorkoutState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isTimerServiceConnected: Boolean = false,
    val isSessionActive: Boolean = false,
    val hasActiveWorkout: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val currentWorkout: Workout? = null,
    val currentSession: com.example.liftrix.domain.model.ActiveWorkoutSession? = null,
    val sessionId: String? = null,
    val showExitConfirmation: Boolean = false,
    val timerState: WorkoutTimerService.TimerServiceState = WorkoutTimerService.TimerServiceState(),
    val formattedSessionTime: String = "00:00",
    val formattedRestTime: String = "",
    val error: String? = null,
    val connectionError: String? = null,
    val successMessage: String? = null,
    // Exercise search functionality
    val availableExercises: List<SearchableExercise> = emptyList(),
    val exerciseSearchQuery: String = "",
    val selectedExercise: SearchableExercise? = null,
    val isExerciseSelectorExpanded: Boolean = false,
    // Pending exercises to add when workout is loaded
    val pendingExercises: List<Exercise> = emptyList(),
    // Track if this workout was created from a template
    val isWorkoutFromTemplate: Boolean = false,
    // 🔥 NEW: Track template ID to prevent duplicate initialization
    val templateId: String? = null
)

/**
 * Events for the active workout screen
 */
sealed class ActiveWorkoutEvent {
    data object StartSession : ActiveWorkoutEvent()
    data object PauseSession : ActiveWorkoutEvent()
    data object ResumeSession : ActiveWorkoutEvent()
    data object StopSession : ActiveWorkoutEvent()
    data class StartSessionFromTemplate(val templateId: String) : ActiveWorkoutEvent()
    data object StartBlankSession : ActiveWorkoutEvent()
    data class ResumeExistingSession(val sessionId: String) : ActiveWorkoutEvent()
    data class StartRest(val restTimer: RestTimer) : ActiveWorkoutEvent()
    data object SkipRest : ActiveWorkoutEvent()
    data class AddExercise(val exercise: Exercise) : ActiveWorkoutEvent()
    data class AddExerciseFromSelector(val searchableExercise: SearchableExercise) : ActiveWorkoutEvent()
    data class OnExerciseSearchQueryChanged(val query: String) : ActiveWorkoutEvent()
    data class OnExerciseSelected(val exercise: SearchableExercise?) : ActiveWorkoutEvent()
    data class OnExerciseSelectorExpandedChanged(val expanded: Boolean) : ActiveWorkoutEvent()
    data class RemoveExercise(val exerciseIndex: Int) : ActiveWorkoutEvent()
    data class AddSet(val exerciseIndex: Int) : ActiveWorkoutEvent()
    data class UpdateSet(val exerciseIndex: Int, val setIndex: Int, val set: ExerciseSet) : ActiveWorkoutEvent()
    data class RemoveSet(val exerciseIndex: Int, val setIndex: Int) : ActiveWorkoutEvent()
    data object SaveWorkout : ActiveWorkoutEvent()
    data class SaveAsTemplate(val templateName: String, val templateDescription: String?) : ActiveWorkoutEvent()
    data class LoadWorkout(val workoutId: WorkoutId) : ActiveWorkoutEvent()
    data object ShowExitConfirmation : ActiveWorkoutEvent()
    data object HideExitConfirmation : ActiveWorkoutEvent()
    data object ClearError : ActiveWorkoutEvent()
    data object DismissMessage : ActiveWorkoutEvent()
}