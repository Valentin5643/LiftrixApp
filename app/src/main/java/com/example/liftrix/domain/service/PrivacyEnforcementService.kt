package com.example.liftrix.domain.service

import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.domain.model.social.WorkoutVisibility
import com.example.liftrix.domain.model.social.WorkoutPost
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for enforcing privacy rules across social features.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * This service provides centralized privacy enforcement to ensure consistent
 * application of privacy settings across all social operations.
 */
@Singleton
class PrivacyEnforcementService @Inject constructor(
    private val privacySettingsDao: SocialPrivacySettingsDao,
    private val followRelationshipDao: FollowRelationshipDao,
    private val blockedUserDao: BlockedUserDao
) {

    // ========================================
    // Profile Visibility Rules
    // ========================================

    /**
     * Checks if a profile can be viewed by the specified viewer.
     * 
     * @param profileUserId The ID of the profile owner
     * @param viewerId The ID of the viewer (null for anonymous)
     * @return true if profile can be viewed, false otherwise
     */
    suspend fun canViewProfile(profileUserId: String, viewerId: String?): Boolean {
        // Profile owners can always view their own profile
        if (viewerId == profileUserId) return true
        
        // Anonymous users cannot view profiles
        if (viewerId == null) return false
        
        // Check if viewer is blocked
        if (blockedUserDao.hasBlockRelationship(viewerId, profileUserId)) {
            return false
        }
        
        // Check privacy settings
        val privacySettings = privacySettingsDao.getPrivacySettings(profileUserId)
        
        // If no privacy settings exist, default to private (fail-safe)
        if (privacySettings == null) return false
        
        // If social features are disabled, profile is not visible
        if (!privacySettings.socialEnabled) return false
        
        return when (privacySettings.profileVisibility) {
            ProfileVisibility.PUBLIC.name -> true
            ProfileVisibility.FOLLOWERS.name -> isFollower(viewerId, profileUserId)
            ProfileVisibility.PRIVATE.name -> false
            else -> false // Unknown visibility level, default to private
        }
    }

    // ========================================
    // Workout Visibility Rules  
    // ========================================

    /**
     * Checks if a workout can be viewed by the specified viewer.
     * 
     * @param workoutOwnerId The ID of the workout owner
     * @param viewerId The ID of the viewer (null for anonymous)
     * @param workoutVisibility Override visibility for this specific workout (optional)
     * @return true if workout can be viewed, false otherwise
     */
    suspend fun canViewWorkout(
        workoutOwnerId: String, 
        viewerId: String?,
        workoutVisibility: WorkoutVisibility? = null
    ): Boolean {
        // Workout owners can always view their own workouts
        if (viewerId == workoutOwnerId) return true
        
        // Anonymous users cannot view workouts
        if (viewerId == null) return false
        
        // Check if viewer is blocked
        if (blockedUserDao.hasBlockRelationship(viewerId, workoutOwnerId)) {
            return false
        }
        
        // Get privacy settings
        val privacySettings = privacySettingsDao.getPrivacySettings(workoutOwnerId)
        
        // If no privacy settings exist, default to private
        if (privacySettings == null) return false
        
        // If social features or workout sharing is disabled, workout is not visible
        if (!privacySettings.socialEnabled || !privacySettings.workoutSharingEnabled) {
            return false
        }
        
        // Use specific workout visibility if provided, otherwise use default
        return if (workoutVisibility != null) {
            when (workoutVisibility) {
                WorkoutVisibility.PUBLIC -> true
                WorkoutVisibility.FOLLOWERS -> isFollower(viewerId, workoutOwnerId)
                WorkoutVisibility.PRIVATE -> false
            }
        } else {
            when (privacySettings.defaultWorkoutVisibility) {
                WorkoutVisibility.PUBLIC.name -> true
                WorkoutVisibility.FOLLOWERS.name -> isFollower(viewerId, workoutOwnerId)
                WorkoutVisibility.PRIVATE.name -> false
                else -> false // Unknown visibility level, default to private
            }
        }
    }

    // ========================================
    // Follow Relationship Checks
    // ========================================

    /**
     * Checks if viewerId is following profileUserId.
     */
    private suspend fun isFollower(viewerId: String, profileUserId: String): Boolean {
        return followRelationshipDao.isFollowing(viewerId, profileUserId)
    }

    // ========================================
    // Social Feature Access Rules
    // ========================================

    /**
     * Checks if a user can send a follow request to another user.
     * 
     * @param requesterId The ID of the user sending the follow request
     * @param targetUserId The ID of the user receiving the follow request
     * @return true if follow request can be sent, false otherwise
     */
    suspend fun canSendFollowRequest(requesterId: String, targetUserId: String): Boolean {
        // Users cannot follow themselves
        if (requesterId == targetUserId) return false
        
        // Check if requester is blocked
        if (blockedUserDao.hasBlockRelationship(requesterId, targetUserId)) {
            return false
        }
        
        // Check if already following or has pending request
        if (followRelationshipDao.isFollowing(requesterId, targetUserId) ||
            followRelationshipDao.hasPendingFollowRequest(requesterId, targetUserId)) {
            return false
        }
        
        // Check target's privacy settings
        val privacySettings = privacySettingsDao.getPrivacySettings(targetUserId)
        
        // If no privacy settings exist, don't allow follow requests
        if (privacySettings == null) return false
        
        // Check if social features and follow requests are enabled
        return privacySettings.socialEnabled && privacySettings.allowFollowRequests
    }

    /**
     * Checks if a user can add another user as a gym buddy.
     * 
     * @param requesterId The ID of the user sending the gym buddy request
     * @param targetUserId The ID of the user receiving the gym buddy request
     * @return true if gym buddy request can be sent, false otherwise
     */
    suspend fun canAddGymBuddy(requesterId: String, targetUserId: String): Boolean {
        // Users cannot add themselves as gym buddies
        if (requesterId == targetUserId) return false
        
        // Check if requester is blocked
        if (blockedUserDao.hasBlockRelationship(requesterId, targetUserId)) {
            return false
        }
        
        // Check both users' privacy settings for gym buddies
        val requesterSettings = privacySettingsDao.getPrivacySettings(requesterId)
        val targetSettings = privacySettingsDao.getPrivacySettings(targetUserId)
        
        // Both users must have gym buddies enabled
        return requesterSettings?.socialEnabled == true && 
               requesterSettings.gymBuddiesEnabled &&
               targetSettings?.socialEnabled == true && 
               targetSettings.gymBuddiesEnabled
    }

    // ========================================
    // Discovery and Search Rules
    // ========================================

    /**
     * Filters a list of user IDs based on discovery privacy settings.
     * Removes users who have hidden themselves from suggestions or are blocked.
     * 
     * @param viewerId The ID of the viewer
     * @param userIds List of user IDs to filter
     * @return Filtered list of user IDs that can be discovered by the viewer
     */
    suspend fun filterDiscoverableUsers(viewerId: String, userIds: List<String>): List<String> {
        if (userIds.isEmpty()) return emptyList()
        
        // Get blocked user IDs to filter out
        val blockedUserIds = blockedUserDao.getBlockedUserIds(viewerId).toSet()
        val usersWhoBlockedViewer = blockedUserDao.getUsersWhoBlockedMe(viewerId).toSet()
        
        return userIds.filter { userId ->
            // Exclude self, blocked users, and users who blocked the viewer
            userId != viewerId && 
            userId !in blockedUserIds && 
            userId !in usersWhoBlockedViewer &&
            !isHiddenFromDiscovery(userId)
        }
    }

    /**
     * Checks if a user has hidden themselves from discovery/suggestions.
     */
    private suspend fun isHiddenFromDiscovery(userId: String): Boolean {
        val privacySettings = privacySettingsDao.getPrivacySettings(userId)
        return privacySettings?.hideFromSuggestions == true
    }

    // ========================================
    // Post Visibility Rules
    // ========================================

    /**
     * Checks if a workout post can be viewed by the specified viewer.
     * 
     * @param viewerId The ID of the viewer (null for anonymous)
     * @param post The workout post to check visibility for
     * @return true if post can be viewed, false otherwise
     */
    suspend fun canViewPost(viewerId: String?, post: WorkoutPost): Boolean {
        // Post authors can always view their own posts
        if (viewerId == post.userId) return true
        
        // Anonymous users cannot view posts
        if (viewerId == null) return false
        
        // Check if viewer is blocked by post author
        if (blockedUserDao.hasBlockRelationship(viewerId, post.userId)) {
            return false
        }
        
        // Check post visibility and privacy settings
        return when (post.visibility) {
            com.example.liftrix.domain.model.social.PostVisibility.PUBLIC -> {
                // Public posts are visible to everyone (unless blocked)
                true
            }
            com.example.liftrix.domain.model.social.PostVisibility.FOLLOWERS -> {
                // Followers-only posts require follow relationship
                isFollower(viewerId, post.userId)
            }
            com.example.liftrix.domain.model.social.PostVisibility.PRIVATE -> {
                // Private posts are only visible to the author
                false
            }
        }
    }

    // ========================================
    // Content Filtering Rules
    // ========================================

    /**
     * Filters content visibility based on privacy settings.
     * Used to determine what profile information should be shown to viewers.
     * 
     * @param profileUserId The ID of the profile owner
     * @param viewerId The ID of the viewer
     * @return Privacy filter result with allowed content types
     */
    suspend fun getContentVisibilityFilter(
        profileUserId: String, 
        viewerId: String?
    ): ContentVisibilityFilter {
        // Profile owner sees everything
        if (viewerId == profileUserId) {
            return ContentVisibilityFilter(
                showWorkoutStats = true,
                showAchievements = true,
                showWorkoutStreak = true,
                showFullProfile = true
            )
        }
        
        // Check if can view profile at all
        if (!canViewProfile(profileUserId, viewerId)) {
            return ContentVisibilityFilter() // All false by default
        }
        
        val privacySettings = privacySettingsDao.getPrivacySettings(profileUserId)
            ?: return ContentVisibilityFilter() // Default to all hidden
        
        return ContentVisibilityFilter(
            showWorkoutStats = privacySettings.showWorkoutStats,
            showAchievements = privacySettings.showAchievements,
            showWorkoutStreak = privacySettings.showWorkoutStreak,
            showFullProfile = true // Profile is viewable if we got this far
        )
    }

    /**
     * Data class representing what content can be shown to a viewer
     */
    data class ContentVisibilityFilter(
        val showWorkoutStats: Boolean = false,
        val showAchievements: Boolean = false,
        val showWorkoutStreak: Boolean = false,
        val showFullProfile: Boolean = false
    )
}