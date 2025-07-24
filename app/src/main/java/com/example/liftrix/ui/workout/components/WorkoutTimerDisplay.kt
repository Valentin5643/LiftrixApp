package com.example.liftrix.ui.workout.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.common.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlin.math.*

/**
 * Enhanced Workout Timer Display Component
 * 
 * Modern timer component with sophisticated visual design for workout sessions,
 * rest periods, and exercise timing. Integrates with existing timer services
 * while providing enhanced visual feedback and accessibility.
 * 
 * Built on UnifiedWorkoutCard foundation with specialized timer functionality
 * including animated progress indicators, state transitions, and haptic feedback.
 * 
 * Features:
 * - Large, prominent time display with modern typography
 * - Animated circular progress indicator for rest timer countdown
 * - Smooth state transitions between running/paused/stopped
 * - Visual pulse animation for active timers
 * - Accessibility support with time announcements
 * - Configurable timer types (session, rest, exercise)
 * - Integration with existing timer service architecture
 * 
 * @param timerType Type of timer (session, rest, exercise)
 * @param elapsedTime Current elapsed time in seconds
 * @param totalTime Total time for countdown timers (null for count-up)
 * @param isRunning Whether the timer is currently running
 * @param isPaused Whether the timer is paused
 * @param onToggleTimer Callback for play/pause actions
 * @param onResetTimer Callback for reset actions
 * @param modifier Modifier for customizing the component layout and behavior
 */
