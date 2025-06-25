package com.example.liftrix.domain.usecase.exercise

import app.cash.turbine.test
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SearchExercisesUseCaseTest {
    
    private lateinit var searchExercisesUseCase: SearchExercisesUseCase
    private lateinit var exerciseLibraryRepository: ExerciseLibraryRepository
    private lateinit var customExerciseRepository: CustomExerciseRepository
    private lateinit var authRepository: AuthRepository
    
    // Test data
    private val testUserId = "test-user-123"
    private val lateralRaiseDumbbell = ExerciseLibrary(
        id = "shoulders-lateral-raise-dumbbell",
        name = "Lateral Raise",
        primaryMuscleGroup = ExerciseCategory.SHOULDERS,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "raise",
        difficultyLevel = 2,
        instructions = "Raise arms to sides until parallel to floor",
        isCompound = false,
        searchableTerms = listOf("lateral raise", "side raise", "delt raise")
    )
    
    private val lateralRaiseBands = ExerciseLibrary(
        id = "shoulders-lateral-raise-bands",
        name = "Band Lateral Raise",
        primaryMuscleGroup = ExerciseCategory.SHOULDERS,
        equipment = Equipment.RESISTANCE_BANDS,
        secondaryMuscleGroups = emptyList(),
        movementPattern = "raise",
        difficultyLevel = 2,
        instructions = "Step on band, raise handles to sides",
        isCompound = false,
        searchableTerms = listOf("band lateral", "resistance band raise", "band side raise")
    )
    
    private val benchPress = ExerciseLibrary(
        id = "chest-bench-press-barbell",
        name = "Bench Press",
        primaryMuscleGroup = ExerciseCategory.CHEST,
        equipment = Equipment.BARBELL,
        secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.ARMS),
        movementPattern = "press",
        difficultyLevel = 3,
        instructions = "Press barbell from chest to arms extended",
        isCompound = true,
        searchableTerms = listOf("bench press", "barbell press", "chest press")
    )
    
    private val customLateralRaise = CustomExercise(
        id = CustomExerciseId("custom-lateral-raise-123"),
        userId = testUserId,
        name = "My Custom Lateral Raise",
        primaryMuscle = ExerciseCategory.SHOULDERS,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscles = emptySet(),
        difficulty = 3,
        notes = "Custom variation with raise movement",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
    
    @Before
    fun setup() {
        exerciseLibraryRepository = mockk()
        customExerciseRepository = mockk()
        authRepository = mockk()
        
        searchExercisesUseCase = SearchExercisesUseCase(
            exerciseLibraryRepository = exerciseLibraryRepository,
            customExerciseRepository = customExerciseRepository,
            authRepository = authRepository
        )
        
        // Default mock behavior
        every { authRepository.getCurrentUserId() } returns testUserId
    }
    
    @Test
    fun `search with lateral raises returns dumbbell and band variations`() = runTest {
        // Given
        val query = "lateral raises"
        val userEquipment = setOf(Equipment.DUMBBELLS, Equipment.RESISTANCE_BANDS)
        val libraryExercises = listOf(lateralRaiseDumbbell, lateralRaiseBands, benchPress)
        val customExercises = listOf(customLateralRaise)
        
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(libraryExercises)
        every { customExerciseRepository.searchCustomExercises(testUserId, query) } returns flowOf(customExercises)
        
        // When
        searchExercisesUseCase.search(query, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(3, result.size)
            
            // Should contain both dumbbell and band variations
            val dumbbellExercise = result.find { it.equipment == Equipment.DUMBBELLS && it.name.contains("Lateral") }
            val bandExercise = result.find { it.equipment == Equipment.RESISTANCE_BANDS }
            val customExercise = result.find { it.name.contains("Custom") }
            
            assertTrue("Should contain dumbbell lateral raise", dumbbellExercise != null)
            assertTrue("Should contain band lateral raise", bandExercise != null)
            assertTrue("Should contain custom exercise", customExercise != null)
            
            // Results should be sorted by match score (descending) then name
            assertTrue("Results should be sorted by relevance", result[0].calculateMatchScore(query) >= result[1].calculateMatchScore(query))
        }
    }
    
    @Test
    fun `search filters results by user equipment`() = runTest {
        // Given
        val query = "lateral raise"
        val userEquipment = setOf(Equipment.DUMBBELLS) // Only dumbbells
        val libraryExercises = listOf(lateralRaiseDumbbell, lateralRaiseBands)
        val customExercises = emptyList<CustomExercise>()
        
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(libraryExercises)
        every { customExerciseRepository.searchCustomExercises(testUserId, query) } returns flowOf(customExercises)
        
        // When
        searchExercisesUseCase.search(query, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(1, result.size)
            assertEquals(Equipment.DUMBBELLS, result[0].equipment)
            assertTrue("Should only return dumbbell exercise", result[0].name.contains("Lateral Raise"))
        }
    }
    
    @Test
    fun `search with empty query returns all exercises filtered by equipment`() = runTest {
        // Given
        val query = ""
        val userEquipment = setOf(Equipment.DUMBBELLS, Equipment.BARBELL)
        val libraryExercises = listOf(lateralRaiseDumbbell, benchPress, lateralRaiseBands)
        val customExercises = listOf(customLateralRaise)
        
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(libraryExercises)
        every { customExerciseRepository.searchCustomExercises(testUserId, query) } returns flowOf(customExercises)
        
        // When
        searchExercisesUseCase.search(query, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(3, result.size) // Should include dumbbell lateral raise, bench press, and custom exercise
            result.forEach { exercise ->
                assertTrue("All results should match user equipment", userEquipment.contains(exercise.equipment))
            }
        }
    }
    
    @Test
    fun `search handles typos and partial matches correctly`() = runTest {
        // Given
        val query = "lateral" // Partial match
        val userEquipment = setOf(Equipment.DUMBBELLS, Equipment.RESISTANCE_BANDS)
        val libraryExercises = listOf(lateralRaiseDumbbell, lateralRaiseBands)
        val customExercises = emptyList<CustomExercise>()
        
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(libraryExercises)
        every { customExerciseRepository.searchCustomExercises(testUserId, query) } returns flowOf(customExercises)
        
        // When
        searchExercisesUseCase.search(query, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(2, result.size)
            result.forEach { exercise ->
                assertTrue("Exercise name should contain 'lateral'", exercise.name.lowercase().contains("lateral"))
            }
        }
    }
    
    @Test
    fun `searchWithVariations groups exercises by movement pattern`() = runTest {
        // Given
        val query = "raise"
        val userEquipment = setOf(Equipment.DUMBBELLS, Equipment.RESISTANCE_BANDS)
        val libraryExercises = listOf(lateralRaiseDumbbell, lateralRaiseBands)
        val customExercises = listOf(customLateralRaise)
        
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(libraryExercises)
        every { customExerciseRepository.searchCustomExercises(testUserId, query) } returns flowOf(customExercises)
        
        // When
        searchExercisesUseCase.searchWithVariations(query, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(1, result.size) // Should have one group for "raise" movement pattern
            
            val raiseGroup = result[0]
            assertEquals("raise", raiseGroup.movementPattern)
            assertEquals(2, raiseGroup.libraryVariations.size)
            assertEquals(1, raiseGroup.customVariations.size)
            
            // Variations should be sorted by difficulty
            assertTrue("Variations should be sorted by difficulty", 
                raiseGroup.libraryVariations[0].difficultyLevel <= raiseGroup.libraryVariations[1].difficultyLevel)
        }
    }
    
    @Test
    fun `getVariations returns exercise variations for movement pattern`() = runTest {
        // Given
        val baseMovement = "raise"
        val userEquipment = setOf(Equipment.DUMBBELLS, Equipment.RESISTANCE_BANDS)
        val libraryVariations = listOf(lateralRaiseDumbbell, lateralRaiseBands)
        val customExercises = listOf(customLateralRaise)
        
        every { exerciseLibraryRepository.getVariationsByMovement(baseMovement, userEquipment) } returns flowOf(libraryVariations)
        every { customExerciseRepository.getAllCustomExercises(testUserId) } returns flowOf(customExercises)
        
        // When
        searchExercisesUseCase.getVariations(baseMovement, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(1, result.size)
            
            val group = result[0]
            assertEquals("raise", group.movementPattern)
            assertEquals(2, group.libraryVariations.size)
            assertEquals(1, group.customVariations.size)
        }
    }
    
    @Test
    fun `search with unauthenticated user only returns library exercises`() = runTest {
        // Given
        val query = "lateral raise"
        val userEquipment = setOf(Equipment.DUMBBELLS)
        val libraryExercises = listOf(lateralRaiseDumbbell)
        
        every { authRepository.getCurrentUserId() } returns null
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(libraryExercises)
        
        // When
        searchExercisesUseCase.search(query, userEquipment).test {
            val result = awaitItem()
            awaitComplete()
            
            // Then
            assertEquals(1, result.size)
            assertTrue("Should only return library exercises", result[0] is SearchableExercise.LibraryExercise)
        }
    }
    
    @Test
    fun `search performance meets 200ms requirement with large dataset`() = runTest {
        // Given
        val query = "lat"
        val userEquipment = Equipment.values().toSet()
        val largeLibraryDataset = (1..100).map { index ->
            ExerciseLibrary(
                id = "exercise-$index",
                name = "Exercise $index Lateral",
                primaryMuscleGroup = ExerciseCategory.SHOULDERS,
                equipment = Equipment.values()[index % Equipment.values().size],
                secondaryMuscleGroups = emptyList(),
                movementPattern = "raise",
                difficultyLevel = (index % 10) + 1,
                instructions = "Instructions $index",
                isCompound = false,
                searchableTerms = listOf("exercise$index", "lateral$index")
            )
        }
        val customExercises = emptyList<CustomExercise>()
        
        every { exerciseLibraryRepository.searchExercises(query) } returns flowOf(largeLibraryDataset)
        every { customExerciseRepository.searchCustomExercises(testUserId, query) } returns flowOf(customExercises)
        
        // When
        val startTime = System.currentTimeMillis()
        searchExercisesUseCase.search(query, userEquipment).test {
            val result = awaitItem()
            val endTime = System.currentTimeMillis()
            awaitComplete()
            
            // Then
            val responseTime = endTime - startTime
            assertTrue("Search should complete in under 200ms", responseTime < 200)
            assertTrue("Should return matching results", result.isNotEmpty())
        }
    }
} 