package com.example.liftrix.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * In-app notification banner that slides down from the top of the screen.
 * 
 * Features:
 * - Smooth slide-in/slide-out animations
 * - Auto-dismiss after specified duration
 * - Manual dismiss with swipe or tap
 * - Different notification types with appropriate colors and icons
 * - Haptic feedback for better UX
 * - Accessibility support with semantic descriptions
 * - Material 3 design with proper elevation
 * 
 * Used for displaying notifications when the app is in the foreground to
 * provide immediate feedback without interrupting the user experience.
 * 
 * @param notification The notification data to display
 * @param isVisible Whether the banner should be visible
 * @param onDismiss Callback when notification is dismissed
 * @param onTap Callback when notification is tapped
 * @param modifier Modifier for styling the banner
 */
@Composable
fun InAppNotificationBanner(
    notification: InAppNotification,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    
    // Animation specifications for smooth performance
    val slideAnimation = remember {
        tween<IntOffset>(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    }
    
    val fadeAnimation = remember {
        tween<Float>(
            durationMillis = 250,
            easing = LinearEasing
        )
    }
    
    // Auto dismiss after duration
    LaunchedEffect(isVisible, notification.duration) {
        if (isVisible && notification.duration > 0) {
            delay(notification.duration)
            onDismiss()
        }
    }
    
    // Show banner with animations
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = slideAnimation,
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = fadeAnimation),
        exit = slideOutVertically(
            animationSpec = slideAnimation,
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = fadeAnimation),
        modifier = modifier
    ) {
        NotificationBannerContent(
            notification = notification,
            onDismiss = onDismiss,
            onTap = onTap,
            hapticFeedback = hapticFeedback
        )
    }
}

/**
 * Content of the notification banner
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationBannerContent(
    notification: InAppNotification,
    onDismiss: () -> Unit,
    onTap: (() -> Unit)?,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val colors = getNotificationColors(notification.type)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .zIndex(1000f)
            .semantics {
                contentDescription = "${notification.type.name} notification: ${notification.title}"
            }
            .then(
                if (onTap != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )
                            onTap()
                        }
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Notification type icon
            Icon(
                imageVector = getNotificationIcon(notification.type),
                contentDescription = null,
                tint = colors.onBackground,
                modifier = Modifier.size(24.dp)
            )
            
            // Notification content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (notification.message.isNotEmpty()) {
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Dismiss button
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(
                        HapticFeedbackType.LongPress
                    )
                    onDismiss()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss notification",
                    tint = colors.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Get appropriate colors for notification type
 */
