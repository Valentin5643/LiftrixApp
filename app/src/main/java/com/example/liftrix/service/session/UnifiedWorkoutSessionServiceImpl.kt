package com.example.liftrix.service.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UnifiedWorkoutSessionService using composition pattern.
 * 
 * Delegates to specialized managers while providing a unified interface for backward
 * compatibility. This approach maintains single responsibility for each manager while
 * offering a convenient unified API for clients that need multiple capabilities.
 * 
 * The implementation is thread-safe and coordinates between managers to ensure
 * consistent behavior across state, persistence, and recovery operations.
 */
@Singleton
class UnifiedWorkoutSessionServiceImpl @Inject constructor(
    override val stateManager: SessionStateManager,
    override val persistenceManager: SessionPersistenceManager,
    override val recoveryManager: SessionRecoveryManager
) : UnifiedWorkoutSessionService {
    
    // Delegate SessionStateManager methods
    override val currentSession: StateFlow<UnifiedWorkoutSession?> = stateManager.currentSession
    override val hasActiveSession: StateFlow<Boolean> = stateManager.hasActiveSession
    
    override fun startSession(session: UnifiedWorkoutSession) = stateManager.startSession(session)
    override fun forceStartSession(session: UnifiedWorkoutSession) = stateManager.forceStartSession(session)
    override fun pauseSession() = stateManager.pauseSession()
    override fun resumeSession() = stateManager.resumeSession()
    override suspend fun completeSession(): Boolean = stateManager.completeSession()
    override fun discardSession() = stateManager.discardSession()
    override fun addExerciseToSession(exercise: SessionExercise) = stateManager.addExerciseToSession(exercise)
    override fun removeExerciseFromSession(exerciseId: ExerciseId) = stateManager.removeExerciseFromSession(exerciseId)
    override fun updateExerciseInSession(exerciseId: ExerciseId, updatedExercise: SessionExercise) = 
        stateManager.updateExerciseInSession(exerciseId, updatedExercise)
    override fun updateSetInSession(exerciseId: ExerciseId, setNumber: Int, updatedSet: SessionSet) = 
        stateManager.updateSetInSession(exerciseId, setNumber, updatedSet)
    override fun moveToNextExercise() = stateManager.moveToNextExercise()
    override fun moveToPreviousExercise() = stateManager.moveToPreviousExercise()
    override fun updateSessionNotes(notes: String?) = stateManager.updateSessionNotes(notes)
    override fun getSessionDurationSeconds(): Long = stateManager.getSessionDurationSeconds()
    override fun getSessionDurationMillis(): Long = stateManager.getSessionDurationMillis()
    override fun refreshSessionState() = stateManager.refreshSessionState()
    
    // Delegate SessionPersistenceManager methods
    override suspend fun persistSession(session: UnifiedWorkoutSession): LiftrixResult<Unit> = 
        persistenceManager.persistSession(session)
    override suspend fun loadPersistedSession(): LiftrixResult<UnifiedWorkoutSession?> = 
        persistenceManager.loadPersistedSession()
    override suspend fun hasPersistedSession(): Boolean = persistenceManager.hasPersistedSession()
    override suspend fun clearPersistedSession(): LiftrixResult<Unit> = persistenceManager.clearPersistedSession()
    override fun validateSession(session: UnifiedWorkoutSession): LiftrixResult<Unit> = 
        persistenceManager.validateSession(session)
    override suspend fun getPersistedSessionSize(): Long = persistenceManager.getPersistedSessionSize()
    override suspend fun backupPersistedSession(): LiftrixResult<Unit> = persistenceManager.backupPersistedSession()
    override suspend fun restoreFromBackup(): LiftrixResult<UnifiedWorkoutSession?> = 
        persistenceManager.restoreFromBackup()
    
    // Delegate SessionRecoveryManager methods
    override val isRecovering: StateFlow<Boolean> = recoveryManager.isRecovering
    override val recoveryError: StateFlow<String?> = recoveryManager.recoveryError
    
    override suspend fun recoverSessionOnStartup(): LiftrixResult<UnifiedWorkoutSession?> = 
        recoveryManager.recoverSessionOnStartup()
    override suspend fun retrySaveSession(): Boolean = recoveryManager.retrySaveSession()
    override fun detectDataCorruption(session: UnifiedWorkoutSession): LiftrixResult<Boolean> = 
        recoveryManager.detectDataCorruption(session)
    override suspend fun repairCorruptedSession(corruptedSession: UnifiedWorkoutSession): LiftrixResult<UnifiedWorkoutSession> = 
        recoveryManager.repairCorruptedSession(corruptedSession)
    override suspend fun createEmergencyBackup(session: UnifiedWorkoutSession): LiftrixResult<Unit> = 
        recoveryManager.createEmergencyBackup(session)
    override suspend fun recoverFromEmergencyBackup(): LiftrixResult<UnifiedWorkoutSession?> = 
        recoveryManager.recoverFromEmergencyBackup()
    override fun clearRecoveryError() = recoveryManager.clearRecoveryError()
    override suspend fun canAutoRecover(): Boolean = recoveryManager.canAutoRecover()
    override suspend fun performAutoRecovery(): LiftrixResult<UnifiedWorkoutSession?> = 
        recoveryManager.performAutoRecovery()
    override fun getRecoveryStatistics(): Map<String, Any> = recoveryManager.getRecoveryStatistics()
    
    // Unified interface convenience methods
    override suspend fun hasAnySession(): Boolean {
        return hasActiveSession.value || hasPersistedSession()
    }
    
    override suspend fun getCurrentSessionFromAnySource(): UnifiedWorkoutSession? {
        // Check active session first
        currentSession.value?.let { return it }
        
        // Fall back to persisted session
        return loadPersistedSession().getOrNull()
    }
}