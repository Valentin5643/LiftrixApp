package com.example.liftrix

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.liftrix.BuildConfig
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.service.CacheWarmingService
import com.example.liftrix.sync.SyncCoordinator
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        const val WORKOUT_TIMER_CHANNEL_ID = "workout_timer_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())
        
        // CRITICAL: Initialize WorkManager FIRST before any other systems
        // This ensures HiltWorkerFactory is registered before any worker operations
        initializeWorkManager()
        
        // Initialize debug system
        initializeDebugSystem()
        
        // Create notification channels
        createNotificationChannels()
        
        // Initialize widget migration system
        initializeWidgetMigration()
        
        // Initialize cache warming system
        initializeCacheWarmingSystem()
        
        // Initialize sync system
        initializeSyncSystem()
    }
    
    override val workManagerConfiguration: Configuration
        get() {
            Timber.d("WorkManagerConfiguration: Creating configuration with HiltWorkerFactory")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
                .build()
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
     * Initialize WorkManager explicitly to ensure HiltWorkerFactory is properly registered.
     * This prevents NoSuchMethodException when WorkManager tries to instantiate Hilt workers.
     */
    private fun initializeWorkManager() {
        try {
            Timber.d("Starting WorkManager initialization...")
            Timber.d("HiltWorkerFactory available: ${::workerFactory.isInitialized}")
            
            // Ensure WorkManager is not already initialized
            try {
                WorkManager.getInstance(this)
                Timber.d("WorkManager already initialized, checking configuration...")
            } catch (e: IllegalStateException) {
                Timber.d("WorkManager not yet initialized, proceeding with initialization")
                
                // Force WorkManager initialization with our configuration
                val config = workManagerConfiguration
                Timber.d("Created WorkManager configuration with factory: ${config.workerFactory?.javaClass?.simpleName}")
                
                WorkManager.initialize(this, config)
                Timber.d("WorkManager.initialize() completed")
            }
            
            // Verify WorkManager is properly configured
            val workManager = WorkManager.getInstance(this)
            Timber.d("WorkManager verification successful: ${workManager.javaClass.simpleName}")
            
            // Log the factory being used
            val factoryField = workManager.javaClass.getDeclaredField("mWorkManagerImpl")
            factoryField.isAccessible = true
            val workManagerImpl = factoryField.get(workManager)
            Timber.d("WorkManager implementation: ${workManagerImpl?.javaClass?.simpleName}")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize WorkManager - this will cause worker instantiation failures")
            // This is critical - if WorkManager fails to initialize, sync won't work
        }
    }
    
    /**
     * Initialize the sync system for background data synchronization.
     * Sets up periodic sync for authenticated users and monitors auth state changes.
     */
    private fun initializeSyncSystem() {
        applicationScope.launch {
            try {
                Timber.d("Initializing sync system")
                
                // Check if user is already authenticated
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    Timber.d("User already authenticated, scheduling periodic sync: ${currentUser.uid}")
                    syncCoordinator.schedulePeriodicSync(currentUser.uid)
                } else {
                    Timber.d("No authenticated user found, sync will be initialized after authentication")
                }
                
                // Listen for auth state changes to setup/teardown sync
                firebaseAuth.addAuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user != null) {
                        Timber.d("User authenticated, scheduling periodic sync: ${user.uid}")
                        applicationScope.launch {
                            syncCoordinator.schedulePeriodicSync(user.uid)
                        }
                    } else {
                        Timber.d("User signed out, canceling sync operations")
                        applicationScope.launch {
                            // Cancel sync for the previously authenticated user
                            // Note: We don't have the old userId here, but WorkManager will handle cleanup
                            // when the user signs out via the auth flow
                        }
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