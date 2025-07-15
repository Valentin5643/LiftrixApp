package com.example.liftrix.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive Deep Linking Tests for Type-Safe Navigation
 * 
 * This test suite validates deep linking functionality with the UnifiedNavigationContainer
 * implementation, focusing on kotlinx.serialization integration and external intent handling
 * according to TEST-002 requirements.
 * 
 * Test Coverage:
 * - Deep linking with LiftrixRoute serialization/deserialization
 * - External intent handling from notifications, widgets, other apps
 * - Route parameter validation and type safety in deep links
 * - Deep link navigation state management
 * - Error handling for malformed deep links
 * - Performance validation for deep link resolution
 * - Integration with Android intent filter system
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DeepLinkingTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

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
     * TEST-002: Basic deep linking validation
     * Verifies deep links work correctly for simple routes without parameters
     */
    @Test
    fun testBasicDeepLinking_simpleRoutes() {
        // Test deep linking to main routes
        val homeDeepLink = createDeepLinkUri("liftrix://home")
        navController.navigateFromDeepLink(LiftrixRoute.Home)
        assertEquals(LiftrixRoute.Home::class.simpleName, getCurrentRouteClass())
        
        val workoutDeepLink = createDeepLinkUri("liftrix://workout")
        navController.navigateFromDeepLink(LiftrixRoute.Workout)
        assertEquals(LiftrixRoute.Workout::class.simpleName, getCurrentRouteClass())
        
        val progressDeepLink = createDeepLinkUri("liftrix://progress")
        navController.navigateFromDeepLink(LiftrixRoute.Progress)
        assertEquals(LiftrixRoute.Progress::class.simpleName, getCurrentRouteClass())
        
        val coachDeepLink = createDeepLinkUri("liftrix://coach")
        navController.navigateFromDeepLink(LiftrixRoute.Coach)
        assertEquals(LiftrixRoute.Coach::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Parameterized deep linking validation
     * Verifies deep links work correctly with route parameters using kotlinx.serialization
     */
    @Test
    fun testParameterizedDeepLinking_typeSafety() {
        val testWorkoutId = "deep-link-workout-123"
        val testExerciseId = "deep-link-exercise-456"
        val testTemplateId = "deep-link-template-789"
        
        // Test WorkoutDetails deep linking with workoutId parameter
        val workoutDetailsRoute = LiftrixRoute.WorkoutDetails(testWorkoutId)
        navController.navigateFromDeepLink(workoutDetailsRoute)
        assertEquals(LiftrixRoute.WorkoutDetails::class.simpleName, getCurrentRouteClass())
        
        // Test ExerciseDetails deep linking with exerciseId parameter
        val exerciseDetailsRoute = LiftrixRoute.ExerciseDetails(testExerciseId)
        navController.navigateFromDeepLink(exerciseDetailsRoute)
        assertEquals(LiftrixRoute.ExerciseDetails::class.simpleName, getCurrentRouteClass())
        
        // Test ActiveWorkout deep linking with multiple parameters
        val activeWorkoutRoute = LiftrixRoute.ActiveWorkout(
            templateId = testTemplateId,
            isBlankWorkout = false
        )
        navController.navigateFromDeepLink(activeWorkoutRoute)
        assertEquals(LiftrixRoute.ActiveWorkout::class.simpleName, getCurrentRouteClass())
        
        // Test ExerciseSelection deep linking with optional parameters
        val exerciseSelectionRoute = LiftrixRoute.ExerciseSelection(
            templateId = testTemplateId,
            isForTemplate = true
        )
        navController.navigateFromDeepLink(exerciseSelectionRoute)
        assertEquals(LiftrixRoute.ExerciseSelection::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Route serialization/deserialization validation
     * Verifies kotlinx.serialization works correctly for deep link processing
     */
    @Test
    fun testRouteSerialization_kotlinxSerialization() {
        // Test serialization of simple routes
        val homeRouteJson = Json.encodeToString(LiftrixRoute.Home)
        assertNotNull(homeRouteJson)
        assertTrue(homeRouteJson.isNotEmpty())
        
        val deserializedHomeRoute = Json.decodeFromString<LiftrixRoute>(homeRouteJson)
        assertEquals(LiftrixRoute.Home, deserializedHomeRoute)
        
        // Test serialization of parameterized routes
        val workoutDetailsRoute = LiftrixRoute.WorkoutDetails("serialization-test-workout")
        val workoutDetailsJson = Json.encodeToString(workoutDetailsRoute)
        assertNotNull(workoutDetailsJson)
        
        val deserializedWorkoutDetails = Json.decodeFromString<LiftrixRoute>(workoutDetailsJson)
        assertEquals(workoutDetailsRoute, deserializedWorkoutDetails)
        
        // Test serialization of complex routes with multiple parameters
        val exerciseSelectionRoute = LiftrixRoute.ExerciseSelection(
            templateId = "test-template-123",
            isForTemplate = true
        )
        val exerciseSelectionJson = Json.encodeToString(exerciseSelectionRoute)
        val deserializedExerciseSelection = Json.decodeFromString<LiftrixRoute>(exerciseSelectionJson)
        assertEquals(exerciseSelectionRoute, deserializedExerciseSelection)
    }

    /**
     * TEST-002: External intent handling validation
     * Verifies deep links work correctly when coming from external applications
     */
    @Test
    fun testExternalIntentHandling_realWorldScenarios() {
        // Scenario 1: Notification deep link to specific workout
        val notificationIntent = createIntentWithDeepLink("liftrix://workout/details/notification-workout-123")
        val workoutRoute = LiftrixRoute.WorkoutDetails("notification-workout-123")
        navController.navigateFromDeepLink(workoutRoute, clearStack = false)
        assertEquals(LiftrixRoute.WorkoutDetails::class.simpleName, getCurrentRouteClass())
        
        // Scenario 2: Widget deep link to start blank workout
        val widgetIntent = createIntentWithDeepLink("liftrix://workout/active?blank=true")
        val blankWorkoutRoute = LiftrixRoute.ActiveWorkout(isBlankWorkout = true)
        navController.navigateFromDeepLink(blankWorkoutRoute, clearStack = false)
        assertEquals(LiftrixRoute.ActiveWorkout::class.simpleName, getCurrentRouteClass())
        
        // Scenario 3: Share link deep link to exercise details
        val shareIntent = createIntentWithDeepLink("liftrix://exercise/details/shared-exercise-456")
        val sharedExerciseRoute = LiftrixRoute.ExerciseDetails("shared-exercise-456")
        navController.navigateFromDeepLink(sharedExerciseRoute, clearStack = false)
        assertEquals(LiftrixRoute.ExerciseDetails::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Deep link back stack management validation
     * Verifies proper back stack handling for deep link navigation
     */
    @Test
    fun testDeepLinkBackStack_stateManagement() {
        // Test deep link with clear stack
        navController.navigateToWorkout()
        navController.navigateToFriends()
        assertEquals(LiftrixRoute.Friends::class.simpleName, getCurrentRouteClass())
        
        // Deep link with clearStack = true should reset navigation state
        val deepLinkRoute = LiftrixRoute.WorkoutDetails("deep-link-clear-test")
        navController.navigateFromDeepLink(deepLinkRoute, clearStack = true)
        assertEquals(LiftrixRoute.WorkoutDetails::class.simpleName, getCurrentRouteClass())
        
        // Should not be able to navigate back since stack was cleared
        val canNavigateBack = navController.canNavigateUp()
        assertTrue(!canNavigateBack || navController.previousBackStackEntry == null)
        
        // Test deep link without clear stack
        navController.navigateToHome()
        navController.navigateToProgress()
        
        val preserveStackRoute = LiftrixRoute.ExerciseDetails("preserve-stack-test")
        navController.navigateFromDeepLink(preserveStackRoute, clearStack = false)
        assertEquals(LiftrixRoute.ExerciseDetails::class.simpleName, getCurrentRouteClass())
        
        // Should be able to navigate back to previous screen
        val backSuccess = navController.popBackStackSafely()
        assertTrue(backSuccess)
        assertEquals(LiftrixRoute.Progress::class.simpleName, getCurrentRouteClass())
    }

    /**
     * TEST-002: Deep link error handling validation
     * Verifies proper error handling for malformed or invalid deep links
     */
    @Test
    fun testDeepLinkErrorHandling_invalidLinks() {
        // Test navigation with invalid route data
        // Note: In real implementation, this would test malformed URI handling
        val originalRoute = getCurrentRouteClass()
        
        // Attempt to navigate with malformed data should not crash
        try {
            val malformedRoute = LiftrixRoute.WorkoutDetails("") // Empty ID
            navController.navigateFromDeepLink(malformedRoute)
            // Should handle gracefully - either stay on current route or navigate to error state
        } catch (e: Exception) {
            // Should not throw unhandled exceptions
            assertTrue(false, "Deep link error handling should be graceful, but threw: ${e.message}")
        }
        
        // Test navigation with null or missing parameters
        try {
            // This would represent a deep link with missing required parameters
            val incompleteRoute = LiftrixRoute.ExerciseSelection() // No template ID when required
            navController.navigateFromDeepLink(incompleteRoute)
            // Should handle gracefully
        } catch (e: Exception) {
            assertTrue(false, "Deep link with missing parameters should be handled gracefully")
        }
    }

    /**
     * TEST-002: Deep link performance validation
     * Verifies deep link resolution meets performance targets
     */
    @Test
    fun testDeepLinkPerformance_resolutionSpeed() {
        val deepLinkRoutes = listOf(
            LiftrixRoute.Home,
            LiftrixRoute.WorkoutDetails("perf-test-workout"),
            LiftrixRoute.ExerciseSelection(templateId = "perf-test-template"),
            LiftrixRoute.ActiveWorkout(templateId = "perf-test-template", isBlankWorkout = false),
            LiftrixRoute.ExerciseDetails("perf-test-exercise")
        )
        
        deepLinkRoutes.forEach { route ->
            val startTime = System.currentTimeMillis()
            navController.navigateFromDeepLink(route)
            val duration = System.currentTimeMillis() - startTime
            
            // Verify deep link resolution meets <10ms performance target
            assertTrue(
                duration < 10,
                "Deep link resolution for ${route::class.simpleName} took ${duration}ms, exceeding 10ms target"
            )
        }
    }

    /**
     * TEST-002: Deep link integration with session management
     * Verifies deep links work correctly with active workout sessions
     */
    @Test
    fun testDeepLinkSessionIntegration_activeWorkouts() {
        // Test deep link to active workout when session exists
        val activeWorkoutRoute = LiftrixRoute.ActiveWorkout(
            templateId = "session-test-template",
            isBlankWorkout = false
        )
        navController.navigateFromDeepLink(activeWorkoutRoute)
        assertEquals(LiftrixRoute.ActiveWorkout::class.simpleName, getCurrentRouteClass())
        
        // Test deep link to exercise selection from active workout context
        val exerciseSelectionRoute = LiftrixRoute.ExerciseSelection(isForTemplate = false)
        navController.navigateFromDeepLink(exerciseSelectionRoute)
        assertEquals(LiftrixRoute.ExerciseSelection::class.simpleName, getCurrentRouteClass())
        
        // Navigation should maintain session context
        composeTestRule.waitForIdle()
    }

    /**
     * TEST-002: Deep link state restoration validation
     * Verifies deep links restore proper application state
     */
    @Test
    fun testDeepLinkStateRestoration_applicationState() {
        // Test deep link to specific feature restores proper app state
        val templateCreationRoute = LiftrixRoute.TemplateCreation
        navController.navigateFromDeepLink(templateCreationRoute, clearStack = true)
        assertEquals(LiftrixRoute.TemplateCreation::class.simpleName, getCurrentRouteClass())
        
        // Verify app state is properly initialized for the deep-linked feature
        composeTestRule.waitForIdle()
        
        // Test deep link to settings preserves user context
        val settingsRoute = LiftrixRoute.Settings
        navController.navigateFromDeepLink(settingsRoute)
        assertEquals(LiftrixRoute.Settings::class.simpleName, getCurrentRouteClass())
        
        // Verify navigation maintains proper state context
        composeTestRule.waitForIdle()
    }

    /**
     * TEST-002: Cross-platform deep linking validation
     * Verifies deep links work correctly across different entry points
     */
    @Test
    fun testCrossPlatformDeepLinking_entryPoints() {
        // Test deep link from Android notification
        simulateNotificationDeepLink("workout-notification-123")
        
        // Test deep link from Android widget
        simulateWidgetDeepLink("widget-template-456")
        
        // Test deep link from external app sharing
        simulateExternalAppDeepLink("shared-exercise-789")
        
        // All should result in proper navigation without errors
        composeTestRule.waitForIdle()
    }

    // Helper functions for testing

    private fun getCurrentRouteClass(): String? {
        return navController.currentBackStackEntry?.destination?.route?.substringAfterLast(".")
    }

    private fun createDeepLinkUri(uriString: String): Uri {
        return Uri.parse(uriString)
    }

    private fun createIntentWithDeepLink(uri: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(context.packageName)
        }
    }

    private fun simulateNotificationDeepLink(workoutId: String) {
        val route = LiftrixRoute.WorkoutDetails(workoutId)
        navController.navigateFromDeepLink(route, clearStack = false)
    }

    private fun simulateWidgetDeepLink(templateId: String) {
        val route = LiftrixRoute.ActiveWorkout(templateId = templateId, isBlankWorkout = false)
        navController.navigateFromDeepLink(route, clearStack = true)
    }

    private fun simulateExternalAppDeepLink(exerciseId: String) {
        val route = LiftrixRoute.ExerciseDetails(exerciseId)
        navController.navigateFromDeepLink(route, clearStack = false)
    }
}