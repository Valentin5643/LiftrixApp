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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liftrix.domain.model.Folder
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Inline Folder Section Component
 * 
 * Displays a collapsible folder section with workouts, following Liftrix design system.
 * Features expand/collapse animations, folder management, and workout organization.
 * 
 * @param folder The folder to display
 * @param workouts List of workout templates in this folder
 * @param isExpanded Whether the folder is currently expanded
 * @param onToggleExpanded Callback when folder expansion state changes
 * @param onCreateFolder Callback to create a new folder
 * @param onEditFolder Callback to edit folder name
 * @param onStartWorkout Callback when workout is started
 * @param onEditWorkout Callback when workout is edited
 * @param modifier Modifier for styling
 */
@Composable
fun LazyItemScope.InlineFolderSection(
    folder: Folder,
    workouts: List<WorkoutTemplate>,
    isExpanded: Boolean,
    onToggleExpanded: (String) -> Unit,
    onCreateFolder: () -> Unit = {},
    onCreateWorkout: (String) -> Unit = {}, // Added: Create workout in specific folder
    onEditFolder: (String) -> Unit = {},
    onStartWorkout: (WorkoutTemplate) -> Unit,
    onEditWorkout: (WorkoutTemplate) -> Unit,
    onMoveWorkout: ((WorkoutTemplate, Offset) -> Unit)? = null,
    onFolderPositionChanged: ((String, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.listItemSpacing)
    ) {
        // Folder Header
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Column {
                    Text(
                        text = folder.name.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if (workoutCount == 1) "1 workout" else "$workoutCount workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit folder button (always available now)
                IconButton(
                    onClick = { onEditFolder(folder.id.value) },
                    modifier = Modifier.semantics {
                        contentDescription = "Edit ${folder.name.value} folder"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Expand/Collapse Arrow
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Folder Workout Card Component
 * 
 * Modern workout card matching the reference design with play button on right.
 * Simplified layout with better visual hierarchy.
 */
@Composable
private fun FolderWorkoutCard(
    workout: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditWorkout: () -> Unit,
    onMoveWorkout: ((WorkoutTemplate, Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isDragActive by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var totalDragDistance by remember { mutableStateOf(0f) }
    var cardPosition by remember { mutableStateOf(Offset.Zero) }
    
    // Minimum drag distance to trigger move (in dp)
    val minDragDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 50.dp.toPx() }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                cardPosition = coordinates.positionInRoot()
            }
            .offset { 
                IntOffset(
                    x = if (isDragActive) dragOffset.x.roundToInt() else 0,
                    y = if (isDragActive) dragOffset.y.roundToInt() else 0
                )
            }
            .graphicsLayer {
                alpha = if (isDragActive) 0.8f else 1f
                scaleX = if (isDragActive) 1.05f else 1f
                scaleY = if (isDragActive) 1.05f else 1f
                shadowElevation = if (isDragActive) 8.dp.toPx() else 0f
            }
            .then(
                if (onMoveWorkout != null) {
                    Modifier.pointerInput(workout.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                isDragActive = true
                                dragOffset = Offset.Zero
                                totalDragDistance = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                isDragActive = false
                                
                                // Only move if dragged far enough (indicates intentional move)
                                if (totalDragDistance >= minDragDistancePx) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Calculate absolute drop position: card position + drag offset
                                    val absoluteDropPosition = cardPosition + dragOffset
                                    onMoveWorkout?.invoke(workout, absoluteDropPosition)
                                }
                                
                                dragOffset = Offset.Zero
                                totalDragDistance = 0f
                            },
                            onDrag = { change, dragAmount ->
                                dragOffset += dragAmount
                                totalDragDistance += kotlin.math.sqrt(
                                    dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                )
                            }
                        )
                    }
                } else Modifier
            )
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle (only show if move functionality is available)
                if (onMoveWorkout != null) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "Drag to move to different folder",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Workout info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                Text(
                    text = workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Compact stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    WorkoutStatChip(
                        label = "Sets",
                        value = workout.exercises.sumOf { it.targetSets ?: 3 }.toString()
                    )
                    WorkoutStatChip(
                        label = "Duration",
                        value = "${workout.estimatedDurationMinutes ?: 30}min"
                    )
                }
                }
            }
            
            // Action buttons on the right
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit button
                IconButton(
                    onClick = onEditWorkout,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit workout",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Play button - prominent
                FilledIconButton(
                    onClick = onStartWorkout,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start workout",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Compact workout stat chip for displaying metrics
 */
@Composable
private fun WorkoutStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Original UnifiedWorkoutCard implementation for drag support
 */
@Composable
private fun FolderWorkoutCardLegacy(
    workout: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditWorkout: () -> Unit,
    onMoveWorkout: ((WorkoutTemplate, Offset) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isDragActive by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var totalDragDistance by remember { mutableStateOf(0f) }
    var cardPosition by remember { mutableStateOf(Offset.Zero) }
    
    // Minimum drag distance to trigger move (in dp)
    val minDragDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 50.dp.toPx() }
    
    UnifiedWorkoutCard(
        title = workout.name,
        subtitle = "${workout.exercises.size} exercises",
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                cardPosition = coordinates.positionInRoot()
            }
            .offset { 
                IntOffset(
                    x = if (isDragActive) dragOffset.x.roundToInt() else 0,
                    y = if (isDragActive) dragOffset.y.roundToInt() else 0
                )
            }
            .graphicsLayer {
                alpha = if (isDragActive) 0.8f else 1f
                scaleX = if (isDragActive) 1.05f else 1f
                scaleY = if (isDragActive) 1.05f else 1f
                shadowElevation = if (isDragActive) 8.dp.toPx() else 0f
            }
            .then(
                if (onMoveWorkout != null) {
                    Modifier.pointerInput(workout.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                isDragActive = true
                                dragOffset = Offset.Zero
                                totalDragDistance = 0f
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                isDragActive = false
                                
                                // Only move if dragged far enough (indicates intentional move)
                                if (totalDragDistance >= minDragDistancePx) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Calculate absolute drop position: card position + drag offset
                                    val absoluteDropPosition = cardPosition + dragOffset
                                    onMoveWorkout?.invoke(workout, absoluteDropPosition)
                                }
                                
                                dragOffset = Offset.Zero
                                totalDragDistance = 0f
                            },
                            onDrag = { change, dragAmount ->
                                dragOffset += dragAmount
                                totalDragDistance += kotlin.math.sqrt(
                                    dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y
                                )
                            }
                        )
                    }
                } else Modifier
            ),
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle (only show if move functionality is available)
                if (onMoveWorkout != null) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "Drag to move to different folder",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                SecondaryActionButton(
                    text = "Edit",
                    onClick = onEditWorkout,
                    leadingIcon = Icons.Filled.Edit
                )
                
                PrimaryActionButton(
                    text = "Start",
                    onClick = onStartWorkout,
                    leadingIcon = Icons.Filled.PlayArrow
                )
            }
        }
    ) {
        // Minimal workout info for folder context
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WorkoutStatItem(
                label = "Sets",
                value = workout.getTotalSets().toString()
            )
            
            workout.estimatedDurationMinutes?.let { duration ->
                WorkoutStatItem(
                    label = "Duration",
                    value = "${duration}min"
                )
            }
        }
    }
}

