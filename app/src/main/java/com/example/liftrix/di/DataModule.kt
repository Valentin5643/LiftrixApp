package com.example.liftrix.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.liftrix.BuildConfig
import com.example.liftrix.core.security.DatabaseEncryption
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.*
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.local.seed.MetDataSeedService
import com.example.liftrix.data.mapper.WorkoutPostMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import java.io.File
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

// ========================================
// FILE-LEVEL DATASTORE EXTENSION PROPERTIES
// ========================================
// Extension properties for DataStore must be defined at file level (not inside companion objects)

/**
 * Extension property to create main DataStore instance for user settings.
 * This creates a singleton DataStore instance that's tied to the application lifecycle.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_settings")

/**
 * Extension property to create widget preferences DataStore instance.
 * This creates a separate DataStore instance specifically for widget configurations
 * to improve performance and maintainability.
 */
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_widget_preferences")

/**
 * Extension property to create onboarding temporary data DataStore instance.
 * This creates a separate DataStore instance specifically for temporary onboarding data
 * that survives account creation and sign-in transitions.
 */
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "liftrix_onboarding_temp")

/**
 * DataModule - Data Layer Dependency Injection
 *
 * PURPOSE: All data persistence and storage infrastructure
 *
 * CONSOLIDATES:
 * - DatabaseModule (61 bindings) - Room + SQLCipher + DAOs
 * - DataStoreModule (3 bindings) - Preferences storage
 * - SecurityModule (6 bindings) - Serialization and validation
 * - CoreModule (5 bindings) - Cache infrastructure and dispatchers
 *
 * TOTAL BINDINGS: ~75
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    companion object {
        // ========================================
        // DATABASE SECTION (Room + SQLCipher)
        // ========================================

        @Provides
        @Singleton
        fun provideLiftrixDatabase(
            @ApplicationContext context: Context,
            exerciseLibrarySeedData: ExerciseLibrarySeedData,
            metDataSeedService: MetDataSeedService,
            databaseEncryption: DatabaseEncryption
        ): LiftrixDatabase {

            // 🔒 SECURITY: Initialize SQLCipher and validate encryption setup
            System.loadLibrary("sqlcipher")

            // Validate encryption setup before proceeding
            if (!databaseEncryption.validateEncryptionSetup()) {
                throw SecurityException("Database encryption validation failed")
            }

            // Get the encryption passphrase for SQLCipher
            val passphrase = databaseEncryption.getSQLCipherPassphrase()
            val factory = SupportOpenHelperFactory(passphrase.toByteArray())

            val database = Room.databaseBuilder(
                context.applicationContext,
                LiftrixDatabase::class.java,
                "liftrix_database_encrypted" // Encrypted database name
            )
                .openHelperFactory(factory) // 🔒 Enable SQLCipher encryption
                .setTransactionExecutor(Dispatchers.IO.asExecutor())
                .setQueryExecutor(Dispatchers.IO.asExecutor())
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // WAL mode for better data persistence
                // 🛡️ DATABASE LIFECYCLE: Add callback for database lifecycle events
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Timber.d("Encrypted database created successfully")

                        // Verify encryption is active
                        try {
                            val result = db.query("PRAGMA cipher_version;")
                            if (result.moveToFirst()) {
                                val version = result.getString(0)
                                Timber.i("SQLCipher version: $version - encryption active")
                            }
                            result.close()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to verify SQLCipher encryption")
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Timber.d("Encrypted database opened successfully")
                    }

                })
                .build()

            // CRITICAL: Initialize database synchronously to prevent early access issues
            runBlocking(Dispatchers.IO) {
                try {
                    Timber.d("Starting database initialization...")

                    // 🔍 MIGRATION VALIDATION: Check database integrity before proceeding
                    val dbVersion = try {
                        database.openHelper.readableDatabase.version
                    } catch (e: Exception) {
                        Timber.e(e, "Database version check failed - possible corruption")
                        // In development, attempt recovery by clearing corrupted database
                        if (BuildConfig.DEBUG) {
                            try {
                                val dbFile = File(context.getDatabasePath("liftrix_database").absolutePath)
                                if (dbFile.exists()) {
                                    dbFile.delete()
                                    Timber.w("Cleared corrupted database file for recovery")
                                    // Retry database initialization after clearing
                                    database.openHelper.readableDatabase.version
                                } else {
                                    throw e
                                }
                            } catch (recoveryException: Exception) {
                                Timber.e(recoveryException, "Database recovery failed")
                                throw recoveryException
                            }
                        } else {
                            throw e
                        }
                    }

                    Timber.d("Database version: $dbVersion - initialization successful")

                    // Populate exercise library if needed
                    exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)

                    // Populate MET data if needed
                    metDataSeedService.populateMetDataIfNeeded(database)

                    Timber.d("Database initialization completed successfully")

                } catch (e: Exception) {
                    Timber.e(e, "Database initialization failed - repositories may experience connection errors")
                    // Continue anyway to avoid blocking app startup completely
                }
            }

            return database
        }

        @Provides
        @Singleton
        fun provideDatabaseEncryption(
            @ApplicationContext context: Context
        ): DatabaseEncryption {
            return DatabaseEncryption(context)
        }

        // --- Core DAOs ---
        @Provides
        fun provideWorkoutDao(database: LiftrixDatabase): WorkoutDao = database.workoutDao()

        @Provides
        fun provideUserProfileDao(database: LiftrixDatabase): UserProfileDao = database.userProfileDao()

        @Provides
        fun provideCustomExerciseDao(database: LiftrixDatabase): CustomExerciseDao = database.customExerciseDao()

        @Provides
        fun provideExerciseLibraryDao(database: LiftrixDatabase): ExerciseLibraryDao = database.exerciseLibraryDao()

        @Provides
        fun provideExerciseDao(database: LiftrixDatabase): ExerciseDao = database.exerciseDao()

        @Provides
        fun provideExerciseSetDao(database: LiftrixDatabase): ExerciseSetDao = database.exerciseSetDao()

        @Provides
        fun provideExerciseWeightMemoryDao(database: LiftrixDatabase): ExerciseWeightMemoryDao = database.exerciseWeightMemoryDao()

        @Provides
        fun provideWorkoutTemplateDao(database: LiftrixDatabase): WorkoutTemplateDao = database.workoutTemplateDao()

        @Provides
        fun provideExerciseUsageHistoryDao(database: LiftrixDatabase): ExerciseUsageHistoryDao = database.exerciseUsageHistoryDao()

        @Provides
        fun provideFriendDao(database: LiftrixDatabase): FriendDao = database.friendDao()

        @Provides
        fun providePrivacySettingsDao(database: LiftrixDatabase): PrivacySettingsDao = database.privacySettingsDao()

        @Provides
        fun provideFolderDao(database: LiftrixDatabase): FolderDao = database.folderDao()

        @Provides
        fun provideSubscriptionDao(database: LiftrixDatabase): SubscriptionDao = database.subscriptionDao()

        @Provides
        fun provideSettingsDao(database: LiftrixDatabase): SettingsDao = database.settingsDao()

        @Provides
        fun provideMetDataDao(database: LiftrixDatabase): MetDataDao = database.metDataDao()

        @Provides
        fun provideConsentDao(database: LiftrixDatabase): ConsentDao = database.consentDao()

        @Provides
        fun provideWorkoutAnomalyDao(database: LiftrixDatabase): WorkoutAnomalyDao = database.workoutAnomalyDao()

        @Provides
        fun provideAnomalyDetectionSettingsDao(database: LiftrixDatabase): AnomalyDetectionSettingsDao = database.anomalyDetectionSettingsDao()

        @Provides
        fun provideExerciseHistoryDao(database: LiftrixDatabase): ExerciseHistoryDao = database.exerciseHistoryDao()

        @Provides
        fun provideWidgetPreferencesDao(database: LiftrixDatabase): WidgetPreferencesDao = database.widgetPreferencesDao()

        @Provides
        fun provideAchievementDao(database: LiftrixDatabase): AchievementDao = database.achievementDao()

        @Provides
        fun provideUserSearchCacheDao(database: LiftrixDatabase): UserSearchCacheDao = database.userSearchCacheDao()

        @Provides
        fun provideQRCodeMappingDao(database: LiftrixDatabase): QRCodeMappingDao = database.qrCodeMappingDao()

        // --- Analytics DAOs ---
        @Provides
        fun provideAnalyticsCacheDao(database: LiftrixDatabase): AnalyticsCacheDao = database.analyticsCacheDao()

        // --- Social DAOs ---
        @Provides
        fun provideSocialProfileDao(database: LiftrixDatabase): SocialProfileDao = database.socialProfileDao()

        @Provides
        fun provideFollowRelationshipDao(database: LiftrixDatabase): FollowRelationshipDao = database.followRelationshipDao()

        @Provides
        fun provideFollowRequestDao(database: LiftrixDatabase): FollowRequestDao = database.followRequestDao()

        @Provides
        fun provideGymBuddyDao(database: LiftrixDatabase): GymBuddyDao = database.gymBuddyDao()

        @Provides
        fun provideTemplateShareEventDao(database: LiftrixDatabase): TemplateShareEventDao = database.templateShareEventDao()

        @Provides
        fun providePRNotificationDao(database: LiftrixDatabase): PRNotificationDao = database.prNotificationDao()

        @Provides
        fun providePRReactionDao(database: LiftrixDatabase): PRReactionDao = database.prReactionDao()

        @Provides
        fun providePRNotificationPreferencesDao(database: LiftrixDatabase): PRNotificationPreferencesDao = database.prNotificationPreferencesDao()

        @Provides
        fun providePersonalRecordDao(database: LiftrixDatabase): PersonalRecordDao = database.personalRecordDao()

        @Provides
        fun provideSocialPrivacySettingsDao(database: LiftrixDatabase): SocialPrivacySettingsDao = database.socialPrivacySettingsDao()

        @Provides
        fun provideBlockedUserDao(database: LiftrixDatabase): BlockedUserDao = database.blockedUserDao()

        @Provides
        fun provideContentReportsDao(database: LiftrixDatabase): ContentReportsDao = database.contentReportsDao()

        // --- Notification DAOs ---
        @Provides
        fun provideFCMTokenDao(database: LiftrixDatabase): FCMTokenDao = database.fcmTokenDao()

        @Provides
        fun provideNotificationPreferenceDao(database: LiftrixDatabase): NotificationPreferenceDao = database.notificationPreferenceDao()

        @Provides
        fun provideNotificationQueueDao(database: LiftrixDatabase): NotificationQueueDao = database.notificationQueueDao()

        @Provides
        fun provideNotificationMuteDao(database: LiftrixDatabase): NotificationMuteDao = database.notificationMuteDao()

        @Provides
        fun provideNotificationHistoryDao(database: LiftrixDatabase): NotificationHistoryDao = database.notificationHistoryDao()

        @Provides
        fun provideUserAccountDao(database: LiftrixDatabase): UserAccountDao = database.userAccountDao()

        // --- Support DAOs ---
        @Provides
        fun provideHelpArticleDao(database: LiftrixDatabase): HelpArticleDao = database.helpArticleDao()

        @Provides
        fun provideSupportTicketDao(database: LiftrixDatabase): SupportTicketDao = database.supportTicketDao()

        @Provides
        fun provideAppConfigDao(database: LiftrixDatabase): AppConfigDao = database.appConfigDao()

        @Provides
        fun provideSettingsAuditDao(database: LiftrixDatabase): SettingsAuditDao = database.settingsAuditDao()

        // --- Import/Export DAOs ---
        @Provides
        fun provideDataExportDao(database: LiftrixDatabase): DataExportDao = database.dataExportDao()

        @Provides
        fun provideDataImportDao(database: LiftrixDatabase): DataImportDao = database.dataImportDao()

        // --- Sync DAOs ---
        @Provides
        fun provideSyncQueueDao(database: LiftrixDatabase): SyncQueueDao = database.syncQueueDao()

        @Provides
        fun provideDeadLetterQueueDao(database: LiftrixDatabase): DeadLetterQueueDao = database.deadLetterQueueDao()

        @Provides
        fun provideSyncPreferencesDao(database: LiftrixDatabase): SyncPreferencesDao = database.syncPreferencesDao()

        // --- Chat DAOs ---
        @Provides
        fun provideChatPreferencesDao(database: LiftrixDatabase): ChatPreferencesDao = database.chatPreferencesDao()

        @Provides
        fun provideChatHistoryDao(database: LiftrixDatabase): ChatHistoryDao = database.chatHistoryDao()

        // --- Mappers (DAO-dependent) ---
        @Provides
        @Singleton
        fun provideWorkoutPostMapper(
            workoutDao: WorkoutDao,
            customExerciseDao: CustomExerciseDao
        ): WorkoutPostMapper {
            return WorkoutPostMapper(workoutDao, customExerciseDao)
        }

        // ========================================
        // DATASTORE SECTION (Preferences)
        // ========================================

        /**
         * Provides a singleton DataStore instance for user preferences.
         * References file-level extension property.
         *
         * @param context The application context
         * @return DataStore instance for general user preferences
         */
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.settingsDataStore
        }

        /**
         * Provides a singleton DataStore instance for widget preferences.
         * References file-level extension property.
         *
         * @param context The application context
         * @return DataStore instance for widget preferences
         */
        @Provides
        @Singleton
        @javax.inject.Named("widgetPreferences")
        fun provideWidgetPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.widgetDataStore
        }

        /**
         * Provides a singleton DataStore instance for temporary onboarding data.
         * References file-level extension property.
         *
         * @param context The application context
         * @return DataStore instance for onboarding temporary storage
         */
        @Provides
        @Singleton
        @javax.inject.Named("onboardingDataStore")
        fun provideOnboardingDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.onboardingDataStore
        }

        // ========================================
        // SECURITY & SERIALIZATION SECTION
        // ========================================
        @Provides
        @Singleton
        fun provideJsonInputValidator(): com.example.liftrix.core.security.JsonInputValidator {
            return com.example.liftrix.core.security.JsonInputValidator()
        }

        @Provides
        @Singleton
        fun provideObjectPoolManager(): com.example.liftrix.core.performance.ObjectPoolManager {
            return com.example.liftrix.core.performance.ObjectPoolManager()
        }

        @Provides
        @Singleton
        fun provideStreamingJsonParser(
            objectPoolManager: com.example.liftrix.core.performance.ObjectPoolManager
        ): com.example.liftrix.core.performance.StreamingJsonParser {
            return com.example.liftrix.core.performance.StreamingJsonParser(objectPoolManager)
        }

        @Provides
        @Singleton
        fun provideSerializationPerformanceMonitor(): com.example.liftrix.core.performance.SerializationPerformanceMonitor {
            return com.example.liftrix.core.performance.SerializationPerformanceMonitor()
        }

        @Provides
        @Singleton
        fun provideSerializationCacheManager(
            performanceMonitor: com.example.liftrix.core.performance.SerializationPerformanceMonitor
        ): com.example.liftrix.core.performance.SerializationCacheManager {
            return com.example.liftrix.core.performance.SerializationCacheManager(performanceMonitor)
        }

        @Provides
        @Singleton
        fun provideKotlinxWorkoutSerializationService(
            jsonValidator: com.example.liftrix.core.security.JsonInputValidator,
            performanceMonitor: com.example.liftrix.core.performance.SerializationPerformanceMonitor,
            cacheManager: com.example.liftrix.core.performance.SerializationCacheManager
        ): com.example.liftrix.data.service.KotlinxWorkoutSerializationService {
            return com.example.liftrix.data.service.KotlinxWorkoutSerializationService(jsonValidator, performanceMonitor, cacheManager)
        }

    }
}
