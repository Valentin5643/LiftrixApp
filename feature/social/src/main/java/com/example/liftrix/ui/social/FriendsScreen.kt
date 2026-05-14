package com.example.liftrix.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.User
import com.example.liftrix.domain.model.Friend
import androidx.compose.foundation.background
import com.example.liftrix.ui.theme.LiftrixTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Full friends list, search, requests, and privacy settings in dedicated screen
 * Features tabbed interface for friends and requests with comprehensive friend management
 * Now integrated with global navigation system - no longer needs dedicated TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit, // Kept for backward compatibility, but navigation handled by global TopAppBar
    onNavigateToUserSearch: () -> Unit = {},
    onNavigateToGymBuddy: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // Handle navigation events
    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is SocialNavigationEvent.NavigateToUserProfile -> {
                    onNavigateToUserProfile(event.userId)
                }
            }
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // TopAppBar removed - now handled by NavigationAwareTopAppBar in MainNavigationContainer
        // This eliminates the duplicate top bar and saves vertical space
        
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { 
                    Text(
                        "Following",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (uiState.followingCount > 0) {
                                Badge { 
                                    Text(uiState.followingCount.toString()) 
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Following tab",
                            tint = if (selectedTabIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { 
                    Text(
                        "Followers",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (uiState.followersCount > 0) {
                                Badge { 
                                    Text(uiState.followersCount.toString()) 
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Followers tab",
                            tint = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
        
        GymBuddiesEntryCard(
            onClick = onNavigateToGymBuddy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Tab Content
        when (selectedTabIndex) {
            0 -> FollowingTab(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateToUserSearch = onNavigateToUserSearch,
                onUserClick = onNavigateToUserProfile,
                modifier = Modifier.fillMaxSize()
            )
            1 -> FollowersTab(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onUserClick = onNavigateToUserProfile,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun GymBuddiesEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Open Gym Buddies"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Gym Buddies",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Show your QR code or scan a code to add a buddy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Following tab with search and list of people you follow
 */
@Composable
private fun FollowingTab(
    uiState: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local state for following search
    var followingSearchQuery by remember { mutableStateOf("") }
    
    // Filter the following list based on search query
    val filteredFollowing = remember(uiState.following, followingSearchQuery) {
        if (followingSearchQuery.isBlank()) {
            uiState.following
        } else {
            uiState.following.filter { friend ->
                friend.displayName.contains(followingSearchQuery, ignoreCase = true) ||
                (friend.email?.contains(followingSearchQuery, ignoreCase = true) == true)
            }
        }
    }
    
    Column(
        modifier = modifier
    ) {
        // Search Section for Following
        SearchFollowingUsers(
            searchQuery = followingSearchQuery,
            onSearchQueryChange = { followingSearchQuery = it },
            modifier = Modifier.padding(16.dp)
        )
        
        // Following List (filtered)
        FollowingList(
            following = filteredFollowing,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onEvent = onEvent,
            onUserClick = onUserClick,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Followers tab - shows people who follow you
 */
@Composable
private fun FollowersTab(
    uiState: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FollowersList(
        followers = uiState.followers,
        isLoading = uiState.isLoading,
        error = uiState.error,
        onEvent = onEvent,
        onUserClick = onUserClick,
        modifier = modifier
    )
}

/**
 * Search functionality for filtering existing following users
 */
@Composable
private fun SearchFollowingUsers(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search following by name or email") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search following"
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onSearchQueryChange("") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() }
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Search functionality for finding users to follow
 */
@Composable
private fun SearchUsers(
    searchQuery: String,
    searchResults: List<User>,
    onEvent: (SocialEvent) -> Unit,
    onNavigateToUserSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { onEvent(SocialEvent.SearchUsers(it)) },
                placeholder = { Text("Search users by name or email") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search users"
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { onEvent(SocialEvent.SearchUsers("")) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
                modifier = Modifier.weight(1f)
            )
            
            // Add navigation button
            IconButton(
                onClick = onNavigateToUserSearch
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = "Advanced search"
                )
            }
        }
        
        // Search Results
        if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Search Results",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    searchResults.forEach { user ->
                        SearchResultItem(
                            user = user,
                            onFollowUser = { onEvent(SocialEvent.FollowUser(user.uid)) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search result item with follow button
 */
@Composable
private fun SearchResultItem(
    user: User,
    onFollowUser: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = user.displayName ?: "User",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        
        FilledTonalButton(
            onClick = onFollowUser,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Follow user",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Follow")
        }
    }
}

/**
 * Following list display - shows people you follow
 */
@Composable
private fun FollowingList(
    following: List<Friend>,
    isLoading: Boolean,
    error: String?,
    onEvent: (SocialEvent) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug logging for UI state
    LaunchedEffect(following, isLoading, error) {
        timber.log.Timber.d("DEBUG_UI_FOLLOWING: State update - isLoading: $isLoading, error: $error, following count: ${following.size}")
        following.forEachIndexed { index, friend ->
            timber.log.Timber.d("DEBUG_UI_FOLLOWING: Friend $index - ${friend.displayName} (${friend.userId})")
        }
    }
    
    when {
        isLoading -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWING: Showing loading state")
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        error != null -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWING: Showing error state: $error")
            ErrorState(
                error = error,
                onRetry = { onEvent(SocialEvent.LoadFollowing) },
                modifier = modifier
            )
        }
        
        following.isEmpty() -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWING: Showing empty state")
            EmptyFollowingState(
                modifier = modifier
            )
        }
        
        else -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWING: Showing list with ${following.size} items")
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(following) { user ->
                    FollowingItem(
                        user = user,
                        onUnfollow = { onEvent(SocialEvent.UnfollowUser(user.userId)) },
                        onUserClick = { onUserClick(user.userId) }
                    )
                }
            }
        }
    }
}

/**
 * Followers list display - shows people who follow you
 */
@Composable
private fun FollowersList(
    followers: List<Friend>,
    isLoading: Boolean,
    error: String?,
    onEvent: (SocialEvent) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Debug logging for UI state
    LaunchedEffect(followers, isLoading, error) {
        timber.log.Timber.d("DEBUG_UI_FOLLOWERS: State update - isLoading: $isLoading, error: $error, followers count: ${followers.size}")
        followers.forEachIndexed { index, friend ->
            timber.log.Timber.d("DEBUG_UI_FOLLOWERS: Follower $index - ${friend.displayName} (${friend.userId})")
        }
    }
    
    when {
        isLoading -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWERS: Showing loading state")
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        error != null -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWERS: Showing error state: $error")
            ErrorState(
                error = error,
                onRetry = { onEvent(SocialEvent.LoadFollowers) },
                modifier = modifier
            )
        }
        
        followers.isEmpty() -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWERS: Showing empty state")
            EmptyFollowersState(
                modifier = modifier
            )
        }
        
        else -> {
            timber.log.Timber.d("DEBUG_UI_FOLLOWERS: Showing list with ${followers.size} items")
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(followers) { user ->
                    FollowerItem(
                        user = user,
                        onFollowBack = { onEvent(SocialEvent.FollowUser(user.userId)) },
                        onUserClick = { onUserClick(user.userId) }
                    )
                }
            }
        }
    }
}

/**
 * Individual following item with unfollow option
 */
@Composable
private fun FollowingItem(
    user: Friend,
    onUnfollow: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onUserClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Profile Image
            SocialProfileImage(
                imageUrl = user.avatarUrl,
                displayName = user.displayName,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val email = user.email
                if (!email.isNullOrEmpty()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (user.isOnline()) {
                    Text(
                        text = user.getPresenceDisplayStatus(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Display MUTUAL badge for mutual following
                if (user.isMutual) {
                    Text(
                        text = "MUTUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            OutlinedButton(
                onClick = onUnfollow,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Unfollow")
            }
        }
    }
}

/**
 * Individual follower item with follow back option
 */
@Composable
private fun FollowerItem(
    user: Friend,
    onFollowBack: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onUserClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Profile Image
            SocialProfileImage(
                imageUrl = user.avatarUrl,
                displayName = user.displayName,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val email = user.email
                if (!email.isNullOrEmpty()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                if (user.isOnline()) {
                    Text(
                        text = user.getPresenceDisplayStatus(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Display MUTUAL badge for mutual followers
                if (user.isMutual) {
                    Text(
                        text = "MUTUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            if (!user.isMutual) {
                Button(
                    onClick = onFollowBack
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Follow back",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Follow Back")
                }
            }
        }
    }
}

@Composable
private fun SocialProfileImage(
    imageUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
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
            Text(
                text = displayName
                    .split(' ')
                    .take(2)
                    .mapNotNull { it.firstOrNull() }
                    .joinToString("")
                    .ifBlank { displayName.take(2) }
                    .uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


/**
 * Empty state for when user follows no one
 */
@Composable
private fun EmptyFollowingState(
    modifier: Modifier = Modifier
) {
    EmptyStateContent(
        icon = Icons.Default.People,
        title = "Not following anyone yet",
        description = "Search for people above to start following and see their workout updates!",
        modifier = modifier
    )
}

/**
 * Empty state for when user has no followers
 */
@Composable
private fun EmptyFollowersState(
    modifier: Modifier = Modifier
) {
    EmptyStateContent(
        icon = Icons.Default.PersonAdd,
        title = "No followers yet",
        description = "When people follow you, they'll appear here. Share great workouts to attract followers!",
        modifier = modifier
    )
}

/**
 * Reusable empty state content
 */
@Composable
private fun EmptyStateContent(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Error state with retry button
 */
@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Retry",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FriendsScreenPreview() {
    LiftrixTheme {
        FriendsScreen(
            onNavigateBack = { }
        )
    }
}
