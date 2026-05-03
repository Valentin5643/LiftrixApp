package com.example.liftrix.ui.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun TemplateBuddyShareScreen(
    templateId: String,
    onNavigateBack: () -> Unit,
    onOpenQrShareMode: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateBuddyShareViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(uiState.qrShareReady) {
        if (uiState.qrShareReady) onOpenQrShareMode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share with Gym Buddy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.createQrShare(templateId) },
                enabled = !uiState.isSharing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Open QR Share Mode")
            }

            Text(
                text = "Choose a connected gym buddy",
                style = MaterialTheme.typography.titleMedium
            )

            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
            uiState.successMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
            }

            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                uiState.buddies.isEmpty() -> {
                    Text("No gym buddies yet.")
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.buddies, key = { it.buddyId }) { buddy ->
                            ListItem(
                                headlineContent = { Text(buddy.getBuddyDisplayName()) },
                                supportingContent = { Text(buddy.buddyId) },
                                trailingContent = {
                                    Button(
                                        onClick = { viewModel.shareDirect(templateId, buddy.buddyId) },
                                        enabled = !uiState.isSharing
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Share, contentDescription = null)
                                            Text("Share")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.isSharing) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}
