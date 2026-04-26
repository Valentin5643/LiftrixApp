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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.PerformanceOptimizations
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.dataOrNull
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.settings.components.*
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Comprehensive widget customization screen for dashboard widget management.
 * 
 * This screen provides a complete interface for users to customize their dashboard
 * widgets including visibility toggles, layout mode selection, drag-and-drop reordering,
 * and user experience level configuration. It follows Material 3 design principles
 * with full accessibility support.
 * 
 * Features:
 * - Widget visibility toggles with real-time preview
 * - Dashboard layout mode selection with visual previews
 * - Drag-and-drop widget reordering (UI ready, functionality can be extended)
 * - User experience level selector with automatic widget recommendations
 * - Reset to defaults functionality
 * - Auto-save with manual save option
 * - Comprehensive loading and error states
 * - Material 3 design with accessibility support
 * 
 * @param onNavigateBack Callback to navigate back to settings
 * @param modifier Modifier for styling the screen
 * @param viewModel WidgetSettingsViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetSettingsViewModel = hiltViewModel()
) {
    // Performance monitoring for widget settings screen
    PerformanceOptimizations.AnimationPerformanceMonitor.MonitorAnimation(
        key = "WidgetSettingsScreen"
    ) {
        val uiState by viewModel.uiState.collectAsState()
        
        // Stable callbacks to prevent unnecessary recompositions
        val stableOnEvent = remember(viewModel) { viewModel::handleEvent }
        val stableOnNavigateBack = remember(onNavigateBack) { onNavigateBack }
        
        // Auto-save is handled internally by the ViewModel
        
        Column(
            modifier = modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Widget settings screen for customizing dashboard widgets"
                }
        ) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Widget Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = stableOnNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to settings"
                        )
                    }
                },
                actions = {
                    // Manual save button when there are unsaved changes
                    val hasUnsavedChanges = uiState.dataOrNull()?.hasUnsavedChanges ?: false
                    if (hasUnsavedChanges) {
                        TextButton(
                            onClick = { stableOnEvent(WidgetSettingsEvent.SavePreferences) }
                        ) {
                            Text("Save")
                        }
                    }
                    
                    // Fix widget migration button
                    IconButton(
                        onClick = { stableOnEvent(WidgetSettingsEvent.FixWidgetMigration) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Fix widget preferences"
                        )
                    }
                    
                    // Reset to defaults option
                    IconButton(
                        onClick = { stableOnEvent(WidgetSettingsEvent.ResetToDefaults) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset to defaults"
                        )
                    }
                }
            )
            
            // Main content with performance tracking
            PerformanceOptimizations.MemoryEfficientComponents.TrackRecomposition(
                key = "WidgetSettingsContent"
            ) {
                when (uiState) {
                    is UiState.Loading -> {
                        LoadingState(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is UiState.Error -> {
                        ErrorState(
                            error = (uiState as UiState.Error).error,
                            onRetry = { stableOnEvent(WidgetSettingsEvent.RetryLastAction) },
                            onDismiss = { stableOnEvent(WidgetSettingsEvent.DismissError) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is UiState.Success -> {
                        WidgetSettingsContent(
                            data = (uiState as UiState.Success).data,
                            onEvent = stableOnEvent,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    is UiState.Empty -> {
                        EmptyState(
                            message = "No widget settings available",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    else -> {
                        ErrorState(
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
 * Main content area for widget settings with scrollable sections
 */
@Composable
private fun WidgetSettingsContent(
    data: WidgetSettingsData,
    onEvent: (WidgetSettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stable callback and data to prevent unnecessary recompositions
    val stableOnEvent = remember(onEvent) { onEvent }
    val (visibleWidgets, hiddenWidgets) = remember(data) { data.getWidgetsByVisibility() }
    
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Experience Level Selector
        item {
            UserLevelSection(
                currentLevel = data.preferences.userLevel,
                onLevelChanged = { level ->
                    stableOnEvent(WidgetSettingsEvent.UpdateUserLevel(level))
                }
            )
        }
        
        // Dashboard Layout Selector
        item {
            LayoutModeSelector(
                selectedMode = data.preferences.dashboardLayout,
                onModeSelected = { mode ->
                    stableOnEvent(WidgetSettingsEvent.UpdateLayoutMode(mode))
                }
            )
        }
        
        // Visible Widgets Section
        item {
            SectionHeader(
                title = "Active Widgets",
                subtitle = "${visibleWidgets.size} widgets displayed on your dashboard",
                icon = Icons.Default.Visibility
            )
        }
        
        items(
            items = visibleWidgets,
            key = { widget -> widget.id }
        ) { widget ->
            WidgetToggleCard(
                widget = widget,
                isEnabled = true,
                isLoading = data.isLoading,
                onToggle = { stableOnEvent(WidgetSettingsEvent.ToggleWidget(widget)) },
                onReorder = {
                    // Open reorder dialog for drag-and-drop functionality
                    // The actual reordering will be handled by a dedicated dialog with drag handles
                    val currentOrder = visibleWidgets.map { it.id }
                    
                    // For now, move widget up by one position as a simple implementation
                    val currentIndex = visibleWidgets.indexOf(widget)
                    if (currentIndex > 0) {
                        val newOrder = currentOrder.toMutableList().apply {
                            // Swap with previous item
                            val temp = this[currentIndex]
                            this[currentIndex] = this[currentIndex - 1]
                            this[currentIndex - 1] = temp
                        }
                        stableOnEvent(WidgetSettingsEvent.ReorderWidgets(newOrder))
                    }
                }
            )
        }
        
        // Available Widgets Section
        if (hiddenWidgets.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Available Widgets",
                    subtitle = "${hiddenWidgets.size} additional widgets you can add",
                    icon = Icons.Default.Add
                )
            }
            
            items(
                items = hiddenWidgets,
                key = { widget -> widget.id }
            ) { widget ->
                WidgetToggleCard(
                    widget = widget,
                    isEnabled = false,
                    isLoading = data.isLoading,
                    canReorder = false,
                    onToggle = { stableOnEvent(WidgetSettingsEvent.ToggleWidget(widget)) }
                )
            }
        }
        
        // Widget Previews Section
        item {
            SectionHeader(
                title = "Widget Previews",
                subtitle = "Preview how widgets will look on your dashboard",
                icon = Icons.Default.Preview
            )
        }
        
        items(
            items = visibleWidgets.take(3), // Show preview for first 3 visible widgets
            key = { widget -> "${widget.id}_preview" }
        ) { widget ->
            WidgetPreviewCard(
                widget = widget,
                currentSize = data.preferences.getWidgetSize(widget.id),
                onSizeChange = { size ->
                    stableOnEvent(WidgetSettingsEvent.UpdateWidgetSize(widget, size))
                }
            )
        }
        
        // Bottom padding for better scrolling experience
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * User experience level selector section
 */
@Composable
private fun UserLevelSection(
    currentLevel: UserLevel,
    onLevelChanged: (UserLevel) -> Unit
) {
    val stableOnLevelChanged = remember(onLevelChanged) { onLevelChanged }
    
    ElevatedLiftrixCard(
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "User experience level selector"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Experience Level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "This determines which widgets are recommended for your dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // User level options
            UserLevel.values().forEach { level ->
                UserLevelOption(
                    level = level,
                    isSelected = level == currentLevel,
                    onSelected = { stableOnLevelChanged(level) }
                )
            }
        }
    }
}

/**
 * Individual user level option component
 */
@Composable
private fun UserLevelOption(
    level: UserLevel,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val stableOnSelected = remember(onSelected) { onSelected }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${level.displayName} experience level. ${level.description}"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = stableOnSelected,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = level.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Text(
                text = level.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section header component
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Loading state component
 */
@Composable
private fun LoadingState(
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
            text = "Loading Widget Settings...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorState(
    error: com.example.liftrix.domain.model.error.LiftrixError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Error Loading Widget Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
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
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Preview for WidgetSettingsScreen
 */
@Preview(showBackground = true)
@Composable
private fun WidgetSettingsScreenPreview() {
    LiftrixTheme {
        // Note: This is a simplified preview without ViewModel integration
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Widget Settings",
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
                                contentDescription = "Reset to defaults"
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
                            selectedMode = DashboardLayoutMode.AUTO,
                            onModeSelected = { }
                        )
                    }
                    
                    item {
                        SectionHeader(
                            title = "Active Widgets",
                            subtitle = "3 widgets displayed on your dashboard",
                            icon = Icons.Default.Visibility
                        )
                    }
                    
                    item {
                        WidgetToggleCard(
                            widget = AnalyticsWidget.TotalVolume,
                            isEnabled = true,
                            onToggle = { }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty state composable for widget settings screen
 */
@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Dashboard,
            contentDescription = "Empty",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
