package com.example.liftrix.ui.workout.active

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.model.common.LiftrixResult
import java.time.Instant
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import com.example.liftrix.domain.usecase.session.AddExerciseToSessionUseCase
import com.example.liftrix.domain.usecase.template.CreateTemplateFromSessionUseCase
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
 * 🔥 NEW: Unified active workout ViewModel using single session model
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
    private val workoutTemplateRepository: WorkoutTemplateRepository,
    private val authRepository: AuthRepository,
    private val addExerciseToSessionUseCase: AddExerciseToSessionUseCase,
    private val createTemplateFromSessionUseCase: CreateTemplateFromSessionUseCase
) : ViewModel() {

    // 🔥 SIMPLIFIED: Single UI state flow
    private val _uiState = MutableStateFlow<UnifiedActiveWorkoutUiState>(
        UnifiedActiveWorkoutUiState.Loading
    )
    val uiState: StateFlow<UnifiedActiveWorkoutUiState> = _uiState.asStateFlow()
    
    // 🔥 DEBUG: Track all UI state changes
    init {
        viewModelScope.launch {
            _uiState.collect { state ->
                Timber.d("🔥 VIEWMODEL-STATE-CHANGE: UI State changed to ${state::class.simpleName} at ${System.currentTimeMillis()}")
                if (state is UnifiedActiveWorkoutUiState.Success) {
                    Timber.d("🔥 VIEWMODEL-STATE-CHANGE: Success state - showSaveQuickWorkoutDialog=${state.showSaveQuickWorkoutDialog}, showSaveAsTemplateDialog=${state.showSaveAsTemplateDialog}")
                    Timber.d("🔥 VIEWMODEL-STATE-CHANGE: Session name: ${state.session.name}, exercises: ${state.session.exercises.size}")
                }
            }
        }
    }

    // 🔥 SIMPLIFIED: Direct session access
    val currentSession: StateFlow<UnifiedWorkoutSession?> = sessionManager.currentSession

    init {
        timber.log.Timber.d("🔥 INIT-DEBUG: UnifiedActiveWorkoutViewModel initializing...")
        
        // Check initial session state and set appropriate UI state
        val currentSession = sessionManager.currentSession.value
        if (currentSession != null) {
            timber.log.Timber.d("🔥 INIT-DEBUG: Found existing session: ${currentSession.name} with ${currentSession.exercises.size} exercises")
            _uiState.value = UnifiedActiveWorkoutUiState.Success(currentSession)
        } else {
            timber.log.Timber.d("🔥 INIT-DEBUG: No existing session - setting NoSession state")
            _uiState.value = UnifiedActiveWorkoutUiState.NoSession
        }
        
        // Start observing session changes
        observeSessionState()
        timber.log.Timber.d("🔥 INIT-DEBUG: ViewModel initialization complete")
    }

    /**
     * 🔥 SIMPLIFIED: Observes session state and updates UI
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
                            // Don't override WorkoutCompleted state when session is completed
                            val currentState = _uiState.value
                            if (currentState is UnifiedActiveWorkoutUiState.WorkoutCompleted && 
                                session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                                // Keep the WorkoutCompleted state - user needs to navigate away
                                Timber.d("🔥 OBSERVE-SESSION: Keeping WorkoutCompleted state for completed session")
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
     * 🔥 SIMPLIFIED: Pauses or resumes the current session
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
                Timber.d("Session paused")
            }
            UnifiedWorkoutSession.SessionStatus.PAUSED -> {
                sessionManager.resumeSession()
                Timber.d("Session resumed")
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
     * 🔥 IMPROVED: Completes the current workout with save-as-template option
     * Enhanced for unified screen integration with navigation callback support
     */
    fun completeWorkout() {
        viewModelScope.launch {
            try {
                // Set completing state
                val currentState = _uiState.value
                if (currentState is UnifiedActiveWorkoutUiState.Success) {
                    _uiState.value = currentState.copy(isCompleting = true)
                    Timber.d("Starting completion process...")
                }
                
                // 🔥 FIX: Capture session state BEFORE calling completeSession to avoid race condition
                val capturedSession = sessionManager.currentSession.value
                if (capturedSession == null) {
                    Timber.w("🔥 COMPLETE-WORKOUT: No session to complete")
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
                    Timber.d("🔥 QUICK-WORKOUT-DEBUG: Detected quick workout, preparing to show save dialog")
                    // Show Quick workout save dialog WITHOUT completing session yet
                    val currentSuccessState = _uiState.value as? UnifiedActiveWorkoutUiState.Success
                    Timber.d("🔥 QUICK-WORKOUT-DEBUG: Current UI state is Success: ${currentSuccessState != null}")
                    if (currentSuccessState != null) {
                        Timber.d("🔥 QUICK-WORKOUT-DEBUG: Setting showSaveQuickWorkoutDialog = true")
                        Timber.d("🔥 QUICK-WORKOUT-DEBUG: Current state before copy: showSaveQuickWorkoutDialog=${currentSuccessState.showSaveQuickWorkoutDialog}")
                        val newState = currentSuccessState.copy(
                            isCompleting = false,
                            showSaveQuickWorkoutDialog = true
                        )
                        Timber.d("🔥 QUICK-WORKOUT-DEBUG: New state after copy: showSaveQuickWorkoutDialog=${newState.showSaveQuickWorkoutDialog}")
                        Timber.d("🔥 QUICK-WORKOUT-DEBUG: States are equal: ${currentSuccessState == newState}")
                        
                        _uiState.value = newState
                        
                        // Force trigger state change detection
                        Timber.d("🔥 QUICK-WORKOUT-DEBUG: Force checking if state actually changed")
                        val checkState = _uiState.value
                        if (checkState is UnifiedActiveWorkoutUiState.Success) {
                            Timber.d("🔥 QUICK-WORKOUT-DEBUG: Confirmed state in _uiState: showSaveQuickWorkoutDialog=${checkState.showSaveQuickWorkoutDialog}")
                        }
                        
                        Timber.d("🔥 QUICK-WORKOUT-SAVE: Showing save dialog for Quick workout with ${capturedSession.exercises.size} exercises")
                        Timber.d("🔥 QUICK-WORKOUT-DEBUG: New state showSaveQuickWorkoutDialog = ${newState.showSaveQuickWorkoutDialog}")
                        return@launch
                    } else {
                        Timber.e("🔥 QUICK-WORKOUT-ERROR: Current UI state is not Success, cannot show dialog. Current state: ${_uiState.value::class.simpleName}")
                    }
                } else if (isTemplateWithAddedExercises) {
                    // Show template update dialog WITHOUT completing session yet
                    val currentSuccessState = _uiState.value as? UnifiedActiveWorkoutUiState.Success
                    if (currentSuccessState != null) {
                        _uiState.value = currentSuccessState.copy(
                            isCompleting = false,
                            showSaveAsTemplateDialog = true
                        )
                        Timber.d("🔥 TEMPLATE-UPDATE: Showing template update dialog")
                        return@launch
                    }
                }
                
                // No dialogs needed, proceed with direct completion
                completeSessionDirectly()
                
            } catch (e: Exception) {
                Timber.e(e, "🔥 COMPLETE-WORKOUT: Error completing workout")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to complete workout: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 🔥 NEW: Directly completes the session without showing dialogs
     */
    private suspend fun completeSessionDirectly() {
        val success = sessionManager.completeSession()
        if (success) {
            Timber.i("Workout completion initiated")
            
            // Wait for the save to complete and get the saved workout ID
            delay(200) // Small delay to ensure save completes
            
            // 🔥 FIX: Get the saved workout ID from the session manager after completion
            val savedWorkoutId = sessionManager.savedWorkoutId.value
            val workoutId = if (!savedWorkoutId.isNullOrEmpty()) {
                Timber.d("🔥 COMPLETE-WORKOUT: Using saved workout ID: $savedWorkoutId")
                savedWorkoutId
            } else {
                // Fallback to session ID if saved workout ID is not available
                val currentSession = sessionManager.currentSession.value
                val fallbackId = currentSession?.id?.value ?: "unknown"
                Timber.w("🔥 COMPLETE-WORKOUT: No saved workout ID, using fallback: $fallbackId")
                fallbackId
            }
            
            // Show completion state - user will navigate away via UI buttons
            _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted(workoutId)
            Timber.d("🔥 COMPLETE-WORKOUT: Workout completed successfully with ID: $workoutId")
            
            // Check if session is in failed state after a delay
            delay(300) // Additional delay to ensure session state is updated
            val updatedSession = sessionManager.currentSession.value
            if (updatedSession?.sessionStatus == UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE) {
                Timber.w("🔥 COMPLETE-WORKOUT: Session failed to save, showing retry option")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Workout completed but failed to save. Tap to retry.",
                    isRetryable = true
                )
            }
            // Do NOT transition to NoSession - let user navigate via UI buttons
        } else {
            Timber.w("🔥 COMPLETE-WORKOUT: Failed to complete workout")
            _uiState.value = UnifiedActiveWorkoutUiState.Error(
                message = "Failed to complete workout. Please try again."
            )
        }
    }

    /**
     * 🔥 SIMPLIFIED: Discards the current workout
     */
    fun discardWorkout() {
        viewModelScope.launch {
            try {
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
     * 🔥 KEY FIX: Removes exercise from session-scoped list only
     */
    fun removeExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                sessionManager.removeExerciseFromSession(ExerciseId(exerciseId))
                Timber.d("Exercise removed from session: $exerciseId")
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
                Timber.d("Set added to exercise: $exerciseId, total sets: ${updatedExercise.sets.size}")
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
                updatedSets[setIndex] = updatedSet
                val updatedExercise = exercise.copy(sets = updatedSets)
                
                sessionManager.updateExerciseInSession(ExerciseId(exerciseId), updatedExercise)
                Timber.d("Set updated in exercise: $exerciseId, set: $setNumber, completed: ${updatedSet.isCompleted()}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to update set in exercise: $exerciseId")
                _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to update set")
            }
        }
    }

    /**
     * Moves to the next exercise in the session
     */
    fun moveToNextExercise() {
        viewModelScope.launch {
            try {
                sessionManager.moveToNextExercise()
                Timber.d("Moved to next exercise")
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
                Timber.d("Moved to previous exercise")
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
                Timber.d("Session notes updated")
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
     * 🔥 FIXED: Now uses proper use case for Clean Architecture compliance
     */
    fun addExerciseToSession(exerciseLibrary: ExerciseLibrary) {
        timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: addExerciseToSession called")
        timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Exercise: ${exerciseLibrary.name} (ID: ${exerciseLibrary.id})")
        
        viewModelScope.launch {
            try {
                // 🔥 FIXED: Use proper use case instead of direct session manager call
                val exerciseId = ExerciseId.fromString(exerciseLibrary.id)
                val result = addExerciseToSessionUseCase.execute(exerciseId)
                
                result.fold(
                    onSuccess = {
                        timber.log.Timber.i("🔥 VIEWMODEL-SUCCESS: Exercise added successfully: ${exerciseLibrary.name}")
                        
                        // Verify the exercise was actually added
                        val updatedSession = sessionManager.currentSession.value
                        timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: After addition - session exercises count: ${updatedSession?.exercises?.size ?: 0}")
                        if (updatedSession != null) {
                            timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Updated exercises: ${updatedSession.exercises.map { it.name }}")
                        }
                    },
                    onFailure = { error ->
                        timber.log.Timber.e(error, "🔥 VIEWMODEL-ERROR: Failed to add exercise: ${error.message}")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Failed to add exercise: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "🔥 VIEWMODEL-EXCEPTION: Unexpected error adding exercise to session")
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
                Timber.d("🔥 TEMPLATE-WORKOUT-DEBUG: Starting template workout with ID: $templateId")
                
                // Clear any existing session first
                val existingSession = sessionManager.currentSession.value
                if (existingSession != null) {
                    Timber.d("🔥 TEMPLATE-WORKOUT-DEBUG: Clearing existing session: ${existingSession.name}")
                    sessionManager.discardSession()
                    delay(100)
                }
                
                // Template loading handled by session creation service
                // For now, create a blank session with template reference
                createTemplateSession(templateId)
                Timber.d("🔥 TEMPLATE-WORKOUT-DEBUG: Template workout session created")
                
                // Timeout fallback
                delay(2000)
                val newSession = sessionManager.currentSession.value
                if (newSession == null) {
                    Timber.e("🔥 TEMPLATE-WORKOUT-DEBUG: Session not created after 2 seconds")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Failed to create template workout session"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "🔥 TEMPLATE-WORKOUT-DEBUG: Error starting template workout")
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
                Timber.d("🔥 BLANK-WORKOUT-DEBUG: Starting blank workout session...")
                
                // Force clear any existing session to avoid conflicts
                val existingSession = sessionManager.currentSession.value
                if (existingSession != null) {
                    Timber.d("🔥 BLANK-WORKOUT-DEBUG: Found existing session: ${existingSession.name}, force clearing...")
                    sessionManager.discardSession()
                    // Add small delay to ensure session is cleared
                    delay(100)
                }
                
                Timber.d("🔥 BLANK-WORKOUT-DEBUG: About to create blank session...")
                createBlankSession()
                Timber.d("🔥 BLANK-WORKOUT-DEBUG: Blank session creation completed")
                
                // Add timeout fallback - if no session appears within 2 seconds, something is wrong
                delay(2000)
                val newSession = sessionManager.currentSession.value
                if (newSession == null) {
                    Timber.e("🔥 BLANK-WORKOUT-DEBUG: Session not created after 2 seconds - forcing error state")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Failed to create workout session - timeout"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "🔥 BLANK-WORKOUT-DEBUG: Error starting blank workout")
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
                timber.log.Timber.d("Creating blank workout session")
                
                // Get current user ID
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    timber.log.Timber.e("No authenticated user - cannot create session")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error("Please sign in to start a workout")
                    return@launch
                }
                
                val blankSession = UnifiedWorkoutSession(
                    id = WorkoutSessionId.generate(),
                    userId = userId,
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
                
                timber.log.Timber.d("🔥 CREATE-SESSION-DEBUG: Blank session created with ID: ${blankSession.id}")
                timber.log.Timber.d("🔥 CREATE-SESSION-DEBUG: Session name: ${blankSession.name}")
                timber.log.Timber.d("🔥 CREATE-SESSION-DEBUG: Session status: ${blankSession.sessionStatus}")
                timber.log.Timber.d("🔥 CREATE-SESSION-DEBUG: User ID: $userId")
                
                sessionManager.forceStartSession(blankSession)
                timber.log.Timber.d("🔥 CREATE-SESSION-DEBUG: Session started via sessionManager")
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "🔥 CREATE-SESSION-DEBUG: Error creating blank session")
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
                timber.log.Timber.d("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Loading template with ID: $templateId")
                
                // Get current user ID
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    timber.log.Timber.e("🔥 CREATE-TEMPLATE-SESSION-DEBUG: No authenticated user - cannot load template")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error("Please sign in to use templates")
                    return@launch
                }
                
                // Load the template from repository
                val templateResult = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId.fromString(templateId), 
                    userId
                )
                
                val template = templateResult.fold(
                    onSuccess = { it },
                    onFailure = { exception ->
                        timber.log.Timber.e("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Error loading template: ${exception.message}")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error("Failed to load template: ${exception.message}")
                        return@launch
                    }
                )
                
                if (template == null) {
                    timber.log.Timber.e("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Template not found with ID: $templateId")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error("Template not found")
                    return@launch
                }
                
                timber.log.Timber.d("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Template loaded: ${template.name} with ${template.exercises.size} exercises")
                
                // Debug each template exercise in detail
                template.exercises.forEachIndexed { index, templateExercise ->
                    timber.log.Timber.d("🔥 TEMPLATE-EXERCISE-DEBUG[$index]: ${templateExercise.name}")
                    timber.log.Timber.d("🔥 TEMPLATE-EXERCISE-DEBUG[$index]: targetSets=${templateExercise.targetSets}")
                    timber.log.Timber.d("🔥 TEMPLATE-EXERCISE-DEBUG[$index]: targetReps=${templateExercise.targetReps}")
                    timber.log.Timber.d("🔥 TEMPLATE-EXERCISE-DEBUG[$index]: targetWeight=${templateExercise.targetWeight}")
                    timber.log.Timber.d("🔥 TEMPLATE-EXERCISE-DEBUG[$index]: restTime=${templateExercise.restTimeSeconds}")
                }
                
                // Convert template exercises to session exercises
                val sessionExercises = template.exercises.mapIndexed { index, templateExercise ->
                    // Create initial sets based on targetSets with better defaults
                    val numberOfSets = templateExercise.targetSets ?: 3 // Default to 3 sets if not specified
                    timber.log.Timber.d("🔥 SETS-CREATION-DEBUG: Creating $numberOfSets sets for ${templateExercise.name}")
                    
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
                        timber.log.Timber.d("🔥 SET-DEBUG: Set $setNumber - targetReps=${set.targetReps}, targetWeight=${set.targetWeight}")
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
                    userId = userId,
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
                
                timber.log.Timber.d("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Template session created with ${sessionExercises.size} exercises")
                timber.log.Timber.d("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Session name: ${templateSession.name}")
                timber.log.Timber.d("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Exercises: ${sessionExercises.map { it.name }}")
                
                // Debug the final session exercises
                sessionExercises.forEachIndexed { index, sessionExercise ->
                    timber.log.Timber.d("🔥 SESSION-EXERCISE-DEBUG[$index]: ${sessionExercise.name} with ${sessionExercise.sets.size} sets")
                    sessionExercise.sets.forEach { set ->
                        timber.log.Timber.d("🔥 SESSION-SET-DEBUG: Set ${set.setNumber} - reps=${set.targetReps}, weight=${set.targetWeight}")
                    }
                }
                
                // Record template usage
                workoutTemplateRepository.recordTemplateUsage(WorkoutTemplateId.fromString(templateId), userId)
                
                sessionManager.forceStartSession(templateSession)
                timber.log.Timber.d("🔥 CREATE-TEMPLATE-SESSION-DEBUG: Template session started via sessionManager")
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "🔥 CREATE-TEMPLATE-SESSION-DEBUG: Error creating template session")
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
                Timber.d("🔥 RETRY-SAVE: Attempting to retry save for failed session")
                _uiState.value = UnifiedActiveWorkoutUiState.Loading
                
                val retrySuccess = sessionManager.retrySaveSession()
                if (retrySuccess) {
                    // Monitor session state to check if retry was successful
                    delay(500) // Give it a moment to process
                    val updatedSession = sessionManager.currentSession.value
                    if (updatedSession?.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                        Timber.i("🔥 RETRY-SAVE: Session save retry successful")
                        // 🔥 FIX: Use the saved workout ID from session manager
                        val savedWorkoutId = sessionManager.savedWorkoutId.value
                        val workoutId = savedWorkoutId ?: updatedSession.id.value
                        Timber.d("🔥 RETRY-SAVE: Using workout ID: $workoutId")
                        _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted(workoutId)
                        // Do NOT transition to NoSession - let user navigate via UI buttons
                    } else {
                        Timber.w("🔥 RETRY-SAVE: Session save retry failed")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Retry failed. Please try again or contact support.",
                            isRetryable = true
                        )
                    }
                } else {
                    Timber.w("🔥 RETRY-SAVE: Cannot retry - no failed session")
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
                
                Timber.d("Updating template: $templateId with new exercises")
                
                // Get the original template
                val templateResult = workoutTemplateRepository.getTemplateById(
                    WorkoutTemplateId.fromString(templateId),
                    userId
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
                                        com.example.liftrix.domain.model.Reps(it) 
                                    },
                                    targetWeight = sessionExercise.sets.firstOrNull()?.targetWeight,
                                    restTimeSeconds = sessionExercise.restTimeSeconds,
                                    notes = sessionExercise.notes
                                )
                            }
                            
                            // Update the template with new exercises
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
                Timber.d("🔥 WORKOUT-COMPLETION: Using saved workout ID: $savedWorkoutId")
                savedWorkoutId
            } else {
                // Fallback to session ID if saved workout ID is not available (shouldn't happen)
                val currentSession = sessionManager.currentSession.value
                val fallbackId = currentSession?.id?.value ?: "unknown"
                Timber.w("🔥 WORKOUT-COMPLETION: No saved workout ID, using fallback: $fallbackId")
                fallbackId
            }
            
            // Show completion state - navigation will be triggered by the screen
            _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted(workoutId)
            Timber.d("🔥 WORKOUT-COMPLETION: Set state to WorkoutCompleted with ID: $workoutId")
            
            // 🔥 FIXED: Don't transition to NoSession - let the navigation handle the state change
            // The UnifiedActiveWorkoutScreen will call onNavigateToHome() when it sees WorkoutCompleted
        }
    }

    /**
     * Checks if exercises were added beyond the original template
     */
    private fun hasExercisesAddedBeyondTemplate(session: UnifiedWorkoutSession): Boolean {
        // If session wasn't started from a template, can't have exercises added beyond template
        val templateId = session.templateId ?: return false
        
        // Get the original template to compare with
        // For now, we'll use a simple heuristic based on exercise count and IDs
        // This could be enhanced to store original template data in session metadata
        
        // If we have the original template data in session metadata, use that
        session.metadata["originalExerciseCount"]?.let { originalCountStr ->
            val originalCount = originalCountStr.toIntOrNull() ?: return false
            val currentCount = session.exercises.size
            
            Timber.d("🔥 TEMPLATE-COMPARISON: Original: $originalCount, Current: $currentCount")
            return currentCount > originalCount
        }
        
        // Fallback: If no metadata available, assume no exercises were added
        // This prevents false positive save prompts when starting from templates
        Timber.d("🔥 TEMPLATE-COMPARISON: No original exercise count metadata, assuming no exercises added")
        return false
    }

    /**
     * Saves the current Quick workout as a template with the given name
     */
    fun saveQuickWorkoutAsTemplate(templateName: String) {
        viewModelScope.launch {
            try {
                Timber.d("🔥 SAVE-TEMPLATE: Dialog button clicked - starting template save process for: '$templateName'")
                
                val currentSession = sessionManager.currentSession.value
                if (currentSession == null) {
                    Timber.e("🔥 SAVE-TEMPLATE: Cannot save template - no active session")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "No active workout session"
                    )
                    return@launch
                }
                
                if (currentSession.exercises.isEmpty()) {
                    Timber.e("🔥 SAVE-TEMPLATE: Cannot save template - no exercises")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Cannot save empty workout as template"
                    )
                    return@launch
                }
                
                Timber.d("🔥 SAVE-TEMPLATE: Saving Quick workout as template: '$templateName' with ${currentSession.exercises.size} exercises")
                
                // First hide the dialog
                val currentUiState = _uiState.value
                if (currentUiState is UnifiedActiveWorkoutUiState.Success) {
                    _uiState.value = currentUiState.copy(showSaveQuickWorkoutDialog = false)
                    Timber.d("🔥 SAVE-TEMPLATE: Dialog hidden, proceeding with template creation")
                }
                
                val result = createTemplateFromSessionUseCase(
                    session = currentSession,
                    templateName = templateName,
                    templateDescription = "Created from Quick workout"
                )
                
                result.fold(
                    onSuccess = { template ->
                        Timber.i("🔥 SAVE-TEMPLATE: Successfully saved template: '${template.name}' (ID: ${template.id.value})")
                        // Now complete the session after successful template save
                        completeSessionDirectly()
                    },
                    onFailure = { error ->
                        Timber.e("🔥 SAVE-TEMPLATE: Failed to save template: ${error.message}")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Failed to save template: ${error.message}"
                        )
                    }
                )
                
            } catch (e: Exception) {
                Timber.e(e, "🔥 SAVE-TEMPLATE: Error saving template")
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
            Timber.d("🔥 SAVE-TEMPLATE: User clicked 'Skip' - completing workout without saving template")
            
            // First hide the dialog
            val currentUiState = _uiState.value
            if (currentUiState is UnifiedActiveWorkoutUiState.Success) {
                _uiState.value = currentUiState.copy(showSaveQuickWorkoutDialog = false)
                Timber.d("🔥 SAVE-TEMPLATE: Dialog hidden, proceeding with workout completion")
            }
            
            // Complete the session without saving template
            completeSessionDirectly()
        }
    }
    
    /**
     * Dismisses the Quick workout save dialog without completing workout
     */
    fun dismissSaveQuickWorkoutDialog() {
        Timber.d("🔥 SAVE-TEMPLATE: User dismissed dialog (clicked outside or back) - hiding dialog but not completing workout")
        val currentState = _uiState.value
        if (currentState is UnifiedActiveWorkoutUiState.Success) {
            _uiState.value = currentState.copy(showSaveQuickWorkoutDialog = false)
            Timber.d("🔥 SAVE-TEMPLATE: Dialog hidden, workout remains active")
        } else {
            Timber.w("🔥 SAVE-TEMPLATE: Cannot dismiss dialog - UI state is not Success: ${currentState::class.simpleName}")
        }
    }

    /**
     * Clears any recovery errors
     */
    fun clearError() {
        sessionManager.clearRecoveryError()
    }
    
    /**
     * 🔥 FIX: Clears the saved workout ID after navigation
     */
    fun clearSavedWorkoutId() {
        sessionManager.clearSavedWorkoutId()
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
                    Timber.d("Set $setNumber removed from exercise $exerciseId")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing set from exercise")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to remove set: ${e.message}"
                )
            }
        }
    }
}

/**
 * 🔥 SIMPLIFIED: UI state sealed class
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