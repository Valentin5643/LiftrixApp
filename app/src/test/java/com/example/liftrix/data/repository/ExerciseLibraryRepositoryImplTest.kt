package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.data.mapper.ExerciseLibraryMapper
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseLibraryRepositoryImplTest {

    private lateinit var repository: ExerciseLibraryRepositoryImpl
    private val dao: ExerciseLibraryDao = mockk()
    private val usageHistoryDao: ExerciseUsageHistoryDao = mockk()
    private val mapper: ExerciseLibraryMapper = mockk()

    private val sampleExerciseEntities = listOf(
        ExerciseLibraryEntity(
            id = "1",
            name = "Bench Press",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BARBELL,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 3,
            instructions = "Lie on bench and press barbell",
            isCompound = true,
            searchableTerms = listOf("chest", "press", "barbell")
        ),
        ExerciseLibraryEntity(
            id = "2",
            name = "Dumbbell Curl",
            primaryMuscleGroup = ExerciseCategory.BICEPS,
            equipment = Equipment.DUMBBELLS,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "Pull",
            difficultyLevel = 2,
            instructions = "Curl dumbbells with controlled motion",
            isCompound = false,
            searchableTerms = listOf("biceps", "curl", "dumbbell")
        ),
        ExerciseLibraryEntity(
            id = "3",
            name = "Push-ups",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 1,
            instructions = "Perform push-ups with proper form",
            isCompound = true,
            searchableTerms = listOf("chest", "bodyweight", "push")
        )
    )

    private val sampleExercises = listOf(
        ExerciseLibrary(
            id = "1",
            name = "Bench Press",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BARBELL,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 3,
            instructions = "Lie on bench and press barbell",
            isCompound = true,
            searchableTerms = listOf("chest", "press", "barbell")
        ),
        ExerciseLibrary(
            id = "2",
            name = "Dumbbell Curl",
            primaryMuscleGroup = ExerciseCategory.BICEPS,
            equipment = Equipment.DUMBBELLS,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "Pull",
            difficultyLevel = 2,
            instructions = "Curl dumbbells with controlled motion",
            isCompound = false,
            searchableTerms = listOf("biceps", "curl", "dumbbell")
        ),
        ExerciseLibrary(
            id = "3",
            name = "Push-ups",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 1,
            instructions = "Perform push-ups with proper form",
            isCompound = true,
            searchableTerms = listOf("chest", "bodyweight", "push")
        )
    )

    @Before
    fun setup() {
        repository = ExerciseLibraryRepositoryImpl(dao, usageHistoryDao, mapper)
        
        // Setup common mocks
        every { dao.getAllExercises() } returns flowOf(sampleExerciseEntities)
        every { mapper.toDomainList(sampleExerciseEntities) } returns sampleExercises
    }

    @Test
    fun `searchExercises with equipment filter returns filtered results`() = runTest {
        // Given
        val query = "press"
        val equipment = setOf(Equipment.BARBELL)
        
        // When
        val result = repository.searchExercises(query, equipment, null)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(1, exercises.size)
        assertEquals("Bench Press", exercises[0].name)
        assertEquals(Equipment.BARBELL, exercises[0].equipment)
    }

    @Test
    fun `searchExercises with muscle group filter returns filtered results`() = runTest {
        // Given
        val query = ""
        val muscleGroups = setOf(ExerciseCategory.CHEST)
        
        // When
        val result = repository.searchExercises(query, null, muscleGroups)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(2, exercises.size) // Bench Press and Push-ups
        assertTrue(exercises.all { 
            it.primaryMuscleGroup == ExerciseCategory.CHEST || 
            it.secondaryMuscleGroups.contains(ExerciseCategory.CHEST) 
        })
    }

    @Test
    fun `searchExercises with both equipment and muscle group filters returns intersection`() = runTest {
        // Given
        val query = ""
        val equipment = setOf(Equipment.BODYWEIGHT_ONLY)
        val muscleGroups = setOf(ExerciseCategory.CHEST)
        
        // When
        val result = repository.searchExercises(query, equipment, muscleGroups)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(1, exercises.size)
        assertEquals("Push-ups", exercises[0].name)
    }

    @Test
    fun `searchExercises with fuzzy search returns scored and sorted results`() = runTest {
        // Given
        val query = "curl"
        
        // When
        val result = repository.searchExercises(query, null, null)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(1, exercises.size)
        assertEquals("Dumbbell Curl", exercises[0].name)
    }

    @Test
    fun `searchExercises with empty query returns all filtered exercises`() = runTest {
        // Given
        val query = ""
        val equipment = setOf(Equipment.DUMBBELLS, Equipment.BARBELL)
        
        // When
        val result = repository.searchExercises(query, equipment, null)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(2, exercises.size) // Bench Press and Dumbbell Curl
        assertTrue(exercises.all { it.equipment in equipment })
    }

    @Test
    fun `getRecentExercises returns exercises in usage order`() = runTest {
        // Given
        val userId = "user123"
        val limit = 5
        val recentExerciseIds = listOf("2", "1") // Dumbbell Curl, then Bench Press
        
        coEvery { usageHistoryDao.getRecentExerciseIds(userId, limit) } returns recentExerciseIds
        
        // When
        val result = repository.getRecentExercises(userId, limit)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(2, exercises.size)
        assertEquals("Dumbbell Curl", exercises[0].name) // Most recent first
        assertEquals("Bench Press", exercises[1].name)
        
        coVerify { usageHistoryDao.getRecentExerciseIds(userId, limit) }
    }

    @Test
    fun `getRecentExercises handles empty usage history`() = runTest {
        // Given
        val userId = "user123"
        val limit = 5
        
        coEvery { usageHistoryDao.getRecentExerciseIds(userId, limit) } returns emptyList()
        
        // When
        val result = repository.getRecentExercises(userId, limit)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertTrue(exercises.isEmpty())
    }

    @Test
    fun `getRecentExercises handles unknown exercise IDs gracefully`() = runTest {
        // Given
        val userId = "user123"
        val limit = 5
        val recentExerciseIds = listOf("999", "1") // Unknown ID and valid ID
        
        coEvery { usageHistoryDao.getRecentExerciseIds(userId, limit) } returns recentExerciseIds
        
        // When
        val result = repository.getRecentExercises(userId, limit)
        
        // Then
        assertTrue(result.isSuccess)
        val exercises = result.getOrNull()!!
        assertEquals(1, exercises.size) // Only valid exercise returned
        assertEquals("Bench Press", exercises[0].name)
    }

    @Test
    fun `searchExercises handles dao exception gracefully`() = runTest {
        // Given
        val query = "test"
        every { dao.getAllExercises() } throws RuntimeException("Database error")
        
        // When
        val result = repository.searchExercises(query, null, null)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }

    @Test
    fun `getRecentExercises handles dao exception gracefully`() = runTest {
        // Given
        val userId = "user123"
        coEvery { usageHistoryDao.getRecentExerciseIds(userId, 10) } throws RuntimeException("Database error")
        
        // When
        val result = repository.getRecentExercises(userId, 10)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
} 