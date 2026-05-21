package com.example.liftrix.domain.interactor.profile

import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.profile.ProfileCommandUseCase
import com.example.liftrix.domain.usecase.profile.ProfileImageOperationsUseCase
import com.example.liftrix.domain.usecase.profile.ProfileQueryUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ProfileInteractor @Inject constructor(
    private val profileQueryUseCase: ProfileQueryUseCase,
    private val profileCommandUseCase: ProfileCommandUseCase,
    private val profileImageOperationsUseCase: ProfileImageOperationsUseCase
) {
    suspend fun observe(userId: String): Flow<UserProfile?> =
        profileQueryUseCase(userId)

    suspend fun getById(userId: String): LiftrixResult<UserProfile?> =
        profileQueryUseCase.getById(userId)

    suspend fun getPublicProfile(userId: String): LiftrixResult<UserProfile?> =
        profileQueryUseCase.getPublicProfile(userId)

    suspend fun hasProfile(userId: String): LiftrixResult<Boolean> =
        profileQueryUseCase.hasProfile(userId)

    suspend fun hasCompletedProfile(userId: String): LiftrixResult<Boolean> =
        profileQueryUseCase.hasCompletedProfile(userId)

    suspend fun getUnsyncedCount(userId: String): LiftrixResult<Int> =
        profileQueryUseCase.getUnsyncedCount(userId)

    suspend fun getPublicProfiles(limit: Int = 50): LiftrixResult<List<UserProfile>> =
        profileQueryUseCase.getPublicProfiles(limit)

    suspend fun saveProfile(
        profile: UserProfile,
        strictValidation: Boolean = false
    ): LiftrixResult<Unit> =
        profileCommandUseCase.saveProfile(profile, strictValidation)

    suspend fun updatePartial(
        userId: String,
        updates: Map<String, Any>
    ): LiftrixResult<Unit> =
        profileCommandUseCase.updatePartial(userId, updates)

    suspend fun deleteProfile(userId: String): LiftrixResult<Unit> =
        profileCommandUseCase.deleteProfile(userId)

    suspend fun syncProfile(userId: String): LiftrixResult<Unit> =
        profileCommandUseCase.syncProfile(userId)

    suspend fun queueSync(userId: String): LiftrixResult<Unit> =
        profileCommandUseCase.queueSync(userId)

    suspend fun updateCompletion(userId: String): LiftrixResult<Int> =
        profileCommandUseCase.updateCompletion(userId)

    suspend fun calculateStreak(userId: String): LiftrixResult<StreakData> =
        profileCommandUseCase.calculateStreak(userId)

    suspend fun updatePrivacy(userId: String, isPublic: Boolean): LiftrixResult<Unit> =
        profileCommandUseCase.updatePrivacy(userId, isPublic)

    suspend fun getImageUrl(userId: String): LiftrixResult<String?> =
        profileImageOperationsUseCase.getImageUrl(userId)

    suspend fun hasCustomImage(userId: String): LiftrixResult<Boolean> =
        profileImageOperationsUseCase.hasCustomImage(userId)

    suspend fun uploadImage(userId: String, imageBytes: ByteArray): LiftrixResult<String> =
        profileImageOperationsUseCase.upload(userId, imageBytes)

    suspend fun deleteImage(userId: String): LiftrixResult<Unit> =
        profileImageOperationsUseCase.delete(userId)

    suspend fun syncImageToSocial(userId: String): LiftrixResult<Unit> =
        profileImageOperationsUseCase.syncToSocial(userId)

    suspend fun uploadAndSyncImage(userId: String, imageBytes: ByteArray): LiftrixResult<String> =
        profileImageOperationsUseCase.uploadAndSync(userId, imageBytes)
}
