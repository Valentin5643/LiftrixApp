package com.example.liftrix.domain.model

import com.example.liftrix.TestDataFactory
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkoutTest {

    @Test
    fun `workout creation with valid data should succeed`() {
        val workout = TestDataFactory.sampleWorkout
        
        assertEquals("test-user-id", workout.userId)
        assertEquals("Upper Body Workout", workout.name)
        assertEquals(LocalDate.now(), workout.date)
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertEquals(2, workout.exercises.size)
    }

    @Test
    fun `workout with blank userId should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(userId = "")
        }
    }

    @Test
    fun `workout with blank name should throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(name = "")
        }
    }

    @Test
    fun `workout with name exceeding max length should throw exception`() {
        val longName = "a".repeat(Workout.MAX_NAME_LENGTH + 1)
        
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(name = longName)
        }
    }

    @Test
    fun `workout with too many exercises should throw exception`() {
        val manyExercises = (1..Workout.MAX_EXERCISES + 1).map {
            TestDataFactory.benchPressExercise.copy(id = ExerciseId.generate())
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(exercises = manyExercises)
        }
    }

    @Test
    fun `workout with notes exceeding max length should throw exception`() {
        val longNotes = "a".repeat(Workout.MAX_NOTES_LENGTH + 1)
        
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(notes = longNotes)
        }
    }

    @Test
    fun `workout with end time before start time should throw exception`() {
        val startTime = Instant.now()
        val endTime = startTime.minusSeconds(3600)
        
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    @Test
    fun `workout with duration exceeding max hours should throw exception`() {
        val startTime = Instant.now()
        val endTime = startTime.plusSeconds((Workout.MAX_WORKOUT_HOURS + 1) * 3600)
        
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    @Test
    fun `workout with duplicate exercise IDs should throw exception`() {
        val duplicateExercise = TestDataFactory.benchPressExercise.copy(
            id = TestDataFactory.benchPressExercise.id
        )
        
        assertThrows(IllegalArgumentException::class.java) {
            TestDataFactory.sampleWorkout.copy(
                exercises = listOf(TestDataFactory.benchPressExercise, duplicateExercise)
            )
        }
    }

    @Test
    fun `getDuration should return correct duration`() {
        val startTime = Instant.now()
        val endTime = startTime.plusSeconds(3600)
        val workout = TestDataFactory.sampleWorkout.copy(
            startTime = startTime,
            endTime = endTime
        )
        
        val duration = workout.getDuration()
        
        assertNotNull(duration)
        assertEquals(3600, duration!!.seconds)
    }

    @Test
    fun `getDuration should return null when start or end time is null`() {
        val workout = TestDataFactory.sampleWorkout.copy(
            startTime = Instant.now(),
            endTime = null
        )
        
        assertNull(workout.getDuration())
    }

    @Test
    fun `calculateTotalVolume should sum all exercise volumes`() {
        val workout = TestDataFactory.completedWorkout
        val totalVolume = workout.calculateTotalVolume()
        
        assertTrue(totalVolume.kilograms > 0.0)
    }

    @Test
    fun `getTotalSets should count all sets across exercises`() {
        val workout = TestDataFactory.sampleWorkout
        val totalSets = workout.getTotalSets()
        
        assertEquals(3, totalSets) // 2 sets from bench press + 1 set from squat
    }

    @Test
    fun `getCompletedSets should count only completed sets`() {
        val workout = TestDataFactory.sampleWorkout
        val completedSets = workout.getCompletedSets()
        
        assertEquals(2, completedSets) // 1 completed from bench press + 1 from squat
    }

    @Test
    fun `getCompletionPercentage should calculate correct percentage`() {
        val workout = TestDataFactory.sampleWorkout
        val percentage = workout.getCompletionPercentage()
        
        assertEquals(66.67, percentage, 0.01) // 2 completed out of 3 total
    }

    @Test
    fun `getCompletionPercentage should return zero for empty workout`() {
        val workout = TestDataFactory.sampleWorkout.copy(exercises = emptyList())
        val percentage = workout.getCompletionPercentage()
        
        assertEquals(0.0, percentage, 0.01)
    }

    @Test
    fun `isCompleted should return true when all exercises are completed`() {
        val workout = TestDataFactory.completedWorkout
        
        assertTrue(workout.isCompleted())
    }

    @Test
    fun `isCompleted should return false when not all exercises are completed`() {
        val workout = TestDataFactory.sampleWorkout
        
        assertFalse(workout.isCompleted())
    }

    @Test
    fun `isCompleted should return false for empty workout`() {
        val workout = TestDataFactory.sampleWorkout.copy(exercises = emptyList())
        
        assertFalse(workout.isCompleted())
    }

    @Test
    fun `getExerciseCategories should return unique categories`() {
        val workout = TestDataFactory.sampleWorkout
        val categories = workout.getExerciseCategories()
        
        assertEquals(2, categories.size)
        assertTrue(categories.contains(ExerciseCategory.CHEST))
        assertTrue(categories.contains(ExerciseCategory.LEGS))
    }

    @Test
    fun `start should set status to IN_PROGRESS and update timestamps`() {
        val workout = TestDataFactory.sampleWorkout.copy(
            status = WorkoutStatus.PLANNED,
            startTime = null
        )
        
        val startedWorkout = workout.start()
        
        assertEquals(WorkoutStatus.IN_PROGRESS, startedWorkout.status)
        assertNotNull(startedWorkout.startTime)
        assertTrue(startedWorkout.updatedAt.isAfter(workout.updatedAt))
    }

    @Test
    fun `complete should set status to COMPLETED and update timestamps`() {
        val workout = TestDataFactory.sampleWorkout
        
        val completedWorkout = workout.complete()
        
        assertEquals(WorkoutStatus.COMPLETED, completedWorkout.status)
        assertNotNull(completedWorkout.endTime)
        assertTrue(completedWorkout.updatedAt.isAfter(workout.updatedAt))
    }

    @Test
    fun `pause should set status to PAUSED and update timestamp`() {
        val workout = TestDataFactory.sampleWorkout
        
        val pausedWorkout = workout.pause()
        
        assertEquals(WorkoutStatus.PAUSED, pausedWorkout.status)
        assertTrue(pausedWorkout.updatedAt.isAfter(workout.updatedAt))
    }

    @Test
    fun `resume should set status to IN_PROGRESS from PAUSED`() {
        val pausedWorkout = TestDataFactory.sampleWorkout.copy(status = WorkoutStatus.PAUSED)
        
        val resumedWorkout = pausedWorkout.resume()
        
        assertEquals(WorkoutStatus.IN_PROGRESS, resumedWorkout.status)
        assertTrue(resumedWorkout.updatedAt.isAfter(pausedWorkout.updatedAt))
    }

    @Test
    fun `resume should throw exception when workout is not paused`() {
        val workout = TestDataFactory.sampleWorkout
        
        assertThrows(IllegalArgumentException::class.java) {
            workout.resume()
        }
    }

    @Test
    fun `cancel should set status to CANCELLED and update timestamp`() {
        val workout = TestDataFactory.sampleWorkout
        
        val cancelledWorkout = workout.cancel()
        
        assertEquals(WorkoutStatus.CANCELLED, cancelledWorkout.status)
        assertTrue(cancelledWorkout.updatedAt.isAfter(workout.updatedAt))
    }

    @Test
    fun `addExercise should add exercise to workout`() {
        val workout = TestDataFactory.sampleWorkout.copy(exercises = emptyList())
        val newExercise = TestDataFactory.benchPressExercise
        
        val updatedWorkout = workout.addExercise(newExercise)
        
        assertEquals(1, updatedWorkout.exercises.size)
        assertEquals(newExercise.id, updatedWorkout.exercises.first().id)
        assertTrue(updatedWorkout.updatedAt.isAfter(workout.updatedAt))
    }

    @Test
    fun `addExercise should throw exception when max exercises reached`() {
        val maxExercises = (1..Workout.MAX_EXERCISES).map {
            TestDataFactory.benchPressExercise.copy(id = ExerciseId.generate())
        }
        val workout = TestDataFactory.sampleWorkout.copy(exercises = maxExercises)
        val newExercise = TestDataFactory.squatExercise
        
        assertThrows(IllegalArgumentException::class.java) {
            workout.addExercise(newExercise)
        }
    }

    @Test
    fun `addExercise should throw exception when exercise ID already exists`() {
        val workout = TestDataFactory.sampleWorkout
        val duplicateExercise = TestDataFactory.benchPressExercise.copy(
            id = TestDataFactory.benchPressExercise.id
        )
        
        assertThrows(IllegalArgumentException::class.java) {
            workout.addExercise(duplicateExercise)
        }
    }
} 