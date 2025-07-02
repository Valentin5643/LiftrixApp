package com.example.liftrix.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.common.LiftrixProgressIndicator
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.format.DateTimeFormatter

/**
 * Modern workout templates dashboard that serves as the main Workout tab.
 * 
 * Key Features:
 * - Template-centric design (no completed workouts shown here)
 * - Quick start workout from templates
 * - Template management (create, edit, duplicate, delete)
 * - Search and filter capabilities
 * - Favorites and usage-based sorting
 * - Modern card-based layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTemplatesDashboard(
    onStartWorkout: (WorkoutTemplate) -> Unit,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit,
    onCreateBlankWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutTemplatesDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.workout_templates_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Search templates"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Search Bar (overlays content when active)
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { query ->
                        searchQuery = query
                        viewModel.searchTemplates(query)
                    },
                    onSearch = { isSearchActive = false },
                    active = isSearchActive,
                    onActiveChange = { active -> 
                        isSearchActive = active
                        if (!active) {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search templates...") }
                ) {
                    // Search results content
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val searchTemplates = when (val state = uiState) {
                            is WorkoutTemplatesDashboardUiState.Success -> state.filteredTemplates
                            else -> emptyList()
                        }
                        items(searchTemplates) { template ->
                            WorkoutTemplateSearchItem(
                                template = template,
                                onStartWorkout = { 
                                    onStartWorkout(template)
                                    isSearchActive = false
                                },
                                onEditTemplate = {
                                    onEditTemplate(template)
                                    isSearchActive = false
                                }
                            )
                        }
                    }
                }
            }
            
            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                
                // Content based on state
                when (val state = uiState) {
                    is WorkoutTemplatesDashboardUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LiftrixProgressIndicator()
                        }
                    }
                    is WorkoutTemplatesDashboardUiState.Empty -> {
                        EmptyTemplatesState(
                            onCreateTemplate = onCreateTemplate,
                            onCreateBlankWorkout = onCreateBlankWorkout,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is WorkoutTemplatesDashboardUiState.Success -> {
                        TemplatesGrid(
                            templates = state.filteredTemplates,
                            onStartWorkout = onStartWorkout,
                            onEditTemplate = onEditTemplate,
                            onDuplicateTemplate = viewModel::duplicateTemplate,
                            onDeleteTemplate = viewModel::deleteTemplate,
                            onToggleFavorite = viewModel::toggleFavorite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is WorkoutTemplatesDashboardUiState.Error -> {
                        ErrorTemplatesState(
                            error = state.error,
                            onRetry = viewModel::retry,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Quick Actions Bar
                QuickActionsBar(
                    onCreateBlankWorkout = onCreateBlankWorkout,
                    onCreateTemplate = onCreateTemplate,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}


/**
 * Grid layout for workout templates
 */
@Composable
private fun TemplatesGrid(
    templates: List<WorkoutTemplate>,
    onStartWorkout: (WorkoutTemplate) -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit,
    onDuplicateTemplate: (WorkoutTemplate) -> Unit,
    onDeleteTemplate: (WorkoutTemplate) -> Unit,
    onToggleFavorite: (WorkoutTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(300.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        items(templates) { template ->
            WorkoutTemplateCard(
                template = template,
                onStartWorkout = { onStartWorkout(template) },
                onEditTemplate = { onEditTemplate(template) },
                onDuplicateTemplate = { onDuplicateTemplate(template) },
                onDeleteTemplate = { onDeleteTemplate(template) },
                onToggleFavorite = { onToggleFavorite(template) }
            )
        }
    }
}

/**
 * Individual template card component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTemplateCard(
    template: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditTemplate: () -> Unit,
    onDuplicateTemplate: () -> Unit,
    onDeleteTemplate: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with name and favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Favorite button would go here
            }
            
            // Description
            template.description?.let { description ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Template stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${template.exercises.size} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${template.getTotalSets()} sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    template.estimatedDurationMinutes?.let { duration ->
                        Text(
                            text = "${duration}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    template.difficultyLevel?.let { difficulty ->
                        Text(
                            text = "Level $difficulty",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Usage stats
            if (template.usageCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Used ${template.usageCount} times",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                template.lastUsedAt?.let { lastUsed ->
                    Text(
                        text = "Last used ${lastUsed.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onStartWorkout,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }
                
                OutlinedButton(
                    onClick = onEditTemplate,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }
            }
        }
    }
}

/**
 * Search result item component
 */
@Composable
private fun WorkoutTemplateSearchItem(
    template: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${template.exercises.size} exercises • ${template.getTotalSets()} sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onStartWorkout) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                OutlinedButton(onClick = onEditTemplate) {
                    Text("Edit")
                }
            }
        }
    }
}

/**
 * Empty state when no templates exist
 */
@Composable
private fun EmptyTemplatesState(
    onCreateTemplate: () -> Unit,
    onCreateBlankWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Workout Templates",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Create reusable workout templates to quickly start your favorite routines.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onCreateTemplate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Template")
            }
            
            OutlinedButton(
                onClick = onCreateBlankWorkout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Blank Workout")
            }
        }
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorTemplatesState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Unable to Load Templates",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        FilledTonalButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/**
 * Quick actions bar at the bottom
 */
@Composable
private fun QuickActionsBar(
    onCreateBlankWorkout: () -> Unit,
    onCreateTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCreateBlankWorkout,
                modifier = Modifier.weight(1f)
            ) {
                Text("Quick Workout")
            }
            
            FilledTonalButton(
                onClick = onCreateTemplate,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Template")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
private fun WorkoutTemplatesDashboardPreview() {
    LiftrixTheme {
        // Preview would show mock data
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Workout Templates Dashboard Preview")
        }
    }
}