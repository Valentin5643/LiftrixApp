package com.example.liftrix.ui.progress.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.MuscleGroup

/**
 * Exercise filter bottom sheet component
 * 
 * Features:
 * - Search functionality with real-time filtering
 * - Exercise categories with sticky headers
 * - Multi-select with checkboxes
 * - Recently used exercises section
 * - Clear all selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseFilterSheet(
    exercises: List<ExerciseLibrary>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Exercises",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(
                    onClick = { onSelectionChange(emptySet()) }
                ) {
                    Text("Clear All")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search exercises") },
                leadingIcon = { 
                    Icon(Icons.Default.Search, contentDescription = null) 
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Selection summary
            if (selectedIds.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "${selectedIds.size} exercises selected",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Exercise list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                val filteredExercises = exercises.filter { exercise ->
                    exercise.name.contains(searchQuery, ignoreCase = true)
                }
                
                val groupedExercises = filteredExercises.groupBy { it.primaryMuscleGroup }
                
                groupedExercises.forEach { (muscleGroup, exerciseList) ->
                    // Category header
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = muscleGroup.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    
                    // Exercises in category
                    items(exerciseList) { exercise ->
                        ExerciseSelectionItem(
                            exercise = exercise,
                            isSelected = exercise.id in selectedIds,
                            onToggle = { isSelected ->
                                val newSelection = if (isSelected) {
                                    selectedIds + exercise.id
                                } else {
                                    selectedIds - exercise.id
                                }
                                onSelectionChange(newSelection)
                            }
                        )
                    }
                }
                
                // Empty state
                if (filteredExercises.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No exercises found",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Try adjusting your search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual exercise selection item with checkbox
 */
@Composable
private fun ExerciseSelectionItem(
    exercise: ExerciseLibrary,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (!exercise.instructions.isNullOrEmpty()) {
                Text(
                    text = exercise.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}