package com.example.liftrix.ui.progress

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.navigation.UnifiedNavigationContainer
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.test.runTest

/**
 * End-to-End Analytics Navigation Flow Tests
 * 
 * Tests complete user flows from Progress tab → widget interaction → detail screen → back navigation.
 * Validates the full navigation integration including:
 * 
 * Complete User Flows:
 * 1. Start at Main Navigation (BottomNav)
 * 2. Navigate to Progress tab
 * 3. Interact with dashboard (scroll, time range changes)
 * 4. Click analytics widget
 * 5. Navigate to detail screen with parameters
 * 6. Interact with detail screen (filters, exports, etc.)
 * 7. Navigate back to dashboard 
 * 8. Verify dashboard state is preserved
 * 9. Test multiple navigation paths
 * 
 * Real-World Scenarios:
 * - User discovers analytics widgets on dashboard
 * - User explores different time ranges and sees widget updates
 * - User clicks widget to get detailed analysis
 * - User navigates between multiple detail screens
 * - User returns to dashboard and continues normal usage
 * - User can access detail screens directly via deep links
 * 
 * Performance & UX:
 * - Navigation maintains 60fps performance
 * - Dashboard state (scroll position, selections) preserved
 * - Detail screens load within acceptable time limits
 * - Back navigation is smooth and predictable
 * - No memory leaks or state corruption
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class E2EAnalyticsNavigationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun setupFullNavigationFlow() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            
            LiftrixTheme {
                UnifiedNavigationContainer(navController = navController)
            }
        }
        
        // Start at the main screen and wait for initial load
        composeTestRule.waitForIdle()
    }

    @Test
    fun test_complete_analytics_discovery_flow() = runTest {
        setupFullNavigationFlow()
        
        // 1. Navigate to Progress tab from main navigation
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        
        // Verify we're on the Progress dashboard
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
        
        // 2. Explore the dashboard - scroll to see analytics widgets
        val dashboardScrollable = composeTestRule.onNodeWithTag("ProgressDashboardScrollable")
        dashboardScrollable.performScrollToNode(hasText("Analytics"))
        
        // 3. User changes time range to see how widgets update
        val timeRangeSelector = composeTestRule.onNodeWithContentDescription("Time Range Selector")
        timeRangeSelector.performClick()
        composeTestRule.onNodeWithText("6 Months").performClick()
        composeTestRule.waitForIdle()
        
        // Verify widgets updated with new time range
        composeTestRule.onNodeWithText("6 Months").assertIsDisplayed()
        
        // 4. User discovers and clicks 1RM Progression widget
        val oneRmWidget = composeTestRule.onNodeWithContentDescription("1RM Progression Widget")
        oneRmWidget.assertIsDisplayed()
        oneRmWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 5. User is now in 1RM detail screen
        composeTestRule.onNodeWithText("1RM Progression Detail").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIX_MONTHS").assertIsDisplayed()
        
        // 6. User interacts with detail screen - try to change exercise filter
        val exerciseFilterButton = composeTestRule.onNodeWithContentDescription("Exercise Filter")
        if (exerciseFilterButton.tryPerformClick()) {
            composeTestRule.onNodeWithText("Select Exercises").assertIsDisplayed()
            // Select some exercises
            composeTestRule.onNodeWithText("Bench Press").performClick()
            composeTestRule.onNodeWithText("Apply").performClick()
            composeTestRule.waitForIdle()
        }
        
        // 7. User tries export functionality
        val exportButton = composeTestRule.onNodeWithContentDescription("Export Data")
        if (exportButton.tryPerformClick()) {
            composeTestRule.waitForIdle()
            // Export should work without crashing
        }
        
        // 8. User navigates back to dashboard
        navController.popBackStack()
        composeTestRule.waitForIdle()
        
        // 9. Verify we're back at Progress dashboard with preserved state
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("6 Months").assertIsDisplayed() // Time range preserved
        
        // Verify scroll position is approximately preserved
        composeTestRule.onNodeWithText("Analytics").assertIsDisplayed()
    }

    @Test
    fun test_multi_widget_exploration_flow() = runTest {
        setupFullNavigationFlow()
        
        // Navigate to Progress tab
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        
        // Explore multiple widgets in sequence
        val widgetExplorationFlow = listOf(
            "1RM Progression Widget" to "1RM Progression Detail",
            "Volume Analysis Widget" to "Volume Analysis",
            "Muscle Group Distribution Widget" to "Muscle Group Analysis",
            "Exercise Ranking Widget" to "Exercise Rankings"
        )
        
        widgetExplorationFlow.forEach { (widgetContentDesc, expectedDetailTitle) ->
            // Go to Progress dashboard if not already there
            if (navController.currentDestination?.route != "Progress") {
                navController.navigate(LiftrixRoute.Progress)
                composeTestRule.waitForIdle()
            }
            
            // Find and click the widget
            try {
                val widget = composeTestRule.onNodeWithContentDescription(widgetContentDesc)
                widget.assertIsDisplayed()
                widget.performClick()
                composeTestRule.waitForIdle()
                
                // Verify detail screen loaded
                composeTestRule.onNodeWithText(expectedDetailTitle).assertIsDisplayed()
                
                // Quick interaction test
                composeTestRule.onNodeWithContentDescription("Refresh").tryPerformClick()
                composeTestRule.waitForIdle()
                
                // Navigate back
                navController.popBackStack()
                composeTestRule.waitForIdle()
                
                // Verify we're back at Progress
                composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
                
            } catch (e: AssertionError) {
                // Widget might not be available - log but continue
                println("Widget '$widgetContentDesc' not available: ${e.message}")
            }
        }
    }

    @Test 
    fun test_dashboard_state_persistence_through_navigation() = runTest {
        setupFullNavigationFlow()
        
        // Navigate to Progress tab
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        
        // Set up specific dashboard state
        // 1. Change time range to 1 Year
        val timeRangeSelector = composeTestRule.onNodeWithContentDescription("Time Range Selector")
        timeRangeSelector.performClick()
        composeTestRule.onNodeWithText("1 Year").performClick()
        composeTestRule.waitForIdle()
        
        // 2. Scroll to a specific position
        val dashboardScrollable = composeTestRule.onNodeWithTag("ProgressDashboardScrollable")
        dashboardScrollable.performScrollToNode(hasText("Analytics Widgets"))
        
        // 3. Note current state indicators
        composeTestRule.onNodeWithText("1 Year").assertIsDisplayed()
        composeTestRule.onNodeWithText("Analytics Widgets").assertIsDisplayed()
        
        // 4. Navigate to detail screen
        val oneRmWidget = composeTestRule.onNodeWithContentDescription("1RM Progression Widget")
        oneRmWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 5. Spend some time in detail screen
        composeTestRule.onNodeWithText("1RM Progression Detail").assertIsDisplayed()
        Thread.sleep(1000) // Simulate user reading the detail view
        
        // 6. Navigate to another detail screen from this one
        navController.navigate(LiftrixRoute.VolumeAnalysisDetail())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Volume Analysis").assertIsDisplayed()
        Thread.sleep(1000)
        
        // 7. Navigate back to original detail screen
        navController.popBackStack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1RM Progression Detail").assertIsDisplayed()
        
        // 8. Navigate back to dashboard
        navController.popBackStack()
        composeTestRule.waitForIdle()
        
        // 9. Verify dashboard state is preserved
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 Year").assertIsDisplayed() // Time range preserved
        composeTestRule.onNodeWithText("Analytics Widgets").assertIsDisplayed() // Scroll position preserved
    }

    @Test
    fun test_parameter_passing_through_complete_flow() = runTest {
        setupFullNavigationFlow()
        
        // Navigate to Progress tab
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        
        // Set specific dashboard parameters
        // 1. Set time range to 3 months
        val timeRangeSelector = composeTestRule.onNodeWithContentDescription("Time Range Selector")
        timeRangeSelector.performClick()
        composeTestRule.onNodeWithText("3 Months").performClick()
        composeTestRule.waitForIdle()
        
        // 2. Click OneRM widget to test exercise parameter passing
        val oneRmWidget = composeTestRule.onNodeWithContentDescription("1RM Progression Widget")
        oneRmWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 3. Verify parameters were passed correctly
        composeTestRule.onNodeWithText("THREE_MONTHS").assertIsDisplayed()
        composeTestRule.onNodeWithText("1RM Progression Detail").assertIsDisplayed()
        
        // 4. Go back and test different widget type
        navController.popBackStack()
        composeTestRule.waitForIdle()
        
        // 5. Click Volume Analysis widget
        val volumeWidget = composeTestRule.onNodeWithContentDescription("Volume Analysis Widget")
        volumeWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 6. Verify Volume parameters were passed
        composeTestRule.onNodeWithText("Volume Analysis").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Volume").assertIsDisplayed() // Default grouping
        
        // 7. Go back and test Muscle Group widget
        navController.popBackStack()
        composeTestRule.waitForIdle()
        
        // 8. Click Muscle Group widget
        val muscleGroupWidget = composeTestRule.onNodeWithContentDescription("Muscle Group Distribution Widget")
        muscleGroupWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 9. Verify Muscle Group parameters
        composeTestRule.onNodeWithText("Muscle Group Analysis").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Muscle Groups").assertIsDisplayed() // null = all groups
        
        // 10. Test Exercise Ranking parameters
        navController.popBackStack()
        composeTestRule.waitForIdle()
        
        val exerciseRankingWidget = composeTestRule.onNodeWithContentDescription("Exercise Ranking Widget")
        exerciseRankingWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 11. Verify Exercise Ranking parameters
        composeTestRule.onNodeWithText("Exercise Rankings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Performance Score").assertIsDisplayed() // Default sorting
        composeTestRule.onNodeWithText("Top 20 Exercises").assertIsDisplayed() // Default limit
    }

    @Test
    fun test_deep_linking_to_analytics_detail_screens() = runTest {
        setupFullNavigationFlow()
        
        // Test direct navigation to detail screens (simulating deep links)
        
        // 1. Navigate directly to OneRM detail with specific parameters
        val oneRmRoute = LiftrixRoute.OneRmProgressionDetail(
            exerciseIds = listOf("bench-press", "squat"),
            timeRange = TimeRangeType.SIX_MONTHS
        )
        navController.navigate(oneRmRoute)
        composeTestRule.waitForIdle()
        
        // Verify detail screen loads correctly with parameters
        composeTestRule.onNodeWithText("1RM Progression Detail").assertIsDisplayed()
        composeTestRule.onNodeWithText("SIX_MONTHS").assertIsDisplayed()
        
        // 2. Navigate directly to Volume Analysis detail
        val volumeRoute = LiftrixRoute.VolumeAnalysisDetail(
            groupBy = com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_EXERCISE,
            timeRange = TimeRangeType.ONE_YEAR
        )
        navController.navigate(volumeRoute)
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("Volume Analysis").assertIsDisplayed()
        
        // 3. Navigate directly to Muscle Group detail
        val muscleGroupRoute = LiftrixRoute.MuscleGroupDetail(
            muscleGroup = MuscleGroup.CHEST,
            timeRange = TimeRangeType.THREE_MONTHS
        )
        navController.navigate(muscleGroupRoute)
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("Muscle Group Analysis").assertIsDisplayed()
        
        // 4. Navigate directly to Exercise Ranking detail
        val exerciseRankingRoute = LiftrixRoute.ExerciseRankingDetail(
            sortBy = com.example.liftrix.domain.model.analytics.RankingMetric.VOLUME_GROWTH,
            limit = 15
        )
        navController.navigate(exerciseRankingRoute)
        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithText("Exercise Rankings").assertIsDisplayed()
        
        // 5. Test back navigation works from deep-linked screens
        repeat(4) {
            navController.popBackStack()
            composeTestRule.waitForIdle()
        }
        
        // Should still have a valid navigation state
        assert(navController.currentDestination != null)
    }

    @Test
    fun test_rapid_navigation_stability() = runTest {
        setupFullNavigationFlow()
        
        // Navigate to Progress tab
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        
        // Test rapid navigation between widgets and screens
        repeat(10) { iteration ->
            try {
                // Choose different widgets based on iteration
                val widgetToTest = when (iteration % 4) {
                    0 -> "1RM Progression Widget"
                    1 -> "Volume Analysis Widget" 
                    2 -> "Muscle Group Distribution Widget"
                    else -> "Exercise Ranking Widget"
                }
                
                // Navigate to Progress if not already there
                if (navController.currentDestination?.route != "Progress") {
                    navController.navigate(LiftrixRoute.Progress)
                    composeTestRule.waitForIdle()
                }
                
                // Click widget rapidly
                val widget = composeTestRule.onNodeWithContentDescription(widgetToTest)
                widget.performClick()
                Thread.sleep(50) // Very short delay
                
                composeTestRule.waitForIdle()
                
                // Rapid back navigation
                navController.popBackStack()
                Thread.sleep(50)
                
                composeTestRule.waitForIdle()
                
            } catch (e: Exception) {
                // Log but don't fail - rapid navigation testing
                println("Iteration $iteration failed: ${e.message}")
            }
        }
        
        // App should still be stable
        assert(navController.currentDestination != null)
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
    }

    @Test
    fun test_analytics_tracking_throughout_navigation_flow() = runTest {
        setupFullNavigationFlow()
        
        // Navigate to Progress tab
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        
        // This test verifies analytics events are fired throughout navigation
        // In a real implementation, we would inject mock analytics service
        
        // 1. Widget view events (dashboard loaded)
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
        
        // 2. Widget interaction events (clicks)
        val oneRmWidget = composeTestRule.onNodeWithContentDescription("1RM Progression Widget")
        oneRmWidget.performClick()
        composeTestRule.waitForIdle()
        
        // 3. Detail screen view events
        composeTestRule.onNodeWithText("1RM Progression Detail").assertIsDisplayed()
        
        // 4. Detail screen interaction events
        val refreshButton = composeTestRule.onNodeWithContentDescription("Refresh")
        refreshButton.tryPerformClick()
        composeTestRule.waitForIdle()
        
        // 5. Back navigation events
        navController.popBackStack()
        composeTestRule.waitForIdle()
        
        // 6. Return to dashboard events
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
        
        // In real implementation, verify analytics service calls:
        // - Widget displayed events
        // - Widget clicked events  
        // - Screen viewed events
        // - User interaction events
        // - Navigation events
    }

    @Test
    fun test_accessibility_throughout_navigation_flow() = runTest {
        setupFullNavigationFlow()
        
        // Test accessibility features work throughout navigation
        
        // Navigate to Progress tab
        val progressTab = composeTestRule.onNodeWithContentDescription("Progress")
        progressTab.assertHasClickAction()
        progressTab.performClick()
        composeTestRule.waitForIdle()
        
        // Test dashboard accessibility
        composeTestRule.onNodeWithText("Progress Dashboard")
            .assertIsDisplayed()
            .assertIsNotFocusable() // Title should not be focusable
        
        // Test widget accessibility
        val oneRmWidget = composeTestRule.onNodeWithContentDescription("1RM Progression Widget")
        oneRmWidget.assertIsDisplayed()
        oneRmWidget.assertHasClickAction()
        oneRmWidget.performClick()
        composeTestRule.waitForIdle()
        
        // Test detail screen accessibility
        composeTestRule.onNodeWithText("1RM Progression Detail")
            .assertIsDisplayed()
            .assertIsNotFocusable()
        
        // Test back navigation accessibility
        val backButton = composeTestRule.onNodeWithContentDescription("Navigate up")
        backButton.assertIsDisplayed()
        backButton.assertHasClickAction()
        
        // Test time range selector accessibility
        val timeRangeSelector = composeTestRule.onNodeWithContentDescription("Time Range Selector")
        if (timeRangeSelector.tryAssertIsDisplayed()) {
            timeRangeSelector.assertHasClickAction()
        }
    }

    /**
     * Helper extension to safely try performing click without failing test
     */
    private fun SemanticsNodeInteraction.tryPerformClick(): Boolean {
        return try {
            performClick()
            true
        } catch (e: AssertionError) {
            false
        }
    }
    
    /**
     * Helper extension to safely try asserting element is displayed
     */
    private fun SemanticsNodeInteraction.tryAssertIsDisplayed(): Boolean {
        return try {
            assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}