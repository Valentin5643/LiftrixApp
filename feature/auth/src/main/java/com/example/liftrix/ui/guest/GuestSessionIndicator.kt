package com.example.liftrix.ui.guest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.GuestSession

/**
 * Compact indicator showing guest session status in the main UI
 */
@Composable
fun GuestSessionIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuestSessionViewModel = hiltViewModel()
) {
    val guestSessionState by viewModel.guestSessionState.collectAsState()
    
    val currentState = guestSessionState
    if (currentState is GuestSessionState.Active) {
        GuestStatusBanner(
            guestSession = currentState.guestSession,
            onClick = onClick,
            modifier = modifier
        )
    }
}

/**
 * Banner showing guest status information
 */
@Composable
private fun GuestStatusBanner(
    guestSession: GuestSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLowWorkouts = guestSession.getWorkoutsRemaining() <= 1
    val isLimitReached = guestSession.isLimitReached
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isLimitReached -> MaterialTheme.colorScheme.errorContainer
            isLowWorkouts -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "background_color"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            isLimitReached -> MaterialTheme.colorScheme.onErrorContainer
            isLowWorkouts -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 300),
        label = "content_color"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isLimitReached -> Icons.Default.Warning
                        isLowWorkouts -> Icons.Default.Warning
                        else -> Icons.Default.Person
                    },
                    contentDescription = if (isLimitReached || isLowWorkouts) {
                        "Guest limit warning"
                    } else {
                        "Guest mode"
                    },
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = when {
                            isLimitReached -> "Guest Limit Reached"
                            isLowWorkouts -> "Guest Mode - ${guestSession.getWorkoutsRemaining()} left"
                            else -> "Guest Mode - ${guestSession.getWorkoutsRemaining()} workouts left"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                    
                    if (!isLimitReached) {
                        Text(
                            text = "Tap to upgrade for unlimited workouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                    } else {
                        Text(
                            text = "Create account to continue",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                    }
                }
            }
            
            // Progress indicator
            WorkoutProgressIndicator(
                guestSession = guestSession,
                contentColor = contentColor
            )
        }
    }
}

/**
 * Visual progress indicator for workout count
 */
@Composable
private fun WorkoutProgressIndicator(
    guestSession: GuestSession,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(guestSession.maxWorkouts) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < guestSession.workoutCount) {
                            contentColor
                        } else {
                            contentColor.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

/**
 * Compact guest mode chip for top bars
 */
@Composable
fun GuestModeChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuestSessionViewModel = hiltViewModel()
) {
    val guestSessionState by viewModel.guestSessionState.collectAsState()
    
    val currentState = guestSessionState
    if (currentState is GuestSessionState.Active) {
        val guestSession = currentState.guestSession
        val isUrgent = guestSession.getWorkoutsRemaining() <= 1 || guestSession.isLimitReached
        
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isUrgent) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isUrgent) Icons.Default.Warning else Icons.Default.Person,
                    contentDescription = if (isUrgent) "Guest limit warning" else "Guest mode",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (guestSession.isLimitReached) {
                        "Limit Reached"
                    } else {
                        "${guestSession.getWorkoutsRemaining()} left"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Floating upgrade prompt for guest users
 */
@Composable
fun GuestUpgradePrompt(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuestSessionViewModel = hiltViewModel()
) {
    val shouldShowNudge by viewModel.shouldShowNudge.collectAsState()
    val shouldShowLimitWarning by viewModel.shouldShowLimitWarning.collectAsState()
    
    if (shouldShowNudge || shouldShowLimitWarning) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Upgrade prompt",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = viewModel.getNudgeMessage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Row {
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Later")
                    }
                    
                    androidx.compose.material3.TextButton(
                        onClick = onUpgrade
                    ) {
                        Text(
                            text = "Upgrade",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
