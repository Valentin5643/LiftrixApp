package com.example.liftrix.domain.usecase.social

import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SeedWorkoutPostsUseCaseTest {
    private lateinit var workoutPostDao: WorkoutPostDao
    private lateinit var socialProfileDao: SocialProfileDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var useCase: SeedWorkoutPostsUseCase

    @Before
    fun setUp() {
        workoutPostDao = mockk()
        socialProfileDao = mockk()
        workoutDao = mockk()
        userProfileDao = mockk()
        useCase = SeedWorkoutPostsUseCase(
            workoutPostDao = workoutPostDao,
            socialProfileDao = socialProfileDao,
            workoutDao = workoutDao,
            userProfileDao = userProfileDao
        )

        coEvery { userProfileDao.insertProfile(any()) } returns 1L
        coEvery { socialProfileDao.getSocialProfileByUserId(any()) } returns null
        coEvery { socialProfileDao.insertProfile(any()) } returns 1L
        coEvery { workoutDao.insertWorkout(any()) } returns 1L
        coEvery { workoutPostDao.getPostById(any()) } returns null
        coEvery { workoutPostDao.insertPost(any()) } returns Unit
    }

    @Test
    fun `invoke seeds deterministic official profiles workouts and posts`() = runTest {
        val socialProfileSlot = slot<SocialProfileEntity>()
        val workoutSlot = slot<WorkoutEntity>()
        val postSlot = slot<WorkoutPostEntity>()
        coEvery { socialProfileDao.insertProfile(capture(socialProfileSlot)) } returns 1L
        coEvery { workoutDao.insertWorkout(capture(workoutSlot)) } returns 1L
        coEvery { workoutPostDao.insertPost(capture(postSlot)) } returns Unit

        val result = useCase("new-user")

        assertTrue(result.isSuccess)
        assertEquals(25, result.getOrThrow())
        coVerify(exactly = 5) { userProfileDao.insertProfile(any<UserProfileEntity>()) }
        coVerify(exactly = 5) { socialProfileDao.insertProfile(any()) }
        coVerify(exactly = 25) { workoutDao.insertWorkout(any<WorkoutEntity>()) }
        coVerify(exactly = 25) { workoutPostDao.insertPost(any()) }
        assertTrue(socialProfileSlot.captured.isVerified)
        assertTrue(workoutSlot.captured.exercisesJson.contains("\"schemaVersion\":1"))
        assertTrue(workoutSlot.captured.exercisesJson.contains("\"sets\":["))
        assertTrue(workoutSlot.captured.exercisesJson.contains("\"repsCount\""))
        assertEquals("PUBLIC", postSlot.captured.visibility)
        assertTrue(postSlot.captured.workoutDuration in 15..50)
        assertTrue(postSlot.captured.mediaUrls.orEmpty().contains("file:///android_asset/official_posts/"))
        assertTrue(postSlot.captured.mediaThumbnails.orEmpty().contains("file:///android_asset/official_posts/"))
        assertTrue(postSlot.captured.mediaUrls.orEmpty().contains(".webp"))
        assertTrue(postSlot.captured.mediaThumbnails.orEmpty().contains(".webp"))
        assertTrue(postSlot.captured.isSynced)
    }

    @Test
    fun `invoke is idempotent and reports zero new posts when posts already exist`() = runTest {
        coEvery { workoutPostDao.getPostById(any()) } returns mockk(relaxed = true)

        val result = useCase("new-user")

        assertEquals(0, result.getOrThrow())
        coVerify(exactly = 25) { workoutPostDao.insertPost(any()) }
    }
}
