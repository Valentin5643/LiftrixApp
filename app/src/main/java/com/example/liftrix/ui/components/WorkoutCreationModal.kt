package com.example.liftrix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.guest.GuestSessionViewModel
import com.example.liftrix.ui.guest.GuestSessionState

/**
 * Modal bottom sheet for workout creation options.
 * 
 * Provides users with two primary workout creation paths:
 * - Template-based workouts for quick setup
 * - Custom workouts for detailed personalization
 * 
 * Features:
 * - Material3 ModalBottomSheet with proper theming
 * - Two distinct workout creation options with clear visual hierarchy
 * - Accessibility support with semantic descriptions
 * - Proper dismissal behavior on outside tap and back gesture
 * - Integration with navigation system through callback actions
 * 
 * @param isVisible Controls modal visibility state
 * @param onDismiss Callback triggered when modal should be dismissed
 * @param onStartFromTemplate Callback triggered when user selects template-based workout creation
 * @param onStartBlankWorkout Callback triggered when user selects blank workout creation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCreationModal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onStartFromTemplate: () -> Unit,
    onStartBlankWorkout: () -> Unit,
    onGuestUpgrade: (() -> Unit)? = null,
    guestViewModel: GuestSessionViewModel = hiltViewModel()
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                contentDescription = "Workout creation options"
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val guestSessionState by guestViewModel.guestSessionState.collectAsState()
                
                // Header
                Text(
                    text = "Create Workout",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Guest limitation warning if applicable
                val currentGuestState = guestSessionState
                if (currentGuestState is GuestSessionState.Active) {
                    val guestSession = currentGuestState.guestSession
                    if (guestSession.isLimitReached) {
                        GuestLimitReachedWarning(
                            onUpgrade = {
                                onGuestUpgrade?.invoke()
                                onDismiss()
                            }
                        )
                    } else if (guestSession.getWorkoutsRemaining() <= 1) {
                        GuestLimitWarning(
                            workoutsRemaining = guestSession.getWorkoutsRemaining(),
                            onUpgrade = {
                                onGuestUpgrade?.invoke()
                                onDismiss()
                            }
                        )
                    }
                }
                
                // Determine if workout creation should be enabled
                val isEnabled = when (currentGuestState) {
                    is GuestSessionState.Active -> !currentGuestState.guestSession.isLimitReached
                    else -> true // Not a guest user or loading
                }
                
                // Start blank workout option
                WorkoutCreationOption(
                    icon = Icons.Default.Create,
                    title = "Make a Blank Workout",
                    description = "Build your own workout from scratch with personalized exercises",
                    enabled = isEnabled,
                    onClick = {
                        if (isEnabled) {
                            onStartBlankWorkout()
                            onDismiss()
                            
                            // Record interaction for guest users
                            if (guestSessionState is GuestSessionState.Active) {
                                guestViewModel.recordSignificantInteraction(
                                    com.example.liftrix.domain.model.SignificantInteraction.WORKOUT_COMPLETED
                                )
                            }
                        }
                    }
                )
                
                // Template workout option
                WorkoutCreationOption(
                    icon = Icons.Default.FitnessCenter,
                    title = "Start a Workout",
                    description = "Choose from pre-built workout routines and customize as needed",
                    enabled = isEnabled,
                    onClick = {
                        if (isEnabled) {
                            onStartFromTemplate()
                            onDismiss()
                            
                            // Record interaction for guest users
                            if (guestSessionState is GuestSessionState.Active) {
                                guestViewModel.recordSignificantInteraction(
                                    com.example.liftrix.domain.model.SignificantInteraction.TEMPLATE_VIEWED
                                )
                            }
                        }
                    }
                )
                
                // Bottom spacing for gesture navigation
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Individual workout creation option card component.
 * 
 * Displays a workout creation option with icon, title, and description
 * in a Material3 card format with proper touch feedback and accessibility.
 * 
 * @param icon Vector icon representing the workout creation type
 * @param title Primary text describing the option
 * @param description Secondary text providing additional context
 * @param onClick Callback triggered when option is selected
 * @param modifier Modifier for styling and positioning
 */
@Composable
private fun WorkoutCreationOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        onClick = if (enabled) onClick else { {} },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $description"
            },
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
            contentColor = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Warning component for guest users who have reached their workout limit
 */
@Composable
private fun GuestLimitReachedWarning(
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Guest Limit Reached",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "You've completed all 3 guest workouts. Create a free account to continue tracking your fitness journey.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onUpgrade
            ) {
                Text(
                    text = "Create Free Account",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Warning component for guest users approaching their workout limit
 */
@Composable
private fun GuestLimitWarning(
    workoutsRemaining: Int,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Almost at your limit!",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "You have $workoutsRemaining workout${if (workoutsRemaining != 1) "s" else ""} remaining. Create an account to unlock unlimited workouts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(
                onClick = onUpgrade
            ) {
                Text(
                    text = "Upgrade Now",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
} 
