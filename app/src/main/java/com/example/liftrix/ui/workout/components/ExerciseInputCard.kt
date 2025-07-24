package com.example.liftrix.ui.workout.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.ui.common.AccessibilityUtils.accessibilitySemantics
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.delay

/**
 * Exercise Input Card Component
 * 
 * Interactive card component for inputting exercise set data during active workouts.
 * Provides optimized input fields for reps, weight, time, and distance with real-time
 * validation and user-friendly error handling.
 * 
 * Built on UnifiedWorkoutCard for consistent design with specialized functionality
 * for data input during active workout sessions.
 * 
 * Features:
 * - Exercise type-aware input fields (weight/reps, time, distance)
 * - Real-time input validation with error messages
 * - Quick completion actions and set completion tracking
 * - Rest timer integration for time-based exercises
 * - Keyboard navigation optimization for fast input
 * - Accessibility support with proper field descriptions
 * - Auto-save and recovery for interrupted sessions
 * 
 * @param exercise The exercise for which to collect input data
 * @param currentSet The current set being performed (1-indexed)
 * @param previousSet Previous set data for reference/pre-filling
 * @param isRestTimer Whether the rest timer is currently active
 * @param onSetComplete Callback when a set is completed with entered data
 * @param onSetUpdate Callback for real-time set data updates
 * @param onStartRestTimer Callback to start rest timer
 * @param modifier Modifier for customizing the card's layout and behavior
 */
@Composable
fun ExerciseInputCard(
    exercise: Exercise,
    currentSet: Int,
    previousSet: ExerciseSet? = null,
    isRestTimer: Boolean = false,
    onSetComplete: (ExerciseSet) -> Unit,
    onSetUpdate: (ExerciseSet) -> Unit = {},
    onStartRestTimer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var repsInput by remember { mutableStateOf(previousSet?.reps?.count?.toString() ?: "") }
    var weightInput by remember { mutableStateOf(previousSet?.weight?.kilograms?.toString() ?: "") }
    var timeInputMinutes by remember { mutableStateOf("") }
    var timeInputSeconds by remember { mutableStateOf("") }
    var distanceInput by remember { mutableStateOf(previousSet?.distance?.meters?.toString() ?: "") }
    var rpeInput by remember { mutableStateOf(previousSet?.rpe?.value?.toString() ?: "") }
    var notesInput by remember { mutableStateOf(previousSet?.notes ?: "") }
    
    var hasValidationErrors by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val focusManager = LocalFocusManager.current
    
    // Validate input in real-time
    LaunchedEffect(repsInput, weightInput, timeInputMinutes, timeInputSeconds, distanceInput) {
        val validationResult = validateInputs(
            exercise = exercise,
            repsInput = repsInput,
            weightInput = weightInput,
            timeInputMinutes = timeInputMinutes,
            timeInputSeconds = timeInputSeconds,
            distanceInput = distanceInput
        )
        hasValidationErrors = !validationResult.isValid
        errorMessage = validationResult.errorMessage
        
        // Auto-update set data for real-time feedback
        if (validationResult.isValid) {
            val setData = createExerciseSetFromInputs(
                exercise = exercise,
                currentSet = currentSet,
                repsInput = repsInput,
                weightInput = weightInput,
                timeInputMinutes = timeInputMinutes,
                timeInputSeconds = timeInputSeconds,
                distanceInput = distanceInput,
                rpeInput = rpeInput,
                notesInput = notesInput
            )
            setData?.let { onSetUpdate(it) }
        }
    }
    
    UnifiedWorkoutCard(
        title = exercise.libraryExercise.name,
        subtitle = "Set $currentSet",
        modifier = modifier,
        actions = {
            SetActionButtons(
                canComplete = !hasValidationErrors && hasRequiredInputs(exercise, repsInput, weightInput, timeInputMinutes, timeInputSeconds, distanceInput),
                isRestTimer = isRestTimer,
                onComplete = {
                    val setData = createExerciseSetFromInputs(
                        exercise = exercise,
                        currentSet = currentSet,
                        repsInput = repsInput,
                        weightInput = weightInput,
                        timeInputMinutes = timeInputMinutes,
                        timeInputSeconds = timeInputSeconds,
                        distanceInput = distanceInput,
                        rpeInput = rpeInput,
                        notesInput = notesInput
                    )
                    setData?.let { 
                        onSetComplete(it.complete())
                        if (exercise.isWeightBased) {
                            onStartRestTimer()
                        }
                    }
                },
                onStartTimer = onStartRestTimer
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.formFieldSpacing)
        ) {
            // Previous set reference (if available)
            previousSet?.let { prevSet ->
                PreviousSetReference(previousSet = prevSet, exercise = exercise)
            }
            
            // Input fields based on exercise type
            ExerciseInputFields(
                exercise = exercise,
                repsInput = repsInput,
                onRepsChange = { repsInput = it },
                weightInput = weightInput,
                onWeightChange = { weightInput = it },
                timeInputMinutes = timeInputMinutes,
                onTimeMinutesChange = { timeInputMinutes = it },
                timeInputSeconds = timeInputSeconds,
                onTimeSecondsChange = { timeInputSeconds = it },
                distanceInput = distanceInput,
                onDistanceChange = { distanceInput = it },
                rpeInput = rpeInput,
                onRpeChange = { rpeInput = it },
                notesInput = notesInput,
                onNotesChange = { notesInput = it },
                focusManager = focusManager,
                hasValidationErrors = hasValidationErrors
            )
            
            // Validation error display
            if (hasValidationErrors && errorMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(LiftrixSpacing.elementPaddingLarge)
                            .accessibilitySemantics(
                                description = "Input error: $errorMessage"
                            )
                    )
                }
            }
            
            // Quick input suggestions for weight-based exercises
            if (exercise.isWeightBased && exercise.sets.isNotEmpty()) {
                QuickInputSuggestions(
                    exercise = exercise,
                    onQuickWeight = { weight -> weightInput = weight.toString() },
                    onQuickReps = { reps -> repsInput = reps.toString() }
                )
            }
        }
    }
}

