package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for retrieving workout templates with filtering, sorting, and user authorization.
 * 
 * Responsibilities:
 * - Validates user authorization and request parameters
 * - Applies filters and sorting to template results
 * - Handles empty results and provides recommendations
 * - Manages template categorization and organization
 * 
 * Business Rules:
 * - User can only access their own templates
 * - Templates returned in order of relevance (recently used, frequently used, alphabetical)
 * - Filter combinations must be valid and performant
 * - Empty results should provide helpful suggestions
 * - Recently used templates prioritized for better UX
 */
class GetTemplatesUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Retrieves workout templates based on the provided criteria.
     * 
     * @param request The request containing user ID and filter criteria
     * @return Flow of LiftrixResult containing templates or error information
     */
    operator fun invoke(request: GetTemplatesRequest): Flow<LiftrixResult<GetTemplatesResult>> {
        return validateRequest(request)
            .let { validationResult ->
                when (validationResult.isSuccess) {
                    true -> performTemplateRetrieval(validationResult.getOrThrow())
                    false -> kotlinx.coroutines.flow.flowOf(
                        liftrixFailure(validationResult.exceptionOrNull() as LiftrixError)
                    )
                }
            }
    }
    
    /**
     * Validates the request parameters for retrieving templates.
     */
    private fun validateRequest(request: GetTemplatesRequest): LiftrixResult<GetTemplatesRequest> {
        val violations = mutableListOf<String>()
        
        // Validate user ID
        if (request.userId.isBlank()) {
            violations.add("User ID is required")
        }
        
        // Validate search query length if provided
        if (request.searchQuery != null && request.searchQuery.length > MAX_SEARCH_QUERY_LENGTH) {
            violations.add("Search query cannot exceed $MAX_SEARCH_QUERY_LENGTH characters")
        }
        
        // Validate search query minimum length if provided
        if (request.searchQuery != null && request.searchQuery.isNotBlank() && request.searchQuery.length < MIN_SEARCH_QUERY_LENGTH) {
            violations.add("Search query must be at least $MIN_SEARCH_QUERY_LENGTH characters")
        }
        
        // Validate difficulty level if provided
        if (request.difficultyLevel != null && (request.difficultyLevel < 1 || request.difficultyLevel > 5)) {
            violations.add("Difficulty level must be between 1 and 5")
        }
        
        // Validate limit
        if (request.limit <= 0) {
            violations.add("Limit must be greater than 0")
        } else if (request.limit > MAX_TEMPLATE_LIMIT) {
            violations.add("Limit cannot exceed $MAX_TEMPLATE_LIMIT")
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "GetTemplatesRequest",
                    violations = violations
                )
            )
        }
    }
    
    /**
     * Performs the template retrieval based on validated request parameters.
     */
    private fun performTemplateRetrieval(request: GetTemplatesRequest): Flow<LiftrixResult<GetTemplatesResult>> {
        // 🔥 OPTIMIZATION: Use database-level folder filtering when folderId is provided
        return if (!request.folderId.isNullOrBlank()) {
            // Use optimized folder-specific query
            getTemplatesByFolder(request)
        } else {
            // Use existing sorting logic when no folder filter
            when (request.sortBy) {
                TemplateSortBy.RECENT -> getRecentTemplates(request)
                TemplateSortBy.MOST_USED -> getMostUsedTemplates(request)
                TemplateSortBy.ALPHABETICAL -> getAllTemplatesAlphabetical(request)
                TemplateSortBy.DIFFICULTY -> getTemplatesByDifficulty(request)
            }
        }.map { templatesResult ->
            templatesResult.map { templates ->
                val filteredTemplates = applyFilters(templates, request)
                val limitedTemplates = filteredTemplates.take(request.limit)
                
                GetTemplatesResult(
                    templates = limitedTemplates,
                    totalCount = filteredTemplates.size,
                    hasMore = filteredTemplates.size > request.limit,
                    appliedFilters = createAppliedFilters(request),
                    sortedBy = request.sortBy
                )
            }
        }.catch { throwable ->
            emit(
                liftrixFailure(
                    LiftrixError.DatabaseError(
                        errorMessage = "Failed to retrieve templates",
                        operation = "getTemplates",
                        analyticsContext = mapOf(
                            "userId" to request.userId,
                            "sortBy" to request.sortBy.name
                        )
                    )
                )
            )
        }
    }
    
    /**
     * Retrieves templates sorted by recent usage.
     */
    private fun getRecentTemplates(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return if (request.hasFilters()) {
            // If filters are applied, get all templates and filter them
            templateRepository.getAllTemplatesForUser(request.userId)
        } else {
            // 🔥 CHANGED: Use getAllTemplatesForUser to include newly created templates
            // getRecentlyUsedTemplates excludes templates with usageCount=0 or lastUsedAt=null
            timber.log.Timber.d("🔥 RECENT-TEMPLATES: Loading all templates for user ${request.userId} (including new ones)")
            templateRepository.getAllTemplatesForUser(request.userId)
        }
    }
    
    /**
     * Retrieves templates sorted by usage frequency.
     */
    private fun getMostUsedTemplates(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return if (request.hasFilters()) {
            // If filters are applied, get all templates and filter them
            templateRepository.getAllTemplatesForUser(request.userId)
        } else {
            // If no filters, use optimized most used templates query
            templateRepository.getMostUsedTemplates(request.userId, request.limit)
        }
    }
    
    /**
     * Retrieves all templates sorted alphabetically.
     */
    private fun getAllTemplatesAlphabetical(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return templateRepository.getAllTemplatesForUser(request.userId)
            .map { result ->
                result.map { templates ->
                    templates.sortedBy { it.name.lowercase() }
                }
            }
    }
    
    /**
     * Retrieves templates filtered by difficulty level.
     */
    private fun getTemplatesByDifficulty(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return if (request.difficultyLevel != null) {
            // Use specific difficulty query if provided
            templateRepository.getTemplatesByDifficulty(request.userId, request.difficultyLevel)
        } else {
            // Get all templates if no specific difficulty requested
            templateRepository.getAllTemplatesForUser(request.userId)
        }
    }
    
    /**
     * 🔥 NEW: Retrieves templates filtered by folder using optimized database query.
     * This avoids loading all templates into memory and filtering them afterwards.
     */
    private fun getTemplatesByFolder(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        timber.log.Timber.d("🔥 FOLDER-QUERY: Using database-level folder filtering for folderId: ${request.folderId}")
        
        return templateRepository.getTemplatesByFolder(request.userId, request.folderId!!)
            .map { result ->
                result.map { templates ->
                    // Apply sorting to folder-specific templates
                    when (request.sortBy) {
                        TemplateSortBy.RECENT -> templates.sortedByDescending { it.lastUsedAt ?: it.createdAt }
                        TemplateSortBy.MOST_USED -> templates.sortedByDescending { it.usageCount }
                        TemplateSortBy.ALPHABETICAL -> templates.sortedBy { it.name.lowercase() }
                        TemplateSortBy.DIFFICULTY -> templates.sortedBy { it.difficultyLevel ?: 0 }
                    }
                }
            }
    }
    
    /**
     * Applies additional filters to the template list.
     * 🔥 OPTIMIZATION: Folder filtering now handled at database level, not in-memory.
     */
    private fun applyFilters(templates: List<WorkoutTemplate>, request: GetTemplatesRequest): List<WorkoutTemplate> {
        var filteredTemplates = templates
        
        // Apply search query filter using simple string matching
        if (!request.searchQuery.isNullOrBlank()) {
            val searchQuery = request.searchQuery.lowercase()
            filteredTemplates = filteredTemplates.filter { template ->
                template.name.lowercase().contains(searchQuery) ||
                template.description?.lowercase()?.contains(searchQuery) == true
            }
        }
        
        // 🔥 REMOVED: Folder filter now handled at database level in getTemplatesByFolder()
        // This eliminates the need to load all templates and filter in memory
        
        // Apply difficulty filter (if not already applied by repository)
        if (request.difficultyLevel != null && request.sortBy != TemplateSortBy.DIFFICULTY) {
            filteredTemplates = filteredTemplates.filter { template ->
                template.difficultyLevel == request.difficultyLevel
            }
        }
        
        return filteredTemplates
    }
    
    /**
     * Creates a summary of applied filters for the result.
     */
    private fun createAppliedFilters(request: GetTemplatesRequest): TemplateFilters {
        return TemplateFilters(
            searchQuery = request.searchQuery,
            folderId = request.folderId,
            difficultyLevel = request.difficultyLevel
        )
    }
    
    companion object {
        private const val MIN_SEARCH_QUERY_LENGTH = 2
        private const val MAX_SEARCH_QUERY_LENGTH = 100
        private const val MAX_TEMPLATE_LIMIT = 100
    }
}

