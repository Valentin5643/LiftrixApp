package com.example.liftrix.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
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
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.RPE
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.core.cache.CacheManager
import com.example.liftrix.core.cache.CacheKey
import java.time.LocalDate
import java.time.Duration
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
    private val workoutRepository: WorkoutRepository,
    private val feedRepository: FeedRepository,
    private val cacheManager: CacheManager,
    private val cacheInvalidationService: CacheInvalidationService
) {
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

    fun startSession(session: UnifiedWorkoutSession) {
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
            try {
                // Save completed workout to repository
                val completedWorkout = completedSession.toCompletedWorkout()
                
                // Use synchronous save to ensure database commit before session cleanup
                val saveResult = if (workoutRepository is com.example.liftrix.data.repository.workout.WorkoutRepositoryImpl) {
                    // Use the LiftrixResult-based method for proper error handling
                    workoutRepository.createWorkout(completedWorkout)
                } else {
                    // Fallback to legacy save method
                    val legacyResult = workoutRepository.saveWorkout(completedWorkout)
                    if (legacyResult.isSuccess) {
                        com.example.liftrix.domain.model.common.LiftrixResult.success(completedWorkout)
                    } else {
                        com.example.liftrix.domain.model.common.LiftrixResult.failure(
                            com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                                errorMessage = "Failed to save workout: ${legacyResult.exceptionOrNull()?.message}",
                                operation = "CREATE",
                                table = "workouts"
                            )
                        )
                    }
                }
                
                saveResult.fold(
                    onSuccess = { savedWorkout ->
                        // Store the saved workout ID for navigation
                        _savedWorkoutId.value = savedWorkout.id.value
                        
                        // Add small delay to ensure database transaction is fully committed
                        kotlinx.coroutines.delay(100)
                        
                        // 🔍 AUTO-POST: Automatically create feed post for completed workout
                        createAutomaticWorkoutPost(savedWorkout)
                        
                        // Invalidate analytics cache after workout completion using enhanced invalidation service
                        invalidateWorkoutRelatedCache(savedWorkout)
                        
                        // Only clear session after confirmed database save
                        clearSession()
                    },
                    onFailure = { exception ->
                        Timber.e("Failed to save completed workout: ${exception.message}")
                        
                        // Check if error is recoverable to decide whether to preserve session
                        val shouldPreserveSession = when (exception) {
                            is com.example.liftrix.domain.model.error.LiftrixError.DatabaseError -> {
                                exception.isRecoverable
                            }
                            is com.example.liftrix.domain.model.error.LiftrixError.NetworkError -> {
                                exception.isRecoverable
                            }
                            else -> false
                        }
                        
                        if (shouldPreserveSession) {
                            Timber.w("Preserving session for recoverable error: ${exception.message}")
                            // Mark session as failed but preserve for retry
                            _currentSession.value = completedSession.copy(
                                sessionStatus = UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
                            )
                            
                            // Persist the failed session state to SharedPreferences
                            persistSession(_currentSession.value!!)
                        } else {
                            Timber.w("Clearing session for non-recoverable error")
                            clearSession()
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in completion process")
                
                // Preserve session on unexpected errors to prevent data loss
                Timber.w("Preserving session due to unexpected error")
                _currentSession.value = completedSession.copy(
                    sessionStatus = UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE
                )
                scope.launch {
                    persistSession(_currentSession.value!!)
                }
            }
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
            try {
                val completedWorkout = session.toCompletedWorkout()
                Timber.d("Attempting to save workout: ${completedWorkout.name}")
                
                // Use same robust save method as completion
                val saveResult = if (workoutRepository is com.example.liftrix.data.repository.workout.WorkoutRepositoryImpl) {
                    // Use the LiftrixResult-based method for proper error handling
                    workoutRepository.createWorkout(completedWorkout)
                } else {
                    // Fallback to legacy save method
                    val legacyResult = workoutRepository.saveWorkout(completedWorkout)
                    if (legacyResult.isSuccess) {
                        com.example.liftrix.domain.model.common.LiftrixResult.success(completedWorkout)
                    } else {
                        com.example.liftrix.domain.model.common.LiftrixResult.failure(
                            com.example.liftrix.domain.model.error.LiftrixError.DatabaseError(
                                errorMessage = "Failed to save workout: ${legacyResult.exceptionOrNull()?.message}",
                                operation = "CREATE",
                                table = "workouts"
                            )
                        )
                    }
                }
                
                saveResult.fold(
                    onSuccess = { savedWorkout ->
                        Timber.i("Workout saved successfully on retry with ID: ${savedWorkout.id.value}")
                        
                        // Store the saved workout ID for navigation
                        _savedWorkoutId.value = savedWorkout.id.value
                        
                        // Add delay for transaction commit
                        kotlinx.coroutines.delay(100)
                        
                        // Update session status to completed and clear
                        _currentSession.value = session.copy(sessionStatus = UnifiedWorkoutSession.SessionStatus.COMPLETED)
                        clearSession()
                    },
                    onFailure = { exception ->
                        Timber.e("Retry also failed for workout: ${completedWorkout.name}, error: ${exception.message}")
                        // Keep session in FAILED_TO_SAVE state for another retry attempt
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error in retry process")
            }
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

    fun hasActiveSession(): Boolean {
        return _currentSession.value?.isActive() == true
    }

    fun hasLiveSession(): Boolean {
        return _currentSession.value?.isLive() == true
    }

    fun getCurrentSession(): UnifiedWorkoutSession? {
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
            
            // Persist the completed session through the repository
            val result = workoutRepository.saveWorkout(completedSession.toWorkout())
            
            result.fold(
                onSuccess = {
                    Timber.d("Successfully saved completed workout")
                    
                    // Clear the session after successful save
                    clearSession()
                    
                    return true
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to save completed workout")
                    return false
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception during session completion")
            return false
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
     * Enhanced cache invalidation after workout completion using CacheInvalidationService.
     * 
     * This method provides comprehensive cache invalidation based on data relationships:
     * - Invalidates volume, frequency, and 1RM progression data
     * - Handles exercise-specific cache entries
     * - Processes muscle group analytics
     * - Updates dashboard and widget caches
     * - Provides proper error handling and recovery
     */
    private suspend fun invalidateWorkoutRelatedCache(workout: Workout) {
        try {
            Timber.d("Enhanced cache invalidation for workout completion - user: ${workout.userId}")
            
            // Extract exercise IDs from the workout  
            val exerciseIds = workout.exercises.map { it.libraryExercise.id }
            
            // Get workout date as LocalDate
            val workoutDate = workout.date.let { javaDate ->
                kotlinx.datetime.LocalDate(javaDate.year, javaDate.monthValue, javaDate.dayOfMonth)
            }
            
            // Calculate workout duration in minutes
            val workoutDuration = workout.getDuration()?.toMinutes()?.toInt() ?: 0
            
            // Use enhanced cache invalidation service
            val invalidationResult = cacheInvalidationService.invalidateWorkoutData(
                userId = workout.userId,
                workoutDate = workoutDate,
                exerciseIds = exerciseIds,
                workoutDuration = workoutDuration
            )
            
            invalidationResult.fold(
                onSuccess = {
                    Timber.i("Enhanced cache invalidation completed successfully for user: ${workout.userId}")
                },
                onFailure = { exception ->
                    Timber.e(exception, "Enhanced cache invalidation failed for user: ${workout.userId}")
                    
                    // Fallback to basic cache invalidation
                    fallbackCacheInvalidation(workout.userId)
                }
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Error in enhanced cache invalidation for user: ${workout.userId}")
            
            // Fallback to basic cache invalidation
            fallbackCacheInvalidation(workout.userId)
            // Don't let cache invalidation failure affect workout completion
        }
    }
    
    /**
     * Fallback cache invalidation using basic pattern-based invalidation.
     * 
     * Used when the enhanced cache invalidation service fails, providing
     * a simple but effective cache clearing mechanism to ensure data freshness.
     */
    private suspend fun fallbackCacheInvalidation(userId: String) {
        try {
            Timber.d("Fallback cache invalidation for user: $userId")
            
            // Use pattern-based invalidation for all user data
            cacheManager.invalidatePattern { cacheKey ->
                val keyString = cacheKey.keyString
                keyString.contains(":$userId:") || keyString.contains("user:$userId")
            }
            
            Timber.i("Fallback cache invalidation completed for user: $userId")
            
        } catch (e: Exception) {
            Timber.e(e, "Fallback cache invalidation failed for user: $userId")
            // Continue anyway - don't let cache issues block workout completion
        }
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
    
    /**
     * 🔍 AUTO-POST: Creates automatic workout post when workout is completed
     */
    private fun createAutomaticWorkoutPost(savedWorkout: Workout) {
        scope.launch {
            try {
                Timber.d("🔍 WORKOUT-POSTS-DEBUG: Creating automatic post for workout ${savedWorkout.id.value}")
                
                // 🔥 FIX: Check if post already exists to prevent duplicates
                val hasPostResult = feedRepository.hasPostForWorkout(savedWorkout.userId, savedWorkout.id.value)
                hasPostResult.fold(
                    onSuccess = { hasPost ->
                        if (hasPost) {
                            Timber.d("🔍 WORKOUT-POSTS-DEBUG: Skipping auto-post, already exists for workout ${savedWorkout.id.value}")
                            return@launch
                        }
                        
                        // Proceed with post creation
                        val postRequest = CreateWorkoutPostRequest(
                            workoutId = savedWorkout.id.value,
                            caption = "Great workout completed! 💪",
                            mediaUrls = emptyList(),
                            visibility = PostVisibility.FOLLOWERS
                        )
                        
                        val result = feedRepository.createPost(
                            userId = savedWorkout.userId,
                            request = postRequest
                        )
                        
                        result.fold(
                            onSuccess = { post ->
                                Timber.d("🔍 WORKOUT-POSTS-DEBUG: Auto-post created successfully - ID=${post.id}")
                            },
                            onFailure = { error ->
                                Timber.w("🔍 WORKOUT-POSTS-DEBUG: Auto-post creation failed: ${error.message}")
                                // Don't fail workout completion if post creation fails
                            }
                        )
                    },
                    onFailure = { error ->
                        Timber.w("🔍 WORKOUT-POSTS-DEBUG: Failed to check existing post: ${error.message}")
                        // Don't fail workout completion if check fails
                    }
                )
            } catch (e: Exception) {
                Timber.w(e, "🔍 WORKOUT-POSTS-DEBUG: Exception during auto-post creation")
                // Don't fail workout completion if post creation fails
            }
        }
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

/**
 * Extension function to convert UnifiedWorkoutSession to Workout for persistence.
 * Ensures proper conversion of session data to workout format.
 */
private fun UnifiedWorkoutSession.toWorkout(): Workout {
    return Workout(
        userId = this.userId,
        id = WorkoutId(this.id.value),
        name = this.name,
        date = LocalDate.now(),
        exercises = this.exercises.map { sessionExercise ->
            sessionExercise.toCompletedExercise()
        },
        status = when (this.sessionStatus) {
            UnifiedWorkoutSession.SessionStatus.COMPLETED -> WorkoutStatus.COMPLETED
            UnifiedWorkoutSession.SessionStatus.ACTIVE -> WorkoutStatus.IN_PROGRESS
            UnifiedWorkoutSession.SessionStatus.PAUSED -> WorkoutStatus.IN_PROGRESS
            UnifiedWorkoutSession.SessionStatus.FAILED_TO_SAVE -> WorkoutStatus.COMPLETED
        },
        startTime = this.startedAt,
        endTime = this.endedAt,
        notes = this.notes,
        templateId = this.templateId?.let { WorkoutId(it) },
        createdAt = this.startedAt,
        updatedAt = this.lastModified
    )
}