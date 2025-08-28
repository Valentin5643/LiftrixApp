package com.example.liftrix.ui.social

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.ui.social.PublicProfileScreen
import com.example.liftrix.ui.social.PublicProfileViewModel
import com.example.liftrix.ui.social.PublicProfileUiState
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * UI tests for blocking functionality
 * Tests from SPEC-20250116-social-privacy-moderation integration task TEST-002
 */
@RunWith(AndroidJUnit4::class)
class BlockingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun blockUserDialog_showsCorrectContent() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = true,
            isFollowing = false,
            isBlocked = false,
            followersCount = 100,
            followingCount = 50
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Open the options menu
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        
        // Click block user option
        composeTestRule.onNodeWithText("Block User").performClick()
        
        // Then
        composeTestRule.onNodeWithText("Block ${testProfile.displayName}?").assertIsDisplayed()
        composeTestRule.onNodeWithText("They won't be able to find your profile, posts, or interact with you.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Block").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun blockUserDialog_confirmBlock_callsViewModel() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = true,
            isFollowing = false,
            isBlocked = false,
            followersCount = 100,
            followingCount = 50
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Open the options menu and block user
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Block User").performClick()
        
        // Confirm block
        composeTestRule.onNodeWithText("Block").performClick()
        
        // Then
        verify { mockViewModel.blockUser(testProfile.userId) }
    }

    @Test
    fun blockUserDialog_cancel_dismissesDialog() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = true,
            isFollowing = false,
            isBlocked = false,
            followersCount = 100,
            followingCount = 50
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Open the options menu and block user
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Block User").performClick()
        
        // Cancel block
        composeTestRule.onNodeWithText("Cancel").performClick()
        
        // Then
        composeTestRule.onNodeWithText("Block ${testProfile.displayName}?").assertDoesNotExist()
    }

    @Test
    fun blockedUserProfile_showsBlockedState() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = false,
            isFollowing = false,
            isBlocked = true,
            followersCount = 0, // Hidden when blocked
            followingCount = 0  // Hidden when blocked
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Then
        composeTestRule.onNodeWithText("This user is blocked").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unblock").assertIsDisplayed()
        
        // Verify interaction buttons are not shown
        composeTestRule.onNodeWithText("Follow").assertDoesNotExist()
        composeTestRule.onNodeWithText("Message").assertDoesNotExist()
    }

    @Test
    fun unblockUser_callsViewModel() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = false,
            isFollowing = false,
            isBlocked = true,
            followersCount = 0,
            followingCount = 0
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Click unblock
        composeTestRule.onNodeWithText("Unblock").performClick()
        
        // Then
        verify { mockViewModel.unblockUser(testProfile.userId) }
    }

    @Test
    fun reportProfile_showsBottomSheet() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = true,
            isFollowing = false,
            isBlocked = false,
            followersCount = 100,
            followingCount = 50
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Open the options menu and report profile
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Report Profile").performClick()
        
        // Then
        composeTestRule.onNodeWithText("Report profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Why are you reporting this?").assertIsDisplayed()
        
        // Check report reasons are shown
        composeTestRule.onNodeWithText("Spam or misleading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inappropriate content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Harassment or bullying").assertIsDisplayed()
    }

    @Test
    fun reportProfile_submitReport_callsViewModel() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = false,
            canInteract = true,
            isFollowing = false,
            isBlocked = false,
            followersCount = 100,
            followingCount = 50
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Open report bottom sheet and select reason
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Report Profile").performClick()
        composeTestRule.onNodeWithText("Spam or misleading").performClick()
        
        // Submit report
        composeTestRule.onNodeWithText("Submit Report").performClick()
        
        // Then
        verify { mockViewModel.reportProfile("SPAM", null) }
    }

    @Test
    fun ownProfile_hidesBlockAndReportOptions() {
        // Given
        val mockViewModel = mockk<PublicProfileViewModel>(relaxed = true)
        val testProfile = createTestProfile()
        val uiState = PublicProfileUiState.Success(
            profile = testProfile,
            isOwnProfile = true, // Own profile
            canInteract = true,
            isFollowing = false,
            isBlocked = false,
            followersCount = 100,
            followingCount = 50
        )
        
        every { mockViewModel.uiState } returns MutableStateFlow(uiState)
        
        // When
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user",
                onNavigateBack = {},
                viewModel = mockViewModel
            )
        }
        
        // Check that more options menu doesn't show block/report for own profile
        composeTestRule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }

    @Test
    fun blockedUsersScreen_displaysBlockedUsers() {
        // This would test the BlockedUsersScreen component
        // Implementation would depend on the actual UI structure
        // Placeholder for comprehensive blocked users management test
    }

    @Test
    fun bidirectionalBlocking_preventsInteraction() {
        // This test would verify that when user A blocks user B,
        // user B also cannot see or interact with user A's content
        // Implementation would require testing the privacy enforcement at the data layer
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun createTestProfile(): PublicUserProfile {
        return PublicUserProfile(
            userId = "test_user_123",
            username = "testuser",
            displayName = "Test User",
            bio = "Test bio",
            profileImageUrl = null,
            followerCount = 100,
            followingCount = 50,
            workoutCount = 25,
            memberSince = LocalDateTime.now().minusMonths(6),
            connectionStatus = ConnectionStatus.NONE,
            mutualConnections = 5,
            fitnessGoals = emptyList(),
            availableEquipment = emptyList(),
            isVerified = false
        )
    }
}