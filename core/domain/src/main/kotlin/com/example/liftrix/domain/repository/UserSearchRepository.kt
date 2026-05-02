package com.example.liftrix.domain.repository

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.PublicUserProfile

/**
 * Repository interface for advanced user search and discovery functionality
 * 
 * Provides privacy-aware user search with caching, filtering, and profile access.
 * Designed for social discovery features with performance optimization.
 */
interface UserSearchRepository {
    
    /**
     * Search for users with advanced filtering and privacy controls
     * 
     * @param query Search query (display name, username, interests)
     * @param currentUserId ID of user performing the search
     * @param filters Additional search filters for refinement
     * @return LiftrixResult containing list of search results or error
     */
    suspend fun searchUsers(
        query: String,
        currentUserId: String,
        filters: SearchFilters = SearchFilters()
    ): LiftrixResult<List<UserSearchResult>>
    
    /**
     * Get public profile information for a specific user
     * 
     * @param userId ID of user whose profile to retrieve
     * @param viewerId ID of user viewing the profile
     * @return LiftrixResult containing public profile or error
     */
    suspend fun getPublicProfile(
        userId: String,
        viewerId: String
    ): LiftrixResult<PublicUserProfile?>
    
    /**
     * Check if a profile exists (regardless of privacy settings)
     * 
     * @param userId ID of user whose profile existence to check
     * @return LiftrixResult containing true if profile exists, false otherwise
     */
    suspend fun profileExists(userId: String): LiftrixResult<Boolean>
    
    /**
     * Generate QR code data for profile sharing
     * 
     * @param userId ID of user whose profile to share
     * @return LiftrixResult containing QR code data string or error
     */
    suspend fun generateProfileQRCode(userId: String): LiftrixResult<String>
    
    /**
     * Resolve QR code data to user profile ID
     * 
     * @param qrData QR code data string to resolve
     * @return LiftrixResult containing user ID or error
     */
    suspend fun resolveQRCodeProfile(qrData: String): LiftrixResult<String>
    
    /**
     * Update user's search keywords for indexing
     * 
     * @param userId ID of user to update
     * @param keywords List of search keywords
     * @return LiftrixResult indicating success or error
     */
    suspend fun updateSearchKeywords(
        userId: String,
        keywords: List<String>
    ): LiftrixResult<Unit>
    
    /**
     * Track profile view for analytics and privacy
     * 
     * @param profileUserId ID of user whose profile was viewed
     * @param viewerId ID of user viewing the profile
     * @return LiftrixResult indicating success or error
     */
    suspend fun trackProfileView(
        profileUserId: String,
        viewerId: String
    ): LiftrixResult<Unit>
    
    /**
     * Clear search cache for a specific user
     * 
     * @param userId ID of user whose cache to clear
     * @return LiftrixResult indicating success or error
     */
    suspend fun clearSearchCache(userId: String): LiftrixResult<Unit>
    
    /**
     * Get cached search results if available and not expired
     * 
     * @param viewerId ID of user performing the search
     * @param query Search query to check cache for
     * @return LiftrixResult containing cached results or null if not available
     */
    suspend fun getCachedSearchResults(
        viewerId: String,
        query: String
    ): LiftrixResult<List<UserSearchResult>?>
}