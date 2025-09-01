package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.SyncPreferencesDao
import com.example.liftrix.data.local.entity.SyncPreferencesEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.SyncPreferences
import com.example.liftrix.domain.repository.SyncPreferencesRepository
import com.example.liftrix.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SyncPreferencesRepository with Room database persistence.
 * Part of Phase 2: Sync Infrastructure Enhancement from SPEC-20250901-todo-implementation.
 * 
 * Features:
 * - User-scoped sync preferences with mandatory userId filtering
 * - Auto-sync toggle functionality with proper error handling
 * - Integration with SyncStatusRepository for sync coordination
 * - Offline-first approach with Room as source of truth
 * - Comprehensive analytics tracking for sync operations
 */
@Singleton
class SyncPreferencesRepositoryImpl @Inject constructor(
    private val syncPreferencesDao: SyncPreferencesDao,
    private val syncStatusRepository: SyncStatusRepository
) : SyncPreferencesRepository {
    
    override suspend fun getAutoSyncEnabled(userId: String): LiftrixResult<Boolean> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "AUTO_SYNC_STATUS_FETCH_FAILED",
                    errorMessage = "Failed to get auto sync status",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            syncPreferencesDao.getAutoSyncEnabled(userId) ?: true // Default to enabled
        }
    
    override suspend fun setAutoSyncEnabled(userId: String, enabled: Boolean): LiftrixResult<Unit> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "AUTO_SYNC_UPDATE_FAILED",
                    errorMessage = "Failed to update auto sync preference",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "enabled" to enabled.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            // Get existing preferences or create default
            val existingPrefs = syncPreferencesDao.getSyncPreferences(userId)
            val updatedPrefs = if (existingPrefs != null) {
                existingPrefs.copy(
                    autoSyncEnabled = enabled,
                    lastModified = System.currentTimeMillis()
                )
            } else {
                SyncPreferencesEntity(
                    userId = userId,
                    autoSyncEnabled = enabled,
                    lastModified = System.currentTimeMillis()
                )
            }
            
            syncPreferencesDao.upsertSyncPreferences(updatedPrefs)
            
            Timber.d("SyncPreferencesRepositoryImpl: Updated auto sync for user $userId to $enabled")
        }
    
    override fun observeAutoSyncStatus(userId: String): Flow<Boolean> {
        return syncPreferencesDao.observeAutoSyncEnabled(userId).map { enabled ->
            enabled ?: true // Default to enabled if no preference found
        }
    }
    
    override suspend fun getLastSyncTime(userId: String): LiftrixResult<Long?> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "LAST_SYNC_TIME_FETCH_FAILED",
                    errorMessage = "Failed to get last sync time",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            syncPreferencesDao.getLastSyncTimestamp(userId)
        }
    
    override suspend fun updateLastSyncTime(userId: String, timestamp: Long): LiftrixResult<Unit> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "LAST_SYNC_TIME_UPDATE_FAILED",
                    errorMessage = "Failed to update last sync time",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "timestamp" to timestamp.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(timestamp > 0) { "Timestamp must be positive" }
            
            syncPreferencesDao.updateLastSyncTimestamp(userId, timestamp)
            
            Timber.d("SyncPreferencesRepositoryImpl: Updated last sync time for user $userId to $timestamp")
        }
    
    override suspend fun getSyncInterval(userId: String): LiftrixResult<Long> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SYNC_INTERVAL_FETCH_FAILED",
                    errorMessage = "Failed to get sync interval",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            val preferences = syncPreferencesDao.getSyncPreferences(userId)
            preferences?.syncIntervalMinutes ?: 15L // Default to 15 minutes
        }
    
    override suspend fun setSyncInterval(userId: String, intervalMinutes: Long): LiftrixResult<Unit> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SYNC_INTERVAL_UPDATE_FAILED",
                    errorMessage = "Failed to update sync interval",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "interval_minutes" to intervalMinutes.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(intervalMinutes >= 5) { "Sync interval must be at least 5 minutes" }
            require(intervalMinutes <= 1440) { "Sync interval cannot exceed 24 hours" }
            
            syncPreferencesDao.updateSyncInterval(userId, intervalMinutes)
            
            Timber.d("SyncPreferencesRepositoryImpl: Updated sync interval for user $userId to $intervalMinutes minutes")
        }
    
    override suspend fun getSyncPreferences(userId: String): LiftrixResult<SyncPreferences> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SYNC_PREFERENCES_FETCH_FAILED",
                    errorMessage = "Failed to get sync preferences",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            val entity = syncPreferencesDao.getSyncPreferences(userId)
            
            if (entity != null) {
                SyncPreferences(
                    autoSyncEnabled = entity.autoSyncEnabled,
                    syncIntervalMinutes = entity.syncIntervalMinutes,
                    syncOnWifiOnly = entity.syncOnWifiOnly,
                    syncOnBatterySaver = entity.syncOnBatterySaver,
                    lastSyncTimestamp = entity.lastSyncTimestamp,
                    syncWorkoutData = entity.syncWorkoutData,
                    syncProfileData = entity.syncProfileData,
                    syncSocialData = entity.syncSocialData,
                    syncSettings = entity.syncSettings
                )
            } else {
                // Return default preferences if none found
                SyncPreferences()
            }
        }
    
    override suspend fun updateSyncPreferences(userId: String, preferences: SyncPreferences): LiftrixResult<Unit> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SYNC_PREFERENCES_UPDATE_FAILED",
                    errorMessage = "Failed to update sync preferences",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            val entity = SyncPreferencesEntity(
                userId = userId,
                autoSyncEnabled = preferences.autoSyncEnabled,
                syncIntervalMinutes = preferences.syncIntervalMinutes,
                syncOnWifiOnly = preferences.syncOnWifiOnly,
                syncOnBatterySaver = preferences.syncOnBatterySaver,
                lastSyncTimestamp = preferences.lastSyncTimestamp,
                syncWorkoutData = preferences.syncWorkoutData,
                syncProfileData = preferences.syncProfileData,
                syncSocialData = preferences.syncSocialData,
                syncSettings = preferences.syncSettings,
                lastModified = System.currentTimeMillis()
            )
            
            syncPreferencesDao.upsertSyncPreferences(entity)
            
            Timber.d("SyncPreferencesRepositoryImpl: Updated sync preferences for user $userId")
        }
    
    override suspend fun getUsersDueForSync(currentTimestamp: Long): LiftrixResult<List<String>> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "USERS_DUE_FOR_SYNC_FETCH_FAILED",
                    errorMessage = "Failed to get users due for sync",
                    analyticsContext = mapOf("current_timestamp" to currentTimestamp.toString())
                )
            }
        ) {
            require(currentTimestamp > 0) { "Current timestamp must be positive" }
            
            syncPreferencesDao.getUsersDueForSync(currentTimestamp)
        }
    
    override suspend fun getUsersWithAutoSyncEnabled(): LiftrixResult<List<String>> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "AUTO_SYNC_USERS_FETCH_FAILED",
                    errorMessage = "Failed to get users with auto sync enabled"
                )
            }
        ) {
            syncPreferencesDao.getUsersWithAutoSyncEnabled()
        }
    
    override suspend fun shouldUserSync(
        userId: String, 
        isWifiConnected: Boolean,
        isBatterySaverOn: Boolean
    ): LiftrixResult<Boolean> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "SHOULD_SYNC_CHECK_FAILED",
                    errorMessage = "Failed to check if user should sync",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "wifi_connected" to isWifiConnected.toString(),
                        "battery_saver" to isBatterySaverOn.toString()
                    )
                )
            }
        ) {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            
            val preferences = getSyncPreferences(userId).fold(
                onSuccess = { it },
                onFailure = { return@liftrixCatching false }
            )
            
            // Check if auto sync is enabled
            if (!preferences.autoSyncEnabled) {
                return@liftrixCatching false
            }
            
            // Check WiFi requirement
            if (preferences.syncOnWifiOnly && !isWifiConnected) {
                return@liftrixCatching false
            }
            
            // Check battery saver requirement
            if (!preferences.syncOnBatterySaver && isBatterySaverOn) {
                return@liftrixCatching false
            }
            
            // Check if enough time has passed since last sync
            val lastSync = preferences.lastSyncTimestamp
            if (lastSync != null) {
                val timeSinceLastSync = System.currentTimeMillis() - lastSync
                val intervalMs = preferences.syncIntervalMinutes * 60 * 1000
                if (timeSinceLastSync < intervalMs) {
                    return@liftrixCatching false
                }
            }
            
            true
        }
}