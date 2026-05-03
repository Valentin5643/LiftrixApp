package com.example.liftrix.ui.social.gymbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.social.GymBuddy
import com.example.liftrix.domain.model.social.QRCodeData
import com.example.liftrix.domain.model.social.QRUserProfile
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Gym buddy screen with QR code pairing functionality
 * Shows list of current gym buddies (max 5) with QR actions for adding new buddies
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymBuddyScreen(
    onNavigateToQrScanner: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GymBuddyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showQrSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.handleEvent(GymBuddyEvent.GenerateQrCode)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header section with QR actions
        GymBuddyHeader(
            buddyCount = uiState.gymBuddies.size,
            maxBuddies = 5,
            canAddMore = uiState.canAddMoreBuddies,
            qrCode = uiState.qrCode,
            isGeneratingQr = uiState.isGeneratingQr,
            onShowQr = { 
                showQrSheet = true
                viewModel.handleEvent(GymBuddyEvent.GenerateQrCode)
            },
            onScanQr = { onNavigateToQrScanner() },
            onRegenerateQr = {
                viewModel.handleEvent(GymBuddyEvent.RegenerateQrCode)
            },
            modifier = Modifier.padding(16.dp)
        )

        // Divider
        HorizontalDivider()

        // Gym buddies list
        GymBuddiesList(
            gymBuddies = uiState.gymBuddies,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onEvent = viewModel::handleEvent,
            modifier = Modifier.fillMaxSize()
        )
    }

    // QR display bottom sheet
    if (showQrSheet) {
        QRDisplayBottomSheet(
            qrCode = uiState.qrCode,
            userProfile = uiState.userProfile,
            isGenerating = uiState.isGeneratingQr,
            onDismiss = { 
                showQrSheet = false
                viewModel.handleEvent(GymBuddyEvent.ClearQrCode)
            },
            onRegenerate = {
                viewModel.handleEvent(GymBuddyEvent.RegenerateQrCode)
            },
            onScanQr = {
                showQrSheet = false
                onNavigateToQrScanner()
            }
        )
    }
}

/**
 * Header section with buddy count and QR actions
 */
@Composable
private fun GymBuddyHeader(
    buddyCount: Int,
    maxBuddies: Int,
    canAddMore: Boolean,
    qrCode: QRCodeData?,
    isGeneratingQr: Boolean,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onRegenerateQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Title and count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Gym Buddies",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$buddyCount of $maxBuddies buddies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Buddy count indicator
            Badge(
                containerColor = if (canAddMore) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (canAddMore) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            ) {
                Text("$buddyCount/$maxBuddies")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QR Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Show my QR code
            Button(
                onClick = onShowQr,
                modifier = Modifier.weight(1f),
                enabled = canAddMore
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Show my QR code",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Show My Code")
            }

            // Scan QR code
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.weight(1f),
                enabled = canAddMore
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR code",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Code")
            }
        }

        if (canAddMore) {
            Spacer(modifier = Modifier.height(16.dp))

            InlineQrInviteCard(
                qrCode = qrCode,
                isGenerating = isGeneratingQr,
                onRegenerate = onRegenerateQr,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Limit reached message
        if (!canAddMore) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Maximum gym buddies reached. Remove a buddy to add a new one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InlineQrInviteCard(
    qrCode: QRCodeData?,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.shapes.medium
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isGenerating -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    qrCode?.bitmap != null -> Image(
                        bitmap = qrCode.bitmap!!.asImageBitmap(),
                        contentDescription = "Gym Buddy QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Add Friend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Have another Liftrix user scan this code to connect immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (qrCode != null) {
                    CountdownTimer(
                        expiresAt = qrCode.expiresAt,
                        onExpired = onRegenerate
                    )
                }
            }
        }
    }
}

/**
 * Gym buddies list display
 */
