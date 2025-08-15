package com.example.liftrix.data.repository.folder

import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.FolderDao
import com.example.liftrix.data.local.dao.WorkoutTemplateDao
import com.example.liftrix.data.local.dao.UserProfileDao
import com.example.liftrix.data.local.entity.FolderEntity
import com.example.liftrix.data.mapper.FolderMapper
import com.example.liftrix.data.repository.FolderRepositoryImpl
import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.repository.FolderRepository
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Integration tests for FolderRepositoryImpl
 * 
 * Tests the integration between the repository implementation, DAO layer,
 * and data mapping. Focuses on user scoping validation and data integrity.
 */
class FolderRepositoryImplIntegrationTest {
    
    private lateinit var database: LiftrixDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var workoutTemplateDao: WorkoutTemplateDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var folderMapper: FolderMapper
    private lateinit var folderRepository: FolderRepository
    
    // Test data constants
    private val validUserId = "user123"
    private val differentUserId = "user456"
    private val validFolderId = FolderId("folder789")
    private val validFolderName = "Test Folder"
    private val existingFolderName = "Existing Folder"
    
    private val currentTime = Instant.now()
    
    private val testFolderEntity = FolderEntity(
        id = validFolderId.value,
        userId = validUserId,
        name = validFolderName,
        createdAt = currentTime,
        updatedAt = currentTime,
        templateCount = 0,
        isSynced = false,
        syncVersion = 1
    )
    
    private val testFolder = Folder(
        id = validFolderId,
        userId = validUserId,
        name = FolderName(validFolderName),
        createdAt = currentTime,
        updatedAt = currentTime,
        templateCount = 0
    )
    
    @Before
    fun setup() {
        database = mockk()
        folderDao = mockk()
        workoutTemplateDao = mockk()
        userProfileDao = mockk()
        folderMapper = mockk()
        folderRepository = FolderRepositoryImpl(database, folderDao, workoutTemplateDao, userProfileDao, folderMapper)
        
        // Setup default mapper behavior
        every { folderMapper.toNewEntity(any()) } answers { 
            val folder = firstArg<Folder>()
            testFolderEntity.copy(
                id = folder.id.value,
                userId = folder.userId,
                name = folder.name.value,
                templateCount = folder.templateCount
            )
        }
        
        every { folderMapper.toDomain(any()) } answers {
            val entity = firstArg<FolderEntity>()
            testFolder.copy(
                id = FolderId(entity.id),
                userId = entity.userId,
                name = FolderName(entity.name),
                templateCount = entity.templateCount
            )
        }
        
        every { folderMapper.toDomainList(any()) } answers {
            val entities = firstArg<List<FolderEntity>>()
            entities.map { entity ->
                testFolder.copy(
                    id = FolderId(entity.id),
                    userId = entity.userId,
                    name = FolderName(entity.name),
                    templateCount = entity.templateCount
                )
            }
        }
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `getAllFoldersForUser should return user-scoped folders`() = runTest {
        // Arrange
        val userFolders = listOf(testFolderEntity, testFolderEntity.copy(id = "folder2", name = "Folder 2"))
        
        every { folderDao.getFoldersByUserId(validUserId) } returns flowOf(userFolders)
        
        // Act
        val result = folderRepository.getAllFoldersForUser(validUserId).first()
        
        // Assert
        assertEquals(2, result.size)
        assertTrue(result.all { it.userId == validUserId })
        
        // Verify user scoping in DAO call
        verify(exactly = 1) { folderDao.getFoldersByUserId(validUserId) }
    }
    
    @Test
    fun `getFolderById with userId should enforce user scoping`() = runTest {
        // Arrange
        coEvery { folderDao.getFolderById(validFolderId.value, validUserId) } returns testFolderEntity
        coEvery { folderDao.getFolderById(validFolderId.value, differentUserId) } returns null
        
        // Act - Same folder ID with different users
        val resultValidUser = folderRepository.getFolderById(validFolderId, validUserId).first()
        val resultDifferentUser = folderRepository.getFolderById(validFolderId, differentUserId).first()
        
        // Assert
        assertNotNull(resultValidUser)
        assertNull(resultDifferentUser)
        assertEquals(validUserId, resultValidUser?.userId)
        
        // Verify user scoping in DAO calls
        coVerify(exactly = 1) { folderDao.getFolderById(validFolderId.value, validUserId) }
        coVerify(exactly = 1) { folderDao.getFolderById(validFolderId.value, differentUserId) }
    }
    
    @Test
    fun `createFolder should persist folder with proper mapping`() = runTest {
        // Arrange
        coEvery { folderDao.insertFolder(any()) } returns 1L
        coEvery { folderDao.doesFolderNameExist(validUserId, validFolderName) } returns false
        
        // Act
        val result = folderRepository.createFolder(testFolder)
        
        // Assert
        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { createdFolder ->
                assertEquals(testFolder.id, createdFolder.id)
                assertEquals(testFolder.userId, createdFolder.userId)
                assertEquals(testFolder.name, createdFolder.name)
            },
            onFailure = { fail("Expected success but got failure: $it") }
        )
        
        // Verify DAO operations
        coVerify(exactly = 1) { folderDao.insertFolder(any()) }
        verify(exactly = 1) { folderMapper.toNewEntity(testFolder) }
    }
    
