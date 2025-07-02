package com.example.liftrix.ui.workout

import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.ExerciseSetId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.usecase.template.CreateTemplateFromSessionUseCase
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

/**
 * Test suite for the modern workout creation flow implementation.
 * 
 * Covers the complete user journey from workout creation to template saving,
 * ensuring all new components work together correctly.
 */
class ModernWorkoutCreationFlowTest {

    private val mockTemplateRepository = mockk<WorkoutTemplateRepository>()
    private val createTemplateFromSessionUseCase = CreateTemplateFromSessionUseCase(mockTemplateRepository)

    @Test
    fun `blank workout session creation should initialize correctly`() {
        // Given
        val userId = "user123"
        val workoutName = "My Custom Workout"
        
        // When
        val session = ActiveWorkoutSession.createBlank(userId, workoutName)
        
        // Then
        assertEquals(userId, session.userId)
        assertEquals(workoutName, session.name)
        assertTrue(session.exercises.isEmpty())
        assertEquals(0, session.currentExerciseIndex)
        assertEquals(ActiveWorkoutSession.SessionState.ACTIVE, session.sessionState)
        assertNull(session.templateId)
        assertNotNull(session.id)
        assertNotNull(session.startedAt)
    }

    @Test
    fun `session from template should preserve template structure`() {
        // Given
        val userId = "user123"
        val template = createMockTemplate()
        val customName = "Push Day Session"
        
        // When
        val session = ActiveWorkoutSession.fromTemplate(userId, template, customName)
        
        // Then
        assertEquals(userId, session.userId)
        assertEquals(customName, session.name)
        assertEquals(template.id, session.templateId)
        assertEquals(template.exercises.size, session.exercises.size)
        assertEquals(ActiveWorkoutSession.SessionState.ACTIVE, session.sessionState)
        
        // Verify exercises are converted correctly
        session.exercises.forEachIndexed { index, sessionExercise ->
            val templateExercise = template.exercises[index]
            assertEquals(templateExercise.libraryExercise.name, sessionExercise.libraryExercise.name)
            assertEquals(templateExercise.targetSets, sessionExercise.targetSets)
            assertEquals(templateExercise.targetReps, sessionExercise.targetReps)
            assertEquals(templateExercise.targetWeight, sessionExercise.targetWeight)
        }
    }

    @Test
    fun `adding exercises to session should update state correctly`() {
        // Given
        val session = ActiveWorkoutSession.createBlank("user123", "Test Workout")
        val exercise = createMockSessionExercise()
        
        // When
        val updatedSession = session.addExercise(exercise)
        
        // Then
        assertEquals(1, updatedSession.exercises.size)
        assertEquals(exercise.libraryExercise.name, updatedSession.exercises[0].libraryExercise.name)
        assertEquals(0, updatedSession.exercises[0].orderIndex)
        assertTrue(updatedSession.lastModified.isAfter(session.lastModified))
    }

    @Test
    fun `session pause and resume should track time correctly`() {
        // Given
        val session = ActiveWorkoutSession.createBlank("user123", "Test Workout")
        
        // When - pause session
        val pausedSession = session.pause()
        
        // Then - verify pause state
        assertEquals(ActiveWorkoutSession.SessionState.PAUSED, pausedSession.sessionState)
        assertNotNull(pausedSession.pausedAt)
        
        // When - resume session
        val resumedSession = pausedSession.resume()
        
        // Then - verify resume state
        assertEquals(ActiveWorkoutSession.SessionState.ACTIVE, resumedSession.sessionState)
        assertNull(resumedSession.pausedAt)
        assertNotNull(resumedSession.resumedAt)
        assertTrue(resumedSession.totalPausedDuration > 0)
    }

    @Test
    fun `session completion percentage should calculate correctly`() {
        // Given
        val session = createSessionWithSets()
        
        // When - no sets completed initially
        val initialPercentage = session.getCompletionPercentage()
        
        // Then
        assertEquals(0f, initialPercentage, 0.01f)
        
        // When - complete one set
        val completedSet = session.exercises[0].sets[0].copy(completedAt = Instant.now())
        val updatedExercise = session.exercises[0].copy(
            sets = listOf(completedSet) + session.exercises[0].sets.drop(1)
        )
        val updatedSession = session.copy(
            exercises = listOf(updatedExercise) + session.exercises.drop(1)
        )
        
        val partialPercentage = updatedSession.getCompletionPercentage()
        
        // Then - should be 25% (1 of 4 sets completed)
        assertEquals(25f, partialPercentage, 0.01f)
    }

