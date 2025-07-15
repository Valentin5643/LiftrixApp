package com.example.liftrix.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.repository.workout.WorkoutRepository
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
 * 🔥 NEW: Unified workout session manager that eliminates dual state management
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
    private val workoutRepository: WorkoutRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(
        "unified_workout_session", Context.MODE_PRIVATE
    )
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // 🔥 SIMPLIFIED: Single state flow for session state
    private val _currentSession = MutableStateFlow<UnifiedWorkoutSession?>(null)
    val currentSession: StateFlow<UnifiedWorkoutSession?> = _currentSession.asStateFlow()

    // 🔥 SIMPLIFIED: Single state flow for session recovery
    private val _recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.NoRecovery)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()

    init {
        // 🔥 KEY FIX: Ensure session recovery happens immediately
        recoverSessionOnStartup()
    }

    /**
     * 🔥 SIMPLIFIED: Starts a new workout session
     */
    fun startSession(session: UnifiedWorkoutSession) {
        Timber.d("Starting unified workout session: ${session.name} with ${session.exercises.size} exercises")
        
        if (_currentSession.value != null) {
            Timber.w("Cannot start new session - session already active")
            return
        }
        
        forceStartSession(session)
    }

    /**
     * 🔥 FORCE START: Starts a session regardless of existing session state
     */
    fun forceStartSession(session: UnifiedWorkoutSession) {
        Timber.d("🔥 FORCE-START-DEBUG: Force starting session: ${session.name} with ${session.exercises.size} exercises")
        
        // Clear any existing session first
        _currentSession.value = null
        
        // Set the new session immediately
        _currentSession.value = session
        
        // Log exercise details for debugging
        session.exercises.forEachIndexed { index, exercise ->
            Timber.d("🔥 FORCE-START-DEBUG: Exercise $index: ${exercise.name} with ${exercise.sets.size} sets")
        }
        
        scope.launch {
            persistSession(session)
        }
        
        Timber.i("🔥 FORCE-START-DEBUG: Session force-started successfully with ${session.exercises.size} exercises")
    }

    /**
     * 🔥 SIMPLIFIED: Pauses the current session
     */
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

    /**
     * 🔥 SIMPLIFIED: Resumes the current session
     */
    fun resumeSession() {
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

    /**
     * 🔥 SIMPLIFIED: Completes the current session
     */
    fun completeSession(): Boolean {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot complete - no active session")
            return false
        }
        
        if (session.sessionStatus == UnifiedWorkoutSession.SessionStatus.COMPLETED) {
            Timber.w("Session already completed")
            return false
        }
        
        val completedSession = session.complete()
        _currentSession.value = completedSession
        
        scope.launch {
            // Save completed workout to repository
            val completedWorkout = completedSession.toCompletedWorkout()
            workoutRepository.saveWorkout(completedWorkout)
                .onSuccess {
                    Timber.i("Workout saved successfully: ${completedWorkout.name}")
                    // Clear session after saving
                    clearSession()
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to save completed workout")
                }
        }
        
        Timber.d("Session completed: ${session.name}")
        return true
    }

    /**
     * 🔥 SIMPLIFIED: Discards the current session without saving
     */
    fun discardSession() {
        val session = _currentSession.value
        if (session == null) {
            Timber.w("Cannot discard - no active session")
            return
        }
        
        clearSession()
        Timber.d("Session discarded: ${session.name}")
    }

    /**
     * 🔥 KEY FIX: Adds exercise to session-scoped list only
     */
    fun addExerciseToSession(exercise: SessionExercise) {
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

    /**
     * 🔥 KEY FIX: Removes exercise from session-scoped list only
     */
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

    /**
     * Updates an exercise in the current session
     */
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
     * 🔥 KEY FIX: Updates a specific set in a session exercise
     * This ensures proper state propagation when sets are marked as completed
     */
    fun updateSetInSession(exerciseId: ExerciseId, setNumber: Int, updatedSet: SessionSet) {
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
            
            // 🔥 KEY: Immediately update state to trigger UI refresh
            _currentSession.value = updatedSession
            
            scope.launch {
                persistSession(updatedSession)
            }
            
            Timber.d("Set updated in session: exercise=$exerciseId, set=$setNumber, completed=${updatedSet.isCompleted()}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update set in session: exercise=$exerciseId, set=$setNumber")
        }
    }

    /**
     * Moves to the next exercise in the session
     */
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

    /**
     * Moves to the previous exercise in the session
     */
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

    /**
     * Updates session notes
     */
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

    /**
     * Checks if there's an active session
     */
    fun hasActiveSession(): Boolean {
        return _currentSession.value?.isActive() == true
    }

    /**
     * Checks if there's a live session (active or paused)
     */
    fun hasLiveSession(): Boolean {
        return _currentSession.value?.isLive() == true
    }

    /**
     * Gets the current session
     */
    fun getCurrentSession(): UnifiedWorkoutSession? {
        return _currentSession.value
    }

    /**
     * Gets the session duration in seconds
     */
    fun getSessionDurationSeconds(): Long {
        return _currentSession.value?.getTotalDurationSeconds() ?: 0L
    }

    /**
     * Gets the session duration in milliseconds
     */
    fun getSessionDurationMillis(): Long {
        return getSessionDurationSeconds() * 1000L
    }
    
    /**
     * 🔥 KEY FIX: Forces a session state refresh to trigger UI updates
     * Use this when the session has been modified and UI needs to reflect changes
     */
    fun refreshSessionState() {
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
     * 🔥 SIMPLIFIED: Persists session to SharedPreferences
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
     * 🔥 SIMPLIFIED: Clears persisted session
     */
    private fun clearSession() {
        _currentSession.value = null
        sharedPrefs.edit {
            remove(KEY_CURRENT_SESSION)
            remove(KEY_LAST_UPDATED)
        }
        Timber.d("Session cleared")
    }

    /**
     * 🔥 SIMPLIFIED: Recovers session on startup
     */
    private fun recoverSessionOnStartup() {
        scope.launch {
            try {
                val serializedSession = sharedPrefs.getString(KEY_CURRENT_SESSION, null)
                if (serializedSession != null) {
                    val lastUpdated = sharedPrefs.getLong(KEY_LAST_UPDATED, 0)
                    val currentTime = System.currentTimeMillis()
                    
                    // Check if session is not too old (7 days)
                    val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000)
                    if (lastUpdated < sevenDaysAgo) {
                        Timber.w("Session too old, discarding")
                        clearSession()
                        return@launch
                    }
                    
                    val sessionData = json.decodeFromString<SerializableSession>(serializedSession)
                    val session = sessionData.toUnifiedWorkoutSession()
                    
                    if (session.sessionStatus != UnifiedWorkoutSession.SessionStatus.COMPLETED) {
                        // 🔥 KEY FIX: Immediately set session state for UI visibility
                        _currentSession.value = session
                        
                        Timber.i("Session recovered successfully: ${session.name} with ${session.exercises.size} exercises")
                        
                        // Log exercise details for debugging
                        session.exercises.forEachIndexed { index, exercise ->
                            Timber.d("Recovered exercise $index: ${exercise.name} with ${exercise.sets.size} sets")
                        }
                    } else {
                        // Session was completed, clear it
                        clearSession()
                        Timber.d("Completed session cleared on startup")
                    }
                } else {
                    Timber.d("No persisted session found on startup")
                }
            } catch (e: Exception) {
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
 * 🔥 SIMPLIFIED: Serializable session data for persistence
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
    val lastModified: Long
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
        lastModified = lastModified.epochSecond
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
        lastModified = Instant.ofEpochSecond(lastModified)
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