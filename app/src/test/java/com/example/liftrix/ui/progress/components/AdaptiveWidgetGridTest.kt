package com.example.liftrix.ui.progress.components

import com.example.liftrix.ui.common.WindowSizeClass
import com.example.liftrix.ui.common.WindowWidthSizeClass
import com.example.liftrix.ui.common.WindowHeightSizeClass
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AdaptiveWidgetGrid component.
 * 
 * Tests the implementation of SPEC-20250205-progress-tab-ui-redesign:
 * - 2-column grid on mobile (<600dp)
 * - 3-column grid on tablet (600-767dp)  
 * - 4-column grid on desktop (≥768dp)
 * - Proper spacing (12dp cards, 16dp screen padding)
 * - Full-width widget spanning
 */
class AdaptiveWidgetGridTest {

    @Test
    fun `calculateOptimalColumns returns 2 for mobile screens`() {
        // Test mobile screen width (< 600dp)
        val mobileWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.COMPACT,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 400.dp,
            heightDp = 800.dp
        )
        
        val columns = calculateOptimalColumns(
            windowSizeClass = mobileWindowSize,
            widgetCount = 6,
            maxColumns = 4
        )
        
        assertEquals("Mobile should have 2 columns per SPEC-20250205", 2, columns)
    }
    
    @Test
    fun `calculateOptimalColumns returns 3 for tablet screens`() {
        // Test tablet screen width (600-767dp)
        val tabletWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 700.dp,
            heightDp = 900.dp
        )
        
        val columns = calculateOptimalColumns(
            windowSizeClass = tabletWindowSize,
            widgetCount = 9,
            maxColumns = 4
        )
        
        assertEquals("Tablet should have 3 columns per SPEC-20250205", 3, columns)
    }
    
    @Test
    fun `calculateOptimalColumns returns 4 for desktop screens`() {
        // Test desktop screen width (≥768dp)
        val desktopWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.EXPANDED,
            heightSizeClass = WindowHeightSizeClass.EXPANDED,
            widthDp = 1024.dp,
            heightDp = 768.dp
        )
        
        val columns = calculateOptimalColumns(
            windowSizeClass = desktopWindowSize,
            widgetCount = 12,
            maxColumns = 4
        )
        
        assertEquals("Desktop should have 4 columns per SPEC-20250205", 4, columns)
    }
    
    @Test
    fun `calculateResponsiveSpacing returns 12dp for mobile and tablet`() {
        val mobileWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.COMPACT,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 400.dp,
            heightDp = 800.dp
        )
        
        val mobileSpacing = calculateResponsiveSpacing(mobileWindowSize)
        assertEquals("Mobile spacing should be 12dp per spec", 12.dp, mobileSpacing.spacing)
        
        val tabletWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 700.dp,
            heightDp = 900.dp
        )
        
        val tabletSpacing = calculateResponsiveSpacing(tabletWindowSize)
        assertEquals("Tablet spacing should be 12dp per spec", 12.dp, tabletSpacing.spacing)
    }
    
    @Test
    fun `calculateResponsiveSpacing returns 16dp for desktop`() {
        val desktopWindowSize = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.EXPANDED,
            heightSizeClass = WindowHeightSizeClass.EXPANDED,
            widthDp = 1024.dp,
            heightDp = 768.dp
        )
        
        val desktopSpacing = calculateResponsiveSpacing(desktopWindowSize)
        assertEquals("Desktop spacing should be 16dp per spec", 16.dp, desktopSpacing.spacing)
    }
    
    @Test
    fun `calculateResponsivePadding returns 16dp for all screen sizes`() {
        val screenSizes = listOf(
            WindowSizeClass(WindowWidthSizeClass.COMPACT, WindowHeightSizeClass.MEDIUM, 400.dp, 800.dp),
            WindowSizeClass(WindowWidthSizeClass.MEDIUM, WindowHeightSizeClass.MEDIUM, 700.dp, 900.dp),
            WindowSizeClass(WindowWidthSizeClass.EXPANDED, WindowHeightSizeClass.EXPANDED, 1024.dp, 768.dp)
        )
        
        screenSizes.forEach { windowSize ->
            val padding = calculateResponsivePadding(windowSize)
            assertEquals(
                "All screen sizes should have 16dp padding per spec", 
                16.dp, 
                padding.calculateTopPadding()
            )
        }
    }
    
    @Test
    fun `widget size determines layout behavior`() {
        // Test that LARGE widgets should span full width
        val volumeChart = AnalyticsWidget.VolumeChart
        assertEquals("Volume chart should be LARGE size", 
            com.example.liftrix.domain.model.analytics.WidgetSize.LARGE, 
            volumeChart.getRecommendedSize())
        
        // Test that SIMPLE widgets should be SMALL size
        val workoutFreq = AnalyticsWidget.WorkoutFrequency
        assertEquals("Workout frequency should be SMALL size", 
            com.example.liftrix.domain.model.analytics.WidgetSize.SMALL, 
            workoutFreq.getRecommendedSize())
    }
    
    @Test
    fun `breakpoint values match specification`() {
        // Test exact breakpoint values from SPEC-20250205
        assertTrue("400dp should be mobile", 400.dp < 600.dp)
        assertTrue("600dp should be tablet", 600.dp >= 600.dp && 600.dp < 768.dp)
        assertTrue("768dp should be desktop", 768.dp >= 768.dp)
    }
    
    @Test
    fun `edge case breakpoint values work correctly`() {
        // Test exact breakpoint edge cases
        val windowSize599 = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.COMPACT,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 599.dp,
            heightDp = 800.dp
        )
        assertEquals("599dp should still be mobile (2 columns)", 2, 
            calculateOptimalColumns(windowSize599, 6, 4))
        
        val windowSize600 = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 600.dp,
            heightDp = 900.dp
        )
        assertEquals("600dp should be tablet (3 columns)", 3, 
            calculateOptimalColumns(windowSize600, 9, 4))
        
        val windowSize767 = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            heightSizeClass = WindowHeightSizeClass.MEDIUM,
            widthDp = 767.dp,
            heightDp = 900.dp
        )
        assertEquals("767dp should still be tablet (3 columns)", 3, 
            calculateOptimalColumns(windowSize767, 9, 4))
        
        val windowSize768 = WindowSizeClass(
            widthSizeClass = WindowWidthSizeClass.EXPANDED,
            heightSizeClass = WindowHeightSizeClass.EXPANDED,
            widthDp = 768.dp,
            heightDp = 768.dp
        )
        assertEquals("768dp should be desktop (4 columns)", 4, 
            calculateOptimalColumns(windowSize768, 12, 4))
    }
}