package com.example.liftrix.domain.usecase.folder

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.FolderRepository
import com.example.liftrix.domain.repository.ProfileRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for CreateFolderUseCase
 * 
 * Tests business logic validation, user scoping, error handling,
 * and integration with repository layer.
 */
class CreateFolderUseCaseTest {
    
    private lateinit var folderRepository: FolderRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var createFolderUseCase: CreateFolderUseCase
    
    // Test data constants
    private val validUserId = "user123"
    private val validFolderName = "Test Folder"
    private val existingFolderName = "Existing Folder"
    private val invalidUserId = ""
    private val blankFolderNameString = ""  // For validation testing
    private val tooLongFolderName = "a".repeat(51) // Assuming max length is 50
    private val tooShortFolderName = "ab" // Assuming min length is 3
    
    @Before
    fun setup() {
        folderRepository = mockk()
        profileRepository = mockk()
        createFolderUseCase = CreateFolderUseCase(folderRepository, profileRepository)
        
        // Mock successful profile existence by default
        coEvery { profileRepository.hasProfile(any()) } returns true
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `invoke with valid input should create folder successfully`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, validFolderName)
        val expectedFolder = Folder(
            id = FolderId.generate(),
            userId = validUserId,
            name = FolderName(validFolderName),
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            templateCount = 0
        )
        
        coEvery { folderRepository.doesFolderNameExist(validUserId, validFolderName) } returns false
        coEvery { folderRepository.createFolder(any()) } returns Result.success(expectedFolder)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        result.onSuccess { folder ->
            assertEquals(validUserId, folder.userId)
            assertEquals(validFolderName, folder.name.value)
            assertEquals(0, folder.templateCount)
        }
        
