package com.example.liftrix.domain.repository.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for workout template operations following single responsibility principle.
 * 
 * Handles:
 * - Template CRUD operations
 * - Template organization and categorization
 * - Template search and filtering
 * - User-scoped template management
 * 
 * Does NOT handle:
 * - Template usage statistics (see TemplateAnalyticsRepository)
 * - Template sharing and social features (see TemplateSharingRepository)
 * - Template creation from workouts (see TemplateCreationRepository)
 */
interface TemplateRepository {
    
    /**
     * Create a new workout template for the specified user.
     * 
     * @param template The template to create (must include valid userId)
     * @return LiftrixResult with created template including generated ID
     */
    suspend fun createTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate>
    
    /**
     * Get a specific template by ID for the specified user.
     * 
     * @param templateId The template ID to retrieve
     * @param userId The user ID for data scoping
     * @return LiftrixResult with template if found, null otherwise
     */
    suspend fun getTemplateById(templateId: WorkoutTemplateId, userId: String): LiftrixResult<WorkoutTemplate?>
    
    /**
     * Get all templates for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @return Flow of user's templates from the local source of truth
     */
    fun getAllTemplatesForUser(userId: String): Flow<List<WorkoutTemplate>>
    
    /**
     * Update an existing workout template.
     * 
     * @param template The template with updated data (must include valid ID and userId)
     * @return LiftrixResult with updated template
     */
    suspend fun updateTemplate(template: WorkoutTemplate): LiftrixResult<WorkoutTemplate>
    
    /**
     * Delete a template for the specified user.
     * 
     * @param templateId The ID of the template to delete
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult indicating success or failure
     */
    suspend fun deleteTemplate(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Unit>
    
    /**
     * Search templates by name for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @param searchQuery Search term for template name or description
     * @return Flow of matching templates from the local source of truth
     */
    fun searchTemplates(userId: String, searchQuery: String): Flow<List<WorkoutTemplate>>
    
    /**
     * Get templates filtered by folder for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @param folderId The folder ID to filter by
     * @return Flow of templates in the specified folder from the local source of truth
     */
    fun getTemplatesByFolder(userId: String, folderId: String): Flow<List<WorkoutTemplate>>
    
    /**
     * Get templates filtered by difficulty level for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @param difficultyLevel Difficulty level (1-5, where 1 is beginner and 5 is expert)
     * @return Flow of templates at the specified difficulty level from the local source of truth
     */
    fun getTemplatesByDifficulty(userId: String, difficultyLevel: Int): Flow<List<WorkoutTemplate>>
    
    /**
     * Get recently used templates for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @param limit Maximum number of templates to return (default: 5)
     * @return Flow of recently used templates ordered by last used date from the local source of truth
     */
    fun getRecentlyUsedTemplates(userId: String, limit: Int = 5): Flow<List<WorkoutTemplate>>
    
    /**
     * Get most frequently used templates for a specific user.
     * 
     * @param userId The user ID for data scoping
     * @param limit Maximum number of templates to return (default: 10)
     * @return Flow of most used templates ordered by usage count from the local source of truth
     */
    fun getMostUsedTemplates(userId: String, limit: Int = 10): Flow<List<WorkoutTemplate>>
    
    /**
     * Check if a template name exists for a user.
     * 
     * @param userId The user ID for data scoping
     * @param name The template name to check
     * @return LiftrixResult with true if name exists, false otherwise
     */
    suspend fun doesTemplateNameExist(userId: String, name: String): LiftrixResult<Boolean>
    
    /**
     * Check if a template exists for the specified user.
     * 
     * @param templateId The template ID to check
     * @param userId The user ID for data scoping
     * @return LiftrixResult with true if template exists, false otherwise
     */
    suspend fun templateExists(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Boolean>
    
    /**
     * Get total count of templates for a user.
     * 
     * @param userId The user ID for data scoping
     * @return LiftrixResult with total template count
     */
    suspend fun getTemplateCount(userId: String): LiftrixResult<Int>
    
    /**
     * Record usage of a template (increment usage count and update last used timestamp).
     * 
     * @param templateId The template ID to record usage for
     * @param userId The user ID for data scoping and authorization
     * @return LiftrixResult indicating success or failure
     */
    suspend fun recordTemplateUsage(templateId: WorkoutTemplateId, userId: String): LiftrixResult<Unit>
    
    /**
     * Duplicate an existing template for the same user.
     * 
     * @param templateId The template ID to duplicate
     * @param userId The user ID for data scoping and authorization
     * @param newName Optional new name for the duplicated template
     * @return LiftrixResult with the newly created duplicate template
     */
    suspend fun duplicateTemplate(
        templateId: WorkoutTemplateId, 
        userId: String, 
        newName: String? = null
    ): LiftrixResult<WorkoutTemplate>
}
