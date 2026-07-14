package com.example.liftrix.ui.chat.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.liftrix.domain.model.chat.ChatConversation

@Composable
fun RenameConversationDialog(conversation: ChatConversation, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember(conversation.id) { mutableStateOf(conversation.title) }
    val error = when { title.isBlank() -> "Title is required"; title.length > 60 -> "Use 60 characters or fewer"; else -> null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, isError = error != null, supportingText = { error?.let { Text(it) } }) },
        confirmButton = { TextButton(onClick = { onConfirm(title.trim()) }, enabled = error == null) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DeleteConversationDialog(conversation: ChatConversation, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete conversation?") },
        text = { Text("${conversation.title} and its saved messages will be removed from this device.") },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
