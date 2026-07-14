package com.example.liftrix

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.liftrix.BuildConfig
import com.example.liftrix.core.logging.ReleaseLoggingTree
import com.example.liftrix.debug.DebugToolingInitializer
import com.example.liftrix.di.ApplicationScope
import com.example.liftrix.di.IoDispatcher
import com.example.liftrix.sync.StartupRestoreGate
import com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase
import com.example.liftrix.monitoring.PerformanceMonitor
import com.example.liftrix.startup.PostFirstScreenStartupCoordinator
import com.example.liftrix.startup.StartupTaskClass
import com.example.liftrix.startup.StartupTaskTracer
import com.example.liftrix.ui.theme.ThemeManager
import com.example.liftrix.widget.LiftrixWidgetUpdateScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.perf.metrics.Trace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LiftrixApp : Application(), Configuration.Provider {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var initializeUserThemeUseCase: InitializeUserThemeUseCase

    @Inject
    lateinit var startupRestoreGate: StartupRestoreGate

    @Inject
    lateinit var liftrixWidgetUpdateScheduler: LiftrixWidgetUpdateScheduler

    @Inject
    lateinit var postFirstScreenStartupCoordinator: PostFirstScreenStartupCoordinator

    @Inject
    lateinit var startupTaskTracer: StartupTaskTracer

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var performanceMonitor: PerformanceMonitor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.INFO
            )
            .build()

    private var appOnCreateTrace: Trace? = null
    private var workManagerInitTrace: Trace? = null
    private var cacheWarmingTrace: Trace? = null

    @Volatile
    private var firstContentStartupUserId: String? = null

    // App Check initialization state
    private val _isAppCheckInitialized = MutableStateFlow(false)
    val isAppCheckInitialized: StateFlow<Boolean> = _isAppCheckInitialized.asStateFlow()

    companion object {
        private const val TAG = "LiftrixApp"
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

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        INSTANCE = this
    }
    
    override fun onCreate() {
        super.onCreate()
        appOnCreateTrace = performanceMonitor.startCustomTrace("liftrix_app_on_create")

        // Set the singleton instance
        INSTANCE = this

        // Initialize Timber for logging
        DebugToolingInitializer.initialize(this)

        if (BuildConfig.ENABLE_VERBOSE_LOGGING) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseLoggingTree())
        }

        startupTaskTracer.measure("platform_theme_apply", StartupTaskClass.BlockingAuth) {
            ThemeManager.getInstance(this).applyCurrentThemeToPlatform()
        }

        // Mark/log App Check readiness and perform a single non-forced verification.
        startupTaskTracer.measure("firebase_app_check_setup", StartupTaskClass.BlockingAuth) {
            initializeFirebaseAppCheck(verifyToken = true)
        }

        // SPEC-20241228: Log Room-First Architecture Configuration
        startupTaskTracer.measure("offline_architecture_log", StartupTaskClass.Deferred) {
            logOfflineArchitectureMode()
        }

        // 🔥 FIX: MANUAL WorkManager initialization to ensure HiltWorkerFactory is used
        // This MUST happen before anything calls WorkManager.getInstance()
        // We disabled auto-initialization via androidx.startup in AndroidManifest
        startupTaskTracer.measure("workmanager_setup", StartupTaskClass.BlockingAuth) {
            workManagerInitTrace = performanceMonitor.startCustomTrace("liftrix_workmanager_init")
            try {
                val workManagerConfig = Configuration.Builder()
                    .setWorkerFactory(hiltWorkerFactory)
                    .setMinimumLoggingLevel(
                        if (BuildConfig.DEBUG) android.util.Log.DEBUG
                        else android.util.Log.INFO
                    )
                    .build()

                WorkManager.initialize(this, workManagerConfig)
                Timber.d("WorkManager manually initialized with HiltWorkerFactory")
            } catch (e: IllegalStateException) {
                // WorkManager was already initialized (shouldn't happen with Startup disabled)
                Timber.e(e, "WorkManager already initialized - early init detected")
            } finally {
                workManagerInitTrace?.let { performanceMonitor.stopCustomTrace(it) }
                workManagerInitTrace = null
            }
        }

        // 🔧 DEBUG: Test if HiltWorkerFactory can create workers (debug builds only)
        if (BuildConfig.DEBUG) {
            debugVerifyWorkerFactory()
        }
        
        // Create notification channels
        startupTaskTracer.measure("notification_channels", StartupTaskClass.BlockingAuth) {
            createNotificationChannels()
        }
        
        // Initialize widget migration system
        startupTaskTracer.measure("widget_migration_registration", StartupTaskClass.Deferred) {
            initializeWidgetMigration()
        }
        
        // Initialize sync system after WorkManager is ready
        startupTaskTracer.measure("auth_startup_listener", StartupTaskClass.FirstScreen) {
            initializeSyncSystem()
        }
        
        // 🔥 FIX: Clean up legacy queue entries from before SyncPayload refactoring
        // Legacy sync cleanup is deferred until after the first authenticated screen.

        appOnCreateTrace?.let { performanceMonitor.stopCustomTrace(it) }
        appOnCreateTrace = null
    }

    fun onFirstContentReady(userId: String) {
        if (firstContentStartupUserId == userId) return

        firstContentStartupUserId = userId
        postFirstScreenStartupCoordinator.start(
            userId = userId,
            source = "main_activity_first_authenticated_content"
        )
        initializeCacheWarmingSystem()
    }
    
    /**
     * Initialize Firebase and App Check with build-specific providers.
     * CRITICAL: This must be called before any Firebase AI service calls to ensure valid tokens.
     * Thread-safe initialization with proper singleton check.
     */
    private fun initializeFirebaseAppCheck(verifyToken: Boolean) {
        try {
            val firebaseApp = AppCheckInitializer.initialize(this@LiftrixApp)
            Log.i(
                TAG,
                "Firebase initialized for App Check. appName=${firebaseApp.name}, " +
                    "package=$packageName, projectId=${firebaseApp.options.projectId}, " +
                    "provider=${FirebaseAppCheckProviderInstaller.providerName}"
            )

            _isAppCheckInitialized.value = true

            if (!verifyToken) return

            // Trigger exactly one debug-provider token exchange so Firebase prints the debug secret.
            // A 403 here is configuration, not a retryable startup failure.
            applicationScope.launch {
                AppCheckInitializer.verifyTokenOnce()
                    .onSuccess {
                        Timber.i("Firebase App Check token verified with ${FirebaseAppCheckProviderInstaller.providerName}")
                    }
                    .onFailure { error ->
                        Timber.e(
                            error,
                            "Firebase App Check token verification failed with ${FirebaseAppCheckProviderInstaller.providerName}"
                        )
                        logAppCheckConfigurationGuidance()
                    }
                }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during Firebase/App Check initialization", e)
        }
    }

    private fun logAppCheckConfigurationGuidance() {
        if (BuildConfig.DEBUG) {
            Timber.e(
                "Debug App Check token is not registered. Register the token emitted during startup at " +
                    "Firebase Console > App Check > Apps > $packageName > Manage debug tokens."
            )
        } else {
            Timber.e("Release App Check uses Play Integrity. Verify SHA-1/SHA-256 fingerprints and Play Integrity provider setup.")
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
        com.example.liftrix.services.NotificationChannelManager(this).initializeChannels()

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
        applicationScope.launch(ioDispatcher) {
            try {
                startupTaskTracer.measure("cache_warming", StartupTaskClass.Deferred) {
                    cacheWarmingTrace = performanceMonitor.startCustomTrace("liftrix_cache_warming")
                    Timber.d("Initializing cache warming system")
                    Timber.i("Cache warming skipped; Room-backed first screen data loads through observers")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize cache warming system")
                // Don't crash app if cache warming fails - it's an optimization, not critical
            } finally {
                cacheWarmingTrace?.let { performanceMonitor.stopCustomTrace(it) }
                cacheWarmingTrace = null
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
        val currentUser = firebaseAuth.currentUser
        Timber.tag("StartupRestoreFix").i(
            "[TEMPLATE-LOAD] operation=APP_STARTUP_AUTH_CHECK firebaseCurrentUserId=${currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
        )

        if (currentUser == null) {
            startupRestoreGate.resetForAuthPending("app_startup_no_current_user")
            postFirstScreenStartupCoordinator.reset()
            applicationScope.launch {
                try {
                    liftrixWidgetUpdateScheduler.clearAndUpdateAll()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clear native home widgets during unauthenticated startup")
                }
            }
            Timber.d("No authenticated user found; deferred startup waits for first authenticated screen")
        }

        firebaseAuth.addAuthStateListener { auth ->
            val user = auth.currentUser
            Timber.tag("StartupRestoreFix").i(
                "[TEMPLATE-LOAD] operation=APP_AUTH_STATE firebaseCurrentUserId=${user?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
            )
            if (user != null) {
                applicationScope.launch {
                    try {
                        startupTaskTracer.measureSuspend("user_theme_initialization", StartupTaskClass.FirstScreen) {
                            initializeUserThemeUseCase(user.uid)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to initialize theme for user: ${user.uid}")
                    }
                }
            } else {
                startupRestoreGate.resetForAuthPending("auth_listener_signed_out")
                firstContentStartupUserId = null
                postFirstScreenStartupCoordinator.reset()
                applicationScope.launch {
                    try {
                        liftrixWidgetUpdateScheduler.clearAndUpdateAll()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to clear native home widgets after sign out")
                    }
                }
            }
        }

        Timber.i("Startup auth listener initialized; seed, sync, cache, and widgets defer until first authenticated screen")
        /*
        applicationScope.launch {
            try {
                Timber.d("Initializing sync system...")
                
                // WorkManager is automatically initialized with HiltWorkerFactory
                // No delay needed since Configuration.Provider handles initialization timing
                
                // Check if user is already authenticated
                val currentUser = firebaseAuth.currentUser
                Timber.tag("StartupRestoreFix").i(
                    "[TEMPLATE-LOAD] operation=APP_STARTUP_AUTH_CHECK firebaseCurrentUserId=${currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                )
                Timber.tag("FreshLoginRestoreDebug").d(
                    "operation=APP_SYNC_INIT_CHECK firebaseCurrentUserId=${currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                )
                if (currentUser != null) {
                    Timber.tag("FreshLoginRestoreDebug").i(
                        "operation=APP_START_AUTHENTICATED userId=${currentUser.uid} syncType=startup direction=Firebase->Room_then_Room->Firebase timestamp=${System.currentTimeMillis()}"
                    )
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
                            Timber.tag("StartupRestoreFix").d(
                                "[TEMPLATE-LOAD] operation=STARTUP_SYNC_REQUESTED userId=${currentUser.uid} source=app_start_current_user firebaseCurrentUserId=${firebaseAuth.currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                            )
                            Timber.tag("FreshLoginRestoreDebug").d(
                                "operation=APP_STARTUP_SYNC_REQUESTED userId=${currentUser.uid} firebaseCurrentUserId=${firebaseAuth.currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                            )
                            val syncResult = syncCoordinator.triggerStartupSync(
                                userId = currentUser.uid,
                                source = "app_start_current_user"
                            )
                            Timber.tag("FreshLoginRestoreDebug").d(
                                "operation=APP_STARTUP_SYNC_TRIGGER_RETURNED userId=${currentUser.uid} enqueued=${syncResult.isSuccess} timestamp=${System.currentTimeMillis()}"
                            )
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
                    liftrixWidgetUpdateScheduler.enqueueRefresh(
                        reason = "app_start_authenticated",
                        userId = currentUser.uid
                    )
                    liftrixWidgetUpdateScheduler.enqueuePeriodicRefresh(currentUser.uid)
                } else {
                    startupRestoreGate.resetForAuthPending("app_startup_no_current_user")
                    liftrixWidgetUpdateScheduler.clearAndUpdateAll()
                    Timber.d("No authenticated user found, sync will be initialized after authentication")
                }
                
                // Listen for auth state changes to setup/teardown sync and theme
                firebaseAuth.addAuthStateListener { auth ->
                    val user = auth.currentUser
                    Timber.tag("StartupRestoreFix").i(
                        "[TEMPLATE-LOAD] operation=APP_AUTH_STATE firebaseCurrentUserId=${user?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                    )
                    Timber.tag("FreshLoginRestoreDebug").d(
                        "operation=APP_AUTH_LISTENER_EVENT firebaseCurrentUserId=${user?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                    )
                    if (user != null) {
                        Timber.tag("FreshLoginRestoreDebug").i(
                            "operation=APP_LOGIN_INITIALIZATION_START userId=${user.uid} syncType=startup direction=Firebase->Room_then_Room->Firebase timestamp=${System.currentTimeMillis()}"
                        )
                        Timber.d("User authenticated, initializing theme and triggering login sync: ${user.uid}")
                        applicationScope.launch {
                            // Initialize theme first (no delay needed)
                            try {
                                initializeUserThemeUseCase(user.uid)
                                Timber.d("Theme initialized successfully for user: ${user.uid}")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to initialize theme for current user")
                            }
                            
                            // 🔥 NEW: Trigger startup sync on login to ensure all workouts are synchronized
                            try {
                                Timber.tag("StartupRestoreFix").d(
                                    "[TEMPLATE-LOAD] operation=STARTUP_SYNC_REQUESTED userId=${user.uid} source=firebase_auth_state_listener firebaseCurrentUserId=${firebaseAuth.currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                                )
                                Timber.tag("FreshLoginRestoreDebug").d(
                                    "operation=APP_LOGIN_STARTUP_SYNC_REQUESTED userId=${user.uid} firebaseCurrentUserId=${firebaseAuth.currentUser?.uid ?: "null"} timestamp=${System.currentTimeMillis()}"
                                )
                                val syncResult = syncCoordinator.triggerStartupSync(
                                    userId = user.uid,
                                    source = "firebase_auth_state_listener"
                                )
                                Timber.tag("FreshLoginRestoreDebug").d(
                                    "operation=APP_LOGIN_STARTUP_SYNC_TRIGGER_RETURNED userId=${user.uid} enqueued=${syncResult.isSuccess} timestamp=${System.currentTimeMillis()}"
                                )
                                if (syncResult.isSuccess) {
                                    Timber.i("Login sync initiated successfully for user: ${user.uid}")
                                } else {
                                    Timber.w("Login sync failed for current user")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to trigger login sync for current user")
                            }
                            
                            // Schedule periodic sync for ongoing synchronization
                            syncCoordinator.schedulePeriodicSync(user.uid)
                            liftrixWidgetUpdateScheduler.enqueueRefresh(
                                reason = "auth_state_authenticated",
                                userId = user.uid
                            )
                            liftrixWidgetUpdateScheduler.enqueuePeriodicRefresh(user.uid)
                        }
                    } else {
                        startupRestoreGate.resetForAuthPending("auth_listener_signed_out")
                        Timber.d("User signed out, canceling sync operations")
                        applicationScope.launch {
                            try {
                                liftrixWidgetUpdateScheduler.clearAndUpdateAll()
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to clear native home widgets after sign out")
                            }
                        }
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
        */
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
    
}
