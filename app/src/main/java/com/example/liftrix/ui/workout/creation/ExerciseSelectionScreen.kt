package com.example.liftrix.ui.workout.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import com.example.liftrix.ui.components.SearchableExerciseSelector
import com.example.liftrix.domain.usecase.exercise.ExerciseGroup
import com.example.liftrix.ui.workout.components.ExerciseSearchWithVariations

/**
 * Screen for selecting exercises with variations and custom exercise creation
 * 
 * @param onNavigateBack Callback when back navigation is triggered
 * @param onExerciseSelected Callback when an exercise is selected
 * @param onNavigateToCustomExerciseCreation Callback to navigate to custom exercise creation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSelectionScreen(
    onNavigateBack: () -> Unit,
    onExerciseSelected: (SearchableExercise) -> Unit,
    onNavigateToCustomExerciseCreation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedExercise by remember { mutableStateOf<SearchableExercise?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Select Exercise",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { 
                            contentDescription = "Navigate back" 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCustomExerciseCreation,
                modifier = Modifier.semantics { 
                    contentDescription = "Create custom exercise" 
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Exercise")
            }
        }
    ) { paddingValues ->
        ExerciseSelectionContent(
            uiState = uiState,
            searchQuery = searchQuery,
            selectedExercise = selectedExercise,
            isSearchExpanded = isSearchExpanded,
            onSearchQueryChanged = { query: String ->
                searchQuery = query
                viewModel.searchExercises(query)
            },
            onExerciseSelected = { exercise: SearchableExercise? ->
                selectedExercise = exercise
                if (exercise != null) {
                    onExerciseSelected(exercise)
                }
            },
            onSearchExpandedChanged = { expanded: Boolean ->
                isSearchExpanded = expanded
            },
            onEquipmentFilterChanged = { equipment: Set<Equipment> ->
                viewModel.updateEquipmentFilter(equipment)
            },
            onToggleEquipmentFilter = {
                viewModel.toggleEquipmentFilter()
            },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun ExerciseSelectionContent(
    uiState: ExerciseSelectionUiState,
    searchQuery: String,
    selectedExercise: SearchableExercise?,
    isSearchExpanded: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onExerciseSelected: (SearchableExercise?) -> Unit,
    onSearchExpandedChanged: (Boolean) -> Unit,
    onEquipmentFilterChanged: (Set<Equipment>) -> Unit,
    onToggleEquipmentFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Section
        SearchableExerciseSelector(
            exercises = uiState.exercises,
            searchQuery = searchQuery,
            selectedExercise = selectedExercise,
            isExpanded = isSearchExpanded,
            onSearchQueryChanged = onSearchQueryChanged,
            onExerciseSelected = onExerciseSelected,
            onExpandedChanged = onSearchExpandedChanged
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Equipment Filter Section
        EquipmentFilterSection(
            selectedEquipment = uiState.selectedEquipment,
            isExpanded = uiState.isEquipmentFilterExpanded,
            onEquipmentChanged = onEquipmentFilterChanged,
            onToggleExpanded = onToggleEquipmentFilter
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Results Section
        when {
            uiState.isLoading -> {
                LoadingContent()
            }
            uiState.exercises.isEmpty() && searchQuery.isNotEmpty() -> {
                EmptySearchResults(searchQuery = searchQuery)
            }
            uiState.exercises.isNotEmpty() -> {
                ExerciseResultsWithVariations(
                    exerciseGroups = uiState.exerciseGroups,
                    onExerciseSelected = onExerciseSelected
                )
            }
            else -> {
                InitialContent()
            }
        }
    }
}

@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Searching exercises...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptySearchResults(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No exercises found for \"$searchQuery\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try a different search term or create a custom exercise",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExerciseResultsWithVariations(
    exerciseGroups: List<ExerciseGroup>,
    onExerciseSelected: (SearchableExercise?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Show variations grouped by movement pattern
        if (exerciseGroups.size > 1 || exerciseGroups.any { it.libraryVariations.size + it.customVariations.size > 1 }) {
            Text(
                text = "Exercise Variations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ExerciseSearchWithVariations(
                searchResults = exerciseGroups,
                onVariationSelected = { exercise: SearchableExercise -> onExerciseSelected(exercise) }
            )
        } else {
            // Fallback to simple list if no meaningful grouping
            val allExercises = exerciseGroups.flatMap { group ->
                group.libraryVariations.map { SearchableExercise.LibraryExercise(it) } +
                group.customVariations.map { SearchableExercise.CustomExercise(it) }
            }
            ExerciseResults(
                exercises = allExercises,
                onExerciseSelected = onExerciseSelected
            )
        }
    }
}

@Composable
private fun ExerciseResults(
    exercises: List<SearchableExercise>,
    onExerciseSelected: (SearchableExercise?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(exercises) { exercise ->
            ExerciseResultCard(
                exercise = exercise,
                onClick = { onExerciseSelected(exercise) }
            )
        }
    }
}

@Composable
private fun ExerciseResultCard(
    exercise: SearchableExercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { 
                contentDescription = "Select ${exercise.name}" 
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
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
        }
    }
}

@Composable
private fun EquipmentFilterSection(
    selectedEquipment: Set<Equipment>,
    isExpanded: Boolean,
    onEquipmentChanged: (Set<Equipment>) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Filter toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Equipment Filter",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.semantics { 
                    contentDescription = if (isExpanded) "Collapse equipment filter" else "Expand equipment filter"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null
                )
            }
        }
        
        // Equipment chips
        if (isExpanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(Equipment.values()) { equipment ->
                    FilterChip(
                        onClick = {
                            val newSelection = if (selectedEquipment.contains(equipment)) {
                                selectedEquipment - equipment
                            } else {
                                selectedEquipment + equipment
                            }
                            onEquipmentChanged(newSelection)
                        },
                        label = {
                            Text(
                                text = equipment.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        selected = selectedEquipment.contains(equipment),
                        modifier = Modifier.semantics {
                            contentDescription = "${equipment.displayName} filter ${if (selectedEquipment.contains(equipment)) "selected" else "not selected"}"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InitialContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Search for exercises above",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Type to find exercises or create a custom one",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} 