package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.CustomExerciseDao
import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.data.mapper.CustomExerciseMapper
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for CustomExerciseRepositoryImpl
 */
class CustomExerciseRepositoryImplTest {
    
    private lateinit var dao: CustomExerciseDao
    private lateinit var mapper: CustomExerciseMapper
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: CustomExerciseRepositoryImpl
    
    private val testUserId = "test-user-123"
    private val testExerciseId = CustomExerciseId.generate()
    private val testEntity = CustomExerciseEntity(
        id = testExerciseId.value,
        userId = testUserId,
        name = "Test Exercise",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS),
        difficulty = 5,
        notes = "Test notes",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        isSynced = false,
        syncVersion = 1
    )
    
    private val testDomainModel = CustomExercise(
        id = testExerciseId,
        userId = testUserId,
        name = "Test Exercise",
        primaryMuscle = ExerciseCategory.CHEST,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscles = setOf(ExerciseCategory.SHOULDERS),
        difficulty = 5,
        notes = "Test notes",
        createdAt = testEntity.createdAt,
        updatedAt = testEntity.updatedAt
    )
    
    @Before
    fun setup() {
        dao = mockk()
        mapper = mockk()
        firestore = mockk(relaxed = true)
        repository = CustomExerciseRepositoryImpl(dao, mapper, firestore)
    }
    
    @Test
    fun `createCustomExercise creates exercise successfully`() = runTest {
        // Given
        every { 
            mapper.createEntity(
                testUserId,
                "Test Exercise",
                ExerciseCategory.CHEST,
                Equipment.DUMBBELLS,
                setOf(ExerciseCategory.SHOULDERS),
                5,
                "Test notes",
                false
            ) 
        } returns testEntity
        
        every { mapper.toDomain(testEntity) } returns testDomainModel
        coEvery { dao.insertCustomExercise(testEntity) } returns 1L
        
        // When
        val result = repository.createCustomExercise(
            userId = testUserId,
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            secondaryMuscles = setOf(ExerciseCategory.SHOULDERS),
            difficulty = 5,
            notes = "Test notes"
        )
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testDomainModel, result.getOrNull())
        
        coVerify { dao.insertCustomExercise(testEntity) }
        verify { mapper.toDomain(testEntity) }
    }
    
    @Test
    fun `createCustomExercise handles database error`() = runTest {
        // Given
        every { 
            mapper.createEntity(any(), any(), any(), any(), any(), any(), any(), any()) 
        } returns testEntity
        
        coEvery { dao.insertCustomExercise(any()) } throws RuntimeException("Database error")
        
        // When
        val result = repository.createCustomExercise(
            userId = testUserId,
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS
        )
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
    
    @Test
    fun `getAllCustomExercises returns mapped domain models`() = runTest {
        // Given
        val entities = listOf(testEntity)
        val domainModels = listOf(testDomainModel)
        
        every { dao.getAllCustomExercisesForUser(testUserId) } returns flowOf(entities)
        every { mapper.toDomainList(entities) } returns domainModels
        
        // When
        val result = repository.getAllCustomExercises(testUserId).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(domainModels, result[0])
        
        verify { dao.getAllCustomExercisesForUser(testUserId) }
        verify { mapper.toDomainList(entities) }
    }
    
    @Test
    fun `getCustomExercisesByMuscleGroup filters correctly`() = runTest {
        // Given
        val entities = listOf(testEntity)
        val domainModels = listOf(testDomainModel)
        
        every { dao.getCustomExercisesByMuscleGroup(testUserId, ExerciseCategory.CHEST) } returns flowOf(entities)
        every { mapper.toDomainList(entities) } returns domainModels
        
        // When
        val result = repository.getCustomExercisesByMuscleGroup(testUserId, ExerciseCategory.CHEST).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(domainModels, result[0])
        
        verify { dao.getCustomExercisesByMuscleGroup(testUserId, ExerciseCategory.CHEST) }
    }
    
    @Test
    fun `getCustomExercisesByEquipment filters correctly`() = runTest {
        // Given
        val entities = listOf(testEntity)
        val domainModels = listOf(testDomainModel)
        
        every { dao.getCustomExercisesByEquipment(testUserId, Equipment.DUMBBELLS) } returns flowOf(entities)
        every { mapper.toDomainList(entities) } returns domainModels
        
        // When
        val result = repository.getCustomExercisesByEquipment(testUserId, Equipment.DUMBBELLS).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(domainModels, result[0])
        
        verify { dao.getCustomExercisesByEquipment(testUserId, Equipment.DUMBBELLS) }
    }
    
    @Test
    fun `searchCustomExercises returns filtered results`() = runTest {
        // Given
        val query = "test"
        val entities = listOf(testEntity)
        val domainModels = listOf(testDomainModel)
        
        every { dao.searchCustomExercises(testUserId, query) } returns flowOf(entities)
        every { mapper.toDomainList(entities) } returns domainModels
        
        // When
        val result = repository.searchCustomExercises(testUserId, query).toList()
        
        // Then
        assertEquals(1, result.size)
        assertEquals(domainModels, result[0])
        
        verify { dao.searchCustomExercises(testUserId, query) }
    }
    
    @Test
    fun `getCustomExercise returns exercise when found`() = runTest {
        // Given
        coEvery { dao.getCustomExerciseById(testExerciseId.value, testUserId) } returns testEntity
        every { mapper.toDomain(testEntity) } returns testDomainModel
        
        // When
        val result = repository.getCustomExercise(testUserId, testExerciseId)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testDomainModel, result.getOrNull())
        
        coVerify { dao.getCustomExerciseById(testExerciseId.value, testUserId) }
        verify { mapper.toDomain(testEntity) }
    }
    
    @Test
    fun `getCustomExercise returns failure when not found`() = runTest {
        // Given
        coEvery { dao.getCustomExerciseById(testExerciseId.value, testUserId) } returns null
        
        // When
        val result = repository.getCustomExercise(testUserId, testExerciseId)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
    
    @Test
    fun `updateCustomExercise updates successfully`() = runTest {
        // Given
        val updatedEntity = testEntity.copy(name = "Updated Exercise")
        val updatedDomain = testDomainModel.copy(name = "Updated Exercise")
        
        coEvery { dao.getCustomExerciseById(testExerciseId.value, testUserId) } returns testEntity
        every { mapper.updateEntity(testEntity, testDomainModel) } returns updatedEntity
        coEvery { dao.updateCustomExercise(updatedEntity) } returns 1
        every { mapper.toDomain(updatedEntity) } returns updatedDomain
        
        // When
        val result = repository.updateCustomExercise(testUserId, testDomainModel)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(updatedDomain, result.getOrNull())
        
        coVerify { dao.updateCustomExercise(updatedEntity) }
        verify { mapper.updateEntity(testEntity, testDomainModel) }
    }
    
    @Test
    fun `updateCustomExercise fails when exercise not found`() = runTest {
        // Given
        coEvery { dao.getCustomExerciseById(testExerciseId.value, testUserId) } returns null
        
        // When
        val result = repository.updateCustomExercise(testUserId, testDomainModel)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
    
    @Test
    fun `deleteCustomExercise deletes successfully`() = runTest {
        // Given
        coEvery { dao.deleteCustomExercise(testExerciseId.value, testUserId) } returns 1
        
        // When
        val result = repository.deleteCustomExercise(testUserId, testExerciseId)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { dao.deleteCustomExercise(testExerciseId.value, testUserId) }
    }
    
    @Test
    fun `deleteCustomExercise fails when exercise not found`() = runTest {
        // Given
        coEvery { dao.deleteCustomExercise(testExerciseId.value, testUserId) } returns 0
        
        // When
        val result = repository.deleteCustomExercise(testUserId, testExerciseId)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }
    
    @Test
    fun `isExerciseNameUnique returns true when name doesn't exist`() = runTest {
        // Given
        coEvery { dao.doesCustomExerciseNameExist(testUserId, "New Exercise") } returns false
        
        // When
        val result = repository.isExerciseNameUnique(testUserId, "New Exercise")
        
        // Then
        assertTrue(result)
        
        coVerify { dao.doesCustomExerciseNameExist(testUserId, "New Exercise") }
    }
    
    @Test
    fun `isExerciseNameUnique returns false when name exists`() = runTest {
        // Given
        coEvery { dao.doesCustomExerciseNameExist(testUserId, "Existing Exercise") } returns true
        
        // When
        val result = repository.isExerciseNameUnique(testUserId, "Existing Exercise")
        
        // Then
        assertFalse(result)
    }
    
    @Test
    fun `isExerciseNameUnique returns true when name exists but belongs to excluded exercise`() = runTest {
        // Given
        val exerciseName = "Test Exercise"
        coEvery { dao.doesCustomExerciseNameExist(testUserId, exerciseName) } returns true
        coEvery { dao.getCustomExerciseById(testExerciseId.value, testUserId) } returns testEntity.copy(name = exerciseName)
        
        // When
        val result = repository.isExerciseNameUnique(testUserId, exerciseName, testExerciseId)
        
        // Then
        assertTrue(result)
    }
    
    @Test
    fun `syncUnsyncedExercises processes all unsynced exercises`() = runTest {
        // Given
        val unsyncedExercises = listOf(testEntity, testEntity.copy(id = "another-id"))
        coEvery { dao.getUnsyncedCustomExercises(testUserId) } returns unsyncedExercises
        
        // When
        val result = repository.syncUnsyncedExercises(testUserId)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { dao.getUnsyncedCustomExercises(testUserId) }
    }
} 