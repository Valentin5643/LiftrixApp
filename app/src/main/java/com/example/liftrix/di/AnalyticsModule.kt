package com.example.liftrix.di

import com.example.liftrix.analytics.UxMetricsTracker
import com.example.liftrix.analytics.TaskCompletionTracker
import com.example.liftrix.analytics.CognitiveLoadMeasurement
import com.example.liftrix.core.time.TimeProvider
import com.example.liftrix.core.time.SystemTimeProvider
import com.example.liftrix.core.analytics.ArchitectureAnalytics
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.service.AnalyticsServiceImpl
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.monitoring.ArchitecturePerformanceMonitor
import com.example.liftrix.monitoring.NavigationPerformanceTracker
import com.example.liftrix.service.AnalyticsEngine
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsService(
        analyticsServiceImpl: AnalyticsServiceImpl
    ): AnalyticsService

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAnalytics(): FirebaseAnalytics {
            return Firebase.analytics.apply {
                // Enable analytics collection
                setAnalyticsCollectionEnabled(true)
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseMessaging(): com.google.firebase.messaging.FirebaseMessaging {
            return com.google.firebase.messaging.FirebaseMessaging.getInstance()
        }

        @Provides
        @Singleton
        fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
            return FirebaseCrashlytics.getInstance().apply {
                // Enable crashlytics collection
                setCrashlyticsCollectionEnabled(true)
            }
        }

        @Provides
        @Singleton
        fun provideFirebasePerformance(): FirebasePerformance {
            return FirebasePerformance.getInstance().apply {
                // Enable performance monitoring collection
                isPerformanceCollectionEnabled = true
            }
        }

        @Provides
        @Singleton
        fun provideArchitecturePerformanceMonitor(
            firebasePerformance: FirebasePerformance,
            analyticsService: AnalyticsService
        ): ArchitecturePerformanceMonitor {
            return ArchitecturePerformanceMonitor(
                firebasePerformance = firebasePerformance,
                analyticsService = analyticsService
            )
        }

        @Provides
        @Singleton
        fun provideNavigationPerformanceTracker(
            architecturePerformanceMonitor: ArchitecturePerformanceMonitor
        ): NavigationPerformanceTracker {
            return NavigationPerformanceTracker(
                architecturePerformanceMonitor = architecturePerformanceMonitor
            )
        }

        @Provides
        @Singleton
        fun provideArchitectureAnalytics(
            analyticsService: AnalyticsService
        ): ArchitectureAnalytics {
            return ArchitectureAnalytics(
                analyticsService = analyticsService
            )
        }

        /**
         * Provides CalorieCalculator with proper dependency injection
         * 
         * @return Configured CalorieCalculator instance
         */
        @Provides
        @Singleton
        fun provideCalorieCalculator(
            metDataRepository: com.example.liftrix.domain.repository.MetDataRepository
        ): CalorieCalculator {
            return CalorieCalculator(metDataRepository)
        }

        /**
         * Provides PerformanceBenchmark for analytics performance tracking
         * 
         * @return Configured PerformanceBenchmark instance
         */
        @Provides
        @Singleton
        fun providePerformanceBenchmark(): com.example.liftrix.service.PerformanceBenchmark {
            return com.example.liftrix.service.PerformanceBenchmark()
        }

        /**
         * Provides AnalyticsEngine with proper dependency injection
         * 
         * @param workoutDao The workout DAO for data access
         * @param calorieCalculator The calorie calculator for MET-based calculations
         * @param progressStatsRepository The progress stats repository for analytics data
         * @param performanceBenchmark The performance benchmark for tracking
         * @return Configured AnalyticsEngine instance
         */
        @Provides
        @Singleton
        fun provideAnalyticsEngine(
            workoutDao: WorkoutDao,
            calorieCalculator: CalorieCalculator,
            progressStatsRepository: com.example.liftrix.domain.repository.ProgressStatsRepository,
            performanceBenchmark: com.example.liftrix.service.PerformanceBenchmark
        ): AnalyticsEngine {
            return AnalyticsEngine(
                workoutDao = workoutDao,
                calorieCalculator = calorieCalculator,
                progressStatsRepository = progressStatsRepository,
                performanceBenchmark = performanceBenchmark
            )
        }

        /**
         * Provides AnalyticsPerformanceTracker with proper dependency injection
         * 
         * @param firebasePerformance Firebase Performance instance for traces
         * @param analyticsService Analytics service for custom event tracking
         * @return Configured AnalyticsPerformanceTracker instance
         */
        @Provides
        @Singleton
        fun provideAnalyticsPerformanceTracker(
            firebasePerformance: FirebasePerformance,
            analyticsService: AnalyticsService
        ): com.example.liftrix.monitoring.AnalyticsPerformanceTracker {
            return com.example.liftrix.monitoring.AnalyticsPerformanceTracker(
                firebasePerformance = firebasePerformance,
                analyticsService = analyticsService
            )
        }

        /**
         * Provides ChartRenderingMonitor with proper dependency injection
         * 
         * @param analyticsPerformanceTracker Performance tracker for chart rendering metrics
         * @return Configured ChartRenderingMonitor instance
         */
        @Provides
        @Singleton
        fun provideChartRenderingMonitor(
            analyticsPerformanceTracker: com.example.liftrix.monitoring.AnalyticsPerformanceTracker
        ): com.example.liftrix.monitoring.ChartRenderingMonitor {
            return com.example.liftrix.monitoring.ChartRenderingMonitor(
                analyticsPerformanceTracker = analyticsPerformanceTracker
            )
        }

        /**
         * Provides Firebase Remote Config for feature flags
         * 
         * @return Configured FirebaseRemoteConfig instance
         */
        @Provides
        @Singleton
        fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
            return Firebase.remoteConfig
        }

        /**
         * Provides AnalyticsFeatureFlags with proper dependency injection
         * 
         * @param remoteConfig Firebase Remote Config instance
         * @return Configured AnalyticsFeatureFlags instance
         */
        @Provides
        @Singleton
        fun provideAnalyticsFeatureFlags(
            remoteConfig: FirebaseRemoteConfig
        ): com.example.liftrix.feature.AnalyticsFeatureFlags {
            return com.example.liftrix.feature.AnalyticsFeatureFlags(
                remoteConfig = remoteConfig
            )
        }

        /**
         * Provides AnalyticsABTestManager with proper dependency injection
         * 
         * @param remoteConfig Firebase Remote Config instance
         * @param firebaseAnalytics Firebase Analytics instance
         * @param featureFlags Analytics feature flags instance
         * @return Configured AnalyticsABTestManager instance
         */
        @Provides
        @Singleton
        fun provideAnalyticsABTestManager(
            remoteConfig: FirebaseRemoteConfig,
            firebaseAnalytics: FirebaseAnalytics,
            featureFlags: com.example.liftrix.feature.AnalyticsFeatureFlags
        ): com.example.liftrix.feature.AnalyticsABTestManager {
            return com.example.liftrix.feature.AnalyticsABTestManager(
                remoteConfig = remoteConfig,
                firebaseAnalytics = firebaseAnalytics,
                featureFlags = featureFlags
            )
        }


        /**
         * Provides AnalyticsWidgetManager with proper dependency injection
         * 
         * @param widgetResolver Widget resolution service for dynamic widget selection
         * @return Configured AnalyticsWidgetManager instance
         */
        @Provides
        @Singleton
        fun provideAnalyticsWidgetManager(
            widgetResolver: com.example.liftrix.service.WidgetResolver
        ): com.example.liftrix.ui.progress.components.AnalyticsWidgetManager {
            return com.example.liftrix.ui.progress.components.AnalyticsWidgetManager(widgetResolver)
        }
        
        // UX Metrics providers for PRD success tracking
        
        /**
         * Provides TimeProvider for testable time operations
         * 
         * @return SystemTimeProvider implementation
         */
        @Provides
        @Singleton
        fun provideTimeProvider(): TimeProvider {
            return SystemTimeProvider()
        }
        
        /**
         * Provides UxMetricsTracker for measuring PRD success criteria
         * 
         * @param analyticsService Analytics service for event logging
         * @param timeProvider Time provider for workflow timing
         * @return Configured UxMetricsTracker instance
         */
        @Provides
        @Singleton
        fun provideUxMetricsTracker(
            analyticsService: AnalyticsService,
            timeProvider: TimeProvider
        ): UxMetricsTracker {
            return UxMetricsTracker(analyticsService, timeProvider)
        }
        
        /**
         * Provides TaskCompletionTracker for measuring task completion rates
         * 
         * @param analyticsService Analytics service for event logging
         * @param timeProvider Time provider for task timing
         * @return Configured TaskCompletionTracker instance
         */
        @Provides
        @Singleton
        fun provideTaskCompletionTracker(
            analyticsService: AnalyticsService,
            timeProvider: TimeProvider
        ): TaskCompletionTracker {
            return TaskCompletionTracker(analyticsService, timeProvider)
        }
        
        /**
         * Provides CognitiveLoadMeasurement for calculating cognitive load scores
         * 
         * @param analyticsService Analytics service for event logging
         * @return Configured CognitiveLoadMeasurement instance
         */
        @Provides
        @Singleton
        fun provideCognitiveLoadMeasurement(
            analyticsService: AnalyticsService
        ): CognitiveLoadMeasurement {
            return CognitiveLoadMeasurement(analyticsService)
        }
    }
} 