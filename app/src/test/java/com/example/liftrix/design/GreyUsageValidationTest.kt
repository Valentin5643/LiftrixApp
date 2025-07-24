package com.example.liftrix.design

import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for grey usage validation ensuring <20% target compliance
 */
class GreyUsageValidationTest {

    @Test
    fun `isGreyColor detects pure grey correctly`() {
        val pureGrey = Color(0.5f, 0.5f, 0.5f)
        assertTrue(GreyUsageAnalyzer.isGreyColor(pureGrey))
    }
    
    @Test
    fun `isGreyColor rejects teal color`() {
        val teal = Color(0.125f, 0.788f, 0.718f) // #20C9B7
        assertFalse(GreyUsageAnalyzer.isGreyColor(teal))
    }
    
    @Test
    fun `calculateGreyPercentage returns correct percentage`() {
        val colors = listOf(
            Color(0.5f, 0.5f, 0.5f), // Grey
            Color.Red,
            Color.Green,
            Color(0.3f, 0.3f, 0.3f)  // Grey
        )
        val percentage = GreyUsageAnalyzer.calculateGreyPercentage(colors)
        assertEquals(50.0, percentage, 0.1)
    }
    
    @Test
    fun `validateGreyUsage passes when under 20 percent`() {
        val colors = listOf(
            Color(0.5f, 0.5f, 0.5f), // 1 grey out of 10 = 10%
            Color.Red, Color.Green, Color.Blue, Color.Yellow,
            Color.Cyan, Color.Magenta, Color.White, Color.Black
        )
        val result = GreyUsageAnalyzer.validateGreyUsage(colors)
        assertTrue("Should pass with 10% grey usage", result.isValid)
        assertEquals(10.0, result.greyPercentage, 1.0)
    }
    
    @Test
    fun `validateGreyUsage fails when over 20 percent`() {
        val colors = listOf(
            Color(0.5f, 0.5f, 0.5f), // 3 greys out of 10 = 30%
            Color(0.3f, 0.3f, 0.3f),
            Color(0.7f, 0.7f, 0.7f),
            Color.Red, Color.Green, Color.Blue, Color.Yellow
        )
        val result = GreyUsageAnalyzer.validateGreyUsage(colors)
        assertFalse("Should fail with 30% grey usage", result.isValid) 
        assertEquals(30.0, result.greyPercentage, 1.0)
    }
    
    @Test
    fun `theme colors validation meets target`() {
        val themeColors = GreyUsageAnalyzer.getThemeColors()
        val result = GreyUsageAnalyzer.validateGreyUsage(themeColors)
        
        assertTrue("Theme should have <20% grey usage", result.isValid)
        assertTrue("Theme should have colors to validate", themeColors.isNotEmpty())
        
        println("Theme grey usage: ${result.greyPercentage}%")
    }
}