@Composable
private fun getNotificationColors(type: InAppNotificationType): NotificationColors {
    return when (type) {
        InAppNotificationType.SUCCESS -> NotificationColors(
            background = MaterialTheme.colorScheme.primaryContainer,
            onBackground = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        InAppNotificationType.ERROR -> NotificationColors(
            background = MaterialTheme.colorScheme.errorContainer,
            onBackground = MaterialTheme.colorScheme.onErrorContainer
        )
        
        InAppNotificationType.WARNING -> NotificationColors(
            background = Color(0xFFFFF3E0), // Orange container color
            onBackground = Color(0xFF7A4100) // Orange on-container color
        )
        
        InAppNotificationType.INFO -> NotificationColors(
            background = MaterialTheme.colorScheme.surfaceVariant,
            onBackground = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        InAppNotificationType.SOCIAL -> NotificationColors(
            background = MaterialTheme.colorScheme.tertiaryContainer,
            onBackground = MaterialTheme.colorScheme.onTertiaryContainer
        )
        
        InAppNotificationType.ACHIEVEMENT -> NotificationColors(
            background = Color(0xFFE8F5E8), // Light green container
            onBackground = Color(0xFF1B5E1F) // Dark green text
        )
    }
}

/**
 * Get appropriate icon for notification type
 */
private fun getNotificationIcon(type: InAppNotificationType): ImageVector {
    return when (type) {
        InAppNotificationType.SUCCESS -> Icons.Default.CheckCircle
        InAppNotificationType.ERROR -> Icons.Default.Error
        InAppNotificationType.WARNING -> Icons.Default.Warning
        InAppNotificationType.INFO -> Icons.Default.Info
        InAppNotificationType.SOCIAL -> Icons.Default.People
        InAppNotificationType.ACHIEVEMENT -> Icons.Default.EmojiEvents
    }
}

/**
 * Colors for notification banner
 */
private data class NotificationColors(
    val background: Color,
    val onBackground: Color
)

/**
 * Data class representing an in-app notification
 */
data class InAppNotification(
    val id: String = "",
    val title: String,
    val message: String = "",
    val type: InAppNotificationType = InAppNotificationType.INFO,
    val duration: Long = 4000L, // 4 seconds default
    val actionData: Map<String, String> = emptyMap()
)

/**
 * Types of in-app notifications with different visual styles
 */
enum class InAppNotificationType {
    SUCCESS,    // Green - for successful operations
    ERROR,      // Red - for errors and failures
    WARNING,    // Orange - for warnings and cautions
    INFO,       // Blue/Gray - for general information
    SOCIAL,     // Purple - for social notifications
    ACHIEVEMENT // Gold/Green - for achievements and PRs
}

/**
 * Manager for displaying in-app notifications
 * 
 * Handles the queue of notifications and ensures only one is shown at a time.
 * Provides methods for showing different types of notifications with appropriate
 * styling and behavior.
 */
@Composable
fun InAppNotificationManager(
    notifications: List<InAppNotification>,
    onDismissNotification: (String) -> Unit,
    onTapNotification: ((InAppNotification) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Show only the first notification in the queue
    val currentNotification = notifications.firstOrNull()
    
    currentNotification?.let { notification ->
        InAppNotificationBanner(
            notification = notification,
            isVisible = true,
            onDismiss = { onDismissNotification(notification.id) },
            onTap = if (onTapNotification != null) {
                { onTapNotification(notification) }
            } else null,
            modifier = modifier
        )
    }
}

/**
 * Convenience functions for creating different types of notifications
 */
object InAppNotifications {
    
    fun success(
        title: String,
        message: String = "",
        duration: Long = 3000L
    ) = InAppNotification(
        id = generateId(),
        title = title,
        message = message,
        type = InAppNotificationType.SUCCESS,
        duration = duration
    )
    
    fun error(
        title: String,
        message: String = "",
        duration: Long = 5000L
    ) = InAppNotification(
        id = generateId(),
        title = title,
        message = message,
        type = InAppNotificationType.ERROR,
        duration = duration
    )
    
    fun warning(
        title: String,
        message: String = "",
        duration: Long = 4000L
    ) = InAppNotification(
        id = generateId(),
        title = title,
        message = message,
        type = InAppNotificationType.WARNING,
        duration = duration
    )
    
    fun info(
        title: String,
        message: String = "",
        duration: Long = 4000L
    ) = InAppNotification(
        id = generateId(),
        title = title,
        message = message,
        type = InAppNotificationType.INFO,
        duration = duration
    )
    
    fun social(
        title: String,
        message: String = "",
        duration: Long = 4000L,
        actionData: Map<String, String> = emptyMap()
    ) = InAppNotification(
        id = generateId(),
        title = title,
        message = message,
        type = InAppNotificationType.SOCIAL,
        duration = duration,
        actionData = actionData
    )
    
    fun achievement(
        title: String,
        message: String = "",
        duration: Long = 6000L // Longer for achievements
    ) = InAppNotification(
        id = generateId(),
        title = title,
        message = message,
        type = InAppNotificationType.ACHIEVEMENT,
        duration = duration
    )
    
    private fun generateId(): String = System.currentTimeMillis().toString()
}

/**
 * Preview for different notification types
 */
@Preview(showBackground = true)
@Composable
private fun InAppNotificationBannerPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success notification
            NotificationBannerContent(
                notification = InAppNotifications.success(
                    title = "Workout Saved!",
                    message = "Your workout has been successfully saved and synced."
                ),
                onDismiss = { },
                onTap = null,
                hapticFeedback = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        // No-op for preview
                    }
                }
            )
            
            // Error notification
            NotificationBannerContent(
                notification = InAppNotifications.error(
                    title = "Sync Failed",
                    message = "Unable to sync your workout. Check your connection."
                ),
                onDismiss = { },
                onTap = null,
                hapticFeedback = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        // No-op for preview
                    }
                }
            )
            
            // Social notification
            NotificationBannerContent(
                notification = InAppNotifications.social(
                    title = "🎉 John hit a PR!",
                    message = "Bench Press: 225 lbs (+10 lbs)"
                ),
                onDismiss = { },
                onTap = { },
                hapticFeedback = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        // No-op for preview
                    }
                }
            )
            
            // Achievement notification
            NotificationBannerContent(
                notification = InAppNotifications.achievement(
                    title = "New Personal Record!",
                    message = "Deadlift: 315 lbs - Your best yet!"
                ),
                onDismiss = { },
                onTap = null,
                hapticFeedback = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        // No-op for preview
                    }
                }
            )
        }
    }
}

/**
 * Preview for notification manager with queue
 */
@Preview(showBackground = true)
@Composable
private fun InAppNotificationManagerPreview() {
    LiftrixTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Simulate some background content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Background Content")
                Text("Notification will appear above this")
            }
            
            // Notification manager at the top
            InAppNotificationManager(
                notifications = listOf(
                    InAppNotifications.social(
                        title = "New Follower",
                        message = "Alex started following you"
                    )
                ),
                onDismissNotification = { },
                onTapNotification = { },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}