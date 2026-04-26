package com.example.liftrix.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.ui.components.buttons.LiftrixButton
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/**
 * Screen for displaying QR codes for in-app friend pairing
 * 
 * Generates and displays QR codes containing app-native payloads with sharing capabilities.
 * Includes options to save, share, and customize QR code appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRCodeDisplayScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QRCodeDisplayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        viewModel.handleEvent(QRCodeDisplayEvent.GenerateQRCode(userId))
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top app bar
        QRCodeTopBar(
            onNavigateBack = onNavigateBack,
            onShareClick = {
                uiState.qrCodeBitmap?.let { bitmap ->
                    viewModel.handleEvent(QRCodeDisplayEvent.ShareQRCode(bitmap))
                }
            },
            onSaveClick = {
                uiState.qrCodeBitmap?.let { bitmap ->
                    viewModel.handleEvent(QRCodeDisplayEvent.SaveQRCode(bitmap))
                }
            },
            canShare = uiState.qrCodeBitmap != null,
            modifier = Modifier.fillMaxWidth()
        )

        // QR Code content
        when {
            uiState.isLoading -> {
                QRCodeLoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
            
            uiState.error != null -> {
                val error = uiState.error
                QRCodeErrorState(
                    error = error!!,
                    onRetry = { viewModel.handleEvent(QRCodeDisplayEvent.RetryGeneration) },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
            
            uiState.qrCodeBitmap != null -> {
                val qrCodeBitmap = uiState.qrCodeBitmap
                QRCodeContent(
                    qrCodeBitmap = qrCodeBitmap!!,
                    profileUrl = uiState.profileUrl,
                    onRefreshClick = { 
                        viewModel.handleEvent(QRCodeDisplayEvent.RefreshQRCode)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Top app bar with navigation and action buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRCodeTopBar(
    onNavigateBack: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    canShare: Boolean,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text("QR Code") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Go back"
                )
            }
        },
        actions = {
            if (canShare) {
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save QR code"
                    )
                }
                
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share QR code"
                    )
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Main QR code display content
 */
@Composable
private fun QRCodeContent(
    qrCodeBitmap: Bitmap,
    profileUrl: String?,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Add Friend",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Others can scan this QR code in Liftrix to connect with you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        
        // QR Code display
        QRCodeDisplay(
            qrCodeBitmap = qrCodeBitmap,
            modifier = Modifier.weight(1f, fill = false)
        )
        
        // Profile URL display
        if (!profileUrl.isNullOrBlank()) {
            ProfileUrlCard(
                url = profileUrl,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Action buttons
        QRCodeActions(
            onRefreshClick = onRefreshClick,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Instructions
        InstructionsCard(
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * QR code image display with styling
 */
@Composable
private fun QRCodeDisplay(
    qrCodeBitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier
            .semantics {
                contentDescription = "QR code for friend pairing"
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = qrCodeBitmap.asImageBitmap(),
                contentDescription = "Friend QR code",
                modifier = Modifier
                    .size(250.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

/**
 * Profile URL display card
 */
@Composable
private fun ProfileUrlCard(
    url: String,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "In-app code",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            )
        }
    }
}

/**
 * Action buttons for QR code operations
 */
@Composable
private fun QRCodeActions(
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onRefreshClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh")
        }
    }
}

/**
 * Instructions card
 */
@Composable
private fun InstructionsCard(
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "How to use",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstructionItem(
                    number = "1",
                    text = "Share this QR code with another Liftrix user"
                )
                
                InstructionItem(
                    number = "2",
                    text = "They can scan it with the Liftrix scanner"
                )
                
                InstructionItem(
                    number = "3",
                    text = "The app connects you as gym buddies after a successful scan"
                )
            }
        }
    }
}

/**
 * Individual instruction item
 */
@Composable
private fun InstructionItem(
    number: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Loading state
 */
@Composable
private fun QRCodeLoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            
            Text(
                text = "Generating QR code...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state
 */
@Composable
private fun QRCodeErrorState(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Failed to generate QR code",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryActionButton(
            text = "Try Again",
            onClick = onRetry
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QRCodeDisplayScreenPreview() {
    LiftrixTheme {
        QRCodeDisplayScreen(
            userId = "1",
            onNavigateBack = { }
        )
    }
}
