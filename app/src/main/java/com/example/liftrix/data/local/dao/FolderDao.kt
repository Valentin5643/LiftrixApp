package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for folder operations with user-scoped access
 * 
 * Provides CRUD operations for organizing workout templates into folders.
 * All operations are user-scoped to ensure data isolation between users.
 */
@Dao
interface FolderDao {
    
    /**
     * Insert a new folder
     * 
     * @param folder The folder entity to insert
     * @return The row ID of the inserted folder
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: FolderEntity): Long
    
    /**
     * Update an existing folder
     * 
     * @param folder The folder entity to update
     * @return Number of rows affected (should be 1 for successful update)
     */
    @Update
    suspend fun updateFolder(folder: FolderEntity): Int
    
    /**
     * Delete a folder by ID (user-scoped for security)
     * 
     * @param folderId The ID of the folder to delete
     * @param userId The user ID to ensure user-scoped deletion
     * @return Number of rows affected (should be 1 for successful deletion)
     */
    @Query("DELETE FROM folders WHERE id = :folderId AND user_id = :userId")
    suspend fun deleteFolder(folderId: String, userId: String): Int
    
    /**
     * Delete a folder entity
     * 
     * @param folder The folder entity to delete
     * @return Number of rows affected (should be 1 for successful deletion)
     */
    @Delete
    suspend fun deleteFolder(folder: FolderEntity): Int
    
    /**
     * Get all folders for a specific user
     * 
     * @param userId The user ID to get folders for
     * @return Flow of folder list ordered by creation date (most recent first)
     */
    @Query("SELECT * FROM folders WHERE user_id = :userId ORDER BY created_at DESC")
    fun getFoldersByUserId(userId: String): Flow<List<FolderEntity>>
    
    /**
     * Get a specific folder by ID and user ID
     * 
     * @param folderId The folder ID to retrieve
     * @param userId The user ID to ensure user-scoped access
     * @return The folder entity or null if not found
     */
    @Query("SELECT * FROM folders WHERE id = :folderId AND user_id = :userId")
    suspend fun getFolderById(folderId: String, userId: String): FolderEntity?
    
    /**
     * Get a folder by ID (without user scope - for internal operations)
     * 
     * @param folderId The folder ID to retrieve
     * @return The folder entity or null if not found
     */
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?
    
    /**
     * Update the template count for a specific folder
     * 
     * @param folderId The folder ID to update
     * @param count The new template count
     * @return Number of rows affected (should be 1 for successful update)
     */
    @Query("UPDATE folders SET template_count = :count, updated_at = :updatedAt WHERE id = :folderId")
    suspend fun updateTemplateCount(folderId: String, count: Int, updatedAt: Long): Int
    
    /**
     * Increment the template count for a specific folder
     * 
     * @param folderId The folder ID to update
     * @param updatedAt The timestamp when the update occurred
     * @return Number of rows affected (should be 1 for successful update)
     */
    @Query("UPDATE folders SET template_count = template_count + 1, updated_at = :updatedAt WHERE id = :folderId")
    suspend fun incrementTemplateCount(folderId: String, updatedAt: Long): Int
    
    /**
     * Decrement the template count for a specific folder
     * 
     * @param folderId The folder ID to update
     * @param updatedAt The timestamp when the update occurred
     * @return Number of rows affected (should be 1 for successful update)
     */
    @Query("UPDATE folders SET template_count = template_count - 1, updated_at = :updatedAt WHERE id = :folderId AND template_count > 0")
    suspend fun decrementTemplateCount(folderId: String, updatedAt: Long): Int
    
    /**
     * Check if a folder name exists for a user
     * 
     * @param userId The user ID to check within
     * @param name The folder name to check
     * @return True if the folder name exists for the user, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM folders WHERE user_id = :userId AND name = :name)")
    suspend fun doesFolderNameExist(userId: String, name: String): Boolean
    
    /**
     * Get count of folders for a user
     * 
     * @param userId The user ID to count folders for
     * @return The number of folders for the user
     */
    @Query("SELECT COUNT(*) FROM folders WHERE user_id = :userId")
    suspend fun getFolderCount(userId: String): Int
    
    /**
     * Get unsynced folders for a user
     * 
     * @param userId The user ID to get unsynced folders for
     * @return List of folders that haven't been synced to Firebase
     */
    @Query("SELECT * FROM folders WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedFolders(userId: String): List<FolderEntity>
    
    /**
     * Mark folders as synced
     * 
     * @param folderIds List of folder IDs to mark as synced
     * @return Number of rows affected
     */
    @Query("UPDATE folders SET is_synced = 1 WHERE id IN (:folderIds)")
    suspend fun markFoldersAsSynced(folderIds: List<String>): Int
    
    /**
     * Search folders by name for a specific user
     * 
     * @param userId The user ID to search within
     * @param searchQuery The search query to match against folder names
     * @return Flow of matching folders ordered by creation date
     */
    @Query("""
        SELECT * FROM folders 
        WHERE user_id = :userId 
        AND name LIKE '%' || :searchQuery || '%'
        ORDER BY created_at DESC
    """)
    fun searchFolders(userId: String, searchQuery: String): Flow<List<FolderEntity>>
}