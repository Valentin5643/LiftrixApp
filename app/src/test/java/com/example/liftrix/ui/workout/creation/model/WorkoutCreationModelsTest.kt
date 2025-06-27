package com.example.liftrix.ui.workout.creation.model

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for WorkoutCreationModels following android.mdc testing guidelines
 */
class WorkoutCreationModelsTest {
    
    // Test data factory methods
    private fun createTestExerciseLibrary(
        id: String = "test-exercise-1",
        name: String = "Push-ups",
        equipment: Equipment = Equipment.BODYWEIGHT_ONLY
    ): ExerciseLibrary {
        return ExerciseLibrary(
            id = id,
            name = name,
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = equipment,
            secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 5,
            instructions = "Standard push-up exercise",
            isCompound = true,
            searchableTerms = listOf("pushup", "chest", "bodyweight")
        )
    }
    
    // SelectedExercise Tests
    @Test
    fun selectedExercise_fromLibraryExercise_createsCorrectInstance() {
        // Arrange
        val exerciseLibrary: ExerciseLibrary = createTestExerciseLibrary()
        val orderIndex: Int = 0
        
        // Act
        val selectedExercise: SelectedExercise = SelectedExercise.fromLibraryExercise(exerciseLibrary, orderIndex)
        
        // Assert
        assertEquals(exerciseLibrary, selectedExercise.libraryExercise)
        assertEquals(orderIndex, selectedExercise.orderIndex)
        assertEquals(1, selectedExercise.sets.size)
        assertFalse(selectedExercise.sets.first().isWeightSupported)
    }
    
    @Test
    fun selectedExercise_fromLibraryExerciseWithWeightEquipment_createsWeightSupportedSet() {
        // Arrange
        val exerciseLibrary: ExerciseLibrary = createTestExerciseLibrary(equipment = Equipment.DUMBBELLS)
        val orderIndex: Int = 0
        
        // Act
        val selectedExercise: SelectedExercise = SelectedExercise.fromLibraryExercise(exerciseLibrary, orderIndex)
        
        // Assert
        assertTrue(selectedExercise.sets.first().isWeightSupported)
    }
    
    @Test
    fun selectedExercise_isValid_returnsTrueWhenHasValidSet() {
        // Arrange
        val exerciseLibrary: ExerciseLibrary = createTestExerciseLibrary()
        val validSet: SetInput = SetInput(reps = "10", rpe = "8", isWeightSupported = false)
        val selectedExercise: SelectedExercise = SelectedExercise(exerciseLibrary, listOf(validSet), 0)
        
        // Act
        val isValid: Boolean = selectedExercise.isValid()
        
        // Assert
        assertTrue(isValid)
    }
    
    @Test
    fun selectedExercise_isValid_returnsFalseWhenNoValidSets() {
        // Arrange
        val exerciseLibrary: ExerciseLibrary = createTestExerciseLibrary()
        val invalidSet: SetInput = SetInput(reps = "", rpe = "", isWeightSupported = false)
        val selectedExercise: SelectedExercise = SelectedExercise(exerciseLibrary, listOf(invalidSet), 0)
        
        // Act
        val isValid: Boolean = selectedExercise.isValid()
        
        // Assert
        assertFalse(isValid)
    }
    
    @Test
    fun selectedExercise_calculateTotalVolume_calculatesCorrectlyForBodyweight() {
        // Arrange
        val exerciseLibrary: ExerciseLibrary = createTestExerciseLibrary()
        val set1: SetInput = SetInput(reps = "10", isWeightSupported = false)
        val set2: SetInput = SetInput(reps = "8", isWeightSupported = false)
        val selectedExercise: SelectedExercise = SelectedExercise(exerciseLibrary, listOf(set1, set2), 0)
        
        // Act
        val totalVolume: Double = selectedExercise.calculateTotalVolume()
        
        // Assert
        assertEquals(18.0, totalVolume, 0.01)
    }
    
    @Test
    fun selectedExercise_calculateTotalVolume_calculatesCorrectlyForWeightedExercise() {
        // Arrange
        val exerciseLibrary: ExerciseLibrary = createTestExerciseLibrary(equipment = Equipment.DUMBBELLS)
        val set1: SetInput = SetInput(reps = "10", weight = "20.0", isWeightSupported = true)
        val set2: SetInput = SetInput(reps = "8", weight = "22.5", isWeightSupported = true)
        val selectedExercise: SelectedExercise = SelectedExercise(exerciseLibrary, listOf(set1, set2), 0)
        
        // Act
        val totalVolume: Double = selectedExercise.calculateTotalVolume()
        
        // Assert
        assertEquals(380.0, totalVolume, 0.01) // (10 * 20) + (8 * 22.5)
    }
    
