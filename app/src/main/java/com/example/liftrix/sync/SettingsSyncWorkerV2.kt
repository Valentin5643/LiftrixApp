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

            Timber.d("Starting bidirectional settings sync for user $userId (forceSync: $forceSync)")
            
            val docRef = firestore
                .collection("users")
                .document(userId)
                .collection("settings")
                .document("preferences")
            
            // Download remote settings
            val remoteDoc = docRef.get().await()
            val localSettings = settingsDao.getSettingsForUser(userId)
            
            // Merge settings with last-write-wins as per specification
            val mergedSettings = if (remoteDoc.exists() && localSettings != null) {
                val remoteSettings = remoteDoc.toSettingsEntity(userId)
                if (localSettings.updatedAt.isAfter(remoteSettings.updatedAt)) {
                    localSettings
                } else {
                    remoteSettings
                }
            } else {
                localSettings ?: createDefaultSettings(userId)
            }
            
            // Save merged settings locally
            settingsDao.upsertSettings(mergedSettings)
            
            // Upload to Firebase with all fields as per specification
            val settingsData = mapOf(
                "userId" to userId,
                "weightUnit" to when (mergedSettings.weightUnit) {
                    WeightUnit.KILOGRAMS -> "KG"
                    WeightUnit.POUNDS -> "LBS"
                },
                "distanceUnit" to mergedSettings.distanceUnit,
                "darkMode" to mergedSettings.darkMode,
                "notifications" to mergedSettings.notificationsEnabled,
                "privateProfile" to mergedSettings.privateProfile,
                "hideStats" to mergedSettings.hideStats,
                "allowMessages" to mergedSettings.allowMessages,
                "autoPlayVideos" to mergedSettings.autoPlayVideos,
                "updatedAt" to FieldValue.serverTimestamp(),
                "syncVersion" to System.currentTimeMillis()
            )
            
            docRef.set(settingsData, SetOptions.merge()).await()
            
            // Mark as synced
            settingsDao.markAsSynced(
                userId = userId,
                isSynced = true,
                version = System.currentTimeMillis().toInt()
            )
            
            Timber.d("Successfully completed bidirectional settings sync for user $userId")
            
            return@withContext Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, 1)
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