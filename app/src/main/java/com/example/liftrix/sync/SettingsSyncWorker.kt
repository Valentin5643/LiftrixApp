package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.mapper.SettingsMapper
import com.example.liftrix.domain.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    private val settingsMapper: SettingsMapper,
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

            Timber.d("Starting settings sync for user: $targetUserId")

            // Perform bidirectional sync
            val syncResult = performBidirectionalSync(targetUserId)
            
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
     * Performs bidirectional sync between local and Firebase.
     */
    private suspend fun performBidirectionalSync(userId: String): SyncResult {
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
                    // Both exist, resolve conflicts
                    val resolvedSettings = resolveSettingsConflict(localSettings, remoteSettings)
                    
                    // Update both local and remote with resolved settings
                    if (resolvedSettings != localSettings) {
                        val entity = settingsMapper.toEntity(resolvedSettings)
                        settingsDao.insertSettings(entity)
                    }
                    
                    if (resolvedSettings != remoteSettings) {
                        syncSettingsToFirebase(userId, resolvedSettings)
                    }
                    
                    SyncResult.Success
                }
                else -> SyncResult.NoChanges
            }
        } catch (e: Exception) {
            SyncResult.Error(e)
        }
    }

    /**
     * Syncs local settings to Firebase Firestore.
     */
    private suspend fun syncSettingsToFirebase(userId: String, settings: UserSettings) {
        val settingsData = mapOf(
            "userId" to settings.userId,
            "darkMode" to settings.darkMode,
            "notificationsEnabled" to settings.notificationsEnabled,
            "updatedAt" to settings.updatedAt.toEpochMilli(),
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
     * Sealed class representing sync operation results.
     */
    private sealed class SyncResult {
        object Success : SyncResult()
        object NoChanges : SyncResult()
        data class Error(val exception: Exception) : SyncResult()
    }
}