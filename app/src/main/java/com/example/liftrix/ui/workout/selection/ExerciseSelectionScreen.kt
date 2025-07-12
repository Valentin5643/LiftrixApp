package com.example.liftrix.ui.workout.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.creation.components.ExerciseSearchField
import com.example.liftrix.ui.workout.creation.components.ExercisePreviewCard
import com.example.liftrix.ui.workout.creation.components.UnifiedFilterChips



/**
 * Full-screen exercise selection with comprehensive filtering and search
 * Replaces the bottom sheet implementation with proper navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    onNavigateBack: () -> Unit,
    onExerciseSelected: (ExerciseLibrary) -> Unit,
    onCreateCustomExercise: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExerciseSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Select Exercise",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        
        if (uiState.isLoading) {
            // Show loading indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Text(
                        text = "Loading exercises...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (uiState.error != null) {
            // Show error state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Error loading exercises",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            ExerciseSelectionContent(
                exercises = uiState.filteredExercises,
                recentExercises = uiState.recentExercises,
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                selectedEquipment = uiState.selectedEquipment,
                onEquipmentSelectionChange = viewModel::updateSelectedEquipment,
                selectedMuscleGroups = uiState.selectedMuscleGroups,
                onMuscleGroupSelectionChange = viewModel::updateSelectedMuscleGroups,
                onExerciseSelected = { searchableExercise ->
                    // Convert SearchableExercise to ExerciseLibrary for the navigation callback
                    when (searchableExercise) {
                        is SearchableExercise.LibraryExercise -> {
                            onExerciseSelected(searchableExercise.exercise)
                        }
                        is SearchableExercise.CustomExercise -> {
                            // Convert custom exercise to ExerciseLibrary format for compatibility
                            val customExercise = searchableExercise.exercise
                            val exerciseLibrary = ExerciseLibrary(
                                id = customExercise.id.value,
                                name = customExercise.name,
                                primaryMuscleGroup = customExercise.primaryMuscle,
                                equipment = customExercise.equipment,
                                secondaryMuscleGroups = emptyList(),
                                movementPattern = "Custom Exercise",
                                difficultyLevel = 3,
                                instructions = customExercise.notes ?: "Custom exercise added by user",
                                isCompound = false,
                                searchableTerms = listOf(customExercise.name.lowercase())
                            )
                            onExerciseSelected(exerciseLibrary)
                        }
                    }
                },
                onCreateCustomExercise = onCreateCustomExercise,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    } // Close Scaffold
}

@Composable
private fun ExerciseSelectionContent(
    exercises: List<SearchableExercise>,
    recentExercises: List<SearchableExercise>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedEquipment: Set<Equipment>,
    onEquipmentSelectionChange: (Set<Equipment>) -> Unit,
    selectedMuscleGroups: Set<ExerciseCategory>,
    onMuscleGroupSelectionChange: (Set<ExerciseCategory>) -> Unit,
    onExerciseSelected: (SearchableExercise) -> Unit,
    onCreateCustomExercise: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    // Use LazyColumn for proper scrolling with bounded height
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search field
        item {
            ExerciseSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                focusRequester = focusRequester,
                onClearFocus = { focusManager.clearFocus() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Unified filter chips (Equipment + Muscle Groups)
        if (selectedEquipment.isNotEmpty() || selectedMuscleGroups.isNotEmpty() || searchQuery.isBlank()) {
            item {
                Column {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    UnifiedFilterChips(
                        selectedEquipment = selectedEquipment,
                        onEquipmentSelectionChange = onEquipmentSelectionChange,
                        selectedMuscleGroups = selectedMuscleGroups,
                        onMuscleGroupSelectionChange = onMuscleGroupSelectionChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Recent exercises section (show when no search/filters applied)
        if (searchQuery.isBlank() && selectedEquipment.isEmpty() && selectedMuscleGroups.isEmpty() && recentExercises.isNotEmpty()) {
            item {
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
            }
            
            // Recent exercises items
            items(recentExercises.take(5)) { searchableExercise ->
                ExercisePreviewCard(
                    exercise = searchableExercise,
                    onClick = { onExerciseSelected(searchableExercise) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            item {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
        
        // Exercise list header
        item {
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
        }
        
        
        // Exercise list or empty state
        if (exercises.isEmpty()) {
            item {
                EmptyExerciseState(
                    searchQuery = searchQuery,
                    hasFilters = selectedEquipment.isNotEmpty() || selectedMuscleGroups.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
        } else {
            
            // Render exercises using LazyColumn items
            items(exercises) { searchableExercise ->
                ExercisePreviewCard(
                    exercise = searchableExercise,
                    onClick = { onExerciseSelected(searchableExercise) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // Auto-focus search field when screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
private fun ExerciseSelectionScreenPreview() {
    val sampleExercises = listOf(
        SearchableExercise.LibraryExercise(
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
            )
        ),
        SearchableExercise.LibraryExercise(
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
    )
    
    LiftrixTheme {
        ExerciseSelectionContent(
            exercises = sampleExercises,
            recentExercises = sampleExercises.take(2),
            searchQuery = "",
            onSearchQueryChange = {},
            selectedEquipment = emptySet(),
            onEquipmentSelectionChange = {},
            selectedMuscleGroups = emptySet(),
            onMuscleGroupSelectionChange = {},
            onExerciseSelected = {},
            onCreateCustomExercise = {}
        )
    }
}