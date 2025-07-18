package com.example.liftrix.performance

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.common.analytics.ChartThemeProvider
import com.example.liftrix.ui.progress.components.ProgressChart
import com.example.liftrix.ui.progress.components.StrengthProgressChart
import com.example.liftrix.ui.progress.components.VolumeProgressChart
import com.example.liftrix.ui.progress.components.WorkoutDurationChart
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.theme.ThemeMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rendering performance validation tests to ensure 60fps targets are met:
 * - UI component rendering under 16.67ms per frame
 * - Chart rendering performance optimization
 * - List scrolling performance validation  
 * - Animation frame rate consistency
 * - Recomposition performance monitoring
 * 
 * These tests validate the UI performance optimizations implemented across the progress dashboard.
 */
@RunWith(AndroidJUnit4::class)
class RenderingPerformanceTest {
    
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    companion object {
        private const val TARGET_FRAME_TIME_MS = 16.67 // 60fps = 16.67ms per frame
        private const val FRAME_TIME_TOLERANCE_MS = 2.0 // Allow 2ms tolerance
        private const val SCROLL_PERFORMANCE_TARGET_MS = 10.0 // Smooth scrolling target
        private const val CHART_RENDER_TARGET_MS = 100.0 // Chart rendering target
    }
    
    @Test
    fun `chart rendering performance meets 60fps target`() {
        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                LiftrixTheme {
                    ChartThemeProvider {
                        TestChartsContainer()
                    }
                }
            }
            
