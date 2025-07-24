package com.example.liftrix.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.pressInteraction
import com.example.liftrix.ui.common.AccessibilityUtils
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget
import com.example.liftrix.ui.common.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.common.rememberAccessibilityState
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.CardElevationGradient

/**
 * Base card component for Liftrix with consistent 8pt grid spacing and 2xl border radius
 * Provides standardized elevation, shape, and interaction patterns with full accessibility support
 */
@Composable
fun LiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    shape: Shape = RoundedCornerShape(24.dp), // 2xl border radius
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    contentPadding: PaddingValues = PaddingValues(16.dp), // 8pt grid base * 2
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val accessibilityState = rememberAccessibilityState()
    
    // Validate contrast ratio for accessibility compliance
    val containerColor = colors.containerColor
    val contentColor = colors.contentColor
    val contrastRatio = AccessibilityUtils.checkContrastRatio(contentColor, containerColor)
    val cardModifier = if (onClick != null) {
        modifier
            .ensureMinimumTouchTarget()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pressInteraction()
            .accessibilitySemantics(
                description = contentDescription ?: "Card",
                role = Role.Button,
                stateDescription = if (enabled) "Enabled" else "Disabled",
                isEnabled = enabled
            )
    } else {
        modifier.let { baseModifier ->
            if (contentDescription != null) {
                baseModifier.accessibilitySemantics(
                    description = contentDescription,
                    isEnabled = enabled
                )
            } else {
                baseModifier
            }
        }
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = colors,
        elevation = elevation
    ) {
        Box(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}

/**
 * Card with gradient background for visual depth and accessibility support
 */
@Composable
fun GradientLiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gradient: Brush = CardElevationGradient,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .ensureMinimumTouchTarget()
            .clip(shape)
            .background(gradient, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pressInteraction()
            .accessibilitySemantics(
                description = contentDescription ?: "Gradient card",
                role = Role.Button,
                stateDescription = if (enabled) "Enabled" else "Disabled",
                isEnabled = enabled
            )
    } else {
        modifier
            .clip(shape)
            .background(gradient, shape)
            .let { baseModifier ->
                if (contentDescription != null) {
                    baseModifier.accessibilitySemantics(
                        description = contentDescription,
                        isEnabled = enabled
                    )
                } else {
                    baseModifier
                }
            }
    }

    Surface(
        modifier = cardModifier,
        shape = shape,
        shadowElevation = 4.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.padding(contentPadding)
        ) {
            content()
        }
    }
}

/**
 * Compact card for smaller content areas
 */
@Composable
fun CompactLiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    shape: Shape = RoundedCornerShape(16.dp), // Large border radius for compact cards
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(12.dp), // Reduced padding for compact cards
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    LiftrixCard(
        modifier = modifier,
        onClick = onClick,
        elevation = elevation,
        colors = colors,
        shape = shape,
        enabled = enabled,
        contentPadding = contentPadding,
        contentDescription = contentDescription,
        content = content
    )
}

/**
 * Elevated card for important content
 */
@Composable
fun ElevatedLiftrixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(24.dp), // Increased padding for elevated cards
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    LiftrixCard(
        modifier = modifier,
        onClick = onClick,
        elevation = elevation,
        colors = colors,
        shape = shape,
        enabled = enabled,
        contentPadding = contentPadding,
        contentDescription = contentDescription,
        content = content
    )
}

/**
 * Grid spacing values based on 8pt system
 */
object CardSpacing {
    val XXS = 4.dp   // 8pt grid / 2
    val XS = 8.dp    // 8pt grid base
    val S = 12.dp    // 8pt grid * 1.5
    val M = 16.dp    // 8pt grid * 2
    val L = 24.dp    // 8pt grid * 3
    val XL = 32.dp   // 8pt grid * 4
    val XXL = 40.dp  // 8pt grid * 5
}

/**
 * Card border radius values
 */
object CardRadius {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 20.dp
    val XXLarge = 24.dp  // 2xl border radius - standard for LiftrixCard
    val Rounded = 32.dp
}

/**
 * Card elevation presets
 */
object CardElevations {
    @Composable
    fun none() = CardDefaults.cardElevation(defaultElevation = 0.dp)
    
    @Composable 
    fun subtle() = CardDefaults.cardElevation(defaultElevation = 1.dp)
    
    @Composable
    fun standard() = CardDefaults.cardElevation(defaultElevation = 2.dp)
    
    @Composable
    fun medium() = CardDefaults.cardElevation(defaultElevation = 4.dp)
    
    @Composable
    fun high() = CardDefaults.cardElevation(defaultElevation = 8.dp)
    
    @Composable
    fun veryHigh() = CardDefaults.cardElevation(defaultElevation = 12.dp)
} 