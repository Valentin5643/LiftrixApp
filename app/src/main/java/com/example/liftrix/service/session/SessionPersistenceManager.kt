package com.example.liftrix.service.session

import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Interface for managing workout session persistence.
 * 
 * Responsible for:
 * - Session data serialization and storage
 * - Session data loading and deserialization
 * - Persistent storage management
 * - Session data validation
 * 
 * Follows Interface Segregation Principle by focusing only on persistence operations.
 */
interface SessionPersistenceManager {
    
    /**
     * Persists a workout session to storage.
     * 
     * @param session The session to persist
     * @return Result indicating success or failure with error details
     */
    suspend fun persistSession(session: UnifiedWorkoutSession): LiftrixResult<Unit>
    
    /**
     * Loads a persisted session from storage.
     * 
     * @return Result containing the loaded session or null if no session exists
     */
    suspend fun loadPersistedSession(): LiftrixResult<UnifiedWorkoutSession?>
    
    /**
     * Checks if a persisted session exists in storage.
     * 
     * @return true if a persisted session exists, false otherwise
     */
    suspend fun hasPersistedSession(): Boolean
    
    /**
     * Clears any persisted session data from storage.
     * 
     * @return Result indicating success or failure
     */
    suspend fun clearPersistedSession(): LiftrixResult<Unit>
    
    /**
     * Validates the integrity of a persisted session.
     * 
     * @param session The session to validate
     * @return Result indicating if the session is valid
     */
    fun validateSession(session: UnifiedWorkoutSession): LiftrixResult<Unit>
    
    /**
     * Gets the size of persisted session data in bytes.
     * 
     * @return Size in bytes, 0 if no persisted session
     */
    suspend fun getPersistedSessionSize(): Long
    
    /**
     * Backs up the current persisted session to a backup location.
     * 
     * @return Result indicating success or failure
     */
    suspend fun backupPersistedSession(): LiftrixResult<Unit>
    
    /**
     * Restores a session from backup if available.
     * 
     * @return Result containing the restored session or null if no backup exists
     */
    suspend fun restoreFromBackup(): LiftrixResult<UnifiedWorkoutSession?>
}