package com.example.liftrix.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import com.example.liftrix.data.sync.OfflineQueueManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

@HiltWorker
class DeadLetterCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineQueueManager: OfflineQueueManager
) : BaseSyncWorker(context, params) {

    override val workerName: String = "DeadLetterCleanupWorker"

    override suspend fun performSync(userId: String): Result {
        checkCancellation()

        val cleanedCount = offlineQueueManager.cleanupOldDeadLetterItems(userId)
            .getOrElse { throw it }

        return Result.success(
            Data.Builder()
                .putInt(KEY_CLEANED_COUNT, cleanedCount)
                .build()
        )
    }

    companion object {
        const val WORK_NAME = "dead_letter_cleanup"
        const val KEY_CLEANED_COUNT = "cleaned_count"
        private const val CLEANUP_INTERVAL_HOURS = 24L

        fun createPeriodicWorkRequest(userId: String): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<DeadLetterCleanupWorker>(
                CLEANUP_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setInputData(workDataOf(KEY_USER_ID to userId))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(WORK_NAME)
                .addTag("user_$userId")
                .build()
        }

        fun getWorkName(userId: String): String = "${WORK_NAME}_$userId"
    }
}
