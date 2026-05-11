package com.example.liftrix.ui.feed

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.WorkoutPost
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.feature.home.ports.HomeFeedPort
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
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
    private val feedPort: HomeFeedPort,
    private val authQueryUseCase: AuthQueryUseCase
) : ModernBaseViewModel<FeedUiState>(
    initialState = FeedUiState(
        selectedTab = FeedTab.HOME,
        likedPosts = emptySet(),
        savedPosts = emptySet(),
        currentUserId = null,
        isLoading = false,
        error = null
    )
) {

    private val _selectedTab = MutableStateFlow(FeedTab.HOME)
    private val _likedPosts = MutableStateFlow<Set<String>>(emptySet())
    private val _savedPosts = MutableStateFlow<Set<String>>(emptySet())
    
    // Track posts that have been loaded to initialize engagement state
    private val _loadedPosts = MutableStateFlow<List<WorkoutPost>>(emptyList())
    
    private val _viewModelEvents = MutableSharedFlow<FeedViewModelEvent>()
    val viewModelEvents: SharedFlow<FeedViewModelEvent> = _viewModelEvents.asSharedFlow()
    init {
        collectUserState()
        initializeCurrentUser()
    }
    
    private fun initializeCurrentUser() {
        viewModelScope.launch {
            try {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value },
                    onFailure = { null }
                )
                _uiState.value = _uiState.value.copy(currentUserId = userId)

                seedSampleData(userId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user ID")
            }
        }
    }
    
    private suspend fun seedSampleData(userId: String?) {
        if (userId != null) {
            feedPort.seedWorkoutPosts(userId).fold(
                onSuccess = { count ->
                    if (count > 0) {
                    }
                },
                onFailure = { error ->
                    Timber.e("Failed to seed sample data: $error")
                }
            )
        }
    }

    val posts: Flow<PagingData<WorkoutPost>> = _selectedTab
        .flatMapLatest { tab ->
            flow {
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value },
                    onFailure = { null }
                )
                if (userId != null) {
                    Timber.d("🔍 WORKOUT-POSTS-DEBUG: FeedViewModel loading ${tab.name} feed for user $userId")

                    seedSampleData(userId)

                    val feedFlow = when (tab) {
                        FeedTab.HOME -> feedPort.getHomeFeed(
                            userId = userId,
                            pageSize = 20
                        )
                        FeedTab.DISCOVERY -> feedPort.getDiscoveryFeed(
                            userId = userId,
                            pageSize = 10  // Start with 10 items for Explore feed
                        )
                    }
                    emitAll(feedFlow)
                } else {
                    Timber.e("Failed to get current user ID for feed")
                    emit(PagingData.empty())
                }
            }
        }
        .cachedIn(viewModelScope)

    fun handleEvent(event: FeedEvent) {
        when (event) {
            is FeedEvent.SelectTab -> selectTab(event.tab)
            is FeedEvent.HandlePostInteraction -> handlePostInteraction(event.interaction)
            is FeedEvent.RefreshFeed -> refreshFeed()
        }
    }

    private fun handleError(error: LiftrixError) {
        updateState { currentState ->
            currentState.copy(isLoading = false, error = error)
        }
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
                isLoading = false,
                error = null
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeedUiState(
                selectedTab = FeedTab.HOME,
                likedPosts = emptySet(),
                savedPosts = emptySet(),
                isLoading = true,
                error = null
            )
        ).let { flow ->
            viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
                flow.collect { newState ->
                    updateState { newState }
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
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
            if (userId != null) {
                val currentLiked = _likedPosts.value
                val isCurrentlyLiked = currentLiked.contains(postId)
                
                // Optimistic update
                _likedPosts.value = if (isCurrentlyLiked) {
                    currentLiked - postId
                } else {
                    currentLiked + postId
                }
                
                // Perform the actual like/unlike operation
                val result = feedPort.toggleLike(postId, userId)
                result.fold(
                    onSuccess = {
                        Timber.d("Like toggled successfully for post: $postId")
                    },
                    onFailure = { error ->
                        // Revert optimistic update on error
                        _likedPosts.value = currentLiked
                        val liftrixError = if (error is LiftrixError) {
                            error
                        } else {
                            LiftrixError.NetworkError(
                                errorMessage = "Failed to toggle like for post: $postId"
                            )
                        }
                        handleError(liftrixError)
                        Timber.e("Failed to toggle like for post: $postId", error)
                    }
                )
            } else {
                Timber.e("Failed to get current user ID for like action")
            }
        }
    }

    private fun toggleSave(postId: String) {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
            if (userId != null) {
                val currentSaved = _savedPosts.value
                val isCurrentlySaved = currentSaved.contains(postId)
                
                // Optimistic update
                _savedPosts.value = if (isCurrentlySaved) {
                    currentSaved - postId
                } else {
                    currentSaved + postId
                }
                
                // Perform the actual save/unsave operation
                val result = feedPort.toggleSave(postId, userId)
                result.fold(
                    onSuccess = {
                        Timber.d("Save toggled successfully for post: $postId")
                    },
                    onFailure = { error ->
                        // Revert optimistic update on error
                        _savedPosts.value = currentSaved
                        val liftrixError = if (error is LiftrixError) {
                            error
                        } else {
                            LiftrixError.NetworkError(
                                errorMessage = "Failed to toggle save for post: $postId"
                            )
                        }
                        handleError(liftrixError)
                        Timber.e("Failed to toggle save for post: $postId", error)
                    }
                )
            } else {
                Timber.e("Failed to get current user ID for save action")
            }
        }
    }

    private fun sharePost(post: WorkoutPost) {
        viewModelScope.launch {
            try {
                // Track share analytics with proper share event
                val userId = authQueryUseCase(waitForAuth = false).fold(
                    onSuccess = { it?.value ?: "" },
                    onFailure = { "" }
                )
                feedPort.trackShare(
                    contentType = "POST",
                    contentId = post.id,
                    platform = "NATIVE_SHARE", // Android native share intent
                    userId = userId,
                    hasCustomMessage = false,
                    additionalProperties = mapOf(
                        "target_user_id" to post.userId,
                        "source" to "FEED",
                        "has_achievements" to post.achievements.isNotEmpty()
                    )
                )
                
                // Create share content
                val shareText = buildShareContent(post)
                
                // Emit share event to UI to trigger Android share intent
                _viewModelEvents.emit(FeedViewModelEvent.SharePost(shareText, post.id))
                
                Timber.d("Post share initiated: ${post.id}")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to share post: ${post.id}")
                feedPort.trackError(
                    "SHARE_POST_FAILED",
                    e.message ?: "Unknown error",
                    mapOf("post_id" to post.id)
                )
            }
        }
    }
    
    private fun buildShareContent(post: WorkoutPost): String {
        val workoutSummary = buildString {
            append("Check out this awesome workout!\n\n")
            
            if (post.authorUsername?.isNotEmpty() == true) {
                append("💪 By: ${post.authorDisplayName ?: post.authorUsername}\n")
            }
            
            val summary = post.workoutSummary
            if (summary?.totalSets != null) {
                append("📊 ${summary.totalSets} sets")
                val duration = post.workoutDuration
                if (duration != null) {
                    append(" • ${duration / 60}m")
                }
                append("\n")
            }
            
            if (post.achievements.isNotEmpty()) {
                append("🏆 ${post.achievements.size} Personal Records!\n")
            }
            
            if (post.caption.isNotEmpty()) {
                append("\n\"${post.caption}\"\n")
            }
            
            append("\nShared from Liftrix")
        }
        return workoutSummary
    }

    private fun copyWorkout(post: WorkoutPost) {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
            if (userId != null) {
                // Copy the workout from the post to the user's templates
                val result = feedPort.copyWorkoutFromPost(post.id, userId)
                result.fold(
                    onSuccess = {
                        // Track copy workout analytics
                        feedPort.trackEngagement(
                            action = "COPY_WORKOUT",
                            contentType = "POST",
                            contentId = post.id,
                            contentOwnerUserId = post.userId,
                            userId = userId,
                            additionalProperties = mapOf(
                                "source" to "FEED",
                                "workout_has_achievements" to post.achievements.isNotEmpty()
                            )
                        )
                        Timber.d("Workout copied successfully from post: ${post.id}")
                        // Success handled by navigation in UI layer
                    },
                    onFailure = { error ->
                        val liftrixError = if (error is LiftrixError) {
                            error
                        } else {
                            LiftrixError.NetworkError(
                                errorMessage = "Failed to copy workout from post: ${post.id}"
                            )
                        }
                        handleError(liftrixError)
                        Timber.e("Failed to copy workout from post: ${post.id}", error)
                    }
                )
            } else {
                Timber.e("Failed to get current user ID for copy workout action")
            }
        }
    }

    private fun refreshFeed() {
        viewModelScope.launch {
            val userId = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it?.value },
                onFailure = { null }
            )
            if (userId != null) {
                seedSampleData(userId)
                // Clear engagement state before refresh to ensure fresh state
                _likedPosts.value = emptySet()
                _savedPosts.value = emptySet()
                
                val result = feedPort.refreshFeed(userId)
                result.fold(
                    onSuccess = {
                        Timber.d("Feed refreshed successfully")
                        // The new posts will repopulate engagement state via the posts flow
                    },
                    onFailure = { error ->
                        val liftrixError = if (error is LiftrixError) {
                            error
                        } else {
                            LiftrixError.NetworkError(
                                errorMessage = "Failed to refresh feed"
                            )
                        }
                        handleError(liftrixError)
                        Timber.e("Failed to refresh feed", error)
                    }
                )
            } else {
                Timber.e("Failed to get current user ID for feed refresh")
            }
        }
    }
}

