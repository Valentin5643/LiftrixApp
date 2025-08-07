package com.example.liftrix.ui.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlin.math.roundToInt

/**
 * Folder Display Components
 * 
 * Contains pure display components for folder visualization with minimal business logic.
 * These components focus on visual presentation and basic user interactions.
 */

/**
 * Inline Folder Section Component
 * 
 * Main folder container showing folder header and expandable workout list.
 * Handles folder expansion state and basic user interactions.
 */
@Composable
fun LazyItemScope.InlineFolderSection(
    folder: Folder,
    workouts: List<WorkoutTemplate>,
    isExpanded: Boolean,
    onToggleExpanded: (String) -> Unit,
    onStartWorkout: (WorkoutTemplate) -> Unit,
    onEditWorkout: (WorkoutTemplate) -> Unit,
    onEditFolder: (String) -> Unit,
    onCreateFolder: () -> Unit,
    onCreateWorkout: (String) -> Unit, // Added: Create workout in specific folder
    onMoveWorkout: (WorkoutTemplate, String) -> Unit,
    onFolderPositionChanged: ((String, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 300)
            )
            .padding(horizontal = LiftrixSpacing.screenPadding),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.cardPadding)
        ) {
            FolderHeader(
                folder = folder,
                workoutCount = workouts.size,
                isExpanded = isExpanded,
                onToggleExpanded = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpanded(folder.id.value)
                },
                onEditFolder = onEditFolder,
                onPositionChanged = onFolderPositionChanged
            )
            
            // Expandable Workout List
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 250),
                    expandFrom = Alignment.Top
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.listItemSpacing),
                    modifier = Modifier.padding(start = LiftrixSpacing.cardPadding)
                ) {
                    if (workouts.isEmpty() && !folder.isDefault()) {
                        EmptyFolderContent(
                            onCreateWorkout = { onCreateWorkout(folder.id.value) }
                        )
                    } else {
                        workouts.forEach { workout ->
                            FolderWorkoutCard(
                                workout = workout,
                                onStartWorkout = { onStartWorkout(workout) },
                                onEditWorkout = { onEditWorkout(workout) },
                                onMoveWorkout = onMoveWorkout
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Folder Header Component
 * 
 * Clickable header showing folder info with expand/collapse arrow.
 * Follows Material 3 design with semantic colors and accessibility.
 */
@Composable
private fun FolderHeader(
    folder: Folder,
    workoutCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditFolder: (String) -> Unit,
    onPositionChanged: ((String, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "folderArrowRotation"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onToggleExpanded
            )
            .onGloballyPositioned { coordinates ->
                onPositionChanged?.let { callback ->
                    val rect = androidx.compose.ui.geometry.Rect(
                        offset = coordinates.positionInRoot(),
                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                    )
                    callback(folder.id.value, rect)
                }
            }
            .semantics {
                contentDescription = if (isExpanded) {
                    "Collapse ${folder.name.value} folder with $workoutCount workouts"
                } else {
                    "Expand ${folder.name.value} folder with $workoutCount workouts"
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Folder Info Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(LiftrixSpacing.elementSpacing))
                
                Column {
                    Text(
                        text = folder.name.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if (workoutCount == 1) "$workoutCount workout" else "$workoutCount workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Action Buttons Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                // Edit Folder Button
                IconButton(
                    onClick = { onEditFolder(folder.id.value) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit folder ${folder.name.value}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // Expand/Collapse Arrow
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

/**
 * Folder Workout Card Component
 * 
 * Individual workout item within a folder with drag-and-drop capabilities.
 * Includes workout stats and action buttons.
 */
@Composable
private fun FolderWorkoutCard(
    workout: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditWorkout: () -> Unit,
    onMoveWorkout: (WorkoutTemplate, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.05f
                    scaleY = 1.05f
                    shadowElevation = 8.dp.toPx()
                }
            }
            .pointerInput(workout.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { _ ->
                        isDragging = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                    onDrag = { _, delta ->
                        dragOffset = dragOffset + delta
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Drag to move workout",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(LiftrixSpacing.elementSpacing))
            
            // Workout Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (workout.exercises.isNotEmpty()) {
                        val count = workout.exercises.size
                        WorkoutStatItem(
                            icon = Icons.Filled.Assignment,
                            text = if (count == 1) "$count exercise" else "$count exercises"
                        )
                    }
                    
                    workout.estimatedDurationMinutes?.let { duration ->
                        WorkoutStatItem(
                            icon = Icons.Filled.PlayArrow,
                            text = "${duration}min"
                        )
                    }
                }
            }
            
            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                TextButton(
                    onClick = onEditWorkout,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Start",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Workout Stat Item Component
 * 
 * Small component displaying workout statistics with icon and text.
 */
@Composable
private fun WorkoutStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty Folder Content Component
 * 
 * Displayed when a folder has no workouts, encouraging user to create content.
 */
@Composable
private fun EmptyFolderContent(
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(LiftrixSpacing.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
        
        Text(
            text = "No workouts in this folder",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.elementSpacing))
        
        OutlinedButton(
            onClick = onCreateWorkout,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Create Workout",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}