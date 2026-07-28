package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.TimeRange
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
import kotlinx.datetime.Month
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressChartsViewModelTest {
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
    fun switchingToLongerRangesCompletesWithoutLeavingAndReturningToScreen() = runTest(dispatcher) {
        val viewModel = ProgressChartsViewModel(FakeProgressDataPort())
        viewModel.handleCoordinatorEvent(CoordinatorEvent.UserAuthChanged(USER_ID))
        advanceUntilIdle()

        listOf(TimeRange.lastSixMonths(), TimeRange.allTime()).forEach { timeRange ->
            viewModel.handleCoordinatorEvent(CoordinatorEvent.TimePeriodChanged(timeRange))
            advanceUntilIdle()

            val state = (viewModel.uiState.value as UiState.Success).data
            assertEquals(timeRange, state.currentTimeRange)
            assertTrue(state.volumeChart is AsyncData.Success)
            assertTrue(state.durationChart is AsyncData.Success)
            assertTrue(state.frequencyChart is AsyncData.Success)
            assertTrue(state.volumeCalendar is AsyncData.Success)
        }
    }

    private class FakeProgressDataPort : ProgressDataPort {
        override suspend fun getVolumeData(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<List<VolumeDataPoint>> {
            delay(10)
            return Result.success(emptyList())
        }

        override suspend fun getDurationData(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<List<DurationDataPoint>> {
            delay(10)
            return Result.success(emptyList())
        }

        override suspend fun getFrequencyData(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<List<FrequencyDataPoint>> {
            delay(10)
            return Result.success(emptyList())
        }

        override suspend fun getVolumeCalendarData(
            userId: String
        ): LiftrixResult<VolumeCalendarData> = Result.success(
            VolumeCalendarData.empty(2026, Month.JULY)
        )

        override suspend fun getProgressSummary(
            userId: String,
            timeRange: TimeRange
        ): LiftrixResult<ProgressSummary> = error("Not used by ProgressChartsViewModel")

        override suspend fun refreshAllData(userId: String): LiftrixResult<Unit> =
            Result.success(Unit)
    }

    private companion object {
        const val USER_ID = "progress-user"
    }
}
