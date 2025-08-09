package com.example.liftrix.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.progress.components.AdaptiveWidgetGrid
import com.example.liftrix.ui.progress.components.SimpleWidgetRenderer
import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.WindowWidthSizeClass
import com.example.liftrix.ui.common.WindowHeightSizeClass
import com.example.liftrix.ui.common.components.ErrorDisplay
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Comprehensive error handling tests for Progress Tab UI components.
 * 
 * Tests error scenarios including:
 * - Network errors during widget data loading
 * - Invalid widget configurations
 * - Memory pressure scenarios
 * - Concurrent access issues
 * - Service unavailability
 * - Data corruption scenarios
 * - Recovery mechanisms
 */
@RunWith(AndroidJUnit4::class)
class ErrorHandlingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createTestWidgets() = listOf(
        AnalyticsWidget.TotalVolume,
        AnalyticsWidget.WorkoutFrequency,
        AnalyticsWidget.StrengthProgress
    )

    private fun createErrorWidgetData(widget: AnalyticsWidget, errorMessage: String): WidgetData {
        return object : WidgetData {
            override val widgetType = widget
            override val lastUpdated = kotlinx.datetime.Clock.System.now()
            override val primaryValue = "Error"
            override val secondaryValue = errorMessage
            override val unit = ""
            override val trend = TrendDirection.STABLE
            override val hasError = true
            override val errorMessage = errorMessage
        }
    }

    private fun createTimeoutWidgetData(widget: AnalyticsWidget): WidgetData {
        return createErrorWidgetData(widget, "Request timeout")
    }

    private fun createNetworkErrorWidgetData(widget: AnalyticsWidget): WidgetData {
        return createErrorWidgetData(widget, "Network unavailable")
    }

    @Test
    fun test_network_error_graceful_handling() {
        val widgets = createTestWidgets()
        
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
                    widgetDataProvider = { widget -> createNetworkErrorWidgetData(widget) }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify error states are shown gracefully
        composeTestRule.onNodeWithText("Network unavailable")
            .assertExists()
        
        // Verify widgets are still interactive (for retry)
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
                .assertIsDisplayed()
        }
    }

    @Test
    fun test_timeout_error_with_retry_mechanism() {
        val widgets = createTestWidgets()
        var shouldTimeout by mutableStateOf(true)
        var retryCount by mutableStateOf(0)
        
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
                    onWidgetClick = { 
                        retryCount++
                        if (retryCount >= 2) shouldTimeout = false
                    },
                    widgetDataProvider = { widget ->
                        if (shouldTimeout) {
                            createTimeoutWidgetData(widget)
                        } else {
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "Recovered",
                                secondaryValue = "Data loaded successfully",
                                unit = "",
                                trend = TrendDirection.UP
                            )
                        }
                    }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show timeout error
        composeTestRule.onNodeWithText("Request timeout")
            .assertExists()
        
        // Click to retry (first attempt)
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .performClick()
        composeTestRule.waitForIdle()
        
        // Still should show timeout
        composeTestRule.onNodeWithText("Request timeout")
            .assertExists()
        
        // Click to retry (second attempt - should succeed)
        composeTestRule.onNodeWithContentDescription("Total Volume widget")
            .performClick()
        composeTestRule.waitForIdle()
        
        // Should now show recovered data
        composeTestRule.onNodeWithText("Recovered")
            .assertExists()
    }

    @Test
    fun test_partial_widget_failures() {
        val widgets = createTestWidgets()
        
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
                    widgetDataProvider = { widget ->
                        when (widget) {
                            AnalyticsWidget.TotalVolume -> BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "2,450 kg",
                                secondaryValue = "Success",
                                unit = "kg",
                                trend = TrendDirection.UP
                            )
                            AnalyticsWidget.WorkoutFrequency -> createNetworkErrorWidgetData(widget)
                            AnalyticsWidget.StrengthProgress -> createTimeoutWidgetData(widget)
                            else -> createErrorWidgetData(widget, "Unknown error")
                        }
                    }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify successful widget shows data
        composeTestRule.onNodeWithText("2,450 kg")
            .assertExists()
            .assertIsDisplayed()
        
        // Verify failed widgets show appropriate errors
        composeTestRule.onNodeWithText("Network unavailable")
            .assertExists()
        
        composeTestRule.onNodeWithText("Request timeout")
            .assertExists()
        
        // Verify all widgets are still present
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
    }

    @Test
    fun test_loading_state_timeout_handling() {
        val widgets = createTestWidgets()
        var isLoading by mutableStateOf(true)
        var hasTimedOut by mutableStateOf(false)
        
        // Simulate timeout after delay
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000) // 2 second timeout
            isLoading = false
            hasTimedOut = true
        }
        
        composeTestRule.setContent {
            LiftrixTheme {
                when {
                    isLoading -> {
                        LoadingIndicator()
                    }
                    hasTimedOut -> {
                        ErrorDisplay(
                            error = LiftrixError.NetworkError(
                                errorMessage = "Operation timed out",
                                analyticsContext = mapOf("timeout" to "2000ms")
                            ),
                            onRetry = { 
                                isLoading = true
                                hasTimedOut = false 
                            }
                        )
                    }
                    else -> {
                        AdaptiveWidgetGrid(
                            widgets = widgets,
                            windowSizeClass = WindowSizeClass(
                                widthSizeClass = WindowWidthSizeClass.COMPACT,
                                heightSizeClass = WindowHeightSizeClass.MEDIUM,
                                widthDp = 400.dp,
                                heightDp = 800.dp
                            ),
                            widgetDataProvider = { widget ->
                                BasicWidgetData(
                                    widgetType = widget,
                                    lastUpdated = kotlinx.datetime.Clock.System.now(),
                                    primaryValue = "Success",
                                    secondaryValue = "Loaded after retry",
                                    unit = "",
                                    trend = TrendDirection.STABLE
                                )
                            }
                        )
                    }
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show loading
        composeTestRule.onNode(hasTestTag("loading_indicator") or hasText("Loading"))
            .assertExists()
        
        // Wait for timeout
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            hasTimedOut
        }
        
        // Should show timeout error
        composeTestRule.onNodeWithText("Operation timed out")
            .assertExists()
        
        // Test retry functionality
        composeTestRule.onNodeWithText("Retry")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun test_memory_pressure_graceful_degradation() {
        val maxWidgets = AnalyticsWidget.getAllWidgets()
        var memoryPressure by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                AdaptiveWidgetGrid(
                    widgets = if (memoryPressure) maxWidgets.take(3) else maxWidgets,
                    windowSizeClass = WindowSizeClass(
                        widthSizeClass = WindowWidthSizeClass.COMPACT,
                        heightSizeClass = WindowHeightSizeClass.MEDIUM,
                        widthDp = 400.dp,
                        heightDp = 800.dp
                    ),
                    widgetDataProvider = { widget ->
                        if (memoryPressure) {
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "—",
                                secondaryValue = "Reduced for performance",
                                unit = "",
                                trend = TrendDirection.STABLE
                            )
                        } else {
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "Normal",
                                secondaryValue = "Full data",
                                unit = "",
                                trend = TrendDirection.STABLE
                            )
                        }
                    }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show all widgets with full data
        composeTestRule.onNodeWithText("Full data")
            .assertExists()
        
        // Simulate memory pressure
        memoryPressure = true
        composeTestRule.waitForIdle()
        
        // Should show fewer widgets with reduced data
        composeTestRule.onNodeWithText("Reduced for performance")
            .assertExists()
        
        // Verify basic functionality is maintained
        maxWidgets.take(3).forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
    }

    @Test
    fun test_concurrent_access_error_handling() {
        val widgets = createTestWidgets()
        var concurrentError by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                if (concurrentError) {
                    ErrorDisplay(
                        error = LiftrixError.DatabaseError(
                            errorMessage = "Database is locked",
                            analyticsContext = mapOf("concurrent_access" to "true")
                        ),
                        onRetry = { concurrentError = false }
                    )
                } else {
                    AdaptiveWidgetGrid(
                        widgets = widgets,
                        windowSizeClass = WindowSizeClass(
                            widthSizeClass = WindowWidthSizeClass.COMPACT,
                            heightSizeClass = WindowHeightSizeClass.MEDIUM,
                            widthDp = 400.dp,
                            heightDp = 800.dp
                        ),
                        widgetDataProvider = { widget ->
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "Available",
                                secondaryValue = "Database accessible",
                                unit = "",
                                trend = TrendDirection.STABLE
                            )
                        }
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show normal data
        composeTestRule.onNodeWithText("Database accessible")
            .assertExists()
        
        // Simulate concurrent access error
        concurrentError = true
        composeTestRule.waitForIdle()
        
        // Should show error message
        composeTestRule.onNodeWithText("Database is locked")
            .assertExists()
        
        // Test retry mechanism
        composeTestRule.onNodeWithText("Retry")
            .performClick()
        composeTestRule.waitForIdle()
        
        // Should recover
        composeTestRule.onNodeWithText("Database accessible")
            .assertExists()
    }

    @Test
    fun test_data_corruption_recovery() {
        val widgets = createTestWidgets()
        var dataCorrupted by mutableStateOf(false)
        
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
                    widgetDataProvider = { widget ->
                        if (dataCorrupted) {
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "—",
                                secondaryValue = "Data validation failed",
                                unit = "",
                                trend = TrendDirection.STABLE
                            )
                        } else {
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "Valid",
                                secondaryValue = "Data integrity confirmed",
                                unit = "",
                                trend = TrendDirection.UP
                            )
                        }
                    }
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show valid data
        composeTestRule.onNodeWithText("Data integrity confirmed")
            .assertExists()
        
        // Simulate data corruption
        dataCorrupted = true
        composeTestRule.waitForIdle()
        
        // Should show validation failure
        composeTestRule.onNodeWithText("Data validation failed")
            .assertExists()
        
        // Verify widgets are still rendered (graceful degradation)
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // Test recovery
        dataCorrupted = false
        composeTestRule.waitForIdle()
        
        // Should show valid data again
        composeTestRule.onNodeWithText("Data integrity confirmed")
            .assertExists()
    }

    @Test
    fun test_service_unavailability_fallback() {
        val widgets = createTestWidgets()
        var serviceAvailable by mutableStateOf(true)
        
        composeTestRule.setContent {
            LiftrixTheme {
                if (!serviceAvailable) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        widgets.forEach { widget ->
                            SimpleWidgetRenderer(
                                widget = widget,
                                primaryValue = "Offline",
                                secondaryValue = "Using cached data",
                                isLoading = false,
                                error = null
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
                        widgetDataProvider = { widget ->
                            BasicWidgetData(
                                widgetType = widget,
                                lastUpdated = kotlinx.datetime.Clock.System.now(),
                                primaryValue = "Live",
                                secondaryValue = "Real-time data",
                                unit = "",
                                trend = TrendDirection.UP
                            )
                        }
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Initially should show live data
        composeTestRule.onNodeWithText("Real-time data")
            .assertExists()
        
        // Simulate service unavailability
        serviceAvailable = false
        composeTestRule.waitForIdle()
        
        // Should fallback to cached data
        composeTestRule.onNodeWithText("Using cached data")
            .assertExists()
        
        composeTestRule.onNodeWithText("Offline")
            .assertExists()
        
        // Verify all widgets are still functional
        widgets.forEach { widget ->
            composeTestRule.onNodeWithContentDescription("${widget.displayName} widget")
                .assertExists()
        }
        
        // Test service recovery
        serviceAvailable = true
        composeTestRule.waitForIdle()
        
        // Should return to live data
        composeTestRule.onNodeWithText("Real-time data")
            .assertExists()
    }
}