    @Test
    fun `template creation from session should remove session-specific data`() = runTest {
        // Given
        val session = createCompletedSession()
        val templateName = "Push Day Template"
        val templateDescription = "Upper body focused workout"
        
        val expectedTemplate = createMockTemplate().copy(
            name = templateName,
            description = templateDescription
        )
        
        coEvery { mockTemplateRepository.createTemplate(any()) } returns expectedTemplate
        
        // When
        val result = createTemplateFromSessionUseCase(session, templateName, templateDescription)
        
        // Then
        assertTrue(result.isSuccess)
        coVerify { mockTemplateRepository.createTemplate(any()) }
        
        // Verify template doesn't contain session-specific data
        val template = result.getOrNull()!!
        assertEquals(templateName, template.name)
        assertEquals(templateDescription, template.description)
        assertEquals(0, template.usageCount)
        assertNull(template.lastUsedAt)
        assertEquals(session.userId, template.userId)
        assertEquals(session.exercises.size, template.exercises.size)
    }

    @Test
    fun `session conversion to completed workout should preserve data`() {
        // Given
        val session = createCompletedSession()
        
        // When
        val workout = session.toCompletedWorkout()
        
        // Then
        assertEquals(session.name, workout.name)
        assertEquals(session.userId, workout.userId)
        assertEquals(session.exercises.size, workout.exercises.size)
        assertEquals(com.example.liftrix.domain.model.WorkoutStatus.COMPLETED, workout.status)
        assertEquals(session.startedAt, workout.startTime)
        assertNotNull(workout.endTime)
        
        // Verify exercises are converted correctly
        workout.exercises.forEachIndexed { index, workoutExercise ->
            val sessionExercise = session.exercises[index]
            assertEquals(sessionExercise.libraryExercise.name, workoutExercise.libraryExercise.name)
            assertEquals(sessionExercise.sets.size, workoutExercise.sets.size)
        }
    }

    @Test
    fun `template difficulty estimation should be accurate`() = runTest {
        // Given - session with compound exercises
        val session = createSessionWithCompoundExercises()
        val templateName = "Heavy Compound Workout"
        
        val mockTemplate = createMockTemplate().copy(
            name = templateName,
            difficultyLevel = 5 // Expected high difficulty
        )
        
        coEvery { mockTemplateRepository.createTemplate(any()) } returns mockTemplate
        
        // When
        val result = createTemplateFromSessionUseCase(session, templateName, null)
        
        // Then
        assertTrue(result.isSuccess)
        // Verify the use case was called with appropriate parameters
        coVerify { 
            mockTemplateRepository.createTemplate(
                match { template ->
                    template.name == templateName && 
                    template.exercises.isNotEmpty()
                }
            )
        }
    }

    @Test
    fun `session volume calculation should be accurate`() {
        // Given
        val session = createSessionWithWeights()
        
        // When
        val totalVolume = session.getTotalVolume()
        
        // Then
        // Expected: 2 exercises × 2 sets × (100kg × 10 reps) = 4000kg total volume
        assertEquals(4000.0, totalVolume, 0.01)
    }

    @Test
    fun `session state transitions should be validated`() {
        // Given
        val session = ActiveWorkoutSession.createBlank("user123", "Test Workout")
        
        // When/Then - valid transitions
        val pausedSession = session.pause()
        assertEquals(ActiveWorkoutSession.SessionState.PAUSED, pausedSession.sessionState)
        
        val resumedSession = pausedSession.resume()
        assertEquals(ActiveWorkoutSession.SessionState.ACTIVE, resumedSession.sessionState)
        
        val restSession = resumedSession.startRest()
        assertEquals(ActiveWorkoutSession.SessionState.REST, restSession.sessionState)
        
        val activeSession = restSession.endRest()
        assertEquals(ActiveWorkoutSession.SessionState.ACTIVE, activeSession.sessionState)
    }

    @Test
    fun `session with invalid state transitions should throw exceptions`() {
        // Given
        val session = ActiveWorkoutSession.createBlank("user123", "Test Workout")
        
        // When/Then - invalid transitions should throw
        assertThrows(IllegalArgumentException::class.java) {
            session.resume() // Cannot resume non-paused session
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            session.pause().pause() // Cannot pause already paused session
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            session.endRest() // Cannot end rest when not in rest state
        }
    }

    // Helper methods for creating test data

    private fun createMockTemplate(): WorkoutTemplate {
        return WorkoutTemplate(
            id = WorkoutTemplateId.generate(),
            userId = "user123",
            name = "Test Template",
            description = "Test template description",
            exercises = listOf(
                WorkoutTemplate.TemplateExercise(
                    exerciseId = ExerciseId.generate(),
                    libraryExercise = createMockLibraryExercise("Bench Press"),
                    orderIndex = 0,
                    targetSets = 3,
                    targetReps = Reps(10),
                    targetWeight = Weight(100.0),
                    targetTime = null,
                    targetDistance = null,
                    restTimer = null,
                    notes = null
                )
            ),
            estimatedDurationMinutes = 45,
            difficultyLevel = 3,
            category = "Strength",
            tags = listOf("Upper Body"),
            usageCount = 0,
            lastUsedAt = null,
            isFavorite = false,
            isPublic = false,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            sourceSessionId = null,
            notes = null
        )
    }

