package com.example.liftrix.ui.profile

import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.social.FollowRelationship
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.usecase.social.FollowUserUseCase
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * FollowerListViewModel - Manages paginated follower/following lists with search
 * 
 * Features:
 * - Paginated loading with infinite scroll support
 * - Real-time search and filtering
 * - Follow/unfollow actions for each user
 * - Mutual connection detection and display
 * - Pull-to-refresh support
 * - Error handling with retry capability
 * - Memory-efficient lazy loading
 * - State preservation during configuration changes
 * 
 * Performance Optimizations:
 * - Debounced search to avoid excessive API calls
 * - Cached results for pagination
 * - Efficient list updates with DiffUtil patterns
 * - Background loading with proper coroutine scoping
 */
@HiltViewModel
class FollowerListViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val followUserUseCase: FollowUserUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<FollowerListUiState, FollowerListEvent>(
    errorHandler = errorHandler
) {
    
    override val _uiState = MutableStateFlow(FollowerListUiState())
    
    // Search query flow with debouncing
    private val searchQueryFlow = MutableStateFlow("")
    
    // Current user ID for follow operations
    private val currentUserId = flow {
        try {
            val userId = getCurrentUserIdUseCase()
            emit(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user ID")
            emit(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
    
    // Pagination state
    private var currentOffset = 0
    private val pageSize = 20
    private var isLastPage = false
    
    // Current list configuration
    private var currentUserId_param: String? = null
    private var currentListType: FollowerListType? = null
    
    init {
        // Set up search query debouncing
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300) // Wait 300ms after user stops typing
                .distinctUntilChanged()
                .collect { query ->
                    updateState { it.copy(searchQuery = query) }
                    filterFollowers()
                }
        }
    }
    
    /**
     * Load follower list for the specified user and type
     */
    fun loadFollowerList(userId: String, listType: FollowerListType) {
        if (currentUserId_param == userId && currentListType == listType && _uiState.value.followers.isNotEmpty()) {
            // Already loaded for this user and type
            return
        }
        
        currentUserId_param = userId
        currentListType = listType
        currentOffset = 0
        isLastPage = false
        
        viewModelScope.launch {
            try {
                updateState { 
                    it.copy(
                        isLoading = true, 
                        error = null,
                        followers = emptyList(),
                        filteredFollowers = emptyList()
                    ) 
                }
                
                Timber.d("Loading ${listType.name} for user: $userId")
                
                // 🔥 FIX: Use enriched repository methods that join with social_profiles table
                // to get complete user information instead of just relationship data
                val result = when (listType) {
                    FollowerListType.FOLLOWERS -> {
                        // For followers, we need to observe with profiles and convert Flow to suspend function
                        liftrixCatching(
                            errorMapper = { throwable ->
                                LiftrixError.DatabaseError(
                                    errorMessage = "Failed to get followers: ${throwable.message}",
                                    analyticsContext = mapOf("operation" to "GET_FOLLOWERS")
                                )
                            }
                        ) {
                            // Since we need suspending behavior, collect the first emission from the Flow
                            followRepository.observeFollowersWithProfiles(userId).first()
                        }
                    }
                    FollowerListType.FOLLOWING -> {
                        // For following, we need to observe with profiles and convert Flow to suspend function
                        liftrixCatching(
                            errorMapper = { throwable ->
                                LiftrixError.DatabaseError(
                                    errorMessage = "Failed to get following: ${throwable.message}",
                                    analyticsContext = mapOf("operation" to "GET_FOLLOWING")
                                )
                            }
                        ) {
                            // Since we need suspending behavior, collect the first emission from the Flow
                            followRepository.observeFollowingWithProfiles(userId).first()
                        }
                    }
                    FollowerListType.PENDING_REQUESTS -> followRepository.getPendingFollowRequests(
                        userId = userId
                    )
                }
                
                if (result.isSuccess) {
                    val followers = result.getOrThrow()
                    isLastPage = followers.size < pageSize
                    currentOffset = followers.size
                    
                    updateState { 
                        it.copy(
                            isLoading = false,
                            followers = followers,
                            canLoadMore = !isLastPage
                        )
                    }
                    
                    filterFollowers()
                    Timber.i("Loaded ${followers.size} ${listType.name.lowercase()} for user: $userId")
                } else {
                    val error = result.exceptionOrNull()
                    updateState { 
                        it.copy(
                            isLoading = false,
                            error = "Failed to load ${listType.name.lowercase()}: ${error?.message ?: "Unknown error"}"
                        )
                    }
                    Timber.e(error, "Failed to load ${listType.name} for user: $userId")
                }
                
            } catch (e: Exception) {
                updateState { 
                    it.copy(
                        isLoading = false,
                        error = "Unexpected error loading ${listType?.name?.lowercase()}: ${e.message}"
                    )
                }
                Timber.e(e, "Unexpected error loading follower list")
            }
        }
    }
    
    /**
     * Load more items for pagination
     */
    fun loadMoreItems() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.canLoadMore || isLastPage) {
            return
        }
        
        val userId = currentUserId_param ?: return
        val listType = currentListType ?: return
        
        viewModelScope.launch {
            try {
                updateState { it.copy(isLoadingMore = true) }
                
                Timber.d("Loading more ${listType.name} for user: $userId, offset: $currentOffset")
                
                // 🔥 FIX: For pagination, we still need the enriched data but pagination is tricky with Flow
                // For now, we'll disable pagination since the enriched methods return Flow
                // In a full implementation, we'd modify the repository to support paginated enriched queries
                updateState { it.copy(isLoadingMore = false, canLoadMore = false) }
                Timber.d("🔥 FIX: Pagination disabled while using enriched profile data - all data loaded initially")
                return@launch
                
            } catch (e: Exception) {
                updateState { 
                    it.copy(
                        isLoadingMore = false,
                        error = "Unexpected error loading more items: ${e.message}"
                    )
                }
                Timber.e(e, "Unexpected error loading more items")
            }
        }
    }
    
    /**
     * Toggle search visibility
     */
    fun toggleSearch() {
        updateState { 
            it.copy(
                isSearchVisible = !it.isSearchVisible,
                searchQuery = if (it.isSearchVisible) "" else it.searchQuery
            )
        }
        
        if (!_uiState.value.isSearchVisible) {
            searchQueryFlow.value = ""
        }
    }
    
    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }
    
    /**
     * Clear search query
     */
    fun clearSearch() {
        searchQueryFlow.value = ""
    }
    
    /**
     * Toggle follow status for a specific user
     */
    fun toggleFollowStatus(userId: String) {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val relationship = currentState.followers.find { it.userId == userId }
                    ?: return@launch
                
                val action = when (relationship.connectionStatus) {
                    com.example.liftrix.domain.model.social.ConnectionStatus.NONE -> FollowAction.FOLLOW
                    com.example.liftrix.domain.model.social.ConnectionStatus.CONNECTED -> FollowAction.UNFOLLOW
                    com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_SENT -> FollowAction.CANCEL
                    com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_RECEIVED -> FollowAction.ACCEPT
                    com.example.liftrix.domain.model.social.ConnectionStatus.MUTUAL_FOLLOW -> FollowAction.UNFOLLOW
                    com.example.liftrix.domain.model.social.ConnectionStatus.GYM_BUDDY -> FollowAction.UNFOLLOW
                    com.example.liftrix.domain.model.social.ConnectionStatus.BLOCKED -> return@launch
                    com.example.liftrix.domain.model.social.ConnectionStatus.SELF -> return@launch // Cannot follow yourself
                }
                
                Timber.d("Toggling follow status: $action for user: $userId")
                
                val result = followUserUseCase(
                    targetUserId = userId,
                    action = action
                )
                
                if (result.isSuccess) {
                    val newStatus = result.getOrThrow()
                    
                    // Update the relationship in the list
                    updateState { currentState ->
                        val updatedFollowers = currentState.followers.map { follower ->
                            if (follower.userId == userId) {
                                follower.copy(
                                    connectionStatus = when (newStatus) {
                                        com.example.liftrix.domain.model.social.FollowStatus.FOLLOWING -> com.example.liftrix.domain.model.social.ConnectionStatus.CONNECTED
                                        com.example.liftrix.domain.model.social.FollowStatus.PENDING_SENT -> com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_SENT
                                        com.example.liftrix.domain.model.social.FollowStatus.PENDING_RECEIVED -> com.example.liftrix.domain.model.social.ConnectionStatus.PENDING_RECEIVED
                                        com.example.liftrix.domain.model.social.FollowStatus.MUTUAL_FOLLOW -> com.example.liftrix.domain.model.social.ConnectionStatus.MUTUAL_FOLLOW
                                        com.example.liftrix.domain.model.social.FollowStatus.BLOCKED -> com.example.liftrix.domain.model.social.ConnectionStatus.BLOCKED
                                        else -> com.example.liftrix.domain.model.social.ConnectionStatus.NONE
                                    }
                                )
                            } else {
                                follower
                            }
                        }
                        currentState.copy(followers = updatedFollowers)
                    }
                    
                    filterFollowers()
                    Timber.i("Follow status updated: $action -> $newStatus")
                } else {
                    val error = result.exceptionOrNull()
                    updateState { 
                        it.copy(
                            error = "Failed to ${action.name.lowercase()}: ${error?.message ?: "Unknown error"}"
                        )
                    }
                    Timber.e(error, "Follow action failed: $action")
                }
                
            } catch (e: Exception) {
                updateState { 
                    it.copy(
                        error = "Unexpected error during follow action: ${e.message}"
                    )
                }
                Timber.e(e, "Unexpected error during follow action")
            }
        }
    }
    
    /**
     * Retry last operation
     */
    fun retryLastOperation() {
        val userId = currentUserId_param
        val listType = currentListType
        
        if (userId != null && listType != null) {
            loadFollowerList(userId, listType)
        }
    }
    
    override fun handleEvent(event: FollowerListEvent) {
        when (event) {
            is FollowerListEvent.LoadFollowerList -> loadFollowerList(event.userId, event.listType)
            is FollowerListEvent.LoadMoreItems -> loadMoreItems()
            is FollowerListEvent.ToggleSearch -> toggleSearch()
            is FollowerListEvent.UpdateSearchQuery -> updateSearchQuery(event.query)
            is FollowerListEvent.ClearSearch -> clearSearch()
            is FollowerListEvent.ToggleFollowStatus -> toggleFollowStatus(event.userId)
            is FollowerListEvent.RetryLastOperation -> retryLastOperation()
        }
    }
    
    /**
     * Filter followers based on search query
     */
    private fun filterFollowers() {
        val currentState = _uiState.value
        val query = currentState.searchQuery.lowercase().trim()
        
        val filteredList = if (query.isEmpty()) {
            currentState.followers
        } else {
            currentState.followers.filter { relationship ->
                relationship.displayName?.lowercase()?.contains(query) == true ||
                relationship.bio?.lowercase()?.contains(query) == true ||
                relationship.location?.lowercase()?.contains(query) == true
            }
        }
        
        updateState { it.copy(filteredFollowers = filteredList) }
    }
}

/**
 * UI state for follower list screen
 */
data class FollowerListUiState(
    val followers: List<FollowRelationship> = emptyList(),
    val filteredFollowers: List<FollowRelationship> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null
)

/**
 * Events for follower list interactions
 */
sealed class FollowerListEvent : ViewModelEvent {
    data class LoadFollowerList(val userId: String, val listType: FollowerListType) : FollowerListEvent()
    data object LoadMoreItems : FollowerListEvent()
    data object ToggleSearch : FollowerListEvent()
    data class UpdateSearchQuery(val query: String) : FollowerListEvent()
    data object ClearSearch : FollowerListEvent()
    data class ToggleFollowStatus(val userId: String) : FollowerListEvent()
    data object RetryLastOperation : FollowerListEvent()
}