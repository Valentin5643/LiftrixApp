package com.example.liftrix.demo

import androidx.paging.PagingData
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.domain.model.social.PostExercise
import com.example.liftrix.domain.model.social.PostVisibility
import com.example.liftrix.domain.model.social.WorkoutAchievement
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.model.social.WorkoutSummary
import com.example.liftrix.feature.home.model.HomeFeedWorkout
import com.example.liftrix.feature.home.model.HomeWorkout
import com.example.liftrix.feature.home.model.HomeWorkoutStats
import com.example.liftrix.feature.home.model.HomeWorkoutStatus
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaLocalDate
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoHomeDataFactory @Inject constructor() {
    fun recentWorkouts(timeline: DemoTimeline, limit: Int): List<HomeWorkout> =
        timeline.recentWorkouts.take(limit).map { it.toHomeWorkout() }

    @Suppress("UNUSED_PARAMETER")
    fun recentActivityFeed(timeline: DemoTimeline, includeOthers: Boolean, limit: Int): List<HomeFeedWorkout> {
        return timeline.recentWorkouts.take(limit).map { workout ->
            HomeFeedWorkout(
                workout = workout.toHomeWorkout(),
                isPersonal = true,
                user = null,
                mediaUrls = emptyList(),
                mediaThumbnails = emptyList()
            )
        }
    }

    fun stats(timeline: DemoTimeline): HomeWorkoutStats {
        val recentWeek = timeline.workouts.takeLast(7)
        val totalMinutes = timeline.workouts.sumOf { it.durationMinutes }
        return HomeWorkoutStats(
            totalWorkouts = timeline.workouts.size,
            currentStreak = calculateCurrentStreak(timeline),
            weeklyVolume = Duration.ofMinutes(recentWeek.sumOf { it.durationMinutes }.toLong()),
            averageWorkoutDuration = Duration.ofMinutes((totalMinutes / timeline.workouts.size.coerceAtLeast(1)).toLong()),
            weeklyWorkouts = recentWeek.size,
            averagePerWeek = timeline.workouts.size / 26.0,
            workoutsThisWeek = recentWeek.size,
            totalMinutesThisWeek = recentWeek.sumOf { it.durationMinutes },
            daysSinceLastWorkout = timeline.anchorDate.toEpochDays() - (timeline.workouts.lastOrNull()?.date?.toEpochDays() ?: timeline.anchorDate.toEpochDays())
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun recommendedUsers(timeline: DemoTimeline, limit: Int, offset: Int): List<RecommendedUser> =
        emptyList()

    fun posts(timeline: DemoTimeline, pageSize: Int): List<WorkoutPost> =
        timeline.recentWorkouts.take(pageSize).mapIndexed { index, workout ->
            val person = timeline.user
            val prs = timeline.personalRecords.filter { it.workoutId == workout.id }
            WorkoutPost(
                id = "demo-post-${workout.id}",
                userId = person.userId,
                workoutId = workout.id,
                caption = captionFor(workout, prs.size),
                workoutDuration = workout.durationMinutes,
                totalVolume = workout.totalVolumeKg,
                exercisesCount = workout.exercises.size,
                prsCount = prs.size,
                achievements = prs.take(2).map {
                    WorkoutAchievement(
                        id = it.id,
                        type = "personal_record",
                        title = "${it.exerciseName} PR",
                        description = "${it.weightKg.toInt()} kg x ${it.reps}",
                        value = it.weightKg
                    )
                },
                workoutSummary = WorkoutSummary(
                    totalSets = workout.totalSets,
                    totalReps = workout.totalReps,
                    totalVolume = workout.totalVolumeKg,
                    exerciseCount = workout.exercises.size,
                    duration = workout.durationMinutes
                ),
                exercises = workout.exercises.map {
                    PostExercise(
                        name = it.name,
                        setsCount = it.sets.size,
                        maxWeight = it.sets.maxOfOrNull { set -> set.weightKg },
                        isPR = prs.any { pr -> pr.exerciseId == it.exerciseId }
                    )
                },
                likeCount = 8 + index * 3,
                commentCount = index % 5,
                shareCount = index % 3,
                saveCount = 2 + index,
                visibility = PostVisibility.PUBLIC,
                createdAt = workout.endTime.toEpochMilli(),
                updatedAt = workout.endTime.toEpochMilli(),
                relevanceScore = 1.0 - (index * 0.03),
                authorUsername = person.displayName.lowercase().replace(" ", "."),
                authorDisplayName = person.displayName,
                authorProfilePhotoUrl = person.profileImageUrl
            )
        }

    fun pagingPosts(timeline: DemoTimeline, pageSize: Int): PagingData<WorkoutPost> =
        PagingData.from(posts(timeline, pageSize))

    private fun DemoWorkout.toHomeWorkout(): HomeWorkout =
        HomeWorkout(
            id = id,
            userId = userId,
            name = name,
            date = date.toJavaLocalDate(),
            exerciseCount = exercises.size,
            totalSets = totalSets,
            completedSetCount = totalSets,
            totalVolumeKg = totalVolumeKg,
            status = HomeWorkoutStatus.COMPLETED,
            startTime = startTime,
            endTime = endTime,
            notes = null
        )

    private fun captionFor(workout: DemoWorkout, prs: Int): String =
        if (prs > 0) {
            "${workout.name}: ${prs} new PRs and ${workout.totalVolumeKg.toInt()} kg moved."
        } else {
            "${workout.name}: ${workout.totalSets} sets in ${workout.durationMinutes} minutes."
        }

    private fun calculateCurrentStreak(timeline: DemoTimeline): Int {
        val workoutDates = timeline.workouts.map { it.date }.toSet()
        var date = timeline.anchorDate
        var streak = 0
        while (date in workoutDates) {
            streak += 1
            date = date.minus(kotlinx.datetime.DatePeriod(days = 1))
        }
        return streak
    }
}
