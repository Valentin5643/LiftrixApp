package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * DomainModule - Domain Layer Dependency Injection
 *
 * PURPOSE: All business logic (use cases, services, domain-level operations)
 *
 * CONSOLIDATES:
 * - ServiceModule (38+ bindings) - Core services and business logic
 * - UseCaseModule (3 bindings) - Use cases with complex initialization
 * - ProfileModule (3 bindings) - Profile-specific use cases
 * - UnifiedWorkoutSessionModule (2 bindings) - Session management
 * - ExportModule (5 bindings) - Export services and parsers
 *
 * TOTAL BINDINGS: ~50
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    // ========================================
    // SERVICE LAYER SECTION
    // ========================================

    // --- Core Services ---

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindWeightMemoryService(
        impl: com.example.liftrix.data.service.WeightMemoryServiceImpl
    ): com.example.liftrix.domain.service.WeightMemoryService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindBackupService(
        impl: com.example.liftrix.data.service.BackupServiceImpl
    ): com.example.liftrix.domain.repository.backup.BackupService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindSyncService(
        impl: com.example.liftrix.data.service.SyncServiceImpl
    ): com.example.liftrix.domain.repository.sync.SyncService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindPRDetectionService(
        impl: com.example.liftrix.service.PRDetectionServiceImpl
    ): com.example.liftrix.domain.service.PRDetectionService

    // --- Analytics Services ---

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindAnalyticsCalculationService(
        impl: com.example.liftrix.domain.service.AnalyticsCalculationServiceImpl
    ): com.example.liftrix.domain.service.AnalyticsCalculationService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindWidgetOperationsService(
        impl: com.example.liftrix.domain.service.WidgetOperationsServiceImpl
    ): com.example.liftrix.domain.service.WidgetOperationsService

    // --- Support Services ---

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindHelpCenterService(
        impl: com.example.liftrix.data.service.HelpCenterServiceImpl
    ): com.example.liftrix.domain.service.HelpCenterService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindLegalDocumentService(
        impl: com.example.liftrix.data.service.LegalDocumentServiceImpl
    ): com.example.liftrix.domain.service.LegalDocumentService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindSupportService(
        impl: com.example.liftrix.data.service.SupportServiceImpl
    ): com.example.liftrix.domain.service.SupportService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindAppInfoService(
        impl: com.example.liftrix.data.service.AppInfoServiceImpl
    ): com.example.liftrix.domain.service.AppInfoService

    // --- Export/Import Services ---

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindExerciseMappingService(
        impl: com.example.liftrix.domain.service.ExerciseMappingServiceImpl
    ): com.example.liftrix.domain.service.ExerciseMappingService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindPdfGenerationService(
        impl: com.example.liftrix.data.service.PdfGenerationServiceImpl
    ): com.example.liftrix.domain.service.PdfGenerationService

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindDownloadManagerService(
        impl: com.example.liftrix.data.service.DownloadManagerServiceImpl
    ): com.example.liftrix.domain.service.DownloadManagerService

    // ========================================
    // COMPANION OBJECT (All @Provides methods)
    // ========================================
    companion object {

        // --- Analytics Services (Complex Init) ---

        @dagger.Provides
        @javax.inject.Singleton
        fun provideProgressDataService(
            progressStatsRepository: com.example.liftrix.domain.repository.ProgressStatsRepository,
            cacheManager: com.example.liftrix.core.cache.EnhancedCacheManager,
            generateVolumeCalendarUseCase: com.example.liftrix.domain.usecase.analytics.GenerateVolumeCalendarUseCase,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.ProgressDataService {
            return com.example.liftrix.service.ProgressDataServiceImpl(
                progressStatsRepository = progressStatsRepository,
                cacheManager = cacheManager,
                generateVolumeCalendarUseCase = generateVolumeCalendarUseCase,
                ioDispatcher = ioDispatcher
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideAnalyticsService(
            widgetManager: com.example.liftrix.ui.progress.components.AnalyticsWidgetManager,
            preferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
            analyticsEngine: com.example.liftrix.service.AnalyticsEngine,
            cacheManager: com.example.liftrix.core.cache.CacheManager,
            widgetCacheManager: com.example.liftrix.service.cache.WidgetCacheManager,
            realtimeSyncManager: com.example.liftrix.service.sync.RealtimeSyncManager,
            getWidgetDataUseCase: com.example.liftrix.domain.usecase.analytics.GetWidgetDataUseCase,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.AnalyticsService {
            return com.example.liftrix.service.AnalyticsServiceImpl(
                widgetManager = widgetManager,
                preferencesRepository = preferencesRepository,
                analyticsEngine = analyticsEngine,
                cacheManager = cacheManager,
                widgetCacheManager = widgetCacheManager,
                realtimeSyncManager = realtimeSyncManager,
                getWidgetDataUseCase = getWidgetDataUseCase,
                ioDispatcher = ioDispatcher
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideCalorieService(
            calorieCalculator: com.example.liftrix.domain.model.analytics.CalorieCalculator,
            metDataRepository: com.example.liftrix.domain.repository.MetDataRepository,
            workoutRepository: com.example.liftrix.domain.repository.workout.WorkoutRepository,
            userRepository: com.example.liftrix.domain.repository.UserRepository,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.CalorieService {
            return com.example.liftrix.service.CalorieServiceImpl(
                calorieCalculator = calorieCalculator,
                metDataRepository = metDataRepository,
                workoutRepository = workoutRepository,
                userRepository = userRepository,
                dispatcher = ioDispatcher
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun providePreferencesService(
            preferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
            authRepository: com.example.liftrix.domain.repository.AuthRepository,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.PreferencesService {
            return com.example.liftrix.service.PreferencesServiceImpl(
                preferencesRepository = preferencesRepository,
                authRepository = authRepository,
                dispatcher = ioDispatcher
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideFeatureFlagService(
            firebaseRemoteConfig: com.google.firebase.remoteconfig.FirebaseRemoteConfig,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.FeatureFlagService {
            return com.example.liftrix.service.FeatureFlagServiceImpl(
                firebaseRemoteConfig = firebaseRemoteConfig,
                dispatcher = ioDispatcher
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideWeightUnitManager(
            settingsRepository: com.example.liftrix.domain.repository.SettingsRepository,
            authQueryUseCase: com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
        ): com.example.liftrix.domain.service.WeightUnitManager {
            return com.example.liftrix.domain.service.WeightUnitManager(
                settingsRepository = settingsRepository,
                authQueryUseCase = authQueryUseCase
            )
        }

        // --- Settings Services (Triple-store: DataStore + Room + Firebase) ---

        @dagger.Provides
        @javax.inject.Singleton
        fun provideSettingsPersistenceManager(
            dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
            settingsDao: com.example.liftrix.data.local.dao.SettingsDao,
            firestore: com.google.firebase.firestore.FirebaseFirestore,
            auditDao: com.example.liftrix.data.local.dao.SettingsAuditDao
        ): com.example.liftrix.domain.service.SettingsPersistenceManager {
            return com.example.liftrix.domain.service.SettingsPersistenceManager(
                dataStore = dataStore,
                settingsDao = settingsDao,
                firestore = firestore,
                auditDao = auditDao
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideSettingsValidator(): com.example.liftrix.domain.service.SettingsValidator {
            return com.example.liftrix.domain.service.SettingsValidator()
        }

        // --- Import Format Detection & Parsing ---

        @dagger.Provides
        @javax.inject.Singleton
        fun provideFormatDetector(): com.example.liftrix.domain.service.parser.FormatDetector {
            return com.example.liftrix.domain.service.parser.FormatDetectorImpl()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideJsonParser(): com.example.liftrix.domain.service.parser.JsonParser {
            return com.example.liftrix.domain.service.parser.JsonParser()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideCsvParser(): com.example.liftrix.domain.service.parser.CsvParser {
            return com.example.liftrix.domain.service.parser.CsvParser()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideTcxParser(): com.example.liftrix.domain.service.parser.TcxParser {
            return com.example.liftrix.domain.service.parser.TcxParser()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideGpxParser(): com.example.liftrix.domain.service.parser.GpxParser {
            return com.example.liftrix.domain.service.parser.GpxParser()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideFitParser(): com.example.liftrix.domain.service.parser.FitParser {
            return com.example.liftrix.domain.service.parser.FitParser()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideWorkoutParserFactory(
            jsonParser: com.example.liftrix.domain.service.parser.JsonParser,
            csvParser: com.example.liftrix.domain.service.parser.CsvParser,
            tcxParser: com.example.liftrix.domain.service.parser.TcxParser,
            gpxParser: com.example.liftrix.domain.service.parser.GpxParser,
            fitParser: com.example.liftrix.domain.service.parser.FitParser
        ): com.example.liftrix.domain.service.parser.WorkoutParserFactory {
            return com.example.liftrix.domain.service.parser.WorkoutParserFactory(
                jsonParser = jsonParser,
                csvParser = csvParser,
                tcxParser = tcxParser,
                gpxParser = gpxParser,
                fitParser = fitParser
            )
        }

        // ========================================
        // USE CASE SECTION
        // ========================================
        // Note: Most use cases (consolidated 25 from 82 legacy) use @Inject constructor
        // Only provide use cases with complex initialization here

        @dagger.Provides
        @javax.inject.Singleton
        fun provideInitializeUserThemeUseCase(
            settingsRepository: com.example.liftrix.domain.repository.SettingsRepository,
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
        ): com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase {
            return com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase(
                settingsRepository = settingsRepository,
                context = context
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideSettingsCommandUseCase(
            settingsRepository: com.example.liftrix.domain.repository.SettingsRepository,
            preferencesService: com.example.liftrix.service.PreferencesService
        ): com.example.liftrix.domain.usecase.settings.SettingsCommandUseCase {
            return com.example.liftrix.domain.usecase.settings.SettingsCommandUseCase(
                settingsRepository = settingsRepository,
                preferencesService = preferencesService
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideEvaluateFeatureFlagUseCase(
            featureFlagService: com.example.liftrix.service.FeatureFlagService
        ): com.example.liftrix.domain.usecase.settings.EvaluateFeatureFlagUseCase {
            return com.example.liftrix.domain.usecase.settings.EvaluateFeatureFlagUseCase(
                featureFlagService = featureFlagService
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideValidateProfileInputUseCase(): com.example.liftrix.domain.usecase.ValidateProfileInputUseCase {
            return com.example.liftrix.domain.usecase.ValidateProfileInputUseCase()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideProfileImageOperationsUseCase(
            profileImageRepository: com.example.liftrix.domain.repository.ProfileImageRepository,
            socialProfileRepository: com.example.liftrix.domain.repository.social.SocialProfileRepository
        ): com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase {
            return com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase(
                profileImageRepository = profileImageRepository,
                socialProfileRepository = socialProfileRepository
            )
        }

        @dagger.Provides
        fun provideStartWorkoutSessionUseCase(
            sessionManager: com.example.liftrix.service.UnifiedWorkoutSessionManager,
            workoutTemplateRepository: com.example.liftrix.domain.repository.WorkoutTemplateRepository
        ): com.example.liftrix.domain.usecase.session.StartWorkoutSessionUseCase {
            return com.example.liftrix.domain.usecase.session.StartWorkoutSessionUseCase(
                sessionManager = sessionManager,
                workoutTemplateRepository = workoutTemplateRepository
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideExportWorkoutsUseCase(
            workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
            dataExportDao: com.example.liftrix.data.local.dao.DataExportDao
        ): com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCase {
            return com.example.liftrix.domain.usecase.export.ExportWorkoutsUseCase(
                workoutDao = workoutDao,
                dataExportDao = dataExportDao
            )
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideDataImportUseCase(
            formatDetector: com.example.liftrix.domain.service.parser.FormatDetector,
            parserFactory: com.example.liftrix.domain.service.parser.WorkoutParserFactory,
            exerciseMappingService: com.example.liftrix.domain.service.ExerciseMappingService,
            workoutDao: com.example.liftrix.data.local.dao.WorkoutDao,
            dataImportDao: com.example.liftrix.data.local.dao.DataImportDao
        ): com.example.liftrix.domain.usecase.data_import.DataImportUseCase {
            return com.example.liftrix.domain.usecase.data_import.DataImportUseCase(
                formatDetector = formatDetector,
                parserFactory = parserFactory,
                exerciseMappingService = exerciseMappingService,
                workoutDao = workoutDao,
                dataImportDao = dataImportDao
            )
        }

        // --- Export Services ---

        @dagger.Provides
        @javax.inject.Singleton
        fun providePdfExporter(): com.example.liftrix.data.export.PdfExporter {
            return com.example.liftrix.data.export.PdfExporter()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideCsvExporter(): com.example.liftrix.data.export.CsvExporter {
            return com.example.liftrix.data.export.CsvExporter()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideAnalyticsExporter(
            pdfExporter: com.example.liftrix.data.export.PdfExporter,
            csvExporter: com.example.liftrix.data.export.CsvExporter
        ): com.example.liftrix.data.export.AnalyticsExporter {
            return com.example.liftrix.data.export.AnalyticsExporter(
                pdfExporter = pdfExporter,
                csvExporter = csvExporter
            )
        }

        // ========================================
        // SESSION MANAGEMENT
        // ========================================

        @dagger.Provides
        @javax.inject.Singleton
        fun provideUnifiedWorkoutSessionManager(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
            workoutRepository: com.example.liftrix.domain.repository.workout.WorkoutRepository,
            feedRepository: com.example.liftrix.domain.repository.social.FeedRepository,
            cacheManager: com.example.liftrix.core.cache.CacheManager,
            cacheInvalidationService: com.example.liftrix.service.CacheInvalidationService,
            gymBuddyWorkoutCompletionNotifier: com.example.liftrix.service.GymBuddyWorkoutCompletionNotifier
        ): com.example.liftrix.service.UnifiedWorkoutSessionManager {
            return com.example.liftrix.service.UnifiedWorkoutSessionManager(
                context = context,
                workoutRepository = workoutRepository,
                feedRepository = feedRepository,
                cacheManager = cacheManager,
                cacheInvalidationService = cacheInvalidationService,
                gymBuddyWorkoutCompletionNotifier = gymBuddyWorkoutCompletionNotifier
            )
        }

        // ========================================
        // UTILITIES
        // ========================================

        @dagger.Provides
        @javax.inject.Singleton
        fun provideNotificationManager(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
        ): android.app.NotificationManager {
            return context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideStateCleanupManager(): com.example.liftrix.ui.common.state.StateCleanupManager {
            return com.example.liftrix.ui.common.state.StateCleanupManager()
        }

        @dagger.Provides
        @javax.inject.Singleton
        fun provideBillingClientManager(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
        ): com.example.liftrix.billing.BillingClientManager {
            return com.example.liftrix.billing.BillingClientManager(context)
        }

        // --- Analytics Dashboard UI (UI-Layer Caching) ---

        /**
         * Provides DiskCache implementation for persistent widget data storage.
         */
        @dagger.Provides
        @javax.inject.Singleton
        fun provideDiskCache(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
            gson: com.google.gson.Gson,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.cache.DiskCache {
            return com.example.liftrix.service.cache.DiskCache(
                context = context,
                gson = gson,
                ioDispatcher = ioDispatcher
            )
        }

        /**
         * Provides CacheStrategy implementation for widget complexity-based TTL management.
         */
        @dagger.Provides
        @javax.inject.Singleton
        fun provideCacheStrategy(): com.example.liftrix.service.cache.CacheStrategy {
            return com.example.liftrix.service.cache.CacheStrategyImpl()
        }

        /**
         * Provides WidgetCacheManager implementation for coordinated multi-tier caching.
         */
        @dagger.Provides
        @javax.inject.Singleton
        fun provideWidgetCacheManager(
            memoryCache: com.example.liftrix.core.cache.CacheManager,
            diskCache: com.example.liftrix.service.cache.DiskCache,
            cacheStrategy: com.example.liftrix.service.cache.CacheStrategy,
            @IoDispatcher ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
        ): com.example.liftrix.service.cache.WidgetCacheManager {
            return com.example.liftrix.service.cache.WidgetCacheManager(
                memoryCache = memoryCache,
                diskCache = diskCache,
                cacheStrategy = cacheStrategy,
                ioDispatcher = ioDispatcher
            )
        }

        /**
         * Provides RefreshWidgetDataUseCase for batch widget refresh operations.
         */
        @dagger.Provides
        @javax.inject.Singleton
        fun provideRefreshWidgetDataUseCase(
            analyticsService: com.example.liftrix.service.AnalyticsService
        ): com.example.liftrix.domain.usecase.analytics.RefreshWidgetDataUseCase {
            return com.example.liftrix.domain.usecase.analytics.RefreshWidgetDataUseCase(
                analyticsService = analyticsService
            )
        }

        /**
         * Provides GetDashboardConfigurationUseCase for layout and preferences.
         */
        @dagger.Provides
        @javax.inject.Singleton
        fun provideGetDashboardConfigurationUseCase(
            widgetPreferencesRepository: com.example.liftrix.domain.repository.WidgetPreferencesRepository,
            analyticsService: com.example.liftrix.service.AnalyticsService
        ): com.example.liftrix.domain.usecase.analytics.GetDashboardConfigurationUseCase {
            return com.example.liftrix.domain.usecase.analytics.GetDashboardConfigurationUseCase(
                widgetPreferencesRepository = widgetPreferencesRepository,
                analyticsService = analyticsService
            )
        }
    }

    // ========================================
    // PROFILE & REPOSITORY BINDINGS
    // ========================================

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindProfileImageRepository(
        impl: com.example.liftrix.data.repository.ProfileImageRepositoryImpl
    ): com.example.liftrix.domain.repository.ProfileImageRepository

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindSettingsRepository(
        impl: com.example.liftrix.data.repository.SettingsRepositoryImpl
    ): com.example.liftrix.domain.repository.SettingsRepository

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindSubscriptionRepository(
        impl: com.example.liftrix.data.repository.SubscriptionRepositoryImpl
    ): com.example.liftrix.domain.repository.SubscriptionRepository

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindUserAccountRepository(
        impl: com.example.liftrix.data.repository.UserAccountRepositoryImpl
    ): com.example.liftrix.domain.repository.UserAccountRepository

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindBillingRepository(
        impl: com.example.liftrix.billing.BillingRepositoryImpl
    ): com.example.liftrix.billing.BillingRepository

    @dagger.Binds
    @javax.inject.Singleton
    abstract fun bindAdminFirebaseService(
        impl: com.example.liftrix.data.service.AdminFirebaseServiceImpl
    ): com.example.liftrix.domain.service.AdminFirebaseService
}