    // SetInput Tests
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForDumbbells() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.DUMBBELLS)
        
        // Assert
        assertTrue(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForBarbell() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.BARBELL)
        
        // Assert
        assertTrue(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForKettlebells() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.KETTLEBELLS)
        
        // Assert
        assertTrue(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForCableMachine() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.CABLE_MACHINE)
        
        // Assert
        assertTrue(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForBodyweight() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.BODYWEIGHT_ONLY)
        
        // Assert
        assertFalse(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForPullUpBar() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.PULL_UP_BAR)
        
        // Assert
        assertFalse(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_createForEquipment_setsCorrectWeightSupportForResistanceBands() {
        // Act
        val setInput: SetInput = SetInput.createForEquipment(Equipment.RESISTANCE_BANDS)
        
        // Assert
        assertFalse(setInput.isWeightSupported)
    }
    
    @Test
    fun setInput_isValid_returnsTrueForValidBodyweightSet() {
        // Arrange
        val setInput: SetInput = SetInput(reps = "10", rpe = "8", isWeightSupported = false)
        
        // Act
        val isValid: Boolean = setInput.isValid()
        
        // Assert
        assertTrue(isValid)
    }
    
    @Test
    fun setInput_isValid_returnsTrueForValidWeightedSet() {
        // Arrange
        val setInput: SetInput = SetInput(reps = "10", rpe = "8", weight = "20.0", isWeightSupported = true)
        
        // Act
        val isValid: Boolean = setInput.isValid()
        
        // Assert
        assertTrue(isValid)
    }
    
    @Test
    fun setInput_isValid_returnsFalseForMissingReps() {
        // Arrange
        val setInput: SetInput = SetInput(reps = "", rpe = "8", isWeightSupported = false)
        
        // Act
        val isValid: Boolean = setInput.isValid()
        
        // Assert
        assertFalse(isValid)
    }
    
    @Test
    fun setInput_isValid_returnsFalseForMissingWeightWhenRequired() {
        // Arrange
        val setInput: SetInput = SetInput(reps = "10", rpe = "8", weight = "", isWeightSupported = true)
        
        // Act
        val isValid: Boolean = setInput.isValid()
        
        // Assert
        assertFalse(isValid)
    }
    
    @Test
    fun setInput_validateReps_returnsNullForValidInput() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateReps("10")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun setInput_validateReps_returnsErrorForBlankInput() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateReps("")
        
        // Assert
        assertEquals("Reps are required", error)
    }
    
    @Test
    fun setInput_validateReps_returnsErrorForNonNumericInput() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateReps("abc")
        
        // Assert
        assertEquals("Reps must be a number", error)
    }
    
    @Test
    fun setInput_validateReps_returnsErrorForTooLowValue() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateReps("0")
        
        // Assert
        assertEquals("Reps must be at least ${SetInput.MIN_REPS}", error)
    }
    
    @Test
    fun setInput_validateReps_returnsErrorForTooHighValue() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateReps("1000")
        
        // Assert
        assertEquals("Reps cannot exceed ${SetInput.MAX_REPS}", error)
    }
    
    @Test
    fun setInput_validateRpe_returnsNullForValidInput() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateRpe("8")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun setInput_validateRpe_returnsNullForBlankInputWhenNotRequired() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateRpe("")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun setInput_validateRpe_returnsErrorForNonNumericInput() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateRpe("abc")
        
        // Assert
        assertEquals("RPE must be a number", error)
    }
    
    @Test
    fun setInput_validateRpe_returnsErrorForTooLowValue() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateRpe("0")
        
        // Assert
        assertEquals("RPE must be at least ${SetInput.MIN_RPE}", error)
    }
    
    @Test
    fun setInput_validateRpe_returnsErrorForTooHighValue() {
        // Arrange
        val setInput: SetInput = SetInput()
        
        // Act
        val error: String? = setInput.validateRpe("11")
        
        // Assert
        assertEquals("RPE cannot exceed ${SetInput.MAX_RPE}", error)
    }
    
    @Test
    fun setInput_validateWeight_returnsNullWhenWeightNotSupported() {
        // Arrange
        val setInput: SetInput = SetInput(isWeightSupported = false)
        
        // Act
        val error: String? = setInput.validateWeight("invalid")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun setInput_validateWeight_returnsNullForValidInput() {
        // Arrange
        val setInput: SetInput = SetInput(isWeightSupported = true)
        
        // Act
        val error: String? = setInput.validateWeight("20.5")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun setInput_validateWeight_returnsErrorForBlankInput() {
        // Arrange
        val setInput: SetInput = SetInput(isWeightSupported = true)
        
        // Act
        val error: String? = setInput.validateWeight("")
        
        // Assert
        assertEquals("Weight is required", error)
    }
    
    @Test
    fun setInput_validateWeight_returnsErrorForNonNumericInput() {
        // Arrange
        val setInput: SetInput = SetInput(isWeightSupported = true)
        
        // Act
        val error: String? = setInput.validateWeight("abc")
        
        // Assert
        assertEquals("Weight must be a number", error)
    }
    
    @Test
    fun setInput_validateWeight_returnsErrorForNegativeValue() {
        // Arrange
        val setInput: SetInput = SetInput(isWeightSupported = true)
        
        // Act
        val error: String? = setInput.validateWeight("-5.0")
        
        // Assert
        assertEquals("Weight cannot be negative", error)
    }
    
    @Test
    fun setInput_validateWeight_returnsErrorForTooHighValue() {
        // Arrange
        val setInput: SetInput = SetInput(isWeightSupported = true)
        
        // Act
        val error: String? = setInput.validateWeight("10000.0")
        
        // Assert
        assertEquals("Weight cannot exceed ${SetInput.MAX_WEIGHT} kg", error)
    }
    
    // WorkoutCreationState Tests
    @Test
    fun workoutCreationState_validateWorkoutName_returnsNullForValidName() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState()
        
        // Act
        val error: String? = state.validateWorkoutName("Valid Workout Name")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun workoutCreationState_validateWorkoutName_returnsErrorForBlankName() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState()
        
        // Act
        val error: String? = state.validateWorkoutName("")
        
        // Assert
        assertEquals("Workout name is required", error)
    }
    
    @Test
    fun workoutCreationState_validateWorkoutName_returnsErrorForTooLongName() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState()
        val longName: String = "a".repeat(WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH + 1)
        
        // Act
        val error: String? = state.validateWorkoutName(longName)
        
        // Assert
        assertEquals("Workout name cannot exceed ${WorkoutCreationState.MAX_WORKOUT_NAME_LENGTH} characters", error)
    }
    
    @Test
    fun workoutCreationState_validateWorkoutDescription_returnsNullForValidDescription() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState()
        
        // Act
        val error: String? = state.validateWorkoutDescription("Valid description")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun workoutCreationState_validateWorkoutDescription_returnsNullForEmptyDescription() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState()
        
        // Act
        val error: String? = state.validateWorkoutDescription("")
        
        // Assert
        assertNull(error)
    }
    
    @Test
    fun workoutCreationState_validateWorkoutDescription_returnsErrorForTooLongDescription() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState()
        val longDescription: String = "a".repeat(WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH + 1)
        
        // Act
        val error: String? = state.validateWorkoutDescription(longDescription)
        
        // Assert
        assertEquals("Description cannot exceed ${WorkoutCreationState.MAX_WORKOUT_DESCRIPTION_LENGTH} characters", error)
    }
    
    @Test
    fun workoutCreationState_validateForm_returnsTrueForValidForm() {
        // Arrange
        val validExercise: SelectedExercise = SelectedExercise.fromLibraryExercise(createTestExerciseLibrary(), 0)
        val validExerciseWithValidSet: SelectedExercise = validExercise.copy(
            sets = listOf(SetInput(reps = "10", rpe = "8", isWeightSupported = false))
        )
        val state: WorkoutCreationState = WorkoutCreationState(
            workoutName = "Valid Workout",
            workoutNameError = null,
            workoutDescription = "Valid description",
            workoutDescriptionError = null,
            selectedExercises = listOf(validExerciseWithValidSet)
        )
        
        // Act
        val isValid: Boolean = state.validateForm()
        
        // Assert
        assertTrue(isValid)
    }
    
    @Test
    fun workoutCreationState_validateForm_returnsFalseForInvalidName() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState(
            workoutName = "",
            workoutNameError = "Workout name is required"
        )
        
        // Act
        val isValid: Boolean = state.validateForm()
        
        // Assert
        assertFalse(isValid)
    }
    
    @Test
    fun workoutCreationState_validateForm_returnsFalseForNoExercises() {
        // Arrange
        val state: WorkoutCreationState = WorkoutCreationState(
            workoutName = "Valid Workout",
            workoutNameError = null,
            selectedExercises = emptyList()
        )
        
        // Act
        val isValid: Boolean = state.validateForm()
        
        // Assert
        assertFalse(isValid)
    }
    
    @Test
    fun workoutCreationState_validateForm_returnsFalseForTooManyExercises() {
        // Arrange
        val exercises: List<SelectedExercise> = (1..25).map { index ->
            SelectedExercise.fromLibraryExercise(createTestExerciseLibrary(id = "exercise-$index"), index)
        }
        val state: WorkoutCreationState = WorkoutCreationState(
            workoutName = "Valid Workout",
            workoutNameError = null,
            selectedExercises = exercises
        )
        
        // Act
        val isValid: Boolean = state.validateForm()
        
        // Assert
        assertFalse(isValid)
    }
} 