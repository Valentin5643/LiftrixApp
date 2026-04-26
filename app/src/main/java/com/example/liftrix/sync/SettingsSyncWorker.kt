package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.local.dao.SettingsAuditDao
import com.example.liftrix.data.mapper.SettingsMapper
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.service.SettingsPersistenceManager
import com.example.liftrix.domain.service.SettingsValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.liftrix.config.OfflineArchitectureFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant

@HiltWorker
class SettingsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsDao: SettingsDao,
    private val settingsAuditDao: SettingsAuditDao,
    private val settingsMapper: SettingsMapper,
    private val settingsPersistenceManager: SettingsPersistenceManager,
    private val settingsValidator: SettingsValidator,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "settings_sync_work"
        const val KEY_USER_ID = "user_id"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val FIRESTORE_USERS_COLLECTION = "users"
        private const val FIRESTORE_SETTINGS_COLLECTION = "settings"
        private const val FIRESTORE_PREFERENCES_DOCUMENT = "preferences"
    }

    override suspend fun doWork(): Result {
        return try {
            val currentUserId = auth.currentUser?.uid
                ?: return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User not authenticated for settings sync")
                        .build()
                )

            val inputUserId = inputData.getString(KEY_USER_ID)
            val targetUserId = inputUserId ?: currentUserId

            // Only sync current user's settings for security
            if (targetUserId != currentUserId) {
                return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Cannot sync settings for different user")
                        .build()
                )
            }

            Timber.d("Starting enhanced settings sync with persistence validation for user: $targetUserId")
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

            // Validate settings integrity before sync
            val integrityValid = settingsPersistenceManager.validateSettingsIntegrity(targetUserId).fold(
                onSuccess = { it },
                onFailure = { 
                    Timber.w("Settings integrity validation failed, attempting repair")
                    settingsPersistenceManager.repairCorruptedSettings(targetUserId)
                    false
                }
            )

            if (!integrityValid) {
                Timber.w("Settings integrity issues detected for user: $targetUserId")
            }

            // Perform bidirectional sync with enhanced conflict resolution
            val syncResult = performEnhancedBidirectionalSync(targetUserId, useDirtyFlagGating)
            
            when (syncResult) {
                is SyncResult.Success -> {
                    Timber.d("Settings sync completed successfully for user: $targetUserId")
                    Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, 1)
                            .build()
                    )
                }
                is SyncResult.NoChanges -> {
                    Timber.d("No settings changes to sync for user: $targetUserId")
                    Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, 0)
                            .build()
                    )
                }
                is SyncResult.Error -> {
                    Timber.e(syncResult.exception, "Settings sync failed for user: $targetUserId")
                    if (runAttemptCount < MAX_RETRY_COUNT) {
                        Result.retry()
                    } else {
                        Result.failure(
                            Data.Builder()
                                .putString(KEY_ERROR_MESSAGE, syncResult.exception.message ?: "Unknown sync error")
                                .putInt(KEY_SYNC_COUNT, 0)
                                .build()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SettingsSyncWorker failed with exception")
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                        .build()
                )
            }
        }
    }

    /**
     * Performs enhanced bidirectional sync between local and Firebase with conflict resolution.
     */
    private suspend fun performEnhancedBidirectionalSync(
        userId: String,
        useDirtyFlagGating: Boolean
    ): SyncResult {
        if (useDirtyFlagGating) {
            return performDirtyFlagSync(userId)
        }

        return try {
            // Get local settings
            val localEntity = settingsDao.getUserSettingsSync(userId)
            val localSettings = localEntity?.let { settingsMapper.toDomain(it) }

            // Get remote settings from Firebase
            val remoteSettings = syncSettingsFromFirebase(userId)

            // Determine sync strategy
            when {
                localSettings == null && remoteSettings == null -> {
                    // No settings in either location
                    SyncResult.NoChanges
                }
                localSettings != null && remoteSettings == null -> {
                    // Only local settings exist, upload to Firebase
                    syncSettingsToFirebase(userId, localSettings)
                    SyncResult.Success
                }
                localSettings == null && remoteSettings != null -> {
                    // Only remote settings exist, download to local
                    val entity = settingsMapper.toEntity(remoteSettings)
                    settingsDao.insertSettings(entity)
                    SyncResult.Success
                }
                localSettings != null && remoteSettings != null -> {
                    // Both exist, resolve conflicts with enhanced strategy
                    val resolvedSettings = resolveEnhancedSettingsConflict(localSettings, remoteSettings, userId)
                    
                    // Use persistence manager to ensure reliable storage
                    var updateSuccess = true
                    
                    // Update local if different
                    if (resolvedSettings != localSettings) {
                        val persistResult = settingsPersistenceManager.persistSettings(userId, resolvedSettings)
                        persistResult.fold(
                            onSuccess = {
                                Timber.d("Local settings updated during sync for user: $userId")
                            },
                            onFailure = { error ->
                                Timber.e("Failed to persist local settings during sync: $error")
                                updateSuccess = false
                            }
                        )
                    }
                    
                    // Update remote if different
                    if (resolvedSettings != remoteSettings && updateSuccess) {
                        try {
                            syncEnhancedSettingsToFirebase(userId, resolvedSettings)
                            Timber.d("Remote settings updated during sync for user: $userId")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update remote settings during sync")
                            updateSuccess = false
                        }
                    }
                    
                    // Create audit entry for sync
                    createSyncAuditEntry(userId, resolvedSettings, "bidirectional_sync")
                    
                    if (updateSuccess) SyncResult.Success else SyncResult.Error(Exception("Failed to complete sync updates"))
                }
                else -> SyncResult.NoChanges
            }
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Performs Room-first dirty-flag sync for settings.
     */
    private suspend fun performDirtyFlagSync(userId: String): SyncResult {
        return try {
            // Remote -> local
            val remoteSettings = syncSettingsFromFirebase(userId)
            val remoteLastModified = remoteSettings?.updatedAt?.toEpochMilli() ?: 0L
            if (remoteSettings != null) {
                val remoteEntity = settingsMapper.toEntity(remoteSettings).copy(
                    isDirty = false,
                    isSynced = true,
                    syncVersion = System.currentTimeMillis(),
                    lastModified = remoteLastModified
                )
                settingsDao.upsertFromRemote(remoteEntity)
            }

            // Local -> remote (dirty only)
            val dirtySettings = settingsDao.getDirtySettings(userId)
            if (dirtySettings.isEmpty()) {
                return SyncResult.NoChanges
            }

            dirtySettings.forEach { entity ->
                if (remoteLastModified > entity.lastModified) {
                    return@forEach
                }

                val settings = settingsMapper.toDomain(entity)
                syncEnhancedSettingsToFirebase(userId, settings)
                settingsDao.markAsClean(
                    ids = listOf(userId),
                    userId = userId,
                    syncVersion = System.currentTimeMillis()
                )
            }

            SyncResult.Success
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Syncs local settings to Firebase Firestore (legacy method).
     */
    private suspend fun syncSettingsToFirebase(userId: String, settings: UserSettings) {
        val settingsData = mapOf(
            "userId" to settings.userId,
            "darkMode" to settings.darkMode,
            "notificationsEnabled" to settings.notificationsEnabled,
            "updatedAt" to settings.updatedAt.toEpochMilli(),
            "lastModified" to settings.updatedAt.toEpochMilli(),
            "syncedAt" to System.currentTimeMillis()
        )

        firestore
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_SETTINGS_COLLECTION)
            .document(FIRESTORE_PREFERENCES_DOCUMENT)
            .set(settingsData, SetOptions.merge())
            .await()

        Timber.d("Settings synced to Firebase for user: $userId")
    }

    /**
     * Enhanced sync to Firebase that includes weight unit and comprehensive settings.
     */
    private suspend fun syncEnhancedSettingsToFirebase(userId: String, settings: UserSettings) {
        val settingsData = mapOf(
            "userId" to settings.userId,
            "darkMode" to settings.darkMode,
            "notificationsEnabled" to settings.notificationsEnabled,
            "weightUnit" to settings.weightUnit.name, // Now includes weight unit
            "updatedAt" to settings.updatedAt.toEpochMilli(),
            "lastModified" to settings.updatedAt.toEpochMilli(),
            "syncedAt" to System.currentTimeMillis(),
            "syncVersion" to 2L // Version 2 indicates enhanced sync format
        )

        firestore
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_SETTINGS_COLLECTION)
            .document(FIRESTORE_PREFERENCES_DOCUMENT)
            .set(settingsData, SetOptions.merge())
            .await()

        Timber.d("Enhanced settings synced to Firebase for user: $userId (weight unit: ${settings.weightUnit.name})")
    }

    /**
     * Syncs settings from Firebase Firestore to local storage.
     */
    private suspend fun syncSettingsFromFirebase(userId: String): UserSettings? {
        val document = firestore
            .collection(FIRESTORE_USERS_COLLECTION)
            .document(userId)
            .collection(FIRESTORE_SETTINGS_COLLECTION)
            .document(FIRESTORE_PREFERENCES_DOCUMENT)
            .get()
            .await()

        return if (document.exists()) {
            val data = document.data ?: return null
            
            UserSettings(
                userId = data["userId"] as? String ?: userId,
                darkMode = data["darkMode"] as? Boolean ?: false,
                notificationsEnabled = data["notificationsEnabled"] as? Boolean ?: true,
                updatedAt = (data["updatedAt"] as? Long)?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
            )
        } else {
            null
        }
    }

    /**
     * Resolves conflicts between local and remote settings.
     * Uses timestamp-based conflict resolution (last write wins).
     */
    private fun resolveSettingsConflict(localSettings: UserSettings, remoteSettings: UserSettings): UserSettings {
        return if (localSettings.updatedAt.isAfter(remoteSettings.updatedAt)) {
            Timber.d("Using local settings (newer): ${localSettings.updatedAt} > ${remoteSettings.updatedAt}")
            localSettings
        } else {
            Timber.d("Using remote settings (newer): ${remoteSettings.updatedAt} >= ${localSettings.updatedAt}")
            remoteSettings
        }
    }

    /**
     * Enhanced conflict resolution with field-level granularity and validation.
     * Attempts to merge compatible changes and validates the result.
     */
    private suspend fun resolveEnhancedSettingsConflict(
        localSettings: UserSettings,
        remoteSettings: UserSettings,
        userId: String
    ): UserSettings {
        Timber.d("Resolving settings conflict with enhanced strategy for user: $userId")
        
        // Start with timestamp-based resolution as base
        val timestampWinner = if (localSettings.updatedAt.isAfter(remoteSettings.updatedAt)) {
            localSettings
        } else {
            remoteSettings
        }
        
        // Attempt field-level merge for compatible changes
        val mergedSettings = try {
            // For critical settings like weight unit, prefer the most recent change
            val weightUnit = if (localSettings.weightUnit != remoteSettings.weightUnit) {
                // Use the weight unit from the timestamp winner
                timestampWinner.weightUnit
            } else {
                localSettings.weightUnit
            }
            
            // For boolean settings, we can be more intelligent
            val darkMode = if (localSettings.darkMode != remoteSettings.darkMode) {
                timestampWinner.darkMode
            } else {
                localSettings.darkMode
            }
            
            val notificationsEnabled = if (localSettings.notificationsEnabled != remoteSettings.notificationsEnabled) {
                timestampWinner.notificationsEnabled
            } else {
                localSettings.notificationsEnabled
            }
            
            // Create merged settings
            UserSettings(
                userId = userId,
                darkMode = darkMode,
                notificationsEnabled = notificationsEnabled,
                weightUnit = weightUnit,
                updatedAt = maxOf(localSettings.updatedAt, remoteSettings.updatedAt)
            )
            
        } catch (e: Exception) {
            Timber.w(e, "Field-level merge failed, falling back to timestamp winner")
            timestampWinner
        }
        
        // Validate the merged settings
        val validationResult = settingsValidator.validateUserSettings(mergedSettings)
        return validationResult.fold(
            onSuccess = {
                Timber.d("Conflict resolution successful with validation for user: $userId")
                mergedSettings
            },
            onFailure = { error ->
                Timber.w("Merged settings failed validation, using timestamp winner: $error")
                timestampWinner
            }
        )
    }
    
    /**
     * Creates an audit entry for sync operations.
     */
    private suspend fun createSyncAuditEntry(
        userId: String,
        settings: UserSettings,
        operation: String
    ) {
        try {
            val auditResult = settingsPersistenceManager.createAuditEntry(
                userId = userId,
                operation = operation,
                settingKey = "all_settings",
                oldValue = null, // Could be enhanced to track old values
                newValue = "sync_operation",
                source = "sync_worker"
            )
            
            auditResult.fold(
                onSuccess = {
                    Timber.d("Sync audit entry created for user: $userId, operation: $operation")
                },
                onFailure = { error ->
                    Timber.w("Failed to create sync audit entry: $error")
                }
            )
        } catch (e: Exception) {
            Timber.w(e, "Exception creating sync audit entry")
        }
    }

    /**
     * Sealed class representing sync operation results.
     */
    private sealed class SyncResult {
        object Success : SyncResult()
        object NoChanges : SyncResult()
        data class Error(val exception: Exception) : SyncResult()
    }
}
