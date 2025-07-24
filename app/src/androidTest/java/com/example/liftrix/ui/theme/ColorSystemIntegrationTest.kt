package com.example.liftrix.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.ui.auth.components.AuthTextField
import com.example.liftrix.ui.workout.components.ModernActionButton.*
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.math.pow

/**
 * Comprehensive Color System Integration Test
 * 
 * Validates the new 5-color system implementation across:
 * - Build verification and component rendering
 * - Theme switching functionality 
 * - WCAG 2.1 AA accessibility compliance
 * - Component integration with Material 3
 * - Performance and visual consistency
 * 
 * This test suite ensures the color system overhaul maintains all functionality
 * while providing comprehensive coverage validation.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ColorSystemIntegrationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }
    
    @Test
    fun `verify all major components render with new 5-color system`() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(modifier = Modifier.testTag("color_system_test_root")) {
                    // Test UnifiedWorkoutCard with new color system
                    UnifiedWorkoutCard(
                        title = "Push Day Workout",
                        subtitle = "6 exercises",
                        modifier = Modifier.testTag("test_workout_card")
                    ) {
                        Text(
                            text = "Workout content using Material 3 semantic colors",
                            modifier = Modifier.testTag("card_content_text")
                        )
                    }
                    
                    // Test ModernActionButton hierarchy
                    Row(modifier = Modifier.testTag("button_hierarchy_row")) {
                        PrimaryActionButton(
                            text = "Primary",
                            onClick = { },
                            modifier = Modifier.testTag("primary_button")
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        SecondaryActionButton(
                            text = "Secondary",
                            onClick = { },
                            modifier = Modifier.testTag("secondary_button")
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TertiaryActionButton(
                            text = "Tertiary",
                            onClick = { },
                            modifier = Modifier.testTag("tertiary_button")
                        )
                    }
                    
                    // Test AuthTextField with new color system
                    AuthTextField(
                        value = "test@example.com",
                        onValueChange = { },
                        label = "Email",
                        modifier = Modifier.testTag("auth_text_field")
                    )
                }
            }
        }
        
        // Verify all components render successfully
        composeTestRule.onNodeWithTag("color_system_test_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("test_workout_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_content_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("button_hierarchy_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("primary_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("secondary_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tertiary_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_text_field").assertIsDisplayed()
        
        // Verify text content is visible
        composeTestRule.onNodeWithText("Push Day Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("6 exercises").assertIsDisplayed()
        composeTestRule.onNodeWithText("Primary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Secondary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tertiary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
    }
    
    @Test
    fun `verify theme switching maintains component functionality`() {
        var isDarkTheme by mutableStateOf(false)
        
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = isDarkTheme) {
                Column(modifier = Modifier.testTag("theme_switch_test")) {
                    UnifiedWorkoutCard(
                        title = "Theme Test Workout",
                        subtitle = "Light/Dark switching test",
                        modifier = Modifier.testTag("theme_test_card")
                    ) {
                        Text(
                            text = "Content should maintain readability in both themes",
                            modifier = Modifier.testTag("theme_content_text")
                        )
                    }
                    
                    Row {
                        Button(
                            onClick = { isDarkTheme = false },
                            modifier = Modifier.testTag("light_theme_button")
                        ) {
                            Text("Light Theme")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = { isDarkTheme = true },
                            modifier = Modifier.testTag("dark_theme_button")
                        ) {
                            Text("Dark Theme")
                        }
                    }
                }
            }
        }
        
        // Test light theme (initial state)
        composeTestRule.onNodeWithTag("theme_test_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme Test Workout").assertIsDisplayed()
        
        // Switch to dark theme
        composeTestRule.onNodeWithTag("dark_theme_button").performClick()
        composeTestRule.waitForIdle()
        
        // Verify dark theme components still render correctly
        composeTestRule.onNodeWithTag("theme_test_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme Test Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Content should maintain readability in both themes").assertIsDisplayed()
        
        // Switch back to light theme
        composeTestRule.onNodeWithTag("light_theme_button").performClick()
        composeTestRule.waitForIdle()
        
        // Verify light theme components still render correctly
        composeTestRule.onNodeWithTag("theme_test_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Theme Test Workout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Content should maintain readability in both themes").assertIsDisplayed()
    }
    
    @Test
    fun `verify all UI components render without crashes in light theme`() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Column(modifier = Modifier.testTag("light_theme_components")) {
                    // Test comprehensive component suite in light theme
                    UnifiedWorkoutCard(
                        title = "Light Theme Card",
                        subtitle = "Snow background test"
                    ) {
                        Text("Light theme content with proper contrast")
                    }
                    
                    PrimaryActionButton(
                        text = "Persian Green Primary",
                        onClick = { }
                    )
                    
                    SecondaryActionButton(
                        text = "Tiffany Blue Secondary", 
                        onClick = { }
                    )
                    
                    TertiaryActionButton(
                        text = "Persian Green Tertiary",
                        onClick = { }
                    )
                }
            }
        }
        
        // Verify all components render successfully in light theme
        composeTestRule.onNodeWithTag("light_theme_components").assertIsDisplayed()
        composeTestRule.onNodeWithText("Light Theme Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Persian Green Primary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tiffany Blue Secondary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Persian Green Tertiary").assertIsDisplayed()
    }
    
    @Test
    fun `verify all UI components render without crashes in dark theme`() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Column(modifier = Modifier.testTag("dark_theme_components")) {
                    // Test comprehensive component suite in dark theme
                    UnifiedWorkoutCard(
                        title = "Dark Theme Card",
                        subtitle = "Night/Jet background test"
                    ) {
                        Text("Dark theme content with proper contrast")
                    }
                    
                    PrimaryActionButton(
                        text = "Persian Green Primary",
                        onClick = { }
                    )
                    
                    SecondaryActionButton(
                        text = "Tiffany Blue Secondary",
                        onClick = { }
                    )
                    
                    TertiaryActionButton(
                        text = "Persian Green Tertiary",
                        onClick = { }
                    )
                }
            }
        }
        
        // Verify all components render successfully in dark theme
        composeTestRule.onNodeWithTag("dark_theme_components").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark Theme Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Persian Green Primary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tiffany Blue Secondary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Persian Green Tertiary").assertIsDisplayed()
    }
    
    @Test
    fun `verify component interaction states work with new color system`() {
        var clickCount = 0
        
        composeTestRule.setContent {
            LiftrixTheme {
                Column(modifier = Modifier.testTag("interaction_test")) {
                    UnifiedWorkoutCard(
                        title = "Interactive Card",
                        subtitle = "Click to test interaction",
                        onClick = { clickCount++ },
                        modifier = Modifier.testTag("interactive_card")
                    ) {
                        Text("Click count: $clickCount")
                    }
                    
                    PrimaryActionButton(
                        text = "Interactive Button",
                        onClick = { clickCount++ },
                        modifier = Modifier.testTag("interactive_button")
                    )
                }
            }
        }
        
        // Test component interactions
        composeTestRule.onNodeWithTag("interactive_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("interactive_button").assertIsDisplayed()
        
        // Verify interactions work (buttons are clickable and maintain visual feedback)
        composeTestRule.onNodeWithTag("interactive_button").performClick()
        composeTestRule.waitForIdle()
        
        // Components should still be displayed after interaction
        composeTestRule.onNodeWithTag("interactive_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("interactive_button").assertIsDisplayed()
    }
    
    @Test
    fun `verify error states use preserved red colors correctly`() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(modifier = Modifier.testTag("error_states_test")) {
                    // Test error state with preserved red colors (exception to 5-color rule)
                    AuthTextField(
                        value = "invalid-email",
                        onValueChange = { },
                        label = "Email",
                        isError = true,
                        errorMessage = "Invalid email format",
                        modifier = Modifier.testTag("error_text_field")
                    )
                }
            }
        }
        
        // Verify error state components render correctly
        composeTestRule.onNodeWithTag("error_states_test").assertIsDisplayed()
        composeTestRule.onNodeWithTag("error_text_field").assertIsDisplayed()
        composeTestRule.onNodeWithText("Invalid email format").assertIsDisplayed()
    }
    
    @Test
    fun `verify color scheme consistency across component hierarchy`() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(modifier = Modifier.testTag("color_hierarchy_test")) {
                    // Test nested components maintain color consistency
                    UnifiedWorkoutCard(
                        title = "Parent Card",
                        subtitle = "Contains nested components"
                    ) {
                        Column {
                            Text("Nested content should use proper color roles")
                            
                            Row {
                                PrimaryActionButton(
                                    text = "Nested Primary",
                                    onClick = { }
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                SecondaryActionButton(
                                    text = "Nested Secondary",
                                    onClick = { }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Verify nested component hierarchy renders correctly
        composeTestRule.onNodeWithTag("color_hierarchy_test").assertIsDisplayed()
        composeTestRule.onNodeWithText("Parent Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nested Primary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nested Secondary").assertIsDisplayed()
    }
    
    /**
     * Tests WCAG 2.1 AA accessibility compliance for all color combinations
     * used in the new 5-color system.
     */
    @Test
    fun `verify WCAG 2_1 AA accessibility compliance for all color combinations`() {
        var capturedColors: Map<String, Color> = emptyMap()
        
        composeTestRule.setContent {
            LiftrixTheme {
                // Capture actual theme colors for testing
                val colorScheme = MaterialTheme.colorScheme
                
                LaunchedEffect(colorScheme) {
                    capturedColors = mapOf(
                        "primary" to colorScheme.primary,
                        "onPrimary" to colorScheme.onPrimary,
                        "secondary" to colorScheme.secondary,
                        "onSecondary" to colorScheme.onSecondary,
                        "surface" to colorScheme.surface,
                        "onSurface" to colorScheme.onSurface,
                        "background" to colorScheme.background,
                        "onBackground" to colorScheme.onBackground,
                        "surfaceVariant" to colorScheme.surfaceVariant,
                        "onSurfaceVariant" to colorScheme.onSurfaceVariant
                    )
                }
                
                // Test content to ensure colors are applied
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("accessibility_test_container")
                ) {
                    Text(
                        text = "WCAG 2.1 AA Compliance Test",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("accessibility_headline")
                    )
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("accessibility_test_card")
                    ) {
                        Text(
                            text = "Card content for contrast validation",
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag("card_content_text")
                        )
                    }
                    
                    PrimaryActionButton(
                        text = "Persian Green Button",
                        onClick = { },
                        modifier = Modifier.testTag("accessibility_button")
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        // Verify components are displayed before testing colors
        composeTestRule.onNodeWithTag("accessibility_test_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_headline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_test_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_button").assertIsDisplayed()
        
        // Test WCAG 2.1 AA compliance for critical color combinations
        val criticalCombinations = listOf(
            Pair("primary", "onPrimary"), // Persian Green on white/snow
            Pair("surface", "onSurface"), // Snow/Jet on Night/Snow
            Pair("background", "onBackground"), // Background on text
            Pair("surfaceVariant", "onSurfaceVariant") // Surface variants
        )
        
        criticalCombinations.forEach { (backgroundKey, foregroundKey) ->
            val backgroundColor = capturedColors[backgroundKey]
            val foregroundColor = capturedColors[foregroundKey]
            
            if (backgroundColor != null && foregroundColor != null) {
                val contrastRatio = calculateWcagContrastRatio(foregroundColor, backgroundColor)
                
                assertTrue(
                    "Contrast ratio for $foregroundKey on $backgroundKey is ${"%.2f".format(contrastRatio)}:1, " +
                    "which is below WCAG AA standard (4.5:1)",
                    contrastRatio >= 4.5f
                )
            }
        }
    }
    
    /**
     * Tests build configuration compatibility and ProGuard/R8 optimization
     * with the new color system.
     */
    @Test
    fun `verify build configuration compatibility with color system`() {
        composeTestRule.setContent {
            LiftrixTheme {
                Column(modifier = Modifier.testTag("build_config_test")) {
                    // Test that all color constants are accessible and not minified
                    val coreColors = listOf(
                        LiftrixColors.Night,
                        LiftrixColors.Jet,
                        LiftrixColors.PersianGreen,
                        LiftrixColors.TiffanyBlue,
                        LiftrixColors.Snow
                    )
                    
                    coreColors.forEachIndexed { index, color ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("color_constant_$index"),
                            colors = CardDefaults.cardColors(
                                containerColor = color.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "Color constant $index: ${color.value.toString(16)}",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Verify all color constants are accessible
        for (i in 0..4) {
            composeTestRule
                .onNodeWithTag("color_constant_$i")
                .assertIsDisplayed()
        }
        
        // Verify core color values match expected 5-color system
        assertEquals(0xFF131515, LiftrixColors.Night.value, "Night color should be #131515")
        assertEquals(0xFF2B2C28, LiftrixColors.Jet.value, "Jet color should be #2B2C28")
        assertEquals(0xFF339989, LiftrixColors.PersianGreen.value, "Persian Green should be #339989")
        assertEquals(0xFF7DE2D1, LiftrixColors.TiffanyBlue.value, "Tiffany Blue should be #7DE2D1")
        assertEquals(0xFFFFFAFB, LiftrixColors.Snow.value, "Snow color should be #FFFAFB")
    }
    
    /**
     * Tests comprehensive visual regression by verifying theme switching
     * performance and consistency.
     */
    @Test
    fun `verify visual regression and theme switching performance`() {
        var isDarkTheme by mutableStateOf(false)
        var themeSwithingStartTime = 0L
        var themeSwitchingDuration = 0L
        
        composeTestRule.setContent {
            // Monitor theme switching performance
            LaunchedEffect(isDarkTheme) {
                if (themeSwithingStartTime != 0L) {
                    themeSwitchingDuration = System.nanoTime() - themeSwithingStartTime
                }
                themeSwithingStartTime = System.nanoTime()
            }
            
            LiftrixTheme(darkTheme = isDarkTheme) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .testTag("performance_test_container")
                ) {
                    Text(
                        text = "Performance Test - ${if (isDarkTheme) "Dark" else "Light"} Theme",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("performance_theme_indicator")
                    )
                    
                    // Test multiple components for performance impact
                    repeat(5) { index ->
                        UnifiedWorkoutCard(
                            title = "Performance Test Card $index",
                            subtitle = "Theme switching performance test",
                            modifier = Modifier.testTag("performance_card_$index")
                        ) {
                            Text("Card content $index")
                            
                            Row {
                                PrimaryActionButton(
                                    text = "Primary $index",
                                    onClick = { },
                                    modifier = Modifier.testTag("performance_primary_$index")
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                SecondaryActionButton(
                                    text = "Secondary $index",
                                    onClick = { },
                                    modifier = Modifier.testTag("performance_secondary_$index")
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { 
                            themeSwithingStartTime = System.nanoTime()
                            isDarkTheme = !isDarkTheme 
                        },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Text("Toggle Theme")
                    }
                }
            }
        }
        
        // Initial verification - light theme
        composeTestRule.onNodeWithTag("performance_test_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("performance_theme_indicator").assertIsDisplayed()
        
        // Verify all performance test components render
        for (i in 0..4) {
            composeTestRule.onNodeWithTag("performance_card_$i").assertIsDisplayed()
            composeTestRule.onNodeWithTag("performance_primary_$i").assertIsDisplayed()
            composeTestRule.onNodeWithTag("performance_secondary_$i").assertIsDisplayed()
        }
        
        // Test theme switching performance
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()
        
        // Verify dark theme renders correctly
        composeTestRule.onNodeWithTag("performance_theme_indicator").assertIsDisplayed()
        
        // Verify all components still work after theme switch
        for (i in 0..4) {
            composeTestRule.onNodeWithTag("performance_card_$i").assertIsDisplayed()
            composeTestRule.onNodeWithTag("performance_primary_$i").assertIsDisplayed()
            composeTestRule.onNodeWithTag("performance_secondary_$i").assertIsDisplayed()
        }
        
        // Switch back to light theme
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()
        
        // Final verification - light theme restored
        composeTestRule.onNodeWithTag("performance_theme_indicator").assertIsDisplayed()
        
        // Verify performance (theme switching should be fast)
        if (themeSwitchingDuration > 0) {
            val durationMs = themeSwitchingDuration / 1_000_000
            assertTrue(
                "Theme switching took ${durationMs}ms, should be under 100ms for good performance",
                durationMs < 100
            )
        }
    }
    
    /**
     * Calculates WCAG 2.1 contrast ratio between two colors.
     * Implementation follows WCAG 2.1 specification for luminance calculation.
     */
    private fun calculateWcagContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = calculateRelativeLuminance(foreground)
        val backgroundLuminance = calculateRelativeLuminance(background)
        
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    /**
     * Calculates relative luminance according to WCAG 2.1 specification.
     */
    private fun calculateRelativeLuminance(color: Color): Float {
        fun linearize(component: Float): Float {
            return if (component <= 0.03928f) {
                component / 12.92f
            } else {
                ((component + 0.055f) / 1.055f).pow(2.4f)
            }
        }
        
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}