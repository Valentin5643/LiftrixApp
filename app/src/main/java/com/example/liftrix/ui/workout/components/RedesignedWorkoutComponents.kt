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

/**
 * Context for RedesignedExerciseCard to control behavior
 */
enum class ExerciseCardContext {
    ACTIVE_WORKOUT,  // Show completion checkboxes, focus on actual values
    TEMPLATE_CREATION  // Hide completion, focus on target values
}

/**
 * Redesigned Exercise Card matching reference UI
 */
@Composable
fun RedesignedExerciseCard(
    exerciseName: String,
    exerciseSubtitle: String? = null,
    sets: List<RedesignedSetData>,
    onAddSet: () -> Unit,
    onUpdateSet: (Int, RedesignedSetData) -> Unit,
    onMenuClick: () -> Unit,
    onNotesClick: (() -> Unit)? = null,
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
        
        // Set headers
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
        
        // Set rows
        sets.forEachIndexed { index, set ->
            RedesignedSetRow(
                setNumber = index + 1,
                setData = set,
                context = context,
                onUpdateSet = { updatedSet ->
                    onUpdateSet(index, updatedSet)
                }
            )
            if (index < sets.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
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
 * Redesigned Set Row
 */
@Composable
private fun RedesignedSetRow(
    setNumber: Int,
    setData: RedesignedSetData,
    context: ExerciseCardContext,
    onUpdateSet: (RedesignedSetData) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set number
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
        
        // Weight input
        RedesignedInputField(
            value = setData.weight,
            onValueChange = { onUpdateSet(setData.copy(weight = it)) },
            placeholder = "0",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.width(70.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Reps input
        RedesignedInputField(
            value = setData.reps,
            onValueChange = { onUpdateSet(setData.copy(reps = it)) },
            placeholder = "0",
            keyboardType = KeyboardType.Number,
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
 * Redesigned Input Field
 */
@Composable
private fun RedesignedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
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
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp),
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
 * Data class for redesigned set
 */
data class RedesignedSetData(
    val weight: String = "",
    val reps: String = "",
    val previousValue: String? = null,
    val isCompleted: Boolean = false,
    val notes: String? = null
)