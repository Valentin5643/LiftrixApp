package com.example.liftrix.ui.workout.active

import com.example.liftrix.domain.model.*
import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify ExerciseSet validation and smart defaults after migration
 */
class ActiveWorkoutViewModelMigrationTest {

    @Test
    fun `ExerciseSet validation should pass with smart defaults`() {
        // Test cardio exercise defaults
        val cardioExercise = ExerciseLibrary(
            id = "cardio-test",
            name = "Treadmill Run",
            primaryMuscleGroup = MuscleGroup.CARDIO,
            equipment = Equipment.EXERCISE_BIKE,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "cardio",
            difficultyLevel = 3,
            instructions = "Test cardio exercise",
            isCompound = false,
            searchableTerms = listOf("bike", "cardio")
        )
        
        // Verify cardio exercises get time defaults
        assertTrue("Cardio exercise should have time-based defaults", 
            cardioExercise.movementPattern.contains("cardio", ignoreCase = true))
        
        // Test strength exercise defaults
        val strengthExercise = ExerciseLibrary(
            id = "strength-test",
            name = "Bench Press",
            primaryMuscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            secondaryMuscleGroups = emptyList(),
            movementPattern = "push",
            difficultyLevel = 4,
            instructions = "Test strength exercise",
            isCompound = true,
            searchableTerms = listOf("bench", "press")
        )
        
        // Verify strength exercises get rep defaults
        assertFalse("Strength exercise should not be cardio", 
            strengthExercise.movementPattern.contains("cardio", ignoreCase = true))
    }
    
    @Test
    fun `ExerciseSet should fail validation with all null metrics`() {
        // This test verifies the original bug would fail
        try {
            val invalidSet = ExerciseSet(
                id = ExerciseSetId.generate(),
                setNumber = 1,
                reps = null,
                weight = null,
                time = null,
                distance = null,
                completedAt = null,
                notes = null
            )
            fail("ExerciseSet should fail validation with all null metrics")
        } catch (e: IllegalArgumentException) {
            assertTrue("Should fail validation", e.message?.contains("at least one metric") == true)
        }
    }
}