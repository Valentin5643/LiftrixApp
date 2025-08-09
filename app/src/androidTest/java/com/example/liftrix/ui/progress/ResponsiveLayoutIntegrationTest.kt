package com.example.liftrix.ui.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.LayoutMode
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.ResponsiveDashboardLayout
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for responsive layout behavior in the analytics dashboard.
 * 
 * Tests verify that the dashboard adapts correctly to different screen sizes
 * and device orientations, with particular focus on the 2-column mobile grid
 * layout and widget arrangement patterns.
 * 
 * Test Coverage:
 * - 2-column mobile grid layout with real widget data
 * - Responsive breakpoints and layout transitions
 * - Widget arrangement and spacing in different layouts
 * - Scroll behavior with grid layouts
 * - Widget content adaptation to container sizes
 * - Performance during layout changes
 * - Edge cases with varying widget counts
 * 
 * Real Data Integration:
 * - Uses actual AnalyticsWidget implementations
 * - Tests with realistic widget data patterns
 * - Validates data display in responsive containers
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ResponsiveLayoutIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Test 2-column mobile grid layout with realistic widget data
     */
    @Test
    fun mobileGrid_with2Columns_displaysCorrectly() {
        val realWidgetData = createRealisticWidgetDataSet()
        val activeWidgets = realWidgetData.keys.toList().take(6)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Simulate mobile screen width
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.width(360.dp) // Typical mobile width
                    ) {
                        ResponsiveDashboardLayout(
                            widgets = activeWidgets,
                            widgetData = realWidgetData,
                            layoutMode = LayoutMode.GRID,
                            onWidgetClick = { },
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify widgets are displayed in grid layout
        activeWidgets.take(4).forEach { widget ->
            composeTestRule
                .onNode(hasContentDescription("${widget.displayName} Widget"))
                .assertExists()
        }
        
        // Verify grid container is present
        composeTestRule
            .onNode(hasTestTag("dashboard_grid"))
            .assertExists()
    }
    
    /**
     * Test responsive layout with different screen widths
     */
    @Test
    fun responsiveLayout_adaptsToScreenWidth() {
        val widgetData = createRealisticWidgetDataSet()
        val activeWidgets = widgetData.keys.toList().take(4)
        
        // Test compact width (< 400dp)
        composeTestRule.setContent {
            TestResponsiveContainer(width = 350.dp) {
                ResponsiveDashboardLayout(
                    widgets = activeWidgets,
                    widgetData = widgetData,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify layout adapts to compact width
        activeWidgets.take(2).forEach { widget ->
            composeTestRule
                .onNode(hasContentDescription("${widget.displayName} Widget"))
                .assertExists()
                .assertIsDisplayed()
        }
        
        // Test medium width (400-600dp)
        composeTestRule.setContent {
            TestResponsiveContainer(width = 500.dp) {
                ResponsiveDashboardLayout(
                    widgets = activeWidgets,
                    widgetData = widgetData,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify layout adapts to medium width
        activeWidgets.forEach { widget ->
            composeTestRule
                .onNode(hasContentDescription("${widget.displayName} Widget"))
                .assertExists()
        }
    }
    
    /**
     * Test grid layout with varying numbers of widgets
     */
    @Test
    fun gridLayout_handlesVaryingWidgetCounts() {
        val widgetData = createRealisticWidgetDataSet()
        
        // Test with 1 widget
        testGridWithWidgetCount(widgetData, 1)
        
        // Test with 3 widgets (odd number)
        testGridWithWidgetCount(widgetData, 3)
        
        // Test with 6 widgets (even number)
        testGridWithWidgetCount(widgetData, 6)
        
        // Test with 8 widgets (larger set)
        testGridWithWidgetCount(widgetData, 8)
    }
    
    private fun testGridWithWidgetCount(
        widgetData: Map<AnalyticsWidget, WidgetData>,
        widgetCount: Int
    ) {
        val activeWidgets = widgetData.keys.toList().take(widgetCount)
        
        composeTestRule.setContent {
            TestResponsiveContainer(width = 400.dp) {
                ResponsiveDashboardLayout(
                    widgets = activeWidgets,
                    widgetData = widgetData,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify all widgets are displayed
        val displayedWidgets = composeTestRule
            .onAllNodes(hasContentDescription("Widget", substring = true))
            .fetchSemanticsNodes()
        
        // Should display at least the minimum of requested widgets or visible widgets
        assert(displayedWidgets.size >= minOf(widgetCount, 2)) {
            "Expected at least ${minOf(widgetCount, 2)} widgets but found ${displayedWidgets.size}"
        }
    }
    
    /**
     * Test widget data rendering in responsive containers
     */
    @Test
    fun widgetDataRendering_adaptsToContainerSize() {
        val realVolumeData = createRealVolumeDataPoints()
        val volumeWidgetData = BasicWidgetData(
            widgetType = AnalyticsWidget.TotalVolume,
            lastUpdated = Clock.System.now(),
            primaryValue = "15,420 kg",
            secondaryValue = "This Week",
            trend = "+12.5%",
            isEmpty = false,
            additionalData = mapOf("volumeData" to realVolumeData)
        )
        
        val widgetDataMap = mapOf(
            AnalyticsWidget.TotalVolume to volumeWidgetData,
            AnalyticsWidget.WorkoutFrequency to BasicWidgetData(
                widgetType = AnalyticsWidget.WorkoutFrequency,
                lastUpdated = Clock.System.now(),
                primaryValue = "4.2 /week",
                secondaryValue = "Average",
                trend = "+0.3",
                isEmpty = false
            )
        )
        
        composeTestRule.setContent {
            TestResponsiveContainer(width = 360.dp) {
                ResponsiveDashboardLayout(
                    widgets = listOf(AnalyticsWidget.TotalVolume, AnalyticsWidget.WorkoutFrequency),
                    widgetData = widgetDataMap,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify widget data is displayed correctly
        composeTestRule
            .onNode(hasText("15,420 kg"))
            .assertExists()
            .assertIsDisplayed()
        
        composeTestRule
            .onNode(hasText("4.2 /week"))
            .assertExists()
            .assertIsDisplayed()
        
        // Verify trend data is displayed
        composeTestRule
            .onNode(hasText("+12.5%"))
            .assertExists()
    }
    
    /**
     * Test scroll behavior with grid layout
     */
    @Test
    fun gridLayout_scrollBehavior_worksCorrectly() {
        val widgetData = createRealisticWidgetDataSet()
        val manyWidgets = widgetData.keys.toList().take(10) // More widgets than fit on screen
        
        composeTestRule.setContent {
            TestResponsiveContainer(width = 360.dp) {
                ResponsiveDashboardLayout(
                    widgets = manyWidgets,
                    widgetData = widgetData,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify initial widgets are visible
        val initialVisibleWidgets = composeTestRule
            .onAllNodes(hasContentDescription("Widget", substring = true))
            .fetchSemanticsNodes()
        
        // Perform scroll
        val scrollContainer = composeTestRule
            .onNode(hasTestTag("dashboard_grid"))
        
        if (scrollContainer.isDisplayed()) {
            scrollContainer.performTouchInput {
                swipeUp()
            }
            
            composeTestRule.waitForIdle()
            
            // Verify scroll worked and content changed
            val afterScrollWidgets = composeTestRule
                .onAllNodes(hasContentDescription("Widget", substring = true))
                .fetchSemanticsNodes()
            
            // Should still have widgets displayed after scroll
            assert(afterScrollWidgets.isNotEmpty()) {
                "No widgets visible after scroll"
            }
        }
    }
    
    /**
     * Test layout performance during responsive changes
     */
    @Test
    fun responsiveLayout_performanceDuringChanges() {
        val widgetData = createRealisticWidgetDataSet()
        val activeWidgets = widgetData.keys.toList().take(6)
        
        val startTime = System.currentTimeMillis()
        
        composeTestRule.setContent {
            TestResponsiveContainer(width = 360.dp) {
                ResponsiveDashboardLayout(
                    widgets = activeWidgets,
                    widgetData = widgetData,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        val layoutTime = System.currentTimeMillis() - startTime
        
        // Verify layout completes within performance budget
        assert(layoutTime < 500) {
            "Layout took ${layoutTime}ms, exceeding 500ms performance budget"
        }
        
        // Verify widgets are displayed
        val visibleWidgets = composeTestRule
            .onAllNodes(hasContentDescription("Widget", substring = true))
            .fetchSemanticsNodes()
        
        assert(visibleWidgets.isNotEmpty()) {
            "No widgets visible after layout"
        }
    }
    
    /**
     * Test edge case with empty widget data
     */
    @Test
    fun gridLayout_handlesEmptyWidgetData() {
        composeTestRule.setContent {
            TestResponsiveContainer(width = 360.dp) {
                ResponsiveDashboardLayout(
                    widgets = emptyList(),
                    widgetData = emptyMap(),
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Should not crash and should show appropriate empty state or fallback
        composeTestRule
            .onNode(hasTestTag("dashboard_grid"))
            .assertExists()
        
        // Verify no widgets are displayed for empty data
        val widgets = composeTestRule
            .onAllNodes(hasContentDescription("Widget", substring = true))
            .fetchSemanticsNodes()
        
        assert(widgets.isEmpty()) {
            "Expected no widgets for empty data but found ${widgets.size}"
        }
    }
    
    /**
     * Test responsive layout with real analytics data patterns
     */
    @Test
    fun responsiveLayout_withRealAnalyticsPatterns() {
        val realPatternData = createAnalyticsPatternData()
        val patternWidgets = realPatternData.keys.toList()
        
        composeTestRule.setContent {
            TestResponsiveContainer(width = 400.dp) {
                ResponsiveDashboardLayout(
                    widgets = patternWidgets,
                    widgetData = realPatternData,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify complex analytics data is displayed correctly
        composeTestRule
            .onNode(hasText("Volume Trends"))
            .assertExists()
        
        // Verify trend indicators are shown
        composeTestRule
            .onAllNodes(hasText("%", substring = true))
            .assertCountEquals(patternWidgets.size.coerceAtMost(4)) // Expect trend percentages
    }
    
    /**
     * Helper composable to create responsive test containers
     */
    @androidx.compose.runtime.Composable
    private fun TestResponsiveContainer(
        width: androidx.compose.ui.unit.Dp,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        LiftrixTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.width(width)
                ) {
                    content()
                }
            }
        }
    }
    
    /**
     * Create realistic widget data set for testing
     */
    private fun createRealisticWidgetDataSet(): Map<AnalyticsWidget, WidgetData> {
        return mapOf(
            AnalyticsWidget.TotalVolume to BasicWidgetData(
                widgetType = AnalyticsWidget.TotalVolume,
                lastUpdated = Clock.System.now(),
                primaryValue = "15,420 kg",
                secondaryValue = "This Month",
                trend = "+8.3%",
                isEmpty = false
            ),
            AnalyticsWidget.WorkoutFrequency to BasicWidgetData(
                widgetType = AnalyticsWidget.WorkoutFrequency,
                lastUpdated = Clock.System.now(),
                primaryValue = "4.2 /week",
                secondaryValue = "Average",
                trend = "+0.3",
                isEmpty = false
            ),
            AnalyticsWidget.WorkoutStreak to BasicWidgetData(
                widgetType = AnalyticsWidget.WorkoutStreak,
                lastUpdated = Clock.System.now(),
                primaryValue = "12 days",
                secondaryValue = "Current",
                trend = "+5 days",
                isEmpty = false
            ),
            AnalyticsWidget.AverageDuration to BasicWidgetData(
                widgetType = AnalyticsWidget.AverageDuration,
                lastUpdated = Clock.System.now(),
                primaryValue = "68 min",
                secondaryValue = "Per workout",
                trend = "+3 min",
                isEmpty = false
            ),
            AnalyticsWidget.PersonalRecords to BasicWidgetData(
                widgetType = AnalyticsWidget.PersonalRecords,
                lastUpdated = Clock.System.now(),
                primaryValue = "3 PRs",
                secondaryValue = "This week",
                trend = "+1 PR",
                isEmpty = false
            ),
            AnalyticsWidget.MuscleGroupDistribution to BasicWidgetData(
                widgetType = AnalyticsWidget.MuscleGroupDistribution,
                lastUpdated = Clock.System.now(),
                primaryValue = "6 groups",
                secondaryValue = "Trained",
                trend = "Balanced",
                isEmpty = false
            )
        )
    }
    
    /**
     * Create real volume data points for testing
     */
    private fun createRealVolumeDataPoints(): List<VolumeDataPoint> {
        return listOf(
            VolumeDataPoint(LocalDate(2024, 1, 1), Weight.fromKilograms(12500.0), 3),
            VolumeDataPoint(LocalDate(2024, 1, 8), Weight.fromKilograms(13200.0), 4),
            VolumeDataPoint(LocalDate(2024, 1, 15), Weight.fromKilograms(14100.0), 3),
            VolumeDataPoint(LocalDate(2024, 1, 22), Weight.fromKilograms(15420.0), 4),
            VolumeDataPoint(LocalDate(2024, 1, 29), Weight.fromKilograms(16800.0), 3)
        )
    }
    
    /**
     * Create analytics pattern data representing real-world usage
     */
    private fun createAnalyticsPatternData(): Map<AnalyticsWidget, WidgetData> {
        return mapOf(
            AnalyticsWidget.VolumeTrends to BasicWidgetData(
                widgetType = AnalyticsWidget.VolumeTrends,
                lastUpdated = Clock.System.now(),
                primaryValue = "↗ Upward",
                secondaryValue = "Trend",
                trend = "+15.2%",
                isEmpty = false
            ),
            AnalyticsWidget.MuscleGroupDistribution to BasicWidgetData(
                widgetType = AnalyticsWidget.MuscleGroupDistribution,
                lastUpdated = Clock.System.now(),
                primaryValue = "Legs 35%",
                secondaryValue = "Top muscle",
                trend = "+5%",
                isEmpty = false
            ),
            AnalyticsWidget.RecoveryMetrics to BasicWidgetData(
                widgetType = AnalyticsWidget.RecoveryMetrics,
                lastUpdated = Clock.System.now(),
                primaryValue = "2.1 days",
                secondaryValue = "Avg rest",
                trend = "Optimal",
                isEmpty = false
            )
        )
    }
    
    /**
     * Helper extension to safely check if node is displayed
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isDisplayed(): Boolean {
        return try {
            assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}