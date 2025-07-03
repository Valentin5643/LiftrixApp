package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.domain.model.WorkoutStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import kotlin.system.measureTimeMillis

/**
 * Performance tests for WorkoutDao queries to validate sub-500ms load times
 * after database indexing optimization (Migration 22 to 23).
 * 
 * Validates performance criteria from PERF-001:
 * - Initial load time under 500ms
 * - Database queries properly indexed
 * - Memory usage optimized
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoPerformanceTest {

    private lateinit var database: LiftrixDatabase
    private lateinit var workoutDao: WorkoutDao
    
    private val testUserId = "performance-test-user"
    private val testFriendIds = listOf("friend-1", "friend-2", "friend-3")

    @Before
    fun setup() {
        // Create in-memory database for consistent performance testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftrixDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
            
        workoutDao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testWorkoutHistoryPaginatedPerformance() = runTest {
        // Setup: Create test data set
        val testWorkouts = createTestWorkouts(100)
        workoutDao.insertWorkouts(testWorkouts)
        
        // Performance test: Measure query execution time
        val executionTime = measureTimeMillis {
            val workouts = workoutDao.getWorkoutHistoryPaginated(
                userId = testUserId,
                limit = 20,
                offset = 0
            ).first()
            
            // Verify results are returned
            assertTrue("Query should return workouts", workouts.isNotEmpty())
        }
        
        // Validation: Must be under 500ms
        assertTrue(
            "Workout history query should complete in under 500ms, took ${executionTime}ms",
            executionTime < 500
        )
        
        Timber.i("✅ Workout history pagination performance: ${executionTime}ms")
    }

    @Test
    fun testFeedWorkoutsPerformance() = runTest {
        // Setup: Create test data for feed query
        val personalWorkouts = createTestWorkouts(50, testUserId)
        val friendWorkouts = testFriendIds.flatMap { friendId ->
            createTestWorkouts(30, friendId)
        }
        val allWorkouts = personalWorkouts + friendWorkouts
        
        workoutDao.insertWorkouts(allWorkouts)
        
        // Performance test: Measure feed query execution time
        val executionTime = measureTimeMillis {
            val feedWorkouts = workoutDao.getFeedWorkouts(
                currentUserId = testUserId,
                friendIds = testFriendIds,
                limit = 20,
                offset = 0
            ).first()
            
            // Verify results include both personal and friend workouts
            assertTrue("Feed should return workouts", feedWorkouts.isNotEmpty())
        }
        
        // Validation: Must be under 500ms
        assertTrue(
            "Feed workouts query should complete in under 500ms, took ${executionTime}ms",
            executionTime < 500
        )
        
        Timber.i("✅ Feed workouts performance: ${executionTime}ms")
    }

    @Test
    fun testPersonalCompletedWorkoutsPerformance() = runTest {
        // Setup: Create test data with completed workouts
        val testWorkouts = createTestWorkouts(75, testUserId, WorkoutStatus.COMPLETED)
        workoutDao.insertWorkouts(testWorkouts)
        
        // Performance test: Measure personal completed workouts query
        val executionTime = measureTimeMillis {
            val completedWorkouts = workoutDao.getPersonalCompletedWorkouts(
                userId = testUserId,
                limit = 20,
                offset = 0
            ).first()
            
            // Verify all returned workouts are completed
            assertTrue("Should return completed workouts", completedWorkouts.isNotEmpty())
            assertTrue("All workouts should be completed", 
                completedWorkouts.all { it.status == WorkoutStatus.COMPLETED })
        }
        
        // Validation: Must be under 500ms
        assertTrue(
            "Personal completed workouts query should complete in under 500ms, took ${executionTime}ms",
            executionTime < 500
        )
        
        Timber.i("✅ Personal completed workouts performance: ${executionTime}ms")
    }

    @Test
    fun testWorkoutCountForUserPerformance() = runTest {
        // Setup: Create large dataset for count query
        val testWorkouts = createTestWorkouts(200, testUserId)
        workoutDao.insertWorkouts(testWorkouts)
        
        // Performance test: Measure count query execution time
        val executionTime = measureTimeMillis {
            val count = workoutDao.getWorkoutCountForUser(testUserId)
            
            // Verify count is accurate
            assertTrue("Count should match inserted workouts", count == testWorkouts.size)
        }
        
        // Validation: Count queries should be very fast (under 100ms)
        assertTrue(
            "Workout count query should complete in under 100ms, took ${executionTime}ms",
            executionTime < 100
        )
        
        Timber.i("✅ Workout count performance: ${executionTime}ms")
    }

    @Test
    fun testMultipleUserQueryPerformance() = runTest {
        // Setup: Create workouts for multiple users to test user_id indexing
        val allUserIds = listOf(testUserId) + testFriendIds
        val allWorkouts = allUserIds.flatMap { userId ->
            createTestWorkouts(40, userId)
        }
        workoutDao.insertWorkouts(allWorkouts)
        
        // Performance test: Query specific user from large multi-user dataset
        val executionTime = measureTimeMillis {
            val userWorkouts = workoutDao.getWorkoutHistoryPaginated(
                userId = testUserId,
                limit = 20,
                offset = 0
            ).first()
            
            // Verify user isolation works correctly
            assertTrue("Should return workouts for specific user", userWorkouts.isNotEmpty())
            assertTrue("All workouts should belong to test user",
                userWorkouts.all { it.userId == testUserId })
        }
        
        // Validation: User-scoped queries must be under 500ms even with large dataset
        assertTrue(
            "Multi-user query should complete in under 500ms, took ${executionTime}ms",
            executionTime < 500
        )
        
        Timber.i("✅ Multi-user query performance: ${executionTime}ms")
    }

    /**
     * Helper function to create test workout entities
     */
    private fun createTestWorkouts(
        count: Int, 
        userId: String = testUserId,
        status: WorkoutStatus = WorkoutStatus.COMPLETED
    ): List<WorkoutEntity> {
        val now = Instant.now()
        return (1..count).map { index ->
            WorkoutEntity(
                id = "workout-$userId-$index",
                userId = userId,
                name = "Test Workout $index",
                date = LocalDate.now().minusDays(index.toLong()),
                exercisesJson = """[{"exerciseId": "test-exercise", "sets": []}]""",
                status = status,
                startTime = now.minusSeconds(3600),
                endTime = if (status == WorkoutStatus.COMPLETED) now.minusSeconds(1800) else null,
                notes = null,
                templateId = null,
                createdAt = now.minusSeconds(7200),
                updatedAt = now,
                isSynced = false,
                syncVersion = 0L
            )
        }
    }
}