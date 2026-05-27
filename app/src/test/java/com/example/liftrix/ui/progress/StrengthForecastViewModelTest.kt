package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.StrengthForecastResult
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.progress.ExportOneRmDataRequest
import com.example.liftrix.domain.progress.ExportVolumeDataRequest
import com.example.liftrix.domain.progress.ExportWorkoutFrequencyDataRequest
import com.example.liftrix.domain.progress.ExerciseRankingData
import com.example.liftrix.domain.progress.MuscleGroup
import com.example.liftrix.domain.progress.MuscleGroupAnalyticsData
import com.example.liftrix.domain.progress.OneRmProgressionData
import com.example.liftrix.domain.progress.ProgressAuthPort
import com.example.liftrix.domain.progress.ProgressDetailAnalyticsGateway
import com.example.liftrix.domain.model.UserId
import com.example.liftrix.domain.progress.VolumeAnalysisData
import com.example.liftrix.domain.progress.WorkoutFrequencyData
import com.example.liftrix.test.MainDispatcherRule
import com.example.liftrix.ui.common.state.UiState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class StrengthForecastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `auth coordinator event loads forecast and selection can change`() = runTest {
        val viewModel = StrengthForecastViewModel(
            FakeGateway(StrengthForecastResult(emptyList())),
            FakeAuthPort()
        )

        viewModel.handleCoordinatorEvent(CoordinatorEvent.UserAuthChanged("user-1"))
        viewModel.handleEvent(StrengthForecastEvent.SelectExercise("bench"))

        val state = (viewModel.uiState.value as UiState.Success).data
        assertEquals("user-1", state.userId)
        assertEquals("bench", state.selectedExerciseId)
        assertTrue(state.errorMessage == null)
    }

    @Test
    fun `load event resolves authenticated user when opened from detail screen`() = runTest {
        val viewModel = StrengthForecastViewModel(
            FakeGateway(StrengthForecastResult(emptyList())),
            FakeAuthPort("detail-user")
        )

        viewModel.handleEvent(StrengthForecastEvent.Load)

        val state = (viewModel.uiState.value as UiState.Success).data
        assertEquals("detail-user", state.userId)
        assertTrue(state.errorMessage == null)
    }

    private class FakeGateway(
        private val result: StrengthForecastResult
    ) : ProgressDetailAnalyticsGateway {
        override suspend fun getStrengthForecast(userId: String, selectedExerciseId: String?, historyDays: Int, forecastDays: Int): LiftrixResult<StrengthForecastResult> =
            Result.success(result)

        override suspend fun getVolumeAnalysis(userId: String, timeRange: TimeRangeType, muscleGroupFilter: String?, grouping: VolumeGrouping): LiftrixResult<VolumeAnalysisData> = TODO()
        override suspend fun getOneRmProgression(userId: String, exerciseIds: List<String>?, timeRange: TimeRangeType, includeEstimated: Boolean): LiftrixResult<OneRmProgressionData> = TODO()
        override suspend fun getWorkoutFrequency(userId: String, timeRange: TimeRangeType): LiftrixResult<WorkoutFrequencyData> = TODO()
        override suspend fun getMuscleGroupAnalytics(userId: String, timeRange: TimeRangeType, muscleGroup: MuscleGroup?): LiftrixResult<MuscleGroupAnalyticsData> = TODO()
        override suspend fun getExerciseRanking(userId: String, timeRange: TimeRangeType, metric: com.example.liftrix.domain.model.analytics.RankingMetric): LiftrixResult<ExerciseRankingData> = TODO()
        override suspend fun exportOneRm(request: ExportOneRmDataRequest): LiftrixResult<File> = TODO()
        override suspend fun exportVolume(request: ExportVolumeDataRequest): LiftrixResult<File> = TODO()
        override suspend fun exportFrequency(request: ExportWorkoutFrequencyDataRequest): LiftrixResult<File> = TODO()
    }

    private class FakeAuthPort(
        private val userId: String = "user-1"
    ) : ProgressAuthPort {
        override suspend fun invoke(waitForAuth: Boolean): LiftrixResult<UserId> =
            Result.success(UserId(userId))
    }
}
