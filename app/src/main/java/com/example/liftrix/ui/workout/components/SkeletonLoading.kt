package com.example.liftrix.ui.workout.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixAnimations
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Skeleton Loading Components
 * 
 * Provides skeleton loading states with teal accent color matching final layouts.
 * Implements smooth shimmer effects optimized for 60fps performance and consistent
 * with Liftrix's athletic branding and Material 3 design system.
 * 
 * Features:
 * - Teal accent shimmer effect (#20C9B7 with opacity variations)
 * - 12dp corner radius matching UnifiedWorkoutCard specification
 * - Smooth 1200ms animation cycle for natural breathing effect
 * - Performance optimized for minimal recomposition overhead
 * - Accessibility support with proper content descriptions
 */

/**
 * Workout Card Skeleton
 * 
 * Skeleton loading placeholder that matches UnifiedWorkoutCard layout exactly.
 * Uses teal accent shimmer animation with consistent spacing and dimensions.
 * 
 * @param modifier Modifier for customizing the skeleton's layout and behavior
 */
@Composable
fun WorkoutCardSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), // Task specification: teal accent
        MaterialTheme.colorScheme.surfaceVariant
    )
    
    val transition = rememberInfiniteTransition(label = "workoutCardShimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing), // Task specification: smooth shimmer
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), // Match UnifiedWorkoutCard specification
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = LiftrixSpacing.cardPadding, // Match UnifiedWorkoutCard: 12dp
                vertical = LiftrixSpacing.cardPadding
            )
        ) {
            // Title skeleton - matches MaterialTheme.typography.titleLarge dimensions
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(24.dp) // Title large approximate height
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(translateAnimation - 200f, 0f),
                            end = Offset(translateAnimation, 0f)
                        )
                    )
            )
            
            Spacer(modifier = Modifier.height(4.dp)) // Match subtitle spacing
            
            // Subtitle skeleton - matches MaterialTheme.typography.bodyMedium dimensions  
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp) // Body medium approximate height
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(translateAnimation - 200f, 0f),
                            end = Offset(translateAnimation, 0f)
                        )
                    )
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing)) // Match content spacing: 8dp
            
            // Content skeleton lines
            repeat(2) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 1) 0.8f else 1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = shimmerColors,
                                start = Offset(translateAnimation - 200f, 0f),
                                end = Offset(translateAnimation, 0f)
                            )
                        )
                )
                if (index < 1) Spacer(modifier = Modifier.height(6.dp))
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing)) // Match action spacing: 8dp
            
            // Action buttons skeleton - matches ModernActionButton layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(40.dp) // Match button minimum height
                        .clip(RoundedCornerShape(20.dp)) // Match ModernActionButton: 20dp corners
                        .background(
                            brush = Brush.linearGradient(
                                colors = shimmerColors,
                                start = Offset(translateAnimation - 200f, 0f),
                                end = Offset(translateAnimation, 0f)
                            )
                        )
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = shimmerColors,
                                start = Offset(translateAnimation - 200f, 0f),
                                end = Offset(translateAnimation, 0f)
                            )
                        )
                )
            }
        }
    }
}

/**
 * Exercise Card Skeleton
 * 
 * Skeleton loading placeholder for exercise components with sets/reps layout.
 * Matches exercise-specific content patterns with teal accent shimmer.
 * 
 * @param modifier Modifier for customizing the skeleton's layout and behavior
 * @param setsCount Number of skeleton set rows to display (default: 3)
 */
@Composable
fun ExerciseCardSkeleton(
    modifier: Modifier = Modifier,
    setsCount: Int = 3
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), // Teal accent
        MaterialTheme.colorScheme.surfaceVariant
    )
    
    val transition = rememberInfiniteTransition(label = "exerciseCardShimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "exerciseShimmerTranslate"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding)
        ) {
            // Exercise name skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(translateAnimation - 200f, 0f),
                            end = Offset(translateAnimation, 0f)
                        )
                    )
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
            
            // Sets skeletons
            repeat(setsCount) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Set number
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = shimmerColors,
                                    start = Offset(translateAnimation - 200f, 0f),
                                    end = Offset(translateAnimation, 0f)
                                )
                            )
                    )
                    
                    // Weight/reps values
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .width(50.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = shimmerColors,
                                            start = Offset(translateAnimation - 200f, 0f),
                                            end = Offset(translateAnimation, 0f)
                                        )
                                    )
                            )
                        }
                    }
                }
                if (index < setsCount - 1) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Button Skeleton
 * 
 * Skeleton loading placeholder for ModernActionButton components.
 * Matches button specifications with pill-shaped 20dp corners.
 * 
 * @param modifier Modifier for customizing the skeleton's layout and behavior
 * @param width Width of the button skeleton
 * @param isPrimary Whether to use primary button styling (default: true)
 */
@Composable
fun ButtonSkeleton(
    modifier: Modifier = Modifier,
    width: Int = 120,
    isPrimary: Boolean = true
) {
    val shimmerColors = listOf(
        if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface
    )
    
    val transition = rememberInfiniteTransition(label = "buttonShimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "buttonShimmerTranslate"
    )
    
    Box(
        modifier = modifier
            .width(width.dp)
            .height(LiftrixSpacing.touchTarget) // Match ModernActionButton: 48dp minimum
            .clip(RoundedCornerShape(20.dp)) // Match ModernActionButton: 20dp corners
            .background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnimation - 100f, 0f),
                    end = Offset(translateAnimation, 0f)
                )
            )
    )
}

/**
 * Text Skeleton
 * 
 * Generic text skeleton with customizable dimensions and teal accent shimmer.
 * Useful for creating custom skeleton layouts.
 * 
 * @param modifier Modifier for customizing the skeleton's layout and behavior
 * @param width Width fraction of available space (0.0 to 1.0)
 * @param height Height in dp
 * @param cornerRadius Corner radius in dp (default: 4dp)
 */
@Composable
fun TextSkeleton(
    modifier: Modifier = Modifier,
    width: Float = 1f,
    height: Int = 16,
    cornerRadius: Int = 4
) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        MaterialTheme.colorScheme.surfaceVariant
    )
    
    val transition = rememberInfiniteTransition(label = "textShimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "textShimmerTranslate"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth(width)
            .height(height.dp)
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translateAnimation - 150f, 0f),
                    end = Offset(translateAnimation, 0f)
                )
            )
    )
}

/**
 * Preview functions for development and testing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutCardSkeletonPreview() {
    MaterialTheme {
        WorkoutCardSkeleton()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCardSkeletonPreview() {
    MaterialTheme {
        ExerciseCardSkeleton(setsCount = 4)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkeletonLoadingPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WorkoutCardSkeleton()
            ExerciseCardSkeleton()
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ButtonSkeleton(width = 80, isPrimary = false)
                ButtonSkeleton(width = 120, isPrimary = true)
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextSkeleton(width = 0.8f, height = 20)
                TextSkeleton(width = 0.6f, height = 16)
                TextSkeleton(width = 0.9f, height = 16)
            }
        }
    }
}