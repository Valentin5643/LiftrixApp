package com.example.liftrix.ui.onboarding.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.ui.onboarding.model.OnboardingStep

/**
 * Fitness goals selection screen for onboarding flow.
 * Provides multi-select goals grid and drag-and-drop prioritization.
 */
@Composable
fun GoalsSelectionScreen(
    selectedGoals: Set<FitnessGoal>,
    goalsPriority: Map<FitnessGoal, Int>,
    onGoalToggle: (FitnessGoal) -> Unit,
    onGoalReorder: (FitnessGoal, Int) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Validation state
    var goalsValidationResult by remember { mutableStateOf<ValidationResult?>(null) }
    val validateProfileInputUseCase = remember { ValidateProfileInputUseCase() }
    
    // Validate goals selection
    LaunchedEffect(selectedGoals) {
        goalsValidationResult = validateGoalsSelection(selectedGoals.toList())
    }
    
    val isGoalsValid = goalsValidationResult is ValidationResult.Valid || selectedGoals.isEmpty()
    val canContinue = isGoalsValid && selectedGoals.isNotEmpty()
    
    OnboardingScreenTemplate(
        title = OnboardingStep.GOALS.title,
        subtitle = OnboardingStep.GOALS.description,
        currentStep = OnboardingStep.GOALS.stepNumber + 1,
        totalSteps = OnboardingStep.getContentSteps().size + 1,
        onBack = onBack,
        onSkip = onSkip,
        onContinue = onContinue,
        continueText = "Continue",
        canContinue = canContinue,
        isLoading = false,
        modifier = modifier
    ) {
        GoalsSelectionContent(
            selectedGoals = selectedGoals,
            goalsPriority = goalsPriority,
            onGoalToggle = onGoalToggle,
            onGoalReorder = onGoalReorder,
            goalsValidationResult = goalsValidationResult
        )
    }
}

/**
 * Main content area for goals selection.
 */
@Composable
private fun GoalsSelectionContent(
    selectedGoals: Set<FitnessGoal>,
    goalsPriority: Map<FitnessGoal, Int>,
    onGoalToggle: (FitnessGoal) -> Unit,
    onGoalReorder: (FitnessGoal, Int) -> Unit,
    goalsValidationResult: ValidationResult?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        GoalsSelectionHelperText()
        GoalsGrid(
            selectedGoals = selectedGoals,
            onGoalToggle = onGoalToggle
        )
        if (selectedGoals.isNotEmpty()) {
            SelectedGoalsDisplay(
                selectedGoals = selectedGoals,
                goalsPriority = goalsPriority,
                onGoalReorder = onGoalReorder
            )
        }
        GoalsValidationMessage(validationResult = goalsValidationResult)
    }
}

/**
 * Helper text explaining fitness goals selection.
 */
@Composable
private fun GoalsSelectionHelperText() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "🎯",
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "What are your fitness goals? Select all that apply and drag to prioritize them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Grid layout for fitness goals selection using FilterChips.
 */
@Composable
private fun GoalsGrid(
    selectedGoals: Set<FitnessGoal>,
    onGoalToggle: (FitnessGoal) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(200.dp) // Fixed height to prevent layout issues
    ) {
        items(FitnessGoal.entries) { goal ->
            GoalChip(
                goal = goal,
                isSelected = selectedGoals.contains(goal),
                onToggle = { onGoalToggle(goal) }
            )
        }
    }
}

/**
 * Individual fitness goal FilterChip component.
 */
@Composable
private fun GoalChip(
    goal: FitnessGoal,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onToggle,
        label = {
            Text(
                text = goal.displayName,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${goal.displayName} goal selector"
            }
    )
}

/**
 * Display selected goals with drag-and-drop prioritization.
 */
@Composable
private fun SelectedGoalsDisplay(
    selectedGoals: Set<FitnessGoal>,
    goalsPriority: Map<FitnessGoal, Int>,
    onGoalReorder: (FitnessGoal, Int) -> Unit
) {
    val prioritizedGoals = selectedGoals.toList().sortedBy { goal ->
        goalsPriority[goal] ?: Int.MAX_VALUE
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal Priorities",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${selectedGoals.size}/7",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedGoals.size <= 7) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "Drag goals to reorder by priority (most important first)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            DraggableGoalsList(
                goals = prioritizedGoals,
                onReorder = onGoalReorder
            )
        }
    }
}

/**
 * Draggable goals list with reorder functionality.
 */
