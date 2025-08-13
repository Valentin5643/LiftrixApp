package com.example.liftrix.domain.repository.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.FitnessLevel

/**
 * Repository interface for user profile search with privacy filtering and advanced matching.
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Responsibilities:
 * - Privacy-aware profile search with viewer context validation
 * - Advanced filtering by fitness goals, equipment, and experience level
 * - Suggestion algorithms based on mutual connections and interests
 * - Search result caching and performance optimization
 * - Analytics tracking for search patterns and engagement
 * 
 * Security: All search operations respect privacy settings and blocked user lists
 */
interface ProfileSearchRepository {

    // ========================================
    // Core Search Operations
    // ========================================

    /**
     * Search users with comprehensive filtering and privacy checks
     */
    suspend fun searchUsers(
        query: String,
        viewerId: String,
        filters: SearchFilters = SearchFilters(),
        limit: Int = 20,
        offset: Int = 0
    ): LiftrixResult<List<UserSearchResult>>

    /**
     * Get user profile with privacy filtering based on viewer context
     */
    suspend fun getFilteredProfile(
        profileUserId: String,
        viewerId: String
    ): LiftrixResult<SocialProfile?>

    /**
     * Search users by username with exact and partial matches
     */
    suspend fun searchByUsername(
        username: String,
        viewerId: String,
        includePartialMatches: Boolean = true,
        limit: Int = 10
    ): LiftrixResult<List<UserSearchResult>>

    /**
     * Search users by display name
     */
    suspend fun searchByDisplayName(
        displayName: String,
        viewerId: String,
        limit: Int = 20
    ): LiftrixResult<List<UserSearchResult>>

    // ========================================
    // Advanced Filtering & Suggestions
    // ========================================

    /**
     * Get suggested users based on mutual connections
     */
    suspend fun getSuggestedUsersByMutualConnections(
        userId: String,
        limit: Int = 20
    ): LiftrixResult<List<UserSearchResult>>

    /**
     * Get suggested users based on similar fitness goals and equipment
     */
    suspend fun getSuggestedUsersByInterests(
        userId: String,
        limit: Int = 20
    ): LiftrixResult<List<UserSearchResult>>

    /**
     * Get suggested users based on geographic proximity (if location sharing enabled)
     */
    suspend fun getSuggestedUsersByLocation(
        userId: String,
        radiusKm: Double = 50.0,
        limit: Int = 20
    ): LiftrixResult<List<UserSearchResult>>

    /**
     * Get trending or popular users (high engagement, active)
     */
    suspend fun getTrendingUsers(
        viewerId: String,
        timeWindowDays: Int = 7,
        limit: Int = 20
    ): LiftrixResult<List<UserSearchResult>>

    /**
     * Get new users (recently joined)
     */
    suspend fun getNewUsers(
        viewerId: String,
        joinedWithinDays: Int = 30,
        limit: Int = 20
    ): LiftrixResult<List<UserSearchResult>>

    // ========================================
    // Search Analytics & Optimization
    // ========================================

    /**
     * Track search query for analytics and improvement
     */
    suspend fun trackSearch(
        query: String,
        viewerId: String,
        resultCount: Int,
        selectedProfileId: String? = null
    ): LiftrixResult<Unit>

    /**
     * Get popular search queries for suggestions
     */
    suspend fun getPopularSearchQueries(
        limit: Int = 10
    ): LiftrixResult<List<PopularQuery>>

    /**
     * Cache search results for performance
     */
    suspend fun cacheSearchResults(
        query: String,
        viewerId: String,
        results: List<UserSearchResult>,
        expirationHours: Int = 1
    ): LiftrixResult<Unit>

    /**
     * Get cached search results if available and valid
     */
    suspend fun getCachedSearchResults(
        query: String,
        viewerId: String
    ): LiftrixResult<List<UserSearchResult>?>

    /**
     * Clear search cache for user
     */
    suspend fun clearSearchCache(viewerId: String): LiftrixResult<Unit>

