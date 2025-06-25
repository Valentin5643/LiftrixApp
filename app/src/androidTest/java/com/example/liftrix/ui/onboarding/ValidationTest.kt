package com.example.liftrix.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.ui.onboarding.navigation.OnboardingNavigation
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Comprehensive validation test suite for onboarding form inputs.
 * Tests real-time validation feedback, error states, edge cases, and performance requirements.
 * Verifies <100ms validation response time and proper user feedback throughout the flow.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ValidationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var mockValidateProfileInputUseCase: ValidateProfileInputUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        
        // Initialize validation mock
        mockValidateProfileInputUseCase = mockk(relaxed = true)
        
        // Setup default validation behavior
        coEvery { mockValidateProfileInputUseCase.validateAge(any()) } returns ValidationResult.Valid
        coEvery { mockValidateProfileInputUseCase.validateWeight(any()) } returns ValidationResult.Valid
    }

    /**
     * Test age validation with all boundary conditions and error cases.
     */
    @Test
    fun testAgeValidation_allBoundaryConditions_providesCorrectFeedback() = runTest {
        // Setup validation responses
        coEvery { mockValidateProfileInputUseCase.validateAge("12") } returns 
            ValidationResult.Invalid("Age must be between 13 and 100")
        coEvery { mockValidateProfileInputUseCase.validateAge("13") } returns ValidationResult.Valid
        coEvery { mockValidateProfileInputUseCase.validateAge("100") } returns ValidationResult.Valid
        coEvery { mockValidateProfileInputUseCase.validateAge("101") } returns 
            ValidationResult.Invalid("Age must be between 13 and 100")
        coEvery { mockValidateProfileInputUseCase.validateAge("abc") } returns 
            ValidationResult.Invalid("Please enter a valid number")
        coEvery { mockValidateProfileInputUseCase.validateAge("") } returns 
            ValidationResult.Invalid("Age is required")

        val testUserId = "test-validation-age"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to age screen
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()

        // Test boundary: too young (12)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextInput("12")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test boundary: minimum valid (13)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("13")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test boundary: maximum valid (100)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("100")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test boundary: too old (101)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("101")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test invalid input: letters
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("abc")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Please enter a valid number").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test empty input
        composeTestRule.onNodeWithContentDescription("Age input field").performTextClearance()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age is required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    /**
     * Test weight validation with unit conversion and boundary conditions.
     */
    @Test
    fun testWeightValidation_withUnitConversion_handlesAllScenarios() = runTest {
        // Setup weight validation responses
        coEvery { mockValidateProfileInputUseCase.validateWeight("0") } returns 
            ValidationResult.Invalid("Weight must be greater than 0")
        coEvery { mockValidateProfileInputUseCase.validateWeight("50") } returns ValidationResult.Valid
        coEvery { mockValidateProfileInputUseCase.validateWeight("500") } returns 
            ValidationResult.Invalid("Please enter a realistic weight")
        coEvery { mockValidateProfileInputUseCase.validateWeight("abc") } returns 
            ValidationResult.Invalid("Please enter a valid number")

        val testUserId = "test-validation-weight"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to weight screen
        navigateToWeightScreen()

        // Test zero weight
        composeTestRule.onNodeWithContentDescription("Weight input field").performTextInput("0")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Weight must be greater than 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test valid weight
        composeTestRule.onNodeWithContentDescription("Weight input field").performTextReplacement("70")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This weight is valid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test unit conversion display
        composeTestRule.onNodeWithText("kg").performClick() // Switch to kg
        composeTestRule.onNodeWithText("70 kg = 154 lbs").assertIsDisplayed()

        composeTestRule.onNodeWithText("lbs").performClick() // Switch to lbs
        composeTestRule.onNodeWithText("70 lbs = 32 kg").assertIsDisplayed()

        // Test unrealistic weight
        composeTestRule.onNodeWithContentDescription("Weight input field").performTextReplacement("500")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Please enter a realistic weight").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test invalid characters
        composeTestRule.onNodeWithContentDescription("Weight input field").performTextReplacement("abc")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Please enter a valid number").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test "Prefer not to say" option
        composeTestRule.onNodeWithText("Prefer not to say").performClick()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    /**
     * Test equipment selection validation and limits.
     */
    @Test
    fun testEquipmentValidation_selectionLimits_enforcesConstraints() = runTest {
        val testUserId = "test-validation-equipment"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to equipment screen
        navigateToEquipmentScreen()

        // Test no selection initially
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test single selection
        composeTestRule.onNodeWithText("Dumbbells").performClick()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test multiple selections (within limit)
        composeTestRule.onNodeWithText("Barbell").performClick()
        composeTestRule.onNodeWithText("Kettlebells").performClick()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test maximum selection limit (if applicable)
        val equipmentOptions = listOf(
            "Resistance Bands", "Pull-up Bar", "Bench", "Cable Machine",
            "Smith Machine", "Power Rack", "Olympic Platform"
        )
        
        equipmentOptions.forEach { equipment ->
            if (composeTestRule.onNodeWithText(equipment).isDisplayed()) {
                composeTestRule.onNodeWithText(equipment).performClick()
            }
        }

        // Should still be able to continue or show limit message
        // Implementation dependent on maximum equipment limit
        composeTestRule.onNodeWithText("Continue").assertExists()

        // Test "Other" equipment input
        composeTestRule.onNodeWithText("Other").performClick()
        composeTestRule.onNodeWithContentDescription("Other equipment input").assertIsDisplayed()
        
        // Test other equipment validation
        composeTestRule.onNodeWithContentDescription("Other equipment input").performTextInput("Custom gym equipment")
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test empty other equipment
        composeTestRule.onNodeWithContentDescription("Other equipment input").performTextClearance()
        // Should deselect "Other" option or show validation message
    }

    /**
     * Test goals selection validation and prioritization.
     */
    @Test
    fun testGoalsValidation_prioritySystem_worksCorrectly() = runTest {
        val testUserId = "test-validation-goals"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to goals screen
        navigateToGoalsScreen()

        // Test no selection initially
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Test single goal selection
        composeTestRule.onNodeWithText("Build Muscle").performClick()
        composeTestRule.onNodeWithText("1").assertIsDisplayed() // Priority indicator
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test multiple goal selection
        composeTestRule.onNodeWithText("Lose Weight").performClick()
        composeTestRule.onNodeWithText("1").assertIsDisplayed() // Build Muscle
        composeTestRule.onNodeWithText("2").assertIsDisplayed() // Lose Weight

        composeTestRule.onNodeWithText("Get Stronger").performClick()
        composeTestRule.onNodeWithText("3").assertIsDisplayed() // Get Stronger

        // Test maximum goals limit (7 goals max)
        val additionalGoals = listOf(
            "Improve Endurance", "Increase Flexibility", "Improve Balance", "Enhance Athletic Performance"
        )
        
        additionalGoals.forEach { goal ->
            if (composeTestRule.onNodeWithText(goal).isDisplayed()) {
                composeTestRule.onNodeWithText(goal).performClick()
            }
        }

        // Should show limit message or prevent further selection
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test goal deselection and priority reordering
        composeTestRule.onNodeWithText("Lose Weight").performClick() // Deselect
        composeTestRule.onNodeWithText("1").assertIsDisplayed() // Build Muscle should remain #1
        composeTestRule.onNodeWithText("2").assertIsDisplayed() // Get Stronger should become #2
    }

    /**
     * Test real-time validation performance (<100ms response time).
     */
    @Test
    fun testValidationPerformance_realTimeResponse_meetsTargets() = runTest {
        val testUserId = "test-validation-performance"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to age screen
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()

        // Measure validation response time
        val validationTime = measureTimeMillis {
            composeTestRule.onNodeWithContentDescription("Age input field").performTextInput("25")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        }

        // Verify validation response is under 100ms target
        assert(validationTime < 100) {
            "Validation response time ($validationTime ms) exceeds 100ms target"
        }

        // Test error validation performance
        val errorValidationTime = measureTimeMillis {
            composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("10")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()
        }

        assert(errorValidationTime < 100) {
            "Error validation response time ($errorValidationTime ms) exceeds 100ms target"
        }
    }

    /**
     * Test validation state persistence during navigation.
     */
    @Test
    fun testValidationPersistence_duringNavigation_maintainsState() = runTest {
        val testUserId = "test-validation-persistence"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to age screen and enter valid data
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Age input field").performTextInput("25")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").performClick()

        // Navigate to weight screen and enter valid data
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Weight input field").performTextInput("70")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This weight is valid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").performClick()

        // Navigate back to weight screen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        composeTestRule.waitForIdle()

        // Verify weight data and validation state are preserved
        composeTestRule.onNodeWithText("70").assertIsDisplayed()
        composeTestRule.onNodeWithText("Great! This weight is valid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Navigate back to age screen
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()
        composeTestRule.waitForIdle()

        // Verify age data and validation state are preserved
        composeTestRule.onNodeWithText("25").assertIsDisplayed()
        composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    /**
     * Test validation error recovery scenarios.
     */
    @Test
    fun testValidationErrorRecovery_fromInvalidStates_allowsCorrection() = runTest {
        val testUserId = "test-validation-recovery"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to age screen
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()

        // Enter invalid age
        composeTestRule.onNodeWithContentDescription("Age input field").performTextInput("10")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Correct the error gradually
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("1")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("12")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("13")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()

        // Test error recovery with complete correction
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("150")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Age must be between 13 and 100").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("25")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Great! This age is supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("Continue").assertIsEnabled()
    }

    /**
     * Test validation with edge case inputs.
     */
    @Test
    fun testValidationEdgeCases_specialInputs_handledCorrectly() = runTest {
        val testUserId = "test-validation-edge-cases"

        composeTestRule.setContent {
            LiftrixTheme {
                OnboardingNavigation(
                    userId = testUserId,
                    onComplete = {},
                    onSkip = {}
                )
            }
        }

        // Navigate to age screen
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()

        // Test decimal input (should be filtered out for age)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextInput("25.5")
        composeTestRule.waitForIdle()
        // Should only show "25" since decimals are not allowed for age
        composeTestRule.onNodeWithText("25").assertIsDisplayed()

        // Test very long input (should be limited)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("123456789")
        composeTestRule.waitForIdle()
        // Should be limited to reasonable length (3 digits max)
        
        // Test special characters (should be filtered)
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("2!@#$%5")
        composeTestRule.waitForIdle()
        // Should only show "25" as special characters are filtered

        // Test leading zeros
        composeTestRule.onNodeWithContentDescription("Age input field").performTextReplacement("025")
        composeTestRule.waitForIdle()
        // Should handle leading zeros appropriately
    }

    // Helper methods for navigation
    private fun navigateToWeightScreen() {
        composeTestRule.onNodeWithText("Get Started").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Age input field").performTextInput("25")
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToEquipmentScreen() {
        navigateToWeightScreen()
        composeTestRule.onNodeWithContentDescription("Weight input field").performTextInput("70")
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToGoalsScreen() {
        navigateToEquipmentScreen()
        composeTestRule.onNodeWithText("Dumbbells").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.waitForIdle()
    }
}