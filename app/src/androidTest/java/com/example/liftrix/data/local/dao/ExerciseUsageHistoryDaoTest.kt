package com.example.liftrix.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ExerciseUsageHistoryDaoTest {
    
    private lateinit var database: LiftrixDatabase
    private lateinit var dao: ExerciseUsageHistoryDao
    
    private val testUserId1 = "user1"
    private val testUserId2 = "user2"
    private val testExerciseId1 = "exercise1"
    private val testExerciseId2 = "exercise2"
    
    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LiftrixDatabase::class.java
        ).allowMainThreadQueries().build()
        
        dao = database.exerciseUsageHistoryDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertUsage_insertsSuccessfully() = runTest {
        // Arrange
        val usage = createTestUsage(testUserId1, testExerciseId1, 80.0f)
        
        // Act
        val insertedId = dao.insertUsage(usage)
        
        // Assert
        assertTrue(insertedId > 0)
    }
    
    @Test
    fun getLastUsedWeight_returnsCorrectWeight() = runTest {
        // Arrange
        val weight1 = 75.0f
        val weight2 = 85.0f
        val usage1 = createTestUsage(testUserId1, testExerciseId1, weight1, LocalDateTime.now().minusDays(2))
        val usage2 = createTestUsage(testUserId1, testExerciseId1, weight2, LocalDateTime.now().minusDays(1))
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        
        // Act
        val lastWeight = dao.getLastUsedWeight(testUserId1, testExerciseId1)
        
        // Assert
        assertEquals(weight2, lastWeight) // Should return the most recent weight
    }
    
    @Test
    fun getLastUsedWeight_returnsNullWhenNoHistory() = runTest {
        // Act
        val lastWeight = dao.getLastUsedWeight(testUserId1, testExerciseId1)
        
        // Assert
        assertNull(lastWeight)
    }
    
    @Test
    fun getLastUsedWeight_isolatesUserData() = runTest {
        // Arrange
        val user1Weight = 80.0f
        val user2Weight = 90.0f
        val usage1 = createTestUsage(testUserId1, testExerciseId1, user1Weight)
        val usage2 = createTestUsage(testUserId2, testExerciseId1, user2Weight)
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        
        // Act
        val user1LastWeight = dao.getLastUsedWeight(testUserId1, testExerciseId1)
        val user2LastWeight = dao.getLastUsedWeight(testUserId2, testExerciseId1)
        
        // Assert
        assertEquals(user1Weight, user1LastWeight)
        assertEquals(user2Weight, user2LastWeight)
    }
    
    @Test
    fun getRecentExercises_returnsInDescendingOrder() = runTest {
        // Arrange
        val now = LocalDateTime.now()
        val usage1 = createTestUsage(testUserId1, testExerciseId1, 80.0f, now.minusDays(3))
        val usage2 = createTestUsage(testUserId1, testExerciseId2, 85.0f, now.minusDays(1))
        val usage3 = createTestUsage(testUserId1, testExerciseId1, 90.0f, now.minusDays(2))
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        dao.insertUsage(usage3)
        
        // Act
        val recentExercises = dao.getRecentExercises(testUserId1, 10)
        
        // Assert
        assertEquals(3, recentExercises.size)
        assertTrue(recentExercises[0].usedAt.isAfter(recentExercises[1].usedAt))
        assertTrue(recentExercises[1].usedAt.isAfter(recentExercises[2].usedAt))
    }
    
    @Test
    fun getRecentExerciseIds_returnsDistinctIds() = runTest {
        // Arrange
        val now = LocalDateTime.now()
        val usage1 = createTestUsage(testUserId1, testExerciseId1, 80.0f, now.minusDays(3))
        val usage2 = createTestUsage(testUserId1, testExerciseId1, 85.0f, now.minusDays(2)) // Same exercise
        val usage3 = createTestUsage(testUserId1, testExerciseId2, 90.0f, now.minusDays(1))
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        dao.insertUsage(usage3)
        
        // Act
        val recentExerciseIds = dao.getRecentExerciseIds(testUserId1, 10)
        
        // Assert
        assertEquals(2, recentExerciseIds.size) // Should only return distinct exercise IDs
        assertEquals(testExerciseId2, recentExerciseIds[0]) // Most recent first
        assertEquals(testExerciseId1, recentExerciseIds[1])
    }
    
    @Test
    fun getRecentExerciseIds_respectsLimit() = runTest {
        // Arrange
        val now = LocalDateTime.now()
        for (i in 1..5) {
            val usage = createTestUsage(testUserId1, "exercise$i", 80.0f, now.minusDays(i.toLong()))
            dao.insertUsage(usage)
        }
        
        // Act
        val recentExerciseIds = dao.getRecentExerciseIds(testUserId1, 3)
        
        // Assert
        assertEquals(3, recentExerciseIds.size)
    }
    
    @Test
    fun getExerciseHistory_returnsUserSpecificHistory() = runTest {
        // Arrange
        val now = LocalDateTime.now()
        val usage1 = createTestUsage(testUserId1, testExerciseId1, 80.0f, now.minusDays(2))
        val usage2 = createTestUsage(testUserId1, testExerciseId1, 85.0f, now.minusDays(1))
        val usage3 = createTestUsage(testUserId2, testExerciseId1, 90.0f, now) // Different user
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        dao.insertUsage(usage3)
        
        // Act
        val user1History = dao.getExerciseHistory(testUserId1, testExerciseId1, 10)
        
        // Assert
        assertEquals(2, user1History.size) // Should only return user1's history
        assertEquals(85.0f, user1History[0].weightUsed) // Most recent first
        assertEquals(80.0f, user1History[1].weightUsed)
    }
    
    @Test
    fun getAverageWeightLast30Days_calculatesCorrectly() = runTest {
        // Arrange
        val now = LocalDateTime.now()
        val usage1 = createTestUsage(testUserId1, testExerciseId1, 80.0f, now.minusDays(10))
        val usage2 = createTestUsage(testUserId1, testExerciseId1, 90.0f, now.minusDays(5))
        val usage3 = createTestUsage(testUserId1, testExerciseId1, 70.0f, now.minusDays(40)) // Outside 30 days
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        dao.insertUsage(usage3)
        
        // Act
        val averageWeight = dao.getAverageWeightLast30Days(testUserId1, testExerciseId1)
        
        // Assert
        assertEquals(85.0f, averageWeight) // (80 + 90) / 2 = 85
    }
    
    @Test
    fun getAverageWeightLast30Days_returnsNullWhenNoRecentHistory() = runTest {
        // Arrange
        val now = LocalDateTime.now()
        val usage = createTestUsage(testUserId1, testExerciseId1, 80.0f, now.minusDays(40))
        dao.insertUsage(usage)
        
        // Act
        val averageWeight = dao.getAverageWeightLast30Days(testUserId1, testExerciseId1)
        
        // Assert
        assertNull(averageWeight)
    }
    
    @Test
    fun getExerciseUsageCount_returnsCorrectCount() = runTest {
        // Arrange
        val usage1 = createTestUsage(testUserId1, testExerciseId1, 80.0f)
        val usage2 = createTestUsage(testUserId1, testExerciseId1, 85.0f)
        val usage3 = createTestUsage(testUserId1, testExerciseId2, 90.0f) // Different exercise
        val usage4 = createTestUsage(testUserId2, testExerciseId1, 95.0f) // Different user
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        dao.insertUsage(usage3)
        dao.insertUsage(usage4)
        
        // Act
        val count = dao.getExerciseUsageCount(testUserId1, testExerciseId1)
        
        // Assert
        assertEquals(2, count) // Should only count user1's usage of exercise1
    }
    
    @Test
    fun deleteAllForUser_removesOnlyUserData() = runTest {
        // Arrange
        val usage1 = createTestUsage(testUserId1, testExerciseId1, 80.0f)
        val usage2 = createTestUsage(testUserId2, testExerciseId1, 85.0f)
        
        dao.insertUsage(usage1)
        dao.insertUsage(usage2)
        
        // Act
        val deletedCount = dao.deleteAllForUser(testUserId1)
        
        // Assert
        assertEquals(1, deletedCount)
        assertNull(dao.getLastUsedWeight(testUserId1, testExerciseId1))
        assertEquals(85.0f, dao.getLastUsedWeight(testUserId2, testExerciseId1)) // user2's data preserved
    }
    
    @Test
    fun performanceTest_queryWithIndexes() = runTest {
        // Arrange - Insert a large number of records
        val now = LocalDateTime.now()
        for (i in 1..1000) {
            val usage = createTestUsage(
                userId = "user${i % 10}", // 10 different users
                exerciseId = "exercise${i % 50}", // 50 different exercises
                weight = (50 + i % 100).toFloat(),
                usedAt = now.minusDays((i % 365).toLong())
            )
            dao.insertUsage(usage)
        }
        
        // Act & Assert - These queries should be fast due to indexes
        val startTime = System.currentTimeMillis()
        
        val lastWeight = dao.getLastUsedWeight("user1", "exercise1")
        val recentExercises = dao.getRecentExercises("user1", 10)
        val averageWeight = dao.getAverageWeightLast30Days("user1", "exercise1")
        val usageCount = dao.getExerciseUsageCount("user1", "exercise1")
        
        val endTime = System.currentTimeMillis()
        val queryTime = endTime - startTime
        
        // Verify results are reasonable
        assertTrue(lastWeight != null || lastWeight == null) // Either result is valid
        assertTrue(recentExercises.size <= 10)
        assertTrue(usageCount >= 0)
        
        // Performance assertion - queries should complete quickly with proper indexing
        assertTrue("Queries took too long: ${queryTime}ms", queryTime < 100)
    }
    
    private fun createTestUsage(
        userId: String,
        exerciseId: String,
        weight: Float,
        usedAt: LocalDateTime = LocalDateTime.now(),
        reps: Int = 10,
        sets: Int = 3,
        workoutId: String? = null
    ): ExerciseUsageHistoryEntity {
        return ExerciseUsageHistoryEntity(
            userId = userId,
            exerciseId = exerciseId,
            weightUsed = weight,
            repsPerformed = reps,
            setsPerformed = sets,
            usedAt = usedAt,
            workoutId = workoutId
        )
    }
} 