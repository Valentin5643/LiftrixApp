package com.example.liftrix.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.liftrix.domain.model.ProgressComparison
import com.example.liftrix.domain.model.ProgressPhoto
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Progress comparison view with before/after layouts and swipe interactions.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Composable
fun ProgressComparisonView(
    comparison: ProgressComparison,
    modifier: Modifier = Modifier,
    onImageTap: (ProgressPhoto) -> Unit = {},
    onComparisonModeToggle: () -> Unit = {}
) {
    var comparisonMode by remember { mutableStateOf(ComparisonMode.SIDE_BY_SIDE) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.medium)
        ) {
            // Header with comparison controls
            ComparisonHeader(
                comparison = comparison,
                comparisonMode = comparisonMode,
                onModeChanged = { comparisonMode = it }
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            // Progress images based on comparison mode
            when (comparisonMode) {
                ComparisonMode.SIDE_BY_SIDE -> {
                    SideBySideComparison(
                        beforePhoto = comparison.beforePhoto,
                        afterPhoto = comparison.afterPhoto,
                        onImageTap = onImageTap
                    )
                }
                ComparisonMode.OVERLAY_SWIPE -> {
                    OverlaySwipeComparison(
                        beforePhoto = comparison.beforePhoto,
                        afterPhoto = comparison.afterPhoto,
                        onImageTap = onImageTap
                    )
                }
                ComparisonMode.TOGGLE_VIEW -> {
                    ToggleComparison(
                        beforePhoto = comparison.beforePhoto,
                        afterPhoto = comparison.afterPhoto,
                        onImageTap = onImageTap
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            // Progress metrics
            ProgressMetrics(comparison = comparison)
            
            // Notes section
            comparison.notes?.let { notes ->
                Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ComparisonHeader(
    comparison: ProgressComparison,
    comparisonMode: ComparisonMode,
    onModeChanged: (ComparisonMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = comparison.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${comparison.timeDifferenceWeeks} weeks progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        ComparisonModeToggle(
            currentMode = comparisonMode,
            onModeChanged = onModeChanged
        )
    }
}

@Composable
private fun ComparisonModeToggle(
    currentMode: ComparisonMode,
    onModeChanged: (ComparisonMode) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
        ComparisonMode.values().forEach { mode ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (currentMode == mode) {
                            LiftrixColorsV2.primary
                        } else {
                            LiftrixColorsV2.primary.copy(alpha = 0.1f)
                        },
                        shape = CircleShape
                    )
                    .clickable { onModeChanged(mode) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = mode.displayName,
                    tint = if (currentMode == mode) {
                        Color.White
                    } else {
                        LiftrixColorsV2.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SideBySideComparison(
    beforePhoto: ProgressPhoto,
    afterPhoto: ProgressPhoto,
    onImageTap: (ProgressPhoto) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
        // Before photo
        ProgressPhotoCard(
            photo = beforePhoto,
            label = "Before",
            modifier = Modifier.weight(1f),
            onTap = { onImageTap(beforePhoto) }
        )
        
        // After photo
        ProgressPhotoCard(
            photo = afterPhoto,
            label = "After",
            modifier = Modifier.weight(1f),
            onTap = { onImageTap(afterPhoto) }
        )
    }
}

@Composable
private fun OverlaySwipeComparison(
    beforePhoto: ProgressPhoto,
    afterPhoto: ProgressPhoto,
    onImageTap: (ProgressPhoto) -> Unit
) {
    var sliderPosition by remember { mutableStateOf(0.5f) }
    var containerWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val newPosition = sliderPosition + (change.position.x / containerWidth)
                    sliderPosition = newPosition.coerceIn(0f, 1f)
                }
            }
    ) {
        // Before photo (background)
        AsyncImage(
            model = getPhotoUrl(beforePhoto),
            contentDescription = "Before photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // After photo (foreground with clip)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    RectClipShape(
                        clipFraction = sliderPosition
                    )
                )
        ) {
            AsyncImage(
                model = getPhotoUrl(afterPhoto),
                contentDescription = "After photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Slider handle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = with(density) { (containerWidth * sliderPosition).roundToInt() - 20.dp.roundToPx() },
                        y = 0
                    )
                }
                .size(40.dp)
                .background(
                    color = Color.White,
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = LiftrixColorsV2.primary,
                    shape = CircleShape
                )
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Drag to compare",
                tint = LiftrixColorsV2.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Labels
        Text(
            text = "Before",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(LiftrixSpacing.small)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
        Text(
            text = "After",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(LiftrixSpacing.small)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ToggleComparison(
    beforePhoto: ProgressPhoto,
    afterPhoto: ProgressPhoto,
    onImageTap: (ProgressPhoto) -> Unit
) {
    var showBefore by remember { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showBefore = !showBefore }
    ) {
        // Current photo
        AnimatedVisibility(
            visible = showBefore,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AsyncImage(
                model = getPhotoUrl(beforePhoto),
                contentDescription = "Before photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        AnimatedVisibility(
            visible = !showBefore,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AsyncImage(
                model = getPhotoUrl(afterPhoto),
                contentDescription = "After photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Toggle indicator
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(60.dp)
                .background(
                    color = Color.White.copy(alpha = alpha),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = LiftrixColorsV2.primary.copy(alpha = alpha),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (showBefore) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Tap to toggle",
                tint = LiftrixColorsV2.primary.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Current label
        Text(
            text = if (showBefore) "Before" else "After",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(LiftrixSpacing.medium)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = LiftrixSpacing.medium, vertical = LiftrixSpacing.small)
        )
    }
}

@Composable
private fun ProgressPhotoCard(
    photo: ProgressPhoto,
    label: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onTap() }
        ) {
            AsyncImage(
                model = getPhotoUrl(photo),
                contentDescription = "$label photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Date overlay
            Text(
                text = formatPhotoDate(photo.takenAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(LiftrixSpacing.small)
                    .background(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        // Weight and body fat if available
        photo.weightKg?.let { weight ->
            Text(
                text = "${weight}kg",
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColorsV2.primary
            )
        }
        
        photo.bodyFatPercent?.let { bodyFat ->
            Text(
                text = "${bodyFat}% BF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressMetrics(
    comparison: ProgressComparison
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        comparison.weightChangeKg?.let { weightChange ->
            MetricCard(
                label = "Weight Change",
                value = "${if (weightChange >= 0) "+" else ""}${weightChange}kg",
                isPositive = weightChange <= 0 // For weight loss, negative is positive
            )
        }
        
        comparison.bodyFatChange?.let { bodyFatChange ->
            MetricCard(
                label = "Body Fat Change",
                value = "${if (bodyFatChange >= 0) "+" else ""}${bodyFatChange}%",
                isPositive = bodyFatChange <= 0 // For body fat loss, negative is positive
            )
        }
        
        MetricCard(
            label = "Time Period",
            value = "${comparison.timeDifferenceWeeks} weeks",
            isPositive = null
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    isPositive: Boolean?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (isPositive) {
                true -> LiftrixColorsV2.Dark.Success.copy(alpha = 0.1f)
                false -> LiftrixColorsV2.Dark.Error.copy(alpha = 0.1f)
                null -> LiftrixColorsV2.primary.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (isPositive) {
                    true -> LiftrixColorsV2.Dark.Success
                    false -> LiftrixColorsV2.Dark.Error
                    null -> LiftrixColorsV2.primary
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Comparison modes
enum class ComparisonMode(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    SIDE_BY_SIDE("Side by Side", Icons.Default.CompareArrows),
    OVERLAY_SWIPE("Swipe Compare", Icons.Default.SwapHoriz),
    TOGGLE_VIEW("Toggle View", Icons.Default.Visibility)
}

// Custom clip shape for overlay comparison
private class RectClipShape(private val clipFraction: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        return androidx.compose.ui.graphics.Outline.Rectangle(
            androidx.compose.ui.geometry.Rect(
                offset = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(
                    width = size.width * clipFraction,
                    height = size.height
                )
            )
        )
    }
}

// Helper functions
private fun getPhotoUrl(photo: ProgressPhoto): String {
    // In production, this would return the actual photo URL
    return "https://via.placeholder.com/400x400"
}

private fun formatPhotoDate(timestamp: Long): String {
    // Format timestamp to readable date
    return java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}