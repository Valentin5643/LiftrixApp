package com.example.liftrix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.usecase.exercise.SearchableExercise

/**
 * Stateless searchable exercise selector component
 * 
 * @param exercises List of available exercises to select from
 * @param searchQuery Current search query text
 * @param selectedExercise Currently selected exercise (nullable)
 * @param isExpanded Whether the dropdown is expanded
 * @param onSearchQueryChanged Callback when search query changes
 * @param onExerciseSelected Callback when an exercise is selected
 * @param onExpandedChanged Callback when dropdown expansion state changes
 * @param modifier Optional modifier for styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableExerciseSelector(
    exercises: List<SearchableExercise>,
    searchQuery: String,
    selectedExercise: SearchableExercise?,
    isExpanded: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onExerciseSelected: (SearchableExercise?) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    
    val filteredExercises = remember(exercises, searchQuery) {
        if (searchQuery.isBlank()) {
            exercises
        } else {
            exercises.filter { exercise ->
                exercise.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Exercise selector" }
    ) {
        OutlinedTextField(
            value = selectedExercise?.name ?: searchQuery,
            onValueChange = { query ->
                onSearchQueryChanged(query)
                if (!isExpanded) onExpandedChanged(true)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { 
                Text(
                    text = "Search exercises...",
                    style = MaterialTheme.typography.bodyLarge
                ) 
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty() || selectedExercise != null) {
                    IconButton(
                        onClick = {
                            onSearchQueryChanged("")
                            onExerciseSelected(null)
                            onExpandedChanged(false)
                            focusManager.clearFocus()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        AnimatedVisibility(
            visible = isExpanded && filteredExercises.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                LazyColumn {
                    items(filteredExercises) { exercise ->
                        ExerciseItem(
                            exercise = exercise,
                            onClick = {
                                onExerciseSelected(exercise)
                                onExpandedChanged(false)
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual exercise item in the selector dropdown
 */
@Composable
private fun ExerciseItem(
    exercise: SearchableExercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (exercise) {
                    is SearchableExercise.LibraryExercise -> exercise.exercise.primaryMuscleGroup.displayName
                    is SearchableExercise.CustomExercise -> "Custom Exercise"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Badge(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text(
                text = when (exercise) {
                    is SearchableExercise.LibraryExercise -> exercise.exercise.primaryMuscleGroup.displayName.take(3).uppercase()
                    is SearchableExercise.CustomExercise -> "CUS"
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * Stateless set input row component for reps, weight, rest, and RPE
 * 
 * @param setNumber The set number (1, 2, 3, etc.)
 * @param reps Current reps value
 * @param weight Current weight value
 * @param restTimeSeconds Current rest time in seconds (nullable)
 * @param rpe Current RPE (Rate of Perceived Exertion) value (nullable)
 * @param onRepsChanged Callback when reps value changes
 * @param onWeightChanged Callback when weight value changes
 * @param onRestTimeChanged Callback when rest time changes
 * @param onRpeChanged Callback when RPE value changes
 * @param onSetCompleted Callback when set is marked as completed
 * @param isCompleted Whether this set is completed
 * @param modifier Optional modifier for styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetInputRow(
    setNumber: Int,
    reps: Reps,
    weight: Weight,
    restTimeSeconds: Int?,
    rpe: Int?,
    onRepsChanged: (Reps) -> Unit,
    onWeightChanged: (Weight) -> Unit,
    onRestTimeChanged: (Int?) -> Unit,
    onRpeChanged: (Int?) -> Unit,
    onSetCompleted: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Set $setNumber input" },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Set number and completion status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Button(
                    onClick = onSetCompleted,
                    enabled = !isCompleted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (isCompleted) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                ) {
                    Text(
                        text = if (isCompleted) "Completed" else "Complete",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Input row for reps and weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reps input
                OutlinedTextField(
                    value = reps.count.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { repsCount ->
                            if (repsCount > 0 && repsCount <= 999) {
                                onRepsChanged(Reps(repsCount))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    enabled = !isCompleted
                )
                
                // Weight input
                OutlinedTextField(
                    value = weight.kilograms.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { weightKg ->
                            if (weightKg >= 0.0 && weightKg <= 1000.0) {
                                onWeightChanged(Weight(weightKg))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    enabled = !isCompleted
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Input row for rest time and RPE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Rest time input
                OutlinedTextField(
                    value = restTimeSeconds?.toString() ?: "",
                    onValueChange = { value ->
                        if (value.isEmpty()) {
                            onRestTimeChanged(null)
                        } else {
                            value.toIntOrNull()?.let { restSeconds ->
                                if (restSeconds >= 0 && restSeconds <= 3600) {
                                    onRestTimeChanged(restSeconds)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Rest (sec)") },
                    placeholder = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    enabled = !isCompleted
                )
                
                // RPE input
                OutlinedTextField(
                    value = rpe?.toString() ?: "",
                    onValueChange = { value ->
                        if (value.isEmpty()) {
                            onRpeChanged(null)
                        } else {
                            value.toIntOrNull()?.let { rpeValue ->
                                if (rpeValue in 1..10) {
                                    onRpeChanged(rpeValue)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("RPE (1-10)") },
                    placeholder = { Text("Optional") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    enabled = !isCompleted
                )
            }
        }
    }
}

/**
 * Stateless muscle engagement selector component (1-5 scale)
 * 
 * @param selectedEngagement Currently selected engagement level (1-5)
 * @param onEngagementSelected Callback when engagement level is selected
 * @param modifier Optional modifier for styling
 */
@Composable
fun MuscleEngagementSelector(
    selectedEngagement: Int?,
    onEngagementSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Muscle engagement selector" }
    ) {
        Text(
            text = "Muscle Engagement",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Rate how well this exercise targeted your muscles (1=Light, 5=Maximum)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..5).forEach { engagement ->
                EngagementButton(
                    engagement = engagement,
                    isSelected = selectedEngagement == engagement,
                    onClick = { onEngagementSelected(engagement) }
                )
            }
        }
    }
}

/**
 * Individual engagement level button
 */
@Composable
private fun EngagementButton(
    engagement: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = tween(150),
        label = "engagement_scale"
    )
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                shape = CircleShape
            )
            .clickable { onClick() }
            .semantics { 
                contentDescription = "Engagement level $engagement" 
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = engagement.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Stateless save button with loading state
 * 
 * @param isLoading Whether the save operation is in progress
 * @param isEnabled Whether the button should be enabled
 * @param onSaveClicked Callback when save button is clicked
 * @param modifier Optional modifier for styling
 */
@Composable
fun SaveButton(
    isLoading: Boolean,
    isEnabled: Boolean,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSaveClicked,
        enabled = isEnabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { 
                contentDescription = if (isLoading) "Saving workout" else "Save workout" 
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Saving...",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        AnimatedVisibility(
            visible = !isLoading,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Save Workout",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
} 