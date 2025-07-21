package com.example.liftrix.ui.progress.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTokens

/**
 * Drop target indicator component for visual drop zone feedback.
 * 
 * Provides clear visual indicators for valid drop zones during drag operations
 * with Material 3 design language, accessibility support, and smooth animations.
 * Includes multiple visual styles for different drop scenarios and states.
 * 
 * Features:
 * - Material 3 compliant visual design with semantic colors
 * - Smooth animated transitions for active/inactive states
 * - Accessibility support with content descriptions
 * - Multiple indicator styles for different use cases
 * - Performance-optimized animations for 60fps rendering
 * - Customizable visual feedback intensity
 * 
 * @param isActive Whether the drop target is currently active
 * @param isValidTarget Whether this is a valid drop target for current drag
 * @param indicatorStyle Visual style variant for the indicator
 * @param contentDescription Accessibility description for the drop zone
 * @param modifier Modifier for styling the indicator
 */
@Composable
fun DropTargetIndicator(
    isActive: Boolean = false,
    isValidTarget: Boolean = true,
    indicatorStyle: DropIndicatorStyle = DropIndicatorStyle.BORDER,
    contentDescription: String = "Drop zone for widget reordering",
    modifier: Modifier = Modifier
) {
    // Animation states for smooth visual feedback
    val borderAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "drop_indicator_border_alpha"
    )
    
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.1f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "drop_indicator_background_alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "drop_indicator_scale"
    )
    
    // Color animation based on validity
    val indicatorColor by animateColorAsState(
        targetValue = if (isValidTarget) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        animationSpec = tween(durationMillis = 200),
        label = "drop_indicator_color"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .semantics {
                this.contentDescription = if (isActive) {
                    "Active drop zone - release to reorder widget here"
                } else {
                    contentDescription
                }
            }
    ) {
        when (indicatorStyle) {
            DropIndicatorStyle.BORDER -> {
                BorderDropIndicator(
                    borderAlpha = borderAlpha,
                    backgroundAlpha = backgroundAlpha,
                    indicatorColor = indicatorColor,
                    isActive = isActive
                )
            }
            DropIndicatorStyle.FILL -> {
                FillDropIndicator(
                    backgroundAlpha = backgroundAlpha,
                    indicatorColor = indicatorColor,
                    isActive = isActive
                )
            }
            DropIndicatorStyle.PULSE -> {
                PulseDropIndicator(
                    indicatorColor = indicatorColor,
                    isActive = isActive
                )
            }
            DropIndicatorStyle.GLOW -> {
                GlowDropIndicator(
                    indicatorColor = indicatorColor,
                    isActive = isActive
                )
            }
        }
    }
}

/**
 * Border-style drop indicator with animated border and subtle background
 */
@Composable
private fun BorderDropIndicator(
    borderAlpha: Float,
    backgroundAlpha: Float,
    indicatorColor: Color,
    isActive: Boolean
) {
    val borderWidth = if (isActive) 3.dp else 2.dp
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = indicatorColor.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
            .border(
                width = borderWidth,
                color = indicatorColor.copy(alpha = borderAlpha),
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
    )
}

/**
 * Fill-style drop indicator with gradient background
 */
@Composable
private fun FillDropIndicator(
    backgroundAlpha: Float,
    indicatorColor: Color,
    isActive: Boolean
) {
    if (isActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            indicatorColor.copy(alpha = backgroundAlpha),
                            indicatorColor.copy(alpha = backgroundAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
                )
        )
    }
}

/**
 * Pulse-style drop indicator with animated scaling effect
 */
@Composable
private fun PulseDropIndicator(
    indicatorColor: Color,
    isActive: Boolean
) {
    if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
                .background(
                    color = indicatorColor.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
                )
                .border(
                    width = 2.dp,
                    color = indicatorColor.copy(alpha = pulseAlpha * 2),
                    shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
                )
        )
    }
}

/**
 * Glow-style drop indicator with radial gradient effect
 */
@Composable
private fun GlowDropIndicator(
    indicatorColor: Color,
    isActive: Boolean
) {
    if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "glow_transition")
        val glowIntensity by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_intensity"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            indicatorColor.copy(alpha = glowIntensity),
                            indicatorColor.copy(alpha = glowIntensity * 0.5f),
                            Color.Transparent
                        ),
                        radius = 200f
                    ),
                    shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
                )
        )
    }
}

/**
 * Enhanced drop indicator with icon and text for complex drop scenarios
 */
@Composable
fun EnhancedDropTargetIndicator(
    isActive: Boolean = false,
    isValidTarget: Boolean = true,
    icon: ImageVector = Icons.Default.AddCircle,
    indicatorStyle: DropIndicatorStyle = DropIndicatorStyle.BORDER,
    contentDescription: String = "Enhanced drop zone",
    modifier: Modifier = Modifier
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.8f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "enhanced_icon_alpha"
    )
    
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "enhanced_icon_scale"
    )
    
    Box(modifier = modifier) {
        // Base drop indicator
        DropTargetIndicator(
            isActive = isActive,
            isValidTarget = isValidTarget,
            indicatorStyle = indicatorStyle,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
        
        // Central icon for enhanced feedback
        if (isActive) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isValidTarget) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .scale(iconScale)
                    .alpha(iconAlpha)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Accessibility-focused drop indicator with high contrast design
 */
@Composable
fun AccessibleDropTargetIndicator(
    isActive: Boolean = false,
    isValidTarget: Boolean = true,
    contentDescription: String = "Accessible drop zone",
    modifier: Modifier = Modifier
) {
    val highContrastAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 150),
        label = "accessible_alpha"
    )
    
    val borderWidth by animateFloatAsState(
        targetValue = if (isActive) 4f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "accessible_border_width"
    )
    
    Box(
        modifier = modifier
            .background(
                color = if (isValidTarget) {
                    MaterialTheme.colorScheme.primary.copy(alpha = highContrastAlpha * 0.2f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = highContrastAlpha * 0.2f)
                },
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
            .border(
                width = borderWidth.dp,
                color = if (isValidTarget) {
                    MaterialTheme.colorScheme.primary.copy(alpha = highContrastAlpha)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = highContrastAlpha)
                },
                shape = RoundedCornerShape(LiftrixTokens.CornerRadius.Medium)
            )
            .semantics {
                this.contentDescription = if (isActive) {
                    "Active drop zone - widget will be placed here when released"
                } else {
                    contentDescription
                }
            }
    )
}

/**
 * Enumeration of drop indicator visual styles
 */
enum class DropIndicatorStyle {
    BORDER,     // Animated border with subtle background
    FILL,       // Gradient fill background
    PULSE,      // Pulsing scale animation
    GLOW        // Radial glow effect
}

/**
 * Data class for drop indicator configuration
 */
data class DropIndicatorConfig(
    val style: DropIndicatorStyle = DropIndicatorStyle.BORDER,
    val animationDuration: Int = 200,
    val borderWidth: Float = 2f,
    val cornerRadius: Float = LiftrixTokens.CornerRadius.Medium.value,
    val accessibilityEnabled: Boolean = false
)