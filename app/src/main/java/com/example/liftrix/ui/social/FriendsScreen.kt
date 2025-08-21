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
import androidx.compose.ui.graphics.vector.ImageVector
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
    modifier: Modifier = Modifier,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
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
                            contentDescription = null,
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
                            contentDescription = null,
                            tint = if (selectedTabIndex == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
        
        // Tab Content
        when (selectedTabIndex) {
            0 -> FollowingTab(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateToUserSearch = onNavigateToUserSearch,
                modifier = Modifier.fillMaxSize()
            )
            1 -> FollowersTab(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                modifier = Modifier.fillMaxSize()
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Search Section
        SearchUsers(
            searchQuery = uiState.searchQuery,
            searchResults = uiState.searchResults,
            onEvent = onEvent,
            onNavigateToUserSearch = onNavigateToUserSearch,
            modifier = Modifier.padding(16.dp)
        )
        
        // Following List
        FollowingList(
            following = uiState.following,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onEvent = onEvent,
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
    modifier: Modifier = Modifier
) {
    FollowersList(
        followers = uiState.followers,
        isLoading = uiState.isLoading,
        error = uiState.error,
        onEvent = onEvent,
        modifier = modifier
    )
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
                        contentDescription = null
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
                contentDescription = null,
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
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        error != null -> {
            ErrorState(
                error = error,
                onRetry = { onEvent(SocialEvent.LoadFollowing) },
                modifier = modifier
            )
        }
        
        following.isEmpty() -> {
            EmptyFollowingState(
                modifier = modifier
            )
        }
        
        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(following) { user ->
                    FollowingItem(
                        user = user,
                        onUnfollow = { onEvent(SocialEvent.UnfollowUser(user.userId)) }
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
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        error != null -> {
            ErrorState(
                error = error,
                onRetry = { onEvent(SocialEvent.LoadFollowers) },
                modifier = modifier
            )
        }
        
        followers.isEmpty() -> {
            EmptyFollowersState(
                modifier = modifier
            )
        }
        
        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(followers) { user ->
                    FollowerItem(
                        user = user,
                        onFollowBack = { onEvent(SocialEvent.FollowUser(user.userId)) }
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initials = user.displayName
                    .split(' ')
                    .take(2)
                    .mapNotNull { it.firstOrNull() }
                    .joinToString("")
                
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!user.email.isNullOrEmpty()) {
                    Text(
                        text = user.email,
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initials = user.displayName
                    .split(' ')
                    .take(2)
                    .mapNotNull { it.firstOrNull() }
                    .joinToString("")
                
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!user.email.isNullOrEmpty()) {
                    Text(
                        text = user.email,
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
            }
            
            Button(
                onClick = onFollowBack
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Follow Back")
            }
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
            contentDescription = null,
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
            contentDescription = null,
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
                contentDescription = null,
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