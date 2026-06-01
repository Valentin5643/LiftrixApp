package com.example.liftrix.di

import com.example.liftrix.analytics.CognitiveLoadMeasurement
import com.example.liftrix.analytics.TaskCompletionTracker
import com.example.liftrix.analytics.UxMetricsTracker
import com.example.liftrix.core.analytics.ArchitectureAnalytics
import com.example.liftrix.core.time.SystemTimeProvider
import com.example.liftrix.core.time.TimeProvider
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.service.AnalyticsServiceImpl
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.progress.ProgressWidgetResolverPort
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.service.analytics.AnalyticsWidgetManager
import com.example.liftrix.monitoring.AnalyticsPerformanceTracker
import com.example.liftrix.monitoring.ArchitecturePerformanceMonitor
import com.example.liftrix.monitoring.ChartRenderingMonitor
import com.example.liftrix.monitoring.NavigationPerformanceTracker
import com.example.liftrix.service.AnalyticsEngine
import com.example.liftrix.service.PerformanceBenchmark
import com.google.firebase.perf.FirebasePerformance
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindAnalyticsService(impl: AnalyticsServiceImpl): AnalyticsService

    companion object {
        @Provides
        @Singleton
        fun provideArchitecturePerformanceMonitor(
            firebasePerformance: FirebasePerformance,
            analyticsService: AnalyticsService
        ): ArchitecturePerformanceMonitor = ArchitecturePerformanceMonitor(firebasePerformance, analyticsService)

        @Provides
        @Singleton
        fun provideNavigationPerformanceTracker(
            architecturePerformanceMonitor: ArchitecturePerformanceMonitor
        ): NavigationPerformanceTracker = NavigationPerformanceTracker(architecturePerformanceMonitor)

        @Provides
        @Singleton
        fun provideAnalyticsPerformanceTracker(
            firebasePerformance: FirebasePerformance,
            analyticsService: AnalyticsService
        ): AnalyticsPerformanceTracker = AnalyticsPerformanceTracker(firebasePerformance, analyticsService)

        @Provides
        @Singleton
        fun provideChartRenderingMonitor(
            analyticsPerformanceTracker: AnalyticsPerformanceTracker
        ): ChartRenderingMonitor = ChartRenderingMonitor(analyticsPerformanceTracker)

        @Provides
        @Singleton
        fun providePerformanceBenchmark(): PerformanceBenchmark = PerformanceBenchmark()

        @Provides
        @Singleton
        fun provideArchitectureAnalytics(analyticsService: AnalyticsService): ArchitectureAnalytics =
            ArchitectureAnalytics(analyticsService)

        @Provides
        @Singleton
        fun provideCalorieCalculator(metDataRepository: MetDataRepository): CalorieCalculator =
            CalorieCalculator(metDataRepository)

        @Provides
        @Singleton
        fun provideAnalyticsEngine(
            workoutDao: WorkoutDao,
            calorieCalculator: CalorieCalculator,
            progressStatsRepository: ProgressStatsRepository,
            performanceBenchmark: PerformanceBenchmark
        ): AnalyticsEngine = AnalyticsEngine(
            workoutDao = workoutDao,
            calorieCalculator = calorieCalculator,
            progressStatsRepository = progressStatsRepository,
            performanceBenchmark = performanceBenchmark
        )

        @Provides
        @Singleton
        fun provideAnalyticsWidgetManager(
            widgetResolver: ProgressWidgetResolverPort
        ): AnalyticsWidgetManager = AnalyticsWidgetManager(widgetResolver)

        @Provides
        @Singleton
        fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

        @Provides
        @Singleton
        fun provideUxMetricsTracker(
            analyticsService: AnalyticsService,
            timeProvider: TimeProvider,
            @ApplicationScope applicationScope: CoroutineScope
        ): UxMetricsTracker = UxMetricsTracker(analyticsService, timeProvider, applicationScope)

        @Provides
        @Singleton
        fun provideTaskCompletionTracker(
            analyticsService: AnalyticsService,
            timeProvider: TimeProvider,
            @ApplicationScope applicationScope: CoroutineScope
        ): TaskCompletionTracker = TaskCompletionTracker(analyticsService, timeProvider, applicationScope)

        @Provides
        @Singleton
        fun provideCognitiveLoadMeasurement(
            analyticsService: AnalyticsService,
            @ApplicationScope applicationScope: CoroutineScope
        ): CognitiveLoadMeasurement = CognitiveLoadMeasurement(analyticsService, applicationScope)
    }
}
