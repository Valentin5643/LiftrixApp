package com.example.liftrix.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.ui.common.UserCardShimmer
import com.example.liftrix.ui.theme.LiftrixColors
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Discovery carousel component displaying recommended users with lazy loading
 * and 70% scroll prefetch trigger for pagination
 */
@Composable
fun DiscoveryCarousel(
    recommendedUsers: List<RecommendedUser>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onFollowUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // Prefetch trigger at 70% scroll width
    LaunchedEffect(listState, recommendedUsers.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            
            if (totalItemsCount > 0) {
                val triggerPoint = (totalItemsCount * 0.7).toInt()
                lastVisibleItemIndex >= triggerPoint
            } else false
        }.distinctUntilChanged().collect { shouldLoadMore ->
            if (shouldLoadMore && hasMore && !isLoading) {
                onLoadMore()
            }
        }
    }
    
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.testTag("discovery_carousel")
    ) {
        items(
            items = recommendedUsers,
            key = { user -> user.userId }
        ) { user ->
            RecommendedUserCard(
                user = user,
                onFollowUser = { onFollowUser(user.userId) },
                modifier = Modifier.testTag("recommended_user_card")
            )
        }
        
        // Loading placeholders
        if (isLoading) {
            items(3) {
                UserCardShimmer()
            }
        }
    }
}

/**
 * Individual user recommendation card with profile image, username, and follow button
 */
@Composable
private fun RecommendedUserCard(
    user: RecommendedUser,
    onFollowUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(140.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile image
            ProfileImage(
                imageUrl = user.profileImageUrl,
                displayName = user.username,
                size = 48.dp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Username
            Text(
                text = user.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Follow/Unfollow button
            FollowButton(
                isFollowing = user.isFollowing,
                onClick = onFollowUser
            )
        }
    }
}

/**
 * Profile image component with URL loading capability and initials fallback
 * Based on existing FriendAvatar pattern but enhanced for image loading
 */
@Composable
private fun ProfileImage(
    imageUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // For now, using initials-based approach similar to FriendAvatar
    // TODO: Add Coil image loading library for actual URL support in future iteration
    val initials = displayName
        .trim()
        .split(' ')
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LiftrixColors.Primary),
        contentAlignment = Alignment.Center
    ) {
        if (initials == "?") {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User avatar",
                tint = LiftrixColors.OnPrimary,
                modifier = Modifier.size(size * 0.5f)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = LiftrixColors.OnPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Follow/Unfollow button with appropriate icons and states
 */
@Composable
private fun FollowButton(
    isFollowing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, contentDescription) = if (isFollowing) {
        Icons.Default.PersonRemove to "Unfollow user"
    } else {
        Icons.Default.PersonAdd to "Follow user"
    }
    
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFollowing) {
                MaterialTheme.colorScheme.error
            } else {
                LiftrixColors.Primary
            }
        )
    }
}

