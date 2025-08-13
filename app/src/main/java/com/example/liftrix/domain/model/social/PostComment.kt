package com.example.liftrix.domain.model.social

/**
 * Domain model representing a comment on a workout post
 */
data class PostComment(
    val id: String,
    val postId: String,
    val userId: String,
    val content: String,
    val replyToCommentId: String? = null, // For nested replies (renamed from parentCommentId)
    
    // Engagement
    val likeCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val isLikedByViewer: Boolean = false, // Added for mapper compatibility
    val isEdited: Boolean = false,
    
    // Timestamps
    val createdAt: Long,
    val editedAt: Long? = null,
    val updatedAt: Long, // Added for mapper compatibility
    
    // Author information (denormalized for performance)
    val authorDisplayName: String = "",
    val authorUsername: String = "",
    val authorProfilePhotoUrl: String? = null, // Renamed from authorProfileImageUrl
    
    // Nested comment support
    val replies: List<PostComment> = emptyList(), // Added for mapper compatibility
    val hasMoreReplies: Boolean = false // Added for mapper compatibility
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
 * Model for post likes
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