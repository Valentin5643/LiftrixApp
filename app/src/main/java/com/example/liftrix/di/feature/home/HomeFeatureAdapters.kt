package com.example.liftrix.di.feature.home

import com.example.liftrix.domain.model.FeedWorkout
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutStats
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.FeedType
import com.example.liftrix.domain.model.social.MediaItem
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.toSummary
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.SocialRepository
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.workout.WorkoutAnalyticsDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutFeedDataRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.service.AnalyticsService
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.service.MediaUploadService
import com.example.liftrix.domain.usecase.social.SeedWorkoutPostsUseCase
import com.example.liftrix.domain.usecase.workout.WorkoutQueryUseCase
import com.example.liftrix.feature.home.model.HomeFeedWorkout
import com.example.liftrix.feature.home.model.HomeMediaUploadRequest
import com.example.liftrix.feature.home.model.HomeUser
import com.example.liftrix.feature.home.model.HomeWorkout
import com.example.liftrix.feature.home.model.HomeWorkoutStats
import com.example.liftrix.feature.home.model.HomeWorkoutStatus
import com.example.liftrix.feature.home.model.PostWorkoutSummary
import com.example.liftrix.feature.home.ports.HomeAnalyticsPort
import com.example.liftrix.feature.home.ports.HomeAuthPort
import com.example.liftrix.feature.home.ports.HomeFeedPort
import com.example.liftrix.feature.home.ports.HomeSocialPort
import com.example.liftrix.feature.home.ports.HomeWorkoutPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppHomeAuthAdapter @Inject constructor(
    authRepository: AuthRepository
) : HomeAuthPort {
    override val currentUserId: Flow<String?> = authRepository.currentUser.map { it?.uid }
}

@Singleton
class AppHomeWorkoutAdapter @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val workoutFeedDataRepository: WorkoutFeedDataRepository,
    private val workoutAnalyticsDataRepository: WorkoutAnalyticsDataRepository
) : HomeWorkoutPort {
    override fun getRecentWorkouts(
        userId: String,
        limit: Int
    ): Flow<LiftrixResult<List<HomeWorkout>>> {
        return workoutRepository.getRecentWorkouts(userId, limit).map { result ->
            result.map { workouts -> workouts.mapNotNull { it.toHomeWorkoutOrNull() } }
        }
    }

    override fun getRecentActivityFeed(
        userId: String,
        includeOthers: Boolean,
        limit: Int
    ): Flow<LiftrixResult<List<HomeFeedWorkout>>> {
        return workoutFeedDataRepository.getRecentActivityFeed(userId, includeOthers, limit)
            .map { result ->
                result.map { feedWorkouts -> feedWorkouts.mapNotNull { it.toHomeFeedWorkoutOrNull() } }
            }
            .catch { throwable ->
                Timber.e(throwable, "Failed to adapt recent activity feed")
                emit(LiftrixResult.failure(throwable))
            }
    }

    override suspend fun getWorkoutStats(userId: String): LiftrixResult<HomeWorkoutStats> {
        return workoutAnalyticsDataRepository.getWorkoutStats(userId).map { it.toHomeWorkoutStats() }
    }
}

@Singleton
class AppHomeSocialAdapter @Inject constructor(
    private val socialRepository: SocialRepository
) : HomeSocialPort {
    override fun getRecommendedUsers(limit: Int, offset: Int) =
        socialRepository.getRecommendedUsers(limit, offset)

    override suspend fun refreshDiscoveryCache(): Result<Unit> =
        socialRepository.refreshDiscoveryCache()
}

@Singleton
class AppHomeAnalyticsAdapter @Inject constructor(
    private val analyticsService: AnalyticsService
) : HomeAnalyticsPort {
    override suspend fun logEvent(eventName: String, parameters: Map<String, Any>): Result<Unit> =
        analyticsService.logEvent(eventName, parameters)

    override suspend fun trackFeedLoadTime(duration: Long): Result<Unit> =
        analyticsService.trackFeedLoadTime(duration)

    override suspend fun trackUserDiscoveryEngagement(
        action: String,
        additionalData: Map<String, Any>
    ): Result<Unit> = analyticsService.trackUserDiscoveryEngagement(action, additionalData)
}

