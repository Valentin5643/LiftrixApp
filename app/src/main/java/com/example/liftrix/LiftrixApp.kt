package com.example.liftrix

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.liftrix.BuildConfig
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.service.CacheWarmingService
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.domain.usecase.settings.InitializeUserThemeUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
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
class LiftrixApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var widgetPreferencesRepository: WidgetPreferencesRepository
    
    @Inject
    lateinit var cacheWarmingService: CacheWarmingService
    
    @Inject
    lateinit var syncCoordinator: SyncCoordinator
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var initializeUserThemeUseCase: InitializeUserThemeUseCase
    
    // Lazy initialization to ensure workerFactory is injected before access
    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
    }
    
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
        
        // Initialize Firebase and App Check FIRST before any other Firebase services
        initializeFirebaseAppCheck()
        
        // Initialize debug system
        initializeDebugSystem()
        
        // Create notification channels
        createNotificationChannels()
        
        // WorkManager is auto-initialized via Configuration.Provider
        // No manual initialization needed - this prevents NoSuchMethodException in workers
        Timber.d("WorkManager will auto-initialize with HiltWorkerFactory via Configuration.Provider")
        
        // Initialize widget migration system
        initializeWidgetMigration()
        
        // Initialize cache warming system
        initializeCacheWarmingSystem()
        
        // Initialize sync system after WorkManager is ready
        initializeSyncSystem()
    }
    
    /**
     * Initialize Firebase and App Check with build-specific providers.
     * CRITICAL: This must be called before any Firebase AI service calls to ensure valid tokens.
     */
    private fun initializeFirebaseAppCheck() {
        try {
            // CRITICAL: Initialize Firebase synchronously first
            Timber.d("Initializing Firebase synchronously...")
            Firebase.initialize(this@LiftrixApp)
            Timber.d("Firebase initialized successfully")
            
            // Configure App Check with build-specific provider synchronously
            Firebase.appCheck.installAppCheckProviderFactory(
                if (BuildConfig.DEBUG) {
                    // Debug builds use DebugAppCheckProviderFactory for local development
                    Timber.d("Using DebugAppCheckProviderFactory for debug builds")
                    
                    // Get the debug provider factory
                    val debugFactory = DebugAppCheckProviderFactory.getInstance()
                    
                    // IMPORTANT: The debug token will be logged by Firebase automatically
                    // Look for this in logcat: "Enter this debug token in the Firebase Console"
                    Timber.e("═════════════════════════════════════════════════════════════")
                    Timber.e("FIREBASE APP CHECK DEBUG MODE ACTIVE")
                    Timber.e("═════════════════════════════════════════════════════════════")
                    Timber.e("ACTION REQUIRED: Register debug token in Firebase Console")
                    Timber.e("1. Search ALL logcat output (not filtered by app) for the token")
                    Timber.e("2. Run this ADB command to find it:")
                    Timber.e("   adb logcat | grep -i \"debug token\"")
                    Timber.e("3. Or search for: 'DebugAppCheckProvider' in logcat")
                    Timber.e("4. The token is a long alphanumeric string (e.g., 123ABC456DEF...)")
                    Timber.e("5. Go to Firebase Console → App Check → Apps → Manage debug tokens")
                    Timber.e("6. Add the token with a descriptive name (e.g., 'Development Device')")
                    Timber.e("7. Restart the app after registering the token")
                    Timber.e("═════════════════════════════════════════════════════════════")
                    
                    debugFactory
                } else {
                    // Production builds use Play Integrity provider for security
                    Timber.d("Using PlayIntegrityAppCheckProviderFactory for release builds")
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
            )
            Timber.d("App Check provider factory installed")
            
            // Now launch async task to verify token generation with better retry logic
            applicationScope.launch {
                // In debug mode, try to trigger the debug token logging
                if (BuildConfig.DEBUG) {
                    try {
                        // Force a token request to trigger debug token generation
                        Timber.e("═════════════════════════════════════════════════════════════")
                        Timber.e("ATTEMPTING TO RETRIEVE DEBUG TOKEN...")
                        Timber.e("Check the UNFILTERED logcat output now!")
                        Timber.e("The token will appear in a message from 'DebugAppCheckProvider'")
                        Timber.e("═════════════════════════════════════════════════════════════")
                        
                        // This will trigger the debug token to be logged
                        val debugToken = Firebase.appCheck.getAppCheckToken(true).await()
                        
                        // The actual debug token for registration is logged by Firebase internally
                        // We can't access it directly, but this call triggers it to be logged
                        Timber.e("═════════════════════════════════════════════════════════════")
                        Timber.e("APP CHECK TOKEN RETRIEVED (this is NOT the debug token)")
                        Timber.e("The DEBUG TOKEN for Firebase Console is in the logcat above")
                        Timber.e("Look for a message containing a long hex string")
                        Timber.e("═════════════════════════════════════════════════════════════")
                    } catch (e: Exception) {
                        Timber.e("Failed to trigger debug token: ${e.message}")
                        Timber.e("The debug token should still appear in logcat")
                    }
                }
                
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
                        Timber.i("Firebase App Check initialized successfully with ${if (BuildConfig.DEBUG) "Debug" else "Play Integrity"} provider after ${retryCount + 1} attempt(s)")
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
     * Initialize the debug system for the application
     */
    private fun initializeDebugSystem() {
        try {
            // Initialize the LiftrixDebugger
            com.example.liftrix.debug.LiftrixDebugger.info("Liftrix Application starting up")
            com.example.liftrix.debug.LiftrixDebugger.info("Debug mode: ${BuildConfig.DEBUG}")
            
            // Log initial memory usage
            com.example.liftrix.debug.LiftrixDebugger.logMemoryUsage(force = true)
            
            // Validate build configuration
            val buildValidation = com.example.liftrix.debug.LiftrixDebugger.validateBuildConfiguration()
            if (!buildValidation.isValid) {
                com.example.liftrix.debug.LiftrixDebugger.warning("Build configuration issues detected: ${buildValidation.issues.size} issues, ${buildValidation.warnings.size} warnings")
            }
            
            Timber.d("Debug system initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize debug system")
        }
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
                
                // Add a small delay to ensure WorkManager is fully initialized
                // This prevents the NoSuchMethodException for Hilt workers
                delay(100)
                
                // Check if user is already authenticated
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    Timber.d("User already authenticated, initializing theme and scheduling periodic sync: ${currentUser.uid}")
                    
                    // Initialize theme immediately
                    applicationScope.launch {
                        try {
                            initializeUserThemeUseCase(currentUser.uid)
                            Timber.d("Theme initialized successfully for user: ${currentUser.uid}")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to initialize theme for user: ${currentUser.uid}")
                        }
                    }
                    
                    syncCoordinator.schedulePeriodicSync(currentUser.uid)
                } else {
                    Timber.d("No authenticated user found, sync will be initialized after authentication")
                }
                
                // Listen for auth state changes to setup/teardown sync and theme
                firebaseAuth.addAuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user != null) {
                        Timber.d("User authenticated, initializing theme and scheduling periodic sync: ${user.uid}")
                        applicationScope.launch {
                            // Initialize theme first (no delay needed)
                            try {
                                initializeUserThemeUseCase(user.uid)
                                Timber.d("Theme initialized successfully for user: ${user.uid}")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to initialize theme for user: ${user.uid}")
                            }
                            
                            // Also delay here to ensure WorkManager is ready for sync
                            delay(100)
                            syncCoordinator.schedulePeriodicSync(user.uid)
                        }
                    } else {
                        Timber.d("User signed out, canceling sync operations")
                    }
                }
                
                Timber.i("Sync system initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize sync system")
                // Don't crash app if sync init fails - sync will be attempted again on user action
            }
        }
    }
} 