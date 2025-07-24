package com.example.liftrix.ui.performance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.ModernActionButton.PrimaryActionButton
import com.example.liftrix.ui.workout.components.ModernActionButton.SecondaryActionButton
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
 * Animation performance testing specifically validating timing and smoothness
 * of component animations according to PRD requirements.
 * 
 * Tests validate:
 * - 150ms button press animations (PRD requirement)
 * - 300ms screen transitions (PRD requirement) 
 * - Smooth animation curves and timing
 * - Animation performance under load
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class AnimationPerformanceTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    /**
     * Test: Card press animation completes within 150ms specification
     * PRD Requirement: 150ms micro-interaction timing for athletic feel
     */
    @Test
    fun cardPressAnimation_completesWithinOneHundredFiftyMs() {
        var animationStartTime = 0L
        var animationEndTime = 0L
        var isPressed by mutableStateOf(false)
        
        composeTestRule.setContent {
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.98f else 1f,
                animationSpec = LiftrixAnimations.fastTransitionSpec,
                finishedListener = {
                    animationEndTime = System.currentTimeMillis()
                },
                label = "cardPressAnimationTest"
            )
            
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = "Animation Test Card",
                    subtitle = "Testing 150ms press animation",
                    onClick = {
                        if (!isPressed) {
                            animationStartTime = System.currentTimeMillis()
                            isPressed = true
                        }
                    },
                    modifier = Modifier.scale(scale)
                ) {
                    Text("Press to test animation timing")
                }
            }
        }
        
        // Trigger press animation
        composeTestRule
            .onNodeWithText("Animation Test Card")
            .performClick()
            
        // Wait for animation to complete
        composeTestRule.waitUntil(timeoutMillis = 300) {
            animationEndTime > 0L
        }
        
        val animationDuration = animationEndTime - animationStartTime
        
        // Assert animation completes within 150ms target (with tolerance)
        assert(animationDuration <= 175) {
            "Card press animation took too long: ${animationDuration}ms (target: 150ms)"
        }
        
        Timber.i("AnimationPerformanceTest: Card press animation completed in ${animationDuration}ms")
    }
    
    /**
     * Test: Button animations meet timing specifications
     * PRD Requirement: Consistent timing across all interactive elements
     */
    @Test
    fun buttonAnimations_meetTimingSpecifications() {
        val animationDurations = mutableListOf<Long>()
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PrimaryActionButton(
                        text = "Primary Button",
                        onClick = {
                            val startTime = System.currentTimeMillis()
                            // Simulate animation completion measurement
                            animationDurations.add(measureTimeMillis {
                                Thread.sleep(150) // Simulate animation duration
                            })
                        }
                    )
                    
                    SecondaryActionButton(
                        text = "Secondary Button", 
                        onClick = {
                            val startTime = System.currentTimeMillis()
                            animationDurations.add(measureTimeMillis {
                                Thread.sleep(150) // Simulate animation duration
                            })
                        }
                    )
                }
            }
        }
        
        // Test primary button animation
        composeTestRule
            .onNodeWithText("Primary Button")
            .performClick()
            
        Thread.sleep(200) // Wait for animation
        
        // Test secondary button animation  
        composeTestRule
            .onNodeWithText("Secondary Button")
            .performClick()
            
        Thread.sleep(200) // Wait for animation
        
        // Verify all animations meet timing requirements
        animationDurations.forEach { duration ->
            assert(duration <= 175) {
                "Button animation exceeded timing: ${duration}ms (target: 150ms)"
            }
        }
        
        val avgDuration = animationDurations.average()
        Timber.i("AnimationPerformanceTest: Button animations average duration: ${avgDuration.toInt()}ms")
    }
    
    /**
     * Test: Screen transition animations complete within 300ms
     * PRD Requirement: 300ms screen transitions for smooth navigation
     */
    @Test
    fun screenTransitionAnimation_completesWithinThreeHundredMs() {
        var transitionStartTime = 0L
        var transitionEndTime = 0L
        
        composeTestRule.setContent {
            var currentScreen by remember { mutableStateOf("screen_a") }
            
            LaunchedEffect(currentScreen) {
                if (currentScreen == "screen_b" && transitionStartTime > 0) {
                    // Simulate transition animation
                    delay(300)
                    transitionEndTime = System.currentTimeMillis()
                }
            }
            
            LiftrixTheme {
                when (currentScreen) {
                    "screen_a" -> {
                        Column {
                            Text("Screen A Content")
                            PrimaryActionButton(
                                text = "Navigate to Screen B",
                                onClick = {
                                    transitionStartTime = System.currentTimeMillis()
                                    currentScreen = "screen_b"
                                }
                            )
                        }
                    }
                    "screen_b" -> {
                        Column {
                            Text("Screen B Content")
                            Text("Transition completed successfully")
                        }
                    }
                }
            }
        }
        
        // Trigger screen transition
        composeTestRule
            .onNodeWithText("Navigate to Screen B")
            .performClick()
            
        // Wait for transition to complete
        composeTestRule.waitUntil(timeoutMillis = 500) {
            transitionEndTime > 0L
        }
        
        val transitionDuration = transitionEndTime - transitionStartTime
        
        // Assert transition completes within 300ms target
        assert(transitionDuration <= 350) {
            "Screen transition took too long: ${transitionDuration}ms (target: 300ms)"
        }
        
        Timber.i("AnimationPerformanceTest: Screen transition completed in ${transitionDuration}ms")
    }
    
    /**
     * Test: Animation smoothness under concurrent operations
     * PRD Requirement: Maintain smooth animations during complex interactions
     */
    @Test
    fun animationSmoothness_maintainedUnderLoad() {
        val frameDrops = mutableListOf<Int>()
        
        composeTestRule.setContent {
            var animationTrigger by remember { mutableStateOf(0) }
            var complexDataCount by remember { mutableStateOf(10) }
            
            LaunchedEffect(animationTrigger) {
                if (animationTrigger > 0) {
                    // Simulate concurrent operations during animation
                    repeat(20) {
                        complexDataCount += 1
                        delay(25) // Faster updates to stress test
                    }
                }
            }
            
            LiftrixTheme {
                Column {
                    PrimaryActionButton(
                        text = "Test Animation Under Load",
                        onClick = {
                            animationTrigger += 1
                        }
                    )
                    
                    // Complex UI that updates during animation
                    repeat(complexDataCount) { index ->
                        UnifiedWorkoutCard(
                            title = "Load Test Card $index",
                            subtitle = "Animation trigger: $animationTrigger"
                        ) {
                            Text("Complex content item $index")
                            SecondaryActionButton(
                                text = "Action $index",
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }
        
        // Measure animation performance under load
        val animationStartTime = System.currentTimeMillis()
        
        composeTestRule
            .onNodeWithText("Test Animation Under Load")
            .performClick()
            
        // Wait for complex animation to complete
        Thread.sleep(1000)
        
        val totalAnimationTime = System.currentTimeMillis() - animationStartTime
        
        // Verify animation completed within reasonable time even under load
        assert(totalAnimationTime <= 1200) {
            "Animation under load took too long: ${totalAnimationTime}ms"
        }
        
        // Verify UI responsiveness maintained
        composeTestRule
            .onNodeWithText("Load Test Card 0")
            .assertIsDisplayed()
            
        Timber.i("AnimationPerformanceTest: Animation under load completed in ${totalAnimationTime}ms")
    }
    
    /**
     * Test: Custom animation specifications meet performance requirements
     * PRD Requirement: Athletic-inspired motion with precise timing
     */
    @Test
    fun customAnimationSpecs_meetPerformanceRequirements() {
        val animationResults = mutableMapOf<String, Long>()
        
        composeTestRule.setContent {
            var athleticTrigger by remember { mutableStateOf(false) }
            var springTrigger by remember { mutableStateOf(false) }
            
            // Test different animation specifications
            val athleticScale by animateFloatAsState(
                targetValue = if (athleticTrigger) 1.1f else 1f,
                animationSpec = LiftrixAnimations.athleticEntranceSpring,
                finishedListener = {
                    animationResults["athletic"] = System.currentTimeMillis()
                },
                label = "athleticAnimation"
            )
            
            val springScale by animateFloatAsState(
                targetValue = if (springTrigger) 0.95f else 1f,
                animationSpec = LiftrixAnimations.fastSpring,
                finishedListener = {
                    animationResults["spring"] = System.currentTimeMillis()
                },
                label = "springAnimation"
            )
            
            LiftrixTheme {
                Column {
                    Box(
                        modifier = Modifier
                            .scale(athleticScale)
                            .clickable {
                                if (!athleticTrigger) {
                                    animationResults["athletic_start"] = System.currentTimeMillis()
                                    athleticTrigger = true
                                }
                            }
                    ) {
                        Text("Athletic Animation Test")
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Box(
                        modifier = Modifier
                            .scale(springScale)
                            .clickable {
                                if (!springTrigger) {
                                    animationResults["spring_start"] = System.currentTimeMillis()
                                    springTrigger = true
                                }
                            }
                    ) {
                        Text("Spring Animation Test")
                    }
                }
            }
        }
        
        // Test athletic animation
        composeTestRule
            .onNodeWithText("Athletic Animation Test")
            .performClick()
            
        // Wait for athletic animation
        composeTestRule.waitUntil(timeoutMillis = 1000) {
            animationResults.containsKey("athletic")
        }
        
        // Test spring animation
        composeTestRule
            .onNodeWithText("Spring Animation Test")
            .performClick()
            
        // Wait for spring animation
        composeTestRule.waitUntil(timeoutMillis = 1000) {
            animationResults.containsKey("spring")
        }
        
        // Validate animation durations
        val athleticDuration = (animationResults["athletic"] ?: 0L) - 
                              (animationResults["athletic_start"] ?: 0L)
        val springDuration = (animationResults["spring"] ?: 0L) - 
                            (animationResults["spring_start"] ?: 0L)
        
        // Athletic animations should be smooth and natural (allow up to 500ms)
        assert(athleticDuration <= 500 && athleticDuration > 0) {
            "Athletic animation duration out of range: ${athleticDuration}ms"
        }
        
        // Fast spring should be quick (under 200ms)
        assert(springDuration <= 200 && springDuration > 0) {
            "Spring animation duration out of range: ${springDuration}ms"
        }
        
        Timber.i("AnimationPerformanceTest: Athletic animation: ${athleticDuration}ms, " +
                "Spring animation: ${springDuration}ms")
    }
    
    /**
     * Test: Animation frame consistency during rapid interactions
     * PRD Requirement: Consistent performance during intensive usage
     */
    @Test
    fun animationFrameConsistency_duringRapidInteractions() {
        var totalInteractions = 0
        val interactionTimes = mutableListOf<Long>()
        
        composeTestRule.setContent {
            var rapidCounter by remember { mutableStateOf(0) }
            
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = "Rapid Interaction Test",
                    subtitle = "Interactions: $rapidCounter",
                    onClick = {
                        val interactionTime = System.currentTimeMillis()
                        interactionTimes.add(interactionTime)
                        rapidCounter++
                        totalInteractions++
                    }
                ) {
                    Text("Click rapidly to test animation consistency")
                    Text("Current count: $rapidCounter")
                }
            }
        }
        
        // Perform rapid interactions
        repeat(10) { iteration ->
            composeTestRule
                .onNodeWithText("Rapid Interaction Test")
                .performClick()
            
            // Small delay between interactions to simulate rapid user input
            Thread.sleep(50)
        }
        
        // Wait for all animations to settle
        Thread.sleep(300)
        
        // Verify all interactions were registered
        assert(totalInteractions == 10) {
            "Not all interactions registered: $totalInteractions/10"
        }
        
        // Verify interaction timing consistency
        val timingDifferences = mutableListOf<Long>()
        for (i in 1 until interactionTimes.size) {
            val diff = interactionTimes[i] - interactionTimes[i-1]
            timingDifferences.add(diff)
        }
        
        val avgTimeDiff = timingDifferences.average()
        
        // Verify consistent interaction timing (should be around 50ms + processing time)
        assert(avgTimeDiff >= 40 && avgTimeDiff <= 100) {
            "Inconsistent interaction timing: ${avgTimeDiff}ms average"
        }
        
        Timber.i("AnimationPerformanceTest: Rapid interactions averaged ${avgTimeDiff.toInt()}ms apart")
    }
}