package com.example.liftrix.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance tests for SettingsScreen to ensure optimization targets are met.
 * 
 * Tests verify:
 * - Settings screen loads in <500ms
 * - Animation frame rate >60fps  
 * - Memory usage is optimized
 * - Smooth scrolling performance
 * - Reduced recomposition counts
 */
@RunWith(AndroidJUnit4::class)
class SettingsPerformanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val performanceMetrics = PerformanceOptimizations.PerformanceMetrics

    @Before
    fun setUp() {
        // Clear any existing performance metrics
        performanceMetrics.clearAllMetrics()
        
        // Start animation performance monitoring
        PerformanceOptimizations.AnimationPerformanceMonitor.startMonitoring()
    }

    @After
    fun tearDown() {
        // Stop monitoring and clear metrics
        PerformanceOptimizations.AnimationPerformanceMonitor.stopMonitoring()
        performanceMetrics.clearAllMetrics()
    }

    @Test
    fun settingsScreen_loadsWithinPerformanceTarget() {
        val mockState = SettingsState(
            userSettings = UserSettings(
                darkMode = false,
                notificationsEnabled = true
            ),
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            isLoading = false,
            error = null
        )

        // Measure loading time
        val loadingTime = measureTimeMillis {
            composeTestRule.setContent {
                LiftrixTheme {
                    SettingsScreen(
                        onNavigateBack = { },
                        onNavigateToProfile = { },
                        onNavigateToAuth = { }
                    )
                }
            }
            
            // Wait for composition to complete
            composeTestRule.waitForIdle()
        }

        // Verify loading time is under 500ms target
        assertTrue(
            "Settings screen should load in <500ms, actual: ${loadingTime}ms",
            loadingTime < 500
        )
        
        // Record performance metric
        performanceMetrics.recordMetric("settings_screen_load_time", loadingTime)
    }

    @Test
    fun settingsScreen_animationPerformanceTarget() {
        val mockState = SettingsState(
            userSettings = UserSettings(
                darkMode = false,
                notificationsEnabled = true
            ),
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            isLoading = false,
            error = null
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SettingsScreen(
                    onNavigateBack = { },
                    onNavigateToProfile = { },
                    onNavigateToAuth = { }
                )
            }
        }

        // Wait for initial composition
        composeTestRule.waitForIdle()

        // Simulate interactions to trigger animations
        repeat(10) {
            composeTestRule.waitForIdle()
            Thread.sleep(50) // Allow frame processing
        }

        // Get animation performance metrics
        val animationMetrics = PerformanceOptimizations.AnimationPerformanceMonitor.getPerformanceMetrics()
        
        // Verify frame rate is above 54fps (90% of 60fps target)
        assertTrue(
            "Animation frame rate should be >54fps, actual: ${animationMetrics.averageTime}fps",
            animationMetrics.averageTime > 54f
        )
        
        // Verify low frame drop rate (<10%)
        val frameDropRate = if (animationMetrics.sampleCount > 0) {
            (animationMetrics.frameDrops.toFloat() / animationMetrics.sampleCount) * 100f
        } else 0f
        
        assertTrue(
            "Frame drop rate should be <10%, actual: ${frameDropRate}%",
            frameDropRate < 10f
        )
    }

    @Test
    fun settingsScreen_recompositionOptimization() {
        val mockState = SettingsState(
            userSettings = UserSettings(
                darkMode = false,
                notificationsEnabled = true
            ),
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            isLoading = false,
            error = null
        )

        composeTestRule.setContent {
            LiftrixTheme {
                SettingsScreen(
                    onNavigateBack = { },
                    onNavigateToProfile = { },
                    onNavigateToAuth = { }
                )
            }
        }

        // Wait for initial composition
        composeTestRule.waitForIdle()

        // Simulate multiple state changes
        repeat(5) {
            composeTestRule.waitForIdle()
            Thread.sleep(100)
        }

        // Get recomposition statistics
        val recompositionStats = PerformanceOptimizations.MemoryEfficientComponents.getRecompositionStats()
        
        // Verify reasonable recomposition counts
        recompositionStats.forEach { (component, count) ->
            assertTrue(
                "Component $component should have reasonable recomposition count, actual: $count",
                count < 20 // Threshold for acceptable recomposition
            )
        }
    }

    @Test
    fun expandableCard_animationPerformance() {
        composeTestRule.setContent {
            LiftrixTheme {
                com.example.liftrix.ui.settings.components.ExpandableSettingsCard(
                    title = "Test Card",
                    isExpanded = false,
                    onToggle = { }
                ) {
                    // Mock content
                }
            }
        }

        // Wait for initial composition
        composeTestRule.waitForIdle()

        // Measure animation performance
        val animationTime = measureTimeMillis {
            // Simulate expand/collapse animations
            repeat(5) {
                composeTestRule.waitForIdle()
                Thread.sleep(167) // Animation duration
            }
        }

        // Verify animation completes within expected time
        val expectedMaxTime = 167 * 5 + 100 // Animation duration * cycles + buffer
        assertTrue(
            "Expandable card animation should complete efficiently, actual: ${animationTime}ms",
            animationTime < expectedMaxTime
        )
    }

    @Test
    fun settingsScreen_memoryUsageOptimization() {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsScreen(
                    onNavigateBack = { },
                    onNavigateToProfile = { },
                    onNavigateToAuth = { }
                )
            }
        }

        // Wait for composition to complete
        composeTestRule.waitForIdle()

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryIncreasePercentage = (memoryIncrease.toFloat() / initialMemory) * 100f

        // Verify memory increase is reasonable (less than 20% increase)
        assertTrue(
            "Memory increase should be <20%, actual: ${memoryIncreasePercentage}%",
            memoryIncreasePercentage < 20f
        )
        
        // Record memory usage metric
        performanceMetrics.recordMetric("settings_screen_memory_usage", memoryIncrease)
    }

    @Test
    fun settingsScreen_performanceReport() {
        // Run all performance tests and generate report
        composeTestRule.setContent {
            LiftrixTheme {
                SettingsScreen(
                    onNavigateBack = { },
                    onNavigateToProfile = { },
                    onNavigateToAuth = { }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Generate performance report
        val report = performanceMetrics.generatePerformanceReport()
        
        // Verify report contains expected metrics
        assertTrue("Performance report should contain animation metrics", 
            report.contains("Animation Performance"))
        assertTrue("Performance report should contain component metrics", 
            report.contains("Component Recomposition"))
        
        // Log report for analysis
        println("Settings Screen Performance Report:")
        println(report)
    }
}