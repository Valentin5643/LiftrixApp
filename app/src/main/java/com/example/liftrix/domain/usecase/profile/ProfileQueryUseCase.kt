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
 *
 * **Replaces**:
 * - GetProfileUseCase.kt
 *
 * **Design Philosophy**:
 * - CQRS pattern for read operations
 * - Consistent LiftrixResult error handling
 * - User scoping enforced for all operations
 * - Flow-based reactive data access
 *
 * **Usage Examples**:
 * ```kotlin
 * // Get profile as Flow (replaces GetProfileUseCase.invoke)
 * val profileFlow = profileQueryUseCase.invoke(userId)
 *
 * // Get profile once (new method)
 * val profileResult = profileQueryUseCase.getById(userId)
 *
 * // Check profile existence (replaces GetProfileUseCase.hasProfile)
 * val exists = profileQueryUseCase.hasProfile(userId)
 *
 * // Check profile completion (replaces GetProfileUseCase.hasCompletedProfile)
 * val isComplete = profileQueryUseCase.hasCompletedProfile(userId)
 * ```
 *
 * @property profileRepository Repository for profile data access
 */
@Singleton
class ProfileQueryUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {

    /**
     * Retrieves a user's profile as a reactive stream.
     * Replaces GetProfileUseCase.invoke()
     *
     * @param userId The ID of the user whose profile to retrieve
     * @return Flow<UserProfile?> that emits profile updates, null if not found
     */
    suspend operator fun invoke(userId: String): Flow<UserProfile?> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        return profileRepository.getProfile(userId)
    }

    /**
     * Retrieves a user's profile as a single value with error handling.
     * New method for non-reactive use cases.
     *
     * @param userId The ID of the user whose profile to retrieve
     * @return LiftrixResult<UserProfile?> containing profile or error
     */
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

    /**
     * Gets a public profile if available.
     *
     * @param userId The ID of the user whose public profile to retrieve
     * @return LiftrixResult<UserProfile?> containing public profile or null
     */
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

    /**
     * Checks if a user has a profile.
     * Replaces GetProfileUseCase.hasProfile()
     *
     * @param userId The ID of the user to check
     * @return LiftrixResult<Boolean> indicating if profile exists
     */
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

    /**
     * Checks if a user has completed their profile onboarding.
     * Replaces GetProfileUseCase.hasCompletedProfile()
     *
     * @param userId The ID of the user to check
     * @return LiftrixResult<Boolean> indicating if profile is complete
     */
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

    /**
     * Gets the count of unsynced profiles for a user.
     * New method for sync monitoring.
     *
     * @param userId The ID of the user to check
     * @return LiftrixResult<Int> with unsynced count
     */
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

    /**
     * Gets public profiles for discovery.
     * New method for social features.
     *
     * @param limit Maximum number of profiles to return
     * @return LiftrixResult<List<UserProfile>> with public profiles
     */
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
