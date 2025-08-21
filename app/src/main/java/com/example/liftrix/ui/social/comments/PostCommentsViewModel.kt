package com.example.liftrix.ui.social.comments

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.model.social.CreateCommentRequest
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.GetCurrentUserIdUseCase
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.viewmodel.BaseViewModel
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.ui.common.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the PostComments screen.
 * Manages comment loading, posting, and engagement interactions.
 */
@HiltViewModel
class PostCommentsViewModel @Inject constructor(
    private val engagementRepository: EngagementRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<PostCommentsUiState, PostCommentsEvent>(errorHandler) {

    private val _postId = MutableStateFlow<String?>(null)
    private val _currentUserId = MutableStateFlow<String?>(null)
    private val _commentText = MutableStateFlow("")
    private val _isPosting = MutableStateFlow(false)
    private val _replyingTo = MutableStateFlow<PostComment?>(null)

    override val _uiState = MutableStateFlow<PostCommentsUiState>(
        PostCommentsUiState.Loading
    )

    init {
        // Get current user ID
        viewModelScope.launch {
            _currentUserId.value = getCurrentUserIdUseCase()
        }
    }

    /**
     * Flow of paginated comments for the current post
     */
    val comments: Flow<PagingData<PostComment>> = _postId
        .filterNotNull()
        .flatMapLatest { postId ->
            engagementRepository.getPostComments(postId, pageSize = 20)
        }
        .cachedIn(viewModelScope)

    override fun handleEvent(event: PostCommentsEvent) {
        when (event) {
            is PostCommentsEvent.Initialize -> initializeForPost(event.postId)
            is PostCommentsEvent.UpdateCommentText -> updateCommentText(event.text)
            PostCommentsEvent.PostComment -> postComment()
            is PostCommentsEvent.LikeComment -> toggleCommentLike(event.commentId)
            is PostCommentsEvent.ReplyToComment -> setReplyingTo(event.comment)
            PostCommentsEvent.CancelReply -> cancelReply()
            PostCommentsEvent.RefreshComments -> refreshComments()
        }
    }

    private fun initializeForPost(postId: String) {
        Timber.d("Initializing PostComments for post: $postId")
        _postId.value = postId
        updateState { PostCommentsUiState.Success }
    }

    private fun updateCommentText(text: String) {
        _commentText.value = text
    }

    private fun postComment() {
        val postId = _postId.value ?: return
        val currentUserId = _currentUserId.value ?: return
        val commentText = _commentText.value.trim()
        val replyingTo = _replyingTo.value

        if (commentText.isBlank()) return

        viewModelScope.launch {
            _isPosting.value = true
            
            try {
                val request = CreateCommentRequest(
                    postId = postId,
                    content = commentText,
                    parentCommentId = replyingTo?.id
                )

                val result = engagementRepository.createComment(currentUserId, request)
                
                result.fold(
                    onSuccess = { comment ->
                        Timber.d("Comment posted successfully: ${comment.id}")
                        
                        // Clear input and reply state
                        _commentText.value = ""
                        _replyingTo.value = null
                        
                        // Show success state briefly
                        updateState { PostCommentsUiState.Success }
                    },
                    onFailure = { throwable ->
                        val error = when (throwable) {
                            is LiftrixError -> throwable
                            else -> LiftrixError.BusinessLogicError(
                                code = "COMMENT_POST_FAILED",
                                errorMessage = "Failed to post comment: ${throwable.message}",
                                analyticsContext = mapOf(
                                    "post_id" to postId,
                                    "user_id" to currentUserId
                                )
                            )
                        }
                        Timber.e("Failed to post comment: $error")
                        updateState { PostCommentsUiState.Error(error) }
                    }
                )
            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "COMMENT_POST_FAILED",
                    errorMessage = "Failed to post comment: ${e.message}",
                    analyticsContext = mapOf(
                        "post_id" to postId,
                        "user_id" to currentUserId
                    )
                )
                Timber.e(e, "Exception posting comment")
                updateState { PostCommentsUiState.Error(error) }
            } finally {
                _isPosting.value = false
            }
        }
    }

    private fun toggleCommentLike(commentId: String) {
        // TODO: Implement comment liking functionality
        // This would require adding a toggleCommentLike method to EngagementRepository
        Timber.d("Comment like toggled for comment: $commentId")
    }

    private fun setReplyingTo(comment: PostComment) {
        _replyingTo.value = comment
        Timber.d("Replying to comment: ${comment.id}")
    }

    private fun cancelReply() {
        _replyingTo.value = null
        _commentText.value = ""
    }

    private fun refreshComments() {
        // Comments are automatically refreshed through paging
        updateState { PostCommentsUiState.Success }
    }

    // Exposed state for UI
    val commentText: StateFlow<String> = _commentText.asStateFlow()
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()
    val replyingTo: StateFlow<PostComment?> = _replyingTo.asStateFlow()
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    override fun setLoadingState() {
        updateState { PostCommentsUiState.Loading }
    }
}

/**
 * UI state for the PostComments screen
 */
sealed class PostCommentsUiState {
    object Loading : PostCommentsUiState()
    object Success : PostCommentsUiState()
    data class Error(val error: LiftrixError) : PostCommentsUiState()
}

/**
 * Events for the PostComments screen
 */
sealed class PostCommentsEvent : ViewModelEvent {
    data class Initialize(val postId: String) : PostCommentsEvent()
    data class UpdateCommentText(val text: String) : PostCommentsEvent()
    object PostComment : PostCommentsEvent()
    data class LikeComment(val commentId: String) : PostCommentsEvent()
    data class ReplyToComment(val comment: com.example.liftrix.domain.model.social.PostComment) : PostCommentsEvent()
    object CancelReply : PostCommentsEvent()
    object RefreshComments : PostCommentsEvent()
}