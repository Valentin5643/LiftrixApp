package com.example.liftrix.ui.social.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.common.components.LoadingIndicator
import com.example.liftrix.ui.common.components.EmptyState
import com.example.liftrix.ui.common.components.ErrorDisplay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen for displaying and managing post comments.
 * 
 * Features:
 * - Paginated comment loading
 * - Real-time comment posting
 * - Reply functionality
 * - Comment liking
 * - Proper error handling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCommentsScreen(
    postId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostCommentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    
    val comments = viewModel.comments.collectAsLazyPagingItems()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Initialize ViewModel with postId
    LaunchedEffect(postId) {
        viewModel.handleEvent(PostCommentsEvent.Initialize(postId))
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LiftrixColorsV2.surface)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Comments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LiftrixColorsV2.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = LiftrixColorsV2.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = LiftrixColorsV2.surface,
                titleContentColor = LiftrixColorsV2.onSurface
            )
        )
        
        // Content
        Box(
            modifier = Modifier.weight(1f)
        ) {
            when (uiState) {
                is PostCommentsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                
                is PostCommentsUiState.Error -> {
                    val errorState = uiState as PostCommentsUiState.Error
                    ErrorDisplay(
                        error = errorState.error,
                        onRetry = {
                            viewModel.handleEvent(PostCommentsEvent.RefreshComments)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                is PostCommentsUiState.Success -> {
                    CommentsContent(
                        comments = comments,
                        currentUserId = currentUserId,
                        onCommentLike = { commentId ->
                            viewModel.handleEvent(PostCommentsEvent.LikeComment(commentId))
                        },
                        onCommentReply = { comment ->
                            viewModel.handleEvent(PostCommentsEvent.ReplyToComment(comment))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        // Reply indicator
        replyingTo?.let { replyComment ->
            Surface(
                color = LiftrixColorsV2.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LiftrixSpacing.medium, vertical = LiftrixSpacing.small)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = null,
                        tint = LiftrixColorsV2.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(LiftrixSpacing.small))
                    
                    Text(
                        text = "Replying to ${replyComment.authorDisplayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColorsV2.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    IconButton(
                        onClick = {
                            viewModel.handleEvent(PostCommentsEvent.CancelReply)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = LiftrixColorsV2.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        
        // Comment input
        CommentInput(
            text = commentText,
            onTextChange = { text ->
                viewModel.handleEvent(PostCommentsEvent.UpdateCommentText(text))
            },
            isPosting = isPosting,
            onSubmit = {
                viewModel.handleEvent(PostCommentsEvent.PostComment)
                keyboardController?.hide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium)
        )
    }
}

@Composable
private fun CommentsContent(
    comments: LazyPagingItems<PostComment>,
    currentUserId: String?,
    onCommentLike: (String) -> Unit,
    onCommentReply: (PostComment) -> Unit,
    modifier: Modifier = Modifier
) {
    when (comments.loadState.refresh) {
        is LoadState.Loading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }
        
        is LoadState.Error -> {
            val error = comments.loadState.refresh as LoadState.Error
            EmptyState(
                message = "Error loading comments: ${error.error.message ?: "Please try again"}",
                modifier = modifier
            )
        }
        
        else -> {
            if (comments.itemCount == 0) {
                EmptyCommentsState(modifier = modifier)
            } else {
                LazyColumn(
                    modifier = modifier,
                    contentPadding = PaddingValues(LiftrixSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                ) {
                    items(
                        count = comments.itemCount,
                        key = comments.itemKey { it.id }
                    ) { index ->
                        val comment = comments[index]
                        if (comment != null) {
                            CommentItem(
                                comment = comment,
                                isOwnComment = comment.userId == currentUserId,
                                onLikeClick = { onCommentLike(comment.id) },
                                onReplyClick = { onCommentReply(comment) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // Loading indicator for pagination
                    when (comments.loadState.append) {
                        is LoadState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(LiftrixSpacing.medium),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = LiftrixColorsV2.primary
                                    )
                                }
                            }
                        }
                        is LoadState.Error -> {
                            // Handle append error if needed
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCommentsState(
    modifier: Modifier = Modifier
) {
    EmptyState(
        message = "No comments yet. Be the first to share your thoughts!",
        modifier = modifier
    )
}

@Composable
private fun CommentItem(
    comment: PostComment,
    isOwnComment: Boolean,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        // Profile image
        AsyncImage(
            model = comment.authorProfilePhotoUrl,
            contentDescription = "Profile picture of ${comment.authorDisplayName}",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LiftrixColorsV2.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(LiftrixSpacing.small))
        
        Column(modifier = Modifier.weight(1f)) {
            // Comment bubble
            Surface(
                color = LiftrixColorsV2.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(LiftrixSpacing.small)
                ) {
                    // Author name
                    Text(
                        text = comment.authorDisplayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LiftrixColorsV2.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Comment content
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LiftrixColorsV2.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Comment actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp
                Text(
                    text = formatCommentTimestamp(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = LiftrixColorsV2.onSurfaceVariant
                )
                
                // Like button
                Text(
                    text = if (comment.isLikedByViewer) "Unlike" else "Like",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comment.isLikedByViewer) LiftrixColorsV2.primary else LiftrixColorsV2.onSurfaceVariant,
                    fontWeight = if (comment.isLikedByViewer) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clickable { onLikeClick() }
                )
                
                // Reply button
                Text(
                    text = "Reply",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiftrixColorsV2.onSurfaceVariant,
                    modifier = Modifier.clickable { onReplyClick() }
                )
                
                // Like count
                if (comment.likeCount > 0) {
                    Text(
                        text = "${comment.likeCount} ${if (comment.likeCount == 1) "like" else "likes"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LiftrixColorsV2.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentInput(
    text: String,
    onTextChange: (String) -> Unit,
    isPosting: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LiftrixColorsV2.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.small),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "Add a comment...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSubmit() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LiftrixColorsV2.surface,
                    unfocusedContainerColor = LiftrixColorsV2.surface,
                    focusedBorderColor = LiftrixColorsV2.outline.copy(alpha = 0.5f),
                    unfocusedBorderColor = LiftrixColorsV2.outline.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                enabled = !isPosting
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            // Send button
            IconButton(
                onClick = onSubmit,
                enabled = text.isNotBlank() && !isPosting,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (text.isNotBlank() && !isPosting) LiftrixColorsV2.primary else LiftrixColorsV2.outline,
                    contentColor = if (text.isNotBlank() && !isPosting) LiftrixColorsV2.onPrimary else LiftrixColorsV2.onSurfaceVariant
                )
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LiftrixColorsV2.onSurfaceVariant
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send comment"
                    )
                }
            }
        }
    }
}

private fun formatCommentTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(Date(timestamp))
        }
    }
}