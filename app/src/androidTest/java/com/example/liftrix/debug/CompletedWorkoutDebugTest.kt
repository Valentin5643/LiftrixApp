package com.example.liftrix.debug

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.domain.model.WorkoutStatus
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.repository.session.SessionRepository
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Equipment
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import javax.inject.Inject

/**
 * Debug test to verify completed workout persistence and retrieval
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CompletedWorkoutDebugTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workoutDao: WorkoutDao
    
    @Inject
    lateinit var workoutRepository: WorkoutRepository
    
    @Inject
    lateinit var sessionRepository: SessionRepository

    private val testUserId = "test_user_debug"

    @Before
    fun setup() {
        hiltRule.inject()
        
        // Clear any existing data
        runTest {
            workoutDao.deleteAllWorkoutsForUser(testUserId)
        }
    }

    @Test
    fun `debug completed workout flow - session to home display`() = runTest {
        Timber.d("🔍 DEBUG: Starting completed workout flow test")
        
        // Step 1: Create a simple workout template
        val template = WorkoutTemplate(
            id = WorkoutTemplateId.generate(),
            name = "Debug Workout",
            description = "Test workout for debugging",
            exercises = listOf(
                TemplateExercise(
                    id = ExerciseId.generate(),
                    name = "Push-ups",
                    category = ExerciseCategory.CHEST,
                    equipment = Equipment.BODYWEIGHT_ONLY,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeight = null,
                    orderIndex = 0,
                    restTimeSeconds = 60,
                    notes = "Test exercise"
                )
            ),
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            userId = testUserId,
            tags = emptyList(),
            isPublic = false
        )
        
        // Step 2: Start a session from the template
        Timber.d("🔍 DEBUG: Starting session from template")
        val sessionResult = sessionRepository.startSession(template, testUserId)
        assertTrue("Session should start successfully", sessionResult.isSuccess)
        
        val session = sessionResult.getOrThrow()
        assertNotNull("Session should not be null", session)
        assertEquals("Session should be ACTIVE", UnifiedWorkoutSession.SessionStatus.ACTIVE, session.sessionStatus)
        
        // Step 3: Complete the session (this should persist the workout as COMPLETED)
        Timber.d("🔍 DEBUG: Completing session")
        val completeResult = sessionRepository.completeSession(testUserId)
        assertTrue("Session should complete successfully", completeResult.isSuccess)
        
        // Step 4: Check if workout was persisted with COMPLETED status
        Timber.d("🔍 DEBUG: Checking if workout was persisted")
        val completedWorkouts = workoutDao.getRecentCompletedWorkouts(testUserId, 10)
        
        // Collect the flow to get the actual data
        var workoutEntities: List<com.example.liftrix.data.local.entity.WorkoutEntity> = emptyList()
        completedWorkouts.collect { entities ->
            workoutEntities = entities
        }
        
        Timber.d("🔍 DEBUG: Found ${workoutEntities.size} completed workouts")
        workoutEntities.forEach { entity ->
            Timber.d("🔍 DEBUG: Workout - name: ${entity.name}, status: ${entity.status}, endTime: ${entity.endTime}")
        }
        
        // Assert that we have exactly one completed workout
        assertEquals("Should have exactly 1 completed workout", 1, workoutEntities.size)
        
        val completedWorkout = workoutEntities.first()
        assertEquals("Workout should be COMPLETED", WorkoutStatus.COMPLETED, completedWorkout.status)
        assertEquals("Workout should have correct name", "Debug Workout", completedWorkout.name)
        assertNotNull("Workout should have end time", completedWorkout.endTime)
        
        // Step 5: Test repository level - getRecentWorkouts
        Timber.d("🔍 DEBUG: Testing repository getRecentWorkouts")
        val recentWorkoutsResult = workoutRepository.getRecentWorkouts(testUserId, 10)
        
        // Collect the flow to get the actual data
        var domainWorkouts: List<com.example.liftrix.domain.model.Workout> = emptyList()
        recentWorkoutsResult.collect { result ->
            result.fold(
                onSuccess = { workouts -> domainWorkouts = workouts },
                onFailure = { error -> fail("Repository should return workouts successfully: ${error.message}") }
            )
        }
        
        Timber.d("🔍 DEBUG: Repository returned ${domainWorkouts.size} workouts")
        domainWorkouts.forEach { workout ->
            Timber.d("🔍 DEBUG: Domain workout - name: ${workout.name}, status: ${workout.status}, endTime: ${workout.endTime}")
        }
        
        // Assert that repository returns the completed workout
        assertEquals("Repository should return exactly 1 workout", 1, domainWorkouts.size)
        val domainWorkout = domainWorkouts.first()
        assertEquals("Domain workout should be COMPLETED", WorkoutStatus.COMPLETED, domainWorkout.status)
        assertEquals("Domain workout should have correct name", "Debug Workout", domainWorkout.name)
        assertNotNull("Domain workout should have end time", domainWorkout.endTime)
        
        Timber.d("🔍 DEBUG: All tests passed - completed workout flow is working correctly!")
    }
}