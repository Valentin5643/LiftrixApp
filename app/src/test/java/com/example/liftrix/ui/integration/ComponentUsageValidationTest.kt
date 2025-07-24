package com.example.liftrix.ui.integration

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Component Usage Validation Test
 * 
 * Unit tests to ensure no legacy components remain in the codebase and validate
 * proper UnifiedWorkoutCard and ModernActionButton integration across all screens.
 * 
 * This test suite performs static code analysis to verify INTEG-001 compliance:
 * - No legacy LiftrixCard or ElevatedLiftrixCard imports remain  
 * - All workout screens import and use UnifiedWorkoutCard
 * - All workout screens import and use ModernActionButton components
 * - Consistent component usage patterns across the codebase
 */
@RunWith(JUnit4::class)
class ComponentUsageValidationTest {
    
    private val projectRoot = File(System.getProperty("user.dir"))
    private val sourceDir = File(projectRoot, "app/src/main/java/com/example/liftrix/ui")
    
    @Test
    fun noLegacyCardImports_remainInWorkoutScreens() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Verify no legacy LiftrixCard imports
            assertFalse(
                content.contains("import com.example.liftrix.ui.components.cards.LiftrixCard"),
                "File ${file.name} still imports legacy LiftrixCard"
            )
            
            // Verify no legacy ElevatedLiftrixCard imports
            assertFalse(
                content.contains("import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard"),
                "File ${file.name} still imports legacy ElevatedLiftrixCard"
            )
        }
    }
    
    @Test
    fun allWorkoutScreens_importUnifiedWorkoutCard() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Verify UnifiedWorkoutCard import is present
            assertTrue(
                content.contains("import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard"),
                "File ${file.name} missing UnifiedWorkoutCard import"
            )
        }
    }
    
    @Test
    fun allWorkoutScreens_importModernActionButtons() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Verify at least one ModernActionButton import is present
            val hasModernButtonImport = content.contains("import com.example.liftrix.ui.workout.components.PrimaryActionButton") ||
                                       content.contains("import com.example.liftrix.ui.workout.components.SecondaryActionButton") ||
                                       content.contains("import com.example.liftrix.ui.workout.components.TertiaryActionButton")
            
            assertTrue(
                hasModernButtonImport,
                "File ${file.name} missing ModernActionButton imports"
            )
        }
    }
    
    @Test
    fun noLegacyCardUsage_inWorkoutScreens() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Verify no legacy card component usage
            assertFalse(
                content.contains("LiftrixCard(") && !content.contains("UnifiedWorkoutCard("),
                "File ${file.name} still uses legacy LiftrixCard component"
            )
            
            assertFalse(
                content.contains("ElevatedLiftrixCard("),
                "File ${file.name} still uses legacy ElevatedLiftrixCard component"
            )
        }
    }
    
    @Test
    fun unifiedWorkoutCard_isUsedConsistently() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // If file contains card usage, it should be UnifiedWorkoutCard
            if (content.contains("Card(") && !content.contains("// Legacy card")) {
                assertTrue(
                    content.contains("UnifiedWorkoutCard("),
                    "File ${file.name} should use UnifiedWorkoutCard for card components"
                )
            }
        }
    }
    
    @Test
    fun modernActionButtons_areUsedConsistently() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Count usage of modern vs legacy buttons
            val modernButtonUsage = content.split("PrimaryActionButton(").size - 1 +
                                  content.split("SecondaryActionButton(").size - 1 +
                                  content.split("TertiaryActionButton(").size - 1
                                  
            val legacyButtonUsage = content.split("Button(").size - 1 +
                                   content.split("OutlinedButton(").size - 1 +
                                   content.split("TextButton(").size - 1 +
                                   content.split("FilledTonalButton(").size - 1
            
            // Modern buttons should be used more than legacy buttons in workout screens
            // (allowing some legacy usage for specific cases like IconButton, etc.)
            if (modernButtonUsage > 0) {
                assertTrue(
                    modernButtonUsage >= legacyButtonUsage / 2,
                    "File ${file.name} should predominantly use ModernActionButton components. " +
                    "Modern: $modernButtonUsage, Legacy: $legacyButtonUsage"
                )
            }
        }
    }
    
    @Test
    fun homeScreen_usesUnifiedComponents() {
        val homeScreenFile = File(sourceDir, "home/HomeScreen.kt")
        
        if (homeScreenFile.exists()) {
            val content = homeScreenFile.readText()
            
            // Verify HomeScreen imports unified components
            assertTrue(
                content.contains("import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard"),
                "HomeScreen.kt missing UnifiedWorkoutCard import"
            )
            
            assertTrue(
                content.contains("import com.example.liftrix.ui.workout.components.PrimaryActionButton"),
                "HomeScreen.kt missing PrimaryActionButton import"
            )
            
            // Verify no legacy card usage
            assertFalse(
                content.contains("LiftrixCard(") && !content.contains("UnifiedWorkoutCard("),
                "HomeScreen.kt still uses legacy card components"
            )
        }
    }
    
    @Test
    fun componentStyleConsistency_isValidated() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Verify UnifiedWorkoutCard is used with proper parameters
            if (content.contains("UnifiedWorkoutCard(")) {
                // Should have title parameter
                assertTrue(
                    content.contains("title ="),
                    "UnifiedWorkoutCard in ${file.name} should have title parameter"
                )
            }
            
            // Verify ModernActionButton usage follows hierarchy
            if (content.contains("PrimaryActionButton(")) {
                assertTrue(
                    content.contains("text =") || content.contains("onClick ="),
                    "PrimaryActionButton in ${file.name} should have required parameters"
                )
            }
        }
    }
    
    @Test
    fun accessibilitySemantics_arePresent() {
        val workoutScreenFiles = getWorkoutScreenFiles()
        
        workoutScreenFiles.forEach { file ->
            val content = file.readText()
            
            // Verify accessibility imports are present where components are used
            if (content.contains("UnifiedWorkoutCard(") || content.contains("ActionButton(")) {
                assertTrue(
                    content.contains("contentDescription") ||
                    content.contains("AccessibilityUtils") ||
                    content.contains("semantics"),
                    "File ${file.name} should include accessibility semantics for components"
                )
            }
        }
    }
    
    /**
     * Helper function to get all workout-related screen files
     */
    private fun getWorkoutScreenFiles(): List<File> {
        val workoutScreens = mutableListOf<File>()
        
        // Add specific screen files as per INTEG-001 task requirements
        val screenPaths = listOf(
            "home/HomeScreen.kt",
            "workout/WorkoutScreen.kt",
            "workout/create/CreateWorkoutScreen.kt",
            "workout/active/ActiveWorkoutScreen.kt",
            "workout/edit/EditWorkoutScreen.kt",
            "workout/WorkoutTemplateScreen.kt",
            "progress/DashboardScreen.kt"
        )
        
        screenPaths.forEach { path ->
            val file = File(sourceDir, path)
            if (file.exists()) {
                workoutScreens.add(file)
            }
        }
        
        return workoutScreens
    }
    
    @Test
    fun allRequiredScreenFiles_exist() {
        val requiredScreens = listOf(
            "home/HomeScreen.kt",
            "workout/WorkoutScreen.kt", 
            "workout/create/CreateWorkoutScreen.kt",
            "workout/active/ActiveWorkoutScreen.kt"
        )
        
        requiredScreens.forEach { screenPath ->
            val file = File(sourceDir, screenPath)
            assertTrue(
                file.exists(),
                "Required screen file $screenPath does not exist"
            )
        }
    }
    
    @Test
    fun unifiedComponents_areProperlyDefined() {
        val unifiedWorkoutCardFile = File(sourceDir, "workout/components/UnifiedWorkoutCard.kt")
        val modernActionButtonFile = File(sourceDir, "workout/components/ModernActionButton.kt")
        
        assertTrue(
            unifiedWorkoutCardFile.exists(),
            "UnifiedWorkoutCard component file does not exist"
        )
        
        assertTrue(
            modernActionButtonFile.exists(),
            "ModernActionButton component file does not exist"
        )
        
        // Verify component files have proper exports
        val cardContent = unifiedWorkoutCardFile.readText()
        assertTrue(
            cardContent.contains("fun UnifiedWorkoutCard("),
            "UnifiedWorkoutCard.kt missing main component function"
        )
        
        val buttonContent = modernActionButtonFile.readText()
        assertTrue(
            buttonContent.contains("fun PrimaryActionButton(") &&
            buttonContent.contains("fun SecondaryActionButton(") &&
            buttonContent.contains("fun TertiaryActionButton("),
            "ModernActionButton.kt missing required button functions"
        )
    }
}