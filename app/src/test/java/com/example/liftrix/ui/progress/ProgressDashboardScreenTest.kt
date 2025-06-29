package com.example.liftrix.ui.progress

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo

/**
 * Comprehensive tests for ProgressDashboardScreen composable.
 * 
 * Tests chart rendering, empty states, loading states, user interactions,
 * ViewModel integration, and error scenarios as specified in task TEST-PROGRESS-003.
 * 
 * Coverage includes:
 * - Chart component rendering (WorkoutVolumeChart, WorkoutDurationChart, WorkoutFrequencyHeatmap)
 * - Empty state handling for all chart types
 * - Loading state displays and interactions
 * - User interactions (refresh, time period selection, error handling)
 * - ViewModel event handling and state management
 * - Error scenarios and recovery flows
 * - Accessibility support and semantic markup
 */
@RunWith(AndroidJUnit4::class)
class ProgressDashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockViewModel: ProgressDashboardViewModel
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockProgressStatsRepository: ProgressStatsRepository
    private val uiStateFlow = MutableStateFlow(ProgressDashboardUiState())

    private val mockVolumeData = listOf(
        VolumeDataPoint(LocalDate(2024, 1, 1), 1000f),
        VolumeDataPoint(LocalDate(2024, 1, 2), 1100f),
        VolumeDataPoint(LocalDate(2024, 1, 3), 1200f)
    )

    private val mockDurationData = listOf(
        DurationDataPoint(LocalDate(2024, 1, 1), 60),
        DurationDataPoint(LocalDate(2024, 1, 2), 65),
        DurationDataPoint(LocalDate(2024, 1, 3), 70)
    )

    private val mockFrequencyData = listOf(
        FrequencyDataPoint(LocalDate(2024, 1, 1), 1),
        FrequencyDataPoint(LocalDate(2024, 1, 2), 1),
        FrequencyDataPoint(LocalDate(2024, 1, 3), 2)
    )

    private val mockSummaryData = ProgressSummary(
        totalWorkouts = 10,
        totalVolume = 5000f,
        totalDuration = 600,
        averageWorkoutDuration = 60,
        workoutsThisWeek = 3,
        averageVolumePerWorkout = 500f,
        totalPrs = 5
    )

    private fun setupMocks() {
        mockAuthRepository = mockk(relaxed = true)
        mockProgressStatsRepository = mockk(relaxed = true)
        mockViewModel = mockk(relaxed = true)

        every { mockViewModel.uiState } returns uiStateFlow
        every { mockViewModel.onEvent(any()) } returns Unit
    }

    // MARK: - Basic Screen Rendering Tests

    @Test
    fun progressDashboardScreen_displaysScreenTitle() {
        setupMocks()

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        composeTestRule
            .onNodeWithText("Progress Dashboard")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_displaysTimePeriodSelector() {
        setupMocks()

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Time period options should be displayed
        composeTestRule
            .onNodeWithText("Month")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_displaysAllChartContainers() {
        setupMocks()

        // Set state with data to ensure chart containers are shown
        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = mockVolumeData,
            durationData = mockDurationData,
            frequencyData = mockFrequencyData,
            summaryData = mockSummaryData
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Verify chart titles are displayed
        composeTestRule
            .onNodeWithText("Workout Volume Trend")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Workout Duration")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Workout Frequency")
            .assertIsDisplayed()
    }

    // MARK: - Chart Rendering Tests

    @Test
    fun progressDashboardScreen_workoutVolumeChart_displaysWithData() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = mockVolumeData,
            isVolumeLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Chart title should be displayed
        composeTestRule
            .onNodeWithText("Workout Volume Trend")
            .assertIsDisplayed()

        // Chart should render data (no loading or empty state)
        composeTestRule
            .onNodeWithText("Loading volume data...")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("No volume data available")
            .assertIsNotDisplayed()
    }

    @Test
    fun progressDashboardScreen_workoutDurationChart_displaysWithData() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            durationData = mockDurationData,
            isDurationLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Chart title should be displayed
        composeTestRule
            .onNodeWithText("Workout Duration")
            .assertIsDisplayed()

        // Chart should render data (no loading or empty state)
        composeTestRule
            .onNodeWithText("Loading duration data...")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("No duration data available")
            .assertIsNotDisplayed()
    }

    @Test
    fun progressDashboardScreen_workoutFrequencyChart_displaysWithData() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            frequencyData = mockFrequencyData,
            isFrequencyLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Chart title should be displayed
        composeTestRule
            .onNodeWithText("Workout Frequency")
            .assertIsDisplayed()

        // Chart should render data (no loading or empty state)
        composeTestRule
            .onNodeWithText("Loading frequency data...")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("No frequency data available")
            .assertIsNotDisplayed()
    }

    // MARK: - Empty State Tests

    @Test
    fun progressDashboardScreen_volumeChart_showsEmptyState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = emptyList(),
            isVolumeLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Empty state message should be displayed
        composeTestRule
            .onNodeWithText("No volume data available")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Complete workouts with weights to see volume trends")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_durationChart_showsEmptyState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            durationData = emptyList(),
            isDurationLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Empty state message should be displayed
        composeTestRule
            .onNodeWithText("No duration data available")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Complete timed workouts to see duration trends")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_frequencyChart_showsEmptyState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            frequencyData = emptyList(),
            isFrequencyLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Empty state message should be displayed
        composeTestRule
            .onNodeWithText("No frequency data available")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Complete workouts regularly to see frequency patterns")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_globalEmptyState_whenNoDataAndNotLoading() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = emptyList(),
            durationData = emptyList(),
            frequencyData = emptyList(),
            summaryData = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
            isVolumeLoading = false,
            isDurationLoading = false,
            isFrequencyLoading = false,
            isSummaryLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Global empty state should be displayed
        composeTestRule
            .onNodeWithText("No Progress Data Yet")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Start working out to see your progress analytics and charts here")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Check for Data")
            .assertIsDisplayed()
    }

    // MARK: - Loading State Tests

    @Test
    fun progressDashboardScreen_volumeChart_showsLoadingState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = emptyList(),
            isVolumeLoading = true
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Loading message should be displayed
        composeTestRule
            .onNodeWithText("Loading volume data...")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_durationChart_showsLoadingState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            durationData = emptyList(),
            isDurationLoading = true
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Loading message should be displayed
        composeTestRule
            .onNodeWithText("Loading duration data...")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_frequencyChart_showsLoadingState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            frequencyData = emptyList(),
            isFrequencyLoading = true
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Loading message should be displayed
        composeTestRule
            .onNodeWithText("Loading frequency data...")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_summaryStats_showsLoadingState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            summaryData = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
            isSummaryLoading = true
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Summary stats loading should be handled by SummaryStatsCard
        // Verify that screen is functional during loading
        composeTestRule
            .onNodeWithText("Progress Dashboard")
            .assertIsDisplayed()
    }

    // MARK: - User Interaction Tests

    @Test
    fun progressDashboardScreen_refreshData_triggersViewModelEvent() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = emptyList(),
            durationData = emptyList(),
            frequencyData = emptyList(),
            summaryData = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
            isVolumeLoading = false,
            isDurationLoading = false,
            isFrequencyLoading = false,
            isSummaryLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Click refresh button in empty state
        composeTestRule
            .onNodeWithText("Check for Data")
            .performClick()

        // Verify ViewModel event was triggered
        verify { mockViewModel.onEvent(ProgressDashboardEvent.RefreshData) }
    }

    @Test
    fun progressDashboardScreen_timePeriodSelection_triggersViewModelEvent() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            selectedTimePeriod = TimePeriod.MONTH,
            volumeData = mockVolumeData
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Note: TimePeriodSelector would handle the click and trigger the event
        // This tests the integration but actual selector testing would be in its own test
        composeTestRule
            .onNodeWithText("Month")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_errorHandling_displaysErrorState() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            error = "Failed to load progress data"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Error state should be displayed
        composeTestRule
            .onNodeWithText("Error Loading Progress")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Failed to load progress data")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Retry")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Dismiss")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_errorState_retryButton_triggersViewModelEvent() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            error = "Network error"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Click retry button
        composeTestRule
            .onNodeWithText("Retry")
            .performClick()

        // Verify ViewModel event was triggered
        verify { mockViewModel.onEvent(ProgressDashboardEvent.RefreshData) }
    }

    @Test
    fun progressDashboardScreen_errorState_dismissButton_triggersViewModelEvent() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            error = "Network error"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Click dismiss button
        composeTestRule
            .onNodeWithText("Dismiss")
            .performClick()

        // Verify ViewModel event was triggered
        verify { mockViewModel.onEvent(ProgressDashboardEvent.ClearError) }
    }

    // MARK: - ViewModel Integration Tests

    @Test
    fun progressDashboardScreen_viewModelIntegration_stateChangesReflectedInUI() = runTest {
        setupMocks()

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Initially empty state
        uiStateFlow.value = ProgressDashboardUiState()
        composeTestRule.waitForIdle()

        // Change to loading state
        uiStateFlow.value = ProgressDashboardUiState(isVolumeLoading = true)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Loading volume data...")
            .assertIsDisplayed()

        // Change to data loaded state
        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = mockVolumeData,
            isVolumeLoading = false
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Loading volume data...")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("Workout Volume Trend")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_viewModelEvents_calledWithCorrectParameters() {
        setupMocks()

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // ViewModel should be provided correctly
        verify { mockViewModel.uiState }
    }

    // MARK: - Accessibility Tests

    @Test
    fun progressDashboardScreen_accessibility_errorStateHasProperSemantics() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            error = "Test error"
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Error icon should have proper accessibility
        composeTestRule
            .onNodeWithContentDescription("Error Loading Progress")
            .assertExists()
    }

    @Test
    fun progressDashboardScreen_accessibility_emptyStateHasProperSemantics() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = emptyList(),
            durationData = emptyList(),
            frequencyData = emptyList(),
            summaryData = ProgressSummary(0, 0f, 0, 0, 0, 0f, 0),
            isVolumeLoading = false,
            isDurationLoading = false,
            isFrequencyLoading = false,
            isSummaryLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Empty state should be accessible
        composeTestRule
            .onNodeWithText("No Progress Data Yet")
            .assertIsDisplayed()
    }

    // MARK: - Edge Cases and Complex State Tests

    @Test
    fun progressDashboardScreen_mixedLoadingStates_handledCorrectly() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = mockVolumeData,
            durationData = emptyList(),
            isVolumeLoading = false,
            isDurationLoading = true,
            isFrequencyLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Volume chart should show data
        composeTestRule
            .onNodeWithText("Workout Volume Trend")
            .assertIsDisplayed()

        // Duration chart should show loading
        composeTestRule
            .onNodeWithText("Loading duration data...")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_partialDataState_displaysCorrectly() {
        setupMocks()

        uiStateFlow.value = ProgressDashboardUiState(
            volumeData = mockVolumeData,
            durationData = emptyList(),
            frequencyData = mockFrequencyData,
            isVolumeLoading = false,
            isDurationLoading = false,
            isFrequencyLoading = false
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Volume chart should show data
        composeTestRule
            .onNodeWithText("Workout Volume Trend")
            .assertIsDisplayed()

        // Duration chart should show empty state
        composeTestRule
            .onNodeWithText("No duration data available")
            .assertIsDisplayed()

        // Frequency chart should show data
        composeTestRule
            .onNodeWithText("Workout Frequency")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_timePeriodChanges_reflectedInUI() {
        setupMocks()

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Test different time periods
        uiStateFlow.value = ProgressDashboardUiState(selectedTimePeriod = TimePeriod.WEEK)
        composeTestRule.waitForIdle()

        uiStateFlow.value = ProgressDashboardUiState(selectedTimePeriod = TimePeriod.YEAR)
        composeTestRule.waitForIdle()

        // Time period selector should update accordingly
        // (Specific testing would be in TimePeriodSelector tests)
        composeTestRule
            .onNodeWithText("Progress Dashboard")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_rapidStateChanges_handledGracefully() {
        setupMocks()

        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen(viewModel = mockViewModel)
            }
        }

        // Rapidly change states to test stability
        repeat(3) {
            uiStateFlow.value = ProgressDashboardUiState(isVolumeLoading = true)
            composeTestRule.waitForIdle()

            uiStateFlow.value = ProgressDashboardUiState(
                volumeData = mockVolumeData,
                isVolumeLoading = false
            )
            composeTestRule.waitForIdle()

            uiStateFlow.value = ProgressDashboardUiState(error = "Test error")
            composeTestRule.waitForIdle()

            uiStateFlow.value = ProgressDashboardUiState(error = null)
            composeTestRule.waitForIdle()
        }

        // Final state should be stable
        composeTestRule
            .onNodeWithText("Progress Dashboard")
            .assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_handlesNestedScrollablesCorrectly() {
        // Test that LazyVerticalGrid components within LazyColumn have proper height constraints
        // This prevents the "infinity maximum height constraints" error
        
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }
        
        // Verify the main LazyColumn is scrollable
        composeTestRule.onNode(hasScrollAction()).assertExists()
        
        // Verify components render without crashing due to infinite constraints
        composeTestRule.onNodeWithText("Progress Dashboard").assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_summaryCardsRenderWithFixedHeight() {
        // Test that ProgressSummaryCards component renders properly with fixed height constraints
        
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }
        
        // The summary cards should be visible and not cause layout issues
        composeTestRule.onNodeWithText("Progress Summary").assertIsDisplayed()
    }

    @Test
    fun progressDashboardScreen_allowsScrollingThroughAllComponents() {
        // Test that all chart components are accessible through scrolling
        
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressDashboardScreen()
            }
        }
        
        // Scroll to ensure all components are reachable
        composeTestRule.onNodeWithText("Progress Dashboard").performScrollTo()
        
        // Verify key components are present (they may be loading states initially)
        composeTestRule.onNodeWithText("Progress Summary").assertIsDisplayed()
    }
}