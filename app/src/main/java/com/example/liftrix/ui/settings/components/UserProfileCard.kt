package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.SubscriptionTier
import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.social.SocialProfile
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.LocalDateTime

/**
 * User profile display component with avatar, name, and edit functionality.
 * 
 * This component provides a user profile card for the settings screen with:
 * - Circular avatar (60x60dp) with user initials fallback
 * - User display name with proper typography prioritization
 * - Edit profile button with accessibility support
 * - Profile image upload functionality on avatar tap
 * - Consistent Material3 design with LiftrixCard base
 * - Proper loading and error state handling
 * - Race condition protection for username display
 * 
 * @param user The Firebase Auth user data to display, null for loading state
 * @param socialProfile The social profile data with username, null if not loaded
 * @param userProfile The user profile data with display name, null if not loaded
 * @param onEditProfile Callback invoked when edit profile button is clicked
 * @param onAvatarClick Callback invoked when avatar is clicked for image upload
 * @param modifier Modifier for styling the component
 */
@Composable
fun UserProfileCard(
    user: User?,
    socialProfile: SocialProfile? = null,
    userProfile: com.example.liftrix.domain.model.UserProfile? = null,
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
            ProfileImageDisplay(
                imageUrl = userProfile?.profileImageUrl ?: user?.photoUrl,
                displayName = userProfile?.displayName ?: user?.displayName,
                userId = user?.uid,
                size = 60.dp,
                onClick = onAvatarClick,
                modifier = Modifier
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Prioritize display sources to prevent race conditions and invalid data
                val displayText = when {
                    // 1. Prefer social profile username if available and NOT a temporary username
                    socialProfile?.username?.isNotBlank() == true && 
                        !socialProfile.username.startsWith("user_") && 
                        !socialProfile.username.contains("User ", ignoreCase = true) -> "@${socialProfile.username}"
                    // 2. Use user profile display name if available and not a fallback
                    userProfile?.displayName?.isNotBlank() == true && 
                        !userProfile.displayName.startsWith("User ") -> userProfile.displayName
                    // 3. Use social profile display name if available and not a fallback
                    socialProfile?.displayName?.isNotBlank() == true && 
                        !socialProfile.displayName.startsWith("User ") -> socialProfile.displayName
                    // 4. Fall back to Firebase Auth display name if not a fallback
                    user?.displayName?.isNotBlank() == true && 
                        !user.displayName.startsWith("User ") -> user.displayName
                    // 5. Use Firebase Auth email if valid
                    user?.email?.isNotBlank() == true && 
                        !user.email.startsWith("user_") && 
                        !user.isAnonymous -> user.email.substringBefore("@")
                    // 6. Default welcome message (avoids showing temporary usernames)
                    else -> "Welcome to Liftrix"
                }
                
                Text(
                    text = displayText,
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


@Preview(showBackground = true)
@Composable
private fun UserProfileCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Normal user profile with social profile
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
                socialProfile = com.example.liftrix.domain.model.social.SocialProfile(
                    userId = "user123",
                    username = "johndoe",
                    displayName = "John Doe",
                    bio = null,
                    profilePhotoUrl = null,
                    coverPhotoUrl = null,
                    workoutCount = 0,
                    followerCount = 0,
                    followingCount = 0,
                    memberSince = System.currentTimeMillis(),
                    lastActive = null,
                    isVerified = false,
                    isPrivate = true,
                    hideFromSuggestions = false,
                    allowFriendRequests = true,
                    instagramHandle = null,
                    youtubeChannel = null,
                    personalWebsite = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                ),
                userProfile = null, // Simplified preview - use null to test fallback logic
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
                userProfile = null, // Simplified preview - use null to test fallback logic
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