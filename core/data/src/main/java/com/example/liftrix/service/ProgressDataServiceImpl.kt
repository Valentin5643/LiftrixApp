package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.usecase.analytics.GenerateVolumeCalendarUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressDataServiceImpl @Inject constructor(
    private val progressStatsRepository: ProgressStatsRepository,
    private val generateVolumeCalendarUseCase: GenerateVolumeCalendarUseCase,
    @com.example.liftrix.data.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProgressDataService {

    companion object {
        private const val TAG = "ProgressDataService"
    }

    override suspend fun getVolumeData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<VolumeDataPoint>> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get volume data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve volume data: ${throwable.message}",
                    operation = "getVolumeData",
                    analyticsContext = mapOf("userId" to userId, "timeRange" to timeRange.toString())
                )
            }
        ) {
            val (startDate, endDate) = timeRange.toLocalDateRange()
            Timber.d("$TAG: Fetching volume data from Room: startDate=$startDate, endDate=$endDate")
            kotlinx.coroutines.withTimeout(8000) {
                progressStatsRepository.getWorkoutVolumeData(userId, startDate, endDate).first()
            }
        }
    }

    override suspend fun getDurationData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<DurationDataPoint>> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get duration data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve duration data: ${throwable.message}",
                    operation = "getDurationData",
                    analyticsContext = mapOf("userId" to userId, "timeRange" to timeRange.toString())
                )
            }
        ) {
            val (startDate, endDate) = timeRange.toLocalDateRange()
            kotlinx.coroutines.withTimeout(8000) {
                progressStatsRepository.getWorkoutDurationData(userId, startDate, endDate).first()
            }
        }
    }

    override suspend fun getFrequencyData(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<List<FrequencyDataPoint>> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get frequency data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve frequency data: ${throwable.message}",
                    operation = "getFrequencyData",
                    analyticsContext = mapOf("userId" to userId, "timeRange" to timeRange.toString())
                )
            }
        ) {
            val (startDate, endDate) = timeRange.toLocalDateRange()
            kotlinx.coroutines.withTimeout(8000) {
                progressStatsRepository.getWorkoutFrequencyData(userId, startDate, endDate).first()
            }
        }
    }

    override suspend fun getProgressSummary(
        userId: String,
        timeRange: TimeRange
    ): LiftrixResult<ProgressSummary> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get progress summary for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Database query failed: ${throwable.message ?: "Unknown database error"}",
                    operation = "getProgressSummary",
                    analyticsContext = mapOf("userId" to userId, "timeRange" to timeRange.toString())
                )
            }
        ) {
            val (startDate, endDate) = timeRange.toLocalDateRange()
            Timber.d("$TAG: Fetching progress summary from Room")
            kotlinx.coroutines.withTimeout(15000) {
                progressStatsRepository.getProgressSummary(userId, startDate, endDate).first()
            }
        }
    }

    override suspend fun getVolumeCalendarData(
        userId: String
    ): LiftrixResult<VolumeCalendarData> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to get volume calendar data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve volume calendar data: ${throwable.message}",
                    operation = "getVolumeCalendarData",
                    analyticsContext = mapOf("userId" to userId)
                )
            }
        ) {
            val result = generateVolumeCalendarUseCase.generateCurrentMonth(userId)
            result.getOrElse {
                throw (it as? LiftrixError ?: LiftrixError.UnknownError("Failed to generate volume calendar data"))
            }
        }
    }

    override suspend fun refreshAllData(userId: String): LiftrixResult<Unit> = withContext(ioDispatcher) {
        liftrixCatching(
            errorMapper = { throwable ->
                Timber.e(throwable, "$TAG: Failed to refresh data for user: $userId")
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to refresh progress data",
                    operation = "refreshAllData",
                    analyticsContext = mapOf("userId" to userId)
                )
            }
        ) {
            Timber.d("$TAG: Refresh requested for user: $userId; Room Flow observers own freshness")
            Unit
        }
    }

    private fun java.util.Date.toKotlinLocalDate(): LocalDate =
        kotlinx.datetime.Instant.fromEpochMilliseconds(time)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    private fun TimeRange.toLocalDateRange(): Pair<LocalDate, LocalDate> {
        val startLocalDate = startDate.toKotlinLocalDate()
        val endLocalDate = endDate.toKotlinLocalDate()
        return if (startLocalDate <= endLocalDate) {
            startLocalDate to endLocalDate
        } else {
            endLocalDate to startLocalDate
        }
    }
}
