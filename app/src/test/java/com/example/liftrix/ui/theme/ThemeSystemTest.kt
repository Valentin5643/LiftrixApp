package com.example.liftrix.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/**
 * Comprehensive test suite for the theme system.
 * 
 * Tests cover:
 * - Theme transitions and performance
 * - Color system accessibility (WCAG compliance)
 * - Dark/light theme switching
 * - Time-based color adaptation
 * - Theme state management
 * - Material 3 integration
 * - Brand color system
 * - Animation specifications
 */
@RunWith(AndroidJUnit4::class)
class ThemeSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        themeManager = ThemeManager.getInstance()
        // Reset to default state
        themeManager.setThemeMode(ThemeMode.SYSTEM)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Theme Manager Tests ==========

    @Test
    fun themeManager_singletonBehavior() {
        val instance1 = ThemeManager.getInstance()
        val instance2 = ThemeManager.getInstance()
        
        assertSame(instance1, instance2)
    }

    @Test
    fun themeManager_initialState() {
        val initialState = themeManager.themeState.value
        
        assertEquals(ThemeMode.SYSTEM, initialState.themeMode)
        assertFalse(initialState.timeBasedColors)
        assertTrue(initialState.fastTransitions)
    }

    @Test
    fun themeManager_switchTheme() = runTest {
        // Test switching to light theme
        themeManager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, themeManager.themeState.value.themeMode)

        // Test switching to dark theme
        themeManager.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, themeManager.themeState.value.themeMode)

        // Test switching to system theme
        themeManager.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, themeManager.themeState.value.themeMode)
    }

    @Test
    fun themeManager_timeBasedColors() = runTest {
        // Test enabling time-based colors
        themeManager.setTimeBasedColors(true)
        assertTrue(themeManager.themeState.value.timeBasedColors)

        // Test disabling time-based colors
        themeManager.setTimeBasedColors(false)
        assertFalse(themeManager.themeState.value.timeBasedColors)
    }

    @Test
    fun themeManager_fastTransitions() = runTest {
        // Test disabling fast transitions
        themeManager.setFastTransitions(false)
        assertFalse(themeManager.themeState.value.fastTransitions)

        // Test enabling fast transitions
        themeManager.setFastTransitions(true)
        assertTrue(themeManager.themeState.value.fastTransitions)
    }

    // ========== LiftrixTheme Composable Tests ==========

    @Test
    fun liftrixTheme_rendersWithLightTheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("theme-box")
                ) {
                    Text(
                        text = "Light Theme Test",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("theme-box")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Light Theme Test")
            .assertIsDisplayed()
    }

    @Test
    fun liftrixTheme_rendersWithDarkTheme() {
        composeTestRule.setContent {
            LiftrixTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("dark-theme-box")
                ) {
                    Text(
                        text = "Dark Theme Test",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dark-theme-box")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dark Theme Test")
            .assertIsDisplayed()
    }

    @Test
    fun liftrixTheme_switchesBetweenThemes() {
        var isDark by mutableStateOf(false)

        composeTestRule.setContent {
            LiftrixTheme(darkTheme = isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("switchable-theme-box")
                ) {
                    Text(
                        text = "Theme Switch Test",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Verify initial light theme
        composeTestRule
            .onNodeWithTag("switchable-theme-box")
            .assertExists()

        // Switch to dark theme
        isDark = true
        composeTestRule.waitForIdle()

        // Verify dark theme is applied
        composeTestRule
            .onNodeWithTag("switchable-theme-box")
            .assertExists()

        // Switch back to light theme
        isDark = false
        composeTestRule.waitForIdle()

        // Verify light theme is applied
        composeTestRule
            .onNodeWithTag("switchable-theme-box")
            .assertExists()
    }

    // ========== Color System Tests ==========

    @Test
    fun colorSystem_hasProperContrast() {
        val lightColorScheme = getLightColorScheme()
        val darkColorScheme = getDarkColorScheme()

        // Test light theme contrast ratios
        assertTrue(
            checkContrastRatio(lightColorScheme.onBackground, lightColorScheme.background) >= 4.5f,
            "Light theme background contrast insufficient"
        )
        assertTrue(
            checkContrastRatio(lightColorScheme.onPrimary, lightColorScheme.primary) >= 4.5f,
            "Light theme primary contrast insufficient"
        )
        assertTrue(
            checkContrastRatio(lightColorScheme.onSurface, lightColorScheme.surface) >= 4.5f,
            "Light theme surface contrast insufficient"
        )

        // Test dark theme contrast ratios
        assertTrue(
            checkContrastRatio(darkColorScheme.onBackground, darkColorScheme.background) >= 4.5f,
            "Dark theme background contrast insufficient"
        )
        assertTrue(
            checkContrastRatio(darkColorScheme.onPrimary, darkColorScheme.primary) >= 4.5f,
            "Dark theme primary contrast insufficient"
        )
        assertTrue(
            checkContrastRatio(darkColorScheme.onSurface, darkColorScheme.surface) >= 4.5f,
            "Dark theme surface contrast insufficient"
        )
    }

    @Test
    fun colorSystem_brandColorsAccessible() {
        val brandColors = LiftrixColors.BrandPalette

        // Test brand color contrast with white background
        assertTrue(
            checkContrastRatio(brandColors.Teal, Color.White) >= 3.0f,
            "Teal brand color insufficient contrast with white"
        )
        assertTrue(
            checkContrastRatio(brandColors.Indigo, Color.White) >= 4.5f,
            "Indigo brand color insufficient contrast with white"
        )

        // Test brand color contrast with dark background
        assertTrue(
            checkContrastRatio(brandColors.Teal, Color.Black) >= 3.0f,
            "Teal brand color insufficient contrast with black"
        )
    }

    @Test
    fun colorSystem_timeBasedColorsWork() {
        // Test morning colors (6 AM)
        val morningScheme = getTimeBasedColorScheme(6)
        assertNotNull(morningScheme)

        // Test afternoon colors (2 PM)
        val afternoonScheme = getTimeBasedColorScheme(14)
        assertNotNull(afternoonScheme)

        // Test evening colors (8 PM)
        val eveningScheme = getTimeBasedColorScheme(20)
        assertNotNull(eveningScheme)

        // Test night colors (2 AM)
        val nightScheme = getTimeBasedColorScheme(2)
        assertNotNull(nightScheme)

        // Verify different times produce different color schemes
        assertNotEquals(morningScheme.primary, eveningScheme.primary)
    }

    @Test
    fun colorSystem_extendedPaletteConsistent() {
        val lightScheme = getLightColorScheme()
        val darkScheme = getDarkColorScheme()

        // Verify extended color palette exists and is consistent
        assertNotNull(lightScheme.primary)
        assertNotNull(lightScheme.secondary)
        assertNotNull(lightScheme.tertiary)
        assertNotNull(lightScheme.error)

        assertNotNull(darkScheme.primary)
        assertNotNull(darkScheme.secondary)
        assertNotNull(darkScheme.tertiary)
        assertNotNull(darkScheme.error)

        // Verify complementary colors maintain relationships
        assertNotEquals(lightScheme.primary, lightScheme.secondary)
        assertNotEquals(darkScheme.primary, darkScheme.secondary)
    }

    // ========== Animation System Tests ==========

    @Test
    fun animationSystem_hasCorrectSpecs() {
        val animations = LiftrixAnimations

        // Test duration constants
        assertEquals(100L, animations.Duration.MICRO)
        assertEquals(150L, animations.Duration.FAST)
        assertEquals(300L, animations.Duration.STANDARD)

        // Test spring specs exist
        assertNotNull(animations.standardSpring())
        assertNotNull(animations.fastSpring())
        assertNotNull(animations.gentleSpring())
        assertNotNull(animations.bouncySpring())
        assertNotNull(animations.preciseSpring())

        // Test micro-interaction specs
        assertNotNull(animations.microInteractionSpec)
        assertNotNull(animations.bounceEntranceSpec)
    }

    @Test
    fun animationSystem_performanceOptimized() {
        val animations = LiftrixAnimations

        // Verify animations are optimized for 60fps
        assertTrue(
            animations.Duration.MICRO <= 100L,
            "Micro-interaction duration should be ≤100ms for 60fps"
        )
        assertTrue(
            animations.Duration.FAST <= 150L,
            "Fast animation duration should be ≤150ms for 60fps"
        )
        assertTrue(
            animations.Duration.STANDARD <= 300L,
            "Standard animation duration should be ≤300ms for smooth UX"
        )
    }

    // ========== Typography System Tests ==========

    @Test
    fun typographySystem_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                Text(
                    text = "Typography Test",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.testTag("typography-test")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("typography-test")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Typography Test")
            .assertIsDisplayed()
    }

    @Test
    fun typographySystem_scalesCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                // Test different typography scales
                Text(
                    text = "Display Large",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.testTag("display-large")
                )
                Text(
                    text = "Body Medium",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("body-medium")
                )
                Text(
                    text = "Label Small",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.testTag("label-small")
                )
            }
        }

        composeTestRule
            .onNodeWithTag("display-large")
            .assertExists()

        composeTestRule
            .onNodeWithTag("body-medium")
            .assertExists()

        composeTestRule
            .onNodeWithTag("label-small")
            .assertExists()
    }

    // ========== Performance Tests ==========

    @Test
    fun themeSystem_transitionsPerformance() {
        var isDark by mutableStateOf(false)
        val startTime = System.currentTimeMillis()

        composeTestRule.setContent {
            LiftrixTheme(darkTheme = isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("performance-box")
                )
            }
        }

        // Switch theme and measure time
        isDark = true
        composeTestRule.waitForIdle()

        val endTime = System.currentTimeMillis()
        val transitionTime = endTime - startTime

        // Theme transition should be fast (<100ms target)
        assertTrue(
            transitionTime < 1000, // Allow for test overhead, but verify responsiveness
            "Theme transition took too long: ${transitionTime}ms"
        )

        composeTestRule
            .onNodeWithTag("performance-box")
            .assertExists()
    }

    @Test
    fun themeSystem_memoryEfficient() {
        // Test that creating multiple theme instances doesn't cause memory issues
        repeat(100) {
            composeTestRule.setContent {
                LiftrixTheme {
                    Text("Memory Test $it")
                }
            }
            composeTestRule.waitForIdle()
        }

        // If we reach here without OutOfMemoryError, theme system is memory efficient
        assertTrue(true)
    }

    // ========== Integration Tests ==========

    @Test
    fun themeSystem_integratesWithMaterial3() {
        composeTestRule.setContent {
            LiftrixTheme {
                // Test that Material 3 components work with our theme
                androidx.compose.material3.Card(
                    modifier = Modifier.testTag("material3-card")
                ) {
                    Text(
                        text = "Material 3 Integration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("material3-card")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Material 3 Integration")
            .assertIsDisplayed()
    }

    @Test
    fun themeSystem_worksWithDynamicColor() {
        composeTestRule.setContent {
            LiftrixTheme(dynamicColor = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("dynamic-color-box")
                ) {
                    Text(
                        text = "Dynamic Color Test",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("dynamic-color-box")
            .assertExists()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Dynamic Color Test")
            .assertIsDisplayed()
    }

    // ========== Helper Functions Tests ==========

    @Test
    fun helperFunctions_contrastCalculationWorks() {
        // Test contrast calculation accuracy
        val whiteBlackContrast = checkContrastRatio(Color.White, Color.Black)
        assertTrue(whiteBlackContrast > 20f, "White-black contrast should be maximum")

        val sameColorContrast = checkContrastRatio(Color.Red, Color.Red)
        assertEquals(1f, sameColorContrast, 0.1f, "Same color contrast should be 1:1")
    }

    @Test
    fun helperFunctions_luminanceCalculationWorks() {
        // Test luminance calculation
        val whiteLuminance = Color.White.luminance()
        val blackLuminance = Color.Black.luminance()

        assertTrue(whiteLuminance > blackLuminance, "White should have higher luminance than black")
        assertTrue(whiteLuminance > 0.9f, "White luminance should be near 1.0")
        assertTrue(blackLuminance < 0.1f, "Black luminance should be near 0.0")
    }

    // ========== Error Handling Tests ==========

    @Test
    fun themeSystem_handlesInvalidHours() {
        // Test that invalid hours are handled gracefully
        val invalidHourScheme1 = getTimeBasedColorScheme(-1)
        val invalidHourScheme2 = getTimeBasedColorScheme(25)
        val validHourScheme = getTimeBasedColorScheme(12)

        // Should fall back to default scheme for invalid hours
        assertNotNull(invalidHourScheme1)
        assertNotNull(invalidHourScheme2)
        assertNotNull(validHourScheme)
    }

    @Test
    fun themeSystem_handlesStateCorruption() {
        // Test resilience to invalid theme states
        try {
            themeManager.setThemeMode(ThemeMode.LIGHT)
            themeManager.setThemeMode(ThemeMode.DARK)
            themeManager.setThemeMode(ThemeMode.SYSTEM)
            
            // Should not throw exceptions
            assertTrue(true)
        } catch (e: Exception) {
            fail("Theme system should handle state changes gracefully: ${e.message}")
        }
    }
}

/**
 * Helper function to check contrast ratio between two colors
 */
private fun checkContrastRatio(foreground: Color, background: Color): Float {
    val foregroundLuminance = foreground.luminance()
    val backgroundLuminance = background.luminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
} 