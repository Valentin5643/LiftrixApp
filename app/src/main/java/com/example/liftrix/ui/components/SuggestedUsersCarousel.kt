package com.example.liftrix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.liftrix.domain.model.social.SuggestedUser
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.theme.LiftrixSpacing
import timber.log.Timber

/**
 * SuggestedUsersCarousel - Horizontal scrolling carousel of user recommendations
 * 
 * Features:
 * - Horizontal scrolling with smooth animations
 * - Mutual connection badges for social proof
 * - One-tap follow actions with state management
 * - Elegant card design with profile images and info
 * - Real-time updates when follow actions complete
 * - Empty state handling for no suggestions
 * - Loading states with skeleton UI
 * - Error handling with retry functionality
 * 
 * Design System Compliance:
 * - Uses Material 3 design tokens
 * - Follows LiftrixSpacing semantic spacing
 * - Implements proper accessibility labels
 * - Responsive design for different screen sizes
 * - Consistent with other UI components
 */
@Composable
fun SuggestedUsersCarousel(
    title: String = "Suggested for you",
    onNavigateToProfile: (String) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SuggestedUsersCarouselViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    
    // Load suggestions when component first appears
    LaunchedEffect(Unit) {
        viewModel.loadSuggestedUsers()
    }
    
    Timber.d("SuggestedUsersCarousel: Composing with ${uiState.suggestedUsers.size} suggestions")
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
    ) {
        // Header row with title and optional "See All" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LiftrixSpacing.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (onSeeAll != null && uiState.suggestedUsers.isNotEmpty()) {
                TextButton(onClick = onSeeAll) {
                    Text("See All")
                }
            }
        }
        
        // Content based on state
        when {
            uiState.isLoading -> {
                SuggestedUsersLoadingCarousel()
            }
            uiState.error != null -> {
                SuggestedUsersErrorState(
                    error = uiState.error,
                    onRetry = { viewModel.retryLoadSuggestions() }
                )
            }
            uiState.suggestedUsers.isEmpty() -> {
                SuggestedUsersEmptyState()
            }
            else -> {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = LiftrixSpacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
                ) {
                    items(uiState.suggestedUsers) { suggestedUser ->
                        SuggestedUserCard(
                            suggestedUser = suggestedUser,
                            onFollowClick = { viewModel.followUser(suggestedUser.userId) },
                            onCardClick = { onNavigateToProfile(suggestedUser.userId) },
                            isFollowActionLoading = uiState.followingUsers.contains(suggestedUser.userId)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedUserCard(
    suggestedUser: SuggestedUser,
    onFollowClick: () -> Unit,
    onCardClick: () -> Unit,
    isFollowActionLoading: Boolean
) {
    Card(
        onClick = onCardClick,
        modifier = Modifier
            .width(200.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            // Profile Image
            ProfileImageDisplay(
                imageUrl = suggestedUser.profileImageUrl,
                displayName = suggestedUser.displayName ?: "User",
                userId = suggestedUser.userId,
                size = 64.dp,
                onClick = onCardClick
            )
            
            // User Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = suggestedUser.displayName ?: "Unknown User",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!suggestedUser.bio.isNullOrBlank()) {
                    Text(
                        text = suggestedUser.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Mutual connections badge
                if (suggestedUser.mutualConnections > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Mutual connections",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${suggestedUser.mutualConnections} mutual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Suggestion reason
                if (!suggestedUser.suggestionReason.isNullOrBlank()) {
                    Text(
                        text = suggestedUser.suggestionReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Follow Button
            if (!suggestedUser.isFollowing) {
                PrimaryActionButton(
                    text = if (isFollowActionLoading) "Following..." else "Follow",
                    onClick = onFollowClick,
                    leadingIcon = if (isFollowActionLoading) null else Icons.Default.PersonAdd,
                    enabled = !isFollowActionLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Show following status - no action needed since they're already followed
                SecondaryActionButton(
                    text = "Following",
                    onClick = { /* No action needed - already following */ },
                    leadingIcon = Icons.Default.Check,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Loading indicator
            if (isFollowActionLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

@Composable
private fun SuggestedUsersLoadingCarousel() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LiftrixSpacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        items(3) { // Show 3 loading cards
            Card(
                modifier = Modifier
                    .width(200.dp)
                    .height(280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(LiftrixSpacing.cardPadding)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
                ) {
                    // Profile image skeleton
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Text skeletons
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (it == 0) 0.8f else 0.6f)
                                .height(16.dp)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedUsersErrorState(
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
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = "Error loading suggestions",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        
        SecondaryActionButton(
            text = "Retry",
            onClick = onRetry,
            leadingIcon = Icons.Default.Refresh
        )
    }
}

@Composable
private fun SuggestedUsersEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
    ) {
        Icon(
            imageVector = Icons.Default.PersonSearch,
            contentDescription = "No suggestions",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = "No suggestions available",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Check back later for new users to follow",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * ViewModel for managing suggested users carousel state and follow actions
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class SuggestedUsersCarouselViewModel @javax.inject.Inject constructor(
    private val followRepository: com.example.liftrix.domain.repository.social.FollowRepository,
    private val socialRelationshipUseCase: com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase,
    private val getCurrentUserIdUseCase: com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase,
    private val userSearchRepository: com.example.liftrix.domain.repository.UserSearchRepository,
    errorHandler: com.example.liftrix.domain.usecase.common.ErrorHandler
) : com.example.liftrix.ui.common.viewmodel.BaseViewModel<SuggestedUsersCarouselUiState, SuggestedUsersCarouselEvent>(
    errorHandler = errorHandler
) {
    
    override val _uiState = kotlinx.coroutines.flow.MutableStateFlow(SuggestedUsersCarouselUiState())
    
    /**
     * Load suggested users from repository
     */
    fun loadSuggestedUsers() {
        viewModelScope.launch {
            try {
                updateState { it.copy(isLoading = true, error = null) }
                
                val currentUserId = getCurrentUserIdUseCase()
                if (currentUserId == null) {
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                    return@launch
                }
                
                Timber.d("Loading suggested users for user: $currentUserId")
                
                val result = followRepository.getSuggestedUsers(
                    userId = currentUserId,
                    limit = 10
                )
                
                if (result.isSuccess) {
                    val userIds = result.getOrThrow()
                    // Fetch full profile data for each suggested user
                    val suggestedUsers = userIds.mapNotNull { userId ->
                        try {
                            val profileResult = userSearchRepository.getPublicProfile(
                                userId = userId,
                                viewerId = currentUserId
                            )
                            
                            profileResult.getOrNull()?.let { profile ->
                                // Check actual follow status instead of hardcoding false
                                val followStatusResult = followRepository.getFollowStatus(
                                    followerId = currentUserId,
                                    targetUserId = userId
                                )
                                val isFollowing = followStatusResult.getOrNull()?.let { status ->
                                    status == com.example.liftrix.domain.model.social.FollowStatus.FOLLOWING
                                } ?: false
                                
                                SuggestedUser(
                                    userId = profile.userId,
                                    displayName = profile.displayName ?: profile.username,
                                    bio = profile.bio ?: "Fitness enthusiast",
                                    profileImageUrl = profile.profileImageUrl,
                                    mutualConnections = profile.mutualConnectionsCount,
                                    suggestionReason = when {
                                        profile.mutualConnectionsCount > 0 -> "${profile.mutualConnectionsCount} mutual connections"
                                        profile.location != null -> "Popular in ${profile.location}"
                                        profile.fitnessLevel != null -> "Similar fitness level"
                                        else -> "Recommended for you"
                                    },
                                    isFollowing = isFollowing
                                )
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to fetch profile for user: $userId")
                            null
                        }
                    }
                    
                    // Filter out users who are already being followed to maintain clean suggestions
                    val unfollowedUsers = suggestedUsers.filter { !it.isFollowing }
                    
                    updateState { 
                        it.copy(
                            isLoading = false,
                            suggestedUsers = unfollowedUsers
                        )
                    }
                    Timber.i("Loaded ${unfollowedUsers.size} unfollowed users from ${suggestedUsers.size} total suggestions")
                } else {
                    val error = result.exceptionOrNull()
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = "Failed to load suggestions: ${error?.message ?: "Unknown error"}"
                        )
                    }
                    Timber.e(error, "Failed to load suggested users")
                }
                
            } catch (e: Exception) {
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = "Unexpected error loading suggestions: ${e.message}"
                    )
                }
                Timber.e(e, "Unexpected error loading suggested users")
            }
        }
    }
    
    /**
     * Follow a suggested user
     */
    fun followUser(userId: String) {
        viewModelScope.launch {
            try {
                // Add user to following set to show loading state
                updateState { 
                    it.copy(followingUsers = it.followingUsers + userId) 
                }
                
                val result = socialRelationshipUseCase.followAction(
                    targetUserId = userId,
                    action = com.example.liftrix.domain.usecase.social.FollowAction.FOLLOW,
                    context = "SUGGESTED_USERS_CAROUSEL"
                )
                
                if (result.isSuccess) {
                    // Remove user from suggestions after successful follow
                    updateState { currentState ->
                        currentState.copy(
                            suggestedUsers = currentState.suggestedUsers.filterNot { it.userId == userId },
                            followingUsers = currentState.followingUsers - userId
                        )
                    }
                    Timber.i("Successfully followed suggested user: $userId")
                } else {
                    val error = result.exceptionOrNull()
                    updateState { 
                        it.copy(
                            followingUsers = it.followingUsers - userId,
                            error = "Failed to follow user: ${error?.message ?: "Unknown error"}"
                        )
                    }
                    Timber.e(error, "Failed to follow suggested user: $userId")
                }
                
            } catch (e: Exception) {
                updateState { 
                    it.copy(
                        followingUsers = it.followingUsers - userId,
                        error = "Unexpected error following user: ${e.message}"
                    )
                }
                Timber.e(e, "Unexpected error following suggested user")
            }
        }
    }
    
    /**
     * Retry loading suggestions
     */
    fun retryLoadSuggestions() {
        loadSuggestedUsers()
    }
    
    override fun handleEvent(event: SuggestedUsersCarouselEvent) {
        when (event) {
            is SuggestedUsersCarouselEvent.LoadSuggestions -> loadSuggestedUsers()
            is SuggestedUsersCarouselEvent.FollowUser -> followUser(event.userId)
            is SuggestedUsersCarouselEvent.RetryLoad -> retryLoadSuggestions()
        }
    }
}

/**
 * UI state for suggested users carousel
 */
data class SuggestedUsersCarouselUiState(
    val suggestedUsers: List<SuggestedUser> = emptyList(),
    val followingUsers: Set<String> = emptySet(), // Users currently being followed (loading state)
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Events for suggested users carousel
 */
sealed class SuggestedUsersCarouselEvent : com.example.liftrix.ui.common.event.ViewModelEvent {
    data object LoadSuggestions : SuggestedUsersCarouselEvent()
    data class FollowUser(val userId: String) : SuggestedUsersCarouselEvent()
    data object RetryLoad : SuggestedUsersCarouselEvent()
}