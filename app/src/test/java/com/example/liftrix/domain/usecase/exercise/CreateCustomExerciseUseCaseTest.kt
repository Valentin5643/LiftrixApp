package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for CreateCustomExerciseUseCase
 */
class CreateCustomExerciseUseCaseTest {
    
    private lateinit var customExerciseRepository: CustomExerciseRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var useCase: CreateCustomExerciseUseCase
    
    private val testUserId = "test-user-123"
    private val testExercise = CustomExercise(
        id = CustomExerciseId.generate(),
        userId = testUserId,
        name = "Test Exercise",
        primaryMuscle = ExerciseCategory.CHEST,
        equipment = Equipment.DUMBBELLS,
        secondaryMuscles = setOf(ExerciseCategory.SHOULDERS),
        difficulty = 5,
        notes = "Test notes",
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
    
    @Before
    fun setup() {
        customExerciseRepository = mockk()
        authRepository = mockk()
        useCase = CreateCustomExerciseUseCase(customExerciseRepository, authRepository)
        
        // Default auth setup
        coEvery { authRepository.getCurrentUserId() } returns testUserId
    }
    
    @Test
    fun `invoke creates custom exercise successfully with valid input`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Dumbbell Chest Press",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            secondaryMuscles = setOf(ExerciseCategory.SHOULDERS),
            difficulty = 5,
            notes = "Focus on form"
        )
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Dumbbell Chest Press") } returns true
        coEvery { 
            customExerciseRepository.createCustomExercise(
                testUserId, 
                "Dumbbell Chest Press", 
                ExerciseCategory.CHEST, 
                Equipment.DUMBBELLS,
                setOf(ExerciseCategory.SHOULDERS),
                5,
                "Focus on form"
            ) 
        } returns Result.success(testExercise)
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(testExercise, result.getOrNull())
        
        coVerify { 
            customExerciseRepository.createCustomExercise(
                testUserId,
                "Dumbbell Chest Press",
                ExerciseCategory.CHEST,
                Equipment.DUMBBELLS,
                setOf(ExerciseCategory.SHOULDERS),
                5,
                "Focus on form"
            ) 
        }
    }
    
    @Test
    fun `invoke fails when user not authenticated`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS
        )
        
        coEvery { authRepository.getCurrentUserId() } returns null
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateCustomExerciseException.UserNotAuthenticated)
    }
    
    @Test
    fun `invoke fails when exercise name already exists`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Existing Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS
        )
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Existing Exercise") } returns false
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateCustomExerciseException.InvalidInput)
    }
    
    @Test
    fun `invoke trims whitespace from name and notes`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "  Test Exercise  ",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            notes = "  Some notes  "
        )
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Test Exercise") } returns true
        coEvery { 
            customExerciseRepository.createCustomExercise(
                testUserId, 
                "Test Exercise", 
                ExerciseCategory.CHEST, 
                Equipment.DUMBBELLS,
                emptySet(),
                null,
                "Some notes"
            ) 
        } returns Result.success(testExercise)
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { 
            customExerciseRepository.createCustomExercise(
                testUserId,
                "Test Exercise", // Trimmed
                ExerciseCategory.CHEST,
                Equipment.DUMBBELLS,
                emptySet(),
                null,
                "Some notes" // Trimmed
            ) 
        }
    }
    
    @Test
    fun `invoke removes empty notes`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            notes = "   " // Only whitespace
        )
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Test Exercise") } returns true
        coEvery { 
            customExerciseRepository.createCustomExercise(
                testUserId, 
                "Test Exercise", 
                ExerciseCategory.CHEST, 
                Equipment.DUMBBELLS,
                emptySet(),
                null,
                null // Should be null for whitespace-only notes
            ) 
        } returns Result.success(testExercise)
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isSuccess)
        
        coVerify { 
            customExerciseRepository.createCustomExercise(
                testUserId,
                "Test Exercise",
                ExerciseCategory.CHEST,
                Equipment.DUMBBELLS,
                emptySet(),
                null,
                null // Empty notes converted to null
            ) 
        }
    }
    
    @Test
    fun `invoke fails when too many secondary muscles`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            secondaryMuscles = setOf(
                ExerciseCategory.SHOULDERS,
                ExerciseCategory.ARMS,
                ExerciseCategory.CORE,
                ExerciseCategory.BACK // 4 secondary muscles, max is 3
            )
        )
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Test Exercise") } returns true
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateCustomExerciseException.InvalidInput)
    }
    
    @Test
    fun `invoke fails when cardio exercise has secondary muscles`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Test Cardio",
            primaryMuscle = ExerciseCategory.CARDIO,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscles = setOf(ExerciseCategory.LEGS) // Cardio shouldn't have secondary muscles
        )
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Test Cardio") } returns true
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateCustomExerciseException.InvalidInput)
    }
    
    @Test
    fun `invoke handles repository failure`() = runTest {
        // Given
        val input = CreateCustomExerciseInput(
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS
        )
        
        val repositoryError = RuntimeException("Database error")
        
        coEvery { customExerciseRepository.isExerciseNameUnique(testUserId, "Test Exercise") } returns true
        coEvery { 
            customExerciseRepository.createCustomExercise(any(), any(), any(), any(), any(), any(), any()) 
        } returns Result.failure(repositoryError)
        
        // When
        val result = useCase(input)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CreateCustomExerciseException.RepositoryError)
    }
    
    @Test
    fun `CreateCustomExerciseInput validates name length`() {
        // Test maximum valid length
        val validInput = CreateCustomExerciseInput(
            name = "a".repeat(100), // Exactly 100 characters
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS
        )
        assertEquals(100, validInput.name.length)
        
        // Test invalid length
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "a".repeat(101), // 101 characters
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS
            )
        }
    }
    
    @Test
    fun `CreateCustomExerciseInput validates blank name`() {
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "", // Blank name
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS
            )
        }
        
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "   ", // Whitespace only
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS
            )
        }
    }
    
    @Test
    fun `CreateCustomExerciseInput validates difficulty range`() {
        // Valid difficulty
        val validInput = CreateCustomExerciseInput(
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            difficulty = 5
        )
        assertEquals(5, validInput.difficulty)
        
        // Invalid difficulty - too low
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "Test Exercise",
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS,
                difficulty = 0
            )
        }
        
        // Invalid difficulty - too high
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "Test Exercise",
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS,
                difficulty = 11
            )
        }
    }
    
    @Test
    fun `CreateCustomExerciseInput validates notes length`() {
        // Valid notes
        val validInput = CreateCustomExerciseInput(
            name = "Test Exercise",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.DUMBBELLS,
            notes = "a".repeat(500) // Exactly 500 characters
        )
        assertEquals(500, validInput.notes?.length)
        
        // Invalid notes - too long
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "Test Exercise",
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS,
                notes = "a".repeat(501) // 501 characters
            )
        }
    }
    
    @Test
    fun `CreateCustomExerciseInput validates primary muscle not in secondary muscles`() {
        assertFailsWith<IllegalArgumentException> {
            CreateCustomExerciseInput(
                name = "Test Exercise",
                primaryMuscle = ExerciseCategory.CHEST,
                equipment = Equipment.DUMBBELLS,
                secondaryMuscles = setOf(ExerciseCategory.CHEST, ExerciseCategory.SHOULDERS) // Primary in secondary
            )
        }
    }
} 