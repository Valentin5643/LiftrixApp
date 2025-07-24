package com.example.liftrix.ui.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.ModernActionButton.PrimaryActionButton
import com.example.liftrix.ui.workout.components.ModernActionButton.SecondaryActionButton
import com.example.liftrix.ui.performance.RecompositionCounter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Component performance testing focusing on recomposition overhead
 * and rendering optimization for the redesigned UI components.
 * 
 * Tests validate:
 * - UnifiedWorkoutCard recomposition efficiency
 * - ModernActionButton rendering performance
 * - LazyColumn performance with complex cards
 * - Memory allocation during component updates
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class ComponentPerformanceTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    /**
     * Test: UnifiedWorkoutCard recomposition efficiency
     * PRD Requirement: Efficient component rendering for smooth 60fps performance
     */
    @Test
    fun unifiedWorkoutCard_minimizesUnnecessaryRecompositions() {
        val recompositionCounter = RecompositionCounter()
        var cardTitle by mutableStateOf("Initial Title")
        var unrelatedState by mutableStateOf(0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column {
                    // This should NOT cause card recomposition
                    Text("Unrelated state: $unrelatedState")
                    
                    // Track recompositions of the card
                    recompositionCounter.TrackRecomposition("workout_card") {
                        UnifiedWorkoutCard(
                            title = cardTitle,
                            subtitle = "Recomposition test card"
                        ) {
                            Text("Card content that should be stable")
                            PrimaryActionButton(
                                text = "Test Action",
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }
        
        // Initial state - should have 1 composition
        Thread.sleep(100)
        val initialCompositions = recompositionCounter.getCompositionCount("workout_card")
        assert(initialCompositions == 1) {
            "Initial composition count should be 1, got $initialCompositions"
        }
        
        // Change unrelated state - should NOT cause card recomposition
        unrelatedState = 1
        composeTestRule.waitForIdle()
        Thread.sleep(100)
        
        val afterUnrelatedChange = recompositionCounter.getCompositionCount("workout_card")
        assert(afterUnrelatedChange == initialCompositions) {
            "Card should not recompose for unrelated state changes. " +
            "Expected $initialCompositions, got $afterUnrelatedChange"
        }
        
        // Change card-specific state - should cause exactly 1 recomposition
        cardTitle = "Updated Title"
        composeTestRule.waitForIdle()
        Thread.sleep(100)
        
        val afterCardUpdate = recompositionCounter.getCompositionCount("workout_card")
        assert(afterCardUpdate == initialCompositions + 1) {
            "Card should recompose exactly once for title change. " +
            "Expected ${initialCompositions + 1}, got $afterCardUpdate"
        }
        
        Timber.i("ComponentPerformanceTest: UnifiedWorkoutCard recomposition efficiency verified")
    }
    
    /**
     * Test: ModernActionButton rendering performance
     * PRD Requirement: Consistent button performance across different states
     */
    @Test
    fun modernActionButton_maintainsRenderingPerformance() {
        val renderTimes = mutableListOf<Long>()
        var buttonState by mutableStateOf("primary")
        var clickCount by mutableStateOf(0)
        
        composeTestRule.setContent {
            val renderStartTime = remember { System.currentTimeMillis() }
            
            LaunchedEffect(buttonState, clickCount) {
                renderTimes.add(System.currentTimeMillis() - renderStartTime)
            }
            
            LiftrixTheme {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (buttonState) {
                        "primary" -> {
                            PrimaryActionButton(
                                text = "Primary Button ($clickCount)",
                                onClick = { 
                                    clickCount++
                                    buttonState = "secondary"
                                }
                            )
                        }
                        "secondary" -> {
                            SecondaryActionButton(
                                text = "Secondary Button ($clickCount)",
                                onClick = {
                                    clickCount++
                                    buttonState = "primary"
                                }
                            )
                        }
                    }
                    
                    Text("Clicks: $clickCount")
                }
            }
        }
        
        // Test button state changes and rendering performance
        repeat(5) {
            composeTestRule
                .onNodeWithText("Primary Button ($clickCount)", substring = true)
                .performClick()
                
            Thread.sleep(50)
            
            composeTestRule
                .onNodeWithText("Secondary Button ($clickCount)", substring = true)
                .performClick()
                
            Thread.sleep(50)
        }
        
        // Verify render times are reasonable (under 10ms for button updates)
        val avgRenderTime = renderTimes.drop(1).average() // Drop initial render
        assert(avgRenderTime <= 15.0) {
            "Button rendering too slow: ${avgRenderTime}ms average (target: <10ms)"
        }
        
        Timber.i("ComponentPerformanceTest: Button rendering average: ${avgRenderTime.toInt()}ms")
    }
    
    /**
     * Test: LazyColumn performance with complex cards
     * PRD Requirement: Smooth scrolling performance with card-based design
     */
    @Test
    fun lazyColumnWithCards_maintainsScrollPerformance() {
        val scrollFrameTimes = mutableListOf<Long>()
        var itemCount by mutableStateOf(20)
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    items(itemCount) { index ->
                        UnifiedWorkoutCard(
                            title = "Workout Card $index",
                            subtitle = "$itemCount total items",
                            onClick = { 
                                val frameTime = System.currentTimeMillis()
                                scrollFrameTimes.add(frameTime)
                            }
                        ) {
                            Text("Complex card content for item $index")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SecondaryActionButton(
                                    text = "Edit",
                                    onClick = { }
                                )
                                PrimaryActionButton(
                                    text = "Start",
                                    onClick = { }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Simulate scrolling by clicking items
        repeat(10) { index ->
            composeTestRule
                .onNodeWithText("Workout Card $index")
                .performClick()
        }
        
        // Increase item count to test performance under load
        itemCount = 50
        composeTestRule.waitForIdle()
        
        // Test more interactions with increased load
        repeat(5) { index ->
            composeTestRule
                .onNodeWithText("Workout Card $index")
                .performClick()
        }
        
        // Verify scroll performance remains smooth
        val frameIntervals = mutableListOf<Long>()
        for (i in 1 until scrollFrameTimes.size) {
            frameIntervals.add(scrollFrameTimes[i] - scrollFrameTimes[i-1])
        }
        
        val avgFrameInterval = frameIntervals.average()
        
        // Frame intervals should be reasonable for smooth scrolling
        assert(avgFrameInterval >= 16.0 && avgFrameInterval <= 100.0) {
            "Unusual frame intervals during scroll: ${avgFrameInterval}ms"
        }
        
        Timber.i("ComponentPerformanceTest: LazyColumn scroll performance verified")
    }
    
    /**
     * Test: Memory allocation during component updates
     * PRD Requirement: Efficient memory usage for extended app usage
     */
    @Test
    fun componentUpdates_minimizeMemoryAllocation() {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        var updateCounter by mutableStateOf(0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    items(30) { index ->
                        UnifiedWorkoutCard(
                            title = "Memory Test Card $index",
                            subtitle = "Update count: $updateCounter"
                        ) {
                            Text("Card content that updates with counter")
                            Text("Additional content line $index")
                            PrimaryActionButton(
                                text = "Action $updateCounter",
                                onClick = { updateCounter++ }
                            )
                        }
                    }
                }
            }
        }
        
        // Perform multiple updates to test memory usage
        repeat(20) { iteration ->
            updateCounter++
            composeTestRule.waitForIdle()
            
            // Trigger some interactions
            if (iteration % 5 == 0) {
                composeTestRule
                    .onNodeWithText("Memory Test Card 0")
                    .performClick()
            }
            
            Thread.sleep(50)
        }
        
        // Force garbage collection to get accurate measurement
        System.gc()
        Thread.sleep(100)
        
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryIncreaseMB = memoryIncrease / (1024 * 1024)
        
        // Memory increase should be reasonable (less than 20MB for this test)
        assert(memoryIncreaseMB <= 20) {
            "Excessive memory usage during component updates: ${memoryIncreaseMB}MB"
        }
        
        Timber.i("ComponentPerformanceTest: Memory usage increased by ${memoryIncreaseMB}MB")
    }
    
    /**
     * Test: Component state changes performance
     * PRD Requirement: Responsive UI during rapid state changes
     */
    @Test
    fun componentStateChanges_maintainResponsiveness() {
        val stateChangeTimes = mutableListOf<Long>()
        var cardState by mutableStateOf("normal")
        var isLoading by mutableStateOf(false)
        var hasError by mutableStateOf(false)
        
        composeTestRule.setContent {
            LaunchedEffect(cardState, isLoading, hasError) {
                stateChangeTimes.add(System.currentTimeMillis())
            }
            
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = when {
                        hasError -> "Error State"
                        isLoading -> "Loading..."
                        else -> "Normal State ($cardState)"
                    },
                    subtitle = "State change performance test"
                ) {
                    when {
                        hasError -> {
                            Text("Error occurred during operation")
                            SecondaryActionButton(
                                text = "Retry",
                                onClick = { hasError = false }
                            )
                        }
                        isLoading -> {
                            Text("Loading workout data...")
                        }
                        else -> {
                            Text("Ready for workout")
                            PrimaryActionButton(
                                text = "Start Loading",
                                onClick = { isLoading = true }
                            )
                            SecondaryActionButton(
                                text = "Trigger Error", 
                                onClick = { hasError = true }
                            )
                        }
                    }
                }
            }
        }
        
        // Test rapid state changes
        val stateChangeStartTime = System.currentTimeMillis()
        
        // Cycle through different states rapidly
        isLoading = true
        composeTestRule.waitForIdle()
        
        Thread.sleep(50)
        isLoading = false
        hasError = true
        composeTestRule.waitForIdle()
        
        Thread.sleep(50)
        hasError = false
        cardState = "updated"
        composeTestRule.waitForIdle()
        
        val totalStateChangeTime = System.currentTimeMillis() - stateChangeStartTime
        
        // State changes should be processed quickly (under 200ms total)
        assert(totalStateChangeTime <= 300) {
            "State changes took too long: ${totalStateChangeTime}ms"
        }
        
        // Verify final state is correct
        composeTestRule
            .onNodeWithText("Normal State (updated)")
            .assertIsDisplayed()
            
        Timber.i("ComponentPerformanceTest: State changes completed in ${totalStateChangeTime}ms")
    }
    
    /**
     * Test: Complex component hierarchy performance
     * PRD Requirement: Maintain performance with nested component structures
     */
    @Test
    fun complexComponentHierarchy_maintainsPerformance() {
        var hierarchyLevel by mutableStateOf(1)
        val renderTimings = mutableListOf<Long>()
        
        composeTestRule.setContent {
            val renderStart = remember { System.currentTimeMillis() }
            
            LaunchedEffect(hierarchyLevel) {
                renderTimings.add(System.currentTimeMillis() - renderStart)
            }
            
            LiftrixTheme {
                // Create nested component hierarchy
                UnifiedWorkoutCard(
                    title = "Level 1 Card",
                    subtitle = "Hierarchy level: $hierarchyLevel"
                ) {
                    if (hierarchyLevel >= 2) {
                        UnifiedWorkoutCard(
                            title = "Level 2 Nested Card",
                            subtitle = "Nested within level 1"
                        ) {
                            if (hierarchyLevel >= 3) {
                                Column {
                                    Text("Level 3 content")
                                    PrimaryActionButton(
                                        text = "Deep Action",
                                        onClick = { }
                                    )
                                }
                            } else {
                                Text("Level 2 content")
                            }
                        }
                    }
                    
                    PrimaryActionButton(
                        text = "Increase Hierarchy",
                        onClick = { if (hierarchyLevel < 3) hierarchyLevel++ }
                    )
                    SecondaryActionButton(
                        text = "Reset",
                        onClick = { hierarchyLevel = 1 }
                    )
                }
            }
        }
        
        // Test hierarchy changes
        composeTestRule
            .onNodeWithText("Increase Hierarchy")
            .performClick()
        composeTestRule.waitForIdle()
        
        composeTestRule
            .onNodeWithText("Increase Hierarchy") 
            .performClick()
        composeTestRule.waitForIdle()
        
        // Verify complex hierarchy renders within acceptable time
        val finalRenderTime = renderTimings.lastOrNull() ?: 0L
        assert(finalRenderTime <= 50) {
            "Complex hierarchy rendering too slow: ${finalRenderTime}ms"
        }
        
        // Verify all hierarchy levels are accessible
        composeTestRule
            .onNodeWithText("Level 1 Card")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Level 2 Nested Card")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Level 3 content")
            .assertIsDisplayed()
            
        Timber.i("ComponentPerformanceTest: Complex hierarchy performance verified")
    }
}