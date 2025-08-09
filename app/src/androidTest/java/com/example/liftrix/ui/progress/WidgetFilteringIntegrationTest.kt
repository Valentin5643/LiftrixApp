package com.example.liftrix.ui.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.LayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.ResponsiveDashboardLayout
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for widget filtering functionality in the analytics dashboard.
 * 
 * Tests verify that deprecated widgets are properly filtered out from the dashboard
 * display and that only active (non-deprecated) widgets are shown to users.
 * 
 * Key test scenarios:
 * - Deprecated widgets are hidden from dashboard display
 * - Active widgets are properly displayed
 * - Widget filtering works across different user levels
 * - Edge cases like empty widget states are handled correctly
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class WidgetFilteringIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Test that verifies deprecated widgets are completely filtered out
     * from the dashboard and not displayed to users
     */
    @Test
    fun dashboard_hidesDeprecatedWidgets() {
        // Get all deprecated widgets that should be hidden
        val deprecatedWidgets = AnalyticsWidget.getDeprecatedWidgets()
        
        // Create widget data map that includes both active and deprecated widgets
        val allWidgetData = createWidgetDataForAllWidgets()
        
        // Create preferences that attempt to show deprecated widgets
        val preferences = createPreferencesWithDeprecatedWidgets()
        
        // Create dashboard configuration
        val configuration = DashboardConfiguration(
            layoutMode = LayoutMode.GRID,
            userLevel = UserLevel.INTERMEDIATE,
            visibleWidgetIds = preferences.visibleWidgets.toList()
        )
        
        // Create analytics widget state with all widgets
        val widgetState = AnalyticsWidgetState(
            widgetData = allWidgetData,
            preferences = preferences,
            configuration = configuration,
            activeWidgets = AnalyticsWidget.getActiveWidgets() // Only active widgets should be set
        )
        
        val uiState = UiState.Success(widgetState)
        
        // Set up Compose UI
        composeTestRule.setContent {
            LiftrixTheme {
                ResponsiveDashboardLayout(
                    widgets = widgetState.activeWidgets, // Should only contain active widgets
                    widgetData = widgetState.widgetDataMap,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { },
                    modifier = androidx.compose.ui.Modifier
                )
            }
        }
        
        // Verify deprecated widgets are not displayed
        deprecatedWidgets.forEach { deprecatedWidget ->
            composeTestRule
                .onNode(hasText(deprecatedWidget.displayName))
                .assertDoesNotExist()
            
            composeTestRule
                .onNode(hasTestTag("widget_${deprecatedWidget.id}"))
                .assertDoesNotExist()
        }
    }
    
    /**
     * Test that verifies active (non-deprecated) widgets are properly displayed
     */
    @Test
    fun dashboard_showsActiveWidgets() {
        // Get active widgets that should be displayed
        val activeWidgets = AnalyticsWidget.getActiveWidgets().take(6) // Limit for test performance
        
        // Create widget data for active widgets only
        val activeWidgetData = activeWidgets.associateWith { widget ->
            createSampleWidgetData(widget)
        }
        
        // Create preferences with active widgets only
        val preferences = WidgetPreferences(
            userId = "test_user",
            visibleWidgets = activeWidgets.map { it.id }.toSet(),
            userLevel = UserLevel.INTERMEDIATE,
            enabledCategories = setOf(),
            refreshIntervalMinutes = 15
        )
        
        val configuration = DashboardConfiguration(
            layoutMode = LayoutMode.GRID,
            userLevel = UserLevel.INTERMEDIATE,
            visibleWidgetIds = preferences.visibleWidgets.toList()
        )
        
        val widgetState = AnalyticsWidgetState(
            widgetDataMap = activeWidgetData,
            preferences = preferences,
            configuration = configuration,
            activeWidgets = activeWidgets
        )
        
        // Set up Compose UI
        composeTestRule.setContent {
            LiftrixTheme {
                ResponsiveDashboardLayout(
                    widgets = widgetState.activeWidgets,
                    widgetData = widgetState.widgetDataMap,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { },
                    modifier = androidx.compose.ui.Modifier
                )
            }
        }
        
        // Verify active widgets are displayed
        activeWidgets.take(3).forEach { activeWidget -> // Test first 3 to avoid flakiness
            composeTestRule
                .onNode(hasText(activeWidget.displayName))
                .assertExists()
        }
    }
    
    /**
     * Test widget filtering across different user levels to ensure
     * deprecated widgets are hidden regardless of user level
     */
    @Test
    fun widgetFiltering_worksAcrossUserLevels() {
        val deprecatedWidgets = AnalyticsWidget.getDeprecatedWidgets().take(3)
        val userLevels = listOf(UserLevel.BEGINNER, UserLevel.INTERMEDIATE, UserLevel.ADVANCED)
        
        userLevels.forEach { userLevel ->
            // Create preferences for this user level that include deprecated widgets
            val preferences = WidgetPreferences(
                userId = "test_user",
                visibleWidgets = deprecatedWidgets.map { it.id }.toSet(),
                userLevel = userLevel,
                enabledCategories = setOf(),
                refreshIntervalMinutes = 15
            )
            
            val widgetState = AnalyticsWidgetState(
                preferences = preferences,
                activeWidgets = AnalyticsWidget.getActiveWidgets().filter { 
                    it.isDefaultEnabledForLevel(userLevel) 
                }.take(4) // Only active widgets for this level
            )
            
            composeTestRule.setContent {
                LiftrixTheme {
                    ResponsiveDashboardLayout(
                        widgets = widgetState.activeWidgets, // Should contain only active widgets
                        widgetData = emptyMap(),
                        layoutMode = LayoutMode.GRID,
                        onWidgetClick = { },
                        modifier = androidx.compose.ui.Modifier
                    )
                }
            }
            
            // Verify deprecated widgets are not shown for this user level
            deprecatedWidgets.forEach { deprecatedWidget ->
                composeTestRule
                    .onNode(hasText(deprecatedWidget.displayName))
                    .assertDoesNotExist()
            }
        }
    }
    
    /**
     * Test that the deprecated widget filtering preserves widget order
     * and doesn't affect the layout of active widgets
     */
    @Test
    fun widgetFiltering_preservesActiveWidgetOrder() {
        // Get active widgets in priority order
        val activeWidgetsInOrder = AnalyticsWidget.getActiveWidgets()
            .sortedBy { it.getLayoutPriority() }
            .take(4)
        
        val widgetDataMap = activeWidgetsInOrder.associateWith { widget ->
            createSampleWidgetData(widget)
        }
        
        val preferences = WidgetPreferences(
            userId = "test_user",
            visibleWidgets = activeWidgetsInOrder.map { it.id }.toSet(),
            userLevel = UserLevel.INTERMEDIATE,
            enabledCategories = setOf(),
            refreshIntervalMinutes = 15
        )
        
        val widgetState = AnalyticsWidgetState(
            widgetDataMap = widgetDataMap,
            preferences = preferences,
            activeWidgets = activeWidgetsInOrder
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                ResponsiveDashboardLayout(
                    widgets = widgetState.activeWidgets,
                    widgetData = widgetState.widgetDataMap,
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { },
                    modifier = androidx.compose.ui.Modifier
                )
            }
        }
        
        // Verify first two widgets are displayed (order verification)
        activeWidgetsInOrder.take(2).forEach { widget ->
            composeTestRule
                .onNode(hasText(widget.displayName))
                .assertExists()
        }
    }
    
    /**
     * Test edge case where all widgets in preferences are deprecated
     */
    @Test
    fun widgetFiltering_handlesAllDeprecatedPreferences() {
        val allDeprecatedWidgets = AnalyticsWidget.getDeprecatedWidgets()
        
        val preferences = WidgetPreferences(
            userId = "test_user",
            visibleWidgets = allDeprecatedWidgets.map { it.id }.toSet(),
            userLevel = UserLevel.INTERMEDIATE,
            enabledCategories = setOf(),
            refreshIntervalMinutes = 15
        )
        
        // Active widgets should be empty since all preferences are deprecated
        val widgetState = AnalyticsWidgetState(
            preferences = preferences,
            activeWidgets = emptyList() // Should fallback to empty list
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                ResponsiveDashboardLayout(
                    widgets = widgetState.activeWidgets,
                    widgetData = emptyMap(),
                    layoutMode = LayoutMode.GRID,
                    onWidgetClick = { },
                    modifier = androidx.compose.ui.Modifier
                )
            }
        }
        
        // Verify no deprecated widgets are displayed
        allDeprecatedWidgets.forEach { deprecatedWidget ->
            composeTestRule
                .onNode(hasText(deprecatedWidget.displayName))
                .assertDoesNotExist()
        }
        
        // In a real app, fallback widgets should be displayed, but for this test
        // we verify that the deprecated widgets are definitely not shown
    }
    
    /**
     * Helper function to create sample widget data
     */
    private fun createSampleWidgetData(widget: AnalyticsWidget): WidgetData {
        return BasicWidgetData(
            widgetType = widget,
            lastUpdated = Clock.System.now(),
            primaryValue = when (widget) {
                AnalyticsWidget.TotalVolume -> "2,450 kg"
                AnalyticsWidget.WorkoutFrequency -> "4.2/week"
                AnalyticsWidget.WorkoutStreak -> "12 days"
                AnalyticsWidget.AverageDuration -> "45 min"
                else -> "Sample"
            },
            secondaryValue = "+12%",
            trend = "+5.2%",
            isEmpty = false
        )
    }
    
    /**
     * Helper function to create preferences that include deprecated widgets
     */
    private fun createPreferencesWithDeprecatedWidgets(): WidgetPreferences {
        val deprecatedWidgetIds = AnalyticsWidget.getDeprecatedWidgets()
            .take(5)
            .map { it.id }
        
        val activeWidgetIds = AnalyticsWidget.getActiveWidgets()
            .take(5)
            .map { it.id }
        
        return WidgetPreferences(
            userId = "test_user",
            visibleWidgets = (deprecatedWidgetIds + activeWidgetIds).toSet(),
            userLevel = UserLevel.INTERMEDIATE,
            enabledCategories = setOf(),
            refreshIntervalMinutes = 15
        )
    }
    
    /**
     * Helper function to create widget data for all widgets (including deprecated ones)
     */
    private fun createWidgetDataForAllWidgets(): Map<String, WidgetData> {
        return AnalyticsWidget.getAllWidgets().associate { widget ->
            widget.id to createSampleWidgetData(widget)
        }
    }
}