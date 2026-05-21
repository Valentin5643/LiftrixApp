package com.example.liftrix.domain.interactor.social

import androidx.paging.PagingData
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.usecase.social.FeedGeneratorUseCase
import com.example.liftrix.domain.usecase.social.SearchUsersRequest
import com.example.liftrix.domain.usecase.social.SearchUsersResult
import com.example.liftrix.domain.usecase.social.SocialSearchUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SocialDiscoveryInteractor @Inject constructor(
    private val socialSearchUseCase: SocialSearchUseCase,
    private val feedGeneratorUseCase: FeedGeneratorUseCase
) {
    suspend fun searchProfiles(
        query: String,
        limit: Int = 20
    ): LiftrixResult<List<SocialProfile>> =
        socialSearchUseCase(query, limit)

    suspend fun searchUsers(request: SearchUsersRequest): LiftrixResult<SearchUsersResult> =
        socialSearchUseCase.searchUsers(request)

    suspend fun generateFeed(
        userId: String,
        includeDiscovery: Boolean = false
    ): Flow<PagingData<WorkoutPost>> =
        feedGeneratorUseCase(userId, includeDiscovery)

    suspend fun generateDiscoveryFeed(
        userId: String,
        timeWindowHours: Int = 24
    ): Flow<PagingData<WorkoutPost>> =
        feedGeneratorUseCase.generateDiscoveryFeed(userId, timeWindowHours)
}
