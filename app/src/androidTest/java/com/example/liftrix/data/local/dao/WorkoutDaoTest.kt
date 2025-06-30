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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    
    private lateinit var database: LiftrixDatabase
    private lateinit var dao: WorkoutDao
    
    private val testUserId1 = "user1"
    private val testUserId2 = "user2"
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()
        
        dao = database.workoutDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun getRecentCompletedWorkouts_returnsOnlyCompletedWorkouts() = runTest {
        // Arrange
        val now = Instant.now()
        val completedWorkout = createTestWorkout("1", testUserId1, WorkoutStatus.COMPLETED, LocalDate.now().minusDays(1))
        val inProgressWorkout = createTestWorkout("2", testUserId1, WorkoutStatus.IN_PROGRESS, LocalDate.now())
        val plannedWorkout = createTestWorkout("3", testUserId1, WorkoutStatus.PLANNED, LocalDate.now().minusDays(2))
        
        dao.insertWorkout(completedWorkout)
        dao.insertWorkout(inProgressWorkout)
        dao.insertWorkout(plannedWorkout)
        
        // Act
        val recentCompleted = dao.getRecentCompletedWorkouts(testUserId1, 10).first()
        
        // Assert
        assertEquals(1, recentCompleted.size)
        assertEquals(WorkoutStatus.COMPLETED, recentCompleted[0].status)
        assertEquals("1", recentCompleted[0].id)
    }
    
    @Test
    fun getRecentCompletedWorkouts_returnsInDescendingDateOrder() = runTest {
        // Arrange
        val workout1 = createTestWorkout("1", testUserId1, WorkoutStatus.COMPLETED, LocalDate.now().minusDays(3))
        val workout2 = createTestWorkout("2", testUserId1, WorkoutStatus.COMPLETED, LocalDate.now().minusDays(1))
        val workout3 = createTestWorkout("3", testUserId1, WorkoutStatus.COMPLETED, LocalDate.now().minusDays(2))
        
        dao.insertWorkout(workout1)
        dao.insertWorkout(workout2)
        dao.insertWorkout(workout3)
        
        // Act
        val recentCompleted = dao.getRecentCompletedWorkouts(testUserId1, 10).first()
        
        // Assert
        assertEquals(3, recentCompleted.size)
        assertEquals("2", recentCompleted[0].id) // Most recent first
        assertEquals("3", recentCompleted[1].id)
        assertEquals("1", recentCompleted[2].id) // Oldest last
    }
    
    @Test
    fun getRecentCompletedWorkouts_respectsLimit() = runTest {
        // Arrange
        for (i in 1..5) {
            val workout = createTestWorkout("$i", testUserId1, WorkoutStatus.COMPLETED, LocalDate.now().minusDays(i.toLong()))
            dao.insertWorkout(workout)
        }
        
        // Act
        val recentCompleted = dao.getRecentCompletedWorkouts(testUserId1, 3).first()
        
        // Assert
        assertEquals(3, recentCompleted.size)
    }
    
    @Test
    fun getRecentCompletedWorkouts_isolatesUserData() = runTest {
        // Arrange
        val user1Workout = createTestWorkout("1", testUserId1, WorkoutStatus.COMPLETED, LocalDate.now().minusDays(1))
        val user2Workout = createTestWorkout("2", testUserId2, WorkoutStatus.COMPLETED, LocalDate.now())
        
        dao.insertWorkout(user1Workout)
        dao.insertWorkout(user2Workout)
        
        // Act
        val user1Workouts = dao.getRecentCompletedWorkouts(testUserId1, 10).first()
        val user2Workouts = dao.getRecentCompletedWorkouts(testUserId2, 10).first()
        
        // Assert
        assertEquals(1, user1Workouts.size)
        assertEquals(1, user2Workouts.size)
        assertEquals("1", user1Workouts[0].id)
        assertEquals("2", user2Workouts[0].id)
    }
    
    @Test
    fun getRecentCompletedWorkouts_returnsEmptyWhenNoCompletedWorkouts() = runTest {
        // Arrange
        val inProgressWorkout = createTestWorkout("1", testUserId1, WorkoutStatus.IN_PROGRESS, LocalDate.now())
        dao.insertWorkout(inProgressWorkout)
        
        // Act
        val recentCompleted = dao.getRecentCompletedWorkouts(testUserId1, 10).first()
        
        // Assert
        assertTrue(recentCompleted.isEmpty())
    }
    
    private fun createTestWorkout(
        id: String,
        userId: String,
        status: WorkoutStatus,
        date: LocalDate,
        name: String = "Test Workout $id"
    ): WorkoutEntity {
        val now = Instant.now()
        return WorkoutEntity(
            id = id,
            userId = userId,
            name = name,
            date = date,
            exercisesJson = "[]",
            status = status,
            startTime = if (status != WorkoutStatus.PLANNED) now else null,
            endTime = if (status == WorkoutStatus.COMPLETED) now else null,
            notes = null,
            templateId = null,
            createdAt = now,
            updatedAt = now,
            isSynced = false,
            syncVersion = 0L
        )
    }
} 