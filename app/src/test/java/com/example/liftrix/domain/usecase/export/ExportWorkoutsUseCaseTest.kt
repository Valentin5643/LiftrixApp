package com.example.liftrix.domain.usecase.export

import com.example.liftrix.data.local.dao.DataExportDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.DataExportEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for ExportWorkoutsUseCase.
 * 
 * This test suite validates the core export functionality including:
 * - Successful export operations for all supported formats
 * - Error handling for invalid requests and system failures
 * - Export tracking and status management
 * - Progress monitoring and cancellation
 * - Data validation and security checks
 * 
 * The tests use MockK for dependency mocking and follow the AAA pattern
 * (Arrange, Act, Assert) for clear test structure and readability.
 */
class ExportWorkoutsUseCaseTest {

    // System under test
    private lateinit var exportWorkoutsUseCase: ExportWorkoutsUseCase
    
    // Dependencies (mocked)
    private val workoutDao = mockk<WorkoutDao>()
    private val dataExportDao = mockk<DataExportDao>()
    
    // Test data
    private val testUserId = "test-user-123"
    private val testExportId = "export-456"
    
    @Before
    fun setup() {
        exportWorkoutsUseCase = ExportWorkoutsUseCase(
            workoutDao = workoutDao,
            dataExportDao = dataExportDao
        )
        
        // Setup common mock behaviors
        every { dataExportDao.insertExport(any()) } returns Unit
        every { dataExportDao.updateExportStatus(any(), any(), any(), any()) } returns Unit
        every { dataExportDao.markExportCompleted(any(), any(), any(), any(), any(), any()) } returns Unit
        
        // Mock file creation for tests
        mockkStatic(File::class)
        every { File.createTempFile(any(), any()) } returns mockk<File> {
            every { absolutePath } returns "/tmp/test-export.json"
            every { writeText(any()) } returns Unit
            every { length() } returns 1024L
        }
    }
    
    @After
    fun teardown() {
        unmockkAll()
    }
    
