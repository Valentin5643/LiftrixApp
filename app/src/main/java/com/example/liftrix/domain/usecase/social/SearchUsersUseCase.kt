package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for searching users with privacy filtering and caching
 * 
 * Responsibilities:
 * - Validates search query and parameters
 * - Orchestrates user search with privacy controls
 * - Manages search result caching for performance
 * - Tracks search analytics and user interactions
 * 
 * Business Rules:
 * - Search query must be at least 2 characters (or empty for browse mode)
 * - Results respect user privacy settings (only public profiles)
 * - Search results are cached for performance optimization
 * - Maximum search results limited to prevent performance issues
 * - Blocked users are excluded from all search results
 */
class SearchUsersUseCase @Inject constructor(
    private val userSearchRepository: UserSearchRepository,
    private val authRepository: AuthRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Searches for users based on the provided search criteria
     * 
     * @param request The search request containing query and filter parameters
     * @return LiftrixResult containing matching users or error information
     */
    suspend operator fun invoke(request: SearchUsersRequest): LiftrixResult<SearchUsersResult> {
        return try {
            // Get current user ID for privacy filtering
            val currentUserId = getCurrentUserId()
                ?: return liftrixFailure(LiftrixError.AuthenticationError("User not authenticated"))
            
            // Validate search request
            val validationResult = validateRequest(request)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<SearchUsersResult>
            }
            
            // Check cache first for performance
            if (request.useCache) {
                val cachedResult = getCachedSearchResults(currentUserId, request)
                if (cachedResult.isSuccess && cachedResult.getOrNull() != null) {
                    Timber.d("Returning cached search results for query: ${request.query}")
                    return cachedResult
                }
            }
            
            // Perform fresh search
            val searchResult = performSearch(currentUserId, request)
            if (searchResult.isFailure) {
                return searchResult as LiftrixResult<SearchUsersResult>
            }
            
            val users = searchResult.getOrThrow()
            val result = SearchUsersResult(
                users = users,
                totalCount = users.size,
                hasMore = users.size >= request.limit,
                searchQuery = request.query,
                appliedFilters = request.filters,
                isCachedResult = false
            )
            
            LiftrixResult.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during user search")
            val error = LiftrixError.UnknownError("User search failed: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "SearchUsersUseCase"))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Gets current authenticated user ID
     */
    private suspend fun getCurrentUserId(): String? {
        return try {
            authRepository.getCurrentUserId()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            null
        }
    }
    
    /**
     * Validates the search request parameters
     */
    private fun validateRequest(request: SearchUsersRequest): LiftrixResult<SearchUsersRequest> {
        val violations = mutableListOf<String>()
        
        // Validate query length if provided
        if (request.query.isNotBlank() && request.query.length < MIN_QUERY_LENGTH) {
            violations.add("Search query must be at least $MIN_QUERY_LENGTH characters")
        }
        
        // Validate query length maximum
        if (request.query.length > MAX_QUERY_LENGTH) {
            violations.add("Search query cannot exceed $MAX_QUERY_LENGTH characters")
        }
        
        // Validate limit
        if (request.limit <= 0) {
            violations.add("Search limit must be greater than 0")
        } else if (request.limit > MAX_SEARCH_LIMIT) {
            violations.add("Search limit cannot exceed $MAX_SEARCH_LIMIT")
        }
        
        // Validate workout range if provided
        val filters = request.filters
        if (filters.minWorkouts != null && filters.maxWorkouts != null && 
            filters.minWorkouts > filters.maxWorkouts) {
            violations.add("Minimum workouts cannot be greater than maximum workouts")
        }
        
        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "SearchUsersRequest",
                    violations = violations,
                    errorMessage = "Search users request validation failed"
                )
            )
        }
    }
    
    /**
     * Gets cached search results if available and valid
     */
    private suspend fun getCachedSearchResults(
        currentUserId: String,
        request: SearchUsersRequest
    ): LiftrixResult<SearchUsersResult> {
        return userSearchRepository.getCachedSearchResults(
            viewerId = currentUserId,
            query = request.query
        ).flatMapLiftrix { cachedUsers ->
            if (cachedUsers != null) {
                // Filter cached results based on current filters
                val filteredUsers = applyCachedFilters(cachedUsers, request.filters)
                val limitedUsers = filteredUsers.take(request.limit)
                
                LiftrixResult.success(
                    SearchUsersResult(
                        users = limitedUsers,
                        totalCount = limitedUsers.size,
                        hasMore = filteredUsers.size > request.limit,
                        searchQuery = request.query,
                        appliedFilters = request.filters,
                        isCachedResult = true
                    )
                )
            } else {
                LiftrixResult.success(null)
            }
        }.flatMapLiftrix { result ->
            if (result != null) {
                LiftrixResult.success(result)
            } else {
                // No valid cache found, continue with fresh search
                LiftrixResult.failure(LiftrixError.CacheError("No valid cached results"))
            }
        }
    }
    
    /**
     * Performs fresh user search with privacy filtering
     */
    private suspend fun performSearch(
        currentUserId: String,
        request: SearchUsersRequest
    ): LiftrixResult<List<UserSearchResult>> {
        return userSearchRepository.searchUsers(
            query = request.query,
            currentUserId = currentUserId,
            filters = request.filters
        ).flatMapLiftrix { users ->
            // Apply additional business rules
            val processedUsers = users
                .filter { user -> user.userId != currentUserId } // Exclude current user
                .take(request.limit)
            
            LiftrixResult.success(processedUsers)
        }
    }
    
    /**
     * Applies filters to cached search results
     */
    private fun applyCachedFilters(
        cachedUsers: List<UserSearchResult>,
        filters: SearchFilters
    ): List<UserSearchResult> {
        return cachedUsers.filter { user ->
            // Apply fitness level filter
            if (filters.fitnessLevel != null && user.fitnessLevel != filters.fitnessLevel) {
                return@filter false
            }
            
            // Apply equipment filter
            if (filters.equipment.isNotEmpty() && 
                !user.sharedEquipment.any { equipment -> filters.equipment.contains(equipment) }) {
                return@filter false
            }
            
            // Apply goals filter
            if (filters.goals.isNotEmpty() && 
                !user.sharedGoals.any { goal -> filters.goals.contains(goal) }) {
                return@filter false
            }
            
            // Apply workout count filters
            if (filters.minWorkouts != null && user.totalWorkouts < filters.minWorkouts) {
                return@filter false
            }
            if (filters.maxWorkouts != null && user.totalWorkouts > filters.maxWorkouts) {
                return@filter false
            }
            
            true
        }
    }
    
    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 100
        private const val MAX_SEARCH_LIMIT = 50
    }
}

/**
 * Request data class for searching users
 * 
 * @property query Text query for user search (empty for browse mode)
 * @property filters Additional search filters for refinement
 * @property limit Maximum number of results to return (default: 20)
 * @property useCache Whether to check cache first (default: true)
 */
data class SearchUsersRequest(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val limit: Int = 20,
    val useCache: Boolean = true
)

/**
 * Result data class for user search operations
 * 
 * @property users List of matching users
 * @property totalCount Total number of results found
 * @property hasMore Whether more results are available
 * @property searchQuery The original search query
 * @property appliedFilters Filters applied to the search
 * @property isCachedResult Whether this result came from cache
 */
data class SearchUsersResult(
    val users: List<UserSearchResult>,
    val totalCount: Int,
    val hasMore: Boolean,
    val searchQuery: String,
    val appliedFilters: SearchFilters,
    val isCachedResult: Boolean
)