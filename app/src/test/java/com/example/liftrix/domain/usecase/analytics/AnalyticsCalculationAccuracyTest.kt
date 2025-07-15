package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.analytics.WorkoutMetrics
import com.example.liftrix.domain.model.analytics.VolumeCalendarData
import com.example.liftrix.domain.model.analytics.CalorieCalculator
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

/**
 * Integration tests for analytics calculation accuracy with known datasets
 * 
 * Validates:
 * - Volume calculation accuracy across different workout types
 * - Calorie calculation precision using standardized MET values
 * - Calendar data aggregation correctness
 * - Edge cases and boundary conditions
 * - Performance characteristics of calculation algorithms
 */
@DisplayName("Analytics Calculation Accuracy Tests")
class AnalyticsCalculationAccuracyTest {

    private lateinit var testWorkoutMetrics: List<WorkoutMetrics>
    private lateinit var testVolumeCalendarData: VolumeCalendarData
    
    @BeforeEach
    fun setUp() {
        testWorkoutMetrics = createTestWorkoutMetrics()
        testVolumeCalendarData = createTestVolumeCalendarData()
    }

    @Nested
    @DisplayName("Volume Calculation Tests")
    inner class VolumeCalculationTests {

        @Test
        @DisplayName("Should calculate accurate total volume for strength workout")
        fun testStrengthWorkoutVolumeCalculation() {
            // Given: Strength-focused workout data
            val strengthWorkout = testWorkoutMetrics.first { it.categories.contains(ExerciseCategory.STRENGTH) }
            
            // When: Calculating volume metrics
            val totalVolume = strengthWorkout.totalVolume
            val volumePerExercise = strengthWorkout.getAverageVolumePerExercise()
            val volumeEfficiency = strengthWorkout.volumeEfficiency
            
            // Then: Volume calculations should be accurate
            assertTrue(totalVolume > Weight.ZERO, "Total volume should be positive")
            assertTrue(volumePerExercise.kilograms > 0, "Volume per exercise should be positive")
            assertTrue(volumeEfficiency > 0, "Volume efficiency should be positive for workouts with duration")
            
            // Volume should be realistic for strength training (typically 2000-8000kg)
            assertTrue(totalVolume.kilograms in 1000.0..10000.0, 
                "Total volume should be realistic for strength training: ${totalVolume.kilograms}kg")
        }

        @Test
        @DisplayName("Should handle zero volume workouts gracefully")
        fun testZeroVolumeWorkout() {
            // Given: Workout with zero volume
            val zeroVolumeWorkout = WorkoutMetrics.fromBasicData(
                workoutId = "zero-volume-test",
                userId = "test-user",
                date = LocalDate(2025, 7, 14),
                totalVolume = Weight.ZERO,
                duration = Duration.ofMinutes(30),
                exerciseCount = 2,
                totalSets = 6,
                completedSets = 6,
                totalReps = Reps(30),
                categories = setOf(ExerciseCategory.CARDIO)
            )
            
            // When: Accessing volume metrics
            val intensity = zeroVolumeWorkout.getVolumeIntensityFactor()
            val avgVolumePerExercise = zeroVolumeWorkout.getAverageVolumePerExercise()
            val qualityScore = zeroVolumeWorkout.getWorkoutQualityScore()
            
            // Then: Should handle gracefully without errors
            assertEquals(0.0f, intensity, 0.01f)
            assertEquals(Weight.ZERO, avgVolumePerExercise)
            assertTrue(qualityScore >= 0.0f && qualityScore <= 1.0f)
        }

        @Test
        @DisplayName("Should calculate volume intensity factor correctly")
        fun testVolumeIntensityFactor() {
            // Given: Known workout data
            val workout = testWorkoutMetrics[0]
            
            // When: Calculating intensity factor
            val intensityFactor = workout.getVolumeIntensityFactor()
            
            // Then: Should be within valid range and proportional to volume/intensity
            assertTrue(intensityFactor in 0.0f..1.0f, 
                "Intensity factor should be 0.0-1.0: $intensityFactor")
            
            // Higher volume + higher intensity should result in higher factor
            val highVolumeWorkout = workout.copy(
                totalVolume = Weight(8000.0),
                averageIntensity = 0.9f
            )
            val highIntensityFactor = highVolumeWorkout.getVolumeIntensityFactor()
            
            assertTrue(highIntensityFactor >= intensityFactor,
                "Higher volume/intensity should result in higher intensity factor")
        }
    }

