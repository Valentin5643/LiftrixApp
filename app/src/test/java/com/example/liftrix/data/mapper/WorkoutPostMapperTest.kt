package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.social.CreateWorkoutPostRequest
import com.example.liftrix.domain.model.social.PostVisibility
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class WorkoutPostMapperTest {

    private val workoutDao: WorkoutDao = mockk()
    private val customExerciseDao: CustomExerciseDao = mockk()
    private val mapper = WorkoutPostMapper(workoutDao, customExerciseDao)

    @Test
    fun createEntityFromRequest_marksLocalPostPendingSync() {
        val entity = mapper.createEntityFromRequest(
            id = "post-1",
            userId = "user-1",
            request = CreateWorkoutPostRequest(
                workoutId = "workout-1",
                visibility = PostVisibility.PUBLIC
            )
        )

        assertEquals(false, entity.isSynced)
        assertEquals(true, entity.isDirty)
        assertEquals(entity.createdAt, entity.lastModified)
        assertEquals("PUBLIC", entity.visibility)
    }

    @Test
    fun toDomain_mapsKotlinxSerializedExerciseNameIntoPostExercises() = runTest {
        val userId = "user-1"
        val workoutId = "workout-1"

        coEvery { workoutDao.getWorkoutByIdForUser(workoutId, userId) } returns workoutEntity(
            workoutId = workoutId,
            userId = userId,
            exercisesJson = """
                {
                  "schemaVersion": 1,
                  "exercises": [
                    {
                      "id": "exercise-1",
                      "workoutId": "$workoutId",
                      "libraryExerciseName": "Arnold Press",
                      "orderIndex": 0,
                      "sets": [
                        {
                          "id": "set-1",
                          "setNumber": 1,
                          "repsCount": 10,
                          "weightKg": 22.5,
                          "completedAtEpochMilli": 1710000000000
                        }
                      ],
                      "createdAtEpochMilli": 1710000000000
                    }
                  ],
                  "metadata": {
                    "totalVolumeKg": 225.0,
                    "totalSets": 1,
                    "exerciseCount": 1,
                    "createdAt": 1710000000000,
                    "format": "kotlinx_serialization_v4"
                  },
                  "serializationFormat": "kotlinx.serialization"
                }
            """.trimIndent()
        )
        every { customExerciseDao.searchCustomExercises(userId, "Arnold Press") } returns flowOf(emptyList())

        val post = mapper.toDomain(postEntity(workoutId = workoutId, userId = userId))

        assertEquals(1, post.exercises.size)
        assertEquals("Arnold Press", post.exercises.single().name)
        assertEquals(1, post.exercises.single().setsCount)
        assertEquals(22.5, post.exercises.single().maxWeight)
    }

    @Test
    fun toDomain_usesBodyweightDefaultImageWhenPostHasNoMediaAndAllExercisesAreBodyweight() = runTest {
        val userId = "user-1"
        val workoutId = "workout-1"

        coEvery { workoutDao.getWorkoutByIdForUser(workoutId, userId) } returns workoutEntity(
            workoutId = workoutId,
            userId = userId,
            exercisesJson = """
                [
                  {
                    "libraryExerciseName": "Push-up",
                    "equipment": "BODYWEIGHT_ONLY",
                    "sets": []
                  },
                  {
                    "libraryExerciseName": "Pull-up",
                    "equipment": "PULL_UP_BAR",
                    "sets": []
                  }
                ]
            """.trimIndent()
        )
        every { customExerciseDao.searchCustomExercises(userId, any()) } returns flowOf(emptyList())

        val post = mapper.toDomain(postEntity(workoutId = workoutId, userId = userId))

        assertEquals(1, post.mediaItems.size)
        assertEquals(
            WorkoutPostDefaultImageMapper.BODYWEIGHT_DEFAULT_IMAGE_URI,
            post.mediaItems.single().originalUrl
        )
        assertEquals(emptyList(), post.mediaUrls)
    }

    @Test
    fun toDomain_usesGymDefaultImageWhenPostHasNoMediaAndAnyExerciseIsWeighted() = runTest {
        val userId = "user-1"
        val workoutId = "workout-1"

        coEvery { workoutDao.getWorkoutByIdForUser(workoutId, userId) } returns workoutEntity(
            workoutId = workoutId,
            userId = userId,
            exercisesJson = """
                [
                  {
                    "libraryExerciseName": "Bench Press",
                    "equipment": "BARBELL",
                    "sets": []
                  },
                  {
                    "libraryExerciseName": "Push-up",
                    "equipment": "BODYWEIGHT_ONLY",
                    "sets": []
                  }
                ]
            """.trimIndent()
        )
        every { customExerciseDao.searchCustomExercises(userId, any()) } returns flowOf(emptyList())

        val post = mapper.toDomain(postEntity(workoutId = workoutId, userId = userId))

        assertEquals(1, post.mediaItems.size)
        assertEquals(
            WorkoutPostDefaultImageMapper.GYM_DEFAULT_IMAGE_URI,
            post.mediaItems.single().originalUrl
        )
    }

    @Test
    fun toDomain_keepsUserSelectedImageWhenPostAlreadyHasMedia() = runTest {
        val userId = "user-1"
        val workoutId = "workout-1"
        val userImageUrl = "https://example.com/user-workout.jpg"

        coEvery { workoutDao.getWorkoutByIdForUser(workoutId, userId) } returns workoutEntity(
            workoutId = workoutId,
            userId = userId,
            exercisesJson = """
                [
                  {
                    "libraryExerciseName": "Push-up",
                    "equipment": "BODYWEIGHT_ONLY",
                    "sets": []
                  }
                ]
            """.trimIndent()
        )
        every { customExerciseDao.searchCustomExercises(userId, any()) } returns flowOf(emptyList())

        val post = mapper.toDomain(
            postEntity(
                workoutId = workoutId,
                userId = userId,
                mediaUrls = """["$userImageUrl"]"""
            )
        )

        assertEquals(listOf(userImageUrl), post.mediaUrls)
        assertEquals(1, post.mediaItems.size)
        assertEquals(userImageUrl, post.mediaItems.single().originalUrl)
    }

    private fun postEntity(
        workoutId: String,
        userId: String,
        mediaUrls: String? = null
    ) = WorkoutPostEntity(
        id = "post-1",
        userId = userId,
        workoutId = workoutId,
        caption = null,
        mediaUrls = mediaUrls,
        mediaThumbnails = null,
        workoutDuration = 45,
        totalVolume = 225.0,
        exercisesCount = 1,
        prsCount = 0,
        likeCount = 0,
        commentCount = 0,
        shareCount = 0,
        saveCount = 0,
        visibility = "FOLLOWERS",
        createdAt = 1710000000000,
        updatedAt = 1710000000000
    )

    private fun workoutEntity(
        workoutId: String,
        userId: String,
        exercisesJson: String
    ) = WorkoutEntity(
        id = workoutId,
        userId = userId,
        name = "Shoulders",
        date = LocalDate.of(2026, 5, 9),
        exercisesJson = exercisesJson,
        status = WorkoutStatus.COMPLETED,
        startTime = Instant.ofEpochMilli(1710000000000),
        endTime = Instant.ofEpochMilli(1710002700000),
        notes = null,
        templateId = null,
        createdAt = Instant.ofEpochMilli(1710000000000),
        updatedAt = Instant.ofEpochMilli(1710002700000)
    )
}
