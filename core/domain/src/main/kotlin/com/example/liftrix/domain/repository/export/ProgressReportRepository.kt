package com.example.liftrix.domain.repository.export

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.model.export.ProgressReportResult

interface ProgressReportRepository {
    suspend fun generateLocalReport(
        userId: String,
        request: ProgressReportRequest
    ): LiftrixResult<ProgressReportResult>
}
