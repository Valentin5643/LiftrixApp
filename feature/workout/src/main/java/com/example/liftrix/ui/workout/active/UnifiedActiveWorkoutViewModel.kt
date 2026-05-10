package com.example.liftrix.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import java.time.Instant
import com.example.liftrix.service.TimerServiceManager
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.domain.usecase.session.SessionOperationsUseCase
import com.example.liftrix.domain.usecase.template.TemplateCommandUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import com.example.liftrix.domain.usecase.workout.PreviousSetDataResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

/**
 * 
 * This ViewModel replaces the complex ActiveWorkoutViewModel with a simplified
 * version that uses UnifiedWorkoutSession as the single source of truth.
 * No more dual state management or complex synchronization.
 * 
 * Key improvements:
 * - Single source of truth from UnifiedWorkoutSessionManager
 * - Simplified state management with sealed classes
 * - Direct session operations without conversion
 * - Clean error handling
 * - Reactive UI updates
 */
@HiltViewModel
class UnifiedActiveWorkoutViewModel @Inject constructor(
    private val sessionManager: UnifiedWorkoutSessionManager,
    private val workoutTemplateRepository: TemplateRepository,
    private val authRepository: AuthRepository,
    private val sessionOperationsUseCase: SessionOperationsUseCase,
    private val templateCommandUseCase: TemplateCommandUseCase,
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val timerServiceManager: TimerServiceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<UnifiedActiveWorkoutUiState>(
        UnifiedActiveWorkoutUiState.Loading
    )
    val uiState: StateFlow<UnifiedActiveWorkoutUiState> = _uiState.asStateFlow()
    val timerState: StateFlow<WorkoutTimerService.TimerServiceState> = timerServiceManager.timerState
    private var pendingRestTimer: RestTimer? = null
    
    init {
        timerServiceManager.bindService().onFailure { error ->
            Timber.w(error, "Unable to bind workout timer service")
        }

        viewModelScope.launch {
            timerServiceManager.connectionState.collect { state ->
                if (state is TimerServiceManager.ConnectionState.Connected) {
                    ensureSessionTimerStarted()
                    startPendingRestTimerIfNeeded()
                }
            }
        }
    }

    val currentSession: StateFlow<UnifiedWorkoutSession?> = sessionManager.currentSession
    
    // Previous set data management
    private val _previousSetData = MutableStateFlow<Map<String, PreviousSetDataResponse>>(emptyMap())
    val previousSetData: StateFlow<Map<String, PreviousSetDataResponse>> = _previousSetData.asStateFlow()

    init {
        
        // Check initial session state and set appropriate UI state
        val currentSession = sessionManager.currentSession.value
        if (currentSession != null) {
            _uiState.value = UnifiedActiveWorkoutUiState.Success(currentSession)
        } else {
            _uiState.value = UnifiedActiveWorkoutUiState.NoSession
        }
        
        // Start observing session changes
        observeSessionState()
    }

    /**
     */
    private fun observeSessionState() {
        viewModelScope.launch {
            sessionManager.currentSession
                .combine(sessionManager.recoveryState) { session, recoveryState ->
                    Pair(session, recoveryState)
                }
                .collect { (session, recoveryState) ->
                    when {
                        recoveryState is UnifiedWorkoutSessionManager.RecoveryState.RecoveryError -> {
                            _uiState.value = UnifiedActiveWorkoutUiState.Error(
                                message = recoveryState.error
                            )
                        }
                        session != null -> {
                            // Load previous set data when session becomes active
                            if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE &&
                                _previousSetData.value.isEmpty() && session.exercises.isNotEmpty()) {
                                loadPreviousSetData()
                            }

                            if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE) {
                                ensureSessionTimerStarted()
                            }
                            
                            // Don't override WorkoutCompleted state when session is completed
                            val currentState = _uiState.value
                            if (currentState is UnifiedActiveWorkoutUiState.WorkoutCompleted && 
                                session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                                // Keep the WorkoutCompleted state - user needs to navigate away
                            } else {
                                _uiState.value = UnifiedActiveWorkoutUiState.Success(session)
                            }
                        }
                        else -> {
                            // No session exists - show appropriate state based on context
                            if (_uiState.value !is UnifiedActiveWorkoutUiState.Loading &&
                                _uiState.value !is UnifiedActiveWorkoutUiState.WorkoutCompleted) {
                                _uiState.value = UnifiedActiveWorkoutUiState.NoSession
                            }
                        }
                    }
                }
        }
    }

    /**
     */
    fun togglePauseResume() {
        val session = currentSession.value
        if (session == null) {
            Timber.w("Cannot toggle pause/resume - no active session")
            return
        }

        when (session.sessionStatus) {
            UnifiedWorkoutSession.SessionStatus.ACTIVE -> {
                sessionManager.pauseSession()
            }
            UnifiedWorkoutSession.SessionStatus.PAUSED -> {
                sessionManager.resumeSession()
            }
            UnifiedWorkoutSession.SessionStatus.COMPLETED -> {
                Timber.w("Cannot toggle pause/resume - session completed")
            }
            UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> {
                Timber.w("Cannot toggle pause/resume - session failed to save")
            }
        }
    }

    /**
     * Enhanced for unified screen integration with navigation callback support
     */
    fun completeWorkout() {
        viewModelScope.launch {
            try {
                // Set completing state
                val currentState = _uiState.value
                if (currentState is UnifiedActiveWorkoutUiState.Success) {
                    _uiState.value = currentState.copy(isCompleting = true)
                }
                
                // Capture session state BEFORE calling completeSession to avoid race condition
                val capturedSession = sessionManager.currentSession.value
                if (capturedSession == null) {
                    Timber.w("No session to complete")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "No active workout session"
                    )
                    return@launch
                }
                
                // Check conditions based on captured session state
                val isQuickWorkout = capturedSession.templateId == null && capturedSession.exercises.isNotEmpty()
                val isTemplateWithAddedExercises = capturedSession.templateId != null && 
                    hasExercisesAddedBeyondTemplate(capturedSession)
                
                if (isQuickWorkout) {
                    // Show Quick workout save dialog WITHOUT completing session yet
                    val currentSuccessState = _uiState.value as? UnifiedActiveWorkoutUiState.Success
                    if (currentSuccessState != null) {
                        val newState = currentSuccessState.copy(
                            isCompleting = false,
                            showSaveQuickWorkoutDialog = true
                        )
                        
                        _uiState.value = newState
                        
                        // Force trigger state change detection
                        val checkState = _uiState.value
                        if (checkState is UnifiedActiveWorkoutUiState.Success) {
                        }
                        
                        return@launch
                    } else {
                        Timber.e("Current UI state is not Success, cannot show dialog. Current state: ${_uiState.value::class.simpleName}")
                    }
                } else if (isTemplateWithAddedExercises) {
                    // Show template update dialog WITHOUT completing session yet
                    val currentSuccessState = _uiState.value as? UnifiedActiveWorkoutUiState.Success
                    if (currentSuccessState != null) {
                        _uiState.value = currentSuccessState.copy(
                            isCompleting = false,
                            showSaveAsTemplateDialog = true
                        )
                        return@launch
                    }
                }
                
                // No dialogs needed, proceed with direct completion
                completeSessionDirectly()
                
            } catch (e: Exception) {
                Timber.e(e, "Error completing workout")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to complete workout: ${e.message}"
                )
            }
        }
    }
    
    /**
     */
    private suspend fun completeSessionDirectly() {
        val success = sessionManager.completeSession()
        if (success) {
            timerServiceManager.stopTimer()
            Timber.i("Workout completion initiated")
            
            // Wait for the save to complete and get the saved workout ID
            delay(200) // Small delay to ensure save completes
            
            // Get the saved workout ID from the session manager after completion
            val savedWorkoutId = sessionManager.savedWorkoutId.value
            val workoutId = if (!savedWorkoutId.isNullOrEmpty()) {
                savedWorkoutId
            } else {
                // Fallback to session ID if saved workout ID is not available
                val currentSession = sessionManager.currentSession.value
                val fallbackId = currentSession?.id?.value ?: "unknown"
                Timber.w("No saved workout ID, using fallback: $fallbackId")
                fallbackId
            }
            
            // Show completion state - user will navigate away via UI buttons
            _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted(workoutId)
            
            // Check if session is in failed state after a delay
            delay(300) // Additional delay to ensure session state is updated
            val updatedSession = sessionManager.currentSession.value
            if (updatedSession?.sessionStatus == UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE) {
                Timber.w("Session failed to save, showing retry option")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Workout completed but failed to save. Tap to retry.",
                    isRetryable = true
                )
            }
            // Do NOT transition to NoSession - let user navigate via UI buttons
        } else {
            Timber.w("Failed to complete workout")
            _uiState.value = UnifiedActiveWorkoutUiState.Error(
                message = "Failed to complete workout. Please try again."
            )
        }
    }

    /**
     */
    fun discardWorkout() {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.currentSession.value
                Timber.d("[WORKOUT-DEBUG] discardWorkout requested sessionId=${currentSession?.id?.value} userId=${currentSession?.userId} status=${currentSession?.sessionStatus}")
                timerServiceManager.stopTimer()
                sessionManager.discardSession()
                Timber.i("Workout discarded")
            } catch (e: Exception) {
                Timber.e(e, "Error discarding workout")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to discard workout: ${e.message}"
                )
            }
        }
    }

    /**
     */
    fun removeExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                sessionManager.removeExerciseFromSession(ExerciseId(exerciseId))
            } catch (e: Exception) {
                Timber.e(e, "Error removing exercise from session")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to remove exercise: ${e.message}"
                )
            }
        }
    }

    /**
     * Adds a new set to an exercise
     */
    fun addSetToExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.getCurrentSession()
                if (currentSession == null) {
                    Timber.e("Cannot add set - no active session")
                    return@launch
                }

                val exercise = currentSession.exercises.find { it.exerciseId.value == exerciseId }
                if (exercise == null) {
                    Timber.e("Cannot add set - exercise not found: $exerciseId")
                    return@launch
                }

                val updatedExercise = exercise.addSet()
                sessionManager.updateExerciseInSession(ExerciseId(exerciseId), updatedExercise)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add set to exercise: $exerciseId")
                _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to add set")
            }
        }
    }

    /**
     * Updates a specific set in an exercise
     */
    fun updateSetInExercise(exerciseId: String, setNumber: Int, updatedSet: SessionSet) {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.getCurrentSession()
                if (currentSession == null) {
                    Timber.e("Cannot update set - no active session")
                    return@launch
                }

                val exercise = currentSession.exercises.find { it.exerciseId.value == exerciseId }
                if (exercise == null) {
                    Timber.e("Cannot update set - exercise not found: $exerciseId")
                    return@launch
                }

                val setIndex = exercise.sets.indexOfFirst { it.setNumber == setNumber }
                if (setIndex == -1) {
                    Timber.e("Cannot update set - set not found: $setNumber in exercise $exerciseId")
                    return@launch
                }

                val updatedSets = exercise.sets.toMutableList()
                val previousSet = updatedSets[setIndex]
                updatedSets[setIndex] = updatedSet
                val updatedExercise = exercise.copy(sets = updatedSets)
                val updatedSession = currentSession.copy(
                    exercises = currentSession.exercises.map { sessionExercise ->
                        if (sessionExercise.exerciseId.value == exerciseId) updatedExercise else sessionExercise
                    }
                )
                
                // 🔥 SETS-DEBUG: Log set update in ViewModel
                Timber.d("[SETS-DEBUG-VM] Updating set ${setNumber} in exercise '${exercise.name}': reps=${updatedSet.actualReps}, weight=${updatedSet.actualWeight}")
                Timber.d("[SETS-DEBUG-VM] Exercise now has ${updatedSets.size} sets total")
                
                sessionManager.updateExerciseInSession(ExerciseId(exerciseId), updatedExercise)

                maybeStartRestTimer(
                    previousSet = previousSet,
                    updatedSet = updatedSet,
                    exercise = exercise,
                    updatedSession = updatedSession
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to update set in exercise: $exerciseId")
                _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to update set")
            }
        }
    }

    fun pauseRestTimer() {
        timerServiceManager.pauseTimer().onFailure { error ->
            Timber.w(error, "Failed to pause rest timer")
        }
    }

    fun resumeRestTimer() {
        timerServiceManager.resumeTimer().onFailure { error ->
            Timber.w(error, "Failed to resume rest timer")
        }
    }

    fun skipRestTimer() {
        timerServiceManager.skipRest().onFailure { error ->
            Timber.w(error, "Failed to skip rest timer")
        }
    }

    fun adjustRestTimerBy(deltaSeconds: Int) {
        timerServiceManager.adjustRestBySeconds(deltaSeconds).onFailure { error ->
            Timber.w(error, "Failed to adjust rest timer")
        }
    }

    private fun ensureSessionTimerStarted() {
        val session = sessionManager.currentSession.value ?: return
        if (session.sessionStatus != UnifiedWorkoutSession.SessionStatus.ACTIVE) return

        if (timerServiceManager.getCurrentTimerState().timerState is WorkoutTimerService.TimerState.Stopped) {
            timerServiceManager.startSession().onFailure { error ->
                Timber.w(error, "Failed to start workout session timer")
            }
        }
    }

    private fun maybeStartRestTimer(
        previousSet: SessionSet,
        updatedSet: SessionSet,
        exercise: SessionExercise,
        updatedSession: UnifiedWorkoutSession
    ) {
        val becameComplete = previousSet.completedAt == null && updatedSet.completedAt != null
        if (!becameComplete) return
        if (updatedSession.exercises.all { sessionExercise ->
                sessionExercise.sets.all { set -> set.completedAt != null }
            }
        ) {
            return
        }

        val durationSeconds = exercise.restTimeSeconds ?: SessionExercise.DEFAULT_REST_TIME_SECONDS
        if (durationSeconds <= 0) return

        startRestTimerWhenReady(RestTimer(durationSeconds = durationSeconds))
    }

    private fun startRestTimerWhenReady(restTimer: RestTimer) {
        if (!timerServiceManager.isServiceBound()) {
            pendingRestTimer = restTimer
            timerServiceManager.bindService().onFailure { error ->
                Timber.w(error, "Failed to bind timer service for pending rest timer")
            }
            return
        }

        timerServiceManager.startRestTimer(restTimer).onFailure { error ->
            pendingRestTimer = restTimer
            Timber.w(error, "Failed to start rest timer; queued until service reconnects")
            timerServiceManager.bindService().onFailure { bindError ->
                Timber.w(bindError, "Failed to rebind timer service for rest timer")
            }
        }
    }

    private fun startPendingRestTimerIfNeeded() {
        val restTimer = pendingRestTimer ?: return
        pendingRestTimer = null

        timerServiceManager.startRestTimer(restTimer).onFailure { error ->
            pendingRestTimer = restTimer
            Timber.w(error, "Failed to start pending rest timer")
        }
    }

    /**
     * Moves to the next exercise in the session
     */
    fun moveToNextExercise() {
        viewModelScope.launch {
            try {
                sessionManager.moveToNextExercise()
            } catch (e: Exception) {
                Timber.e(e, "Error moving to next exercise")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to move to next exercise: ${e.message}"
                )
            }
        }
    }

    /**
     * Moves to the previous exercise in the session
     */
    fun moveToPreviousExercise() {
        viewModelScope.launch {
            try {
                sessionManager.moveToPreviousExercise()
            } catch (e: Exception) {
                Timber.e(e, "Error moving to previous exercise")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to move to previous exercise: ${e.message}"
                )
            }
        }
    }

    /**
     * Updates session notes
     */
    fun updateNotes(notes: String?) {
        viewModelScope.launch {
            try {
                sessionManager.updateSessionNotes(notes)
            } catch (e: Exception) {
                Timber.e(e, "Error updating session notes")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to update notes: ${e.message}"
                )
            }
        }
    }

    /**
     * Adds an exercise from the library to the current session
     */
    fun addExerciseToSession(exerciseLibrary: ExerciseLibrary) {
        
        viewModelScope.launch {
            try {
                // Use proper use case instead of direct session manager call
                val exerciseId = ExerciseId.fromString(exerciseLibrary.id)
                val result = sessionOperationsUseCase.addExercise(exerciseId)
                
                result.fold(
                    onSuccess = {
                        
                        // Verify the exercise was actually added
                        val updatedSession = sessionManager.currentSession.value
                        if (updatedSession != null) {
                            // Load previous data for the newly added exercise
                            refreshPreviousSetDataForExercise(exerciseLibrary.id)
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Failed to add exercise: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error adding exercise to session")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to add exercise: ${e.message}"
                )
            }
        }
    }

    /**
     * Starts a workout session from a template
     */
    fun startTemplateWorkout(templateId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = UnifiedActiveWorkoutUiState.Loading
                
                // Clear any existing session first
                val existingSession = sessionManager.currentSession.value
                if (existingSession != null) {
                    sessionManager.discardSession()
                    delay(100)
                }
                
                // Template loading handled by session creation service
                // For now, create a blank session with template reference
                createTemplateSession(templateId)
                
                // Timeout fallback
                delay(2000)
                val newSession = sessionManager.currentSession.value
                if (newSession == null) {
                    Timber.e("Session not created after 2 seconds")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Failed to create template workout session"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting template workout")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to start template workout: ${e.message}"
                )
            }
        }
    }

    /**
     * Explicitly starts a blank workout session when user chooses "Quick Workout"
     */
    fun startBlankWorkout() {
        viewModelScope.launch {
            try {
                _uiState.value = UnifiedActiveWorkoutUiState.Loading
                
                // Force clear any existing session to avoid conflicts
                val existingSession = sessionManager.currentSession.value
                if (existingSession != null) {
                    sessionManager.discardSession()
                    // Add small delay to ensure session is cleared
                    delay(100)
                }
                
                createBlankSession()
                
                // Add timeout fallback - if no session appears within 2 seconds, something is wrong
                delay(2000)
                val newSession = sessionManager.currentSession.value
                if (newSession == null) {
                    Timber.e("Session not created after 2 seconds - forcing error state")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Failed to create workout session - timeout"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting blank workout")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to start workout: ${e.message}"
                )
            }
        }
    }

    /**
     * Creates a blank workout session
     */
    private fun createBlankSession() {
        viewModelScope.launch {
            try {
                
                // Get current user ID
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    timber.log.Timber.e("No authenticated user - cannot create session")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error("Please sign in to start a workout")
                    return@launch
                }
                
                val blankSession = UnifiedWorkoutSession(
                    id = WorkoutSessionId.generate(),
                    userId = userId.value,
                    name = "Quick Workout",
                    templateId = null,
                    exercises = emptyList(),
                    currentExerciseIndex = 0,
                    sessionStatus = UnifiedWorkoutSession.SessionStatus.ACTIVE,
                    startedAt = Instant.now(),
                    endedAt = null,
                    elapsedTimeSeconds = 0,
                    notes = null,
                    lastModified = Instant.now()
                )
                
                
                sessionManager.forceStartSession(blankSession)
                
            } catch (e: Exception) {
                _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to start workout: ${e.message}")
            }
        }
    }

    /**
     * Creates a session from a template with exercises loaded
     */
    private fun createTemplateSession(templateId: String) {
        viewModelScope.launch {
            try {
                
                // Get current user ID
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = UnifiedActiveWorkoutUiState.Error("Please sign in to use templates")
                    return@launch
                }
                
                // Load the template from repository
                val templateResult = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId.fromString(templateId),
                    userId.value
                )
                
                val template = templateResult.fold(
                    onSuccess = { it },
                    onFailure = { exception ->
                        _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to load template: ${exception.message}")
                        return@launch
                    }
                )
                
                if (template == null) {
                    _uiState.value = UnifiedActiveWorkoutUiState.Error("Template not found")
                    return@launch
                }
                
                
                // Debug each template exercise in detail
                template.exercises.forEachIndexed { index, templateExercise ->
                }
                
                // Convert template exercises to session exercises
                val sessionExercises = template.exercises.mapIndexed { index, templateExercise ->
                    // Create initial sets based on targetSets with better defaults
                    val numberOfSets = templateExercise.targetSets ?: 3 // Default to 3 sets if not specified
                    
                    val initialSets = (1..numberOfSets).map { setNumber ->
                        val set = SessionSet(
                            setNumber = setNumber,
                            targetReps = templateExercise.targetReps?.count,
                            targetWeight = templateExercise.targetWeight,
                            actualReps = null,
                            actualWeight = null,
                            completedAt = null,
                            skipped = false
                        )
                        set
                    }
                    
                    SessionExercise(
                        exerciseId = templateExercise.exerciseId,
                        name = templateExercise.name,
                        category = templateExercise.primaryMuscle,
                        primaryMuscle = templateExercise.primaryMuscle,
                        equipment = templateExercise.equipment,
                        secondaryMuscles = emptySet(), // Template doesn't store secondary muscles
                        sets = initialSets,
                        orderIndex = index,
                        restTimeSeconds = templateExercise.restTimeSeconds ?: 60,
                        notes = templateExercise.notes,
                        isSuperset = false, // Template exercises don't have superset info
                        supersetWith = null,
                        lastModified = Instant.now()
                    )
                }
                
                // Create session with template exercises
                val templateSession = UnifiedWorkoutSession(
                    id = WorkoutSessionId.generate(),
                    userId = userId.value,
                    name = template.name,
                    templateId = templateId,
                    exercises = sessionExercises,
                    currentExerciseIndex = 0,
                    sessionStatus = UnifiedWorkoutSession.SessionStatus.ACTIVE,
                    startedAt = Instant.now(),
                    endedAt = null,
                    elapsedTimeSeconds = 0,
                    notes = null,
                    lastModified = Instant.now()
                )
                
                
                // Debug the final session exercises
                sessionExercises.forEachIndexed { index, sessionExercise ->
                    sessionExercise.sets.forEach { set ->
                    }
                }

                // Record template usage
                workoutTemplateRepository.recordTemplateUsage(WorkoutTemplateId.fromString(templateId), userId.value)

                sessionManager.forceStartSession(templateSession)
                
            } catch (e: Exception) {
                _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to load template: ${e.message}")
            }
        }
    }

    /**
     * Retries the current operation, including save retry for failed sessions
     */
    fun retry() {
        viewModelScope.launch {
            val currentSession = sessionManager.currentSession.value
            if (currentSession?.sessionStatus == UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE) {
                // Retry saving the failed session
                _uiState.value = UnifiedActiveWorkoutUiState.Loading
                
                val retrySuccess = sessionManager.retrySaveSession()
                if (retrySuccess) {
                    // Monitor session state to check if retry was successful
                    delay(500) // Give it a moment to process
                    val updatedSession = sessionManager.currentSession.value
                    if (updatedSession?.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                        Timber.i("Session save retry successful")
                        // Use the saved workout ID from session manager
                        val savedWorkoutId = sessionManager.savedWorkoutId.value
                        val workoutId = savedWorkoutId ?: updatedSession.id.value
                        _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted(workoutId)
                        // Do NOT transition to NoSession - let user navigate via UI buttons
                    } else {
                        Timber.w("Session save retry failed")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Retry failed. Please try again or contact support.",
                            isRetryable = true
                        )
                    }
                } else {
                    Timber.w("Cannot retry - no failed session")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Cannot retry at this time. Please try again."
                    )
                }
            } else {
                // Regular retry - reload session state
                _uiState.value = UnifiedActiveWorkoutUiState.Loading
                observeSessionState()
            }
        }
    }

    /**
     * Updates the original template with new exercises added during workout
     */
    fun updateOriginalTemplate() {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.currentSession.value
                if (currentSession == null) {
                    Timber.e("Cannot update template - no active session")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "No active workout session"
                    )
                    return@launch
                }
                
                val templateId = currentSession.templateId
                if (templateId == null) {
                    Timber.e("Cannot update template - workout not started from template")
                    finishWorkoutCompletion()
                    return@launch
                }
                
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    Timber.e("Cannot update template - no authenticated user")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "User not authenticated"
                    )
                    return@launch
                }
                

                val templateResult = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId.fromString(templateId),
                    userId.value
                )
                
                templateResult.fold(
                    onSuccess = { originalTemplate ->
                        if (originalTemplate != null) {
                            // Convert session exercises to template exercises
                            val updatedTemplateExercises = currentSession.exercises.mapIndexed { index, sessionExercise ->
                                com.example.liftrix.domain.model.TemplateExercise(
                                    exerciseId = sessionExercise.exerciseId,
                                    name = sessionExercise.name,
                                    primaryMuscle = sessionExercise.primaryMuscle,
                                    equipment = com.example.liftrix.domain.model.Equipment.BODYWEIGHT_ONLY,
                                    orderIndex = index,
                                    targetSets = sessionExercise.sets.size,
                                    targetReps = sessionExercise.sets.firstOrNull()?.targetReps?.let { 
                                        val safeReps = it.coerceIn(0, 1000)
                                        com.example.liftrix.domain.model.Reps(safeReps) 
                                    },
                                    targetWeight = sessionExercise.sets.firstOrNull()?.targetWeight,
                                    restTimeSeconds = sessionExercise.restTimeSeconds,
                                    notes = sessionExercise.notes
                                )
                            }
                            
                            val updatedTemplate = originalTemplate.copy(
                                exercises = updatedTemplateExercises,
                                updatedAt = java.time.Instant.now()
                            )
                            
                            // Save the updated template
                            val updateResult = workoutTemplateRepository.updateTemplate(updatedTemplate)
                            updateResult.fold(
                                onSuccess = {
                                    Timber.i("Template updated successfully: ${updatedTemplate.name}")
                                    completeSessionDirectly()
                                },
                                onFailure = { error ->
                                    Timber.e("Failed to update template: ${error.message}")
                                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                                        message = "Failed to update template: ${error.message}"
                                    )
                                }
                            )
                        } else {
                            Timber.e("Template not found: $templateId")
                            completeSessionDirectly()
                        }
                    },
                    onFailure = { error ->
                        Timber.e("Failed to load template: ${error.message}")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Failed to load template: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Error updating template")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to update template: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Skips updating template and finishes workout completion
     */
    fun skipTemplateUpdate() {
        viewModelScope.launch {
            completeSessionDirectly()
        }
    }
    
    /**
     * Dismisses the template update dialog without completing workout
     */
    fun dismissTemplateUpdateDialog() {
        val currentState = _uiState.value
        if (currentState is UnifiedActiveWorkoutUiState.Success) {
            _uiState.value = currentState.copy(showSaveAsTemplateDialog = false)
        }
    }
    
    /**
     * Finishes the workout completion process after template handling
     */
    private fun finishWorkoutCompletion() {
        viewModelScope.launch {
            // 🔥 FIX: Get the saved workout ID from the session manager
            val savedWorkoutId = sessionManager.savedWorkoutId.value
            
            // Use the saved workout ID if available, otherwise fall back to session ID
            val workoutId = if (!savedWorkoutId.isNullOrEmpty()) {
                savedWorkoutId
            } else {
                // Fallback to session ID if saved workout ID is not available (shouldn't happen)
                val currentSession = sessionManager.currentSession.value
                val fallbackId = currentSession?.id?.value ?: "unknown"
                Timber.w("No saved workout ID, using fallback: $fallbackId")
                fallbackId
            }
            
            // Show completion state - navigation will be triggered by the screen
            _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted(workoutId)
            
            // Don't transition to NoSession - let the navigation handle the state change
            // The UnifiedActiveWorkoutScreen will call onNavigateToHome() when it sees WorkoutCompleted
        }
    }

    /**
     * Checks if exercises were added beyond the original template
     */
    private fun hasExercisesAddedBeyondTemplate(session: UnifiedWorkoutSession): Boolean {
        // If session wasn't started from a template, can't have exercises added beyond template
        val templateId = session.templateId ?: return false
        
        // For now, we'll use a simple heuristic based on exercise count and IDs
        // This could be enhanced to store original template data in session metadata
        
        // If we have the original template data in session metadata, use that
        session.metadata["originalExerciseCount"]?.let { originalCountStr ->
            val originalCount = originalCountStr.toIntOrNull() ?: return false
            val currentCount = session.exercises.size
            
            return currentCount > originalCount
        }
        
        // Fallback: If no metadata available, assume no exercises were added
        // This prevents false positive save prompts when starting from templates
        return false
    }

    /**
     * Saves the current Quick workout as a template with the given name
     */
    fun saveQuickWorkoutAsTemplate(templateName: String) {
        viewModelScope.launch {
            try {
                
                val currentSession = sessionManager.currentSession.value
                if (currentSession == null) {
                    Timber.e("Cannot save template - no active session")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "No active workout session"
                    )
                    return@launch
                }
                
                if (currentSession.exercises.isEmpty()) {
                    Timber.e("Cannot save template - no exercises")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Cannot save empty workout as template"
                    )
                    return@launch
                }
                
                
                // First hide the dialog
                val currentUiState = _uiState.value
                if (currentUiState is UnifiedActiveWorkoutUiState.Success) {
                    _uiState.value = currentUiState.copy(showSaveQuickWorkoutDialog = false)
                }
                
                val result = templateCommandUseCase.createFromSession(
                    session = currentSession,
                    templateName = templateName,
                    templateDescription = "Created from Quick workout"
                )
                
                result.fold(
                    onSuccess = { template ->
                        Timber.i("Successfully saved template: '${template.name}' (ID: ${template.id.value})")
                        // Now complete the session after successful template save
                        completeSessionDirectly()
                    },
                    onFailure = { error ->
                        Timber.e("Failed to save template: ${error.message}")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Failed to save template: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Error saving template")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to save template: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Skips saving the Quick workout as template and finishes workout completion
     */
    fun skipSaveQuickWorkoutAsTemplate() {
        viewModelScope.launch {
            
            // First hide the dialog
            val currentUiState = _uiState.value
            if (currentUiState is UnifiedActiveWorkoutUiState.Success) {
                _uiState.value = currentUiState.copy(showSaveQuickWorkoutDialog = false)
            }
            
            // Complete the session without saving template
            completeSessionDirectly()
        }
    }
    
    /**
     * Dismisses the Quick workout save dialog without completing workout
     */
    fun dismissSaveQuickWorkoutDialog() {
        val currentState = _uiState.value
        if (currentState is UnifiedActiveWorkoutUiState.Success) {
            _uiState.value = currentState.copy(showSaveQuickWorkoutDialog = false)
        } else {
            Timber.w("Cannot dismiss dialog - UI state is not Success: ${currentState::class.simpleName}")
        }
    }

    /**
     * Clears any recovery errors
     */
    fun clearError() {
        sessionManager.clearRecoveryError()
    }
    
    /**
     */
    fun clearSavedWorkoutId() {
        sessionManager.clearSavedWorkoutId()
    }
    
    /**
     * Loads previous set data for all exercises in the current session.
     * 
     * This method is called when a session starts to populate the PREVIOUS column
     * in RedesignedExerciseCard with historical performance data for comparison.
     * 
     * Uses structured concurrency to load data for multiple exercises in parallel
     * while respecting the ViewModelScope lifecycle and error handling patterns.
     */
    fun loadPreviousSetData() {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.getCurrentSession()
                if (currentSession == null) {
                    Timber.w("Cannot load previous set data - no active session")
                    return@launch
                }
                
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    Timber.w("Cannot load previous set data - no authenticated user")
                    return@launch
                }
                
                Timber.d("Loading previous set data for ${currentSession.exercises.size} exercises")
                
                // Load previous data for all exercises in parallel
                val previousDataMap = mutableMapOf<String, PreviousSetDataResponse>()
                
                // Process exercises in parallel with structured concurrency
                currentSession.exercises.forEach { exercise ->
                    // 🔥 FIX: Use canonical library ID for history queries instead of display name
                    // The exerciseId should contain the canonical library ID like "core-ab-wheel-rollout"
                    val canonicalLibraryId = exercise.exerciseId.value
                    
                    // Debug logging for exercise ID investigation
                    Timber.d("[PREV_SET_DEBUG] Exercise '${exercise.name}' - Using canonical ID: '$canonicalLibraryId' for history query")
                    Timber.d("[PREV_SET_ID_FIX] Switching from display name '${exercise.name}' to canonical ID '$canonicalLibraryId'")
                    
                    // Launch concurrent requests for each exercise
                    launch {
                        workoutQueryUseCase.getPreviousSetData(
                            userId = userId.value,
                            exerciseId = canonicalLibraryId, // 🔥 FIX: Use canonical library ID instead of display name
                            setNumber = 1, // Load for all sets initially
                            excludeWorkoutId = currentSession.id.value // Exclude current active session
                        ).fold(
                            onSuccess = { response ->
                                if (response.hasPreviousData()) {
                                    previousDataMap[exercise.exerciseId.value] = response
                                    Timber.d("Loaded previous data for exercise ${exercise.name}: ${response.previousSets.size} sets")
                                } else {
                                    Timber.d("No previous data found for exercise ${exercise.name}")
                                }
                            },
                            onFailure = { error ->
                                Timber.w("Failed to load previous set data for exercise ${exercise.name}: ${error.message}")
                                // Don't fail the entire operation for individual exercise failures
                            }
                        )
                    }
                }
                
                // Wait for all parallel requests to complete, then update state
                // Using a small delay to allow all parallel operations to complete
                delay(100)
                _previousSetData.value = previousDataMap
                
                Timber.d("Previous set data loading completed: ${previousDataMap.size} exercises with data")
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading previous set data")
                // Don't show error to user - this is enhancement data, not critical
            }
        }
    }
    
    /**
     * Gets the formatted previous value for a specific set.
     * 
     * Used by UI components (RedesignedExerciseCard) to populate the PREVIOUS column
     * with formatted strings like "50kg x 10" or "Bodyweight x 12".
     * 
     * @param exerciseId The exercise ID to look up
     * @param setNumber The set number (1-based) to get previous data for
     * @return Formatted display string or null if no previous data available
     */
    fun getPreviousValueForSet(exerciseId: String, setNumber: Int): String? {
        val exerciseData = _previousSetData.value[exerciseId]
        return exerciseData?.getPreviousSetInfo(setNumber)?.formattedDisplay
    }
    
    /**
     * Checks if previous data is available for an exercise.
     * 
     * Used by UI to show loading states or handle no-data scenarios gracefully.
     */
    fun hasPreviousDataForExercise(exerciseId: String): Boolean {
        return _previousSetData.value[exerciseId]?.hasPreviousData() == true
    }
    
    /**
     * Refreshes previous set data for a specific exercise.
     * 
     * Called when exercises are added dynamically to the session to ensure
     * all exercises have their previous data loaded for UI display.
     */
    fun refreshPreviousSetDataForExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: return@launch
                val currentSession = sessionManager.getCurrentSession() ?: return@launch
                
                // 🔥 FIX: The exerciseId parameter should already be the canonical library ID
                // since it comes from the exercise selection. Add logging to verify.
                Timber.d("[PREV_SET_REFRESH_FIX] Refreshing previous data for canonical ID: '$exerciseId'")

                workoutQueryUseCase.getPreviousSetData(
                    userId = userId.value,
                    exerciseId = exerciseId, // Should already be canonical library ID
                    setNumber = 1,
                    excludeWorkoutId = currentSession.id.value
                ).fold(
                    onSuccess = { response ->
                        val currentMap = _previousSetData.value.toMutableMap()
                        if (response.hasPreviousData()) {
                            currentMap[exerciseId] = response
                        } else {
                            currentMap.remove(exerciseId)
                        }
                        _previousSetData.value = currentMap
                        
                        Timber.d("Refreshed previous data for exercise $exerciseId")
                    },
                    onFailure = { error ->
                        Timber.w("Failed to refresh previous data for exercise $exerciseId: ${error.message}")
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing previous set data for exercise $exerciseId")
            }
        }
    }

    /**
     * Removes a set from an exercise in the current session
     */
    fun removeSetFromExercise(exerciseId: String, setNumber: Int) {
        viewModelScope.launch {
            try {
                val currentSession = sessionManager.getCurrentSession()
                if (currentSession == null) {
                    Timber.e("Cannot remove set - no active session")
                    return@launch
                }
                
                val exercise = currentSession.exercises.find { it.exerciseId.value == exerciseId }
                if (exercise == null) {
                    Timber.e("Exercise not found: $exerciseId")
                    return@launch
                }
                
                if (exercise.sets.size <= 1) {
                    Timber.w("Cannot remove last set from exercise")
                    return@launch
                }
                
                // Remove the set and reindex remaining sets
                val updatedSets = exercise.sets.toMutableList()
                if (setNumber > 0 && setNumber <= updatedSets.size) {
                    updatedSets.removeAt(setNumber - 1)
                    val reindexedSets = updatedSets.mapIndexed { index, set ->
                        set.copy(setNumber = index + 1)
                    }
                    
                    val updatedExercise = exercise.copy(sets = reindexedSets)
                    sessionManager.updateExerciseInSession(ExerciseId(exerciseId), updatedExercise)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing set from exercise")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to remove set: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        timerServiceManager.unbindService()
        super.onCleared()
    }
}

/**
 */
sealed class UnifiedActiveWorkoutUiState {
    object Loading : UnifiedActiveWorkoutUiState()
    object NoSession : UnifiedActiveWorkoutUiState()
    data class WorkoutCompleted(val workoutId: String) : UnifiedActiveWorkoutUiState()
    
    data class Success(
        val session: UnifiedWorkoutSession,
        val isCompleting: Boolean = false,
        val showSaveAsTemplateDialog: Boolean = false,
        val showSaveQuickWorkoutDialog: Boolean = false
    ) : UnifiedActiveWorkoutUiState()
    
    data class Error(
        val message: String,
        val isRetryable: Boolean = false
    ) : UnifiedActiveWorkoutUiState()
}
