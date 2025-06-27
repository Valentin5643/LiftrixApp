package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.entity.ExerciseUsageHistoryEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDateTime

class WeightMemoryServiceImplTest {

    private lateinit var exerciseUsageHistoryDao: ExerciseUsageHistoryDao
    private lateinit var weightMemoryService: WeightMemoryServiceImpl

    private val testUserId = "test-user-123"
    private val testExerciseId = "exercise-456"
    private val testWeight = 100.5f
    private val testReps = 10
    private val testSets = 3
    private val testWorkoutId = "workout-789"

    @Before
    fun setup() {
        exerciseUsageHistoryDao = mockk()
        weightMemoryService = WeightMemoryServiceImpl(exerciseUsageHistoryDao)
    }

    // ============ getLastUsedWeight Tests ============

    @Test
    fun getLastUsedWeight_withValidParameters_returnsCorrectWeight() = runTest {
        // Arrange
        val expectedWeight = 75.5f
        coEvery { exerciseUsageHistoryDao.getLastUsedWeight(testUserId, testExerciseId) } returns expectedWeight

        // Act
        val result = weightMemoryService.getLastUsedWeight(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertEquals("Weight should match expected value", expectedWeight, result.getOrNull())
        coVerify { exerciseUsageHistoryDao.getLastUsedWeight(testUserId, testExerciseId) }
    }

    @Test
    fun getLastUsedWeight_withNoHistory_returnsNull() = runTest {
        // Arrange
        coEvery { exerciseUsageHistoryDao.getLastUsedWeight(testUserId, testExerciseId) } returns null

        // Act
        val result = weightMemoryService.getLastUsedWeight(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertNull("Weight should be null when no history exists", result.getOrNull())
    }

    @Test
    fun getLastUsedWeight_withBlankUserId_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.getLastUsedWeight("", testExerciseId)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("User ID and Exercise ID cannot be blank", 
            result.exceptionOrNull()?.message)
    }

    @Test
    fun getLastUsedWeight_withBlankExerciseId_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.getLastUsedWeight(testUserId, "")

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun getLastUsedWeight_withDatabaseException_returnsFailure() = runTest {
        // Arrange
        val expectedException = RuntimeException("Database connection failed")
        coEvery { exerciseUsageHistoryDao.getLastUsedWeight(testUserId, testExerciseId) } throws expectedException

        // Act
        val result = weightMemoryService.getLastUsedWeight(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Exception should be preserved", expectedException, result.exceptionOrNull())
    }

    // ============ updateExerciseWeight Tests ============

    @Test
    fun updateExerciseWeight_withValidParameters_persistsCorrectly() = runTest {
        // Arrange
        val expectedId = 1L
        val capturedEntity = slot<ExerciseUsageHistoryEntity>()
        coEvery { exerciseUsageHistoryDao.insertUsage(capture(capturedEntity)) } returns expectedId

        // Act
        val result = weightMemoryService.updateExerciseWeight(
            testUserId, testExerciseId, testWeight, testReps, testSets, testWorkoutId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        coVerify { exerciseUsageHistoryDao.insertUsage(any()) }
        
        val entity = capturedEntity.captured
        assertEquals("User ID should match", testUserId, entity.userId)
        assertEquals("Exercise ID should match", testExerciseId, entity.exerciseId)
        assertEquals("Weight should match", testWeight, entity.weightUsed)
        assertEquals("Reps should match", testReps, entity.repsPerformed)
        assertEquals("Sets should match", testSets, entity.setsPerformed)
        assertEquals("Workout ID should match", testWorkoutId, entity.workoutId)
        assertNotNull("Usage timestamp should be set", entity.usedAt)
    }

    @Test
    fun updateExerciseWeight_withMinimalParameters_usesDefaults() = runTest {
        // Arrange
        val expectedId = 1L
        val capturedEntity = slot<ExerciseUsageHistoryEntity>()
        coEvery { exerciseUsageHistoryDao.insertUsage(capture(capturedEntity)) } returns expectedId

        // Act
        val result = weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, testWeight)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        val entity = capturedEntity.captured
        assertEquals("Reps should use default", 0, entity.repsPerformed)
        assertEquals("Sets should use default", 0, entity.setsPerformed)
        assertNull("Workout ID should be null", entity.workoutId)
    }

    @Test
    fun updateExerciseWeight_withNegativeWeight_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, -10.0f)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Weight must be positive", result.exceptionOrNull()?.message)
    }

