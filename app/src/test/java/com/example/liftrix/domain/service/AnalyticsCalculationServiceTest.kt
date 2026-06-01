package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.Set
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.model.common.Weight
import com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData
import com.example.liftrix.domain.usecase.analytics.OneRmDataPoint
import com.example.liftrix.domain.usecase.analytics.VolumeDataPoint
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive unit tests for AnalyticsCalculationServiceImpl.
 *
 * **Test Coverage Target**: >90%
 * **Test Categories**:
 * - Calorie calculations (basic, multiple workouts, estimates)
 * - Exercise ranking calculations
 * - Workout metrics calculations
 * - One rep max calculations
 * - Volume calculations
 * - Performance score calculations
 * - Edge cases and error handling
 */
class AnalyticsCalculationServiceTest {

    @get:Rule
    val mockKRule = MockKRule(this)

    private lateinit var service: AnalyticsCalculationService

    @Before
    fun setup() {
        service = AnalyticsCalculationServiceImpl()
    }

    // ========== Calorie Calculation Tests ==========

    @Test
    fun `calculateCaloriesBurned returns correct calories for standard workout`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(
                createExercise(sets = listOf(
                    createSet(weight = 100.0, reps = 10),
                    createSet(weight = 100.0, reps = 10),
                    createSet(weight = 100.0, reps = 10)
                ))
            ),
            durationMinutes = 60
        )
        val userWeightKg = 80.0

        // When
        val result = service.calculateCaloriesBurned(workout, userWeightKg)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val calories = result.getOrNull()!!
        assertTrue("Calories should be positive", calories > 0)
        // MET formula: 6.0 * 80 * 1.0 = 480 base calories
        assertTrue("Calories should be reasonable", calories in 400..600)
    }

    @Test
    fun `calculateCaloriesBurned estimates duration when not provided`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(
                createExercise(sets = listOf(
                    createSet(weight = 100.0, reps = 10),
                    createSet(weight = 100.0, reps = 10)
                ))
            ),
            durationMinutes = null // No duration provided
        )
        val userWeightKg = 75.0

        // When
        val result = service.calculateCaloriesBurned(workout, userWeightKg)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val calories = result.getOrNull()!!
        assertTrue("Calories should be positive even with estimated duration", calories > 0)
    }

    @Test
    fun `calculateCaloriesBurned fails for zero weight`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(createExercise()),
            durationMinutes = 60
        )
        val userWeightKg = 0.0

        // When
        val result = service.calculateCaloriesBurned(workout, userWeightKg)

        // Then
        assertTrue("Should return error for zero weight", result.isFailure)
    }

    @Test
    fun `calculateCaloriesBurned fails for negative weight`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(createExercise()),
            durationMinutes = 60
        )
        val userWeightKg = -50.0

        // When
        val result = service.calculateCaloriesBurned(workout, userWeightKg)

        // Then
        assertTrue("Should return error for negative weight", result.isFailure)
    }

    @Test
    fun `calculateCaloriesBurned fails for workout with no exercises`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = emptyList(),
            durationMinutes = 60
        )
        val userWeightKg = 75.0

        // When
        val result = service.calculateCaloriesBurned(workout, userWeightKg)

        // Then
        assertTrue("Should return error for empty exercises", result.isFailure)
    }

    @Test
    fun `calculateCaloriesBurned adjusts for high intensity workout`() = runTest {
        // Given - High intensity workout (20+ sets)
        val workout = createWorkout(
            exercises = List(5) {
                createExercise(sets = List(5) { createSet(weight = 100.0, reps = 10) })
            },
            durationMinutes = 90
        )
        val userWeightKg = 80.0

        // When
        val result = service.calculateCaloriesBurned(workout, userWeightKg)

        // Then
        val calories = result.getOrNull()!!
        // Should have intensity multiplier of 1.2 for 25 sets
        assertTrue("High intensity should burn more calories", calories > 500)
    }

    @Test
    fun `calculateMultipleWorkoutCalories sums calories correctly`() = runTest {
        // Given
        val workouts = listOf(
            createWorkout(exercises = listOf(createExercise()), durationMinutes = 60),
            createWorkout(exercises = listOf(createExercise()), durationMinutes = 60),
            createWorkout(exercises = listOf(createExercise()), durationMinutes = 60)
        )
        val userWeightKg = 75.0

        // When
        val result = service.calculateMultipleWorkoutCalories(workouts, userWeightKg)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val totalCalories = result.getOrNull()!!
        assertTrue("Total calories should be positive", totalCalories > 0)
    }

    @Test
    fun `calculateMultipleWorkoutCalories handles empty list`() = runTest {
        // Given
        val workouts = emptyList<Workout>()
        val userWeightKg = 75.0

        // When
        val result = service.calculateMultipleWorkoutCalories(workouts, userWeightKg)

        // Then
        assertTrue("Should return success with zero calories", result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `estimateWorkoutCalories returns reasonable estimate`() = runTest {
        // Given
        val exerciseCount = 5
        val estimatedDurationMinutes = 60
        val userWeightKg = 75.0

        // When
        val result = service.estimateWorkoutCalories(exerciseCount, estimatedDurationMinutes, userWeightKg)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val calories = result.getOrNull()!!
        assertTrue("Estimated calories should be reasonable", calories in 400..600)
    }

    @Test
    fun `estimateWorkoutCalories adjusts for exercise complexity`() = runTest {
        // Given
        val userWeightKg = 75.0
        val duration = 60

        // When
        val simpleWorkout = service.estimateWorkoutCalories(2, duration, userWeightKg).getOrNull()!!
        val complexWorkout = service.estimateWorkoutCalories(10, duration, userWeightKg).getOrNull()!!

        // Then
        assertTrue("Complex workout should burn more calories", complexWorkout > simpleWorkout)
    }

    @Test
    fun `estimateWorkoutCalories fails for invalid inputs`() = runTest {
        // Test zero exercise count
        var result = service.estimateWorkoutCalories(0, 60, 75.0)
        assertTrue("Should fail for zero exercises", result.isFailure)

        // Test zero duration
        result = service.estimateWorkoutCalories(5, 0, 75.0)
        assertTrue("Should fail for zero duration", result.isFailure)

        // Test zero weight
        result = service.estimateWorkoutCalories(5, 60, 0.0)
        assertTrue("Should fail for zero weight", result.isFailure)
    }

    // ========== Exercise Ranking Tests ==========

    @Test
    fun `calculateExerciseRanking returns empty list for empty input`() = runTest {
        // Given
        val performanceData = emptyList<ExercisePerformanceData>()

        // When
        val result = service.calculateExerciseRanking(performanceData)

        // Then
        assertTrue("Should return success", result.isSuccess)
        assertTrue("Should return empty list", result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `calculateExerciseRanking ranks by performance score`() = runTest {
        // Given
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val twoMonthsAgo = now.minus(DatePeriod(months = 2))

        val performanceData = listOf(
            createPerformanceData(
                exerciseId = "ex1",
                exerciseName = "Squat",
                volumeHistory = listOf(
                    VolumeDataPoint(twoMonthsAgo, Weight.kilograms(1000.0)),
                    VolumeDataPoint(now, Weight.kilograms(1500.0)) // 50% growth
                ),
                oneRmHistory = listOf(
                    OneRmDataPoint(twoMonthsAgo, 100.0),
                    OneRmDataPoint(now, 120.0) // 20% growth
                )
            ),
            createPerformanceData(
                exerciseId = "ex2",
                exerciseName = "Bench",
                volumeHistory = listOf(
                    VolumeDataPoint(twoMonthsAgo, Weight.kilograms(800.0)),
                    VolumeDataPoint(now, Weight.kilograms(880.0)) // 10% growth
                ),
                oneRmHistory = listOf(
                    OneRmDataPoint(twoMonthsAgo, 80.0),
                    OneRmDataPoint(now, 84.0) // 5% growth
                )
            )
        )

        // When
        val result = service.calculateExerciseRanking(performanceData)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val rankings = result.getOrNull()!!
        assertEquals(2, rankings.size)
        assertEquals("ex1", rankings[0].exerciseId) // Squat should rank first (35% avg growth)
        assertEquals("ex2", rankings[1].exerciseId) // Bench should rank second (7.5% avg growth)
    }

    @Test
    fun `calculateExerciseRanking respects limit parameter`() = runTest {
        // Given
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val oneMonthAgo = now.minus(DatePeriod(months = 1))

        val performanceData = List(10) { index ->
            createPerformanceData(
                exerciseId = "ex$index",
                exerciseName = "Exercise $index",
                volumeHistory = listOf(
                    VolumeDataPoint(oneMonthAgo, Weight.kilograms(1000.0)),
                    VolumeDataPoint(now, Weight.kilograms(1100.0))
                ),
                oneRmHistory = listOf(
                    OneRmDataPoint(oneMonthAgo, 100.0),
                    OneRmDataPoint(now, 110.0)
                )
            )
        }

        // When
        val result = service.calculateExerciseRanking(performanceData, limit = 5)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val rankings = result.getOrNull()!!
        assertEquals(5, rankings.size)
    }

    @Test
    fun `calculateExerciseRanking filters exercises with insufficient data`() = runTest {
        // Given
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val performanceData = listOf(
            createPerformanceData(
                exerciseId = "ex1",
                exerciseName = "Valid",
                volumeHistory = listOf(
                    VolumeDataPoint(now, Weight.kilograms(1000.0)),
                    VolumeDataPoint(now, Weight.kilograms(1100.0))
                ),
                oneRmHistory = listOf(
                    OneRmDataPoint(now, 100.0),
                    OneRmDataPoint(now, 110.0)
                )
            ),
            createPerformanceData(
                exerciseId = "ex2",
                exerciseName = "Insufficient",
                volumeHistory = listOf(VolumeDataPoint(now, Weight.kilograms(1000.0))), // Only 1 data point
                oneRmHistory = listOf(OneRmDataPoint(now, 100.0)) // Only 1 data point
            )
        )

        // When
        val result = service.calculateExerciseRanking(performanceData)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val rankings = result.getOrNull()!!
        assertEquals(1, rankings.size)
        assertEquals("ex1", rankings[0].exerciseId)
    }

    // ========== Workout Metrics Tests ==========

    @Test
    fun `calculateWorkoutMetrics returns correct metrics`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(
                createExercise(sets = listOf(
                    createSet(weight = 100.0, reps = 10),
                    createSet(weight = 100.0, reps = 8)
                )),
                createExercise(sets = listOf(
                    createSet(weight = 50.0, reps = 12)
                ))
            ),
            durationMinutes = 45
        )

        // When
        val result = service.calculateWorkoutMetrics(workout)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val metrics = result.getOrNull()!!
        assertEquals(workout.id, metrics.workoutId)
        assertEquals(3, metrics.totalSets)
        assertEquals(30, metrics.totalReps) // 10 + 8 + 12
        assertEquals(45, metrics.durationMinutes)
        assertTrue("Total volume should be positive", metrics.totalVolume > 0.0)
    }

    @Test
    fun `calculateWorkoutMetrics estimates duration when not provided`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(
                createExercise(sets = listOf(
                    createSet(weight = 100.0, reps = 10),
                    createSet(weight = 100.0, reps = 10)
                ))
            ),
            durationMinutes = null
        )

        // When
        val result = service.calculateWorkoutMetrics(workout)

        // Then
        assertTrue("Should return success", result.isSuccess)
        val metrics = result.getOrNull()!!
        assertTrue("Duration should be estimated", metrics.durationMinutes > 0)
    }

    @Test
    fun `calculateWorkoutMetrics calculates efficiency metrics`() = runTest {
        // Given
        val workout = createWorkout(
            exercises = listOf(
                createExercise(sets = listOf(
                    createSet(weight = 100.0, reps = 10)
                ))
            ),
            durationMinutes = 30
        )

        // When
        val result = service.calculateWorkoutMetrics(workout)

        // Then
        val metrics = result.getOrNull()!!
        assertTrue("Volume per minute should be calculated", metrics.volumePerMinute > 0.0)
        assertTrue("Sets per minute should be calculated", metrics.setsPerMinute > 0.0)
    }

    // ========== One Rep Max Tests ==========

    @Test
    fun `calculateOneRepMax uses Epley formula correctly`() {
        // Given
        val weight = 100.0
        val reps = 10

        // When
        val oneRm = service.calculateOneRepMax(weight, reps)

        // Then
        // Epley: 1RM = 100 × (1 + 10/30) = 100 × 1.333 = 133.3
        assertEquals(133.33, oneRm, 0.1)
    }

    @Test
    fun `calculateOneRepMax handles single rep`() {
        // Given
        val weight = 100.0
        val reps = 1

        // When
        val oneRm = service.calculateOneRepMax(weight, reps)

        // Then
        // 1RM = 100 × (1 + 1/30) = 103.33
        assertEquals(103.33, oneRm, 0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateOneRepMax fails for zero weight`() {
        service.calculateOneRepMax(0.0, 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateOneRepMax fails for zero reps`() {
        service.calculateOneRepMax(100.0, 0)
    }

    // ========== Volume Calculation Tests ==========

    @Test
    fun `calculateVolume returns correct volume`() {
        // When
        val volume = service.calculateVolume(sets = 3, reps = 10, weight = 100.0)

        // Then
        assertEquals(3000.0, volume, 0.01)
    }

    @Test
    fun `calculateVolume handles bodyweight exercises`() {
        // When
        val volume = service.calculateVolume(sets = 3, reps = 10, weight = 0.0)

        // Then
        assertEquals(0.0, volume, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateVolume fails for negative weight`() {
        service.calculateVolume(3, 10, -50.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateVolume fails for zero sets`() {
        service.calculateVolume(0, 10, 100.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateVolume fails for zero reps`() {
        service.calculateVolume(3, 0, 100.0)
    }

    // ========== Performance Score Tests ==========

    @Test
    fun `calculatePerformanceScore averages growth percentages`() {
        // When
        val score = service.calculatePerformanceScore(
            volumeGrowthPercent = 20f,
            strengthGrowthPercent = 10f
        )

        // Then
        assertEquals(15f, score, 0.01f)
    }

    @Test
    fun `calculatePerformanceScore handles negative growth`() {
        // When
        val score = service.calculatePerformanceScore(
            volumeGrowthPercent = -10f,
            strengthGrowthPercent = 5f
        )

        // Then
        assertEquals(-2.5f, score, 0.01f)
    }

    @Test
    fun `calculatePerformanceScore handles zero growth`() {
        // When
        val score = service.calculatePerformanceScore(
            volumeGrowthPercent = 0f,
            strengthGrowthPercent = 0f
        )

        // Then
        assertEquals(0f, score, 0.01f)
    }

    // ========== Helper Methods ==========

    private fun createWorkout(
        exercises: List<Exercise>,
        durationMinutes: Int?
    ): Workout {
        return Workout(
            id = WorkoutId(UUID.randomUUID().toString()),
            userId = "test-user",
            name = "Test Workout",
            exercises = exercises,
            date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            status = WorkoutStatus.COMPLETED,
            durationMinutes = durationMinutes,
            notes = null,
            isSynced = true,
            syncVersion = 1L,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
    }

    private fun createExercise(
        sets: List<Set> = listOf(createSet())
    ): Exercise {
        return Exercise(
            id = UUID.randomUUID().toString(),
            name = "Test Exercise",
            sets = sets,
            muscleGroup = "Legs",
            equipmentType = "Barbell",
            notes = null
        )
    }

    private fun createSet(
        weight: Double = 100.0,
        reps: Int = 10
    ): Set {
        return Set(
            id = UUID.randomUUID().toString(),
            weight = Weight.kilograms(weight),
            reps = reps,
            isCompleted = true,
            restTimeSeconds = 90
        )
    }

    private fun createPerformanceData(
        exerciseId: String,
        exerciseName: String,
        volumeHistory: List<VolumeDataPoint>,
        oneRmHistory: List<OneRmDataPoint>
    ): ExercisePerformanceData {
        return ExercisePerformanceData(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            volumeHistory = volumeHistory,
            oneRmHistory = oneRmHistory
        )
    }
}
