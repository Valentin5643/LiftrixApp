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
import com.example.liftrix.data.service.ProfileCleanupService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FieldValue
import com.example.liftrix.config.OfflineArchitectureFlags
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
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
    private val followRepository: FollowRepositoryImpl,
    private val profileCleanupService: ProfileCleanupService
) : BaseSyncWorker(context, params) {

    init {
        Timber.d("✅ ProfileSyncWorker constructed with Hilt dependency injection")
    }

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
                .addTag("user_$userId") // Add user-specific tag for better tracking
                .build()
        }
        
        /**
         * Get unique work name per user to prevent job conflicts
         */
        fun getWorkName(userId: String): String = "${WORK_NAME}_$userId"
    }

    override suspend fun performSync(userId: String): Result {
        try {
            // Check cancellation before starting
            checkCancellation()
            val useDirtyFlagGating = OfflineArchitectureFlags.ROOM_FIRST_ENABLED &&
                OfflineArchitectureFlags.USE_DIRTY_FLAG_GATING
            val forceSync = inputData.getBoolean("forceSync", false)
            
            // 🔍 FORENSIC LOGGING - Pre-sync data integrity verification
            val preWorkoutCount = try {
                workoutDao.getWorkoutCountForUser(userId)
            } catch (e: Exception) {
                Timber.w("Could not retrieve workout count for user $userId: ${e.message}")
                -1
            }
            
            // 🚨 CRITICAL DIAGNOSTIC - Check workout sync status before profile sync
            val preUnsyncedWorkouts = try {
                if (useDirtyFlagGating) {
                    workoutDao.getDirtyWorkouts(userId).size
                } else {
                    workoutDao.getUnsyncedCountForUser(userId)
                }
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
            
            val profile = userProfileDao.getProfileForUserSuspend(userId)
            
            // 🧹 ORPHAN CLEANUP: If profile not found, check if user is orphaned and trigger cleanup
            if (profile == null) {
                Timber.w("🧹 ProfileSyncWorker: Profile not found for user $userId - checking if orphaned")
                
                try {
                    val isOrphaned = profileCleanupService.isUserOrphaned(userId)
                    if (isOrphaned) {
                        Timber.w("🧹 ProfileSyncWorker: User $userId confirmed as orphaned - triggering cleanup")
                        val cleanupResult = profileCleanupService.performOrphanedProfileCleanup(excludeUserId = null)
                        
                        Timber.i("🧹 ProfileSyncWorker: Cleanup completed - removed ${cleanupResult.orphanedProfilesRemoved} orphaned profiles")
                        
                        return Result.failure(
                            Data.Builder()
                                .putString(KEY_ERROR_MESSAGE, "Orphaned user $userId cleaned up - sync no longer needed")
                                .putBoolean("orphan_cleanup_performed", true)
                                .putInt("orphaned_profiles_removed", cleanupResult.orphanedProfilesRemoved)
                                .build()
                        )
                    } else {
                        Timber.w("🧹 ProfileSyncWorker: User $userId not orphaned but profile missing - possible sync issue")
                        
                        // 🚨 ANTI-LOOP: Don't retry indefinitely for missing profiles
                        // This prevents infinite retry loops when profiles are genuinely missing
                        val retryCount = inputData.getInt("retry_count", 0)
                        if (retryCount >= 3) {
                            Timber.w("🧹 ProfileSyncWorker: Max retries reached for missing profile $userId - stopping")
                            return Result.failure(
                                Data.Builder()
                                    .putString(KEY_ERROR_MESSAGE, "Profile missing after $retryCount retries - giving up")
                                    .putInt("max_retries_reached", retryCount)
                                    .build()
                            )
                        } else {
                            Timber.d("🧹 ProfileSyncWorker: Retrying profile sync for $userId (attempt ${retryCount + 1}/3)")
                            return Result.retry() // Limited retry with counter
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "🧹 ProfileSyncWorker: Failed to check orphan status for user $userId")
                    return Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Profile not found and cleanup check failed for user $userId")
                            .build()
                    )
                }
            }
            
            if (useDirtyFlagGating && !profile.isDirty) {
                Timber.d("Profile not dirty for user $userId (dirty gating enabled)")
                if (forceSync) {
                    Timber.d("Force sync requested but ignored due to dirty gating")
                }
                return Result.success(
                    Data.Builder()
                        .putInt(KEY_SYNC_COUNT, 0)
                        .build()
                )
            }

            if (!useDirtyFlagGating && profile.isSynced && !forceSync) {
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

            val remoteDoc = docRef.get().await()
            if (remoteDoc.exists()) {
                val remoteLastModified = when (val remoteValue = remoteDoc.get("lastModified")) {
                    is com.google.firebase.Timestamp -> remoteValue.toDate().time
                    is Number -> remoteValue.toLong()
                    else -> 0L
                }
                if (remoteLastModified > profile.lastModified) {
                    val goalsJson = when (val remoteGoals = remoteDoc.get("fitnessGoals")) {
                        is List<*> -> gson.toJson(remoteGoals)
                        is String -> remoteGoals
                        else -> profile.goals
                    }
                    val equipmentJson = when (val remoteEquipment = remoteDoc.get("availableEquipment")) {
                        is List<*> -> gson.toJson(remoteEquipment)
                        is String -> remoteEquipment
                        else -> profile.availableEquipment
                    }
                    val updatedAt = remoteDoc.getTimestamp("updatedAt")?.toDate()?.toInstant()
                        ?.atZone(java.time.ZoneOffset.UTC)
                        ?.toLocalDateTime() ?: profile.updatedAt
                    val createdAt = remoteDoc.getTimestamp("createdAt")?.toDate()?.toInstant()
                        ?.atZone(java.time.ZoneOffset.UTC)
                        ?.toLocalDateTime() ?: profile.createdAt
                    val lastActiveAt = remoteDoc.getTimestamp("lastActiveAt")?.toDate()?.toInstant()
                        ?.atZone(java.time.ZoneOffset.UTC)
                        ?.toLocalDateTime() ?: profile.lastActiveAt
                    val memberSince = remoteDoc.getTimestamp("memberSince")?.toDate()?.toInstant()
                        ?.atZone(java.time.ZoneOffset.UTC)
                        ?.toLocalDateTime() ?: profile.memberSince
                    val profileImageUpdatedAt = remoteDoc.getTimestamp("profileImageUpdatedAt")?.toDate()?.toInstant()
                        ?.atZone(java.time.ZoneOffset.UTC)
                        ?.toLocalDateTime() ?: profile.profileImageUpdatedAt
                    val completedAt = remoteDoc.getTimestamp("completedAt")?.toDate()?.toInstant()
                        ?.atZone(java.time.ZoneOffset.UTC)
                        ?.toLocalDateTime() ?: profile.completedAt

                    val remoteEntity = profile.copy(
                        displayName = remoteDoc.getString("displayName") ?: profile.displayName,
                        bio = remoteDoc.getString("bio") ?: profile.bio,
                        age = remoteDoc.getLong("age")?.toInt() ?: profile.age,
                        weightKg = remoteDoc.getDouble("weightKg") ?: profile.weightKg,
                        heightCm = remoteDoc.getDouble("heightCm") ?: profile.heightCm,
                        fitnessLevel = remoteDoc.getString("fitnessLevel") ?: profile.fitnessLevel,
                        goals = goalsJson,
                        availableEquipment = equipmentJson,
                        workoutFrequency = remoteDoc.getLong("workoutFrequency")?.toInt()
                            ?: profile.workoutFrequency,
                        preferredWorkoutDuration = remoteDoc.getLong("preferredWorkoutDuration")?.toInt()
                            ?: profile.preferredWorkoutDuration,
                        completedAt = completedAt,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        isDirty = false,
                        isSynced = true,
                        syncVersion = remoteDoc.getLong("syncVersion") ?: profile.syncVersion,
                        lastModified = remoteLastModified,
                        isPublic = remoteDoc.getBoolean("isPublic") ?: profile.isPublic,
                        lastActiveAt = lastActiveAt,
                        totalWorkouts = remoteDoc.getLong("totalWorkouts")?.toInt()
                            ?: profile.totalWorkouts,
                        currentStreak = remoteDoc.getLong("currentStreak")?.toInt()
                            ?: profile.currentStreak,
                        longestStreak = remoteDoc.getLong("longestStreak")?.toInt()
                            ?: profile.longestStreak,
                        memberSince = memberSince,
                        profileCompletionPercentage = remoteDoc.getLong("profileCompletionPercentage")?.toInt()
                            ?: profile.profileCompletionPercentage,
                        profileImageUrl = remoteDoc.getString("profileImageUrl")
                            ?: profile.profileImageUrl,
                        profileImageUpdatedAt = profileImageUpdatedAt,
                        hasCustomProfileImage = remoteDoc.getBoolean("hasCustomProfileImage")
                            ?: profile.hasCustomProfileImage
                    )
                    userProfileDao.upsertFromRemote(remoteEntity)
                    return Result.success(
                        Data.Builder()
                            .putInt(KEY_SYNC_COUNT, 0)
                            .build()
                    )
                }
            }
            
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
                "lastModified" to profile.lastModified,
                "isSynced" to true, // Required by security rules
                "updatedAt" to FieldValue.serverTimestamp()
            )
            
            // 🔒 SYNC ISOLATION - Use merge with field-specific updates to prevent cascade corruption
            // Only update profile-specific fields, never touch workout-related sync metadata
            val profileOnlyData = profileData.filterKeys { key ->
                // Only allow profile-specific fields, exclude any workout-related metadata
                key !in listOf("workout_sync_status", "last_workout_sync", "workout_count", "total_workouts")
            }
            
            try {
                docRef.set(profileOnlyData, SetOptions.merge()).await()
            } catch (e: Exception) {
                // 🚨 PERMISSION_DENIED FIX: Handle Firestore security rule violations
                if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                    Timber.e("🧹 ProfileSyncWorker: PERMISSION_DENIED for user $userId - security rules prevent client write")
                    Timber.e("🧹 ProfileSyncWorker: ⚠️  This may indicate orphaned Firebase Auth user trying to sync")
                    
                    // Record metrics for server-side cleanup tracking
                    try {
                        profileCleanupService.metricsCollector.recordPermissionDeniedError("ProfileSyncWorker", userId)
                    } catch (metricsError: Exception) {
                        Timber.w(metricsError, "Failed to record PERMISSION_DENIED metrics")
                    }
                    
                    return Result.failure(
                        Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "Permission denied - user may be orphaned or security rules block access")
                            .putString("error_type", "PERMISSION_DENIED")
                            .putBoolean("requires_server_cleanup", true)
                            .build()
                    )
                } else {
                    // Re-throw other Firestore errors for normal retry handling
                    throw e
                }
            }
            
            Timber.d("[DATA-INTEGRITY] Profile sync completed with field isolation - ${profileOnlyData.size} fields updated")
            
            // 🔒 SYNC ISOLATION - Update only profile sync status, not workout sync status
            userProfileDao.markAsClean(
                ids = listOf(profile.id),
                userId = userId,
                syncVersion = System.currentTimeMillis()
            )
            
            Timber.d("[DATA-INTEGRITY] Profile sync status updated - workout sync status preserved")
            
            // 🔥 FIX: Sync follow relationships using upsert to prevent feed flickering
            try {
                val followCount = followRepository.syncFollowRelationshipsFromFirebaseUpsert(userId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync follow relationships during profile sync")
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
                if (useDirtyFlagGating) {
                    workoutDao.getDirtyWorkouts(userId).size
                } else {
                    workoutDao.getUnsyncedCountForUser(userId)
                }
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
                    val corruptedCount = if (useDirtyFlagGating) {
                        val workouts = workoutDao.getAllWorkoutsForUser(userId).first()
                        workouts.forEach { workout ->
                            workoutDao.upsertLocal(workout)
                        }
                        workouts.size
                    } else {
                        workoutDao.markAllWorkoutsAsUnsyncedForUser(userId)
                    }
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


 
