package com.example.liftrix.demo

import com.example.liftrix.domain.model.export.ProgressReportData
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.repository.export.DemoProgressReportDataProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoProgressReportDataProviderImpl @Inject constructor(
    private val demoModeController: DemoModeController,
    private val demoModeStore: DemoModeStore,
    private val timelineGenerator: DemoTimelineGenerator,
    private val demoProgressDataFactory: DemoProgressDataFactory
) : DemoProgressReportDataProvider {
    override suspend fun buildDemoReportDataIfActive(request: ProgressReportRequest): ProgressReportData? {
        val state = demoModeController.state.value.takeIf { it.isActive } ?: demoModeStore.state.first()
        if (!state.isActive) return null

        val sessionSeed = requireNotNull(state.sessionSeed)
        val timeline = timelineGenerator.generate(sessionSeed)
        return demoProgressDataFactory.progressReportData(timeline, request)
    }
}