@Composable
private fun DraggableGoalsList(
    goals: List<FitnessGoal>,
    onReorder: (FitnessGoal, Int) -> Unit
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(kotlin.math.min(goals.size * 64.dp.value, 300.dp.value).dp)
            .pointerInput(goals) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val itemHeight = with(density) { 64.dp.toPx() }
                        val index = (offset.y / itemHeight).toInt()
                        if (index in goals.indices) {
                            draggedIndex = index
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { _, dragAmount ->
                        if (draggedIndex != -1) {
                            dragOffset += dragAmount.y
                            
                            val itemHeight = with(density) { 64.dp.toPx() }
                            val newIndex = ((draggedIndex * itemHeight + dragOffset) / itemHeight).toInt()
                                .coerceIn(0, goals.size - 1)
                            
                            if (newIndex != draggedIndex && newIndex in goals.indices) {
                                val draggedGoal = goals[draggedIndex]
                                onReorder(draggedGoal, newIndex)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                
                                // Adjust offset for the new position
                                val offsetAdjustment = (newIndex - draggedIndex) * itemHeight
                                dragOffset -= offsetAdjustment
                                draggedIndex = newIndex
                            }
                        }
                    },
                    onDragEnd = {
                        draggedIndex = -1
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        draggedIndex = -1
                        dragOffset = 0f
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(goals) { index, goal ->
            val isDragging = index == draggedIndex
            val animatedElevation by animateFloatAsState(
                targetValue = if (isDragging) 8f else 0f,
                label = "elevation"
            )
            
            DraggableGoalItem(
                goal = goal,
                priority = index + 1,
                isDragging = isDragging,
                dragOffset = if (isDragging) dragOffset else 0f,
                elevation = animatedElevation,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Individual draggable goal item.
 */
@Composable
private fun DraggableGoalItem(
    goal: FitnessGoal,
    priority: Int,
    isDragging: Boolean,
    dragOffset: Float,
    elevation: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .graphicsLayer {
                translationY = dragOffset
                shadowElevation = elevation
            }
            .semantics {
                contentDescription = "Goal ${goal.displayName}, priority $priority"
            },
        shape = MaterialTheme.shapes.medium,
        color = if (isDragging) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shadowElevation = elevation.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PriorityIndicator(priority = priority)
            
            Text(
                text = goal.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag handle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Priority indicator showing the goal's priority number.
 */
@Composable
private fun PriorityIndicator(
    priority: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = priority.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Goals selection validation message.
 */
@Composable
private fun GoalsValidationMessage(
    validationResult: ValidationResult?
) {
    when (validationResult) {
        is ValidationResult.Loading -> {
            // Show loading state - no message displayed during validation
        }
        is ValidationResult.Invalid -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Goals selection error: ${validationResult.message}"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Goals error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = validationResult.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        is ValidationResult.Valid -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Goals selection is valid"
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Goals selection valid",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "Great goal selection!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        null -> {
            // No validation message when no goals selected yet
        }
    }
}

/**
 * Validates fitness goals selection.
 */
private fun validateGoalsSelection(goals: List<FitnessGoal>): ValidationResult {
    return when {
        goals.isEmpty() -> ValidationResult.Invalid("Please select at least one fitness goal")
        goals.size > 7 -> ValidationResult.Invalid("Cannot select more than 7 goals")
        goals.distinct().size != goals.size -> ValidationResult.Invalid("Duplicate goals selected")
        else -> ValidationResult.Valid
    }
}

@Preview(showBackground = true)
@Composable
private fun GoalsSelectionScreenPreview() {
    MaterialTheme {
        GoalsSelectionScreen(
            selectedGoals = setOf(
                FitnessGoal.BUILD_MUSCLE,
                FitnessGoal.LOSE_WEIGHT,
                FitnessGoal.IMPROVE_ENDURANCE
            ),
            goalsPriority = mapOf(
                FitnessGoal.BUILD_MUSCLE to 1,
                FitnessGoal.LOSE_WEIGHT to 2,
                FitnessGoal.IMPROVE_ENDURANCE to 3
            ),
            onGoalToggle = {},
            onGoalReorder = { _, _ -> },
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty State")
@Composable
private fun GoalsSelectionScreenEmptyPreview() {
    MaterialTheme {
        GoalsSelectionScreen(
            selectedGoals = emptySet(),
            goalsPriority = emptyMap(),
            onGoalToggle = {},
            onGoalReorder = { _, _ -> },
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun GoalsSelectionScreenDarkPreview() {
    MaterialTheme {
        GoalsSelectionScreen(
            selectedGoals = setOf(
                FitnessGoal.INCREASE_STRENGTH,
                FitnessGoal.GENERAL_FITNESS
            ),
            goalsPriority = mapOf(
                FitnessGoal.INCREASE_STRENGTH to 1,
                FitnessGoal.GENERAL_FITNESS to 2
            ),
            onGoalToggle = {},
            onGoalReorder = { _, _ -> },
            onContinue = {},
            onBack = {},
            onSkip = {}
        )
    }
}
