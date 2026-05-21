package com.example.liftrix.service.export

import com.example.liftrix.data.local.dao.DailyRepActivityResult
import com.example.liftrix.data.local.dao.DailyVolumeResult
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExercisePerformanceHistoryResult
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.MuscleGroupRepActivityResult
import com.example.liftrix.data.local.dao.OneRmResult
import com.example.liftrix.data.local.dao.PersonalRecordDao
import com.example.liftrix.data.local.dao.SyncPreferencesDao
import com.example.liftrix.data.local.dao.SyncQueueDao
import com.example.liftrix.data.local.dao.StrengthForecastSetSampleResult
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.WorkoutSetActivityResult
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportRequest
import com.example.liftrix.domain.service.analytics.StrengthForecastService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class ProgressReportDataBuilderTest {

    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseSetDao: ExerciseSetDao
    private lateinit var exerciseLibraryDao: ExerciseLibraryDao
    private lateinit var personalRecordDao: PersonalRecordDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var syncPreferencesDao: SyncPreferencesDao
    private lateinit var builder: ProgressReportDataBuilder

    @Before
    fun setUp() {
        workoutDao = mockk()
        exerciseSetDao = mockk()
        exerciseLibraryDao = mockk()
        personalRecordDao = mockk()
        syncQueueDao = mockk()
        syncPreferencesDao = mockk()
        builder = ProgressReportDataBuilder(
            workoutDao = workoutDao,
            exerciseSetDao = exerciseSetDao,
            exerciseLibraryDao = exerciseLibraryDao,
            personalRecordDao = personalRecordDao,
            syncQueueDao = syncQueueDao,
            syncPreferencesDao = syncPreferencesDao,
            strengthForecastService = StrengthForecastService()
        )
    }

    @Test
    fun `deduplicates identical personal records before rows and count`() = runTest {
        stubCommonReportData(
            workouts = listOf(workout("workout-1")),
            prs = listOf(
                pr(id = "pr-1", type = "REPS", reps = 15),
                pr(id = "pr-2", type = "REPS", reps = 15),
                pr(id = "pr-3", type = "REPS", reps = 15)
            ),
            libraryRows = listOf(bodyweightSquat())
        )

        val data = builder.build(USER_ID, request())

        assertEquals(1, data.personalRecordRows.size)
        assertEquals(1, data.summary.newPersonalRecords)
        assertEquals("REPS", data.personalRecordRows.single().recordType)
    }

    @Test
    fun `filters weighted records for bodyweight only exercises but keeps reps`() = runTest {
        stubCommonReportData(
            workouts = listOf(workout("workout-1")),
            prs = listOf(
                pr(id = "pr-weight", type = "MAX_WEIGHT", weightKg = 500.0, reps = 1),
                pr(id = "pr-one-rm", type = "ONE_RM", weightKg = 500.0, estimatedOneRm = 650.0, reps = 9),
                pr(id = "pr-reps", type = "REPS", weightKg = 500.0, reps = 20)
            ),
            libraryRows = listOf(bodyweightSquat())
        )

        val data = builder.build(USER_ID, request())

        assertEquals(listOf("REPS"), data.personalRecordRows.map { it.recordType })
        assertEquals("20 reps", data.personalRecordRows.single().newValue)
    }

    @Test
    fun `derives sync and excludes missing durations from average`() = runTest {
        stubCommonReportData(
            workouts = listOf(
                workout(
                    id = "synced",
                    start = Instant.parse("2026-05-10T10:00:00Z"),
                    end = Instant.parse("2026-05-10T11:00:00Z"),
                    isSynced = true,
                    syncVersion = 1_800_000_000_000
                ),
                workout(id = "missing-end", start = Instant.parse("2026-05-11T10:00:00Z"), end = null)
            ),
            prs = emptyList(),
            libraryRows = emptyList(),
            lastSync = null
        )

        val data = builder.build(USER_ID, request())

        assertEquals(60L, data.summary.averageDurationMinutes)
        assertEquals(1, data.summary.validDurationWorkoutCount)
        assertEquals(1, data.syncStatus.syncedWorkoutCount)
        assertNotNull(data.syncStatus.lastSyncTimestampMillis)
    }

    @Test
    fun `bodyweight only reports show rep activity instead of blank volume rows`() = runTest {
        stubCommonReportData(
            workouts = listOf(workout("workout-1", date = LocalDate.of(2026, 5, 6))),
            prs = emptyList(),
            libraryRows = emptyList(),
            dailyVolume = emptyList(),
            dailyRepActivity = listOf(DailyRepActivityResult("2026-05-06", total_reps = 150, total_sets = 10, exercise_count = 2)),
            muscleGroupRepActivity = listOf(MuscleGroupRepActivityResult("LEGS", total_reps = 150, exercise_count = 2, total_sets = 10))
        )

        val data = builder.build(
            USER_ID,
            request(ProgressReportDateRange.Custom(LocalDate.of(2026, 5, 6), LocalDate.of(2026, 5, 18)))
        )

        assertEquals(0.0, data.summary.totalVolumeKg, 0.01)
        assertEquals(150, data.weeklyVolumeRows.single().repCount)
        assertEquals(150, data.muscleGroupRows.single().repCount)
        assertNotEquals("05/04", data.weeklyVolumeRows.single().weekLabel)
    }

    @Test
    fun `same day workouts remain separate sessions with per workout details`() = runTest {
        val firstStart = Instant.parse("2026-05-18T09:00:00Z")
        val secondStart = Instant.parse("2026-05-18T17:00:00Z")
        stubCommonReportData(
            workouts = listOf(
                workout(
                    id = "morning-session",
                    name = "Upper Body",
                    date = LocalDate.of(2026, 5, 18),
                    start = firstStart,
                    end = Instant.parse("2026-05-18T10:00:00Z")
                ),
                workout(
                    id = "evening-session",
                    name = "Upper Body",
                    date = LocalDate.of(2026, 5, 18),
                    start = secondStart,
                    end = Instant.parse("2026-05-18T18:00:00Z")
                )
            ),
            prs = emptyList(),
            libraryRows = emptyList(),
            dailyVolume = listOf(DailyVolumeResult("2026-05-18", total_volume = 1500.0, total_sets = 8, exercise_count = 3)),
            dailyRepActivity = listOf(DailyRepActivityResult("2026-05-18", total_reps = 80, total_sets = 8, exercise_count = 3)),
            workoutSetActivity = listOf(
                WorkoutSetActivityResult("morning-session", total_volume = 500.0, total_reps = 30, total_sets = 3),
                WorkoutSetActivityResult("evening-session", total_volume = 1000.0, total_reps = 50, total_sets = 5)
            )
        )

        val data = builder.build(
            USER_ID,
            request(ProgressReportDateRange.Custom(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 18)))
        )

        assertEquals(2, data.summary.workoutsCompleted)
        assertEquals(2, data.summary.rawWorkoutEntries)
        assertEquals(1, data.summary.activeTrainingDays)
        assertEquals(2, data.weeklyVolumeRows.single().workoutCount)
        assertEquals(1, data.weeklyVolumeRows.single().activeDays)
        assertEquals(1500.0, data.weeklyVolumeRows.single().totalVolumeKg, 0.01)
        assertEquals(listOf(500.0, 1000.0), data.workoutRows.map { it.volumeKg })
        assertEquals(listOf(3, 5), data.workoutRows.map { it.setCount })
    }

    @Test
    fun `strength rows populate repeated weighted exercise sessions by exercise name`() = runTest {
        stubCommonReportData(
            workouts = listOf(workout("workout-1"), workout("workout-2", date = LocalDate.of(2026, 5, 12))),
            prs = emptyList(),
            libraryRows = emptyList(),
            oneRmData = listOf(
                OneRmResult("bench", "Bench Press", 80f, 5, Instant.parse("2026-05-01T10:00:00Z").toEpochMilli(), 93.3),
                OneRmResult("bench", "Bench Press", 90f, 5, Instant.parse("2026-05-12T10:00:00Z").toEpochMilli(), 105.0)
            ),
            performanceHistory = listOf(
                ExercisePerformanceHistoryResult("2026-05-01", "bench", "Bench Press", 400.0, 1, 80.0, 5, 93.3),
                ExercisePerformanceHistoryResult("2026-05-12", "bench", "Bench Press", 450.0, 1, 90.0, 5, 105.0)
            )
        )

        val data = builder.build(USER_ID, request())

        assertEquals("Bench Press", data.strengthRows.single().exerciseName)
        assertEquals(11.7, data.strengthRows.single().improvementKg, 0.1)
    }

    @Test
    fun `strength forecast section populates from local forecast samples`() = runTest {
        stubCommonReportData(
            workouts = listOf(workout("workout-1"), workout("workout-2", date = LocalDate.of(2026, 5, 18))),
            prs = emptyList(),
            libraryRows = emptyList(),
            forecastSamples = listOf(
                StrengthForecastSetSampleResult("bench", "Bench Press", "2026-05-05", 80f, 5, Instant.parse("2026-05-05T10:00:00Z").toEpochMilli()),
                StrengthForecastSetSampleResult("bench", "Bench Press", "2026-05-18", 90f, 5, Instant.parse("2026-05-18T10:00:00Z").toEpochMilli())
            )
        )

        val data = builder.build(USER_ID, request())

        assertNotNull(data.strengthForecast)
        assertEquals("Bench Press", data.strengthForecast?.generatedForExerciseName)
        assertEquals(14, data.strengthForecast?.forecast?.forecastPoints?.size)
    }

    private fun stubCommonReportData(
        workouts: List<WorkoutEntity>,
        prs: List<PersonalRecordEntity>,
        libraryRows: List<ExerciseLibraryEntity>,
        dailyVolume: List<DailyVolumeResult> = emptyList(),
        dailyRepActivity: List<DailyRepActivityResult> = emptyList(),
        workoutSetActivity: List<WorkoutSetActivityResult> = emptyList(),
        muscleGroupRepActivity: List<MuscleGroupRepActivityResult> = emptyList(),
        oneRmData: List<OneRmResult> = emptyList(),
        performanceHistory: List<ExercisePerformanceHistoryResult> = emptyList(),
        forecastSamples: List<StrengthForecastSetSampleResult> = emptyList(),
        lastSync: Long? = null
    ) {
        coEvery { workoutDao.getCompletedWorkoutsInDateRangeForUser(USER_ID, any(), any(), any()) } returns workouts
        coEvery { exerciseSetDao.getDailyVolumeData(USER_ID, any(), any()) } returns dailyVolume
        coEvery { exerciseSetDao.getDailyRepActivityData(USER_ID, any(), any()) } returns dailyRepActivity
        coEvery { exerciseSetDao.getWorkoutSetActivityData(USER_ID, any(), any()) } returns workoutSetActivity
        coEvery { exerciseSetDao.getVolumeDataByMuscleGroup(USER_ID, any(), any(), any()) } returns emptyList()
        coEvery { exerciseSetDao.getRepActivityByMuscleGroup(USER_ID, any(), any(), any()) } returns muscleGroupRepActivity
        coEvery { exerciseSetDao.getAllOneRmData(USER_ID, any(), any()) } returns oneRmData
        coEvery { exerciseSetDao.getExercisePerformanceHistory(USER_ID, any(), any()) } returns performanceHistory
        coEvery { exerciseSetDao.getStrengthForecastSetSamples(USER_ID, any(), any()) } returns forecastSamples
        coEvery { personalRecordDao.getPRsInDateRange(USER_ID, any(), any()) } returns prs
        coEvery { exerciseLibraryDao.getExercisesByNames(any()) } returns libraryRows
        coEvery { syncQueueDao.getPendingItemsCount(USER_ID) } returns 0
        coEvery { syncPreferencesDao.getLastSyncTimestamp(USER_ID) } returns lastSync
    }

    private fun request(
        range: ProgressReportDateRange = ProgressReportDateRange.Last30Days
    ): ProgressReportRequest {
        return ProgressReportRequest(
            dateRange = range,
            generatedAt = LocalDateTime.of(2026, 5, 18, 12, 0)
        )
    }

    private fun workout(
        id: String,
        name: String = "Workout $id",
        date: LocalDate = LocalDate.of(2026, 5, 10),
        start: Instant? = Instant.parse("2026-05-10T10:00:00Z"),
        end: Instant? = Instant.parse("2026-05-10T11:00:00Z"),
        isSynced: Boolean = false,
        syncVersion: Long = 0
    ): WorkoutEntity {
        return WorkoutEntity(
            id = id,
            userId = USER_ID,
            name = name,
            date = date,
            exercisesJson = "[]",
            status = WorkoutStatus.COMPLETED,
            startTime = start,
            endTime = end,
            notes = null,
            templateId = null,
            createdAt = start ?: Instant.parse("2026-05-10T10:00:00Z"),
            updatedAt = end ?: start ?: Instant.parse("2026-05-10T10:00:00Z"),
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }

    private fun pr(
        id: String,
        type: String,
        weightKg: Double? = null,
        estimatedOneRm: Double? = null,
        reps: Int
    ): PersonalRecordEntity {
        return PersonalRecordEntity(
            id = id,
            userId = USER_ID,
            exerciseName = "Bodyweight Squat",
            prType = type,
            weightKg = weightKg,
            reps = reps,
            volume = weightKg?.let { it * reps },
            estimatedOneRM = estimatedOneRm,
            achievedAt = Instant.parse("2026-05-10T11:00:00Z").toEpochMilli(),
            workoutId = "workout-1",
            previousBest = null,
            improvementPercent = null
        )
    }

    private fun bodyweightSquat(): ExerciseLibraryEntity {
        return ExerciseLibraryEntity(
            id = "legs-squat-bodyweight",
            name = "Bodyweight Squat",
            primaryMuscleGroup = ExerciseCategory.LEGS,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "squat",
            difficultyLevel = 1,
            instructions = null,
            isCompound = true,
            searchableTerms = listOf("bodyweight squat")
        )
    }

    private companion object {
        const val USER_ID = "user-123"
    }
}
