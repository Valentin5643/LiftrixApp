package com.example.liftrix.domain.repository.export

import com.example.liftrix.domain.model.export.ProgressReportData
import com.example.liftrix.domain.model.export.ProgressReportRequest

interface DemoProgressReportDataProvider {
    suspend fun buildDemoReportDataIfActive(request: ProgressReportRequest): ProgressReportData?
}