    @Nested
    @DisplayName("Calorie Calculation Tests")
    inner class CalorieCalculationTests {

        @Test
        @DisplayName("Should calculate realistic calories for typical strength workout")
        fun testRealisticCalorieCalculation() {
            // Given: Typical strength workout
            val strengthWorkout = testWorkoutMetrics.find { 
                it.sessionDuration != null && it.exerciseCount >= 4 
            }!!
            
            // When: Checking calorie calculation
            val caloriesBurned = strengthWorkout.caloriesBurned
            val durationHours = strengthWorkout.sessionDuration!!.toMinutes() / 60.0
            val caloriesPerHour = caloriesBurned / durationHours
            
            // Then: Should be within realistic range for strength training
            assertTrue(caloriesBurned > 0, "Calories burned should be positive")
            assertTrue(caloriesPerHour in 200.0..800.0, 
                "Calories per hour should be realistic for strength training: $caloriesPerHour")
            
            // Should not exceed maximum realistic calories
            assertTrue(caloriesBurned <= WorkoutMetrics.MAX_REALISTIC_CALORIES_PER_HOUR * durationHours,
                "Calories should not exceed maximum realistic amount")
        }

        @Test
        @DisplayName("Should scale calories with workout duration")
        fun testCalorieScalingWithDuration() {
            // Given: Same workout with different durations
            val baseWorkout = testWorkoutMetrics[0]
            val shortWorkout = baseWorkout.copy(sessionDuration = Duration.ofMinutes(30))
            val longWorkout = baseWorkout.copy(sessionDuration = Duration.ofMinutes(90))
            
            // When: Recalculating calories (using estimation method)
            val shortCalories = WorkoutMetrics.fromBasicData(
                workoutId = shortWorkout.workoutId,
                userId = shortWorkout.userId,
                date = shortWorkout.date,
                totalVolume = shortWorkout.totalVolume,
                duration = shortWorkout.sessionDuration,
                exerciseCount = shortWorkout.exerciseCount,
                totalSets = shortWorkout.totalSets,
                completedSets = shortWorkout.completedSets,
                totalReps = shortWorkout.totalReps,
                categories = shortWorkout.categories
            ).caloriesBurned
            
            val longCalories = WorkoutMetrics.fromBasicData(
                workoutId = longWorkout.workoutId,
                userId = longWorkout.userId,
                date = longWorkout.date,
                totalVolume = longWorkout.totalVolume,
                duration = longWorkout.sessionDuration,
                exerciseCount = longWorkout.exerciseCount,
                totalSets = longWorkout.totalSets,
                completedSets = longWorkout.completedSets,
                totalReps = longWorkout.totalReps,
                categories = longWorkout.categories
            ).caloriesBurned
            
            // Then: Longer workout should burn more calories
            assertTrue(longCalories > shortCalories,
                "Longer workout should burn more calories: $longCalories vs $shortCalories")
            
            // Calories should scale approximately linearly with duration
            val durationRatio = 90.0 / 30.0 // 3x longer
            val calorieRatio = longCalories.toDouble() / shortCalories.toDouble()
            assertTrue(calorieRatio >= 2.0 && calorieRatio <= 4.0,
                "Calorie scaling should be reasonable with duration: ${calorieRatio}x")
        }

        @Test
        @DisplayName("Should handle workouts without duration")
        fun testWorkoutWithoutDuration() {
            // Given: Workout without duration
            val workoutNoDuration = testWorkoutMetrics[0].copy(sessionDuration = null)
            
            // When: Accessing calorie data
            val formattedDuration = workoutNoDuration.getFormattedDuration()
            val volumeEfficiency = workoutNoDuration.volumeEfficiency
            
            // Then: Should handle gracefully
            assertEquals("Unknown", formattedDuration)
            assertEquals(0.0f, volumeEfficiency)
            assertNotNull(workoutNoDuration.caloriesBurned) // Should still have value from creation
        }
    }

