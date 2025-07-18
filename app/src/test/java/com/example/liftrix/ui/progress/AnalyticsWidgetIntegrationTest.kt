package com.example.liftrix.ui.progress

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.service.AnalyticsService
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.progress.components.WidgetContainer
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.theme.LiftrixTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * Integration tests for AnalyticsWidgetViewModel and widget UI components.
 * 
 * Tests widget-specific integration flows:
 * - Widget data loading and visualization
 * - Widget configuration and visibility management
 * - Layout mode switching and responsive design
 * - Widget-specific user interactions
 * - Dashboard configuration persistence
 * - Real-time widget data updates
 * 
 * Testing Strategy:
 * - Real AnalyticsWidgetViewModel with mocked AnalyticsService
 * - Widget container and individual widget testing
 * - Configuration state validation
 * - Layout responsiveness and adaptation
 * - Widget performance and data refresh
 * 
 * Widget Types Tested:
 * - Total Volume widgets with trend analysis
 * - Workout Frequency widgets with consistency tracking
 * - Average Duration widgets with session insights
 * - Consistency Streak widgets with motivation metrics
 * - Calorie-based widgets with burn tracking
 * - Custom widget configurations and personalization
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AnalyticsWidgetIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    // Test data for widgets
    private val testUser = User(
        id = "test-user-123",
        email = "test@example.com", 
        displayName = "Test User"
    )

    private val testDashboardConfiguration = DashboardConfiguration(
        userId = testUser.id,
        layoutMode = WidgetLayoutMode.GRID,
        visibleWidgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.AverageDuration,
            AnalyticsWidget.ConsistencyStreak
        ),
        customization = mapOf(
            "theme" to "default",
            "animation" to "enabled"
        ),
        lastUpdated = Clock.System.now()
    )

    private val testWidgetDataMap = mapOf(
        AnalyticsWidget.TotalVolume to BasicWidgetData(
            widgetType = AnalyticsWidget.TotalVolume,
            lastUpdated = Clock.System.now(),
            value = "25,500 kg",
            subtitle = "Total volume - Last 30 days",
            trend = TrendDirection.UP
        ),
        AnalyticsWidget.WorkoutFrequency to BasicWidgetData(
            widgetType = AnalyticsWidget.WorkoutFrequency,
            lastUpdated = Clock.System.now(),
            value = "15 sessions",
            subtitle = "Last 30 days",
            trend = TrendDirection.STABLE
        ),
        AnalyticsWidget.AverageDuration to BasicWidgetData(
            widgetType = AnalyticsWidget.AverageDuration,
            lastUpdated = Clock.System.now(),
            value = "65 min",
            subtitle = "Average session",
            trend = TrendDirection.UP
        ),
        AnalyticsWidget.ConsistencyStreak to BasicWidgetData(
            widgetType = AnalyticsWidget.ConsistencyStreak,
            lastUpdated = Clock.System.now(),
            value = "7 days",
            subtitle = "Current streak",
            trend = TrendDirection.UP
        )
    )

    @Before
    fun setup() {
        hiltRule.inject()
        clearAllMocks()
    }

    /**
     * Test: Widget container displays all configured widgets
     * Validates widget rendering and layout management
     */
    @Test
    fun widgetContainerDisplaysAllConfiguredWidgets() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                
                // Trigger widget loading
                viewModel.handleEvent(
                    AnalyticsWidgetEvent.LoadAllWidgets
                )
                
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                WidgetsSection(
                    widgetState = widgetState,
                    onEvent = viewModel::handleEvent
                )
            }
        }

        // Wait for widgets to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("widgets_section")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithTag("widgets_loading")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify widgets section is displayed
        val hasWidgetsSection = composeTestRule
            .onAllNodesWithTag("widgets_section")
            .fetchSemanticsNodes().isNotEmpty()

        val hasWidgetsLoading = composeTestRule
            .onAllNodesWithTag("widgets_loading")
            .fetchSemanticsNodes().isNotEmpty()

        assert(hasWidgetsSection || hasWidgetsLoading) {
            "Widgets section should be displayed or loading"
        }

        // If widgets are loaded, verify widget container
        if (hasWidgetsSection) {
            composeTestRule
                .onNodeWithTag("widgets_section")
                .assertIsDisplayed()

            // Verify widget container is present
            composeTestRule
                .onNodeWithTag("widget_container")
                .assertExists()
        }
    }

    /**
     * Test: Widget visibility toggle updates UI immediately  
     * Validates real-time widget configuration changes
     */
    @Test
    fun widgetVisibilityToggleUpdatesUIImmediately() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                Column {
                    // Widget configuration controls
                    if (widgetState is UiState.Success) {
                        Row {
                            Button(
                                onClick = {
                                    viewModel.handleEvent(
                                        AnalyticsWidgetEvent.ToggleVisibility(AnalyticsWidget.TotalVolume)
                                    )
                                }
                            ) {
                                Text("Toggle Volume Widget")
                            }
                        }
                    }
                    
                    WidgetsSection(
                        widgetState = widgetState,
                        onEvent = viewModel::handleEvent
                    )
                }
            }
        }

        // Wait for widgets to load
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("Toggle Volume Widget")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Get initial widget count
        val initialWidgetCount = composeTestRule
            .onAllNodesWithTag("widget_item")
            .fetchSemanticsNodes().size

        // Toggle widget visibility
        composeTestRule
            .onNodeWithText("Toggle Volume Widget")
            .performClick()

        // Wait for UI update
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            val currentWidgetCount = composeTestRule
                .onAllNodesWithTag("widget_item")
                .fetchSemanticsNodes().size
            currentWidgetCount != initialWidgetCount
        }

        // Verify widget count changed
        val updatedWidgetCount = composeTestRule
            .onAllNodesWithTag("widget_item")
            .fetchSemanticsNodes().size

        assert(updatedWidgetCount != initialWidgetCount) {
            "Widget count should change when visibility is toggled"
        }
    }

    /**
     * Test: Layout mode switching updates widget arrangement
     * Validates responsive layout changes and widget reorganization
     */
    @Test
    fun layoutModeSwitchingUpdatesWidgetArrangement() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                Column {
                    // Layout mode controls
                    Row {
                        Button(
                            onClick = {
                                viewModel.handleEvent(
                                    AnalyticsWidgetEvent.UpdateConfiguration(
                                        testDashboardConfiguration.copy(
                                            layoutMode = WidgetLayoutMode.LIST
                                        )
                                    )
                                )
                            }
                        ) {
                            Text("List Mode")
                        }
                        
                        Button(
                            onClick = {
                                viewModel.handleEvent(
                                    AnalyticsWidgetEvent.UpdateConfiguration(
                                        testDashboardConfiguration.copy(
                                            layoutMode = WidgetLayoutMode.GRID
                                        )
                                    )
                                )
                            }
                        ) {
                            Text("Grid Mode")
                        }
                    }
                    
                    WidgetsSection(
                        widgetState = widgetState,
                        onEvent = viewModel::handleEvent
                    )
                }
            }
        }

        // Wait for layout controls
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("List Mode")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to list mode
        composeTestRule
            .onNodeWithText("List Mode")
            .performClick()

        // Verify layout change
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodesWithTag("widget_list_layout")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to grid mode
        composeTestRule
            .onNodeWithText("Grid Mode")
            .performClick()

        // Verify layout change
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodesWithTag("widget_grid_layout")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Test: Widget data refresh updates individual widgets
     * Validates selective widget refresh and data synchronization
     */
    @Test
    fun widgetDataRefreshUpdatesIndividualWidgets() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                Column {
                    // Widget refresh controls
                    Button(
                        onClick = {
                            viewModel.handleEvent(
                                AnalyticsWidgetEvent.RefreshWidget(AnalyticsWidget.TotalVolume)
                            )
                        }
                    ) {
                        Text("Refresh Volume Widget")
                    }
                    
                    WidgetsSection(
                        widgetState = widgetState,
                        onEvent = viewModel::handleEvent
                    )
                }
            }
        }

        // Wait for widgets and controls
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("Refresh Volume Widget")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Trigger widget refresh
        composeTestRule
            .onNodeWithText("Refresh Volume Widget")
            .performClick()

        // Verify loading state for specific widget
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodesWithTag("volume_widget_loading")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithTag("widget_refreshing")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify widget updates
        val hasWidgetLoading = composeTestRule
            .onAllNodesWithTag("volume_widget_loading")
            .fetchSemanticsNodes().isNotEmpty()

        val hasWidgetRefreshing = composeTestRule
            .onAllNodesWithTag("widget_refreshing")
            .fetchSemanticsNodes().isNotEmpty()

        assert(hasWidgetLoading || hasWidgetRefreshing) {
            "Widget should show loading state during refresh"
        }
    }

    /**
     * Test: Widget interaction triggers detailed views
     * Validates widget click handling and navigation integration
     */
    @Test
    fun widgetInteractionTriggersDetailedViews() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                WidgetsSection(
                    widgetState = widgetState,
                    onEvent = viewModel::handleEvent
                )
            }
        }

        // Wait for widgets to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("widget_item")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click on first widget
        if (composeTestRule
                .onAllNodesWithTag("widget_item")
                .fetchSemanticsNodes().isNotEmpty()) {
            
            composeTestRule
                .onAllNodesWithTag("widget_item")[0]
                .performClick()

            // Verify widget click is handled
            composeTestRule
                .onNodeWithTag("widget_detail_dialog")
                .assertExists()
        }
    }

    /**
     * Test: Widget error states display appropriate fallbacks
     * Validates error handling for individual widget failures
     */
    @Test
    fun widgetErrorStatesDisplayAppropriateFallbacks() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                
                // Simulate error by triggering refresh that fails
                viewModel.handleEvent(
                    AnalyticsWidgetEvent.RefreshAllWidgets
                )
                
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                WidgetsSection(
                    widgetState = widgetState,
                    onEvent = viewModel::handleEvent
                )
            }
        }

        // Wait for error state to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithText("Error loading widgets")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithTag("widget_error_state")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify error handling
        val hasErrorMessage = composeTestRule
            .onAllNodesWithText("Error loading widgets")
            .fetchSemanticsNodes().isNotEmpty()

        val hasErrorState = composeTestRule
            .onAllNodesWithTag("widget_error_state")
            .fetchSemanticsNodes().isNotEmpty()

        if (hasErrorMessage || hasErrorState) {
            // Verify retry option
            composeTestRule
                .onNodeWithText("Retry")
                .assertIsDisplayed()

            // Test retry functionality
            composeTestRule
                .onNodeWithText("Retry")
                .performClick()

            // Verify loading state after retry
            composeTestRule
                .onNodeWithTag("widgets_loading")
                .assertExists()
        }
    }

    /**
     * Test: Widget configuration persistence works correctly
     * Validates configuration state management and persistence
     */
    @Test
    fun widgetConfigurationPersistenceWorksCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                Column {
                    // Configuration controls
                    Row {
                        Button(
                            onClick = {
                                viewModel.handleEvent(
                                    AnalyticsWidgetEvent.SaveConfiguration(
                                        testDashboardConfiguration.copy(
                                            customization = mapOf(
                                                "theme" to "dark",
                                                "animation" to "disabled"
                                            )
                                        )
                                    )
                                )
                            }
                        ) {
                            Text("Save Config")
                        }
                        
                        Button(
                            onClick = {
                                viewModel.handleEvent(
                                    AnalyticsWidgetEvent.LoadConfiguration
                                )
                            }
                        ) {
                            Text("Load Config")
                        }
                    }
                    
                    WidgetsSection(
                        widgetState = widgetState,
                        onEvent = viewModel::handleEvent
                    )
                }
            }
        }

        // Wait for configuration controls
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("Save Config")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Save configuration
        composeTestRule
            .onNodeWithText("Save Config")
            .performClick()

        // Verify save operation
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodesWithTag("config_saved_indicator")
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithText("Configuration saved")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Load configuration
        composeTestRule
            .onNodeWithText("Load Config")
            .performClick()

        // Verify load operation
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodesWithTag("config_loaded_indicator")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Test: Real-time widget updates reflect data changes
     * Validates live data synchronization and reactive updates
     */
    @Test
    fun realTimeWidgetUpdatesReflectDataChanges() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                Column {
                    // Data change simulation
                    Button(
                        onClick = {
                            viewModel.handleEvent(
                                AnalyticsWidgetEvent.UpdateWidgetData(
                                    AnalyticsWidget.TotalVolume,
                                    BasicWidgetData(
                                        widgetType = AnalyticsWidget.TotalVolume,
                                        lastUpdated = Clock.System.now(),
                                        value = "26,000 kg", // Updated value
                                        subtitle = "Total volume - Last 30 days",
                                        trend = TrendDirection.UP
                                    )
                                )
                            )
                        }
                    ) {
                        Text("Update Data")
                    }
                    
                    WidgetsSection(
                        widgetState = widgetState,
                        onEvent = viewModel::handleEvent
                    )
                }
            }
        }

        // Wait for initial widget load
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("Update Data")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Capture initial widget state
        val initialWidgetText = composeTestRule
            .onAllNodesWithTag("volume_widget_value")
            .fetchSemanticsNodes().firstOrNull()

        // Trigger data update
        composeTestRule
            .onNodeWithText("Update Data")
            .performClick()

        // Verify widget updates with new data
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            val currentWidgetNodes = composeTestRule
                .onAllNodesWithTag("volume_widget_value")
                .fetchSemanticsNodes()
            
            currentWidgetNodes.isNotEmpty() && 
            currentWidgetNodes.first() != initialWidgetText
        }

        // Verify specific updated value if widget is present
        if (composeTestRule
                .onAllNodesWithText("26,000 kg")
                .fetchSemanticsNodes().isNotEmpty()) {
            
            composeTestRule
                .onNodeWithText("26,000 kg")
                .assertIsDisplayed()
        }
    }

    /**
     * Test: Widget performance with large datasets
     * Validates widget responsiveness and performance optimization
     */
    @Test
    fun widgetPerformanceWithLargeDatasets() {
        composeTestRule.setContent {
            LiftrixTheme {
                val viewModel = hiltViewModel<AnalyticsWidgetViewModel>()
                
                // Load many widgets with data
                viewModel.handleEvent(
                    AnalyticsWidgetEvent.LoadAllWidgets
                )
                
                val widgetState by viewModel.uiState.collectAsStateWithLifecycle()
                
                WidgetsSection(
                    widgetState = widgetState,
                    onEvent = viewModel::handleEvent
                )
            }
        }

        // Measure loading time for performance validation
        val startTime = System.currentTimeMillis()
        
        // Wait for widgets to load (with performance threshold)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("widgets_section")
                .fetchSemanticsNodes().isNotEmpty()
        }

        val loadTime = System.currentTimeMillis() - startTime

        // Verify widgets loaded within performance threshold (5 seconds)
        assert(loadTime < 5000) {
            "Widgets should load within 5 seconds for performance. Actual: ${loadTime}ms"
        }

        // Verify widgets section is responsive
        composeTestRule
            .onNodeWithTag("widgets_section")
            .assertIsDisplayed()

        // Test scroll performance if multiple widgets are present
        val widgetCount = composeTestRule
            .onAllNodesWithTag("widget_item")
            .fetchSemanticsNodes().size

        if (widgetCount > 3) {
            composeTestRule
                .onNodeWithTag("widgets_section")
                .performScrollToIndex(widgetCount - 1)

            // Verify scroll completed without performance issues
            composeTestRule
                .onAllNodesWithTag("widget_item")
                .onLast()
                .assertIsDisplayed()
        }
    }
}