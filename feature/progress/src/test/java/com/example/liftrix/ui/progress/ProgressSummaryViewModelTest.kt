package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.progress.ProgressDataPort
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.ui.common.state.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mainSummaryCardsLoadForSixMonthsAndAllTimeWithoutNavigation() = runTest(dispatcher) {
        val dataPort = FakeProgressDataPort()
        val viewModel = ProgressSummaryViewModel(dataPort)
        viewModel.handleCoordinatorEvent(CoordinatorEvent.UserAuthChanged(USER_ID))
        advanceUntilIdle()

        listOf(TimeRange.lastSixMonths(), TimeRange.allTime()).forEach { timeRange ->
            viewModel.handleCoordinatorEvent(CoordinatorEvent.TimePeriodChanged(timeRange))
            advanceUntilIdle()

            val state = (viewModel.uiState.value as UiState.Success).data
            assertEquals(timeRange, state.currentTimeRange)
            assertTrue(state.summaryData is AsyncData.Success)
            val summary = (state.summaryData as AsyncData.Success<ProgressSummary>).data
            assertEquals(expectedWorkoutCount(timeRange.type), summary.totalWorkouts)
        }

        assertEquals(
            listOf(TimeRangeType.MONTH, TimeRangeType.SIX_MONTHS, TimeRangeType.ALL_TIME),
            dataPort.requestedRanges
        )
    }

    private class FakeProgressDataPort : ProgressDataPort {
        val requestedRanges = mutableListOf<TimeRangeType>()

        override suspend fun getProgressSummary(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<ProgressSummary> {
            requestedRanges += timeRange.type
            delay(10)
            return Result.success(
                ProgressSummary(
                    totalWorkouts = expectedWorkoutCount(timeRange.type),
                    totalVolume = 1_000f,
                    averageDuration = 45,
                    currentStreak = 3,
                    longestStreak = 7,
                    averageWorkoutsPerWeek = 3f,
                    totalActiveTime = 180
                )
            )
        }

        override suspend fun getVolumeData(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<List<VolumeDataPoint>> = error("Not used by ProgressSummaryViewModel")

        override suspend fun getDurationData(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<List<DurationDataPoint>> = error("Not used by ProgressSummaryViewModel")

        override suspend fun getFrequencyData(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<List<FrequencyDataPoint>> = error("Not used by ProgressSummaryViewModel")

        override suspend fun getVolumeCalendarData(
            userId: String
        ): LiftrixResult<VolumeCalendarData> = error("Not used by ProgressSummaryViewModel")

        override suspend fun refreshAllData(userId: String): LiftrixResult<Unit> =
            Result.success(Unit)
    }

    private companion object {
        const val USER_ID = "progress-user"

        fun expectedWorkoutCount(timeRange: TimeRangeType): Int = when (timeRange) {
            TimeRangeType.MONTH -> 1
            TimeRangeType.SIX_MONTHS -> 6
            TimeRangeType.ALL_TIME -> 99
        }
    }
}
