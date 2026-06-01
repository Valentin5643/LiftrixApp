package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.WorkoutStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AnalyticsReadModelDaoTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var readModelDao: AnalyticsReadModelDao
    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var exerciseSetDao: ExerciseSetDao
    private lateinit var exerciseLibraryDao: ExerciseLibraryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftrixDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        readModelDao = database.analyticsReadModelDao()
        workoutDao = database.workoutDao()
        exerciseDao = database.exerciseDao()
        exerciseSetDao = database.exerciseSetDao()
        exerciseLibraryDao = database.exerciseLibraryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshWorkoutReadModels_matchesNormalizedSourceAggregates() = runBlocking {
        seedExerciseLibrary()
        seedWorkout("workout-1", LocalDate.parse("2026-05-01"), benchWeight = 100f)

        readModelDao.refreshWorkoutReadModels("user-1", "workout-1")

        val normalizedCompleted = workoutDao.getCompletedWorkoutMetricsInDateRange(
            userId = "user-1",
            startDate = "2026-05-01",
            endDate = "2026-05-01"
        ).single()
        val completedReadModel = readModelDao.getCompletedWorkoutMetrics(
            userId = "user-1",
            startDate = "2026-05-01",
            endDate = "2026-05-01"
        ).single()

        assertEquals(normalizedCompleted.totalVolume, completedReadModel.totalVolume, 0.001)
        assertEquals(normalizedCompleted.totalReps, completedReadModel.totalReps)
        assertEquals(normalizedCompleted.totalSets, completedReadModel.totalSets)
        assertEquals(normalizedCompleted.exerciseCount, completedReadModel.exerciseCount)

        val daily = readModelDao.getDailyVolumes("user-1", "2026-05-01", "2026-05-01").single()
        assertEquals(normalizedCompleted.totalVolume, daily.totalVolume, 0.001)
        assertEquals(1, daily.workoutCount)
        assertEquals(normalizedCompleted.durationMinutes, daily.totalDurationMinutes)

        val muscleGroups = readModelDao.getMuscleGroupVolumeSummary(
            userId = "user-1",
            startDate = "2026-05-01",
            endDate = "2026-05-01"
        )
        val normalizedMuscles = exerciseSetDao.getVolumeDataByMuscleGroup(
            userId = "user-1",
            startDate = "2026-05-01",
            endDate = "2026-05-01"
        )
        assertEquals(
            normalizedMuscles.associate { it.primary_muscle_group to it.total_volume },
            muscleGroups.associate { it.primary_muscle_group to it.total_volume }
        )

        val benchPr = readModelDao.getExercisePrs("user-1").first { it.exerciseLibraryId == "bench" }
        assertEquals(100.0 * (1.0 + 5.0 / 30.0), benchPr.maxEstimatedOneRm, 0.001)
        assertEquals("workout-1", benchPr.sourceWorkoutId)
    }

    @Test
    fun refreshWorkoutReadModels_rebuildsAffectedDatesAndPrsAfterEditAndDelete() = runBlocking {
        seedExerciseLibrary()
        seedWorkout("workout-1", LocalDate.parse("2026-05-01"), benchWeight = 100f)
        readModelDao.refreshWorkoutReadModels("user-1", "workout-1")

        val oldExerciseIds = readModelDao.getExerciseLibraryIdsForWorkout("user-1", "workout-1")
        workoutDao.upsertLocal(workoutEntity("workout-1", LocalDate.parse("2026-05-02")))
        exerciseDao.deleteExercisesForWorkout("workout-1", "user-1")
        seedExercisesAndSets("workout-1", benchWeight = 80f, completedDate = LocalDate.parse("2026-05-02"))

        readModelDao.refreshWorkoutReadModels(
            userId = "user-1",
            workoutId = "workout-1",
            oldWorkoutDate = "2026-05-01",
            oldExerciseLibraryIds = oldExerciseIds
        )

        assertTrue(readModelDao.getDailyVolumes("user-1", "2026-05-01", "2026-05-01").isEmpty())
        assertEquals(1, readModelDao.getDailyVolumes("user-1", "2026-05-02", "2026-05-02").size)
        assertEquals(
            80.0 * (1.0 + 5.0 / 30.0),
            readModelDao.getExercisePrs("user-1").first { it.exerciseLibraryId == "bench" }.maxEstimatedOneRm,
            0.001
        )

        val deleteExerciseIds = readModelDao.getExerciseLibraryIdsForWorkout("user-1", "workout-1")
        workoutDao.deleteWorkoutByIdForUser("workout-1", "user-1")
        readModelDao.deleteWorkoutReadModels(
            userId = "user-1",
            workoutId = "workout-1",
            oldWorkoutDate = "2026-05-02",
            oldExerciseLibraryIds = deleteExerciseIds
        )

        assertTrue(readModelDao.getCompletedWorkoutMetrics("user-1", "2026-05-01", "2026-05-03").isEmpty())
        assertTrue(readModelDao.getExercisePrs("user-1").isEmpty())
        assertTrue(readModelDao.getMuscleGroupVolumeSummary("user-1", "2026-05-01", "2026-05-03").isEmpty())
    }

    private suspend fun seedExerciseLibrary() {
        exerciseLibraryDao.insertExercises(
            listOf(
                exerciseLibrary("bench", "Bench Press", ExerciseCategory.CHEST, Equipment.BARBELL),
                exerciseLibrary("row", "Dumbbell Row", ExerciseCategory.BACK, Equipment.DUMBBELLS)
            )
        )
    }

    private suspend fun seedWorkout(workoutId: String, workoutDate: LocalDate, benchWeight: Float) {
        workoutDao.upsertLocal(workoutEntity(workoutId, workoutDate))
        seedExercisesAndSets(workoutId, benchWeight, workoutDate)
    }

    private suspend fun seedExercisesAndSets(
        workoutId: String,
        benchWeight: Float,
        completedDate: LocalDate = LocalDate.parse("2026-05-01")
    ) {
        val benchExerciseId = exerciseDao.insertExercise(exerciseEntity(workoutId, "bench", 0))
        val rowExerciseId = exerciseDao.insertExercise(exerciseEntity(workoutId, "row", 1))
        exerciseSetDao.insertSets(
            listOf(
                setEntity(benchExerciseId, 1, reps = 5, weightKg = benchWeight, completedDate = completedDate),
                setEntity(benchExerciseId, 2, reps = 8, weightKg = benchWeight - 10f, completedDate = completedDate),
                setEntity(rowExerciseId, 1, reps = 10, weightKg = 50f, completedDate = completedDate)
            )
        )
    }

    private fun workoutEntity(id: String, date: LocalDate): WorkoutEntity {
        val start = Instant.parse("${date}T10:00:00Z")
        return WorkoutEntity(
            id = id,
            userId = "user-1",
            name = "Workout",
            date = date,
            exercisesJson = "[]",
            status = WorkoutStatus.COMPLETED,
            startTime = start,
            endTime = start.plusSeconds(3600),
            notes = null,
            templateId = null,
            createdAt = start,
            updatedAt = start,
            isSynced = false,
            syncVersion = 0L,
            isDirty = true,
            lastModified = start.toEpochMilli()
        )
    }

    private fun exerciseEntity(workoutId: String, exerciseLibraryId: String, orderIndex: Int): ExerciseEntity =
        ExerciseEntity(
            userId = "user-1",
            workoutId = workoutId,
            exerciseLibraryId = exerciseLibraryId,
            orderIndex = orderIndex,
            createdAt = 1L,
            updatedAt = 1L
        )

    private fun setEntity(
        exerciseId: Long,
        setNumber: Int,
        reps: Int,
        weightKg: Float,
        completedDate: LocalDate
    ): ExerciseSetEntity =
        ExerciseSetEntity(
            userId = "user-1",
            exerciseId = exerciseId,
            setNumber = setNumber,
            reps = reps,
            weightKg = weightKg,
            completedAt = Instant.parse("${completedDate}T10:00:00Z").toEpochMilli() + setNumber
        )

    private fun exerciseLibrary(
        id: String,
        name: String,
        primaryMuscleGroup: ExerciseCategory,
        equipment: Equipment
    ): ExerciseLibraryEntity =
        ExerciseLibraryEntity(
            id = id,
            name = name,
            primaryMuscleGroup = primaryMuscleGroup,
            equipment = equipment,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "test",
            difficultyLevel = 1,
            instructions = null,
            isCompound = true,
            searchableTerms = emptyList()
        )
}
