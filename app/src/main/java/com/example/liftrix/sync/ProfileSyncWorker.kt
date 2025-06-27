package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.mapper.UserProfileMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@HiltWorker
class ProfileSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userProfileDao: UserProfileDao,
    private val userProfileMapper: UserProfileMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "profile_sync_work"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val MAX_RETRY_COUNT = 3
        private const val FIRESTORE_COLLECTION = "user_profiles"
    }

    override suspend fun doWork(): Result {
        return try {
            val currentUserId = auth.currentUser?.uid
                ?: return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "User not authenticated for profile sync")
                        .build()
                )

            val unsyncedProfiles = userProfileDao.getUnsyncedProfiles()
            
            if (unsyncedProfiles.isEmpty()) {
                Timber.d("No unsynced user profiles found")
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Found ${unsyncedProfiles.size} unsynced user profiles")
            
            val syncedProfileIds = mutableListOf<String>()
            var hasError = false
            var lastError: Exception? = null

            for (profileEntity in unsyncedProfiles) {
                // Only sync the current user's profile
                if (profileEntity.userId != currentUserId) continue
                
                try {
                    val profile = userProfileMapper.toDomain(profileEntity)
                    val profileDto = userProfileMapper.toFirestoreDto(profile)
                    
                    // Upload to Firestore
                    val documentRef = firestore
                        .collection(FIRESTORE_COLLECTION)
                        .document(profileEntity.userId)
                    
                    documentRef.set(profileDto).await()
                    
                    // Mark as synced in local database
                    userProfileDao.updateSyncStatus(
                        userId = profileEntity.userId,
                        isSynced = true,
                        version = System.currentTimeMillis()
                    )
                    
                    syncedProfileIds.add(profileEntity.userId)
                    Timber.d("Successfully synced user profile: ${profileEntity.userId}")
                    
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync user profile: ${profileEntity.userId}")
                    hasError = true
                    lastError = e
                    continue
                }
            }

            return when {
                syncedProfileIds.isNotEmpty() && !hasError -> Result.success(Data.Builder().putInt(KEY_SYNC_COUNT, syncedProfileIds.size).build())
                syncedProfileIds.isNotEmpty() && hasError -> {
                    Timber.w("Partial sync completed for user profiles. Synced: ${syncedProfileIds.size}, Failed: ${unsyncedProfiles.size - syncedProfileIds.size}")
                    Result.success(Data.Builder().putInt(KEY_SYNC_COUNT, syncedProfileIds.size).putString(KEY_ERROR_MESSAGE, "Partial sync: ${lastError?.message}").build())
                }
                else -> {
                    val errorMessage = lastError?.message ?: "Unknown profile sync error"
                    Timber.e("All user profiles failed to sync: $errorMessage")
                    if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure(Data.Builder().putString(KEY_ERROR_MESSAGE, errorMessage).putInt(KEY_SYNC_COUNT, 0).build())
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "ProfileSyncWorker failed with exception")
            if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure(Data.Builder().putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error").build())
        }
    }
} 