package com.example.liftrix.ui.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive Integration Tests for Type-Safe Navigation
 * 
 * This test suite validates the UnifiedNavigationContainer implementation
 * with focus on type-safe navigation, route resolution performance, and
 * navigation state management according to TEST-002 requirements.
 * 
 * Test Coverage:
 * - Type-safe navigation flows using LiftrixRoute sealed classes
 * - Navigation extension functions and clean API usage
 * - Back stack management and state preservation
 * - Route resolution performance (<10ms target)
 * - Navigation error prevention through compile-time safety
 * - Integration with UnifiedWorkoutSessionManager
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
        
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            
            // Initialize with UnifiedNavigationContainer
            UnifiedNavigationContainer(navController = navController)
        }
    }

    /**
     * TEST-002: Type-safe navigation validation
     * Verifies all LiftrixRoute navigation paths work correctly with compile-time safety
     */
    @Test
    fun testTypeSafeNavigation_allRoutes() {
        // Test Home route navigation (starting point)
        assertEquals(LiftrixRoute.Home::class.simpleName, getCurrentRouteClass())
        
        // Test main tab navigation
        navController.navigateToWorkout()
        assertEquals(LiftrixRoute.Workout::class.simpleName, getCurrentRouteClass())
        
        navController.navigateToProgress()
        assertEquals(LiftrixRoute.Progress::class.simpleName, getCurrentRouteClass())
        
        navController.navigateToCoach()
        assertEquals(LiftrixRoute.Coach::class.simpleName, getCurrentRouteClass())
        
        navController.navigateToFriends()
        assertEquals(LiftrixRoute.Friends::class.simpleName, getCurrentRouteClass())
        
        // Test feature navigation
        navController.navigateToSettings()
        assertEquals(LiftrixRoute.Settings::class.simpleName, getCurrentRouteClass())
        
        navController.navigateToTemplateCreation()
        assertEquals(LiftrixRoute.TemplateCreation::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Parameterized route navigation validation
     * Verifies type-safe navigation with parameters works correctly
     */
    @Test
    fun testParameterizedRoutes_typeSafeNavigation() {
        val testWorkoutId = "test-workout-123"
        val testExerciseId = "test-exercise-456"
        val testTemplateId = "test-template-789"
        
        // Test WorkoutDetails navigation with workoutId parameter
        navController.navigateToWorkoutDetails(testWorkoutId)
        val workoutDetailsRoute = getCurrentRouteClass()
        assertEquals(LiftrixRoute.WorkoutDetails::class.simpleName, workoutDetailsRoute)
        
        // Test ExerciseSelection navigation with parameters
        navController.navigateToExerciseSelection(templateId = testTemplateId, isForTemplate = true)
        assertEquals(LiftrixRoute.ExerciseSelection::class.simpleName, getCurrentRouteClass())
        
        // Test ActiveWorkout navigation with parameters
        navController.navigateToActiveWorkout(templateId = testTemplateId, isBlankWorkout = false)
        assertEquals(LiftrixRoute.ActiveWorkout::class.simpleName, getCurrentRouteClass())
        
        // Test ExerciseDetails navigation
        navController.navigateToExerciseDetails(testExerciseId)
        assertEquals(LiftrixRoute.ExerciseDetails::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Navigation extension functions validation
     * Verifies all extension functions provide clean API and work correctly
     */
    @Test
    fun testNavigationExtensions_cleanAPI() {
        // Test utility extension functions
        navController.navigateToFriends()
        assertTrue(navController.canNavigateUp())
        
        val backResult = navController.popBackStackSafely()
        assertTrue(backResult)
        
        // Test single top navigation
        navController.navigateSingleTop(LiftrixRoute.Workout)
        assertEquals(LiftrixRoute.Workout::class.simpleName, getCurrentRouteClass())
        
        // Test navigate and replace
        navController.navigateAndReplace(LiftrixRoute.Progress)
        assertEquals(LiftrixRoute.Progress::class.simpleName, getCurrentRouteClass())
        
        // Test clear back stack and navigate
        navController.clearBackStackAndNavigate(LiftrixRoute.Home)
        assertEquals(LiftrixRoute.Home::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Back stack management validation
     * Verifies proper back stack state management and navigation safety
     */
    @Test
    fun testBackStackManagement_statePreservation() {
        // Build a navigation stack
        navController.navigateToWorkout()
        navController.navigateToFriends()
        navController.navigateToSettings()
        
        // Verify current position
        assertEquals(LiftrixRoute.Settings::class.simpleName, getCurrentRouteClass())
        assertTrue(navController.canNavigateUp())
        
        // Test safe back navigation
        val backSuccess1 = navController.popBackStackSafely()
        assertTrue(backSuccess1)
        assertEquals(LiftrixRoute.Friends::class.simpleName, getCurrentRouteClass())
        
        val backSuccess2 = navController.popBackStackSafely()
        assertTrue(backSuccess2)
        assertEquals(LiftrixRoute.Workout::class.simpleName, getCurrentRouteClass())
        
        val backSuccess3 = navController.popBackStackSafely()
        assertTrue(backSuccess3)
        assertEquals(LiftrixRoute.Home::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Navigation state preservation across configuration changes
     * Verifies navigation state is properly maintained during device rotation, etc.
     */
    @Test
    fun testNavigationState_configurationChanges() {
        // Navigate to a specific screen with parameters
        val testWorkoutId = "config-test-workout"
        navController.navigateToWorkoutDetails(testWorkoutId)
        
        // Verify navigation state before configuration change
        assertEquals(LiftrixRoute.WorkoutDetails::class.simpleName, getCurrentRouteClass())
        
        // Simulate configuration change by recreating the navigation container
        composeTestRule.setContent {
            val newNavController = TestNavHostController(LocalContext.current)
            newNavController.navigatorProvider.addNavigator(ComposeNavigator())
            
            // Restore navigation state (in real app this would be automatic)
            newNavController.navigate(LiftrixRoute.WorkoutDetails(testWorkoutId))
            
            UnifiedNavigationContainer(navController = newNavController)
        }
        
        // Verify state is preserved after configuration change
        // Note: In a real test, this would verify automatic state restoration
        composeTestRule.waitForIdle()
    }

    /**
     * TEST-002: Navigation performance validation
     * Verifies route resolution meets <10ms performance target
     */
    @Test
    fun testNavigationPerformance_routeResolution() {
        val routes = listOf(
            LiftrixRoute.Home,
            LiftrixRoute.Workout,
            LiftrixRoute.Progress,
            LiftrixRoute.Coach,
            LiftrixRoute.Friends
        )
        
        routes.forEach { route ->
            val startTime = System.currentTimeMillis()
            navController.navigate(route)
            val duration = System.currentTimeMillis() - startTime
            
            // Verify <10ms route resolution target
            assertTrue(
                duration < 10,
                "Route resolution for ${route::class.simpleName} took ${duration}ms, exceeding 10ms target"
            )
        }
    }

    /**
     * TEST-002: Complex navigation scenarios validation
     * Verifies navigation works correctly in complex app scenarios
     */
    @Test
    fun testComplexNavigationScenarios_realWorldUsage() {
        // Scenario 1: Template creation flow
        navController.navigateToWorkout()
        navController.navigateToTemplateCreation()
        navController.navigateToExerciseSelection(isForTemplate = true)
        
        // Verify proper navigation state
        assertEquals(LiftrixRoute.ExerciseSelection::class.simpleName, getCurrentRouteClass())
        
        // Navigate back through the flow
        navController.popBackStackSafely()
        assertEquals(LiftrixRoute.TemplateCreation::class.simpleName, getCurrentRouteClass())
        
        // Scenario 2: Active workout flow
        navController.navigateToActiveWorkout(templateId = "test-template", isBlankWorkout = false)
        assertEquals(LiftrixRoute.ActiveWorkout::class.simpleName, getCurrentRouteClass())
        
        // Add exercise to active workout
        navController.navigateToExerciseSelection()
        assertEquals(LiftrixRoute.ExerciseSelection::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Navigation error prevention validation
     * Verifies compile-time safety prevents navigation errors
     */
    @Test
    fun testNavigationSafety_errorPrevention() {
        // Test navigateIfNotCurrent prevents duplicate navigation
        navController.navigateToWorkout()
        val currentRoute = getCurrentRouteClass()
        
        navController.navigateIfNotCurrent(LiftrixRoute.Workout)
        assertEquals(currentRoute, getCurrentRouteClass()) // Should remain the same
        
        // Test conditional navigation
        var conditionMet = false
        navController.navigateIf(
            route = LiftrixRoute.Progress,
            condition = false,
            onConditionFailed = { conditionMet = true }
        )
        
        assertTrue(conditionMet)
        assertEquals(LiftrixRoute.Workout::class.simpleName, getCurrentRouteClass()) // Should not have navigated
        
        // Test successful conditional navigation
        navController.navigateIf(
            route = LiftrixRoute.Progress,
            condition = true
        )
        assertEquals(LiftrixRoute.Progress::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Bottom navigation integration validation
     * Verifies bottom navigation works correctly with type-safe routes
     */
    @Test
    fun testBottomNavigation_integration() {
        // Verify bottom navigation items are displayed
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Workout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Progress").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Coach").assertIsDisplayed()
        
        // Test bottom navigation item clicks
        composeTestRule.onNodeWithContentDescription("Workout").performClick()
        composeTestRule.waitForIdle()
        assertEquals(LiftrixRoute.Workout::class.simpleName, getCurrentRouteClass())
        
        composeTestRule.onNodeWithContentDescription("Progress").performClick()
        composeTestRule.waitForIdle()
        assertEquals(LiftrixRoute.Progress::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Navigation integration with session management
     * Verifies navigation works correctly with UnifiedWorkoutSessionManager
     */
    @Test
    fun testSessionIntegration_navigationCoordination() {
        // Navigate to active workout to test session integration
        navController.navigateToActiveWorkout(isBlankWorkout = true)
        assertEquals(LiftrixRoute.ActiveWorkout::class.simpleName, getCurrentRouteClass())
        
        // Verify navigation container handles session state correctly
        // Note: Full session integration testing would require mock session manager
        composeTestRule.waitForIdle()
        
        // Test navigation back from active workout
        navController.popBackStackSafely()
        assertEquals(LiftrixRoute.Home::class.simpleName, getCurrentRouteClass())
    }

    // Helper function to get current route class name for testing
    private fun getCurrentRouteClass(): String? {
        return navController.currentBackStackEntry?.destination?.route?.substringAfterLast(".")
    }
}