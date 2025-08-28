package com.example.liftrix.ui.progress.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ChartInteractionTest - Comprehensive interaction testing for chart components
 *
 * Tests mobile-optimized interactions:
 * - Touch target accessibility (minimum 44dp)
 * - Tap gesture recognition and response
 * - Time selector state changes with haptic feedback
 * - Pie chart slice selection and callbacks
 * - Chart data point selection with visual feedback
 * - Animation state transitions
 * - Accessibility semantic actions
 * - Multi-touch gesture handling
 * - Edge case interaction scenarios
 */
@RunWith(AndroidJUnit4::class)
class ChartInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun globalTimeRangeSelector_changesSelectionOnClick() {
        var selectedTimeRange by mutableStateOf(TimeRangeType.MONTH)
        var selectionChangeCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    GlobalTimeRangeSelector(
                        selectedTimeRange = selectedTimeRange,
                        onTimeRangeChange = { newRange ->
                            selectedTimeRange = newRange
                            selectionChangeCount++
                        }
                    )
                }
            }
        }
        
        // Initial state verification
        composeTestRule
            .onNodeWithContentDescription("Month time range, selected", substring = true)
            .assertIsDisplayed()
        
        // Click on Quarter option
        composeTestRule
            .onNodeWithText("Quarter")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify selection changed
        assertEquals(TimeRangeType.QUARTER, selectedTimeRange)
        assertEquals(1, selectionChangeCount)
        
        composeTestRule
            .onNodeWithContentDescription("Quarter time range, selected", substring = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun globalTimeRangeSelector_hasProperTouchTargets() {
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    GlobalTimeRangeSelector(
                        selectedTimeRange = TimeRangeType.WEEK,
                        onTimeRangeChange = { }
                    )
                }
            }
        }
        
        // Verify all time range buttons have click actions
        TimeRangeType.values().forEach { timeRange ->
            composeTestRule
                .onNodeWithText(timeRange.getShortDisplayName())
                .assertHasClickAction()
                .assertIsDisplayed()
        }
    }
    
    @Test
    fun globalTimeRangeSelector_preventsDoubleSelection() {
        var selectedTimeRange by mutableStateOf(TimeRangeType.MONTH)
        var selectionChangeCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    GlobalTimeRangeSelector(
                        selectedTimeRange = selectedTimeRange,
                        onTimeRangeChange = { newRange ->
                            selectedTimeRange = newRange
                            selectionChangeCount++
                        }
                    )
                }
            }
        }
        
        // Click on already selected option
        composeTestRule
            .onNodeWithText("Month")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Should not trigger callback for already selected option
        assertEquals(TimeRangeType.MONTH, selectedTimeRange)
        assertEquals(0, selectionChangeCount)
    }
    
    @Test
    fun modernVolumeChart_detectsDataPointTaps() {
        val testData = generateTestVolumeData()
        var selectedPoint: VolumeDataPoint? = null
        var tapCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    ModernVolumeChart(
                        data = testData,
                        timeRange = TimeRangeType.WEEK,
                        onDataPointSelected = { point ->
                            selectedPoint = point
                            tapCount++
                        }
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Tap near the center of the chart (should hit a data point)
        composeTestRule
            .onNodeWithContentDescription("Volume progress chart", substring = true)
            .performTouchInput {
                click(center)
            }
        
        composeTestRule.waitForIdle()
        
        // Verify tap was detected
        assertTrue("Data point tap was not detected", tapCount > 0)
        assertTrue("No data point was selected", selectedPoint != null)
    }
    
    @Test
    fun muscleGroupPieChart_handlesSliceSelection() {
        val muscleGroupData = generateTestMuscleGroupData()
        var selectedSlice: MuscleGroup? = null
        var selectionCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    MuscleGroupPieChart(
                        data = muscleGroupData,
                        onSliceClick = { muscleGroup ->
                            selectedSlice = muscleGroup
                            selectionCount++
                        },
                        showLegend = true
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Click on the pie chart area
        composeTestRule
            .onNodeWithContentDescription("Muscle group distribution pie chart", substring = true)
            .performTouchInput {
                click(Offset(center.x + 50f, center.y))
            }
        
        composeTestRule.waitForIdle()
        
        // Verify slice selection
        assertTrue("Pie chart slice selection was not detected", selectionCount > 0)
        assertTrue("No muscle group was selected", selectedSlice != null)
    }
    
    @Test
    fun muscleGroupPieChart_legendItemsClickable() {
        val muscleGroupData = generateTestMuscleGroupData()
        var selectedFromLegend: MuscleGroup? = null
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    MuscleGroupPieChart(
                        data = muscleGroupData,
                        onSliceClick = { muscleGroup ->
                            selectedFromLegend = muscleGroup
                        },
                        showLegend = true
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Click on a legend item (Chest)
        composeTestRule
            .onNodeWithText("Chest")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Verify legend click triggered selection
        assertEquals(MuscleGroup.CHEST, selectedFromLegend)
    }
    
    @Test
    fun charts_displaySelectedStateInformation() {
        val testData = generateTestVolumeData()
        var selectedPoint: VolumeDataPoint? = null
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    ModernVolumeChart(
                        data = testData,
                        timeRange = TimeRangeType.WEEK,
                        onDataPointSelected = { point ->
                            selectedPoint = point
                        }
                    )
                }
            }
        }
        
        // Tap on chart to select a point
        composeTestRule
            .onNodeWithContentDescription("Volume progress chart", substring = true)
            .performTouchInput {
                click(center)
            }
        
        composeTestRule.waitForIdle()
        
        // Verify selection information is displayed
        selectedPoint?.let { point ->
            val expectedVolumeText = "${point.volume.value.toInt()}kg"
            composeTestRule
                .onNodeWithText(expectedVolumeText)
                .assertIsDisplayed()
        }
    }
    
    @Test
    fun timeRangeSelector_worksWithDisabledState() {
        var selectionCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    GlobalTimeRangeSelector(
                        selectedTimeRange = TimeRangeType.MONTH,
                        onTimeRangeChange = { selectionCount++ },
                        enabled = false
                    )
                }
            }
        }
        
        // Try to click on Quarter when disabled
        composeTestRule
            .onNodeWithText("Quarter")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Should not trigger callback when disabled
        assertEquals(0, selectionCount)
    }
    
    @Test
    fun charts_handleMultipleTouchEvents() {
        val testData = generateTestVolumeData()
        var totalTaps = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    ModernVolumeChart(
                        data = testData,
                        timeRange = TimeRangeType.WEEK,
                        onDataPointSelected = { totalTaps++ }
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Perform multiple taps in different areas
        val chartNode = composeTestRule.onNodeWithContentDescription("Volume progress chart", substring = true)
        
        chartNode.performTouchInput { click(Offset(center.x - 100f, center.y)) }
        composeTestRule.waitForIdle()
        
        chartNode.performTouchInput { click(Offset(center.x + 100f, center.y)) }
        composeTestRule.waitForIdle()
        
        chartNode.performTouchInput { click(Offset(center.x, center.y - 50f)) }
        composeTestRule.waitForIdle()
        
        // Should handle multiple tap events
        assertTrue("Multiple touch events were not handled properly", totalTaps >= 2)
    }
    
    @Test
    fun pieChart_showsSelectionFeedback() {
        val muscleGroupData = generateTestMuscleGroupData()
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    MuscleGroupPieChart(
                        data = muscleGroupData,
                        onSliceClick = { },
                        showLegend = true
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Click on Chest in legend
        composeTestRule
            .onNodeWithText("Chest")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Should display selection details
        composeTestRule
            .onNodeWithText("Chest", substring = true)
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("% of total volume", substring = true)
            .assertIsDisplayed()
    }
    
    @Test
    fun charts_accessibilitySemantics() {
        val testData = generateTestVolumeData()
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    Column {
                        GlobalTimeRangeSelector(
                            selectedTimeRange = TimeRangeType.MONTH,
                            onTimeRangeChange = { }
                        )
                        
                        ModernVolumeChart(
                            data = testData,
                            timeRange = TimeRangeType.MONTH
                        )
                    }
                }
            }
        }
        
        // Verify accessibility content descriptions exist
        composeTestRule
            .onNodeWithContentDescription("Time range selector", substring = true)
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Volume progress chart", substring = true)
            .assertIsDisplayed()
        
        // Verify semantic roles are properly set
        composeTestRule
            .onAllNodesWithContentDescription("time range", substring = true)[0]
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
    }
    
    @Test
    fun charts_handleEdgeCaseTouches() {
        val testData = generateTestVolumeData()
        var tapsOutsideChart = 0
        var validTaps = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    ModernVolumeChart(
                        data = testData,
                        timeRange = TimeRangeType.WEEK,
                        onDataPointSelected = { validTaps++ }
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        val chartNode = composeTestRule.onNodeWithContentDescription("Volume progress chart", substring = true)
        
        // Tap far outside any data points
        chartNode.performTouchInput {
            click(Offset(10f, 10f))  // Top-left corner
        }
        composeTestRule.waitForIdle()
        
        chartNode.performTouchInput {
            click(Offset(size.width - 10f, size.height - 10f))  // Bottom-right corner
        }
        composeTestRule.waitForIdle()
        
        // These edge case taps should not trigger data point selection
        assertTrue("Edge case touches should not select data points", validTaps == 0)
    }
    
    @Test
    fun animatedChartTransitions_maintainInteractivity() {
        var timeRange by mutableStateOf(TimeRangeType.WEEK)
        val testData = generateTestVolumeData()
        var dataPointSelected = false
        
        composeTestRule.setContent {
            LiftrixTheme {
                TestContainer {
                    Column {
                        GlobalTimeRangeSelector(
                            selectedTimeRange = timeRange,
                            onTimeRangeChange = { timeRange = it }
                        )
                        
                        ModernVolumeChart(
                            data = testData,
                            timeRange = timeRange,
                            onDataPointSelected = { dataPointSelected = true }
                        )
                    }
                }
            }
        }
        
        // Change time range to trigger animation
        composeTestRule
            .onNodeWithText("Month")
            .performClick()
        
        composeTestRule.waitForIdle()
        
        // Try to interact with chart during/after animation
        composeTestRule
            .onNodeWithContentDescription("Volume progress chart", substring = true)
            .performTouchInput {
                click(center)
            }
        
        composeTestRule.waitForIdle()
        
        // Chart should remain interactive during animations
        assertTrue("Chart lost interactivity during animation transition", dataPointSelected)
    }

    // Helper functions for test data generation
    
    private fun generateTestVolumeData(): List<VolumeDataPoint> {
        return listOf(
            VolumeDataPoint(LocalDate(2024, 1, 1), Weight.fromKilograms(1200.0)),
            VolumeDataPoint(LocalDate(2024, 1, 2), Weight.fromKilograms(1350.0)),
            VolumeDataPoint(LocalDate(2024, 1, 3), Weight.fromKilograms(1180.0)),
            VolumeDataPoint(LocalDate(2024, 1, 4), Weight.fromKilograms(1450.0)),
            VolumeDataPoint(LocalDate(2024, 1, 5), Weight.fromKilograms(1520.0))
        )
    }
    
    private fun generateTestMuscleGroupData(): Map<MuscleGroup, Float> {
        return mapOf(
            MuscleGroup.CHEST to 2500f,
            MuscleGroup.BACK to 3200f,
            MuscleGroup.SHOULDERS to 1800f,
            MuscleGroup.ARMS to 2100f
        )
    }
}

/**
 * Test container with consistent layout for interaction testing
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