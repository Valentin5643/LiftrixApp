package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.analytics.CognitiveLoadMeasurement
import com.example.liftrix.analytics.TaskCompletionTracker
import com.example.liftrix.analytics.UxMetricsTracker
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.core.analytics.ArchitectureAnalytics
import com.example.liftrix.core.time.SystemTimeProvider
import com.example.liftrix.core.time.TimeProvider
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.remote.FirebaseDataSource
import com.example.liftrix.data.remote.FirebaseDataSourceImpl
import com.example.liftrix.data.remote.PRNotificationFirebaseService
import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.data.serialization.ExerciseDeserializer
import com.example.liftrix.data.serialization.ExerciseSetDeserializer
import com.example.liftrix.data.service.AnalyticsServiceImpl
import com.example.liftrix.data.service.NetworkConnectivityMonitorImpl
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import com.example.liftrix.domain.repository.MetDataRepository
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.progress.ProgressWidgetResolverPort
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import com.example.liftrix.feature.AnalyticsABTestManager
import com.example.liftrix.feature.AnalyticsFeatureFlags
import com.example.liftrix.monitoring.AnalyticsPerformanceTracker
import com.example.liftrix.monitoring.ArchitecturePerformanceMonitor
import com.example.liftrix.monitoring.ChartRenderingMonitor
import com.example.liftrix.monitoring.NavigationPerformanceTracker
import com.example.liftrix.service.AnalyticsEngine
import com.example.liftrix.service.PerformanceBenchmark
import com.example.liftrix.domain.service.analytics.AnalyticsWidgetManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Singleton

