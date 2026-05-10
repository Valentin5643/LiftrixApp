package com.example.liftrix.ui.workout.active.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.RestTimer
import com.example.liftrix.service.WorkoutTimerService
import com.example.liftrix.ui.components.animations.CompletionCheckmark
import com.example.liftrix.ui.components.animations.CompletionFeedback
import com.example.liftrix.ui.components.animations.CompletionFeedbackType
import com.example.liftrix.ui.theme.LiftrixTheme

const val REST_TIMER_DISPLAY_TAG = "rest_timer_display"
const val REST_TIMER_COUNTDOWN_TAG = "rest_timer_countdown"
const val REST_TIMER_PAUSE_RESUME_TAG = "rest_timer_pause_resume"
const val REST_TIMER_SKIP_TAG = "rest_timer_skip"
const val REST_TIMER_ADD_TIME_TAG = "rest_timer_add_time"
const val REST_TIMER_SUBTRACT_TIME_TAG = "rest_timer_subtract_time"

/**
 * Rest timer display component that shows countdown timer during rest periods.
 * 
 * Features:
 * - Prominent countdown display with circular progress indicator
 * - Animated transitions for smooth visual feedback
 * - Different visual states for active and paused rest
 * - Material3 design with high contrast for visibility
 * - Accessibility support with content descriptions
 * - Only visible when rest timer is active
 * 
 * @param timerState Current timer service state
 * @param formattedTime Pre-formatted countdown time string (MM:SS)
 * @param modifier Modifier for styling
 */
@Composable
fun RestTimerDisplay(
    timerState: WorkoutTimerService.TimerServiceState,
    formattedTime: String,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onSkip: () -> Unit = {},
    onAddTime: () -> Unit = {},
    onSubtractTime: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isRestActive = timerState.timerState is WorkoutTimerService.TimerState.RestActive ||
            timerState.timerState is WorkoutTimerService.TimerState.RestPaused
    
    AnimatedVisibility(
        visible = isRestActive,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier.testTag(REST_TIMER_DISPLAY_TAG)
    ) {
        when (val state = timerState.timerState) {
            is WorkoutTimerService.TimerState.RestActive -> {
                RestTimerCard(
                    restTimer = state.restTimer,
                    remainingSeconds = state.remainingSeconds,
                    formattedTime = formattedTime,
                    isPaused = false,
                    onPause = onPause,
                    onResume = onResume,
                    onSkip = onSkip,
                    onAddTime = onAddTime,
                    onSubtractTime = onSubtractTime
                )
            }
            is WorkoutTimerService.TimerState.RestPaused -> {
                RestTimerCard(
                    restTimer = state.restTimer,
                    remainingSeconds = state.remainingSeconds,
                    formattedTime = formattedTime,
                    isPaused = true,
                    onPause = onPause,
                    onResume = onResume,
                    onSkip = onSkip,
                    onAddTime = onAddTime,
                    onSubtractTime = onSubtractTime
                )
            }
            else -> {
                // This shouldn't happen due to isRestActive check, but handle gracefully
            }
        }
    }
}

