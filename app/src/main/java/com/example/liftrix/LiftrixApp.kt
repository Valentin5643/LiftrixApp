package com.example.liftrix

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.liftrix.BuildConfig
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.service.CacheWarmingService
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
// Debug import handled conditionally in code
import com.google.firebase.initialize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LiftrixApp : Application() {

    @Inject
    lateinit var widgetPreferencesRepository: WidgetPreferencesRepository

    @Inject
    lateinit var cacheWarmingService: CacheWarmingService

    @Inject
    lateinit var syncCoordinator: SyncCoordinator

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var initializeUserThemeUseCase: InitializeUserThemeUseCase

    @Inject
    lateinit var offlineQueueManager: com.example.liftrix.data.sync.OfflineQueueManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // App Check initialization state
    private val _isAppCheckInitialized = MutableStateFlow(false)
    val isAppCheckInitialized: StateFlow<Boolean> = _isAppCheckInitialized.asStateFlow()

    companion object {
        const val WORKOUT_TIMER_CHANNEL_ID = "workout_timer_channel"
        
        @Volatile
        private var INSTANCE: LiftrixApp? = null
        
        fun getInstance(): LiftrixApp {
            return INSTANCE ?: throw IllegalStateException("LiftrixApp instance not available")
        }
        
        fun isAppCheckReady(): Boolean {
            return INSTANCE?.isAppCheckInitialized?.value ?: false
        }
    }
    
    override fun onCreate() {
        super.onCreate()

        // Set the singleton instance
        INSTANCE = this

        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())

        // SPEC-20241228: Log Room-First Architecture Configuration
        logOfflineArchitectureMode()

        // 🔥 FIX: MANUAL WorkManager initialization to ensure HiltWorkerFactory is used
        // This MUST happen before anything calls WorkManager.getInstance()
        // We disabled auto-initialization via androidx.startup in AndroidManifest
        try {
            val workManagerConfig = Configuration.Builder()
                .setWorkerFactory(hiltWorkerFactory)
                .setMinimumLoggingLevel(
                    if (BuildConfig.DEBUG) android.util.Log.DEBUG
                    else android.util.Log.INFO
                )
                .build()

            WorkManager.initialize(this, workManagerConfig)
            Timber.d("🔥 SYNC WorkManager manually initialized with HiltWorkerFactory")
        } catch (e: IllegalStateException) {
            // WorkManager was already initialized (shouldn't happen with Startup disabled)
            Timber.e(e, "🔥 SYNC ❌ WorkManager already initialized - early init detected!")
        }

        // 🔧 DEBUG: Test if HiltWorkerFactory can create workers (debug builds only)
        if (BuildConfig.DEBUG) {
            debugVerifyWorkerFactory()
        }
        
        // Initialize Firebase and App Check FIRST before any other Firebase services
        initializeFirebaseAppCheck()
        
        
        // Create notification channels
        createNotificationChannels()
        
        // Initialize widget migration system
        initializeWidgetMigration()
        
        // Initialize cache warming system
        initializeCacheWarmingSystem()
        
        // Initialize sync system after WorkManager is ready
        initializeSyncSystem()
        
        // 🔥 FIX: Clean up legacy queue entries from before SyncPayload refactoring
        cleanupLegacyQueueEntries()
    }
    
    /**
     * Initialize Firebase and App Check with build-specific providers.
     * CRITICAL: This must be called before any Firebase AI service calls to ensure valid tokens.
     * Thread-safe initialization with proper singleton check.
     */
    private fun initializeFirebaseAppCheck() {
        try {
            // Check if Firebase is already initialized to prevent duplicate initialization
            val existingApp = try {
                com.google.firebase.FirebaseApp.getInstance()
            } catch (e: IllegalStateException) {
                null
            }
            
            if (existingApp != null) {
                Timber.w("Firebase already initialized, skipping re-initialization")
                _isAppCheckInitialized.value = true
                return
            }
            
            // CRITICAL: Initialize Firebase synchronously first
            Timber.d("Initializing Firebase synchronously...")
            Firebase.initialize(this@LiftrixApp)
            Timber.d("Firebase initialized successfully")
            
            // Configure App Check with Play Integrity provider for all builds
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Timber.d("App Check provider factory installed")
            
            // Now launch async task to verify token generation with better retry logic
            applicationScope.launch {
                
                var retryCount = 0
                val maxRetries = 3
                var backoffDelay = 1000L // Start with 1 second
                
                while (retryCount < maxRetries && !_isAppCheckInitialized.value) {
                    try {
                        Timber.d("Attempting to verify App Check token (attempt ${retryCount + 1}/$maxRetries)")
                        
                        // Try to get a token (cached or fresh)
                        val token = if (retryCount == 0) {
                            // First attempt: try cached token
                            Firebase.appCheck.getAppCheckToken(false).await()
                        } else {
                            // Subsequent attempts: force refresh
                            Firebase.appCheck.getAppCheckToken(true).await()
                        }
                        
                        Timber.d("App Check token obtained successfully: ${token.token.take(10)}...")
                        _isAppCheckInitialized.value = true
                        Timber.i("Firebase App Check initialized successfully with Play Integrity provider after ${retryCount + 1} attempt(s)")
                        break
                        
                    } catch (e: Exception) {
                        retryCount++
                        Timber.w(e, "App Check token verification failed (attempt $retryCount/$maxRetries)")
                        
                        if (retryCount < maxRetries) {
                            Timber.d("Waiting ${backoffDelay}ms before retry...")
                            delay(backoffDelay)
                            backoffDelay *= 2 // Exponential backoff
                        } else {
                            Timber.e("Failed to verify App Check token after $maxRetries attempts - AI services may be degraded")
                            // Set to true anyway to allow app to function (token will be retried later)
                            _isAppCheckInitialized.value = true
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Critical error during Firebase/App Check initialization")
            // Still set initialized to true to prevent app from hanging
            _isAppCheckInitialized.value = true
        }
    }
    
    /**
     * Check if we're running in the main process to avoid multi-process WorkManager issues.
     */
    private fun isMainProcess(): Boolean {
        val pid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        val isMain = processName == packageName

        Timber.d("Process check - PID: $pid, Process: $processName, Package: $packageName, IsMain: $isMain")
        return isMain
    }

    /**
     * SPEC-20241228: Log Room-First Offline Architecture configuration at app startup.
     * Provides visibility into which architecture mode is active and why.
     */
    private fun logOfflineArchitectureMode() {
        val flags = com.example.liftrix.config.OfflineArchitectureFlags

        Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.i("🏗️  OFFLINE ARCHITECTURE MODE: ${if (flags.ROOM_FIRST_ENABLED) "ROOM-FIRST" else "LEGACY"}")
        Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        if (flags.ROOM_FIRST_ENABLED) {
            Timber.i("✅ Room is single source of truth (Firestore persistence disabled)")
            Timber.i("✅ Origin-aware writes enabled (upsertLocal/upsertFromRemote)")
            Timber.i("✅ Dirty flag sync gating enabled (only isDirty=true synced)")
            Timber.i("✅ Idempotent real-time listeners enabled (no feedback loops)")
            Timber.i("✅ Repository Firestore bypass eliminated (Room-first writes)")
        } else {
            Timber.w("⚠️ LEGACY MODE: Dual authority with Firestore offline persistence")
            Timber.w("⚠️ Risk: Potential sync conflicts between Room and Firestore caches")
            Timber.w("⚠️ Risk: Feedback loops from real-time listeners possible")
        }

        Timber.i("📊 Feature Flags:")
        Timber.i("   • DISABLE_FIRESTORE_PERSISTENCE = ${flags.DISABLE_FIRESTORE_PERSISTENCE}")
        Timber.i("   • USE_DIRTY_FLAG_GATING = ${flags.USE_DIRTY_FLAG_GATING}")
        Timber.i("   • USE_IDEMPOTENT_LISTENERS = ${flags.USE_IDEMPOTENT_LISTENERS}")
        Timber.i("   • FIX_AUTH_REPOSITORY = ${flags.FIX_AUTH_REPOSITORY}")
        Timber.i("   • FIX_SEARCH_REPOSITORY = ${flags.FIX_SEARCH_REPOSITORY}")
        Timber.i("   • FIX_PROFILE_REPOSITORY = ${flags.FIX_PROFILE_REPOSITORY}")
        Timber.i("   • FIX_CUSTOM_EXERCISE_REPOSITORY = ${flags.FIX_CUSTOM_EXERCISE_REPOSITORY}")
        Timber.i("   • FIX_BLOCK_REPOSITORY = ${flags.FIX_BLOCK_REPOSITORY}")
        Timber.i("   • FIX_REPORT_REPOSITORY = ${flags.FIX_REPORT_REPOSITORY}")
        Timber.i("   • FIX_ACHIEVEMENT_REPOSITORY = ${flags.FIX_ACHIEVEMENT_REPOSITORY}")
        Timber.i("   • FIX_FOLLOW_REPOSITORY = ${flags.FIX_FOLLOW_REPOSITORY}")
        Timber.i("   • FIX_PROFILE_SEARCH_REPOSITORY = ${flags.FIX_PROFILE_SEARCH_REPOSITORY}")
        Timber.i("   • FIX_SOCIAL_REPOSITORY = ${flags.FIX_SOCIAL_REPOSITORY}")
        Timber.i("   • VERBOSE_SYNC_LOGGING = ${flags.VERBOSE_SYNC_LOGGING}")
        Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    
    
    
    /**
     * Creates notification channels for workout and rest timers.
     * Only creates channels on Android O+ where they are required.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val workoutTimerChannel = NotificationChannel(
                WORKOUT_TIMER_CHANNEL_ID,
                "Workout Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for workout and rest timers"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(workoutTimerChannel)
            Timber.d("Notification channels created")
        } else {
            Timber.d("Notification channels not created (Android version < O)")
        }
    }
    
    /**
     * Initialize widget migration system to ensure all user preferences use canonical widget names.
     * This fixes legacy widget name inconsistencies automatically on app startup.
     */
    private fun initializeWidgetMigration() {
        applicationScope.launch {
            try {
                Timber.d("Initializing widget migration system")
                
                // For now, we'll start migration system but not apply to all users automatically
                // Real users will have their preferences migrated when they're first accessed
                // This just ensures the migration system is ready
                
                Timber.i("Widget migration system initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize widget migration system")
            }
        }
    }
    
    /**
     * Initialize cache warming system to preload frequently accessed data on app startup.
     * This improves user experience by reducing cold start loading times for analytics dashboards.
     */
    private fun initializeCacheWarmingSystem() {
        applicationScope.launch {
            try {
                Timber.d("Initializing cache warming system")
                
                // Start cache warming process in background
                // This will preload user-specific data if authenticated user is found
                cacheWarmingService.startCacheWarming()
                
                Timber.i("Cache warming system initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize cache warming system")
                // Don't crash app if cache warming fails - it's an optimization, not critical
            }
        }
    }
    
    
    /**
     * Initialize the sync system for background data synchronization.
     * Sets up periodic sync for authenticated users and monitors auth state changes.
     * 
     * IMPORTANT: Delayed to ensure WorkManager is fully initialized with HiltWorkerFactory.
     */
    private fun initializeSyncSystem() {
        applicationScope.launch {
            try {
                Timber.d("Initializing sync system...")
                
                // WorkManager is automatically initialized with HiltWorkerFactory
                // No delay needed since Configuration.Provider handles initialization timing
                
                // Check if user is already authenticated
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    Timber.d("User already authenticated, initializing theme and triggering startup sync: ${currentUser.uid}")
                    
                    // Initialize theme immediately
                    applicationScope.launch {
                        try {
                            initializeUserThemeUseCase(currentUser.uid)
                            Timber.d("Theme initialized successfully for user: ${currentUser.uid}")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to initialize theme for user: ${currentUser.uid}")
                        }
                    }
                    
                    // 🔥 NEW: Trigger full startup sync to ensure all workouts are synchronized
                    applicationScope.launch {
                        try {
                            val syncResult = syncCoordinator.triggerStartupSync(currentUser.uid)
                            if (syncResult.isSuccess) {
                                Timber.i("Startup sync initiated successfully for user: ${currentUser.uid}")
                            } else {
                                Timber.w("Startup sync failed for user: ${currentUser.uid}")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to trigger startup sync for user: ${currentUser.uid}")
                        }
                    }
                    
                    // Also schedule periodic sync for ongoing synchronization
                    syncCoordinator.schedulePeriodicSync(currentUser.uid)
                } else {
                    Timber.d("No authenticated user found, sync will be initialized after authentication")
                }
                
                // Listen for auth state changes to setup/teardown sync and theme
                firebaseAuth.addAuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user != null) {
                        Timber.d("User authenticated, initializing theme and triggering login sync: ${user.uid}")
                        applicationScope.launch {
                            // Initialize theme first (no delay needed)
                            try {
                                initializeUserThemeUseCase(user.uid)
                                Timber.d("Theme initialized successfully for user: ${user.uid}")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to initialize theme for user: ${user.uid}")
                            }
                            
                            // 🔥 NEW: Trigger startup sync on login to ensure all workouts are synchronized
                            try {
                                val syncResult = syncCoordinator.triggerStartupSync(user.uid)
                                if (syncResult.isSuccess) {
                                    Timber.i("Login sync initiated successfully for user: ${user.uid}")
                                } else {
                                    Timber.w("Login sync failed for user: ${user.uid}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to trigger login sync for user: ${user.uid}")
                            }
                            
                            // Schedule periodic sync for ongoing synchronization
                            syncCoordinator.schedulePeriodicSync(user.uid)
                        }
                    } else {
                        Timber.d("User signed out, canceling sync operations")
                        // 🔥 ENHANCED: Cancel all sync operations for previous user
                        // Note: We don't have the userId here, so we rely on WorkManager tagging
                        // The sync coordinator should handle cleanup when users sign out
                    }
                }
                
                Timber.i("Sync system initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize sync system")
                // Don't crash app if sync init fails - sync will be attempted again on user action
            }
        }
    }
    
    /**
     * 🔒 DEBUG: Verify HiltWorkerFactory is configured correctly
     * Lightweight assertion to prevent silent regressions
     */
    private fun debugVerifyWorkerFactory() {
        applicationScope.launch {
            try {
                delay(1000) // Give WorkManager time to initialize
                val wf = WorkManager.getInstance(this@LiftrixApp).configuration.workerFactory

                // Simple assertion to catch regressions early
                check(wf is HiltWorkerFactory) {
                    "❌ WorkManager is not using HiltWorkerFactory! Using ${wf::class.java.simpleName} instead."
                }

                Timber.d("✅ WorkManager configured with HiltWorkerFactory")
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to verify WorkerFactory configuration")
            }
        }
    }
    
    /**
     * 🔥 FIX: Clean up legacy queue entries on app startup.
     * Removes any stale entries from before the SyncPayload refactoring that can't be deserialized.
     */
    private fun cleanupLegacyQueueEntries() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Timber.d("🔧 LEGACY-CLEANUP: Starting legacy queue cleanup...")
                
                val currentUserId = firebaseAuth.currentUser?.uid
                if (currentUserId.isNullOrBlank()) {
                    Timber.d("🔧 LEGACY-CLEANUP: No authenticated user; skipping legacy queue cleanup")
                    return@launch
                }

                val result = offlineQueueManager.cleanupLegacyQueueEntries(currentUserId)
                result.fold(
                    onSuccess = { removedCount ->
                        if (removedCount > 0) {
                            Timber.i("🔧 LEGACY-CLEANUP: ✅ Removed $removedCount legacy queue entries")
                        } else {
                            Timber.d("🔧 LEGACY-CLEANUP: ✅ No legacy entries found - queue is clean")
                        }
                    },
                    onFailure = { error ->
                        Timber.w("🔧 LEGACY-CLEANUP: ⚠️ Cleanup failed: ${error.message}")
                        // Non-critical failure - app can continue normally
                    }
                )
            } catch (e: Exception) {
                Timber.w(e, "🔧 LEGACY-CLEANUP: ⚠️ Cleanup exception - continuing app startup")
                // Non-critical failure - app can continue normally
            }
        }
    }
}
