package com.example.liftrix.ui.common.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Error display components for consistent error handling throughout the app.
 */


@Composable
fun ErrorDisplay(
    error: LiftrixError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (compact) "Error" else "Something went wrong",
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            if (onRetry != null && error.isRecoverable) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
            
            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
fun ErrorEmptyState(
    error: LiftrixError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        if (onRetry != null && error.isRecoverable) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}