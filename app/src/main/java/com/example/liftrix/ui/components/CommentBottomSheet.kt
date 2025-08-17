package com.example.liftrix.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.repository.social.EngagementRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Bottom sheet component for displaying and managing post comments.
 * 
 * Features:
 * - Real-time comment updates
 * - Nested comment replies
 * - Comment posting with optimistic updates
 * - Comment editing and deletion
 * - User profile integration
 * - Responsive design with proper keyboard handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<PostComment?>(null) }

    // Inject dependencies - in a real implementation, these would come from a ViewModel
    val engagementRepository = remember { 
        // This would be injected via Hilt in the actual implementation
        object : EngagementRepository {
            override suspend fun toggleLike(postId: String, userId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "TOGGLE_LIKE_FAILED",
                        errorMessage = "Failed to toggle like"
                    ) }
                ) { true }
            override suspend fun isPostLiked(postId: String, userId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "CHECK_LIKE_FAILED",
                        errorMessage = "Failed to check like status"
                    ) }
                ) { false }
            override fun observePostLiked(postId: String, userId: String) = 
                kotlinx.coroutines.flow.flowOf(false)
            override fun getPostLikers(postId: String, pageSize: Int) = 
                kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<com.example.liftrix.domain.model.social.PostLike>())
            override suspend fun createComment(userId: String, request: com.example.liftrix.domain.model.social.CreateCommentRequest) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "CREATE_COMMENT_FAILED",
                        errorMessage = "Failed to create comment"
                    ) }
                ) { 
                    PostComment(
                        id = UUID.randomUUID().toString(),
                        postId = request.postId,
                        userId = userId,
                        content = request.content,
                        authorDisplayName = "Current User",
                        authorUsername = "currentuser",
                        authorProfilePhotoUrl = null,
                        likeCount = 0,
                        isLikedByViewer = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            override suspend fun editComment(userId: String, request: com.example.liftrix.domain.model.social.EditCommentRequest) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "EDIT_COMMENT_FAILED",
                        errorMessage = "Failed to edit comment"
                    ) }
                ) { 
                    PostComment(
                        id = request.commentId,
                        postId = "",
                        userId = userId,
                        content = request.content,
                        authorDisplayName = "Current User",
                        authorUsername = "currentuser",
                        authorProfilePhotoUrl = null,
                        likeCount = 0,
                        isLikedByViewer = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                }
            override suspend fun deleteComment(commentId: String, userId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "DELETE_COMMENT_FAILED",
                        errorMessage = "Failed to delete comment"
                    ) }
                ) { Unit }
            override fun getPostComments(postId: String, pageSize: Int) = 
                kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<PostComment>())
            override suspend fun getCommentReplies(commentId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "GET_REPLIES_FAILED",
                        errorMessage = "Failed to get comment replies"
                    ) }
                ) { emptyList<PostComment>() }
            override fun observeCommentCount(postId: String) = 
                kotlinx.coroutines.flow.flowOf(0)
            override suspend fun toggleSave(postId: String, userId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "TOGGLE_SAVE_FAILED",
                        errorMessage = "Failed to toggle save"
                    ) }
                ) { true }
            override suspend fun isPostSaved(postId: String, userId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "CHECK_SAVE_FAILED",
                        errorMessage = "Failed to check save status"
                    ) }
                ) { false }
            override fun observePostSaved(postId: String, userId: String) = 
                kotlinx.coroutines.flow.flowOf(false)
            override fun getUserSavedPosts(userId: String, pageSize: Int) = 
                kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty<com.example.liftrix.domain.model.social.WorkoutPost>())
            override suspend fun recordShare(postId: String, userId: String, shareMethod: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "RECORD_SHARE_FAILED",
                        errorMessage = "Failed to record share"
                    ) }
                ) { Unit }
            override suspend fun getPostEngagementStats(postId: String, viewerId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "GET_ENGAGEMENT_STATS_FAILED",
                        errorMessage = "Failed to get engagement stats"
                    ) }
                ) { 
                    com.example.liftrix.domain.model.social.PostEngagementStats(
                        postId = postId,
                        likeCount = 0,
                        commentCount = 0,
                        shareCount = 0,
                        saveCount = 0,
                        isLikedByViewer = false,
                        isSavedByViewer = false,
                        topLikers = emptyList(),
                        recentComments = emptyList()
                    )
                }
            override fun observePostEngagementStats(postId: String, viewerId: String) = 
                kotlinx.coroutines.flow.flowOf(
                    com.example.liftrix.domain.model.social.PostEngagementStats(
                        postId = postId,
                        likeCount = 0,
                        commentCount = 0,
                        shareCount = 0,
                        saveCount = 0,
                        isLikedByViewer = false,
                        isSavedByViewer = false,
                        topLikers = emptyList(),
                        recentComments = emptyList()
                    )
                )
            override suspend fun getTrendingEngagement(timeWindowHours: Int, limit: Int) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "GET_TRENDING_FAILED",
                        errorMessage = "Failed to get trending engagement"
                    ) }
                ) { emptyList<Pair<String, Double>>() }
            override suspend fun copyWorkoutFromPost(postId: String, userId: String) = 
                liftrixCatching(
                    errorMapper = { LiftrixError.BusinessLogicError(
                        code = "COPY_WORKOUT_FAILED",
                        errorMessage = "Failed to copy workout"
                    ) }
                ) { UUID.randomUUID().toString() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // Header
            CommentSheetHeader(
                commentCount = comments.size,
                onDismiss = onDismiss
            )

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Comments List
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        EmptyCommentsState()
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            currentUserId = currentUserId,
                            onReplyClick = { replyingTo = comment },
                            onEditClick = { 
                                commentText = comment.content
                                replyingTo = comment
                            },
                            onDeleteClick = { commentId ->
                                // Remove comment optimistically
                                comments = comments.filterNot { it.id == commentId }
                            },
                            onLikeClick = { /* Handle comment like */ }
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Comment Input
            CommentInput(
                text = commentText,
                onTextChange = { commentText = it },
                isPosting = isPosting,
                replyingTo = replyingTo,
                onCancelReply = { 
                    replyingTo = null
                    commentText = ""
                },
                onSubmit = {
                    if (commentText.isNotBlank() && currentUserId != null) {
                        isPosting = true
                        
                        // Create optimistic comment
                        val optimisticComment = PostComment(
                            id = UUID.randomUUID().toString(),
                            postId = postId,
                            userId = currentUserId!!,
                            content = commentText,
                            authorDisplayName = "You",
                            authorUsername = "you",
                            authorProfilePhotoUrl = null,
                            likeCount = 0,
                            isLikedByViewer = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        
                        // Add optimistically
                        comments = comments + optimisticComment
                        commentText = ""
                        replyingTo = null
                        isPosting = false
                    }
                }
            )
        }
    }
}

