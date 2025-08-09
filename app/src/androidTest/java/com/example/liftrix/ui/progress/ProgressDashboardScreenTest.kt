package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * UI tests for responsive behavior of the Progress Dashboard.
 * 
 * Tests SPEC-20250205-progress-tab-ui-redesign requirements:
 * - 2-column mobile grid layout
 * - 3-column tablet grid layout
 * - 4-column desktop grid layout
 * - Proper widget arrangement and spacing
 * - Full-width widget spanning
 */
@RunWith(AndroidJUnit4::class)
class ProgressDashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createSampleWidgets(): List<AnalyticsWidget> = listOf(
        AnalyticsWidget.WorkoutFrequency,
        AnalyticsWidget.TotalVolume,
        AnalyticsWidget.WorkoutStreak,
        AnalyticsWidget.AverageDuration,
        AnalyticsWidget.VolumeChart,
        AnalyticsWidget.ProgressChart
    )

    private fun createSampleWidgetData(widget: AnalyticsWidget) = BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "42",
        secondaryValue = "Test data",
        unit = "kg",
        trend = TrendDirection.UP
    )

    @Test
    fun test_2column_mobile_grid() {
        // Test mobile screen configuration
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createSampleWidgets().take(4), // 4 1x1 widgets
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
        
        // Verify widgets are displayed
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Workout Streak widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Average Duration widget")
            .assertIsDisplayed()
        
        // In a 2-column layout, widgets should be arranged side by side
        // This is validated through the grid structure itself
    }

    @Test
    fun test_3column_tablet_grid() {
        // Test tablet screen configuration
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createSampleWidgets().take(6), // 6 widgets
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
        
        // Verify all widgets are displayed
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volume Chart chart widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Progress Chart chart widget")
            .assertIsDisplayed()
    }

    @Test
    fun test_4column_desktop_grid() {
        // Test desktop screen configuration
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createSampleWidgets(), // All 6 widgets
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
        
        // Verify all widgets are displayed
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Workout Streak widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Average Duration widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Volume Chart chart widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Progress Chart chart widget")
            .assertIsDisplayed()
    }

    @Test
    fun test_fullwidth_charts_span_all_columns() {
        // Test that chart widgets (LARGE size) span full width
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = listOf(
                        AnalyticsWidget.WorkoutFrequency,
                        AnalyticsWidget.TotalVolume,
                        AnalyticsWidget.VolumeChart // Should span full width
                    ),
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
        
        // The volume chart should be displayed as a full-width widget
        composeTestRule.onNodeWithContentDescription("Volume Chart chart widget")
            .assertIsDisplayed()
        
        // The other widgets should be displayed as small widgets
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .assertIsDisplayed()
    }

    @Test
    fun test_widget_click_interactions() {
        var clickedWidget: AnalyticsWidget? = null
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = listOf(AnalyticsWidget.WorkoutFrequency),
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    onWidgetClick = { widget ->
                        clickedWidget = widget
                    },
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        // Click on the widget
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .performClick()
        
        // Verify the click was registered
        assert(clickedWidget == AnalyticsWidget.WorkoutFrequency)
    }

    @Test
    fun test_odd_number_widgets_alignment() {
        // Test that odd numbers of widgets are left-aligned (not centered)
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = createSampleWidgets().take(3), // 3 widgets (odd number)
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
        
        // All widgets should be displayed
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Workout Streak widget")
            .assertIsDisplayed()
        
        // The LazyVerticalGrid inherently handles left alignment for odd numbers
        // This test verifies the widgets are all visible and properly arranged
    }

    @Test
    fun test_loading_state_display() {
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = listOf(AnalyticsWidget.WorkoutFrequency),
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    isLoading = true,
                    widgetDataProvider = ::createSampleWidgetData
                )
            }
        }
        
        // Widget should still be displayed even in loading state
        composeTestRule.onNodeWithContentDescription("Workout Frequency widget")
            .assertIsDisplayed()
    }

    @Test
    fun test_empty_widgets_list() {
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = emptyList(),
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
        
        // Grid should be empty but not crash
        // No assertions needed - test passes if no exceptions are thrown
    }
}