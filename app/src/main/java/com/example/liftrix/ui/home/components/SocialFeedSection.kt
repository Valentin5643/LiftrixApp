package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.SharedWorkout
import com.example.liftrix.ui.social.SocialEvent
import com.example.liftrix.ui.social.SocialUiState
import com.example.liftrix.ui.social.components.FriendWorkoutCard
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Social feed section for home screen displaying friend workout activity.
 * 
 * Features:
 * - Horizontal scrolling list of friend workout cards
 * - Section header with "View All Friends" action
 * - Empty state with CTA to add friends
 * - Loading and error state handling
 * - Material 3 design with proper accessibility
 * 
 * @param socialUiState Current social UI state from SocialViewModel
 * @param onEvent Callback for handling social events
 * @param modifier Modifier for styling the section
 */
@Composable
fun SocialFeedSection(
    socialUiState: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Friends activity section with recent workout updates"
            }
    ) {
        SectionHeader(
            title = "Friends Activity",
            onViewAllClick = { onEvent(SocialEvent.ViewAllFriends) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        when {
            socialUiState.isLoading -> {
                LoadingState(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            socialUiState.error != null -> {
                ErrorState(
                    errorMessage = socialUiState.error,
                    onRetry = { onEvent(SocialEvent.RefreshData) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            socialUiState.friendWorkouts.isEmpty() -> {
                EmptyFriendsState(
                    onAddFriends = { onEvent(SocialEvent.ViewAllFriends) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            else -> {
                FriendWorkoutsList(
                    friendWorkouts = socialUiState.friendWorkouts,
                    onViewWorkout = { workout ->
                        onEvent(SocialEvent.ViewWorkout(workout))
                    },
                    onCongratulate = { workout ->
                        onEvent(SocialEvent.CongratulateWorkout(workout))
                    }
                )
            }
        }
    }
}

/**
 * Section header with title and view all action
 */
@Composable
private fun SectionHeader(
    title: String,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        TextButton(
            onClick = onViewAllClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Horizontal scrolling list of friend workout cards
 */
@Composable
private fun FriendWorkoutsList(
    friendWorkouts: List<SharedWorkout>,
    onViewWorkout: (SharedWorkout) -> Unit,
    onCongratulate: (SharedWorkout) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Horizontal list of ${friendWorkouts.size} friend workouts"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(friendWorkouts) { workout ->
            FriendWorkoutCard(
                sharedWorkout = workout,
                onViewWorkout = onViewWorkout,
                onCongratulate = onCongratulate
            )
        }
    }
}

/**
 * Empty state when user has no friends or friend activity
 */
@Composable
private fun EmptyFriendsState(
    onAddFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "No friend activity available, add friends to see their workouts"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No Friend Activity Yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Add friends to see their workout updates and stay motivated together!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
            
            Button(
                onClick = onAddFriends,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Add Friends",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Loading state for social feed section
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Loading friend activity"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
            
            Text(
                text = "Loading friend activity...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state for social feed section
 */
@Composable
private fun ErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Error loading friend activity: $errorMessage"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Failed to load friend activity",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
            
            TextButton(
                onClick = onRetry
            ) {
                Text(
                    text = "Try Again",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialFeedSectionPreview() {
    LiftrixTheme {
        SocialFeedSection(
            socialUiState = SocialUiState(
                friendWorkouts = emptyList(),
                isLoading = false
            ),
            onEvent = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialFeedSectionLoadingPreview() {
    LiftrixTheme {
        SocialFeedSection(
            socialUiState = SocialUiState(
                isLoading = true
            ),
            onEvent = {}
        )
    }
}