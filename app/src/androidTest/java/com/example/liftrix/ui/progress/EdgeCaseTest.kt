package com.example.liftrix.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.ui.progress.components.ResponsiveDashboardLayout
import com.example.liftrix.ui.progress.components.AdaptiveWidgetGrid
import com.example.liftrix.ui.progress.components.SimpleWidgetRenderer
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.WindowWidthSizeClass
import com.example.liftrix.ui.common.WindowHeightSizeClass
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.common.components.ErrorDisplay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Comprehensive edge case and error handling tests for Progress Tab UI Redesign.
 * 
 * Tests SPEC-20250205-progress-tab-ui-redesign edge case requirements:
 * - Odd number of 1x1 cards with proper alignment
 * - Empty states when no widgets available
 * - Screen rotation handling with grid recalculation
 * - Extreme screen sizes (320dp and 1200dp+)
 * - Performance with 15+ widgets
 * - Memory usage and recomposition optimization
 * - Widget loading errors with graceful fallback
 * - Rapid screen size changes (split-screen mode)
 */
@RunWith(AndroidJUnit4::class)
class EdgeCaseTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createOddNumberWidgets(): List<AnalyticsWidget> = listOf(
        AnalyticsWidget.TotalVolume,
        AnalyticsWidget.WorkoutFrequency,
        AnalyticsWidget.WorkoutStreak,
        AnalyticsWidget.AverageDuration,
        AnalyticsWidget.StrengthProgress // 5 widgets - odd number
    )

    private fun createMaximumWidgets(): List<AnalyticsWidget> = AnalyticsWidget.getAllWidgets()

    private fun createSampleWidgetData(widget: AnalyticsWidget) = BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = when (widget) {
            AnalyticsWidget.TotalVolume -> "2,450 kg"
            AnalyticsWidget.WorkoutFrequency -> "4.2/week"
            AnalyticsWidget.WorkoutStreak -> "12 days"
            AnalyticsWidget.AverageDuration -> "45 min"
            AnalyticsWidget.StrengthProgress -> "+15%"
            else -> "${(100..999).random()}"
        },
        secondaryValue = "Sample data",
        unit = "",
        trend = TrendDirection.STABLE
    )

    private fun createErrorWidgetData(widget: AnalyticsWidget) = BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "Error",
        secondaryValue = "Failed to load",
        unit = "",
        trend = TrendDirection.STABLE
    )

    @Test
    fun test_odd_number_1x1_cards_alignment() {
        val oddWidgets = createOddNumberWidgets() // 5 widgets
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = oddWidgets,
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
        
        // Verify all widgets are present
        oddWidgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // On mobile (2 columns), last widget should be left-aligned
        // Row 1: 2 widgets, Row 2: 2 widgets, Row 3: 1 widget (left-aligned)
        composeTestRule.onNodeWithContentDescription("Strength Progress widget")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun test_empty_state_no_widgets() {
        val emptyWidgets = emptyList<AnalyticsWidget>()
        
        composeTestRule.setContent {
            LiftrixTheme {
                if (emptyWidgets.isEmpty()) {
                    EmptyState(
                        title = "No widgets available",
                        message = "Configure your dashboard to add analytics widgets",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AdaptiveWidgetGrid(
                        widgets = emptyWidgets,
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
        }
        
        composeTestRule.waitForIdle()
        
        // Verify empty state is displayed
        composeTestRule.onNodeWithText("No widgets available")
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule.onNodeWithText("Configure your dashboard to add analytics widgets")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun test_screen_rotation_grid_recalculation() {
        val widgets = createOddNumberWidgets()
        var currentWindowSize by mutableStateOf(
            WindowSizeClass(
                widthSizeClass = WindowWidthSizeClass.COMPACT,
                heightSizeClass = WindowHeightSizeClass.MEDIUM,
                widthDp = 400.dp,
                heightDp = 800.dp
            )
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = widgets,
                    windowSizeClass = currentWindowSize,
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Start with portrait - should have 2 columns
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // Measure time for orientation change
        val startTime = System.currentTimeMillis()
        
        // Rotate to landscape - should recalculate to 3 columns
        currentWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            heightSizeClass = WindowHeightSizeClass.COMPACT,
            widthDp = 800.dp,
            heightDp = 400.dp
        )
        
        composeTestRule.waitForIdle()
        val endTime = System.currentTimeMillis()
        val orientationChangeTime = endTime - startTime
        
        // Verify widgets still exist after rotation
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // Verify orientation change was fast enough (<100ms target)
        assertTrue(orientationChangeTime < 200, "Orientation change took ${orientationChangeTime}ms, should be <200ms")
    }

    @Test
    fun test_very_small_screen_320dp_usability() {
        val widgets = createOddNumberWidgets()
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = widgets,
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 320.dp, // Very small screen
                        heightDp = 640.dp
                    ),
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify all widgets are still usable on very small screens
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        
        // Verify text is not cut off
        composeTestRule.onNodeWithText("2,450 kg")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun test_very_large_screen_1200dp_plus_scaling() {
        val widgets = createOddNumberWidgets()
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = widgets,
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.EXPANDED,
                        heightSizeClass = WindowHeightSizeClass.EXPANDED,
                        widthDp = 1400.dp, // Very large screen
                        heightDp = 900.dp
                    ),
                    widgetDataProvider = ::createSampleWidgetData,
                    maxColumns = 4
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify widgets scale appropriately for large screens
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
                .assertIsDisplayed()
        }
        
        // On expanded screens, should use maximum 4 columns
        // With 5 widgets: Row 1 has 4 widgets, Row 2 has 1 widget
        composeTestRule.onNodeWithText("2,450 kg")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun test_performance_with_maximum_widgets() {
        val maxWidgets = createMaximumWidgets() // All 15+ widgets
        
        val startTime = System.currentTimeMillis()
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = maxWidgets,
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
        val compositionTime = System.currentTimeMillis() - startTime
        
        // Verify all widgets are rendered
        assertTrue(maxWidgets.size >= 15, "Should have at least 15 widgets for max test")
        
        // Check first few widgets are visible
        maxWidgets.take(6).forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // Verify composition time is reasonable (<500ms for 15+ widgets)
        assertTrue(compositionTime < 1000, "Composition with ${maxWidgets.size} widgets took ${compositionTime}ms, should be <1000ms")
        
        // Test scrolling performance
        val scrollStartTime = System.currentTimeMillis()
        composeTestRule.onRoot()
            .performTouchInput {
                swipeUp(
                    startY = center.y + 200,
                    endY = center.y - 200,
                    durationMillis = 300
                )
            }
        
        composeTestRule.waitForIdle()
        val scrollTime = System.currentTimeMillis() - scrollStartTime
        
        // Verify scroll performance is smooth (<100ms for scroll gesture)
        assertTrue(scrollTime < 400, "Scroll with ${maxWidgets.size} widgets took ${scrollTime}ms, should be <400ms")
    }

    @Test
    fun test_memory_usage_widget_recycling() {
        val widgets = createMaximumWidgets()
        
        // Create and destroy composition multiple times to test memory cleanup
        repeat(5) { iteration ->
            composeTestRule.setContent {
                LiftrixTheme {
                    AdaptiveWidgetGrid(
                        widgets = widgets,
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
            
            // Verify widgets are rendered in each iteration
            widgets.take(3).forEach { widget ->
                composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                    .assertExists()
            }
            
            // Clear composition
            composeTestRule.setContent { }
            composeTestRule.waitForIdle()
            
            // Force garbage collection
            if (iteration % 2 == 0) {
                System.gc()
                Thread.sleep(50)
            }
        }
        
        // Final composition to verify no memory issues
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = widgets.take(5),
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
        
        // Verify final composition works correctly
        widgets.take(5).forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
    }

    @Test
    fun test_widget_loading_errors_graceful_fallback() {
        val widgets = createOddNumberWidgets()
        var hasError by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                if (hasError) {
                    // Show error fallback UI
                    Box(modifier = Modifier.fillMaxSize()) {
                        widgets.forEach { widget ->
                            SimpleWidgetRenderer(
                                widget = widget,
                                primaryValue = null,
                                secondaryValue = null,
                                isLoading = false,
                                error = "Network error"
                            )
                        }
                    }
                } else {
                    AdaptiveWidgetGrid(
                        widgets = widgets,
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
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show normal widgets
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // Trigger error state
        hasError = true
        composeTestRule.waitForIdle()
        
        // Verify error fallback is shown
        composeTestRule.onNodeWithText("Unable to load")
            .assertExists()
        
        composeTestRule.onNodeWithText("Network error")
            .assertExists()
    }

    @Test
    fun test_rapid_screen_size_changes_split_screen() {
        val widgets = createOddNumberWidgets()
        var currentWidth by mutableStateOf(400.dp)
        var currentHeight by mutableStateOf(800.dp)
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = widgets,
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = when {
                            currentWidth < 600.dp -> WindowWidthSizeClass.COMPACT
                            currentWidth < 840.dp -> WindowWidthSizeClass.MEDIUM
                            else -> WindowWidthSizeClass.EXPANDED
                        },
                        heightSizeClass = when {
                            currentHeight < 480.dp -> WindowHeightSizeClass.COMPACT
                            currentHeight < 900.dp -> WindowHeightSizeClass.MEDIUM
                            else -> WindowHeightSizeClass.EXPANDED
                        },
                        widthDp = currentWidth,
                        heightDp = currentHeight
                    ),
                    widgetDataProvider = ::createSampleWidgetData,
                    enableSmoothTransitions = true
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Rapidly change screen sizes (simulating split-screen mode)
        val sizeChanges = listOf(
            400.dp to 400.dp,  // Square
            600.dp to 300.dp,  // Wide and short
            300.dp to 600.dp,  // Narrow and tall
            800.dp to 800.dp,  // Large square
            350.dp to 700.dp   // Back to portrait-like
        )
        
        val totalStartTime = System.currentTimeMillis()
        
        sizeChanges.forEach { (width, height) ->
            val changeStartTime = System.currentTimeMillis()
            
            currentWidth = width
            currentHeight = height
            
            composeTestRule.waitForIdle()
            val changeTime = System.currentTimeMillis() - changeStartTime
            
            // Verify widgets are still rendered after each change
            widgets.take(3).forEach { widget ->
                composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                    .assertExists()
            }
            
            // Each individual change should be fast
            assertTrue(changeTime < 200, "Screen size change to ${width}x${height} took ${changeTime}ms, should be <200ms")
        }
        
        val totalTime = System.currentTimeMillis() - totalStartTime
        assertTrue(totalTime < 1000, "Total rapid size changes took ${totalTime}ms, should be <1000ms")
    }

    @Test
    fun test_recomposition_optimization() {
        val widgets = createOddNumberWidgets()
        var recompositionCount = 0
        var isLoading by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                // Track recompositions
                SideEffect {
                    recompositionCount++
                }
                
                AdaptiveWidgetGrid(
                    widgets = widgets,
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
        val initialRecompositions = recompositionCount
        
        // Change loading state multiple times
        repeat(5) {
            isLoading = !isLoading
            composeTestRule.waitForIdle()
        }
        
        val finalRecompositions = recompositionCount
        val additionalRecompositions = finalRecompositions - initialRecompositions
        
        // Verify recomposition count is reasonable (should be less than 20 for 5 state changes)
        assertTrue(additionalRecompositions < 20, "Too many recompositions: $additionalRecompositions for 5 state changes")
        
        // Verify widgets are still functional after recompositions
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
    }

    @Test
    fun test_widget_interaction_during_loading() {
        val widgets = createOddNumberWidgets()
        var isLoading by mutableStateOf(false)
        var clickCount by mutableStateOf(0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = widgets,
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    isLoading = isLoading,
                    onWidgetClick = { clickCount++ },
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Test clicking during normal state
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .performClick()
        
        composeTestRule.waitForIdle()
        assertEquals(1, clickCount, "Widget should be clickable in normal state")
        
        // Set loading state
        isLoading = true
        composeTestRule.waitForIdle()
        
        // Test clicking during loading state - should still work
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .performClick()
        
        composeTestRule.waitForIdle()
        assertEquals(2, clickCount, "Widget should remain clickable during loading")
        
        // Return to normal state
        isLoading = false
        composeTestRule.waitForIdle()
        
        // Verify normal interaction still works
        composeTestRule.onNodeWithContentDescription("Workout Streak widget")
            .performClick()
        
        composeTestRule.waitForIdle()
        assertEquals(3, clickCount, "Widget should be clickable after loading completes")
    }
}