package com.example.liftrix.domain.interactor.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.SocialPrivacySettings
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.usecase.social.GetPublicProfileRequest
import com.example.liftrix.domain.usecase.social.GetPublicProfileResult
import com.example.liftrix.domain.usecase.social.SocialProfileCommandUseCase
import com.example.liftrix.domain.usecase.social.SocialProfileQueryUseCase
import javax.inject.Inject

class SocialProfileInteractor @Inject constructor(
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    private val socialProfileCommandUseCase: SocialProfileCommandUseCase
) {
    suspend fun getProfile(userId: String): LiftrixResult<SocialProfile?> =
        socialProfileQueryUseCase(userId)

    suspend fun getPublicProfile(request: GetPublicProfileRequest): LiftrixResult<GetPublicProfileResult> =
        socialProfileQueryUseCase.getPublicProfile(request)

    suspend fun getDiscoverableProfiles(limit: Int = 50): LiftrixResult<List<SocialProfile>> =
        socialProfileQueryUseCase.getDiscoverableProfiles(limit)

    suspend fun checkUsernameAvailability(username: String): LiftrixResult<Boolean> =
        socialProfileQueryUseCase.checkUsernameAvailability(username)

    suspend fun create(
        username: String,
        displayName: String,
        bio: String?,
        profilePhotoUrl: String? = null
    ): LiftrixResult<SocialProfile> =
        socialProfileCommandUseCase.create(username, displayName, bio, profilePhotoUrl)

    suspend fun update(
        displayName: String? = null,
        bio: String? = null,
        profilePhotoUrl: String? = null,
        coverPhotoUrl: String? = null,
        instagramHandle: String? = null,
        youtubeChannel: String? = null,
        personalWebsite: String? = null
    ): LiftrixResult<SocialProfile> =
        socialProfileCommandUseCase.update(
            displayName = displayName,
            bio = bio,
            profilePhotoUrl = profilePhotoUrl,
            coverPhotoUrl = coverPhotoUrl,
            instagramHandle = instagramHandle,
            youtubeChannel = youtubeChannel,
            personalWebsite = personalWebsite
        )

    suspend fun updatePrivacySettings(
        privacySettings: SocialPrivacySettings
    ): LiftrixResult<SocialPrivacySettings> =
        socialProfileCommandUseCase.updatePrivacySettings(privacySettings)
}
