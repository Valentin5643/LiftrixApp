package com.example.liftrix.ui.common.state

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.sync.SyncStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for sealed class UiState patterns.
 * 
 * Validates that the new screen-specific sealed class hierarchies provide
 * proper type safety, state management, and exhaustive when expressions.
 */
class UiStatePatternTest {

    @Test
    fun `WorkoutUiState Loading state should be properly typed`() {
        val state: WorkoutUiState = WorkoutUiState.Loading
        
        assertTrue("Loading state should be WorkoutUiState.Loading", state is WorkoutUiState.Loading)
        assertFalse("Loading state should not be Success", state is WorkoutUiState.Success)
        assertFalse("Loading state should not be Error", state is WorkoutUiState.Error)
        assertFalse("Loading state should not be Empty", state is WorkoutUiState.Empty)
    }

    @Test
    fun `WorkoutUiState Success state should contain data`() {
        val workoutData = WorkoutScreenData(
            workouts = emptyList(),
            templates = emptyList(),
            syncStatus = SyncStatus.Idle,
            unsyncedCount = 0
        )
        
        val state: WorkoutUiState = WorkoutUiState.Success(
            data = workoutData,
            isRefreshing = false
        )
        
        assertTrue("Success state should be WorkoutUiState.Success", state is WorkoutUiState.Success)
        
        when (state) {
            is WorkoutUiState.Success -> {
                assertEquals("Data should match", workoutData, state.data)
                assertFalse("Should not be refreshing", state.isRefreshing)
                assertTrue("Timestamp should be recent", state.timestamp > 0)
            }
            else -> fail("State should be Success")
        }
    }

    @Test
    fun `WorkoutUiState Error state should contain error and previous data`() {
        val error = LiftrixError.NetworkError("Network connection failed")
        val previousData = WorkoutScreenData()
        
        val state: WorkoutUiState = WorkoutUiState.Error(
            error = error,
            previousData = previousData
        )
        
        assertTrue("Error state should be WorkoutUiState.Error", state is WorkoutUiState.Error)
        
        when (state) {
            is WorkoutUiState.Error -> {
                assertEquals("Error should match", error, state.error)
                assertEquals("Previous data should match", previousData, state.previousData)
            }
            else -> fail("State should be Error")
        }
    }

    @Test
    fun `WorkoutUiState Empty state should be properly typed`() {
        val state: WorkoutUiState = WorkoutUiState.Empty
        
        assertTrue("Empty state should be WorkoutUiState.Empty", state is WorkoutUiState.Empty)
        assertFalse("Empty state should not be Loading", state is WorkoutUiState.Loading)
        assertFalse("Empty state should not be Success", state is WorkoutUiState.Success)
        assertFalse("Empty state should not be Error", state is WorkoutUiState.Error)
    }

    @Test
    fun `HomeUiState should provide type-safe state management`() {
        val homeData = HomeScreenData(
            recentWorkouts = emptyList(),
            workoutFeedState = FeedState.Loading,
            recommendationsState = RecommendationsState.Loading
        )
        
        val state: HomeUiState = HomeUiState.Success(data = homeData)
        
        // Test exhaustive when expression
        val result = when (state) {
            is HomeUiState.Loading -> "loading"
            is HomeUiState.Success -> "success"
            is HomeUiState.Error -> "error"
            is HomeUiState.Empty -> "empty"
        }
        
        assertEquals("When expression should return success", "success", result)
    }

    @Test
    fun `WorkoutTemplateCreationUiState should manage creation data`() {
        val creationData = WorkoutTemplateCreationData(
            exercises = emptyList(),
            availableExercises = emptyList(),
            exerciseSearchQuery = "",
            selectedExercise = null,
            isExerciseSelectorExpanded = false
        )
        
        val state: WorkoutTemplateCreationUiState = WorkoutTemplateCreationUiState.Success(
            data = creationData
        )
        
        assertTrue("Should be creation success state", state is WorkoutTemplateCreationUiState.Success)
        
        when (state) {
            is WorkoutTemplateCreationUiState.Success -> {
                assertEquals("Creation data should match", creationData, state.data)
                assertFalse("Should not have exercises", state.data.hasExercises)
                assertEquals("Exercise count should be 0", 0, state.data.exerciseCount)
            }
            else -> fail("State should be Success")
        }
    }

    @Test
    fun `EditWorkoutUiState should manage editing data with change tracking`() {
        val editData = EditWorkoutData(
            originalWorkout = null,
            editedWorkout = null,
            editedName = "Test Workout",
            editedDescription = "Test Description",
            editedExercises = emptyList(),
            validationErrors = emptyList()
        )
        
        val state: EditWorkoutUiState = EditWorkoutUiState.Success(data = editData)
        
        assertTrue("Should be edit success state", state is EditWorkoutUiState.Success)
        
        when (state) {
            is EditWorkoutUiState.Success -> {
                assertEquals("Edit data should match", editData, state.data)
                assertFalse("Should not have changes without original", state.data.hasChanges)
                assertFalse("Should not be able to save without changes", state.data.canSave)
            }
            else -> fail("State should be Success")
        }
    }

