package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.ui.common.pressInteraction
import com.example.liftrix.ui.theme.LiftrixTheme
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Draggable exercise list component for workout template creation.
 * 
 * Provides drag-and-drop reordering functionality with:
 * - Haptic feedback during drag operations
 * - Visual drag indicators and animations
 * - Smooth reordering with proper state management
 * - Accessibility support for screen readers
 * 
 * @param exercises List of template exercises to display
 * @param onReorder Callback when exercises are reordered (fromIndex, toIndex)
 * @param onRemoveExercise Callback to remove an exercise
 * @param onUpdateExercise Callback to update exercise parameters
 * @param modifier Modifier for styling
 */
@Composable
fun DragDropExerciseList(
    exercises: List<TemplateExercise>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveExercise: (TemplateExercise) -> Unit,
    onUpdateExercise: (TemplateExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = onReorder
    )
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Template Exercises (${exercises.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            if (exercises.isEmpty()) {
                Text(
                    text = "No exercises added yet. Use the exercise selector above to add exercises to your template.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Draggable exercise list. Long press and drag to reorder exercises."
                        }
                        .pointerInput(dragDropState) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val itemIndex = lazyListState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { itemInfo ->
                                            offset.y.toInt() in itemInfo.offset..(itemInfo.offset + itemInfo.size)
                                        }?.index
                                    
                                    itemIndex?.let { index ->
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragDropState.startDrag(
                                            index = index,
                                            offset = offset,
                                            size = lazyListState.layoutInfo.visibleItemsInfo[index - lazyListState.firstVisibleItemIndex].let {
                                                androidx.compose.ui.unit.IntSize(it.size, it.size)
                                            }
                                        )
                                    }
                                },
                                onDragEnd = {
                                    dragDropState.endDrag()
                                },
                                onDrag = { change, _ ->
                                    dragDropState.onDrag(change.position)
                                    
                                    // Auto-scroll when dragging near edges
                                    scope.launch {
                                        val scrollAmount = when {
                                            change.position.y > 0 && dragDropState.isDragging -> 10f
                                            change.position.y < 0 && dragDropState.isDragging -> -10f
                                            else -> 0f
                                        }
                                        if (scrollAmount != 0f) {
                                            lazyListState.scrollBy(scrollAmount)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    itemsIndexed(
                        items = exercises,
                        key = { index, exercise -> exercise.instanceId }
                    ) { index, exercise ->
                        DraggableExerciseItem(
                            exercise = exercise,
                            index = index,
                            dragDropState = dragDropState,
                            onRemove = { onRemoveExercise(exercise) },
                            onUpdate = onUpdateExercise,
                            modifier = Modifier
                                .fillMaxWidth()
                                .dragVisualIndicator(dragDropState, index)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual draggable exercise item component
 */
@Composable
private fun DraggableExerciseItem(
    exercise: TemplateExercise,
    index: Int,
    dragDropState: DragDropState,
    onRemove: () -> Unit,
    onUpdate: (TemplateExercise) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track individual sets for this exercise
    // Use instanceId as key to ensure each exercise instance has its own unique state
    var setInputs by remember(exercise.instanceId) { 
        Timber.d("🔥 DRAG-DROP-DEBUG: Initializing setInputs for exercise ${exercise.name} with instanceId ${exercise.instanceId}")
        Timber.d("🔥 DRAG-DROP-DEBUG: Exercise targetSets: ${exercise.targetSets}")
        mutableStateOf(
            // Always start with 1 set for new exercises, ignore targetSets to prevent jumping
            listOf(SetInput(setNumber = 1, weight = "", reps = ""))
        )
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Exercise header with drag handle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier
                        .size(20.dp)
                        .pressInteraction(),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove exercise",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            // Display all set input rows
            setInputs.forEachIndexed { setIndex, setInput ->
                TemplateSetInputRow(
                    setInput = setInput,
                    onUpdateSet = { updatedSet ->
                        setInputs = setInputs.toMutableList().apply {
                            set(setIndex, updatedSet)
                        }
                        // TODO: Temporarily disabled to debug the issue
                        // updateExerciseFromSets(exercise, setInputs, onUpdate)
                    },
                    onRemoveSet = if (setInputs.size > 1) {
                        {
                            setInputs = setInputs.toMutableList().apply {
                                removeAt(setIndex)
                                // Renumber remaining sets
                                forEachIndexed { i, set -> set(i, this[i].copy(setNumber = i + 1)) }
                            }
                            // TODO: Temporarily disabled to debug the issue
                            // updateExerciseFromSets(exercise, setInputs, onUpdate)
                        }
                    } else null
                )
                
                if (setIndex < setInputs.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Add set button
            OutlinedButton(
                onClick = { 
                    val newSetInputs = setInputs + SetInput(
                        setNumber = setInputs.size + 1,
                        weight = setInputs.lastOrNull()?.weight ?: "",
                        reps = setInputs.lastOrNull()?.reps ?: ""
                    )
                    setInputs = newSetInputs
                    // TODO: Temporarily disabled to debug the issue
                    // updateExerciseFromSets(exercise, newSetInputs, onUpdate)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Set")
            }
        }
    }
}

/**
 * Data class to track individual set inputs
 */
private data class SetInput(
    val setNumber: Int,
    val weight: String,
    val reps: String
)

/**
 * Individual set input row for template exercises
 */
@Composable
private fun TemplateSetInputRow(
    setInput: SetInput,
    onUpdateSet: (SetInput) -> Unit,
    onRemoveSet: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${setInput.setNumber}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(24.dp)
        )
        
        OutlinedTextField(
            value = setInput.weight,
            onValueChange = { newValue ->
                if ((newValue.isEmpty() || newValue.toDoubleOrNull() != null) && newValue.length <= 6) {
                    onUpdateSet(setInput.copy(weight = newValue))
                }
            },
            label = { Text("Weight") },
            placeholder = { Text("50") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        
        OutlinedTextField(
            value = setInput.reps,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                    onUpdateSet(setInput.copy(reps = newValue))
                }
            },
            label = { Text("Reps") },
            placeholder = { Text("12") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        
        if (onRemoveSet != null) {
            IconButton(
                onClick = onRemoveSet,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove set",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            // Placeholder to maintain alignment when there's only one set
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * Helper function to update exercise from set inputs
 */
private fun updateExerciseFromSets(
    exercise: TemplateExercise,
    setInputs: List<SetInput>,
    onUpdate: (TemplateExercise) -> Unit
) {
    // Use the first set's values as the target values for the template
    val firstSet = setInputs.firstOrNull()
    val updatedExercise = exercise.copy(
        targetSets = setInputs.size,
        targetReps = firstSet?.reps?.toIntOrNull()?.let { Reps(it) },
        targetWeight = firstSet?.weight?.toDoubleOrNull()?.let { Weight.fromKilograms(it) }
    )
    onUpdate(updatedExercise)
}

@Preview(showBackground = true)
@Composable
private fun DragDropExerciseListPreview() {
    LiftrixTheme {
        // Preview implementation would go here
    }
}