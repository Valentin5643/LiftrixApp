package com.example.liftrix.ui.common

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for ContextualColorOverlay component and supporting functions
 * 
 * Tests cover:
 * - Color context calculations
 * - Workout intensity color mapping
 * - Time-based overlay colors
 * - User preference color handling
 * - Accessibility compliance
 * - Input validation
 */
@RunWith(AndroidJUnit4::class)
class ContextualColorOverlayTest {

    @Test
    fun `getWorkoutIntensityColor returns correct colors for general workout type`() {
        // Test low intensity
        val lowIntensity = getWorkoutIntensityColor(0.1f, WorkoutType.GENERAL)
        assertEquals(LiftrixColors.Primary.copy(alpha = 0.2f), lowIntensity)
        
        // Test moderate intensity
        val moderateIntensity = getWorkoutIntensityColor(0.5f, WorkoutType.GENERAL)
        assertEquals(LiftrixColors.Primary.copy(alpha = 0.5f), moderateIntensity)
        
        // Test high intensity
        val highIntensity = getWorkoutIntensityColor(0.9f, WorkoutType.GENERAL)
        assertEquals(LiftrixColors.Secondary.copy(alpha = 0.8f), highIntensity)
    }

    @Test
    fun `getWorkoutIntensityColor returns specialized colors for cardio workout type`() {
        // Test cardio-specific colors
        val lowCardio = getWorkoutIntensityColor(0.2f, WorkoutType.CARDIO)
        assertEquals(LiftrixColors.Primary.copy(alpha = 0.3f), lowCardio)
        
        val moderateCardio = getWorkoutIntensityColor(0.5f, WorkoutType.CARDIO)
        assertEquals(Color(0xFF42A5F5).copy(alpha = 0.5f), moderateCardio)
        
        val highCardio = getWorkoutIntensityColor(0.9f, WorkoutType.CARDIO)
        assertEquals(Color(0xFF1565C0).copy(alpha = 0.9f), highCardio)
    }

    @Test
    fun `getWorkoutIntensityColor returns specialized colors for strength workout type`() {
        // Test strength-specific colors
        val lowStrength = getWorkoutIntensityColor(0.2f, WorkoutType.STRENGTH)
        assertEquals(LiftrixColors.Accent.copy(alpha = 0.3f), lowStrength)
        
        val moderateStrength = getWorkoutIntensityColor(0.5f, WorkoutType.STRENGTH)
        assertEquals(Color(0xFFFF7043).copy(alpha = 0.5f), moderateStrength)
        
        val highStrength = getWorkoutIntensityColor(0.9f, WorkoutType.STRENGTH)
        assertEquals(Color(0xFFB71C1C).copy(alpha = 0.9f), highStrength)
    }

    @Test
    fun `getWorkoutIntensityColor validates intensity range`() {
        // Test invalid intensity values
        assertThrows(IllegalArgumentException::class.java) {
            getWorkoutIntensityColor(-0.1f)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            getWorkoutIntensityColor(1.1f)
        }
        
        // Test valid boundary values
        assertNotNull(getWorkoutIntensityColor(0.0f))
        assertNotNull(getWorkoutIntensityColor(1.0f))
    }

    @Test
    fun `getTimeBasedOverlay returns correct colors for different times`() {
        // Test morning time (6-11 AM)
        val morningColor = getTimeBasedOverlay(8)
        assertEquals(LiftrixColors.TimeBasedColors.MorningPrimary.copy(alpha = 0.1f), morningColor)
        
        // Test afternoon time (12-17 PM)
        val afternoonColor = getTimeBasedOverlay(14)
        assertEquals(LiftrixColors.TimeBasedColors.AfternoonPrimary.copy(alpha = 0.1f), afternoonColor)
        
        // Test evening time (18-23 PM)
        val eveningColor = getTimeBasedOverlay(20)
        assertEquals(LiftrixColors.TimeBasedColors.EveningPrimary.copy(alpha = 0.1f), eveningColor)
        
        // Test night time (0-5 AM)
        val nightColor = getTimeBasedOverlay(2)
        assertEquals(LiftrixColors.TimeBasedColors.NightPrimary.copy(alpha = 0.1f), nightColor)
    }

    @Test
    fun `getTimeBasedOverlay validates hour range`() {
        // Test invalid hour values
        assertThrows(IllegalArgumentException::class.java) {
            getTimeBasedOverlay(-1)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            getTimeBasedOverlay(24)
        }
        
        // Test valid boundary values
        assertNotNull(getTimeBasedOverlay(0))
        assertNotNull(getTimeBasedOverlay(23))
    }

    @Test
    fun `getTimeBasedOverlay uses custom colors when provided`() {
        val customColors = TimeBasedColorScheme(
            primary = Color.Red,
            accent = Color.Green,
            background = Color.Blue
        )
        
        val customColor = getTimeBasedOverlay(12, customColors)
        assertEquals(Color.Red.copy(alpha = 0.1f), customColor)
    }

    @Test
    fun `calculateWorkoutIntensity returns correct values for different completion percentages`() {
        // Test low completion
        val lowCompletion = calculateWorkoutIntensity(25.0)
        assertEquals(0.25f, lowCompletion, 0.01f)
        
        // Test moderate completion
        val moderateCompletion = calculateWorkoutIntensity(60.0)
        assertEquals(0.6f, moderateCompletion, 0.01f)
        
        // Test high completion
        val highCompletion = calculateWorkoutIntensity(95.0)
        assertEquals(0.95f, highCompletion, 0.01f)
        
        // Test over 100% (should be clamped)
        val overCompletion = calculateWorkoutIntensity(120.0)
        assertEquals(1.0f, overCompletion, 0.01f)
    }

