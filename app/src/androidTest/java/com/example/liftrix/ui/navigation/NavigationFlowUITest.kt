package com.example.liftrix.ui.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for Navigation Flow and Screen Transitions
 * 
 * Tests critical navigation patterns:
 * - Bottom navigation between main screens
 * - Deep linking and route parameter handling
 * - Back navigation and state preservation
 * - Screen rotation state persistence
 * - Navigation accessibility for screen readers
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationFlowUITest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun bottomNavigation_displaysAllTabs() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Then: All bottom navigation tabs are visible
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coach").assertIsDisplayed()
        composeTestRule.onNodeWithText("Friends").assertIsDisplayed()
    }

    @Test
    fun bottomNavigation_switchesBetweenScreens() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Given: User is on Home screen (default)
        composeTestRule.onNodeWithText("Home")
            .assertIsSelected()

        // When: User taps Workout tab
        composeTestRule.onNodeWithText("Workout").performClick()

        // Then: Workout screen is displayed and tab is selected
        composeTestRule.onNodeWithText("Workout")
            .assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Workout screen content")
            .assertIsDisplayed()

        // When: User taps Progress tab
        composeTestRule.onNodeWithText("Progress").performClick()

        // Then: Progress screen is displayed
        composeTestRule.onNodeWithText("Progress")
            .assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Progress dashboard")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_preservesStateOnTabSwitch() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Given: User enters text in Home screen search
        composeTestRule.onNodeWithText("Search workouts")
            .performTextInput("Push workout")

        // When: User switches to Workout tab and back to Home
        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithText("Home").performClick()

        // Then: Search text is preserved
        composeTestRule.onNodeWithText("Search workouts")
            .assertTextContains("Push workout")
    }

    @Test
    fun deepLink_navigatesToSpecificWorkout() {
        val workoutId = "test-workout-123"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.WorkoutDetails(workoutId)
                )
            }
        }

        // Then: Workout details screen is displayed with correct workout
        composeTestRule.onNodeWithContentDescription("Workout details for $workoutId")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_handlesBackPressCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Given: User navigates to profile screen
        composeTestRule.onNodeWithContentDescription("Profile").performClick()
        composeTestRule.onNodeWithText("Profile").assertIsDisplayed()

        // When: User presses back (simulated)
        composeTestRule.onNodeWithContentDescription("Navigate back")
            .performClick()

        // Then: User returns to previous screen
        composeTestRule.onNodeWithText("Home")
            .assertIsSelected()
    }

    @Test
    fun navigation_supportsAccessibility() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Then: Navigation elements have proper accessibility labels
        composeTestRule.onNodeWithText("Home")
            .assert(hasContentDescription())
            .assertHasClickAction()

        composeTestRule.onNodeWithText("Workout")
            .assert(hasContentDescription())
            .assertHasClickAction()

        composeTestRule.onNodeWithText("Progress")
            .assert(hasContentDescription())
            .assertHasClickAction()

        // Then: Current tab state is announced for screen readers
        composeTestRule.onNodeWithText("Home")
            .assert(hasStateDescription("Selected"))
    }

    @Test
    fun navigation_handlesScreenRotation() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Given: User is on Workout tab with some state
        composeTestRule.onNodeWithText("Workout").performClick()
        
        // Simulate user entering workout name
        composeTestRule.onNodeWithText("Search exercises")
            .performTextInput("Bench press")

        // When: Screen rotation occurs (simulated configuration change)
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Then: Workout tab is still selected and state is preserved
        composeTestRule.onNodeWithText("Workout")
            .assertIsSelected()
        composeTestRule.onNodeWithText("Search exercises")
            .assertTextContains("Bench press")
    }

    @Test
    fun navigation_profileRouteWithUserId() {
        val userId = "user-123"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.Profile(userId)
                )
            }
        }

        // Then: Profile screen displays for specific user
        composeTestRule.onNodeWithContentDescription("Profile for user $userId")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_handlesInvalidRoutes() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.WorkoutDetails("invalid-workout-id")
                )
            }
        }

        // Then: Error state is handled gracefully
        composeTestRule.onNodeWithText("Workout not found")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to Home")
            .assertIsDisplayed()
            .performClick()

        // Then: Navigation returns to safe state
        composeTestRule.onNodeWithText("Home")
            .assertIsSelected()
    }

    // ==========================================
    // Social Screen Navigation Tests
    // ==========================================

    @Test
    fun navigation_socialFeedScreenIsAccessible() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.SocialFeed
                )
            }
        }

        // Then: Social feed screen is displayed
        composeTestRule.onNodeWithContentDescription("Social feed screen")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Feed")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_shareWorkoutScreenIsAccessible() {
        val workoutId = "workout-123"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.ShareWorkout(workoutId)
                )
            }
        }

        // Then: Share workout screen is displayed with workout ID
        composeTestRule.onNodeWithContentDescription("Share workout screen for $workoutId")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_publicProfileScreenIsAccessible() {
        val userId = "profile-user-123"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.PublicProfile(userId)
                )
            }
        }

        // Then: Public profile screen is displayed for user
        composeTestRule.onNodeWithContentDescription("Public profile for user $userId")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_gymBuddyScreenIsAccessible() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.GymBuddy
                )
            }
        }

        // Then: Gym buddy screen is displayed
        composeTestRule.onNodeWithContentDescription("Gym buddy screen")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Gym Buddies")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_progressComparisonScreenIsAccessible() {
        val exerciseIds = listOf("exercise1", "exercise2")
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.ProgressComparison(exerciseIds)
                )
            }
        }

        // Then: Progress comparison screen is displayed
        composeTestRule.onNodeWithContentDescription("Progress comparison for exercises")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_notificationSettingsScreenIsAccessible() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.NotificationSettings
                )
            }
        }

        // Then: Notification settings screen is displayed
        composeTestRule.onNodeWithContentDescription("Notification settings screen with comprehensive controls")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Notifications")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_socialScreensFromFriendsTab() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedNavigationContainer()
            }
        }

        // Given: User taps Friends tab
        composeTestRule.onNodeWithText("Friends").performClick()

        // Then: Social feed is displayed
        composeTestRule.onNodeWithContentDescription("Social feed screen")
            .assertIsDisplayed()

        // When: User navigates to gym buddy screen from social menu
        composeTestRule.onNodeWithContentDescription("Gym buddy navigation button")
            .performClick()

        // Then: Gym buddy screen is accessible from social context
        composeTestRule.onNodeWithContentDescription("Gym buddy screen")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_socialScreenBackNavigation() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.PublicProfile("user123")
                )
            }
        }

        // Given: User is on public profile screen
        composeTestRule.onNodeWithContentDescription("Public profile for user user123")
            .assertIsDisplayed()

        // When: User navigates back
        composeTestRule.onNodeWithContentDescription("Back to previous screen")
            .performClick()

        // Then: Navigation handles back correctly for social screens
        composeTestRule.onNodeWithText("Home")
            .assertIsSelected()
    }

    @Test
    fun navigation_socialScreenParameterPassing() {
        val workoutId = "social-workout-456"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.ShareWorkout(workoutId)
                )
            }
        }

        // Then: Parameters are correctly passed to social screens
        composeTestRule.onNodeWithContentDescription("Share workout screen for $workoutId")
            .assertIsDisplayed()
        
        // And: Screen shows workout-specific content
        composeTestRule.onNodeWithText("Share Workout")
            .assertIsDisplayed()
    }

    @Test
    fun navigation_socialScreenDeepLinkHandling() {
        val userId = "deep-link-user"
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedMainNavigationContainer(
                    startDestination = LiftrixRoute.PublicProfile(userId)
                )
            }
        }

        // Then: Deep links to social screens work correctly
        composeTestRule.onNodeWithContentDescription("Public profile for user $userId")
            .assertIsDisplayed()
        
        // And: User can navigate away and back
        composeTestRule.onNodeWithText("Friends").performClick()
        composeTestRule.onNodeWithContentDescription("Back to profile")
            .performClick()
            
        composeTestRule.onNodeWithContentDescription("Public profile for user $userId")
            .assertIsDisplayed()
    }
}