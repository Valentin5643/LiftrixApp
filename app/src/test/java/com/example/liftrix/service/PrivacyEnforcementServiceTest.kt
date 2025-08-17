package com.example.liftrix.service

import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.data.local.entity.SocialPrivacySettingsEntity
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.service.PrivacyEnforcementService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

/**
 * Unit tests for PrivacyEnforcementService
 * Tests from SPEC-20250116-social-privacy-moderation integration task TEST-001
 */
class PrivacyEnforcementServiceTest {

    @MockK
    private lateinit var privacySettingsDao: SocialPrivacySettingsDao
    
    @MockK
    private lateinit var followRelationshipDao: FollowRelationshipDao
    
    @MockK
    private lateinit var blockedUserDao: BlockedUserDao
    
    private lateinit var privacyEnforcementService: PrivacyEnforcementService
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        privacyEnforcementService = PrivacyEnforcementService(
            privacySettingsDao = privacySettingsDao,
            followRelationshipDao = followRelationshipDao,
            blockedUserDao = blockedUserDao
        )
    }

    // ========================================
    // Profile Visibility Tests
    // ========================================

    @Test
    fun `canViewProfile returns true for profile owner viewing own profile`() = runTest {
        // Given
        val userId = "user123"
        
        // When
        val result = privacyEnforcementService.canViewProfile(userId, userId)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `canViewProfile returns false for anonymous viewer`() = runTest {
        // Given
        val profileUserId = "user123"
        val viewerId = null
        
        // When
        val result = privacyEnforcementService.canViewProfile(profileUserId, viewerId)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canViewProfile returns false when viewer is blocked`() = runTest {
        // Given
        val profileUserId = "user123"
        val viewerId = "user456"
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, profileUserId) } returns true
        
        // When
        val result = privacyEnforcementService.canViewProfile(profileUserId, viewerId)
        
        // Then
        assertFalse(result)
        coVerify { blockedUserDao.hasBlockRelationship(viewerId, profileUserId) }
    }

    @Test
    fun `canViewProfile returns true for public profile when not blocked`() = runTest {
        // Given
        val profileUserId = "user123"
        val viewerId = "user456"
        val privacySettings = createPrivacySettings(
            userId = profileUserId,
            profileVisibility = ProfileVisibility.PUBLIC.name,
            socialEnabled = true
        )
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, profileUserId) } returns false
        coEvery { privacySettingsDao.getPrivacySettings(profileUserId) } returns privacySettings
        
        // When
        val result = privacyEnforcementService.canViewProfile(profileUserId, viewerId)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `canViewProfile returns false for private profile when not following`() = runTest {
        // Given
        val profileUserId = "user123"
        val viewerId = "user456"
        val privacySettings = createPrivacySettings(
            userId = profileUserId,
            profileVisibility = ProfileVisibility.PRIVATE.name,
            socialEnabled = true
        )
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, profileUserId) } returns false
        coEvery { privacySettingsDao.getPrivacySettings(profileUserId) } returns privacySettings
        
        // When
        val result = privacyEnforcementService.canViewProfile(profileUserId, viewerId)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canViewProfile returns true for followers-only profile when following`() = runTest {
        // Given
        val profileUserId = "user123"
        val viewerId = "user456"
        val privacySettings = createPrivacySettings(
            userId = profileUserId,
            profileVisibility = ProfileVisibility.FOLLOWERS.name,
            socialEnabled = true
        )
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, profileUserId) } returns false
        coEvery { privacySettingsDao.getPrivacySettings(profileUserId) } returns privacySettings
        coEvery { followRelationshipDao.isFollowing(viewerId, profileUserId) } returns true
        
        // When
        val result = privacyEnforcementService.canViewProfile(profileUserId, viewerId)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `canViewProfile returns false when social features disabled`() = runTest {
        // Given
        val profileUserId = "user123"
        val viewerId = "user456"
        val privacySettings = createPrivacySettings(
            userId = profileUserId,
            profileVisibility = ProfileVisibility.PUBLIC.name,
            socialEnabled = false
        )
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, profileUserId) } returns false
        coEvery { privacySettingsDao.getPrivacySettings(profileUserId) } returns privacySettings
        
        // When
        val result = privacyEnforcementService.canViewProfile(profileUserId, viewerId)
        
        // Then
        assertFalse(result)
    }

    // ========================================
    // Post Visibility Tests
    // ========================================

    @Test
    fun `canViewPost returns true for post author viewing own post`() = runTest {
        // Given
        val userId = "user123"
        val post = createWorkoutPost(userId = userId, visibility = PostVisibility.PRIVATE)
        
        // When
        val result = privacyEnforcementService.canViewPost(userId, post)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `canViewPost returns false for anonymous viewer`() = runTest {
        // Given
        val post = createWorkoutPost(userId = "user123", visibility = PostVisibility.PUBLIC)
        val viewerId = null
        
        // When
        val result = privacyEnforcementService.canViewPost(viewerId, post)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canViewPost returns false when viewer is blocked`() = runTest {
        // Given
        val postUserId = "user123"
        val viewerId = "user456"
        val post = createWorkoutPost(userId = postUserId, visibility = PostVisibility.PUBLIC)
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, postUserId) } returns true
        
        // When
        val result = privacyEnforcementService.canViewPost(viewerId, post)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canViewPost returns true for public post when not blocked`() = runTest {
        // Given
        val postUserId = "user123"
        val viewerId = "user456"
        val post = createWorkoutPost(userId = postUserId, visibility = PostVisibility.PUBLIC)
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, postUserId) } returns false
        
        // When
        val result = privacyEnforcementService.canViewPost(viewerId, post)
        
        // Then
        assertTrue(result)
    }

    @Test
    fun `canViewPost returns false for private post when not author`() = runTest {
        // Given
        val postUserId = "user123"
        val viewerId = "user456"
        val post = createWorkoutPost(userId = postUserId, visibility = PostVisibility.PRIVATE)
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, postUserId) } returns false
        
        // When
        val result = privacyEnforcementService.canViewPost(viewerId, post)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canViewPost returns true for followers-only post when following`() = runTest {
        // Given
        val postUserId = "user123"
        val viewerId = "user456"
        val post = createWorkoutPost(userId = postUserId, visibility = PostVisibility.FOLLOWERS)
        
        coEvery { blockedUserDao.hasBlockRelationship(viewerId, postUserId) } returns false
        coEvery { followRelationshipDao.isFollowing(viewerId, postUserId) } returns true
        
        // When
        val result = privacyEnforcementService.canViewPost(viewerId, post)
        
        // Then
        assertTrue(result)
    }

    // ========================================
    // Discovery Filtering Tests
    // ========================================

    @Test
    fun `filterDiscoverableUsers excludes self and blocked users`() = runTest {
        // Given
        val viewerId = "user123"
        val userIds = listOf("user123", "user456", "user789", "user101")
        val blockedUserIds = listOf("user456")
        val usersWhoBlockedViewer = listOf("user789")
        
        coEvery { blockedUserDao.getBlockedUserIds(viewerId) } returns blockedUserIds
        coEvery { blockedUserDao.getUsersWhoBlockedMe(viewerId) } returns usersWhoBlockedViewer
        coEvery { privacySettingsDao.getPrivacySettings("user101") } returns createPrivacySettings(
            userId = "user101",
            hideFromSuggestions = false
        )
        
        // When
        val result = privacyEnforcementService.filterDiscoverableUsers(viewerId, userIds)
        
        // Then
        assertEquals(listOf("user101"), result)
    }

    @Test
    fun `filterDiscoverableUsers excludes users hidden from discovery`() = runTest {
        // Given
        val viewerId = "user123"
        val userIds = listOf("user456", "user789")
        
        coEvery { blockedUserDao.getBlockedUserIds(viewerId) } returns emptyList()
        coEvery { blockedUserDao.getUsersWhoBlockedMe(viewerId) } returns emptyList()
        coEvery { privacySettingsDao.getPrivacySettings("user456") } returns createPrivacySettings(
            userId = "user456",
            hideFromSuggestions = true
        )
        coEvery { privacySettingsDao.getPrivacySettings("user789") } returns createPrivacySettings(
            userId = "user789",
            hideFromSuggestions = false
        )
        
        // When
        val result = privacyEnforcementService.filterDiscoverableUsers(viewerId, userIds)
        
        // Then
        assertEquals(listOf("user789"), result)
    }

    // ========================================
    // Follow Request Tests
    // ========================================

    @Test
    fun `canSendFollowRequest returns false when targeting self`() = runTest {
        // Given
        val userId = "user123"
        
        // When
        val result = privacyEnforcementService.canSendFollowRequest(userId, userId)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canSendFollowRequest returns false when blocked`() = runTest {
        // Given
        val requesterId = "user123"
        val targetUserId = "user456"
        
        coEvery { blockedUserDao.hasBlockRelationship(requesterId, targetUserId) } returns true
        
        // When
        val result = privacyEnforcementService.canSendFollowRequest(requesterId, targetUserId)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canSendFollowRequest returns false when already following`() = runTest {
        // Given
        val requesterId = "user123"
        val targetUserId = "user456"
        
        coEvery { blockedUserDao.hasBlockRelationship(requesterId, targetUserId) } returns false
        coEvery { followRelationshipDao.isFollowing(requesterId, targetUserId) } returns true
        
        // When
        val result = privacyEnforcementService.canSendFollowRequest(requesterId, targetUserId)
        
        // Then
        assertFalse(result)
    }

    @Test
    fun `canSendFollowRequest returns true when conditions are met`() = runTest {
        // Given
        val requesterId = "user123"
        val targetUserId = "user456"
        val privacySettings = createPrivacySettings(
            userId = targetUserId,
            socialEnabled = true,
            allowFollowRequests = true
        )
        
        coEvery { blockedUserDao.hasBlockRelationship(requesterId, targetUserId) } returns false
        coEvery { followRelationshipDao.isFollowing(requesterId, targetUserId) } returns false
        coEvery { followRelationshipDao.hasPendingFollowRequest(requesterId, targetUserId) } returns false
        coEvery { privacySettingsDao.getPrivacySettings(targetUserId) } returns privacySettings
        
        // When
        val result = privacyEnforcementService.canSendFollowRequest(requesterId, targetUserId)
        
        // Then
        assertTrue(result)
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createPrivacySettings(
        userId: String,
        profileVisibility: String = ProfileVisibility.PUBLIC.name,
        socialEnabled: Boolean = true,
        allowFollowRequests: Boolean = true,
        hideFromSuggestions: Boolean = false
    ): SocialPrivacySettingsEntity {
        return SocialPrivacySettingsEntity(
            userId = userId,
            profileVisibility = profileVisibility,
            defaultWorkoutVisibility = "FOLLOWERS",
            socialEnabled = socialEnabled,
            workoutSharingEnabled = true,
            allowFollowRequests = allowFollowRequests,
            gymBuddiesEnabled = true,
            showWorkoutStats = true,
            showAchievements = true,
            showWorkoutStreak = true,
            hideFromSuggestions = hideFromSuggestions,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createWorkoutPost(
        userId: String,
        visibility: PostVisibility
    ): WorkoutPost {
        return WorkoutPost(
            id = UUID.randomUUID().toString(),
            userId = userId,
            workoutId = UUID.randomUUID().toString(),
            caption = "Test workout post",
            visibility = visibility,
            mediaItems = emptyList(),
            likeCount = 0,
            commentCount = 0,
            prsCount = 0,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            relevanceScore = 0.0
        )
    }
}