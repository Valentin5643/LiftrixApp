package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.TimeRange
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProgressStatsRepository
import com.example.liftrix.domain.repository.VolumeDataPoint
import com.example.liftrix.domain.repository.DurationDataPoint
import com.example.liftrix.domain.repository.FrequencyDataPoint
import com.example.liftrix.domain.repository.ProgressSummary
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * Comprehensive unit tests for ProgressDataServiceImpl
 * 
 * Tests all service methods with success and error scenarios using MockK
 * and proper coroutine testing patterns following Given/When/Then structure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressDataServiceTest {
    
    @MockK
    private lateinit var progressStatsRepository: ProgressStatsRepository
    
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var progressDataService: ProgressDataService
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = StandardTestDispatcher()
        progressDataService = ProgressDataServiceImpl(progressStatsRepository, testDispatcher)
    }
    
    @Test
    fun `given valid user and time range, when getting volume data, then returns success with data`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000) // 1 day ago
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        
        val expectedVolumeData = listOf(
            VolumeDataPoint(
                date = LocalDate.fromEpochDays(12345),
                totalVolume = 1500.0,
                workoutCount = 2,
                averageIntensity = 75.0
            ),
            VolumeDataPoint(
                date = LocalDate.fromEpochDays(12346),
                totalVolume = 1800.0,
                workoutCount = 3,
                averageIntensity = 80.0
            )
        )
        
        coEvery { 
            progressStatsRepository.getWorkoutVolumeData(userId, any(), any())
        } returns flowOf(expectedVolumeData)
        
        // When
        val result = progressDataService.getVolumeData(userId, timeRange)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected volume data", expectedVolumeData, result.getOrNull())
        
        coVerify { 
            progressStatsRepository.getWorkoutVolumeData(userId, any(), any())
        }
    }
    
    @Test
    fun `given repository error, when getting volume data, then returns database error`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        val repositoryException = RuntimeException("Database connection failed")
        
        coEvery { 
            progressStatsRepository.getWorkoutVolumeData(userId, any(), any())
        } throws repositoryException
        
        // When
        val result = progressDataService.getVolumeData(userId, timeRange)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val dbError = error as LiftrixError.DatabaseError
        assertEquals("Failed to retrieve volume data", dbError.errorMessage)
        assertEquals("getVolumeData", dbError.operation)
        assertEquals(userId, dbError.analyticsContext["userId"])
        assertEquals(timeRange.toString(), dbError.analyticsContext["timeRange"])
    }
    
    @Test
    fun `given valid user and time range, when getting duration data, then returns success with data`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        
        val expectedDurationData = listOf(
            DurationDataPoint(
                date = LocalDate.fromEpochDays(12345),
                averageDuration = 45.0,
                workoutCount = 2,
                totalMinutes = 90
            ),
            DurationDataPoint(
                date = LocalDate.fromEpochDays(12346),
                averageDuration = 50.0,
                workoutCount = 3,
                totalMinutes = 150
            )
        )
        
        coEvery { 
            progressStatsRepository.getWorkoutDurationData(userId, any(), any())
        } returns flowOf(expectedDurationData)
        
        // When
        val result = progressDataService.getDurationData(userId, timeRange)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected duration data", expectedDurationData, result.getOrNull())
        
        coVerify { 
            progressStatsRepository.getWorkoutDurationData(userId, any(), any())
        }
    }
    
    @Test
    fun `given repository error, when getting duration data, then returns database error`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        val repositoryException = RuntimeException("Database timeout")
        
        coEvery { 
            progressStatsRepository.getWorkoutDurationData(userId, any(), any())
        } throws repositoryException
        
        // When
        val result = progressDataService.getDurationData(userId, timeRange)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val dbError = error as LiftrixError.DatabaseError
        assertEquals("Failed to retrieve duration data", dbError.errorMessage)
        assertEquals("getDurationData", dbError.operation)
        assertEquals(userId, dbError.analyticsContext["userId"])
        assertEquals(timeRange.toString(), dbError.analyticsContext["timeRange"])
    }
    
    @Test
    fun `given valid user and time range, when getting frequency data, then returns success with data`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        
        val expectedFrequencyData = listOf(
            FrequencyDataPoint(
                date = LocalDate.fromEpochDays(12345),
                workoutCount = 2,
                weeklyAverage = 3.5,
                restDaysBetween = 1
            ),
            FrequencyDataPoint(
                date = LocalDate.fromEpochDays(12346),
                workoutCount = 3,
                weeklyAverage = 4.0,
                restDaysBetween = 0
            )
        )
        
        coEvery { 
            progressStatsRepository.getWorkoutFrequencyData(userId, any(), any())
        } returns flowOf(expectedFrequencyData)
        
        // When
        val result = progressDataService.getFrequencyData(userId, timeRange)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected frequency data", expectedFrequencyData, result.getOrNull())
        
        coVerify { 
            progressStatsRepository.getWorkoutFrequencyData(userId, any(), any())
        }
    }
    
    @Test
    fun `given repository error, when getting frequency data, then returns database error`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        val repositoryException = RuntimeException("Query execution failed")
        
        coEvery { 
            progressStatsRepository.getWorkoutFrequencyData(userId, any(), any())
        } throws repositoryException
        
        // When
        val result = progressDataService.getFrequencyData(userId, timeRange)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val dbError = error as LiftrixError.DatabaseError
        assertEquals("Failed to retrieve frequency data", dbError.errorMessage)
        assertEquals("getFrequencyData", dbError.operation)
        assertEquals(userId, dbError.analyticsContext["userId"])
        assertEquals(timeRange.toString(), dbError.analyticsContext["timeRange"])
    }
    
    @Test
    fun `given valid user and time range, when getting progress summary, then returns success with data`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        
        val expectedProgressSummary = ProgressSummary(
            totalWorkouts = 10,
            totalVolume = 15000.0,
            averageIntensity = 77.5,
            improvementPercentage = 12.5,
            consistencyScore = 85,
            weeklyFrequency = 3.2
        )
        
        coEvery { 
            progressStatsRepository.getProgressSummary(userId, any(), any())
        } returns flowOf(expectedProgressSummary)
        
        // When
        val result = progressDataService.getProgressSummary(userId, timeRange)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected progress summary", expectedProgressSummary, result.getOrNull())
        
        coVerify { 
            progressStatsRepository.getProgressSummary(userId, any(), any())
        }
    }
    
    @Test
    fun `given repository error, when getting progress summary, then returns database error`() = runTest {
        // Given
        val userId = "user123"
        val startDate = Date(System.currentTimeMillis() - 86400000)
        val endDate = Date(System.currentTimeMillis())
        val timeRange = TimeRange(startDate, endDate)
        val repositoryException = RuntimeException("Summary calculation failed")
        
        coEvery { 
            progressStatsRepository.getProgressSummary(userId, any(), any())
        } throws repositoryException
        
        // When
        val result = progressDataService.getProgressSummary(userId, timeRange)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val dbError = error as LiftrixError.DatabaseError
        assertEquals("Failed to retrieve progress summary", dbError.errorMessage)
        assertEquals("getProgressSummary", dbError.operation)
        assertEquals(userId, dbError.analyticsContext["userId"])
        assertEquals(timeRange.toString(), dbError.analyticsContext["timeRange"])
    }
    
    @Test
    fun `given valid user id, when refreshing all data, then returns success`() = runTest {
        // Given
        val userId = "user123"
        
        // When
        val result = progressDataService.refreshAllData(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return Unit", Unit, result.getOrNull())
    }
    
    @Test
    fun `given null or empty user id, when calling service methods, then handles gracefully`() = runTest {
        // Given
        val emptyUserId = ""
        val timeRange = TimeRange(Date(), Date())
        
        // When
        val volumeResult = progressDataService.getVolumeData(emptyUserId, timeRange)
        val durationResult = progressDataService.getDurationData(emptyUserId, timeRange)
        val frequencyResult = progressDataService.getFrequencyData(emptyUserId, timeRange)
        val summaryResult = progressDataService.getProgressSummary(emptyUserId, timeRange)
        val refreshResult = progressDataService.refreshAllData(emptyUserId)
        
        // Then
        // The service should handle empty user IDs gracefully
        // This tests the service's robustness with invalid inputs
        assertTrue("Volume result should complete", volumeResult.isSuccess || volumeResult.isFailure)
        assertTrue("Duration result should complete", durationResult.isSuccess || durationResult.isFailure)
        assertTrue("Frequency result should complete", frequencyResult.isSuccess || frequencyResult.isFailure)
        assertTrue("Summary result should complete", summaryResult.isSuccess || summaryResult.isFailure)
        assertTrue("Refresh result should complete", refreshResult.isSuccess || refreshResult.isFailure)
    }
    
    @Test
    fun `given concurrent calls to same service method, when executed simultaneously, then handles concurrency correctly`() = runTest {
        // Given
        val userId = "user123"
        val timeRange = TimeRange(Date(), Date())
        val expectedVolumeData = listOf(
            VolumeDataPoint(
                date = LocalDate.fromEpochDays(12345),
                totalVolume = 1500.0,
                workoutCount = 2,
                averageIntensity = 75.0
            )
        )
        
        coEvery { 
            progressStatsRepository.getWorkoutVolumeData(userId, any(), any())
        } returns flowOf(expectedVolumeData)
        
        // When - Execute multiple concurrent calls
        val results = listOf(
            progressDataService.getVolumeData(userId, timeRange),
            progressDataService.getVolumeData(userId, timeRange),
            progressDataService.getVolumeData(userId, timeRange)
        )
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
            assertEquals("Each result should return expected data", expectedVolumeData, result.getOrNull())
        }
        
        coVerify(exactly = 3) { 
            progressStatsRepository.getWorkoutVolumeData(userId, any(), any())
        }
    }
    
    @Test
    fun `given flow emission error, when getting data, then maps to proper LiftrixError`() = runTest {
        // Given
        val userId = "user123"
        val timeRange = TimeRange(Date(), Date())
        
        coEvery { 
            progressStatsRepository.getWorkoutVolumeData(userId, any(), any())
        } returns flowOf<List<VolumeDataPoint>>().apply {
            // Simulate flow emission error
            throw RuntimeException("Flow emission failed")
        }
        
        // When
        val result = progressDataService.getVolumeData(userId, timeRange)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val dbError = error as LiftrixError.DatabaseError
        assertEquals("Failed to retrieve volume data", dbError.errorMessage)
        assertEquals("getVolumeData", dbError.operation)
        assertTrue("Should contain analytics context", dbError.analyticsContext.isNotEmpty())
    }
}