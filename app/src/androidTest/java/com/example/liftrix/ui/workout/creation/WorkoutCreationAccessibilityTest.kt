package com.example.liftrix.ui.workout.creation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.components.ExerciseCard
import com.example.liftrix.ui.workout.creation.components.ExerciseSelector
import com.example.liftrix.ui.workout.creation.components.SetInputRow
import com.example.liftrix.ui.workout.creation.components.WorkoutHeaderForm
import com.example.liftrix.ui.workout.creation.model.SelectedExercise
import com.example.liftrix.ui.workout.creation.model.SetInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Comprehensive accessibility tests for workout creation components
 * Ensures new components meet accessibility standards for screen readers,
 * keyboard navigation, and assistive technologies
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutCreationAccessibilityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

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
            name = "Push-ups",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS),
            movementPattern = "Push",
            difficultyLevel = 1,
            instructions = "Perform push-ups with proper form",
            isCompound = true,
            searchableTerms = listOf("chest", "bodyweight", "push")
        )
    )

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun unifiedWorkoutCreationScreen_hasAccessibleElements() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }

        // Verify main screen elements have proper accessibility
        composeTestRule.onNodeWithContentDescription("Navigate back")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .assertExists()
            .assert(hasSetTextAction())

        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .assertExists()
            .assert(hasSetTextAction())

        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun exerciseSelector_screenReaderCompatible() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseSelector(
                    exercises = sampleExercises,
                    recentExercises = emptyList(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    selectedEquipment = emptySet(),
                    onEquipmentSelectionChange = {},
                    selectedMuscleGroups = emptySet(),
                    onMuscleGroupSelectionChange = {},
                    onExerciseSelected = {},
                    onCreateCustomExercise = {},
                    onDismiss = {},
                    isVisible = true
                )
            }
        }

        // Verify modal has proper accessibility structure
        composeTestRule.onNodeWithContentDescription("Exercise selection bottom sheet")
            .assertExists()

        composeTestRule.onNodeWithContentDescription("Close exercise selector")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .assertExists()
            .assert(hasSetTextAction())

        // Verify exercise items are accessible
        composeTestRule.onNodeWithContentDescription("Select Bench Press exercise")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Select Push-ups exercise")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Create custom exercise")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun exerciseSelector_keyboardNavigation() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseSelector(
                    exercises = sampleExercises,
                    recentExercises = emptyList(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    selectedEquipment = emptySet(),
                    onEquipmentSelectionChange = {},
                    selectedMuscleGroups = emptySet(),
                    onMuscleGroupSelectionChange = {},
                    onExerciseSelected = {},
                    onCreateCustomExercise = {},
                    onDismiss = {},
                    isVisible = true
                )
            }
        }

        // Test keyboard navigation through components
        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .requestFocus()
            .assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .performTextInput("bench")

        // Verify search results are keyboard accessible
        composeTestRule.onNodeWithContentDescription("Select Bench Press exercise")
            .requestFocus()
            .assertIsFocused()
    }

    @Test
    fun exerciseCard_hasProperAccessibilitySupport() {
        val selectedExercise = SelectedExercise(
            exercise = sampleExercises[0],
            sets = listOf(
                SetInput(
                    reps = 10,
                    rpe = 8.0,
                    weightKg = 80.0,
                    isWeightSupported = true
                )
            )
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetsChange = {},
                    onRemoveExercise = {},
                    isExpanded = true,
                    onExpandedChange = {}
                )
            }
        }

        // Verify exercise card accessibility
        composeTestRule.onNodeWithContentDescription("Exercise: Bench Press")
            .assertExists()

        composeTestRule.onNodeWithContentDescription("Remove Bench Press from workout")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Expand or collapse Bench Press exercise details")
            .assertExists()
            .assertHasClickAction()

        // Verify set input accessibility
        composeTestRule.onNodeWithContentDescription("Set number 1")
            .assertExists()

        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .assertExists()
            .assert(hasSetTextAction())

        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .assertExists()
            .assert(hasSetTextAction())

        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .assertExists()
            .assert(hasSetTextAction())
    }

    @Test
    fun setInputRow_keyboardNavigationSupport() {
        composeTestRule.setContent {
            LiftrixTheme {
                SetInputRow(
                    setInput = SetInput(isWeightSupported = true),
                    setNumber = 1,
                    onSetChange = {},
                    onRemoveSet = {}
                )
            }
        }

        // Test focus order through set input fields
        composeTestRule.onNodeWithContentDescription("Repetitions input field")
            .requestFocus()
            .assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Weight in kilograms input field")
            .requestFocus()
            .assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Rate of perceived exertion input field, optional")
            .requestFocus()
            .assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .requestFocus()
            .assertIsFocused()
    }

    @Test
    fun workoutHeaderForm_accessibilityCompliant() {
        composeTestRule.setContent {
            LiftrixTheme {
                WorkoutHeaderForm(
                    workoutName = "",
                    onWorkoutNameChange = {},
                    workoutDescription = "",
                    onWorkoutDescriptionChange = {},
                    workoutNameError = "Name is required",
                    workoutDescriptionError = null
                )
            }
        }

        // Verify form fields have proper accessibility labels
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .assertExists()
            .assert(hasSetTextAction())

        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .assertExists()
            .assert(hasSetTextAction())

        // Verify error messages are announced to screen readers
        composeTestRule.onNodeWithText("Name is required")
            .assertExists()
            .assert(hasText("Name is required"))
    }

    @Test
    fun exerciseSelector_filterChips_accessible() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseSelector(
                    exercises = sampleExercises,
                    recentExercises = emptyList(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    selectedEquipment = emptySet(),
                    onEquipmentSelectionChange = {},
                    selectedMuscleGroups = emptySet(),
                    onMuscleGroupSelectionChange = {},
                    onExerciseSelected = {},
                    onCreateCustomExercise = {},
                    onDismiss = {},
                    isVisible = true
                )
            }
        }

        // Verify equipment filter chips are accessible
        composeTestRule.onNodeWithContentDescription("Add Barbell filter")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Add Dumbbells filter")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Add Bodyweight Only filter")
            .assertExists()
            .assertHasClickAction()

        // Verify muscle group filter chips are accessible
        composeTestRule.onNodeWithContentDescription("Add Chest filter")
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNodeWithContentDescription("Add Back filter")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun workoutCreationScreen_minimumTouchTargets() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }

        val minimumTouchTargetSize = 48.dp

        // Verify all interactive elements meet minimum touch target size
        composeTestRule.onNodeWithContentDescription("Navigate back")
            .assertHeightIsAtLeast(minimumTouchTargetSize)
            .assertWidthIsAtLeast(minimumTouchTargetSize)

        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .assertHeightIsAtLeast(minimumTouchTargetSize)

        composeTestRule.onNodeWithContentDescription("Save workout button, disabled - complete required fields")
            .assertHeightIsAtLeast(minimumTouchTargetSize)
    }

    @Test
    fun exerciseCard_minimumTouchTargets() {
        val selectedExercise = SelectedExercise(
            exercise = sampleExercises[0],
            sets = listOf(SetInput(isWeightSupported = true))
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetsChange = {},
                    onRemoveExercise = {},
                    isExpanded = true,
                    onExpandedChange = {}
                )
            }
        }

        val minimumTouchTargetSize = 48.dp

        composeTestRule.onNodeWithContentDescription("Remove Bench Press from workout")
            .assertHeightIsAtLeast(minimumTouchTargetSize)
            .assertWidthIsAtLeast(minimumTouchTargetSize)

        composeTestRule.onNodeWithContentDescription("Expand or collapse Bench Press exercise details")
            .assertHeightIsAtLeast(minimumTouchTargetSize)

        composeTestRule.onNodeWithContentDescription("Remove set 1")
            .assertHeightIsAtLeast(minimumTouchTargetSize)
            .assertWidthIsAtLeast(minimumTouchTargetSize)
    }

    @Test
    fun workoutCreationScreen_providesStatusAnnouncements() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }

        // Verify loading states are announced
        composeTestRule.onNodeWithContentDescription("Loading workout data, please wait")
            .assertDoesNotExist() // Should not be loading initially

        // Verify form validation errors are announced
        composeTestRule.onNodeWithContentDescription("Save workout button, disabled - complete required fields")
            .assertExists()
    }

    @Test
    fun exerciseSelector_emptyState_accessible() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseSelector(
                    exercises = emptyList(),
                    recentExercises = emptyList(),
                    searchQuery = "nonexistent",
                    onSearchQueryChange = {},
                    selectedEquipment = emptySet(),
                    onEquipmentSelectionChange = {},
                    selectedMuscleGroups = emptySet(),
                    onMuscleGroupSelectionChange = {},
                    onExerciseSelected = {},
                    onCreateCustomExercise = {},
                    onDismiss = {},
                    isVisible = true
                )
            }
        }

        // Verify empty state messages are accessible
        composeTestRule.onNodeWithText("No exercises found for \"nonexistent\"")
            .assertExists()

        composeTestRule.onNodeWithText("Try a different search term")
            .assertExists()

        // Verify alternative action is still available
        composeTestRule.onNodeWithContentDescription("Create custom exercise")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun workoutCreationFlow_maintainsFocusOrder() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }

        // Test logical focus order through the form
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .requestFocus()
            .assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .requestFocus()
            .assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Add exercise to workout")
            .requestFocus()
            .assertIsFocused()
    }

    @Test
    fun workoutCreationScreen_performanceAccessibilityTest() {
        val renderTime = measureTimeMillis {
            composeTestRule.setContent {
                LiftrixTheme {
                    UnifiedWorkoutCreationScreen(
                        onNavigateBack = {},
                        onWorkoutCreated = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify screen renders quickly for accessibility tools
        assert(renderTime < 1000) { "Screen took too long to render: ${renderTime}ms" }

        // Verify all accessibility elements are ready
        composeTestRule.onNodeWithContentDescription("Navigate back").assertExists()
        composeTestRule.onNodeWithContentDescription("Workout name input field").assertExists()
        composeTestRule.onNodeWithContentDescription("Add exercise to workout").assertExists()
    }

    @Test
    fun exerciseSelector_modalAccessibility() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseSelector(
                    exercises = sampleExercises,
                    recentExercises = emptyList(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    selectedEquipment = emptySet(),
                    onEquipmentSelectionChange = {},
                    selectedMuscleGroups = emptySet(),
                    onMuscleGroupSelectionChange = {},
                    onExerciseSelected = {},
                    onCreateCustomExercise = {},
                    onDismiss = {},
                    isVisible = true
                )
            }
        }

        // Verify modal dialog accessibility
        composeTestRule.onNodeWithContentDescription("Exercise selection bottom sheet")
            .assertExists()

        // Verify focus trapping in modal
        composeTestRule.onNodeWithContentDescription("Exercise search field")
            .requestFocus()
            .assertIsFocused()

        // Verify escape mechanism
        composeTestRule.onNodeWithContentDescription("Close exercise selector")
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun workoutCreationScreen_semanticProperties() {
        composeTestRule.setContent {
            LiftrixTheme {
                UnifiedWorkoutCreationScreen(
                    onNavigateBack = {},
                    onWorkoutCreated = {}
                )
            }
        }

        // Verify semantic properties are set correctly
        composeTestRule.onNodeWithContentDescription("Workout name input field")
            .assert(hasSetTextAction())
            .assert(hasImeAction(androidx.compose.ui.text.input.ImeAction.Next))

        composeTestRule.onNodeWithContentDescription("Workout description input field, optional")
            .assert(hasSetTextAction())
            .assert(hasImeAction(androidx.compose.ui.text.input.ImeAction.Done))
    }

    @Test
    fun exerciseCard_expandedState_accessible() {
        val selectedExercise = SelectedExercise(
            exercise = sampleExercises[0],
            sets = listOf(
                SetInput(reps = 10, rpe = 8.0, weightKg = 80.0, isWeightSupported = true),
                SetInput(reps = 8, rpe = 9.0, weightKg = 85.0, isWeightSupported = true)
            )
        )

        composeTestRule.setContent {
            LiftrixTheme {
                ExerciseCard(
                    selectedExercise = selectedExercise,
                    onSetsChange = {},
                    onRemoveExercise = {},
                    isExpanded = true,
                    onExpandedChange = {}
                )
            }
        }

        // Verify all sets are accessible when expanded
        composeTestRule.onNodeWithContentDescription("Set number 1")
            .assertExists()

        composeTestRule.onNodeWithContentDescription("Set number 2")
            .assertExists()

        // Verify add set button is accessible
        composeTestRule.onNodeWithContentDescription("Add another set to Bench Press")
            .assertExists()
            .assertHasClickAction()
    }
} 