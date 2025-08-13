package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for MoveWorkoutToFolderUseCase
 * 
 * Critical tests focusing on user scoping validation, security, and business logic.
 * This test suite addresses the security risks identified in the quality review.
 */
class MoveWorkoutToFolderUseCaseTest {
    
    private lateinit var workoutTemplateRepository: WorkoutTemplateRepository
    private lateinit var folderRepository: FolderRepository
    private lateinit var moveWorkoutToFolderUseCase: MoveWorkoutToFolderUseCase
    
    // Test data constants
    private val validUserId = "user123"
    private val differentUserId = "user456"
    private val validFolderId = "folder789"
    private val invalidFolderId = "invalid_folder"
    private val sameFolder = "same_folder"
    private val nonExistentFolder = "nonexistent_folder"
    
    private val testWorkoutTemplate = WorkoutTemplate(
        id = WorkoutTemplateId("template123"),
        userId = validUserId,
        name = "Test Workout",
        folderId = "original_folder",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        exercises = emptyList()
    )
    
    private val targetFolder = Folder(
        id = FolderId(validFolderId),
        userId = validUserId,
        name = FolderName("Target Folder"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 0
    )
    
    private val differentUserFolder = Folder(
        id = FolderId(validFolderId),
        userId = differentUserId,
        name = FolderName("Different User's Folder"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 0
    )
    
    @Before
    fun setup() {
        workoutTemplateRepository = mockk()
        folderRepository = mockk()
        moveWorkoutToFolderUseCase = MoveWorkoutToFolderUseCase(workoutTemplateRepository, folderRepository)
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `invoke with valid input should move workout to target folder successfully`() = runTest {
        // Arrange
        val updatedTemplate = testWorkoutTemplate.copy(folderId = validFolderId)
        
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } returns targetFolder
        coEvery { workoutTemplateRepository.updateTemplate(any()) } returns Result.success(updatedTemplate)
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert
        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { updatedWorkout ->
                assertEquals(validFolderId, updatedWorkout.folderId)
                assertEquals(testWorkoutTemplate.name, updatedWorkout.name)
                assertEquals(testWorkoutTemplate.id, updatedWorkout.id)
            },
            onFailure = { fail("Expected success but got failure: $it") }
        )
        
        // Verify interactions
        coVerify(exactly = 1) { folderRepository.getFolderById(FolderId(validFolderId)) }
        coVerify(exactly = 1) { workoutTemplateRepository.updateTemplate(any()) }
    }
    
    @Test
    fun `invoke with same folder should return workout unchanged without repository calls`() = runTest {
        // Arrange
        val workoutInSameFolder = testWorkoutTemplate.copy(folderId = sameFolder)
        
        // Act
        val result = moveWorkoutToFolderUseCase(workoutInSameFolder, sameFolder)
        
        // Assert
        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { unchangedWorkout ->
                assertEquals(workoutInSameFolder, unchangedWorkout)
            },
            onFailure = { fail("Expected success but got failure: $it") }
        )
        
        // Verify no repository interactions when no move is needed
        coVerify(exactly = 0) { folderRepository.getFolderById(any()) }
        coVerify(exactly = 0) { workoutTemplateRepository.updateTemplate(any()) }
    }
    