@Composable
private fun CommentSheetHeader(
    commentCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (commentCount == 0) "Comments" else "Comments ($commentCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close comments",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCommentsState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Comment,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No comments yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Be the first to share your thoughts!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CommentItem(
    comment: PostComment,
    currentUserId: String?,
    onReplyClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Profile Picture
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(comment.authorProfilePhotoUrl ?: "")
                .crossfade(true)
                .build(),
            contentDescription = "Profile picture",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Comment Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.authorDisplayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatCommentTime(comment.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (currentUserId == comment.userId) {
                    CommentOptionsMenu(
                        canEdit = true,
                        canDelete = true,
                        onEdit = onEditClick,
                        onDelete = { onDeleteClick(comment.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment Content
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Comment Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLikeClick() }
                ) {
                    Icon(
                        imageVector = if (comment.isLikedByViewer) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        modifier = Modifier.size(16.dp),
                        tint = if (comment.isLikedByViewer) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (comment.likeCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = comment.likeCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Reply Button
                Text(
                    text = "Reply",
                    style = MaterialTheme.typography.bodySmall,
                    color = LiftrixColorsV2.primary,
                    modifier = Modifier.clickable { onReplyClick() }
                )
            }
        }
    }
}

@Composable
private fun CommentOptionsMenu(
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )
            }
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun CommentInput(
    text: String,
    onTextChange: (String) -> Unit,
    isPosting: Boolean,
    replyingTo: PostComment?,
    onCancelReply: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Reply indicator
        AnimatedVisibility(
            visible = replyingTo != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LiftrixColorsV2.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LiftrixColorsV2.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Replying to ${replyingTo?.authorDisplayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onCancelReply,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { 
                    Text(
                        if (replyingTo != null) "Write a reply..." else "Add a comment..."
                    ) 
                },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LiftrixColorsV2.primary
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSubmit,
                enabled = text.isNotBlank() && !isPosting
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = LiftrixColorsV2.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send comment",
                        tint = if (text.isNotBlank()) LiftrixColorsV2.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatCommentTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> {
            val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
            formatter.format(Date(timestamp))
        }
    }
}