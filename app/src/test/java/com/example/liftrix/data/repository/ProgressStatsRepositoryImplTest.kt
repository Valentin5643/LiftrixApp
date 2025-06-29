package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ExerciseDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.ExerciseEntity
import com.example.liftrix.data.local.entity.ExerciseSetEntity
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.domain.repository.VolumeDataPoint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate as JavaLocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ProgressStatsRepositoryImpl
 * Tests data aggregation logic with comprehensive mock data scenarios
 */
class ProgressStatsRepositoryImplTest {

    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var exerciseSetDao: ExerciseSetDao
    private lateinit var repository: ProgressStatsRepositoryImpl

    private val testUserId = "test-user-123"
    private val testStartDate = LocalDate(2024, 1, 1)
    private val testEndDate = LocalDate(2024, 1, 31)

    @Before
    fun setup() {
        workoutDao = mockk()
        exerciseDao = mockk()
        exerciseSetDao = mockk()
        repository = ProgressStatsRepositoryImpl(workoutDao, exerciseDao, exerciseSetDao)
    }

    // ============ getWorkoutVolumeData Tests ============

    @Test
    fun getWorkoutVolumeData_withValidData_returnsCorrectVolumeCalculations() = runTest {
        // Arrange
        val workout1 = createTestWorkout("workout1", "2024-01-01")
        val workout2 = createTestWorkout("workout2", "2024-01-02")
        val workouts = listOf(workout1, workout2)

        val exercise1 = createTestExercise(1L, "workout1", "exercise1")
        val exercise2 = createTestExercise(2L, "workout2", "exercise2")

        val sets1 = listOf(
            createTestSet(1L, 1L, 100f, 10), // 100 * 10 = 1000
            createTestSet(2L, 1L, 105f, 8)   // 105 * 8 = 840
        )
        val sets2 = listOf(
            createTestSet(3L, 2L, 50f, 12),  // 50 * 12 = 600
            createTestSet(4L, 2L, 55f, 10)   // 55 * 10 = 550
        )

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns workouts
        coEvery { exerciseDao.getExercisesByWorkoutId("workout1") } returns listOf(exercise1)
        coEvery { exerciseDao.getExercisesByWorkoutId("workout2") } returns listOf(exercise2)
        coEvery { exerciseSetDao.getSetsByExercise(1L) } returns sets1
        coEvery { exerciseSetDao.getSetsByExercise(2L) } returns sets2

        // Act
        val result = repository.getWorkoutVolumeData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        assertEquals(1, result.size, "Should emit one list of volume data")
        val volumeData = result.first()
        assertEquals(2, volumeData.size, "Should have data for 2 days")

        val day1Data = volumeData.find { it.date == LocalDate(2024, 1, 1) }
        val day2Data = volumeData.find { it.date == LocalDate(2024, 1, 2) }

        assertEquals(1840f, day1Data?.totalVolume, "Day 1 total volume should be 1840kg")
        assertEquals(1, day1Data?.exerciseCount, "Day 1 should have 1 exercise")
        
        assertEquals(1150f, day2Data?.totalVolume, "Day 2 total volume should be 1150kg")
        assertEquals(1, day2Data?.exerciseCount, "Day 2 should have 1 exercise")

        coVerify { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") }
        coVerify { exerciseDao.getExercisesByWorkoutId("workout1") }
        coVerify { exerciseDao.getExercisesByWorkoutId("workout2") }
    }

    @Test
    fun getWorkoutVolumeData_withNullWeightOrReps_ignoresIncompleteSets() = runTest {
        // Arrange
        val workout = createTestWorkout("workout1", "2024-01-01")
        val exercise = createTestExercise(1L, "workout1", "exercise1")
        val sets = listOf(
            createTestSet(1L, 1L, 100f, 10),    // Valid: 100 * 10 = 1000
            createTestSet(2L, 1L, null, 10),    // Invalid: null weight
            createTestSet(3L, 1L, 100f, null),  // Invalid: null reps
            createTestSet(4L, 1L, null, null)   // Invalid: both null
        )

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns listOf(workout)
        coEvery { exerciseDao.getExercisesByWorkoutId("workout1") } returns listOf(exercise)
        coEvery { exerciseSetDao.getSetsByExercise(1L) } returns sets

        // Act
        val result = repository.getWorkoutVolumeData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        val volumeData = result.first()
        assertEquals(1, volumeData.size)
        assertEquals(1000f, volumeData.first().totalVolume, "Should only count valid sets")
    }

    @Test
    fun getWorkoutVolumeData_withException_returnsEmptyList() = runTest {
        // Arrange
        coEvery { workoutDao.getWorkoutsInDateRangeForUser(any(), any(), any()) } throws RuntimeException("Database error")

        // Act
        val result = repository.getWorkoutVolumeData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        assertEquals(1, result.size)
        assertTrue(result.first().isEmpty(), "Should return empty list on exception")
    }

    // ============ getWorkoutDurationData Tests ============

    @Test
    fun getWorkoutDurationData_withValidTimes_returnsCorrectDurations() = runTest {
        // Arrange
        val startTime1 = Instant.parse("2024-01-01T10:00:00Z")
        val endTime1 = Instant.parse("2024-01-01T11:30:00Z") // 90 minutes
        val startTime2 = Instant.parse("2024-01-01T15:00:00Z")
        val endTime2 = Instant.parse("2024-01-01T16:00:00Z") // 60 minutes

        val workout1 = createTestWorkout("workout1", "2024-01-01", startTime1, endTime1)
        val workout2 = createTestWorkout("workout2", "2024-01-01", startTime2, endTime2)

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns listOf(workout1, workout2)

        // Act
        val result = repository.getWorkoutDurationData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        val durationData = result.first()
        assertEquals(1, durationData.size, "Should group by date")
        
        val dayData = durationData.first()
        assertEquals(LocalDate(2024, 1, 1), dayData.date)
        assertEquals(150, dayData.durationMinutes, "Total duration should be 150 minutes")
        assertEquals(2, dayData.workoutCount, "Should have 2 workouts")
    }

    @Test
    fun getWorkoutDurationData_withNullTimes_ignoresIncompleteWorkouts() = runTest {
        // Arrange
        val startTime = Instant.parse("2024-01-01T10:00:00Z")
        val endTime = Instant.parse("2024-01-01T11:00:00Z")
        
        val completeWorkout = createTestWorkout("workout1", "2024-01-01", startTime, endTime)
        val incompleteWorkout1 = createTestWorkout("workout2", "2024-01-01", null, endTime)
        val incompleteWorkout2 = createTestWorkout("workout3", "2024-01-01", startTime, null)

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns 
            listOf(completeWorkout, incompleteWorkout1, incompleteWorkout2)

        // Act
        val result = repository.getWorkoutDurationData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        val durationData = result.first()
        val dayData = durationData.first()
        assertEquals(60, dayData.durationMinutes, "Should only count complete workout")
        assertEquals(3, dayData.workoutCount, "Should count all workouts")
    }

    // ============ getWorkoutFrequencyData Tests ============

    @Test
    fun getWorkoutFrequencyData_withVariousFrequencies_calculatesCorrectIntensity() = runTest {
        // Arrange
        val workout1 = createTestWorkout("workout1", "2024-01-01")
        val workout2 = createTestWorkout("workout2", "2024-01-01") // Same day as workout1
        val workout3 = createTestWorkout("workout3", "2024-01-02")

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns 
            listOf(workout1, workout2, workout3)

        // Act
        val result = repository.getWorkoutFrequencyData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        val frequencyData = result.first()
        assertEquals(2, frequencyData.size, "Should have data for 2 days")

        val day1Data = frequencyData.find { it.date == LocalDate(2024, 1, 1) }
        val day2Data = frequencyData.find { it.date == LocalDate(2024, 1, 2) }

        assertEquals(2, day1Data?.workoutCount, "Day 1 should have 2 workouts")
        assertEquals(1.0f, day1Data?.intensity, "Day 1 should have max intensity (2/2)")
        
        assertEquals(1, day2Data?.workoutCount, "Day 2 should have 1 workout")
        assertEquals(0.5f, day2Data?.intensity, "Day 2 should have half intensity (1/2)")
    }

    @Test
    fun getWorkoutFrequencyData_withNoWorkouts_returnsEmptyList() = runTest {
        // Arrange
        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns emptyList()

        // Act
        val result = repository.getWorkoutFrequencyData(testUserId, testStartDate, testEndDate).toList()

        // Assert
        assertTrue(result.first().isEmpty(), "Should return empty list when no workouts")
    }

    // ============ getProgressSummary Tests ============

    @Test
    fun getProgressSummary_withCompleteData_calculatesAllMetricsCorrectly() = runTest {
        // Arrange
        val startTime1 = Instant.parse("2024-01-01T10:00:00Z")
        val endTime1 = Instant.parse("2024-01-01T11:00:00Z") // 60 minutes
        val startTime2 = Instant.parse("2024-01-02T10:00:00Z")
        val endTime2 = Instant.parse("2024-01-02T11:30:00Z") // 90 minutes
        val startTime3 = Instant.parse("2024-01-03T10:00:00Z")
        val endTime3 = Instant.parse("2024-01-03T10:45:00Z") // 45 minutes

        val workout1 = createTestWorkout("workout1", "2024-01-01", startTime1, endTime1)
        val workout2 = createTestWorkout("workout2", "2024-01-02", startTime2, endTime2)
        val workout3 = createTestWorkout("workout3", "2024-01-03", startTime3, endTime3)

        val exercise1 = createTestExercise(1L, "workout1", "exercise1")
        val exercise2 = createTestExercise(2L, "workout2", "exercise2")
        val exercise3 = createTestExercise(3L, "workout3", "exercise3")

        val sets1 = listOf(createTestSet(1L, 1L, 100f, 10)) // 1000 volume
        val sets2 = listOf(createTestSet(2L, 2L, 80f, 12))  // 960 volume
        val sets3 = listOf(createTestSet(3L, 3L, 120f, 8))  // 960 volume

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns 
            listOf(workout1, workout2, workout3)
        coEvery { exerciseDao.getExercisesByWorkoutId("workout1") } returns listOf(exercise1)
        coEvery { exerciseDao.getExercisesByWorkoutId("workout2") } returns listOf(exercise2)
        coEvery { exerciseDao.getExercisesByWorkoutId("workout3") } returns listOf(exercise3)
        coEvery { exerciseSetDao.getSetsByExercise(1L) } returns sets1
        coEvery { exerciseSetDao.getSetsByExercise(2L) } returns sets2
        coEvery { exerciseSetDao.getSetsByExercise(3L) } returns sets3

        // Act
        val result = repository.getProgressSummary(testUserId, testStartDate, testEndDate).toList()

        // Assert
        val summary = result.first()
        assertEquals(3, summary.totalWorkouts, "Should count all workouts")
        assertEquals(2920f, summary.totalVolume, "Should sum all volume (1000 + 960 + 960)")
        assertEquals(65, summary.averageDuration, "Average duration should be 65 minutes ((60+90+45)/3)")
        assertEquals(195, summary.totalActiveTime, "Total time should be 195 minutes")
        assertEquals(3, summary.currentStreak, "Should have 3-day current streak")
        assertEquals(3, summary.longestStreak, "Should have 3-day longest streak")
        assertTrue(summary.averageWorkoutsPerWeek > 0, "Should calculate workouts per week")
    }

    @Test
    fun getProgressSummary_withIncompleteWorkouts_handlesPartialData() = runTest {
        // Arrange
        val completeWorkout = createTestWorkout("workout1", "2024-01-01", 
            Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T11:00:00Z"))
        val incompleteWorkout = createTestWorkout("workout2", "2024-01-02", null, null)

        coEvery { workoutDao.getWorkoutsInDateRangeForUser(testUserId, "2024-01-01", "2024-01-31") } returns 
            listOf(completeWorkout, incompleteWorkout)
        coEvery { exerciseDao.getExercisesByWorkoutId(any()) } returns emptyList()

        // Act
        val result = repository.getProgressSummary(testUserId, testStartDate, testEndDate).toList()

        // Assert
        val summary = result.first()
        assertEquals(2, summary.totalWorkouts, "Should count all workouts")
        assertEquals(60, summary.averageDuration, "Should average only complete workouts")
        assertEquals(60, summary.totalActiveTime, "Should sum only complete workouts")
    }

    // ============ getExerciseVolumeProgression Tests ============

    @Test
    fun getExerciseVolumeProgression_withSpecificExercise_returnsProgressionData() = runTest {
        // Arrange
        val exerciseLibraryId = "bench-press"
        val exercise1 = createTestExercise(1L, "workout1", exerciseLibraryId)
        val exercise2 = createTestExercise(2L, "workout2", exerciseLibraryId)
        
        val workout1 = createTestWorkout("workout1", "2024-01-01")
        val workout2 = createTestWorkout("workout2", "2024-01-02")

        val sets1 = listOf(createTestSet(1L, 1L, 100f, 10)) // 1000 volume
        val sets2 = listOf(createTestSet(2L, 2L, 105f, 10)) // 1050 volume

        coEvery { exerciseDao.getExerciseHistoryInDateRange(testUserId, exerciseLibraryId, "2024-01-01", "2024-01-31") } returns 
            listOf(exercise1, exercise2)
        coEvery { workoutDao.getWorkoutByExerciseId(1L) } returns workout1
        coEvery { workoutDao.getWorkoutByExerciseId(2L) } returns workout2
        coEvery { exerciseSetDao.getSetsByExercise(1L) } returns sets1
        coEvery { exerciseSetDao.getSetsByExercise(2L) } returns sets2

        // Act
        val result = repository.getExerciseVolumeProgression(testUserId, exerciseLibraryId, testStartDate, testEndDate).toList()

        // Assert
        val progressionData = result.first()
        assertEquals(2, progressionData.size, "Should have progression for 2 workouts")
        
        val day1Data = progressionData.find { it.date == LocalDate(2024, 1, 1) }
        val day2Data = progressionData.find { it.date == LocalDate(2024, 1, 2) }
        
        assertEquals(1000f, day1Data?.totalVolume, "Day 1 volume should be 1000")
        assertEquals(1050f, day2Data?.totalVolume, "Day 2 volume should be 1050 (progression)")
    }

    @Test
    fun getExerciseVolumeProgression_withNoExerciseHistory_returnsEmptyList() = runTest {
        // Arrange
        coEvery { exerciseDao.getExerciseHistoryInDateRange(testUserId, "non-existent", "2024-01-01", "2024-01-31") } returns emptyList()

        // Act
        val result = repository.getExerciseVolumeProgression(testUserId, "non-existent", testStartDate, testEndDate).toList()

        // Assert
        assertTrue(result.first().isEmpty(), "Should return empty list when no exercise history")
    }

    // ============ Helper Methods for Test Data Creation ============

    private fun createTestWorkout(
        id: String,
        date: String,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): WorkoutEntity {
        return WorkoutEntity(
            id = id,
            userId = testUserId,
            name = "Test Workout",
            date = JavaLocalDate.parse(date),
            description = "Test workout description",
            status = WorkoutStatus.COMPLETED,
            templateId = null,
            startTime = startTime,
            endTime = endTime,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            isSynced = false,
            syncVersion = 1
        )
    }

    private fun createTestExercise(
        id: Long,
        workoutId: String,
        exerciseLibraryId: String
    ): ExerciseEntity {
        return ExerciseEntity(
            id = id,
            workoutId = workoutId,
            exerciseLibraryId = exerciseLibraryId,
            orderIndex = 0,
            notes = null,
            lastUsedWeightKg = null,
            weightMemoryUpdatedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createTestSet(
        id: Long,
        exerciseId: Long,
        weightKg: Float?,
        reps: Int?
    ): ExerciseSetEntity {
        return ExerciseSetEntity(
            id = id,
            exerciseId = exerciseId,
            setNumber = 1,
            weightKg = weightKg,
            reps = reps,
            rpe = null,
            timeSeconds = null,
            distanceMeters = null,
            isCompleted = true,
            completedAt = if (weightKg != null && reps != null) Instant.now() else null
        )
    }
}