package com.example.liftrix.ui.profile.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.domain.model.social.PublicUserProfile
import timber.log.Timber
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.ProfileColors
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton
import com.example.liftrix.ui.components.actions.TertiaryActionButton

/**
 * ModernProfileHeader - Enhanced profile header component following Material 3 design
 * 
 * Features:
 * - Large centered profile image (100dp) with brand border
 * - Prominent statistics display with large numbers
 * - Context-aware action buttons (Follow/Edit Profile)
 * - Verified badge support for verified accounts
 * - Responsive layout that adapts to content
 * - Full accessibility compliance with semantic properties
 * - Smooth animations for state changes
 * 
 * Design System Compliance:
 * - Uses ProfileColors for consistent theming
 * - Follows 16dp card radius and semantic spacing
 * - Implements ModernActionButton component hierarchy
 * - WCAG 2.1 AA accessibility standards
 * 
 * @param profile User profile data to display
 * @param stats Statistics data for follower/following counts
 * @param followStatus Current follow relationship status
 * @param isOwnProfile Whether this is the current user's profile
 * @param onFollowClick Callback for follow button interactions
 * @param onMessageClick Callback for message button interactions  
 * @param onStatsClick Callback for statistics item clicks
 * @param modifier Optional modifier for customization
 */
@Composable
fun ModernProfileHeader(
    profile: PublicUserProfile,
    stats: ProfileStatsData,
    followStatus: FollowStatus,
    isOwnProfile: Boolean,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onStatsClick: (StatType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image with verification badge
            ProfileImageSection(
                profile = profile,
                isOwnProfile = isOwnProfile,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Name and username
            ProfileNameSection(
                profile = profile,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Bio if available
            profile.bio?.let { bio ->
                Spacer(modifier = Modifier.height(8.dp))
                ProfileBioSection(
                    bio = bio,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics row with prominent numbers
            ProfileStatsSection(
                stats = stats,
                onStatsClick = onStatsClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            ProfileActionSection(
                followStatus = followStatus,
                isOwnProfile = isOwnProfile,
                onFollowClick = onFollowClick,
                onMessageClick = onMessageClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Profile image section with verification badge and border
 */
@Composable
private fun ProfileImageSection(
    profile: PublicUserProfile,
    isOwnProfile: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            val effectiveImageUrl = profile.profileImageUrl
            Timber.d(
                "PFP_DEBUG: MODERN_PROFILE_HEADER: isOwnProfile=$isOwnProfile | " +
                    "hasProfileImage=${!effectiveImageUrl.isNullOrBlank()}"
            )
            // Ensure display name is not empty for better initials
            val displayName = profile.displayName?.takeIf { it.isNotBlank() } 
                ?: profile.username?.takeIf { it.isNotBlank() } 
                ?: "User"
            
            Timber.d(
                "PFP_DEBUG: DISPLAY_NAME_DEBUG: hasDisplayName=${!profile.displayName.isNullOrBlank()} | " +
                    "hasUsername=${!profile.username.isNullOrBlank()}"
            )
            
            ProfileImageDisplay(
                imageUrl = effectiveImageUrl,
                displayName = displayName,
                userId = profile.userId,
                size = 96.dp,
                onClick = null,
                modifier = Modifier
                    .border(
                        BorderStroke(3.dp, LiftrixColorsV2.Teal),
                        CircleShape
                    )
            )
            
            // Verified badge if applicable
            if (profile.isVerified) {
                VerifiedBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                )
            }
        }
    }
}

/**
 * Verified badge component
 */
@Composable
private fun VerifiedBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = LiftrixColorsV2.Teal,
                shape = CircleShape
            )
            .semantics {
                contentDescription = "Verified account"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Verified account",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Profile name and username section
 */
@Composable
private fun ProfileNameSection(
    profile: PublicUserProfile,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display name
        Text(
            text = profile.displayName ?: profile.username,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                contentDescription = "User name: ${profile.displayName ?: profile.username}"
            }
        )
        
        // Username if different from display name
        if (profile.displayName != null && profile.displayName != profile.username) {
            Text(
                text = "@${profile.username}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    contentDescription = "Username: ${profile.username}"
                }
            )
        }
    }
}

/**
 * Profile bio section
 */
@Composable
private fun ProfileBioSection(
    bio: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = bio,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics {
            contentDescription = "Profile bio: $bio"
        }
    )
}

/**
 * Statistics section with prominent numbers
 */
@Composable
private fun ProfileStatsSection(
    stats: ProfileStatsData,
    onStatsClick: (StatType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Workouts stat
        StatsItem(
            value = stats.workoutCount.toString(),
            label = "Workouts",
            onClick = { onStatsClick(StatType.WORKOUTS) }
        )
        
        VerticalDivider(
            modifier = Modifier.height(48.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // Followers stat
        StatsItem(
            value = formatCount(stats.followersCount),
            label = "Followers",
            onClick = { onStatsClick(StatType.FOLLOWERS) }
        )
        
        VerticalDivider(
            modifier = Modifier.height(48.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        
        // Following stat
        StatsItem(
            value = formatCount(stats.followingCount),
            label = "Following", 
            onClick = { onStatsClick(StatType.FOLLOWING) }
        )
    }
}

/**
 * Individual statistics item
 */
@Composable
private fun StatsItem(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = "$value $label"
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextButton(onClick = onClick) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = LiftrixColorsV2.Teal
                )
                
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Action buttons section  
 */
@Composable
private fun ProfileActionSection(
    followStatus: FollowStatus,
    isOwnProfile: Boolean,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isOwnProfile) {
            // Edit Profile button for own profile
            PrimaryActionButton(
                text = "Edit Profile",
                onClick = { /* Navigate to edit profile */ },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Follow button for other profiles
            FollowButton(
                followStatus = followStatus,
                onClick = onFollowClick,
                modifier = Modifier.weight(1f)
            )
            
            // Settings/More Options button
            SecondaryActionButton(
                text = "Settings",
                onClick = onMessageClick, // Triggers settings menu in UserProfileScreen
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Follow button with animated states
 */
@Composable
private fun FollowButton(
    followStatus: FollowStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = followStatus != FollowStatus.BLOCKED
    
    val buttonModifier = modifier.semantics {
        contentDescription = when (followStatus) {
            FollowStatus.NONE -> "Follow user"
            FollowStatus.FOLLOWING -> "Unfollow user"
            FollowStatus.PENDING_SENT -> "Cancel follow request"
            FollowStatus.PENDING_RECEIVED -> "Accept follow request"
            FollowStatus.MUTUAL_FOLLOW -> "Mutual follow relationship"
            FollowStatus.BLOCKED -> "User is blocked"
        }
    }
    
    when (followStatus) {
        FollowStatus.NONE -> {
            PrimaryActionButton(
                text = "Follow",
                onClick = onClick,
                leadingIcon = Icons.Default.PersonAdd,
                modifier = buttonModifier,
                enabled = enabled
            )
        }
        FollowStatus.FOLLOWING -> {
            SecondaryActionButton(
                text = "Following",
                onClick = onClick,
                modifier = buttonModifier,
                enabled = enabled
            )
        }
        FollowStatus.PENDING_SENT -> {
            TertiaryActionButton(
                text = "Requested",
                onClick = onClick,
                leadingIcon = Icons.Default.Schedule,
                modifier = buttonModifier,
                enabled = enabled
            )
        }
        FollowStatus.PENDING_RECEIVED -> {
            PrimaryActionButton(
                text = "Accept",
                onClick = onClick,
                leadingIcon = Icons.Default.Check,
                modifier = buttonModifier,
                enabled = enabled
            )
        }
        FollowStatus.MUTUAL_FOLLOW -> {
            SecondaryActionButton(
                text = "Mutual",
                onClick = onClick,
                leadingIcon = Icons.Default.Favorite,
                modifier = buttonModifier,
                enabled = enabled
            )
        }
        FollowStatus.BLOCKED -> {
            TertiaryActionButton(
                text = "Blocked",
                onClick = onClick,
                leadingIcon = Icons.Default.Block,
                modifier = buttonModifier,
                enabled = enabled
            )
        }
    }
}


/**
 * Utility function to format large numbers for display
 */
private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${(count / 100_000) / 10.0}M"
        count >= 1_000 -> "${(count / 100) / 10.0}K"
        else -> count.toString()
    }
}
