package com.example.liftrix.data.local.seed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ExerciseLibrarySeedData
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseLibrarySeedDataTest {
    
    private lateinit var context: Context
    private lateinit var gson: Gson
    private lateinit var seedData: ExerciseLibrarySeedData
    private lateinit var mockDatabase: LiftrixDatabase
    private lateinit var mockDao: ExerciseLibraryDao
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        gson = Gson()
        mockDatabase = mockk()
        mockDao = mockk()
        
        coEvery { mockDatabase.exerciseLibraryDao() } returns mockDao
        
        seedData = ExerciseLibrarySeedData(context, gson)
    }
    
    @Test
    fun `getChestExercises returns only chest exercises`() = runTest {
        // When
        val chestExercises = seedData.getChestExercises()
        
        // Then
        assertTrue(chestExercises.isNotEmpty())
        chestExercises.forEach { exercise ->
            assertEquals(ExerciseCategory.CHEST, exercise.primaryMuscleGroup)
        }
    }
    
    @Test
    fun `getVariationsByMovement returns exercises with same movement pattern`() = runTest {
        // When
        val pressExercises = seedData.getVariationsByMovement("press")
        
        // Then
        assertTrue(pressExercises.isNotEmpty())
        pressExercises.forEach { exercise ->
            assertEquals("press", exercise.movementPattern)
        }
        
        // Should have variations with different equipment
        val equipmentTypes = pressExercises.map { it.equipment }.toSet()
        assertTrue(equipmentTypes.size > 1)
    }
    
    @Test
    fun `getExercisesByEquipment returns only exercises for specified equipment`() = runTest {
        // When
        val dumbbellExercises = seedData.getExercisesByEquipment(Equipment.DUMBBELLS)
        
        // Then
        assertTrue(dumbbellExercises.isNotEmpty())
        dumbbellExercises.forEach { exercise ->
            assertEquals(Equipment.DUMBBELLS, exercise.equipment)
        }
    }
    
    @Test
    fun `getExercisesByMuscleGroup returns exercises for specified muscle group`() = runTest {
        // When
        val backExercises = seedData.getExercisesByMuscleGroup(ExerciseCategory.BACK)
        
        // Then
        assertTrue(backExercises.isNotEmpty())
        backExercises.forEach { exercise ->
            assertEquals(ExerciseCategory.BACK, exercise.primaryMuscleGroup)
        }
    }
    
    @Test
    fun `getCompoundExercises returns only compound movements`() = runTest {
        // When
        val compoundExercises = seedData.getCompoundExercises()
        
        // Then
        assertTrue(compoundExercises.isNotEmpty())
        compoundExercises.forEach { exercise ->
            assertTrue(exercise.isCompound)
        }
    }
    
    @Test
    fun `getExercisesByDifficulty returns exercises within difficulty range`() = runTest {
        // Given
        val minLevel = 3
        val maxLevel = 6
        
        // When
        val exercises = seedData.getExercisesByDifficulty(minLevel, maxLevel)
        
        // Then
        assertTrue(exercises.isNotEmpty())
        exercises.forEach { exercise ->
            assertTrue(exercise.difficultyLevel in minLevel..maxLevel)
        }
    }
    
    @Test
    fun `populateExerciseLibraryIfNeeded populates when empty`() = runTest {
        // Given
        coEvery { mockDao.getExerciseCount() } returns 0
        coEvery { mockDao.insertExercises(any()) } returns listOf()
        
        // When
        seedData.populateExerciseLibraryIfNeeded(mockDatabase)
        
        // Then
        coVerify { mockDao.insertExercises(any()) }
    }
    
    @Test
    fun `populateExerciseLibraryIfNeeded skips when already populated`() = runTest {
        // Given
        coEvery { mockDao.getExerciseCount() } returns 50
        
        // When
        seedData.populateExerciseLibraryIfNeeded(mockDatabase)
        
        // Then
        coVerify(exactly = 0) { mockDao.insertExercises(any()) }
    }
    
    @Test
    fun `exercise library covers all muscle groups`() = runTest {
        // When
        val allExercises = seedData.getExercisesByDifficulty(1, 10) // Get all exercises
        
        // Then
        val muscleGroups = allExercises.map { it.primaryMuscleGroup }.toSet()
        
        // Should have exercises for major muscle groups
        assertTrue(muscleGroups.contains(ExerciseCategory.CHEST))
        assertTrue(muscleGroups.contains(ExerciseCategory.BACK))
        assertTrue(muscleGroups.contains(ExerciseCategory.SHOULDERS))
        assertTrue(muscleGroups.contains(ExerciseCategory.ARMS))
        assertTrue(muscleGroups.contains(ExerciseCategory.LEGS))
        assertTrue(muscleGroups.contains(ExerciseCategory.CORE))
        assertTrue(muscleGroups.contains(ExerciseCategory.CARDIO))
        
        // Should have at least 50 exercises total
        assertTrue(allExercises.size >= 50)
    }
    
    @Test
    fun `exercise library includes bodyweight options`() = runTest {
        // When
        val bodyweightExercises = seedData.getExercisesByEquipment(Equipment.BODYWEIGHT_ONLY)
        
        // Then
        assertTrue(bodyweightExercises.isNotEmpty())
        
        // Should cover multiple muscle groups
        val muscleGroups = bodyweightExercises.map { it.primaryMuscleGroup }.toSet()
        assertTrue(muscleGroups.size >= 3) // At least 3 different muscle groups
    }
    
    @Test
    fun `exercise library includes variations for common movements`() = runTest {
        // When
        val pressVariations = seedData.getVariationsByMovement("press")
        val rowVariations = seedData.getVariationsByMovement("row")
        val squatVariations = seedData.getVariationsByMovement("squat")
        
        // Then
        assertTrue(pressVariations.size >= 3) // Multiple press variations
        assertTrue(rowVariations.size >= 2) // Multiple row variations  
        assertTrue(squatVariations.size >= 2) // Multiple squat variations
    }
    
    @Test
    fun `all exercises have valid search terms`() = runTest {
        // When
        val allExercises = seedData.getExercisesByDifficulty(1, 10)
        
        // Then
        allExercises.forEach { exercise ->
            assertFalse(exercise.searchableTerms.isEmpty())
            assertTrue(exercise.searchableTerms.all { it.isNotBlank() })
        }
    }
    
    @Test
    fun `difficulty levels are within valid range`() = runTest {
        // When
        val allExercises = seedData.getExercisesByDifficulty(1, 10)
        
        // Then
        allExercises.forEach { exercise ->
            assertTrue(exercise.difficultyLevel in 1..10)
        }
    }
} 