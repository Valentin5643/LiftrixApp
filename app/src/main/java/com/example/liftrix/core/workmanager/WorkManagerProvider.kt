package com.example.liftrix.core.workmanager

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.hilt.work.HiltWorkerFactory
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton provider for WorkManager that ensures proper initialization with HiltWorkerFactory.
 * 
 * This class solves the initialization order problem by:
 * 1. Providing a single point of WorkManager access
 * 2. Ensuring WorkManager is only initialized once with HiltWorkerFactory
 * 3. Preventing any access before proper initialization
 * 
 * CRITICAL: This class must be initialized by LiftrixApp.onCreate() AFTER Hilt injection.
 * No component should access WorkManager before Application.onCreate() completes.
 */
@Singleton
class WorkManagerProvider @Inject constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: WorkManagerProvider? = null
        
        private val isInitialized = AtomicBoolean(false)
        private val initializationLock = Any()
        
        /**
         * Initialize WorkManager with the provided configuration.
         * This MUST be called from Application.onCreate() after Hilt injection.
         * 
         * @param context Application context
         * @param workerFactory HiltWorkerFactory from Hilt injection
         * @return true if initialization succeeded, false if already initialized
         */
        @JvmStatic
        fun initialize(context: Context, workerFactory: HiltWorkerFactory): Boolean {
            synchronized(initializationLock) {
                if (isInitialized.get()) {
                    Timber.w("WorkManager already initialized - skipping")
                    return false
                }
                
                try {
                    Timber.d("Initializing WorkManager with HiltWorkerFactory...")
                    
                    val config = Configuration.Builder()
                        .setWorkerFactory(workerFactory)
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .build()
                    
                    WorkManager.initialize(context, config)
                    isInitialized.set(true)
                    
                    Timber.d("✅ WorkManager initialized successfully with HiltWorkerFactory")
                    return true
                    
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("WorkManager is already initialized") == true) {
                        Timber.e("WorkManager was already initialized outside of our control!")
                        Timber.e("This will cause ProfileSyncWorker to fail with NoSuchMethodException")
                        isInitialized.set(true) // Mark as initialized to prevent further attempts
                        return false
                    }
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize WorkManager with HiltWorkerFactory")
                    throw IllegalStateException(
                        "WorkManager initialization failed. ProfileSyncWorker will not work.", e
                    )
                }
            }
        }
        
        /**
         * Get WorkManager instance.
         * 
         * @throws IllegalStateException if WorkManager is not initialized
         */
        @JvmStatic
        fun getInstance(context: Context): WorkManager {
            if (!isInitialized.get()) {
                throw IllegalStateException(
                    "WorkManager not initialized! Call WorkManagerProvider.initialize() from " +
                    "Application.onCreate() after Hilt injection completes. " +
                    "This error prevents ProfileSyncWorker from working."
                )
            }
            
            return try {
                WorkManager.getInstance(context)
            } catch (e: IllegalStateException) {
                throw IllegalStateException(
                    "WorkManager not properly initialized with HiltWorkerFactory. " +
                    "ProfileSyncWorker will fail.", e
                )
            }
        }
        
        /**
         * Check if WorkManager has been initialized.
         */
        @JvmStatic
        fun isInitialized(): Boolean = isInitialized.get()
    }
}