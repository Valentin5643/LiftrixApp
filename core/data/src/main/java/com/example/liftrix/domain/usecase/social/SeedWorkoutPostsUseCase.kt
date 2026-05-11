package com.example.liftrix.domain.usecase.social

import com.example.liftrix.data.local.dao.SocialProfileDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutPostDao
import com.example.liftrix.data.local.entity.SocialProfileEntity
import com.example.liftrix.data.local.entity.UserProfileEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.local.entity.WorkoutPostEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Seeds deterministic official Liftrix profiles and posts for first-run social feed content.
 */
class SeedWorkoutPostsUseCase @Inject constructor(
    private val workoutPostDao: WorkoutPostDao,
    private val socialProfileDao: SocialProfileDao,
    private val workoutDao: WorkoutDao,
    private val userProfileDao: UserProfileDao
) {

    suspend operator fun invoke(currentUserId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SEED_POSTS_FAILED",
                errorMessage = "Failed to seed official workout posts: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SEED_OFFICIAL_WORKOUT_POSTS",
                    "user_id" to currentUserId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            val accounts = OfficialLiftrixAccountCatalog.accounts
            var seededPosts = 0

            accounts.forEach { account ->
                userProfileDao.insertProfile(account.toUserProfileEntity())
                socialProfileDao.insertProfile(account.toSocialProfileEntity())

                account.posts.forEachIndexed { index, post ->
                    val accountIndex = accounts.indexOf(account)
                    val workoutId = workoutId(account.id, index)
                    workoutDao.insertWorkout(post.toWorkoutEntity(account.id, workoutId, accountIndex, index))

                    val existingPost = workoutPostDao.getPostById(postId(account.id, index))
                    workoutPostDao.insertPost(
                        post.toWorkoutPostEntity(
                            userId = account.id,
                            workoutId = workoutId,
                            accountIndex = accountIndex,
                            index = index,
                            postImageUrl = account.postImageUrl,
                            existingPost = existingPost
                        )
                    )
                    if (existingPost == null) {
                        seededPosts++
                    }
                }
            }

            Timber.d("Official Liftrix seed content ensured. New posts inserted: $seededPosts")
            seededPosts
        }
    }

    private fun OfficialAccount.toUserProfileEntity(): UserProfileEntity {
        val now = LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()
        return UserProfileEntity(
            id = id,
            userId = id,
            displayName = displayName,
            age = null,
            weightKg = null,
            heightCm = null,
            fitnessLevel = "OFFICIAL",
            goals = null,
            availableEquipment = null,
            workoutFrequency = null,
            preferredWorkoutDuration = null,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
            isSynced = true,
            syncVersion = 1L,
            isDirty = false,
            lastModified = nowMillis,
            bio = bio,
            isPublic = true,
            lastActiveAt = now,
            totalWorkouts = posts.size,
            memberSince = now,
            profileCompletionPercentage = 100,
            searchKeywords = "$handle $displayName Liftrix official",
            profileImageUrl = profilePhotoUrl,
            profileImageUpdatedAt = now,
            hasCustomProfileImage = true
        )
    }

    private suspend fun OfficialAccount.toSocialProfileEntity(): SocialProfileEntity {
        val existing = socialProfileDao.getSocialProfileByUserId(id)
        val now = System.currentTimeMillis()
        return SocialProfileEntity(
            userId = id,
            username = handle,
            displayName = displayName,
            bio = bio,
            profilePhotoUrl = profilePhotoUrl,
            coverPhotoUrl = null,
            workoutCount = posts.size,
            followerCount = existing?.followerCount ?: 0,
            followingCount = existing?.followingCount ?: 0,
            memberSince = now,
            lastActive = now,
            isVerified = verified,
            isPrivate = false,
            hideFromSuggestions = false,
            allowFriendRequests = true,
            instagramHandle = null,
            youtubeChannel = null,
            personalWebsite = null,
            isSynced = true,
            syncVersion = 1L,
            isDirty = false,
            lastModified = now,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun OfficialPost.toWorkoutEntity(
        userId: String,
        workoutId: String,
        accountIndex: Int,
        index: Int
    ): WorkoutEntity {
        val createdAt = createdAtFor(accountIndex, index)
        val start = Instant.ofEpochMilli(createdAt - durationMinutes * MINUTE_MILLIS)
        val end = Instant.ofEpochMilli(createdAt)
        val exercisesJson = createExercisesJson(userId, workoutId, accountIndex, index)

        return WorkoutEntity(
            id = workoutId,
            userId = userId,
            name = workoutName,
            date = LocalDate.ofInstant(end, ZoneId.systemDefault()),
            exercisesJson = exercisesJson,
            status = WorkoutStatus.COMPLETED,
            startTime = start,
            endTime = end,
            notes = caption,
            templateId = null,
            createdAt = start,
            updatedAt = end,
            isSynced = true,
            syncVersion = 1L,
            isDirty = false,
            lastModified = createdAt
        )
    }

    private fun OfficialPost.toWorkoutPostEntity(
        userId: String,
        workoutId: String,
        accountIndex: Int,
        index: Int,
        postImageUrl: String,
        existingPost: WorkoutPostEntity?
    ): WorkoutPostEntity {
        val timestamp = createdAtFor(accountIndex, index)
        val mediaJson = seedJson.encodeToString(listOf(postImageUrl))
        val engagement = realisticEngagementFor(accountIndex, index)
        return WorkoutPostEntity(
            id = postId(userId, index),
            userId = userId,
            workoutId = workoutId,
            caption = caption,
            mediaUrls = mediaJson,
            mediaThumbnails = mediaJson,
            workoutDuration = durationMinutes,
            totalVolume = totalVolume,
            exercisesCount = exercises.size,
            prsCount = 0,
            likeCount = maxOf(existingPost?.likeCount ?: 0, engagement.likes),
            commentCount = maxOf(existingPost?.commentCount ?: 0, engagement.comments),
            shareCount = existingPost?.shareCount ?: 0,
            saveCount = maxOf(existingPost?.saveCount ?: 0, engagement.saves),
            visibility = "PUBLIC",
            createdAt = timestamp,
            updatedAt = timestamp,
            isSynced = true,
            syncVersion = 1L,
            isDirty = false,
            lastModified = timestamp,
            isHidden = existingPost?.isHidden ?: false,
            hiddenReason = existingPost?.hiddenReason,
            hiddenAt = existingPost?.hiddenAt,
            hiddenByUserId = existingPost?.hiddenByUserId
        )
    }

    private fun OfficialPost.createExercisesJson(
        userId: String,
        workoutId: String,
        accountIndex: Int,
        postIndex: Int
    ): String {
        val completedAt = createdAtFor(accountIndex, postIndex)
        val weightedExerciseCount = exercises.size.coerceAtLeast(1)
        val baseWeight = if (totalVolume > 0.0) {
            (totalVolume / (weightedExerciseCount * 3 * 10)).coerceAtLeast(5.0)
        } else {
            null
        }
        val exerciseDtos = exercises.mapIndexed { exerciseIndex, exerciseName ->
            val weightKg = baseWeight?.let { roundToNearestHalf(it + exerciseIndex * 2.5) }
            val reps = when {
                exerciseName.contains("Walk", ignoreCase = true) -> 20
                exerciseName.contains("Hold", ignoreCase = true) -> 30
                else -> 10
            }
            SeedExercise(
                id = "${workoutId}_exercise_${exerciseIndex + 1}",
                workoutId = workoutId,
                libraryExerciseName = exerciseName,
                orderIndex = exerciseIndex,
                targetSets = 3,
                targetReps = reps,
                targetWeightKg = weightKg,
                sets = List(3) { setIndex ->
                    SeedExerciseSet(
                        id = "${workoutId}_exercise_${exerciseIndex + 1}_set_${setIndex + 1}",
                        setNumber = setIndex + 1,
                        repsCount = reps,
                        weightKg = weightKg,
                        rpeValue = if (weightKg == null) null else 7,
                        completedAtEpochMilli = completedAt - (3 - setIndex) * MINUTE_MILLIS
                    )
                },
                notes = "Official Liftrix seed workout",
                createdAtEpochMilli = completedAt
            )
        }

        return seedJson.encodeToString(
            SeedWorkoutData(
                exercises = exerciseDtos,
                metadata = SeedWorkoutMetadata(
                    totalVolumeKg = totalVolume,
                    totalSets = exerciseDtos.sumOf { it.sets.size },
                    exerciseCount = exerciseDtos.size,
                    createdAt = completedAt
                )
            )
        )
    }

    private fun roundToNearestHalf(value: Double): Double = kotlin.math.round(value * 2.0) / 2.0

    private fun createdAtFor(accountIndex: Int, postIndex: Int): Long {
        val now = System.currentTimeMillis()
        val baseAgeMinutes = postIndex * 170L + accountIndex * 23L
        return (now - baseAgeMinutes * MINUTE_MILLIS).coerceAtLeast(now - DAY_MILLIS + MINUTE_MILLIS)
    }

    private fun realisticEngagementFor(accountIndex: Int, postIndex: Int): SeedEngagement {
        val sessionCount = ((System.currentTimeMillis() / (4 * 60 * MINUTE_MILLIS)) % 6).toInt() + 1
        val base = 8 + accountIndex * 3 + (5 - postIndex)
        return SeedEngagement(
            likes = base + sessionCount * 2,
            saves = (base / 3) + sessionCount,
            comments = if ((accountIndex + postIndex + sessionCount) % 3 == 0) 1 + sessionCount / 3 else 0
        )
    }

    private fun workoutId(accountId: String, index: Int): String = "${accountId}_workout_${index + 1}"

    private fun postId(accountId: String, index: Int): String = "${accountId}_post_${index + 1}"

    private companion object {
        private const val DAY_MILLIS = 86_400_000L
        private const val MINUTE_MILLIS = 60_000L
        private val seedJson = Json {
            encodeDefaults = true
        }
    }
}

private data class SeedEngagement(
    val likes: Int,
    val saves: Int,
    val comments: Int
)

@Serializable
private data class SeedWorkoutData(
    val schemaVersion: Int = 1,
    val exercises: List<SeedExercise>,
    val metadata: SeedWorkoutMetadata,
    val serializationFormat: String = "kotlinx.serialization"
)

@Serializable
private data class SeedWorkoutMetadata(
    val totalVolumeKg: Double,
    val totalSets: Int,
    val exerciseCount: Int,
    val createdAt: Long,
    val format: String = "kotlinx_serialization_v4"
)

@Serializable
private data class SeedExercise(
    val id: String,
    val workoutId: String,
    val libraryExerciseName: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double? = null,
    val sets: List<SeedExerciseSet>,
    val notes: String? = null,
    val createdAtEpochMilli: Long
)

@Serializable
private data class SeedExerciseSet(
    val id: String,
    val setNumber: Int,
    val repsCount: Int? = null,
    val weightKg: Double? = null,
    val rpeValue: Int? = null,
    val completedAtEpochMilli: Long? = null,
    val notes: String? = null
)
