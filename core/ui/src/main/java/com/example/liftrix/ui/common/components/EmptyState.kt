package com.example.liftrix.ui.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Standard empty state component
 */
@Composable
fun EmptyState(
    title: String = "",
    message: String = "",
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = Icons.Default.Info, contentDescription = null)
        Text(text = if (title.isNotBlank()) title else message, textAlign = TextAlign.Center)
        if (title.isNotBlank() && message.isNotBlank()) {
            Text(text = message, textAlign = TextAlign.Center)
        }
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionText)
            }
        }
    }
}
