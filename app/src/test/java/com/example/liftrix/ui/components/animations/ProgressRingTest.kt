package com.example.liftrix.ui.components.animations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test suite for ProgressRing animation components
 * Tests progress calculations, rendering, and animation behavior
 */
@RunWith(AndroidJUnit4::class)
class ProgressRingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Test basic progress ring rendering
     */
    @Test
    fun progressRing_rendersCorrectly() {
        val testProgress = 0.75f
        
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressRing(
                    progress = testProgress,
                    size = 120.dp,
                    strokeWidth = 8.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 75%")
            .assertIsDisplayed()
    }

    /**
     * Test animated progress ring rendering
     */
    @Test
    fun animatedProgressRing_rendersCorrectly() {
        val testProgress = 0.5f
        
        composeTestRule.setContent {
            LiftrixTheme {
                AnimatedProgressRing(
                    progress = testProgress,
                    size = 120.dp,
                    strokeWidth = 8.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 50%")
            .assertIsDisplayed()
    }

    /**
     * Test progress ring with gradient rendering
     */
    @Test
    fun animatedProgressRingWithGradient_rendersCorrectly() {
        val testProgress = 0.85f
        
        composeTestRule.setContent {
            LiftrixTheme {
                AnimatedProgressRingWithGradient(
                    progress = testProgress,
                    progressBrush = LiftrixColors.BrandGradients.TealCoral,
                    size = 120.dp,
                    strokeWidth = 10.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 85%")
            .assertIsDisplayed()
    }

    /**
     * Test multi-layer progress ring rendering
     */
    @Test
    fun multiLayerProgressRing_rendersCorrectly() {
        val progressLayers = listOf(
            ProgressLayer(
                progress = 0.6f,
                color = LiftrixColors.Primary,
                strokeWidth = 8.dp
            ),
            ProgressLayer(
                progress = 0.9f,
                color = LiftrixColors.Accent,
                strokeWidth = 6.dp
            )
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                MultiLayerProgressRing(
                    progressLayers = progressLayers,
                    size = 140.dp,
                    layerSpacing = 8.dp
                )
            }
        }
        
        // Test that the component renders without crashing
        composeTestRule.waitForIdle()
    }

    /**
     * Test progress value clamping
     */
    @Test
    fun progressRing_clampsProgressValues() {
        // Test values outside 0.0-1.0 range
        val testCases = listOf(
            -0.5f to "Progress: 0%",
            1.5f to "Progress: 100%",
            0.0f to "Progress: 0%",
            1.0f to "Progress: 100%"
        )
        
        testCases.forEach { (inputProgress, expectedDescription) ->
            composeTestRule.setContent {
                LiftrixTheme {
                    ProgressRing(
                        progress = inputProgress,
                        size = 120.dp,
                        strokeWidth = 8.dp
                    )
                }
            }
            
            composeTestRule
                .onNodeWithContentDescription(expectedDescription)
                .assertIsDisplayed()
        }
    }

    /**
     * Test ProgressRingDefaults variants
     */
    @Test
    fun progressRingDefaults_workoutProgress_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressRingDefaults.workoutProgress(
                    progress = 0.7f,
                    size = 80.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 70%")
            .assertIsDisplayed()
    }

    @Test
    fun progressRingDefaults_streakProgress_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressRingDefaults.streakProgress(
                    progress = 0.85f,
                    size = 80.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 85%")
            .assertIsDisplayed()
    }

    @Test
    fun progressRingDefaults_volumeProgress_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressRingDefaults.volumeProgress(
                    progress = 0.45f,
                    size = 80.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 45%")
            .assertIsDisplayed()
    }

    @Test
    fun progressRingDefaults_durationProgress_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressRingDefaults.durationProgress(
                    progress = 0.6f,
                    size = 80.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 60%")
            .assertIsDisplayed()
    }

    /**
     * Test ProgressLayer data class
     */
    @Test
    fun progressLayer_createdCorrectly() {
        val layer = ProgressLayer(
            progress = 0.75f,
            color = Color.Blue,
            backgroundColor = Color.Blue.copy(alpha = 0.1f),
            strokeWidth = 8.dp,
            startAngle = -90f,
            endAngle = 270f
        )
        
        assertEquals(0.75f, layer.progress)
        assertEquals(Color.Blue, layer.color)
        assertEquals(8.dp, layer.strokeWidth)
        assertEquals(-90f, layer.startAngle)
        assertEquals(270f, layer.endAngle)
    }

    /**
     * Test custom animation specs
     */
    @Test
    fun animatedProgressRing_withCustomAnimationSpec_rendersCorrectly() {
        val customAnimationSpec = tween<Float>(
            durationMillis = 1000,
            easing = LinearEasing
        )
        
        composeTestRule.setContent {
            LiftrixTheme {
                AnimatedProgressRing(
                    progress = 0.8f,
                    animationSpec = customAnimationSpec,
                    size = 120.dp,
                    strokeWidth = 8.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 80%")
            .assertIsDisplayed()
    }

    /**
     * Test different color schemes
     */
    @Test
    fun progressRing_withDifferentColors_rendersCorrectly() {
        val testColors = listOf(
            LiftrixColors.Primary,
            LiftrixColors.Secondary,
            LiftrixColors.Accent,
            Color.Red
        )
        
        testColors.forEach { color ->
            composeTestRule.setContent {
                LiftrixTheme {
                    ProgressRing(
                        progress = 0.5f,
                        color = color,
                        size = 120.dp,
                        strokeWidth = 8.dp
                    )
                }
            }
            
            composeTestRule
                .onNodeWithContentDescription("Progress: 50%")
                .assertIsDisplayed()
        }
    }

    /**
     * Test different stroke widths
     */
    @Test
    fun progressRing_withDifferentStrokeWidths_rendersCorrectly() {
        val strokeWidths = listOf(4.dp, 8.dp, 12.dp, 16.dp)
        
        strokeWidths.forEach { strokeWidth ->
            composeTestRule.setContent {
                LiftrixTheme {
                    ProgressRing(
                        progress = 0.6f,
                        strokeWidth = strokeWidth,
                        size = 120.dp
                    )
                }
            }
            
            composeTestRule
                .onNodeWithContentDescription("Progress: 60%")
                .assertIsDisplayed()
        }
    }

    /**
     * Test different sizes
     */
    @Test
    fun progressRing_withDifferentSizes_rendersCorrectly() {
        val sizes = listOf(60.dp, 80.dp, 120.dp, 160.dp)
        
        sizes.forEach { size ->
            composeTestRule.setContent {
                LiftrixTheme {
                    ProgressRing(
                        progress = 0.4f,
                        size = size,
                        strokeWidth = 8.dp
                    )
                }
            }
            
            composeTestRule
                .onNodeWithContentDescription("Progress: 40%")
                .assertIsDisplayed()
        }
    }

    /**
     * Test custom start and end angles
     */
    @Test
    fun progressRing_withCustomAngles_rendersCorrectly() {
        composeTestRule.setContent {
            LiftrixTheme {
                ProgressRing(
                    progress = 0.75f,
                    startAngle = 0f,   // Start at 3 o'clock
                    endAngle = 180f,   // End at 9 o'clock (half circle)
                    size = 120.dp,
                    strokeWidth = 8.dp
                )
            }
        }
        
        composeTestRule
            .onNodeWithContentDescription("Progress: 75%")
            .assertIsDisplayed()
    }
} 