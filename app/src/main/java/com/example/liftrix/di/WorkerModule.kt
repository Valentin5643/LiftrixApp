package com.example.liftrix.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * WorkerModule - WorkManager Integration
 *
 * ARCHITECTURE NOTES:
 * - Uses @HiltWorker pattern for the current worker set
 * - Workers use @AssistedInject for Context and WorkerParameters
 * - BaseSyncWorker provides error handling, cancellation, and backoff
 * - WorkerServiceLocator provides fallback EntryPoint access (temporary HOTFIX)
 *
 * WORKER LIST (20 total at 2026-05-20 source check):
 * - MasterSyncWorker (orchestrates all sync operations every 15 min)
 * - AchievementSyncWorker, AnalyticsSyncWorker, ChatSyncWorker
 * - DeadLetterCleanupWorker, DatabaseIntegrityWorker
 * - FollowRelationshipSyncWorker, GymBuddySyncWorker, ProfileSyncWorker
 * - SettingsSyncWorker, SettingsSyncWorkerV2 (dual versions during migration)
 * - SocialProfileSyncWorker, TemplateSyncWorker, UnifiedSyncWorker
 * - UserPublicSyncWorker, WorkoutPostSyncWorker, WorkoutSyncWorker
 * - ExportWorker (data export), WidgetSyncWorker
 * - LiftrixWidgetRefreshWorker
 *
 * INITIALIZATION:
 * - WorkManager initialized via Configuration.Provider in LiftrixApp
 * - HiltWorkerFactory injected via @Inject in Application class
 * - Workers scheduled via SyncCoordinator.schedulePeriodicSync()
 *
 * KNOWN ISSUES:
 * - WorkerServiceLocator is TEMPORARY fallback (remove once Hilt factory confirmed)
 * - Fallback constructors in workers can be removed after WorkerServiceLocator deletion
 *
 * USAGE:
 * All workers use the @HiltWorker annotation and @AssistedInject constructors.
 * Example:
 *
 * ```kotlin
 * @HiltWorker
 * class MyWorker @AssistedInject constructor(
 *     @Assisted appContext: Context,
 *     @Assisted workerParams: WorkerParameters,
 *     private val repository: MyRepository
 * ) : CoroutineWorker(appContext, workerParams) {
 *     override suspend fun doWork(): Result {
 *         // Worker implementation
 *     }
 * }
 * ```
 *
 * WorkManager configuration in LiftrixApp:
 *
 * ```kotlin
 * class LiftrixApp : Application(), Configuration.Provider {
 *     @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
 *
 *     override val workManagerConfiguration: Configuration
 *         get() = Configuration.Builder()
 *             .setWorkerFactory(hiltWorkerFactory)
 *             .build()
 * }
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    // Currently, workers use @HiltWorker with @AssistedInject constructors
    // No explicit module bindings needed (Hilt handles this automatically)

    // WorkManager initialization is handled via Configuration.Provider in LiftrixApp:
    // override val workManagerConfiguration: Configuration
    //     get() = Configuration.Builder()
    //         .setWorkerFactory(hiltWorkerFactory)
    //         .build()

    // Future: Add explicit worker dependency documentation here if needed
}
