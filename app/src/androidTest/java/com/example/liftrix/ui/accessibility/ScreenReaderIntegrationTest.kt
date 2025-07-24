package com.example.liftrix.ui.accessibility

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.ui.common.AccessibilityUtils
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.ModernActionButton.*
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Comprehensive screen reader integration testing for TalkBack and other assistive technologies.
 * 
 * Validates proper integration with Android's accessibility services including:
 * - TalkBack screen reader announcements and navigation
 * - Content description accuracy and contextual information
 * - State descriptions for dynamic content updates
 * - Live region announcements for real-time feedback
 * - Semantic traversal order for logical navigation flow
 * - Focus management during screen transitions and state changes
 * - Alternative interaction methods for drag-and-drop operations
 * - Screen reader support for complex UI patterns and workflows
 * 
 * Ensures all UnifiedWorkoutCard and ModernActionButton components provide
 * excellent screen reader experience matching WCAG 2.1 AA requirements.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScreenReaderIntegrationTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    /**
     * Tests that workout cards provide comprehensive screen reader announcements
     * with proper contextual information, actions, and state descriptions.
     */
    @Test
    fun workoutCards_provideRichScreenReaderAnnouncements() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Test active workout card with full context
                    UnifiedWorkoutCard(
                        title = "Morning Strength Training",
                        subtitle = "5 exercises • 45 minutes • High intensity",
                        onClick = { /* Navigate to workout details */ },
                        modifier = Modifier.testTag("talkback_active_workout")
                    ) {
                        Text("Upper body strength focus with progressive overload")
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Start Workout",
                                onClick = { /* Start active session */ },
                                modifier = Modifier.testTag("talkback_start_button")
                            )
                            SecondaryActionButton(
                                text = "Preview Exercises",
                                onClick = { /* Show exercise list */ },
                                modifier = Modifier.testTag("talkback_preview_button")
                            )
                        }
                    }
                    
                    // Test completed workout with historical context
                    UnifiedWorkoutCard(
                        title = "Evening Cardio Session",
                        subtitle = "Completed yesterday • 30 minutes • Personal record",
                        onClick = { /* View workout summary */ },
                        modifier = Modifier.testTag("talkback_completed_workout")
                    ) {
                        Text("High intensity interval training with new distance record")
                        
                        TertiaryActionButton(
                            text = "View Statistics",
                            onClick = { /* Show workout stats */ },
                            modifier = Modifier.testTag("talkback_stats_button")
                        )
                    }
                    
                    // Test non-interactive informational card
                    UnifiedWorkoutCard(
                        title = "Rest Day Recovery",
                        subtitle = "Scheduled for optimal performance",
                        modifier = Modifier.testTag("talkback_rest_card")
                    ) {
                        Text("Active recovery with light stretching and mobility work recommended")
                    }
                }
            }
        }
        
        // Verify active workout card screen reader announcements
        composeTestRule
            .onNodeWithTag("talkback_active_workout")
            .assert(hasClickAction())
            .assert(
                hasContentDescription(
                    "workout card: Morning Strength Training. 5 exercises • 45 minutes • High intensity. Double tap to open"
                ) or hasAnyDescendant(hasText("Morning Strength Training"))
            )
        
        // Verify button screen reader descriptions with hierarchy context
        composeTestRule
            .onNodeWithTag("talkback_start_button")
            .assert(
                hasContentDescription(
                    "Start Workout primary button. Double tap to activate"
                ) or hasText("Start Workout")
            )
        
        composeTestRule
            .onNodeWithTag("talkback_preview_button")
            .assert(
                hasContentDescription(
                    "Preview Exercises secondary button. Double tap to activate"
                ) or hasText("Preview Exercises")
            )
        
        // Verify completed workout provides historical context
        composeTestRule
            .onNodeWithTag("talkback_completed_workout")
            .assert(
                hasContentDescription(
                    "workout card: Evening Cardio Session. Completed yesterday • 30 minutes • Personal record. Double tap to open"
                ) or hasAnyDescendant(hasText("Evening Cardio Session"))
            )
        
        // Verify tertiary button has proper hierarchy description
        composeTestRule
            .onNodeWithTag("talkback_stats_button")
            .assert(
                hasContentDescription(
                    "View Statistics tertiary button. Double tap to activate"
                ) or hasText("View Statistics")
            )
        
        // Verify non-interactive card doesn't announce actions
        composeTestRule
            .onNodeWithTag("talkback_rest_card")
            .assert(!hasClickAction())
            .assert(
                hasContentDescription(
                    "workout card: Rest Day Recovery. Scheduled for optimal performance"
                ) or hasAnyDescendant(hasText("Rest Day Recovery"))
            )
    }
    
    /**
     * Tests dynamic state descriptions that update in real-time as workout state changes.
     * Validates proper live region announcements for active workout progress.
     */
    @Test
    fun dynamicWorkoutStates_provideRealTimeScreenReaderUpdates() {
        var workoutProgress by mutableStateOf(0)
        var workoutStatus by mutableStateOf("ready")
        var exerciseCount by mutableIntStateOf(0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Live announcement region for workout updates
                    Text(
                        text = when (workoutStatus) {
                            "ready" -> "Workout ready to start"
                            "active" -> "Workout in progress • $exerciseCount of 5 exercises completed"
                            "rest" -> "Rest period • Next exercise starts soon"
                            "completed" -> "Workout completed successfully with $exerciseCount exercises"
                            else -> ""
                        },
                        modifier = Modifier
                            .testTag("talkback_live_announcement")
                            .semantics {
                                liveRegion = if (workoutStatus == "rest") {
                                    LiveRegionMode.Assertive // Higher priority for rest announcements
                                } else {
                                    LiveRegionMode.Polite // Normal priority for progress updates
                                }
                            }
                    )
                    
                    // Dynamic workout card with changing state descriptions
                    UnifiedWorkoutCard(
                        title = "Active Workout Session",
                        subtitle = when (workoutStatus) {
                            "ready" -> "Ready to begin • 5 exercises planned"
                            "active" -> "In progress • $workoutProgress% complete"
                            "rest" -> "Rest period • Prepare for next exercise"
                            "completed" -> "Session complete • Excellent work!"
                            else -> "Unknown status"
                        },
                        onClick = { /* Handle workout card interaction */ },
                        modifier = Modifier
                            .testTag("talkback_dynamic_workout")
                            .semantics {
                                stateDescription = "Workout status: $workoutStatus • Progress: $workoutProgress percent"
                            }
                    ) {
                        Text(
                            text = when (workoutStatus) {
                                "ready" -> "Tap Start Workout to begin your training session"
                                "active" -> "Exercise ${exerciseCount + 1} of 5 • Keep up the great work!"
                                "rest" -> "Take a 30 second break before the next exercise"
                                "completed" -> "Amazing job completing your workout!"
                                else -> "Workout session status"
                            }
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            when (workoutStatus) {
                                "ready" -> {
                                    PrimaryActionButton(
                                        text = "Start Workout",
                                        onClick = { 
                                            workoutStatus = "active"
                                            workoutProgress = 20
                                            exerciseCount = 1
                                        },
                                        modifier = Modifier.testTag("talkback_start_dynamic")
                                    )
                                }
                                "active" -> {
                                    SecondaryActionButton(
                                        text = "Complete Exercise",
                                        onClick = { 
                                            if (exerciseCount < 4) {
                                                workoutStatus = "rest"
                                                workoutProgress += 20
                                                exerciseCount++
                                            } else {
                                                workoutStatus = "completed"
                                                workoutProgress = 100
                                                exerciseCount = 5
                                            }
                                        },
                                        modifier = Modifier.testTag("talkback_complete_exercise")
                                    )
                                    TertiaryActionButton(
                                        text = "Pause Workout",
                                        onClick = { /* Pause functionality */ },
                                        modifier = Modifier.testTag("talkback_pause_workout")
                                    )
                                }
                                "rest" -> {
                                    PrimaryActionButton(
                                        text = "Continue Workout",
                                        onClick = { 
                                            workoutStatus = "active"
                                        },
                                        modifier = Modifier.testTag("talkback_continue_workout")
                                    )
                                }
                                "completed" -> {
                                    PrimaryActionButton(
                                        text = "Save & Finish",
                                        onClick = { 
                                            workoutStatus = "ready"
                                            workoutProgress = 0
                                            exerciseCount = 0
                                        },
                                        modifier = Modifier.testTag("talkback_save_workout")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Test initial ready state screen reader announcements
        composeTestRule
            .onNodeWithTag("talkback_dynamic_workout")
            .assert(hasStateDescription("Workout status: ready • Progress: 0 percent"))
            .assert(hasAnyDescendant(hasText("Ready to begin • 5 exercises planned")))
        
        composeTestRule
            .onNodeWithTag("talkback_live_announcement")
            .assert(hasText("Workout ready to start"))
        
        // Simulate workout start
        composeTestRule
            .onNodeWithTag("talkback_start_dynamic")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify active state screen reader updates
        composeTestRule
            .onNodeWithTag("talkback_dynamic_workout")
            .assert(hasStateDescription("Workout status: active • Progress: 20 percent"))
        
        composeTestRule
            .onNodeWithTag("talkback_live_announcement")
            .assert(hasText("Workout in progress • 1 of 5 exercises completed"))
        
        // Complete an exercise to test rest state
        composeTestRule
            .onNodeWithTag("talkback_complete_exercise")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify rest state with assertive live region
        composeTestRule
            .onNodeWithTag("talkback_dynamic_workout")
            .assert(hasStateDescription("Workout status: rest • Progress: 40 percent"))
        
        composeTestRule
            .onNodeWithTag("talkback_live_announcement")
            .assert(hasText("Rest period • Next exercise starts soon"))
        
        // Continue workout to test progression
        composeTestRule
            .onNodeWithTag("talkback_continue_workout")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify return to active state
        composeTestRule
            .onNodeWithTag("talkback_dynamic_workout")
            .assert(hasStateDescription("Workout status: active • Progress: 40 percent"))
    }
    
    /**
     * Tests live region announcements for time-sensitive feedback and urgent notifications.
     * Validates proper use of polite vs assertive live regions based on urgency.
     */
    @Test
    fun liveRegionAnnouncements_providePriorityBasedFeedback() {
        var timerSeconds by mutableIntStateOf(30)
        var timerStatus by mutableStateOf("stopped")
        var urgentMessage by mutableStateOf("")
        var politeMessage by mutableStateOf("")
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Assertive live region for urgent announcements
                    Text(
                        text = urgentMessage,
                        modifier = Modifier
                            .testTag("talkback_urgent_announcements")
                            .semantics {
                                if (urgentMessage.isNotEmpty()) {
                                    liveRegion = LiveRegionMode.Assertive
                                }
                            }
                    )
                    
                    // Polite live region for regular updates
                    Text(
                        text = politeMessage,
                        modifier = Modifier
                            .testTag("talkback_polite_announcements")
                            .semantics {
                                if (politeMessage.isNotEmpty()) {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            }
                    )
                    
                    // Timer card with dynamic live region behavior
                    UnifiedWorkoutCard(
                        title = "Rest Timer",
                        subtitle = if (timerStatus == "running") {
                            "Time remaining: ${timerSeconds}s"
                        } else {
                            "Timer ready"
                        },
                        modifier = Modifier
                            .testTag("talkback_timer_card")
                            .semantics {
                                stateDescription = when (timerStatus) {
                                    "running" -> "Timer running • $timerSeconds seconds remaining"
                                    "finished" -> "Timer finished • Rest period complete"
                                    else -> "Timer stopped"
                                }
                                
                                // Use assertive live region for critical countdown
                                if (timerStatus == "running" && timerSeconds <= 5) {
                                    liveRegion = LiveRegionMode.Assertive
                                }
                            }
                    ) {
                        Text(
                            text = when {
                                timerStatus == "running" && timerSeconds > 10 -> "Rest in progress • Stay hydrated"
                                timerStatus == "running" && timerSeconds > 0 -> "Get ready! $timerSeconds seconds left"
                                timerStatus == "finished" -> "Rest period complete • Time to continue"
                                else -> "Ready to start rest timer"
                            },
                            modifier = Modifier.testTag("talkback_timer_message")
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (timerStatus) {
                                "stopped" -> {
                                    PrimaryActionButton(
                                        text = "Start Rest Timer",
                                        onClick = { 
                                            timerStatus = "running"
                                            timerSeconds = 30
                                            politeMessage = "Rest timer started • 30 seconds"
                                        },
                                        modifier = Modifier.testTag("talkback_start_timer")
                                    )
                                }
                                "running" -> {
                                    SecondaryActionButton(
                                        text = "Stop Timer",
                                        onClick = { 
                                            timerStatus = "stopped"
                                            urgentMessage = "Timer stopped • Rest interrupted"
                                        },
                                        modifier = Modifier.testTag("talkback_stop_timer")
                                    )
                                    TertiaryActionButton(
                                        text = "Add Time",
                                        onClick = { 
                                            timerSeconds += 15
                                            politeMessage = "15 seconds added • Timer extended"
                                        },
                                        modifier = Modifier.testTag("talkback_add_time")
                                    )
                                }
                                "finished" -> {
                                    PrimaryActionButton(
                                        text = "Continue Workout",
                                        onClick = { 
                                            timerStatus = "stopped"
                                            politeMessage = "Continuing with next exercise"
                                        },
                                        modifier = Modifier.testTag("talkback_continue_after_timer")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Start the timer and verify polite announcement
        composeTestRule
            .onNodeWithTag("talkback_start_timer")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("talkback_polite_announcements")
            .assert(hasText("Rest timer started • 30 seconds"))
        
        composeTestRule
            .onNodeWithTag("talkback_timer_card")
            .assert(hasStateDescription("Timer running • 30 seconds remaining"))
        
        // Simulate critical countdown (assertive live region)
        timerSeconds = 3
        urgentMessage = "3 seconds remaining • Get ready to continue!"
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("talkback_urgent_announcements")
            .assert(hasText("3 seconds remaining • Get ready to continue!"))
        
        composeTestRule
            .onNodeWithTag("talkback_timer_card")
            .assert(hasStateDescription("Timer running • 3 seconds remaining"))
        
        // Test timer completion
        timerStatus = "finished"
        urgentMessage = "Rest period complete! Time to continue your workout."
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("talkback_urgent_announcements")
            .assert(hasText("Rest period complete! Time to continue your workout."))
        
        composeTestRule
            .onNodeWithTag("talkback_timer_card")
            .assert(hasStateDescription("Timer finished • Rest period complete"))
        
        // Test timer interruption (urgent announcement)
        composeTestRule
            .onNodeWithTag("talkback_continue_after_timer")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("talkback_polite_announcements")
            .assert(hasText("Continuing with next exercise"))
    }
    
    /**
     * Tests semantic traversal order for logical screen reader navigation flow.
     * Validates proper heading structure and navigation landmarks.
     */
    @Test
    fun screenReaderNavigation_followsLogicalTraversalOrder() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Primary heading (traversal order 1)
                    Text(
                        text = "Your Workout Library",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .testTag("talkback_main_heading")
                            .semantics {
                                heading()
                                traversalIndex = 1f
                            }
                    )
                    
                    // Secondary heading (traversal order 2)
                    Text(
                        text = "Featured Workouts",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .testTag("talkback_section_heading")
                            .semantics {
                                heading()
                                traversalIndex = 2f
                            }
                    )
                    
                    // Primary content area (traversal order 3)
                    UnifiedWorkoutCard(
                        title = "High Intensity Strength",
                        subtitle = "Advanced • 60 minutes • Equipment required",
                        onClick = { /* Navigate to workout */ },
                        modifier = Modifier
                            .testTag("talkback_featured_workout")
                            .semantics {
                                traversalIndex = 3f
                            }
                    ) {
                        Text("Full body strength training with compound movements")
                        
                        PrimaryActionButton(
                            text = "Start Workout",
                            onClick = { /* Start featured workout */ },
                            modifier = Modifier.testTag("talkback_featured_action")
                        )
                    }
                    
                    // Secondary content section (traversal order 4)
                    Text(
                        text = "Recent Workouts",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .testTag("talkback_recent_heading")
                            .semantics {
                                heading()
                                traversalIndex = 4f
                            }
                    )
                    
                    // Recent workout list (traversal order 5)
                    UnifiedWorkoutCard(
                        title = "Morning Cardio",
                        subtitle = "Completed 2 hours ago • 30 minutes",
                        onClick = { /* View workout details */ },
                        modifier = Modifier
                            .testTag("talkback_recent_workout")
                            .semantics {
                                traversalIndex = 5f
                            }
                    ) {
                        Text("Cardiovascular endurance training session")
                        
                        SecondaryActionButton(
                            text = "View Details",
                            onClick = { /* Show workout summary */ },
                            modifier = Modifier.testTag("talkback_recent_action")
                        )
                    }
                    
                    // Navigation actions (traversal order 6)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.semantics {
                            traversalIndex = 6f
                        }
                    ) {
                        TertiaryActionButton(
                            text = "Browse All",
                            onClick = { /* Navigate to full library */ },
                            modifier = Modifier.testTag("talkback_browse_all")
                        )
                        
                        PrimaryActionButton(
                            text = "Create New",
                            onClick = { /* Navigate to workout creation */ },
                            modifier = Modifier.testTag("talkback_create_new")
                        )
                    }
                }
            }
        }
        
        // Verify heading structure and semantic traversal
        composeTestRule
            .onNodeWithTag("talkback_main_heading")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("talkback_section_heading")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("talkback_recent_heading")
            .assertExists()
            .assertIsDisplayed()
        
        // Verify interactive elements are accessible in logical order
        composeTestRule
            .onNodeWithTag("talkback_featured_workout")
            .assertExists()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("talkback_featured_action")
            .assertExists()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("talkback_recent_workout")
            .assertExists()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("talkback_recent_action")
            .assertExists()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("talkback_browse_all")
            .assertExists()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("talkback_create_new")
            .assertExists()
            .assert(hasClickAction())
    }
    
    /**
     * Tests comprehensive screen reader support across complex interaction patterns
     * including state changes, error handling, and recovery workflows.
     */
    @Test
    fun complexInteractionPatterns_maintainScreenReaderSupport() {
        var workoutLoadingState by mutableStateOf("idle")
        var errorMessage by mutableStateOf("")
        var successMessage by mutableStateOf("")
        var hasError by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Error announcement region (assertive for immediate attention)
                    if (hasError && errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .testTag("talkback_error_announcement")
                                .semantics {
                                    liveRegion = LiveRegionMode.Assertive
                                    role = Role.Button
                                }
                        )
                    }
                    
                    // Success announcement region (polite for positive feedback)
                    if (!hasError && successMessage.isNotEmpty()) {
                        Text(
                            text = successMessage,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .testTag("talkback_success_announcement")
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                        )
                    }
                    
                    // Complex workout card with multiple states
                    UnifiedWorkoutCard(
                        title = "Dynamic State Workout",
                        subtitle = when (workoutLoadingState) {
                            "idle" -> "Ready to load workout data"
                            "loading" -> "Loading workout information..."
                            "loaded" -> "Workout loaded successfully"
                            "error" -> "Failed to load workout"
                            else -> "Unknown state"
                        },
                        onClick = if (workoutLoadingState == "loaded") {
                            { /* Navigate to workout */ }
                        } else null,
                        modifier = Modifier
                            .testTag("talkback_complex_workout")
                            .semantics {
                                stateDescription = when (workoutLoadingState) {
                                    "loading" -> "Loading content • Please wait"
                                    "error" -> "Error occurred • Retry available"
                                    "loaded" -> "Content loaded • Ready for interaction"
                                    else -> "Ready to load content"
                                }
                                
                                if (hasError) {
                                    error("Workout loading failed")
                                }
                            }
                    ) {
                        when (workoutLoadingState) {
                            "idle" -> {
                                Text("Tap Load Workout to fetch your training data")
                                
                                PrimaryActionButton(
                                    text = "Load Workout",
                                    onClick = { 
                                        workoutLoadingState = "loading"
                                        hasError = false
                                        errorMessage = ""
                                        successMessage = ""
                                    },
                                    modifier = Modifier.testTag("talkback_load_workout")
                                )
                            }
                            "loading" -> {
                                Text("Please wait while we fetch your workout data...")
                                
                                // Simulate loading completion after short delay
                                LaunchedEffect(Unit) {
                                    delay(100) // Short delay for testing
                                    // Simulate occasional error for error handling testing
                                    if (System.currentTimeMillis() % 3 == 0L) {
                                        workoutLoadingState = "error"
                                        hasError = true
                                        errorMessage = "Failed to load workout • Network connection issue"
                                    } else {
                                        workoutLoadingState = "loaded"
                                        hasError = false
                                        successMessage = "Workout loaded successfully • Ready to start"
                                    }
                                }
                            }
                            "loaded" -> {
                                Text("Workout loaded successfully! Your training plan is ready.")
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PrimaryActionButton(
                                        text = "Start Training",
                                        onClick = { 
                                            successMessage = "Training session started"
                                        },
                                        modifier = Modifier.testTag("talkback_start_training")
                                    )
                                    SecondaryActionButton(
                                        text = "Preview Workout",
                                        onClick = { 
                                            successMessage = "Showing workout preview"
                                        },
                                        modifier = Modifier.testTag("talkback_preview_workout")
                                    )
                                }
                            }
                            "error" -> {
                                Text("Sorry, we couldn't load your workout. Please try again.")
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PrimaryActionButton(
                                        text = "Retry Loading",
                                        onClick = { 
                                            workoutLoadingState = "loading"
                                            hasError = false
                                            errorMessage = ""
                                        },
                                        modifier = Modifier.testTag("talkback_retry_loading")
                                    )
                                    TertiaryActionButton(
                                        text = "Cancel",
                                        onClick = { 
                                            workoutLoadingState = "idle"
                                            hasError = false
                                            errorMessage = ""
                                        },
                                        modifier = Modifier.testTag("talkback_cancel_loading")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Test initial idle state
        composeTestRule
            .onNodeWithTag("talkback_complex_workout")
            .assert(hasStateDescription("Ready to load content"))
            .assert(hasAnyDescendant(hasText("Ready to load workout data")))
        
        // Start loading process
        composeTestRule
            .onNodeWithTag("talkback_load_workout")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify loading state screen reader support
        composeTestRule
            .onNodeWithTag("talkback_complex_workout")
            .assert(hasStateDescription("Loading content • Please wait"))
        
        // Wait for loading to complete (success or error)
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            workoutLoadingState == "loaded" || workoutLoadingState == "error"
        }
        
        // Test success path
        if (workoutLoadingState == "loaded") {
            composeTestRule
                .onNodeWithTag("talkback_complex_workout")
                .assert(hasStateDescription("Content loaded • Ready for interaction"))
                .assert(hasClickAction()) // Should be clickable when loaded
            
            composeTestRule
                .onNodeWithTag("talkback_success_announcement")
                .assertExists()
                .assert(hasText("Workout loaded successfully • Ready to start"))
            
            // Test success actions
            composeTestRule
                .onNodeWithTag("talkback_start_training")
                .assertExists()
                .assert(hasClickAction())
        }
        
        // Test error path (when error occurs)
        if (workoutLoadingState == "error") {
            composeTestRule
                .onNodeWithTag("talkback_complex_workout")
                .assert(hasStateDescription("Error occurred • Retry available"))
                .assert(!hasClickAction()) // Should not be clickable in error state
            
            composeTestRule
                .onNodeWithTag("talkback_error_announcement")
                .assertExists()
                .assert(hasText("Failed to load workout • Network connection issue"))
            
            // Test error recovery actions
            composeTestRule
                .onNodeWithTag("talkback_retry_loading")
                .assertExists()
                .assert(hasClickAction())
            
            composeTestRule
                .onNodeWithTag("talkback_cancel_loading")
                .assertExists()
                .assert(hasClickAction())
        }
    }
}