package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.FolderId
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for folder operations with user-scoped access
 * 
 * Provides reactive data access for folder management and organization of workout templates.
 * All operations are user-scoped to ensure data isolation between users.
 * 
 * Uses Kotlin Result for error handling and Flow for reactive data streams.
 */
interface FolderRepository {
    
    /**
     * Get all folders for a specific user
     * 
     * @param userId The user ID to get folders for
     * @return Flow of folder list ordered by creation date (most recent first)
     */
    fun getAllFoldersForUser(userId: String): Flow<List<Folder>>
    
    /**
     * Get a specific folder by ID with user-scoped access
     * 
     * @param folderId The folder ID to retrieve
     * @param userId The user ID to ensure user-scoped access
     * @return Flow of folder or null if not found
     */
    fun getFolderById(folderId: FolderId, userId: String): Flow<Folder?>
    
    /**
     * Create a new folder
     * 
     * @param folder The folder to create
     * @return Result containing the created folder or error
     */
    suspend fun createFolder(folder: Folder): Result<Folder>
    
    /**
     * Update an existing folder
     * 
     * @param folder The folder to update
     * @return Result containing the updated folder or error
     */
    suspend fun updateFolder(folder: Folder): Result<Folder>
    
    /**
     * Delete a folder by ID with user-scoped access
     * 
     * Note: When a folder is deleted, all templates in that folder should be moved
     * to the user's "Uncategorized" folder to maintain data integrity.
     * 
     * @param folderId The folder ID to delete
     * @param userId The user ID to ensure user-scoped deletion
     * @return Result indicating success or failure
     */
    suspend fun deleteFolder(folderId: FolderId, userId: String): Result<Unit>
    
    /**
     * Move a workout template to a different folder
     * 
     * Updates the template's folder assignment and maintains folder template counts.
     * 
     * @param templateId The workout template ID to move
     * @param targetFolderId The target folder ID to move the template to
     * @param userId The user ID to ensure user-scoped access
     * @return Result indicating success or failure
     */
    suspend fun moveTemplateToFolder(
        templateId: String, 
        targetFolderId: FolderId, 
        userId: String
    ): Result<Unit>
    
    /**
     * Update the template count for a specific folder
     * 
     * @param folderId The folder ID to update
     * @param newCount The new template count
     * @param userId The user ID to ensure user-scoped access
     * @return Result indicating success or failure
     */
    suspend fun updateTemplateCount(
        folderId: FolderId, 
        newCount: Int, 
        userId: String
    ): Result<Unit>
    
    /**
     * Get a folder by ID without user scope check (for internal operations)
     * 
     * @param folderId The folder ID to retrieve
     * @return The folder or null if not found
     */
    suspend fun getFolderById(folderId: FolderId): Folder?
    
    /**
     * Check if a folder name already exists for a user
     * 
     * @param userId The user ID to check within
     * @param name The folder name to check
     * @return True if the folder name exists for the user, false otherwise
     */
    suspend fun doesFolderNameExist(userId: String, name: String): Boolean
    
    /**
     * Get the total number of folders for a user
     * 
     * @param userId The user ID to count folders for
     * @return The number of folders for the user
     */
    suspend fun getFolderCount(userId: String): Int
    
    /**
     * Search folders by name for a specific user
     * 
     * @param userId The user ID to search within
     * @param searchQuery The search query to match against folder names
     * @return Flow of matching folders ordered by creation date
     */
    fun searchFolders(userId: String, searchQuery: String): Flow<List<Folder>>
    
    /**
     * Get folders that haven't been synced to Firebase for a user
     * 
     * @param userId The user ID to get unsynced folders for
     * @return List of folders that need to be synced
     */
    suspend fun getUnsyncedFolders(userId: String): List<Folder>
    
    /**
     * Mark folders as synced to Firebase
     * 
     * @param folderIds List of folder IDs to mark as synced
     * @return Result indicating success or failure
     */
    suspend fun markFoldersAsSynced(folderIds: List<FolderId>): Result<Unit>
    
    /**
     * Get or create the default "Uncategorized" folder for a user
     * 
     * This ensures every user has at least one folder for organizing templates.
     * 
     * @param userId The user ID to get/create default folder for
     * @return Result containing the default folder or error
     */
    suspend fun getOrCreateDefaultFolder(userId: String): Result<Folder>
}