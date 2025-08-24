package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.domain.model.SessionSet
import timber.log.Timber

/**
 * Context for RedesignedExerciseCard to control behavior
 */
enum class ExerciseCardContext {
    ACTIVE_WORKOUT,  // Show completion checkboxes, focus on actual values
    TEMPLATE_CREATION  // Hide completion, focus on target values
}

/**
 * Redesigned Exercise Card matching reference UI with anomaly detection
 */
@Composable
fun RedesignedExerciseCard(
    exerciseName: String,
    exerciseSubtitle: String? = null,
    sets: List<RedesignedSetData>,
    onAddSet: () -> Unit,
    onUpdateSet: (Int, RedesignedSetData) -> Unit,
    onRemoveSet: ((Int) -> Unit)? = null,
    onMenuClick: () -> Unit,
    onNotesClick: (() -> Unit)? = null,
    onAnomalyDetected: ((String, Int) -> Unit)? = null,
    context: ExerciseCardContext = ExerciseCardContext.ACTIVE_WORKOUT,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Exercise header with name and menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise name with icon
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = exerciseName,
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    exerciseSubtitle?.let {
                        Text(
                            text = it,
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
            
            // Menu button
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Exercise options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = "SET",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.width(50.dp)
            )
            Text(
                text = "PREVIOUS",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.width(90.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "KG",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.width(70.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "REPS",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.width(70.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Stable keys prevent index confusion during recomposition
        sets.forEachIndexed { index, set ->
            // Use stable key based on setId to prevent Compose state confusion
            key(set.setId) {
                if (onRemoveSet != null && sets.size > 1) {
                    SwipeToDismissSetRow(
                        setNumber = index + 1,
                        setData = set,
                        context = context,
                        onUpdateSet = { updatedSet ->
                            onUpdateSet(index, updatedSet)
                        },
                        onRemoveSet = { 
                            // Find actual index based on setId to avoid stale closure problem
                            val actualIndex = sets.indexOfFirst { it.setId == set.setId }
                            if (actualIndex != -1) {
                                onRemoveSet(actualIndex)
                            }
                        },
                        onAnomalyDetected = onAnomalyDetected
                    )
                } else {
                    RedesignedSetRow(
                        setNumber = index + 1,
                        setData = set,
                        context = context,
                        onUpdateSet = { updatedSet ->
                            onUpdateSet(index, updatedSet)
                        },
                        onAnomalyDetected = onAnomalyDetected
                    )
                }
                if (index < sets.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Add set button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onAddSet() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Set",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        
        // Notes button (if provided)
        onNotesClick?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { it() }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Notes",
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}

/**
 * Redesigned Set Row with anomaly detection
 */
@Composable
private fun RedesignedSetRow(
    setNumber: Int,
    setData: RedesignedSetData,
    context: ExerciseCardContext,
    onUpdateSet: (RedesignedSetData) -> Unit,
    onAnomalyDetected: ((String, Int) -> Unit)? = null // (value, setIndex) callback
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$setNumber",
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.width(50.dp)
        )
        
        // Previous values
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(36.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = setData.previousValue ?: "-",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                ),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Weight input with anomaly detection
        RedesignedInputField(
            value = setData.weight,
            onValueChange = { newValue ->
                var hasAnomaly = false
                if (newValue.isNotEmpty() && newValue.toDoubleOrNull() != null) {
                    val weightValue = newValue.toDouble()
                    // Parse previous value format: "50 x 10" where 50 is weight, 10 is reps
                    val previousWeight = setData.previousValue?.split(" x ")?.firstOrNull()?.trim()?.toDoubleOrNull()
                    
                    // Check if we have a valid previous weight (not 0 or null)
                    if (previousWeight != null && previousWeight > 0) {
                        val ratio = weightValue / previousWeight
                        if (ratio >= 2.0 || ratio <= 0.5) { // Default threshold
                            hasAnomaly = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnomalyDetected?.invoke(newValue, setNumber - 1)
                        }
                    }
                }
                onUpdateSet(setData.copy(weight = newValue, hasWeightAnomaly = hasAnomaly))
            },
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            hasAnomaly = setData.hasWeightAnomaly,
            modifier = Modifier.width(70.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Reps input with anomaly detection
        RedesignedInputField(
            value = setData.reps,
            onValueChange = { newValue ->
                var hasAnomaly = false
                if (newValue.isNotEmpty() && newValue.toIntOrNull() != null) {
                    val repsValue = newValue.toInt()
                    // Parse previous value format: "50 x 10" where 50 is weight, 10 is reps
                    val previousReps = setData.previousValue?.split(" x ")?.lastOrNull()?.trim()?.toIntOrNull()
                    
                    if (previousReps != null && previousReps > 0) {
                        val ratio = repsValue.toFloat() / previousReps
                        if (ratio >= 2.0f || ratio <= 0.5f) { // Default threshold
                            hasAnomaly = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnomalyDetected?.invoke(newValue, setNumber - 1)
                        }
                    }
                }
                onUpdateSet(setData.copy(reps = newValue, hasRepsAnomaly = hasAnomaly))
            },
            placeholder = "0",
            keyboardType = KeyboardType.Number,
            hasAnomaly = setData.hasRepsAnomaly,
            modifier = Modifier.width(70.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Checkbox - only show in active workout context
        if (context == ExerciseCardContext.ACTIVE_WORKOUT) {
            Checkbox(
                checked = setData.isCompleted,
                onCheckedChange = { onUpdateSet(setData.copy(isCompleted = it)) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkmarkColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.size(24.dp)
            )
        } else {
            // In template creation, show a placeholder or nothing
            Box(modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * Redesigned Input Field with anomaly detection highlighting
 */
@Composable
private fun RedesignedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    hasAnomaly: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = if (hasAnomaly && !isFocused) MaterialTheme.colorScheme.error 
                   else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(36.dp)
            .background(
                if (hasAnomaly && !isFocused) 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp)
            )
            .then(
                if (hasAnomaly && !isFocused) 
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    )
                else Modifier
            )
            .padding(horizontal = 8.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * Redesigned Workout Header
 */
@Composable
fun RedesignedWorkoutHeader(
    title: String,
    subtitle: String? = null,
    onBackClick: () -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
        }
        
        onSettingsClick?.let {
            IconButton(
                onClick = it,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Redesigned Primary Button
 */
@Composable
fun RedesignedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/**
 * Exercise Options Menu
 */
@Composable
fun ExerciseOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReorder: () -> Unit,
    onChangeExercise: () -> Unit,
    onRemove: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Reorder Exercise",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            onClick = {
                onReorder()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Change Exercise",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            onClick = {
                onChangeExercise()
                onDismiss()
            }
        )
        Divider(color = MaterialTheme.colorScheme.outline)
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Remove Exercise",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            onClick = {
                onRemove()
                onDismiss()
            }
        )
    }
}

/**
 * Exercise Reorder Dialog
 */
@Composable
fun ExerciseReorderDialog(
    exercises: List<Pair<String, String>>, // (exerciseId, exerciseName)
    onDismiss: () -> Unit,
    onConfirmReorder: (List<String>) -> Unit
) {
    var reorderedExercises by remember { mutableStateOf(exercises) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Reorder Exercises",
                color = MaterialTheme.colorScheme.onSurface,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = reorderedExercises,
                    key = { _, exercise -> exercise.first }
                ) { index, exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Exercise name
                        Text(
                            text = "${index + 1}. ${exercise.second}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = TextStyle(fontSize = 14.sp),
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Move up button
                        if (index > 0) {
                            IconButton(
                                onClick = {
                                    val newList = reorderedExercises.toMutableList()
                                    val item = newList.removeAt(index)
                                    newList.add(index - 1, item)
                                    reorderedExercises = newList
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Move down button
                        if (index < reorderedExercises.size - 1) {
                            IconButton(
                                onClick = {
                                    val newList = reorderedExercises.toMutableList()
                                    val item = newList.removeAt(index)
                                    newList.add(index + 1, item)
                                    reorderedExercises = newList
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmReorder(reorderedExercises.map { it.first })
                    onDismiss()
                }
            ) {
                Text("Apply", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

/**
 * Swipe to Dismiss Set Row with red delete background
 */
@Composable
private fun SwipeToDismissSetRow(
    setNumber: Int,
    setData: RedesignedSetData,
    context: ExerciseCardContext,
    onUpdateSet: (RedesignedSetData) -> Unit,
    onRemoveSet: () -> Unit,
    onAnomalyDetected: ((String, Int) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemoveSet()
                    true
                }
                else -> false
            }
        }
    )
    
    // Reset dismiss state if it gets stuck
    LaunchedEffect(setData.setId) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Delete background - only show when actively swiping
            val isActivelySwiping = dismissState.dismissDirection != null
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isActivelySwiping) MaterialTheme.colorScheme.error else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isActivelySwiping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete set",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        // The actual set row content with solid background to prevent bleeding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(6.dp)
                )
        ) {
            RedesignedSetRow(
                setNumber = setNumber,
                setData = setData,
                context = context,
                onUpdateSet = onUpdateSet,
                onAnomalyDetected = onAnomalyDetected
            )
        }
    }
}

/**
 * Data class for redesigned set
 */
data class RedesignedSetData(
    val weight: String = "",
    val reps: String = "",
    val previousValue: String? = null,
    val isCompleted: Boolean = false,
    val notes: String? = null,
    val hasWeightAnomaly: Boolean = false,
    val hasRepsAnomaly: Boolean = false,
    val anomalyConfidenceScore: Float = 0f,
    val setId: String = java.util.UUID.randomUUID().toString() // Stable ID for Compose keys
)

/**
 * Data class for tracking removed sets for undo functionality
 */
data class RemovedSetInfo(
    val setData: RedesignedSetData,
    val index: Int,
    val exerciseIndex: Int? = null
)

/**
 * Undo Set Snackbar - Shows after a set is deleted
 */
@Composable
fun UndoSetSnackbar(
    snackbarHostState: SnackbarHostState,
    removedSetInfo: RemovedSetInfo?,
    onUndo: (RemovedSetInfo) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(removedSetInfo) {
        removedSetInfo?.let { setInfo ->
            val result = snackbarHostState.showSnackbar(
                message = "Set ${setInfo.index + 1} removed",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    onUndo(setInfo)
                }
                SnackbarResult.Dismissed -> {
                    onDismiss()
                }
            }
        }
    }
}

/**
 * Anomaly Nudge Dialog - Shows when an anomaly is detected
 */
@Composable
fun AnomalyNudgeDialog(
    anomalyValue: String,
    anomalyType: String, // "weight" or "reps"
    previousValue: String?,
    exerciseName: String,
    onConfirm: () -> Unit, // User confirms the value is correct
    onCorrect: (String) -> Unit, // User wants to correct the value
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var correctedValue by remember { mutableStateOf(anomalyValue) }
    
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning icon and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Unusual Value Detected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Exercise and value comparison
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Previous",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = previousValue ?: "—",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$anomalyValue ${if (anomalyType == "weight") "kg" else "reps"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                
                Text(
                    text = "This ${anomalyType} seems unusually ${
                        if (previousValue != null) {
                            val prev = if (anomalyType == "weight") {
                                // Parse "50 x 10" format for weight (first value)
                                previousValue.split(" x ").firstOrNull()?.trim()?.replace("kg", "")?.trim()?.toDoubleOrNull() ?: 0.0
                            } else {
                                // Parse "50 x 10" format for reps (last value)
                                (previousValue.split(" x ").lastOrNull()?.trim()?.toIntOrNull() ?: 0).toDouble()
                            }
                            val current = if (anomalyType == "weight") {
                                anomalyValue.toDoubleOrNull() ?: 0.0
                            } else {
                                (anomalyValue.toIntOrNull() ?: 0).toDouble()
                            }
                            if (current > prev) "high" else "low"
                        } else "different"
                    }. Did you mean to enter this value?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Quick fix input
                OutlinedTextField(
                    value = correctedValue,
                    onValueChange = { correctedValue = it },
                    label = { Text("Correct value") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (anomalyType == "weight") KeyboardType.Decimal else KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Keep Value button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfirm()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Keep")
                    }
                    
                    // Fix button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCorrect(correctedValue)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fix It")
                    }
                }
            }
        }
    }
}