/**
 * Workout Stat Item for folder context
 */
@Composable
private fun WorkoutStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty Folder Content Component
 * 
 * Shows when a folder has no workouts, guides user to create content.
 */
@Composable
private fun EmptyFolderContent(
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.listItemSpacing)
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Folder is empty",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            TertiaryActionButton(
                text = "Create Workout",
                onClick = onCreateWorkout,
                leadingIcon = Icons.Filled.Add
            )
        }
    }
}

/**
 * Simple Create Folder Dialog
 * 
 * Lightweight dialog for creating new folders with just name input.
 * Follows Liftrix design system with proper validation and accessibility.
 */
@Composable
fun CreateFolderDialog_OLD(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (show) {
        var folderName by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        val hapticFeedback = LocalHapticFeedback.current
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Create New Folder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics {
                                contentDescription = "Close create folder dialog"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Folder Name Input
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { newValue ->
                            folderName = newValue
                            isError = newValue.trim().length !in 3..30
                        },
                        label = { Text("Folder Name") },
                        placeholder = { Text("Enter folder name") },
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    text = "Name must be 3-30 characters",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Enter folder name, must be 3 to 30 characters"
                            }
                    )
                    
                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                    ) {
                        SecondaryActionButton(
                            text = "Cancel",
                            onClick = onDismiss
                        )
                        
                        PrimaryActionButton(
                            text = "Create",
                            onClick = {
                                val trimmedName = folderName.trim()
                                if (trimmedName.length in 3..30) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCreateFolder(trimmedName)
                                    onDismiss()
                                } else {
                                    isError = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            enabled = folderName.trim().length in 3..30,
                            leadingIcon = Icons.Filled.Add
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick Create Folder Button
 * 
 * Floating action button style component for quick folder creation.
 * Positioned at the bottom of the folder list for easy access.
 */
@Composable
fun QuickCreateFolderButton_OLD(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .semantics {
                contentDescription = "Create new folder"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "New Folder",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Folder Edit Dialog
 * 
 * Comprehensive dialog for managing folder operations:
 * - Delete folder (with confirmation for non-empty folders)
 * - Add new workout routine to folder
 * - Rename folder
 * - Reorder folder position
 */
@Composable
fun FolderEditDialog(
    show: Boolean,
    folder: Folder?,
    onDismiss: () -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onAddWorkoutToFolder: (Folder) -> Unit,
    onReorderFolder: (Folder, Boolean) -> Unit, // true = move up, false = move down
    modifier: Modifier = Modifier
) {
    if (show && folder != null) {
        var currentAction by remember { mutableStateOf(FolderEditAction.None) }
        var folderName by remember(folder) { mutableStateOf(folder.name.value) }
        var showDeleteConfirmation by remember { mutableStateOf(false) }
        val hapticFeedback = LocalHapticFeedback.current
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Edit Folder: ${folder.name.value}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics {
                                contentDescription = "Close edit folder dialog"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    when (currentAction) {
                        FolderEditAction.None -> {
                            // Main action menu
                            FolderEditMainMenu(
                                folder = folder,
                                onActionSelected = { action ->
                                    when (action) {
                                        FolderEditAction.Delete -> {
                                            showDeleteConfirmation = true
                                        }
                                        else -> {
                                            currentAction = action
                                        }
                                    }
                                }
                            )
                        }
                        FolderEditAction.Rename -> {
                            // Rename input form
                            FolderRenameForm(
                                currentName = folderName,
                                onNameChange = { folderName = it },
                                onSave = {
                                    if (folderName.trim().length in 3..30) {
                                        onRenameFolder(folder, folderName.trim())
                                        onDismiss()
                                    }
                                },
                                onCancel = { currentAction = FolderEditAction.None }
                            )
                        }
                        FolderEditAction.AddWorkout -> {
                            // Add workout confirmation
                            FolderAddWorkoutForm(
                                folder = folder,
                                onAddWorkout = {
                                    onAddWorkoutToFolder(folder)
                                    onDismiss()
                                },
                                onCancel = { currentAction = FolderEditAction.None }
                            )
                        }
                        FolderEditAction.Reorder -> {
                            // Reorder controls
                            FolderReorderForm(
                                folder = folder,
                                onMoveUp = {
                                    onReorderFolder(folder, true)
                                    onDismiss()
                                },
                                onMoveDown = {
                                    onReorderFolder(folder, false)
                                    onDismiss()
                                },
                                onCancel = { currentAction = FolderEditAction.None }
                            )
                        }
                        FolderEditAction.Delete -> {
                            // This should not happen as Delete shows confirmation instead
                        }
                    }
                    
                    // Delete confirmation dialog (overlay)
                    if (showDeleteConfirmation) {
                        FolderDeleteConfirmation(
                            folder = folder,
                            onConfirm = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteFolder(folder)
                                onDismiss()
                            },
                            onCancel = { showDeleteConfirmation = false }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Folder edit action types
 */
private enum class FolderEditAction {
    None,
    Delete,
    AddWorkout,
    Rename,
    Reorder
}

/**
 * Main menu for folder edit actions
 */
@Composable
private fun FolderEditMainMenu(
    folder: Folder,
    onActionSelected: (FolderEditAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What would you like to do?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        // Add workout to folder
        FolderEditActionButton_OLD(
            icon = Icons.Filled.Assignment,
            title = "Add New Workout",
            description = "Create a new workout routine in this folder",
            onClick = { onActionSelected(FolderEditAction.AddWorkout) }
        )
        
        // Rename folder
        FolderEditActionButton_OLD(
            icon = Icons.Filled.Edit,
            title = "Rename Folder",
            description = "Change the folder name",
            onClick = { onActionSelected(FolderEditAction.Rename) }
        )
        
        // Reorder folder
        FolderEditActionButton_OLD(
            icon = Icons.Filled.ArrowUpward,
            title = "Reorder Folder",
            description = "Move folder up or down in the list",
            onClick = { onActionSelected(FolderEditAction.Reorder) }
        )
        
        // Delete folder (only if not default)
        if (!folder.isDefault()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            
            FolderEditActionButton_OLD(
                icon = Icons.Filled.Delete,
                title = "Delete Folder",
                description = "Remove this folder and its contents",
                onClick = { onActionSelected(FolderEditAction.Delete) },
                isDestructive = true
            )
        }
    }
}

/**
 * Action button for folder edit menu
 */
@Composable
private fun FolderEditActionButton_OLD(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Form for renaming a folder
 */
@Composable
private fun FolderRenameForm(
    currentName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Rename Folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        OutlinedTextField(
            value = currentName,
            onValueChange = onNameChange,
            label = { Text("Folder Name") },
            placeholder = { Text("Enter new folder name") },
            isError = currentName.trim().length !in 3..30,
            supportingText = {
                if (currentName.trim().length !in 3..30) {
                    Text(
                        text = "Name must be 3-30 characters",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
            
            PrimaryActionButton(
                text = "Save",
                onClick = onSave,
                enabled = currentName.trim().length in 3..30
            )
        }
    }
}

/**
 * Form for adding workout to folder
 */
@Composable
private fun FolderAddWorkoutForm(
    folder: Folder,
    onAddWorkout: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add New Workout",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "This will create a new workout routine in the \"${folder.name.value}\" folder. You'll be taken to the workout creation screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            SecondaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
            
            PrimaryActionButton(
                text = "Create Workout",
                onClick = onAddWorkout,
                leadingIcon = Icons.Filled.Assignment
            )
        }
    }
}

/**
 * Form for reordering folder
 */
@Composable
private fun FolderReorderForm(
    folder: Folder,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Reorder Folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Choose how to move the \"${folder.name.value}\" folder in the list:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryActionButton(
                text = "Move Up",
                onClick = onMoveUp,
                leadingIcon = Icons.Filled.ArrowUpward,
                modifier = Modifier.weight(1f)
            )
            
            SecondaryActionButton(
                text = "Move Down",
                onClick = onMoveDown,
                leadingIcon = Icons.Filled.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TertiaryActionButton(
                text = "Cancel",
                onClick = onCancel
            )
        }
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
private fun FolderDeleteConfirmation(
    folder: Folder,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Delete Folder?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Text(
                text = "Are you sure you want to delete \"${folder.name.value}\" and all its workout routines? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                SecondaryActionButton(
                    text = "Cancel",
                    onClick = onCancel
                )
                
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}