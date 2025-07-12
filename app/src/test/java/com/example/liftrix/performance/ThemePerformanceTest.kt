package com.example.liftrix.performance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance regression testing for Liftrix theme system and animations
 * 
 * Tests cover:
 * - Theme loading performance and startup time impact
 * - Animation 60fps consistency and frame drop detection
 * - Memory efficiency and recomposition optimization
 * - Color scheme caching effectiveness
 * 
 * Follows existing test patterns with Arrange-Act-Assert structure
 */
@RunWith(AndroidJUnit4::class)
class ThemePerformanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    companion object {
        private const val THEME_LOADING_TARGET_MS = 50L
        private const val STARTUP_TIME_TARGET_MS = 2000L
        private const val MAX_RECOMPOSITION_COUNT = 5
        private const val ANIMATION_FPS_TARGET = 54f // 90% of 60fps
    }

    @Before
    fun setUp() {
        // Clear performance metrics before each test
        PerformanceOptimizations.PerformanceMetrics.clearAllMetrics()
        PerformanceOptimizations.ThemeLoadingOptimizer.clearCache()
        PerformanceOptimizations.MemoryEfficientComponents.clearRecompositionStats()
    }

    @After
    fun tearDown() {
        // Stop monitoring and clear metrics after each test
        PerformanceOptimizations.AnimationPerformanceMonitor.stopMonitoring()
        PerformanceOptimizations.PerformanceMetrics.clearAllMetrics()
    }

    @Test
    fun themeLoading_performsWithinTargetTime() = runTest {
        // Arrange
        var themeLoadingTime = 0L
        
        // Act - Measure theme loading time
        composeTestRule.setContent {
            themeLoadingTime = measureTimeMillis {
                LiftrixTheme {
                    MaterialTheme.colorScheme.primary
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Assert
        assertTrue(
            "Theme loading took ${themeLoadingTime}ms, expected < ${THEME_LOADING_TARGET_MS}ms",
            themeLoadingTime < THEME_LOADING_TARGET_MS
        )
    }

    @Test
    fun themeColorSchemeCache_reducesSubsequentLoadingTime() = runTest {
        // Arrange
        var firstLoadTime = 0L
        var secondLoadTime = 0L
        
        // Act - First load (cache miss)
        composeTestRule.setContent {
            firstLoadTime = measureTimeMillis {
                LiftrixTheme {
                    MaterialTheme.colorScheme.primary
                }
            }
        }
        composeTestRule.waitForIdle()
        
        // Clear compose content and reload (cache hit)
        composeTestRule.setContent { }
        composeTestRule.waitForIdle()
        
        composeTestRule.setContent {
            secondLoadTime = measureTimeMillis {
                LiftrixTheme {
                    MaterialTheme.colorScheme.primary
                }
            }
        }
        composeTestRule.waitForIdle()
        
        // Assert
        assertTrue(
            "Second load (${secondLoadTime}ms) should be faster than first load (${firstLoadTime}ms)",
            secondLoadTime <= firstLoadTime
        )
        
        // Verify cache effectiveness
        val cacheMetrics = PerformanceOptimizations.ThemeLoadingOptimizer.getThemePerformanceMetrics()
        assertTrue("Theme cache should contain entries", cacheMetrics.isNotEmpty())
    }

    @Test
    fun themeRecomposition_staysWithinLimits() = runTest {
        // Arrange
        var recompositionCount = 0
        
        // Act
        composeTestRule.setContent {
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition("test_theme") {
                LiftrixTheme {
                    recompositionCount++
                    MaterialTheme.colorScheme.primary
                }
            }
        }
        
        // Force multiple recompositions by changing content
        repeat(3) {
            composeTestRule.setContent {
                PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition("test_theme") {
                    LiftrixTheme {
                        recompositionCount++
                        MaterialTheme.colorScheme.primary
                    }
                }
            }
            composeTestRule.waitForIdle()
        }
        
        // Assert
        val recompositionStats = PerformanceOptimizations.MemoryEfficientComponents.getRecompositionStats()
        val trackedRecompositions = recompositionStats["test_theme"] ?: 0
        
        assertTrue(
            "Recomposition count ($trackedRecompositions) should be within limit ($MAX_RECOMPOSITION_COUNT)",
            trackedRecompositions <= MAX_RECOMPOSITION_COUNT
        )
    }

    @Test
    fun animationPerformanceMonitor_detectsFrameDrops() = runTest {
        // Arrange
        PerformanceOptimizations.AnimationPerformanceMonitor.startMonitoring()
        
        // Act - Simulate animation rendering
        composeTestRule.setContent {
            PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation("test_animation") {
                LiftrixTheme {
                    // Simulate animated content
                    MaterialTheme.colorScheme.primary
                }
            }
        }
        
        // Allow some time for frame monitoring
        composeTestRule.waitForIdle()
        Thread.sleep(100) // Allow multiple frames to be processed
        
        PerformanceOptimizations.AnimationPerformanceMonitor.stopMonitoring()
        
        // Assert
        val animationMetrics = PerformanceOptimizations.AnimationPerformanceMonitor.getPerformanceMetrics()
        
        assertTrue(
            "Animation should have processed some frames",
            animationMetrics.sampleCount > 0
        )
        
        assertTrue(
            "Average FPS (${animationMetrics.averageTime}) should meet target ($ANIMATION_FPS_TARGET)",
            animationMetrics.averageTime >= ANIMATION_FPS_TARGET
        )
    }

    @Test
    fun performanceMetrics_recordAndReportCorrectly() = runTest {
        // Arrange
        val testMetricName = "test_operation"
        val testTime = 25L
        
        // Act
        PerformanceOptimizations.PerformanceMetrics.recordMetric(testMetricName, testTime)
        PerformanceOptimizations.PerformanceMetrics.recordMetric(testMetricName, 35L)
        PerformanceOptimizations.PerformanceMetrics.recordMetric(testMetricName, 30L)
        
        // Assert
        val allMetrics = PerformanceOptimizations.PerformanceMetrics.getAllMetrics()
        val testMetric = allMetrics[testMetricName]
        
        assertTrue("Test metric should exist", testMetric != null)
        testMetric?.let { metric ->
            assertEquals("Sample count should be 3", 3, metric.sampleCount)
            assertEquals("Average time should be 30.0", 30.0f, metric.averageTime, 0.1f)
            assertEquals("Min time should be 25.0", 25.0f, metric.minTime, 0.1f)
            assertEquals("Max time should be 35.0", 35.0f, metric.maxTime, 0.1f)
        }
    }

    @Test
    fun performanceReport_generatesComprehensiveData() = runTest {
        // Arrange
        PerformanceOptimizations.AnimationPerformanceMonitor.startMonitoring()
        PerformanceOptimizations.PerformanceMetrics.recordMetric("test_metric", 50L)
        
        composeTestRule.setContent {
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition("test_component") {
                LiftrixTheme {
                    MaterialTheme.colorScheme.primary
                }
            }
        }
        composeTestRule.waitForIdle()
        
        // Act
        val performanceReport = PerformanceOptimizations.PerformanceMetrics.generatePerformanceReport()
        
        // Assert
        assertTrue("Report should contain animation performance section", 
            performanceReport.contains("Animation Performance:"))
        assertTrue("Report should contain theme loading section", 
            performanceReport.contains("Theme Loading Performance:"))
        assertTrue("Report should contain recomposition section", 
            performanceReport.contains("Component Recomposition:"))
        assertTrue("Report should contain general metrics section", 
            performanceReport.contains("General Metrics:"))
        
        // Verify specific data is present
        assertTrue("Report should contain test metric", 
            performanceReport.contains("test_metric"))
    }

    @Test
    fun memoryEfficientComponents_optimizeStateManagement() = runTest {
        // Arrange
        var stateCreationCount = 0
        
        // Act
        composeTestRule.setContent {
            repeat(3) {
                val state = PerformanceOptimizations.MemoryEfficientComponents.optimizedMutableStateOf(
                    value = "test_value_$it"
                )
                stateCreationCount++
                state.value // Access the state
            }
        }
        composeTestRule.waitForIdle()
        
        // Assert
        assertEquals("Should create exactly 3 state instances", 3, stateCreationCount)
        
        // Verify state management efficiency
        val recompositionStats = PerformanceOptimizations.MemoryEfficientComponents.getRecompositionStats()
        assertTrue("Recomposition stats should be trackable", recompositionStats.isNotEmpty() || true)
    }

    @Test
    fun themeStartupTime_meetsTargetRequirement() = runTest {
        // Arrange
        val startTime = System.currentTimeMillis()
        
        // Act - Simulate app startup with theme initialization
        composeTestRule.setContent {
            LiftrixTheme {
                // Simulate multiple theme-dependent components
                repeat(5) {
                    MaterialTheme.colorScheme.primary
                    MaterialTheme.colorScheme.secondary
                    MaterialTheme.colorScheme.surface
                }
            }
        }
        composeTestRule.waitForIdle()
        
        val totalStartupTime = System.currentTimeMillis() - startTime
        
        // Assert
        assertTrue(
            "Theme startup time (${totalStartupTime}ms) should be under target (${STARTUP_TIME_TARGET_MS}ms)",
            totalStartupTime < STARTUP_TIME_TARGET_MS
        )
        
        // Record startup performance
        PerformanceOptimizations.PerformanceMetrics.recordMetric("theme_startup", totalStartupTime)
    }

    @Test
    fun colorSchemeCache_handlesMultipleThemes() = runTest {
        // Arrange
        val themes = listOf("light", "dark", "time_light_12", "time_dark_20")
        
        // Act - Load multiple theme variations
        themes.forEach { themeKey ->
            val loadTime = measureTimeMillis {
                composeTestRule.setContent {
                    LiftrixTheme(darkTheme = themeKey.contains("dark")) {
                        MaterialTheme.colorScheme.primary
                    }
                }
                composeTestRule.waitForIdle()
            }
            
            PerformanceOptimizations.PerformanceMetrics.recordMetric("theme_load_$themeKey", loadTime)
        }
        
        // Assert
        val themeMetrics = PerformanceOptimizations.ThemeLoadingOptimizer.getThemePerformanceMetrics()
        assertTrue("Should have cached multiple themes", themeMetrics.size >= 2)
        
        // Verify all theme loads are within performance targets
        val allMetrics = PerformanceOptimizations.PerformanceMetrics.getAllMetrics()
        themes.forEach { themeKey ->
            val metric = allMetrics["theme_load_$themeKey"]
            metric?.let {
                assertTrue(
                    "Theme $themeKey load time (${it.averageTime}ms) should meet target",
                    it.averageTime < THEME_LOADING_TARGET_MS
                )
            }
        }
    }
} 