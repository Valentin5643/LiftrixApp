package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.liftrix.ui.theme.ColorSystemOptimizations
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for COLOR-013 color caching optimizations
 * 
 * Tests the performance optimizations implemented in ColorSystemOptimizations
 * focusing on caching efficiency, memory management, and performance validation.
 */
class ColorCachingTest {
    
    @Before
    fun setup() {
        // Clear all caches for clean testing
        ColorSystemOptimizations.ThemePerformanceMonitor.clearMetrics()
        ColorSystemOptimizations.ColorMemoryManager.clearCache()
        ColorSystemOptimizations.SmartCaching.clearCache()
    }
    
    @Test
    fun `verify pre-calculated color schemes are identical`() {
        // Get color schemes multiple times
        val lightScheme1 = ColorSystemOptimizations.getColorScheme(false)
        val lightScheme2 = ColorSystemOptimizations.getColorScheme(false)
        val darkScheme1 = ColorSystemOptimizations.getColorScheme(true)
        val darkScheme2 = ColorSystemOptimizations.getColorScheme(true)
        
        // Verify identity (same object reference for caching)
        assertSame("Light color schemes should be the same cached instance", lightScheme1, lightScheme2)
        assertSame("Dark color schemes should be the same cached instance", darkScheme1, darkScheme2)
        
        // Verify they are different schemes
        assertNotSame("Light and dark schemes should be different", lightScheme1, darkScheme1)
    }
    
    @Test
    fun `verify 5-color system colors are correctly applied`() {
        val lightScheme = ColorSystemOptimizations.getColorScheme(false)
        val darkScheme = ColorSystemOptimizations.getColorScheme(true)
        
        // Verify primary colors use Persian Green consistently
        assertEquals("Light theme primary should be Persian Green", 
            LiftrixColors.PersianGreen, lightScheme.primary)
        assertEquals("Dark theme primary should be Persian Green", 
            LiftrixColors.PersianGreen, darkScheme.primary)
        
        // Verify secondary colors use Tiffany Blue consistently
        assertEquals("Light theme secondary should be Tiffany Blue", 
            LiftrixColors.TiffanyBlue, lightScheme.secondary)
        assertEquals("Dark theme secondary should be Tiffany Blue", 
            LiftrixColors.TiffanyBlue, darkScheme.secondary)
        
        // Verify surface colors use correct 5-color assignments
        assertEquals("Light theme surface should be Snow", 
            LiftrixColors.Snow, lightScheme.surface)
        assertEquals("Dark theme surface should be Jet", 
            LiftrixColors.Jet, darkScheme.surface)
        
        // Verify background colors
        assertEquals("Light theme background should be Snow", 
            LiftrixColors.Snow, lightScheme.background)
        assertEquals("Dark theme background should be Night", 
            LiftrixColors.Night, darkScheme.background)
    }
    
    @Test
    fun `test container color caching performance`() {
        val startTime = System.nanoTime()
        
        // Access container colors multiple times
        repeat(100) {
            ColorSystemOptimizations.CachedContainerColors.getPrimaryContainer(true)
            ColorSystemOptimizations.CachedContainerColors.getPrimaryContainer(false)
            ColorSystemOptimizations.CachedContainerColors.getSecondaryContainer(true)
            ColorSystemOptimizations.CachedContainerColors.getSecondaryContainer(false)
        }
        
        val totalTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
        val averageTimePerAccess = totalTime.toFloat() / 400 // 100 iterations * 4 accesses
        
        // Should be very fast due to pre-calculated values
        assertTrue(
            "Container color access should be very fast (<1ms per access), actual: ${averageTimePerAccess}ms",
            averageTimePerAccess < 1.0f
        )
        
        // Verify correct colors are returned
        val lightPrimaryContainer = ColorSystemOptimizations.CachedContainerColors.getPrimaryContainer(false)
        val darkPrimaryContainer = ColorSystemOptimizations.CachedContainerColors.getPrimaryContainer(true)
        
        // Light theme should have lower alpha
        assertTrue("Light theme container should have lower alpha than dark theme",
            lightPrimaryContainer.alpha < darkPrimaryContainer.alpha)
    }
    
