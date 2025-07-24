package com.example.liftrix.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.settings.components.*
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Dashboard customization screen - central interface for widget management
 * 
 * This screen provides a comprehensive interface for users to customize their analytics dashboard.
 * Following Material 3 design principles with full accessibility support and real-time preview updates.
 * 
 * Features:
 * - Category-based widget organization with collapsible sections
 * - Real-time preview updates showing changes immediately
 * - Optimistic UI updates with error handling and rollback capability
 * - Batch operations for enabling/disabling multiple widgets efficiently
 * - Layout mode selection with visual previews and descriptions
 * - Widget preview grid showing mini widget previews with current data
 * - Auto-save functionality with manual save fallback
 * 
 * @param onNavigateBack Callback to navigate back from customization screen
 * @param modifier Modifier for styling the screen
 * @param viewModel WidgetSettingsViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetSettingsViewModel = hiltViewModel()
) {
    // Performance monitoring for dashboard customization screen
    PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation(
        key = "DashboardCustomizationScreen"
    ) {
        val uiState by viewModel.uiState.collectAsState()
        
        // Stable callbacks to prevent unnecessary recompositions
        val stableOnEvent = remember(viewModel) { viewModel::handleEvent }
        val stableOnNavigateBack = remember(onNavigateBack) { onNavigateBack }
        
        // Enable auto-save functionality
        LaunchedEffect(viewModel) {
            viewModel.enableAutoSave()
        }
        
        Column(
            modifier = modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Dashboard customization screen for personalizing widget layout and settings"
                }
        ) {
            // Top App Bar with save actions
            TopAppBar(
                title = {
                    Text(
                        text = "Customize Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = stableOnNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to progress dashboard"
                        )
                    }
                },
                actions = {
                    DashboardCustomizationActions(
                        uiState = uiState,
                        onEvent = stableOnEvent
                    )
                }
            )
            
            // Main customization content
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
                key = "DashboardCustomizationContent"
            ) {
                when (uiState) {
                    is UiState.Loading -> {
                        CustomizationLoadingState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is UiState.Error -> {
                        CustomizationErrorState(
                            error = (uiState as UiState.Error).error,
                            onRetry = { stableOnEvent(WidgetSettingsEvent.RetryLastAction) },
                            onDismiss = { stableOnEvent(WidgetSettingsEvent.DismissError) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is UiState.Success -> {
                        DashboardCustomizationContent(
                            data = (uiState as UiState.Success).data,
                            onEvent = stableOnEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is UiState.Empty -> {
                        CustomizationEmptyState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    else -> {
                        CustomizationErrorState(
                            error = com.example.liftrix.domain.model.error.LiftrixError.UnknownError("Unknown UI state"),
                            onRetry = { stableOnEvent(WidgetSettingsEvent.RetryLastAction) },
                            onDismiss = { stableOnEvent(WidgetSettingsEvent.DismissError) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top app bar actions for dashboard customization
 */
@Composable
private fun DashboardCustomizationActions(
    uiState: UiState<WidgetSettingsData>,
    onEvent: (WidgetSettingsEvent) -> Unit
) {
    val hasUnsavedChanges = uiState.dataOrNull()?.hasUnsavedChanges ?: false
    
    // Manual save button when there are unsaved changes
    if (hasUnsavedChanges) {
        TextButton(
            onClick = { onEvent(WidgetSettingsEvent.SavePreferences) }
        ) {
            Text(
                text = "Save",
                fontWeight = FontWeight.Medium
            )
        }
    }
    
    // Quick reset button
    IconButton(
        onClick = { onEvent(WidgetSettingsEvent.ResetToDefaults) }
    ) {
        Icon(
            imageVector = Icons.Default.RestartAlt,
            contentDescription = "Reset to default configuration"
        )
    }
    
    // More options menu
    var showMoreMenu by remember { mutableStateOf(false) }
    
    Box {
        IconButton(
            onClick = { showMoreMenu = true }
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More customization options"
            )
        }
        
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Fix Migration Issues") },
                onClick = {
                    onEvent(WidgetSettingsEvent.FixWidgetMigration)
                    showMoreMenu = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Build, contentDescription = null)
                }
            )
        }
    }
}

/**
 * Main customization content with widget organization and preview
 */
@Composable
private fun DashboardCustomizationContent(
    data: WidgetSettingsData,
    onEvent: (WidgetSettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stable callback and organized widgets
    val stableOnEvent = remember(onEvent) { onEvent }
    val widgetsByCategory = remember(data) { 
        AnalyticsWidget.getAllWidgets().groupBy { it.category }
    }
    
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Layout Mode Selection Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Dashboard Layout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LayoutModeSelector(
                        selectedMode = data.preferences.dashboardLayout,
                        onModeSelected = { mode ->
                            stableOnEvent(WidgetSettingsEvent.UpdateLayoutMode(mode))
                        }
                    )
                }
            }
        }
        
        // Widget Categories Section
        items(
            items = WidgetCategory.values(),
            key = { category -> category.name }
        ) { category ->
            val categoryWidgets = widgetsByCategory[category] ?: emptyList()
            
            WidgetToggleSection(
                category = category,
                widgets = categoryWidgets,
                preferences = data.preferences,
                isLoading = data.isLoading,
                onToggle = { widget ->
                    stableOnEvent(WidgetSettingsEvent.ToggleWidget(widget))
                },
                onReorder = { newOrder ->
                    stableOnEvent(WidgetSettingsEvent.ReorderWidgets(newOrder))
                }
            )
        }
        
        // Widget Preview Section
        item {
            val visibleWidgets = data.getOrderedVisibleWidgets().take(6) // Preview first 6
            
            if (visibleWidgets.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Dashboard Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Text(
                                text = "${visibleWidgets.size} active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        WidgetPreviewGrid(
                            widgets = visibleWidgets,
                            layoutMode = data.preferences.dashboardLayout,
                            onWidgetClick = { widget ->
                                stableOnEvent(WidgetSettingsEvent.ShowToast("${widget.displayName} widget preview"))
                            }
                        )
                    }
                }
            }
        }
        
        // Bottom spacing for better scrolling experience
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Loading state for customization screen
 */
@Composable
private fun CustomizationLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Loading Dashboard Settings...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Error state for customization screen
 */
@Composable
private fun CustomizationErrorState(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Customization Error",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Empty state for customization screen
 */
@Composable
private fun CustomizationEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Dashboard,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Customization Options",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Dashboard customization is currently unavailable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Preview for DashboardCustomizationScreen
 */
@Preview(showBackground = true)
@Composable
private fun DashboardCustomizationScreenPreview() {
    LiftrixTheme {
        // Simplified preview without ViewModel integration
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Customize Dashboard",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset"
                            )
                        }
                    }
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        LayoutModeSelector(
                            selectedMode = DashboardLayoutMode.SECTIONS,
                            onModeSelected = { }
                        )
                    }
                }
            }
        }
    }
}