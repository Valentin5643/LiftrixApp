package com.example.liftrix.ui.workout.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import com.example.liftrix.ui.common.state.UiState
import androidx.compose.material3.MaterialTheme
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.creation.components.ExerciseSearchField
import com.example.liftrix.ui.workout.creation.components.EquipmentFilterChips
import timber.log.Timber

/**
 * Custom Exercise List Screen
 * 
 * Comprehensive management interface for user's custom exercises with:
 * - Search and filter functionality
 * - Grid/list view toggle options
 * - Exercise preview with key details
 * - Direct editing and creation actions
 * - Usage statistics (how many times used in workouts)
 * - Sort options (name, created date, usage frequency)
 * - Bulk operations (delete multiple, export)
 * - Empty state with guided onboarding
 * 
 * Design Features:
 * - Modern card-based layout with exercise previews
 * - Efficient lazy loading with pagination
 * - Material 3 design system compliance
 * - Accessibility support with semantic descriptions
 * - Pull-to-refresh functionality
 * - Contextual actions with swipe gestures
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseListScreen(
    onNavigateBack: () -> Unit,
    onCreateExercise: () -> Unit,
    onEditExercise: (String) -> Unit,
    onExerciseSelected: ((String) -> Unit)? = null, // Optional for selection mode
    modifier: Modifier = Modifier,
    viewModel: CustomExerciseListViewModelImpl = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My Exercises",
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
                actions = {
                    // Sort button
                    IconButton(
                        onClick = { showSortDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort exercises"
                        )
                    }
                    
                    // Filter button
                    IconButton(
                        onClick = { showFilterDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter exercises"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LiftrixColorsV2.Dark.BackgroundPrimary,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateExercise,
                containerColor = LiftrixColorsV2.Teal,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics {
                    contentDescription = "Create new custom exercise"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add exercise"
                )
            }
        },
        containerColor = LiftrixColorsV2.Dark.BackgroundPrimary
    ) { paddingValues ->
        
        when (val state = uiState) {
            UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                        Text(
                            text = "Loading your exercises...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = state.error.message ?: "Failed to load exercises",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        SecondaryActionButton(
                            text = "Try Again",
                            onClick = { viewModel.handleEvent(CustomExerciseListEvent.LoadExercises) }
                        )
                    }
                }
            }
            
            is UiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "No exercises",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        val actionText = state.actionText
                        if (state.showAction && actionText != null) {
                            PrimaryActionButton(
                                text = actionText,
                                onClick = onCreateExercise,
                                leadingIcon = Icons.Default.Add
                            )
                        }
                    }
                }
            }
            
            is UiState.Success -> {
                val exercises = state.data.exercises
                val searchQuery = state.data.searchQuery
                val selectedFilters = state.data.selectedFilters
                val sortOption = state.data.sortOption
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Search and filters section
                    Column(
                        modifier = Modifier.padding(horizontal = LiftrixSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
                    ) {
                        // Search field
                        ExerciseSearchField(
                            query = searchQuery,
                            onQueryChange = { viewModel.handleEvent(CustomExerciseListEvent.UpdateSearchQuery(it)) },
                            placeholder = "Search your exercises...",
                            focusRequester = androidx.compose.ui.focus.FocusRequester(),
                            onClearFocus = { /* Handle focus clear if needed */ },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Active filters display
                        if (selectedFilters.hasActiveFilters() || sortOption != CustomExerciseSortOption.NAME) {
                            ActiveFiltersDisplay(
                                filters = selectedFilters,
                                sortOption = sortOption,
                                onClearFilter = { viewModel.handleEvent(CustomExerciseListEvent.ClearFilter) },
                                onClearSort = { viewModel.handleEvent(CustomExerciseListEvent.SetSortOption(CustomExerciseSortOption.NAME)) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(LiftrixSpacing.medium))
                    
                    // Exercise list or empty state
                    if (exercises.isEmpty()) {
                        EmptyExerciseState(
                            hasSearchQuery = searchQuery.isNotBlank(),
                            onCreateExercise = onCreateExercise,
                            onClearSearch = { viewModel.handleEvent(CustomExerciseListEvent.UpdateSearchQuery("")) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = LiftrixSpacing.medium,
                                vertical = LiftrixSpacing.small
                            ),
                            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
                        ) {
                            items(
                                items = exercises,
                                key = { exercise -> exercise.id.value }
                            ) { exercise ->
                                CustomExerciseCard(
                                    exercise = exercise,
                                    onClick = {
                                        if (onExerciseSelected != null) {
                                            onExerciseSelected(exercise.id.value)
                                        } else {
                                            onEditExercise(exercise.id.value)
                                        }
                                    },
                                    onEdit = { onEditExercise(exercise.id.value) },
                                    onDelete = { viewModel.handleEvent(CustomExerciseListEvent.DeleteExercise(exercise.id.value)) },
                                    showSelectionMode = onExerciseSelected != null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // Fallback for any unexpected state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                }
            }
        }
    }
    
    // Sort dialog
    if (showSortDialog) {
        SortOptionsDialog(
            currentSort = when (val state = uiState) {
                is UiState.Success -> state.data.sortOption
                else -> CustomExerciseSortOption.NAME
            },
            onSortSelected = { sortOption ->
                viewModel.handleEvent(CustomExerciseListEvent.SetSortOption(sortOption))
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }
    
    // Filter dialog
    if (showFilterDialog) {
        FilterOptionsDialog(
            currentFilters = when (val state = uiState) {
                is UiState.Success -> state.data.selectedFilters
                else -> CustomExerciseFilters()
            },
            onFiltersChanged = { filters ->
                viewModel.handleEvent(CustomExerciseListEvent.SetFilters(filters))
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

/**
 * Individual custom exercise card component.
 */
@Composable
private fun CustomExerciseCard(
    exercise: CustomExercise,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showSelectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .semantics {
                contentDescription = "Custom exercise: ${exercise.name}. " +
                        "Type: ${exercise.exerciseType.name.replace("_", " ").lowercase()}. " +
                        "Primary muscle: ${exercise.primaryMuscle.name.lowercase()}. " +
                        if (showSelectionMode) "Tap to select" else "Tap to edit"
            },
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.BackgroundSecondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            // Exercise image or type icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LiftrixColorsV2.Dark.BackgroundTertiary),
                contentAlignment = Alignment.Center
            ) {
                if (!exercise.mainImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(exercise.mainImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Exercise image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = getIconForExerciseType(exercise.exerciseType),
                        contentDescription = exercise.exerciseType.name.replace("_", " ").lowercase(),
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // Exercise details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Exercise name
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Exercise type and primary muscle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.exerciseType.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = LiftrixColorsV2.Teal
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = exercise.primaryMuscle.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Equipment and description
                if (exercise.equipment != null || !exercise.description.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (exercise.equipment != null) {
                            Surface(
                                color = LiftrixColorsV2.Dark.BackgroundTertiary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = exercise.equipment!!.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        if (!exercise.description.isNullOrBlank()) {
                            Text(
                                text = exercise.description!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Action buttons
            if (!showSelectionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit exercise",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete exercise",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View exercise",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Empty state component for when no exercises exist or match search criteria.
 */
@Composable
private fun EmptyExerciseState(
    hasSearchQuery: Boolean,
    onCreateExercise: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            Icon(
                imageVector = if (hasSearchQuery) Icons.Default.SearchOff else Icons.Default.FitnessCenter,
                contentDescription = if (hasSearchQuery) "No results" else "No custom exercises",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = if (hasSearchQuery) "No exercises found" else "No custom exercises yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = if (hasSearchQuery) {
                    "Try adjusting your search or filters to find exercises"
                } else {
                    "Create your first custom exercise to get started"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Spacer(modifier = Modifier.height(LiftrixSpacing.small))
            
            if (hasSearchQuery) {
                SecondaryActionButton(
                    text = "Clear Search",
                    onClick = onClearSearch
                )
            }
            
            PrimaryActionButton(
                text = "Create Exercise",
                onClick = onCreateExercise,
                leadingIcon = Icons.Default.Add
            )
        }
    }
}

/**
 * Active filters display component.
 */
@Composable
private fun ActiveFiltersDisplay(
    filters: CustomExerciseFilters,
    sortOption: CustomExerciseSortOption,
    onClearFilter: () -> Unit,
    onClearSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (sortOption != CustomExerciseSortOption.NAME) {
            FilterChip(
                onClick = onClearSort,
                label = { Text("Sort: ${sortOption.displayName}") },
                selected = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear sort",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        
        if (filters.hasActiveFilters()) {
            FilterChip(
                onClick = onClearFilter,
                label = { Text("Filters (${filters.getActiveFilterCount()})") },
                selected = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear filters",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}

/**
 * Gets appropriate icon for exercise type.
 */
private fun getIconForExerciseType(type: ExerciseType): ImageVector {
    return when (type) {
        ExerciseType.WEIGHT_BASED -> Icons.Default.FitnessCenter
        ExerciseType.BODYWEIGHT -> Icons.Default.DirectionsRun
        ExerciseType.TIME_BASED -> Icons.Default.Timer
        ExerciseType.DISTANCE_BASED -> Icons.Default.Straighten
        ExerciseType.CARDIO -> Icons.Default.Favorite
        ExerciseType.HYBRID -> Icons.Default.Tune
        else -> Icons.Default.FitnessCenter
    }
}

/**
 * Sort options dialog.
 */
@Composable
private fun SortOptionsDialog(
    currentSort: CustomExerciseSortOption,
    onSortSelected: (CustomExerciseSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Exercises") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomExerciseSortOption.values().forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSelected(option) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSort == option,
                            onClick = { onSortSelected(option) }
                        )
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Filter options dialog.
 */
@Composable
private fun FilterOptionsDialog(
    currentFilters: CustomExerciseFilters,
    onFiltersChanged: (CustomExerciseFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var tempFilters by remember { mutableStateOf(currentFilters) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Exercises") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Exercise type filters
                Text(
                    text = "Exercise Types",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                ExerciseType.values().take(6).forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempFilters = if (tempFilters.exerciseTypes.contains(type)) {
                                    tempFilters.copy(exerciseTypes = tempFilters.exerciseTypes - type)
                                } else {
                                    tempFilters.copy(exerciseTypes = tempFilters.exerciseTypes + type)
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tempFilters.exerciseTypes.contains(type),
                            onCheckedChange = { checked ->
                                tempFilters = if (checked) {
                                    tempFilters.copy(exerciseTypes = tempFilters.exerciseTypes + type)
                                } else {
                                    tempFilters.copy(exerciseTypes = tempFilters.exerciseTypes - type)
                                }
                            }
                        )
                        Text(
                            text = type.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onFiltersChanged(tempFilters)
                    onDismiss()
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Data classes for managing exercise list state.
 */
data class CustomExerciseListState(
    val exercises: List<CustomExercise> = emptyList(),
    val searchQuery: String = "",
    val selectedFilters: CustomExerciseFilters = CustomExerciseFilters(),
    val sortOption: CustomExerciseSortOption = CustomExerciseSortOption.NAME,
    val isLoading: Boolean = false
)

data class CustomExerciseFilters(
    val exerciseTypes: Set<ExerciseType> = emptySet(),
    val muscleGroups: Set<ExerciseCategory> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
    val hasImages: Boolean? = null
) {
    fun hasActiveFilters(): Boolean {
        return exerciseTypes.isNotEmpty() || 
               muscleGroups.isNotEmpty() || 
               equipment.isNotEmpty() || 
               hasImages != null
    }
    
    fun getActiveFilterCount(): Int {
        var count = 0
        if (exerciseTypes.isNotEmpty()) count++
        if (muscleGroups.isNotEmpty()) count++
        if (equipment.isNotEmpty()) count++
        if (hasImages != null) count++
        return count
    }
}

enum class CustomExerciseSortOption(val displayName: String) {
    NAME("Name (A-Z)"),
    CREATED_DATE("Recently Created"),
    LAST_MODIFIED("Recently Modified"),
    USAGE_COUNT("Most Used"),
    EXERCISE_TYPE("Exercise Type")
}

/**
 * ViewModel interface placeholder for the list screen.
 */
interface CustomExerciseListViewModel {
    val uiState: kotlinx.coroutines.flow.StateFlow<UiState<CustomExerciseListState>>
    fun loadExercises()
    fun updateSearchQuery(query: String)
    fun setSortOption(option: CustomExerciseSortOption)
    fun setFilters(filters: CustomExerciseFilters)
    fun clearFilter()
    fun deleteExercise(exerciseId: String)
}

@Preview(showBackground = true)
@Composable
private fun CustomExerciseListScreenPreview() {
    LiftrixTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LiftrixColorsV2.Dark.BackgroundPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Custom Exercise List Screen Preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
