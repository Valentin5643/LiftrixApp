package com.example.liftrix.ui.workout.creation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.MuscleGroup
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.components.ExerciseCard
import com.example.liftrix.ui.workout.creation.components.SetInputRow
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance validation tests for UnifiedWorkoutCreationScreen
 * Validates screen load time <300ms, set input response <100ms, and component optimization
 * Following Android testing guidelines for performance measurement
 */
@RunWith(AndroidJUnit4::class)
class WorkoutCreationPerformanceTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    companion object {
        private const val SCREEN_LOAD_THRESHOLD_MS = 300L
        private const val SET_INPUT_RESPONSE_THRESHOLD_MS = 100L
        private const val ANIMATION_FRAME_THRESHOLD_MS = 16L // 60fps = ~16ms per frame
        private const val MEMORY_THRESHOLD_MB = 50L
        
        // Test data for performance testing
        private val testExercise = ExerciseLibrary(
            id = "test-exercise-1",
            name = "Bench Press",
            primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            equipment = Equipment.BARBELL,
            category = ExerciseCategory.STRENGTH,
            instructions = "Perform bench press with proper form",
            tips = "Keep your back flat and core engaged"
        )
    }
    
    /**
     * Validates screen load time is consistently under 300ms
     * Critical for user experience during workout creation
     */
    @Test
    fun test_screenLoadTime_under300ms() {
        val loadTimes = mutableListOf<Long>()
        
        // Perform multiple measurements for consistency
        repeat(5) {
            val startTime = System.currentTimeMillis()
            
            composeTestRule.setContent {
                LiftrixTheme {
                    UnifiedWorkoutCreationScreen(
                        onNavigateBack = {},
                        onWorkoutCreated = {}
                    )
                }
            }
            
            // Wait for screen to be fully rendered
            composeTestRule.onNodeWithText("Create Workout").assertIsDisplayed()
            composeTestRule.onNodeWithText("Save Workout").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
            
            val loadTime = System.currentTimeMillis() - startTime
            loadTimes.add(loadTime)
            
            // Clear content between measurements
            composeTestRule.setContent { }
        }
        
        val averageLoadTime = loadTimes.average()
        val maxLoadTime = loadTimes.maxOrNull() ?: 0L
        
        assertThat(averageLoadTime).isLessThan(SCREEN_LOAD_THRESHOLD_MS.toDouble())
        assertThat(maxLoadTime).isLessThan(SCREEN_LOAD_THRESHOLD_MS * 1.5) // Allow 50% tolerance for max
        
        // Log performance metrics for analysis
        println("Screen Load Performance:")
        println("Average: ${averageLoadTime}ms")
        println("Max: ${maxLoadTime}ms")
        println("All measurements: ${loadTimes.joinToString(", ")}ms")
    }
    
    /**
     * Validates set input response time is consistently under 100ms
     * Critical for responsive workout logging during exercise
     */
    @Test
    fun test_setInputResponse_under100ms() {
        val responseTimes = mutableListOf<Long>()
        val selectedExercise = SelectedExercise.fromLibraryExercise(testExercise, 0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput.empty(supportsWeight = true),
                    setNumber = 1,
                    onSetChange = { 
                        // Measure response time when set data changes
                        val responseTime = System.currentTimeMillis()
                        responseTimes.add(responseTime)
                    },
                    onRemoveSet = {},
                    enabled = true
                )
            }
        }
        
        // Test multiple input scenarios
        val testInputs = listOf("10", "12", "15", "8", "20")
        
        testInputs.forEach { repsInput ->
            val startTime = System.currentTimeMillis()
            
            composeTestRule.onNodeWithContentDescription("Repetitions input field")
                .performTextInput(repsInput)
            
            // Wait for UI to respond
            composeTestRule.waitForIdle()
            
            val responseTime = System.currentTimeMillis() - startTime
            responseTimes.add(responseTime)
        }
        
        val averageResponseTime = responseTimes.average()
        val maxResponseTime = responseTimes.maxOrNull() ?: 0L
        
        assertThat(averageResponseTime).isLessThan(SET_INPUT_RESPONSE_THRESHOLD_MS.toDouble())
        assertThat(maxResponseTime).isLessThan(SET_INPUT_RESPONSE_THRESHOLD_MS * 2) // Allow 100% tolerance for max
        
        // Log performance metrics for analysis
        println("Set Input Response Performance:")
        println("Average: ${averageResponseTime}ms")
        println("Max: ${maxResponseTime}ms")
        println("All measurements: ${responseTimes.joinToString(", ")}ms")
    }
    
    /**
     * Validates exercise card expansion animation performs well
     * Ensures smooth 60fps animation without UI blocking
     */
    @Test
    fun test_exerciseCardExpansion_performsWell() {
        val animationTimes = mutableListOf<Long>()
        val selectedExercise = SelectedExercise.fromLibraryExercise(testExercise, 0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetUpdate = { _, _ -> },
                    onAddSet = {},
                    onRemoveSet = {},
                    onRemoveExercise = {},
                    isExpanded = false,
                    onExpandedChange = { },
                    enabled = true
                )
            }
        }
        
        // Test expansion and collapse animations multiple times
        repeat(10) {
            val startTime = System.currentTimeMillis()
            
            // Trigger expansion
            composeTestRule.onNodeWithContentDescription("Expand ${testExercise.name} exercise details")
                ?.performClick()
            
            // Wait for animation to complete
            composeTestRule.waitForIdle()
            
            val expansionTime = System.currentTimeMillis() - startTime
            animationTimes.add(expansionTime)
            
            // Trigger collapse
            val collapseStartTime = System.currentTimeMillis()
            
            composeTestRule.onNodeWithContentDescription("Collapse ${testExercise.name} exercise details")
                ?.performClick()
            
            composeTestRule.waitForIdle()
            
            val collapseTime = System.currentTimeMillis() - collapseStartTime
            animationTimes.add(collapseTime)
        }
        
        val averageAnimationTime = animationTimes.average()
        val maxAnimationTime = animationTimes.maxOrNull() ?: 0L
        
        // Animation should complete within reasonable time (not exceed multiple frame durations)
        val maxReasonableAnimationTime = ANIMATION_FRAME_THRESHOLD_MS * 20 // ~320ms for smooth animation
        
        assertThat(averageAnimationTime).isLessThan(maxReasonableAnimationTime.toDouble())
        assertThat(maxAnimationTime).isLessThan(maxReasonableAnimationTime * 2)
        
        // Log performance metrics for analysis
        println("Exercise Card Animation Performance:")
        println("Average: ${averageAnimationTime}ms")
        println("Max: ${maxAnimationTime}ms")
        println("Frame budget (16ms): ${animationTimes.count { it <= ANIMATION_FRAME_THRESHOLD_MS }} of ${animationTimes.size}")
    }
    
    /**
     * Validates memory usage remains within acceptable limits
     * Prevents memory leaks and excessive resource consumption
     */
    @Test
    fun test_memoryUsage_withinLimits() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Create multiple exercise cards to simulate realistic workout
        val testExercises = (1..10).map { index ->
            SelectedExercise.fromLibraryExercise(
                exercise = testExercise.copy(
                    id = "test-exercise-$index",
                    name = "Exercise $index"
                ),
                orderIndex = index
            )
        }
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }
        
        // Wait for screen to fully load
        composeTestRule.onNodeWithText("Create Workout").assertIsDisplayed()
        composeTestRule.waitForIdle()
        
        // Force garbage collection for accurate measurement
        System.gc()
        Thread.sleep(100)
        
        val afterLoadMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsedMB = (afterLoadMemory - initialMemory) / (1024 * 1024)
        
        assertThat(memoryUsedMB).isLessThan(MEMORY_THRESHOLD_MB)
        
        // Test memory stability with user interactions
        repeat(5) {
            composeTestRule.onNodeWithText("Add Exercise").performClick()
            composeTestRule.waitForIdle()
            
            // Close modal (if opened)
            try {
                composeTestRule.onNodeWithContentDescription("Close exercise selector")
                    ?.performClick()
            } catch (e: Exception) {
                // Modal might not be open, continue
            }
            composeTestRule.waitForIdle()
        }
        
        System.gc()
        Thread.sleep(100)
        
        val afterInteractionMemory = runtime.totalMemory() - runtime.freeMemory()
        val totalMemoryUsedMB = (afterInteractionMemory - initialMemory) / (1024 * 1024)
        
        assertThat(totalMemoryUsedMB).isLessThan(MEMORY_THRESHOLD_MB * 1.5) // Allow 50% increase for interactions
        
        // Log memory usage for analysis
        println("Memory Usage Performance:")
        println("Initial load: ${memoryUsedMB}MB")
        println("After interactions: ${totalMemoryUsedMB}MB")
        println("Memory threshold: ${MEMORY_THRESHOLD_MB}MB")
    }
} 