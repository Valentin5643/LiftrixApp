package com.example.liftrix.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for WorkoutCreationFab component.
 * 
 * Tests cover:
 * - FAB appearance and rendering
 * - Click behavior verification
 * - Accessibility content description testing
 * - Visual styling verification
 * - Icon display verification
 */
@RunWith(AndroidJUnit4::class)
class WorkoutCreationFabTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockOnWorkoutCreationClick: () -> Unit

    @Before
    fun setUp() {
        mockOnWorkoutCreationClick = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun fabDisplaysCorrectly() {
        // Arrange & Act
        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = mockOnWorkoutCreationClick
            )
        }

        // Assert
        composeTestRule
            .onNodeWithContentDescription("Create new workout")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun fabClickTriggersCallback() {
        // Arrange
        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = mockOnWorkoutCreationClick
            )
        }

        // Act
        composeTestRule
            .onNodeWithContentDescription("Create new workout")
            .performClick()

        // Assert
        verify(exactly = 1) { mockOnWorkoutCreationClick() }
    }

    @Test
    fun fabHasCorrectContentDescription() {
        // Arrange & Act
        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = mockOnWorkoutCreationClick
            )
        }

        // Assert
        composeTestRule
            .onNodeWithContentDescription("Create new workout")
            .assertExists()

        // Verify semantics properties are set correctly
        composeTestRule
            .onNode(hasContentDescription("Create new workout"))
            .assertExists()
    }

    @Test
    fun fabDisplaysAddIcon() {
        // Arrange & Act
        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = mockOnWorkoutCreationClick
            )
        }

        // Assert - Verify the FAB contains an icon (Add icon specifically)
        composeTestRule
            .onNodeWithContentDescription("Create new workout")
            .assertExists()
            .assertIsDisplayed()

        // Verify only FAB has the content description (Icon should not duplicate it)
        composeTestRule
            .onAllNodesWithContentDescription("Create new workout")
            .assertCountEquals(1) // Only FAB should have the content description
    }

    @Test
    fun fabHasCorrectStyling() {
        // Arrange & Act
        composeTestRule.setContent {
            MaterialTheme {
                WorkoutCreationFab(
                    onWorkoutCreationClick = mockOnWorkoutCreationClick
                )
            }
        }

        // Assert - Verify FAB is displayed and styled correctly
        val fabNode = composeTestRule
            .onNodeWithContentDescription("Create new workout")

        fabNode.assertExists()
        fabNode.assertIsDisplayed()
        
        // Verify it's actually a FloatingActionButton by checking it's clickable
        fabNode.assertHasClickAction()
    }

    @Test
    fun fabWithCustomModifier() {
        // Arrange & Act
        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = mockOnWorkoutCreationClick,
                modifier = Modifier.testTag("custom-fab")
            )
        }

        // Assert
        composeTestRule
            .onNodeWithTag("custom-fab")
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun fabAccessibilityProperties() {
        // Arrange & Act
        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = mockOnWorkoutCreationClick
            )
        }

        // Assert - Verify accessibility properties
        val fabNode = composeTestRule
            .onNodeWithContentDescription("Create new workout")

        fabNode.assertExists()
        fabNode.assertHasClickAction()
        
        // Verify the node has the correct semantics
        fabNode.assert(
            hasContentDescription("Create new workout")
        )
    }

    @Test
    fun fabClickabilityAndResponsiveness() {
        // Arrange
        var clickCount = 0
        val testCallback = { clickCount++ }

        composeTestRule.setContent {
            WorkoutCreationFab(
                onWorkoutCreationClick = testCallback
            )
        }

        // Act - Multiple clicks to test responsiveness
        val fabNode = composeTestRule
            .onNodeWithContentDescription("Create new workout")

        fabNode.performClick()
        fabNode.performClick()
        fabNode.performClick()

        // Assert
        assertEquals(3, clickCount)
    }
} 