package com.example.liftrix.design

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixColors
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Comprehensive color usage audit test for design compliance
 */
@RunWith(AndroidJUnit4::class)
class ColorUsageAuditTest {

    @Test
    fun validateLiftrixColorsGreyUsage() {
        // Collect all main brand colors
        val brandColors = listOf(
            LiftrixColors.Primary,
            LiftrixColors.Secondary,
            LiftrixColors.Accent,
            LiftrixColors.BackgroundLight,
            LiftrixColors.BackgroundDark,
            LiftrixColors.SurfaceLight,
            LiftrixColors.SurfaceDark,
            LiftrixColors.SurfaceVariant,
            LiftrixColors.SurfaceVariantDark,
            LiftrixColors.Outline,
            LiftrixColors.OutlineVariant,
            LiftrixColors.OutlineDark,
            LiftrixColors.OutlineVariantDark
        )
        
        val result = GreyUsageAnalyzer.validateGreyUsage(brandColors)
        
        assertTrue("Brand colors should meet <20% grey usage target", result.isValid)
        
        // Log results for review
        println("Brand Colors Grey Usage Audit:")
        println("Total colors: ${result.totalColors}")
        println("Grey colors: ${result.greyColors}")
        println("Grey percentage: ${String.format("%.1f", result.greyPercentage)}%")
        println("Meets target: ${result.isValid}")
    }
    
    @Test
    fun validateContainerColorsGreyUsage() {
        val containerColors = listOf(
            LiftrixColors.PrimaryContainer,
            LiftrixColors.PrimaryContainerDark,
            LiftrixColors.SecondaryContainer,
            LiftrixColors.SecondaryContainerDark,
            LiftrixColors.TertiaryContainer,
            LiftrixColors.TertiaryContainerDark,
            LiftrixColors.ErrorContainer,
            LiftrixColors.ErrorContainerDark
        )
        
        val result = GreyUsageAnalyzer.validateGreyUsage(containerColors)
        
        assertTrue("Container colors should meet <20% grey usage target", result.isValid)
        
        println("Container Colors Grey Usage Audit:")
        println("Total colors: ${result.totalColors}")
        println("Grey colors: ${result.greyColors}")
        println("Grey percentage: ${String.format("%.1f", result.greyPercentage)}%")
        println("Meets target: ${result.isValid}")
    }
    
    @Test
    fun ensureTealsAreNotDetectedAsGrey() {
        val tealColors = listOf(
            LiftrixColors.Primary,           // Main teal
            LiftrixColors.TealLight,
            LiftrixColors.TealDark,
            LiftrixColors.SurfaceVariant,    // Teal-tinted
            LiftrixColors.OutlineVariant     // Teal-based
        )
        
        tealColors.forEach { color ->
            assertFalse(
                "Teal color should not be detected as grey: $color",
                GreyUsageAnalyzer.isGreyColor(color)
            )
        }
    }
    
    @Test
    fun detectKnownGreyColors() {
        val greyColors = listOf(
            Color(0.5f, 0.5f, 0.5f),     // Pure grey
            Color(0.3f, 0.3f, 0.3f),     // Dark grey  
            Color(0.7f, 0.7f, 0.7f),     // Light grey
            Color(0.2f, 0.2f, 0.2f)      // Very dark grey
        )
        
        greyColors.forEach { color ->
            assertTrue(
                "Known grey color should be detected as grey: $color",
                GreyUsageAnalyzer.isGreyColor(color)
            )
        }
    }
}