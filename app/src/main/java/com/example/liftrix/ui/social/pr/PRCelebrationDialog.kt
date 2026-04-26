package com.example.liftrix.ui.social.pr

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.social.PRNotification
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rich PR celebration dialog with animations and reactions
 * Shows when gym buddy achieves a personal record with celebration UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PRCelebrationDialog(
    notification: PRNotification,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onViewWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PRCelebrationViewModel = hiltViewModel()
) {
    if (!isVisible) return

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(notification) {
        viewModel.handleEvent(PRCelebrationEvent.LoadNotification(notification))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        PRCelebrationContent(
            notification = notification,
            userReaction = uiState.userReaction,
            isReacting = uiState.isReacting,
            onDismiss = onDismiss,
            onReact = { reaction ->
                onReact(reaction)
                viewModel.handleEvent(PRCelebrationEvent.AddReaction(reaction))
            },
            onViewWorkout = onViewWorkout,
            modifier = modifier
        )
    }
}

/**
 * Main content of the PR celebration dialog
 */
@Composable
private fun PRCelebrationContent(
    notification: PRNotification,
    userReaction: String?,
    isReacting: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onViewWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation states
    var showContent by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
        delay(200)
        showConfetti = true
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Confetti animation overlay
        AnimatedVisibility(
            visible = showConfetti,
            enter = fadeIn(tween(500))
        ) {
            ConfettiAnimation(
                modifier = Modifier.fillMaxSize()
            )
        }

        // Main dialog content
        AnimatedVisibility(
            visible = showContent,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(tween(300))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(24.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with trophy icon
                    PRCelebrationHeader(
                        notification = notification,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // PR details
                    PRDetailsCard(
                        notification = notification,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Reaction buttons
                    ReactionButtons(
                        userReaction = userReaction,
                        isReacting = isReacting,
                        onReact = onReact,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Close")
                        }

                        Button(
                            onClick = onViewWorkout,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "View workout",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Workout")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header section with trophy and user info
 */
@Composable
private fun PRCelebrationHeader(
    notification: PRNotification,
    modifier: Modifier = Modifier
) {
    // Animated trophy icon
    val infiniteTransition = rememberInfiniteTransition(label = "trophy_animation")
    val trophyScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophy_scale"
    )

    val trophyRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophy_rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        // Trophy icon with animation
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = "Personal record",
            tint = Color(0xFFFFD700), // Gold color
            modifier = Modifier
                .size(80.dp)
                .scale(trophyScale)
                .graphicsLayer { rotationZ = trophyRotation }
        )

        // Celebration title
        Text(
            text = "🎉 New Personal Record! 🎉",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        // User info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User avatar placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = notification.fromUserName.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = notification.fromUserName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your gym buddy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * PR details card showing the achievement
 */
@Composable
private fun PRDetailsCard(
    notification: PRNotification,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Exercise name
            Text(
                text = notification.exerciseName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // PR achievement details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Weight achievement
                if (notification.prWeight != null) {
                    PRStatColumn(
                        label = "Weight",
                        value = "${notification.prWeight} ${notification.weightUnit ?: WeightUnit.getSystemDefault().symbol}",
                        icon = Icons.Default.FitnessCenter
                    )
                }

                // Reps achievement
                if (notification.prReps != null) {
                    PRStatColumn(
                        label = "Reps",
                        value = notification.prReps.toString(),
                        icon = Icons.Default.Repeat
                    )
                }

                // Improvement percentage
                if (notification.improvementPercent != null && notification.improvementPercent > 0) {
                    PRStatColumn(
                        label = "Improvement",
                        value = "+${String.format("%.1f", notification.improvementPercent)}%",
                        icon = Icons.Default.TrendingUp,
                        isImprovement = true
                    )
                }
            }

            // Previous best comparison
            if (notification.previousBest != null) {
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Previous best:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${notification.previousBest} ${notification.weightUnit ?: WeightUnit.getSystemDefault().symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Individual stat column for PR details
 */
@Composable
private fun PRStatColumn(
    label: String,
    value: String,
    icon: ImageVector,
    isImprovement: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isImprovement) {
                Color(0xFF4CAF50) // Green for improvement
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isImprovement) {
                Color(0xFF4CAF50)
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Reaction buttons for celebrating the PR
 */
@Composable
private fun ReactionButtons(
    userReaction: String?,
    isReacting: Boolean,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val reactions = listOf("💪", "🔥", "👏", "🎉", "💯", "⚡")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        Text(
            text = "Celebrate with a reaction!",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(reactions) { reaction ->
                ReactionButton(
                    reaction = reaction,
                    isSelected = userReaction == reaction,
                    isReacting = isReacting,
                    onClick = { onReact(reaction) }
                )
            }
        }
    }
}

/**
 * Individual reaction button
 */
@Composable
private fun ReactionButton(
    reaction: String,
    isSelected: Boolean,
    isReacting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "reaction_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "reaction_background"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .scale(scale)
            .background(backgroundColor, CircleShape)
            .clickable { onClick() }
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = reaction,
            style = MaterialTheme.typography.headlineMedium
        )

        // Loading indicator for reaction
        if (isSelected && isReacting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Confetti animation overlay
 */
@Composable
private fun ConfettiAnimation(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val confettiCount = 50
    
    // Confetti particles with random properties
    val confettiPieces = remember {
        (0 until confettiCount).map {
            ConfettiPiece(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                color = listOf(
                    Color.Red, Color.Blue, Color.Green, Color.Yellow, 
                    Color.Magenta, Color.Cyan, Color(0xFFFFD700)
                ).random(),
                size = Random.nextFloat() * 8f + 4f,
                rotation = Random.nextFloat() * 360f,
                velocityX = (Random.nextFloat() - 0.5f) * 2f,
                velocityY = Random.nextFloat() * 2f + 1f
            )
        }
    }

    var animationFrame by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60fps
            animationFrame++
        }
    }

    Canvas(modifier = modifier) {
        confettiPieces.forEach { piece ->
            val progress = (animationFrame * 0.02f) % 2f // Reset animation every 2 seconds
            
            val currentX = (piece.x + piece.velocityX * progress) % 1f
            val currentY = (piece.y + piece.velocityY * progress) % 1.2f - 0.2f
            
            if (currentY >= 0f && currentY <= 1f) {
                val centerX = currentX * size.width
                val centerY = currentY * size.height
                val rotation = piece.rotation + progress * 180f
                
                drawConfettiPiece(
                    centerX = centerX,
                    centerY = centerY,
                    size = piece.size,
                    color = piece.color,
                    rotation = rotation
                )
            }
        }
    }
}

/**
 * Draws a single confetti piece
 */
private fun DrawScope.drawConfettiPiece(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color,
    rotation: Float
) {
    val radians = Math.toRadians(rotation.toDouble()).toFloat()
    val cos = cos(radians)
    val sin = sin(radians)
    
    // Draw confetti as a small rectangle
    val halfSize = size / 2f
    val corners = listOf(
        Pair(-halfSize, -halfSize),
        Pair(halfSize, -halfSize),
        Pair(halfSize, halfSize),
        Pair(-halfSize, halfSize)
    )
    
    val rotatedCorners = corners.map { (x, y) ->
        androidx.compose.ui.geometry.Offset(
            centerX + x * cos - y * sin,
            centerY + x * sin + y * cos
        )
    }
    
    val path = Path().apply {
        moveTo(rotatedCorners[0].x, rotatedCorners[0].y)
        rotatedCorners.drop(1).forEach {
            lineTo(it.x, it.y)
        }
        close()
    }
    
    drawPath(path, color)
}

/**
 * Data class for confetti piece properties
 */
private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val velocityX: Float,
    val velocityY: Float
)

@Preview(showBackground = true)
@Composable
private fun PRCelebrationDialogPreview() {
    LiftrixTheme {
        val mockNotification = PRNotification(
            id = "1",
            fromUserId = "user1",
            fromUserName = "John Doe",
            toUserId = "user2",
            workoutId = "workout1",
            exerciseName = "Bench Press",
            prWeight = 225.0f,
            prReps = 5,
            prType = "1RM",
            previousBest = 205.0f,
            improvementPercent = 9.8f,
            weightUnit = WeightUnit.getSystemDefault().symbol,
            sentAt = System.currentTimeMillis(),
            readAt = null,
            reactedWith = null,
            cooldownKey = "user1:user2:2025-01-14"
        )
        
        PRCelebrationContent(
            notification = mockNotification,
            userReaction = null,
            isReacting = false,
            onDismiss = { },
            onReact = { },
            onViewWorkout = { }
        )
    }
}