    @Test
    fun updateExerciseWeight_withZeroWeight_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, 0.0f)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun updateExerciseWeight_withBlankUserId_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.updateExerciseWeight("", testExerciseId, testWeight)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun updateExerciseWeight_withDatabaseException_returnsFailure() = runTest {
        // Arrange
        val expectedException = RuntimeException("Insert failed")
        coEvery { exerciseUsageHistoryDao.insertUsage(any()) } throws expectedException

        // Act
        val result = weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, testWeight)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Exception should be preserved", expectedException, result.exceptionOrNull())
    }

    // ============ getRecentExercises Tests ============

    @Test
    fun getRecentExercises_withValidParameters_returnsCorrectList() = runTest {
        // Arrange
        val expectedExerciseIds = listOf("exercise1", "exercise2", "exercise3")
        val limit = 5
        coEvery { exerciseUsageHistoryDao.getRecentExerciseIds(testUserId, limit) } returns expectedExerciseIds

        // Act
        val result = weightMemoryService.getRecentExercises(testUserId, limit)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertEquals("Exercise IDs should match", expectedExerciseIds, result.getOrNull())
        coVerify { exerciseUsageHistoryDao.getRecentExerciseIds(testUserId, limit) }
    }

    @Test
    fun getRecentExercises_withDefaultLimit_usesCorrectDefault() = runTest {
        // Arrange
        val expectedExerciseIds = listOf("exercise1", "exercise2")
        coEvery { exerciseUsageHistoryDao.getRecentExerciseIds(testUserId, 10) } returns expectedExerciseIds

        // Act
        val result = weightMemoryService.getRecentExercises(testUserId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertEquals("Exercise IDs should match", expectedExerciseIds, result.getOrNull())
        coVerify { exerciseUsageHistoryDao.getRecentExerciseIds(testUserId, 10) }
    }

    @Test
    fun getRecentExercises_withBlankUserId_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.getRecentExercises("", 5)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun getRecentExercises_withNegativeLimit_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.getRecentExercises(testUserId, -1)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Limit must be positive", result.exceptionOrNull()?.message)
    }

    @Test
    fun getRecentExercises_withZeroLimit_returnsFailure() = runTest {
        // Act
        val result = weightMemoryService.getRecentExercises(testUserId, 0)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertTrue("Should be IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    // ============ getAverageWeightLast30Days Tests ============

    @Test
    fun getAverageWeightLast30Days_withValidParameters_returnsCorrectAverage() = runTest {
        // Arrange
        val expectedAverage = 82.5f
        coEvery { exerciseUsageHistoryDao.getAverageWeightLast30Days(testUserId, testExerciseId) } returns expectedAverage

        // Act
        val result = weightMemoryService.getAverageWeightLast30Days(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertEquals("Average weight should match", expectedAverage, result.getOrNull())
        coVerify { exerciseUsageHistoryDao.getAverageWeightLast30Days(testUserId, testExerciseId) }
    }

    @Test
    fun getAverageWeightLast30Days_withNoRecentData_returnsNull() = runTest {
        // Arrange
        coEvery { exerciseUsageHistoryDao.getAverageWeightLast30Days(testUserId, testExerciseId) } returns null

        // Act
        val result = weightMemoryService.getAverageWeightLast30Days(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertNull("Average should be null when no recent data", result.getOrNull())
    }

    @Test
    fun getAverageWeightLast30Days_withBlankParameters_returnsFailure() = runTest {
        // Act
        val result1 = weightMemoryService.getAverageWeightLast30Days("", testExerciseId)
        val result2 = weightMemoryService.getAverageWeightLast30Days(testUserId, "")

        // Assert
        assertTrue("Expected failure for blank user ID", result1.isFailure)
        assertTrue("Expected failure for blank exercise ID", result2.isFailure)
    }

    // ============ getExerciseUsageCount Tests ============

    @Test
    fun getExerciseUsageCount_withValidParameters_returnsCorrectCount() = runTest {
        // Arrange
        val expectedCount = 15
        coEvery { exerciseUsageHistoryDao.getExerciseUsageCount(testUserId, testExerciseId) } returns expectedCount

        // Act
        val result = weightMemoryService.getExerciseUsageCount(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertEquals("Usage count should match", expectedCount, result.getOrNull())
        coVerify { exerciseUsageHistoryDao.getExerciseUsageCount(testUserId, testExerciseId) }
    }

    @Test
    fun getExerciseUsageCount_withZeroUsage_returnsZero() = runTest {
        // Arrange
        coEvery { exerciseUsageHistoryDao.getExerciseUsageCount(testUserId, testExerciseId) } returns 0

        // Act
        val result = weightMemoryService.getExerciseUsageCount(testUserId, testExerciseId)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        assertEquals("Usage count should be zero", 0, result.getOrNull())
    }

    @Test
    fun getExerciseUsageCount_withBlankParameters_returnsFailure() = runTest {
        // Act
        val result1 = weightMemoryService.getExerciseUsageCount("", testExerciseId)
        val result2 = weightMemoryService.getExerciseUsageCount(testUserId, "")

        // Assert
        assertTrue("Expected failure for blank user ID", result1.isFailure)
        assertTrue("Expected failure for blank exercise ID", result2.isFailure)
    }

    // ============ User Scoping Tests ============

    @Test
    fun userScoping_isolatesDataBetweenUsers() = runTest {
        // Arrange
        val user1Id = "user1"
        val user2Id = "user2"
        val weight1 = 50.0f
        val weight2 = 100.0f
        
        coEvery { exerciseUsageHistoryDao.getLastUsedWeight(user1Id, testExerciseId) } returns weight1
        coEvery { exerciseUsageHistoryDao.getLastUsedWeight(user2Id, testExerciseId) } returns weight2

        // Act
        val result1 = weightMemoryService.getLastUsedWeight(user1Id, testExerciseId)
        val result2 = weightMemoryService.getLastUsedWeight(user2Id, testExerciseId)

        // Assert
        assertTrue("User 1 result should be successful", result1.isSuccess)
        assertTrue("User 2 result should be successful", result2.isSuccess)
        assertEquals("User 1 should get correct weight", weight1, result1.getOrNull())
        assertEquals("User 2 should get correct weight", weight2, result2.getOrNull())
        assertNotEquals("Users should have different weights", result1.getOrNull(), result2.getOrNull())
    }

    // ============ Thread Safety Tests ============

    @Test
    fun concurrentAccess_handledSafely() = runTest {
        // Arrange
        val expectedWeight = 60.0f
        coEvery { exerciseUsageHistoryDao.getLastUsedWeight(testUserId, testExerciseId) } returns expectedWeight
        coEvery { exerciseUsageHistoryDao.insertUsage(any()) } returns 1L

        // Act - Simulate concurrent operations
        val getResults = mutableListOf<Result<Float?>>()
        val updateResults = mutableListOf<Result<Unit>>()
        
        repeat(5) {
            getResults.add(weightMemoryService.getLastUsedWeight(testUserId, testExerciseId))
            updateResults.add(weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, testWeight))
        }

        // Assert
        getResults.forEach { result ->
            assertTrue("All get operations should succeed", result.isSuccess)
            assertEquals("All get operations should return same weight", expectedWeight, result.getOrNull())
        }
        
        updateResults.forEach { result ->
            assertTrue("All update operations should succeed", result.isSuccess)
        }
    }

    // ============ Edge Cases Tests ============

    @Test
    fun updateExerciseWeight_withVeryLargeWeight_handlesCorrectly() = runTest {
        // Arrange
        val largeWeight = Float.MAX_VALUE
        coEvery { exerciseUsageHistoryDao.insertUsage(any()) } returns 1L

        // Act
        val result = weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, largeWeight)

        // Assert
        assertTrue("Should handle large weight values", result.isSuccess)
    }

    @Test
    fun updateExerciseWeight_withVerySmallWeight_handlesCorrectly() = runTest {
        // Arrange
        val smallWeight = 0.1f
        coEvery { exerciseUsageHistoryDao.insertUsage(any()) } returns 1L

        // Act
        val result = weightMemoryService.updateExerciseWeight(testUserId, testExerciseId, smallWeight)

        // Assert
        assertTrue("Should handle small weight values", result.isSuccess)
    }

    @Test
    fun getRecentExercises_withLargeLimit_handlesCorrectly() = runTest {
        // Arrange
        val largeLimit = 1000
        val expectedList = emptyList<String>()
        coEvery { exerciseUsageHistoryDao.getRecentExerciseIds(testUserId, largeLimit) } returns expectedList

        // Act
        val result = weightMemoryService.getRecentExercises(testUserId, largeLimit)

        // Assert
        assertTrue("Should handle large limit values", result.isSuccess)
        assertEquals("Should return expected empty list", expectedList, result.getOrNull())
    }
} 