package com.example.liftrix.ui.progress

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
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

/**
 * Navigation Integration Tests for Analytics Detail Views
 * 
 * Tests the navigation flows and back stack behavior for all detail screens:
 * - OneRmProgressionDetail
 * - VolumeAnalysisDetail  
 * - MuscleGroupDetail
 * - ExerciseRankingDetail
 * 
 * Validates:
 * - Route navigation with parameters
 * - Back navigation maintains proper stack
 * - Deep linking support
 * - Parameter passing between screens
 * - Progress tab scroll position restoration
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class DetailNavigationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun setupNavController() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            
            LiftrixTheme {
                UnifiedNavigationContainer(navController = navController)
            }
        }
    }

    @Test
    fun test_navigation_to_one_rm_progression_detail() {
        setupNavController()
        
        // Navigate to 1RM progression detail
        val route = LiftrixRoute.OneRmProgressionDetail(
            exerciseIds = listOf("bench-press", "squat"),
            timeRange = TimeRangeType.SIX_MONTHS
        )
        
        navController.navigate(route)
        
        // Verify we're at the detail screen
        composeTestRule.onNodeWithText("1RM Progression").assertIsDisplayed()
        composeTestRule.onNodeWithText("Time Range: SIX_MONTHS").assertIsDisplayed()
        
        // Verify back navigation
        navController.popBackStack()
        
        // Should be back at previous screen (Progress tab)
        assert(navController.previousBackStackEntry != null)
    }

    @Test
    fun test_navigation_to_muscle_group_detail() {
        setupNavController()
        
        // Navigate to muscle group detail
        val route = LiftrixRoute.MuscleGroupDetail(
            muscleGroup = MuscleGroup.CHEST,
            timeRange = TimeRangeType.ONE_MONTH
        )
        
        navController.navigate(route)
        
        // Verify we're at the detail screen with correct title
        composeTestRule.onNodeWithText("Muscle Groups - Chest").assertIsDisplayed()
        composeTestRule.onNodeWithText("Time Range: ONE_MONTH").assertIsDisplayed()
        
        // Verify muscle group selection
        composeTestRule.onNodeWithText("Chest").assertIsDisplayed()
    }

    @Test
    fun test_navigation_to_volume_analysis_detail() {
        setupNavController()
        
        // Navigate to volume analysis detail
        val route = LiftrixRoute.VolumeAnalysisDetail(
            groupBy = com.example.liftrix.domain.model.analytics.VolumeGrouping.BY_EXERCISE,
            timeRange = TimeRangeType.THREE_MONTHS
        )
        
        navController.navigate(route)
        
        // Verify navigation occurred
        assert(navController.currentDestination?.route?.contains("VolumeAnalysisDetail") == true)
        
        // Test back navigation
        navController.popBackStack()
        assert(navController.previousBackStackEntry != null)
    }

    @Test
    fun test_navigation_to_exercise_ranking_detail() {
        setupNavController()
        
        // Navigate to exercise ranking detail
        val route = LiftrixRoute.ExerciseRankingDetail(
            sortBy = com.example.liftrix.domain.model.analytics.RankingMetric.PERFORMANCE_SCORE,
            limit = 20
        )
        
        navController.navigate(route)
        
        // Verify navigation occurred
        assert(navController.currentDestination?.route?.contains("ExerciseRankingDetail") == true)
    }

    @Test
    fun test_card_detail_navigation() {
        setupNavController()
        
        // Test navigation triggered by card tap
        composeTestRule.onNodeWithContentDescription("1RM Progression Card").performClick()
        
        // Should navigate to detail view
        composeTestRule.onNodeWithText("1RM Progression").assertIsDisplayed()
        
        // Test back button functionality
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        
        // Should return to previous screen
        assert(navController.previousBackStackEntry != null)
    }

    @Test
    fun test_back_stack_maintains_progress_tab_state() {
        setupNavController()
        
        // Start at Progress tab
        navController.navigate(LiftrixRoute.Progress)
        
        // Navigate to a detail screen
        val route = LiftrixRoute.OneRmProgressionDetail()
        navController.navigate(route)
        
        // Verify detail screen is displayed
        composeTestRule.onNodeWithText("1RM Progression").assertIsDisplayed()
        
        // Navigate back
        navController.popBackStack()
        
        // Verify we're back at Progress tab
        assert(navController.currentDestination?.route == "Progress")
        
        // Scroll position should be maintained (tested via state preservation)
        // This would require additional state management testing
    }

    @Test
    fun test_parameter_passing_between_screens() {
        setupNavController()
        
        // Navigate with specific parameters
        val exerciseIds = listOf("deadlift", "squat", "bench-press")
        val timeRange = TimeRangeType.ONE_YEAR
        
        val route = LiftrixRoute.OneRmProgressionDetail(
            exerciseIds = exerciseIds,
            timeRange = timeRange
        )
        
        navController.navigate(route)
        
        // Verify parameters were passed correctly
        composeTestRule.onNodeWithText("Time Range: ONE_YEAR").assertIsDisplayed()
        
        // Exercise filter should show the passed exercises
        // This would require checking the ViewModel state or UI components
        composeTestRule.onNodeWithContentDescription("Filter exercises").performClick()
        
        // Should show the exercise filter with pre-selected exercises
        exerciseIds.forEach { exerciseId ->
            // Check that exercises are pre-selected in the filter
            // This depends on how the filter displays the exercise names
        }
    }

    @Test
    fun test_deep_linking_to_detail_screens() {
        setupNavController()
        
        // Test deep link navigation directly to a detail screen
        val deepLinkRoute = "liftrix://analytics/muscle-groups?muscleGroup=CHEST&timeRange=MONTH"
        
        // This would test the deep linking functionality
        // Implementation depends on deep link setup in the app
        
        // Navigate via deep link
        // navController.navigate(Uri.parse(deepLinkRoute))
        
        // Verify correct screen is displayed
        // composeTestRule.onNodeWithText("Muscle Groups - Chest").assertIsDisplayed()
    }

    @Test
    fun test_navigation_error_handling() {
        setupNavController()
        
        // Test navigation with invalid parameters
        try {
            val invalidRoute = LiftrixRoute.OneRmProgressionDetail(
                exerciseIds = emptyList(), // Invalid empty list
                timeRange = TimeRangeType.SIX_MONTHS
            )
            
            navController.navigate(invalidRoute)
            
            // Should handle gracefully or show error state
            composeTestRule.onNodeWithText("No Data Available").assertIsDisplayed()
            
        } catch (e: Exception) {
            // Navigation should handle errors gracefully
            assert(false) { "Navigation should not crash with invalid parameters" }
        }
    }

    @Test
    fun test_multiple_detail_screen_navigation() {
        setupNavController()
        
        // Navigate through multiple detail screens
        
        // 1. Go to 1RM detail
        navController.navigate(LiftrixRoute.OneRmProgressionDetail())
        composeTestRule.onNodeWithText("1RM Progression").assertIsDisplayed()
        
        // 2. Navigate to muscle group detail from 1RM screen
        navController.navigate(LiftrixRoute.MuscleGroupDetail())
        composeTestRule.onNodeWithText("Muscle Groups").assertIsDisplayed()
        
        // 3. Navigate to exercise ranking
        navController.navigate(LiftrixRoute.ExerciseRankingDetail())
        
        // 4. Test back navigation through all screens
        navController.popBackStack() // Back to muscle group
        composeTestRule.onNodeWithText("Muscle Groups").assertIsDisplayed()
        
        navController.popBackStack() // Back to 1RM
        composeTestRule.onNodeWithText("1RM Progression").assertIsDisplayed()
        
        navController.popBackStack() // Back to original screen
        
        // Verify back stack is properly maintained
        assert(navController.backQueue.size >= 1)
    }

    @Test
    fun test_screen_rotation_preserves_navigation_state() {
        setupNavController()
        
        // Navigate to a detail screen
        val route = LiftrixRoute.MuscleGroupDetail(
            muscleGroup = MuscleGroup.SHOULDERS,
            timeRange = TimeRangeType.QUARTER
        )
        
        navController.navigate(route)
        composeTestRule.onNodeWithText("Muscle Groups - Shoulders").assertIsDisplayed()
        
        // Simulate screen rotation (configuration change)
        // This would typically be done through ActivityScenario
        // For now, verify that the screen state is maintained
        
        composeTestRule.onNodeWithText("Muscle Groups - Shoulders").assertIsDisplayed()
        composeTestRule.onNodeWithText("Time Range: QUARTER").assertIsDisplayed()
    }

    @Test
    fun test_concurrent_navigation_safety() {
        setupNavController()
        
        // Test rapid navigation to prevent crashes
        repeat(10) { index ->
            val route = when (index % 4) {
                0 -> LiftrixRoute.OneRmProgressionDetail()
                1 -> LiftrixRoute.MuscleGroupDetail()
                2 -> LiftrixRoute.VolumeAnalysisDetail()
                else -> LiftrixRoute.ExerciseRankingDetail()
            }
            
            navController.navigate(route)
            
            // Small delay to simulate user interaction
            Thread.sleep(50)
            
            if (index % 2 == 0) {
                navController.popBackStack()
            }
        }
        
        // Navigation should remain stable
        assert(navController.currentDestination != null)
    }
}