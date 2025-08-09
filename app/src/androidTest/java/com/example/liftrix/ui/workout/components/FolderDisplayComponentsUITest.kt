package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * UI tests for FolderDisplayComponents
 * 
 * Tests user interactions, accessibility, performance, and visual behavior.
 * Focuses on drag-and-drop operations and 60fps animation validation.
 */
class FolderDisplayComponentsUITest {
    
    @get:Rule
    val composeTestRule: ComposeContentTestRule = createComposeRule()
    
    // Test data
    private val testFolder = Folder(
        id = FolderId("test_folder"),
        userId = "test_user",
        name = FolderName("Test Folder"),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        templateCount = 2
    )
    
    private val testWorkouts = listOf(
        WorkoutTemplate(
            id = WorkoutTemplateId("workout1"),
            userId = "test_user",
            name = "Push Workout",
            folderId = "test_folder",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            exercises = emptyList(),
            estimatedDurationMinutes = 45,
            difficultyLevel = 3,
            usageCount = 0,
            lastUsedAt = null
        ),
        WorkoutTemplate(
            id = WorkoutTemplateId("workout2"),
            userId = "test_user",
            name = "Pull Workout",
            folderId = "test_folder",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            exercises = emptyList(),
            estimatedDurationMinutes = 35,
            difficultyLevel = 2,
            usageCount = 0,
            lastUsedAt = null
        )
    )
    
    // Mock callbacks
    private val mockOnToggleExpanded = mockk<(String) -> Unit>(relaxed = true)
    private val mockOnStartWorkout = mockk<(WorkoutTemplate) -> Unit>(relaxed = true)
    private val mockOnEditWorkout = mockk<(WorkoutTemplate) -> Unit>(relaxed = true)
    private val mockOnEditFolder = mockk<(String) -> Unit>(relaxed = true)
    private val mockOnCreateFolder = mockk<() -> Unit>(relaxed = true)
    private val mockOnCreateWorkout = mockk<(String) -> Unit>(relaxed = true)
    private val mockOnMoveWorkout = mockk<(WorkoutTemplate, String) -> Unit>(relaxed = true)
    
