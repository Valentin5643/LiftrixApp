package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Data Access Object for workout templates with user-scoped operations
 */
@Dao
interface WorkoutTemplateDao {
    
    /**
     * Insert a new workout template
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long
    
    /**
     * Get all workout templates for a specific user
     * Uses COALESCE to ensure new templates (last_used_at = NULL) appear at top
     */
    @Query("SELECT * FROM workout_templates WHERE user_id = :userId ORDER BY COALESCE(last_used_at, created_at) DESC, created_at DESC")
    fun getAllTemplatesForUser(userId: String): Flow<List<WorkoutTemplateEntity>>
    
    /**
     * Get a specific workout template by ID and user ID
     */
    @Query("SELECT * FROM workout_templates WHERE id = :templateId AND user_id = :userId")
    suspend fun getTemplateById(templateId: String, userId: String): WorkoutTemplateEntity?
    
    /**
     * Search workout templates by name for a specific user
     */
    @Query("""
        SELECT * FROM workout_templates 
        WHERE user_id = :userId 
        AND name LIKE '%' || :searchQuery || '%'
        ORDER BY usage_count DESC, last_used_at DESC
    """)
    fun searchTemplates(userId: String, searchQuery: String): Flow<List<WorkoutTemplateEntity>>
    
    /**
     * Get templates by folder for a specific user
     */
    @Query("""
        SELECT * FROM workout_templates 
        WHERE user_id = :userId 
        AND folder_id = :folderId
        ORDER BY usage_count DESC, last_used_at DESC
    """)
    fun getTemplatesByFolder(userId: String, folderId: String): Flow<List<WorkoutTemplateEntity>>
    
    /**
     * Get most used templates for a specific user
     */
    @Query("""
        SELECT * FROM workout_templates 
        WHERE user_id = :userId 
        ORDER BY usage_count DESC, last_used_at DESC 
        LIMIT :limit
    """)
    fun getMostUsedTemplates(userId: String, limit: Int = 10): Flow<List<WorkoutTemplateEntity>>
    
    /**
     * Get recently used templates for a specific user
     */
    @Query("""
        SELECT * FROM workout_templates 
        WHERE user_id = :userId 
        AND last_used_at IS NOT NULL
        ORDER BY last_used_at DESC 
        LIMIT :limit
    """)
    fun getRecentlyUsedTemplates(userId: String, limit: Int = 5): Flow<List<WorkoutTemplateEntity>>
    
    /**
     * Update an existing workout template
     */
    @Update
    suspend fun updateTemplate(template: WorkoutTemplateEntity): Int
    
    /**
     * Update template usage statistics
     */
    @Query("""
        UPDATE workout_templates 
        SET usage_count = usage_count + 1, last_used_at = :usedAt, updated_at = :usedAt
        WHERE id = :templateId AND user_id = :userId
    """)
    suspend fun incrementUsageCount(templateId: String, userId: String, usedAt: Instant): Int
    
    /**
     * Delete a workout template by ID (user-scoped)
     */
    @Query("DELETE FROM workout_templates WHERE id = :templateId AND user_id = :userId")
    suspend fun deleteTemplate(templateId: String, userId: String): Int
    
    /**
     * Delete a workout template entity
     */
    @Delete
    suspend fun deleteTemplate(template: WorkoutTemplateEntity): Int
    
    /**
     * Check if a template name exists for a user
     */
    @Query("SELECT EXISTS(SELECT 1 FROM workout_templates WHERE user_id = :userId AND name = :name)")
    suspend fun doesTemplateNameExist(userId: String, name: String): Boolean
    
