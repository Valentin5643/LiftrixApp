package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for workout template operations
 */
interface WorkoutTemplateRepository {
    
    /**
     * Get all workout templates for a user
     */
    fun getAllTemplatesForUser(userId: String): Flow<LiftrixResult<List<WorkoutTemplate>>>
    
    /**
     * Get a specific workout template by ID and user ID
     */
    suspend fun getTemplateById(templateId: WorkoutTemplateId, userId: String): LiftrixResult<WorkoutTemplate?>
    
    /**
     * Search workout templates by name for a specific user
     */
    fun searchTemplates(userId: String, searchQuery: String): Flow<LiftrixResult<List<WorkoutTemplate>>>
    
    /**
     * Get templates filtered by folder for a specific user
     */
    fun getTemplatesByFolder(userId: String, folderId: String): Flow<LiftrixResult<List<WorkoutTemplate>>>
    
    /**
     * Get templates filtered by difficulty level for a specific user
     */
    fun getTemplatesByDifficulty(userId: String, difficultyLevel: Int): Flow<LiftrixResult<List<WorkoutTemplate>>>
    
    /**
     * Get most used templates for a specific user
     */
    fun getMostUsedTemplates(userId: String, limit: Int = 10): Flow<LiftrixResult<List<WorkoutTemplate>>>
    
    /**
     * Get recently used templates for a specific user
     */
    fun getRecentlyUsedTemplates(userId: String, limit: Int = 5): Flow<LiftrixResult<List<WorkoutTemplate>>>
    
    /**
     * Create a new workout template
     */
    suspend fun createTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate>
    
    /**
     * Update an existing workout template
     */
    suspend fun updateTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate>
    
    /**
     * Delete a workout template
     */
    suspend fun deleteTemplate(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Unit>
    
    /**
     * Record usage of a template (increment usage count and update last used timestamp)
     */
    suspend fun recordTemplateUsage(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Unit>
    
    /**
     * Check if a template name exists for a user
     */
    suspend fun doesTemplateNameExist(userId: String, name: String): LiftrixResult<Boolean>
    
    /**
     * Get count of templates for a user
     */
    suspend fun getTemplateCount(userId: String): LiftrixResult<Int>
}