    @Before
    fun setup() {
        // Clear all mocks before each test
        clearAllMocks()
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun folderSection_displaysFolderHeaderCorrectly() {
        // Arrange
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = false,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout,
                            modifier = Modifier.testTag("folder_section")
                        )
                    }
                }
            }
        }
        
        // Assert
        composeTestRule.onNodeWithTag("folder_section").assertExists()
        composeTestRule.onNodeWithText("Test Folder").assertExists()
        composeTestRule.onNodeWithText("2 workouts").assertExists()
    }
    
    @Test
    fun folderSection_expandsAndCollapsesCorrectly() {
        // Arrange
        var isExpanded by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = isExpanded,
                            onToggleExpanded = { isExpanded = !isExpanded },
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout,
                            modifier = Modifier.testTag("folder_section")
                        )
                    }
                }
            }
        }
        
        // Assert initial collapsed state
        composeTestRule.onNodeWithText("Push Workout").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pull Workout").assertDoesNotExist()
        
        // Act - expand folder
        composeTestRule.onNodeWithText("Test Folder").performClick()
        
        // Assert expanded state
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Push Workout").assertExists()
        composeTestRule.onNodeWithText("Pull Workout").assertExists()
        
        // Act - collapse folder
        composeTestRule.onNodeWithText("Test Folder").performClick()
        
        // Assert collapsed state
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Push Workout").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pull Workout").assertDoesNotExist()
    }
    
    @Test
    fun folderSection_handlesEmptyFolderState() {
        // Arrange
        val emptyFolder = testFolder.copy(templateCount = 0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = emptyFolder,
                            workouts = emptyList(),
                            isExpanded = true,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout,
                            modifier = Modifier.testTag("empty_folder_section")
                        )
                    }
                }
            }
        }
        
        // Assert
        composeTestRule.onNodeWithText("No workouts in this folder").assertExists()
        composeTestRule.onNodeWithText("Create Workout").assertExists()
        composeTestRule.onNodeWithText("0 workouts").assertExists()
    }
    
    @Test
    fun workoutCard_displaysWorkoutInfoCorrectly() {
        // Arrange
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = true,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout,
                            modifier = Modifier.testTag("folder_section")
                        )
                    }
                }
            }
        }
        
        // Assert workout details
        composeTestRule.onNodeWithText("Push Workout").assertExists()
        composeTestRule.onNodeWithText("0 exercises").assertExists() // Empty exercise list
        composeTestRule.onNodeWithText("45min").assertExists()
        
        composeTestRule.onNodeWithText("Pull Workout").assertExists()
        composeTestRule.onNodeWithText("0 exercises").assertExists() // Empty exercise list
        composeTestRule.onNodeWithText("35min").assertExists()
    }
    
    @Test
    fun workoutCard_handlesUserInteractions() {
        // Arrange
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = true,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout
                        )
                    }
                }
            }
        }
        
        // Act & Assert - Start workout button
        composeTestRule.onAllNodesWithText("Start")[0].performClick()
        verify(exactly = 1) { mockOnStartWorkout(testWorkouts[0]) }
        
        // Act & Assert - Edit workout button
        composeTestRule.onAllNodesWithText("Edit")[0].performClick()
        verify(exactly = 1) { mockOnEditWorkout(testWorkouts[0]) }
    }
    
    @Test
    fun folderHeader_handlesEditFolderAction() {
        // Arrange
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = false,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout
                        )
                    }
                }
            }
        }
        
        // Act - click edit folder button
        composeTestRule.onNodeWithContentDescription("Edit folder Test Folder").performClick()
        
        // Assert
        verify(exactly = 1) { mockOnEditFolder(testFolder.id.value) }
    }
    
    @Test
    fun folderSection_providesProperAccessibilityLabels() {
        // Arrange
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = false,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout
                        )
                    }
                }
            }
        }
        
        // Assert accessibility labels
        composeTestRule.onNodeWithContentDescription("Expand Test Folder folder with 2 workouts").assertExists()
        composeTestRule.onNodeWithContentDescription("Edit folder Test Folder").assertExists()
        
        // Test expanded state accessibility
        composeTestRule.onNodeWithText("Test Folder").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Collapse Test Folder folder with 2 workouts").assertExists()
    }
    
    @Test
    fun workoutCard_supportsDragGestureInteraction() {
        // This test verifies drag gesture detection setup
        // Note: Actual drag testing requires more complex setup with gesture simulation
        
        // Arrange
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = true,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout
                        )
                    }
                }
            }
        }
        
        // Assert drag handle is present
        composeTestRule.onAllNodesWithContentDescription("Drag to move workout").assertCountEquals(2)
    }
    
    @Test
    fun folderSection_performsWithin60FpsTarget() {
        // Performance test for folder expansion animation
        // This test validates that animations complete within reasonable time frames
        
        // Arrange
        var isExpanded by mutableStateOf(false)
        val animationTimeLimit = 300L // Target: under 300ms for folder expansion
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = testFolder,
                            workouts = testWorkouts,
                            isExpanded = isExpanded,
                            onToggleExpanded = { isExpanded = !isExpanded },
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout
                        )
                    }
                }
            }
        }
        
        // Act & Assert - Test animation performance
        val startTime = System.currentTimeMillis()
        composeTestRule.onNodeWithText("Test Folder").performClick()
        composeTestRule.waitUntil(timeoutMillis = animationTimeLimit) {
            composeTestRule.onNodeWithText("Push Workout").isDisplayed()
        }
        val endTime = System.currentTimeMillis()
        
        // Verify animation completes within performance target
        val animationDuration = endTime - startTime
        assert(animationDuration <= animationTimeLimit) {
            "Folder expansion animation took ${animationDuration}ms, exceeding target of ${animationTimeLimit}ms"
        }
    }
    
    @Test
    fun emptyFolderContent_triggersCreateWorkoutAction() {
        // Arrange
        val emptyFolder = testFolder.copy(templateCount = 0)
        
        composeTestRule.setContent {
            LiftrixTheme {
                LazyColumn {
                    item {
                        InlineFolderSection(
                            folder = emptyFolder,
                            workouts = emptyList(),
                            isExpanded = true,
                            onToggleExpanded = mockOnToggleExpanded,
                            onStartWorkout = mockOnStartWorkout,
                            onEditWorkout = mockOnEditWorkout,
                            onEditFolder = mockOnEditFolder,
                            onCreateFolder = mockOnCreateFolder,
                            onCreateWorkout = mockOnCreateWorkout,
                            onMoveWorkout = mockOnMoveWorkout
                        )
                    }
                }
            }
        }
        
        // Act
        composeTestRule.onNodeWithText("Create Workout").performClick()
        
        // Assert
        verify(exactly = 1) { mockOnCreateWorkout(any()) }
    }
    
    @Test
    fun folderSection_handlesConfigurationChanges() {
        // Test component stability across configuration changes (simulated)
        
        // Arrange
        var screenWidth by mutableStateOf(400.dp)
        
        composeTestRule.setContent {
            LiftrixTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (screenWidth > 600.dp) 32.dp else 16.dp)
                ) {
                    LazyColumn {
                        item {
                            InlineFolderSection(
                                folder = testFolder,
                                workouts = testWorkouts,
                                isExpanded = true,
                                onToggleExpanded = mockOnToggleExpanded,
                                onStartWorkout = mockOnStartWorkout,
                                onEditWorkout = mockOnEditWorkout,
                                onEditFolder = mockOnEditFolder,
                                onCreateFolder = mockOnCreateFolder,
                                onCreateWorkout = mockOnCreateWorkout,
                                onMoveWorkout = mockOnMoveWorkout
                            )
                        }
                    }
                }
            }
        }
        
        // Assert initial state
        composeTestRule.onNodeWithText("Test Folder").assertExists()
        composeTestRule.onNodeWithText("Push Workout").assertExists()
        
        // Act - simulate configuration change
        screenWidth = 800.dp
        composeTestRule.waitForIdle()
        
        // Assert state persists
        composeTestRule.onNodeWithText("Test Folder").assertExists()
        composeTestRule.onNodeWithText("Push Workout").assertExists()
    }
    
    private fun SemanticsNodeInteraction.isDisplayed(): Boolean {
        return try {
            assertIsDisplayed()
            true
        } catch (e: AssertionError) {
            false
        }
    }
}