    /**
     * Get unsynced templates for a user
     */
    @Query("SELECT * FROM workout_templates WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedTemplates(userId: String): List<WorkoutTemplateEntity>
    
    /**
     * Mark templates as synced
     */
    @Query("UPDATE workout_templates SET is_synced = 1 WHERE id IN (:templateIds)")
    suspend fun markTemplatesAsSynced(templateIds: List<String>): Int
    
    /**
     * Get count of templates for a user
     */
    @Query("SELECT COUNT(*) FROM workout_templates WHERE user_id = :userId")
    suspend fun getTemplateCount(userId: String): Int
    
    /**
     * Get templates by difficulty level for a specific user
     */
    @Query("""
        SELECT * FROM workout_templates 
        WHERE user_id = :userId 
        AND difficulty_level = :difficultyLevel
        ORDER BY usage_count DESC
    """)
    fun getTemplatesByDifficulty(userId: String, difficultyLevel: Int): Flow<List<WorkoutTemplateEntity>>
    
    /**
     * Update the folder ID for a specific workout template (user-scoped)
     */
    @Query("UPDATE workout_templates SET folder_id = :folderId WHERE id = :templateId AND user_id = :userId")
    suspend fun updateFolderId(templateId: String, folderId: String, userId: String): Int
    
    /**
     * Move a template between folders (user-scoped)
     */
    @Query("UPDATE workout_templates SET folder_id = :newFolderId WHERE id = :templateId AND folder_id = :oldFolderId AND user_id = :userId")
    suspend fun moveBetweenFolders(templateId: String, oldFolderId: String, newFolderId: String, userId: String): Int
    
    /**
     * Get the count of templates in a specific folder (user-scoped)
     */
    @Query("SELECT COUNT(*) FROM workout_templates WHERE folder_id = :folderId AND user_id = :userId")
    suspend fun getFolderTemplateCount(folderId: String, userId: String): Int
    
    /**
     * Bulk update folder IDs for multiple templates (user-scoped)
     */
    @Query("UPDATE workout_templates SET folder_id = :folderId WHERE id IN (:templateIds) AND user_id = :userId")
    suspend fun bulkUpdateFolderIds(templateIds: List<String>, folderId: String, userId: String): Int
    
    /**
     * Move templates between folders (user-scoped)
     */
    @Query("UPDATE workout_templates SET folder_id = :targetFolderId WHERE id IN (:templateIds) AND folder_id = :sourceFolderId AND user_id = :userId")
    suspend fun moveTemplatesBetweenFolders(templateIds: List<String>, sourceFolderId: String, targetFolderId: String, userId: String): Int
    
    /**
     * Validate folder capacity (check if templates can fit in folder)
     */
    suspend fun validateFolderCapacity(folderId: String, additionalCount: Int, userId: String): Boolean {
        val currentCount = getFolderTemplateCount(folderId, userId)
        return (currentCount + additionalCount) <= 100 // Max 100 templates per folder
    }
    
    /**
     * Update folder statistics (placeholder - would update folder metadata)
     */
    suspend fun updateFolderStatistics(folderId: String, userId: String) {
        // This would update folder-level statistics in a separate FolderDao
        // For now, it's a no-op placeholder
    }
    
    /**
     * Update template statistics (placeholder - would update template metadata)
     */
    suspend fun updateTemplateStatistics(templateId: String, userId: String) {
        // This would update template-level statistics
        // For now, it's a no-op placeholder
    }
    
    /**
     * Validate folder creation (check if folder name is valid and unique)
     */
    suspend fun validateFolderCreation(folderName: String, userId: String): Boolean {
        // This would validate against a folders table
        // For now, return true as placeholder
        return folderName.isNotBlank() && folderName.length <= 100
    }
    
    /**
     * Assign templates to a folder (user-scoped)
     */
    @Query("UPDATE workout_templates SET folder_id = :folderId WHERE id IN (:templateIds) AND user_id = :userId")
    suspend fun assignTemplatesToFolder(templateIds: List<String>, folderId: String, userId: String): Int
    
    /**
     * Calculate folder analytics (placeholder - would compute folder-level metrics)
     */
    suspend fun calculateFolderAnalytics(folderId: String, userId: String): Map<String, Any> {
        val templateCount = getFolderTemplateCount(folderId, userId)
        return mapOf(
            "templateCount" to templateCount,
            "avgDifficulty" to 3.0 // Placeholder value
        )
    }

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert workouttemplate from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(workoutTemplate: WorkoutTemplateEntity) {
        val entity = workoutTemplate.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert workouttemplate from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(workoutTemplate: WorkoutTemplateEntity) {
        val local = getWorkoutTemplateForSync(workoutTemplate.id, workoutTemplate.userId)
        if (local == null || workoutTemplate.lastModified > local.lastModified) {
            val entity = workoutTemplate.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis().toInt()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: WorkoutTemplateEntity)

    /**
     * Get dirty workouttemplate that need upload to Firestore.
     */
    @Query("SELECT * FROM workout_templates WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtyWorkoutTemplates(userId: String): List<WorkoutTemplateEntity>

    /**
     * Mark workouttemplate as clean after successful Firestore upload.
     */
    @Query("UPDATE workout_templates SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local workouttemplate for remote deduplication.
     */
    @Query("SELECT * FROM workout_templates WHERE id = :id AND user_id = :userId LIMIT 1")
    suspend fun getWorkoutTemplateForSync(id: String, userId: String): WorkoutTemplateEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
} 