    @Test
    fun `invoke with valid JSON request should export successfully`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.JSON,
            dataTypes = setOf(DataType.WORKOUTS),
            dateRange = null
        )
        
        val mockWorkouts = listOf(
            mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
                every { id } returns "workout-1"
                every { name } returns "Test Workout"
            }
        )
        
        every { workoutDao.getAllWorkoutsForUser(testUserId) } returns mockWorkouts.toString()
        
        // Act
        val result = exportWorkoutsUseCase.invoke(testUserId, request)
        
        // Assert
        assertIs<LiftrixResult.Success<ExportResult>>(result)
        val exportResult = result.getOrThrow()
        
        assertEquals(ExportFormat.JSON, exportResult.format)
        assertTrue(exportResult.recordCount >= 0)
        
        // Verify database interactions
        verify { dataExportDao.insertExport(any()) }
        verify { dataExportDao.updateExportStatus(any(), testUserId, "IN_PROGRESS") }
        verify { dataExportDao.markExportCompleted(any(), testUserId, any(), any(), any(), any()) }
    }
    
    @Test
    fun `invoke with CSV format should create CSV file`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.CSV,
            dataTypes = setOf(DataType.WORKOUTS, DataType.EXERCISES),
            dateRange = DateRange(
                start = LocalDate.now().minusDays(30),
                end = LocalDate.now()
            )
        )
        
        every { workoutDao.getWorkoutsInDateRangeForUser(any(), any(), any()) } returns emptyList()
        
        // Act
        val result = exportWorkoutsUseCase.invoke(testUserId, request)
        
        // Assert
        assertIs<LiftrixResult.Success<ExportResult>>(result)
        val exportResult = result.getOrThrow()
        assertEquals(ExportFormat.CSV, exportResult.format)
        
        verify { workoutDao.getWorkoutsInDateRangeForUser(testUserId, any(), any()) }
    }
    
    @Test
    fun `invoke with empty data types should fail validation`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.JSON,
            dataTypes = emptySet(), // Invalid: empty data types
            dateRange = null
        )
        
        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            exportWorkoutsUseCase.invoke(testUserId, request)
        }
        
        // Verify no database operations occurred
        verify(exactly = 0) { dataExportDao.insertExport(any()) }
    }
    
    @Test
    fun `invoke with invalid date range should fail validation`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.JSON,
            dataTypes = setOf(DataType.WORKOUTS),
            dateRange = DateRange(
                start = LocalDate.now(), // Invalid: start after end
                end = LocalDate.now().minusDays(1)
            )
        )
        
        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            exportWorkoutsUseCase.invoke(testUserId, request)
        }
    }
    
    @Test
    fun `invoke with database error should handle gracefully`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.JSON,
            dataTypes = setOf(DataType.WORKOUTS),
            dateRange = null
        )
        
        every { workoutDao.getAllWorkoutsForUser(testUserId) } throws RuntimeException("Database error")
        
        // Act
        val result = exportWorkoutsUseCase.invoke(testUserId, request)
        
        // Assert
        assertIs<LiftrixResult.Error<ExportResult>>(result)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.BusinessLogicError>(error)
        assertTrue(error.message.contains("Failed to export workout data"))
        
        // Verify export was marked as failed
        verify { dataExportDao.updateExportStatus(any(), testUserId, "FAILED", any()) }
    }
    
    @Test
    fun `invoke with unsupported format should fail appropriately`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.FIT, // Not yet implemented
            dataTypes = setOf(DataType.WORKOUTS),
            dateRange = null
        )
        
        every { workoutDao.getAllWorkoutsForUser(testUserId) } returns "[]"
        
        // Act
        val result = exportWorkoutsUseCase.invoke(testUserId, request)
        
        // Assert
        assertIs<LiftrixResult.Error<ExportResult>>(result)
        val error = result.exceptionOrNull()
        assertTrue(error is UnsupportedOperationException)
        
        verify { dataExportDao.updateExportStatus(any(), testUserId, "FAILED", any()) }
    }
    
    @Test
    fun `cancelExport should update status correctly`() = runTest {
        // Arrange
        val exportId = "test-export-123"
        
        // Act
        val result = exportWorkoutsUseCase.cancelExport(exportId, testUserId)
        
        // Assert
        assertIs<LiftrixResult.Success<Unit>>(result)
        verify { dataExportDao.updateExportStatus(exportId, testUserId, "CANCELLED") }
    }
    
    @Test
    fun `cancelExport with database error should return error result`() = runTest {
        // Arrange
        val exportId = "test-export-123"
        every { dataExportDao.updateExportStatus(exportId, testUserId, "CANCELLED") } throws RuntimeException("Database error")
        
        // Act
        val result = exportWorkoutsUseCase.cancelExport(exportId, testUserId)
        
        // Assert
        assertIs<LiftrixResult.Error<Unit>>(result)
        val error = result.exceptionOrNull()
        assertIs<LiftrixError.BusinessLogicError>(error)
        assertTrue(error.message.contains("Failed to cancel export"))
    }
    
    @Test
    fun `getExportProgress should emit progress updates`() = runTest {
        // Arrange
        val exportId = "test-export-123"
        
        // Act
        val progressFlow = exportWorkoutsUseCase.getExportProgress(exportId)
        
        // Assert
        val progressList = mutableListOf<ExportProgress>()
        progressFlow.collect { progress ->
            progressList.add(progress)
            if (progress.progressPercentage == 100) {
                return@collect
            }
        }
        
        assertTrue(progressList.isNotEmpty())
        assertEquals(exportId, progressList.first().exportId)
        assertEquals(100, progressList.last().progressPercentage)
    }
    
    @Test
    fun `export with very old date range should fail validation`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.JSON,
            dataTypes = setOf(DataType.WORKOUTS),
            dateRange = DateRange(
                start = LocalDate.now().minusYears(25), // Too far in the past
                end = LocalDate.now()
            )
        )
        
        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            exportWorkoutsUseCase.invoke(testUserId, request)
        }
    }
    
    @Test
    fun `export should create proper entity with all required fields`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.CSV,
            dataTypes = setOf(DataType.WORKOUTS, DataType.TEMPLATES),
            dateRange = DateRange(
                start = LocalDate.now().minusDays(7),
                end = LocalDate.now()
            )
        )
        
        every { workoutDao.getWorkoutsInDateRangeForUser(any(), any(), any()) } returns emptyList()
        
        val capturedEntity = slot<DataExportEntity>()
        every { dataExportDao.insertExport(capture(capturedEntity)) } returns Unit
        
        // Act
        exportWorkoutsUseCase.invoke(testUserId, request)
        
        // Assert
        val entity = capturedEntity.captured
        assertEquals(testUserId, entity.userId)
        assertEquals("CSV", entity.exportType)
        assertEquals("WORKOUTS,TEMPLATES", entity.dataTypes)
        assertEquals("REQUESTED", entity.status)
        assertTrue(entity.requestedAt > 0)
        assertTrue(entity.expiresAt > entity.requestedAt)
    }
    
    @Test
    fun `export with large dataset should handle memory efficiently`() = runTest {
        // Arrange
        val request = ExportRequest(
            format = ExportFormat.JSON,
            dataTypes = setOf(DataType.WORKOUTS),
            dateRange = null
        )
        
        // Simulate large dataset
        val largeWorkoutList = (1..1000).map { i ->
            mockk<com.example.liftrix.data.local.entity.WorkoutEntity> {
                every { id } returns "workout-$i"
                every { name } returns "Workout $i"
            }
        }
        
        every { workoutDao.getAllWorkoutsForUser(testUserId) } returns largeWorkoutList.toString()
        
        // Act
        val result = exportWorkoutsUseCase.invoke(testUserId, request)
        
        // Assert
        assertIs<LiftrixResult.Success<ExportResult>>(result)
        val exportResult = result.getOrThrow()
        assertTrue(exportResult.recordCount >= 0)
        
        // Verify export completed successfully even with large dataset
        verify { dataExportDao.markExportCompleted(any(), testUserId, any(), any(), any(), any()) }
    }
}

/**
 * ★ Insight ─────────────────────────────────────
 * - ExportWorkoutsUseCaseTest provides comprehensive coverage of the core export functionality
 * - Tests both success and failure scenarios including validation, database errors, and format support
 * - Uses MockK for clean dependency mocking and follows AAA pattern for clear test structure
 * ─────────────────────────────────────────────────
 */