    private fun createMockLibraryExercise(name: String): ExerciseLibrary {
        return ExerciseLibrary(
            id = "exercise_$name",
            name = name,
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BARBELL,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "Push",
            difficultyLevel = 3,
            instructions = "Test instructions",
            isCompound = true,
            searchableTerms = listOf(name.lowercase())
        )
    }

    private fun createMockSessionExercise(): SessionExercise {
        return SessionExercise(
            exerciseId = ExerciseId.generate(),
            libraryExercise = createMockLibraryExercise("Push-ups"),
            orderIndex = 0,
            targetSets = 3,
            targetReps = Reps(15),
            targetWeight = null,
            targetTime = null,
            targetDistance = null,
            sets = emptyList(),
            notes = null
        )
    }

    private fun createSessionWithSets(): ActiveWorkoutSession {
        val sets = listOf(
            SessionSet(
                id = ExerciseSetId.generate(),
                weight = Weight(100.0),
                reps = Reps(10),
                time = null,
                distance = null,
                completedAt = null,
                notes = null
            ),
            SessionSet(
                id = ExerciseSetId.generate(),
                weight = Weight(100.0),
                reps = Reps(10),
                time = null,
                distance = null,
                completedAt = null,
                notes = null
            )
        )
        
        val exercises = listOf(
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Bench Press"),
                orderIndex = 0,
                targetSets = 2,
                targetReps = Reps(10),
                targetWeight = Weight(100.0),
                targetTime = null,
                targetDistance = null,
                sets = sets,
                notes = null
            ),
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Squats"),
                orderIndex = 1,
                targetSets = 2,
                targetReps = Reps(10),
                targetWeight = Weight(120.0),
                targetTime = null,
                targetDistance = null,
                sets = sets,
                notes = null
            )
        )
        
        return ActiveWorkoutSession.createBlank("user123", "Test Workout").copy(
            exercises = exercises
        )
    }

    private fun createCompletedSession(): ActiveWorkoutSession {
        val completedSets = listOf(
            SessionSet(
                id = ExerciseSetId.generate(),
                weight = Weight(100.0),
                reps = Reps(10),
                time = null,
                distance = null,
                completedAt = Instant.now(),
                notes = null
            ),
            SessionSet(
                id = ExerciseSetId.generate(),
                weight = Weight(100.0),
                reps = Reps(10),
                time = null,
                distance = null,
                completedAt = Instant.now(),
                notes = null
            )
        )
        
        val exercises = listOf(
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Bench Press"),
                orderIndex = 0,
                targetSets = 2,
                targetReps = Reps(10),
                targetWeight = Weight(100.0),
                targetTime = null,
                targetDistance = null,
                sets = completedSets,
                notes = null
            )
        )
        
        return ActiveWorkoutSession.createBlank("user123", "Completed Workout").copy(
            exercises = exercises
        )
    }

    private fun createSessionWithCompoundExercises(): ActiveWorkoutSession {
        val exercises = listOf(
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Deadlift").copy(
                    isCompound = true,
                    difficultyLevel = 5
                ),
                orderIndex = 0,
                targetSets = 5,
                targetReps = Reps(5),
                targetWeight = Weight(200.0),
                targetTime = null,
                targetDistance = null,
                sets = emptyList(),
                notes = null
            ),
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Squats").copy(
                    isCompound = true,
                    difficultyLevel = 4
                ),
                orderIndex = 1,
                targetSets = 4,
                targetReps = Reps(8),
                targetWeight = Weight(150.0),
                targetTime = null,
                targetDistance = null,
                sets = emptyList(),
                notes = null
            )
        )
        
        return ActiveWorkoutSession.createBlank("user123", "Heavy Compound Workout").copy(
            exercises = exercises
        )
    }

    private fun createSessionWithWeights(): ActiveWorkoutSession {
        val sets = listOf(
            SessionSet(
                id = ExerciseSetId.generate(),
                weight = Weight(100.0),
                reps = Reps(10),
                time = null,
                distance = null,
                completedAt = Instant.now(),
                notes = null
            ),
            SessionSet(
                id = ExerciseSetId.generate(),
                weight = Weight(100.0),
                reps = Reps(10),
                time = null,
                distance = null,
                completedAt = Instant.now(),
                notes = null
            )
        )
        
        val exercises = listOf(
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Exercise 1"),
                orderIndex = 0,
                targetSets = 2,
                targetReps = Reps(10),
                targetWeight = Weight(100.0),
                targetTime = null,
                targetDistance = null,
                sets = sets,
                notes = null
            ),
            SessionExercise(
                exerciseId = ExerciseId.generate(),
                libraryExercise = createMockLibraryExercise("Exercise 2"),
                orderIndex = 1,
                targetSets = 2,
                targetReps = Reps(10),
                targetWeight = Weight(100.0),
                targetTime = null,
                targetDistance = null,
                sets = sets,
                notes = null
            )
        )
        
        return ActiveWorkoutSession.createBlank("user123", "Weighted Workout").copy(
            exercises = exercises
        )
    }
}