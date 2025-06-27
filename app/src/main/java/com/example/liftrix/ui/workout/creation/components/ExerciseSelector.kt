package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Enhanced exercise selector bottom sheet component with filtering and recent exercises
 * following Material 3 design guidelines and accessibility standards
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelector(
    exercises: List<ExerciseLibrary>,
    recentExercises: List<ExerciseLibrary>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedEquipment: Set<Equipment>,
    onEquipmentSelectionChange: (Set<Equipment>) -> Unit,
    selectedMuscleGroups: Set<ExerciseCategory>,
    onMuscleGroupSelectionChange: (Set<ExerciseCategory>) -> Unit,
    onExerciseSelected: (ExerciseLibrary) -> Unit,
    onCreateCustomExercise: () -> Unit,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier.semantics {
                contentDescription = "Exercise selection bottom sheet"
            }
        ) {
            ExerciseSelectorContent(
                exercises = exercises,
                recentExercises = recentExercises,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedEquipment = selectedEquipment,
                onEquipmentSelectionChange = onEquipmentSelectionChange,
                selectedMuscleGroups = selectedMuscleGroups,
                onMuscleGroupSelectionChange = onMuscleGroupSelectionChange,
                onExerciseSelected = onExerciseSelected,
                onCreateCustomExercise = onCreateCustomExercise,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ExerciseSelectorContent(
    exercises: List<ExerciseLibrary>,
    recentExercises: List<ExerciseLibrary>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedEquipment: Set<Equipment>,
    onEquipmentSelectionChange: (Set<Equipment>) -> Unit,
    selectedMuscleGroups: Set<ExerciseCategory>,
    onMuscleGroupSelectionChange: (Set<ExerciseCategory>) -> Unit,
    onExerciseSelected: (ExerciseLibrary) -> Unit,
    onCreateCustomExercise: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Filter exercises based on search query, equipment, and muscle groups
    val filteredExercises = remember(exercises, searchQuery, selectedEquipment, selectedMuscleGroups) {
        var filtered = exercises
        
        // Apply search query filter
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { exercise ->
                exercise.matchesQuery(searchQuery)
            }
        }
        
        // Apply equipment filter
        if (selectedEquipment.isNotEmpty()) {
            filtered = filtered.filter { exercise ->
                selectedEquipment.contains(exercise.equipment)
            }
        }
        
        // Apply muscle group filter
        if (selectedMuscleGroups.isNotEmpty()) {
            filtered = filtered.filter { exercise ->
                selectedMuscleGroups.contains(exercise.primaryMuscleGroup) ||
                exercise.secondaryMuscleGroups.any { secondary -> selectedMuscleGroups.contains(secondary) }
            }
        }
        
        // Sort by match score if search query exists, otherwise by name
        if (searchQuery.isNotBlank()) {
            filtered.sortedByDescending { exercise ->
                exercise.calculateMatchScore(searchQuery)
            }
        } else {
            filtered.sortedBy { exercise -> exercise.name }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header with title and close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Exercise",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = "Close exercise selector"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Search field
        ExerciseSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            focusRequester = focusRequester,
            onClearFocus = { focusManager.clearFocus() },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Equipment filter chips
        if (selectedEquipment.isNotEmpty() || searchQuery.isBlank()) {
            Text(
                text = "Equipment",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            EquipmentFilterChips(
                selectedEquipment = selectedEquipment,
                onSelectionChange = onEquipmentSelectionChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Muscle group filter chips
        if (selectedMuscleGroups.isNotEmpty() || searchQuery.isBlank()) {
            Text(
                text = "Muscle Groups",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            MuscleGroupFilterChips(
                selectedMuscleGroups = selectedMuscleGroups,
                onSelectionChange = onMuscleGroupSelectionChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Recent exercises section (show when no search/filters applied)
        if (searchQuery.isBlank() && selectedEquipment.isEmpty() && selectedMuscleGroups.isEmpty() && recentExercises.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "Recent Exercises",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = recentExercises.take(5), // Show max 5 recent exercises
                    key = { exercise -> "recent_${exercise.id}" }
                ) { exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        onClick = { onExerciseSelected(exercise) },
                        isRecent = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Exercise list header
        val hasFiltersApplied = searchQuery.isNotBlank() || selectedEquipment.isNotEmpty() || selectedMuscleGroups.isNotEmpty()
        val exerciseListTitle = if (hasFiltersApplied) "Search Results" else "All Exercises"
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exerciseListTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            
            // Custom exercise creation button
            OutlinedButton(
                onClick = onCreateCustomExercise,
                modifier = Modifier.semantics {
                    contentDescription = "Create custom exercise"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Custom")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Exercise list
        if (filteredExercises.isEmpty()) {
            EmptyExerciseState(
                searchQuery = searchQuery,
                hasFilters = selectedEquipment.isNotEmpty() || selectedMuscleGroups.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    items = filteredExercises,
                    key = { exercise -> exercise.id }
                ) { exercise ->
                    ExerciseListItem(
                        exercise = exercise,
                        onClick = { onExerciseSelected(exercise) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Bottom padding for last item
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
    
    // Auto-focus search field when sheet opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseListItem(
    exercise: ExerciseLibrary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRecent: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Select ${exercise.name} exercise"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise thumbnail placeholder
            ExerciseThumbnail(
                equipment = exercise.equipment,
                modifier = Modifier.size(48.dp)
            )
            
            // Exercise details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.primaryMuscleGroup.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    Text(
                        text = exercise.equipment.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    if (isRecent) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        
                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseThumbnail(
    equipment: Equipment,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(CircleShape),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun EmptyExerciseState(
    searchQuery: String,
    hasFilters: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = when {
                searchQuery.isNotBlank() -> "No exercises found for \"$searchQuery\""
                hasFilters -> "No exercises match your filters"
                else -> "No exercises available"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Text(
            text = when {
                searchQuery.isNotBlank() -> "Try a different search term"
                hasFilters -> "Try adjusting your equipment or muscle group filters"
                else -> "Check back later for new exercises"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseSelectorContentPreview() {
    val sampleExercises = listOf(
        ExerciseLibrary(
            id = "1",
            name = "Push-ups",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BODYWEIGHT_ONLY,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 3,
            instructions = "Classic push-up exercise",
            isCompound = true,
            searchableTerms = listOf("push", "chest", "bodyweight")
        ),
        ExerciseLibrary(
            id = "2", 
            name = "Bench Press",
            primaryMuscleGroup = ExerciseCategory.CHEST,
            equipment = Equipment.BARBELL,
            secondaryMuscleGroups = listOf(ExerciseCategory.SHOULDERS, ExerciseCategory.TRICEPS),
            movementPattern = "Push",
            difficultyLevel = 5,
            instructions = "Barbell bench press",
            isCompound = true,
            searchableTerms = listOf("bench", "press", "chest", "barbell")
        )
    )
    
    LiftrixTheme {
        ExerciseSelectorContent(
            exercises = sampleExercises,
            recentExercises = sampleExercises.take(2),
            searchQuery = "",
            onSearchQueryChange = {},
            selectedEquipment = emptySet(),
            onEquipmentSelectionChange = {},
            selectedMuscleGroups = emptySet(),
            onMuscleGroupSelectionChange = {},
            onExerciseSelected = {},
            onCreateCustomExercise = {},
            onDismiss = {}
        )
    }
} 