@Composable
private fun RestTimerCard(
    restTimer: RestTimer,
    remainingSeconds: Int,
    formattedTime: String,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    onSubtractTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (restTimer.durationSeconds > 0) {
            remainingSeconds.toFloat() / restTimer.durationSeconds.toFloat()
        } else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = LinearEasing
        ),
        label = "rest_progress"
    )
    
    // Track completion state for animations
    var previousRemainingSeconds by remember { mutableStateOf(remainingSeconds) }
    val isRestCompleted = remainingSeconds == 0 && restTimer.durationSeconds > 0
    
    // Trigger completion animations when timer finishes
    LaunchedEffect(remainingSeconds) {
        if (previousRemainingSeconds > 0 && remainingSeconds == 0) {
            // Rest timer completed
        }
        previousRemainingSeconds = remainingSeconds
    }
    
    val containerColor = if (isPaused) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    
    val contentColor = if (isPaused) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    CompletionFeedback(
        completed = isRestCompleted,
        feedbackType = CompletionFeedbackType.SET_COMPLETE,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rest timer header with completion checkmark
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isRestCompleted) {
                        CompletionCheckmark(
                            completed = true,
                            size = 18.dp,
                            color = contentColor
                        )
                    } else {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.Pause else Icons.Filled.Timer,
                            contentDescription = when {
                                isRestCompleted -> "Rest complete"
                                isPaused -> "Rest paused"
                                else -> "Rest timer"
                            },
                            modifier = Modifier.size(18.dp),
                            tint = contentColor.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isRestCompleted -> "Rest Complete!"
                            isPaused -> "Rest Paused"
                            else -> "Rest Timer"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Circular progress with countdown
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(88.dp)
                ) {
                    // Background circle
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(88.dp),
                        color = contentColor.copy(alpha = 0.2f),
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    
                    // Progress indicator
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(88.dp),
                        color = contentColor,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    
                    // Countdown time with completion checkmark overlay
                    AnimatedContent(
                        targetState = if (isRestCompleted) "✓" else formattedTime,
                        transitionSpec = {
                            (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                        },
                        label = "rest_timer_animation",
                        modifier = Modifier.semantics {
                            contentDescription = if (isRestCompleted) "Rest completed" else "Rest time remaining: $formattedTime"
                        }
                    ) { displayText ->
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = if (isRestCompleted) FontFamily.Default else FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center,
                            color = contentColor,
                            modifier = Modifier.testTag(REST_TIMER_COUNTDOWN_TAG)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Rest type indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestaurantMenu,
                        contentDescription = when {
                            restTimer.isStrengthTimer() -> "Strength rest"
                            restTimer.isCardioTimer() -> "Cardio rest"
                            else -> "Rest break"
                        },
                        modifier = Modifier.size(12.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            restTimer.isStrengthTimer() -> "Strength Rest"
                            restTimer.isCardioTimer() -> "Cardio Rest"
                            else -> "Rest Break"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RestTimerControlButton(
                        onClick = onSubtractTime,
                        contentDescription = "Subtract 15 seconds",
                        testTag = REST_TIMER_SUBTRACT_TIME_TAG,
                        contentColor = contentColor
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = null
                        )
                    }

                    RestTimerControlButton(
                        onClick = if (isPaused) onResume else onPause,
                        contentDescription = if (isPaused) "Resume rest timer" else "Pause rest timer",
                        testTag = REST_TIMER_PAUSE_RESUME_TAG,
                        contentColor = contentColor
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null
                        )
                    }

                    RestTimerControlButton(
                        onClick = onSkip,
                        contentDescription = "Skip rest timer",
                        testTag = REST_TIMER_SKIP_TAG,
                        contentColor = contentColor
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = null
                        )
                    }

                    RestTimerControlButton(
                        onClick = onAddTime,
                        contentDescription = "Add 15 seconds",
                        testTag = REST_TIMER_ADD_TIME_TAG,
                        contentColor = contentColor
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestTimerControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides contentColor
            ) {
                icon()
            }
        }
    }
}

/**
 * Utility function to format rest time in seconds to MM:SS format
 */
fun formatRestTime(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

/**
 * Utility function to extract rest time from timer state
 */
fun getRestTimeFromState(timerState: WorkoutTimerService.TimerServiceState): String {
    return when (val state = timerState.timerState) {
        is WorkoutTimerService.TimerState.RestActive -> formatRestTime(state.remainingSeconds)
        is WorkoutTimerService.TimerState.RestPaused -> formatRestTime(state.remainingSeconds)
        else -> ""
    }
}

/**
 * Utility function to check if rest timer should be displayed
 */
fun shouldShowRestTimer(timerState: WorkoutTimerService.TimerServiceState): Boolean {
    return timerState.timerState is WorkoutTimerService.TimerState.RestActive ||
            timerState.timerState is WorkoutTimerService.TimerState.RestPaused
}

@Preview(showBackground = true)
@Composable
private fun RestTimerDisplayPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active rest timer
            RestTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.RestActive(
                        restTimer = RestTimer(
                            durationSeconds = 90
                        ),
                        remainingSeconds = 45
                    ),
                    restRemainingSeconds = 45
                ),
                formattedTime = "0:45"
            )
            
            // Paused rest timer
            RestTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.RestPaused(
                        restTimer = RestTimer(
                            durationSeconds = 120
                        ),
                        remainingSeconds = 75
                    ),
                    restRemainingSeconds = 75
                ),
                formattedTime = "1:15"
            )
            
            // Hidden when no rest timer
            RestTimerDisplay(
                timerState = WorkoutTimerService.TimerServiceState(
                    timerState = WorkoutTimerService.TimerState.Stopped
                ),
                formattedTime = ""
            )
        }
    }
} 
