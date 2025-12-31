package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.liftrix.config.OfflineArchitectureFlags
import com.example.liftrix.data.service.CanonicalJsonMigration
import com.example.liftrix.data.service.DatabaseIntegrityService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class DatabaseIntegrityWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val integrityService: DatabaseIntegrityService,
    private val canonicalJsonMigration: CanonicalJsonMigration
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!OfflineArchitectureFlags.ENABLE_ASYNC_INTEGRITY_CHECKS) {
            return Result.success()
        }

        val userId = inputData.getString(KEY_USER_ID)
        val result = integrityService.runIntegrityCheck(userId)
        if (OfflineArchitectureFlags.ENABLE_CANONICAL_JSON_FORMAT && !userId.isNullOrBlank()) {
            canonicalJsonMigration.migrateUserWorkouts(userId)
        }

        return if (result.isOk) {
            Result.success(
                Data.Builder()
                    .putString(KEY_STATUS, result.status)
                    .putLong(KEY_DURATION_MS, result.durationMs)
                    .build()
            )
        } else {
            Timber.w("Database integrity check reported status: ${result.status}")
            Result.failure(
                Data.Builder()
                    .putString(KEY_STATUS, result.status)
                    .putLong(KEY_DURATION_MS, result.durationMs)
                    .build()
            )
        }
    }

    companion object {
        private const val KEY_USER_ID = "userId"
        private const val KEY_STATUS = "integrity_status"
        private const val KEY_DURATION_MS = "integrity_duration_ms"
        private const val WORK_NAME = "database_integrity_check"

        fun createPeriodicWorkRequest(userId: String): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<DatabaseIntegrityWorker>(
                24, TimeUnit.HOURS
            )
                .setInputData(workDataOf(KEY_USER_ID to userId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag("database_integrity")
                .addTag("user_$userId")
                .build()
        }

        fun getWorkName(userId: String): String = "${WORK_NAME}_$userId"
    }
}
