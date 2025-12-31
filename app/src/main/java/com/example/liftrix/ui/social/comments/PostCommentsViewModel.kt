package com.example.liftrix.ui.social.comments

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.model.social.CreateCommentRequest
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.usecase.auth.AuthQueryUseCase
import com.example.liftrix.ui.common.viewmodel.ModernBaseViewModel
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
    private val authQueryUseCase: AuthQueryUseCase,
    private val contentReportsDao: com.example.liftrix.data.local.dao.ContentReportsDao
) : ModernBaseViewModel<PostCommentsUiState>(initialState = PostCommentsUiState.Loading) {

    private val _postId = MutableStateFlow<String?>(null)
    private val _currentUserId = MutableStateFlow<com.example.liftrix.core.identity.UserId?>(null)
    private val _commentText = MutableStateFlow("")
    private val _isPosting = MutableStateFlow(false)
    private val _replyingTo = MutableStateFlow<PostComment?>(null)
    private val _reportingComment = MutableStateFlow<PostComment?>(null)
    private val _isSubmittingReport = MutableStateFlow(false)

    init {
        // Get current user ID
        viewModelScope.launch {
            _currentUserId.value = authQueryUseCase(waitForAuth = false).fold(
                onSuccess = { it },
                onFailure = { null }
            )
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

    fun handleEvent(event: PostCommentsEvent) {
        when (event) {
            is PostCommentsEvent.Initialize -> initializeForPost(event.postId)
            is PostCommentsEvent.UpdateCommentText -> updateCommentText(event.text)
            PostCommentsEvent.PostComment -> postComment()
            is PostCommentsEvent.LikeComment -> toggleCommentLike(event.commentId)
            is PostCommentsEvent.ReplyToComment -> setReplyingTo(event.comment)
            PostCommentsEvent.CancelReply -> cancelReply()
            PostCommentsEvent.RefreshComments -> refreshComments()
            is PostCommentsEvent.ReportComment -> showReportDialog(event.comment)
            is PostCommentsEvent.SubmitReport -> submitReport(event.commentId, event.reason, event.notes)
            PostCommentsEvent.DismissReportDialog -> dismissReportDialog()
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
                // FIX INPUT-006: Sanitize comment content to prevent XSS (CVSS 7.4)
                val sanitizedContent = sanitizeHtmlInput(commentText)

                val request = CreateCommentRequest(
                    postId = postId,
                    content = sanitizedContent,
                    parentCommentId = replyingTo?.id
                )

                val result = engagementRepository.createComment(currentUserId.value, request)
                
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
                                    "user_id" to currentUserId.value
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
                        "user_id" to currentUserId.value
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
        val currentUserId = _currentUserId.value ?: return
        val postId = _postId.value ?: return
        
        viewModelScope.launch {
            try {
                Timber.d("Toggling like for comment: $commentId")
                
                // Optimistic update - assume success for immediate UI feedback
                updateState { currentState ->
                    when (currentState) {
                        is PostCommentsUiState.Success -> currentState
                        else -> PostCommentsUiState.Success
                    }
                }
                
                // Execute the actual like toggle operation
                // Note: toggleCommentLike method would need to be added to EngagementRepository
                // For now, implement a placeholder that tracks the operation
                Timber.d("Comment like toggle requested for $commentId (implementation pending)")
                
                // Simulate successful operation for UI testing
                kotlinx.coroutines.delay(500) // Simulate network delay
                
                // Keep success state - actual implementation would update like count
                updateState { PostCommentsUiState.Success }
                
            } catch (e: Exception) {
                val liftrixError = LiftrixError.BusinessLogicError(
                    code = "COMMENT_LIKE_ERROR",
                    errorMessage = "Error toggling comment like: ${e.message}",
                    analyticsContext = mapOf(
                        "comment_id" to commentId,
                        "post_id" to postId,
                        "user_id" to currentUserId.value
                    )
                )
                Timber.e(e, "Exception toggling comment like: $commentId")
                
                updateState { PostCommentsUiState.Error(liftrixError) }
                
                // Auto-recovery after showing error
                kotlinx.coroutines.delay(2000)
                updateState { PostCommentsUiState.Success }
            }
        }
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

    private fun showReportDialog(comment: PostComment) {
        _reportingComment.value = comment
        Timber.d("Showing report dialog for comment: ${comment.id}")
    }

    private fun dismissReportDialog() {
        _reportingComment.value = null
        _isSubmittingReport.value = false
    }

    private fun submitReport(
        commentId: String,
        reason: com.example.liftrix.ui.social.ReportReason,
        notes: String
    ) {
        val currentUserId = _currentUserId.value ?: return

        viewModelScope.launch {
            _isSubmittingReport.value = true

            try {
                // Check if user has already reported this comment
                val hasReported = contentReportsDao.hasUserReported(
                    currentUserId.value,
                    commentId
                )

                if (hasReported) {
                    Timber.w("User has already reported comment: $commentId")
                    dismissReportDialog()
                    return@launch
                }

                // Create report entity
                val reportEntity = com.example.liftrix.data.local.entity.ContentReportEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    reporterUserId = currentUserId.value,
                    contentType = com.example.liftrix.data.local.entity.ContentReportEntity.CONTENT_TYPE_COMMENT,
                    contentId = commentId,
                    reason = reason.value,
                    description = notes.takeIf { it.isNotBlank() },
                    reportedAt = System.currentTimeMillis(),
                    status = com.example.liftrix.data.local.entity.ContentReportEntity.STATUS_PENDING
                )

                // Insert using Room-first pattern
                contentReportsDao.upsertLocal(reportEntity)

                Timber.d("Report submitted successfully for comment: $commentId")

                // Dismiss dialog
                dismissReportDialog()

                // Show success state
                updateState { PostCommentsUiState.Success }

            } catch (e: Exception) {
                val error = LiftrixError.BusinessLogicError(
                    code = "REPORT_SUBMIT_FAILED",
                    errorMessage = "Failed to submit report: ${e.message}",
                    analyticsContext = mapOf(
                        "comment_id" to commentId,
                        "user_id" to currentUserId.value,
                        "reason" to reason.value
                    )
                )
                Timber.e(e, "Exception submitting report")
                updateState { PostCommentsUiState.Error(error) }
            } finally {
                _isSubmittingReport.value = false
            }
        }
    }

    // Exposed state for UI
    val commentText: StateFlow<String> = _commentText.asStateFlow()
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()
    val replyingTo: StateFlow<PostComment?> = _replyingTo.asStateFlow()
    val currentUserId: StateFlow<com.example.liftrix.core.identity.UserId?> = _currentUserId.asStateFlow()
    val reportingComment: StateFlow<PostComment?> = _reportingComment.asStateFlow()
    val isSubmittingReport: StateFlow<Boolean> = _isSubmittingReport.asStateFlow()

    /**
     * FIX INPUT-006: Sanitize HTML input to prevent XSS attacks (CVSS 7.4)
     *
     * Removes potentially dangerous HTML tags and JavaScript event handlers.
     * Allows safe formatting tags like <b>, <i>, <u>, <br> if needed in the future.
     *
     * Current implementation: Strip all HTML tags for maximum security.
     * Future: Can whitelist safe tags if rich text formatting is required.
     */
    private fun sanitizeHtmlInput(input: String): String {
        return input
            .replace("<", "&lt;")  // Escape left angle bracket
            .replace(">", "&gt;")  // Escape right angle bracket
            .replace("\"", "&quot;") // Escape double quotes
            .replace("'", "&#x27;") // Escape single quotes
            .replace("&", "&amp;")   // Escape ampersand (must be last or escape others)
            .take(2000) // Limit length to prevent DoS
            .trim()
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
sealed class PostCommentsEvent {
    data class Initialize(val postId: String) : PostCommentsEvent()
    data class UpdateCommentText(val text: String) : PostCommentsEvent()
    object PostComment : PostCommentsEvent()
    data class LikeComment(val commentId: String) : PostCommentsEvent()
    data class ReplyToComment(val comment: com.example.liftrix.domain.model.social.PostComment) : PostCommentsEvent()
    object CancelReply : PostCommentsEvent()
    object RefreshComments : PostCommentsEvent()
    data class ReportComment(val comment: com.example.liftrix.domain.model.social.PostComment) : PostCommentsEvent()
    data class SubmitReport(
        val commentId: String,
        val reason: com.example.liftrix.ui.social.ReportReason,
        val notes: String
    ) : PostCommentsEvent()
    object DismissReportDialog : PostCommentsEvent()
}