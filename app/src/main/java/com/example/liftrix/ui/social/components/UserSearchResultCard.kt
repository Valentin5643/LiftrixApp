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
import com.example.liftrix.domain.model.social.FitnessLevel
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
    Card(
        onClick = onUserClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "User ${user.displayName}, ${user.totalWorkouts} workouts, member since ${user.memberSince.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User header with avatar and basic info
            UserHeader(
                user = user,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Bio section if available
            if (!user.bio.isNullOrBlank()) {
                UserBio(
                    bio = user.bio,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Shared interests and stats
            UserInterests(
                user = user,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Connection action
            ConnectionAction(
                connectionStatus = user.connectionStatus,
                mutualConnections = user.mutualConnections,
                onConnectClick = onConnectClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * User header with profile image, name, and basic stats
 */
@Composable
private fun UserHeader(
    user: UserSearchResult,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Profile image or avatar
        ProfileAvatar(
            imageUrl = user.profileImageUrl,
            displayName = user.displayName,
            modifier = Modifier.size(56.dp)
        )
        
        // User info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Display name
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Member info and fitness level
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = "Member since ${user.memberSince.format(DateTimeFormatter.ofPattern("MMM yyyy"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                
                user.fitnessLevel?.let { level ->
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
            
            // Workout count
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "${user.totalWorkouts} workouts",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            contentDescription = null,
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
 * Connection action button based on current status
 */
@Composable
private fun ConnectionAction(
    connectionStatus: ConnectionStatus,
    mutualConnections: Int,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mutual connections info
        if (mutualConnections > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = "$mutualConnections mutual connections",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }
        
        // Connection button
        when (connectionStatus) {
            ConnectionStatus.NONE -> {
                Button(
                    onClick = onConnectClick,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect")
                }
            }
            ConnectionStatus.PENDING_SENT -> {
                OutlinedButton(
                    onClick = { /* Handle cancel request */ },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pending")
                }
            }
            ConnectionStatus.PENDING_RECEIVED -> {
                Button(
                    onClick = onConnectClick,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept")
                }
            }
            ConnectionStatus.CONNECTED -> {
                OutlinedButton(
                    onClick = { /* Handle view profile */ },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connected")
                }
            }
        }
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
                sharedGoals = listOf(FitnessGoal.STRENGTH, FitnessGoal.MUSCLE_GAIN),
                connectionStatus = ConnectionStatus.NONE,
                mutualConnections = 3
            ),
            onUserClick = { },
            onConnectClick = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}