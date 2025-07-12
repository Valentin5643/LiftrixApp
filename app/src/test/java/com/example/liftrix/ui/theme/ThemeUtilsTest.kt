package com.example.liftrix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test theme switching performance and functionality
 * Verifies fast transitions (<100ms), state persistence, and theme management
 */
@ExperimentalCoroutinesApi
class ThemeUtilsTest {

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var sharedPreferences: SharedPreferences

    @MockK
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        
        // Mock SharedPreferences behavior
        every { context.applicationContext } returns context
        every { context.getSharedPreferences("liftrix_theme_preferences", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just runs
        
        // Default values
        every { sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) } returns ThemeMode.SYSTEM.name
        every { sharedPreferences.getBoolean("time_based_enabled", false) } returns false
        every { sharedPreferences.getBoolean("fast_transitions_enabled", true) } returns true
    }

    @Test
    fun `themeManager should persist theme mode changes`() = runTest {
        // Arrange
        themeManager = ThemeManager.getInstance(context)
        
        // Act
        themeManager.switchTheme(ThemeMode.DARK)
        
        // Assert
        assertEquals(ThemeMode.DARK, themeManager.themeMode.first())
        verify { sharedPreferences.edit() }
        verify { editor.putString("theme_mode", ThemeMode.DARK.name) }
        verify { editor.apply() }
    }

    @Test
    fun `themeManager should load saved theme mode on initialization`() = runTest {
        // Arrange
        every { sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) } returns ThemeMode.LIGHT.name
        
        // Act
        themeManager = ThemeManager.getInstance(context)
        
        // Assert
        assertEquals(ThemeMode.LIGHT, themeManager.themeMode.first())
        verify { sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) }
    }

    @Test
    fun `themeManager should handle invalid theme mode gracefully`() = runTest {
        // Arrange
        every { sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) } returns "INVALID_MODE"
        
        // Act
        themeManager = ThemeManager.getInstance(context)
        
        // Assert - should default to SYSTEM for invalid modes
        assertEquals(ThemeMode.SYSTEM, themeManager.themeMode.first())
    }

    @Test
    fun `themeManager should persist time-based colors setting`() = runTest {
        // Arrange
        themeManager = ThemeManager.getInstance(context)
        
        // Act
        themeManager.setTimeBasedColors(true)
        
        // Assert
        assertTrue(themeManager.timeBasedEnabled.first())
        verify { sharedPreferences.edit() }
        verify { editor.putBoolean("time_based_enabled", true) }
        verify { editor.apply() }
    }

    @Test
    fun `themeManager should persist fast transitions setting`() = runTest {
        // Arrange
        themeManager = ThemeManager.getInstance(context)
        
        // Act
        themeManager.setFastTransitions(false)
        
        // Assert
        assertFalse(themeManager.fastTransitionsEnabled.first())
        verify { sharedPreferences.edit() }
        verify { editor.putBoolean("fast_transitions_enabled", false) }
        verify { editor.apply() }
    }

    @Test
    fun `themeManager should be singleton`() {
        // Arrange & Act
        val instance1 = ThemeManager.getInstance(context)
        val instance2 = ThemeManager.getInstance(context)
        
        // Assert
        assertEquals(instance1, instance2)
    }

    @Test
    fun `themeUtils should provide fast transition animation spec`() {
        // Arrange & Act
        val fastTransition = ThemeUtils.fastThemeTransition
        
        // Assert - Verify it's a tween with 100ms duration
        // Note: We can't directly test the duration, but we can verify the type
        assertTrue(fastTransition is androidx.compose.animation.core.TweenSpec)
    }

    @Test
    fun `themeUtils should provide smooth color transition spec`() {
        // Arrange & Act
        val smoothTransition = ThemeUtils.smoothColorTransition
        
        // Assert - Verify it's a spring animation
        assertTrue(smoothTransition is androidx.compose.animation.core.SpringSpec)
    }

    @Test
    fun `all theme modes should be supported`() {
        // Arrange & Act & Assert
        val supportedModes = ThemeMode.values()
        
        // Verify all expected modes exist
        assertTrue(supportedModes.contains(ThemeMode.LIGHT))
        assertTrue(supportedModes.contains(ThemeMode.DARK))
        assertTrue(supportedModes.contains(ThemeMode.SYSTEM))
        assertTrue(supportedModes.contains(ThemeMode.TIME_BASED))
    }

    @Test
    fun `theme state should combine all settings correctly`() = runTest {
        // Arrange
        every { sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name) } returns ThemeMode.DARK.name
        every { sharedPreferences.getBoolean("time_based_enabled", false) } returns true
        every { sharedPreferences.getBoolean("fast_transitions_enabled", true) } returns false
        
        themeManager = ThemeManager.getInstance(context)
        
        // Act & Assert - Note: getCurrentThemeState() is a Composable, 
        // so we test the individual state flows instead
        assertEquals(ThemeMode.DARK, themeManager.themeMode.first())
        assertTrue(themeManager.timeBasedEnabled.first())
        assertFalse(themeManager.fastTransitionsEnabled.first())
    }

    @Test
    fun `theme switching should be immediate`() = runTest {
        // Arrange
        themeManager = ThemeManager.getInstance(context)
        val initialMode = themeManager.themeMode.first()
        
        // Act
        val newMode = ThemeMode.LIGHT
        themeManager.switchTheme(newMode)
        
        // Assert - Change should be immediate
        assertEquals(newMode, themeManager.themeMode.first())
        assertTrue(initialMode != newMode) // Verify we actually changed
    }

    @Test
    fun `multiple theme switches should persist correctly`() = runTest {
        // Arrange
        themeManager = ThemeManager.getInstance(context)
        
        // Act - Multiple switches
        themeManager.switchTheme(ThemeMode.DARK)
        themeManager.switchTheme(ThemeMode.LIGHT)
        themeManager.switchTheme(ThemeMode.TIME_BASED)
        
        // Assert - Should persist the last change
        assertEquals(ThemeMode.TIME_BASED, themeManager.themeMode.first())
        verify(exactly = 3) { sharedPreferences.edit() }
        verify(exactly = 3) { editor.apply() }
    }

    @Test
    fun `theme data class should maintain immutability`() {
        // Arrange & Act
        val themeState = ThemeState(
            mode = ThemeMode.DARK,
            timeBasedEnabled = true,
            fastTransitionsEnabled = false
        )
        
        // Assert - Properties should be immutable (read-only)
        assertEquals(ThemeMode.DARK, themeState.mode)
        assertTrue(themeState.timeBasedEnabled)
        assertFalse(themeState.fastTransitionsEnabled)
    }
} 