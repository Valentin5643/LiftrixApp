package com.example.liftrix.data.local

import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.local.seed.MetDataSeedService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeedInitializer @Inject constructor(
    private val database: LiftrixDatabase,
    private val exerciseLibrarySeedData: ExerciseLibrarySeedData,
    private val metDataSeedService: MetDataSeedService
) {
    private val exerciseSeedMutex = Mutex()
    private val allSeedMutex = Mutex()

    suspend fun ensureExerciseLibrarySeeded(reason: String) {
        exerciseSeedMutex.withLock {
            val startedAt = System.currentTimeMillis()
            try {
                val currentCount = database.exerciseLibraryDao().getExerciseCount()
                if (currentCount == 0) {
                    Timber.i("StartupTask name=exercise_library_seed class=FirstScreen reason=$reason status=started")
                    exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                } else {
                    Timber.d(
                        "StartupTask name=exercise_library_seed class=FirstScreen reason=$reason status=skipped count=$currentCount"
                    )
                }
                Timber.i(
                    "StartupTask name=exercise_library_seed class=FirstScreen reason=$reason status=finished durationMs=${System.currentTimeMillis() - startedAt}"
                )
            } catch (error: Exception) {
                Timber.e(
                    error,
                    "StartupTask name=exercise_library_seed class=FirstScreen reason=$reason status=failed durationMs=${System.currentTimeMillis() - startedAt}"
                )
                throw error
            }
        }
    }

    suspend fun runDeferredSeeds(reason: String) {
        allSeedMutex.withLock {
            val startedAt = System.currentTimeMillis()
            try {
                Timber.i("StartupTask name=database_deferred_seeds class=Deferred reason=$reason status=started")
                ensureExerciseLibrarySeeded(reason)
                metDataSeedService.populateMetDataIfNeeded(database)
                Timber.i(
                    "StartupTask name=database_deferred_seeds class=Deferred reason=$reason status=finished durationMs=${System.currentTimeMillis() - startedAt}"
                )
            } catch (error: Exception) {
                Timber.e(
                    error,
                    "StartupTask name=database_deferred_seeds class=Deferred reason=$reason status=failed durationMs=${System.currentTimeMillis() - startedAt}"
                )
            }
        }
    }
}
