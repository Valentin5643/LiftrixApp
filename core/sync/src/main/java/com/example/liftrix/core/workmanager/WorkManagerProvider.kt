package com.example.liftrix.core.workmanager

import android.content.Context
import androidx.work.WorkManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for WorkManager access that defers to manual initialization in Application.onCreate().
 * 
 * This class provides a safe access point for WorkManager that:
 * 1. Waits for WorkManager to be properly initialized with HiltWorkerFactory
 * 2. Provides a convenient static accessor with safety checks
 * 3. Logs any initialization issues for debugging
 * 
 * CRITICAL: WorkManager is manually initialized in LiftrixApp.onCreate() with HiltWorkerFactory.
 * This provider ensures no premature calls create the default instance.
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
                val instance = WorkManager.getInstance(context)
                val factory = instance.configuration.workerFactory
                
                // Log WorkManager access with factory info for diagnostics
                Timber.v("WorkManager accessed via WorkManagerProvider - Factory: $factory")
                
                // Check if we're using HiltWorkerFactory (defensive check)
                if (factory != null && factory::class.java.simpleName.contains("Hilt")) {
                    Timber.v("✅ WorkManager using HiltWorkerFactory: $factory")
                } else {
                    Timber.w("⚠️ WorkManager NOT using HiltWorkerFactory! Factory: $factory")
                    Timber.w("This indicates WorkManager was initialized without Hilt configuration")
                }
                
                instance
                
            } catch (e: IllegalStateException) {
                Timber.e(e, "WorkManager not initialized. Manual initialization in Application.onCreate() may have failed.")
                throw IllegalStateException(
                    "WorkManager not properly initialized with HiltWorkerFactory. " +
                    "Check Application.onCreate() WorkManager initialization.", e
                )
            }
        }
    }
}