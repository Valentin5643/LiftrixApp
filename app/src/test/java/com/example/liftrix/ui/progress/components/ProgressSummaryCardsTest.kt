package com.example.liftrix.ui.progress.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for ProgressSummaryCards component.
 * Tests component rendering, loading states, and accessibility features.
 */
@RunWith(AndroidJUnit4::class)
class ProgressSummaryCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun progressSummaryCards_displaysTitle() {
        // Arrange
        val mockSummaryData = createMockProgressSummary()
        
        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressSummaryCards(
                    summaryData = mockSummaryData,
                    isLoading = false
                )
            }
        }
        
        // Assert
        composeTestRule
            .onNodeWithText("Progress Summary")
            .assertIsDisplayed()
    }

    @Test
    fun progressSummaryCards_displaysAllStatistics_whenNotLoading() {
        // Arrange
        val mockSummaryData = createMockProgressSummary()
        
        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressSummaryCards(
                    summaryData = mockSummaryData,
                    isLoading = false
                )
            }
        }
        
        // Assert
        composeTestRule
            .onNodeWithText("25")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Total Workouts")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("7 days")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Current Streak")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("14 days")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Longest Streak")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("45min")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Avg Duration")
            .assertIsDisplayed()
    }

    @Test
    fun progressSummaryCards_displaysLoadingState_whenLoading() {
        // Arrange
        val mockSummaryData = createMockProgressSummary()
        
        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressSummaryCards(
                    summaryData = mockSummaryData,
                    isLoading = true
                )
            }
        }
        
        // Assert
        composeTestRule
            .onNodeWithText("Loading progress summary...")
            .assertIsDisplayed()
    }

    @Test
    fun progressSummaryCards_hasProperAccessibilityDescriptions() {
        // Arrange
        val mockSummaryData = createMockProgressSummary()
        
        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressSummaryCards(
                    summaryData = mockSummaryData,
                    isLoading = false
                )
            }
        }
        
        // Assert
        composeTestRule
            .onNodeWithContentDescription("Total workouts completed: 25")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Current workout streak: 7 days")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Longest workout streak: 14 days")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Average workout duration: 45 minutes")
            .assertIsDisplayed()
    }

    @Test
    fun progressSummaryCards_displaysZeroValues_correctly() {
        // Arrange
        val mockSummaryData = ProgressSummary(
            totalWorkouts = 0,
            totalVolume = 0f,
            averageDuration = 0,
            currentStreak = 0,
            longestStreak = 0,
            averageWorkoutsPerWeek = 0f,
            totalActiveTime = 0
        )
        
        // Act
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressSummaryCards(
                    summaryData = mockSummaryData,
                    isLoading = false
                )
            }
        }
        
        // Assert
        composeTestRule
            .onNodeWithText("0")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("0 days")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("0min")
            .assertIsDisplayed()
    }

    /**
     * Helper function to create mock ProgressSummary data for testing
     */
    private fun createMockProgressSummary(): ProgressSummary {
        return ProgressSummary(
            totalWorkouts = 25,
            totalVolume = 12500f,
            averageDuration = 45,
            currentStreak = 7,
            longestStreak = 14,
            averageWorkoutsPerWeek = 3.5f,
            totalActiveTime = 1125
        )
    }
} 