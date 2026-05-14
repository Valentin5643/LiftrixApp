package com.example.liftrix.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType

/**
 * Message bubble component for displaying chat messages.
 * 
 * Adapts appearance based on message type (user vs AI) with:
 * - Distinct colors and positioning for user and AI messages
 * - Rounded corner styling with appropriate bubble tails
 * - Timestamp display
 * - Responsive width constraints for readability
 * 
 * @param message The chat message to display
 * @param modifier Modifier for the component
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.type == MessageType.USER
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUserMessage) 
                LiftrixColorsV2.primary 
            else 
                LiftrixColorsV2.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUserMessage) 16.dp else 4.dp,
                bottomEnd = if (isUserMessage) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.cardPadding)
            ) {
                MessageContent(
                    content = message.content,
                    isUserMessage = isUserMessage
                )
                
                MessageMetadata(
                    timestamp = message.createdAt,
                    processingTime = message.processingTimeMs,
                    isUserMessage = isUserMessage
                )
            }
        }
    }
}

@Composable
private fun MessageContent(
    content: String,
    isUserMessage: Boolean
) {
    Text(
        text = content,
        color = if (isUserMessage) 
            LiftrixColorsV2.onPrimary 
        else 
            LiftrixColorsV2.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun MessageMetadata(
    timestamp: Long,
    processingTime: Long?,
    isUserMessage: Boolean
) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatTimestamp(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isUserMessage)
                LiftrixColorsV2.onPrimary.copy(alpha = 0.7f)
            else
                LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        processingTime?.let { time ->
            if (!isUserMessage && time > 1000) {
                Text(
                    text = "${time}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * System message bubble for notifications and errors.
 */
@Composable
fun SystemMessageBubble(
    message: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = if (isError) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message,
                color = if (isError)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(
                    horizontal = LiftrixSpacing.medium,
                    vertical = LiftrixSpacing.small
                )
            )
        }
    }
}

/**
 * Helper function to format timestamp for message display.
 */
private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