/**
 * Request data class for retrieving workout templates.
 * 
 * @property userId The ID of the user requesting templates
 * @property sortBy How to sort the templates
 * @property searchQuery Optional search query for template names
 * @property folderId Optional folder ID to filter templates
 * @property difficultyLevel Optional difficulty level filter (1-5)
 * @property limit Maximum number of templates to return (default: 20)
 */
data class GetTemplatesRequest(
    val userId: String,
    val sortBy: TemplateSortBy = TemplateSortBy.RECENT,
    val searchQuery: String? = null,
    val folderId: String? = null,
    val difficultyLevel: Int? = null,
    val limit: Int = 20
) {
    fun hasFilters(): Boolean = !searchQuery.isNullOrBlank() || !folderId.isNullOrBlank() || difficultyLevel != null
}

/**
 * Result data class for template retrieval operations.
 * 
 * @property templates List of retrieved templates
 * @property totalCount Total number of templates found (before limit applied)
 * @property hasMore Whether more templates are available
 * @property appliedFilters Summary of filters applied to the search
 * @property sortedBy The sorting method used
 */
data class GetTemplatesResult(
    val templates: List<WorkoutTemplate>,
    val totalCount: Int,
    val hasMore: Boolean,
    val appliedFilters: TemplateFilters,
    val sortedBy: TemplateSortBy
)

/**
 * Summary of filters applied to template retrieval.
 * 
 * @property searchQuery Search query used for filtering
 * @property folderId Folder ID used for filtering
 * @property difficultyLevel Difficulty level used for filtering
 */
data class TemplateFilters(
    val searchQuery: String?,
    val folderId: String?,
    val difficultyLevel: Int?
) {
    val hasFilters: Boolean
        get() = !searchQuery.isNullOrBlank() || !folderId.isNullOrBlank() || difficultyLevel != null
}

/**
 * Enumeration of template sorting options.
 */
enum class TemplateSortBy {
    /**
     * Sort by most recently used templates first
     */
    RECENT,
    
    /**
     * Sort by most frequently used templates first
     */
    MOST_USED,
    
    /**
     * Sort alphabetically by template name
     */
    ALPHABETICAL,
    
    /**
     * Sort by difficulty level (1-5)
     */
    DIFFICULTY
}