package com.example.liftrix.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.RPE
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.session.CompleteWorkoutSessionUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified workout session manager that eliminates dual state management.
 * 
 * This replaces the complex LiveWorkoutSessionManager + dual state system with
 * a single, simple session manager that uses UnifiedWorkoutSession as the
 * single source of truth.
 * 
 * Key improvements:
 * - Single source of truth (UnifiedWorkoutSession)
 * - Simplified state management
 * - Session-scoped exercise management
 * - Robust persistence and recovery
 * - Clean separation of concerns
 */
@Singleton
class UnifiedWorkoutSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val completeWorkoutSessionUseCase: CompleteWorkoutSessionUseCase,
    private val authRepository: AuthRepository
) : WorkoutSessionManagerPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        "unified_workout_session", Context.MODE_PRIVATE
    )
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _currentSession = MutableStateFlow<UnifiedWorkoutSession?>(null)
    val currentSession: StateFlow<UnifiedWorkoutSession?> = _currentSession.asStateFlow()

    private val _recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.NoRecovery)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()
    
    // Track the saved workout ID after successful completion
    private val _savedWorkoutId = MutableStateFlow<String?>(null)
    val savedWorkoutId: StateFlow<String?> = _savedWorkoutId.asStateFlow()

    init {
        // Ensure session recovery happens immediately
        recoverSessionOnStartup()
    }

    override fun startSession(session: UnifiedWorkoutSession) {
        Timber.d("Starting unified workout session: ${session.name} with ${session.exercises.size} exercises")
        
        if (_currentSession.value != null) {
            Timber.w("Cannot start new session - session already active")
            return
        }
        
        forceStartSession(session)
        
        // Start foreground service for persistent notifications
        startWorkoutForegroundService()
    }

    fun forceStartSession(session: UnifiedWorkoutSession) {
        Timber.d("[WORKOUT-DEBUG] forceStartSession requested sessionId=${session.id.value} userId=${session.userId} name='${session.name}' exercises=${session.exercises.size} status=${session.sessionStatus}")
        Timber.d("Force starting session: ${session.name} with ${session.exercises.size} exercises")
        
        // Clear any existing session first
        _currentSession.value = null
        
        // Set the new session immediately
        _currentSession.value = session
        
        // Log exercise details for debugging
        session.exercises.forEachIndexed { index, exercise ->
            Timber.d("Exercise $index: ${exercise.name} with ${exercise.sets.size} sets")
        }
        
        scope.launch {
            persistSession(session)
        }
        
        // Start foreground service for force-started sessions too
        startWorkoutForegroundService()
        
        Timber.i("Session force-started successfully with ${session.exercises.size} exercises")
    }

    fun pauseSession() {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot pause - no active session")
            return
        }
        
        if (session.sessionStatus != UnifiedWorkoutSession.SessionStatus.ACTIVE) {
            Timber.w("Cannot pause - session not active")
            return
        }
        
        val pausedSession = session.pause()
        _currentSession.value = pausedSession
        
        scope.launch {
            persistSession(pausedSession)
        }
        
        Timber.d("Session paused: ${session.name}")
    }

    override fun resumeSession() {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot resume - no active session")
            return
        }
        
        if (session.sessionStatus != UnifiedWorkoutSession.SessionStatus.PAUSED) {
            Timber.w("Cannot resume - session not paused")
            return
        }
        
        val resumedSession = session.resume()
        _currentSession.value = resumedSession
        
        scope.launch {
            persistSession(resumedSession)
        }
        
        Timber.d("Session resumed: ${session.name}")
    }

    fun completeSession(): Boolean {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot complete - no active session")
            return false
        }
        Timber.d("[WORKOUT-DEBUG] completeSession requested sessionId=${session.id.value} userId=${session.userId} name='${session.name}' exercises=${session.exercises.size} status=${session.sessionStatus}")
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Session already completed")
            return false
        }
        
        val completedSession = session.complete()
        _currentSession.value = completedSession
        
        scope.launch {
            persistCompletedSession(
                sessionForPersistence = completedSession,
                failedSessionState = completedSession.copy(
                    sessionStatus = UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
                )
            )
        }
        
        Timber.d("Session completed: ${session.name}")
        return true
    }

    fun retrySaveSession(): Boolean {
        val session = _currentSession.value
        if (session == null || session.sessionStatus != UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE) {
            Timber.w("Cannot retry - no failed session available")
            return false
        }

        Timber.i("Retrying save for session: ${session.name}")
        
        scope.launch {
            val completedSession = session.copy(
                sessionStatus = UnifiedWorkoutSession.SessionStatus.COMPLETED
            )
            persistCompletedSession(
                sessionForPersistence = completedSession,
                failedSessionState = session
            )
        }
        
        return true
    }

    fun discardSession() {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot discard - no active session")
            return
        }
        
        // Stop foreground service when discarding session
        stopWorkoutForegroundService()
        
        clearSession()
        Timber.d("Session discarded: ${session.name}")
    }

    override fun addExerciseToSession(exercise: SessionExercise) {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot add exercise - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot add exercise - session completed")
            return
        }
        
        try {
            val updatedSession = session.addExercise(exercise)
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Exercise added to session: ${exercise.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add exercise to session: ${exercise.name}")
        }
    }

    fun removeExerciseFromSession(exerciseId: ExerciseId) {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot remove exercise - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot remove exercise - session completed")
            return
        }
        
        try {
            val updatedSession = session.removeExercise(exerciseId)
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Exercise removed from session: $exerciseId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove exercise from session: $exerciseId")
        }
    }

    fun updateExerciseInSession(exerciseId: ExerciseId, updatedExercise: SessionExercise) {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot update exercise - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot update exercise - session completed")
            return
        }
        
        try {
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Exercise updated in session: ${updatedExercise.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update exercise in session: ${updatedExercise.name}")
        }
    }
    
    /**
     * Updates a specific set in a session exercise.
     * This ensures proper state propagation when sets are marked as completed.
     */
    override fun updateSetInSession(exerciseId: ExerciseId, setNumber: Int, updatedSet: SessionSet) {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot update set - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot update set - session completed")
            return
        }
        
        try {
            // Find the exercise and update the specific set
            val exerciseIndex = session.exercises.indexOfFirst { it.exerciseId == exerciseId }
            if (exerciseIndex == -1) {
                Timber.w("Exercise not found in session: $exerciseId")
                return
            }
            
            val exercise = session.exercises[exerciseIndex]
            val setIndex = exercise.sets.indexOfFirst { it.setNumber == setNumber }
            if (setIndex == -1) {
                Timber.w("Set not found in exercise: set $setNumber")
                return
            }
            
            // Update the set
            val updatedSets = exercise.sets.toMutableList()
            updatedSets[setIndex] = updatedSet
            
            val updatedExercise = exercise.copy(sets = updatedSets)
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            
            // Immediately update state to trigger UI refresh
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Set updated in session: exercise=$exerciseId, set=$setNumber, completed=${updatedSet.isCompleted()}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update set in session: exercise=$exerciseId, set=$setNumber")
        }
    }

    fun moveToNextExercise() {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot move to next exercise - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot move to next exercise - session completed")
            return
        }
        
        try {
            val updatedSession = session.moveToNextExercise()
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Moved to next exercise in session")
        } catch (e: Exception) {
            Timber.e(e, "Failed to move to next exercise: ${e.message}")
        }
    }

    fun moveToPreviousExercise() {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot move to previous exercise - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot move to previous exercise - session completed")
            return
        }
        
        try {
            val updatedSession = session.moveToPreviousExercise()
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Moved to previous exercise in session")
        } catch (e: Exception) {
            Timber.e(e, "Failed to move to previous exercise: ${e.message}")
        }
    }

    fun updateSessionNotes(notes: String?) {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot update notes - no active session")
            return
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Cannot update notes - session completed")
            return
        }
        
        try {
            val updatedSession = session.updateNotes(notes)
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Session notes updated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update session notes: ${e.message}")
        }
    }

    override fun hasActiveSession(): Boolean {
        return _currentSession.value?.isActive() == true
    }

    fun hasLiveSession(): Boolean {
        return _currentSession.value?.isLive() == true
    }

    override fun getCurrentSession(): UnifiedWorkoutSession? {
        return _currentSession.value
    }

    fun getSessionDurationSeconds(): Long {
        return _currentSession.value?.getTotalDurationSeconds() ?: 0L
    }

    fun getSessionDurationMillis(): Long {
        return getSessionDurationSeconds() * 1000L
    }
    
    /**
     * Forces a session state refresh to trigger UI updates.
     * Use this when the session has been modified and UI needs to reflect changes.
     */
    override fun refreshSessionState() {
        val session = _currentSession.value
        if (session != null) {
            // Trigger state update by reassigning the same session
            // This ensures observers get notified of any changes
            _currentSession.value = session.copy(lastModified = Instant.now())
            
            scope.launch {
                persistSession(_currentSession.value!!)
            }
            
            Timber.d("Session state refreshed")
        }
    }

    /**
     * Completes the current session and persists it as a finished workout
     */
    suspend fun completeCurrentSession(): Boolean {
        val currentSession = _currentSession.value
        if (currentSession == null) {
            Timber.w("No active session to complete")
            return false
        }
        
        if (currentSession.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Session already completed")
            return false
        }
        
        try {
            Timber.d("Completing session: ${currentSession.name}")
            
            // Complete the session in the unified session state
            val completedSession = currentSession.complete()
            
            // Update the current session state
            _currentSession.value = completedSession
            return persistCompletedSession(
                sessionForPersistence = completedSession,
                failedSessionState = completedSession.copy(
                    sessionStatus = UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception during session completion")
            return false
        }
    }

    private suspend fun persistCompletedSession(
        sessionForPersistence: UnifiedWorkoutSession,
        failedSessionState: UnifiedWorkoutSession
    ): Boolean {
        return try {
            completeWorkoutSessionUseCase(sessionForPersistence).fold(
                onSuccess = { result ->
                    Timber.d(
                        "[WORKOUT-DEBUG] completion save success workoutId=${result.savedWorkoutId} " +
                            "userId=${sessionForPersistence.userId}"
                    )
                    result.sideEffects.forEach { sideEffect ->
                        Timber.d(
                            "[WORKOUT-DEBUG] completion side effect effect=${sideEffect.effect} " +
                                "state=${sideEffect.state} detail=${sideEffect.detail}"
                        )
                    }
                    _savedWorkoutId.value = result.savedWorkoutId
                    clearSession()
                    true
                },
                onFailure = { exception ->
                    Timber.e(
                        "[WORKOUT-DEBUG] completion save failed sessionId=${sessionForPersistence.id.value} " +
                            "userId=${sessionForPersistence.userId} error=${exception.message}"
                    )
                    Timber.e("Failed to save completed workout: ${exception.message}")
                    handleCompletionFailure(failedSessionState, exception)
                    false
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error in completion process")
            preserveFailedSession(failedSessionState)
            false
        }
    }

    private suspend fun handleCompletionFailure(
        failedSessionState: UnifiedWorkoutSession,
        exception: Throwable
    ) {
        if (isRecoverableCompletionError(exception)) {
            Timber.w("Preserving session for recoverable error: ${exception.message}")
            preserveFailedSession(failedSessionState)
        } else {
            Timber.w("Clearing session for non-recoverable error")
            clearSession()
        }
    }

    private suspend fun preserveFailedSession(session: UnifiedWorkoutSession) {
        _currentSession.value = session.copy(
            sessionStatus = UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
        )
        persistSession(_currentSession.value!!)
    }

    private fun isRecoverableCompletionError(exception: Throwable): Boolean {
        return when (exception) {
            is LiftrixError.DatabaseError -> exception.isRecoverable
            is LiftrixError.NetworkError -> exception.isRecoverable
            else -> false
        }
    }
    
    /**
     * Persists session to SharedPreferences
     */
    private suspend fun persistSession(session: UnifiedWorkoutSession) {
        try {
            val serializedSession = json.encodeToString(session.toSerializable())
            sharedPrefs.edit {
                putString(KEY_CURRENT_SESSION, serializedSession)
                putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            }
            Timber.d("Session persisted: ${session.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist session: ${session.name}")
        }
    }

    /**
     * Clears persisted session with proper logging
     */
    private fun clearSession() {
        val currentSession = _currentSession.value
        Timber.d("[WORKOUT-DEBUG] clearSession requested sessionId=${currentSession?.id?.value} userId=${currentSession?.userId} status=${currentSession?.sessionStatus}")
        
        // Stop foreground service when clearing session
        stopWorkoutForegroundService()
        
        _currentSession.value = null
        // Don't clear the saved workout ID here - it's needed for navigation
        sharedPrefs.edit {
            remove(KEY_CURRENT_SESSION)
            remove(KEY_LAST_UPDATED)
        }
        Timber.d("Session cleared${currentSession?.let { ": ${it.name}" } ?: ""}")
    }
    
    /**
     * Clears the saved workout ID after navigation
     */
    fun clearSavedWorkoutId() {
        _savedWorkoutId.value = null
        Timber.d("Saved workout ID cleared")
    }

    /**
     * Recovers session on startup
     */
    private fun recoverSessionOnStartup() {
        scope.launch {
            try {
                val serializedSession = sharedPrefs.getString(KEY_CURRENT_SESSION, null)
                val firebaseUid = authRepository.getCurrentUserId()?.value
                Timber.tag("StartupRestoreFix").d(
                    "operation=CUSTOM_SESSION_RECOVERY_START firebaseCurrentUserId=${firebaseUid ?: "null"} persistedSessionPresent=${serializedSession != null} timestamp=${System.currentTimeMillis()}"
                )
                if (serializedSession != null) {
                    val lastUpdated = sharedPrefs.getLong(KEY_LAST_UPDATED, 0)
                    val currentTime = System.currentTimeMillis()
                    
                    // Check if session is not too old (7 days)
                    val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000)
                    if (lastUpdated < sevenDaysAgo) {
                        Timber.tag("StartupRestoreFix").w(
                            "operation=CUSTOM_SESSION_CLEAR userId=${firebaseUid ?: "unknown"} reason=session_too_old firebaseCurrentUserId=${firebaseUid ?: "null"} timestamp=${System.currentTimeMillis()}"
                        )
                        Timber.w("Session too old, discarding")
                        clearSession()
                        return@launch
                    }
                    
                    val sessionData = json.decodeFromString<SerializableSession>(serializedSession)
                    val session = sessionData.toUnifiedWorkoutSession()
                    
                    if (session.sessionStatus != UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                        // Immediately set session state for UI visibility
                        _currentSession.value = session
                        
                        // Start foreground service for recovered active sessions
                        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.ACTIVE ||
                            session.sessionStatus == UnifiedWorkoutSession.SessionStatus.PAUSED) {
                            startWorkoutForegroundService()
                        }
                        
                        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE) {
                            Timber.w("Failed save session recovered: ${session.name}")
                        } else {
                            Timber.i("Session recovered successfully: ${session.name} with ${session.exercises.size} exercises")
                        }
                    } else {
                        // Session was completed, clear it
                        Timber.tag("StartupRestoreFix").d(
                            "operation=CUSTOM_SESSION_CLEAR userId=${session.userId} reason=completed_session firebaseCurrentUserId=${firebaseUid ?: "null"} timestamp=${System.currentTimeMillis()}"
                        )
                        clearSession()
                        Timber.d("Completed session cleared on startup")
                    }
                } else {
                    Timber.tag("StartupRestoreFix").d(
                        "operation=CUSTOM_SESSION_EMPTY firebaseCurrentUserId=${firebaseUid ?: "null"} action=no_room_clear timestamp=${System.currentTimeMillis()}"
                    )
                    Timber.d("No persisted session found on startup")
                }
            } catch (e: Exception) {
                val firebaseUid = authRepository.getCurrentUserId()?.value
                Timber.tag("StartupRestoreFix").e(
                    e,
                    "operation=CUSTOM_SESSION_CLEAR userId=${firebaseUid ?: "unknown"} reason=recovery_exception firebaseCurrentUserId=${firebaseUid ?: "null"} action=clear_session_only_no_room_clear timestamp=${System.currentTimeMillis()}"
                )
                Timber.e(e, "Failed to recover session on startup")
                
                // Show recovery error to user
                _recoveryState.value = RecoveryState.RecoveryError(
                    error = "Failed to recover session: ${e.message}"
                )
                
                // Clear corrupted session data
                clearSession()
            }
        }
    }

    /**
     * Clears recovery error state
     */
    fun clearRecoveryError() {
        _recoveryState.value = RecoveryState.NoRecovery
    }
    
    /**
     * Starts the workout foreground service for persistent notifications
     */
    private fun startWorkoutForegroundService() {
        try {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                action = WorkoutForegroundService.ACTION_START_FOREGROUND
            }
            
            // Use ContextCompat for safe foreground service starting
            ContextCompat.startForegroundService(context, intent)
            
            Timber.d("Workout foreground service started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start workout foreground service")
        }
    }

    /**
     * Stops the workout foreground service
     */
    private fun stopWorkoutForegroundService() {
        try {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                action = WorkoutForegroundService.ACTION_STOP_FOREGROUND
            }
            
            context.startService(intent)
            
            Timber.d("Workout foreground service stopped")
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop workout foreground service")
        }
    }

    /**
     * Recovery state management
     */
    sealed class RecoveryState {
        object NoRecovery : RecoveryState()
        
        data class RecoveryError(
            val error: String
        ) : RecoveryState()
    }
    
    companion object {
        private const val KEY_CURRENT_SESSION = "current_session"
        private const val KEY_LAST_UPDATED = "last_updated"
    }
}

