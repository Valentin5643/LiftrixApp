package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all profile query operations.
 */
@Singleton
class ProfileQueryUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String): Flow<UserProfile?> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        return profileRepository.getProfile(userId)
    }

    suspend fun getById(userId: String): LiftrixResult<UserProfile?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_FETCH_FAILED",
                errorMessage = "Failed to fetch profile: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.getUserProfile(userId).getOrThrow()
    }

    suspend fun getPublicProfile(userId: String): LiftrixResult<UserProfile?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PUBLIC_PROFILE_FETCH_FAILED",
                errorMessage = "Failed to fetch public profile: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.getPublicProfile(userId).getOrThrow()
    }

    suspend fun hasProfile(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_CHECK_FAILED",
                errorMessage = "Failed to check profile existence: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.hasProfile(userId)
    }

    suspend fun hasCompletedProfile(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_COMPLETION_CHECK_FAILED",
                errorMessage = "Failed to check profile completion: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.hasCompletedProfile(userId)
    }

    suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UNSYNCED_COUNT_FAILED",
                errorMessage = "Failed to get unsynced count: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.getUnsyncedCount(userId)
    }

    suspend fun getPublicProfiles(limit: Int = 50): LiftrixResult<List<UserProfile>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PUBLIC_PROFILES_FETCH_FAILED",
                errorMessage = "Failed to fetch public profiles: ${throwable.message}",
                analyticsContext = mapOf("limit" to limit.toString())
            )
        }
    ) {
        require(limit > 0) { "Limit must be greater than 0" }
        profileRepository.getPublicProfiles(limit).getOrThrow()
    }
}
