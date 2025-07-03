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
    private val templateRepository: WorkoutTemplateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveWorkoutState())
    val uiState: StateFlow<ActiveWorkoutState> = _uiState.asStateFlow()

    init {
        bindTimerService()
        observeTimerState()
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
     */
    fun addExerciseById(exerciseId: String, isCustomExercise: Boolean) {
        viewModelScope.launch {
            try {
                val searchableExercises = searchExercisesUseCase.search("", Equipment.values().toSet()).first()
                val searchableExercise = searchableExercises.find { searchable ->
                    when (searchable) {
                        is SearchableExercise.LibraryExercise -> !isCustomExercise && searchable.exercise.id == exerciseId
                        is SearchableExercise.CustomExercise -> isCustomExercise && searchable.exercise.id.value == exerciseId
                    }
                }
                
                searchableExercise?.let { exercise ->
                    addExerciseFromSelector(exercise)
                }
            } catch (e: Exception) {
                updateState { copy(error = "Failed to add exercise: ${e.message}") }
            }
        }
    }

    val sessionId: String?
        get() = _uiState.value.sessionId

    /**
     * Starts a session from a workout template
     */
    private fun startSessionFromTemplateInternal(templateId: String) {
        viewModelScope.launch {
            try {
                updateState { copy(isLoading = true) }
                
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
                        isWorkoutFromTemplate = session.templateId != null
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
                    isWorkoutFromTemplate = true
                )
            }
            
            Timber.i("🔍 DEBUG: Template workout session created with ${createdSession.exercises.size} exercises. Session ID: ${createdSession.id.value}. Checking for pending exercises...")
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
                                isWorkoutFromTemplate = activeSession.templateId != null
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
     */
    private fun addExercise(exercise: Exercise) {
        Timber.d("🔍 DEBUG: addExercise called for '${exercise.libraryExercise.name}'")
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
    
    /**
     * Processes any pending exercises that were queued while workout was loading
     */
    private fun processPendingExercises() {
        Timber.d("🔍 DEBUG: processPendingExercises called")
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            val pendingExercises = this.pendingExercises
            
            if (pendingExercises.isEmpty()) {
                Timber.d("🔍 DEBUG: No pending exercises to process")
                return@updateState this
            }
            
            Timber.i("🔍 DEBUG: Processing ${pendingExercises.size} pending exercises for workout '${currentWorkout.name}'")
            // Add all pending exercises to the workout
            val exercisesWithCorrectIds = pendingExercises.mapIndexed { index, exercise ->
                exercise.copy(
                    workoutId = currentWorkout.id,
                    orderIndex = currentWorkout.exercises.size + index
                )
            }
            
            val updatedExercises = currentWorkout.exercises + exercisesWithCorrectIds
            Timber.i("🔍 DEBUG: Successfully processed pending exercises. New exercise count: ${updatedExercises.size}")
            
            copy(
                currentWorkout = currentWorkout.copy(exercises = updatedExercises),
                pendingExercises = emptyList(),
                hasUnsavedChanges = true
            )
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
            sets = createInitialSets(),
            notes = null,
            createdAt = java.time.Instant.now()
        )
    }
    
    /**
     * Creates initial sets for a new exercise (3 sets with default values)
     */
    private fun createInitialSets(): List<ExerciseSet> {
        return (1..3).map { setNumber ->
            ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = setNumber,
                reps = null,
                weight = null,
                distance = null,
                completedAt = null,
                notes = null
            )
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
                    sets = createInitialSets(),
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
                    sets = createInitialSets(),
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
     */
    private fun removeExercise(exerciseIndex: Int) {
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
     */
    private fun addSetToExercise(exerciseIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            val newSet = ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = exercise.sets.size + 1,
                reps = Reps.of(1) // Minimum required metric
            )
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
     */
    private fun removeSetFromExercise(exerciseIndex: Int, setIndex: Int) {
        updateState {
            val currentWorkout = this.currentWorkout ?: return@updateState this
            if (exerciseIndex !in currentWorkout.exercises.indices) return@updateState this
            
            val exercise = currentWorkout.exercises[exerciseIndex]
            if (setIndex !in exercise.sets.indices) return@updateState this
            
            val updatedSets = exercise.sets.toMutableList().apply {
                removeAt(setIndex)
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
    val isWorkoutFromTemplate: Boolean = false
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