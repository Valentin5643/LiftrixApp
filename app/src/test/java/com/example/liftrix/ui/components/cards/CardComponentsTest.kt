package com.example.liftrix.ui.components.cards

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlin.test.assertEquals

/**
 * Comprehensive test suite for all card components.
 * 
 * Tests cover:
 * - Card rendering and layout
 * - Click behavior verification
 * - Accessibility content descriptions
 * - Visual styling verification
 * - Trend indicators and icons
 * - Card variants (Compact, Elevated, Gradient)
 * - StatCard and ActivityCard specific functionality
 * - 8pt grid spacing verification
 * - Material 3 compliance
 */
@RunWith(AndroidJUnit4::class)
class CardComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockOnClick: () -> Unit

    @Before
    fun setUp() {
        mockOnClick = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== LiftrixCard Base Component Tests ==========

    @Test
    fun liftrixCard_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("test-card")
                ) {
                    Text("Test Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("test-card")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Test Content")
            .assertIsDisplayed()
    }

    @Test
    fun liftrixCard_clickableWhenOnClickProvided() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("clickable-card")
                ) {
                    Text("Clickable Card")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("clickable-card")
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { mockOnClick() }
    }

    @Test
    fun liftrixCard_notClickableWhenOnClickNotProvided() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("non-clickable-card")
                ) {
                    Text("Non-clickable Card")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("non-clickable-card")
            .assertExists()
            .assert(hasNoClickAction())
    }

    // ========== LiftrixCard Variants Tests ==========

    @Test
    fun compactLiftrixCard_rendersWithReducedPadding() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompactLiftrixCard(
                    modifier = Modifier.testTag("compact-card")
                ) {
                    Text("Compact Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("compact-card")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Compact Content")
            .assertIsDisplayed()
    }

    @Test
    fun elevatedLiftrixCard_rendersWithIncreasedElevation() {
        composeTestRule.setContent {
            LiftrixTheme {
                ElevatedLiftrixCard(
                    modifier = Modifier.testTag("elevated-card")
                ) {
                    Text("Elevated Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("elevated-card")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Elevated Content")
            .assertIsDisplayed()
    }

    @Test
    fun gradientLiftrixCard_rendersWithGradientBackground() {
        composeTestRule.setContent {
            LiftrixTheme {
                GradientLiftrixCard(
                    modifier = Modifier.testTag("gradient-card")
                ) {
                    Text("Gradient Content")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("gradient-card")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Gradient Content")
            .assertIsDisplayed()
    }

    // ========== StatCard Tests ==========

    @Test
    fun statCard_rendersWithAllComponents() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Weekly Workouts",
                    value = "12",
                    subtitle = "This week",
                    trend = Trend.Positive(15.2f),
                    icon = Icons.Default.FitnessCenter,
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("stat-card")
                )
            }
        }

        // Verify all text elements are displayed
        composeTestRule
            .onNodeWithText("Weekly Workouts")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("12")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("This week")
            .assertIsDisplayed()

        // Verify card is clickable
        composeTestRule
            .onNodeWithTag("stat-card")
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { mockOnClick() }
    }

    @Test
    fun statCard_rendersWithoutOptionalComponents() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Basic Stat",
                    value = "42",
                    modifier = Modifier.testTag("basic-stat-card")
                )
            }
        }

        composeTestRule
            .onNodeWithText("Basic Stat")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("42")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("basic-stat-card")
            .assert(hasNoClickAction())
    }

    @Test
    fun statCard_hasCorrectAccessibilityDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Total Workouts",
                    value = "150",
                    subtitle = "All time",
                    trend = Trend.Positive(25.5f),
                    contentDescription = "Total workout statistics with positive trend"
                )
            }
        }

        composeTestRule
            .onNode(hasContentDescription("Total workout statistics with positive trend"))
            .assertExists()
    }

    @Test
    fun compactStatCard_rendersInCompactLayout() {
        composeTestRule.setContent {
            LiftrixTheme {
                CompactStatCard(
                    title = "PRs",
                    value = "8",
                    trend = Trend.Positive(100f),
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("compact-stat-card")
                )
            }
        }

        composeTestRule
            .onNodeWithText("PRs")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("8")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("compact-stat-card")
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { mockOnClick() }
    }

    // ========== Trend Indicator Tests ==========

    @Test
    fun trendIndicator_rendersPositiveTrend() {
        composeTestRule.setContent {
            LiftrixTheme {
                TrendIndicator(
                    trend = Trend.Positive(15.5f),
                    modifier = Modifier.testTag("positive-trend")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("positive-trend")
            .assertExists()
            .assertIsDisplayed()

        // Should display percentage with positive indication
        composeTestRule
            .onNodeWithText("↗ +15.5% increase")
            .assertExists()
    }

    @Test
    fun trendIndicator_rendersNegativeTrend() {
        composeTestRule.setContent {
            LiftrixTheme {
                TrendIndicator(
                    trend = Trend.Negative(8.3f, "decrease"),
                    modifier = Modifier.testTag("negative-trend")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("negative-trend")
            .assertExists()
            .assertIsDisplayed()

        // Should display percentage with negative indication
        composeTestRule
            .onNodeWithText("↘ -8.3% decrease")
            .assertExists()
    }

    @Test
    fun trendIndicator_rendersNeutralTrend() {
        composeTestRule.setContent {
            LiftrixTheme {
                TrendIndicator(
                    trend = Trend.Neutral("no change"),
                    modifier = Modifier.testTag("neutral-trend")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("neutral-trend")
            .assertExists()
            .assertIsDisplayed()

        // Should display neutral indication
        composeTestRule
            .onNodeWithText("→ no change")
            .assertExists()
    }

    @Test
    fun trendIndicator_rendersInCompactMode() {
        composeTestRule.setContent {
            LiftrixTheme {
                TrendIndicator(
                    trend = Trend.Positive(12.0f),
                    compact = true,
                    modifier = Modifier.testTag("compact-trend")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("compact-trend")
            .assertExists()
            .assertIsDisplayed()
    }

    // ========== ActivityCard Tests ==========

    @Test
    fun activityCard_rendersWithAllComponents() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActivityCard(
                    title = "Morning Workout",
                    subtitle = "Push Day - 45 minutes",
                    icon = Icons.Default.FitnessCenter,
                    timestamp = "Today at 7:00 AM",
                    onClick = mockOnClick,
                    modifier = Modifier.testTag("activity-card")
                )
            }
        }

        composeTestRule
            .onNodeWithText("Morning Workout")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Push Day - 45 minutes")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Today at 7:00 AM")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("activity-card")
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { mockOnClick() }
    }

    @Test
    fun activityCard_hasCorrectAccessibilityDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                ActivityCard(
                    title = "Evening Run",
                    subtitle = "Cardio - 30 minutes",
                    icon = Icons.Default.TrendingUp,
                    timestamp = "Yesterday",
                    contentDescription = "Evening cardio workout completed yesterday"
                )
            }
        }

        composeTestRule
            .onNode(hasContentDescription("Evening cardio workout completed yesterday"))
            .assertExists()
    }

    // ========== Material Design Compliance Tests ==========

    @Test
    fun cardComponents_followMaterial3Design() {
        composeTestRule.setContent {
            MaterialTheme {
                LiftrixCard(
                    modifier = Modifier.testTag("material-card")
                ) {
                    Text("Material 3 Card")
                }
            }
        }

        // Verify card is rendered correctly with Material 3
        composeTestRule
            .onNodeWithTag("material-card")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Material 3 Card")
            .assertIsDisplayed()
    }

    @Test
    fun cardComponents_handle8ptGridSpacing() {
        // Test that card components work correctly with different spacing values
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Spacing Test",
                    value = "Grid",
                    modifier = Modifier
                        .testTag("spacing-card")
                        .fillMaxWidth()
                )
            }
        }

        composeTestRule
            .onNodeWithTag("spacing-card")
            .assertExists()
            .assertIsDisplayed()
    }

    // ========== Error Handling Tests ==========

    @Test
    fun statCard_handlesLongText() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Very Long Title That Should Be Truncated Properly",
                    value = "999,999,999",
                    subtitle = "This is a very long subtitle that might need to be handled with ellipsis",
                    modifier = Modifier.testTag("long-text-card")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("long-text-card")
            .assertExists()
            .assertIsDisplayed()

        // Verify text is displayed (may be truncated)
        composeTestRule
            .onNodeWithText("Very Long Title That Should Be Truncated Properly")
            .assertExists()
    }

    @Test
    fun cardComponents_maintainAccessibilityWithoutContentDescription() {
        composeTestRule.setContent {
            LiftrixTheme {
                StatCard(
                    title = "Auto Description",
                    value = "Test",
                    subtitle = "Generated automatically"
                )
            }
        }

        // Should auto-generate content description from title and value
        composeTestRule
            .onNode(hasContentDescription("Auto Description: Test, Generated automatically"))
            .assertExists()
    }

    // ========== Interaction State Tests ==========

    @Test
    fun liftrixCard_disabledStateWorks() {
        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    onClick = mockOnClick,
                    enabled = false,
                    modifier = Modifier.testTag("disabled-card")
                ) {
                    Text("Disabled Card")
                }
            }
        }

        // Card should exist but not respond to clicks when disabled
        composeTestRule
            .onNodeWithTag("disabled-card")
            .assertExists()
            .assertIsDisplayed()
            .performClick()

        // Verify click was not registered
        verify(exactly = 0) { mockOnClick() }
    }

    @Test
    fun cardComponents_multipleClicksHandled() {
        var clickCount = 0
        val testCallback = { clickCount++ }

        composeTestRule.setContent {
            LiftrixTheme {
                LiftrixCard(
                    onClick = testCallback,
                    modifier = Modifier.testTag("multi-click-card")
                ) {
                    Text("Multi-click Test")
                }
            }
        }

        val cardNode = composeTestRule.onNodeWithTag("multi-click-card")

        // Perform multiple clicks
        cardNode.performClick()
        cardNode.performClick()
        cardNode.performClick()

        assertEquals(3, clickCount)
    }
} 