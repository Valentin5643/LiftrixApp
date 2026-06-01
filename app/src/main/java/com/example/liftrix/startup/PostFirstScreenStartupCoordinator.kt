package com.example.liftrix.startup

import com.example.liftrix.data.local.DatabaseSeedInitializer
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.di.ApplicationScope
import com.example.liftrix.di.IoDispatcher
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.widget.LiftrixWidgetUpdateScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostFirstScreenStartupCoordinator @Inject constructor(
    private val databaseSeedInitializer: DatabaseSeedInitializer,
    private val syncCoordinator: SyncCoordinator,
    private val offlineQueueManager: OfflineQueueManager,
    private val liftrixWidgetUpdateScheduler: LiftrixWidgetUpdateScheduler,
    private val startupTaskTracer: StartupTaskTracer,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    @Volatile
    private var lastStartedUserId: String? = null

    fun start(userId: String, source: String) {
        if (lastStartedUserId == userId) return
        lastStartedUserId = userId

        applicationScope.launch(ioDispatcher) {
            runMeasured("exercise_library_seed_gate", StartupTaskClass.FirstScreen) {
                databaseSeedInitializer.ensureExerciseLibrarySeeded("post_first_screen:$source")
            }

            runMeasured("database_seed_warmup", StartupTaskClass.Deferred) {
                databaseSeedInitializer.runDeferredSeeds("post_first_screen:$source")
            }

            runMeasured("legacy_sync_queue_cleanup", StartupTaskClass.Deferred) {
                cleanupLegacyQueueEntries(userId)
            }

            runMeasured("startup_sync", StartupTaskClass.Deferred) {
                triggerStartupSync(userId, source)
            }

            runMeasured("periodic_sync_schedule", StartupTaskClass.Deferred) {
                syncCoordinator.schedulePeriodicSync(userId)
            }

            runMeasured("widget_refresh_schedule", StartupTaskClass.Deferred) {
                liftrixWidgetUpdateScheduler.enqueueRefresh(
                    reason = "post_first_screen:$source",
                    userId = userId
                )
                liftrixWidgetUpdateScheduler.enqueuePeriodicRefresh(userId)
            }
        }
    }

    fun reset() {
        lastStartedUserId = null
    }

    private suspend fun runMeasured(
        name: String,
        taskClass: StartupTaskClass,
        block: suspend () -> Unit
    ) {
        try {
            startupTaskTracer.measureSuspend(name, taskClass, block)
        } catch (error: Exception) {
            Timber.w(error, "Deferred startup task failed: $name")
        }
    }

    private suspend fun cleanupLegacyQueueEntries(userId: String) {
        val result = offlineQueueManager.cleanupLegacyQueueEntries(userId)
        result.fold(
            onSuccess = { removedCount ->
                if (removedCount > 0) {
                    Timber.i("LEGACY-CLEANUP: Removed $removedCount legacy queue entries")
                } else {
                    Timber.d("LEGACY-CLEANUP: No legacy entries found")
                }
            },
            onFailure = { error ->
                Timber.w("LEGACY-CLEANUP: Cleanup failed: ${error.message}")
            }
        )
    }

    private suspend fun triggerStartupSync(userId: String, source: String) {
        val syncResult = syncCoordinator.triggerStartupSync(
            userId = userId,
            source = "post_first_screen:$source"
        )
        if (syncResult.isSuccess) {
            Timber.i("Startup sync initiated after first screen for user: $userId")
        } else {
            Timber.w("Startup sync failed after first screen for user: $userId")
        }
    }
}