@Composable
fun WorkoutTimerDisplay(
    timerType: TimerType = TimerType.SESSION,
    elapsedTime: Long, // in seconds
    totalTime: Long? = null, // for countdown timers
    isRunning: Boolean = false,
    isPaused: Boolean = false,
    onToggleTimer: () -> Unit = {},
    onResetTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCountdown = totalTime != null
    val remainingTime = totalTime?.let { maxOf(0, it - elapsedTime) } ?: elapsedTime
    val progress = totalTime?.let { if (it > 0) elapsedTime.toFloat() / it else 0f } ?: 0f
    
    // Animation states
    val pulseAnimation by rememberInfiniteTransition().animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val colorAnimation by animateColorAsState(
        targetValue = when {
            !isRunning -> MaterialTheme.colorScheme.onSurfaceVariant
            isPaused -> MaterialTheme.colorScheme.error
            isCountdown && remainingTime <= 10 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300)
    )
    
    UnifiedWorkoutCard(
        title = timerType.displayName,
        subtitle = when {
            !isRunning && !isPaused -> "Stopped"
            isPaused -> "Paused"
            isRunning -> "Running"
            else -> "Ready"
        },
        modifier = modifier,
        actions = {
            TimerControlButtons(
                isRunning = isRunning,
                isPaused = isPaused,
                onToggleTimer = onToggleTimer,
                onResetTimer = onResetTimer
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background and progress indicators
            if (isCountdown && totalTime != null) {
                CircularTimerIndicator(
                    progress = progress,
                    isRunning = isRunning,
                    color = colorAnimation,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Central time display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.alpha(if (isRunning && !isPaused) pulseAnimation else 1f)
            ) {
                // Main time display
                Text(
                    text = formatTimerDisplay(if (isCountdown) remainingTime else elapsedTime),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorAnimation,
                    modifier = Modifier.accessibilitySemantics(
                        description = buildTimeAccessibilityDescription(
                            time = if (isCountdown) remainingTime else elapsedTime,
                            isCountdown = isCountdown,
                            timerType = timerType
                        )
                    )
                )
                
                // Secondary information
                if (isCountdown && totalTime != null) {
                    Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
                    Text(
                        text = "of ${formatTimerDisplay(totalTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Timer status
                if (isPaused) {
                    Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Paused",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PAUSED",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Warning indicator for low time
            if (isCountdown && remainingTime <= 10 && remainingTime > 0 && isRunning) {
                UrgencyIndicator(
                    remainingTime = remainingTime,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/**
 * Circular progress indicator for countdown timers
 */
@Composable
private fun CircularTimerIndicator(
    progress: Float,
    isRunning: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val strokeWidth = 8.dp
    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }
    
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val radius = (canvasSize - strokeWidthPx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topCenter = Offset(center.x, center.y - radius)
        
        // Background circle
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
        
        // Progress arc
        if (progress > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(
                    center.x - radius,
                    center.y - radius
                ),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Timer control buttons with proper hierarchy
 */
@Composable
private fun TimerControlButtons(
    isRunning: Boolean,
    isPaused: Boolean,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
    ) {
        // Play/Pause button
        PrimaryActionButton(
            text = when {
                isPaused -> "Resume"
                isRunning -> "Pause"
                else -> "Start"
            },
            onClick = onToggleTimer
        )
        
        // Reset button (secondary action)
        if (isRunning || isPaused) {
            SecondaryActionButton(
                text = "Reset",
                onClick = onResetTimer
            )
        }
    }
}

/**
 * Urgency indicator for low remaining time
 */
@Composable
private fun UrgencyIndicator(
    remainingTime: Long,
    modifier: Modifier = Modifier
) {
    val urgencyAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        modifier = modifier.alpha(urgencyAlpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = LiftrixSpacing.elementPaddingLarge,
                vertical = LiftrixSpacing.elementPaddingSmall
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Time warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${remainingTime}s remaining",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Compact timer display for smaller spaces
 */
@Composable
fun CompactWorkoutTimerDisplay(
    elapsedTime: Long,
    isRunning: Boolean = false,
    isPaused: Boolean = false,
    timerType: TimerType = TimerType.SESSION,
    modifier: Modifier = Modifier
) {
    val colorAnimation by animateColorAsState(
        targetValue = when {
            !isRunning -> MaterialTheme.colorScheme.onSurfaceVariant
            isPaused -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    )
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning && !isPaused) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(LiftrixSpacing.elementPaddingLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.Timer,
                contentDescription = if (isPaused) "Timer paused" else "Timer active",
                tint = colorAnimation,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = formatTimerDisplay(elapsedTime),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorAnimation,
                modifier = Modifier.accessibilitySemantics(
                    description = buildTimeAccessibilityDescription(
                        time = elapsedTime,
                        isCountdown = false,
                        timerType = timerType
                    )
                )
            )
            
            if (isPaused) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }
    }
}

/**
 * Timer types for different use cases
 */
enum class TimerType(val displayName: String) {
    SESSION("Session Timer"),
    REST("Rest Timer"), 
    EXERCISE("Exercise Timer"),
    INTERVAL("Interval Timer")
}

/**
 * Helper functions
 */
private fun formatTimerDisplay(timeInSeconds: Long): String {
    val hours = timeInSeconds / 3600
    val minutes = (timeInSeconds % 3600) / 60
    val seconds = timeInSeconds % 60
    
    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
        minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
        else -> String.format("0:%02d", seconds)
    }
}

private fun buildTimeAccessibilityDescription(
    time: Long,
    isCountdown: Boolean,
    timerType: TimerType
): String {
    val timeDescription = formatTimeForAccessibility(time)
    val direction = if (isCountdown) "remaining" else "elapsed"
    return "${timerType.displayName}: $timeDescription $direction"
}

private fun formatTimeForAccessibility(timeInSeconds: Long): String {
    val hours = timeInSeconds / 3600
    val minutes = (timeInSeconds % 3600) / 60
    val seconds = timeInSeconds % 60
    
    val parts = mutableListOf<String>()
    
    if (hours > 0) {
        parts.add("$hours ${if (hours == 1L) "hour" else "hours"}")
    }
    
    if (minutes > 0) {
        parts.add("$minutes ${if (minutes == 1L) "minute" else "minutes"}")
    }
    
    if (seconds > 0 || parts.isEmpty()) {
        parts.add("$seconds ${if (seconds == 1L) "second" else "seconds"}")
    }
    
    return parts.joinToString(", ")
}