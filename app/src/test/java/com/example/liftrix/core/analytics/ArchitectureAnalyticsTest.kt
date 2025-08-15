package com.example.liftrix.core.analytics

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.navigation.LiftrixRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ArchitectureAnalytics
 * 
 * Tests the analytics integration for tracking architectural patterns,
 * error occurrences, navigation usage, and state transitions.
 */
class ArchitectureAnalyticsTest {

    private lateinit var analyticsService: AnalyticsService
    private lateinit var architectureAnalytics: ArchitectureAnalytics
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        analyticsService = mockk(relaxed = true)
        testScope = TestScope(UnconfinedTestDispatcher())
        architectureAnalytics = ArchitectureAnalytics(analyticsService, testScope)
    }

    @Test
    fun `trackErrorOccurrence should log error with comprehensive parameters`() = runTest {
        // Given
        val networkError = LiftrixError.NetworkError(
            errorMessage = "Connection failed",
            httpStatusCode = 404,
            networkType = "wifi",
            analyticsContext = mapOf("screen" to "home", "action" to "refresh")
        )
        val context = mapOf("user_id" to "test123", "session_id" to "session456")

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackErrorOccurrence(networkError, context)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_error_occurred",
                parameters = match { params ->
                    params["error_type"] == "NetworkError" &&
                    params["error_message"] == "Connection failed" &&
                    params["is_recoverable"] == true &&
                    params["http_status_code"] == 404 &&
                    params["network_type"] == "wifi" &&
                    params["user_id"] == "test123" &&
                    params["session_id"] == "session456" &&
                    params["content_type"] == "architecture_error"
                }
            )
        }
    }

    @Test
    fun `trackNavigationUsage should log navigation with route and source`() = runTest {
        // Given
        val route = LiftrixRoute.WorkoutDetails("workout123")
        val source = "menu_button"

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackNavigationUsage(route, source)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_navigation_usage",
                parameters = match { params ->
                    params["route_type"] == "WorkoutDetails" &&
                    params["route_parameters"] == "workoutId=workout123" &&
                    params["navigation_source"] == "menu_button" &&
                    params["content_type"] == "architecture_navigation"
                }
            )
        }
    }

    @Test
    fun `trackStateTransition should log state change with transition type`() = runTest {
        // Given
        val fromState = UiState.Loading
        val toState = UiState.Success("test_data")

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackStateTransition(fromState, toState)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_state_transition",
                parameters = match { params ->
                    params["from_state_type"] == "Loading" &&
                    params["to_state_type"] == "Success" &&
                    params["state_transition_type"] == "loading_to_success" &&
                    params["has_data"] == true &&
                    params["is_loading"] == false &&
                    params["can_retry"] == false &&
                    params["content_type"] == "architecture_state"
                }
            )
        }
    }

    @Test
    fun `trackArchitectureMetrics should log metrics with proper categorization`() = runTest {
        // Given
        val metrics = mapOf(
            "navigation_performance" to 8.5,
            "error_handling_success_rate" to 95.2,
            "state_update_count" to 150,
            "memory_usage_mb" to 45.7,
            "feature_enabled" to true
        )

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackArchitectureMetrics(metrics)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_architecture_metrics",
                parameters = match { params ->
                    params["metric_value_navigation_performance"] == 8.5 &&
                    params["metric_type_navigation_performance"] == "numeric" &&
                    params["metric_category_navigation_performance"] == "navigation" &&
                    params["metric_value_error_handling_success_rate"] == 95.2 &&
                    params["metric_category_error_handling_success_rate"] == "error_handling" &&
                    params["metric_value_feature_enabled"] == "true" &&
                    params["metric_type_feature_enabled"] == "boolean" &&
                    params["content_type"] == "architecture_metrics"
                }
            )
        }
    }

    @Test
    fun `trackErrorPatternAnalysis should log error patterns for monitoring`() = runTest {
        // Given
        val errorPattern = "network_timeout_on_feed_load"
        val frequency = 25
        val impactLevel = "medium"

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackErrorPatternAnalysis(errorPattern, frequency, impactLevel)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_error_pattern_analysis",
                parameters = match { params ->
                    params["error_pattern"] == errorPattern &&
                    params["pattern_frequency"] == frequency &&
                    params["impact_level"] == impactLevel &&
                    params["content_type"] == "architecture_error_pattern"
                }
            )
        }
    }

    @Test
    fun `trackNavigationPatternAnalysis should log navigation patterns for UX optimization`() = runTest {
        // Given
        val navigationPattern = "home_to_workout_details"
        val usageFrequency = 342
        val successRate = 98.5

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackNavigationPatternAnalysis(navigationPattern, usageFrequency, successRate)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_navigation_pattern_analysis",
                parameters = match { params ->
                    params["navigation_pattern"] == navigationPattern &&
                    params["usage_frequency"] == usageFrequency &&
                    params["success_rate"] == successRate &&
                    params["content_type"] == "architecture_navigation_pattern"
                }
            )
        }
    }

    @Test
    fun `trackErrorOccurrence with ValidationError should include validation details`() = runTest {
        // Given
        val validationError = LiftrixError.ValidationError(
            field = "email",
            violations = listOf("invalid_format", "required"),
            errorMessage = "Email validation failed"
        )

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackErrorOccurrence(validationError)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_error_occurred",
                parameters = match { params ->
                    params["error_type"] == "ValidationError" &&
                    params["validation_field"] == "email" &&
                    params["validation_violations"] == "invalid_format,required" &&
                    params["is_recoverable"] == true
                }
            )
        }
    }

    @Test
    fun `trackStateTransition with Error to Success should track error recovery`() = runTest {
        // Given
        val errorState = UiState.Error<String>(
            error = LiftrixError.NetworkError(),
            previousData = "cached_data"
        )
        val successState = UiState.Success("new_data")

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackStateTransition(errorState, successState)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_state_transition",
                parameters = match { params ->
                    params["from_state_type"] == "Error" &&
                    params["to_state_type"] == "Success" &&
                    params["state_transition_type"] == "error_to_success" &&
                    params["has_data"] == true &&
                    params["can_retry"] == false
                }
            )
        }
    }

    @Test
    fun `trackNavigationUsage with complex route should extract parameters correctly`() = runTest {
        // Given
        val route = LiftrixRoute.ExerciseSelection(
            templateId = "template456",
            isForTemplate = true
        )
        val source = "create_template_flow"

        coEvery { analyticsService.logEvent(any(), any()) } returns Result.success(Unit)

        // When
        architectureAnalytics.trackNavigationUsage(route, source)

        // Then
        coVerify {
            analyticsService.logEvent(
                eventName = "liftrix_navigation_usage",
                parameters = match { params ->
                    params["route_type"] == "ExerciseSelection" &&
                    params["route_parameters"] == "templateId=template456,isForTemplate=true" &&
                    params["navigation_source"] == "create_template_flow"
                }
            )
        }
    }
}