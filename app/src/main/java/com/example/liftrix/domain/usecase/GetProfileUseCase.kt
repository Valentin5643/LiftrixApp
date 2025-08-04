package com.example.liftrix.domain.usecase

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for retrieving user profile data.
 * Provides reactive access to profile data with caching and refresh capabilities.
 */
class GetProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {

    /**
     * Retrieves a user's profile as a reactive stream.
     *
     * @param userId The ID of the user whose profile to retrieve
     * @return Flow<UserProfile?> that emits profile updates, null if not found
     */
    suspend operator fun invoke(userId: String): Flow<UserProfile?> {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to get profile with blank user ID")
                throw IllegalArgumentException("User ID cannot be blank")
            }

            profileRepository.getProfile(userId)
        } catch (e: Exception) {
            Timber.e(e, "Exception while getting profile for user: $userId")
            throw e
        }
    }

    /**
     * Forces a refresh of the user's profile from remote source.
     *
     * @param userId The ID of the user whose profile to refresh
     * @return Result<Unit> indicating success or failure of the refresh operation
     */
    suspend fun refreshProfile(userId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                val error = IllegalArgumentException("User ID cannot be blank")
                Timber.e("Attempted to refresh profile with blank user ID")
                return Result.failure(error)
            }

            Timber.d("Refreshing profile for user: $userId")
            val result = profileRepository.syncNow(userId)
            
            if (result.isSuccess) {
                Timber.d("Profile refreshed successfully for user: $userId")
            } else {
                Timber.e("Failed to refresh profile for user: $userId")
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Exception while refreshing profile for user: $userId")
            Result.failure(e)
        }
    }

    /**
     * Checks if a user has a profile.
     *
     * @param userId The ID of the user to check
     * @return True if profile exists, false otherwise
     */
    suspend fun hasProfile(userId: String): Boolean {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to check profile existence with blank user ID")
                return false
            }

            profileRepository.hasProfile(userId)
        } catch (e: Exception) {
            Timber.e(e, "Exception while checking profile existence for user: $userId")
            false
        }
    }

    /**
     * Checks if a user has completed their profile onboarding.
     *
     * @param userId The ID of the user to check
     * @return True if profile is complete, false otherwise
     */
    suspend fun hasCompletedProfile(userId: String): Boolean {
        return try {
            if (userId.isBlank()) {
                Timber.e("Attempted to check profile completion with blank user ID")
                return false
            }

            profileRepository.hasCompletedProfile(userId)
        } catch (e: Exception) {
            Timber.e(e, "Exception while checking profile completion for user: $userId")
            false
        }
    }
} 