/**
 * FirebaseModule - Firebase & Analytics Infrastructure
 *
 * PURPOSE: All Firebase services, analytics, monitoring, network infrastructure, and sync
 *
 * CONSOLIDATES:
 * - NetworkModule (6 providers + 1 binding) - Firebase core services, Gson, Context
 * - AnalyticsModule (25 providers + 1 binding) - Firebase analytics/monitoring, UX metrics
 * - SyncModule (1 provider + 1 binding) - Firebase data source, JSON serialization
 *
 * TOTAL BINDINGS: ~34 (30 providers + 3 bindings + 1 Context)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseModule {

    // ========================================
    // NETWORK CONNECTIVITY
    // ========================================

    @Binds
    @Singleton
    abstract fun bindNetworkConnectivityMonitor(
        networkConnectivityMonitorImpl: NetworkConnectivityMonitorImpl
    ): NetworkConnectivityMonitor

    // ========================================
    // ANALYTICS SERVICE
    // ========================================

    @Binds
    @Singleton
    abstract fun bindAnalyticsService(
        analyticsServiceImpl: AnalyticsServiceImpl
    ): AnalyticsService

    // ========================================
    // FIREBASE SYNC
    // ========================================

    @Binds
    @Singleton
    abstract fun bindFirebaseDataSource(
        firebaseDataSourceImpl: FirebaseDataSourceImpl
    ): FirebaseDataSource

    companion object {

        // ========================================
        // FIREBASE CORE SERVICES
        // ========================================

        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth {
            return FirebaseAuth.getInstance()
        }

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance().apply {
                // SPEC-20241228: Room-First Architecture - Conditional Firestore Persistence
                validateFirestorePersistenceConfig()

                val persistenceEnabled = !OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE

                firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(persistenceEnabled)
                    .apply {
                        // Only set cache size if persistence is enabled
                        if (persistenceEnabled) {
                            setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        }
                    }
                    .build()

                // Log configuration
                if (OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE) {
                    Timber.i("✅ ROOM-FIRST MODE: Firestore offline persistence DISABLED (Room is authority)")
                    if (OfflineArchitectureFlags.VERBOSE_SYNC_LOGGING) {
                        Timber.d(OfflineArchitectureFlags.getConfigSummary())
                    }
                } else {
                    Timber.w("⚠️ LEGACY MODE: Firestore offline persistence ENABLED (dual authority)")
                }
            }
        }

        internal fun validateFirestorePersistenceConfig(
            roomFirstEnabled: Boolean = OfflineArchitectureFlags.ROOM_FIRST_ENABLED,
            disableFirestorePersistence: Boolean = OfflineArchitectureFlags.DISABLE_FIRESTORE_PERSISTENCE
        ) {
            if (roomFirstEnabled && !disableFirestorePersistence) {
                throw IllegalStateException(
                    "ARCHITECTURAL VIOLATION: Firestore persistence must be disabled when ROOM_FIRST_ENABLED=true"
                )
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseStorage(): FirebaseStorage {
            return FirebaseStorage.getInstance().apply {
                // Configure maximum upload/download timeout for profile images
                maxUploadRetryTimeMillis = 30000 // 30 seconds for uploads
                maxDownloadRetryTimeMillis = 15000 // 15 seconds for downloads
                maxOperationRetryTimeMillis = 45000 // 45 seconds total operation timeout
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseFunctions(): FirebaseFunctions {
            return FirebaseFunctions.getInstance()
        }

        @Provides
        @Singleton
        fun provideFirebaseMessaging(): FirebaseMessaging {
            return FirebaseMessaging.getInstance()
        }

        @Provides
        @Singleton
        fun providePRNotificationFirebaseService(
            firestore: FirebaseFirestore,
            auth: FirebaseAuth,
            json: Json
        ): PRNotificationFirebaseService {
            return PRNotificationFirebaseService(firestore, auth, json)
        }

        // ========================================
        // FIREBASE ANALYTICS & MONITORING
        // ========================================

        @Provides
        @Singleton
        fun provideFirebaseAnalytics(): FirebaseAnalytics {
            return Firebase.analytics.apply {
                setAnalyticsCollectionEnabled(true)
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
            return FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(true)
            }
        }

        @Provides
        @Singleton
        fun provideFirebasePerformance(): FirebasePerformance {
            return FirebasePerformance.getInstance().apply {
                isPerformanceCollectionEnabled = true
            }
        }

        @Provides
        @Singleton
        fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
            return Firebase.remoteConfig
        }

        @Provides
        @Singleton
        fun provideRemoteConfigManager(
            remoteConfig: FirebaseRemoteConfig
        ): RemoteConfigManager {
            return RemoteConfigManager(remoteConfig = remoteConfig)
        }

        // ========================================
        // PERFORMANCE MONITORING
        // ========================================

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
        fun provideAnalyticsPerformanceTracker(
            firebasePerformance: FirebasePerformance,
            analyticsService: AnalyticsService
        ): AnalyticsPerformanceTracker {
            return AnalyticsPerformanceTracker(
                firebasePerformance = firebasePerformance,
                analyticsService = analyticsService
            )
        }

        @Provides
        @Singleton
        fun provideChartRenderingMonitor(
            analyticsPerformanceTracker: AnalyticsPerformanceTracker
        ): ChartRenderingMonitor {
            return ChartRenderingMonitor(
                analyticsPerformanceTracker = analyticsPerformanceTracker
            )
        }

        @Provides
        @Singleton
        fun providePerformanceBenchmark(): PerformanceBenchmark {
            return PerformanceBenchmark()
        }

        // ========================================
        // ANALYTICS ENGINE & CALCULATIONS
        // ========================================

        @Provides
        @Singleton
        fun provideArchitectureAnalytics(
            analyticsService: AnalyticsService
        ): ArchitectureAnalytics {
            return ArchitectureAnalytics(
                analyticsService = analyticsService
            )
        }

        @Provides
        @Singleton
        fun provideCalorieCalculator(
            metDataRepository: MetDataRepository
        ): CalorieCalculator {
            return CalorieCalculator(metDataRepository)
        }

        @Provides
        @Singleton
        fun provideAnalyticsEngine(
            workoutDao: WorkoutDao,
            calorieCalculator: CalorieCalculator,
            progressStatsRepository: ProgressStatsRepository,
            performanceBenchmark: PerformanceBenchmark
        ): AnalyticsEngine {
            return AnalyticsEngine(
                workoutDao = workoutDao,
                calorieCalculator = calorieCalculator,
                progressStatsRepository = progressStatsRepository,
                performanceBenchmark = performanceBenchmark
            )
        }

        @Provides
        @Singleton
        fun provideAnalyticsWidgetManager(
            widgetResolver: ProgressWidgetResolverPort
        ): AnalyticsWidgetManager {
            return AnalyticsWidgetManager(widgetResolver)
        }

        // ========================================
        // FEATURE FLAGS & A/B TESTING
        // ========================================

        @Provides
        @Singleton
        fun provideAnalyticsFeatureFlags(
            remoteConfig: FirebaseRemoteConfig
        ): AnalyticsFeatureFlags {
            return AnalyticsFeatureFlags(
                remoteConfig = remoteConfig
            )
        }

        @Provides
        @Singleton
        fun provideAnalyticsABTestManager(
            remoteConfig: FirebaseRemoteConfig,
            firebaseAnalytics: FirebaseAnalytics,
            featureFlags: AnalyticsFeatureFlags
        ): AnalyticsABTestManager {
            return AnalyticsABTestManager(
                remoteConfig = remoteConfig,
                firebaseAnalytics = firebaseAnalytics,
                featureFlags = featureFlags
            )
        }

        // ========================================
        // UX METRICS & PRD TRACKING
        // ========================================

        @Provides
        @Singleton
        fun provideTimeProvider(): TimeProvider {
            return SystemTimeProvider()
        }

        @Provides
        @Singleton
        fun provideUxMetricsTracker(
            analyticsService: AnalyticsService,
            timeProvider: TimeProvider,
            @ApplicationScope applicationScope: CoroutineScope
        ): UxMetricsTracker {
            return UxMetricsTracker(analyticsService, timeProvider, applicationScope)
        }

        @Provides
        @Singleton
        fun provideTaskCompletionTracker(
            analyticsService: AnalyticsService,
            timeProvider: TimeProvider,
            @ApplicationScope applicationScope: CoroutineScope
        ): TaskCompletionTracker {
            return TaskCompletionTracker(analyticsService, timeProvider, applicationScope)
        }

        @Provides
        @Singleton
        fun provideCognitiveLoadMeasurement(
            analyticsService: AnalyticsService,
            @ApplicationScope applicationScope: CoroutineScope
        ): CognitiveLoadMeasurement {
            return CognitiveLoadMeasurement(analyticsService, applicationScope)
        }

        // ========================================
        // SERIALIZATION & NETWORK UTILITIES
        // ========================================

        @Provides
        @Singleton
        fun provideGson(): Gson {
            return GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .setLenient() // Add lenient parsing for cache recovery and backward compatibility
                .registerTypeAdapter(ExerciseSet::class.java, ExerciseSetDeserializer())
                .registerTypeAdapter(Exercise::class.java, ExerciseDeserializer())
                .create()
        }

        @Provides
        @Singleton
        fun provideJson(): Json {
            return Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
                prettyPrint = false
                coerceInputValues = true
            }
        }

        @Provides
        @Singleton
        fun provideContext(@ApplicationContext context: Context): Context {
            return context
        }
    }
}
