package com.example.liftrix.service

import android.content.Context
import android.content.Intent
import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.repository.ActiveWorkoutSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveWorkoutSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeWorkoutSessionRepository: ActiveWorkoutSessionRepository,
    private val persistentSessionStorage: PersistentSessionStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _liveSessionState = MutableStateFlow<LiveSessionState>(LiveSessionState.NoSession)
    val liveSessionState: StateFlow<LiveSessionState> = _liveSessionState.asStateFlow()
    
    private val _sessionDuration = MutableStateFlow(0L)
    val sessionDuration: StateFlow<Long> = _sessionDuration.asStateFlow()
    
    private val _recoveryState = MutableStateFlow<RecoveryState>(RecoveryState.NoRecovery)
    val recoveryState: StateFlow<RecoveryState> = _recoveryState.asStateFlow()
    
    private var currentSessionId: WorkoutSessionId? = null
    private var sessionStartTime: Long = 0L
    private var totalPausedDuration: Long = 0L
    private var pauseStartTime: Long? = null
    
    init {
        recoverSessionOnStartup()
    }
    
    fun startLiveSession(session: ActiveWorkoutSession) {
        Timber.d("Starting live workout session: ${session.name}")
        
        if (_liveSessionState.value is LiveSessionState.ActiveSession) {
            Timber.w("Cannot start new session - session already active")
            return
        }
        
        currentSessionId = session.id
        sessionStartTime = System.currentTimeMillis()
        totalPausedDuration = 0L
        pauseStartTime = null
        
        _liveSessionState.value = LiveSessionState.ActiveSession(
            session = session,
            isRunning = true,
            startTime = sessionStartTime
        )
        
        scope.launch {
            persistentSessionStorage.saveSession(session, sessionStartTime, totalPausedDuration)
            startForegroundService()
            startDurationTimer()
        }
    }
    
    fun pauseLiveSession() {
        val currentState = _liveSessionState.value
        if (currentState !is LiveSessionState.ActiveSession) {
            Timber.w("Cannot pause - no active session")
            return
        }
        
        if (!currentState.isRunning) {
            Timber.w("Session already paused")
            return
        }
        
        pauseStartTime = System.currentTimeMillis()
        
        _liveSessionState.value = currentState.copy(isRunning = false)
        
        scope.launch {
            activeWorkoutSessionRepository.pauseSession(currentState.session.id)
            persistentSessionStorage.updateSessionState(
                sessionId = currentState.session.id,
                isRunning = false,
                pauseStartTime = pauseStartTime
            )
        }
        
        Timber.d("Session paused")
    }
    
    fun resumeLiveSession() {
        val currentState = _liveSessionState.value
        if (currentState !is LiveSessionState.ActiveSession) {
            Timber.w("Cannot resume - no active session")
            return
        }
        
        if (currentState.isRunning) {
            Timber.w("Session already running")
            return
        }
        
        pauseStartTime?.let { startTime ->
            totalPausedDuration += System.currentTimeMillis() - startTime
            pauseStartTime = null
        }
        
        _liveSessionState.value = currentState.copy(isRunning = true)
        
        scope.launch {
            activeWorkoutSessionRepository.resumeSession(currentState.session.id)
            persistentSessionStorage.updateSessionState(
                sessionId = currentState.session.id,
                isRunning = true,
                totalPausedDuration = totalPausedDuration
            )
        }
        
        Timber.d("Session resumed")
    }
    
    fun updateSessionExerciseProgress(exerciseIndex: Int, completedSets: Int) {
        val currentState = _liveSessionState.value
        if (currentState !is LiveSessionState.ActiveSession) {
            Timber.w("Cannot update progress - no active session")
            return
        }
        
        // Update the session with new progress
        scope.launch {
            activeWorkoutSessionRepository.getSessionById(currentState.session.id)
                .onSuccess { updatedSession ->
                    updatedSession?.let { session ->
                        _liveSessionState.value = currentState.copy(session = session)
                        persistentSessionStorage.updateSessionProgress(session)
                    }
                }
                .onFailure { exception ->
                    Timber.e(exception, "Failed to get updated session")
                }
        }
    }
    
    fun endLiveSession(): Boolean {
        val currentState = _liveSessionState.value
        if (currentState !is LiveSessionState.ActiveSession) {
            Timber.w("Cannot end - no active session")
            return false
        }
        
        _liveSessionState.value = LiveSessionState.NoSession
        _sessionDuration.value = 0L
        
        scope.launch {
            persistentSessionStorage.clearSession()
            stopForegroundService()
        }
        
        currentSessionId = null
        sessionStartTime = 0L
        totalPausedDuration = 0L
        pauseStartTime = null
        
        Timber.d("Live session ended")
        return true
    }
    
    fun hasActiveSession(): Boolean {
        return _liveSessionState.value is LiveSessionState.ActiveSession
    }
    
    fun getCurrentSession(): ActiveWorkoutSession? {
        return when (val state = _liveSessionState.value) {
            is LiveSessionState.ActiveSession -> state.session
            is LiveSessionState.NoSession -> null
        }
    }
    
    fun getSessionDurationMillis(): Long {
        return if (hasActiveSession()) {
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - sessionStartTime
            val pausedTime = totalPausedDuration + (pauseStartTime?.let { currentTime - it } ?: 0L)
            elapsed - pausedTime
        } else {
            0L
        }
    }
    
    private fun startDurationTimer() {
        scope.launch {
            while (hasActiveSession()) {
                val duration = getSessionDurationMillis()
                _sessionDuration.value = duration
                kotlinx.coroutines.delay(1000L) // Update every second
            }
        }
    }
    
    private fun recoverSessionOnStartup() {
        scope.launch {
            try {
                val recoveredSession = persistentSessionStorage.getStoredSession()
                if (recoveredSession != null) {
                    Timber.d("Recovering session: ${recoveredSession.session.name}")
                    
                    // Validate session integrity
                    if (isSessionValid(recoveredSession)) {
                        currentSessionId = recoveredSession.session.id
                        sessionStartTime = recoveredSession.startTime
                        totalPausedDuration = recoveredSession.totalPausedDuration
                        pauseStartTime = if (!recoveredSession.isRunning) System.currentTimeMillis() else null
                        
                        _liveSessionState.value = LiveSessionState.ActiveSession(
                            session = recoveredSession.session,
                            isRunning = recoveredSession.isRunning,
                            startTime = sessionStartTime
                        )
                        
                        startForegroundService()
                        startDurationTimer()
                        
                        Timber.i("Session recovered successfully: ${recoveredSession.session.name}")
                    } else {
                        // Session is corrupted, show recovery dialog
                        _recoveryState.value = RecoveryState.CorruptedSession(
                            sessionName = recoveredSession.session.name,
                            recoveredSession = recoveredSession
                        )
                        
                        Timber.w("Session recovery failed - corrupted session detected")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to recover session on startup")
                
                // Clear corrupted session data
                scope.launch {
                    persistentSessionStorage.clearSession()
                }
                
                _recoveryState.value = RecoveryState.RecoveryError(
                    error = e.message ?: "Unknown error during session recovery"
                )
            }
        }
    }
    
    private fun isSessionValid(storedSession: PersistentSessionStorage.StoredSession): Boolean {
        return try {
            // Basic validation checks
            val session = storedSession.session
            
            // Check if session has valid ID
            if (session.id.value.isBlank()) {
                Timber.w("Session validation failed: Invalid session ID")
                return false
            }
            
            // Check if session has valid user ID
            if (session.userId.isBlank()) {
                Timber.w("Session validation failed: Invalid user ID")
                return false
            }
            
            // Check if session has valid name
            if (session.name.isBlank()) {
                Timber.w("Session validation failed: Invalid session name")
                return false
            }
            
            // Check if start time is reasonable (not in the future, not too old)
            val currentTime = System.currentTimeMillis()
            if (storedSession.startTime > currentTime) {
                Timber.w("Session validation failed: Start time in the future")
                return false
            }
            
            // Check if session is not older than 7 days
            val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000)
            if (storedSession.startTime < sevenDaysAgo) {
                Timber.w("Session validation failed: Session older than 7 days")
                return false
            }
            
            true
        } catch (e: Exception) {
            Timber.e(e, "Session validation failed with exception")
            false
        }
    }
    
    fun restoreCorruptedSession() {
        val recoveryState = _recoveryState.value
        if (recoveryState is RecoveryState.CorruptedSession) {
            scope.launch {
                try {
                    val recoveredSession = recoveryState.recoveredSession
                    
                    currentSessionId = recoveredSession.session.id
                    sessionStartTime = recoveredSession.startTime
                    totalPausedDuration = recoveredSession.totalPausedDuration
                    pauseStartTime = if (!recoveredSession.isRunning) System.currentTimeMillis() else null
                    
                    _liveSessionState.value = LiveSessionState.ActiveSession(
                        session = recoveredSession.session,
                        isRunning = recoveredSession.isRunning,
                        startTime = sessionStartTime
                    )
                    
                    startForegroundService()
                    startDurationTimer()
                    
                    _recoveryState.value = RecoveryState.NoRecovery
                    
                    Timber.i("Corrupted session restored successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to restore corrupted session")
                    _recoveryState.value = RecoveryState.RecoveryError(
                        error = "Failed to restore session: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun discardCorruptedSession() {
        val recoveryState = _recoveryState.value
        if (recoveryState is RecoveryState.CorruptedSession) {
            scope.launch {
                try {
                    persistentSessionStorage.clearSession()
                    _recoveryState.value = RecoveryState.NoRecovery
                    
                    Timber.i("Corrupted session discarded successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to discard corrupted session")
                }
            }
        }
    }
    
    private fun startForegroundService() {
        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_START_FOREGROUND
        }
        context.startForegroundService(intent)
    }
    
    private fun stopForegroundService() {
        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_STOP_FOREGROUND
        }
        context.startService(intent)
    }
    
    sealed class LiveSessionState {
        object NoSession : LiveSessionState()
        
        data class ActiveSession(
            val session: ActiveWorkoutSession,
            val isRunning: Boolean,
            val startTime: Long
        ) : LiveSessionState()
    }
    
    sealed class RecoveryState {
        object NoRecovery : RecoveryState()
        
        data class CorruptedSession(
            val sessionName: String,
            val recoveredSession: PersistentSessionStorage.StoredSession
        ) : RecoveryState()
        
        data class RecoveryError(
            val error: String
        ) : RecoveryState()
    }
}