    @Nested
    @DisplayName("Volume Calendar Tests")
    inner class VolumeCalendarTests {

        @Test
        @DisplayName("Should calculate accurate monthly volume aggregation")
        fun testMonthlyVolumeAggregation() {
            // Given: Volume calendar with known data
            val calendar = testVolumeCalendarData
            
            // When: Calculating aggregated values
            val totalVolume = calendar.getTotalMonthVolume()
            val averageVolume = calendar.averageVolume
            val maxVolume = calendar.maxVolume
            val workoutDays = calendar.getWorkoutDaysCount()
            
            // Then: Aggregation should be mathematically correct
            val expectedTotal = calendar.dailyVolumes.values.fold(Weight.ZERO) { acc, vol -> acc + vol }
            assertEquals(expectedTotal.kilograms, totalVolume.kilograms, 0.01)
            
            assertTrue(maxVolume >= averageVolume, "Max volume should be >= average volume")
            assertTrue(workoutDays >= 0, "Workout days count should be non-negative")
            assertTrue(workoutDays <= calendar.dailyVolumes.size, "Workout days should not exceed total days")
        }

        @Test
        @DisplayName("Should calculate volume intensity correctly for calendar display")
        fun testVolumeIntensityCalculation() {
            // Given: Calendar with varied volume data
            val calendar = testVolumeCalendarData
            val testDate = calendar.dailyVolumes.keys.first()
            
            // When: Calculating intensity
            val intensity = calendar.getVolumeIntensity(testDate)
            val volumeForDate = calendar.getVolumeForDate(testDate)
            
            // Then: Intensity should be proportional to volume
            assertTrue(intensity in 0.0f..1.0f, "Intensity should be 0.0-1.0: $intensity")
            
            if (volumeForDate > Weight.ZERO && calendar.maxVolume > Weight.ZERO) {
                val expectedIntensity = volumeForDate.kilograms / calendar.maxVolume.kilograms
                assertEquals(expectedIntensity.toFloat(), intensity, 0.01f)
            }
            
            // Date with no data should have zero intensity
            val emptyDate = LocalDate(2025, 7, 31)
            val emptyIntensity = calendar.getVolumeIntensity(emptyDate)
            assertEquals(0.0f, emptyIntensity)
        }

        @Test
        @DisplayName("Should generate correct calendar grid layout")
        fun testCalendarGridGeneration() {
            // Given: Volume calendar
            val calendar = testVolumeCalendarData
            
            // When: Generating calendar grid
            val calendarDays = calendar.getDaysInCalendarGrid()
            
            // Then: Should have exactly 42 days (6 weeks)
            assertEquals(VolumeCalendarData.DAYS_IN_CALENDAR_GRID, calendarDays.size,
                "Calendar grid should have exactly 42 days")
            
            // Should include current month days
            val currentMonthDays = calendarDays.filter { it.isCurrentMonth }
            val expectedCurrentMonthDays = calendar.month.length(isLeapYear(calendar.year))
            assertEquals(expectedCurrentMonthDays, currentMonthDays.size,
                "Should include all days of current month")
            
            // Should have sequential dates
            calendarDays.zipWithNext { current, next ->
                val daysDiff = next.date.toEpochDays() - current.date.toEpochDays()
                assertEquals(1, daysDiff, "Calendar days should be sequential")
            }
        }

        @Test
        @DisplayName("Should calculate workout frequency accurately")
        fun testWorkoutFrequencyCalculation() {
            // Given: Calendar with known workout pattern
            val calendar = testVolumeCalendarData
            
            // When: Calculating frequency
            val frequency = calendar.getWorkoutFrequency()
            val workoutDays = calendar.getWorkoutDaysCount()
            val daysInMonth = calendar.month.length(isLeapYear(calendar.year))
            
            // Then: Frequency should be accurate ratio
            val expectedFrequency = workoutDays.toFloat() / daysInMonth.toFloat()
            assertEquals(expectedFrequency, frequency, 0.01f)
            assertTrue(frequency in 0.0f..1.0f, "Frequency should be 0.0-1.0")
        }

        @Test
        @DisplayName("Should validate calendar data constraints")
        fun testCalendarDataValidation() {
            // Given: Invalid calendar data attempts
            
            // When/Then: Should throw for invalid year
            assertThrows<IllegalArgumentException> {
                VolumeCalendarData(
                    year = 2010, // Before MIN_YEAR
                    month = Month.JANUARY,
                    dailyVolumes = emptyMap(),
                    maxVolume = Weight.ZERO,
                    averageVolume = Weight.ZERO
                )
            }
            
            // Should throw for negative volumes
            assertThrows<IllegalArgumentException> {
                VolumeCalendarData(
                    year = 2025,
                    month = Month.JULY,
                    dailyVolumes = emptyMap(),
                    maxVolume = Weight(-100.0), // Negative weight
                    averageVolume = Weight.ZERO
                )
            }
            
            // Should throw for inconsistent max volume
            assertThrows<IllegalArgumentException> {
                VolumeCalendarData(
                    year = 2025,
                    month = Month.JULY,
                    dailyVolumes = mapOf(LocalDate(2025, 7, 1) to Weight(1000.0)),
                    maxVolume = Weight(500.0), // Less than actual max
                    averageVolume = Weight(750.0)
                )
            }
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    inner class PerformanceTests {

        @Test
        @DisplayName("Should calculate complex analytics within performance target")
        fun testAnalyticsCalculationPerformance() {
            // Given: Large dataset for performance testing
            val largeWorkoutSet = (1..100).map { i ->
                WorkoutMetrics.fromBasicData(
                    workoutId = "perf-test-$i",
                    userId = "test-user",
                    date = LocalDate(2025, 7, i % 28 + 1),
                    totalVolume = Weight(2000.0 + i * 50),
                    duration = Duration.ofMinutes(45 + i % 30),
                    exerciseCount = 4 + i % 3,
                    totalSets = 12 + i % 8,
                    completedSets = 10 + i % 8,
                    totalReps = Reps(80 + i % 40),
                    categories = setOf(ExerciseCategory.STRENGTH, ExerciseCategory.UPPER_BODY)
                )
            }
            
            // When: Performing batch calculations with timing
            val startTime = System.currentTimeMillis()
            
            val totalVolume = largeWorkoutSet.sumOf { it.totalVolume.kilograms }
            val averageCalories = largeWorkoutSet.map { it.caloriesBurned }.average()
            val qualityScores = largeWorkoutSet.map { it.getWorkoutQualityScore() }
            val trainingLoads = largeWorkoutSet.map { it.calculateTrainingLoad() }
            
            val endTime = System.currentTimeMillis()
            val calculationTime = endTime - startTime
            
            // Then: Should complete within performance target
            assertTrue(calculationTime < 500, 
                "Analytics calculations should complete within 500ms: ${calculationTime}ms")
            
            // Results should be reasonable
            assertTrue(totalVolume > 0, "Total volume should be positive")
            assertTrue(averageCalories > 0, "Average calories should be positive")
            assertTrue(qualityScores.all { it in 0.0f..1.0f }, "Quality scores should be 0.0-1.0")
            assertTrue(trainingLoads.all { it >= 0.0f }, "Training loads should be non-negative")
        }

        @Test
        @DisplayName("Should handle calendar operations efficiently")
        fun testCalendarOperationPerformance() {
            // Given: Calendar with full month of data
            val fullMonthData = (1..31).associate { day ->
                LocalDate(2025, 7, day) to Weight(1500.0 + day * 100)
            }
            val fullCalendar = VolumeCalendarData(
                year = 2025,
                month = Month.JULY,
                dailyVolumes = fullMonthData,
                maxVolume = Weight(4600.0),
                averageVolume = Weight(3100.0)
            )
            
            // When: Performing intensive calendar operations
            val startTime = System.currentTimeMillis()
            
            repeat(1000) {
                fullCalendar.getDaysInCalendarGrid()
                fullCalendar.getTotalMonthVolume()
                fullCalendar.getWorkoutFrequency()
                fullCalendar.getVolumeIntensity(LocalDate(2025, 7, it % 28 + 1))
            }
            
            val endTime = System.currentTimeMillis()
            val operationTime = endTime - startTime
            
            // Then: Should complete within reasonable time
            assertTrue(operationTime < 200, 
                "Calendar operations should be efficient: ${operationTime}ms for 1000 iterations")
        }
    }

    // Test data creation helpers
    private fun createTestWorkoutMetrics(): List<WorkoutMetrics> {
        return listOf(
            // Strength-focused workout
            WorkoutMetrics(
                workoutId = "strength-test-1",
                userId = "test-user",
                date = LocalDate(2025, 7, 14),
                totalVolume = Weight(4500.0),
                sessionDuration = Duration.ofMinutes(75),
                caloriesBurned = 420,
                exerciseCount = 5,
                totalSets = 16,
                completedSets = 16,
                totalReps = Reps(64),
                completionPercentage = 100.0,
                averageIntensity = 0.8f,
                volumeEfficiency = 60.0f,
                categories = setOf(ExerciseCategory.STRENGTH, ExerciseCategory.UPPER_BODY)
            ),
            // Cardio-focused workout
            WorkoutMetrics(
                workoutId = "cardio-test-1",
                userId = "test-user",
                date = LocalDate(2025, 7, 13),
                totalVolume = Weight(800.0),
                sessionDuration = Duration.ofMinutes(45),
                caloriesBurned = 380,
                exerciseCount = 3,
                totalSets = 12,
                completedSets = 11,
                totalReps = Reps(120),
                completionPercentage = 91.7,
                averageIntensity = 0.7f,
                volumeEfficiency = 17.8f,
                categories = setOf(ExerciseCategory.CARDIO, ExerciseCategory.FULL_BODY)
            ),
            // Mixed workout
            WorkoutMetrics(
                workoutId = "mixed-test-1",
                userId = "test-user",
                date = LocalDate(2025, 7, 12),
                totalVolume = Weight(2800.0),
                sessionDuration = Duration.ofMinutes(60),
                caloriesBurned = 340,
                exerciseCount = 6,
                totalSets = 18,
                completedSets = 17,
                totalReps = Reps(96),
                completionPercentage = 94.4,
                averageIntensity = 0.6f,
                volumeEfficiency = 46.7f,
                categories = setOf(ExerciseCategory.STRENGTH, ExerciseCategory.CARDIO, ExerciseCategory.LOWER_BODY)
            )
        )
    }
    
    private fun createTestVolumeCalendarData(): VolumeCalendarData {
        val testDailyVolumes = mapOf(
            LocalDate(2025, 7, 1) to Weight(3200.0),
            LocalDate(2025, 7, 3) to Weight(2800.0),
            LocalDate(2025, 7, 5) to Weight(4100.0),
            LocalDate(2025, 7, 8) to Weight(3600.0),
            LocalDate(2025, 7, 10) to Weight(2900.0),
            LocalDate(2025, 7, 12) to Weight(3800.0),
            LocalDate(2025, 7, 14) to Weight(4500.0),
            LocalDate(2025, 7, 17) to Weight(3300.0),
            LocalDate(2025, 7, 19) to Weight(3900.0),
            LocalDate(2025, 7, 21) to Weight(3100.0)
        )
        
        return VolumeCalendarData(
            year = 2025,
            month = Month.JULY,
            dailyVolumes = testDailyVolumes,
            maxVolume = Weight(4500.0),
            averageVolume = Weight(3520.0)
        )
    }
    
    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
}