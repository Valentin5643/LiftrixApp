package com.example.liftrix.feature.home.ports

import androidx.paging.PagingData
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.feature.home.model.HomeFeedWorkout
import com.example.liftrix.feature.home.model.HomeMediaUploadRequest
import com.example.liftrix.feature.home.model.HomeWorkout
import com.example.liftrix.feature.home.model.HomeWorkoutStats
import com.example.liftrix.feature.home.model.PostWorkoutSummary
import kotlinx.coroutines.flow.Flow

interface HomeAuthPort {
    val currentUserId: Flow<String?>
}

interface HomeWorkoutPort {
    fun getRecentWorkouts(
        userId: String,
        limit: Int = 10
    ): Flow<LiftrixResult<List<HomeWorkout>>>

    fun getRecentActivityFeed(
        userId: String,
        includeOthers: Boolean = true,
        limit: Int = 20
    ): Flow<LiftrixResult<List<HomeFeedWorkout>>>

    suspend fun getWorkoutStats(userId: String): LiftrixResult<HomeWorkoutStats>
}

interface HomeSocialPort {
    fun getRecommendedUsers(limit: Int, offset: Int): Flow<List<RecommendedUser>>

    suspend fun refreshDiscoveryCache(): Result<Unit>
}

interface HomeAnalyticsPort {
    suspend fun logEvent(eventName: String, parameters: Map<String, Any>): Result<Unit>

    suspend fun trackFeedLoadTime(duration: Long): Result<Unit>

    suspend fun trackUserDiscoveryEngagement(
        action: String,
        additionalData: Map<String, Any> = emptyMap()
    ): Result<Unit>
}

interface HomeFeedPort {
    fun getFeed(
        userId: String,
        feedType: FeedType,
        targetUserId: String? = null,
        pageSize: Int = 20
    ): Flow<PagingData<WorkoutPost>>

    fun getHomeFeed(userId: String, pageSize: Int = 20): Flow<PagingData<WorkoutPost>>

    fun getDiscoveryFeed(userId: String, pageSize: Int = 20): Flow<PagingData<WorkoutPost>>

    suspend fun createPost(
        userId: String,
        request: CreateWorkoutPostRequest
    ): LiftrixResult<WorkoutPost>

    suspend fun refreshFeed(userId: String): LiftrixResult<Unit>

    suspend fun seedWorkoutPosts(userId: String): LiftrixResult<Int>

    suspend fun toggleLike(postId: String, userId: String): LiftrixResult<Boolean>

    suspend fun toggleSave(postId: String, userId: String): LiftrixResult<Boolean>

    suspend fun copyWorkoutFromPost(postId: String, userId: String): LiftrixResult<String>

    fun trackShare(
        contentType: String,
        contentId: String,
        platform: String,
        userId: String,
        hasCustomMessage: Boolean = false,
        additionalProperties: Map<String, Any> = emptyMap()
    )

    fun trackEngagement(
        action: String,
        contentType: String,
        contentId: String,
        contentOwnerUserId: String,
        userId: String,
        additionalProperties: Map<String, Any> = emptyMap()
    )

    fun trackError(
        errorType: String,
        errorMessage: String,
        additionalProperties: Map<String, Any> = emptyMap()
    )
}

interface PostCreationPort {
    suspend fun getWorkoutSummary(
        workoutId: String,
        userId: String
    ): LiftrixResult<PostWorkoutSummary?>

    suspend fun uploadMediaItems(
        userId: String,
        mediaRequests: List<HomeMediaUploadRequest>
    ): LiftrixResult<List<MediaItem>>
}