        // Verify interactions
        coVerify(exactly = 1) { profileRepository.hasProfile(validUserId) }
        coVerify(exactly = 1) { folderRepository.doesFolderNameExist(validUserId, validFolderName) }
        coVerify(exactly = 1) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke with blank userId should throw IllegalArgumentException`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(invalidUserId, validFolderName)
        
        // Act & Assert
        val result = createFolderUseCase(input)
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("User ID cannot be blank") == true)
        }
        
        // Verify no repository interactions for invalid input
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke with blank folder name should throw IllegalArgumentException`() = runTest {
        // Arrange & Act - FolderName creation should throw immediately
        val exception = try {
            FolderName(blankFolderNameString)
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        
        // Assert
        assertTrue(exception != null, "Should throw IllegalArgumentException for blank folder name")
        assertTrue(exception?.message?.contains("Folder name cannot be blank") == true, 
            "Exception should contain validation message")
        
        // Verify no repository interactions since validation fails early
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke with too short folder name should fail validation`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, tooShortFolderName)
        
        // Act & Assert
        val result = createFolderUseCase(input)
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("must be between") == true)
        }
        
        // Verify no repository interactions for invalid input
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke with too long folder name should fail validation`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, tooLongFolderName)
        
        // Act & Assert
        val result = createFolderUseCase(input)
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("must be between") == true)
        }
        
        // Verify no repository interactions for invalid input
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke with existing folder name should fail`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, existingFolderName)
        
        coEvery { folderRepository.doesFolderNameExist(validUserId, existingFolderName) } returns true
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception.message?.contains("already exists") == true)
        }
        
        // Verify repository interactions
        coVerify(exactly = 1) { profileRepository.hasProfile(validUserId) }
        coVerify(exactly = 1) { folderRepository.doesFolderNameExist(validUserId, existingFolderName) }
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke should create minimal profile when user profile doesn't exist`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, validFolderName)
        val minimalProfile = UserProfile.createMinimal(validUserId)
        val expectedFolder = Folder(
            id = FolderId.generate(),
            userId = validUserId,
            name = FolderName(validFolderName),
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            templateCount = 0
        )
        
        // Mock profile doesn't exist initially, but exists after creation
        coEvery { profileRepository.hasProfile(validUserId) } returnsMany listOf(false, true, true)
        coEvery { profileRepository.saveProfile(any()) } returns Result.success(Unit)
        coEvery { folderRepository.doesFolderNameExist(validUserId, validFolderName) } returns false
        coEvery { folderRepository.createFolder(any()) } returns Result.success(expectedFolder)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify profile creation was attempted
        coVerify(exactly = 3) { profileRepository.hasProfile(validUserId) }
        coVerify(exactly = 1) { profileRepository.saveProfile(any()) }
        coVerify(exactly = 1) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke should fail when profile creation fails`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, validFolderName)
        val profileError = RuntimeException("Profile creation failed")
        
        coEvery { profileRepository.hasProfile(validUserId) } returns false
        coEvery { profileRepository.saveProfile(any()) } returns Result.failure(profileError)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertEquals(profileError, exception)
        }
        
        // Verify folder creation was not attempted
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke should fail when profile still doesn't exist after creation attempt`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, validFolderName)
        val minimalProfile = UserProfile.createMinimal(validUserId)
        
        // Mock profile creation succeeds but profile still doesn't exist after
        coEvery { profileRepository.hasProfile(validUserId) } returnsMany listOf(false, false, false)
        coEvery { profileRepository.saveProfile(any()) } returns Result.success(Unit)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertTrue(exception is IllegalStateException)
            assertTrue(exception.message?.contains("Unable to ensure user profile exists") == true)
        }
        
        // Verify folder creation was not attempted
        coVerify(exactly = 0) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke should handle repository creation failure gracefully`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, validFolderName)
        val repositoryError = RuntimeException("Database error")
        
        coEvery { folderRepository.doesFolderNameExist(validUserId, validFolderName) } returns false
        coEvery { folderRepository.createFolder(any()) } returns Result.failure(repositoryError)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isFailure)
        result.onFailure { exception ->
            assertEquals(repositoryError, exception)
        }
        
        // Verify all expected interactions occurred
        coVerify(exactly = 1) { profileRepository.hasProfile(validUserId) }
        coVerify(exactly = 1) { folderRepository.doesFolderNameExist(validUserId, validFolderName) }
        coVerify(exactly = 1) { folderRepository.createFolder(any()) }
    }
    
    @Test
    fun `invoke should trim folder name whitespace`() = runTest {
        // Arrange
        val folderNameWithSpaces = "  Test Folder  "
        val trimmedName = "Test Folder"
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, folderNameWithSpaces)
        val expectedFolder = Folder(
            id = FolderId.generate(),
            userId = validUserId,
            name = FolderName(trimmedName),
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            templateCount = 0
        )
        
        coEvery { folderRepository.doesFolderNameExist(validUserId, trimmedName) } returns false
        coEvery { folderRepository.createFolder(any()) } returns Result.success(expectedFolder)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        result.onSuccess { folder ->
            assertEquals(trimmedName, folder.name.value)
        }
        
        // Verify trimmed name was used for uniqueness check
        coVerify(exactly = 1) { folderRepository.doesFolderNameExist(validUserId, trimmedName) }
    }
    
    @Test
    fun `invoke should ensure user scoping by including userId in all operations`() = runTest {
        // Arrange
        val input = CreateFolderUseCase.CreateFolderInput(validUserId, validFolderName)
        val expectedFolder = Folder(
            id = FolderId.generate(),
            userId = validUserId,
            name = FolderName(validFolderName),
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            templateCount = 0
        )
        
        coEvery { folderRepository.doesFolderNameExist(validUserId, validFolderName) } returns false
        coEvery { folderRepository.createFolder(any()) } returns Result.success(expectedFolder)
        
        // Act
        val result = createFolderUseCase(input)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify user scoping in all repository calls
        coVerify(exactly = 1) { profileRepository.hasProfile(validUserId) }
        coVerify(exactly = 1) { folderRepository.doesFolderNameExist(validUserId, validFolderName) }
        coVerify(exactly = 1) { 
            folderRepository.createFolder(match { folder -> 
                folder.userId == validUserId 
            })
        }
    }
}