package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.SettingsDao
import com.example.liftrix.data.local.entity.SettingsEntity
import com.example.liftrix.domain.model.WeightUnit
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.example.liftrix.config.OfflineArchitectureFlags
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Worker responsible for bidirectional settings sync with Firebase including kg/lbs preferences.
 * Part of Firebase sync infrastructure from SPEC-20250118-firebase-sync-social.
 * 
 * Implements bidirectional sync with last-write-wins conflict resolution
 * and ensures all unit preferences are properly synchronized.
 */
@HiltWorker
class SettingsSyncWorkerV2 @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsDao: SettingsDao,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "settings_sync_work_v2"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<SettingsSyncWorkerV2>()
                .setInputData(workDataOf(
                    "userId" to userId,
                    "forceSync" to forceSync
                ))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag("settings_sync_v2")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val userId = inputData.getString("userId") ?: return@withContext Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "User ID not provided")
                    .build()
            )
            
            val forceSync = inputData.getBoolean("forceSync", false)
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING

            Timber.d("Starting settings sync for user $userId (forceSync: $forceSync, dirty gating: $useDirtyFlagGating)")
            
            val docRef = firestore
                .collection("users")
                .document(userId)
                .collection("settings")
                .document("preferences")
            
            // Download remote settings (remote -> local)
            val remoteDoc = docRef.get().await()
            val remoteLastModified = if (remoteDoc.exists()) {
                when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
            } else {
                0L
            }
            if (remoteDoc.exists()) {
                val remoteSettings = remoteDoc.toSettingsEntity(userId).copy(
                    isDirty = false,
                    isSynced = true,
                    syncVersion = (remoteDoc.getLong("syncVersion") ?: 0L).toInt(),
                    lastModified = remoteLastModified
                )
                settingsDao.upsertFromRemote(remoteSettings)
            }

            var localSettings = settingsDao.getSettingsForUser(userId)
            if (localSettings == null && !remoteDoc.exists()) {
                val defaultSettings = createDefaultSettings(userId)
                settingsDao.upsertLocal(defaultSettings)
                localSettings = settingsDao.getSettingsForUser(userId)
            }

            val settingsToUpload = if (useDirtyFlagGating) {
                settingsDao.getDirtySettings(userId)
            } else {
                localSettings?.let { settings ->
                    if (!settings.isSynced || forceSync) listOf(settings) else emptyList()
                } ?: emptyList()
            }

            if (settingsToUpload.isEmpty()) {
                Timber.d("No settings changes to sync for user $userId")
                return@withContext Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            var syncedCount = 0
            settingsToUpload.forEach { settings ->
                if (remoteLastModified > settings.lastModified) {
                    return@forEach
                }

                val settingsData = mapOf(
                    "userId" to userId,
                    "weightUnit" to when (settings.weightUnit) {
                        WeightUnit.KILOGRAMS -> "KG"
                        WeightUnit.POUNDS -> "LBS"
                    },
                    "distanceUnit" to settings.distanceUnit,
                    "darkMode" to settings.darkMode,
                    "notifications" to settings.notificationsEnabled,
                    "privateProfile" to settings.privateProfile,
                    "hideStats" to settings.hideStats,
                    "allowMessages" to settings.allowMessages,
                    "autoPlayVideos" to settings.autoPlayVideos,
                    "lastModified" to settings.lastModified,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "syncVersion" to System.currentTimeMillis()
                )
                
                docRef.set(settingsData, SetOptions.merge()).await()
                settingsDao.markAsClean(
                    ids = listOf(userId),
                    userId = userId,
                    syncVersion = System.currentTimeMillis()
                )
                syncedCount++
            }
            
            Timber.d("Successfully completed settings sync for user $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, syncedCount)
                    .build()
            )
            
        } catch (e: Exception) {
            Timber.e(e, "SettingsSyncWorkerV2 failed for user ${inputData.getString("userId")}")
            
            return@withContext if (runAttemptCount < MAX_RETRY_COUNT) {
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
    
    private fun createDefaultSettings(userId: String): SettingsEntity {
        return SettingsEntity(
            userId = userId,
            darkMode = false,
            notificationsEnabled = true,
            weightUnit = WeightUnit.getSystemDefault(),
            terminologyPreference = "NEW",
            migrationCompleted = false,
            migrationExplanationSeen = false,
            settingsVersion = 1,
            lastSyncTimestamp = null,
            updatedAt = Instant.now(),
            distanceUnit = "KM", // Default to KM
            privateProfile = false,
            hideStats = false,
            allowMessages = true,
            autoPlayVideos = true,
            isSynced = false,
            syncVersion = 0
        )
    }
    
    private fun com.google.firebase.firestore.DocumentSnapshot.toSettingsEntity(userId: String): SettingsEntity {
        return SettingsEntity(
            userId = userId,
            darkMode = getBoolean("darkMode") ?: false,
            notificationsEnabled = getBoolean("notifications") ?: true,
            weightUnit = when (getString("weightUnit")) {
                "LBS" -> WeightUnit.POUNDS
                else -> WeightUnit.KILOGRAMS
            },
            terminologyPreference = "NEW",
            migrationCompleted = false,
            migrationExplanationSeen = false,
            settingsVersion = 1,
            lastSyncTimestamp = System.currentTimeMillis(),
            updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: Instant.now(),
            distanceUnit = getString("distanceUnit") ?: "KM",
            privateProfile = getBoolean("privateProfile") ?: false,
            hideStats = getBoolean("hideStats") ?: false,
            allowMessages = getBoolean("allowMessages") ?: true,
            autoPlayVideos = getBoolean("autoPlayVideos") ?: true,
            isSynced = true,
            syncVersion = (getLong("syncVersion") ?: 0).toInt()
        )
    }
}