    @Test
    fun `invoke with non-existent folder should fail validation`() = runTest {
        // Arrange - folder doesn't exist
        coEvery { folderRepository.getFolderById(FolderId(nonExistentFolder)) } returns null
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, nonExistentFolder)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error.toString().contains("Target folder does not exist"))
            }
        )
        
        // Verify validation prevented further operations
        coVerify(exactly = 1) { folderRepository.getFolderById(FolderId(nonExistentFolder)) }
        coVerify(exactly = 0) { workoutTemplateRepository.updateTemplate(any()) }
    }
    
    @Test
    fun `invoke should prevent cross-user folder access - SECURITY TEST`() = runTest {
        // Arrange - target folder belongs to different user
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } returns differentUserFolder
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert - Should fail with user mismatch error
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error.toString().contains("Cannot move workout to folder belonging to different user"))
            }
        )
        
        // Verify security validation prevented unauthorized access
        coVerify(exactly = 1) { folderRepository.getFolderById(FolderId(validFolderId)) }
        coVerify(exactly = 0) { workoutTemplateRepository.updateTemplate(any()) }
    }
    
    @Test
    fun `invoke should handle repository update failure gracefully`() = runTest {
        // Arrange
        val repositoryError = RuntimeException("Database update failed")
        
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } returns targetFolder
        coEvery { workoutTemplateRepository.updateTemplate(any()) } returns Result.failure(repositoryError)
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error.toString().contains("Failed to update workout template"))
            }
        )
        
        // Verify all validations passed but update failed
        coVerify(exactly = 1) { folderRepository.getFolderById(FolderId(validFolderId)) }
        coVerify(exactly = 1) { workoutTemplateRepository.updateTemplate(any()) }
    }
    
    @Test
    fun `invoke should validate user scoping for both workout and folder`() = runTest {
        // Arrange - valid scenario for detailed verification
        val updatedTemplate = testWorkoutTemplate.copy(folderId = validFolderId)
        
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } returns targetFolder
        coEvery { workoutTemplateRepository.updateTemplate(any()) } returns Result.success(updatedTemplate)
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify that the updated template maintains proper user scoping
        coVerify(exactly = 1) { 
            workoutTemplateRepository.updateTemplate(match { template ->
                template.userId == validUserId &&
                template.folderId == validFolderId &&
                template.id == testWorkoutTemplate.id
            })
        }
    }
    
    @Test
    fun `invoke should update timestamp when moving workout`() = runTest {
        // Arrange
        val originalTime = testWorkoutTemplate.updatedAt
        val updatedTemplate = testWorkoutTemplate.copy(
            folderId = validFolderId,
            updatedAt = Instant.now().plusSeconds(1)
        )
        
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } returns targetFolder
        coEvery { workoutTemplateRepository.updateTemplate(any()) } returns Result.success(updatedTemplate)
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify timestamp was updated during the move
        coVerify(exactly = 1) { 
            workoutTemplateRepository.updateTemplate(match { template ->
                template.updatedAt.isAfter(originalTime)
            })
        }
    }
    
    @Test
    fun `invoke should handle folder repository exceptions gracefully`() = runTest {
        // Arrange
        val folderRepositoryError = RuntimeException("Folder lookup failed")
        
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } throws folderRepositoryError
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error.toString().contains("Folder lookup failed"))
            }
        )
        
        // Verify error was caught and handled
        coVerify(exactly = 1) { folderRepository.getFolderById(FolderId(validFolderId)) }
        coVerify(exactly = 0) { workoutTemplateRepository.updateTemplate(any()) }
    }
    
    @Test
    fun `invoke should ensure data integrity with proper field validation`() = runTest {
        // Arrange
        val updatedTemplate = testWorkoutTemplate.copy(folderId = validFolderId)
        
        coEvery { folderRepository.getFolderById(FolderId(validFolderId)) } returns targetFolder
        coEvery { workoutTemplateRepository.updateTemplate(any()) } returns Result.success(updatedTemplate)
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, validFolderId)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify that only the folder ID and timestamp are modified
        coVerify(exactly = 1) { 
            workoutTemplateRepository.updateTemplate(match { template ->
                template.id == testWorkoutTemplate.id &&
                template.userId == testWorkoutTemplate.userId &&
                template.name == testWorkoutTemplate.name &&
                template.folderId == validFolderId &&
                template.exercises == testWorkoutTemplate.exercises &&
                template.createdAt == testWorkoutTemplate.createdAt
            })
        }
    }
    
    @Test
    fun `invoke should validate folder existence before user scoping check`() = runTest {
        // This test ensures the proper order of validation checks
        
        // Arrange - folder doesn't exist, so user check shouldn't happen
        coEvery { folderRepository.getFolderById(FolderId(nonExistentFolder)) } returns null
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, nonExistentFolder)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { error ->
            // Should fail with "folder does not exist" before any user validation
            assertTrue(error.message?.contains("Target folder does not exist") == true)
            // Should not contain user validation error message
            assertTrue(error.message?.contains("different user") == false)
        }
    }
    
    @Test
    fun `invoke should handle edge case with empty folder ID`() = runTest {
        // Arrange
        val emptyFolderId = ""
        
        coEvery { folderRepository.getFolderById(FolderId(emptyFolderId)) } returns null
        
        // Act
        val result = moveWorkoutToFolderUseCase(testWorkoutTemplate, emptyFolderId)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error.toString().contains("Target folder does not exist"))
            }
        )
    }
    
    @Test
    fun `invoke should handle complex user scoping scenario with multiple validations`() = runTest {
        // This test simulates a complex scenario where multiple validations could potentially conflict
        
        // Arrange - Create workout from one user trying to move to another user's folder
        val attackerWorkout = WorkoutTemplate(
            id = WorkoutTemplateId("attacker_template"),
            userId = "attacker_user",
            name = "Attacker Workout",
            folderId = "attacker_folder",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            exercises = emptyList()
        )
        
        // Target folder belongs to victim user
        val victimFolder = Folder(
            id = FolderId("victim_folder"),
            userId = "victim_user",
            name = FolderName("Victim's Folder"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            templateCount = 0
        )
        
        coEvery { folderRepository.getFolderById(FolderId("victim_folder")) } returns victimFolder
        
        // Act - Attacker tries to move their workout to victim's folder
        val result = moveWorkoutToFolderUseCase(attackerWorkout, "victim_folder")
        
        // Assert - Should fail with user mismatch error
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { error ->
                assertTrue(error.toString().contains("Cannot move workout to folder belonging to different user"))
            }
        )
        
        // Verify security prevented the cross-user operation
        coVerify(exactly = 1) { folderRepository.getFolderById(FolderId("victim_folder")) }
        coVerify(exactly = 0) { workoutTemplateRepository.updateTemplate(any()) }
    }
}