    @Test
    fun `calculateWorkoutIntensity applies duration modifiers correctly`() {
        val baseCompletion = 50.0
        
        // Short workout (should increase intensity)
        val shortWorkout = calculateWorkoutIntensity(baseCompletion, 10)
        assertTrue("Short workout should have higher intensity", shortWorkout > 0.5f)
        
        // Normal workout
        val normalWorkout = calculateWorkoutIntensity(baseCompletion, 30)
        assertEquals(0.5f, normalWorkout, 0.01f)
        
        // Long workout (should decrease intensity)
        val longWorkout = calculateWorkoutIntensity(baseCompletion, 90)
        assertTrue("Long workout should have lower intensity", longWorkout < 0.5f)
    }

    @Test
    fun `calculateWorkoutIntensity applies workout type modifiers correctly`() {
        val baseCompletion = 50.0
        val duration = 30L
        
        // HIIT should have highest modifier
        val hiitIntensity = calculateWorkoutIntensity(baseCompletion, duration, WorkoutType.HIIT)
        assertTrue("HIIT should have highest intensity", hiitIntensity > 0.6f)
        
        // Recovery should have lowest modifier
        val recoveryIntensity = calculateWorkoutIntensity(baseCompletion, duration, WorkoutType.RECOVERY)
        assertTrue("Recovery should have lowest intensity", recoveryIntensity < 0.3f)
        
        // General should be baseline
        val generalIntensity = calculateWorkoutIntensity(baseCompletion, duration, WorkoutType.GENERAL)
        assertEquals(0.5f, generalIntensity, 0.01f)
    }

    @Test
    fun `calculateWorkoutIntensity clamps result to valid range`() {
        // Test with extreme values that could exceed 1.0
        val extremeIntensity = calculateWorkoutIntensity(100.0, 5, WorkoutType.HIIT)
        assertTrue("Intensity should not exceed 1.0", extremeIntensity <= 1.0f)
        
        // Test with values that could go below 0.0
        val lowIntensity = calculateWorkoutIntensity(0.0, 120, WorkoutType.RECOVERY)
        assertTrue("Intensity should not be below 0.0", lowIntensity >= 0.0f)
    }

    @Test
    fun `ColorContext_WorkoutIntensity validates intensity range`() {
        // Test valid intensity
        assertNotNull(ColorContext.WorkoutIntensity(0.5f))
        
        // Test invalid intensities
        assertThrows(IllegalArgumentException::class.java) {
            ColorContext.WorkoutIntensity(-0.1f)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            ColorContext.WorkoutIntensity(1.1f)
        }
    }

    @Test
    fun `ColorContext_TimeOfDay validates hour range`() {
        // Test valid hour
        assertNotNull(ColorContext.TimeOfDay(12))
        
        // Test invalid hours
        assertThrows(IllegalArgumentException::class.java) {
            ColorContext.TimeOfDay(-1)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            ColorContext.TimeOfDay(24)
        }
    }

    @Test
    fun `ColorContext_UserPreference validates intensity range`() {
        // Test valid intensity
        assertNotNull(ColorContext.UserPreference(Color.Red, intensity = 0.5f))
        
        // Test invalid intensities
        assertThrows(IllegalArgumentException::class.java) {
            ColorContext.UserPreference(Color.Red, intensity = -0.1f)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            ColorContext.UserPreference(Color.Red, intensity = 1.1f)
        }
    }

    @Test
    fun `workout types have correct enum values`() {
        // Verify all expected workout types exist
        assertEquals(6, WorkoutType.values().size)
        assertTrue(WorkoutType.values().contains(WorkoutType.GENERAL))
        assertTrue(WorkoutType.values().contains(WorkoutType.CARDIO))
        assertTrue(WorkoutType.values().contains(WorkoutType.STRENGTH))
        assertTrue(WorkoutType.values().contains(WorkoutType.FLEXIBILITY))
        assertTrue(WorkoutType.values().contains(WorkoutType.HIIT))
        assertTrue(WorkoutType.values().contains(WorkoutType.RECOVERY))
    }

    @Test
    fun `color calculations maintain accessibility alpha levels`() {
        // Test that intensity colors maintain reasonable alpha levels for accessibility
        val lowIntensity = getWorkoutIntensityColor(0.1f)
        assertTrue("Low intensity alpha should be reasonable", lowIntensity.alpha <= 0.3f)
        
        val highIntensity = getWorkoutIntensityColor(0.9f)
        assertTrue("High intensity alpha should not be too overwhelming", highIntensity.alpha <= 0.9f)
        
        val timeBasedColor = getTimeBasedOverlay(12)
        assertTrue("Time-based color should be subtle", timeBasedColor.alpha <= 0.2f)
    }

    @Test
    fun `time based color scheme contains all required colors`() {
        val colorScheme = TimeBasedColorScheme(
            primary = Color.Red,
            accent = Color.Green,
            background = Color.Blue
        )
        
        assertEquals(Color.Red, colorScheme.primary)
        assertEquals(Color.Green, colorScheme.accent)
        assertEquals(Color.Blue, colorScheme.background)
    }
} 