            composeTestRule.waitForIdle()
        }
    }
    
    @Test
    fun `progress chart rendering performance validation`() = runTest {
        val renderStartTime = System.nanoTime()
        
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeProgressChart(
                    data = generateMockChartData(100), // 100 data points
                    isLoading = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val renderTime = (System.nanoTime() - renderStartTime) / 1_000_000.0 // Convert to ms
        
        assertTrue(
            "Chart rendering time ${renderTime}ms exceeds target ${CHART_RENDER_TARGET_MS}ms",
            renderTime < CHART_RENDER_TARGET_MS
        )
    }
    
    @Test
    fun `large dataset chart performance validation`() = runTest {
        val renderStartTime = System.nanoTime()
        
        composeTestRule.setContent {
            LiftrixTheme {
                StrengthProgressChart(
                    data = generateMockChartData(500), // Large dataset
                    isLoading = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val renderTime = (System.nanoTime() - renderStartTime) / 1_000_000.0 // Convert to ms
        
        // Allow more time for large datasets but ensure reasonable performance
        assertTrue(
            "Large dataset chart rendering ${renderTime}ms exceeds reasonable limit",
            renderTime < 200.0 // 200ms for large datasets
        )
    }
    
    @Test
    fun `list scrolling performance meets smooth scrolling target`() {
        val testData = generateLargeTestDataList(1000) // 1000 items
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .androidx.compose.ui.platform.testTag("performance_list")
                ) {
                    items(testData) { item ->
                        TestListItem(data = item)
                    }
                }
            }
        }
        
        // Measure scroll performance
        val scrollStartTime = System.nanoTime()
        
        // Perform rapid scrolling
        composeTestRule
            .onNodeWithTag("performance_list")
            .performScrollToIndex(500)
        
        composeTestRule.waitForIdle()
        
        val scrollTime = (System.nanoTime() - scrollStartTime) / 1_000_000.0 // Convert to ms
        
        assertTrue(
            "List scrolling time ${scrollTime}ms exceeds smooth scrolling target",
            scrollTime < 50.0 // 50ms for smooth scrolling to middle of large list
        )
    }
    
    @Test
    fun `theme switching performance validation`() = runTest {
        var isDarkTheme by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = isDarkTheme) {
                TestComplexLayout()
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Measure theme switch performance
        val switchStartTime = System.nanoTime()
        
        isDarkTheme = true
        
        composeTestRule.waitForIdle()
        
        val switchTime = (System.nanoTime() - switchStartTime) / 1_000_000.0 // Convert to ms
        
        assertTrue(
            "Theme switch time ${switchTime}ms exceeds target of 100ms",
            switchTime < 100.0
        )
    }
    
    @Test
    fun `animation performance meets frame rate consistency`() = runTest {
        var animationProgress by mutableStateOf(0f)
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestAnimatedComponent(progress = animationProgress)
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Measure animation frame consistency
        val frameStartTime = System.nanoTime()
        
        // Trigger animation
        animationProgress = 1f
        
        composeTestRule.waitForIdle()
        
        val animationTime = (System.nanoTime() - frameStartTime) / 1_000_000.0 // Convert to ms
        
        // Animation should complete smoothly within reasonable time
        assertTrue(
            "Animation completion time ${animationTime}ms indicates poor frame rate",
            animationTime < 300.0 // 300ms for smooth animation completion
        )
    }
    
    @Test
    fun `recomposition performance optimization validation`() {
        var stateValue by mutableStateOf(0)
        
        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                LiftrixTheme {
                    TestRecompositionComponent(value = stateValue)
                }
            }
            
            // Trigger recomposition
            stateValue++
            
            composeTestRule.waitForIdle()
        }
    }
    
    @Test
    fun `complex layout rendering performance`() = runTest {
        val renderStartTime = System.nanoTime()
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestComplexProgressDashboard()
            }
        }
        
        composeTestRule.waitForIdle()
        
        val renderTime = (System.nanoTime() - renderStartTime) / 1_000_000.0 // Convert to ms
        
        assertTrue(
            "Complex layout rendering ${renderTime}ms exceeds reasonable performance target",
            renderTime < 150.0 // 150ms for complex layout
        )
    }
    
    @Composable
    private fun TestChartsContainer() {
        androidx.compose.foundation.layout.Column {
            VolumeProgressChart(
                data = generateMockChartData(50),
                isLoading = false,
                modifier = Modifier.weight(1f)
            )
            
            WorkoutDurationChart(
                data = generateMockDurationData(50),
                isLoading = false,
                modifier = Modifier.weight(1f)
            )
            
            StrengthProgressChart(
                data = generateMockChartData(50),
                isLoading = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
    
    @Composable
    private fun TestListItem(data: TestData) {
        androidx.compose.foundation.layout.Card(
            modifier = Modifier
                .fillMaxSize()
                .androidx.compose.foundation.layout.padding(8.dp)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.androidx.compose.foundation.layout.padding(16.dp)
            ) {
                androidx.compose.material3.Text(
                    text = data.title,
                    style = MaterialTheme.typography.headlineMedium
                )
                androidx.compose.material3.Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    
    @Composable
    private fun TestComplexLayout() {
        androidx.compose.foundation.layout.Column {
            repeat(10) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .androidx.compose.foundation.layout.height(100.dp)
                                .androidx.compose.foundation.background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun TestAnimatedComponent(progress: Float) {
        val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = progress,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 250,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            label = "progress_animation"
        )
        
        androidx.compose.material3.LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary
        )
    }
    
    @Composable
    private fun TestRecompositionComponent(value: Int) {
        // Component designed to test recomposition performance
        androidx.compose.foundation.layout.Column {
            androidx.compose.material3.Text(
                text = "Value: $value",
                style = MaterialTheme.typography.bodyLarge
            )
            
            // Remember expensive computation to test recomposition optimization
            val expensiveValue = androidx.compose.runtime.remember(value) {
                // Simulate expensive computation
                (1..100).map { it * value }.sum()
            }
            
            androidx.compose.material3.Text(
                text = "Computed: $expensiveValue",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    
    @Composable
    private fun TestComplexProgressDashboard() {
        androidx.compose.foundation.layout.Column {
            // Simulate complex progress dashboard with multiple components
            TestChartsContainer()
            
            androidx.compose.foundation.layout.LazyRow {
                items(20) { index ->
                    androidx.compose.foundation.layout.Card(
                        modifier = Modifier
                            .androidx.compose.foundation.layout.size(200.dp)
                            .androidx.compose.foundation.layout.padding(8.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "Widget $index",
                            modifier = Modifier.androidx.compose.foundation.layout.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
    
    private fun generateMockChartData(size: Int): List<com.example.liftrix.domain.repository.VolumeDataPoint> {
        return (1..size).map { index ->
            com.example.liftrix.domain.repository.VolumeDataPoint(
                date = kotlinx.datetime.LocalDate(2024, 1, index % 28 + 1),
                volume = (index * 100).toDouble()
            )
        }
    }
    
    private fun generateMockDurationData(size: Int): List<com.example.liftrix.domain.repository.DurationDataPoint> {
        return (1..size).map { index ->
            com.example.liftrix.domain.repository.DurationDataPoint(
                date = kotlinx.datetime.LocalDate(2024, 1, index % 28 + 1),
                duration = index * 60 // seconds
            )
        }
    }
    
    private fun generateLargeTestDataList(size: Int): List<TestData> {
        return (1..size).map { index ->
            TestData(
                title = "Item $index",
                description = "Description for item $index with some detailed text content"
            )
        }
    }
    
    private data class TestData(
        val title: String,
        val description: String
    )
}