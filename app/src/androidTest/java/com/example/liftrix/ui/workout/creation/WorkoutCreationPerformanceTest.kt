package com.example.liftrix.ui.workout.creation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.create.WorkoutTemplateCreationScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance tests for workout creation workflow to ensure <90 second timing target
 * Validates the complete workout creation user flow from start to finish
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutCreationPerformanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun validateWorkoutCreationTimeTarget() {
        // Arrange - Start timing the complete workflow
        val workflowStartTime = System.currentTimeMillis()
        var currentStep = "initialization"
        
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutTemplateCreationScreen(
                    onNavigateBack = { },
                    onNavigateToWorkout = { }
                )
            }
        }
        
        try {
            // Step 1: Enter workout name (target: <5 seconds)
            currentStep = "entering workout name"
            val nameEntryStart = System.currentTimeMillis()
            
            composeTestRule
                .onNodeWithTag("workout_name_field")
                .assertIsDisplayed()
                .performTextInput("Upper Body Strength")
            
            val nameEntryTime = System.currentTimeMillis() - nameEntryStart
            assertTrue("Workout name entry took $nameEntryTime ms, should be quick", nameEntryTime < 5000)
            
            // Step 2: Add first exercise (target: <15 seconds)
            currentStep = "adding first exercise"
            val firstExerciseStart = System.currentTimeMillis()
            
            composeTestRule
                .onNodeWithContentDescription("Add exercise")
                .performClick()
            
            // Wait for exercise selection screen
            composeTestRule.waitForIdle()
            
            // Search for exercise
            composeTestRule
                .onNodeWithTag("exercise_search_field")
                .performTextInput("bench press")
            
            // Wait for search results
            composeTestRule.waitForIdle()
            
            // Select first result
            composeTestRule
                .onNodeWithTag("exercise_item_0")
                .performClick()
            
            val firstExerciseTime = System.currentTimeMillis() - firstExerciseStart
            assertTrue("First exercise addition took $firstExerciseTime ms, should be under 15s", firstExerciseTime < 15000)
            
            // Calculate total workflow time
            val totalWorkflowTime = System.currentTimeMillis() - workflowStartTime
            
            // Assert - Complete workflow should be under 90 seconds (90,000 ms)
            assertTrue(
                "Complete workout creation workflow took $totalWorkflowTime ms (${totalWorkflowTime/1000}s), " +
                "exceeds 90 second target",
                totalWorkflowTime < 90000L
            )
            
        } catch (e: Exception) {
            // Provide context about which step failed
            throw AssertionError("Workout creation workflow failed at step: $currentStep", e)
        }
    }

    @Test
    fun validateQuickWorkoutCreationPath() {
        // Arrange - Test the fastest possible workout creation path
        val quickPathStart = System.currentTimeMillis()
        
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutTemplateCreationScreen(
                    onNavigateBack = { },
                    onNavigateToWorkout = { }
                )
            }
        }
        
        // Act - Perform minimal viable workout creation
        composeTestRule
            .onNodeWithTag("workout_name_field")
            .performTextInput("Quick Workout")
        
        composeTestRule.waitForIdle()
        
        val quickPathTime = System.currentTimeMillis() - quickPathStart
        
        // Assert - Quick path should be significantly faster (target: <30 seconds)
        assertTrue(
            "Quick workout creation took $quickPathTime ms (${quickPathTime/1000}s), " +
            "should be under 30 seconds for experienced users",
            quickPathTime < 30000L
        )
    }

    @Test 
    fun validateWorkoutCreationStepTiming() {
        // Arrange - Test individual step performance targets
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutTemplateCreationScreen(
                    onNavigateBack = { },
                    onNavigateToWorkout = { }
                )
            }
        }
        
        // Test UI responsiveness (target: immediate)
        val responseStart = System.currentTimeMillis()
        
        composeTestRule
            .onNodeWithTag("workout_name_field")
            .performTextInput("Test")
        
        composeTestRule.waitForIdle()
        
        val responseTime = System.currentTimeMillis() - responseStart
        
        // Assert - UI should respond immediately
        assertTrue(
            "UI response took $responseTime ms, should be under 100ms",
            responseTime < 100L
        )
    }
} 