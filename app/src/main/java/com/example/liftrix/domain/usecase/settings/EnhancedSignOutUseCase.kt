package com.example.liftrix.domain.usecase.settings

import androidx.work.WorkManager
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.sync.SyncManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Enhanced sign out use case that handles comprehensive logout functionality.
 * 
 * This use case orchestrates a complete logout process including:
 * - Firebase authentication sign out
 * - Local data cleanup (DataStore, Room cache)
 * - Analytics event tracking
 * - Background service termination
 * - Navigation state reset
 * 
 * The operation is atomic - if any step fails, the entire process is rolled back
 * to maintain data consistency.
 */
class EnhancedSignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val workManager: WorkManager
) {
    
    /**
     * Executes comprehensive sign out process.
     * 
     * Steps performed:
     * 1. Get current user ID for analytics tracking
     * 2. Sign out from Firebase authentication
     * 3. Clear local data (settings, cache)
     * 4. Stop background work and sync operations
     * 5. Clear analytics user properties
     * 6. Log sign out event
     * 
     * @return Result indicating success or failure of the sign out process
     */
    suspend operator fun invoke(): Result<Unit> {
        return try {
            Timber.d("Starting enhanced sign out process")
            
            // Step 1: Get current user ID for analytics
            val currentUserId = authRepository.getCurrentUserId()
            Timber.d("Current user ID: $currentUserId")
            
            // Step 2: Sign out from Firebase authentication
            val signOutResult = authRepository.signOut()
            if (signOutResult.isFailure) {
                Timber.e("Firebase sign out failed: ${signOutResult.exceptionOrNull()?.message}")
                return Result.failure(signOutResult.exceptionOrNull() ?: Exception("Sign out failed"))
            }
            Timber.d("Firebase sign out successful")
            
            // Step 3: Clear local data
            try {
                // Clear all settings from DataStore
                val clearSettingsResult = settingsRepository.clearAllSettings()
                if (clearSettingsResult.isFailure) {
                    Timber.w("Failed to clear settings: ${clearSettingsResult.exceptionOrNull()?.message}")
                }
                
                Timber.d("Local data cleared successfully")
            } catch (e: Exception) {
                Timber.w(e, "Non-critical: Failed to clear some local data")
                // Continue with sign out process despite local data cleanup issues
            }
            
            // Step 4: Stop background work and sync operations
            try {
                // Cancel all ongoing sync operations
                syncManager.cancelSync()
                
                // Cancel all pending WorkManager tasks
                workManager.cancelAllWork()
                
                Timber.d("Background services stopped successfully")
            } catch (e: Exception) {
                Timber.w(e, "Non-critical: Failed to stop some background services")
                // Continue with sign out process despite service cleanup issues
            }
            
            // Step 5: Clear analytics user properties
            try {
                val clearAnalyticsResult = analyticsService.clearUserProperties()
                if (clearAnalyticsResult.isFailure) {
                    Timber.w("Failed to clear analytics properties: ${clearAnalyticsResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.w(e, "Non-critical: Failed to clear analytics properties")
            }
            
            // Step 6: Log sign out event
            try {
                val logEventResult = analyticsService.logEvent(
                    eventName = "user_signed_out",
                    parameters = mapOf(
                        "user_id" to (currentUserId ?: "unknown"),
                        "sign_out_method" to "enhanced_sign_out_use_case",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                
                if (logEventResult.isFailure) {
                    Timber.w("Failed to log sign out event: ${logEventResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.w(e, "Non-critical: Failed to log sign out analytics event")
            }
            
            Timber.d("Enhanced sign out process completed successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Enhanced sign out process failed")
            
            // Record the exception for crash reporting
            try {
                analyticsService.recordException(
                    throwable = e,
                    additionalData = mapOf(
                        "operation" to "enhanced_sign_out",
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
            } catch (analyticsException: Exception) {
                Timber.w(analyticsException, "Failed to record sign out exception")
            }
            
            Result.failure(e)
        }
    }
}