    @Test
    fun `test color memory manager efficiency`() {
        // Pre-populate cache should include 5-color system colors
        val initialCacheSize = ColorSystemOptimizations.ColorMemoryManager.getCacheSize()
        
        assertTrue("Cache should be pre-populated with 5-color system colors", 
            initialCacheSize >= 5)
        
        // Test cache efficiency
        val cachedColor1 = ColorSystemOptimizations.ColorMemoryManager.getCachedColor(0xFF131515) // Night
        val cachedColor2 = ColorSystemOptimizations.ColorMemoryManager.getCachedColor(0xFF131515) // Night again
        
        // Should return the same instance
        assertSame("Cached colors should return same instance", cachedColor1, cachedColor2)
        
        // Verify color values
        assertEquals("Cached Night color should match", LiftrixColors.Night, cachedColor1)
        
        // Test cache growth
        val newColor = ColorSystemOptimizations.ColorMemoryManager.getCachedColor(0xFF123456)
        val finalCacheSize = ColorSystemOptimizations.ColorMemoryManager.getCacheSize()
        
        assertEquals("Cache size should increase by 1", initialCacheSize + 1, finalCacheSize)
    }
    
    @Test
    fun `test smart caching hit rate calculation`() {
        // Clear cache
        ColorSystemOptimizations.SmartCaching.clearCache()
        
        // Generate cache activity
        val baseColor = LiftrixColors.PersianGreen
        
        // First access (cache miss)
        ColorSystemOptimizations.SmartCaching.getCachedColorVariation("PersianGreen", 0.1f, baseColor)
        
        // Second access (cache hit)
        ColorSystemOptimizations.SmartCaching.getCachedColorVariation("PersianGreen", 0.1f, baseColor)
        
        // Third access different variation (cache miss)
        ColorSystemOptimizations.SmartCaching.getCachedColorVariation("PersianGreen", 0.2f, baseColor)
        
        // Fourth access same as first (cache hit)
        ColorSystemOptimizations.SmartCaching.getCachedColorVariation("PersianGreen", 0.1f, baseColor)
        
        val hitRate = ColorSystemOptimizations.SmartCaching.getCacheHitRate()
        
        // Should have 50% hit rate (2 hits out of 4 total accesses)
        val expectedHitRate = 50f
        val tolerance = 5f // Allow 5% tolerance
        
        assertTrue(
            "Cache hit rate should be around ${expectedHitRate}%, actual: ${hitRate}%",
            abs(hitRate - expectedHitRate) <= tolerance
        )
        
        // Verify cache stats make sense
        val stats = ColorSystemOptimizations.SmartCaching.getCacheStats()
        assertTrue("Cache stats should contain hit information", stats.contains("hits"))
        assertTrue("Cache stats should contain miss information", stats.contains("misses"))
    }
    
    @Test
    fun `test performance validation targets`() {
        // Simulate some theme switching activity
        repeat(5) {
            val startTime = System.nanoTime()
            ColorSystemOptimizations.getColorScheme(true)
            ColorSystemOptimizations.getColorScheme(false)
            val duration = (System.nanoTime() - startTime) / 1_000_000
            
            // Record performance
            if (duration > 0) {
                ColorSystemOptimizations.ThemePerformanceMonitor.recordThemeCreationTime("test", duration)
            }
        }
        
        // Test performance validation
        val performanceScore = ColorSystemOptimizations.PerformanceValidation.getPerformanceScore()
        val targetsValid = ColorSystemOptimizations.PerformanceValidation.validatePerformanceTargets()
        
        // Performance score should be reasonable (0-100)
        assertTrue("Performance score should be between 0 and 100", 
            performanceScore >= 0f && performanceScore <= 100f)
        
        // With pre-calculated schemes, targets should be met
        assertTrue("Performance targets should be met with optimized system", targetsValid)
    }
    
