package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
     */
    @Query("SELECT * FROM workout_templates WHERE user_id = :userId ORDER BY last_used_at DESC, created_at DESC")
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
     * Get templates by tags for a specific user
     */
    @Query("""
        SELECT * FROM workout_templates 
        WHERE user_id = :userId 
        AND tags LIKE '%' || :tag || '%'
        ORDER BY usage_count DESC
    """)
    fun getTemplatesByTag(userId: String, tag: String): Flow<List<WorkoutTemplateEntity>>
    
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
} 