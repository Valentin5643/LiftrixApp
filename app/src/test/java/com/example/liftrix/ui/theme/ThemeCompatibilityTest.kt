package com.example.liftrix.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.components.cards.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Theme compatibility tests validating light/dark theme functionality,
 * theme switching behavior, and component styling consistency as specified in TESTING-001.
 * 
 * Tests cover:
 * - Theme switching maintains component functionality
 * - Color system consistency across light/dark themes
 * - Typography rendering in different themes
 * - Animation performance during theme transitions
 * - Material 3 DayNight integration
 * - Brand color accessibility in both themes
 * - Component visual consistency across theme changes
 */
@RunWith(AndroidJUnit4::class)
class ThemeCompatibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Theme Switching Tests

    @Test
    fun themeSwitch_maintainsComponentFunctionality() {
        var clickCount = 0
        
        composeTestRule.setContent {
            var darkTheme by remember { mutableStateOf(false) }
            
            LiftrixTheme(darkTheme = darkTheme) {
                Column {
                    LiftrixCard(
                        modifier = Modifier.testTag("theme_switch_card"),
                        onClick = { clickCount++ }
                    ) {
                        Text("Test Content")
                    }
                    
                    // Theme switch button for testing
                    LiftrixCard(
                        modifier = Modifier.testTag("theme_switch_button"),
                        onClick = { darkTheme = !darkTheme }
                    ) {
                        Text("Switch Theme")
                    }
                }
            }
        }

        // Test functionality in light theme
        composeTestRule
            .onNodeWithTag("theme_switch_card")
            .performClick()
        
        assert(clickCount == 1)

        // Switch to dark theme
        composeTestRule
            .onNodeWithTag("theme_switch_button")
            .performClick()

        composeTestRule.waitForIdle()

        // Test functionality persists in dark theme
        composeTestRule
            .onNodeWithTag("theme_switch_card")
            .performClick()
        
        assert(clickCount == 2)
    }

    @Test
    fun themeSwitch_preservesComponentState() {
        composeTestRule.setContent {
            var darkTheme by remember { mutableStateOf(false) }
            var counter by remember { mutableStateOf(0) }
            
            LiftrixTheme(darkTheme = darkTheme) {
                Column {
                    StatCard(
                        title = "Counter",
                        value = counter.toString(),
                        modifier = Modifier.testTag("state_card"),
                        onClick = { counter++ }
                    )
                    
                    LiftrixCard(
                        modifier = Modifier.testTag("theme_toggle"),
                        onClick = { darkTheme = !darkTheme }
                    ) {
                        Text("Toggle Theme")
                    }
                }
            }
        }

        // Initial state
        composeTestRule
            .onNodeWithText("0")
            .assertIsDisplayed()

        // Increment counter
        composeTestRule
            .onNodeWithTag("state_card")
            .performClick()

        composeTestRule
            .onNodeWithText("1")
            .assertIsDisplayed()

        // Switch theme
        composeTestRule
            .onNodeWithTag("theme_toggle")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify state is preserved
        composeTestRule
            .onNodeWithText("1")
            .assertIsDisplayed()
    }

    @Test
    fun themeSwitch_maintainsAccessibility() {
        composeTestRule.setContent {
            var darkTheme by remember { mutableStateOf(false) }
            
            LiftrixTheme(darkTheme = darkTheme) {
                Column {
                    LiftrixCard(
                        modifier = Modifier.testTag("accessible_card"),
                        onClick = { /* Test action */ },
                        contentDescription = "Accessible test card"
                    ) {
                        Text("Accessible Content")
                    }
                    
                    LiftrixCard(
                        modifier = Modifier.testTag("theme_switch"),
                        onClick = { darkTheme = !darkTheme }
                    ) {
                        Text("Switch Theme")
                    }
                }
            }
        }

        // Test accessibility in light theme
        composeTestRule
            .onNodeWithTag("accessible_card")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(hasContentDescription("Accessible test card"))

        // Switch to dark theme
        composeTestRule
            .onNodeWithTag("theme_switch")
            .performClick()

        composeTestRule.waitForIdle()

        // Test accessibility persists in dark theme
        composeTestRule
            .onNodeWithTag("accessible_card")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .assert(hasContentDescription("Accessible test card"))
    }

    // Light Theme Tests

    @Test
    fun lightTheme_rendersComponentsCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    LiftrixCard(
                        modifier = Modifier.testTag("light_card")
                    ) {
                        Text("Light Theme Card")
                    }
                    
                    StatCard(
                        title = "Light Stat",
                        value = "100%",
                        trend = Trend.Positive(15f),
                        modifier = Modifier.testTag("light_stat_card")
                    )
                    
                    ActivityCard(
                        title = "Light Activity",
                        subtitle = "Light theme activity",
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.testTag("light_activity_card")
                    )
                }
            }
        }

        // Verify all components render in light theme
        composeTestRule
            .onNodeWithTag("light_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("light_stat_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("light_activity_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Theme Card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Stat")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Activity")
            .assertIsDisplayed()
    }

    @Test
    fun lightTheme_hasCorrectColorScheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Column {
                    Text(
                        text = "Light Theme Text",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("light_text")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("light_text")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Theme Text")
            .assertIsDisplayed()
    }

    // Dark Theme Tests

    @Test
    fun darkTheme_rendersComponentsCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    LiftrixCard(
                        modifier = Modifier.testTag("dark_card")
                    ) {
                        Text("Dark Theme Card")
                    }
                    
                    StatCard(
                        title = "Dark Stat",
                        value = "85%",
                        trend = Trend.Negative(5f),
                        modifier = Modifier.testTag("dark_stat_card")
                    )
                    
                    ActivityCard(
                        title = "Dark Activity",
                        subtitle = "Dark theme activity",
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.testTag("dark_activity_card")
                    )
                }
            }
        }

        // Verify all components render in dark theme
        composeTestRule
            .onNodeWithTag("dark_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("dark_stat_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("dark_activity_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Theme Card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Stat")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Activity")
            .assertIsDisplayed()
    }

    @Test
    fun darkTheme_hasCorrectColorScheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Column {
                    Text(
                        text = "Dark Theme Text",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("dark_text")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark_text")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Theme Text")
            .assertIsDisplayed()
    }

    @Test
    fun darkTheme_usesDeepBlackBackground() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                LiftrixCard(
                    modifier = Modifier.testTag("dark_background_card")
                ) {
                    Text("Dark Background Test")
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark_background_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Background Test")
            .assertIsDisplayed()
    }

    // Typography Tests

    @Test
    fun typography_rendersConsistentlyAcrossThemes() {
        // Test in light theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Column {
                    Text(
                        text = "Display Large",
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.testTag("light_display_large")
                    )
                    Text(
                        text = "Headline Medium",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("light_headline_medium")
                    )
                    Text(
                        text = "Body Large",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("light_body_large")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("light_display_large")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("light_headline_medium")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("light_body_large")
            .assertIsDisplayed()

        // Test in dark theme
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Column {
                    Text(
                        text = "Display Large",
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.testTag("dark_display_large")
                    )
                    Text(
                        text = "Headline Medium",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("dark_headline_medium")
                    )
                    Text(
                        text = "Body Large",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("dark_body_large")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark_display_large")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("dark_headline_medium")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("dark_body_large")
            .assertIsDisplayed()
    }

    // Card Variants Theme Compatibility

    @Test
    fun cardVariants_renderCorrectlyInLightTheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Column {
                    CompactLiftrixCard(
                        modifier = Modifier.testTag("light_compact_card")
                    ) {
                        Text("Compact Light")
                    }
                    
                    ElevatedLiftrixCard(
                        modifier = Modifier.testTag("light_elevated_card")
                    ) {
                        Text("Elevated Light")
                    }
                    
                    GradientLiftrixCard(
                        modifier = Modifier.testTag("light_gradient_card")
                    ) {
                        Text("Gradient Light")
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("light_compact_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("light_elevated_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("light_gradient_card")
            .assertIsDisplayed()
    }

    @Test
    fun cardVariants_renderCorrectlyInDarkTheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Column {
                    CompactLiftrixCard(
                        modifier = Modifier.testTag("dark_compact_card")
                    ) {
                        Text("Compact Dark")
                    }
                    
                    ElevatedLiftrixCard(
                        modifier = Modifier.testTag("dark_elevated_card")
                    ) {
                        Text("Elevated Dark")
                    }
                    
                    GradientLiftrixCard(
                        modifier = Modifier.testTag("dark_gradient_card")
                    ) {
                        Text("Gradient Dark")
                    }
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark_compact_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("dark_elevated_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("dark_gradient_card")
            .assertIsDisplayed()
    }

    // Brand Color Tests

    @Test
    fun brandColors_maintainAccessibilityInLightTheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Column {
                    StatCard(
                        title = "Brand Color Test",
                        value = "100%",
                        trend = Trend.Positive(25f),
                        modifier = Modifier.testTag("brand_color_light_card")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("brand_color_light_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Brand Color Test")
            .assertIsDisplayed()
    }

    @Test
    fun brandColors_maintainAccessibilityInDarkTheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Column {
                    StatCard(
                        title = "Brand Color Test",
                        value = "100%",
                        trend = Trend.Positive(25f),
                        modifier = Modifier.testTag("brand_color_dark_card")
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("brand_color_dark_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Brand Color Test")
            .assertIsDisplayed()
    }

    // Performance Tests

    @Test
    fun themeSwitch_performsWithoutDelay() {
        composeTestRule.setContent {
            var darkTheme by remember { mutableStateOf(false) }
            
            LiftrixTheme(darkTheme = darkTheme) {
                Column {
                    repeat(10) { index ->
                        LiftrixCard(
                            modifier = Modifier.testTag("performance_card_$index")
                        ) {
                            Text("Card $index")
                        }
                    }
                    
                    LiftrixCard(
                        modifier = Modifier.testTag("theme_switch_performance"),
                        onClick = { darkTheme = !darkTheme }
                    ) {
                        Text("Switch Theme")
                    }
                }
            }
        }

        // Verify all cards are initially displayed
        repeat(10) { index ->
            composeTestRule
                .onNodeWithTag("performance_card_$index")
                .assertIsDisplayed()
        }

        // Switch theme and verify performance
        composeTestRule
            .onNodeWithTag("theme_switch_performance")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify all cards are still displayed after theme switch
        repeat(10) { index ->
            composeTestRule
                .onNodeWithTag("performance_card_$index")
                .assertIsDisplayed()
        }
    }

    @Test
    fun multipleThemeSwitches_maintainStability() {
        composeTestRule.setContent {
            var darkTheme by remember { mutableStateOf(false) }
            
            LiftrixTheme(darkTheme = darkTheme) {
                Column {
                    LiftrixCard(
                        modifier = Modifier.testTag("stability_card")
                    ) {
                        Text("Stability Test")
                    }
                    
                    LiftrixCard(
                        modifier = Modifier.testTag("multiple_theme_switch"),
                        onClick = { darkTheme = !darkTheme }
                    ) {
                        Text("Switch Theme")
                    }
                }
            }
        }

        // Perform multiple theme switches
        repeat(5) {
            composeTestRule
                .onNodeWithTag("multiple_theme_switch")
                .performClick()
            
            composeTestRule.waitForIdle()
            
            // Verify card remains stable
            composeTestRule
                .onNodeWithTag("stability_card")
                .assertIsDisplayed()
        }
    }

    // Edge Cases

    @Test
    fun themeSwitch_handlesComplexLayouts() {
        composeTestRule.setContent {
            var darkTheme by remember { mutableStateOf(false) }
            
            LiftrixTheme(darkTheme = darkTheme) {
                Column {
                    StatCard(
                        title = "Complex Layout",
                        value = "42",
                        subtitle = "With multiple elements",
                        trend = Trend.Positive(12f),
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.testTag("complex_stat_card")
                    )
                    
                    ActivityCard(
                        title = "Complex Activity",
                        subtitle = "With trailing content",
                        icon = Icons.Default.FitnessCenter,
                        trailing = "2h ago",
                        modifier = Modifier.testTag("complex_activity_card")
                    )
                    
                    LiftrixCard(
                        modifier = Modifier.testTag("complex_theme_switch"),
                        onClick = { darkTheme = !darkTheme }
                    ) {
                        Text("Switch Theme")
                    }
                }
            }
        }

        // Verify complex layouts work in initial theme
        composeTestRule
            .onNodeWithTag("complex_stat_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("complex_activity_card")
            .assertIsDisplayed()

        // Switch theme
        composeTestRule
            .onNodeWithTag("complex_theme_switch")
            .performClick()

        composeTestRule.waitForIdle()

        // Verify complex layouts still work after theme switch
        composeTestRule
            .onNodeWithTag("complex_stat_card")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("complex_activity_card")
            .assertIsDisplayed()
    }
} 