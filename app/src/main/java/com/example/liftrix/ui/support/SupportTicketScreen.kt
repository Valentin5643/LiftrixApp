package com.example.liftrix.ui.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.settings.support.SupportViewModel
import com.example.liftrix.ui.settings.support.SupportUiState
import java.time.format.DateTimeFormatter

/**
 * Screen for viewing support ticket details and conversation history
 * 
 * Features:
 * - Ticket details and status display
 * - Conversation thread with messages
 * - Reply functionality for ongoing conversations
 * - File attachment viewing
 * - Status update notifications
 * - Real-time message updates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTicketScreen(
    ticketId: String,
    onNavigateBack: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var replyText by remember { mutableStateOf("") }
    
    LaunchedEffect(ticketId) {
        viewModel.handleEvent(SupportEvent.ViewTicket(ticketId))
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Support Ticket") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        val currentState = uiState
        when (currentState) {
            is SupportUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is SupportUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Failed to load ticket",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = currentState.error.message ?: "Unknown error occurred",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            viewModel.handleEvent(SupportEvent.ViewTicket(ticketId))
                        }
                    ) {
                        Text("Retry")
                    }
                }
            }
            
            is SupportUiState.Success -> {
                val ticket = currentState.data.userTickets.find { it.id == ticketId }
                
                if (ticket != null) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Ticket header
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ticket #${ticket.id}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    SupportStatusChip(status = ticket.status)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = ticket.subject,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "Category: ${ticket.category}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = "Created: ${ticket.createdAt.toString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Messages list
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                // Initial message
                                MessageCard(
                                    message = ticket.description,
                                    isFromSupport = false,
                                    timestamp = ticket.createdAt,
                                    attachments = emptyList()
                                )
                            }
                            
                            // Display conversation messages chronologically
                            items(ticket.getMessagesChronologically()) { message ->
                                MessageCard(
                                    message = message.content,
                                    isFromSupport = message.isFromSupport,
                                    timestamp = message.createdAt,
                                    attachments = message.attachments,
                                    authorName = message.getAuthorDisplayName(),
                                    isEdited = message.isEdited()
                                )
                            }
                        }
                        
                        // Reply section (only if ticket is open)
                        if (ticket.status == com.example.liftrix.domain.model.support.SupportStatus.OPEN ||
                            ticket.status == com.example.liftrix.domain.model.support.SupportStatus.IN_PROGRESS) {
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    OutlinedTextField(
                                        value = replyText,
                                        onValueChange = { replyText = it },
                                        label = { Text("Add a reply") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3,
                                        maxLines = 6
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                if (replyText.isNotBlank()) {
                                                    viewModel.handleEvent(
                                                        SupportEvent.AddReply(ticketId, replyText)
                                                    )
                                                    replyText = ""
                                                }
                                            },
                                            enabled = replyText.isNotBlank() && !currentState.data.isSubmitting
                                        ) {
                                            if (currentState.data.isSubmitting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Send,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (currentState.data.isSubmitting) "Sending..." else "Send Reply")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Ticket not found
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Ticket not found",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "The requested support ticket could not be found.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
            
            is SupportUiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No tickets available",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportStatusChip(
    status: com.example.liftrix.domain.model.support.SupportStatus
) {
    val (backgroundColor, contentColor, text) = when (status) {
        com.example.liftrix.domain.model.support.SupportStatus.OPEN -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            "Open"
        )
        com.example.liftrix.domain.model.support.SupportStatus.IN_PROGRESS -> Triple(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary,
            "In Progress"
        )
        com.example.liftrix.domain.model.support.SupportStatus.CLOSED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Closed"
        )
        com.example.liftrix.domain.model.support.SupportStatus.RESOLVED -> Triple(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary,
            "Resolved"
        )
        com.example.liftrix.domain.model.support.SupportStatus.WAITING_FOR_USER -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Waiting for User"
        )
    }
    
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MessageCard(
    message: String,
    isFromSupport: Boolean,
    timestamp: java.time.Instant,
    attachments: List<String> = emptyList(),
    authorName: String? = null,
    isEdited: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isFromSupport) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFromSupport) (authorName ?: "Support Team") else "You",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isFromSupport) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    if (isEdited) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(edited)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFromSupport) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                
                Text(
                    text = formatTimestamp(timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFromSupport) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFromSupport) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            // Show attachments if any
            if (attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Attachments:",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isFromSupport) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                attachments.forEach { attachment ->
                    Text(
                        text = "📎 $attachment",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFromSupport) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }
    }
}

/**
 * Formats a timestamp for display in the message card
 */
private fun formatTimestamp(timestamp: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
    return timestamp.atZone(java.time.ZoneId.systemDefault()).format(formatter)
}