/**
 * UI state for feed screen
 */
@Stable
data class FeedUiState(
    val selectedTab: FeedTab = FeedTab.HOME,
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet(),
    val currentUserId: String? = null,
    val isLoading: Boolean = false,
    val error: LiftrixError? = null
)

/**
 * Events that can be triggered in the feed screen
 */
@Stable
sealed class FeedEvent {
    @Stable
    data class SelectTab(val tab: FeedTab) : FeedEvent()
    @Stable
    data class HandlePostInteraction(val interaction: PostInteraction) : FeedEvent()
    @Stable
    data object RefreshFeed : FeedEvent()
}

/**
 * Available feed tabs
 * HOME - Following tab: shows posts from people you follow and yourself
 * DISCOVERY - Explore tab: shows all public posts from the community
 */
enum class FeedTab {
    HOME,      // Following tab
    DISCOVERY  // Explore tab
}

/**
 * Types of post interactions
 */
@Stable
sealed class PostInteraction {
    @Stable
    data class Like(val postId: String) : PostInteraction()
    @Stable
    data class Save(val postId: String) : PostInteraction()
    @Stable
    data class Share(val post: WorkoutPost) : PostInteraction()
    @Stable
    data class CopyWorkout(val post: WorkoutPost) : PostInteraction()
}

/**
 * ViewModel events that trigger UI actions
 */
@Stable
sealed class FeedViewModelEvent {
    @Stable
    data class SharePost(val shareText: String, val postId: String) : FeedViewModelEvent()
}
