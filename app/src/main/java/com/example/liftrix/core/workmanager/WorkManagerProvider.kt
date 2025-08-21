package com.example.liftrix.core.workmanager

import android.content.Context
import androidx.work.WorkManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for WorkManager access that ensures proper initialization via Configuration.Provider.
 * 
 * This class provides a safe access point for WorkManager that:
 * 1. Ensures WorkManager is auto-initialized with HiltWorkerFactory
 * 2. Provides a convenient static accessor
 * 3. Logs any initialization issues for debugging
 * 
 * NOTE: WorkManager is now auto-initialized through LiftrixApp implementing Configuration.Provider.
 * No manual initialization is needed - this prevents NoSuchMethodException in Hilt workers.
 */
@Singleton
class WorkManagerProvider @Inject constructor() {
    
    companion object {
        /**
         * Get WorkManager instance.
         * 
         * WorkManager is auto-initialized via Configuration.Provider in LiftrixApp.
         * This ensures HiltWorkerFactory is properly configured for all workers.
         * 
         * @param context Application or Activity context
         * @return WorkManager instance configured with HiltWorkerFactory
         */
        @JvmStatic
        fun getInstance(context: Context): WorkManager {
            return try {
                WorkManager.getInstance(context).also {
                    Timber.v("WorkManager accessed - auto-initialized with HiltWorkerFactory")
                }
            } catch (e: IllegalStateException) {
                Timber.e(e, "WorkManager not properly initialized. Check that LiftrixApp implements Configuration.Provider correctly.")
                throw IllegalStateException(
                    "WorkManager not properly initialized with HiltWorkerFactory. " +
                    "Workers with Hilt dependencies will fail.", e
                )
            }
        }
    }
}