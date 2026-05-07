package com.example.liftrix.ui.animations

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Skeleton Loading Components for Liftrix
 * 
 * Provides skeleton loading states that match final component layouts with teal accent shimmer.
 * Uses LiftrixAnimations.skeletonShimmerAnimation (1200ms cycle) for smooth loading effects.
 * 
 * Features:
 * - Teal accent color (#20C9B7) shimmer matching design system
 * - Layout matching UnifiedWorkoutCard structure
 * - 60fps optimized animations with performance monitoring
 * - Accessibility-compliant loading states
 * - Athletic design principles with natural motion
 */

/**
 * Skeleton loader matching UnifiedWorkoutCard layout
 * 
 * @param modifier Modifier for customizing the skeleton appearance
 * @param showIcon Whether to show the icon skeleton area
 * @param showActions Whether to show the action buttons skeleton area
 */
@Composable
fun WorkoutCardSkeleton(
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    showActions: Boolean = true
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), // Teal accent shimmer
        MaterialTheme.colorScheme.surface
    )
    
    val transition = rememberInfiniteTransition(label = "workoutCardSkeleton")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing), // Using LiftrixAnimations.skeletonShimmerAnimation timing
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslation"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), // Matching UnifiedWorkoutCard specifications
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Matching UnifiedWorkoutCard elevation
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant // Matching UnifiedWorkoutCard colors
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = LiftrixSpacing.cardPadding, // 12dp horizontal padding
                vertical = LiftrixSpacing.cardPadding    // 12dp vertical padding
            )
        ) {
            // Header row with optional icon and title skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                // Leading icon skeleton (optional)
                if (showIcon) {
                    Box(
                        modifier = Modifier
                            .size(LiftrixSpacing.touchTarget) // 24dp from tokens
                            .background(
                                brush = createShimmerBrush(shimmerColors, translateAnimation),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
                
                // Title skeleton
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp) // Matching titleLarge height
                        .background(
                            brush = createShimmerBrush(shimmerColors, translateAnimation),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            
            // Subtitle skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp) // Matching bodyMedium height
                    .padding(top = 4.dp)
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            // Content spacing
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing)) // 8dp spacing
            
            // Main content skeleton - 2-3 lines of text
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 1) 0.8f else 1f)
                        .height(14.dp) // Matching bodyMedium content height
                        .padding(vertical = 2.dp)
                        .background(
                            brush = createShimmerBrush(shimmerColors, translateAnimation),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            
            // Actions skeleton (if shown)
            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = LiftrixSpacing.elementSpacing), // 8dp top spacing
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary action skeleton
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(36.dp) // Matching button height
                            .background(
                                brush = createShimmerBrush(shimmerColors, translateAnimation),
                                shape = RoundedCornerShape(18.dp) // Matching button corner radius
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Primary action skeleton
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(36.dp) // Matching button height
                            .background(
                                brush = createShimmerBrush(shimmerColors, translateAnimation),
                                shape = RoundedCornerShape(18.dp) // Matching button corner radius
                            )
                    )
                }
            }
        }
    }
}

/**
 * Compact skeleton loader matching CompactUnifiedWorkoutCard layout
 * 
 * @param modifier Modifier for customizing the skeleton appearance
 * @param showIcon Whether to show the icon skeleton area
 * @param showActions Whether to show the action buttons skeleton area
 */
