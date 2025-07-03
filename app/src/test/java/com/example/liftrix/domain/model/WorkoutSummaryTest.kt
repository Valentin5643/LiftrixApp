package com.example.liftrix.domain.model

import com.example.liftrix.TestDataFactory
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class WorkoutSummaryTest {

    @Test
    fun `workout summary creation with valid data should succeed`() {
        val summary = createTestWorkoutSummary()
        
        assertEquals("test-workout-id", summary.id.value)
        assertEquals("test-user-id", summary.userId)
        assertEquals("Test Workout", summary.name)
        assertEquals(LocalDate.now(), summary.date)
        assertEquals(Duration.ofMinutes(45), summary.duration)
        assertEquals(3, summary.exerciseCount)
        assertEquals(8, summary.completedSets)
        assertEquals(12, summary.totalSets)
        assertEquals(WorkoutStatus.COMPLETED, summary.status)
        assertEquals(66.67, summary.completionPercentage, 0.01)
    }

    @Test
    fun `workout summary with blank userId should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(userId = "")
        }
    }

    @Test
    fun `workout summary with blank name should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(name = "")
        }
    }

    @Test
    fun `workout summary with name exceeding max length should throw exception`() {
        val longName = "a".repeat(WorkoutSummary.MAX_NAME_LENGTH + 1)
        
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(name = longName)
        }
    }

    @Test
    fun `workout summary with negative exercise count should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(exerciseCount = -1)
        }
    }

    @Test
    fun `workout summary with negative completed sets should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(completedSets = -1)
        }
    }

    @Test
    fun `workout summary with negative total sets should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(totalSets = -1)
        }
    }

    @Test
    fun `workout summary with completed sets exceeding total sets should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(completedSets = 15, totalSets = 10)
        }
    }

    @Test
    fun `workout summary with completion percentage below zero should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(completionPercentage = -5.0)
        }
    }

    @Test
    fun `workout summary with completion percentage above 100 should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(completionPercentage = 105.0)
        }
    }

    @Test
    fun `workout summary with negative duration should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(duration = Duration.ofMinutes(-10))
        }
    }

    @Test
    fun `workout summary with duration exceeding max hours should throw exception`() {
        val excessiveDuration = Duration.ofHours(WorkoutSummary.MAX_WORKOUT_HOURS + 1)
        
        assertThrows(IllegalArgumentException::class.java) {
            createTestWorkoutSummary().copy(duration = excessiveDuration)
        }
    }

    @Test
    fun `workout summary with null duration should be valid`() {
        val summary = createTestWorkoutSummary().copy(duration = null)
        
        assertNull(summary.duration)
        assertEquals("Unknown", summary.getFormattedDuration())
    }

    @Test
    fun `isCompleted should return true for completed status`() {
        val summary = createTestWorkoutSummary().copy(status = WorkoutStatus.COMPLETED)
        
        assertTrue(summary.isCompleted())
        assertFalse(summary.isInProgress())
        assertFalse(summary.isPlanned())
    }

    @Test
    fun `isInProgress should return true for in progress status`() {
        val summary = createTestWorkoutSummary().copy(status = WorkoutStatus.IN_PROGRESS)
        
        assertTrue(summary.isInProgress())
        assertFalse(summary.isCompleted())
        assertFalse(summary.isPlanned())
    }

    @Test
    fun `isPlanned should return true for planned status`() {
        val summary = createTestWorkoutSummary().copy(status = WorkoutStatus.PLANNED)
        
        assertTrue(summary.isPlanned())
        assertFalse(summary.isCompleted())
        assertFalse(summary.isInProgress())
    }

    @Test
    fun `getFormattedDuration should format hours and minutes correctly`() {
        val summary = createTestWorkoutSummary().copy(duration = Duration.ofHours(2).plusMinutes(30))
        
        assertEquals("2h 30m", summary.getFormattedDuration())
    }

    @Test
    fun `getFormattedDuration should format minutes only when no hours`() {
        val summary = createTestWorkoutSummary().copy(duration = Duration.ofMinutes(45))
        
        assertEquals("45m", summary.getFormattedDuration())
    }

    @Test
    fun `getFormattedDuration should format seconds when less than one minute`() {
        val summary = createTestWorkoutSummary().copy(duration = Duration.ofSeconds(30))
        
        assertEquals("30s", summary.getFormattedDuration())
    }

    @Test
    fun `getFormattedDuration should return Unknown for null duration`() {
        val summary = createTestWorkoutSummary().copy(duration = null)
        
        assertEquals("Unknown", summary.getFormattedDuration())
    }

    @Test
    fun `workout to summary conversion includes all required fields`() {
        val workout = TestDataFactory.sampleWorkout
        val summary = workout.toSummary()
        
        assertEquals(workout.id, summary.id)
        assertEquals(workout.userId, summary.userId)
        assertEquals(workout.name, summary.name)
        assertEquals(workout.date, summary.date)
        assertEquals(workout.getDuration(), summary.duration)
        assertEquals(workout.exercises.size, summary.exerciseCount)
        assertEquals(workout.getCompletedSets(), summary.completedSets)
        assertEquals(workout.getTotalSets(), summary.totalSets)
        assertEquals(workout.status, summary.status)
        assertEquals(workout.getCompletionPercentage(), summary.completionPercentage, 0.01)
    }

    @Test
    fun `workout to summary conversion with null duration should work`() {
        val workout = TestDataFactory.sampleWorkout.copy(
            startTime = null,
            endTime = null
        )
        val summary = workout.toSummary()
        
        assertNull(summary.duration)
        assertEquals("Unknown", summary.getFormattedDuration())
    }

    @Test
    fun `workout to summary conversion with zero exercises should work`() {
        val workout = TestDataFactory.sampleWorkout.copy(exercises = emptyList())
        val summary = workout.toSummary()
        
        assertEquals(0, summary.exerciseCount)
        assertEquals(0, summary.completedSets)
        assertEquals(0, summary.totalSets)
        assertEquals(0.0, summary.completionPercentage, 0.01)
    }

    @Test
    fun `workout to summary conversion for planned workout should work`() {
        val workout = TestDataFactory.sampleWorkout.copy(
            status = WorkoutStatus.PLANNED,
            startTime = null,
            endTime = null
        )
        val summary = workout.toSummary()
        
        assertEquals(WorkoutStatus.PLANNED, summary.status)
        assertTrue(summary.isPlanned())
        assertNull(summary.duration)
    }

    @Test
    fun `workout to summary conversion for in progress workout should work`() {
        val workout = TestDataFactory.sampleWorkout.copy(
            status = WorkoutStatus.IN_PROGRESS,
            endTime = null
        )
        val summary = workout.toSummary()
        
        assertEquals(WorkoutStatus.IN_PROGRESS, summary.status)
        assertTrue(summary.isInProgress())
    }

    @Test
    fun `workout summary validation allows edge case values`() {
        // Test with boundary values that should be valid
        val summary = WorkoutSummary(
            id = WorkoutId.generate(),
            userId = "u",
            name = "a".repeat(WorkoutSummary.MAX_NAME_LENGTH), // Exactly at limit
            date = LocalDate.now(),
            duration = Duration.ofHours(WorkoutSummary.MAX_WORKOUT_HOURS), // Exactly at limit
            exerciseCount = 0,
            completedSets = 0,
            totalSets = 0,
            status = WorkoutStatus.PLANNED,
            completionPercentage = 0.0 // Exactly at lower bound
        )
        
        assertNotNull(summary)
        assertEquals(0.0, summary.completionPercentage, 0.01)
    }

    @Test
    fun `workout summary validation allows maximum completion percentage`() {
        val summary = createTestWorkoutSummary().copy(completionPercentage = 100.0)
        
        assertEquals(100.0, summary.completionPercentage, 0.01)
    }

    @Test
    fun `workout summary with maximum valid values should succeed`() {
        val summary = WorkoutSummary(
            id = WorkoutId.generate(),
            userId = "max-user-id",
            name = "a".repeat(WorkoutSummary.MAX_NAME_LENGTH),
            date = LocalDate.now(),
            duration = Duration.ofHours(WorkoutSummary.MAX_WORKOUT_HOURS),
            exerciseCount = 50,
            completedSets = 100,
            totalSets = 100,
            status = WorkoutStatus.COMPLETED,
            completionPercentage = 100.0
        )
        
        assertNotNull(summary)
        assertTrue(summary.isCompleted())
        assertEquals("${WorkoutSummary.MAX_WORKOUT_HOURS}h 0m", summary.getFormattedDuration())
    }

    // Helper method to create test data
    private fun createTestWorkoutSummary(): WorkoutSummary {
        return WorkoutSummary(
            id = WorkoutId("test-workout-id"),
            userId = "test-user-id",
            name = "Test Workout",
            date = LocalDate.now(),
            duration = Duration.ofMinutes(45),
            exerciseCount = 3,
            completedSets = 8,
            totalSets = 12,
            status = WorkoutStatus.COMPLETED,
            completionPercentage = 66.67
        )
    }
}