/**
 * Displays reference information from previous set
 */
@Composable
private fun PreviousSetReference(
    previousSet: ExerciseSet,
    exercise: Exercise,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(LiftrixSpacing.elementPaddingLarge)
        ) {
            Text(
                text = "Previous Set",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                previousSet.reps?.let { reps ->
                    Text(
                        text = "${reps.count} reps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                previousSet.weight?.let { weight ->
                    Text(
                        text = "${weight.kilograms}kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                previousSet.time?.let { time ->
                    Text(
                        text = formatDuration(time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Input fields based on exercise capabilities
 */
@Composable
private fun ExerciseInputFields(
    exercise: Exercise,
    repsInput: String,
    onRepsChange: (String) -> Unit,
    weightInput: String,
    onWeightChange: (String) -> Unit,
    timeInputMinutes: String,
    onTimeMinutesChange: (String) -> Unit,
    timeInputSeconds: String,
    onTimeSecondsChange: (String) -> Unit,
    distanceInput: String,
    onDistanceChange: (String) -> Unit,
    rpeInput: String,
    onRpeChange: (String) -> Unit,
    notesInput: String,
    onNotesChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    hasValidationErrors: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.formFieldSpacing)
    ) {
        // Weight and reps inputs (most common)
        if (exercise.isWeightBased) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
            ) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = onWeightChange,
                    label = { Text("Weight (kg)") },
                    placeholder = { Text("0.0") },
                    modifier = Modifier
                        .weight(1f)
                        .accessibilitySemantics(
                            description = "Enter weight in kilograms"
                        ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    isError = hasValidationErrors && weightInput.isNotBlank(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = onRepsChange,
                    label = { Text("Reps") },
                    placeholder = { Text("0") },
                    modifier = Modifier
                        .weight(1f)
                        .accessibilitySemantics(
                            description = "Enter number of repetitions"
                        ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    isError = hasValidationErrors && repsInput.isNotBlank(),
                    singleLine = true
                )
            }
        }
        
        // Time input for time-based exercises
        if (exercise.isTimeBased) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Time",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = timeInputMinutes,
                    onValueChange = onTimeMinutesChange,
                    label = { Text("Min") },
                    placeholder = { Text("0") },
                    modifier = Modifier
                        .weight(1f)
                        .accessibilitySemantics(
                            description = "Enter minutes"
                        ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    singleLine = true
                )
                
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                OutlinedTextField(
                    value = timeInputSeconds,
                    onValueChange = onTimeSecondsChange,
                    label = { Text("Sec") },
                    placeholder = { Text("00") },
                    modifier = Modifier
                        .weight(1f)
                        .accessibilitySemantics(
                            description = "Enter seconds"
                        ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) }
                    ),
                    singleLine = true
                )
            }
        }
        
        // Distance input for distance-based exercises
        if (exercise.isDistanceBased) {
            OutlinedTextField(
                value = distanceInput,
                onValueChange = onDistanceChange,
                label = { Text("Distance (m)") },
                placeholder = { Text("0.0") },
                modifier = Modifier
                    .fillMaxWidth()
                    .accessibilitySemantics(
                        description = "Enter distance in meters"
                    ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                ),
                singleLine = true
            )
        }
        
        // RPE input (optional for all exercises)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = rpeInput,
                onValueChange = onRpeChange,
                label = { Text("RPE (1-10)") },
                placeholder = { Text("Optional") },
                modifier = Modifier
                    .weight(1f)
                    .accessibilitySemantics(
                        description = "Enter Rate of Perceived Exertion from 1 to 10, optional"
                    ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                ),
                singleLine = true
            )
        }
        
        // Notes input (optional)
        OutlinedTextField(
            value = notesInput,
            onValueChange = onNotesChange,
            label = { Text("Notes (optional)") },
            placeholder = { Text("Add notes about this set...") },
            modifier = Modifier
                .fillMaxWidth()
                .accessibilitySemantics(
                    description = "Add optional notes about this set"
                ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            maxLines = 2
        )
    }
}

/**
 * Set completion and timer action buttons
 */
