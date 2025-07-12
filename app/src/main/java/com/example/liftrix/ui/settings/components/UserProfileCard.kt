package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.LocalDateTime

/**
 * User profile display component with avatar, name, and edit functionality.
 * 
 * This component provides a user profile card for the settings screen with:
 * - Circular avatar (60x60dp) with user initials fallback
 * - User display name with proper typography
 * - Edit profile button with accessibility support
 * - Profile image upload functionality on avatar tap
 * - Consistent Material3 design with LiftrixCard base
 * - Proper loading and error state handling
 * 
 * @param user The user data to display, null for loading state
 * @param onEditProfile Callback invoked when edit profile button is clicked
 * @param onAvatarClick Callback invoked when avatar is clicked for image upload
 * @param modifier Modifier for styling the component
 */
@Composable
fun UserProfileCard(
    user: User?,
    onEditProfile: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier,
        contentDescription = "User profile section"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularAvatar(
                user = user,
                onAvatarClick = onAvatarClick,
                modifier = Modifier.size(60.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = user?.displayName ?: "Loading...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                TextButton(
                    onClick = onEditProfile,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics {
                        contentDescription = "Edit your profile"
                    }
                ) {
                    Text(
                        text = "Edit Your Profile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Circular avatar component with user initials fallback and click handling.
 * 
 * Features:
 * - 60x60dp circular avatar following Material3 design
 * - User initials extracted from displayName with fallback
 * - Person icon fallback for missing/invalid displayName
 * - Clickable for profile image upload functionality
 * - Proper accessibility support
 * - Loading state handling
 * 
 * @param user The user data for avatar display
 * @param onAvatarClick Callback invoked when avatar is clicked
 * @param modifier Modifier for styling the component
 */
@Composable
private fun CircularAvatar(
    user: User?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = user?.displayName
        ?.trim()
        ?.split(' ')
        ?.take(2)
        ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
        ?.joinToString("")
        ?.ifEmpty { "?" }
        ?: "?"
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = "Change profile picture"
            ) { onAvatarClick() }
            .semantics {
                contentDescription = "Profile picture, tap to change"
            },
        contentAlignment = Alignment.Center
    ) {
        if (initials == "?" || user == null) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProfileCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Normal user profile
            UserProfileCard(
                user = User(
                    uid = "user123",
                    email = "john.doe@example.com",
                    displayName = "John Doe",
                    photoUrl = null,
                    isAnonymous = false,
                    subscriptionTier = SubscriptionTier.FREE,
                    subscriptionStatus = SubscriptionStatus.ACTIVE,
                    subscriptionExpiresAt = null,
                    premiumFeaturesEnabled = false,
                    onboardingCompleted = true,
                    profileVersion = 1,
                    createdAt = LocalDateTime.now().minusDays(30),
                    lastSignInAt = LocalDateTime.now().minusHours(2),
                    updatedAt = LocalDateTime.now().minusHours(2)
                ),
                onEditProfile = { },
                onAvatarClick = { }
            )
            
            // Single name user
            UserProfileCard(
                user = User(
                    uid = "user456",
                    email = "sarah@example.com",
                    displayName = "Sarah",
                    photoUrl = null,
                    isAnonymous = false,
                    subscriptionTier = SubscriptionTier.PREMIUM,
                    subscriptionStatus = SubscriptionStatus.ACTIVE,
                    subscriptionExpiresAt = LocalDateTime.now().plusDays(30),
                    premiumFeaturesEnabled = true,
                    onboardingCompleted = true,
                    profileVersion = 2,
                    createdAt = LocalDateTime.now().minusDays(15),
                    lastSignInAt = LocalDateTime.now().minusMinutes(30),
                    updatedAt = LocalDateTime.now().minusMinutes(30)
                ),
                onEditProfile = { },
                onAvatarClick = { }
            )
            
            // Loading state
            UserProfileCard(
                user = null,
                onEditProfile = { },
                onAvatarClick = { }
            )
        }
    }
}