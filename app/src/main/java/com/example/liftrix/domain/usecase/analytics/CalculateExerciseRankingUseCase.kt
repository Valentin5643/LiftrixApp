package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.workout.WorkoutAnalyticsDataRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class ExerciseRankingRequest(
    val metric: RankingMetric = RankingMetric.PERFORMANCE_SCORE,
    val timeRange: TimeRangeType = TimeRangeType.MONTH,
    val limit: Int = 10
)

data class ExerciseRankingResult(
    val rankings: List<ExercisePerformanceData>
)

class CalculateExerciseRankingUseCase @Inject constructor(
    private val workoutAnalyticsDataRepository: WorkoutAnalyticsDataRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    suspend operator fun invoke(request: ExerciseRankingRequest): LiftrixResult<ExerciseRankingResult> {
        val userId = getCurrentUserIdUseCase()
            ?: return LiftrixResult.failure(IllegalStateException("User not authenticated"))

        return workoutAnalyticsDataRepository.getExercisePerformanceData(
            userId = userId,
            startDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(6, DateTimeUnit.MONTH),
            endDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
        ).map { data -> ExerciseRankingResult(data.take(request.limit)) }
    }
}
