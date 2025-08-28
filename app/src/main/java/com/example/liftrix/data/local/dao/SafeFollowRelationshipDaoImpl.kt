package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.room.withTransaction
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.FollowRelationshipEntity
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe implementation for FollowRelationshipDao operations with automatic user validation.
 * Prevents foreign key constraint violations by ensuring referenced users exist before
 * inserting follow relationships.
 * 
 * This addresses the SQLiteConstraintException: FOREIGN KEY constraint failed (code 787)
 * that occurs when sync workers try to insert relationships for users that don't exist locally.
 */
@Singleton
class SafeFollowRelationshipDaoImpl @Inject constructor(
    private val database: LiftrixDatabase
) {
    
    private val followDao get() = database.followRelationshipDao()
    private val socialProfileDao get() = database.socialProfileDao()
    private val userProfileDao get() = database.userProfileDao()

    /**
     * Safely inserts follow relationships with automatic user profile creation.
     * Creates placeholder profiles for any missing users referenced in relationships.
     * Uses a single atomic transaction to prevent partial state.
     * 
     * @param relationships List of relationships to insert
     * @return Number of relationships successfully inserted
     */
    suspend fun insertFollowRelationshipsWithUserValidation(
        relationships: List<FollowRelationshipEntity>
    ): Int = withContext(Dispatchers.IO) {
        if (relationships.isEmpty()) return@withContext 0
        
        return@withContext database.withTransaction {
            try {
                // Step 1: Extract all unique user IDs from relationships
                val allUserIds = relationships.flatMap { 
                    listOf(it.followerId, it.followingId) 
                }.distinct()
                
                
                // Step 2: Check which users are missing from social_profiles
                val existingUserIds = mutableSetOf<String>()
                val missingSocialProfiles = mutableListOf<String>()
                val missingUserProfiles = mutableListOf<String>()
                
                for (userId in allUserIds) {
                    try {
                        val hasUserProfile = userProfileDao.hasProfile(userId)
                        val hasSocialProfile = socialProfileDao.hasProfile(userId)
                        
                        if (!hasUserProfile) {
                            missingUserProfiles.add(userId)
                            Timber.w("🔐 SYNC-SAFE: Missing UserProfile for $userId")
                        }
                        
                        if (!hasSocialProfile) {
                            missingSocialProfiles.add(userId)
                            Timber.w("🔐 SYNC-SAFE: Missing SocialProfile for $userId")
                        }
                        
                        if (hasUserProfile && hasSocialProfile) {
                            existingUserIds.add(userId)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "🔐 SYNC-SAFE: Error checking profile existence for $userId")
                        // Assume missing to be safe
                        missingUserProfiles.add(userId)
                        missingSocialProfiles.add(userId)
                    }
                }
                
                // Step 3: Create missing UserProfileEntity entries (parent table)
                if (missingUserProfiles.isNotEmpty()) {
                    val userStubs = createUserProfileStubs(missingUserProfiles)
                    userStubs.forEach { userStub ->
                        try {
                            userProfileDao.insertProfile(userStub)
                            Timber.i("🔐 SYNC-SAFE: Created UserProfile stub for ${userStub.userId}")
                        } catch (e: Exception) {
                            Timber.e(e, "🔐 SYNC-SAFE: Failed to create UserProfile stub for ${userStub.userId}")
                            throw e // Fail the transaction if we can't create required parent records
                        }
                    }
                }
                
                // Step 4: Create missing SocialProfileEntity entries (child of user_profiles)
                if (missingSocialProfiles.isNotEmpty()) {
                    val socialStubs = createSocialProfileStubs(missingSocialProfiles)
                    socialStubs.forEach { socialStub ->
                        try {
                            socialProfileDao.insertProfile(socialStub)
                            Timber.i("🔐 SYNC-SAFE: Created SocialProfile stub for ${socialStub.userId}")
                        } catch (e: Exception) {
                            Timber.e(e, "🔐 SYNC-SAFE: Failed to create SocialProfile stub for ${socialStub.userId}")
                            throw e // Fail the transaction
                        }
                    }
                }
                
                // Step 5: Now safely insert the relationships (all foreign keys will be satisfied)
                var successCount = 0
                relationships.forEach { relationship ->
                    try {
                        followDao.insertFollowRelationship(relationship)
                        successCount++
                        Timber.v("🔐 SYNC-SAFE: Inserted relationship ${relationship.id}")
                    } catch (e: Exception) {
                        Timber.e(e, "🔐 SYNC-SAFE: Failed to insert relationship ${relationship.id} between ${relationship.followerId} and ${relationship.followingId}")
                        // Log the specific userIds that caused issues for debugging
                        try {
                            val followerExists = socialProfileDao.hasProfile(relationship.followerId)
                            val followingExists = socialProfileDao.hasProfile(relationship.followingId)
                            Timber.e("🔐 SYNC-SAFE: FK violation debug - follower exists: $followerExists, following exists: $followingExists")
                        } catch (debugException: Exception) {
                            Timber.e(debugException, "🔐 SYNC-SAFE: Error checking FK existence during debug")
                        }
                        throw e // Fail the transaction to maintain consistency
                    }
                }
                
                Timber.i("🔐 SYNC-SAFE: Successfully inserted $successCount relationships with user validation")
                successCount
                
            } catch (e: Exception) {
                Timber.e(e, "🔐 SYNC-SAFE: Transaction failed, rolling back relationship inserts")
                throw e // Let Room handle the rollback
            }
        }
    }
    
    /**
     * Creates minimal UserProfileEntity stubs for missing users.
     * These will be updated with full data when ProfileSyncWorker runs.
     */
    private fun createUserProfileStubs(userIds: List<String>): List<UserProfileEntity> {
        val currentDateTime = LocalDateTime.now()
        
        return userIds.map { userId ->
            UserProfileEntity(
                id = userId,
                userId = userId,
                displayName = "User", // Will be updated by ProfileSyncWorker
                age = null,
                weightKg = null,
                heightCm = null,
                fitnessLevel = null,
                goals = null,
                availableEquipment = null,
                workoutFrequency = null,
                preferredWorkoutDuration = null,
                completedAt = null,
                createdAt = currentDateTime,
                updatedAt = currentDateTime,
                isSynced = false, // Will be synced later
                syncVersion = 0L,
                bio = null,
                isPublic = true,
                lastActiveAt = currentDateTime,
                totalWorkouts = 0,
                currentStreak = 0,
                longestStreak = 0,
                memberSince = currentDateTime,
                profileCompletionPercentage = 0,
                profileImageUrl = null,
                profileImageUpdatedAt = null,
                hasCustomProfileImage = false
            )
        }
    }
    
    /**
     * Creates minimal SocialProfileEntity stubs for missing users.
     * These will be updated with full data when ProfileSyncWorker runs.
     */
    private fun createSocialProfileStubs(userIds: List<String>): List<SocialProfileEntity> {
        val currentTime = System.currentTimeMillis()
        
        return userIds.map { userId ->
            SocialProfileEntity(
                userId = userId,
                username = "user_${userId.takeLast(8)}", // Generate unique username stub
                displayName = "User", // Will be updated by ProfileSyncWorker
                bio = null,
                profilePhotoUrl = null,
                coverPhotoUrl = null,
                workoutCount = 0,
                followerCount = 0,
                followingCount = 0,
                memberSince = currentTime,
                lastActive = currentTime,
                isVerified = false,
                isPrivate = false, // Default to public for stubs
                hideFromSuggestions = false,
                allowFriendRequests = true,
                instagramHandle = null,
                youtubeChannel = null,
                personalWebsite = null,
                isSynced = false, // Will be synced later by ProfileSyncWorker
                syncVersion = 0,
                createdAt = currentTime,
                updatedAt = currentTime
            )
        }
    }
}