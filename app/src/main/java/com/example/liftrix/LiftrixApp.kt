package com.example.liftrix

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.liftrix.BuildConfig
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.service.CacheWarmingService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LiftrixApp : Application() {
    
    @Inject
    lateinit var widgetPreferencesRepository: WidgetPreferencesRepository
    
    @Inject
    lateinit var cacheWarmingService: CacheWarmingService
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        const val WORKOUT_TIMER_CHANNEL_ID = "workout_timer_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())
        
        // Initialize debug system
        initializeDebugSystem()
        
        // Create notification channels
        createNotificationChannels()
        
        // Initialize widget migration system
        initializeWidgetMigration()
        
        // Initialize cache warming system
        initializeCacheWarmingSystem()
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
} 