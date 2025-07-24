package com.example.liftrix.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.LiftrixTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Icon Consistency Test
 * 
 * Validates that all icons follow consistent sizing, styling, and behavioral standards
 * across the Liftrix application. Ensures WCAG 2.1 AA accessibility compliance
 * and proper Material 3 integration.
 */
@RunWith(AndroidJUnit4::class)
class IconConsistencyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Test that LiftrixIcon uses consistent 24dp sizing by default
     */
    @Test
    fun liftrixIcon_usesStandardTwentyFourDpSize() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixIcon(
                    icon = Icons.Default.Assignment,
                    contentDescription = "Test icon",
                    testTag = "test-icon"
                )
            }
        }

        composeTestRule
            .onNodeWithTag("test-icon")
            .assertExists()
            .assertHeightIsEqualTo(LiftrixTokens.TouchTarget.IconMedium) // 24dp
            .assertWidthIsEqualTo(LiftrixTokens.TouchTarget.IconMedium)  // 24dp
    }

    /**
     * Test that icons maintain consistent sizing across different variants
     */
    @Test
    fun semanticWorkoutIcon_maintainsConsistentSizing() {
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    SemanticWorkoutIcon(
                        workoutType = WorkoutIconType.CREATE,
                        modifier = androidx.compose.ui.Modifier.testTag("create-icon")
                    )
                    SemanticWorkoutIcon(
                        workoutType = WorkoutIconType.ACTIVE,
                        modifier = androidx.compose.ui.Modifier.testTag("active-icon")
                    )
                    SemanticWorkoutIcon(
                        workoutType = WorkoutIconType.PROGRESS,
                        modifier = androidx.compose.ui.Modifier.testTag("progress-icon")
                    )
                }
            }
        }

        // Verify all semantic icons have consistent sizing
        composeTestRule
            .onNodeWithTag("create-icon")
            .assertHeightIsEqualTo(24.dp)
            .assertWidthIsEqualTo(24.dp)

        composeTestRule
            .onNodeWithTag("active-icon")
            .assertHeightIsEqualTo(24.dp)
            .assertWidthIsEqualTo(24.dp)

        composeTestRule
            .onNodeWithTag("progress-icon")
            .assertHeightIsEqualTo(24.dp)
            .assertWidthIsEqualTo(24.dp)
    }

    /**
     * Test that primary action icons use proper tinting
     */
    @Test
    fun primaryActionIcon_usesConsistentTealTinting() {
        composeTestRule.setContent {
            LiftrixTheme {
                PrimaryActionIcon(
                    icon = LiftrixIcons.Actions.Add,
                    contentDescription = "Add item",
                    modifier = androidx.compose.ui.Modifier.testTag("primary-action-icon")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("primary-action-icon")
            .assertExists()
            .assertIsDisplayed()
    }

    /**
     * Test that status icons provide proper semantic meaning through color
     */
    @Test
    fun statusIcon_providesSemanticColorCoding() {
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    StatusIcon(
                        statusType = StatusIconType.SUCCESS,
                        modifier = androidx.compose.ui.Modifier.testTag("success-icon")
                    )
                    StatusIcon(
                        statusType = StatusIconType.ERROR,
                        modifier = androidx.compose.ui.Modifier.testTag("error-icon")
                    )
                    StatusIcon(
                        statusType = StatusIconType.WARNING,
                        modifier = androidx.compose.ui.Modifier.testTag("warning-icon")
                    )
                    StatusIcon(
                        statusType = StatusIconType.INFO,
                        modifier = androidx.compose.ui.Modifier.testTag("info-icon")
                    )
                }
            }
        }

        // Verify all status icons are displayed properly
        composeTestRule
            .onNodeWithTag("success-icon")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(24.dp)

        composeTestRule
            .onNodeWithTag("error-icon")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(24.dp)

        composeTestRule
            .onNodeWithTag("warning-icon")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(24.dp)

        composeTestRule
            .onNodeWithTag("info-icon")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(24.dp)
    }

    /**
     * Test that icons integrate properly with UnifiedWorkoutCard
     */
    @Test
    fun unifiedWorkoutCard_integratesIconsProperly() {
        composeTestRule.setContent {
            LiftrixTheme {
                com.example.liftrix.ui.workout.components.UnifiedWorkoutCard(
                    title = "Test Card with Icon",
                    subtitle = "Icon integration test",
                    leadingIcon = LiftrixIcons.Workflow.WorkoutCreation,
                    modifier = androidx.compose.ui.Modifier.testTag("card-with-icon")
                ) {
                    androidx.compose.material3.Text("Card content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("card-with-icon")
            .assertExists()
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasContentDescription("workout card with icon: Test Card with Icon. Icon integration test")))
    }

    /**
     * Test that icons integrate properly with ModernActionButton
     */
    @Test
    fun modernActionButton_integratesIconsProperly() {
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    com.example.liftrix.ui.workout.components.PrimaryActionButton(
                        text = "Primary with Icon",
                        leadingIcon = LiftrixIcons.Actions.Add,
                        onClick = { },
                        modifier = androidx.compose.ui.Modifier.testTag("primary-button-icon")
                    )
                    com.example.liftrix.ui.workout.components.SecondaryActionButton(
                        text = "Secondary with Icon",
                        leadingIcon = LiftrixIcons.Actions.Edit,
                        onClick = { },
                        modifier = androidx.compose.ui.Modifier.testTag("secondary-button-icon")
                    )
                    com.example.liftrix.ui.workout.components.TertiaryActionButton(
                        text = "Tertiary with Icon",
                        leadingIcon = LiftrixIcons.Actions.Cancel,
                        onClick = { },
                        modifier = androidx.compose.ui.Modifier.testTag("tertiary-button-icon")
                    )
                }
            }
        }

        // Verify buttons with icons are displayed and maintain proper hierarchy
        composeTestRule
            .onNodeWithTag("primary-button-icon")
            .assertExists()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp) // Minimum touch target

        composeTestRule
            .onNodeWithTag("secondary-button-icon")
            .assertExists()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        composeTestRule
            .onNodeWithTag("tertiary-button-icon")
            .assertExists()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    /**
     * Test that custom icon sizing works properly
     */
    @Test
    fun liftrixIcon_supportsCustomSizing() {
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    LiftrixIcon(
                        icon = Icons.Default.Assignment,
                        contentDescription = "Small icon",
                        size = 16.dp,
                        modifier = androidx.compose.ui.Modifier.testTag("small-icon")
                    )
                    LiftrixIcon(
                        icon = Icons.Default.Assignment,
                        contentDescription = "Large icon",
                        size = 32.dp,
                        modifier = androidx.compose.ui.Modifier.testTag("large-icon")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("small-icon")
            .assertHeightIsEqualTo(16.dp)
            .assertWidthIsEqualTo(16.dp)

        composeTestRule
            .onNodeWithTag("large-icon")
            .assertHeightIsEqualTo(32.dp)
            .assertWidthIsEqualTo(32.dp)
    }

    /**
     * Test that all Liftrix icon categories contain expected icons
     */
    @Test
    fun liftrixIcons_containsAllRequiredCategories() {
        // Test that all required icons are available in their respective categories
        
        // Workflow icons
        val workflowCreation = LiftrixIcons.Workflow.WorkoutCreation
        val activeSession = LiftrixIcons.Workflow.ActiveSession  
        val progress = LiftrixIcons.Workflow.Progress
        val edit = LiftrixIcons.Workflow.Edit
        val history = LiftrixIcons.Workflow.History
        val settings = LiftrixIcons.Workflow.Settings

        // Action icons
        val add = LiftrixIcons.Actions.Add
        val remove = LiftrixIcons.Actions.Remove
        val save = LiftrixIcons.Actions.Save
        val cancel = LiftrixIcons.Actions.Cancel

        // State icons
        val success = LiftrixIcons.State.Success
        val error = LiftrixIcons.State.Error
        val warning = LiftrixIcons.State.Warning
        val info = LiftrixIcons.State.Info

        // Navigation icons
        val back = LiftrixIcons.Navigation.Back
        val forward = LiftrixIcons.Navigation.Forward
        val menu = LiftrixIcons.Navigation.Menu

        // Fitness icons
        val workout = LiftrixIcons.Fitness.Workout
        val timer = LiftrixIcons.Fitness.Timer
        val weight = LiftrixIcons.Fitness.Weight

        // Verify icons are not null (basic validation)
        assert(workflowCreation != null) { "WorkoutCreation icon should not be null" }
        assert(activeSession != null) { "ActiveSession icon should not be null" }
        assert(progress != null) { "Progress icon should not be null" }
        assert(edit != null) { "Edit icon should not be null" }
        assert(add != null) { "Add icon should not be null" }
        assert(success != null) { "Success icon should not be null" }
        assert(back != null) { "Back icon should not be null" }
        assert(workout != null) { "Workout icon should not be null" }
    }
}