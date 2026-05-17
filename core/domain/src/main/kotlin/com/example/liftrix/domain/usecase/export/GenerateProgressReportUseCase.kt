package com.example.liftrix.domain.usecase.export

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.model.export.ProgressReportResult
import com.example.liftrix.domain.repository.SubscriptionRepository
import com.example.liftrix.domain.repository.export.ProgressReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

class GenerateProgressReportUseCase @Inject constructor(
    private val repository: ProgressReportRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    suspend operator fun invoke(
        userId: String,
        request: ProgressReportRequest
    ): LiftrixResult<ProgressReportResult> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext liftrixFailure(
                LiftrixError.AuthenticationError(errorMessage = "User not authenticated")
            )
        }

        val validation = validateRequest(request)
        if (validation.isNotEmpty()) {
            return@withContext liftrixFailure(
                LiftrixError.ValidationError(
                    field = "progress_report_request",
                    violations = validation,
                    errorMessage = validation.first()
                )
            )
        }

        val hasPremium = try {
            subscriptionRepository.hasActivePremiumSubscription(userId).first()
        } catch (_: Exception) {
            false
        }

        if (!hasPremium) {
            return@withContext liftrixFailure(
                LiftrixError.PermissionError(
                    errorMessage = "Progress report export requires Liftrix Premium.",
                    permission = "premium_progress_report"
                )
            )
        }

        repository.generateLocalReport(userId, request)
    }

    private fun validateRequest(request: ProgressReportRequest): List<String> {
        val today = LocalDate.now()
        return when (val range = request.dateRange) {
            ProgressReportDateRange.Last30Days,
            ProgressReportDateRange.Last6Months,
            ProgressReportDateRange.AllTime -> emptyList()
            is ProgressReportDateRange.Custom -> buildList {
                if (range.start.isAfter(range.end)) {
                    add("Start date must be before end date.")
                }
                if (range.end.isAfter(today)) {
                    add("End date cannot be in the future.")
                }
            }
        }
    }
}
