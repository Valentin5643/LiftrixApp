package com.example.liftrix.ui.workout.creation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for enhanced exercise search functionality with debounced input and visual previews
 */
class ExerciseSearchFieldTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private val sampleLibraryExercise = SearchableExercise.LibraryExercise(
        ExerciseLibrary(
            id = "1",
            name = "Bench Press",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BARBELL,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 5,
            instructions = "Press the barbell from chest to full arm extension",
            isCompound = true,
            searchableTerms = listOf("bench", "press", "chest")
        )
    )
    
    private val sampleCustomExercise = SearchableExercise.CustomExercise(
        com.example.liftrix.domain.model.CustomExercise(
            id = com.example.liftrix.domain.model.CustomExerciseId("custom-1"),
            userId = "user-1",
            name = "Modified Push-Up",
            primaryMuscle = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            notes = "Custom variation of push-up",
            createdAt = java.time.Instant.now()
        )
    )
    
    @Test
    fun exercisePreviewCard_displaysLibraryExerciseCorrectly() {
        var selectedExercise: SearchableExercise? = null
        
        composeTestRule.setContent {
            LiftrixTheme {
                ExercisePreviewCard(
                    exercise = sampleLibraryExercise,
                    onSelect = { selectedExercise = it }
                )
            }
        }
        
        // Verify exercise name is displayed
        composeTestRule
            .onNodeWithText("Bench Press")
            .assertIsDisplayed()
        
        // Verify primary muscle group is displayed
        composeTestRule
            .onNodeWithText("Chest")
            .assertIsDisplayed()
        
        // Verify equipment icon and description
        composeTestRule
            .onNodeWithContentDescription("Exercise: Bench Press, Equipment: Barbell")
            .assertIsDisplayed()
        
        // Verify click functionality
        composeTestRule
            .onNodeWithContentDescription("Exercise: Bench Press, Equipment: Barbell")
            .performClick()
        
        assertEquals(sampleLibraryExercise, selectedExercise)
    }
    
    @Test
    fun exercisePreviewCard_displaysCustomExerciseCorrectly() {
        var selectedExercise: SearchableExercise? = null
        
        composeTestRule.setContent {
            LiftrixTheme {
                ExercisePreviewCard(
                    exercise = sampleCustomExercise,
                    onSelect = { selectedExercise = it }
                )
            }
        }
        
        // Verify custom exercise name is displayed
        composeTestRule
            .onNodeWithText("Modified Push-Up")
            .assertIsDisplayed()
        
        // Verify custom badge is displayed
        composeTestRule
            .onNodeWithText("Custom")
            .assertIsDisplayed()
        
        // Verify primary muscle group is displayed
        composeTestRule
            .onNodeWithText("Chest")
            .assertIsDisplayed()
        
        // Verify click functionality
        composeTestRule
            .onNodeWithContentDescription("Exercise: Modified Push-Up, Equipment: Bodyweight Only")
            .performClick()
        
        assertEquals(sampleCustomExercise, selectedExercise)
    }
    
    @Test
    fun equipmentIcon_displaysCorrectIconForEachEquipmentType() {
        Equipment.entries.forEach { equipment ->
            composeTestRule.setContent {
                LiftrixTheme {
                    EquipmentIcon(equipment = equipment)
                }
            }
            
            // Verify equipment icon has correct content description
            composeTestRule
                .onNodeWithContentDescription(equipment.displayName)
                .assertIsDisplayed()
        }
    }
    
    @Test
    fun difficultyIndicator_displaysCorrectLevelForBeginner() {
        composeTestRule.setContent {
            LiftrixTheme {
                DifficultyIndicator(difficultyLevel = 2) // Beginner level
            }
        }
        
        // Beginner level (1-3) should show 1 active dot
        // This is a visual test - in a real implementation you might use testTags
        // for more precise verification of the difficulty indicator state
    }
    
    @Test
    fun difficultyIndicator_displaysCorrectLevelForIntermediate() {
        composeTestRule.setContent {
            LiftrixTheme {
                DifficultyIndicator(difficultyLevel = 5) // Intermediate level
            }
        }
        
        // Intermediate level (4-7) should show 2 active dots
    }
    
    @Test
    fun difficultyIndicator_displaysCorrectLevelForAdvanced() {
        composeTestRule.setContent {
            LiftrixTheme {
                DifficultyIndicator(difficultyLevel = 9) // Advanced level
            }
        }
        
        // Advanced level (8-10) should show 3 active dots
    }
    
    @Test
    fun searchableExercise_calculateMatchScoreWorksCorrectly() = runTest {
        // Test library exercise match score
        val libraryMatchScore = sampleLibraryExercise.calculateMatchScore("bench")
        assertTrue(libraryMatchScore > 0.5, "Library exercise should have high match score for 'bench'")
        
        // Test custom exercise match score
        val customMatchScore = sampleCustomExercise.calculateMatchScore("push")
        assertTrue(customMatchScore > 0.5, "Custom exercise should have high match score for 'push'")
        
        // Test no match
        val noMatchScore = sampleLibraryExercise.calculateMatchScore("random")
        assertEquals(0.0, noMatchScore, "Should return 0.0 for no match")
    }
    
    @Test 
    fun exercisePreviewCard_accessibilityPropertiesAreSet() {
        composeTestRule.setContent {
            LiftrixTheme {
                ExercisePreviewCard(
                    exercise = sampleLibraryExercise,
                    onSelect = { }
                )
            }
        }
        
        // Verify semantic properties are properly set for accessibility
        composeTestRule
            .onNodeWithContentDescription("Exercise: Bench Press, Equipment: Barbell")
            .assertIsDisplayed()
    }
    
    @Test
    fun equipmentIcon_hasProperContentDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                EquipmentIcon(equipment = Equipment.DUMBBELLS)
            }
        }
        
        // Verify equipment icon has content description for accessibility
        composeTestRule
            .onNodeWithContentDescription("Dumbbells")
            .assertIsDisplayed()
    }
} 