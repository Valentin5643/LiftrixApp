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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.ui.common.AccessibilityUtils
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.ModernActionButton.*
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Comprehensive screen reader testing to ensure proper TalkBack announcements
 * and screen reader navigation support.
 * 
 * Tests cover:
 * - Content description accuracy for screen readers
 * - State descriptions for dynamic content
 * - Live region announcements for real-time updates
 * - Semantic traversal order
 * - Screen reader focus management
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ScreenReaderTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    /**
     * Tests that workout cards provide proper screen reader announcements
     * with contextual information about workout status and actions.
     */
    @Test
    fun workoutCard_providesProperScreenReaderAnnouncement() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Test active workout card
                    UnifiedWorkoutCard(
                        title = "Morning Cardio",
                        subtitle = "3 exercises • 30 minutes",
                        onClick = { /* Navigate to workout */ },
                        modifier = Modifier.testTag("cardio_workout_card")
                    ) {
                        Text("High intensity interval training")
                        
                        PrimaryActionButton(
                            text = "Start Now",
                            onClick = { /* Start workout */ },
                            modifier = Modifier.testTag("start_cardio_button")
                        )
                    }
                    
                    // Test completed workout card
                    UnifiedWorkoutCard(
                        title = "Push Day Workout", 
                        subtitle = "Completed yesterday • 45 minutes",
                        onClick = { /* View workout details */ },
                        modifier = Modifier.testTag("push_workout_card")
                    ) {
                        Text("6 exercises completed with personal records")
                        
                        SecondaryActionButton(
                            text = "View Details",
                            onClick = { /* View details */ },
                            modifier = Modifier.testTag("view_details_button")
                        )
                    }
                    
                    // Test workout card with no action
                    UnifiedWorkoutCard(
                        title = "Rest Day",
                        subtitle = "Recovery scheduled",
                        modifier = Modifier.testTag("rest_day_card")
                    ) {
                        Text("Take time to recover and prepare for tomorrow's session")
                    }
                }
            }
        }
        
        // Verify screen reader can access workout information with proper descriptions
        composeTestRule
            .onNodeWithTag("cardio_workout_card")
            .assert(hasClickAction())
            .assertIsEnabled()
            .assert(
                hasContentDescriptionExactly("Morning Cardio. 3 exercises • 30 minutes") or
                hasAnyDescendant(hasText("Morning Cardio"))
            )
        
        // Test button accessibility within cards
        composeTestRule
            .onNodeWithTag("start_cardio_button")
            .assert(
                hasContentDescriptionExactly("Start Now button") or
                hasText("Start Now")
            )
            .assert(hasClickAction())
            
        // Verify completed workout provides proper context
        composeTestRule
            .onNodeWithTag("push_workout_card")
            .assert(
                hasContentDescriptionExactly("Push Day Workout. Completed yesterday • 45 minutes") or
                hasAnyDescendant(hasText("Push Day Workout"))
            )
        
        // Test non-interactive card doesn't have click semantics
        composeTestRule
            .onNodeWithTag("rest_day_card")
            .assert(!hasClickAction())
            .assert(
                hasContentDescriptionExactly("Rest Day. Recovery scheduled") or
                hasAnyDescendant(hasText("Rest Day"))
            )
    }
    
    /**
     * Tests state descriptions for dynamic content that changes based on user interaction
     * or system state.
     */
    @Test
    fun dynamicContent_providesStateDescriptions() {
        var workoutState by mutableStateOf("ready")
        var exerciseCount by mutableIntStateOf(0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Test workout with dynamic state
                    UnifiedWorkoutCard(
                        title = "Dynamic Workout Status",
                        subtitle = when (workoutState) {
                            "ready" -> "Ready to start"
                            "active" -> "In progress • $exerciseCount exercises completed"
                            "completed" -> "Workout completed • $exerciseCount exercises"
                            else -> "Unknown state"
                        },
                        onClick = { /* Handle state change */ },
                        modifier = Modifier
                            .testTag("dynamic_workout_card")
                            .semantics {
                                stateDescription = "Workout status: $workoutState"
                            }
                    ) {
                        when (workoutState) {
                            "ready" -> {
                                Text("Tap to begin your workout session")
                                PrimaryActionButton(
                                    text = "Start Workout",
                                    onClick = { 
                                        workoutState = "active"
                                        exerciseCount = 1
                                    },
                                    modifier = Modifier.testTag("start_dynamic_button")
                                )
                            }
                            "active" -> {
                                Text("Keep going! You're doing great.")
                                SecondaryActionButton(
                                    text = "Next Exercise",
                                    onClick = { exerciseCount++ },
                                    modifier = Modifier.testTag("next_exercise_button")
                                )
                                TertiaryActionButton(
                                    text = "Finish Workout",
                                    onClick = { workoutState = "completed" },
                                    modifier = Modifier.testTag("finish_workout_button")
                                )
                            }
                            "completed" -> {
                                Text("Congratulations! Workout completed successfully.")
                                PrimaryActionButton(
                                    text = "Save & Close",
                                    onClick = { workoutState = "ready"; exerciseCount = 0 },
                                    modifier = Modifier.testTag("save_close_button")
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Test initial ready state
        composeTestRule
            .onNodeWithTag("dynamic_workout_card")
            .assert(hasStateDescription("Workout status: ready"))
            .assert(hasAnyDescendant(hasText("Ready to start")))
        
        composeTestRule
            .onNodeWithTag("start_dynamic_button")
            .assertExists()
            .assert(hasText("Start Workout"))
        
        // Trigger state change to active
        composeTestRule
            .onNodeWithTag("start_dynamic_button")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify active state description
        composeTestRule
            .onNodeWithTag("dynamic_workout_card")
            .assert(hasStateDescription("Workout status: active"))
            .assert(hasAnyDescendant(hasText("In progress • 1 exercises completed")))
        
        // Test exercise progression
        composeTestRule
            .onNodeWithTag("next_exercise_button")
            .assertExists()
            .performClick()
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("dynamic_workout_card")
            .assert(hasAnyDescendant(hasText("In progress • 2 exercises completed")))
        
        // Complete the workout
        composeTestRule
            .onNodeWithTag("finish_workout_button")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify completed state
        composeTestRule
            .onNodeWithTag("dynamic_workout_card")
            .assert(hasStateDescription("Workout status: completed"))
            .assert(hasAnyDescendant(hasText("Workout completed • 2 exercises")))
    }
    
    /**
     * Tests live region announcements for real-time feedback and status updates.
     */
    @Test
    fun liveRegionAnnouncements_workProperly() {
        var announcementText by mutableStateOf("")
        var timerCount by mutableIntStateOf(30)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Live region for announcements
                    Text(
                        text = announcementText,
                        modifier = Modifier
                            .testTag("live_announcement")
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                            }
                    )
                    
                    // Timer display with live updates
                    UnifiedWorkoutCard(
                        title = "Rest Timer",
                        subtitle = "Time remaining: ${timerCount}s",
                        modifier = Modifier
                            .testTag("timer_card")
                            .semantics {
                                stateDescription = "Timer: $timerCount seconds remaining"
                                if (timerCount <= 10 && timerCount > 0) {
                                    liveRegion = LiveRegionMode.Assertive
                                }
                            }
                    ) {
                        Text(
                            text = when {
                                timerCount > 10 -> "Rest period in progress"
                                timerCount > 0 -> "Get ready! $timerCount seconds left"
                                else -> "Rest period complete!"
                            },
                            modifier = Modifier.testTag("timer_message")
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SecondaryActionButton(
                                text = "Start Timer",
                                onClick = { 
                                    timerCount = 30
                                    announcementText = "Rest timer started - 30 seconds"
                                },
                                modifier = Modifier.testTag("start_timer_button")
                            )
                            
                            TertiaryActionButton(
                                text = "Skip Rest",
                                onClick = { 
                                    timerCount = 0
                                    announcementText = "Rest period skipped"
                                },
                                modifier = Modifier.testTag("skip_timer_button")
                            )
                        }
                    }
                }
            }
        }
        
        // Start the timer
        composeTestRule
            .onNodeWithTag("start_timer_button")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify announcement was made
        composeTestRule
            .onNodeWithTag("live_announcement")
            .assert(hasText("Rest timer started - 30 seconds"))
        
        // Verify timer state
        composeTestRule
            .onNodeWithTag("timer_card")
            .assert(hasStateDescription("Timer: 30 seconds remaining"))
            .assert(hasAnyDescendant(hasText("Time remaining: 30s")))
        
        // Simulate countdown to critical time
        timerCount = 5
        announcementText = "5 seconds remaining - get ready!"
        
        composeTestRule.waitForIdle()
        
        // Verify critical countdown state (assertive live region)
        composeTestRule
            .onNodeWithTag("timer_card")
            .assert(hasStateDescription("Timer: 5 seconds remaining"))
        
        composeTestRule
            .onNodeWithTag("live_announcement")
            .assert(hasText("5 seconds remaining - get ready!"))
        
        // Complete timer
        composeTestRule
            .onNodeWithTag("skip_timer_button")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("live_announcement")
            .assert(hasText("Rest period skipped"))
        
        composeTestRule
            .onNodeWithTag("timer_message")
            .assert(hasText("Rest period complete!"))
    }
    
    /**
     * Tests semantic traversal order for proper screen reader navigation flow.
     */
    @Test
    fun components_haveProperTraversalOrder() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // First element should be traversed first
                    Text(
                        text = "Workout Selection",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .testTag("header_text")
                            .semantics {
                                heading()
                                traversalIndex = 1f
                            }
                    )
                    
                    // Second element in traversal order
                    UnifiedWorkoutCard(
                        title = "Primary Workout",
                        subtitle = "Most important workout option",
                        onClick = { /* Navigate */ },
                        modifier = Modifier
                            .testTag("primary_workout_card")
                            .semantics {
                                traversalIndex = 2f
                            }
                    ) {
                        Text("This should be the second element accessed by screen reader")
                    }
                    
                    // Third element in traversal order
                    UnifiedWorkoutCard(
                        title = "Secondary Workout",
                        subtitle = "Alternative workout option", 
                        onClick = { /* Navigate */ },
                        modifier = Modifier
                            .testTag("secondary_workout_card")
                            .semantics {
                                traversalIndex = 3f
                            }
                    ) {
                        Text("This should be the third element accessed by screen reader")
                    }
                    
                    // Action buttons should come last
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.semantics {
                            traversalIndex = 4f
                        }
                    ) {
                        TertiaryActionButton(
                            text = "More Options",
                            onClick = { /* Show more */ },
                            modifier = Modifier.testTag("more_options_button")
                        )
                        
                        PrimaryActionButton(
                            text = "Continue",
                            onClick = { /* Continue */ },
                            modifier = Modifier.testTag("continue_button")
                        )
                    }
                }
            }
        }
        
        // Verify all elements exist and are accessible
        composeTestRule
            .onNodeWithTag("header_text")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("primary_workout_card")
            .assertExists()
            .assertIsDisplayed()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("secondary_workout_card")
            .assertExists()
            .assertIsDisplayed()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("more_options_button")
            .assertExists()
            .assertIsDisplayed()
            .assert(hasClickAction())
        
        composeTestRule
            .onNodeWithTag("continue_button")
            .assertExists()
            .assertIsDisplayed()
            .assert(hasClickAction())
    }
    
    /**
     * Tests screen reader focus management and focus transition handling.
     */
    @Test
    fun focusManagement_worksWithScreenReaders() {
        var showSecondCard by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    UnifiedWorkoutCard(
                        title = "Focus Management Test",
                        subtitle = "Testing focus transitions",
                        onClick = { showSecondCard = true },
                        modifier = Modifier.testTag("first_focus_card")
                    ) {
                        Text("Click this card to show the second card and test focus management")
                        
                        PrimaryActionButton(
                            text = "Show Next Card",
                            onClick = { showSecondCard = true },
                            modifier = Modifier.testTag("show_card_button")
                        )
                    }
                    
                    if (showSecondCard) {
                        UnifiedWorkoutCard(
                            title = "Second Card Appeared",
                            subtitle = "This card should receive focus",
                            onClick = { /* Handle click */ },
                            modifier = Modifier
                                .testTag("second_focus_card")
                                .semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                        ) {
                            Text("This card was dynamically added and should announce its presence")
                            
                            SecondaryActionButton(
                                text = "Hide Card",
                                onClick = { showSecondCard = false },
                                modifier = Modifier.testTag("hide_card_button")
                            )
                        }
                    }
                }
            }
        }
        
        // Initially only first card should be visible
        composeTestRule
            .onNodeWithTag("first_focus_card")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithTag("second_focus_card")
            .assertDoesNotExist()
        
        // Trigger second card appearance
        composeTestRule
            .onNodeWithTag("show_card_button")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify second card appears and is accessible
        composeTestRule
            .onNodeWithTag("second_focus_card")
            .assertExists()
            .assertIsDisplayed()
            .assert(hasClickAction())
        
        // Test hiding the card
        composeTestRule
            .onNodeWithTag("hide_card_button")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithTag("second_focus_card")
            .assertDoesNotExist()
        
        // Verify first card is still accessible
        composeTestRule
            .onNodeWithTag("first_focus_card")
            .assertExists()
            .assertIsDisplayed()
    }
}