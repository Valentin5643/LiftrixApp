package com.example.liftrix.demo

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.todayIn
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

@Singleton
class DemoTimelineGenerator @Inject constructor() {
    private var cachedKey: DemoTimelineKey? = null
    private var cachedTimeline: DemoTimeline? = null

    fun generate(sessionSeed: Long, anchorDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())): DemoTimeline {
        val key = DemoTimelineKey(sessionSeed, anchorDate)
        cachedTimeline?.takeIf { cachedKey == key }?.let { return it }

        val timeline = buildTimeline(sessionSeed, anchorDate)
        cachedKey = key
        cachedTimeline = timeline
        return timeline
    }

    private fun buildTimeline(sessionSeed: Long, anchorDate: LocalDate): DemoTimeline {
        val random = Random(sessionSeed)
        val people = demoPeople()
        val startDate = anchorDate.minus(DatePeriod(days = 179))
        val workouts = mutableListOf<DemoWorkout>()
        val achievements = mutableListOf<DemoAchievement>()
        val personalRecords = mutableListOf<DemoPersonalRecord>()
        val exerciseBest = mutableMapOf<String, Double>()
        var workoutIndex = 0

        for (offset in 0..179) {
            val date = startDate.plus(DatePeriod(days = offset))
            val weekday = date.toJavaLocalDate().dayOfWeek.value
            val weekIndex = offset / 7
            val isTrainingDay = weekday in trainingDaysForWeek(weekIndex)
            val isDeload = weekIndex % 8 == 7
            if (!isTrainingDay) continue

            val split = splitFor(workoutIndex)
            val durationMinutes = ((58 + split.durationBias + random.nextInt(-8, 12)) * if (isDeload) 0.82 else 1.0).roundToInt()
            val trendMultiplier = 1.0 + (offset / 179.0) * 0.34
            val deloadMultiplier = if (isDeload) 0.72 else 1.0
            val exercises = split.exercises.mapIndexed { exerciseIndex, exercise ->
                val baseWeight = exercise.baseWeightKg * trendMultiplier * deloadMultiplier
                val sets = if (isDeload) 3 else 3 + ((workoutIndex + exerciseIndex) % 2)
                DemoExerciseBlock(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    muscleGroup = exercise.muscleGroup,
                    sets = (1..sets).map { setIndex ->
                        val reps = exercise.baseReps + random.nextInt(-2, 3)
                        val weight = (baseWeight + setIndex * 1.25 + random.nextDouble(-1.0, 1.5)).roundToNearest(2.5)
                        DemoSet(reps = reps.coerceAtLeast(4), weightKg = weight.coerceAtLeast(8.0))
                    }
                )
            }

            val workout = DemoWorkout(
                id = "demo-workout-${sessionSeed.toString(36)}-$workoutIndex",
                userId = DEMO_USER_ID,
                name = split.name,
                date = date,
                startTime = date.atLocalTime(17 + (workoutIndex % 3), 30),
                durationMinutes = durationMinutes,
                exercises = exercises,
                author = people.first()
            )
            workouts += workout

            exercises.forEach { exercise ->
                val topSet = exercise.sets.maxBy { it.weightKg }
                val previousBest = exerciseBest[exercise.exerciseId] ?: 0.0
                if (topSet.weightKg > previousBest + 2.0 && offset > 21 && !isDeload) {
                    exerciseBest[exercise.exerciseId] = topSet.weightKg
                    val record = DemoPersonalRecord(
                        id = "demo-pr-${workout.id}-${exercise.exerciseId}",
                        workoutId = workout.id,
                        exerciseId = exercise.exerciseId,
                        exerciseName = exercise.name,
                        date = date,
                        weightKg = topSet.weightKg,
                        reps = topSet.reps
                    )
                    personalRecords += record
                    if (personalRecords.size % 3 == 0) {
                        achievements += DemoAchievement(
                            id = "demo-achievement-${personalRecords.size}",
                            workoutId = workout.id,
                            title = "${exercise.name} PR",
                            description = "${topSet.weightKg.roundToInt()} kg for ${topSet.reps} reps",
                            date = date,
                            value = topSet.weightKg
                        )
                    }
                } else {
                    exerciseBest.putIfAbsent(exercise.exerciseId, topSet.weightKg)
                }
            }

            workoutIndex += 1
        }

        val recentWorkout = workouts.lastOrNull()
        val recentActivity = buildList {
            addAll(workouts.takeLast(12).map { workout ->
                DemoActivityEvent(
                    id = "demo-activity-${workout.id}",
                    workoutId = workout.id,
                    person = people.first(),
                    message = "${workout.name} completed",
                    createdAt = workout.endTime
                )
            })
            recentWorkout?.let { workout ->
                add(
                    DemoActivityEvent(
                        id = "demo-activity-fresh-${workout.id}",
                        workoutId = workout.id,
                        person = people.first(),
                        message = "${workout.name} summary updated",
                        createdAt = workout.endTime.plusSeconds(42 * 60)
                    )
                )
            }
        }.sortedByDescending { it.createdAt }

        return DemoTimeline(
            sessionSeed = sessionSeed,
            anchorDate = anchorDate,
            user = people.first(),
            people = people,
            workouts = workouts,
            personalRecords = personalRecords,
            achievements = achievements,
            activityEvents = recentActivity
        )
    }

    private fun trainingDaysForWeek(weekIndex: Int): Set<Int> =
        when (weekIndex % 4) {
            0 -> setOf(1, 3, 5, 6)
            1 -> setOf(1, 2, 4, 6)
            2 -> setOf(2, 4, 5)
            else -> setOf(1, 3, 4, 6, 7)
        }

    private fun splitFor(index: Int): DemoSplit = demoSplits[index % demoSplits.size]

    private fun demoPeople(): List<DemoPerson> = listOf(
        DemoPerson(DEMO_USER_ID, "You", null)
    )
}

