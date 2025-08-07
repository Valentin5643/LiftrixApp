package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for DeleteFolderUseCase
 * 
 * Tests business logic validation, user scoping, template reallocation,
 * default folder protection, and error handling.
 */
class DeleteFolderUseCaseTest {
    
    private lateinit var folderRepository: FolderRepository
    private lateinit var workoutTemplateRepository: WorkoutTemplateRepository
    private lateinit var deleteFolderUseCase: DeleteFolderUseCase
    
    // Test data constants
    private val validUserId = "user123"
    private val validFolderId = FolderId("folder456")
    private val defaultFolderId = FolderId("uncategorized_user123")
    private val invalidUserId = ""
    private val invalidFolderId = FolderId("")
    private val nonExistentFolderId = FolderId("nonexistent")
    
    private val testFolder = Folder(
        id = validFolderId,
        userId = UserId(validUserId),
        name = FolderName("Test Folder"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 2
    )
    
    private val defaultFolder = Folder(
        id = defaultFolderId,
        userId = UserId(validUserId),
        name = FolderName("Uncategorized"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 0
    )
    
    private val testTemplate1 = WorkoutTemplate(
        id = WorkoutTemplateId("template1"),
        userId = UserId(validUserId),
        name = "Test Workout 1",
        folderId = validFolderId.value,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        exercises = emptyList()
    )
    
    private val testTemplate2 = WorkoutTemplate(
        id = WorkoutTemplateId("template2"),
        userId = UserId(validUserId),
        name = "Test Workout 2",
        folderId = validFolderId.value,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        exercises = emptyList()
    )
    
    @Before
    fun setup() {
        folderRepository = mockk()
        workoutTemplateRepository = mockk()
        deleteFolderUseCase = DeleteFolderUseCase(folderRepository, workoutTemplateRepository)
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `invoke with valid input should delete empty folder successfully`() = runTest {
        // Arrange
        val emptyFolder = testFolder.copy(templateCount = 0)
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        
        coEvery { folderRepository.getFolderById(validFolderId, validUserId) } returns flowOf(emptyFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { folderRepository.deleteFolder(validFolderId, validUserId) } returns Result.success(Unit)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify interactions
        coVerify(exactly = 1) { folderRepository.getFolderById(validFolderId, validUserId) }
        coVerify(exactly = 1) { folderRepository.getOrCreateDefaultFolder(validUserId) }
        coVerify(exactly = 1) { folderRepository.deleteFolder(validFolderId, validUserId) }
        coVerify(exactly = 0) { workoutTemplateRepository.getTemplatesByFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with folder containing templates should move templates to default folder before deletion`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templates = listOf(testTemplate1, testTemplate2)
        
        coEvery { folderRepository.getFolderById(validFolderId, validUserId) } returns flowOf(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.success(templates))
        coEvery { folderRepository.moveTemplateToFolder(any(), any(), validUserId) } returns Result.success(Unit)
        coEvery { folderRepository.deleteFolder(validFolderId, validUserId) } returns Result.success(Unit)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify all templates were moved to default folder
        coVerify(exactly = 1) { folderRepository.moveTemplateToFolder("template1", defaultFolderId, validUserId) }
        coVerify(exactly = 1) { folderRepository.moveTemplateToFolder("template2", defaultFolderId, validUserId) }
        coVerify(exactly = 1) { folderRepository.deleteFolder(validFolderId, validUserId) }
    }
    
    @Test
    fun `invoke with blank userId should fail validation`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(invalidUserId, validFolderId)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("User ID cannot be blank") == true)
        }
        
        // Verify no repository interactions
        coVerify(exactly = 0) { folderRepository.getFolderById(any(), any()) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with blank folderId should fail validation`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, invalidFolderId)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Folder ID cannot be blank") == true)
        }
        
        // Verify no repository interactions
        coVerify(exactly = 0) { folderRepository.getFolderById(any(), any()) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with non-existent folder should fail`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, nonExistentFolderId)
        
        coEvery { folderRepository.getFolderById(nonExistentFolderId, validUserId) } returns flowOf(null)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Folder not found or not owned by user") == true)
        }
        
        // Verify folder lookup but no deletion
        coVerify(exactly = 1) { folderRepository.getFolderById(nonExistentFolderId, validUserId) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with default folder should fail protection check`() = runTest {
        // Arrange
        val defaultFolderWithId = testFolder.copy(id = FolderId("uncategorized_user123"))
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, defaultFolderWithId.id)
        
        coEvery { folderRepository.getFolderById(defaultFolderWithId.id, validUserId) } returns flowOf(defaultFolderWithId)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Cannot delete the default 'Uncategorized' folder") == true)
        }
        
        // Verify protection prevented deletion
        coVerify(exactly = 1) { folderRepository.getFolderById(defaultFolderWithId.id, validUserId) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should fail when default folder creation fails`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val defaultFolderError = RuntimeException("Default folder creation failed")
        
        coEvery { folderRepository.getFolderById(validFolderId, validUserId) } returns flowOf(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.failure(defaultFolderError)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertEquals(defaultFolderError, exception)
        }
        
        // Verify deletion was not attempted
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should fail when template retrieval fails`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templateError = RuntimeException("Template retrieval failed")
        
        coEvery { folderRepository.getFolderById(validFolderId, validUserId) } returns flowOf(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.failure(templateError))
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertEquals(templateError, exception)
        }
        
        // Verify deletion was not attempted
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should fail when template move operation fails`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templates = listOf(testTemplate1, testTemplate2)
        val moveError = RuntimeException("Template move failed")
        
        coEvery { folderRepository.getFolderById(validFolderId, validUserId) } returns flowOf(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.success(templates))
        coEvery { folderRepository.moveTemplateToFolder("template1", defaultFolderId, validUserId) } returns Result.failure(moveError)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertEquals(moveError, exception)
        }
        
        // Verify deletion was not attempted
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should ensure user scoping in all operations`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templates = listOf(testTemplate1)
        
        coEvery { folderRepository.getFolderById(validFolderId, validUserId) } returns flowOf(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.success(templates))
        coEvery { folderRepository.moveTemplateToFolder(any(), any(), validUserId) } returns Result.success(Unit)
        coEvery { folderRepository.deleteFolder(validFolderId, validUserId) } returns Result.success(Unit)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify user scoping in all repository calls
        coVerify(exactly = 1) { folderRepository.getFolderById(validFolderId, validUserId) }
        coVerify(exactly = 1) { folderRepository.getOrCreateDefaultFolder(validUserId) }
        coVerify(exactly = 1) { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) }
        coVerify(exactly = 1) { folderRepository.moveTemplateToFolder("template1", defaultFolderId, validUserId) }
        coVerify(exactly = 1) { folderRepository.deleteFolder(validFolderId, validUserId) }
    }
    
    @Test
    fun `invoke should handle different user attempting to delete another user's folder`() = runTest {
        // Arrange
        val differentUserId = "differentUser"
        val input = DeleteFolderUseCase.DeleteFolderInput(differentUserId, validFolderId)
        
        // Mock that folder is not found for different user (user scoping)
        coEvery { folderRepository.getFolderById(validFolderId, differentUserId) } returns flowOf(null)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("Folder not found or not owned by user") == true)
        }
        
        // Verify user scoping prevented unauthorized access
        coVerify(exactly = 1) { folderRepository.getFolderById(validFolderId, differentUserId) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
}