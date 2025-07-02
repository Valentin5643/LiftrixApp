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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.liftrix.ui.workout.creation.components.UnifiedFilterChips

/**
 * Data class for type-safe exercise item rendering
 */
private data class ExerciseItemData(
    val name: String,
    val primaryMuscle: String,
    val equipment: Equipment,
    val isCustom: Boolean
)

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
                    is SearchableExercise.LibraryExercise -> onExerciseSelected(searchableExercise.exercise)
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
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Search field
        ExerciseSearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            focusRequester = focusRequester,
            onClearFocus = { focusManager.clearFocus() },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Unified filter chips (Equipment + Muscle Groups)
        if (selectedEquipment.isNotEmpty() || selectedMuscleGroups.isNotEmpty() || searchQuery.isBlank()) {
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
            
            Spacer(modifier = Modifier.height(12.dp))
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
            
            recentExercises.take(5).forEach { searchableExercise ->
                ExerciseListItem(
                    searchableExercise = searchableExercise,
                    onClick = { onExerciseSelected(searchableExercise) },
                    isRecent = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
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
        if (exercises.isEmpty()) {
            EmptyExerciseState(
                searchQuery = searchQuery,
                hasFilters = selectedEquipment.isNotEmpty() || selectedMuscleGroups.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            )
        } else {
            // Render exercises in regular Column (since parent Column has scroll)
            exercises.forEach { searchableExercise ->
                ExerciseListItem(
                    searchableExercise = searchableExercise,
                    onClick = { onExerciseSelected(searchableExercise) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Bottom padding
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Auto-focus search field when screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseListItem(
    searchableExercise: SearchableExercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRecent: Boolean = false
) {
    val (name, primaryMuscle, equipment, isCustom) = when (searchableExercise) {
        is SearchableExercise.LibraryExercise -> {
            val exercise = searchableExercise.exercise
            ExerciseItemData(exercise.name, exercise.primaryMuscleGroup.displayName, exercise.equipment, false)
        }
        is SearchableExercise.CustomExercise -> {
            val exercise = searchableExercise.exercise
            ExerciseItemData(exercise.name, exercise.primaryMuscle.displayName, exercise.equipment, true)
        }
    }
    
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isRecent) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isRecent) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Exercise thumbnail
            ExerciseThumbnail(
                equipment = equipment,
                modifier = Modifier.size(48.dp)
            )
            
            // Exercise details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = primaryMuscle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isCustom) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.semantics {
                                contentDescription = "Custom exercise"
                            }
                        ) {
                            Text(
                                text = "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Equipment indicator
            Text(
                text = equipment.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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