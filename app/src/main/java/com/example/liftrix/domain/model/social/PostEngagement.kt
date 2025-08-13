package com.example.liftrix.domain.model.social

/**
 * Domain models for post engagement (likes, comments, shares, saves).
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */

/**
 * Represents a like on a workout post
 */
data class PostLike(
    val id: String,
    val postId: String,
    val userId: String,
    val createdAt: Long,
    
    // User information (denormalized)
    val userDisplayName: String = "",
    val userUsername: String = "",
    val userProfileImageUrl: String? = null
)

/**
 * Represents a comment on a workout post
 */
data class PostComment(
    val id: String,
    val postId: String,
    val userId: String,
    val content: String,
    val replyToCommentId: String? = null, // For nested replies
    
    // Engagement
    val likeCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val isLikedByViewer: Boolean = false,
    val isEdited: Boolean = false,
    
    // Timestamps
    val createdAt: Long,
    val editedAt: Long? = null,
    val updatedAt: Long = createdAt, // For mapper compatibility
    
    // Author information (denormalized for performance)
    val authorDisplayName: String = "",
    val authorUsername: String = "",
    val authorProfilePhotoUrl: String? = null,
    
    // Nested comment support
    val replies: List<PostComment> = emptyList(),
    val hasMoreReplies: Boolean = false
)

/**
 * Represents a saved workout post
 */
data class SavedPost(
    val id: String,
    val userId: String,
    val postId: String,
    val savedAt: Long
)

/**
 * Request for creating a new comment
 */
data class CreateCommentRequest(
    val postId: String,
    val content: String,
    val parentCommentId: String? = null
)

/**
 * Request for editing an existing comment
 */
data class EditCommentRequest(
    val commentId: String,
    val content: String
)

/**
 * Engagement statistics for a post
 */
data class PostEngagementStats(
    val postId: String,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val saveCount: Int = 0,
    val isLikedByViewer: Boolean = false,
    val isSavedByViewer: Boolean = false,
    val topLikers: List<String> = emptyList(), // Display names of top likers
    val recentComments: List<PostComment> = emptyList()
)

/**
 * Engagement action types for analytics
 */
enum class EngagementAction {
    LIKE,
    UNLIKE,
    COMMENT,
    EDIT_COMMENT,
    DELETE_COMMENT,
    SAVE,
    UNSAVE,
    SHARE
}

/**
 * Result of an engagement action with optimistic update info
 */
data class EngagementResult(
    val action: EngagementAction,
    val success: Boolean,
    val newCount: Int? = null,
    val errorMessage: String? = null
)