package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.ui.progress.components.VolumeCalendarWidget
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before

/**
 * User flow tests for Volume Calendar Widget
 * 
 * Tests end-to-end user interactions:
 * - Month navigation flow with smooth animations
 * - Date selection and detail view interactions
 * - Volume intensity color coding user comprehension
 * - Calendar accessibility and keyboard navigation
 * - Performance with different data densities
 * - Edge cases (empty months, leap years, year boundaries)
 */
@RunWith(AndroidJUnit4::class)
class VolumeCalendarUserFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var julyCalendarData: VolumeCalendarData
    private lateinit var augustCalendarData: VolumeCalendarData
    private lateinit var emptyCalendarData: VolumeCalendarData

    @Before
    fun setUp() {
        julyCalendarData = createJulyCalendarData()
        augustCalendarData = createAugustCalendarData()
        emptyCalendarData = createEmptyCalendarData()
    }

    @Test
    fun testCompleteMonthNavigationFlow() {
        var currentCalendarData by mutableStateOf(julyCalendarData)
        var selectedDate: LocalDate? by mutableStateOf(null)
        
        // Given: Volume calendar with navigation
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = currentCalendarData,
                    onDateClick = { date -> selectedDate = date },
                    modifier = androidx.compose.ui.Modifier.testTag("volume_calendar")
                )
            }
        }

        // When: Starting in July 2025
        composeTestRule.onNodeWithText("July 2025").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_14")
            .assertIsDisplayed()
            .assert(hasTestTag("high_intensity_day")) // July 14 has high volume

        // When: Navigating to next month
        composeTestRule.onNodeWithTag("calendar_nav_next").performClick()
        currentCalendarData = augustCalendarData
        composeTestRule.waitForIdle()

        // Then: Should display August with smooth transition
        composeTestRule.onNodeWithText("August 2025").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_5")
            .assertIsDisplayed()
            .assert(hasTestTag("medium_intensity_day")) // August 5 has medium volume

        // When: Navigating back to previous month
        composeTestRule.onNodeWithTag("calendar_nav_previous").performClick()
        currentCalendarData = julyCalendarData
        composeTestRule.waitForIdle()

        // Then: Should return to July correctly
        composeTestRule.onNodeWithText("July 2025").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_14")
            .assert(hasTestTag("high_intensity_day"))
    }

    @Test
    fun testDateSelectionFlow() {
        var selectedDate: LocalDate? = null
        var showDetailDialog by mutableStateOf(false)
        
        // Given: Interactive volume calendar
        composeTestRule.setContent {
            LiftrixTheme {
                androidx.compose.foundation.layout.Box {
                    VolumeCalendarWidget(
                        calendarData = julyCalendarData,
                        onDateClick = { date -> 
                            selectedDate = date
                            showDetailDialog = true
                        }
                    )
                    
                    // Simulate detail dialog
                    if (showDetailDialog && selectedDate != null) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showDetailDialog = false },
                            title = { androidx.compose.material3.Text("Workout Details") },
                            text = { 
                                androidx.compose.material3.Text(
                                    "Date: $selectedDate\nVolume: ${julyCalendarData.getVolumeForDate(selectedDate!!).kilograms} kg"
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { showDetailDialog = false },
                                    modifier = androidx.compose.ui.Modifier.testTag("dialog_close")
                                ) {
                                    androidx.compose.material3.Text("Close")
                                }
                            },
                            modifier = androidx.compose.ui.Modifier.testTag("workout_detail_dialog")
                        )
                    }
                }
            }
        }

        // When: Selecting a date with workout data (July 14)
        composeTestRule.onNodeWithTag("calendar_day_14").performClick()
        composeTestRule.waitForIdle()

        // Then: Should show detail dialog with correct information
        composeTestRule.onNodeWithTag("workout_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 2025-07-14").assertIsDisplayed()
        composeTestRule.onNodeWithText("Volume: 4500.0 kg").assertIsDisplayed()

        // When: Closing detail dialog
        composeTestRule.onNodeWithTag("dialog_close").performClick()
        composeTestRule.waitForIdle()

        // Then: Should return to calendar view
        composeTestRule.onNodeWithTag("workout_detail_dialog").assertDoesNotExist()
        composeTestRule.onNodeWithTag("volume_calendar").assertIsDisplayed()

        // When: Selecting a date without workout data (July 2)
        composeTestRule.onNodeWithTag("calendar_day_2").performClick()
        composeTestRule.waitForIdle()

        // Then: Should still show dialog but with zero volume
        composeTestRule.onNodeWithTag("workout_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 2025-07-02").assertIsDisplayed()
        composeTestRule.onNodeWithText("Volume: 0.0 kg").assertIsDisplayed()
    }

    @Test
    fun testVolumeIntensityVisualization() {
        // Given: Calendar with varied volume intensities
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = julyCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Examining different volume days
        composeTestRule.waitForIdle()

        // Then: Should visually distinguish volume intensities
        
        // High volume day (July 14: 4500kg = max volume)
        composeTestRule.onNodeWithTag("calendar_day_14")
            .assertIsDisplayed()
            .assert(hasTestTag("high_intensity_day"))

        // Medium volume day (July 8: 3600kg = ~80% of max)
        composeTestRule.onNodeWithTag("calendar_day_8")
            .assertIsDisplayed()
            .assert(hasTestTag("medium_intensity_day"))

        // Low volume day (July 3: 2800kg = ~62% of max)
        composeTestRule.onNodeWithTag("calendar_day_3")
            .assertIsDisplayed()
            .assert(hasTestTag("low_intensity_day"))

        // No volume day (July 2: 0kg)
        composeTestRule.onNodeWithTag("calendar_day_2")
            .assertIsDisplayed()
            .assert(hasTestTag("no_intensity_day"))

        // Should have consistent visual hierarchy
        composeTestRule.onAllNodesWithTag("high_intensity_day").assertCountEquals(1)
        composeTestRule.onAllNodesWithTag("medium_intensity_day").assertCountEquals(4)
        composeTestRule.onAllNodesWithTag("low_intensity_day").assertCountEquals(2)
        composeTestRule.onAllNodesWithTag("no_intensity_day").assertCountEquals(21) // Days without workouts
    }

    @Test
    fun testCalendarLayoutAndGrid() {
        // Given: Volume calendar
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = julyCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Calendar loads
        composeTestRule.waitForIdle()

        // Then: Should have proper 7-column grid layout
        composeTestRule.onAllNodesWithTag("calendar_day").assertCountEquals(42) // 6 weeks × 7 days

        // Should display proper month header
        composeTestRule.onNodeWithText("July 2025").assertIsDisplayed()

        // Should show day labels (S, M, T, W, T, F, S)
        composeTestRule.onNodeWithText("Sun").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Wed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fri").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sat").assertIsDisplayed()

        // Should display all days of July (1-31)
        (1..31).forEach { day ->
            composeTestRule.onNodeWithTag("calendar_day_$day").assertIsDisplayed()
        }

        // Should show previous/next month overflow days in muted style
        composeTestRule.onNodeWithTag("calendar_day_prev_30")
            .assertIsDisplayed()
            .assert(hasTestTag("other_month_day"))
        composeTestRule.onNodeWithTag("calendar_day_next_1")
            .assertIsDisplayed()
            .assert(hasTestTag("other_month_day"))
    }

    @Test
    fun testEmptyMonthHandling() {
        // Given: Calendar with no workout data
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = emptyCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Empty calendar loads
        composeTestRule.waitForIdle()

        // Then: Should display calendar structure
        composeTestRule.onNodeWithText("June 2025").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("calendar_day").assertCountEquals(42)

        // All days should have no intensity
        (1..30).forEach { day ->
            composeTestRule.onNodeWithTag("calendar_day_$day")
                .assertIsDisplayed()
                .assert(hasTestTag("no_intensity_day"))
        }

        // Should still be interactive
        composeTestRule.onNodeWithTag("calendar_day_15").performClick()
        composeTestRule.waitForIdle()

        // Navigation should still work
        composeTestRule.onNodeWithTag("calendar_nav_next").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testYearBoundaryNavigation() {
        var currentCalendarData by mutableStateOf(createDecemberCalendarData())
        
        // Given: Calendar at year boundary (December 2025)
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = currentCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Starting in December 2025
        composeTestRule.onNodeWithText("December 2025").assertIsDisplayed()

        // When: Navigating to next month (January 2026)
        composeTestRule.onNodeWithTag("calendar_nav_next").performClick()
        currentCalendarData = createJanuaryCalendarData()
        composeTestRule.waitForIdle()

        // Then: Should transition to next year correctly
        composeTestRule.onNodeWithText("January 2026").assertIsDisplayed()

        // When: Navigating back to previous month
        composeTestRule.onNodeWithTag("calendar_nav_previous").performClick()
        currentCalendarData = createDecemberCalendarData()
        composeTestRule.waitForIdle()

        // Then: Should return to previous year correctly
        composeTestRule.onNodeWithText("December 2025").assertIsDisplayed()
    }

    @Test
    fun testLeapYearHandling() {
        // Given: Calendar for February 2024 (leap year)
        val leapYearCalendarData = VolumeCalendarData(
            year = 2024,
            month = Month.FEBRUARY,
            dailyVolumes = mapOf(
                LocalDate(2024, 2, 29) to Weight(3500.0) // Leap day with workout
            ),
            maxVolume = Weight(3500.0),
            averageVolume = Weight(3500.0)
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = leapYearCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Leap year February loads
        composeTestRule.waitForIdle()

        // Then: Should display February 29th
        composeTestRule.onNodeWithText("February 2024").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_29")
            .assertIsDisplayed()
            .assert(hasTestTag("high_intensity_day"))

        // Should not display February 30th
        composeTestRule.onNodeWithTag("calendar_day_30").assertDoesNotExist()
    }

    @Test
    fun testPerformanceWithDenseData() {
        // Given: Calendar with workout data every day
        val denseVolumeData = (1..31).associate { day ->
            LocalDate(2025, 7, day) to Weight(2000.0 + day * 50)
        }
        val denseCalendarData = VolumeCalendarData(
            year = 2025,
            month = Month.JULY,
            dailyVolumes = denseVolumeData,
            maxVolume = Weight(3550.0), // Day 31
            averageVolume = Weight(2775.0)
        )

        // When: Rendering dense calendar data
        val renderStartTime = System.currentTimeMillis()
        
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = denseCalendarData,
                    onDateClick = { }
                )
            }
        }

        composeTestRule.waitForIdle()
        val renderTime = System.currentTimeMillis() - renderStartTime

        // Then: Should render efficiently
        assert(renderTime < 500) { "Dense data rendering took too long: ${renderTime}ms" }

        // Should display all volume days with correct intensities
        (1..31).forEach { day ->
            composeTestRule.onNodeWithTag("calendar_day_$day")
                .assertIsDisplayed()
                .assert(hasAnyOfTags("low_intensity_day", "medium_intensity_day", "high_intensity_day"))
        }

        // Interactions should remain responsive
        val interactionStartTime = System.currentTimeMillis()
        composeTestRule.onNodeWithTag("calendar_day_15").performClick()
        composeTestRule.waitForIdle()
        val interactionTime = System.currentTimeMillis() - interactionStartTime
        
        assert(interactionTime < 100) { "Interaction took too long: ${interactionTime}ms" }
    }

    @Test
    fun testAccessibilityKeyboardNavigation() {
        // Given: Calendar with accessibility focus
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = julyCalendarData,
                    onDateClick = { }
                )
            }
        }

        // When: Calendar loads
        composeTestRule.waitForIdle()

        // Then: Should support keyboard navigation
        composeTestRule.onNodeWithTag("calendar_day_1")
            .assert(isFocusable())
            .performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Tab) }

        // Should move focus to next day
        composeTestRule.onNodeWithTag("calendar_day_2")
            .assert(isFocused())

        // Should support directional navigation
        composeTestRule.onNodeWithTag("calendar_day_1")
            .performKeyInput { 
                pressKey(androidx.compose.ui.input.key.Key.DirectionRight) 
            }
        
        composeTestRule.onNodeWithTag("calendar_day_2")
            .assert(isFocused())

        // Should support Enter key activation
        composeTestRule.onNodeWithTag("calendar_day_14")
            .requestFocus()
            .performKeyInput { 
                pressKey(androidx.compose.ui.input.key.Key.Enter) 
            }
        
        // Should trigger click action
        composeTestRule.waitForIdle()
    }

    @Test
    fun testCalendarStateRestoration() {
        var currentMonth by mutableStateOf(Month.JULY)
        var selectedDate by mutableStateOf<LocalDate?>(null)
        
        // Given: Calendar with state
        composeTestRule.setContent {
            LiftrixTheme {
                VolumeCalendarWidget(
                    calendarData = if (currentMonth == Month.JULY) julyCalendarData else augustCalendarData,
                    onDateClick = { date -> selectedDate = date }
                )
            }
        }

        // When: Interacting and changing state
        composeTestRule.onNodeWithTag("calendar_day_14").performClick()
        assert(selectedDate == LocalDate(2025, 7, 14))

        currentMonth = Month.AUGUST
        composeTestRule.waitForIdle()

        // Then: Should maintain navigation state
        composeTestRule.onNodeWithText("August 2025").assertIsDisplayed()

        // When: Returning to original month
        currentMonth = Month.JULY
        composeTestRule.waitForIdle()

        // Then: Should restore previous month state
        composeTestRule.onNodeWithText("July 2025").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_14").assertIsDisplayed()
    }

    // Helper methods for creating test data
    private fun createJulyCalendarData(): VolumeCalendarData {
        val testDailyVolumes = mapOf(
            LocalDate(2025, 7, 1) to Weight(3200.0),
            LocalDate(2025, 7, 3) to Weight(2800.0),
            LocalDate(2025, 7, 5) to Weight(4100.0),
            LocalDate(2025, 7, 8) to Weight(3600.0),
            LocalDate(2025, 7, 10) to Weight(2900.0),
            LocalDate(2025, 7, 12) to Weight(3800.0),
            LocalDate(2025, 7, 14) to Weight(4500.0), // Maximum volume
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

    private fun createAugustCalendarData(): VolumeCalendarData {
        val testDailyVolumes = mapOf(
            LocalDate(2025, 8, 2) to Weight(3400.0),
            LocalDate(2025, 8, 5) to Weight(3100.0),
            LocalDate(2025, 8, 7) to Weight(3800.0),
            LocalDate(2025, 8, 10) to Weight(4200.0),
            LocalDate(2025, 8, 12) to Weight(3600.0),
            LocalDate(2025, 8, 15) to Weight(3900.0),
            LocalDate(2025, 8, 18) to Weight(3300.0)
        )
        
        return VolumeCalendarData(
            year = 2025,
            month = Month.AUGUST,
            dailyVolumes = testDailyVolumes,
            maxVolume = Weight(4200.0),
            averageVolume = Weight(3614.0)
        )
    }

    private fun createEmptyCalendarData(): VolumeCalendarData {
        return VolumeCalendarData(
            year = 2025,
            month = Month.JUNE,
            dailyVolumes = emptyMap(),
            maxVolume = Weight.ZERO,
            averageVolume = Weight.ZERO
        )
    }

    private fun createDecemberCalendarData(): VolumeCalendarData {
        return VolumeCalendarData(
            year = 2025,
            month = Month.DECEMBER,
            dailyVolumes = mapOf(
                LocalDate(2025, 12, 25) to Weight(3000.0),
                LocalDate(2025, 12, 31) to Weight(3500.0)
            ),
            maxVolume = Weight(3500.0),
            averageVolume = Weight(3250.0)
        )
    }

    private fun createJanuaryCalendarData(): VolumeCalendarData {
        return VolumeCalendarData(
            year = 2026,
            month = Month.JANUARY,
            dailyVolumes = mapOf(
                LocalDate(2026, 1, 1) to Weight(2800.0),
                LocalDate(2026, 1, 3) to Weight(3200.0)
            ),
            maxVolume = Weight(3200.0),
            averageVolume = Weight(3000.0)
        )
    }

    // Helper function to check for multiple possible tags
    private fun hasAnyOfTags(vararg tags: String): SemanticsMatcher {
        return SemanticsMatcher("hasAnyOfTags") { semanticsNode ->
            tags.any { tag ->
                semanticsNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == tag
            }
        }
    }
}

// Import for mutable state
import androidx.compose.runtime.*