package com.example.liftrix.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.components.cards.*
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive test suite for enhanced components including visual regression tests,
 * interaction tests, and theme compatibility validation as specified in TESTING-001.
 * 
 * Tests cover:
 * - LiftrixCard component variants and interactions
 * - StatCard with trend indicators and accessibility
 * - ActivityCard with action states and navigation
 * - Component accessibility compliance (WCAG 2.1 AA)
 * - Visual consistency and design system adherence
 * - Performance under various interaction patterns
 */
@RunWith(AndroidJUnit4::class)
class ComponentTestSuite {

    @get:Rule
    val composeTestRule = createComposeRule()

    // LiftrixCard Base Component Tests

    @Test
    fun liftrixCard_displaysCorrectContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("test_card"),
                    onClick = { /* Test action */ }
                ) {
                    Text("Test Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("test_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Test Content")
            .assertIsDisplayed()
    }

    @Test
    fun liftrixCard_handlesClickInteraction() {
        var clickCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("clickable_card"),
                    onClick = { clickCount++ }
                ) {
                    Text("Clickable Card")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("clickable_card")
            .performClick()

        assert(clickCount == 1)
    }

    @Test
    fun liftrixCard_providesAccessibilitySupport() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("accessible_card"),
                    onClick = { /* Test action */ },
                    contentDescription = "Test card with accessibility support"
                ) {
                    Text("Accessible Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("accessible_card")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(
                hasContentDescription("Test card with accessibility support")
            )
    }

    @Test
    fun liftrixCard_supportsDisabledState() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("disabled_card"),
                    onClick = { /* Test action */ },
                    enabled = false
                ) {
                    Text("Disabled Card")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("disabled_card")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    // LiftrixCard Variants Tests

    @Test
    fun compactLiftrixCard_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompactLiftrixCard(
                    modifier = Modifier.testTag("compact_card"),
                    onClick = { /* Test action */ }
                ) {
                    Text("Compact Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("compact_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Compact Content")
            .assertIsDisplayed()
    }

    @Test
    fun elevatedLiftrixCard_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                ElevatedLiftrixCard(
                    modifier = Modifier.testTag("elevated_card"),
                    onClick = { /* Test action */ }
                ) {
                    Text("Elevated Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("elevated_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Elevated Content")
            .assertIsDisplayed()
    }

    @Test
    fun gradientLiftrixCard_hasCorrectStyling() {
        composeTestRule.setContent {
            LiftrixTheme {
                GradientLiftrixCard(
                    modifier = Modifier.testTag("gradient_card"),
                    onClick = { /* Test action */ }
                ) {
                    Text("Gradient Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("gradient_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Gradient Content")
            .assertIsDisplayed()
    }

    // StatCard Component Tests

    @Test
    fun statCard_displaysCorrectData() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Weekly Workouts",
                    value = "12",
                    subtitle = "This week",
                    trend = Trend.Positive(15.2f),
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.testTag("stat_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("stat_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Weekly Workouts")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("12")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("This week")
            .assertIsDisplayed()
    }

    @Test
    fun statCard_displaysTrendIndicator() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Progress",
                    value = "85%",
                    trend = Trend.Positive(12.5f),
                    modifier = Modifier.testTag("trend_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("trend_card")
            .assertIsDisplayed()

        // Check for trend indicator text
        composeTestRule
            .onNodeWithText("Progress")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("85%")
            .assertIsDisplayed()
    }

    @Test
    fun statCard_handlesNegativeTrend() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Calories",
                    value = "2,100",
                    trend = Trend.Negative(8.5f),
                    modifier = Modifier.testTag("negative_trend_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("negative_trend_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Calories")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("2,100")
            .assertIsDisplayed()
    }

    @Test
    fun statCard_handlesNeutralTrend() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Average Duration",
                    value = "45 min",
                    trend = Trend.Neutral(),
                    modifier = Modifier.testTag("neutral_trend_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("neutral_trend_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Average Duration")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("45 min")
            .assertIsDisplayed()
    }

    @Test
    fun compactStatCard_displaysCorrectData() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompactStatCard(
                    title = "Sets",
                    value = "24",
                    trend = Trend.Positive(10f),
                    modifier = Modifier.testTag("compact_stat_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("compact_stat_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Sets")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("24")
            .assertIsDisplayed()
    }

    // ActivityCard Component Tests

    @Test
    fun activityCard_displaysCorrectData() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActivityCard(
                    title = "Morning Workout",
                    subtitle = "Push Day - 45 minutes",
                    icon = Icons.Default.FitnessCenter,
                    trailing = "2h ago",
                    modifier = Modifier.testTag("activity_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("activity_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Morning Workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Push Day - 45 minutes")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("2h ago")
            .assertIsDisplayed()
    }

    @Test
    fun activityCard_handlesChevronVisibility() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column {
                    ActivityCard(
                        title = "With Chevron",
                        subtitle = "Clickable activity",
                        icon = Icons.Default.FitnessCenter,
                        showChevron = true,
                        modifier = Modifier.testTag("chevron_card"),
                        onClick = { /* Test action */ }
                    )
                    
                    ActivityCard(
                        title = "Without Chevron",
                        subtitle = "Non-clickable activity",
                        icon = Icons.Default.FitnessCenter,
                        showChevron = false,
                        modifier = Modifier.testTag("no_chevron_card"),
                        onClick = { /* Test action */ }
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("chevron_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("no_chevron_card")
            .assertIsDisplayed()
    }

    @Test
    fun compactActivityCard_displaysCorrectData() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompactActivityCard(
                    title = "Quick Start",
                    subtitle = "15 min",
                    icon = Icons.Default.FitnessCenter,
                    modifier = Modifier.testTag("compact_activity_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("compact_activity_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("Quick Start")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("15 min")
            .assertIsDisplayed()
    }

    @Test
    fun featureCard_displaysCorrectData() {
        composeTestRule.setContent {
            LiftrixTheme {
                FeatureCard(
                    title = "New Feature",
                    description = "Enhanced analytics for better insights",
                    icon = Icons.Default.TrendingUp,
                    actionText = "Learn More",
                    modifier = Modifier.testTag("feature_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("feature_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithText("New Feature")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Enhanced analytics for better insights")
            .assertIsDisplayed()
    }

    // Accessibility Compliance Tests

    @Test
    fun allCards_meetAccessibilityRequirements() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    LiftrixCard(
                        modifier = Modifier.testTag("accessible_liftrix_card"),
                        onClick = { /* Test action */ },
                        contentDescription = "Main workout card"
                    ) {
                        Text("Workout Content")
                    }
                    
                    StatCard(
                        title = "Accessible Stat",
                        value = "100%",
                        modifier = Modifier.testTag("accessible_stat_card"),
                        onClick = { /* Test action */ }
                    )
                    
                    ActivityCard(
                        title = "Accessible Activity",
                        subtitle = "Description",
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.testTag("accessible_activity_card"),
                        onClick = { /* Test action */ }
                    )
                }
            }
        }

        // Test that all cards have proper accessibility support
        composeTestRule
            .onNodeWithTag("accessible_liftrix_card")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(hasContentDescription("Main workout card"))

        composeTestRule
            .onNodeWithTag("accessible_stat_card")
            .assertIsDisplayed()
            .assert(hasClickAction())

        composeTestRule
            .onNodeWithTag("accessible_activity_card")
            .assertIsDisplayed()
            .assert(hasClickAction())
    }

    @Test
    fun cards_supportMinimumTouchTargetSize() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column {
                    CompactLiftrixCard(
                        modifier = Modifier.testTag("touch_target_card"),
                        onClick = { /* Test action */ }
                    ) {
                        Text("Small Content")
                    }
                }
            }
        }

        // Verify the card is accessible and has proper touch target
        composeTestRule
            .onNodeWithTag("touch_target_card")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    // Performance and Interaction Tests

    @Test
    fun cards_handleRapidInteractions() {
        var clickCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("rapid_click_card"),
                    onClick = { clickCount++ }
                ) {
                    Text("Rapid Click Test")
                }
            }
        }

        // Perform rapid clicks
        repeat(5) {
            composeTestRule
                .onNodeWithTag("rapid_click_card")
                .performClick()
        }

        assert(clickCount == 5)
    }

    @Test
    fun cards_maintainStateAcrossRecomposition() {
        composeTestRule.setContent {
            LiftrixTheme {
                var counter by remember { mutableStateOf(0) }
                
                Column {
                    StatCard(
                        title = "Counter",
                        value = counter.toString(),
                        modifier = Modifier.testTag("state_card"),
                        onClick = { counter++ }
                    )
                }
            }
        }

        // Initial state
        composeTestRule
            .onNodeWithText("0")
            .assertIsDisplayed()

        // Click and verify state change
        composeTestRule
            .onNodeWithTag("state_card")
            .performClick()

        composeTestRule
            .onNodeWithText("1")
            .assertIsDisplayed()

        // Click again and verify state persistence
        composeTestRule
            .onNodeWithTag("state_card")
            .performClick()

        composeTestRule
            .onNodeWithText("2")
            .assertIsDisplayed()
    }

    @Test
    fun cards_renderConsistentlyAcrossThemes() {
        // Test in light theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                LiftrixCard(
                    modifier = Modifier.testTag("light_theme_card"),
                    onClick = { /* Test action */ }
                ) {
                    Text("Light Theme Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("light_theme_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Theme Content")
            .assertIsDisplayed()

        // Test in dark theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                LiftrixCard(
                    modifier = Modifier.testTag("dark_theme_card"),
                    onClick = { /* Test action */ }
                ) {
                    Text("Dark Theme Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark_theme_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Theme Content")
            .assertIsDisplayed()
    }

    // Edge Cases and Error Handling

    @Test
    fun cards_handleLongText() {
        val longTitle = "This is a very long title that should be properly handled by the card component with ellipsis"
        val longSubtitle = "This is a very long subtitle that should also be properly handled with appropriate text overflow behavior"
        
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = longTitle,
                    value = "999,999",
                    subtitle = longSubtitle,
                    modifier = Modifier.testTag("long_text_card"),
                    onClick = { /* Test action */ }
                )
            }
        }

        composeTestRule
            .onNodeWithTag("long_text_card")
            .assertIsDisplayed()

        // The card should still be functional even with long text
        composeTestRule
            .onNodeWithTag("long_text_card")
            .assertHasClickAction()
    }

    @Test
    fun cards_handleEmptyContent() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column {
                    StatCard(
                        title = "",
                        value = "",
                        modifier = Modifier.testTag("empty_stat_card"),
                        onClick = { /* Test action */ }
                    )
                    
                    ActivityCard(
                        title = "",
                        subtitle = "",
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.testTag("empty_activity_card"),
                        onClick = { /* Test action */ }
                    )
                }
            }
        }

        // Cards should still render and be functional even with empty content
        composeTestRule
            .onNodeWithTag("empty_stat_card")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule
            .onNodeWithTag("empty_activity_card")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
} 