    @Test
    fun `createFolder should handle DAO insertion failures`() = runTest {
        // Arrange
        val daoError = RuntimeException("Database insertion failed")
        coEvery { folderDao.doesFolderNameExist(validUserId, validFolderName) } returns false
        coEvery { folderDao.insertFolder(any()) } throws daoError
        
        // Act
        val result = folderRepository.createFolder(testFolder)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { exception ->
                assertEquals(daoError, exception)
            }
        )
        
        // Verify operations were attempted
        coVerify(exactly = 1) { folderDao.doesFolderNameExist(validUserId, validFolderName) }
        coVerify(exactly = 1) { folderDao.insertFolder(any()) }
    }
    
    @Test
    fun `updateFolder should modify existing folder with user validation`() = runTest {
        // Arrange
        val updatedFolder = testFolder.copy(name = FolderName("Updated Name"))
        val updatedEntity = testFolderEntity.copy(name = "Updated Name")
        
        coEvery { folderDao.getFolderById(validFolderId.value, validUserId) } returns testFolderEntity
        coEvery { folderDao.doesFolderNameExist(validUserId, "Updated Name") } returns false
        coEvery { folderDao.updateFolder(any()) } returns 1
        coEvery { folderDao.getFolderById(validFolderId.value) } returns updatedEntity
        every { folderMapper.updateEntity(any(), any()) } returns updatedEntity
        
        // Act
        val result = folderRepository.updateFolder(updatedFolder)
        
        // Assert
        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { folder ->
                assertEquals("Updated Name", folder.name.value)
                assertEquals(validUserId, folder.userId)
            },
            onFailure = { fail("Expected success but got failure: $it") }
        )
        
        // Verify DAO operations
        coVerify(exactly = 1) { folderDao.getFolderById(validFolderId.value, validUserId) }
        coVerify(exactly = 1) { folderDao.doesFolderNameExist(validUserId, "Updated Name") }
        coVerify(exactly = 1) { folderDao.updateFolder(any()) }
        coVerify(exactly = 1) { folderDao.getFolderById(validFolderId.value) }
        verify(exactly = 1) { folderMapper.updateEntity(any(), updatedFolder) }
    }
    
    @Test
    fun `deleteFolder should enforce user scoping during deletion`() = runTest {
        // Arrange
        coEvery { folderDao.deleteFolder(validFolderId.value, validUserId) } returns 1
        coEvery { folderDao.deleteFolder(validFolderId.value, differentUserId) } returns 0
        
        // Act
        val resultValidUser = folderRepository.deleteFolder(validFolderId, validUserId)
        val resultDifferentUser = folderRepository.deleteFolder(validFolderId, differentUserId)
        
        // Assert
        assertTrue(resultValidUser.isSuccess)
        assertTrue(resultDifferentUser.isFailure) // Should fail when no rows affected
        
        // Verify user scoping in deletions
        coVerify(exactly = 1) { folderDao.deleteFolder(validFolderId.value, validUserId) }
        coVerify(exactly = 1) { folderDao.deleteFolder(validFolderId.value, differentUserId) }
    }
    
    @Test
    fun `doesFolderNameExist should check name uniqueness within user scope`() = runTest {
        // Arrange
        coEvery { folderDao.doesFolderNameExist(validUserId, validFolderName) } returns true
        coEvery { folderDao.doesFolderNameExist(validUserId, "New Name") } returns false
        coEvery { folderDao.doesFolderNameExist(differentUserId, validFolderName) } returns false
        
        // Act
        val existsForValidUser = folderRepository.doesFolderNameExist(validUserId, validFolderName)
        val newNameForValidUser = folderRepository.doesFolderNameExist(validUserId, "New Name")
        val sameNameDifferentUser = folderRepository.doesFolderNameExist(differentUserId, validFolderName)
        
        // Assert
        assertTrue(existsForValidUser)  // Name exists for this user
        assertFalse(newNameForValidUser) // New name doesn't exist for this user
        assertFalse(sameNameDifferentUser) // Same name doesn't exist for different user (user scoping)
        
        // Verify user scoping in name checks
        coVerify(exactly = 1) { folderDao.doesFolderNameExist(validUserId, validFolderName) }
        coVerify(exactly = 1) { folderDao.doesFolderNameExist(validUserId, "New Name") }
        coVerify(exactly = 1) { folderDao.doesFolderNameExist(differentUserId, validFolderName) }
    }
    
    @Test
    fun `getFolderCount should return count scoped to specific user`() = runTest {
        // Arrange
        coEvery { folderDao.getFolderCount(validUserId) } returns 5
        coEvery { folderDao.getFolderCount(differentUserId) } returns 3
        
        // Act
        val countValidUser = folderRepository.getFolderCount(validUserId)
        val countDifferentUser = folderRepository.getFolderCount(differentUserId)
        
        // Assert
        assertEquals(5, countValidUser)
        assertEquals(3, countDifferentUser)
        
        // Verify user scoping
        coVerify(exactly = 1) { folderDao.getFolderCount(validUserId) }
        coVerify(exactly = 1) { folderDao.getFolderCount(differentUserId) }
    }
    
    @Test
    fun `searchFolders should return user-scoped search results`() = runTest {
        // Arrange
        val searchQuery = "Test"
        val matchingFolders = listOf(testFolderEntity, testFolderEntity.copy(id = "folder2", name = "Test Folder 2"))
        
        every { folderDao.searchFolders(validUserId, searchQuery) } returns flowOf(matchingFolders)
        
        // Act
        val result = folderRepository.searchFolders(validUserId, searchQuery).first()
        
        // Assert
        assertEquals(2, result.size)
        assertTrue(result.all { it.userId == validUserId })
        assertTrue(result.all { it.name.value.contains(searchQuery, ignoreCase = true) })
        
        // Verify user scoping in search
        verify(exactly = 1) { folderDao.searchFolders(validUserId, searchQuery) }
    }
    
    @Test
    fun `getUnsyncedFolders should return unsynced folders for specific user`() = runTest {
        // Arrange
        val unsyncedFolders = listOf(
            testFolderEntity.copy(isSynced = false),
            testFolderEntity.copy(id = "folder2", name = "Folder 2", isSynced = false)
        )
        
        coEvery { folderDao.getUnsyncedFolders(validUserId) } returns unsyncedFolders
        
        // Act
        val result = folderRepository.getUnsyncedFolders(validUserId)
        
        // Assert
        assertEquals(2, result.size)
        assertTrue(result.all { it.userId == validUserId })
        
        // Verify user scoping
        coVerify(exactly = 1) { folderDao.getUnsyncedFolders(validUserId) }
    }
    
    @Test
    fun `moveTemplateToFolder should enforce user scoping during move operation`() = runTest {
        // Arrange
        val templateId = "template123"
        val targetFolderId = FolderId("targetFolder")
        
        // Mock that the target folder exists for the user
        coEvery { folderDao.getFolderById(targetFolderId.value, validUserId) } returns testFolderEntity
        coEvery { folderDao.getFolderById(targetFolderId.value, differentUserId) } returns null
        
        // Mock template operations for valid user
        coEvery { workoutTemplateDao.updateFolderId(templateId, targetFolderId.value, validUserId) } returns 1
        coEvery { workoutTemplateDao.getTemplateById(templateId, validUserId) } returns mockk {
            every { folderId } returns "oldFolder"
        }
        
        // Mock template operations for different user  
        coEvery { workoutTemplateDao.updateFolderId(templateId, targetFolderId.value, differentUserId) } returns 0
        
        // Mock folder count operations
        coEvery { folderDao.decrementTemplateCount(any(), any()) } returns 1
        coEvery { folderDao.incrementTemplateCount(any(), any()) } returns 1
        
        // Act
        val resultValidUser = folderRepository.moveTemplateToFolder(templateId, targetFolderId, validUserId)
        val resultDifferentUser = folderRepository.moveTemplateToFolder(templateId, targetFolderId, differentUserId)
        
        // Assert
        assertTrue(resultValidUser.isSuccess)
        assertTrue(resultDifferentUser.isFailure) // Should fail when folder doesn't exist for user
        
        // Verify user scoping
        coVerify(exactly = 1) { folderDao.getFolderById(targetFolderId.value, validUserId) }
        coVerify(exactly = 1) { folderDao.getFolderById(targetFolderId.value, differentUserId) }
    }
    
    @Test
    fun `getOrCreateDefaultFolder should handle user-specific default folders`() = runTest {
        // Arrange
        val defaultFolderId = "uncategorized_$validUserId"
        val defaultFolderEntity = testFolderEntity.copy(
            id = defaultFolderId,
            name = "Uncategorized"
        )
        
        // First call - folder doesn't exist, second call - folder exists after creation
        coEvery { folderDao.getFoldersByUserId(validUserId) } returns flowOf(emptyList())
        coEvery { userProfileDao.getProfileForUserSuspend(validUserId) } returns mockk() // User profile exists
        coEvery { folderDao.insertFolder(any()) } returns 1L
        
        // Act
        val result = folderRepository.getOrCreateDefaultFolder(validUserId)
        
        // Assert
        assertTrue(result.isSuccess)
        result.fold(
            onSuccess = { folder ->
                assertEquals("Uncategorized", folder.name.value)
                assertEquals(validUserId, folder.userId)
            },
            onFailure = { fail("Expected success but got failure: $it") }
        )
        
        // Verify default folder creation flow
        coVerify(exactly = 1) { folderDao.getFoldersByUserId(validUserId) }
        coVerify(exactly = 1) { folderDao.insertFolder(any()) }
    }
    
    @Test
    fun `markFoldersAsSynced should update sync status for specified folders`() = runTest {
        // Arrange
        val folderIds = listOf(FolderId("folder1"), FolderId("folder2"))
        
        coEvery { folderDao.markFoldersAsSynced(any()) } returns 2
        
        // Act
        val result = folderRepository.markFoldersAsSynced(folderIds)
        
        // Assert
        assertTrue(result.isSuccess)
        
        // Verify sync operation
        coVerify(exactly = 1) { folderDao.markFoldersAsSynced(listOf("folder1", "folder2")) }
    }
    
    @Test
    fun `repository should handle mapper exceptions gracefully`() = runTest {
        // Arrange
        val mapperError = RuntimeException("Mapping failed")
        every { folderMapper.toNewEntity(any()) } throws mapperError
        coEvery { folderDao.doesFolderNameExist(validUserId, validFolderName) } returns false
        
        // Act
        val result = folderRepository.createFolder(testFolder)
        
        // Assert
        assertTrue(result.isFailure)
        result.fold(
            onSuccess = { fail("Expected failure but got success") },
            onFailure = { exception ->
                assertEquals(mapperError, exception)
            }
        )
        
        // Verify mapping was attempted
        verify(exactly = 1) { folderMapper.toNewEntity(testFolder) }
        coVerify(exactly = 0) { folderDao.insertFolder(any()) }
    }
    
    @Test
    fun `repository should ensure all operations maintain data consistency`() = runTest {
        // This test verifies that the repository maintains proper data flow
        // from domain models through entities and back to domain models
        
        // Arrange
        val originalFolder = testFolder
        val entityAfterMapping = testFolderEntity
        val updatedFolder = originalFolder.copy(name = FolderName("Updated"))
        val updatedEntity = entityAfterMapping.copy(name = "Updated")
        
        coEvery { folderDao.insertFolder(any()) } returns 1L
        coEvery { folderDao.updateFolder(any()) } returns 1
        coEvery { folderDao.getFolderById(validFolderId.value, validUserId) } returns testFolderEntity
        coEvery { folderDao.doesFolderNameExist(validUserId, validFolderName) } returns false
        coEvery { folderDao.doesFolderNameExist(validUserId, "Updated") } returns false
        
        // Mock mapper methods
        every { folderMapper.updateEntity(any(), any()) } returns updatedEntity
        
        // Act - Create then update
        val createResult = folderRepository.createFolder(originalFolder)
        val updateResult = folderRepository.updateFolder(updatedFolder)
        
        // Assert
        assertTrue(createResult.isSuccess)
        assertTrue(updateResult.isSuccess)
        
        updateResult.fold(
            onSuccess = { folder ->
                assertEquals("Updated", folder.name.value)
                assertEquals(originalFolder.id, folder.id)
                assertEquals(originalFolder.userId, folder.userId)
            },
            onFailure = { fail("Expected success but got failure: $it") }
        )
        
        // Verify proper mapping in both directions
        verify(exactly = 1) { folderMapper.toNewEntity(originalFolder) }
        verify(exactly = 1) { folderMapper.updateEntity(any(), updatedFolder) }
    }
}