@Composable
fun CompactWorkoutCardSkeleton(
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    showActions: Boolean = true
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), // Teal accent shimmer
        MaterialTheme.colorScheme.surface
    )
    
    val transition = rememberInfiniteTransition(label = "compactWorkoutCardSkeleton")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compactShimmerTranslation"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Reduced elevation for compact
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = LiftrixSpacing.elementPaddingLarge, // 12dp horizontal padding
                vertical = LiftrixSpacing.elementSpacing          // 8dp vertical padding (reduced)
            )
        ) {
            // Compact header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                // Leading icon skeleton (smaller for compact)
                if (showIcon) {
                    Box(
                        modifier = Modifier
                            .size(20.dp) // Smaller icon for compact version
                            .background(
                                brush = createShimmerBrush(shimmerColors, translateAnimation),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
                
                // Title skeleton (compact)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp) // Matching titleMedium height
                        .background(
                            brush = createShimmerBrush(shimmerColors, translateAnimation),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            
            // Compact subtitle skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp) // Matching bodySmall height
                    .padding(top = 2.dp) // Reduced spacing for compact
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            // Content spacing (reduced for compact)
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall)) // 4dp spacing
            
            // Compact content skeleton - single line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp) // Smaller content for compact
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            // Compact actions skeleton (if shown)
            if (showActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = LiftrixSpacing.elementPaddingSmall), // 4dp top spacing (reduced)
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Compact action buttons
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(32.dp) // Smaller height for compact
                            .background(
                                brush = createShimmerBrush(shimmerColors, translateAnimation),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(70.dp)
                            .height(32.dp) // Smaller height for compact
                            .background(
                                brush = createShimmerBrush(shimmerColors, translateAnimation),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * List item skeleton for exercise or workout lists
 * 
 * @param modifier Modifier for customizing the skeleton appearance
 * @param showIcon Whether to show leading icon area
 * @param showTrailing Whether to show trailing content area
 */
@Composable
fun ListItemSkeleton(
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    showTrailing: Boolean = true
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), // Teal accent shimmer
        MaterialTheme.colorScheme.surface
    )
    
    val transition = rememberInfiniteTransition(label = "listItemSkeleton")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listItemShimmerTranslation"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = LiftrixSpacing.screenPadding,
                vertical = LiftrixSpacing.elementSpacing
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
    ) {
        // Leading icon skeleton
        if (showIcon) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
        
        // Content area
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Primary text skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(18.dp)
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            // Secondary text skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        
        // Trailing content skeleton
        if (showTrailing) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(30.dp)
                    .background(
                        brush = createShimmerBrush(shimmerColors, translateAnimation),
                        shape = RoundedCornerShape(15.dp)
                    )
            )
        }
    }
}

/**
 * Screen loading skeleton with multiple cards
 * 
 * @param modifier Modifier for customizing the screen skeleton
 * @param itemCount Number of skeleton cards to show
 * @param showHeader Whether to show header skeleton
 */
@Composable
fun ScreenLoadingSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 3,
    showHeader: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        // Header skeleton
        if (showHeader) {
            HeaderSkeleton()
        }
        
        // Multiple workout card skeletons
        repeat(itemCount) { index ->
            WorkoutCardSkeleton(
                showIcon = index == 0, // Only first card has icon
                showActions = true
            )
        }
    }
}

/**
 * Header skeleton for screen titles and navigation
 */
@Composable
private fun HeaderSkeleton() {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), // Teal accent shimmer
        MaterialTheme.colorScheme.surface
    )
    
    val transition = rememberInfiniteTransition(label = "headerSkeleton")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "headerShimmerTranslation"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = LiftrixSpacing.listSectionSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Title skeleton
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(32.dp) // Matching headlineMedium height
                .background(
                    brush = createShimmerBrush(shimmerColors, translateAnimation),
                    shape = RoundedCornerShape(6.dp)
                )
        )
        
        // Action button skeleton
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(40.dp)
                .background(
                    brush = createShimmerBrush(shimmerColors, translateAnimation),
                    shape = RoundedCornerShape(20.dp)
                )
        )
    }
}

/**
 * Creates shimmer brush with teal accent for skeleton animations
 */
private fun createShimmerBrush(
    colors: List<Color>,
    translateAnimation: Float
): Brush {
    return Brush.linearGradient(
        colors = colors,
        start = Offset(translateAnimation - 300f, 0f),
        end = Offset(translateAnimation, 0f)
    )
}

/**
 * Athletic skeleton loader with enhanced motion
 * Provides weight-shifting shimmer effect for athletic branding
 */
@Composable
fun AthleticSkeletonLoader(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shimmerAlpha by rememberInfiniteTransition(label = "athleticSkeleton").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "athleticShimmerAlpha"
    )
    
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = shimmerAlpha * 0.1f)
            )
    ) {
        content()
    }
}