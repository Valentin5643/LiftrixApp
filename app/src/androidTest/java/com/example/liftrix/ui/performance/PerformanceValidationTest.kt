package com.example.liftrix.ui.performance

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.view.Choreographer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.ui.theme.LiftrixTheme
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
import kotlin.math.roundToInt

/**
 * Comprehensive performance validation tests ensuring 60fps targets are met
 * during all animations, screen transitions, and complex UI interactions.
 * 
 * Tests validate PRD requirement: "60fps performance targets" for modern visual appeal
 * and smooth user interactions across workout creation, active sessions, and editing.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class PerformanceValidationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    private lateinit var performanceProfiler: PerformanceProfiler
    private lateinit var context: Context
    
    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        performanceProfiler = PerformanceProfiler(context)
    }
    
    /**
     * Test: UnifiedWorkoutCard maintains 60fps during press animation
     * PRD Requirement: Smooth animations for modern visual appeal
     * Animation: 150ms press animation with 0.98x scale factor
     */
    @Test
    fun unifiedWorkoutCard_maintainsSixtyFpsDuringPressAnimation() {
        var frameDrops = 0
        val targetFps = 60
        val frameTimeThreshold = (1000.0 / targetFps * 1.5).toLong() // 1.5x tolerance
        
        val frameCallback = object : Choreographer.FrameCallback {
            private var lastFrameTime = 0L
            
            override fun doFrame(frameTimeNanos: Long) {
                val currentTime = System.nanoTime()
                if (lastFrameTime != 0L) {
                    val frameDuration = (currentTime - lastFrameTime) / 1_000_000 // Convert to milliseconds
                    if (frameDuration > frameTimeThreshold) {
                        frameDrops++
                    }
                }
                lastFrameTime = currentTime
                
                // Continue frame monitoring during animation
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCard(
                    title = "Performance Test Card",
                    subtitle = "Testing 60fps animation performance",
                    onClick = { 
                        Timber.d("Card pressed - testing animation performance")
                    }
                ) {
                    Text("Card content for performance testing")
                    PrimaryActionButton(
                        text = "Test Action",
                        onClick = { }
                    )
                }
            }
        }
        
        // Start frame monitoring
        Choreographer.getInstance().postFrameCallback(frameCallback)
        
        // Perform press animation test
        composeTestRule
            .onNodeWithText("Performance Test Card")
            .performTouchInput {
                down(center)
                // Hold press for animation duration (150ms + buffer)
                Thread.sleep(200)
                up()
            }
        
        // Wait for animation completion
        Thread.sleep(300)
        
        // Stop frame monitoring
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        
        // Assert 60fps maintained (allow 2-3 frame drops for tolerance on slower devices)
        val maxAllowedFrameDrops = if (isHighPerformanceDevice()) 2 else 5
        assert(frameDrops <= maxAllowedFrameDrops) {
            "Too many frame drops during card press animation: $frameDrops (limit: $maxAllowedFrameDrops)"
        }
        
        Timber.i("PerformanceValidationTest: Card animation completed with $frameDrops frame drops")
    }
    
    /**
     * Test: Screen transition performance within 300ms target
     * PRD Requirement: Streamlined workflow with smooth navigation
     */
    @Test
    fun screenTransition_completesWithinThreeHundredMs() {
        val startTime = System.currentTimeMillis()
        
        composeTestRule.setContent {
            var currentScreen by remember { mutableStateOf("workout_list") }
            
            LiftrixTheme {
                when (currentScreen) {
                    "workout_list" -> {
                        Column {
                            Text("Workout List Screen")
                            PrimaryActionButton(
                                text = "Create New Workout",
                                onClick = { currentScreen = "create_workout" }
                            )
                        }
                    }
                    "create_workout" -> {
                        Column {
                            Text("Creating a workout")
                            Text("Create workout screen content")
                        }
                    }
                }
            }
        }
        
        // Trigger screen transition
        composeTestRule
            .onNodeWithText("Create New Workout")
            .performClick()
            
        // Wait for transition to complete and verify new screen
        composeTestRule.waitUntil(timeoutMillis = 500) {
            composeTestRule
                .onAllNodesWithText("Creating a workout")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        
        val transitionTime = System.currentTimeMillis() - startTime
        
        // Assert transition completes within 300ms target (with reasonable tolerance)
        assert(transitionTime <= 350) {
            "Screen transition took too long: ${transitionTime}ms (target: 300ms)"
        }
        
        Timber.i("PerformanceValidationTest: Screen transition completed in ${transitionTime}ms")
    }
    
    /**
     * Test: Component rendering stays within 16ms target per frame
     * PRD Requirement: 60fps performance for complex UI interactions
     */
    @Test
    fun componentRecomposition_staysWithinSixteenMsTarget() {
        val renderTimes = mutableListOf<Long>()
        
        composeTestRule.setContent {
            var counter by remember { mutableStateOf(0) }
            
            LaunchedEffect(Unit) {
                repeat(10) {
                    val renderStart = System.currentTimeMillis()
                    counter++
                    delay(50) // Simulate typical UI update interval
                    val renderEnd = System.currentTimeMillis()
                    renderTimes.add(renderEnd - renderStart)
                }
            }
            
            LiftrixTheme {
                LazyColumn {
                    items(20) { index ->
                        UnifiedWorkoutCard(
                            title = "Workout $index",
                            subtitle = "Counter: $counter",
                            onClick = { /* Test recomposition */ }
                        ) {
                            Text("Dynamic content: $counter")
                            SecondaryActionButton(
                                text = "Action $index",
                                onClick = { }
                            )
                        }
                    }
                }
            }
        }
        
        // Wait for all recompositions to complete
        Thread.sleep(1000)
        
        // Check if any render exceeded 16ms (60fps target)
        val maxRenderTime = renderTimes.maxOrNull() ?: 0L
        val avgRenderTime = renderTimes.average()
        
        // Assert rendering performance
        assert(maxRenderTime <= 25) { // 25ms tolerance for complex layouts
            "Component rendering exceeded acceptable time: ${maxRenderTime}ms (target: 16ms)"
        }
        
        Timber.i("PerformanceValidationTest: Average render time: ${avgRenderTime.roundToInt()}ms, " +
                "Max: ${maxRenderTime}ms")
    }
    
    /**
     * Test: Memory usage remains stable during extended interactions
     * PRD Requirement: Maintain performance across extended usage
     */
    @Test
    fun memoryUsage_remainsStableDuringExtendedInteractions() {
        val memoryReadings = mutableListOf<Long>()
        var interactionCount = 0
        
        composeTestRule.setContent {
            var refreshCount by remember { mutableStateOf(0) }
            
            LaunchedEffect(Unit) {
                repeat(20) {
                    delay(100)
                    refreshCount++
                }
            }
            
            LiftrixTheme {
                LazyColumn {
                    items(50) { index ->
                        UnifiedWorkoutCard(
                            title = "Memory Test Card $index",
                            subtitle = "Refresh: $refreshCount",
                            onClick = { 
                                interactionCount++
                                memoryReadings.add(getCurrentMemoryUsage())
                            }
                        ) {
                            Text("Content that updates: $refreshCount")
                        }
                    }
                }
            }
        }
        
        // Perform multiple interactions to test memory stability
        repeat(10) {
            composeTestRule
                .onNodeWithText("Memory Test Card 0")
                .performClick()
            Thread.sleep(100)
        }
        
        // Wait for all operations to complete
        Thread.sleep(500)
        
        // Analyze memory usage trend
        if (memoryReadings.isNotEmpty()) {
            val initialMemory = memoryReadings.first()
            val finalMemory = memoryReadings.last()
            val memoryIncrease = finalMemory - initialMemory
            
            // Assert memory usage remains reasonable (less than 50MB increase)
            assert(memoryIncrease < 50 * 1024 * 1024) {
                "Memory usage increased too much: ${memoryIncrease / 1024 / 1024}MB"
            }
            
            Timber.i("PerformanceValidationTest: Memory usage - Initial: ${initialMemory / 1024 / 1024}MB, " +
                    "Final: ${finalMemory / 1024 / 1024}MB, Increase: ${memoryIncrease / 1024 / 1024}MB")
        }
    }
    
    /**
     * Test: Animation smoothness during concurrent operations
     * PRD Requirement: Smooth animations even during complex interactions
     */
    @Test
    fun animationSmoothness_maintainedDuringConcurrentOperations() {
        var animationFrameDrops = 0
        val frameMonitor = FrameDropMonitor { drops ->
            animationFrameDrops += drops
        }
        
        composeTestRule.setContent {
            var isAnimating by remember { mutableStateOf(false) }
            var dataCount by remember { mutableStateOf(10) }
            
            LaunchedEffect(isAnimating) {
                if (isAnimating) {
                    repeat(30) { // 30 iterations of concurrent updates
                        dataCount += 1
                        delay(50)
                    }
                    isAnimating = false
                }
            }
            
            LiftrixTheme {
                Column {
                    PrimaryActionButton(
                        text = "Start Animation Test",
                        onClick = { 
                            isAnimating = true
                            frameMonitor.startMonitoring()
                        }
                    )
                    
                    LazyColumn {
                        items(dataCount) { index ->
                            UnifiedWorkoutCard(
                                title = "Concurrent Test $index",
                                subtitle = if (isAnimating) "Animating..." else "Static"
                            ) {
                                Text("Data count: $dataCount")
                            }
                        }
                    }
                }
            }
        }
        
        // Start the concurrent animation test
        composeTestRule
            .onNodeWithText("Start Animation Test")
            .performClick()
            
        // Wait for animations to complete
        Thread.sleep(2000)
        frameMonitor.stopMonitoring()
        
        // Assert animation smoothness maintained
        val maxAllowedFrameDrops = if (isHighPerformanceDevice()) 5 else 10
        assert(animationFrameDrops <= maxAllowedFrameDrops) {
            "Too many frame drops during concurrent operations: $animationFrameDrops " +
            "(limit: $maxAllowedFrameDrops)"
        }
        
        Timber.i("PerformanceValidationTest: Concurrent animation test completed with " +
                "$animationFrameDrops frame drops")
    }
    
    // Helper functions
    
    private fun getCurrentMemoryUsage(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem
    }
    
    private fun isHighPerformanceDevice(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && 
               (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice.not()
    }
    
    /**
     * Simple frame drop monitor for animation testing
     */
    private class FrameDropMonitor(private val onFrameDrops: (Int) -> Unit) {
        private var isMonitoring = false
        private var frameDropCount = 0
        
        fun startMonitoring() {
            isMonitoring = true
            frameDropCount = 0
            monitorFrames()
        }
        
        fun stopMonitoring() {
            isMonitoring = false
            onFrameDrops(frameDropCount)
        }
        
        private fun monitorFrames() {
            if (!isMonitoring) return
            
            val frameCallback = object : Choreographer.FrameCallback {
                private var lastFrameTime = 0L
                
                override fun doFrame(frameTimeNanos: Long) {
                    if (!isMonitoring) return
                    
                    val currentTime = System.nanoTime()
                    if (lastFrameTime != 0L) {
                        val frameDuration = (currentTime - lastFrameTime) / 1_000_000
                        if (frameDuration > 25) { // 25ms threshold for frame drops
                            frameDropCount++
                        }
                    }
                    lastFrameTime = currentTime
                    
                    if (isMonitoring) {
                        Choreographer.getInstance().postFrameCallback(this)
                    }
                }
            }
            
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }
}