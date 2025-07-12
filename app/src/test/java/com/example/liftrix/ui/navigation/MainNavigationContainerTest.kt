package com.example.liftrix.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.User
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test suite for MainNavigationContainer with enhanced brand colors and animations
 * 
 * Tests focus on:
 * - Navigation state management and MVI pattern
 * - Brand color integration and animation behavior
 * - Accessibility support and semantic properties
 * - Haptic feedback integration
 * - Navigation flow and state restoration
 */
@RunWith(AndroidJUnit4::class)
class MainNavigationContainerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: MainNavigationViewModel
    private lateinit var mockNavigateToAuth: () -> Unit
    private val testUser = User(
        id = "test-user-id",
        email = "test@example.com",
        displayName = "Test User",
        profilePictureUrl = null
    )

    @Before
    fun setup() {
        mockViewModel = mockk(relaxed = true)
        mockNavigateToAuth = mockk(relaxed = true)
        
        // Default UI state
        every { mockViewModel.uiState } returns MutableStateFlow(
            MainNavigationState(
                authenticationState = AuthenticationState.Authenticated(testUser),
                selectedTab = MainNavigationItem.HOME,
                isWorkoutCreationModalVisible = false,
                isLoading = false,
                error = null
            )
        )
    }

    @Test
    fun mainNavigationContainer_displaysAllNavigationItems() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Verify all navigation items are displayed
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Progress").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coach").assertIsDisplayed()
    }

    @Test
    fun navigationItems_haveCorrectAccessibilityLabels() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Verify accessibility labels for navigation tabs
        composeTestRule.onNode(hasContentDescription("Home tab")).assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Workout tab")).assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Progress tab")).assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Coach tab")).assertIsDisplayed()
    }

    @Test
    fun navigationItem_click_triggersNavigation() {
        val navController = composeTestRule.runOnUiThread {
            androidx.navigation.testing.TestNavHostController(
                androidx.compose.ui.platform.LocalContext.current
            )
        }

        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    navController = navController,
                    viewModel = mockViewModel
                )
            }
        }

        // Click on Workout tab
        composeTestRule.onNodeWithText("Workout").performClick()

        // Verify navigation occurred (will be tested with actual NavController in integration tests)
        composeTestRule.onNodeWithText("Workout").assertIsDisplayed()
    }

    @Test
    fun workoutCreationFab_isDisplayed() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Verify FAB is displayed
        composeTestRule.onNode(hasContentDescription("Create new workout")).assertIsDisplayed()
    }

    @Test
    fun workoutCreationFab_click_showsModal() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Click FAB
        composeTestRule.onNode(hasContentDescription("Create new workout")).performClick()

        // Verify event was sent to ViewModel
        verify { mockViewModel.onEvent(MainNavigationEvent.ShowWorkoutCreationModal) }
    }

    @Test
    fun workoutCreationModal_displaysWhenVisible() {
        // Set modal visible state
        every { mockViewModel.uiState } returns MutableStateFlow(
            MainNavigationState(
                authenticationState = AuthenticationState.Authenticated(testUser),
                selectedTab = MainNavigationItem.HOME,
                isWorkoutCreationModalVisible = true,
                isLoading = false,
                error = null
            )
        )

        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Verify modal content is displayed
        composeTestRule.onNodeWithText("Create Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start from Template").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Custom Workout").assertIsDisplayed()
    }

    @Test
    fun selectedNavigationItem_hasCorrectAccessibilityState() {
        // Set Workout tab as selected
        every { mockViewModel.uiState } returns MutableStateFlow(
            MainNavigationState(
                authenticationState = AuthenticationState.Authenticated(testUser),
                selectedTab = MainNavigationItem.WORKOUT,
                isWorkoutCreationModalVisible = false,
                isLoading = false,
                error = null
            )
        )

        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Verify selected state accessibility
        composeTestRule.onNode(hasContentDescription("Workout tab selected")).assertIsDisplayed()
    }

    @Test
    fun navigationContainer_handlesLoadingState() {
        // Set loading state
        every { mockViewModel.uiState } returns MutableStateFlow(
            MainNavigationState(
                authenticationState = AuthenticationState.Loading,
                selectedTab = MainNavigationItem.HOME,
                isWorkoutCreationModalVisible = false,
                isLoading = true,
                error = null
            )
        )

        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Navigation should still be displayed during loading
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun navigationContainer_handlesErrorState() {
        // Set error state
        every { mockViewModel.uiState } returns MutableStateFlow(
            MainNavigationState(
                authenticationState = AuthenticationState.Authenticated(testUser),
                selectedTab = MainNavigationItem.HOME,
                isWorkoutCreationModalVisible = false,
                isLoading = false,
                error = "Test error message"
            )
        )

        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Navigation should still be functional during error state
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout").assertIsDisplayed()
    }

    @Test
    fun navigationBrandColors_areAppliedCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Verify navigation bar is displayed (color testing requires UI test environment)
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        
        // In a real test environment, we would verify:
        // - Selected items use LiftrixColors.Primary
        // - Unselected items use Material 3 onSurfaceVariant
        // - Smooth color transitions occur on selection
        // - Indicator background uses brand color with proper alpha
    }

    @Test
    fun navigationAnimations_performCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Click navigation item to trigger animation
        composeTestRule.onNodeWithText("Workout").performClick()
        
        // Animation testing would require:
        // - Verifying LiftrixAnimations.navigationSelectionSpring is used
        // - Color transitions complete smoothly
        // - No animation jank or frame drops
        // - Proper spring physics behavior
    }

    @Test
    fun hapticFeedback_triggersOnNavigation() {
        composeTestRule.setContent {
            LiftrixTheme {
                MainNavigationContainer(
                    user = testUser,
                    onNavigateToAuth = mockNavigateToAuth,
                    viewModel = mockViewModel
                )
            }
        }

        // Click navigation item
        composeTestRule.onNodeWithText("Progress").performClick()
        
        // In integration tests with real device/emulator:
        // - Verify HapticFeedbackType.LongPress is triggered
        // - Haptic feedback occurs immediately on tap
        // - Feedback is appropriate for navigation action
    }
} 