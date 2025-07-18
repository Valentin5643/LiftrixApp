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
    private val authRepository: AuthRepository
) : ViewModel() {

    // 🔥 SIMPLIFIED: Single UI state flow
    private val _uiState = MutableStateFlow<UnifiedActiveWorkoutUiState>(
        UnifiedActiveWorkoutUiState.Loading
    )
    val uiState: StateFlow<UnifiedActiveWorkoutUiState> = _uiState.asStateFlow()

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
                            _uiState.value = UnifiedActiveWorkoutUiState.Success(session)
                        }
                        else -> {
                            // No session exists - show appropriate state based on context
                            if (_uiState.value !is UnifiedActiveWorkoutUiState.Loading) {
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
     * 🔥 IMPROVED: Completes the current workout with proper state transitions
     * 
     * @param onNavigateToHome Callback to navigate to Home screen after successful completion
     */
    fun completeWorkout(onNavigateToHome: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                // Set completing state
                val currentState = _uiState.value
                if (currentState is UnifiedActiveWorkoutUiState.Success) {
                    _uiState.value = currentState.copy(isCompleting = true)
                    Timber.d("🔥 COMPLETE-WORKOUT: Starting completion process...")
                }
                
                val success = sessionManager.completeSession()
                if (success) {
                    Timber.i("🔥 COMPLETE-WORKOUT: Workout completion initiated")
                    
                    // Show completion state briefly
                    _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted
                    
                    // Give user a moment to see completion state
                    delay(1500)
                    
                    // Check if session is in failed state and offer retry
                    val currentSession = sessionManager.currentSession.value
                    if (currentSession?.sessionStatus == UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE) {
                        Timber.w("🔥 COMPLETE-WORKOUT: Session failed to save, showing retry option")
                        _uiState.value = UnifiedActiveWorkoutUiState.Error(
                            message = "Workout completed but failed to save. Tap to retry.",
                            isRetryable = true
                        )
                    } else {
                        // Navigate to Home after successful completion
                        try {
                            onNavigateToHome?.invoke()
                        } catch (navigationError: Exception) {
                            Timber.w(navigationError, "Navigation to Home failed after workout completion")
                            // Navigation failed, but workout was completed successfully
                            // Don't show error to user as the main action succeeded
                        }
                        
                        // Transition to NoSession state
                        _uiState.value = UnifiedActiveWorkoutUiState.NoSession
                    }
                } else {
                    Timber.w("🔥 COMPLETE-WORKOUT: Failed to complete workout")
                    _uiState.value = UnifiedActiveWorkoutUiState.Error(
                        message = "Failed to complete workout. Please try again."
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "🔥 COMPLETE-WORKOUT: Error completing workout")
                _uiState.value = UnifiedActiveWorkoutUiState.Error(
                    message = "Failed to complete workout: ${e.message}"
                )
            }
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
     */
    fun addExerciseToSession(exerciseLibrary: ExerciseLibrary) {
        timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: addExerciseToSession called")
        timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Exercise: ${exerciseLibrary.name} (ID: ${exerciseLibrary.id})")
        
        viewModelScope.launch {
            try {
                // 🔥 CRITICAL DEBUG: Check current session state
                val currentSession = sessionManager.currentSession.value
                timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Current session available? ${currentSession != null}")
                timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Current session exercises count: ${currentSession?.exercises?.size ?: 0}")
                
                if (currentSession != null) {
                    timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Session status: ${currentSession.sessionStatus}")
                    timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Existing exercises: ${currentSession.exercises.map { it.name }}")
                }
                
                // Convert ExerciseLibrary to SessionExercise
                val sessionExercise = SessionExercise(
                    exerciseId = ExerciseId.fromString(exerciseLibrary.id),
                    name = exerciseLibrary.name,
                    category = exerciseLibrary.primaryMuscleGroup,
                    primaryMuscle = exerciseLibrary.primaryMuscleGroup,
                    equipment = exerciseLibrary.equipment,
                    secondaryMuscles = exerciseLibrary.secondaryMuscleGroups.toSet(),
                    sets = listOf(
                        SessionSet(
                            setNumber = 1,
                            targetReps = null,
                            targetWeight = null,
                            actualReps = null,
                            actualWeight = null,
                            completedAt = null,
                            skipped = false
                        )
                    ),
                    orderIndex = sessionManager.currentSession.value?.exercises?.size ?: 0,
                    restTimeSeconds = null,
                    notes = null,
                    isSuperset = false,
                    supersetWith = null,
                    lastModified = Instant.now()
                )
                
                timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: SessionExercise created - name: ${sessionExercise.name}, orderIndex: ${sessionExercise.orderIndex}")
                timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: About to call sessionManager.addExerciseToSession")
                
                sessionManager.addExerciseToSession(sessionExercise)
                
                timber.log.Timber.i("🔥 VIEWMODEL-DEBUG: sessionManager.addExerciseToSession completed successfully")
                timber.log.Timber.i("Added exercise to session: ${exerciseLibrary.name}")
                
                // 🔥 CRITICAL DEBUG: Verify the exercise was actually added
                val updatedSession = sessionManager.currentSession.value
                timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: After addition - session exercises count: ${updatedSession?.exercises?.size ?: 0}")
                if (updatedSession != null) {
                    timber.log.Timber.d("🔥 VIEWMODEL-DEBUG: Updated exercises: ${updatedSession.exercises.map { it.name }}")
                }
                
            } catch (e: Exception) {
                timber.log.Timber.e(e, "🔥 VIEWMODEL-DEBUG: Error adding exercise to session")
                Timber.e(e, "Error adding exercise to session")
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
                
                // TODO: Implement template loading and session creation
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
                timber.log.Timber.d("🔥 CREATE-SESSION-DEBUG: Creating blank workout session")
                
                // Get current user ID
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    timber.log.Timber.e("🔥 CREATE-SESSION-DEBUG: No authenticated user - cannot create session")
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
                        _uiState.value = UnifiedActiveWorkoutUiState.WorkoutCompleted
                        delay(1000)
                        _uiState.value = UnifiedActiveWorkoutUiState.NoSession
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
     * Clears any recovery errors
     */
    fun clearError() {
        sessionManager.clearRecoveryError()
    }
}

/**
 * 🔥 SIMPLIFIED: UI state sealed class
 */
sealed class UnifiedActiveWorkoutUiState {
    object Loading : UnifiedActiveWorkoutUiState()
    object NoSession : UnifiedActiveWorkoutUiState()
    object WorkoutCompleted : UnifiedActiveWorkoutUiState()
    
    data class Success(
        val session: UnifiedWorkoutSession,
        val isCompleting: Boolean = false
    ) : UnifiedActiveWorkoutUiState()
    
    data class Error(
        val message: String,
        val isRetryable: Boolean = false
    ) : UnifiedActiveWorkoutUiState()
}