@Composable
private fun GymBuddiesList(
    gymBuddies: List<GymBuddy>,
    isLoading: Boolean,
    error: String?,
    onEvent: (GymBuddyEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            ErrorState(
                error = error,
                onRetry = { onEvent(GymBuddyEvent.LoadGymBuddies) },
                modifier = modifier
            )
        }

        gymBuddies.isEmpty() -> {
            EmptyBuddiesState(
                modifier = modifier
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(gymBuddies) { buddy ->
                    GymBuddyCard(
                        buddy = buddy,
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

/**
 * Individual gym buddy card
 */
@Composable
private fun GymBuddyCard(
    buddy: GymBuddy,
    onEvent: (GymBuddyEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Buddy Avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = buddy.getBuddyDisplayName()
                        .split(' ')
                        .take(2)
                        .mapNotNull { it.firstOrNull() }
                        .joinToString("")
                    
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Buddy info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = buddy.getBuddyDisplayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (buddy.pairedViaQr) "Connected via QR" else "Connected manually",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!buddy.pairingLocation.isNullOrEmpty()) {
                        Text(
                            text = "Met at ${buddy.pairingLocation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // PR notification status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (buddy.isEligibleForPrNotification()) {
                            Icons.Default.Notifications
                        } else {
                            Icons.Default.NotificationsOff
                        },
                        contentDescription = if (buddy.isEligibleForPrNotification()) {
                            "PR notifications active"
                        } else {
                            "PR notifications cooldown"
                        },
                        tint = if (buddy.isEligibleForPrNotification()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (buddy.isEligibleForPrNotification()) "Active" else "Cooldown",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // More actions button
                IconButton(
                    onClick = { 
                        onEvent(GymBuddyEvent.ShowBuddyOptions(buddy))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Buddy options"
                    )
                }
            }
        }
    }
}

/**
 * QR Code display bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QRDisplayBottomSheet(
    qrCode: QRCodeData?,
    userProfile: QRUserProfile?,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onScanQr: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Scan to Connect",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Expiration timer or generation status
            when {
                isGenerating -> {
                    Text(
                        text = "Generating QR code...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                qrCode != null -> {
                    CountdownTimer(
                        expiresAt = qrCode.expiresAt,
                        onExpired = onRegenerate
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR Code
            Card(
                modifier = Modifier.size(280.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                when {
                    isGenerating -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    qrCode?.bitmap != null -> {
                        // Display QR bitmap with proper implementation
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.medium
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Display actual QR code bitmap
                                Image(
                                    bitmap = qrCode.bitmap!!.asImageBitmap(),
                                    contentDescription = "Gym Buddy QR Code",
                                    modifier = Modifier.size(200.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Scan to connect as gym buddies",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Expires in ${(qrCode.expiresAt - System.currentTimeMillis()) / 60000}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("QR Code Error")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User info
            if (userProfile != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = userProfile.displayName
                            .split(' ')
                            .take(2)
                            .mapNotNull { it.firstOrNull() }
                            .joinToString("")
                        Text(
                            text = initials,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userProfile.displayName,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "@${userProfile.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Code")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Instructions
            Text(
                text = "Ask your gym buddy to scan this code with their Liftrix app",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Countdown timer for QR code expiration
 */
@Composable
private fun CountdownTimer(
    expiresAt: Long,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier
) {
    var timeLeft by remember { mutableLongStateOf(0L) }

    LaunchedEffect(expiresAt) {
        while (true) {
            val now = System.currentTimeMillis()
            timeLeft = (expiresAt - now) / 1000
            
            if (timeLeft <= 0) {
                onExpired()
                break
            }
            
            kotlinx.coroutines.delay(1000)
        }
    }

    val minutes = timeLeft / 60
    val seconds = timeLeft % 60

    Text(
        text = "Expires in ${minutes}:${seconds.toString().padStart(2, '0')}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (timeLeft <= 60) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
    )
}

/**
 * Empty state when user has no gym buddies
 */
@Composable
private fun EmptyBuddiesState(
    modifier: Modifier = Modifier
) {
    EmptyStateContent(
        icon = Icons.Default.FitnessCenter,
        title = "No gym buddies yet",
        description = "Connect with your workout partners using QR codes to build your gym buddy network!",
        modifier = modifier
    )
}

/**
 * Reusable empty state content
 */
@Composable
private fun EmptyStateContent(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Error state with retry button
 */
@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GymBuddyScreenPreview() {
    LiftrixTheme {
        GymBuddyScreen(
            onNavigateToQrScanner = { }
        )
    }
}
