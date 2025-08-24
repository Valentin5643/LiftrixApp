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
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.AchievementDao
import com.example.liftrix.data.mapper.UserProfileMapper
import com.example.liftrix.data.repository.social.FollowRepositoryImpl
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
    private val workoutDao: WorkoutDao,
    private val achievementDao: AchievementDao,
    private val userProfileMapper: UserProfileMapper,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val gson: Gson,
    private val followRepository: FollowRepositoryImpl
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
            
            // 🔍 FORENSIC LOGGING - Pre-sync data integrity verification
            val preWorkoutCount = try {
                workoutDao.getWorkoutCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve workout count for user $userId: ${e.message}")
                -1
            }
            
            // 🚨 CRITICAL DIAGNOSTIC - Check workout sync status before profile sync
            val preUnsyncedWorkouts = try {
                workoutDao.getUnsyncedCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve unsynced workout count for user $userId: ${e.message}")
                -1
            }
            
            val preSyncedWorkouts = try {
                workoutDao.getSyncedCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve synced workout count for user $userId: ${e.message}")
                -1
            }
            
            val preAchievementCount = try {
                achievementDao.getAchievementCount(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve achievement count for user $userId: ${e.message}")
                -1
            }
            
            Timber.d("[DATA-INTEGRITY] Pre-profile-sync counts for user $userId:")
            Timber.d("[DATA-INTEGRITY] - Total Workouts: $preWorkoutCount")
            Timber.d("[DATA-INTEGRITY] - Unsynced Workouts: $preUnsyncedWorkouts")
            Timber.d("[DATA-INTEGRITY] - Synced Workouts: $preSyncedWorkouts")
            Timber.d("[DATA-INTEGRITY] - Achievements: $preAchievementCount")
            
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
            
            // 🔒 SYNC ISOLATION - Use merge with field-specific updates to prevent cascade corruption
            // Only update profile-specific fields, never touch workout-related sync metadata
            val profileOnlyData = profileData.filterKeys { key ->
                // Only allow profile-specific fields, exclude any workout-related metadata
                key !in listOf("workout_sync_status", "last_workout_sync", "workout_count", "total_workouts")
            }
            
            docRef.set(profileOnlyData, SetOptions.merge()).await()
            
            Timber.d("[DATA-INTEGRITY] Profile sync completed with field isolation - ${profileOnlyData.size} fields updated")
            
            // 🔒 SYNC ISOLATION - Update only profile sync status, not workout sync status
            userProfileDao.updateSyncStatus(
                userId = userId,
                isSynced = true,
                version = System.currentTimeMillis()
            )
            
            Timber.d("[DATA-INTEGRITY] Profile sync status updated - workout sync status preserved")
            
            // 🔥 FIX: Sync follow relationships using upsert to prevent feed flickering
            try {
                Timber.d("🔥 FEED-FIX: Syncing follow relationships using upsert for user $userId")
                val followCount = followRepository.syncFollowRelationshipsFromFirebaseUpsert(userId)
                Timber.d("🔥 FEED-FIX: Successfully synced $followCount follow relationships without clearing feed")
            } catch (e: Exception) {
                Timber.e(e, "🔥 FEED-FIX: Failed to sync follow relationships during profile sync")
                // Don't fail the profile sync if follow sync fails
            }
            
            // 🔍 FORENSIC LOGGING - Post-sync data integrity verification
            val postWorkoutCount = try {
                workoutDao.getWorkoutCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve post-sync workout count for user $userId: ${e.message}")
                -1
            }
            
            // 🚨 CRITICAL DIAGNOSTIC - Check workout sync status AFTER profile sync
            val postUnsyncedWorkouts = try {
                workoutDao.getUnsyncedCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve post-sync unsynced workout count for user $userId: ${e.message}")
                -1
            }
            
            val postSyncedWorkouts = try {
                workoutDao.getSyncedCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve post-sync synced workout count for user $userId: ${e.message}")
                -1
            }
            
            val postAchievementCount = try {
                achievementDao.getAchievementCount(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve post-sync achievement count for user $userId: ${e.message}")
                -1
            }
            
            Timber.d("[DATA-INTEGRITY] Post-profile-sync counts for user $userId:")
            Timber.d("[DATA-INTEGRITY] - Total Workouts: $postWorkoutCount (was $preWorkoutCount)")
            Timber.d("[DATA-INTEGRITY] - Unsynced Workouts: $postUnsyncedWorkouts (was $preUnsyncedWorkouts)")
            Timber.d("[DATA-INTEGRITY] - Synced Workouts: $postSyncedWorkouts (was $preSyncedWorkouts)")
            Timber.d("[DATA-INTEGRITY] - Achievements: $postAchievementCount (was $preAchievementCount)")
            
            // 🚨 CRITICAL CHECK - Detect sync status corruption during profile sync  
            if (preUnsyncedWorkouts >= 0 && postUnsyncedWorkouts >= 0 && preUnsyncedWorkouts != postUnsyncedWorkouts) {
                Timber.e("[DATA-INTEGRITY] ⚠️  WORKOUT SYNC STATUS CHANGED during profile sync!")
                Timber.e("[DATA-INTEGRITY] Unsynced workout count: $preUnsyncedWorkouts -> $postUnsyncedWorkouts")
                
                if (preUnsyncedWorkouts > 0 && postUnsyncedWorkouts == 0) {
                    Timber.e("[DATA-INTEGRITY] 🚨 CRITICAL: All unsynced workouts became synced during profile update!")
                    Timber.e("[DATA-INTEGRITY] This explains why WorkoutSyncWorker finds no workouts to sync")
                }
            }
            
            // 🚨 CRITICAL CHECK - Detect unexpected data loss during profile sync
            if (preWorkoutCount > 0 && postWorkoutCount >= 0 && postWorkoutCount < preWorkoutCount) {
                Timber.e("[DATA-INTEGRITY] ⚠️  WORKOUT DATA LOSS DETECTED during profile sync!")
                Timber.e("[DATA-INTEGRITY] User $userId lost ${preWorkoutCount - postWorkoutCount} workouts during profile sync")
                
                // Don't fail the sync, but log the critical issue
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 1)
                        .putString("data_loss_detected", "workout_count_decreased")
                        .putInt("pre_workout_count", preWorkoutCount)
                        .putInt("post_workout_count", postWorkoutCount)
                        .build()
                )
            }
            
            if (preAchievementCount > 0 && postAchievementCount >= 0 && postAchievementCount < preAchievementCount) {
                Timber.e("[DATA-INTEGRITY] ⚠️  ACHIEVEMENT DATA LOSS DETECTED during profile sync!")
                Timber.e("[DATA-INTEGRITY] User $userId lost ${preAchievementCount - postAchievementCount} achievements during profile sync")
                
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 1)
                        .putString("data_loss_detected", "achievement_count_decreased")
                        .putInt("pre_achievement_count", preAchievementCount)
                        .putInt("post_achievement_count", postAchievementCount)
                        .build()
                )
            }
            
            // 🚨 SYNC STATUS CORRUPTION DETECTION
            if (preUnsyncedWorkouts > 0 && postUnsyncedWorkouts == 0 && preWorkoutCount == postWorkoutCount) {
                Timber.e("[DATA-INTEGRITY] 🚨 SYNC STATUS CORRUPTION DETECTED!")
                Timber.e("[DATA-INTEGRITY] Profile sync incorrectly marked all workouts as synced")
                
                // Mark workouts as unsynced again to fix the corruption
                try {
                    val corruptedCount = workoutDao.markAllWorkoutsAsUnsyncedForUser(userId)
                    Timber.i("[DATA-INTEGRITY] 🔧 SYNC CORRUPTION FIXED: Marked $corruptedCount workouts as unsynced")
                    
                    return Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, 1)
                            .putString("sync_corruption_fixed", "marked_workouts_unsynced")
                            .putInt("corrupted_workout_count", corruptedCount)
                            .build()
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[DATA-INTEGRITY] Failed to fix sync corruption")
                }
            }
            
            Timber.d("[DATA-INTEGRITY] Profile sync completed successfully - no data loss detected")
            
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