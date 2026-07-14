package com.example.liftrix.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.chat.ChatConversation
import com.example.liftrix.ui.components.cards.LiftrixCard
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationHistoryPane(
    conversations: List<ChatConversation>,
    activeConversationId: String?,
    newConversationEnabled: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRenameConversation: (ChatConversation) -> Unit,
    onDeleteConversation: (ChatConversation) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Conversations", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Button(onClick = onNewConversation, enabled = newConversationEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("New chat")
        }
        if (conversations.isEmpty()) {
            Text("Your saved conversations will appear here.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations, key = ChatConversation::id) { conversation ->
                    val isSelected = conversation.id == activeConversationId
                    LiftrixCard(
                        onClick = { onOpenConversation(conversation.id) },
                        contentDescription = "${conversation.title}, ${conversation.messageCount} messages",
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().semantics { selected = isSelected }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(conversation.lastMessagePreview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conversation.lastUpdatedAt)), style = MaterialTheme.typography.labelSmall)
                            Row {
                                IconButton(onClick = { onRenameConversation(conversation) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename ${conversation.title}")
                                }
                                IconButton(onClick = { onDeleteConversation(conversation) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${conversation.title}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
