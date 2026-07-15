package com.example.liftrix.data.repository.social

import com.example.liftrix.data.local.dao.FeedCacheDao
import com.example.liftrix.data.local.dao.FollowRelationshipDao
import com.example.liftrix.data.local.dao.PostLikeDao
import com.example.liftrix.data.local.dao.SavedPostDao
import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.data.mapper.WorkoutPostMapper
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.service.FeedCacheService
import com.example.liftrix.domain.sync.SyncScheduler
import io.mockk.Runs
import io.mockk.capture
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRepositoryImplTest {

    private val workoutPostDao: WorkoutPostDao = mockk()
    private val postLikeDao: PostLikeDao = mockk()
    private val savedPostDao: SavedPostDao = mockk()
    private val socialProfileDao: SocialProfileDao = mockk()
    private val userProfileDao: UserProfileDao = mockk()
    private val workoutDao: WorkoutDao = mockk()
    private val workoutPostMapper: WorkoutPostMapper = mockk()
    private val feedCacheService: FeedCacheService = mockk()
    private val feedCacheDao: FeedCacheDao = mockk()
    private val followRelationshipDao: FollowRelationshipDao = mockk()
    private val authRepository: AuthRepository = mockk()
    private val syncScheduler: SyncScheduler = mockk(relaxed = true)

    private val repository = FeedRepositoryImpl(
        workoutPostDao = workoutPostDao,
        postLikeDao = postLikeDao,
        savedPostDao = savedPostDao,
        socialProfileDao = socialProfileDao,
        userProfileDao = userProfileDao,
        workoutDao = workoutDao,
        workoutPostMapper = workoutPostMapper,
        feedCacheService = feedCacheService,
        feedCacheDao = feedCacheDao,
        followRelationshipDao = followRelationshipDao,
        authRepository = authRepository,
        syncScheduler = syncScheduler
    )

    @Test
    fun `createPost creates a missing social profile before inserting the post`() = runTest {
        val userId = "user-123"
        val workoutId = "workout-123"
        val request = CreateWorkoutPostRequest(
            workoutId = workoutId,
            visibility = PostVisibility.PRIVATE
        )
        val entity = WorkoutPostEntity(
            id = "post-123",
            userId = userId,
            workoutId = workoutId,
            visibility = PostVisibility.PRIVATE.name,
            createdAt = 1L,
            updatedAt = 1L
        )
        val domainPost = WorkoutPost(
            id = entity.id,
            userId = userId,
            workoutId = workoutId,
            visibility = PostVisibility.PRIVATE,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

        var storedProfile: SocialProfileEntity? = null
        val profileSlot = slot<SocialProfileEntity>()
        coEvery { socialProfileDao.getSocialProfileByUserId(userId) } answers { storedProfile }
        coEvery { userProfileDao.getProfileForUserSuspend(userId) } returns null
        coEvery { userProfileDao.getProfileImageUrl(userId) } returns null
        coEvery { authRepository.getCurrentUserId() } returns null
        coEvery { socialProfileDao.isUsernameAvailable(any()) } returns true
        coEvery { socialProfileDao.insertProfile(capture(profileSlot)) } answers {
            storedProfile = profileSlot.captured
            1L
        }
        coEvery { workoutDao.getWorkoutByIdForUser(workoutId, userId) } returns null
        every {
            workoutPostMapper.createEntityFromRequest(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns entity
        coEvery { workoutPostDao.upsertLocal(entity) } just Runs
        coEvery { postLikeDao.isPostLikedByUser(entity.id, userId) } returns false
        coEvery { savedPostDao.isPostSaved(userId, entity.id) } returns false
        coEvery {
            workoutPostMapper.toDomain(
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns domainPost

        val result = repository.createPost(userId, request)

        assertTrue(result.isSuccess)
        assertEquals(domainPost, result.getOrNull())
        coVerifyOrder {
            socialProfileDao.insertProfile(any())
            workoutPostDao.upsertLocal(entity)
        }
    }
}
