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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.CompactLiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.theme.LiftrixColors
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Enhanced discovery carousel with smooth animations and modern card design
 * Displays recommended users with lazy loading and 70% scroll prefetch trigger for pagination
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
        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2),
        contentPadding = PaddingValues(horizontal = GridSystem.spacing3),
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
        
        // Loading placeholders with enhanced styling
        if (isLoading) {
            items(3) {
                UserCardShimmer()
            }
        }
    }
}

/**
 * Enhanced user recommendation card with modern styling and improved interactions
 */
@Composable
private fun RecommendedUserCard(
    user: RecommendedUser,
    onFollowUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier.width(160.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentDescription = "Recommended user: ${user.username}",
        contentPadding = PaddingValues(GridSystem.spacing3)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            // Enhanced profile image
            ProfileImage(
                imageUrl = user.profileImageUrl,
                displayName = user.username,
                size = 56.dp
            )
            
            // Username with enhanced typography
            Text(
                text = user.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            // User stats (if available)
            Text(
                text = "Active user",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            // Enhanced follow/unfollow button
            FollowButton(
                isFollowing = user.isFollowing,
                onClick = onFollowUser,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Enhanced follow/unfollow button with modern styling
 */
@Composable
private fun FollowButton(
    isFollowing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isFollowing) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(
                horizontal = GridSystem.spacing2,
                vertical = GridSystem.spacing1
            )
        ) {
            Icon(
                imageVector = Icons.Default.PersonRemove,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(GridSystem.spacing1))
            
            Text(
                text = "Following",
                style = MaterialTheme.typography.labelMedium
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(
                horizontal = GridSystem.spacing2,
                vertical = GridSystem.spacing1
            )
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(GridSystem.spacing1))
            
            Text(
                text = "Follow",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Enhanced profile image component with modern styling
 */
@Composable
private fun ProfileImage(
    imageUrl: String?,
    displayName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    // Enhanced initials-based approach with better styling
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
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        if (initials == "?") {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "User avatar",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.6f)
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

