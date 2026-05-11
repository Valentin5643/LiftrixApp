package com.example.liftrix.ui.social.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Card component for displaying user search results
 * 
 * Shows user profile information, shared interests, and connection actions
 * in a modern, accessible design following Liftrix UI patterns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSearchResultCard(
    user: UserSearchResult,
    onUserClick: () -> Unit,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasInterests = user.sharedEquipment.isNotEmpty() || user.sharedGoals.isNotEmpty()

    Card(
        onClick = onUserClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "User ${user.displayName}, ${user.totalWorkouts} workouts, member since ${formatMemberSinceDate(user.memberSince)}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // User header with avatar and basic info
            UserHeader(
                user = user,
                onConnectClick = onConnectClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Bio section if available
            val bio = user.bio
            if (!bio.isNullOrBlank()) {
                UserBio(
                    bio = bio,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Shared interests and stats
            if (hasInterests) {
                UserInterests(
                    user = user,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (user.mutualConnections > 0) {
                MutualConnections(
                    mutualConnections = user.mutualConnections,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * User header with profile image, name, and basic stats
 */
@Composable
private fun UserHeader(
    user: UserSearchResult,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile image or avatar
        ProfileAvatar(
            imageUrl = user.profileImageUrl,
            displayName = user.displayName,
            modifier = Modifier.size(44.dp)
        )
        
        // User info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Display name
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Workout count and optional member date
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "Workouts",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = "${user.totalWorkouts} workouts",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                user.memberSince?.let { memberSince ->
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Member since",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )

                    Text(
                        text = formatMemberSinceDate(memberSince),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            user.fitnessLevel?.let { level ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(
                        containerColor = getFitnessLevelColor(level),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = level.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        ConnectionButton(
            connectionStatus = user.connectionStatus,
            onConnectClick = onConnectClick
        )
    }
}

/**
 * Profile avatar with fallback to initials
 */
@Composable
private fun ProfileAvatar(
    imageUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "$displayName profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback to initials
            val initials = displayName
                .split(' ')
                .take(2)
                .mapNotNull { it.firstOrNull() }
                .joinToString("")
                .uppercase()
            
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * User bio section
 */
@Composable
private fun UserBio(
    bio: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = bio,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * User interests and shared equipment/goals
 */
@Composable
private fun UserInterests(
    user: UserSearchResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Shared equipment
        if (user.sharedEquipment.isNotEmpty()) {
            InterestRow(
                icon = Icons.Default.FitnessCenter,
                label = "Equipment",
                items = user.sharedEquipment.map { it.name }
            )
        }
        
        // Shared goals
        if (user.sharedGoals.isNotEmpty()) {
            InterestRow(
                icon = Icons.Default.TrendingUp,
                label = "Goals",
                items = user.sharedGoals.map { it.name }
            )
        }
    }
}

/**
 * Interest row with icon and chip list
 */
@Composable
private fun InterestRow(
    icon: ImageVector,
    label: String,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        // Interest chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.take(3).forEach { item ->
                AssistChip(
                    onClick = { /* No action needed for display */ },
                    label = {
                        Text(
                            text = item.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )
            }
            
            if (items.size > 3) {
                Text(
                    text = "+${items.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * Mutual connections context.
 */
@Composable
private fun MutualConnections(
    mutualConnections: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.People,
            contentDescription = "Mutual connections",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$mutualConnections mutual connections",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Connection action button based on current status.
 */
@Composable
private fun ConnectionButton(
    connectionStatus: ConnectionStatus,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (connectionStatus) {
        ConnectionStatus.NONE -> {
            Button(
                onClick = onConnectClick,
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Follow",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Follow", style = MaterialTheme.typography.labelMedium)
            }
        }
        ConnectionStatus.PENDING_SENT -> {
            OutlinedButton(
                onClick = onConnectClick,
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Request sent",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Requested", style = MaterialTheme.typography.labelMedium)
            }
        }
        ConnectionStatus.PENDING_RECEIVED -> {
            Button(
                onClick = onConnectClick,
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Accept follow",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Accept", style = MaterialTheme.typography.labelMedium)
            }
        }
        ConnectionStatus.CONNECTED,
        ConnectionStatus.MUTUAL_FOLLOW -> {
            OutlinedButton(
                onClick = onConnectClick,
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Following",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Following", style = MaterialTheme.typography.labelMedium)
            }
        }
        ConnectionStatus.GYM_BUDDY -> {
            OutlinedButton(
                onClick = { },
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Gym buddy",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Buddy", style = MaterialTheme.typography.labelMedium)
            }
        }
        ConnectionStatus.BLOCKED -> {
            OutlinedButton(
                onClick = { },
                modifier = modifier,
                enabled = false,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Blocked",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Blocked", style = MaterialTheme.typography.labelMedium)
            }
        }
        ConnectionStatus.SELF -> Unit
    }
}

/**
 * Gets color for fitness level badge
 */
@Composable
private fun getFitnessLevelColor(level: FitnessLevel): androidx.compose.ui.graphics.Color {
    return when (level) {
        FitnessLevel.BEGINNER -> MaterialTheme.colorScheme.secondary
        FitnessLevel.INTERMEDIATE -> MaterialTheme.colorScheme.primary
        FitnessLevel.ADVANCED -> MaterialTheme.colorScheme.tertiary
        FitnessLevel.EXPERT -> MaterialTheme.colorScheme.error
    }
}

@Preview(showBackground = true)
@Composable
private fun UserSearchResultCardPreview() {
    LiftrixTheme {
        UserSearchResultCard(
            user = UserSearchResult(
                userId = "1",
                displayName = "John Doe",
                profileImageUrl = null,
                bio = "Fitness enthusiast looking for workout partners and motivation",
                fitnessLevel = FitnessLevel.INTERMEDIATE,
                totalWorkouts = 45,
                memberSince = LocalDateTime.now().minusMonths(6),
                sharedEquipment = listOf(Equipment.BARBELL, Equipment.DUMBBELLS),
                sharedGoals = listOf(FitnessGoal.INCREASE_STRENGTH, FitnessGoal.BUILD_MUSCLE),
                connectionStatus = ConnectionStatus.NONE,
                mutualConnections = 3
            ),
            onUserClick = { },
            onConnectClick = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Safe date formatting utility to prevent null crashes
 */
private fun formatMemberSinceDate(memberSince: LocalDateTime?): String {
    return try {
        memberSince?.let { date ->
            date.format(DateTimeFormatter.ofPattern("MMM yyyy"))
        } ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}
