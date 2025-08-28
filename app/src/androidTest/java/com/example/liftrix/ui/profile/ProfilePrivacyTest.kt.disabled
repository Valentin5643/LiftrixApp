package com.example.liftrix.ui.profile

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.usecase.social.GetPublicProfileUseCase
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.GetPublicProfileResult
import com.example.liftrix.domain.usecase.social.FollowUserUseCase
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ProfilePrivacyTest - Comprehensive integration tests for profile privacy combinations
 * Part of user profiles and follow system from SPEC-20250113-user-profiles-follow.
 * 
 * Tests all viewer contexts and privacy combinations:
 * - Public profiles viewed by anonymous users
 * - Public profiles viewed by authenticated users
 * - Private profiles viewed by non-followers
 * - Private profiles viewed by pending request senders
 * - Private profiles viewed by followers
 * - Own profile viewing
 * - Blocked user scenarios
 * 
 * Verifies:
 * - Correct visibility of profile information based on privacy settings
 * - Proper follow button states and actions
 * - Appropriate error messages for restricted access
 * - Profile data filtering based on relationship status
 * - Navigation behavior for different privacy contexts
 */
@HiltAndroidTest
@UninstallModules(
    // Remove production modules that we want to mock
)
@RunWith(AndroidJUnit4::class)
class ProfilePrivacyTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    // Mock dependencies
    private val mockGetPublicProfileUseCase = mockk<GetPublicProfileUseCase>()
    private val mockFollowUserUseCase = mockk<FollowUserUseCase>()
    private val mockGetCurrentUserIdUseCase = mockk<GetCurrentUserIdUseCase>()

    private lateinit var context: Context

    // Test data
    private val currentUserId = "current_user_123"
    private val otherUserId = "other_user_456"
    private val privateUserId = "private_user_789"

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Setup default mock responses
        coEvery { mockGetCurrentUserIdUseCase() } returns currentUserId
    }

    @Test
    fun testPublicProfile_ViewedByAnonymousUser_ShowsLimitedInfo() = runTest {
        // Arrange
        val publicProfile = createPublicProfile(
            userId = otherUserId,
            isPublic = true,
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(otherUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = publicProfile,
                isOwnProfile = false,
                canInteract = true
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = otherUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText(publicProfile.displayName!!).assertIsDisplayed()
        composeTestRule.onNodeWithText("Follow").assertIsDisplayed()
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
        composeTestRule.onNodeWithText("Achievements").assertIsDisplayed()
        
        // Verify workout stats are visible for public profile
        composeTestRule.onNodeWithText("Workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText(publicProfile.totalWorkouts.toString()).assertIsDisplayed()
    }

    @Test
    fun testPrivateProfile_ViewedByNonFollower_ShowsPrivacyMessage() = runTest {
        // Arrange
        val privateProfile = createPrivateProfile(
            userId = privateUserId,
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(privateUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = privateProfile,
                isOwnProfile = false,
                canInteract = false
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = privateUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Private Profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Follow").assertIsDisplayed()
        
        // Verify private content is hidden
        composeTestRule.onNodeWithText("About").assertDoesNotExist()
        composeTestRule.onNodeWithText("Achievements").assertDoesNotExist()
        composeTestRule.onNodeWithText("Workouts").assertDoesNotExist()
        
        // Verify privacy message is shown
        composeTestRule.onNodeWithText(
            text = "Follow ${privateProfile.displayName} to see their workouts and achievements.",
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    fun testPrivateProfile_ViewedByFollower_ShowsFullContent() = runTest {
        // Arrange
        val privateProfile = createPrivateProfile(
            userId = privateUserId,
            connectionStatus = ConnectionStatus.CONNECTED
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(privateUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = privateProfile,
                isOwnProfile = false,
                canInteract = true
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = privateUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText(privateProfile.displayName!!).assertIsDisplayed()
        composeTestRule.onNodeWithText("Following").assertIsDisplayed()
        
        // Verify full content is visible to followers
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
        composeTestRule.onNodeWithText("Achievements").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workouts").assertIsDisplayed()
        
        // Verify detailed stats are accessible
        composeTestRule.onNodeWithText(privateProfile.totalWorkouts.toString()).assertIsDisplayed()
        composeTestRule.onNodeWithText("${privateProfile.currentStreak} days").assertIsDisplayed()
    }

    @Test
    fun testPendingFollowRequest_ShowsRequestedState() = runTest {
        // Arrange
        val profileWithPendingRequest = createPrivateProfile(
            userId = privateUserId,
            connectionStatus = ConnectionStatus.PENDING_SENT
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(privateUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = profileWithPendingRequest,
                isOwnProfile = false,
                canInteract = false
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = privateUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Requested").assertIsDisplayed()
        
        // Verify pending request message
        composeTestRule.onNodeWithText(
            "Your follow request is pending approval.",
            substring = true
        ).assertIsDisplayed()
        
        // Verify content is still hidden
        composeTestRule.onNodeWithText("About").assertDoesNotExist()
        composeTestRule.onNodeWithText("Achievements").assertDoesNotExist()
    }

    @Test
    fun testIncomingFollowRequest_ShowsAcceptButton() = runTest {
        // Arrange
        val profileWithIncomingRequest = createPublicProfile(
            userId = otherUserId,
            connectionStatus = ConnectionStatus.PENDING_RECEIVED
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(otherUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = profileWithIncomingRequest,
                isOwnProfile = false,
                canInteract = true
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = otherUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Accept").assertIsDisplayed()
    }

    @Test
    fun testBlockedUser_ShowsBlockedState() = runTest {
        // Arrange
        val blockedProfile = createPublicProfile(
            userId = otherUserId,
            connectionStatus = ConnectionStatus.BLOCKED
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(otherUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = blockedProfile,
                isOwnProfile = false,
                canInteract = false
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = otherUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Blocked").assertIsDisplayed()
        
        // Verify blocked button is disabled
        composeTestRule.onNodeWithText("Blocked").assertIsNotEnabled()
        
        // Verify content access is restricted
        composeTestRule.onNodeWithText("About").assertDoesNotExist()
        composeTestRule.onNodeWithText("Achievements").assertDoesNotExist()
    }

    @Test
    fun testOwnProfile_ShowsEditButton() = runTest {
        // Arrange
        val ownProfile = createPublicProfile(
            userId = currentUserId,
            connectionStatus = ConnectionStatus.NONE // Not applicable for own profile
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(currentUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = ownProfile,
                isOwnProfile = true,
                canInteract = true
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = currentUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        // Should not show follow button for own profile
        composeTestRule.onNodeWithText("Follow").assertDoesNotExist()
        composeTestRule.onNodeWithText("Following").assertDoesNotExist()
        
        // Should show all content for own profile
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
        composeTestRule.onNodeWithText("Achievements").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workouts").assertIsDisplayed()
    }

    @Test
    fun testMutualConnections_DisplayedCorrectly() = runTest {
        // Arrange
        val profileWithMutualConnections = createPublicProfile(
            userId = otherUserId,
            connectionStatus = ConnectionStatus.NONE,
            mutualConnections = 5
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(otherUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = profileWithMutualConnections,
                isOwnProfile = false,
                canInteract = true
            )
        )

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = otherUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("5 mutual connections").assertIsDisplayed()
    }

    @Test
    fun testFollowAction_UpdatesButtonState() = runTest {
        // Arrange
        val publicProfile = createPublicProfile(
            userId = otherUserId,
            connectionStatus = ConnectionStatus.NONE
        )
        
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest(otherUserId, true))
        } returns LiftrixResult.success(
            GetPublicProfileResult(
                profile = publicProfile,
                isOwnProfile = false,
                canInteract = true
            )
        )

        coEvery {
            mockFollowUserUseCase(
                targetUserId = otherUserId,
                action = any(),
                context = any()
            )
        } returns LiftrixResult.success(FollowStatus.FOLLOWING)

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = otherUserId,
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert initial state
        composeTestRule.onNodeWithText("Follow").assertIsDisplayed()

        // Act - click follow button
        composeTestRule.onNodeWithText("Follow").performClick()

        // Assert updated state
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Following").assertIsDisplayed()
    }

    @Test
    fun testProfileNotFound_ShowsErrorState() = runTest {
        // Arrange
        coEvery { 
            mockGetPublicProfileUseCase(GetPublicProfileRequest("nonexistent_user", true))
        } returns LiftrixResult.failure(Exception("User profile not found"))

        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                UserProfileScreen(
                    userId = "nonexistent_user",
                    onNavigateBack = {},
                    onNavigateToFollowersList = {},
                    onNavigateToFollowingList = {},
                    onNavigateToWorkoutDetail = {}
                )
            }
        }

        // Assert
        composeTestRule.onNodeWithText("Error Loading Profile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    // MARK: - Helper Methods

    private fun createPublicProfile(
        userId: String,
        isPublic: Boolean = true,
        connectionStatus: ConnectionStatus = ConnectionStatus.NONE,
        mutualConnections: Int = 0
    ): PublicUserProfile {
        return PublicUserProfile(
            userId = userId,
            displayName = "Test User $userId",
            bio = "This is a test bio for $userId",
            profileImageUrl = null,
            isPublic = isPublic,
            totalWorkouts = 42,
            currentStreak = 7,
            longestStreak = 21,
            followersCount = 10,
            followingCount = 15,
            achievements = listOf(
                createTestAchievement("First Workout", "Completed your first workout")
            ),
            recentWorkouts = listOf(
                createTestRecentWorkout("Push Day", "2024-01-15")
            ),
            connectionStatus = connectionStatus,
            mutualConnections = mutualConnections,
            memberSince = LocalDateTime.now().minusMonths(6),
            age = 25,
            location = "Test City"
        )
    }

    private fun createPrivateProfile(
        userId: String,
        connectionStatus: ConnectionStatus = ConnectionStatus.NONE,
        mutualConnections: Int = 0
    ): PublicUserProfile {
        return createPublicProfile(
            userId = userId,
            isPublic = false,
            connectionStatus = connectionStatus,
            mutualConnections = mutualConnections
        )
    }

    private fun createTestAchievement(
        title: String,
        description: String
    ): com.example.liftrix.domain.model.UserAchievement {
        return com.example.liftrix.domain.model.UserAchievement(
            id = "achievement_${title.lowercase().replace(" ", "_")}",
            title = title,
            description = description,
            iconUrl = null,
            unlockedAt = LocalDateTime.now(),
            category = com.example.liftrix.domain.model.AchievementCategory.MILESTONE,
            rarity = com.example.liftrix.domain.model.AchievementRarity.COMMON
        )
    }

    private fun createTestRecentWorkout(
        name: String,
        date: String
    ): com.example.liftrix.domain.model.social.RecentWorkout {
        return com.example.liftrix.domain.model.social.RecentWorkout(
            id = "workout_${name.lowercase().replace(" ", "_")}",
            name = name,
            date = date,
            exerciseCount = 5,
            duration = "45 min"
        )
    }
}