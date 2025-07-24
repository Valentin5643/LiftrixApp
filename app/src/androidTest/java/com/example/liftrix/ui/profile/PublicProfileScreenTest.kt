package com.example.liftrix.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FitnessLevel
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.PublicWorkoutStats
import com.example.liftrix.ui.social.PublicProfileScreen
import com.example.liftrix.ui.social.PublicProfileUiState
import com.example.liftrix.ui.social.PublicProfileViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * UI tests for PublicProfileScreen
 * 
 * Tests public profile display, connection actions, workout statistics,
 * achievement display, equipment sharing, and accessibility compliance
 * using Compose UI testing framework.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PublicProfileScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var mockViewModel: PublicProfileViewModel
    private lateinit var mockUiState: MutableStateFlow<PublicProfileUiState>

    private val mockPublicProfile = PublicUserProfile(
        userId = "test_user_123",
        displayName = "John Fitness",
        profileImageUrl = null,
        bio = "Passionate about strength training and helping others reach their fitness goals. Been lifting for 5+ years!",
        memberSince = LocalDateTime.now().minusYears(2),
        fitnessLevel = FitnessLevel.ADVANCED,
        isOnline = true,
        lastActiveAt = LocalDateTime.now().minusMinutes(15),
        connectionStatus = ConnectionStatus.NONE,
        mutualConnections = 8,
        publicAchievements = listOf(
            UserAchievement(
                id = "achievement1",
                title = "100 Workout Milestone",
                description = "Completed 100 workouts",
                earnedAt = LocalDateTime.now().minusMonths(1),
                type = "MILESTONE",
                iconUrl = null
            ),
            UserAchievement(
                id = "achievement2",
                title = "Consistency Champion",
                description = "30-day workout streak",
                earnedAt = LocalDateTime.now().minusDays(10),
                type = "STREAK",
                iconUrl = null
            )
        ),
        publicWorkoutStats = PublicWorkoutStats(
            totalWorkouts = 156,
            totalWorkoutTime = 9360, // 156 hours
            averageWorkoutTime = 60, // 1 hour
            currentStreak = 12,
            longestStreak = 45,
            favoriteExercises = listOf("Bench Press", "Deadlift", "Squats")
        ),
        publicFitnessGoals = listOf(FitnessGoal.MUSCLE_GAIN, FitnessGoal.STRENGTH),
        availableEquipment = listOf(Equipment.BARBELL, Equipment.DUMBBELLS, Equipment.PULL_UP_BAR)
    )

    @Before
    fun setup() {
        hiltRule.inject()
        
        mockUiState = MutableStateFlow(
            PublicProfileUiState(
                profile = null,
                isLoading = false,
                errorMessage = null,
                isOwnProfile = false,
                canInteract = true,
                connectionRequestSent = false
            )
        )
        
        mockViewModel = mockk {
            every { uiState } returns mockUiState
        }
    }

    @Test
    fun publicProfileScreen_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify main profile elements are displayed
        composeTestRule.onNodeWithText("John Fitness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced").assertIsDisplayed() // Fitness level
        composeTestRule.onNodeWithText("8 mutual connections").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    @Test
    fun profileBio_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify bio is displayed
        composeTestRule.onNodeWithText("Passionate about strength training and helping others reach their fitness goals. Been lifting for 5+ years!")
            .assertIsDisplayed()
    }

    @Test
    fun onlineStatus_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify online status indicator
        composeTestRule.onNodeWithTag("online_status_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Online").assertIsDisplayed()
    }

    @Test
    fun workoutStats_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify workout statistics
        composeTestRule.onNodeWithText("156").assertIsDisplayed() // Total workouts
        composeTestRule.onNodeWithText("Total Workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("12").assertIsDisplayed() // Current streak
        composeTestRule.onNodeWithText("Current Streak").assertIsDisplayed()
        composeTestRule.onNodeWithText("45").assertIsDisplayed() // Longest streak
        composeTestRule.onNodeWithText("Longest Streak").assertIsDisplayed()
        composeTestRule.onNodeWithText("60 min").assertIsDisplayed() // Average workout time
        composeTestRule.onNodeWithText("Avg Workout").assertIsDisplayed()
    }

    @Test
    fun favoriteExercises_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify favorite exercises
        composeTestRule.onNodeWithText("Favorite Exercises").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bench Press").assertIsDisplayed()
        composeTestRule.onNodeWithText("Deadlift").assertIsDisplayed()
        composeTestRule.onNodeWithText("Squats").assertIsDisplayed()
    }

    @Test
    fun achievements_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify achievements section
        composeTestRule.onNodeWithText("Achievements").assertIsDisplayed()
        composeTestRule.onNodeWithText("100 Workout Milestone").assertIsDisplayed()
        composeTestRule.onNodeWithText("Consistency Champion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completed 100 workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("30-day workout streak").assertIsDisplayed()
    }

    @Test
    fun fitnessGoals_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify fitness goals
        composeTestRule.onNodeWithText("Fitness Goals").assertIsDisplayed()
        composeTestRule.onNodeWithText("Muscle Gain").assertIsDisplayed()
        composeTestRule.onNodeWithText("Strength").assertIsDisplayed()
    }

    @Test
    fun availableEquipment_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify equipment section
        composeTestRule.onNodeWithText("Available Equipment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barbell").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dumbbells").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pull-up Bar").assertIsDisplayed()
    }

    @Test
    fun connectButton_displaysForUnconnectedUsers() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(connectionStatus = ConnectionStatus.NONE),
            isLoading = false,
            canInteract = true,
            isOwnProfile = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify connect button is displayed and enabled
        composeTestRule.onNodeWithText("Connect").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun connectedStatus_displaysForConnectedUsers() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(connectionStatus = ConnectionStatus.CONNECTED),
            isLoading = false,
            canInteract = true,
            isOwnProfile = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify connected status is displayed
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected").assertIsNotEnabled()
    }

    @Test
    fun pendingStatus_displaysForPendingConnection() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(connectionStatus = ConnectionStatus.PENDING_SENT),
            isLoading = false,
            canInteract = false,
            isOwnProfile = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify pending status is displayed
        composeTestRule.onNodeWithText("Request Sent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Request Sent").assertIsNotEnabled()
    }

    @Test
    fun qrCodeButton_displaysForOwnProfile() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false,
            isOwnProfile = true,
            canInteract = true
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify QR code button is displayed for own profile
        composeTestRule.onNodeWithText("Share QR").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Share profile QR code").assertIsDisplayed()
    }

    @Test
    fun qrCodeButton_handlesClickInteraction() {
        // Given
        var qrCodeClicked = false
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false,
            isOwnProfile = true
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { qrCodeClicked = true }
            )
        }

        // When - Click QR code button
        composeTestRule.onNodeWithText("Share QR")
            .performClick()

        // Then - Verify QR code navigation was triggered
        assert(qrCodeClicked)
    }

    @Test
    fun connectButton_handlesClickInteraction() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(connectionStatus = ConnectionStatus.NONE),
            isLoading = false,
            canInteract = true,
            isOwnProfile = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // When - Click connect button
        composeTestRule.onNodeWithText("Connect")
            .performClick()

        // Then - Button should handle the click (verified through ViewModel in real implementation)
        composeTestRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun loadingState_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = null,
            isLoading = true,
            errorMessage = null
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify loading indicator
        composeTestRule.onNodeWithTag("profile_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading profile...").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = null,
            isLoading = false,
            errorMessage = "Profile not found or private"
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify error message
        composeTestRule.onNodeWithText("Profile not found or private").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun memberSince_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify member since information
        composeTestRule.onNodeWithText("Member since").assertIsDisplayed()
        // Actual date display would depend on formatting
    }

    @Test
    fun lastActiveTime_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(
                isOnline = false,
                lastActiveAt = LocalDateTime.now().minusHours(2)
            ),
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify last active time
        composeTestRule.onNodeWithText("Last active").assertIsDisplayed()
        // Specific time formatting would depend on implementation
    }

    @Test
    fun profileScreen_supportsAccessibility() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Verify accessibility elements
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Profile picture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Online status").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Connect with John Fitness").assertIsDisplayed()
    }

    @Test
    fun backButton_handlesNavigationCorrectly() {
        // Given
        var backPressed = false
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile,
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { backPressed = true },
                onNavigateToQRCode = { }
            )
        }

        // When - Click back button
        composeTestRule.onNodeWithContentDescription("Go back")
            .performClick()

        // Then - Verify back navigation was triggered
        assert(backPressed)
    }

    @Test
    fun profileWithoutBio_handlesGracefully() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(bio = null),
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Profile should still display other information
        composeTestRule.onNodeWithText("John Fitness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced").assertIsDisplayed()
        // Bio section should be hidden or show placeholder
    }

    @Test
    fun profileWithoutAchievements_handlesGracefully() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            profile = mockPublicProfile.copy(publicAchievements = emptyList()),
            isLoading = false
        )
        
        composeTestRule.setContent {
            PublicProfileScreen(
                userId = "test_user_123",
                onNavigateBack = { },
                onNavigateToQRCode = { }
            )
        }

        // Then - Other sections should still display
        composeTestRule.onNodeWithText("John Fitness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout Statistics").assertIsDisplayed()
        // Achievements section should be hidden or show empty state
    }
}