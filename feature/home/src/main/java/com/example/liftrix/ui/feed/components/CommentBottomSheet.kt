package com.example.liftrix.ui.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.liftrix.feature.home.R
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.common.components.LoadingIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Bottom sheet component for displaying and creating nested comments
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    postId: String,
    comments: List<PostComment>,
    currentUserProfileImageUrl: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCommentSubmit: (String, String?) -> Unit, // content, parentCommentId
    onCommentLike: (String) -> Unit,
    onCommentReply: (PostComment) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<PostComment?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(Unit) {
        sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = LiftrixSpacing.small),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(2.dp)
            ) {
                Box(
                    modifier = Modifier.size(
                        width = 32.dp,
                        height = 4.dp
                    )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = LiftrixSpacing.medium)
        ) {
            // Header
            CommentSheetHeader(
                commentCount = comments.size,
                replyingTo = replyingTo,
                onClearReply = { replyingTo = null }
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
            
            // Comments list
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading && comments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                } else if (comments.isEmpty()) {
                    EmptyCommentsState(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium),
                        contentPadding = PaddingValues(bottom = LiftrixSpacing.medium)
                    ) {
                        items(
                            items = comments.filter { it.replyToCommentId == null },
                            key = { it.id }
                        ) { comment ->
                            CommentItem(
                                comment = comment,
                                replies = comments.filter { it.replyToCommentId == comment.id },
                                onLikeClick = { onCommentLike(comment.id) },
                                onReplyClick = { 
                                    replyingTo = comment
                                    onCommentReply(comment)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            // Comment input
            CommentInput(
                text = commentText,
                onTextChange = { commentText = it },
                replyingTo = replyingTo,
                currentUserProfileImageUrl = currentUserProfileImageUrl,
                onSubmit = {
                    if (commentText.isNotBlank()) {
                        onCommentSubmit(commentText.trim(), replyingTo?.id)
                        commentText = ""
                        replyingTo = null
                        keyboardController?.hide()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        }
    }
}

@Composable
private fun CommentSheetHeader(
    commentCount: Int,
    replyingTo: PostComment?,
    onClearReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.comments_title, commentCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        
        if (replyingTo != null) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LiftrixSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.replying_to,
                            replyingTo.authorDisplayName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(
                        onClick = onClearReply,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear reply",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Comments",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
        
        Text(
            text = stringResource(R.string.comments_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        Text(
            text = stringResource(R.string.comments_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CommentItem(
    comment: PostComment,
    replies: List<PostComment>,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Main comment
        CommentContent(
            comment = comment,
            onLikeClick = onLikeClick,
            onReplyClick = onReplyClick,
            isReply = false
        )
        
        // Replies
        if (replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            Column(
                modifier = Modifier.padding(start = 48.dp)
            ) {
                replies.forEach { reply ->
                    CommentContent(
                        comment = reply,
                        onLikeClick = { /* Handle reply like */ },
                        onReplyClick = { /* Handle reply to reply */ },
                        isReply = true
                    )
                    
                    if (reply != replies.last()) {
                        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentContent(
    comment: PostComment,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    isReply: Boolean,
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
                .size(if (isReply) 32.dp else 40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(LiftrixSpacing.small))
        
        Column(modifier = Modifier.weight(1f)) {
            // Author and content
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(LiftrixSpacing.small)
                ) {
                    Text(
                        text = comment.authorDisplayName,
                        style = if (isReply) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = comment.content,
                        style = if (isReply) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatCommentTimestamp(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Like",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (comment.isLikedByCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (comment.isLikedByCurrentUser) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clickable { onLikeClick() }
                )
                
                if (!isReply) {
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onReplyClick() }
                    )
                }
                
                if (comment.likeCount > 0) {
                    Text(
                        text = "${comment.likeCount} ${if (comment.likeCount == 1) "like" else "likes"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    replyingTo: PostComment?,
    currentUserProfileImageUrl: String?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.small),
            verticalAlignment = Alignment.Bottom
        ) {
            // Current user profile image
            AsyncImage(
                model = currentUserProfileImageUrl,
                contentDescription = "Your profile picture",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = if (replyingTo != null) {
                            stringResource(R.string.comment_reply_placeholder)
                        } else {
                            stringResource(R.string.comment_placeholder)
                        },
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
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.small))
            
            // Send button
            IconButton(
                onClick = onSubmit,
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send comment"
                )
            }
        }
    }
}

private fun formatCommentTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 604800_000 -> "${diff / 86400_000}d"
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(Date(timestamp))
        }
    }
}
