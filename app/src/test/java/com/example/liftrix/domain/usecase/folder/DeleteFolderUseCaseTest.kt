package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import com.example.liftrix.data.local.LiftrixDatabase
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for DeleteFolderUseCase
 * 
 * Tests business logic validation, user scoping, template reallocation,
 * default folder protection, and error handling.
 */
class DeleteFolderUseCaseTest {
    
    private lateinit var folderRepository: FolderRepository
    private lateinit var workoutTemplateRepository: WorkoutTemplateRepository
    private lateinit var mockDatabase: LiftrixDatabase
    private lateinit var deleteFolderUseCase: DeleteFolderUseCase
    
    // Test data constants
    private val validUserId = "user123"
    private val validFolderId = FolderId("folder456")
    private val defaultFolderId = FolderId("uncategorized_user123")
    private val invalidUserId = ""
    private val blankFolderIdString = ""  // For validation testing
    private val nonExistentFolderId = FolderId("nonexistent")
    
    private val testFolder = Folder(
        id = validFolderId,
        userId = validUserId,
        name = FolderName("Test Folder"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 2
    )
    
    private val defaultFolder = Folder(
        id = defaultFolderId,
        userId = validUserId,
        name = FolderName("Uncategorized"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 0
    )
    
    private val testTemplate1 = WorkoutTemplate(
        id = WorkoutTemplateId("template1"),
        userId = validUserId,
        name = "Test Workout 1",
        folderId = validFolderId.value,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        exercises = emptyList()
    )
    
    private val testTemplate2 = WorkoutTemplate(
        id = WorkoutTemplateId("template2"),
        userId = validUserId,
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
        mockDatabase = mockk(relaxed = true)
        deleteFolderUseCase = DeleteFolderUseCase(folderRepository, workoutTemplateRepository, mockDatabase)
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
        
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, validUserId) } returns Result.success(emptyFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { folderRepository.deleteFolder(validFolderId, validUserId) } returns Result.success(Unit)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify interactions
        coVerify(exactly = 1) { folderRepository.getFolderByIdDirect(validFolderId, validUserId) }
        coVerify(exactly = 1) { folderRepository.getOrCreateDefaultFolder(validUserId) }
        coVerify(exactly = 1) { folderRepository.deleteFolder(validFolderId, validUserId) }
        coVerify(exactly = 0) { workoutTemplateRepository.getTemplatesByFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with folder containing templates should move templates to default folder before deletion`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templates = listOf(testTemplate1, testTemplate2)
        
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, validUserId) } returns Result.success(testFolder)
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
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.ValidationError)
                assertTrue(error.toString().contains("User ID cannot be blank"))
            }
        )
        
        // Verify no repository interactions
        coVerify(exactly = 0) { folderRepository.getFolderByIdDirect(any(), any()) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with blank folderId should fail validation`() = runTest {
        // Arrange & Act - FolderId creation should throw immediately
        val exception = try {
            FolderId(blankFolderIdString)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        
        // Assert
        assertTrue(exception != null, "Should throw IllegalArgumentException for blank folder ID")
        assertTrue(exception?.message?.contains("Folder ID cannot be blank") == true, 
            "Exception should contain validation message")
        
        // Verify no repository interactions
        coVerify(exactly = 0) { folderRepository.getFolderByIdDirect(any(), any()) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with non-existent folder should fail`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, nonExistentFolderId)
        
        coEvery { folderRepository.getFolderByIdDirect(nonExistentFolderId, validUserId) } returns Result.success(null)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.ValidationError)
                assertTrue(error.toString().contains("Folder not found or not owned by user"))
            }
        )
        
        // Verify folder lookup but no deletion
        coVerify(exactly = 1) { folderRepository.getFolderByIdDirect(nonExistentFolderId, validUserId) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke with default folder should fail protection check`() = runTest {
        // Arrange
        val defaultFolderWithId = testFolder.copy(id = FolderId("uncategorized_user123"))
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, defaultFolderWithId.id)
        
        coEvery { folderRepository.getFolderByIdDirect(defaultFolderWithId.id, validUserId) } returns Result.success(defaultFolderWithId)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.ValidationError)
                assertTrue(error.toString().contains("Cannot delete the default 'Uncategorized' folder"))
            }
        )
        
        // Verify protection prevented deletion
        coVerify(exactly = 1) { folderRepository.getFolderByIdDirect(defaultFolderWithId.id, validUserId) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should fail when default folder creation fails`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val defaultFolderError = RuntimeException("Default folder creation failed")
        
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, validUserId) } returns Result.success(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.failure(defaultFolderError)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.BusinessLogicError)
                assertTrue(error.toString().contains("Default folder creation failed"))
            }
        )
        
        // Verify deletion was not attempted
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should fail when template retrieval fails`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templateError = RuntimeException("Template retrieval failed")
        
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, validUserId) } returns Result.success(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.failure(templateError))
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.BusinessLogicError)
                assertTrue(error.toString().contains("Template retrieval failed"))
            }
        )
        
        // Verify deletion was not attempted
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should fail when template move operation fails`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templates = listOf(testTemplate1, testTemplate2)
        val moveError = RuntimeException("Template move failed")
        
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, validUserId) } returns Result.success(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.success(templates))
        coEvery { folderRepository.moveTemplateToFolder("template1", defaultFolderId, validUserId) } returns Result.failure(moveError)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.BusinessLogicError)
                assertTrue(error.toString().contains("Template move failed"))
            }
        )
        
        // Verify deletion was not attempted
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
    
    @Test
    fun `invoke should ensure user scoping in all operations`() = runTest {
        // Arrange
        val input = DeleteFolderUseCase.DeleteFolderInput(validUserId, validFolderId)
        val templates = listOf(testTemplate1)
        
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, validUserId) } returns Result.success(testFolder)
        coEvery { folderRepository.getOrCreateDefaultFolder(validUserId) } returns Result.success(defaultFolder)
        coEvery { workoutTemplateRepository.getTemplatesByFolder(validUserId, validFolderId.value) } returns flowOf(Result.success(templates))
        coEvery { folderRepository.moveTemplateToFolder(any(), any(), validUserId) } returns Result.success(Unit)
        coEvery { folderRepository.deleteFolder(validFolderId, validUserId) } returns Result.success(Unit)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify user scoping in all repository calls
        coVerify(exactly = 1) { folderRepository.getFolderByIdDirect(validFolderId, validUserId) }
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
        coEvery { folderRepository.getFolderByIdDirect(validFolderId, differentUserId) } returns Result.success(null)
        
        // Act
        val result = deleteFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error is LiftrixError.ValidationError)
                assertTrue(error.toString().contains("Folder not found or not owned by user"))
            }
        )
        
        // Verify user scoping prevented unauthorized access
        coVerify(exactly = 1) { folderRepository.getFolderByIdDirect(validFolderId, differentUserId) }
        coVerify(exactly = 0) { folderRepository.deleteFolder(any(), any()) }
    }
}