    @Test
    fun `test theme performance monitoring accuracy`() {
        // Clear metrics
        ColorSystemOptimizations.ThemePerformanceMonitor.clearMetrics()
        
        // Simulate theme switching
        ColorSystemOptimizations.ThemePerformanceMonitor.startThemeSwitching()
        
        // Simulate some work (sleep for controlled time)
        Thread.sleep(10) // 10ms
        
        ColorSystemOptimizations.ThemePerformanceMonitor.endThemeSwitching()
        
        val averageTime = ColorSystemOptimizations.ThemePerformanceMonitor.getAverageThemeSwitchingTime()
        
        // Should be close to our sleep time (within reasonable margin)
        assertTrue("Average switching time should be reasonable (5-50ms), actual: ${averageTime}ms",
            averageTime >= 5f && averageTime <= 50f)
        
        // Test multiple measurements
        repeat(3) {
            ColorSystemOptimizations.ThemePerformanceMonitor.startThemeSwitching()
            Thread.sleep(5) // 5ms
            ColorSystemOptimizations.ThemePerformanceMonitor.endThemeSwitching()
        }
        
        val newAverageTime = ColorSystemOptimizations.ThemePerformanceMonitor.getAverageThemeSwitchingTime()
        
        // Average should have changed and be reasonable
        assertTrue("New average time should be different and reasonable",
            newAverageTime != averageTime && newAverageTime >= 3f && newAverageTime <= 30f)
    }
    
    @Test
    fun `test performance report generation`() {
        // Generate some activity
        ColorSystemOptimizations.ThemePerformanceMonitor.startThemeSwitching()
        Thread.sleep(5)
        ColorSystemOptimizations.ThemePerformanceMonitor.endThemeSwitching()
        
        // Access some cached colors
        ColorSystemOptimizations.SmartCaching.getCachedColorVariation("Test", 0.5f, Color.Red)
        
        // Generate report
        val report = ColorSystemOptimizations.ThemePerformanceMonitor.getThemePerformanceReport()
        
        // Verify report structure
        assertTrue("Report should contain performance report header", 
            report.contains("5-Color System Performance Report"))
        assertTrue("Report should contain switching time", 
            report.contains("theme switching time"))
        assertTrue("Report should contain cache size", 
            report.contains("cache size"))
        assertTrue("Report should contain sample count", 
            report.contains("samples"))
        
        // Report should not be empty
        assertTrue("Report should have substantial content", report.length > 100)
    }
    
    @Test
    fun `test color variation alpha calculations`() {
        val baseColor = LiftrixColors.PersianGreen
        
        // Test various alpha values
        val alphas = listOf(0.1f, 0.2f, 0.5f, 0.8f)
        
        alphas.forEach { alpha ->
            val variation = ColorSystemOptimizations.SmartCaching.getCachedColorVariation(
                "PersianGreen", alpha, baseColor
            )
            
            // Verify alpha is applied correctly
            assertEquals("Alpha should be applied correctly", alpha, variation.alpha, 0.01f)
            
            // Verify base color components are maintained
            assertEquals("Red component should be preserved", baseColor.red, variation.red, 0.01f)
            assertEquals("Green component should be preserved", baseColor.green, variation.green, 0.01f)
            assertEquals("Blue component should be preserved", baseColor.blue, variation.blue, 0.01f)
        }
    }
    
    @Test
    fun `test cache clearing functionality`() {
        // Populate caches
        ColorSystemOptimizations.ColorMemoryManager.getCachedColor(0xFF123456)
        ColorSystemOptimizations.SmartCaching.getCachedColorVariation("Test", 0.5f, Color.Blue)
        
        val initialColorCacheSize = ColorSystemOptimizations.ColorMemoryManager.getCacheSize()
        val initialHitRate = ColorSystemOptimizations.SmartCaching.getCacheHitRate()
        
        assertTrue("Color cache should have content", initialColorCacheSize > 0)
        
        // Clear caches
        ColorSystemOptimizations.ColorMemoryManager.clearCache()
        ColorSystemOptimizations.SmartCaching.clearCache()
        
        val finalColorCacheSize = ColorSystemOptimizations.ColorMemoryManager.getCacheSize()
        val finalHitRate = ColorSystemOptimizations.SmartCaching.getCacheHitRate()
        
        assertEquals("Color cache should be empty after clearing", 0, finalColorCacheSize)
        assertEquals("Hit rate should be 0 after clearing", 0f, finalHitRate, 0.01f)
    }
}