    // ========================================
    // Privacy & Security
    // ========================================

    /**
     * Check if profile is visible to viewer based on privacy settings and relationships
     */
    suspend fun isProfileVisibleToViewer(
        profileUserId: String,
        viewerId: String
    ): LiftrixResult<Boolean>

    /**
     * Get privacy-filtered profile data based on relationship and settings
     */
    suspend fun getFilteredProfileData(
        profileUserId: String,
        viewerId: String
    ): LiftrixResult<FilteredProfileData>

    /**
     * Report inappropriate profile or content
     */
    suspend fun reportProfile(
        reporterId: String,
        profileUserId: String,
        reason: String,
        description: String? = null
    ): LiftrixResult<Unit>

    // ========================================
    // Search History & Preferences
    // ========================================

    /**
     * Get user's search history
     */
    suspend fun getSearchHistory(
        userId: String,
        limit: Int = 20
    ): LiftrixResult<List<SearchHistoryItem>>

    /**
     * Clear search history for user
     */
    suspend fun clearSearchHistory(userId: String): LiftrixResult<Unit>

    /**
     * Save search preferences for personalization
     */
    suspend fun saveSearchPreferences(
        userId: String,
        preferences: SearchPreferences
    ): LiftrixResult<Unit>

    /**
     * Get user's search preferences
     */
    suspend fun getSearchPreferences(
        userId: String
    ): LiftrixResult<SearchPreferences?>
}

/**
 * Search filter parameters
 */
data class SearchFilters(
    val fitnessGoals: List<FitnessGoal> = emptyList(),
    val availableEquipment: List<Equipment> = emptyList(),
    val fitnessLevel: FitnessLevel? = null,
    val minWorkouts: Int? = null,
    val maxWorkouts: Int? = null,
    val isOnlineOnly: Boolean = false,
    val hasProfilePhoto: Boolean = false,
    val joinedWithinDays: Int? = null,
    val excludeBlockedUsers: Boolean = true,
    val requireMutualConnections: Boolean = false
)

/**
 * Filtered profile data based on privacy settings
 */
data class FilteredProfileData(
    val basicInfo: BasicProfileInfo,
    val detailedInfo: DetailedProfileInfo? = null,
    val socialStats: SocialStats? = null,
    val fitnessData: FitnessData? = null,
    val canViewDetails: Boolean,
    val canSendMessage: Boolean,
    val canFollowDirectly: Boolean
)

data class BasicProfileInfo(
    val userId: String,
    val username: String,
    val displayName: String?,
    val profilePhotoUrl: String?,
    val isVerified: Boolean = false
)

data class DetailedProfileInfo(
    val bio: String? = null,
    val location: String? = null,
    val memberSince: Long,
    val lastActiveAt: Long? = null
)

data class SocialStats(
    val followerCount: Int,
    val followingCount: Int,
    val mutualConnectionsCount: Int
)

data class FitnessData(
    val workoutCount: Int,
    val fitnessGoals: List<FitnessGoal>,
    val availableEquipment: List<Equipment>,
    val fitnessLevel: FitnessLevel,
    val currentStreak: Int? = null
)

/**
 * Popular search query data
 */
data class PopularQuery(
    val query: String,
    val searchCount: Int,
    val averageResultCount: Double
)

/**
 * Search history item
 */
data class SearchHistoryItem(
    val query: String,
    val searchedAt: Long,
    val resultCount: Int,
    val selectedProfileId: String? = null
)

/**
 * User search preferences
 */
data class SearchPreferences(
    val preferredFitnessGoals: List<FitnessGoal> = emptyList(),
    val preferredEquipment: List<Equipment> = emptyList(),
    val preferredFitnessLevel: FitnessLevel? = null,
    val maxDistanceKm: Double? = null,
    val showNewUsersFirst: Boolean = false,
    val hideInactiveUsers: Boolean = true,
    val requireProfilePhoto: Boolean = false
)