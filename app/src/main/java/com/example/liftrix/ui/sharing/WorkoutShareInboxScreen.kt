package com.example.liftrix.ui.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutShareInboxScreen(
    senderId: String,
    onNavigateBack: () -> Unit,
    onOpenShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutShareInboxViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(senderId) {
        viewModel.load(senderId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Workouts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp)
                )
            }
            uiState.shares.isEmpty() -> {
                Text(
                    text = "No pending shared workouts.",
                    modifier = Modifier.padding(padding).padding(16.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.shares, key = { it.id }) { share ->
                        ListItem(
                            headlineContent = { Text("Shared workout") },
                            supportingContent = { Text("Tap to preview and save") },
                            modifier = Modifier.padding(vertical = 4.dp),
                            trailingContent = {
                                TextButtonLike(onClick = { onOpenShare(share.id) })
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonLike(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text("Open")
    }
}

