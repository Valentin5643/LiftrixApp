package com.example.liftrix.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.common.PerformanceOptimizations
import timber.log.Timber

/**
 * Dialog for requesting notification permission on Android 13+ (API 33+)
 * 
 * Features:
 * - Clear explanation of why notifications are needed
 * - Visual examples of notification types
 * - Handles initial request, denial, and permanent denial cases
 * - Opens system settings when permission is permanently denied
 * - Material 3 design with proper accessibility
 * - Graceful handling of older Android versions
 * 
 * @param isVisible Whether the dialog should be shown
 * @param permissionState Current state of the notification permission
 * @param onRequestPermission Callback to request the permission
 * @param onDismiss Callback when dialog is dismissed
 * @param onOpenSettings Callback to open system settings
 * @param modifier Modifier for styling the dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPermissionDialog(
    isVisible: Boolean,
    permissionState: NotificationPermissionState,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show on Android 13+ where runtime permission is required
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isVisible) {
        return
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
            key = "NotificationPermissionDialog"
        ) {
            NotificationPermissionContent(
                permissionState = permissionState,
                onRequestPermission = onRequestPermission,
                onDismiss = onDismiss,
                onOpenSettings = onOpenSettings,
                modifier = modifier
            )
        }
    }
}

/**
 * Content of the notification permission dialog
 */
@Composable
private fun NotificationPermissionContent(
    permissionState: NotificationPermissionState,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics {
                contentDescription = "Notification permission dialog"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            
            // Title and description based on permission state
            when (permissionState) {
                NotificationPermissionState.NOT_REQUESTED -> {
                    InitialPermissionRequest(
                        onRequestPermission = onRequestPermission,
                        onDismiss = onDismiss
                    )
                }
                
                NotificationPermissionState.DENIED_ONCE -> {
                    RationalPermissionRequest(
                        onRequestPermission = onRequestPermission,
                        onDismiss = onDismiss
                    )
                }
                
                NotificationPermissionState.PERMANENTLY_DENIED -> {
                    PermanentlyDeniedPermissionRequest(
                        onOpenSettings = onOpenSettings,
                        onDismiss = onDismiss
                    )
                }
                
                NotificationPermissionState.GRANTED -> {
                    // This state should not show the dialog
                    onDismiss()
                }
            }
        }
    }
}

/**
 * Initial permission request content
 */