    @Test
    fun `WorkoutTemplateCreationData should validate creation requirements`() {
        val emptyData = WorkoutTemplateCreationData()
        
        assertFalse("Empty data should not be valid for creation", 
            emptyData.isValidForCreation(""))
        assertFalse("Data without name should not be valid", 
            emptyData.isValidForCreation(""))
        assertFalse("Data with name but no exercises should not be valid", 
            emptyData.isValidForCreation("Test Workout"))
        
        val dataWithExercises = WorkoutTemplateCreationData(
            exercises = listOf(
                TemplateExercise(
                    exerciseId = com.example.liftrix.domain.model.ExerciseId.fromString("test"),
                    name = "Test Exercise",
                    primaryMuscle = com.example.liftrix.domain.model.MuscleGroup.CHEST,
                    equipment = com.example.liftrix.domain.model.Equipment.BARBELL,
                    orderIndex = 0,
                    isCustomExercise = false
                )
            )
        )
        
        assertTrue("Data with name and exercises should be valid", 
            dataWithExercises.isValidForCreation("Test Workout"))
        assertTrue("Should have exercises", dataWithExercises.hasExercises)
        assertEquals("Exercise count should be 1", 1, dataWithExercises.exerciseCount)
    }

    @Test
    fun `EditWorkoutData should track changes correctly`() {
        val originalWorkout = Workout(
            id = com.example.liftrix.domain.model.WorkoutId("test"),
            name = "Original Name",
            notes = "Original Notes",
            exercises = emptyList(),
            userId = "user123",
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            status = com.example.liftrix.domain.model.WorkoutStatus.PLANNED
        )
        
        val editData = EditWorkoutData(
            originalWorkout = originalWorkout,
            editedWorkout = originalWorkout,
            editedName = "Modified Name",
            editedDescription = "Original Notes",
            editedExercises = emptyList()
        )
        
        assertTrue("Should detect name changes", editData.hasChanges)
        
        val unchangedData = EditWorkoutData(
            originalWorkout = originalWorkout,
            editedWorkout = originalWorkout,
            editedName = "Original Name",
            editedDescription = "Original Notes",
            editedExercises = emptyList()
        )
        
        assertFalse("Should not detect changes when data matches", unchangedData.hasChanges)
    }

    @Test
    fun `State extension functions should work correctly`() {
        val successState = WorkoutUiState.Success(
            data = WorkoutScreenData()
        )
        
        val errorState = WorkoutUiState.Error(
            error = LiftrixError.NetworkError("Test error"),
            previousData = WorkoutScreenData()
        )
        
        // Test dataOrNull extension function
        assertNotNull("Success state should return data", successState.dataOrNull())
        assertNotNull("Error state with previous data should return data", errorState.dataOrNull())
        assertNull("Loading state should return null", WorkoutUiState.Loading.dataOrNull())
        
        // Test isLoading function
        assertTrue("Loading state should be loading", WorkoutUiState.Loading.isLoading())
        assertFalse("Success state should not be loading", successState.isLoading())
        assertFalse("Error state should not be loading", errorState.isLoading())
        
        // Test hasData function
        assertTrue("Success state should have data", successState.hasData())
        assertTrue("Error state with previous data should have data", errorState.hasData())
        assertFalse("Loading state should not have data", WorkoutUiState.Loading.hasData())
        
        // Test isError function
        assertTrue("Error state should be error", errorState.isError())
        assertFalse("Success state should not be error", successState.isError())
        assertFalse("Loading state should not be error", WorkoutUiState.Loading.isError())
    }

    @Test
    fun `Sealed class hierarchies should enable exhaustive when expressions`() {
        val states = listOf(
            WorkoutUiState.Loading,
            WorkoutUiState.Success(data = WorkoutScreenData()),
            WorkoutUiState.Error(error = LiftrixError.NetworkError("Test")),
            WorkoutUiState.Empty
        )
        
        states.forEach { state ->
            // This should compile without errors and be exhaustive
            val result = when (state) {
                is WorkoutUiState.Loading -> "Loading"
                is WorkoutUiState.Success -> "Success"
                is WorkoutUiState.Error -> "Error"
                is WorkoutUiState.Empty -> "Empty"
            }
            
            assertNotNull("Result should not be null", result)
            assertTrue("Result should be a valid state name", 
                result in listOf("Loading", "Success", "Error", "Empty"))
        }
    }

    @Test
    fun `Screen-specific states should inherit from base UiState`() {
        assertTrue("WorkoutUiState should extend UiState", 
            WorkoutUiState.Loading is UiState<*>)
        assertTrue("HomeUiState should extend UiState", 
            HomeUiState.Loading is UiState<*>)
        assertTrue("WorkoutTemplateCreationUiState should extend UiState", 
            WorkoutTemplateCreationUiState.Loading is UiState<*>)
        assertTrue("EditWorkoutUiState should extend UiState", 
            EditWorkoutUiState.Loading is UiState<*>)
    }
}