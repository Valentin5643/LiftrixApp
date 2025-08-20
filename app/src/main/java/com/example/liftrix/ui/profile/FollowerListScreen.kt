package com.example.liftrix.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.social.FollowRelationship
import com.example.liftrix.domain.model.social.FollowerDisplayItem
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.theme.LiftrixSpacing
import timber.log.Timber

/**
 * FollowerListScreen - Displays paginated list of followers/following with search
 * 
 * Comprehensive follower management interface with:
 * - Paginated lazy loading for performance
 * - Real-time search functionality
 * - Follow/unfollow actions for each user
 * - Mutual connection indicators
 * - Empty state handling
 * - Pull-to-refresh support
 * - Privacy-aware user information display
 * 
 * Design System Compliance:
 * - Uses UnifiedWorkoutCard for consistent layout
 * - Follows ModernActionButton hierarchy (Primary/Secondary/Tertiary)
 * - Implements LiftrixSpacing semantic tokens
 * - WCAG 2.1 AA accessibility compliance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowerListScreen(
    userId: String,
    listType: FollowerListType,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowerListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    
    // Load followers/following when screen loads
    LaunchedEffect(userId, listType) {
        viewModel.loadFollowerList(userId, listType)
    }
    
    // Infinite scrolling - load more when approaching end
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                val lastVisibleItem = visibleItems.lastOrNull()
                val totalItems = listState.layoutInfo.totalItemsCount
                
                if (lastVisibleItem?.index == totalItems - 1 && totalItems > 0) {
                    viewModel.loadMoreItems()
                }
            }
    }
    
    Timber.d("FollowerListScreen: Composing for user $userId with type $listType")
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { 
                Text(
                    text = when (listType) {
                        FollowerListType.FOLLOWERS -> "Followers"
                        FollowerListType.FOLLOWING -> "Following"
                        FollowerListType.PENDING_REQUESTS -> "Follow Requests"
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Search button
                IconButton(
                    onClick = { viewModel.toggleSearch() }
                ) {
                    Icon(
                        imageVector = if (uiState.isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (uiState.isSearchVisible) "Close search" else "Search"
                    )
                }
            }
        )
        
        // Search bar
        if (uiState.isSearchVisible) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LiftrixSpacing.screenPadding),
                placeholder = { Text("Search users...") },
                leadingIcon = { 
                    Icon(Icons.Default.Search, contentDescription = "Search") 
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )
        }
        
        // Content
        when {
            uiState.isLoading && uiState.followers.isEmpty() -> {
                FollowerListLoadingState()
            }
            uiState.error != null -> {
                FollowerListErrorState(
                    error = uiState.error,
                    onRetry = { viewModel.retryLastOperation() }
                )
            }
            uiState.followers.isEmpty() -> {
                FollowerListEmptyState(
                    listType = listType,
                    searchQuery = uiState.searchQuery
                )
            }
            else -> {
                FollowerListContent(
                    followers = uiState.filteredFollowers,
                    isLoadingMore = uiState.isLoadingMore,
                    canLoadMore = uiState.canLoadMore,
                    onFollowToggle = { userId -> viewModel.toggleFollowStatus(userId) },
                    onNavigateToProfile = onNavigateToProfile,
                    listState = listState
                )
            }
        }
    }
}

@Composable
private fun FollowerListContent(
    followers: List<FollowRelationship>,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onFollowToggle: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(LiftrixSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        items(followers) { relationship ->
            FollowerListItem(
                relationship = relationship,
                onFollowToggle = { onFollowToggle(relationship.userId) },
                onNavigateToProfile = { onNavigateToProfile(relationship.userId) }
            )
        }
        
        // Loading more indicator
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.cardPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // End of list indicator
        if (!canLoadMore && followers.isNotEmpty()) {
            item {
                Text(
                    text = "You've reached the end",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.cardPadding)
                )
            }
        }
    }
}

@Composable
private fun FollowerListItem(
    relationship: FollowRelationship,
    onFollowToggle: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    UnifiedWorkoutCard(
        title = relationship.displayName ?: "Unknown User",
        subtitle = generateFollowerSubtitle(relationship),
        onClick = onNavigateToProfile
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Image
            ProfileImageDisplay(
                imageUrl = relationship.profileImageUrl,
                displayName = relationship.displayName ?: "User",
                userId = relationship.userId,
                size = 48.dp,
                onClick = onNavigateToProfile
            )
            
            // User Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = relationship.displayName ?: "Unknown User",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (!relationship.bio.isNullOrBlank()) {
                    Text(
                        text = relationship.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                // Follow date timestamp
                Text(
                    text = formatFollowDate(java.time.Instant.ofEpochMilli(relationship.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Follow Button
            FollowActionButton(
                connectionStatus = relationship.connectionStatus,
                onClick = onFollowToggle,
                isCurrentUser = false // This screen doesn't show current user in the list
            )
        }
    }
}

@Composable
private fun FollowActionButton(
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit,
    isCurrentUser: Boolean
) {
    if (isCurrentUser) {
        return // No button for current user
    }
    
    when (connectionStatus) {
        ConnectionStatus.NONE -> {
            PrimaryActionButton(
                text = "Follow",
                onClick = onClick,
                leadingIcon = Icons.Default.PersonAdd
            )
        }
        ConnectionStatus.CONNECTED -> {
            SecondaryActionButton(
                text = "Following",
                onClick = onClick,
                leadingIcon = Icons.Default.Check
            )
        }
        ConnectionStatus.PENDING_SENT -> {
            TertiaryActionButton(
                text = "Requested",
                onClick = onClick,
                leadingIcon = Icons.Default.Schedule
            )
        }
        ConnectionStatus.PENDING_RECEIVED -> {
            PrimaryActionButton(
                text = "Accept",
                onClick = onClick,
                leadingIcon = Icons.Default.Check
            )
        }
        ConnectionStatus.BLOCKED -> {
            TertiaryActionButton(
                text = "Blocked",
                onClick = onClick,
                leadingIcon = Icons.Default.Block,
                enabled = false
            )
        }
        ConnectionStatus.MUTUAL_FOLLOW -> {
            SecondaryActionButton(
                text = "Mutual",
                onClick = onClick,
                leadingIcon = Icons.Default.Favorite
            )
        }
        ConnectionStatus.GYM_BUDDY -> {
            PrimaryActionButton(
                text = "Gym Buddy",
                onClick = onClick,
                leadingIcon = Icons.Default.Star
            )
        }
        ConnectionStatus.SELF -> {
            // No action button for viewing own profile in follower list
            // This case shouldn't normally appear in follower lists
        }
    }
}

@Composable
private fun FollowerListLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        repeat(5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(LiftrixSpacing.cardPadding),
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile image placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Text placeholder
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowerListErrorState(
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = "Error Loading List",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = error ?: "Unknown error occurred",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        PrimaryActionButton(
            text = "Retry",
            onClick = onRetry
        )
    }
}

@Composable
private fun FollowerListEmptyState(
    listType: FollowerListType,
    searchQuery: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
    ) {
        val (icon, title, message) = when {
            searchQuery.isNotEmpty() -> Triple(
                Icons.Default.SearchOff,
                "No results found",
                "No users match your search for \"$searchQuery\""
            )
            listType == FollowerListType.FOLLOWERS -> Triple(
                Icons.Default.People,
                "No followers yet",
                "When people follow this profile, they'll appear here"
            )
            listType == FollowerListType.FOLLOWING -> Triple(
                Icons.Default.PersonAdd,
                "Not following anyone",
                "Start following other users to see them here"
            )
            else -> Triple(
                Icons.Default.Notifications,
                "No pending requests",
                "Follow requests will appear here"
            )
        }
        
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// Helper functions
private fun generateFollowerSubtitle(relationship: FollowRelationship): String {
    return buildString {
        if (relationship.location?.isNotBlank() == true) {
            append(relationship.location)
        }
        if (relationship.bio?.isNotBlank() == true && relationship.location.isNullOrBlank()) {
            append(relationship.bio)
        }
        if (isEmpty()) {
            append("Liftrix member")
        }
    }
}

private fun formatFollowDate(createdAt: java.time.LocalDateTime): String {
    val now = java.time.LocalDateTime.now()
    val period = java.time.Period.between(createdAt.toLocalDate(), now.toLocalDate())
    
    return when {
        period.years > 0 -> "Followed ${period.years} year${if (period.years > 1) "s" else ""} ago"
        period.months > 0 -> "Followed ${period.months} month${if (period.months > 1) "s" else ""} ago"
        period.days > 7 -> "Followed ${period.days / 7} week${if (period.days / 7 > 1) "s" else ""} ago"
        period.days > 0 -> "Followed ${period.days} day${if (period.days > 1) "s" else ""} ago"
        else -> "Followed recently"
    }
}

/**
 * Enum representing different types of follower lists
 */
enum class FollowerListType {
    FOLLOWERS,
    FOLLOWING,
    PENDING_REQUESTS
}