data class DemoTimeline(
    val sessionSeed: Long,
    val anchorDate: LocalDate,
    val user: DemoPerson,
    val people: List<DemoPerson>,
    val workouts: List<DemoWorkout>,
    val personalRecords: List<DemoPersonalRecord>,
    val achievements: List<DemoAchievement>,
    val activityEvents: List<DemoActivityEvent>
) {
    val recentWorkouts: List<DemoWorkout> get() = workouts.sortedByDescending { it.date }.take(20)
    val totalVolumeKg: Double get() = workouts.sumOf { it.totalVolumeKg }
}

data class DemoWorkout(
    val id: String,
    val userId: String,
    val name: String,
    val date: LocalDate,
    val startTime: Instant,
    val durationMinutes: Int,
    val exercises: List<DemoExerciseBlock>,
    val author: DemoPerson
) {
    val endTime: Instant = startTime.plusSeconds(durationMinutes * 60L)
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val totalReps: Int get() = exercises.sumOf { block -> block.sets.sumOf { it.reps } }
    val totalVolumeKg: Double get() = exercises.sumOf { it.totalVolumeKg }
}

data class DemoExerciseBlock(
    val exerciseId: String,
    val name: String,
    val muscleGroup: String,
    val sets: List<DemoSet>
) {
    val totalVolumeKg: Double get() = sets.sumOf { it.weightKg * it.reps }
}

data class DemoSet(
    val reps: Int,
    val weightKg: Double
)

data class DemoPersonalRecord(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val exerciseName: String,
    val date: LocalDate,
    val weightKg: Double,
    val reps: Int
)

data class DemoAchievement(
    val id: String,
    val workoutId: String,
    val title: String,
    val description: String,
    val date: LocalDate,
    val value: Double
)

data class DemoActivityEvent(
    val id: String,
    val workoutId: String,
    val person: DemoPerson,
    val message: String,
    val createdAt: Instant
)

data class DemoPerson(
    val userId: String,
    val displayName: String,
    val profileImageUrl: String?
)

private data class DemoTimelineKey(val sessionSeed: Long, val anchorDate: LocalDate)

private data class DemoSplit(
    val name: String,
    val durationBias: Int,
    val exercises: List<DemoExercise>
)

private data class DemoExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val baseWeightKg: Double,
    val baseReps: Int
)

private const val DEMO_USER_ID = "demo-user-local"

private val demoSplits = listOf(
    DemoSplit(
        name = "Upper Strength",
        durationBias = 8,
        exercises = listOf(
            DemoExercise("bench_press", "Bench Press", "Chest", 78.0, 6),
            DemoExercise("barbell_row", "Barbell Row", "Back", 72.0, 8),
            DemoExercise("overhead_press", "Overhead Press", "Shoulders", 44.0, 7),
            DemoExercise("pull_up", "Weighted Pull-Up", "Back", 18.0, 6)
        )
    ),
    DemoSplit(
        name = "Lower Power",
        durationBias = 12,
        exercises = listOf(
            DemoExercise("back_squat", "Back Squat", "Quadriceps", 108.0, 5),
            DemoExercise("romanian_deadlift", "Romanian Deadlift", "Hamstrings", 92.0, 8),
            DemoExercise("walking_lunge", "Walking Lunge", "Glutes", 28.0, 10),
            DemoExercise("standing_calf_raise", "Standing Calf Raise", "Calves", 82.0, 12)
        )
    ),
    DemoSplit(
        name = "Push Hypertrophy",
        durationBias = 2,
        exercises = listOf(
            DemoExercise("incline_dumbbell_press", "Incline Dumbbell Press", "Chest", 30.0, 10),
            DemoExercise("seated_shoulder_press", "Seated Shoulder Press", "Shoulders", 26.0, 9),
            DemoExercise("cable_fly", "Cable Fly", "Chest", 18.0, 12),
            DemoExercise("triceps_pressdown", "Triceps Pressdown", "Triceps", 34.0, 12)
        )
    ),
    DemoSplit(
        name = "Pull Hypertrophy",
        durationBias = 4,
        exercises = listOf(
            DemoExercise("deadlift", "Deadlift", "Back", 132.0, 4),
            DemoExercise("lat_pulldown", "Lat Pulldown", "Back", 62.0, 10),
            DemoExercise("seated_cable_row", "Seated Cable Row", "Back", 58.0, 10),
            DemoExercise("hammer_curl", "Hammer Curl", "Biceps", 18.0, 11)
        )
    ),
    DemoSplit(
        name = "Full Body Conditioning",
        durationBias = -6,
        exercises = listOf(
            DemoExercise("front_squat", "Front Squat", "Quadriceps", 76.0, 6),
            DemoExercise("dumbbell_bench", "Dumbbell Bench Press", "Chest", 32.0, 9),
            DemoExercise("single_arm_row", "Single Arm Row", "Back", 34.0, 10),
            DemoExercise("kettlebell_swing", "Kettlebell Swing", "Glutes", 28.0, 15)
        )
    )
)

private fun LocalDate.atLocalTime(hour: Int, minute: Int): Instant =
    toJavaLocalDate().atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant()

private fun Double.roundToNearest(step: Double): Double = (this / step).roundToInt() * step
