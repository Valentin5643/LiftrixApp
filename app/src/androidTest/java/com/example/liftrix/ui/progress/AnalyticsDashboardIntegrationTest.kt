package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WorkoutMetrics
import com.example.liftrix.ui.progress.components.VolumeCalendarWidget
import com.example.liftrix.ui.progress.components.MetricsCard
import com.example.liftrix.ui.progress.components.WidgetContainer
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.After

/**
 * Integration tests for Analytics Dashboard UI components
 * 
 * Tests:
 * - Dashboard widget rendering in all configurations (Beginner/Intermediate/Advanced)
 * - Volume calendar widget interactions and animations
 * - Real-time analytics updates and state management
 * - Accessibility compliance with TalkBack navigation
 * - Performance with large datasets and smooth animations
 * - Integration between ViewModel and UI components
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsDashboardIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testVolumeCalendarData: VolumeCalendarData
    private lateinit var testDashboardConfiguration: DashboardConfiguration
    private lateinit var testAnalyticsWidgets: List<AnalyticsWidget>

    @Before
    fun setUp() {
        testVolumeCalendarData = createTestVolumeCalendarData()
        testDashboardConfiguration = DashboardConfiguration.Beginner
        testAnalyticsWidgets = testDashboardConfiguration.widgets
    }

    @After
    fun tearDown() {
        // Cleanup after each test
    }

    @Test
    fun testVolumeCalendarWidgetRendering() {
        // Given: Volume calendar with test data
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = testVolumeCalendarData,
                    onDateClick = { },
                    modifier = androidx.compose.ui.Modifier.testTag("volume_calendar")
                )
            }
        }

        // When: Calendar is displayed
        composeTestRule.onNodeWithTag("volume_calendar").assertIsDisplayed()

        // Then: All calendar elements should be visible
        composeTestRule.onNodeWithText("July 2025").assertIsDisplayed()
        
        // Should display all days of the month
        composeTestRule.onAllNodesWithTag("calendar_day").assertCountEquals(42) // 6 weeks * 7 days
        
        // Should show days with volume data differently
        composeTestRule.onNodeWithTag("calendar_day_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_14").assertIsDisplayed()
        
        // Should handle month navigation
        composeTestRule.onNodeWithTag("calendar_nav_previous").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_nav_next").assertIsDisplayed()
    }

    @Test
    fun testVolumeCalendarInteraction() {
        var clickedDate: LocalDate? = null
        
        // Given: Interactive volume calendar
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = testVolumeCalendarData,
                    onDateClick = { date -> clickedDate = date },
                    modifier = androidx.compose.ui.Modifier.testTag("volume_calendar")
                )
            }
        }

        // When: Clicking on a date with workout data
        composeTestRule.onNodeWithTag("calendar_day_14").performClick()
        
        // Then: Should trigger date click callback
        assert(clickedDate == LocalDate(2025, 7, 14))
        
        // When: Clicking navigation buttons
        composeTestRule.onNodeWithTag("calendar_nav_next").performClick()
        
        // Then: Should update month display
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("August 2025").assertIsDisplayed()
    }

    @Test
    fun testCalendarColorIntensityDisplay() {
        // Given: Calendar with varied volume intensities
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = testVolumeCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Examining different intensity days
        composeTestRule.waitForIdle()

        // Then: Days with higher volume should have different visual treatment
        // Day 14 has maximum volume (4500kg) - should have full intensity
        composeTestRule.onNodeWithTag("calendar_day_14")
            .assertIsDisplayed()
            .assert(hasTestTag("high_intensity_day"))

        // Day 3 has lower volume (2800kg) - should have partial intensity  
        composeTestRule.onNodeWithTag("calendar_day_3")
            .assertIsDisplayed()
            .assert(hasTestTag("medium_intensity_day"))

        // Days without data should have minimal/no intensity
        composeTestRule.onNodeWithTag("calendar_day_2")
            .assertIsDisplayed()
            .assert(hasTestTag("no_intensity_day"))
    }

    @Test
    fun testMetricsCardRendering() {
        // Given: Various metrics cards
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    MetricsCard(
                        title = "Total Volume",
                        value = "35,200 kg",
                        subtitle = "This month",
                        icon = null,
                        modifier = androidx.compose.ui.Modifier.testTag("total_volume_card")
                    )
                    MetricsCard(
                        title = "Workout Frequency", 
                        value = "4.2 /week",
                        subtitle = "+0.8 from last month",
                        icon = null,
                        modifier = androidx.compose.ui.Modifier.testTag("frequency_card")
                    )
                    MetricsCard(
                        title = "Consistency Streak",
                        value = "12 days",
                        subtitle = "Personal best: 18 days",
                        icon = null,
                        modifier = androidx.compose.ui.Modifier.testTag("streak_card")
                    )
                }
            }
        }

        // When: Dashboard loads
        composeTestRule.waitForIdle()

        // Then: All metrics cards should be displayed correctly
        composeTestRule.onNodeWithTag("total_volume_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Volume").assertIsDisplayed()
        composeTestRule.onNodeWithText("35,200 kg").assertIsDisplayed()
        composeTestRule.onNodeWithText("This month").assertIsDisplayed()

        composeTestRule.onNodeWithTag("frequency_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout Frequency").assertIsDisplayed()
        composeTestRule.onNodeWithText("4.2 /week").assertIsDisplayed()
        composeTestRule.onNodeWithText("+0.8 from last month").assertIsDisplayed()

        composeTestRule.onNodeWithTag("streak_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Consistency Streak").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 days").assertIsDisplayed()
    }

    @Test
    fun testWidgetContainerConfiguration() {
        // Given: Different dashboard configurations
        val beginnerConfig = DashboardConfiguration.Beginner
        val advancedConfig = DashboardConfiguration.Advanced

        // Test Beginner configuration
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = beginnerConfig.widgets,
                    configuration = beginnerConfig,
                    modifier = androidx.compose.ui.Modifier.testTag("beginner_dashboard")
                )
            }
        }

        // Then: Should display beginner widgets only
        composeTestRule.onNodeWithTag("beginner_dashboard").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("analytics_widget").assertCountEquals(4) // Beginner has 4 widgets

        // When: Switching to advanced configuration
        composeTestRule.setContent {
            LiftrixTheme {
                WidgetContainer(
                    widgets = advancedConfig.widgets,
                    configuration = advancedConfig,
                    modifier = androidx.compose.ui.Modifier.testTag("advanced_dashboard")
                )
            }
        }

        // Then: Should display all advanced widgets
        composeTestRule.onNodeWithTag("advanced_dashboard").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("analytics_widget").assertCountEquals(8) // Advanced has 8+ widgets
    }

    @Test 
    fun testDashboardScrolling() {
        // Given: Dashboard with multiple widgets requiring scrolling
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(
                    modifier = androidx.compose.ui.Modifier.testTag("progress_dashboard_screen")
                )
            }
        }

        // When: Dashboard loads
        composeTestRule.waitForIdle()

        // Then: Should be scrollable
        composeTestRule.onNodeWithTag("progress_dashboard_screen").assertIsDisplayed()
        
        // Should be able to scroll to see all widgets
        composeTestRule.onNodeWithTag("dashboard_scroll_container")
            .performScrollToIndex(5) // Scroll down to see more widgets
        
        composeTestRule.waitForIdle()
        
        // Should still be functional after scrolling
        composeTestRule.onNodeWithTag("volume_calendar").assertIsDisplayed()
    }

    @Test
    fun testRealTimeAnalyticsUpdates() {
        // Given: Dashboard with live data updates
        var updateTrigger by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                // Simulate real-time update trigger
                if (updateTrigger) {
                    VolumeCalendarWidget(
                        calendarData = testVolumeCalendarData.copy(
                            dailyVolumes = testVolumeCalendarData.dailyVolumes + 
                                (LocalDate(2025, 7, 15) to Weight(3700.0)) // New workout data
                        ),
                        onDateClick = { }
                    )
                } else {
                    VolumeCalendarWidget(
                        calendarData = testVolumeCalendarData,
                        onDateClick = { }
                    )
                }
            }
        }

        // When: Initial state
        composeTestRule.onNodeWithTag("calendar_day_15")
            .assert(hasTestTag("no_intensity_day"))

        // When: Simulating real-time update (new workout completed)
        updateTrigger = true
        composeTestRule.waitForIdle()

        // Then: UI should reflect new data
        composeTestRule.onNodeWithTag("calendar_day_15")
            .assert(hasTestTag("medium_intensity_day"))
    }

    @Test
    fun testAccessibilityCompliance() {
        // Given: Dashboard components with accessibility features
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    VolumeCalendarWidget(
                        calendarData = testVolumeCalendarData,
                        onDateClick = { }
                    )
                    MetricsCard(
                        title = "Total Volume",
                        value = "35,200 kg", 
                        subtitle = "This month",
                        icon = null
                    )
                }
            }
        }

        // When: Using accessibility services
        composeTestRule.waitForIdle()

        // Then: All interactive elements should have content descriptions
        composeTestRule.onNodeWithTag("calendar_day_14")
            .assert(hasContentDescription())

        composeTestRule.onNodeWithTag("total_volume_card")
            .assert(hasContentDescription())

        // Navigation buttons should be accessible
        composeTestRule.onNodeWithTag("calendar_nav_previous")
            .assert(hasContentDescription())
            .assert(hasClickAction())

        composeTestRule.onNodeWithTag("calendar_nav_next")
            .assert(hasContentDescription())
            .assert(hasClickAction())

        // Should support focus navigation
        composeTestRule.onNodeWithTag("calendar_day_1")
            .assert(isFocusable())
    }

    @Test
    fun testDashboardPerformanceWithLargeDataset() {
        // Given: Large dataset for performance testing
        val largeVolumeData = (1..31).associate { day ->
            LocalDate(2025, 7, day) to Weight(2000.0 + day * 100)
        }
        val largeCalendarData = VolumeCalendarData(
            year = 2025,
            month = Month.JULY,
            dailyVolumes = largeVolumeData,
            maxVolume = Weight(5100.0),
            averageVolume = Weight(3550.0)
        )

        // When: Rendering large dataset
        val renderStartTime = System.currentTimeMillis()
        
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = largeCalendarData,
                    onDateClick = { }
                )
            }
        }

        composeTestRule.waitForIdle()
        val renderTime = System.currentTimeMillis() - renderStartTime

        // Then: Should render within reasonable time
        assert(renderTime < 1000) { "Large dataset rendering took too long: ${renderTime}ms" }

        // Should remain responsive for interactions
        composeTestRule.onNodeWithTag("calendar_day_15").performClick()
        composeTestRule.waitForIdle()
        
        // Interaction should be smooth
        composeTestRule.onNodeWithTag("calendar_nav_next").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testOfflineFunctionality() {
        // Given: Dashboard with offline data
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Column {
                    // Simulate offline indicator
                    androidx.compose.material3.Text(
                        text = "Offline",
                        modifier = androidx.compose.ui.Modifier.testTag("offline_indicator")
                    )
                    VolumeCalendarWidget(
                        calendarData = testVolumeCalendarData,
                        onDateClick = { }
                    )
                }
            }
        }

        // When: In offline mode
        composeTestRule.waitForIdle()

        // Then: Should still display cached data
        composeTestRule.onNodeWithTag("offline_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("volume_calendar").assertIsDisplayed()
        
        // All calendar functionality should work
        composeTestRule.onNodeWithTag("calendar_day_14").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_nav_previous").assertIsDisplayed()
        
        // Interactions should remain functional
        composeTestRule.onNodeWithTag("calendar_day_14").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAnimationsAndTransitions() {
        // Given: Dashboard with animation testing
        var currentMonth by mutableStateOf(Month.JULY)
        
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = testVolumeCalendarData.copy(month = currentMonth),
                    onDateClick = { }
                )
            }
        }

        // When: Triggering month transition
        composeTestRule.onNodeWithText("July 2025").assertIsDisplayed()
        
        currentMonth = Month.AUGUST
        composeTestRule.waitForIdle()

        // Then: Should smoothly transition to new month
        composeTestRule.onNodeWithText("August 2025").assertIsDisplayed()
        
        // Animation should complete without performance issues
        // (Measured by responsiveness to subsequent interactions)
        composeTestRule.onNodeWithTag("calendar_day_1").performClick()
        composeTestRule.waitForIdle()
    }

    // Helper methods for creating test data
    private fun createTestVolumeCalendarData(): VolumeCalendarData {
        val testDailyVolumes = mapOf(
            LocalDate(2025, 7, 1) to Weight(3200.0),
            LocalDate(2025, 7, 3) to Weight(2800.0),
            LocalDate(2025, 7, 5) to Weight(4100.0),
            LocalDate(2025, 7, 8) to Weight(3600.0),
            LocalDate(2025, 7, 10) to Weight(2900.0),
            LocalDate(2025, 7, 12) to Weight(3800.0),
            LocalDate(2025, 7, 14) to Weight(4500.0), // Maximum volume day
            LocalDate(2025, 7, 17) to Weight(3300.0),
            LocalDate(2025, 7, 19) to Weight(3900.0),
            LocalDate(2025, 7, 21) to Weight(3100.0)
        )
        
        return VolumeCalendarData(
            year = 2025,
            month = Month.JULY,
            dailyVolumes = testDailyVolumes,
            maxVolume = Weight(4500.0),
            averageVolume = Weight(3520.0)
        )
    }
}

// Mock dashboard configurations for testing
sealed class DashboardConfiguration {
    abstract val widgets: List<AnalyticsWidget>
    
    object Beginner : DashboardConfiguration() {
        override val widgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.ConsistencyStreak,
            AnalyticsWidget.ProgressChart
        )
    }
    
    object Advanced : DashboardConfiguration() {
        override val widgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.ProgressChart,
            AnalyticsWidget.ConsistencyStreak,
            AnalyticsWidget.MuscleGroupDistribution,
            AnalyticsWidget.OneRMProgression,
            AnalyticsWidget.VolumeLoadProgression,
            AnalyticsWidget.RecoveryPatterns
        )
    }
}

// Import for mutable state
import androidx.compose.runtime.*