/**
 * Serializable session data for persistence
 */
@Serializable
data class SerializableSession(
    val id: String,
    val userId: String,
    val name: String,
    val templateId: String? = null,
    val exercises: List<SerializableSessionExercise>,
    val currentExerciseIndex: Int = 0,
    val sessionStatus: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val elapsedTimeSeconds: Long = 0,
    val notes: String? = null,
    val lastModified: Long,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class SerializableSessionExercise(
    val exerciseId: String,
    val name: String,
    val category: String,
    val primaryMuscle: String,
    val sets: List<SerializableSessionSet>,
    val orderIndex: Int,
    val restTimeSeconds: Int? = null,
    val notes: String? = null
)

@Serializable
data class SerializableSessionSet(
    val setNumber: Int,
    val targetReps: Int? = null,
    val targetWeight: Double? = null,
    val targetTime: Long? = null,
    val actualReps: Int? = null,
    val actualWeight: Double? = null,
    val actualTime: Long? = null,
    val completedAt: Long? = null
)

/**
 * Extension functions for serialization
 */
private fun UnifiedWorkoutSession.toSerializable(): SerializableSession {
    return SerializableSession(
        id = id.value,
        userId = userId,
        name = name,
        templateId = templateId, // templateId is already String?, no need to unwrap
        exercises = exercises.map { it.toSerializable() },
        currentExerciseIndex = currentExerciseIndex,
        sessionStatus = sessionStatus.name,
        startedAt = startedAt.epochSecond,
        endedAt = endedAt?.epochSecond,
        elapsedTimeSeconds = elapsedTimeSeconds,
        notes = notes,
        lastModified = lastModified.epochSecond,
        metadata = metadata
    )
}

private fun SessionExercise.toSerializable(): SerializableSessionExercise {
    return SerializableSessionExercise(
        exerciseId = exerciseId.value,
        name = name,
        category = category.name,
        primaryMuscle = primaryMuscle.name,
        sets = sets.map { it.toSerializable() },
        orderIndex = orderIndex,
        restTimeSeconds = restTimeSeconds,
        notes = notes
    )
}

private fun SessionSet.toSerializable(): SerializableSessionSet {
    return SerializableSessionSet(
        setNumber = setNumber,
        targetReps = targetReps,
        targetWeight = targetWeight?.kilograms,
        targetTime = targetTime,
        actualReps = actualReps,
        actualWeight = actualWeight?.kilograms,
        actualTime = actualTime,
        completedAt = completedAt?.epochSecond
    )
}

private fun SerializableSession.toUnifiedWorkoutSession(): UnifiedWorkoutSession {
    return UnifiedWorkoutSession(
        id = WorkoutSessionId(id),
        userId = userId,
        name = name,
        templateId = templateId, // Keep as String? since UnifiedWorkoutSession expects String?
        exercises = exercises.map { it.toSessionExercise() },
        currentExerciseIndex = currentExerciseIndex,
        sessionStatus = UnifiedWorkoutSession.SessionStatus.valueOf(sessionStatus),
        startedAt = Instant.ofEpochSecond(startedAt),
        endedAt = endedAt?.let { Instant.ofEpochSecond(it) },
        elapsedTimeSeconds = elapsedTimeSeconds,
        notes = notes,
        lastModified = Instant.ofEpochSecond(lastModified),
        metadata = metadata
    )
}

private fun SerializableSessionExercise.toSessionExercise(): SessionExercise {
    return SessionExercise(
        exerciseId = ExerciseId(exerciseId),
        name = name,
        category = ExerciseCategory.valueOf(category),
        primaryMuscle = ExerciseCategory.valueOf(primaryMuscle),
        equipment = Equipment.BODYWEIGHT_ONLY, // Default for deserialized sessions
        sets = sets.map { it.toSessionSet() },
        orderIndex = orderIndex,
        restTimeSeconds = restTimeSeconds,
        notes = notes
    )
}

private fun SerializableSessionSet.toSessionSet(): SessionSet {
    return SessionSet(
        setNumber = setNumber,
        targetReps = targetReps,
        targetWeight = targetWeight?.let { Weight(it) },
        targetTime = targetTime,
        actualReps = actualReps,
        actualWeight = actualWeight?.let { Weight(it) },
        actualTime = actualTime,
        completedAt = completedAt?.let { Instant.ofEpochSecond(it) }
    )
}
