package com.example.liftrix.data.repository.social

import androidx.work.WorkManager
import com.example.liftrix.data.local.dao.BlockedUserDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.repository.social.SocialProfileRepository
import com.example.liftrix.sync.UserPublicSyncWorker
import com.example.liftrix.sync.SocialProfileSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SocialProfileRepository with user scoping and privacy enforcement.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All operations enforce user scoping at DAO level to prevent data leakage.
 */
@Singleton
class SocialProfileRepositoryImpl @Inject constructor(
    private val socialProfileDao: SocialProfileDao,
    private val blockedUserDao: BlockedUserDao,
    private val userAccountDao: UserAccountDao,
    private val workManager: WorkManager
) : SocialProfileRepository {

    // ========================================
    // Profile Retrieval
    // ========================================

    override fun observeProfile(userId: String): Flow<SocialProfile?> {
        return socialProfileDao.observeProfile(userId).map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun getProfile(userId: String, viewerId: String?): LiftrixResult<SocialProfile?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get social profile",
                    operation = "GET_SOCIAL_PROFILE",
                    analyticsContext = mapOf("user_id" to userId, "viewer_id" to (viewerId ?: "anonymous"))
                )
            }
        ) {
            val entity = socialProfileDao.getProfile(userId)
            val profile = entity?.toDomainModel()
            
            // Apply privacy filtering if viewer is different from profile owner
            if (profile != null && viewerId != null && viewerId != userId) {
                // Check if viewer is blocked
                val isBlocked = blockedUserDao.hasBlockRelationship(viewerId, userId)
                if (isBlocked) {
                    return@liftrixCatching null // Blocked users cannot see profile
                }
                
                // Follower relationship checking handled by relationship service
                // For now, allow viewing if profile is not private
                if (profile.isPrivate) {
                    return@liftrixCatching null // Private profiles not visible to non-followers
                }
            }
            
            profile
        }
    }

    override suspend fun getProfileByUsername(viewerId: String, username: String): LiftrixResult<SocialProfile?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get profile by username",
                    operation = "GET_PROFILE_BY_USERNAME",
                    analyticsContext = mapOf("username" to username, "viewer_id" to viewerId)
                )
            }
        ) {
            val entity = socialProfileDao.getProfileByUsername(viewerId, username)
            val profile = entity?.toDomainModel()
            
            // Apply same privacy filtering as getProfile
            if (profile != null && viewerId != profile.userId) {
                val isBlocked = blockedUserDao.hasBlockRelationship(viewerId, profile.userId)
                if (isBlocked || profile.isPrivate) {
                    return@liftrixCatching null
                }
            }
            
            profile
        }
    }

    override suspend fun hasProfile(userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check profile existence",
                    operation = "HAS_SOCIAL_PROFILE",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            socialProfileDao.hasProfile(userId)
        }
    }

    // ========================================
    // Username Management
    // ========================================

    override suspend fun checkUsernameAvailability(username: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    field = "username",
                    violations = listOf("Failed to check username availability"),
                    errorMessage = "Failed to check username availability",
                    analyticsContext = mapOf("username" to username)
                )
            }
        ) {
            socialProfileDao.isUsernameAvailable(username)
        }
    }
    
    override suspend fun cleanupOrphanedUsername(username: String, currentUserId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "CLEANUP_ORPHANED_USERNAME_FAILED",
                    errorMessage = "Failed to cleanup orphaned username",
                    analyticsContext = mapOf("username" to username, "user_id" to currentUserId)
                )
            }
        ) {
            Timber.i("SocialProfileRepository", "Attempting username cleanup for: $username")
            
            // Check if there's an existing profile with this username
            val existingProfile = socialProfileDao.getProfileByUsername("", username) // Empty viewerId for internal check
            
            if (existingProfile != null) {
                // Found existing profile, checking validity
                
                // Check if the existing profile belongs to a valid user account
                val existingUserAccount = userAccountDao.getAccountForUserSuspend(existingProfile.userId)
                
                if (existingUserAccount == null) {
                    // Orphaned profile - the user account no longer exists
                    Timber.i("SocialProfileRepository", "Deleting orphaned profile for username: $username")
                    
                    socialProfileDao.deleteProfileForUser(existingProfile.userId)
                    Timber.i("SocialProfileRepository", "Successfully deleted orphaned profile")
                    return@liftrixCatching true // Username is now available
                } else {
                    // Profile belongs to a valid user account
                    if (existingUserAccount.userId == currentUserId) {
                        // It's the current user's old profile - can reuse
                        // Profile belongs to current user, can reuse
                        return@liftrixCatching true
                    } else {
                        // Profile belongs to another valid user
                        // Profile belongs to another valid user, cannot cleanup
                        return@liftrixCatching false
                    }
                }
            } else {
                // No existing profile found - username should be available
                // No existing profile found, username available
                return@liftrixCatching true
            }
        }
    }

    // ========================================
    // Profile Discovery
    // ========================================

    override suspend fun getDiscoverableProfiles(viewerId: String, limit: Int): LiftrixResult<List<SocialProfile>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get discoverable profiles",
                    operation = "GET_DISCOVERABLE_PROFILES",
                    analyticsContext = mapOf("viewer_id" to viewerId, "limit" to limit.toString())
                )
            }
        ) {
            val entities = socialProfileDao.getDiscoverableProfiles(viewerId, limit)
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun searchProfiles(viewerId: String, query: String, limit: Int): LiftrixResult<List<SocialProfile>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to search profiles",
                    operation = "SEARCH_PROFILES",
                    analyticsContext = mapOf("viewer_id" to viewerId, "query" to query, "limit" to limit.toString())
                )
            }
        ) {
            val entities = socialProfileDao.searchProfiles(viewerId, query, limit)
            entities.map { it.toDomainModel() }
        }
    }

    // ========================================
    // Profile Management
    // ========================================

    override suspend fun createProfile(profile: SocialProfile): LiftrixResult<SocialProfile> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "CREATE_SOCIAL_PROFILE",
                    errorMessage = "Failed to create social profile",
                    analyticsContext = mapOf("user_id" to profile.userId, "username" to profile.username)
                )
            }
        ) {
            val entity = profile.toEntity()
            socialProfileDao.insertProfile(entity)
            
            // Also update the username in UserAccountEntity for consistency
            userAccountDao.updateUsername(profile.userId, profile.username)
            Timber.d("Updated username in UserAccountEntity for user: ${profile.userId}")
            
            // Trigger sync to make the profile searchable immediately
            val userPublicSyncRequest = UserPublicSyncWorker.createWorkRequest(profile.userId, forceSync = true)
            val socialProfileSyncRequest = SocialProfileSyncWorker.createWorkRequest(profile.userId, forceSync = true)
            
            workManager.enqueue(userPublicSyncRequest)
            workManager.enqueue(socialProfileSyncRequest)
            
            Timber.d("Triggered sync workers after social profile creation for user: ${profile.userId}")
            
            profile
        }
    }

    override suspend fun updateProfile(userId: String, updates: SocialProfileRepository.ProfileUpdate): LiftrixResult<SocialProfile> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_SOCIAL_PROFILE",
                    errorMessage = "Failed to update social profile",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val currentProfile = socialProfileDao.getProfile(userId)
                ?: throw IllegalStateException("Profile not found for user: $userId")
            
            val updatedAt = System.currentTimeMillis()
            
            socialProfileDao.updateBasicInfo(
                userId = userId,
                displayName = updates.displayName ?: currentProfile.displayName,
                bio = updates.bio ?: currentProfile.bio,
                updatedAt = updatedAt
            )
            
            // Update other fields if provided
            updates.profilePhotoUrl?.let {
                socialProfileDao.updateProfilePhoto(userId, it, updatedAt)
            }
            
            // Return updated profile
            val updatedEntity = socialProfileDao.getProfile(userId)!!
            updatedEntity.toDomainModel()
        }
    }

    override suspend fun updateProfilePhoto(userId: String, photoUrl: String?): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_PROFILE_PHOTO",
                    errorMessage = "Failed to update profile photo",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val updatedAt = System.currentTimeMillis()
            socialProfileDao.updateProfilePhoto(userId, photoUrl, updatedAt)
        }
    }

    override suspend fun updatePrivacySetting(userId: String, isPrivate: Boolean): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_PRIVACY_SETTING",
                    errorMessage = "Failed to update privacy setting",
                    analyticsContext = mapOf("user_id" to userId, "is_private" to isPrivate.toString())
                )
            }
        ) {
            val updatedAt = System.currentTimeMillis()
            socialProfileDao.updatePrivacySetting(userId, isPrivate, updatedAt)
        }
    }

    // ========================================
    // Social Stats
    // ========================================

    override suspend fun updateWorkoutCount(userId: String, count: Int): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_WORKOUT_COUNT",
                    errorMessage = "Failed to update workout count",
                    analyticsContext = mapOf("user_id" to userId, "count" to count.toString())
                )
            }
        ) {
            val updatedAt = System.currentTimeMillis()
            socialProfileDao.updateWorkoutCount(userId, count, updatedAt)
        }
    }

    override suspend fun updateFollowerCount(userId: String, count: Int): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_FOLLOWER_COUNT",
                    errorMessage = "Failed to update follower count",
                    analyticsContext = mapOf("user_id" to userId, "count" to count.toString())
                )
            }
        ) {
            val updatedAt = System.currentTimeMillis()
            socialProfileDao.updateFollowerCount(userId, count, updatedAt)
        }
    }

    override suspend fun updateFollowingCount(userId: String, count: Int): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "UPDATE_FOLLOWING_COUNT",
                    errorMessage = "Failed to update following count",
                    analyticsContext = mapOf("user_id" to userId, "count" to count.toString())
                )
            }
        ) {
            val updatedAt = System.currentTimeMillis()
            socialProfileDao.updateFollowingCount(userId, count, updatedAt)
        }
    }

    // ========================================
    // Profile Deletion
    // ========================================

    override suspend fun deleteProfile(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "DELETE_SOCIAL_PROFILE",
                    errorMessage = "Failed to delete social profile",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            socialProfileDao.deleteProfileForUser(userId)
        }
    }

    // ========================================
    // Mapping Extensions
    // ========================================

    /**
     * Converts SocialProfileEntity to domain model
     */
    private fun SocialProfileEntity.toDomainModel(): SocialProfile {
        return SocialProfile(
            userId = userId,
            username = username,
            displayName = displayName,
            bio = bio,
            profilePhotoUrl = profilePhotoUrl,
            coverPhotoUrl = coverPhotoUrl,
            workoutCount = workoutCount,
            followerCount = followerCount,
            followingCount = followingCount,
            memberSince = memberSince,
            lastActive = lastActive,
            isVerified = isVerified,
            isPrivate = isPrivate,
            hideFromSuggestions = hideFromSuggestions,
            allowFriendRequests = allowFriendRequests,
            instagramHandle = instagramHandle,
            youtubeChannel = youtubeChannel,
            personalWebsite = personalWebsite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Converts domain model to SocialProfileEntity
     */
    private fun SocialProfile.toEntity(): SocialProfileEntity {
        return SocialProfileEntity(
            userId = userId,
            username = username,
            displayName = displayName,
            bio = bio,
            profilePhotoUrl = profilePhotoUrl,
            coverPhotoUrl = coverPhotoUrl,
            workoutCount = workoutCount,
            followerCount = followerCount,
            followingCount = followingCount,
            memberSince = memberSince,
            lastActive = lastActive,
            isVerified = isVerified,
            isPrivate = isPrivate,
            hideFromSuggestions = hideFromSuggestions,
            allowFriendRequests = allowFriendRequests,
            instagramHandle = instagramHandle,
            youtubeChannel = youtubeChannel,
            personalWebsite = personalWebsite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}