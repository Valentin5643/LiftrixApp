package com.example.liftrix.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class LiftrixWidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataSource: LiftrixHomeWidgetDataSource,
    private val scheduler: LiftrixWidgetUpdateScheduler
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val reason = inputData.getString(LiftrixWidgetUpdateScheduler.KEY_REASON) ?: "unknown"
        val userId = inputData.getString(LiftrixWidgetUpdateScheduler.KEY_USER_ID)
        return try {
            dataSource.loadSnapshots()
            scheduler.updateAllNow()
            Timber.d("Native home widgets refreshed reason=$reason userId=${userId ?: "none"}")
            Result.success(workDataOf("reason" to reason))
        } catch (e: Exception) {
            Timber.e(e, "Native home widget refresh failed")
            Result.retry()
        }
    }
}
