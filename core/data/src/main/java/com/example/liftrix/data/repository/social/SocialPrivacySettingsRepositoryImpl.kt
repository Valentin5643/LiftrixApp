package com.example.liftrix.data.repository.social

import com.example.liftrix.data.local.dao.SocialPrivacySettingsDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.entity.SocialPrivacySettingsEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.ProfileVisibility
import com.example.liftrix.domain.model.social.WorkoutVisibility
import com.example.liftrix.domain.repository.social.SocialPrivacySettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SocialPrivacySettingsRepository with user scoping and privacy enforcement.
 * Part of social infrastructure foundation from SPEC-20250113-social-infrastructure.
 * 
 * CRITICAL SECURITY: All operations enforce user scoping at DAO level to prevent data leakage.
 */
@Singleton
class SocialPrivacySettingsRepositoryImpl @Inject constructor(
    private val socialPrivacySettingsDao: SocialPrivacySettingsDao,
    private val userProfileDao: UserProfileDao
) : SocialPrivacySettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // ========================================
    // Privacy Settings Creation
    // ========================================

    override suspend fun createPrivacySettings(userId: String): LiftrixResult<SocialPrivacySettings> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to create privacy settings",
                    operation = "CREATE_PRIVACY_SETTINGS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val entity = SocialPrivacySettingsEntity.createDefault(userId)
            socialPrivacySettingsDao.insertPrivacySettings(entity)
            entity.toDomainModel()
        }
    }

    // ========================================
    // Privacy Settings Retrieval
    // ========================================

    override suspend fun getPrivacySettings(userId: String): LiftrixResult<SocialPrivacySettings?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get privacy settings",
                    operation = "GET_PRIVACY_SETTINGS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val entity = socialPrivacySettingsDao.getPrivacySettings(userId)
            entity?.toDomainModel()
        }
    }

    override fun observePrivacySettings(userId: String): Flow<SocialPrivacySettings?> {
        return socialPrivacySettingsDao.observePrivacySettings(userId)
            .map { entity -> entity?.toDomainModel() }
    }

    // ========================================
    // Privacy Settings Update
    // ========================================

    override suspend fun updatePrivacySettings(settings: SocialPrivacySettings): LiftrixResult<SocialPrivacySettings> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update privacy settings",
                    operation = "UPDATE_PRIVACY_SETTINGS",
                    analyticsContext = mapOf("user_id" to settings.userId)
                )
            }
        ) {
            val entity = settings.toEntity()
            socialPrivacySettingsDao.upsertLocal(entity)

            userProfileDao.getProfileForUserSuspend(settings.userId)?.let { profile ->
                val isPublicProfile = settings.profileVisibility == ProfileVisibility.PUBLIC
                userProfileDao.upsertLocal(profile.copy(isPublic = isPublicProfile))
            }
            settings.copy(updatedAt = System.currentTimeMillis())
        }
    }

    // ========================================
    // Privacy Settings Deletion
    // ========================================

    override suspend fun deletePrivacySettings(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete privacy settings",
                    operation = "DELETE_PRIVACY_SETTINGS",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            socialPrivacySettingsDao.deletePrivacySettingsForUser(userId)
        }
    }

    // ========================================
    // Entity <-> Domain Model Mapping
    // ========================================

    private fun SocialPrivacySettingsEntity.toDomainModel(): SocialPrivacySettings {
        val notificationSettingsMap = try {
            if (notificationSettings.isEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, String>>(notificationSettings)
            }
        } catch (e: Exception) {
            Timber.w("Failed to parse notification settings JSON: $notificationSettings", e)
            emptyMap()
        }

        return SocialPrivacySettings(
            userId = userId,
            socialEnabled = socialEnabled,
            profileVisibility = when (profileVisibility) {
                SocialPrivacySettingsEntity.PROFILE_VISIBILITY_PUBLIC -> ProfileVisibility.PUBLIC
                SocialPrivacySettingsEntity.PROFILE_VISIBILITY_FOLLOWERS -> ProfileVisibility.FOLLOWERS
                SocialPrivacySettingsEntity.PROFILE_VISIBILITY_PRIVATE -> ProfileVisibility.PRIVATE
                else -> ProfileVisibility.PRIVATE // Default to most private
            },
            allowFollowRequests = allowFollowRequests,
            workoutSharingEnabled = workoutSharingEnabled,
            gymBuddiesEnabled = gymBuddiesEnabled,
            communityParticipation = communityParticipation,
            challengeParticipation = challengeParticipation,
            routineSharingEnabled = routineSharingEnabled,
            defaultWorkoutVisibility = when (defaultWorkoutVisibility) {
                SocialPrivacySettingsEntity.WORKOUT_VISIBILITY_PUBLIC -> WorkoutVisibility.PUBLIC
                SocialPrivacySettingsEntity.WORKOUT_VISIBILITY_FOLLOWERS -> WorkoutVisibility.FOLLOWERS
                SocialPrivacySettingsEntity.WORKOUT_VISIBILITY_PRIVATE -> WorkoutVisibility.PRIVATE
                else -> WorkoutVisibility.PRIVATE // Default to most private
            },
            showWorkoutStats = showWorkoutStats,
            showAchievements = showAchievements,
            showWorkoutStreak = showWorkoutStreak,
            hideFromSuggestions = hideFromSuggestions,
            hideFromSearch = hideFromSearch,
            notificationSettings = notificationSettingsMap,
            updatedAt = updatedAt
        )
    }

    private fun SocialPrivacySettings.toEntity(): SocialPrivacySettingsEntity {
        val notificationSettingsJson = try {
            if (notificationSettings.isEmpty()) {
                "{}"
            } else {
                json.encodeToString(notificationSettings)
            }
        } catch (e: Exception) {
            Timber.w("Failed to serialize notification settings to JSON", e)
            "{}"
        }

        return SocialPrivacySettingsEntity(
            userId = userId,
            socialEnabled = socialEnabled,
            profileVisibility = when (profileVisibility) {
                ProfileVisibility.PUBLIC -> SocialPrivacySettingsEntity.PROFILE_VISIBILITY_PUBLIC
                ProfileVisibility.FOLLOWERS -> SocialPrivacySettingsEntity.PROFILE_VISIBILITY_FOLLOWERS
                ProfileVisibility.PRIVATE -> SocialPrivacySettingsEntity.PROFILE_VISIBILITY_PRIVATE
            },
            allowFollowRequests = allowFollowRequests,
            workoutSharingEnabled = workoutSharingEnabled,
            gymBuddiesEnabled = gymBuddiesEnabled,
            communityParticipation = communityParticipation,
            challengeParticipation = challengeParticipation,
            routineSharingEnabled = routineSharingEnabled,
            defaultWorkoutVisibility = when (defaultWorkoutVisibility) {
                WorkoutVisibility.PUBLIC -> SocialPrivacySettingsEntity.WORKOUT_VISIBILITY_PUBLIC
                WorkoutVisibility.FOLLOWERS -> SocialPrivacySettingsEntity.WORKOUT_VISIBILITY_FOLLOWERS
                WorkoutVisibility.PRIVATE -> SocialPrivacySettingsEntity.WORKOUT_VISIBILITY_PRIVATE
            },
            showWorkoutStats = showWorkoutStats,
            showAchievements = showAchievements,
            showWorkoutStreak = showWorkoutStreak,
            hideFromSuggestions = hideFromSuggestions,
            hideFromSearch = hideFromSearch,
            notificationSettings = notificationSettingsJson,
            isSynced = false, // Mark as unsynced when updated
            syncVersion = 0,
            updatedAt = updatedAt
        )
    }
}
