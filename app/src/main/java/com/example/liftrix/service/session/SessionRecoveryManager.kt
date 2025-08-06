package com.example.liftrix.service.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing workout session recovery and error handling.
 * 
 * Responsible for:
 * - Session recovery after app crashes or restarts
 * - Error handling and retry mechanisms
 * - Recovery state management
 * - Data corruption detection and recovery
 * 
 * Follows Interface Segregation Principle by focusing only on recovery operations.
 */
interface SessionRecoveryManager {
    
    /**
     * StateFlow indicating if there's a recovery operation in progress.
     */
    val isRecovering: StateFlow<Boolean>
    
    /**
     * StateFlow containing any recovery error that occurred.
     */
    val recoveryError: StateFlow<String?>
    
    /**
     * Attempts to recover a session on application startup.
     * 
     * @return Result containing the recovered session or null if no recovery needed
     */
    suspend fun recoverSessionOnStartup(): LiftrixResult<UnifiedWorkoutSession?>
    
    /**
     * Retries saving a session that previously failed to save.
     * 
     * @return true if retry was successful, false otherwise
     */
    suspend fun retrySaveSession(): Boolean
    
    /**
     * Detects if session data is corrupted.
     * 
     * @param session The session to check
     * @return Result indicating if corruption was detected
     */
    fun detectDataCorruption(session: UnifiedWorkoutSession): LiftrixResult<Boolean>
    
    /**
     * Attempts to repair corrupted session data.
     * 
     * @param corruptedSession The corrupted session
     * @return Result containing the repaired session or failure if unrepairable
     */
    suspend fun repairCorruptedSession(corruptedSession: UnifiedWorkoutSession): LiftrixResult<UnifiedWorkoutSession>
    
    /**
     * Creates an emergency backup of the current session.
     * 
     * @param session The session to backup
     * @return Result indicating success or failure
     */
    suspend fun createEmergencyBackup(session: UnifiedWorkoutSession): LiftrixResult<Unit>
    
    /**
     * Recovers from an emergency backup.
     * 
     * @return Result containing the recovered session or null if no backup
     */
    suspend fun recoverFromEmergencyBackup(): LiftrixResult<UnifiedWorkoutSession?>
    
    /**
     * Clears any recovery error state.
     */
    fun clearRecoveryError()
    
    /**
     * Checks if automatic recovery is available for the current state.
     * 
     * @return true if automatic recovery is possible, false otherwise
     */
    suspend fun canAutoRecover(): Boolean
    
    /**
     * Performs automatic recovery if possible.
     * 
     * @return Result containing the recovered session or failure
     */
    suspend fun performAutoRecovery(): LiftrixResult<UnifiedWorkoutSession?>
    
    /**
     * Gets recovery statistics for debugging and monitoring.
     * 
     * @return Map of recovery statistics
     */
    fun getRecoveryStatistics(): Map<String, Any>
}