@Composable
private fun SetActionButtons(
    canComplete: Boolean,
    isRestTimer: Boolean,
    onComplete: () -> Unit,
    onStartTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
    ) {
        if (isRestTimer) {
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = "Resting",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Rest timer active",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        
        PrimaryActionButton(
            text = "Complete Set",
            onClick = onComplete,
            enabled = canComplete
        )
    }
}

/**
 * Quick input suggestions based on previous sets
 */
@Composable
private fun QuickInputSuggestions(
    exercise: Exercise,
    onQuickWeight: (Double) -> Unit,
    onQuickReps: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentWeights = exercise.sets
        .mapNotNull { it.weight?.kilograms }
        .distinct()
        .sortedDescending()
        .take(3)
    
    val recentReps = exercise.sets
        .mapNotNull { it.reps?.count }
        .distinct()
        .sorted()
        .take(3)
    
    if (recentWeights.isNotEmpty() || recentReps.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = "Quick Select",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
            ) {
                recentWeights.forEach { weight ->
                    FilterChip(
                        selected = false,
                        onClick = { onQuickWeight(weight) },
                        label = {
                            Text(
                                text = "${weight}kg",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
                
                recentReps.forEach { reps ->
                    FilterChip(
                        selected = false,
                        onClick = { onQuickReps(reps) },
                        label = {
                            Text(
                                text = "${reps} reps",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Data classes and helper functions
 */
private data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String = ""
)

private fun validateInputs(
    exercise: Exercise,
    repsInput: String,
    weightInput: String,
    timeInputMinutes: String,
    timeInputSeconds: String,
    distanceInput: String
): ValidationResult {
    if (exercise.isWeightBased) {
        if (weightInput.isNotBlank()) {
            val weight = weightInput.toDoubleOrNull()
            if (weight == null || weight <= 0) {
                return ValidationResult(false, "Weight must be a positive number")
            }
        }
        
        if (repsInput.isNotBlank()) {
            val reps = repsInput.toIntOrNull()
            if (reps == null || reps <= 0) {
                return ValidationResult(false, "Reps must be a positive number")
            }
        }
    }
    
    if (exercise.isTimeBased) {
        if (timeInputMinutes.isNotBlank()) {
            val minutes = timeInputMinutes.toIntOrNull()
            if (minutes == null || minutes < 0) {
                return ValidationResult(false, "Minutes must be a non-negative number")
            }
        }
        
        if (timeInputSeconds.isNotBlank()) {
            val seconds = timeInputSeconds.toIntOrNull()
            if (seconds == null || seconds < 0 || seconds >= 60) {
                return ValidationResult(false, "Seconds must be between 0 and 59")
            }
        }
    }
    
    if (exercise.isDistanceBased) {
        if (distanceInput.isNotBlank()) {
            val distance = distanceInput.toDoubleOrNull()
            if (distance == null || distance <= 0) {
                return ValidationResult(false, "Distance must be a positive number")
            }
        }
    }
    
    return ValidationResult(true)
}

private fun hasRequiredInputs(
    exercise: Exercise,
    repsInput: String,
    weightInput: String,
    timeInputMinutes: String,
    timeInputSeconds: String,
    distanceInput: String
): Boolean {
    return when {
        exercise.isWeightBased -> repsInput.isNotBlank() || weightInput.isNotBlank()
        exercise.isTimeBased -> timeInputMinutes.isNotBlank() || timeInputSeconds.isNotBlank()
        exercise.isDistanceBased -> distanceInput.isNotBlank()
        else -> true
    }
}

private fun createExerciseSetFromInputs(
    exercise: Exercise,
    currentSet: Int,
    repsInput: String,
    weightInput: String,
    timeInputMinutes: String,
    timeInputSeconds: String,
    distanceInput: String,
    rpeInput: String,
    notesInput: String
): ExerciseSet? {
    val reps = repsInput.toIntOrNull()?.let { Reps(it) }
    val weight = weightInput.toDoubleOrNull()?.let { Weight.fromKilograms(it) }
    val minutes = timeInputMinutes.toIntOrNull() ?: 0
    val seconds = timeInputSeconds.toIntOrNull() ?: 0
    val time = if (minutes > 0 || seconds > 0) {
        java.time.Duration.ofMinutes(minutes.toLong()).plusSeconds(seconds.toLong())
    } else null
    val distance = distanceInput.toDoubleOrNull()?.let { 
        com.example.liftrix.domain.model.Distance.fromMeters(it.toFloat()) 
    }
    val rpe = rpeInput.toIntOrNull()?.let { 
        if (it in 1..10) com.example.liftrix.domain.model.RPE(it) else null 
    }
    val notes = notesInput.takeIf { it.isNotBlank() }
    
    return try {
        ExerciseSet(
            id = com.example.liftrix.domain.model.ExerciseSetId.generate(),
            setNumber = currentSet,
            reps = reps,
            weight = weight,
            time = time,
            distance = distance,
            rpe = rpe,
            notes = notes
        )
    } catch (e: Exception) {
        null
    }
}

private fun formatDuration(duration: java.time.Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    
    return when {
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}