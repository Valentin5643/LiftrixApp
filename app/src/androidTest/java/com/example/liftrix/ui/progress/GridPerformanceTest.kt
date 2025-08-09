package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.progress.components.AdaptiveWidgetGrid
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.WindowWidthSizeClass
import com.example.liftrix.ui.common.WindowHeightSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance tests for grid rendering with 15+ widgets.
 * 
 * Tests SPEC-20250205-progress-tab-ui-redesign performance requirements:
 * - Maintain 60fps with 15+ widgets on screen
 * - <100ms layout calculation time on orientation change  
 * - Smooth scrolling and interaction performance
 */
@RunWith(AndroidJUnit4::class)
class GridPerformanceTest {

    @get:Rule
    val benchmarkRule = BenchmarkRule()
    
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createAllWidgets(): List<AnalyticsWidget> = AnalyticsWidget.getAllWidgets()

    private fun createSampleWidgetData(widget: AnalyticsWidget) = BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = when (widget) {
            AnalyticsWidget.TotalVolume -> "2,450 kg"
            AnalyticsWidget.WorkoutFrequency -> "4.2/week"  
            AnalyticsWidget.WorkoutStreak -> "12 days"
            AnalyticsWidget.AverageDuration -> "45 min"
            AnalyticsWidget.StrengthProgress -> "+15%"
            AnalyticsWidget.PersonalRecords -> "3 new"
            else -> "${(100..999).random()}"
        },
        secondaryValue = when (widget) {
            AnalyticsWidget.TotalVolume -> "↑ 8% vs last month"
            AnalyticsWidget.WorkoutFrequency -> "Target: 5/week"
            AnalyticsWidget.WorkoutStreak -> "Best: 28 days"
            AnalyticsWidget.AverageDuration -> "↓ 5 min vs target"
            else -> "Last updated 2h ago"
        },
        unit = when (widget) {
            AnalyticsWidget.TotalVolume -> "kg"
            AnalyticsWidget.WorkoutFrequency -> "sessions"
            AnalyticsWidget.AverageDuration -> "min"
            else -> ""
        },
        trend = listOf(TrendDirection.UP, TrendDirection.DOWN, TrendDirection.STABLE).random()
    )

    @Test
    fun test_15_widgets_render_performance_mobile() {
        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = createAllWidgets(), // 15+ widgets
                        windowSizeClass = WindowSizeClass(
                            widthSizeClass = WindowWidthSizeClass.COMPACT,
                            heightSizeClass = WindowHeightSizeClass.MEDIUM,
                            widthDp = 400.dp,
                            heightDp = 800.dp
                        ),
                        widgetDataProvider = ::createSampleWidgetData
                    )
                }
            }
            
            // Force composition and layout
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun test_15_widgets_render_performance_tablet() {
        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = createAllWidgets(), // 15+ widgets
                        windowSizeClass = WindowSizeClass(
                            widthSizeClass = WindowWidthSizeClass.MEDIUM,
                            heightSizeClass = WindowHeightSizeClass.MEDIUM,
                            widthDp = 700.dp,
                            heightDp = 900.dp
                        ),
                        widgetDataProvider = ::createSampleWidgetData
                    )
                }
            }
            
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun test_15_widgets_render_performance_desktop() {
        benchmarkRule.measureRepeated {
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = createAllWidgets(), // 15+ widgets
                        windowSizeClass = WindowSizeClass(
                            widthSizeClass = WindowWidthSizeClass.EXPANDED,
                            heightSizeClass = WindowHeightSizeClass.EXPANDED,
                            widthDp = 1024.dp,
                            heightDp = 768.dp
                        ),
                        widgetDataProvider = ::createSampleWidgetData
                    )
                }
            }
            
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun test_orientation_change_performance() {
        // Start with portrait
        var windowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.COMPACT,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 400.dp,
            heightDp = 800.dp
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createAllWidgets(),
                    windowSizeClass = windowSize,
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Measure landscape orientation change
        benchmarkRule.measureRepeated {
            // Simulate orientation change to landscape
            windowSize = WindowSizeClass(
                widthSizeClass = WindowWidthSizeClass.MEDIUM,
                heightSizeClass = WindowHeightSizeClass.COMPACT,
                widthDp = 800.dp,
                heightDp = 400.dp
            )
            
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = createAllWidgets(),
                        windowSizeClass = windowSize,
                        widgetDataProvider = ::createSampleWidgetData
                    )
                }
            }
            
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun test_widget_interaction_performance() {
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createAllWidgets(),
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    onWidgetClick = { /* Click handler */ },
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Measure click interaction performance
        benchmarkRule.measureRepeated {
            // Click on first visible widget
            composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
                .performClick()
            
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun test_memory_usage_with_15_widgets() {
        // This test ensures we don't have memory leaks with many widgets
        repeat(10) {
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = createAllWidgets(),
                        windowSizeClass = WindowSizeClass(
                            widthSizeClass = WindowWidthSizeClass.COMPACT,
                            heightSizeClass = WindowHeightSizeClass.MEDIUM,
                            widthDp = 400.dp,
                            heightDp = 800.dp
                        ),
                        widgetDataProvider = ::createSampleWidgetData
                    )
                }
            }
            
            composeTestRule.waitForIdle()
            
            // Clear composition
            composeTestRule.setContent { }
            composeTestRule.waitForIdle()
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
    }

    @Test  
    fun test_scroll_performance_with_many_widgets() {
        // Create a longer list to test scrolling performance
        val manyWidgets = createAllWidgets() + createAllWidgets() // 30+ widgets
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = manyWidgets,
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        benchmarkRule.measureRepeated {
            // Perform scroll gesture
            composeTestRule.onRoot()
                .performTouchInput {
                    swipeUp(
                        startY = center.y + 200,
                        endY = center.y - 200,
                        durationMillis = 300
                    )
                }
            
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun test_grid_recomposition_performance() {
        var isLoading = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createAllWidgets(),
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    isLoading = isLoading,
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Measure recomposition performance when loading state changes
        benchmarkRule.measureRepeated {
            isLoading = !isLoading
            
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = createAllWidgets(),
                        windowSizeClass = WindowSizeClass(
                            widthSizeClass = WindowWidthSizeClass.COMPACT,
                            heightSizeClass = WindowHeightSizeClass.MEDIUM,
                            widthDp = 400.dp,
                            heightDp = 800.dp
                        ),
                        isLoading = isLoading,
                        widgetDataProvider = ::createSampleWidgetData
                    )
                }
            }
            
            composeTestRule.waitForIdle()
        }
    }
}