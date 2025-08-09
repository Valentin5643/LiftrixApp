package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.progress.components.WidgetContainer
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.datetime.Clock

/**
 * UI tests for empty state handling in widgets
 * 
 * Tests FR-003 requirement: widgets should show zero values with subtle empty state indicator
 * instead of "No data" messages after the widget system refactoring.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testWidgetContainer_showsZeroValuesInsteadOfNoData() {
        // Given - widgets with empty/zero data
        val emptyWidgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.AverageDuration,
            AnalyticsWidget.WorkoutStreak
        )

        // When - rendering widgets with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = emptyWidgets,
                    configuration = DashboardConfiguration.Beginner,
                    layoutMode = WidgetLayoutMode.SECTIONS,
                    widgetDataProvider = { widget ->
                        createEmptyWidgetData(widget)
                    }
                )
            }
        }

        // Then - should display zero values instead of "No data"
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
        composeTestRule.onNodeWithText("—").assertDoesNotExist()
        
        // Should show zero values
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("0 kg").assertExists()
        composeTestRule.onNodeWithText("0 sessions").assertExists()
        composeTestRule.onNodeWithText("0 min").assertExists()
        composeTestRule.onNodeWithText("0 days").assertExists()
    }

    @Test
    fun testTotalVolumeWidget_displaysZeroValue() {
        // Given - TotalVolume widget with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.TotalVolume),
                    configuration = DashboardConfiguration.Beginner,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - should display "0" instead of empty state
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
    }

    @Test
    fun testWorkoutFrequencyWidget_displaysZeroValue() {
        // Given - WorkoutFrequency widget with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.WorkoutFrequency),
                    configuration = DashboardConfiguration.Beginner,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - should display "0" instead of empty state
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
    }

    @Test
    fun testAverageDurationWidget_displaysZeroValue() {
        // Given - AverageDuration widget with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.AverageDuration),
                    configuration = DashboardConfiguration.Beginner,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - should display "0" instead of empty state
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
    }

    @Test
    fun testWorkoutStreakWidget_displaysZeroValue() {
        // Given - WorkoutStreak widget with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.WorkoutStreak),
                    configuration = DashboardConfiguration.Beginner,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - should display "0" instead of empty state
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
    }

    @Test
    fun testPersonalRecordsWidget_displaysZeroValue() {
        // Given - PersonalRecords widget with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.PersonalRecords),
                    configuration = DashboardConfiguration.Intermediate,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - should display "0" instead of empty state
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
    }

    @Test
    fun testMultipleWidgets_allShowZeroValues() {
        // Given - multiple widgets with zero data
        val widgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.AverageDuration,
            AnalyticsWidget.WorkoutStreak,
            AnalyticsWidget.PersonalRecords
        )

        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = widgets,
                    configuration = DashboardConfiguration.Intermediate,
                    layoutMode = WidgetLayoutMode.GRID,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - should not show any "No data" messages
        composeTestRule.onNodeWithText("No data").assertDoesNotExist()
        composeTestRule.onNodeWithText("—").assertDoesNotExist()
        
        // Should show appropriate zero values and empty state indicators
        composeTestRule.onAllNodesWithText("0").assertCountEquals(5)
        composeTestRule.onAllNodesWithText("No activity yet").assertCountEquals(5)
    }

    @Test
    fun testEmptyStateIndicator_isSubtle() {
        // Given - widget with zero data
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.TotalVolume),
                    configuration = DashboardConfiguration.Beginner,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - empty state indicator should be subtle (not error-like)
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        
        // Should not show error states or prominent empty messages
        composeTestRule.onNodeWithText("Error").assertDoesNotExist()
        composeTestRule.onNodeWithText("Failed to load").assertDoesNotExist()
        composeTestRule.onNodeWithText("Something went wrong").assertDoesNotExist()
    }

    @Test
    fun testChartWidgets_showZeroValueInsteadOfEmptyChart() {
        // Given - chart widgets with zero data
        val chartWidgets = listOf(
            AnalyticsWidget.ProgressChart,
            AnalyticsWidget.VolumeChart,
            AnalyticsWidget.OneRMProgression
        )

        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = chartWidgets,
                    configuration = DashboardConfiguration.Advanced,
                    layoutMode = WidgetLayoutMode.SECTIONS,
                    widgetDataProvider = { createEmptyWidgetData(it) }
                )
            }
        }

        // Then - chart widgets should show with zero data points instead of empty state
        composeTestRule.onNodeWithText("No chart data").assertDoesNotExist()
        composeTestRule.onNodeWithText("Empty chart").assertDoesNotExist()
        
        // Chart widgets should render even with zero data
        chartWidgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription(widget.displayName).assertExists()
        }
    }

    @Test
    fun testEmptyState_maintainsTrendStabilityDisplay() {
        // Given - widget with zero data but stable trend
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = listOf(AnalyticsWidget.TotalVolume),
                    configuration = DashboardConfiguration.Beginner,
                    widgetDataProvider = { widget ->
                        BasicWidgetData(
                            widgetType = widget,
                            lastUpdated = Clock.System.now(),
                            primaryValue = "0",
                            secondaryValue = "No activity yet",
                            unit = "kg",
                            trend = TrendDirection.STABLE // Show stable trend even with zero data
                        )
                    }
                )
            }
        }

        // Then - should show zero value with stable trend indicator
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("No activity yet").assertExists()
        
        // Should not show unknown or error trends for empty data
        composeTestRule.onNodeWithText("Unknown trend").assertDoesNotExist()
    }

    /**
     * Helper function to create empty widget data for testing
     * Returns zero values instead of null/empty states
     */
    private fun createEmptyWidgetData(widget: AnalyticsWidget): BasicWidgetData {
        return BasicWidgetData(
            widgetType = widget,
            lastUpdated = Clock.System.now(),
            primaryValue = "0",
            secondaryValue = "No activity yet", // Subtle empty state indicator
            unit = getDefaultUnitForWidget(widget),
            trend = TrendDirection.STABLE // Stable trend for zero data
        )
    }

    /**
     * Helper function to get default unit for widget (matches implementation)
     */
    private fun getDefaultUnitForWidget(widget: AnalyticsWidget): String {
        return when (widget) {
            AnalyticsWidget.TotalVolume, AnalyticsWidget.OneRMProgression, AnalyticsWidget.VolumeLoadProgression -> "kg"
            AnalyticsWidget.WorkoutFrequency -> "sessions"
            AnalyticsWidget.AverageDuration -> "min"
            AnalyticsWidget.WorkoutStreak -> "days"
            else -> ""
        }
    }
}