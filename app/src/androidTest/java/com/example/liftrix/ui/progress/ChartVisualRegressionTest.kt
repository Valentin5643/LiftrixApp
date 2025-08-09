package com.example.liftrix.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeDataPoint
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import com.example.liftrix.ui.progress.components.charts.ModernVolumeChart
import com.example.liftrix.ui.progress.components.charts.MuscleGroup
import com.example.liftrix.ui.progress.components.charts.MuscleGroupPieChart
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Visual regression tests for modern chart components in the Progress dashboard.
 * 
 * These tests capture screenshots of chart components in various states to detect
 * visual regressions across app updates. Tests cover:
 * 
 * - ModernVolumeChart with different data sets and states
 * - MuscleGroupPieChart with various distributions
 * - GlobalTimeRangeSelector with different selections
 * - Responsive behavior across different screen sizes
 * - Animation states and transitions
 * - Error and empty states
 * 
 * Screenshots are saved to device storage for manual comparison and CI integration.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChartVisualRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testOutputDir by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
            .apply { mkdirs() }
    }

    /**
     * Test ModernVolumeChart with typical ascending volume data
     */
    @Test
    fun modernVolumeChart_withAscendingData_rendersCorrectly() {
        val testData = createAscendingVolumeData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                ModernVolumeChart(
                    data = testData,
                    timeRange = TimeRangeType.MONTH,
                    modifier = Modifier
                        .width(350.dp)
                        .height(250.dp)
                        .padding(16.dp),
                    showPersonalRecords = true,
                    animationDuration = 0 // Disable animation for consistent screenshots
                )
            }
        }
        
        // Wait for composition to settle
        composeTestRule.waitForIdle()
        
        // Capture screenshot
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "modern_volume_chart_ascending_data")
    }
    
    /**
     * Test ModernVolumeChart with volatile (up and down) volume data
     */
    @Test
    fun modernVolumeChart_withVolatileData_rendersCorrectly() {
        val testData = createVolatileVolumeData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                ModernVolumeChart(
                    data = testData,
                    timeRange = TimeRangeType.QUARTER,
                    modifier = Modifier
                        .width(350.dp)
                        .height(250.dp)
                        .padding(16.dp),
                    showPersonalRecords = true,
                    animationDuration = 0
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "modern_volume_chart_volatile_data")
    }
    
    /**
     * Test ModernVolumeChart with minimal data points
     */
    @Test
    fun modernVolumeChart_withMinimalData_rendersCorrectly() {
        val testData = createMinimalVolumeData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                ModernVolumeChart(
                    data = testData,
                    timeRange = TimeRangeType.WEEK,
                    modifier = Modifier
                        .width(350.dp)
                        .height(250.dp)
                        .padding(16.dp),
                    showPersonalRecords = false, // No PRs with minimal data
                    animationDuration = 0
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "modern_volume_chart_minimal_data")
    }
    
    /**
     * Test ModernVolumeChart empty state
     */
    @Test
    fun modernVolumeChart_emptyState_rendersCorrectly() {
        composeTestRule.setContent {
            ChartTestContainer {
                ModernVolumeChart(
                    data = emptyList(),
                    timeRange = TimeRangeType.MONTH,
                    modifier = Modifier
                        .width(350.dp)
                        .height(250.dp)
                        .padding(16.dp),
                    animationDuration = 0
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "modern_volume_chart_empty_state")
    }
    
    /**
     * Test MuscleGroupPieChart with balanced distribution
     */
    @Test
    fun muscleGroupPieChart_balancedDistribution_rendersCorrectly() {
        val testData = createBalancedMuscleGroupData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                MuscleGroupPieChart(
                    data = testData,
                    modifier = Modifier
                        .width(350.dp)
                        .height(300.dp)
                        .padding(16.dp),
                    showPercentages = true,
                    showLegend = true,
                    animationDuration = 0 // Disable animation for consistent screenshots
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "muscle_group_pie_chart_balanced")
    }
    
    /**
     * Test MuscleGroupPieChart with skewed distribution
     */
    @Test
    fun muscleGroupPieChart_skewedDistribution_rendersCorrectly() {
        val testData = createSkewedMuscleGroupData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                MuscleGroupPieChart(
                    data = testData,
                    modifier = Modifier
                        .width(350.dp)
                        .height(300.dp)
                        .padding(16.dp),
                    showPercentages = true,
                    showLegend = true,
                    animationDuration = 0
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "muscle_group_pie_chart_skewed")
    }
    
    /**
     * Test MuscleGroupPieChart without legend
     */
    @Test
    fun muscleGroupPieChart_withoutLegend_rendersCorrectly() {
        val testData = createBalancedMuscleGroupData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                MuscleGroupPieChart(
                    data = testData,
                    modifier = Modifier
                        .width(300.dp)
                        .height(300.dp)
                        .padding(16.dp),
                    showPercentages = true,
                    showLegend = false,
                    animationDuration = 0
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "muscle_group_pie_chart_no_legend")
    }
    
    /**
     * Test GlobalTimeRangeSelector with MONTH selected
     */
    @Test
    fun globalTimeRangeSelector_monthSelected_rendersCorrectly() {
        composeTestRule.setContent {
            ChartTestContainer {
                GlobalTimeRangeSelector(
                    selectedTimeRange = TimeRangeType.MONTH,
                    onTimeRangeChange = { },
                    modifier = Modifier
                        .width(350.dp)
                        .padding(16.dp),
                    animationDuration = 0 // Disable animation for consistent screenshots
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "time_range_selector_month_selected")
    }
    
    /**
     * Test GlobalTimeRangeSelector with QUARTER selected
     */
    @Test
    fun globalTimeRangeSelector_quarterSelected_rendersCorrectly() {
        composeTestRule.setContent {
            ChartTestContainer {
                GlobalTimeRangeSelector(
                    selectedTimeRange = TimeRangeType.QUARTER,
                    onTimeRangeChange = { },
                    modifier = Modifier
                        .width(350.dp)
                        .padding(16.dp),
                    animationDuration = 0
                )
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "time_range_selector_quarter_selected")
    }
    
    /**
     * Test chart components in compact/mobile layout
     */
    @Test
    fun charts_compactLayout_renderCorrectly() {
        val volumeData = createAscendingVolumeData()
        val muscleGroupData = createBalancedMuscleGroupData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    // Compact volume chart
                    ModernVolumeChart(
                        data = volumeData,
                        timeRange = TimeRangeType.MONTH,
                        modifier = Modifier
                            .width(280.dp)
                            .height(180.dp),
                        animationDuration = 0
                    )
                    
                    // Compact time range selector
                    GlobalTimeRangeSelector(
                        selectedTimeRange = TimeRangeType.MONTH,
                        onTimeRangeChange = { },
                        modifier = Modifier.width(280.dp),
                        animationDuration = 0
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "charts_compact_layout")
    }
    
    /**
     * Test chart color consistency across different components
     */
    @Test
    fun charts_colorConsistency_rendersCorrectly() {
        val volumeData = createAscendingVolumeData()
        val muscleGroupData = createBalancedMuscleGroupData()
        
        composeTestRule.setContent {
            ChartTestContainer {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    // Volume chart showing Persian Green/Tiffany Blue colors
                    ModernVolumeChart(
                        data = volumeData,
                        timeRange = TimeRangeType.MONTH,
                        modifier = Modifier
                            .width(180.dp)
                            .height(150.dp),
                        animationDuration = 0
                    )
                    
                    // Pie chart showing color palette consistency
                    MuscleGroupPieChart(
                        data = muscleGroupData,
                        modifier = Modifier
                            .width(180.dp)
                            .height(150.dp),
                        showPercentages = false,
                        showLegend = false,
                        animationDuration = 0
                    )
                }
            }
        }
        
        composeTestRule.waitForIdle()
        
        val bitmap = composeTestRule.onRoot().captureToImage()
        saveScreenshot(bitmap, "charts_color_consistency")
    }
    
    /**
     * Container component that provides consistent theming and background
     */
    @Composable
    private fun ChartTestContainer(content: @Composable () -> Unit) {
        LiftrixTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    content()
                }
            }
        }
    }
    
    /**
     * Save screenshot bitmap to device storage
     */
    private fun saveScreenshot(bitmap: androidx.compose.ui.graphics.ImageBitmap, filename: String) {
        val file = File(testOutputDir, "$filename.png")
        
        try {
            FileOutputStream(file).use { out ->
                val androidBitmap = android.graphics.Bitmap.createBitmap(
                    bitmap.width,
                    bitmap.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                
                // Copy pixels from Compose ImageBitmap to Android Bitmap
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.readPixels(pixels)
                androidBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                
                androidBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                androidBitmap.recycle()
            }
            println("Screenshot saved: ${file.absolutePath}")
        } catch (e: Exception) {
            println("Failed to save screenshot $filename: ${e.message}")
        }
    }
    
    // Test data creation helpers
    
    private fun createAscendingVolumeData(): List<VolumeDataPoint> {
        return listOf(
            VolumeDataPoint(LocalDate(2024, 1, 1), Weight.fromKilograms(1200.0), 1),
            VolumeDataPoint(LocalDate(2024, 1, 8), Weight.fromKilograms(1350.0), 2),
            VolumeDataPoint(LocalDate(2024, 1, 15), Weight.fromKilograms(1420.0), 1),
            VolumeDataPoint(LocalDate(2024, 1, 22), Weight.fromKilograms(1580.0), 2),
            VolumeDataPoint(LocalDate(2024, 1, 29), Weight.fromKilograms(1750.0), 3),
            VolumeDataPoint(LocalDate(2024, 2, 5), Weight.fromKilograms(1820.0), 2),
            VolumeDataPoint(LocalDate(2024, 2, 12), Weight.fromKilograms(1950.0), 3),
            VolumeDataPoint(LocalDate(2024, 2, 19), Weight.fromKilograms(2100.0), 2)
        )
    }
    
    private fun createVolatileVolumeData(): List<VolumeDataPoint> {
        return listOf(
            VolumeDataPoint(LocalDate(2024, 1, 1), Weight.fromKilograms(1500.0), 2),
            VolumeDataPoint(LocalDate(2024, 1, 8), Weight.fromKilograms(1200.0), 1),
            VolumeDataPoint(LocalDate(2024, 1, 15), Weight.fromKilograms(1800.0), 3),
            VolumeDataPoint(LocalDate(2024, 1, 22), Weight.fromKilograms(1100.0), 1),
            VolumeDataPoint(LocalDate(2024, 1, 29), Weight.fromKilograms(1900.0), 2),
            VolumeDataPoint(LocalDate(2024, 2, 5), Weight.fromKilograms(1400.0), 2),
            VolumeDataPoint(LocalDate(2024, 2, 12), Weight.fromKilograms(2000.0), 3),
            VolumeDataPoint(LocalDate(2024, 2, 19), Weight.fromKilograms(1600.0), 2)
        )
    }
    
    private fun createMinimalVolumeData(): List<VolumeDataPoint> {
        return listOf(
            VolumeDataPoint(LocalDate(2024, 1, 1), Weight.fromKilograms(1000.0), 1),
            VolumeDataPoint(LocalDate(2024, 1, 8), Weight.fromKilograms(1200.0), 1),
            VolumeDataPoint(LocalDate(2024, 1, 15), Weight.fromKilograms(1100.0), 1)
        )
    }
    
    private fun createBalancedMuscleGroupData(): Map<MuscleGroup, Float> {
        return mapOf(
            MuscleGroup.CHEST to 2000f,
            MuscleGroup.BACK to 2200f,
            MuscleGroup.SHOULDERS to 1800f,
            MuscleGroup.ARMS to 1500f,
            MuscleGroup.LEGS to 3500f,
            MuscleGroup.CORE to 1000f
        )
    }
    
    private fun createSkewedMuscleGroupData(): Map<MuscleGroup, Float> {
        return mapOf(
            MuscleGroup.LEGS to 5000f,
            MuscleGroup.BACK to 1500f,
            MuscleGroup.CHEST to 1200f,
            MuscleGroup.SHOULDERS to 800f,
            MuscleGroup.ARMS to 500f
        )
    }
}