@Singleton
class AppHomeFeedAdapter @Inject constructor(
    private val feedRepository: FeedRepository,
    private val engagementRepository: EngagementRepository,
    private val seedWorkoutPostsUseCase: SeedWorkoutPostsUseCase,
    private val analyticsTracker: AnalyticsTracker
) : HomeFeedPort {
    override fun getFeed(userId: String, feedType: FeedType, targetUserId: String?, pageSize: Int) =
        feedRepository.getFeed(userId, feedType, targetUserId, pageSize)

    override fun getHomeFeed(userId: String, pageSize: Int) =
        feedRepository.getHomeFeed(userId, pageSize)

    override fun getDiscoveryFeed(userId: String, pageSize: Int) =
        feedRepository.getDiscoveryFeed(userId, pageSize)

    override suspend fun createPost(userId: String, request: CreateWorkoutPostRequest): LiftrixResult<WorkoutPost> =
        feedRepository.createPost(userId, request)

    override suspend fun refreshFeed(userId: String): LiftrixResult<Unit> =
        feedRepository.refreshFeed(userId)

    override suspend fun seedWorkoutPosts(userId: String): LiftrixResult<Int> =
        seedWorkoutPostsUseCase(userId)

    override suspend fun toggleLike(postId: String, userId: String): LiftrixResult<Boolean> =
        engagementRepository.toggleLike(postId, userId)

    override suspend fun toggleSave(postId: String, userId: String): LiftrixResult<Boolean> =
        engagementRepository.toggleSave(postId, userId)

    override suspend fun copyWorkoutFromPost(postId: String, userId: String): LiftrixResult<String> =
        engagementRepository.copyWorkoutFromPost(postId, userId)

    override fun trackShare(
        contentType: String,
        contentId: String,
        platform: String,
        userId: String,
        hasCustomMessage: Boolean,
        additionalProperties: Map<String, Any>
    ) = analyticsTracker.trackShare(contentType, contentId, platform, userId, hasCustomMessage, additionalProperties)

    override fun trackEngagement(
        action: String,
        contentType: String,
        contentId: String,
        contentOwnerUserId: String,
        userId: String,
        additionalProperties: Map<String, Any>
    ) = analyticsTracker.trackEngagement(action, contentType, contentId, contentOwnerUserId, userId, additionalProperties)

    override fun trackError(
        errorType: String,
        errorMessage: String,
        additionalProperties: Map<String, Any>
    ) = analyticsTracker.trackError(errorType, errorMessage, additionalProperties)
}

@Singleton
class AppPostCreationAdapter @Inject constructor(
    private val workoutQueryUseCase: WorkoutQueryUseCase,
    private val mediaUploadService: MediaUploadService
) : com.example.liftrix.feature.home.ports.PostCreationPort {
    override suspend fun getWorkoutSummary(
        workoutId: String,
        userId: String
    ): LiftrixResult<PostWorkoutSummary?> {
        return workoutQueryUseCase.getById(com.example.liftrix.domain.model.WorkoutId(workoutId), userId).map { workout ->
            workout?.toSummary()?.let { summary ->
                PostWorkoutSummary(
                    id = summary.id.value,
                    name = summary.name,
                    durationMinutes = summary.duration?.toMinutes()?.toInt() ?: 0,
                    totalVolume = workout.calculateTotalVolumeKg(),
                    exerciseCount = summary.exerciseCount,
                    prsCount = 0
                )
            }
        }
    }

    override suspend fun uploadMediaItems(
        userId: String,
        mediaRequests: List<HomeMediaUploadRequest>
    ): LiftrixResult<List<MediaItem>> {
        return mediaUploadService.uploadMediaItems(
            userId = userId,
            mediaRequests = mediaRequests.map {
                com.example.liftrix.domain.model.social.MediaUploadRequest(
                    uri = it.uri,
                    type = it.type,
                    caption = it.caption,
                    compressionQuality = it.compressionQuality,
                    maxFileSizeMB = it.maxFileSizeMB
                )
            }
        )
    }
}

private fun FeedWorkout.toHomeFeedWorkoutOrNull(): HomeFeedWorkout? =
    workout.toHomeWorkoutOrNull()?.let { homeWorkout ->
        HomeFeedWorkout(
            workout = homeWorkout,
            isPersonal = isPersonal,
            user = user?.toHomeUser(),
            mediaUrls = mediaUrls,
            mediaThumbnails = mediaThumbnails
        )
    }

private fun User.toHomeUser() = HomeUser(
    uid = uid,
    displayName = displayName,
    photoUrl = photoUrl
)

private fun Workout.toHomeWorkoutOrNull(): HomeWorkout? {
    return runCatching {
        HomeWorkout(
            id = id.value,
            userId = userId,
            name = name,
            date = date,
            exerciseCount = exercises.size,
            totalSets = getTotalSets(),
            completedSetCount = getCompletedSets(),
            totalVolumeKg = calculateTotalVolumeKg(),
            status = when (status) {
                com.example.liftrix.domain.model.WorkoutStatus.PLANNED -> HomeWorkoutStatus.PLANNED
                com.example.liftrix.domain.model.WorkoutStatus.IN_PROGRESS -> HomeWorkoutStatus.IN_PROGRESS
                com.example.liftrix.domain.model.WorkoutStatus.PAUSED -> HomeWorkoutStatus.PAUSED
                com.example.liftrix.domain.model.WorkoutStatus.COMPLETED -> HomeWorkoutStatus.COMPLETED
                com.example.liftrix.domain.model.WorkoutStatus.CANCELLED -> HomeWorkoutStatus.CANCELLED
            },
            startTime = startTime,
            endTime = endTime,
            notes = notes
        )
    }.onFailure { throwable ->
        Timber.e(throwable, "Skipping invalid home workout ${id.value}")
    }.getOrNull()
}

private fun WorkoutStats.toHomeWorkoutStats() = HomeWorkoutStats(
    totalWorkouts = totalWorkouts,
    currentStreak = currentStreak,
    weeklyVolume = weeklyVolume,
    averageWorkoutDuration = averageWorkoutDuration,
    weeklyWorkouts = weeklyWorkouts,
    averagePerWeek = averagePerWeek,
    workoutsThisWeek = workoutsThisWeek,
    totalMinutesThisWeek = totalMinutesThisWeek,
    daysSinceLastWorkout = daysSinceLastWorkout
)
