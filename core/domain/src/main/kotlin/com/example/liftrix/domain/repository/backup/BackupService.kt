package com.example.liftrix.domain.repository.backup

import com.example.liftrix.domain.model.common.LiftrixResult

/**
 * Service interface for managing data backup and restoration operations.
 * 
 * This service handles creating backups of user data and restoring data
 * from backups during error recovery scenarios.
 */
interface BackupService {
    
    /**
     * Creates a backup of all user data.
     * 
     * @param userId The ID of the user whose data to backup
     * @return A Result containing the backup ID if successful
     */
    suspend fun createBackup(userId: String): LiftrixResult<String>
    
    /**
     * Restores user data from a specific backup.
     * 
     * @param userId The ID of the user whose data to restore
     * @param backupId The ID of the backup to restore from
     * @return A Result indicating success or failure
     */
    suspend fun restoreFromBackup(userId: String, backupId: String): LiftrixResult<Unit>
    
    /**
     * Lists all available backups for a user.
     * 
     * @param userId The ID of the user whose backups to list
     * @return A Result containing the list of backup IDs
     */
    suspend fun listBackups(userId: String): LiftrixResult<List<String>>
    
    /**
     * Deletes a specific backup.
     * 
     * @param userId The ID of the user who owns the backup
     * @param backupId The ID of the backup to delete
     * @return A Result indicating success or failure
     */
    suspend fun deleteBackup(userId: String, backupId: String): LiftrixResult<Unit>
    
    /**
     * Checks if a backup exists and is valid.
     * 
     * @param userId The ID of the user who owns the backup
     * @param backupId The ID of the backup to validate
     * @return A Result containing true if backup is valid
     */
    suspend fun validateBackup(userId: String, backupId: String): LiftrixResult<Boolean>
}