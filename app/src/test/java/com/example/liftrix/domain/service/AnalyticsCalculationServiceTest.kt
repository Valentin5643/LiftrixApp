package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.RPE
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.usecase.analytics.ExercisePerformanceData
import com.example.liftrix.domain.usecase.analytics.PerformanceDataPoint
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
import java.time.Instant
import java.time.LocalDate

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
                    PerformanceDataPoint(date = twoMonthsAgo, volume = 1000.0),
                    PerformanceDataPoint(date = now, volume = 1500.0) // 50% growth
                ),
                oneRmHistory = listOf(
                    PerformanceDataPoint(date = twoMonthsAgo, oneRm = 100.0),
                    PerformanceDataPoint(date = now, oneRm = 120.0) // 20% growth
                )
            ),
            createPerformanceData(
                exerciseId = "ex2",
                exerciseName = "Bench",
                volumeHistory = listOf(
                    PerformanceDataPoint(date = twoMonthsAgo, volume = 800.0),
                    PerformanceDataPoint(date = now, volume = 880.0) // 10% growth
                ),
                oneRmHistory = listOf(
                    PerformanceDataPoint(date = twoMonthsAgo, oneRm = 80.0),
                    PerformanceDataPoint(date = now, oneRm = 84.0) // 5% growth
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
                    PerformanceDataPoint(date = oneMonthAgo, volume = 1000.0),
                    PerformanceDataPoint(date = now, volume = 1100.0)
                ),
                oneRmHistory = listOf(
                    PerformanceDataPoint(date = oneMonthAgo, oneRm = 100.0),
                    PerformanceDataPoint(date = now, oneRm = 110.0)
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
                    PerformanceDataPoint(date = now, volume = 1000.0),
                    PerformanceDataPoint(date = now, volume = 1100.0)
                ),
                oneRmHistory = listOf(
                    PerformanceDataPoint(date = now, oneRm = 100.0),
                    PerformanceDataPoint(date = now, oneRm = 110.0)
                )
            ),
            createPerformanceData(
                exerciseId = "ex2",
                exerciseName = "Insufficient",
                volumeHistory = listOf(PerformanceDataPoint(date = now, volume = 1000.0)), // Only 1 data point
                oneRmHistory = listOf(PerformanceDataPoint(date = now, oneRm = 100.0)) // Only 1 data point
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
        assertEquals(workout.id.value, metrics.workoutId)
        assertEquals(3, metrics.totalSets)
        assertEquals(30, metrics.totalReps.count) // 10 + 8 + 12
        assertEquals(45L, metrics.sessionDuration?.toMinutes())
        assertTrue("Total volume should be positive", metrics.totalVolume.kilograms > 0.0)
    }

    @Test
    fun `calculateWorkoutMetrics preserves absent duration`() = runTest {
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
        assertTrue("Duration should remain absent", metrics.sessionDuration == null)
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
        assertTrue("Volume per minute should be calculated", metrics.volumeEfficiency > 0.0f)
    }

    @Test
    fun `calculateWorkoutMetrics normalizes average RPE as intensity`() = runTest {
        val workout = createWorkout(
            exercises = listOf(
                createExercise(
                    sets = listOf(
                        createSet(rpe = 6),
                        createSet(rpe = 8)
                    )
                )
            ),
            durationMinutes = 30
        )

        val metrics = service.calculateWorkoutMetrics(workout).getOrThrow()

        assertEquals(0.7f, metrics.averageIntensity, 0.001f)
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
        val workoutId = WorkoutId.generate()
        val now = Instant.now()
        return Workout(
            userId = "test-user",
            id = workoutId,
            name = "Test Workout",
            date = LocalDate.now(),
            exercises = exercises.map { it.copy(workoutId = workoutId) },
            status = WorkoutStatus.COMPLETED,
            startTime = durationMinutes?.let { now.minusSeconds(it * 60L) },
            endTime = durationMinutes?.let { now },
            notes = null,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun createExercise(
        sets: List<ExerciseSet> = listOf(createSet())
    ): Exercise {
        return Exercise(
            id = ExerciseId.generate(),
            workoutId = WorkoutId.generate(),
            libraryExercise = ExerciseLibrary(
                id = "test-exercise",
                name = "Test Exercise",
                primaryMuscleGroup = ExerciseCategory.QUADRICEPS,
                equipment = Equipment.BARBELL,
                secondaryMuscleGroups = emptyList(),
                movementPattern = "squat",
                difficultyLevel = 1,
                instructions = null,
                isCompound = true,
                searchableTerms = listOf("test")
            ),
            orderIndex = 0,
            sets = sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) },
            createdAt = Instant.now()
        )
    }

    private fun createSet(
        weight: Double = 100.0,
        reps: Int = 10,
        rpe: Int? = null
    ): ExerciseSet {
        return ExerciseSet(
            id = ExerciseSetId.generate(),
            setNumber = 1,
            weight = Weight.fromKilograms(weight),
            reps = Reps(reps),
            rpe = rpe?.let(::RPE),
            completedAt = Instant.now()
        )
    }

    private fun createPerformanceData(
        exerciseId: String,
        exerciseName: String,
        volumeHistory: List<PerformanceDataPoint>,
        oneRmHistory: List<PerformanceDataPoint>
    ): ExercisePerformanceData {
        return ExercisePerformanceData(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            volumeHistory = volumeHistory,
            oneRmHistory = oneRmHistory
        )
    }
}
