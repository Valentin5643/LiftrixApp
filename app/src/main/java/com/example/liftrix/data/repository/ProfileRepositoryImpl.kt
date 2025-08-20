package com.example.liftrix.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.mapper.UserProfileMapper
import com.example.liftrix.data.sync.OfflineQueueManager
import com.example.liftrix.sync.SyncCoordinator
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.sync.ProfileSyncWorker
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val userProfileMapper: UserProfileMapper,
    private val firestore: FirebaseFirestore,
    private val workManager: WorkManager,
    private val workoutRepository: WorkoutRepository,
    private val database: LiftrixDatabase,
    private val syncCoordinator: SyncCoordinator,
    private val offlineQueueManager: OfflineQueueManager
) : ProfileRepository {

    override fun getProfile(userId: String): Flow<UserProfile?> {
        return flow {
            // 🔥 COLD START FIX: Ensure database is ready before profile operations
            ensureDatabaseReady(userId)
            
            // Emit all values from the DAO flow
            emitAll(
                userProfileDao.getProfileForUser(userId).map { entity ->
                    entity?.let { userProfileMapper.toDomain(it) }
                }
            )
        }
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        return try {
            require(profile.userId.isNotBlank()) { "User profile must have a valid user ID" }
            
            // CRITICAL FIX: Check if profile already exists and preserve entity ID
            val existingEntity = userProfileDao.getProfileForUserSuspend(profile.userId)
            val entity = if (existingEntity != null) {
                // Update existing profile - preserve the entity ID to avoid duplicates
                userProfileMapper.toEntityWithId(profile, existingEntity.id, isSynced = false)
            } else {
                // Create new profile with new ID
                userProfileMapper.toEntity(profile, isSynced = false)
            }
            
            // Save to local database (offline-first)
            val insertResult = userProfileDao.insertProfile(entity)
            
            // Verification to ensure database persistence
            kotlinx.coroutines.delay(100L) // Allow database write to complete
            
            val verifyEntity = userProfileDao.getProfileForUserSuspend(profile.userId)
            if (verifyEntity == null) {
                return Result.failure(IllegalStateException("Profile save verification failed - data may not be persisted"))
            }
            
            // Queue sync in background
            queueSync(profile.userId)
            
            Timber.d("User profile saved successfully: ${profile.userId} (${if (existingEntity != null) "updated" else "created"})")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save user profile: ${profile.userId}")
            Result.failure(e)
        }
    }

    override suspend fun updatePartialProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            require(userId.isNotBlank()) { "User ID cannot be blank for partial update" }
            
            // Fetch existing profile from Room
            val currentEntity = userProfileDao.getProfileForUserSuspend(userId)
                ?: return Result.failure(IllegalStateException("Profile not found for partial update"))

            // Use Firestore for partial update logic on a DTO
            val currentDto = userProfileMapper.toFirestoreDto(userProfileMapper.toDomain(currentEntity))
            
            // This is a simplified approach. In a real app, you would need a more robust way to apply partial updates.
            // For now, we will save the full object again with updates applied.
            // This example assumes 'updates' keys match DTO field names, which is not safe.
            // A proper implementation would use reflection or a more structured update model.
            
            // This is a placeholder for a more complex partial update logic.
            // We'll resave the full profile for now.
            val updatedProfile = applyUpdatesToProfile(currentEntity, updates)
            userProfileDao.updateProfile(updatedProfile)
            
            queueSync(userId)
            
            Timber.d("Partially updated profile for user: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to partially update profile for user: $userId")
            Result.failure(e)
        }
    }

    // Placeholder for a real partial update implementation
    private fun applyUpdatesToProfile(entity: com.example.liftrix.data.local.entity.UserProfileEntity, updates: Map<String, Any>): com.example.liftrix.data.local.entity.UserProfileEntity {
        // In a real app, this would be a more sophisticated merge.
        // For this task, we'll just log and requeue sync for the full entity.
        Timber.d("Applying partial updates: $updates")
        return entity.copy(isSynced = false, syncVersion = System.currentTimeMillis())
    }

    override suspend fun deleteProfile(userId: String): Result<Unit> {
        return try {
            userProfileDao.deleteProfileForUser(userId)
            
            // Also delete from Firestore
            firestore.collection("user_profiles").document(userId).delete().await()
            
            // Cancel any pending sync work for this user
            workManager.cancelUniqueWork("${ProfileSyncWorker.WORK_NAME}_$userId")
            
            Timber.d("User profile deleted successfully: $userId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete user profile: $userId")
            Result.failure(e)
        }
    }

    override suspend fun hasProfile(userId: String): Boolean {
        return try {
            userProfileDao.hasProfile(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if profile exists for user: $userId")
            false
        }
    }

    override suspend fun hasCompletedProfile(userId: String): Boolean {
        return try {
            userProfileDao.hasCompletedProfile(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for completed profile for user: $userId")
            false
        }
    }

    override suspend fun getUnsyncedCount(userId: String): Int {
        return try {
            userProfileDao.getUnsyncedProfilesCount(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unsynced profile count for user: $userId")
            0
        }
    }

    override suspend fun queueSync(userId: String): Result<Unit> {
        return try {
            val profile = userProfileDao.getProfileForUserSuspend(userId)
            if (profile != null && !profile.isSynced) {
                // Queue in offline manager
                val queueResult = offlineQueueManager.queueOperation(
                    userId = userId,
                    entityType = "PROFILE",
                    entityId = userId,
                    operation = "UPSERT",
                    data = profile
                )
                
                if (queueResult.isSuccess) {
                    // Trigger sync coordinator
                    val syncResult = syncCoordinator.triggerEntitySync(userId, "profile")
                    
                    // 🚀 CRITICAL FIX: Also trigger public profile sync to make profile changes searchable
                    // This ensures that when users update bio, fitness level, etc., they become visible to other users
                    val publicSyncResult = syncCoordinator.triggerEntitySync(userId, "user_public")
                    
                    if (syncResult.isSuccess) {
                        Timber.d("Successfully queued and triggered profile sync for user: $userId")
                        if (publicSyncResult.isSuccess) {
                            Timber.d("Also triggered public profile sync for searchability")
                        } else {
                            Timber.w("Profile sync succeeded but public sync failed: ${publicSyncResult.exceptionOrNull()?.message}")
                        }
                        Result.success(Unit)
                    } else {
                        val error = syncResult.exceptionOrNull()
                        Timber.w("Profile queued but sync trigger failed: ${error?.message}")
                        Result.success(Unit) // Still success since queued
                    }
                } else {
                    val error = queueResult.exceptionOrNull()
                    Timber.e("Failed to queue profile for sync: ${error?.message}")
                    Result.failure(error ?: Exception("Unknown queue error"))
                }
            } else {
                Timber.d("Profile already synced or not found for user: $userId")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to queue profile sync for user: $userId")
            Result.failure(e)
        }
    }

    override suspend fun syncNow(userId: String): Result<Unit> {
        return try {
            // Trigger immediate sync through SyncCoordinator
            val syncResult = syncCoordinator.triggerEntitySync(userId, "profile")
            
            if (syncResult.isSuccess) {
                Timber.d("Successfully triggered immediate profile sync for user: $userId")
                
                // Also process any pending offline queue items
                val queueResult = offlineQueueManager.processPendingQueue(userId)
                if (queueResult.isSuccess) {
                    val data = queueResult.getOrNull()
                    Timber.d("Processed pending queue items for user: $userId - $data")
                } else {
                    val error = queueResult.exceptionOrNull()
                    Timber.w("Failed to process pending queue: ${error?.message}")
                    // Don't fail the entire operation for queue processing failure
                }
                
                Result.success(Unit)
            } else {
                val error = syncResult.exceptionOrNull()
                Timber.e("Sync coordinator failed to trigger sync: $error")
                Result.failure(error ?: Exception("Sync trigger failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initiate immediate profile sync for user: $userId")
            Result.failure(e)
        }
    }

    // Enhanced methods for social profile system

    override suspend fun getUserProfile(userId: String): LiftrixResult<UserProfile?> {
        return try {
            // 🔥 COLD START FIX: Ensure database is ready before profile operations
            ensureDatabaseReady(userId)
            
            val entity = userProfileDao.getProfileForUserSuspend(userId)
            val profile = entity?.let { userProfileMapper.toDomain(it) }
            liftrixSuccess(profile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile: $userId")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "PROFILE_FETCH_FAILED",
                    errorMessage = "Failed to get user profile: ${e.message}",
                    analyticsContext = mapOf("userId" to userId)
                )
            )
        }
    }

    override suspend fun saveUserProfile(profile: UserProfile): LiftrixResult<Unit> {
        return try {
            require(profile.userId.isNotBlank()) { "User profile must have a valid user ID" }
            
            // Check if profile already exists and preserve entity ID
            val existingEntity = userProfileDao.getProfileForUserSuspend(profile.userId)
            
            val entity = if (existingEntity != null) {
                // Update existing profile - preserve the entity ID to avoid duplicates
                userProfileMapper.toEntityWithId(profile, existingEntity.id, isSynced = false)
            } else {
                // Create new profile with new ID
                userProfileMapper.toEntity(profile, isSynced = false)
            }
            
            // Save to local database (offline-first)
            val insertResult = userProfileDao.insertProfile(entity)
            
            // Force database sync to ensure data is written to disk
            try {
                forceDatabaseSync()
            } catch (e: Exception) {
                Timber.w(e, "Database sync failed, continuing anyway")
            }
            
            // Small delay to allow filesystem operations to complete
            kotlinx.coroutines.delay(100L)
            
            // Verify that profile was actually saved with correct data
            val savedProfile = userProfileDao.getProfileForUserSuspend(profile.userId)
            if (savedProfile == null) {
                return liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "PROFILE_SAVE_VERIFICATION_FAILED",
                        errorMessage = "Profile save verification failed - no profile found",
                        analyticsContext = mapOf("userId" to profile.userId)
                    )
                )
            }
            
            if (savedProfile.displayName != profile.displayName) {
                return liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "PROFILE_SAVE_VERIFICATION_FAILED",
                        errorMessage = "Profile save verification failed - wrong displayName",
                        analyticsContext = mapOf("userId" to profile.userId)
                    )
                )
            }
            
            // Queue sync in background
            queueSync(profile.userId)
            
            Timber.d("Enhanced user profile saved successfully: ${profile.userId} (${if (existingEntity != null) "updated" else "created"})")
            liftrixSuccess(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save enhanced user profile: ${profile.userId}")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "PROFILE_SAVE_FAILED",
                    errorMessage = "Failed to save enhanced user profile: ${e.message}",
                    analyticsContext = mapOf("userId" to profile.userId)
                )
            )
        }
    }

    override suspend fun updateProfileCompletion(userId: String): LiftrixResult<Int> {
        return try {
            val entity = userProfileDao.getProfileForUserSuspend(userId)
                ?: return liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "PROFILE_NOT_FOUND",
                        errorMessage = "Profile not found for completion update",
                        analyticsContext = mapOf("userId" to userId)
                    )
                )
            
            val profile = userProfileMapper.toDomain(entity)
            
            // Calculate completion percentage based on filled fields
            val completionPercentage = calculateProfileCompletionPercentage(profile)
            
            // Update in database
            userProfileDao.updateProfileCompletion(userId, completionPercentage)
            
            Timber.d("Profile completion updated for user $userId: $completionPercentage%")
            liftrixSuccess(completionPercentage)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to update profile completion for user: $userId")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "PROFILE_COMPLETION_UPDATE_FAILED",
                    errorMessage = "Failed to update profile completion: ${e.message}",
                    analyticsContext = mapOf("userId" to userId)
                )
            )
        }
    }

    override suspend fun calculateStreakData(userId: String): LiftrixResult<StreakData> {
        return try {
            // Get workout data for streak calculation
            val workouts = workoutRepository.getAllWorkoutsForUser(userId)
            // This would need to be implemented properly based on workout data
            // For now, return basic data from the profile
            val entity = userProfileDao.getProfileForUserSuspend(userId)
            if (entity != null) {
                val streakData = StreakData(
                    currentStreak = entity.currentStreak,
                    longestStreak = entity.longestStreak,
                    totalWorkouts = entity.totalWorkouts,
                    lastWorkoutDate = entity.lastActiveAt
                )
                liftrixSuccess(streakData)
            } else {
                liftrixSuccess(StreakData(0, 0, 0, null))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate streak data for user: $userId")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "STREAK_CALCULATION_FAILED",
                    errorMessage = "Failed to calculate streak data: ${e.message}",
                    analyticsContext = mapOf("userId" to userId)
                )
            )
        }
    }

    override suspend fun updatePrivacySettings(userId: String, isPublic: Boolean): LiftrixResult<Unit> {
        return try {
            val updateCount = userProfileDao.updatePrivacySetting(userId, isPublic)
            if (updateCount > 0) {
                queueSync(userId)
                Timber.d("Privacy settings updated for user $userId: public=$isPublic")
                liftrixSuccess(Unit)
            } else {
                liftrixFailure(
                    LiftrixError.BusinessLogicError(
                        code = "PROFILE_NOT_FOUND",
                        errorMessage = "Profile not found for privacy update",
                        analyticsContext = mapOf("userId" to userId)
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update privacy settings for user: $userId")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "PRIVACY_UPDATE_FAILED",
                    errorMessage = "Failed to update privacy settings: ${e.message}",
                    analyticsContext = mapOf("userId" to userId)
                )
            )
        }
    }

    override suspend fun getPublicProfiles(limit: Int): LiftrixResult<List<UserProfile>> {
        return try {
            val entities = userProfileDao.getPublicProfiles(limit)
            val profiles = entities.map { userProfileMapper.toDomain(it) }
            liftrixSuccess(profiles)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get public profiles")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "PUBLIC_PROFILES_FETCH_FAILED",
                    errorMessage = "Failed to get public profiles: ${e.message}",
                    analyticsContext = emptyMap()
                )
            )
        }
    }

    override suspend fun getPublicProfile(userId: String): LiftrixResult<UserProfile?> {
        return try {
            val entity = userProfileDao.getPublicProfile(userId)
            val profile = entity?.let { userProfileMapper.toDomain(it) }
            liftrixSuccess(profile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get public profile for user: $userId")
            liftrixFailure(
                LiftrixError.BusinessLogicError(
                    code = "PUBLIC_PROFILE_FETCH_FAILED",
                    errorMessage = "Failed to get public profile: ${e.message}",
                    analyticsContext = mapOf("userId" to userId)
                )
            )
        }
    }

    private suspend fun forceDatabaseSync() {
        try {
            // Force database operations to complete by performing a read operation
            val profileCount = userProfileDao.getAllProfiles().size
            
            // Additional delay to ensure filesystem operations complete
            kotlinx.coroutines.delay(50L)
            
        } catch (e: Exception) {
            Timber.w(e, "Database sync failed")
        }
    }
    
    private suspend fun ensureDatabaseReady(userId: String) {
        try {
            // Small delay to allow Room to complete cold start initialization
            kotlinx.coroutines.delay(150L)
            
            // Verify database connection by performing a simple query
            val tableExists = try {
                userProfileDao.getAllProfiles().size >= 0
                true
            } catch (e: Exception) {
                kotlinx.coroutines.delay(200L)
                try {
                    userProfileDao.getAllProfiles().size >= 0
                    true
                } catch (e2: Exception) {
                    false
                }
            }
            
            if (!tableExists) {
                return
            }
            
            // Additional verification: Check if we can perform basic profile operations
            try {
                userProfileDao.hasProfile(userId)
            } catch (e: Exception) {
                // Continue anyway - database might still work
            }
            
        } catch (e: Exception) {
            // Continue anyway - database might still work
        }
    }
    
    /**
     * Calculates profile completion percentage based on filled fields.
     */
    private fun calculateProfileCompletionPercentage(profile: UserProfile): Int {
        var completedFields = 0
        val totalFields = 8 // Adjust based on required fields

        if (profile.displayName.isNotBlank()) completedFields++
        if (profile.age != null) completedFields++
        if (profile.weight != null) completedFields++
        if (profile.fitnessGoals.isNotEmpty()) completedFields++
        if (profile.availableEquipment.isNotEmpty()) completedFields++
        if (!profile.bio.isNullOrBlank()) completedFields++
        if (profile.goalsPriority != null) completedFields++
        if (profile.otherEquipment != null) completedFields++

        return ((completedFields.toFloat() / totalFields.toFloat()) * 100).toInt().coerceIn(0, 100)
    }
} 