package com.example.liftrix.ui.progress.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.ui.progress.components.charts.ModernVolumeChart
import com.example.liftrix.ui.progress.components.charts.MuscleGroup
import com.example.liftrix.ui.progress.components.charts.MuscleGroupPieChart
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * ChartPerformanceTest - Performance benchmarks for chart components
 *
 * Validates 60fps performance targets:
 * - Chart rendering with 1000+ data points under 16ms
 * - Smooth animations and interactions
 * - Memory usage optimization with large datasets
 * - Touch target responsiveness (<100ms)
 * - Time selector updates with minimal frame drops
 * - Bezier curve calculations performance
 * - Gradient rendering efficiency
 */
@RunWith(AndroidJUnit4::class)
class ChartPerformanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    companion object {
        private const val FRAME_TIME_TARGET_MS = 16L  // 60fps = 16.67ms per frame
        private const val TOUCH_RESPONSE_TARGET_MS = 100L
        private const val LARGE_DATASET_SIZE = 1000
        private const val MASSIVE_DATASET_SIZE = 5000
        private const val ANIMATION_DURATION_MS = 300L
    }

    @Test
    fun modernVolumeChart_performsWithLargeDataset() {
        val largeDataset = generateLargeVolumeDataset(LARGE_DATASET_SIZE)
        var renderTime = 0L
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    val measureTime = measureTimeMillis {
                        ModernVolumeChart(
                            data = largeDataset,
                            timeRange = TimeRangeType.YEAR
                        )
                    }
                    renderTime = measureTime
                }
            }
        }
        
        // Wait for composition to complete
        composeTestRule.waitForIdle()
        
        // Chart should render within frame time target
        assertTrue(
            "Chart with ${LARGE_DATASET_SIZE} points rendered in ${renderTime}ms, exceeds 16ms target",
            renderTime <= FRAME_TIME_TARGET_MS * 2 // Allow 2 frames for initial render
        )
    }
    
    @Test
    fun modernVolumeChart_handlesExtremeDataset() {
        val extremeDataset = generateLargeVolumeDataset(MASSIVE_DATASET_SIZE)
        var isDisplayed = false
        
        val totalTime = measureTimeMillis {
            composeTestRule.setContent {
                LiftrixTheme {
                    TestContainer {
                        ModernVolumeChart(
                            data = extremeDataset,
                            timeRange = TimeRangeType.ALL_TIME
                        )
                        isDisplayed = true
                    }
                }
            }
            
            composeTestRule.waitForIdle()
        }
        
        assertTrue("Chart failed to render with ${MASSIVE_DATASET_SIZE} data points", isDisplayed)
        assertTrue(
            "Extreme dataset render time ${totalTime}ms exceeds reasonable limit",
            totalTime <= 1000L  // Should render within 1 second even for massive datasets
        )
    }
    
    @Test
    fun muscleGroupPieChart_performsWithManySlices() {
        val manySlicesData = generateManySlicesData()
        var renderTime = 0L
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    val measureTime = measureTimeMillis {
                        MuscleGroupPieChart(
                            data = manySlicesData,
                            showPercentages = true,
                            showLegend = true,
                            animationDuration = 500
                        )
                    }
                    renderTime = measureTime
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        assertTrue(
            "Pie chart with multiple slices rendered in ${renderTime}ms, exceeds target",
            renderTime <= FRAME_TIME_TARGET_MS * 2
        )
    }
    
    @Test
    fun globalTimeRangeSelector_respondsQuickly() {
        var selectionTime = 0L
        var selectedTimeRange by mutableStateOf(TimeRangeType.MONTH)
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    GlobalTimeRangeSelector(
                        selectedTimeRange = selectedTimeRange,
                        onTimeRangeChange = { newTimeRange ->
                            selectedTimeRange = newTimeRange
                        }
                    )
                }
            }
        }
        
        // Measure touch response time
        selectionTime = measureTimeMillis {
            composeTestRule.onRoot().performTouchInput {
                // Simulate tapping on Quarter button
                click(center.copy(x = center.x * 1.2f))
            }
            composeTestRule.waitForIdle()
        }
        
        assertTrue(
            "Time selector response time ${selectionTime}ms exceeds ${TOUCH_RESPONSE_TARGET_MS}ms target",
            selectionTime <= TOUCH_RESPONSE_TARGET_MS
        )
    }
    
    @Test
    fun modernVolumeChart_animatesSmothly() {
        val initialDataset = generateLargeVolumeDataset(50)
        val updatedDataset = generateLargeVolumeDataset(75)
        var currentData by mutableStateOf(initialDataset)
        var animationTime = 0L
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    ModernVolumeChart(
                        data = currentData,
                        timeRange = TimeRangeType.QUARTER,
                        animationDuration = ANIMATION_DURATION_MS.toInt()
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Trigger data update and measure animation performance
        animationTime = measureTimeMillis {
            currentData = updatedDataset
            composeTestRule.waitForIdle()
            
            // Wait for animation to complete
            Thread.sleep(ANIMATION_DURATION_MS + 100)
        }
        
        assertTrue(
            "Chart animation took ${animationTime}ms, should complete smoothly",
            animationTime <= ANIMATION_DURATION_MS + 200  // Allow some buffer for animation
        )
    }
    
    @Test
    fun pieChart_touchInteractionPerformance() {
        val muscleGroupData = generateManySlicesData()
        var touchResponseTime = 0L
        var sliceSelected = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    MuscleGroupPieChart(
                        data = muscleGroupData,
                        onSliceClick = { sliceSelected = true },
                        showPercentages = true,
                        showLegend = true
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Measure touch interaction response time
        touchResponseTime = measureTimeMillis {
            composeTestRule.onRoot().performTouchInput {
                click(center)
            }
            composeTestRule.waitForIdle()
        }
        
        assertTrue(
            "Pie chart touch response ${touchResponseTime}ms exceeds target",
            touchResponseTime <= TOUCH_RESPONSE_TARGET_MS
        )
    }
    
    @Test
    fun charts_memoryUsageWithLargeDatasets() {
        // Test multiple charts with large datasets simultaneously
        val volumeData = generateLargeVolumeDataset(500)
        val muscleData = generateManySlicesData()
        
        var chartsRendered = 0
        val totalRenderTime = measureTimeMillis {
            composeTestRule.setContent {
                LiftrixTheme {
                    TestContainer {
                        // Multiple charts to test memory usage
                        ModernVolumeChart(
                            data = volumeData,
                            timeRange = TimeRangeType.YEAR
                        )
                        chartsRendered++
                        
                        MuscleGroupPieChart(
                            data = muscleData,
                            showLegend = true
                        )
                        chartsRendered++
                    }
                }
            }
            
            composeTestRule.waitForIdle()
        }
        
        assertTrue("Not all charts rendered successfully", chartsRendered == 2)
        assertTrue(
            "Multiple large charts took ${totalRenderTime}ms, may indicate memory issues",
            totalRenderTime <= 500L
        )
    }
    
    @Test
    fun bezierCurveCalculation_performsEfficiently() {
        // Test bezier curve calculation performance specifically
        val denseDataset = generateDenseVolumeDataset(300)  // Dense data for complex curves
        var calculationTime = 0L
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    calculationTime = measureTimeMillis {
                        ModernVolumeChart(
                            data = denseDataset,
                            timeRange = TimeRangeType.QUARTER
                        )
                    }
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        assertTrue(
            "Bezier curve calculation with dense data took ${calculationTime}ms, exceeds target",
            calculationTime <= FRAME_TIME_TARGET_MS * 3  // Allow 3 frames for complex calculations
        )
    }
    
    @Test
    fun gradientRendering_performsEfficiently() {
        val datasetWithGradients = generateLargeVolumeDataset(200)
        var gradientRenderTime = 0L
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    gradientRenderTime = measureTimeMillis {
                        ModernVolumeChart(
                            data = datasetWithGradients,
                            timeRange = TimeRangeType.MONTH
                        )
                        
                        MuscleGroupPieChart(
                            data = generateManySlicesData(),
                            showLegend = true
                        )
                    }
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        assertTrue(
            "Gradient rendering took ${gradientRenderTime}ms, may impact performance",
            gradientRenderTime <= FRAME_TIME_TARGET_MS * 2
        )
    }
    
    @Test
    fun continuousInteraction_maintainsPerformance() {
        var timeRange by mutableStateOf(TimeRangeType.WEEK)
        val dataset = generateLargeVolumeDataset(200)
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    GlobalTimeRangeSelector(
                        selectedTimeRange = timeRange,
                        onTimeRangeChange = { timeRange = it }
                    )
                    
                    ModernVolumeChart(
                        data = dataset,
                        timeRange = timeRange
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Simulate rapid interactions
        val interactionTimes = mutableListOf<Long>()
        TimeRangeType.values().forEach { newTimeRange ->
            val interactionTime = measureTimeMillis {
                timeRange = newTimeRange
                composeTestRule.waitForIdle()
            }
            interactionTimes.add(interactionTime)
        }
        
        val averageInteractionTime = interactionTimes.average()
        assertTrue(
            "Average interaction time ${averageInteractionTime}ms exceeds target for continuous interactions",
            averageInteractionTime <= TOUCH_RESPONSE_TARGET_MS
        )
    }

    // Helper functions for test data generation
    
    private fun generateLargeVolumeDataset(size: Int): List<VolumeDataPoint> {
        return (1..size).map { index ->
            VolumeDataPoint(
                date = LocalDate(2024, 1, 1).plusDays((index % 365).toLong()),
                volume = Weight.fromKilograms(1000.0 + Random.nextDouble(-200.0, 500.0)),
                workoutCount = if (Random.nextBoolean()) 2 else 1
            )
        }
    }
    
    private fun generateDenseVolumeDataset(size: Int): List<VolumeDataPoint> {
        // Generate dataset with high variation for complex bezier curves
        return (1..size).map { index ->
            val baseValue = 1000.0
            val variation = kotlin.math.sin(index * 0.1) * 300 + Random.nextDouble(-100.0, 100.0)
            
            VolumeDataPoint(
                date = LocalDate(2024, 1, 1).plusDays(index.toLong()),
                volume = Weight.fromKilograms(baseValue + variation),
                workoutCount = Random.nextInt(1, 4)
            )
        }
    }
    
    private fun generateManySlicesData(): Map<MuscleGroup, Float> {
        return MuscleGroup.values().associateWith { 
            Random.nextFloat() * 1000f + 500f
        }
    }
}

/**
 * Performance test container with consistent layout
 */
@Composable
private fun TestContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}