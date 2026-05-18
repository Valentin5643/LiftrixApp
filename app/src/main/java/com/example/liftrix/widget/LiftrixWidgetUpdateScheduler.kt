package com.example.liftrix.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LiftrixWidgetUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun enqueueRefresh(reason: String, userId: String? = null) {
        val inputData = Data.Builder()
            .putString(KEY_REASON, reason)
            .apply {
                if (!userId.isNullOrBlank()) {
                    putString(KEY_USER_ID, userId)
                }
            }
            .build()
        val request = OneTimeWorkRequestBuilder<LiftrixWidgetRefreshWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            WORK_REFRESH,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueuePostWorkoutRefresh(userId: String) {
        enqueueRefresh(reason = "post_workout", userId = userId)
    }

    fun enqueuePeriodicRefresh(userId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<LiftrixWidgetRefreshWorker>(
            6,
            TimeUnit.HOURS
        )
            .setInputData(workDataOf(KEY_REASON to "periodic", KEY_USER_ID to userId))
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun clearAndUpdateAll() {
        Timber.d("Clearing native home widgets to private state")
        updateAllNow()
    }

    suspend fun updateAllNow() {
        StreakGlanceWidget().updateAll(context)
        ConsistencyGlanceWidget().updateAll(context)
        DashboardGlanceWidget().updateAll(context)
    }

    companion object {
        const val WORK_REFRESH = "native_home_widget_refresh"
        const val WORK_PERIODIC = "native_home_widget_periodic"
        const val KEY_REASON = "reason"
        const val KEY_USER_ID = "userId"
    }
}
