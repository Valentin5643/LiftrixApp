package com.example.liftrix.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.workDataOf
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.mapper.UserProfileMapper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class ProfileSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val userProfileDao: UserProfileDao,
    private val userProfileMapper: UserProfileMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val gson: Gson
) : BaseSyncWorker(context, params) {

    override val workerName: String = "ProfileSyncWorker"
    
    companion object {
        const val WORK_NAME = "profile_sync_work"
        
        fun createWorkRequest(userId: String, forceSync: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<ProfileSyncWorker>()
                .setInputData(workDataOf(
                    KEY_USER_ID to userId,
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
                .addTag("profile_sync")
                .build()
        }
    }

    override suspend fun performSync(userId: String): Result {
        try {
            // Check cancellation before starting
            checkCancellation()
            
            val forceSync = inputData.getBoolean("forceSync", false)

            val profile = userProfileDao.getProfileForUserSuspend(userId) 
                ?: return Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Profile not found for user $userId")
                        .build()
                )
            
            if (profile.isSynced && !forceSync) {
                Timber.d("Profile already synced for user $userId")
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            Timber.d("Syncing profile for user $userId (forceSync: $forceSync)")
            
            // Check cancellation before network operation
            checkCancellation()
            
            val docRef = firestore
                .collection("users")
                .document(userId)
            
            // Parse JSON fields safely
            val fitnessGoals = profile.goals?.let { 
                try {
                    gson.fromJson(it, Array<String>::class.java).toList() 
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse goals JSON")
                    null
                }
            }
            
            val availableEquipment = profile.availableEquipment?.let {
                try {
                    gson.fromJson(it, Array<String>::class.java).toList()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse availableEquipment JSON")
                    null
                }
            }
            
            // Complete profile data including new fields
            // IMPORTANT: Include all required sync metadata fields for Firestore rules validation
            val profileData = mapOf(
                "userId" to userId,
                "displayName" to profile.displayName,
                "bio" to profile.bio,
                "age" to profile.age,
                "weightKg" to profile.weightKg,
                "heightCm" to profile.heightCm,
                "fitnessLevel" to profile.fitnessLevel,
                "fitnessGoals" to fitnessGoals,
                "availableEquipment" to availableEquipment,
                "workoutFrequency" to profile.workoutFrequency,
                "preferredWorkoutDuration" to profile.preferredWorkoutDuration,
                "profileImageUrl" to profile.profileImageUrl,
                "profileImageUpdatedAt" to profile.profileImageUpdatedAt,
                "hasCustomProfileImage" to profile.hasCustomProfileImage,
                "totalWorkouts" to profile.totalWorkouts,
                "currentStreak" to profile.currentStreak,
                "longestStreak" to profile.longestStreak,
                "memberSince" to profile.memberSince,
                "lastActiveAt" to profile.lastActiveAt,
                "isPublic" to profile.isPublic,
                "profileCompletionPercentage" to profile.profileCompletionPercentage,
                // Required sync metadata fields (isValidSyncMetadata in firestore.rules)
                "syncVersion" to System.currentTimeMillis(),
                "lastModified" to FieldValue.serverTimestamp(), // Required by security rules
                "isSynced" to true, // Required by security rules
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            docRef.set(profileData, SetOptions.merge()).await()
            
            userProfileDao.updateSyncStatus(
                userId = userId,
                isSynced = true,
                version = System.currentTimeMillis()
            )
            
            Timber.d("Successfully synced profile for user $userId")
            
            return Result.success(
                Data.Builder()
                    .putInt(KEY_SYNC_COUNT, 1)
                    .build()
            )
            
        } catch (e: CancellationException) {
            // Re-throw cancellation to maintain cancellation chain
            throw e
        } catch (e: Exception) {
            // Let base class handle the error
            throw e
        }
    }
} 