@Composable
private fun InitialPermissionRequest(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Stay in the Loop",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Get notified about important updates and stay motivated with your fitness journey.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Examples of notification types
        NotificationExamples()
        
        // Benefits
        BenefitsList()
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "Not Now",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            
            PrimaryActionButton(
                text = "Allow",
                onClick = onRequestPermission,
                leadingIcon = Icons.Default.Notifications,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Rationale for permission after initial denial
 */
@Composable
private fun RationalPermissionRequest(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Don't Miss Out",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Notifications help you stay on track with your fitness goals and connect with your workout community.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // What you'll miss without notifications
        MissedOpportunitiesList()
        
        Text(
            text = "You can always change this in your phone's settings later.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "Skip",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            
            PrimaryActionButton(
                text = "Allow Notifications",
                onClick = onRequestPermission,
                leadingIcon = Icons.Default.NotificationImportant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Content for when permission is permanently denied
 */
@Composable
private fun PermanentlyDeniedPermissionRequest(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enable in Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "To receive notifications, you'll need to enable them manually in your device settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Settings instruction
        SettingsInstructionCard()
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "Later",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            
            PrimaryActionButton(
                text = "Open Settings",
                onClick = onOpenSettings,
                leadingIcon = Icons.Default.Settings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Examples of notification types with icons
 */
@Composable
private fun NotificationExamples() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "You'll receive notifications for:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        val examples = listOf(
            NotificationExample(
                icon = Icons.Default.EmojiEvents,
                title = "Gym Buddy PRs",
                description = "When your friends hit new personal records",
                color = MaterialTheme.colorScheme.primary
            ),
            NotificationExample(
                icon = Icons.Default.People,
                title = "Social Activity",
                description = "New followers and workout comments",
                color = MaterialTheme.colorScheme.tertiary
            ),
            NotificationExample(
                icon = Icons.Default.TrendingUp,
                title = "Achievements",
                description = "Your personal records and milestones",
                color = Color(0xFF4CAF50) // Green for achievements
            )
        )
        
        examples.forEach { example ->
            NotificationExampleItem(example = example)
        }
    }
}

/**
 * Individual notification example item
 */
@Composable
private fun NotificationExampleItem(example: NotificationExample) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = example.icon,
            contentDescription = example.title,
            tint = example.color,
            modifier = Modifier.size(20.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = example.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = example.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Benefits of allowing notifications
 */
@Composable
private fun BenefitsList() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val benefits = listOf(
            "Stay motivated with community support",
            "Never miss your friends' achievements",
            "Get reminded of important workout milestones",
            "Stay connected to your fitness journey"
        )
        
        benefits.forEach { benefit ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Benefit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = benefit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * What users will miss without notifications
 */
@Composable
private fun MissedOpportunitiesList() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val missedOpportunities = listOf(
            "🎉 Friends celebrating their PRs",
            "💪 Your own achievement milestones",
            "👥 New followers and social interactions",
            "🔥 Workout streak reminders"
        )
        
        missedOpportunities.forEach { opportunity ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = opportunity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Instructions for enabling notifications in settings
 */
@Composable
private fun SettingsInstructionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "How to enable:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            val steps = listOf(
                "Tap \"Open Settings\" below",
                "Find \"Notifications\" in the app settings",
                "Toggle on \"Allow notifications\"",
                "Return to the app"
            )
            
            steps.forEachIndexed { index, step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * Data class for notification examples
 */
private data class NotificationExample(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)

/**
 * States of the notification permission
 */
enum class NotificationPermissionState {
    NOT_REQUESTED,      // Permission hasn't been asked for yet
    DENIED_ONCE,        // Permission was denied but can still be requested
    PERMANENTLY_DENIED, // Permission was denied permanently (need to go to settings)
    GRANTED            // Permission is granted
}

/**
 * Composable for handling notification permission request with proper state management
 * 
 * @param onPermissionResult Callback with the permission result
 * @param onShowRationale Callback when rationale should be shown
 */
@Composable
fun rememberNotificationPermissionState(
    onPermissionResult: (Boolean) -> Unit = {},
    onShowRationale: () -> Unit = {}
): NotificationPermissionHandler {
    val context = LocalContext.current
    
    // Permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Timber.d("Notification permission result: $isGranted")
        onPermissionResult(isGranted)
    }
    
    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        Timber.d("Returned from settings")
        // Check permission status after returning from settings
    }
    
    return remember {
        NotificationPermissionHandler(
            onRequestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Notification permission is automatically granted on older versions
                    onPermissionResult(true)
                }
            },
            onOpenSettings = {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    settingsLauncher.launch(intent)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to open app settings")
                    // Fallback to general settings
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    settingsLauncher.launch(fallbackIntent)
                }
            },
            onShowRationale = onShowRationale
        )
    }
}

/**
 * Handler for notification permission actions
 */
data class NotificationPermissionHandler(
    val onRequestPermission: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onShowRationale: () -> Unit
)

/**
 * Preview for initial permission request
 */
@Preview(showBackground = true)
@Composable
private fun NotificationPermissionDialogInitialPreview() {
    LiftrixTheme {
        NotificationPermissionContent(
            permissionState = NotificationPermissionState.NOT_REQUESTED,
            onRequestPermission = { },
            onDismiss = { },
            onOpenSettings = { }
        )
    }
}

/**
 * Preview for rationale after denial
 */
@Preview(showBackground = true)
@Composable
private fun NotificationPermissionDialogRationalePreview() {
    LiftrixTheme {
        NotificationPermissionContent(
            permissionState = NotificationPermissionState.DENIED_ONCE,
            onRequestPermission = { },
            onDismiss = { },
            onOpenSettings = { }
        )
    }
}

/**
 * Preview for permanently denied state
 */
@Preview(showBackground = true)
@Composable
private fun NotificationPermissionDialogPermanentlyDeniedPreview() {
    LiftrixTheme {
        NotificationPermissionContent(
            permissionState = NotificationPermissionState.PERMANENTLY_DENIED,
            onRequestPermission = { },
            onDismiss = { },
            onOpenSettings = { }
        )
    }
}
