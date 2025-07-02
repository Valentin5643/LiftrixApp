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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.common.LiftrixProgressIndicator
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.domain.model.ActiveWorkoutSession
import java.time.format.DateTimeFormatter

/**
 * Enhanced workout tab that provides modern template-centric workflow.
 * 
 * Key features:
 * - Template-first design with quick start options
 * - Modern card-based layout with clear visual hierarchy
 * - Fast access to custom workout creation
 * - Search and filter capabilities for templates
 * - Template management and editing functionality
 * 
 * This replaces the legacy WorkoutScreen with a modern approach focused on reusable templates
 * and streamlined workout creation flow.
 * 
 * @param onStartCustomWorkout Callback to start a blank custom workout
 * @param onStartFromTemplate Callback to start workout from a template
 * @param onCreateTemplate Callback to create a new template
 * @param onEditTemplate Callback to edit an existing template
 * @param onNavigateToActiveWorkout Callback to navigate to active workout screen
 * @param modifier Modifier for styling
 * @param viewModel ViewModel for template management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedWorkoutTab(
    onStartCustomWorkout: () -> Unit,
    onStartFromTemplate: (WorkoutTemplate) -> Unit,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit,
    onNavigateToActiveWorkout: () -> Unit,
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
                        text = "Workouts",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartCustomWorkout,
                modifier = Modifier.semantics {
                    contentDescription = "Create custom workout"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Workout")
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Search overlay
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
                    placeholder = { Text("Search workout templates...") }
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
                            TemplateSearchItem(
                                template = template,
                                onStartWorkout = { 
                                    onStartFromTemplate(template)
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
                // Quick actions section
                QuickActionsSection(
                    onStartCustomWorkout = onStartCustomWorkout,
                    onCreateTemplate = onCreateTemplate,
                    modifier = Modifier.padding(16.dp)
                )
                
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
                        EmptyTemplatesContent(
                            onCreateTemplate = onCreateTemplate,
                            onStartCustomWorkout = onStartCustomWorkout,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is WorkoutTemplatesDashboardUiState.Success -> {
                        TemplatesSection(
                            templates = state.filteredTemplates,
                            onStartFromTemplate = onStartFromTemplate,
                            onEditTemplate = onEditTemplate,
                            onDuplicateTemplate = viewModel::duplicateTemplate,
                            onDeleteTemplate = viewModel::deleteTemplate,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    is WorkoutTemplatesDashboardUiState.Error -> {
                        ErrorContent(
                            error = state.error,
                            onRetry = viewModel::retry,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick actions section for immediate workout creation
 */
@Composable
private fun QuickActionsSection(
    onStartCustomWorkout: () -> Unit,
    onCreateTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Start",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onStartCustomWorkout,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Start custom workout immediately"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Workout")
                }
                
                OutlinedButton(
                    onClick = onCreateTemplate,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Create new workout template"
                        }
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
}

/**
 * Templates section with grid layout
 */
@Composable
private fun TemplatesSection(
    templates: List<WorkoutTemplate>,
    onStartFromTemplate: (WorkoutTemplate) -> Unit,
    onEditTemplate: (WorkoutTemplate) -> Unit,
    onDuplicateTemplate: (WorkoutTemplate) -> Unit,
    onDeleteTemplate: (WorkoutTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Templates",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${templates.size} templates",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Templates grid
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp
        ) {
            items(templates) { template ->
                EnhancedTemplateCard(
                    template = template,
                    onStartWorkout = { onStartFromTemplate(template) },
                    onEditTemplate = { onEditTemplate(template) },
                    onDuplicateTemplate = { onDuplicateTemplate(template) },
                    onDeleteTemplate = { onDeleteTemplate(template) }
                )
            }
        }
    }
}

/**
 * Enhanced template card with modern design
 */
@Composable
private fun EnhancedTemplateCard(
    template: WorkoutTemplate,
    onStartWorkout: () -> Unit,
    onEditTemplate: () -> Unit,
    onDuplicateTemplate: () -> Unit,
    onDeleteTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Template: ${template.name}, ${template.exercises.size} exercises"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Description
            template.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${template.exercises.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${template.getTotalSets()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${template.estimatedDurationMinutes ?: 45}m",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "duration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
 * Template search result item
 */
@Composable
private fun TemplateSearchItem(
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
                    text = "${template.exercises.size} exercises • ${template.getTotalSets()} sets • ${template.estimatedDurationMinutes ?: 45}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onStartWorkout) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start workout",
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
private fun EmptyTemplatesContent(
    onCreateTemplate: () -> Unit,
    onStartCustomWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Templates Yet",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Create reusable workout templates to quickly start your favorite routines, or begin with a custom workout.",
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
                onClick = onStartCustomWorkout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Custom Workout")
            }
            
            OutlinedButton(
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
        }
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorContent(
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

@Preview(showBackground = true)
@Composable
private fun EnhancedWorkoutTabPreview() {
    LiftrixTheme {
        EnhancedWorkoutTab(
            onStartCustomWorkout = {},
            onStartFromTemplate = {},
            onCreateTemplate = {},
            onEditTemplate = {},
            onNavigateToActiveWorkout = {}
        )
    }
}