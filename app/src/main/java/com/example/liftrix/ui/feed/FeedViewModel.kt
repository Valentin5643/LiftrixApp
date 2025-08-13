package com.example.liftrix.ui.feed

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.repository.social.FeedRepository
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the social feed screen
 * Manages feed state, post interactions, and tab selection
 */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : BaseViewModel<FeedUiState, FeedEvent>() {

    private val _selectedTab = MutableStateFlow(FeedTab.HOME)
    private val _likedPosts = MutableStateFlow<Set<String>>(emptySet())
    private val _savedPosts = MutableStateFlow<Set<String>>(emptySet())

    override val initialState = FeedUiState()

    init {
        collectUserState()
    }

    val posts: Flow<PagingData<WorkoutPost>> = _selectedTab
        .flatMapLatest { tab ->
            getCurrentUserIdUseCase()
                .flatMapLatest { userResult ->
                    when (userResult) {
                        is LiftrixResult.Success -> {
                            when (tab) {
                                FeedTab.HOME -> feedRepository.getHomeFeed(
                                    userId = userResult.data,
                                    pageSize = 20
                                )
                                FeedTab.DISCOVERY -> feedRepository.getDiscoveryFeed(
                                    userId = userResult.data,
                                    pageSize = 20
                                )
                            }
                        }
                        is LiftrixResult.Error -> {
                            Timber.e("Failed to get current user ID for feed")
                            flowOf(PagingData.empty())
                        }
                    }
                }
        }
        .cachedIn(viewModelScope)

    override fun onEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.SelectTab -> selectTab(event.tab)
            is FeedEvent.PostInteraction -> handlePostInteraction(event.interaction)
            is FeedEvent.RefreshFeed -> refreshFeed()
        }
    }

    override fun updateUiState(update: FeedUiState.() -> FeedUiState) {
        _uiState.value = _uiState.value.update()
    }

    private fun collectUserState() {
        combine(
            _selectedTab,
            _likedPosts,
            _savedPosts
        ) { tab, liked, saved ->
            FeedUiState(
                selectedTab = tab,
                likedPosts = liked,
                savedPosts = saved,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialState
        ).let { flow ->
            viewModelScope.launch {
                flow.collect { newState ->
                    _uiState.value = newState
                }
            }
        }
    }

    private fun selectTab(tab: FeedTab) {
        _selectedTab.value = tab
        Timber.d("Selected feed tab: $tab")
    }

    private fun handlePostInteraction(interaction: PostInteraction) {
        when (interaction) {
            is PostInteraction.Like -> toggleLike(interaction.postId)
            is PostInteraction.Save -> toggleSave(interaction.postId)
            is PostInteraction.Share -> sharePost(interaction.post)
            is PostInteraction.CopyWorkout -> copyWorkout(interaction.post)
        }
    }

    private fun toggleLike(postId: String) {
        viewModelScope.launch {
            getCurrentUserIdUseCase().collect { userResult ->
                when (userResult) {
                    is LiftrixResult.Success -> {
                        val userId = userResult.data
                        val currentLiked = _likedPosts.value
                        val isCurrentlyLiked = currentLiked.contains(postId)
                        
                        // Optimistic update
                        _likedPosts.value = if (isCurrentlyLiked) {
                            currentLiked - postId
                        } else {
                            currentLiked + postId
                        }
                        
                        // Perform the actual like/unlike operation
                        engagementRepository.toggleLike(postId, userId).collect { result ->
                            when (result) {
                                is LiftrixResult.Success -> {
                                    Timber.d("Like toggled successfully for post: $postId")
                                }
                                is LiftrixResult.Error -> {
                                    // Revert optimistic update on error
                                    _likedPosts.value = currentLiked
                                    Timber.e("Failed to toggle like for post: $postId", result.error)
                                }
                            }
                        }
                    }
                    is LiftrixResult.Error -> {
                        Timber.e("Failed to get current user ID for like action")
                    }
                }
            }
        }
    }

    private fun toggleSave(postId: String) {
        viewModelScope.launch {
            getCurrentUserIdUseCase().collect { userResult ->
                when (userResult) {
                    is LiftrixResult.Success -> {
                        val userId = userResult.data
                        val currentSaved = _savedPosts.value
                        val isCurrentlySaved = currentSaved.contains(postId)
                        
                        // Optimistic update
                        _savedPosts.value = if (isCurrentlySaved) {
                            currentSaved - postId
                        } else {
                            currentSaved + postId
                        }
                        
                        // Perform the actual save/unsave operation
                        engagementRepository.toggleSave(postId, userId).collect { result ->
                            when (result) {
                                is LiftrixResult.Success -> {
                                    Timber.d("Save toggled successfully for post: $postId")
                                }
                                is LiftrixResult.Error -> {
                                    // Revert optimistic update on error
                                    _savedPosts.value = currentSaved
                                    Timber.e("Failed to toggle save for post: $postId", result.error)
                                }
                            }
                        }
                    }
                    is LiftrixResult.Error -> {
                        Timber.e("Failed to get current user ID for save action")
                    }
                }
            }
        }
    }

    private fun sharePost(post: WorkoutPost) {
        viewModelScope.launch {
            // Share functionality - could integrate with Android share intent
            Timber.d("Sharing post: ${post.id}")
            // TODO: Implement share functionality
        }
    }

    private fun copyWorkout(post: WorkoutPost) {
        viewModelScope.launch {
            getCurrentUserIdUseCase().collect { userResult ->
                when (userResult) {
                    is LiftrixResult.Success -> {
                        val userId = userResult.data
                        // Copy the workout from the post to the user's templates
                        engagementRepository.copyWorkoutFromPost(post.id, userId).collect { result ->
                            when (result) {
                                is LiftrixResult.Success -> {
                                    Timber.d("Workout copied successfully from post: ${post.id}")
                                    // TODO: Show success message/navigation
                                }
                                is LiftrixResult.Error -> {
                                    Timber.e("Failed to copy workout from post: ${post.id}", result.error)
                                    // TODO: Show error message
                                }
                            }
                        }
                    }
                    is LiftrixResult.Error -> {
                        Timber.e("Failed to get current user ID for copy workout action")
                    }
                }
            }
        }
    }

    private fun refreshFeed() {
        viewModelScope.launch {
            getCurrentUserIdUseCase().collect { userResult ->
                when (userResult) {
                    is LiftrixResult.Success -> {
                        feedRepository.refreshFeed(userResult.data).collect { result ->
                            when (result) {
                                is LiftrixResult.Success -> {
                                    Timber.d("Feed refreshed successfully")
                                }
                                is LiftrixResult.Error -> {
                                    Timber.e("Failed to refresh feed", result.error)
                                }
                            }
                        }
                    }
                    is LiftrixResult.Error -> {
                        Timber.e("Failed to get current user ID for feed refresh")
                    }
                }
            }
        }
    }
}

/**
 * UI state for the feed screen
 */
data class FeedUiState(
    val selectedTab: FeedTab = FeedTab.HOME,
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Events that can be triggered in the feed screen
 */
sealed class FeedEvent {
    data class SelectTab(val tab: FeedTab) : FeedEvent()
    data class PostInteraction(val interaction: PostInteraction) : FeedEvent()
    object RefreshFeed : FeedEvent()
}

/**
 * Available feed tabs
 */
enum class FeedTab {
    HOME,
    DISCOVERY
}

/**
 * Types of post interactions
 */
sealed class PostInteraction {
    data class Like(val postId: String) : PostInteraction()
    data class Save(val postId: String) : PostInteraction()
    data class Share(val post: WorkoutPost) : PostInteraction()
    data class CopyWorkout(val post: WorkoutPost) : PostInteraction()
}