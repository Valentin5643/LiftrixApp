package com.example.liftrix.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { 
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.onEvent(SocialEvent.Refresh) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Refresh friends"
                    )
                }
            }
        )
        
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { 
                    Text(
                        "Friends",
                        style = MaterialTheme.typography.titleSmall
                    ) 
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null
                    )
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { 
                    Text(
                        "Requests",
                        style = MaterialTheme.typography.titleSmall
                    ) 
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (uiState.friendRequests.isNotEmpty()) {
                                Badge { 
                                    Text(uiState.friendRequests.size.toString()) 
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null
                        )
                    }
                }
            )
        }
        
        // Tab Content
        when (selectedTabIndex) {
            0 -> FriendsTab(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                modifier = Modifier.fillMaxSize()
            )
            1 -> RequestsTab(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Friends tab with search and friends list
 */
@Composable
private fun FriendsTab(
    uiState: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Search Section
        SearchFriends(
            searchQuery = uiState.searchQuery,
            searchResults = uiState.searchResults,
            onEvent = onEvent,
            modifier = Modifier.padding(16.dp)
        )
        
        // Friends List
        FriendsList(
            friends = uiState.friends,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Friend requests tab
 */
@Composable
private fun RequestsTab(
    uiState: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    FriendRequests(
        requests = uiState.friendRequests,
        isLoading = uiState.isLoading,
        error = uiState.error,
        onEvent = onEvent,
        modifier = modifier
    )
}

/**
 * Search functionality for finding friends
 */
@Composable
private fun SearchFriends(
    searchQuery: String,
    searchResults: List<User>,
    onEvent: (SocialEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { onEvent(SocialEvent.SearchFriends(it)) },
            placeholder = { Text("Search friends by name or email") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onEvent(SocialEvent.SearchFriends("")) }
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
            modifier = Modifier.fillMaxWidth()
        )
        
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
                            onSendRequest = { onEvent(SocialEvent.SendFriendRequest(user.uid)) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search result item with add friend button
 */
@Composable
private fun SearchResultItem(
    user: User,
    onSendRequest: () -> Unit,
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
                text = user.displayName ?: "Unknown User",
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
            onClick = onSendRequest,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add")
        }
    }
}

/**
 * Friends list display
 */
@Composable
private fun FriendsList(
    friends: List<Friend>,
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
                onRetry = { onEvent(SocialEvent.LoadFriends) },
                modifier = modifier
            )
        }
        
        friends.isEmpty() -> {
            EmptyFriendsState(
                modifier = modifier
            )
        }
        
        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(friends) { friend ->
                    FriendItem(friend = friend)
                }
            }
        }
    }
}

/**
 * Individual friend item
 */
@Composable
private fun FriendItem(
    friend: Friend,
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
            // Friend Avatar Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initials = friend.displayName
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
                    text = friend.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (!friend.email.isNullOrEmpty()) {
                    Text(
                        text = friend.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Friend requests management
 */
@Composable
private fun FriendRequests(
    requests: List<Friend>,
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
                onRetry = { onEvent(SocialEvent.LoadFriends) },
                modifier = modifier
            )
        }
        
        requests.isEmpty() -> {
            EmptyRequestsState(
                modifier = modifier
            )
        }
        
        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(requests) { request ->
                    FriendRequestItem(
                        request = request,
                        onAccept = { onEvent(SocialEvent.AcceptFriendRequest(request.userId)) },
                        onDecline = { onEvent(SocialEvent.DeclineFriendRequest(request.userId)) }
                    )
                }
            }
        }
    }
}

/**
 * Individual friend request item with accept/decline buttons
 */
@Composable
private fun FriendRequestItem(
    request: Friend,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = request.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "wants to be friends",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Decline")
                }
                
                Button(
                    onClick = onAccept
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

/**
 * Empty state for when user has no friends
 */
@Composable
private fun EmptyFriendsState(
    modifier: Modifier = Modifier
) {
    EmptyStateContent(
        icon = Icons.Default.People,
        title = "No friends yet",
        description = "Search for friends above to start building your fitness community!",
        modifier = modifier
    )
}

/**
 * Empty state for when user has no friend requests
 */
@Composable
private fun EmptyRequestsState(
    modifier: Modifier = Modifier
) {
    EmptyStateContent(
        icon = Icons.Default.PersonAdd,
        title = "No friend requests",
        description = "When people send you friend requests, they'll appear here.",
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