package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber
import javax.inject.Inject

/**
 * Consolidated use case for social search operations.
 *
 * Consolidates:
 * - SearchSocialProfilesUseCase (simple profile search by username/display name)
 * - SearchUsersUseCase (advanced user search with filters and caching)
 *
 * Part of Phase 3: Social & Workout Domains consolidation.
 * Ref: SPEC-20251031-usecase-consolidation.md
 *
 * Business Rules:
 * - Search query must be at least 2 characters (or empty for browse mode)
 * - Results respect user privacy settings (only public profiles)
 * - Search results are cached for performance optimization
 * - Maximum search results limited to prevent performance issues
 * - Blocked users are excluded from all search results
 */
class SocialSearchUseCase @Inject constructor(
    private val socialProfileRepository: SocialProfileRepository,
    private val userSearchRepository: UserSearchRepository,
    private val authRepository: AuthRepository,
    private val authQueryUseCase: AuthQueryUseCase,
    private val errorHandler: ErrorHandler
) {

    /**
     * Simple search for social profiles by username or display name.
     * Replaces: SearchSocialProfilesUseCase.invoke()
     *
     * @param query The search query
     * @param limit Maximum number of results to return (1-100)
     * @return List of matching profiles visible to current user
     */
    suspend operator fun invoke(
        query: String,
        limit: Int = 20
    ): LiftrixResult<List<SocialProfile>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SEARCH_SOCIAL_PROFILES",
                errorMessage = "Failed to search social profiles",
                analyticsContext = mapOf(
                    "query" to query,
                    "limit" to limit.toString(),
                    "error" to (throwable.message ?: "Unknown error")
                )
            )
        }
    ) {
        val userId = authQueryUseCase(waitForAuth = false).getOrNull()
            ?: throw IllegalStateException("User not authenticated")

        // Validate inputs
        if (query.isBlank()) {
            throw IllegalArgumentException("Search query cannot be blank")
        }
        if (query.length < 2) {
            throw IllegalArgumentException("Search query must be at least 2 characters")
        }
        if (limit <= 0 || limit > 100) {
            throw IllegalArgumentException("Limit must be between 1 and 100")
        }

        socialProfileRepository.searchProfiles(userId.value, query.trim(), limit).getOrThrow()
    }

    /**
     * Advanced user search with filters and caching.
     * Replaces: SearchUsersUseCase.invoke()
     *
     * @param request The search request containing query and filter parameters
     * @return LiftrixResult containing matching users or error information
     */
    suspend fun searchUsers(request: SearchUsersRequest): LiftrixResult<SearchUsersResult> {
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
            errorHandler.handleError(error, mapOf("context" to "SocialSearchUseCase"))
            LiftrixResult.failure(error)
        }
    }

    // Private helper methods

    /**
     * Gets current authenticated user ID
     */
    private suspend fun getCurrentUserId(): String? {
        return try {
            authRepository.getCurrentUserId()?.value
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
        val minWorkouts = filters.minWorkouts
        val maxWorkouts = filters.maxWorkouts
        if (minWorkouts != null && maxWorkouts != null && minWorkouts > maxWorkouts) {
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
            val minWorkouts = filters.minWorkouts
            val maxWorkouts = filters.maxWorkouts
            if (minWorkouts != null && user.totalWorkouts < minWorkouts) {
                return@filter false
            }
            if (maxWorkouts != null && user.totalWorkouts > maxWorkouts) {
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
