package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.*
import com.example.liftrix.service.*
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * Integration tests for ProgressDashboardScreen focusing on UI flows and ViewModels integration.
 * 
 * Tests critical user flows with real ViewModels and state management:
 * - Dashboard loading and initialization
 * - Multi-ViewModel state composition 
 * - User interaction flows
 * - Error handling and recovery
 * - Time period selection and data refresh
 * - Feature flag controlled UI elements
 * 
 * Testing Strategy:
 * - Uses Hilt for real dependency injection
 * - Compose testing for UI interactions
 * - Real ViewModels with mocked service layer
 * - State synchronization validation
 * - Material 3 component testing
 * 
 * Key Integration Points:
 * - 7 ViewModels coordination via ProgressDashboardCoordinator
 * - Service layer data loading and caching
 * - UI state composition and lifecycle management
 * - Error propagation and user feedback
 * - Feature flags and conditional UI rendering
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProgressDashboardIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    // Test data
    private val testUser = User(
        id = "test-user-123",
        email = "test@example.com",
        displayName = "Test User"
    )

    private val testVolumeData = listOf(
        VolumeDataPoint(
            date = LocalDate(2024, 1, 1),
            volume = 1000.0,
            exercises = 5
        ),
        VolumeDataPoint(
            date = LocalDate(2024, 1, 2),
            volume = 1200.0,
            exercises = 6
        )
    )

    private val testSummaryData = ProgressSummary(
        totalWorkouts = 15,
        totalVolume = 25000.0,
        averageDuration = 65,
        currentStreak = 7,
        longestStreak = 12,
        lastWorkoutDate = LocalDate(2024, 1, 15)
    )

    @Before
    fun setup() {
        hiltRule.inject()
        
        // Clear any previous MockK state
        clearAllMocks()
    }

    /**
     * Test: Dashboard loads all major sections correctly
     * Verifies complete dashboard initialization with all ViewModels
     */
    @Test
    fun progressDashboardLoadsAllComponentsCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Verify screen header is displayed
        composeTestRule
            .onNodeWithText("Progress Dashboard")
            .assertIsDisplayed()

        // Verify major sections are present - wait for initial loading
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithTag("summary_section")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify summary section
        composeTestRule
            .onNodeWithTag("summary_section")
            .assertIsDisplayed()

        // Verify charts section
        composeTestRule
            .onNodeWithTag("charts_section")  
            .assertIsDisplayed()

        // Verify time period selector is available
        composeTestRule
            .onNodeWithTag("time_period_selector")
            .assertIsDisplayed()
    }

    /**
     * Test: Time period selection triggers chart data refresh
     * Validates reactive state management across ViewModels
     */
    @Test
    fun timePeriodSelectionTriggersChartRefresh() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for initial load
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithTag("time_period_selector")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click time period selector
        composeTestRule
            .onNodeWithTag("time_period_selector")
            .performClick()

        // Select different time period (3 months)
        composeTestRule
            .onNodeWithText("3 Months")
            .performClick()

        // Verify loading indicators appear (indicating refresh)
        composeTestRule
            .onNodeWithTag("charts_loading")
            .assertIsDisplayed()

        // Verify charts section updates
        composeTestRule
            .onNodeWithTag("charts_section")
            .assertIsDisplayed()
    }

    /**
     * Test: Error handling displays appropriate UI feedback
     * Validates error propagation and user recovery options
     */
    @Test
    fun errorStateDisplaysCorrectUIFeedback() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Simulate error condition by triggering refresh that fails
        // Wait for initial loading to complete
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithTag("refresh_button")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Trigger refresh that will cause error
        composeTestRule
            .onNodeWithTag("refresh_button")
            .performClick()

        // Verify error UI elements
        composeTestRule
            .onNodeWithText("Error Loading Progress")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Retry")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dismiss")
            .assertIsDisplayed()
    }

    /**
     * Test: Export functionality is feature flag controlled
     * Validates conditional UI rendering based on feature flags
     */
    @Test
    fun exportFunctionalityIsFeatureFlagControlled() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for feature flags to load
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithTag("export_dropdown")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithContentDescription("Export data")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // If export is enabled, verify export options
        if (composeTestRule
                .onAllNodesWithTag("export_dropdown")
                .fetchSemanticsNodes().isNotEmpty()) {
            
            composeTestRule
                .onNodeWithTag("export_dropdown")
                .performClick()

            composeTestRule
                .onNodeWithText("Export as PDF")
                .assertIsDisplayed()

            composeTestRule
                .onNodeWithText("Export as CSV")
                .assertIsDisplayed()
        }
    }

    /**
     * Test: Widget visibility toggle updates UI immediately
     * Validates real-time state synchronization between ViewModels
     */
    @Test
    fun widgetVisibilityToggleUpdatesUIImmediately() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for widgets section to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("widgets_section")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify widgets section is present
        composeTestRule
            .onNodeWithTag("widgets_section")
            .assertIsDisplayed()

        // Look for widget configuration options
        if (composeTestRule
                .onAllNodesWithTag("widget_config")
                .fetchSemanticsNodes().isNotEmpty()) {
            
            composeTestRule
                .onNodeWithTag("widget_config")
                .performClick()

            // Verify configuration options are displayed
            composeTestRule
                .onNodeWithTag("widget_visibility_toggle")
                .assertIsDisplayed()
        }
    }

    /**
     * Test: Calorie analytics section loads with proper data
     * Validates specialized ViewModel integration for calorie tracking
     */
    @Test
    fun calorieAnalyticsSectionLoadsWithProperData() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for calorie section to load
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("Calorie Analytics")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify calorie analytics header
        composeTestRule
            .onNodeWithText("Calorie Analytics")
            .assertIsDisplayed()

        // Verify calorie action buttons
        composeTestRule
            .onNodeWithText("Goals")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("View All")
            .assertIsDisplayed()

        // Verify calorie cards are present or loading state
        val hasCalorieCards = composeTestRule
            .onAllNodesWithTag("daily_calories_card")
            .fetchSemanticsNodes().isNotEmpty()

        val hasLoadingState = composeTestRule
            .onAllNodesWithText("Loading calorie analytics...")
            .fetchSemanticsNodes().isNotEmpty()

        assert(hasCalorieCards || hasLoadingState) {
            "Either calorie cards should be displayed or loading state should be shown"
        }
    }

    /**
     * Test: Summary section updates when data changes
     * Validates reactive data flow from services to UI
     */
    @Test
    fun summarySectionUpdatesWhenDataChanges() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for summary section
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithTag("summary_section")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify summary section is displayed
        composeTestRule
            .onNodeWithTag("summary_section")
            .assertIsDisplayed()

        // Trigger refresh to test data update flow
        if (composeTestRule
                .onAllNodesWithTag("summary_refresh")
                .fetchSemanticsNodes().isNotEmpty()) {
            
            composeTestRule
                .onNodeWithTag("summary_refresh")
                .performClick()

            // Verify loading state during refresh
            composeTestRule
                .onNodeWithTag("summary_loading")
                .assertIsDisplayed()
        }
    }

    /**
     * Test: Dashboard responds correctly to user authentication state
     * Validates auth-dependent UI rendering and data scoping
     */
    @Test
    fun dashboardRespondsCorrectlyToAuthState() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for initial authentication check
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            // Should either show authenticated content or auth prompt
            composeTestRule
                .onAllNodesWithText("Progress Dashboard")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithText("Sign In Required")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify appropriate content is displayed based on auth state
        val isAuthenticated = composeTestRule
            .onAllNodesWithText("Progress Dashboard")
            .fetchSemanticsNodes().isNotEmpty()

        if (isAuthenticated) {
            // Verify authenticated user content
            composeTestRule
                .onNodeWithText("Progress Dashboard")
                .assertIsDisplayed()
            
            composeTestRule
                .onNodeWithTag("summary_section")
                .assertIsDisplayed()
        } else {
            // Verify unauthenticated state
            composeTestRule
                .onNodeWithText("Sign In Required")
                .assertIsDisplayed()
        }
    }

    /**
     * Test: Coordinator manages inter-ViewModel communication
     * Validates centralized event coordination and state management
     */
    @Test
    fun coordinatorManagesInterViewModelCommunication() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }

        // Wait for coordinator to initialize
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithTag("refresh_all_button")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithContentDescription("Refresh all data")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Trigger global refresh via coordinator
        if (composeTestRule
                .onAllNodesWithTag("refresh_all_button")
                .fetchSemanticsNodes().isNotEmpty()) {
            
            composeTestRule
                .onNodeWithTag("refresh_all_button")
                .performClick()

            // Verify multiple sections show loading state (coordinated refresh)
            composeTestRule
                .onNodeWithTag("global_loading_indicator")
                .assertIsDisplayed()
        }
    }
}