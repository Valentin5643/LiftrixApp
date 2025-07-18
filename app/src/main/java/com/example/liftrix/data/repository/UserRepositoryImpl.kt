package com.example.liftrix.data.repository

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileRepository
import com.example.liftrix.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UserRepository that wraps ProfileRepository.
 * 
 * This adapter provides the LiftrixResult-based interface expected by services
 * while delegating to the existing ProfileRepository implementation.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val profileRepository: ProfileRepository
) : UserRepository {
    
    override suspend fun getUserProfile(userId: String): LiftrixResult<UserProfile?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get user profile for user: $userId"
                )
            }
        ) {
            profileRepository.getProfile(userId).first()
        }
    }
    
    override suspend fun saveUserProfile(profile: UserProfile): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to save user profile for user: ${profile.userId}"
                )
            }
        ) {
            profileRepository.saveProfile(profile).getOrThrow()
        }
    }
    
    override suspend fun hasUserProfile(userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check user profile existence for user: $userId"
                )
            }
        ) {
            profileRepository.hasProfile(userId)
        }
    }
    
    override suspend fun deleteUserProfile(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete user profile for user: $userId"
                )
            }
        ) {
            profileRepository.deleteProfile(userId).getOrThrow()
        }
    }
}