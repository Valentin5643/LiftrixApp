package com.example.liftrix.ui.settings.components

import androidx.compose.animation.*
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
import com.example.liftrix.domain.model.analytics.*
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Widget toggle section component for category-based widget organization
 * 
 * Features:
 * - Collapsible category sections with expand/collapse animations
 * - Batch operations for enabling/disabling multiple widgets
 * - Real-time widget count and status indicators
 * - Category-specific recommendations and descriptions
 * - Accessibility support with proper semantic descriptions
 * - Loading states for individual widgets and sections
 * 
 * @param category Widget category to display (METRICS, CHARTS, PROGRESS, ANALYTICS)
 * @param widgets List of widgets in this category
 * @param preferences Current widget preferences for visibility state
 * @param isLoading Whether any widget operations are in progress
 * @param onToggle Callback when individual widget toggle state changes
 * @param onReorder Callback for widget reordering within category
 * @param modifier Modifier for styling the section
 */
@Composable
fun WidgetToggleSection(
    category: WidgetCategory,
    widgets: List<AnalyticsWidget>,
    preferences: WidgetPreferences,
    isLoading: Boolean = false,
    onToggle: (AnalyticsWidget) -> Unit,
    onReorder: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Section expansion state
    var isExpanded by remember { mutableStateOf(true) }
    
    // Calculate visible/enabled widgets in this category
    val visibleWidgets = remember(widgets, preferences) {
        widgets.filter { preferences.isWidgetVisible(it.id) }
    }
    val hiddenWidgets = remember(widgets, preferences) {
        widgets.filter { !preferences.isWidgetVisible(it.id) }
    }
    
    // Stable callbacks
    val stableOnToggle = remember(onToggle) { onToggle }
    val stableOnReorder = remember(onReorder) { onReorder }
    
    ElevatedLiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${category.displayName} widgets section. ${visibleWidgets.size} of ${widgets.size} widgets enabled."
            },
        contentDescription = "${category.displayName} widget category"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Category header with expand/collapse and batch actions
            CategoryHeader(
                category = category,
                totalWidgets = widgets.size,
                visibleWidgets = visibleWidgets.size,
                isExpanded = isExpanded,
                isLoading = isLoading,
                onExpandToggle = { isExpanded = !isExpanded },
                onBatchToggle = { enableAll ->
                    if (enableAll) {
                        // Enable all widgets in category
                        hiddenWidgets.forEach { widget ->
                            stableOnToggle(widget)
                        }
                    } else {
                        // Disable all widgets in category
                        visibleWidgets.forEach { widget ->
                            stableOnToggle(widget)
                        }
                    }
                }
            )
            
            // Expandable content with widgets
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // Category description and recommendations
                    CategoryDescription(
                        category = category,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Visible widgets section
                    if (visibleWidgets.isNotEmpty()) {
                        WidgetSubsection(
                            title = "Active Widgets",
                            subtitle = "${visibleWidgets.size} widgets displayed on dashboard",
                            icon = Icons.Default.Visibility,
                            widgets = visibleWidgets,
                            isVisible = true,
                            isLoading = isLoading,
                            onToggle = stableOnToggle,
                            onReorder = stableOnReorder,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        
                        if (hiddenWidgets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                    
                    // Hidden widgets section
                    if (hiddenWidgets.isNotEmpty()) {
                        WidgetSubsection(
                            title = "Available Widgets",
                            subtitle = "${hiddenWidgets.size} additional widgets to choose from",
                            icon = Icons.Default.Add,
                            widgets = hiddenWidgets,
                            isVisible = false,
                            isLoading = isLoading,
                            onToggle = stableOnToggle,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Category header with expand/collapse and batch operations
 */
@Composable
private fun CategoryHeader(
    category: WidgetCategory,
    totalWidgets: Int,
    visibleWidgets: Int,
    isExpanded: Boolean,
    isLoading: Boolean,
    onExpandToggle: () -> Unit,
    onBatchToggle: (enableAll: Boolean) -> Unit
) {
    val stableOnExpandToggle = remember(onExpandToggle) { onExpandToggle }
    val stableOnBatchToggle = remember(onBatchToggle) { onBatchToggle }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category icon and info
        Icon(
            imageVector = category.getIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "$visibleWidgets of $totalWidgets active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Batch action buttons
        if (!isLoading) {
            if (visibleWidgets < totalWidgets) {
                TextButton(
                    onClick = { stableOnBatchToggle(true) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Enable All",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            if (visibleWidgets > 0) {
                TextButton(
                    onClick = { stableOnBatchToggle(false) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Disable All",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        
        // Expand/collapse button
        IconButton(
            onClick = stableOnExpandToggle
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Category description and recommendations
 */
@Composable
private fun CategoryDescription(
    category: WidgetCategory,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Category-specific recommendations
            when (category) {
                WidgetCategory.METRICS -> {
                    Text(
                        text = "💡 Essential for tracking basic performance indicators",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                WidgetCategory.CHARTS -> {
                    Text(
                        text = "📊 Great for visualizing trends and patterns over time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                WidgetCategory.PROGRESS -> {
                    Text(
                        text = "🎯 Perfect for goal tracking and milestone monitoring",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                WidgetCategory.ANALYTICS -> {
                    Text(
                        text = "🔬 Advanced insights for experienced athletes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Widget subsection for visible or hidden widgets
 */
@Composable
private fun WidgetSubsection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    widgets: List<AnalyticsWidget>,
    isVisible: Boolean,
    isLoading: Boolean,
    onToggle: (AnalyticsWidget) -> Unit,
    onReorder: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Subsection header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Widget toggle cards
        widgets.forEach { widget ->
            WidgetToggleCard(
                widget = widget,
                isEnabled = isVisible,
                isLoading = isLoading,
                canReorder = isVisible && onReorder != null,
                onToggle = { onToggle(widget) },
                onReorder = if (isVisible && onReorder != null) {
                    { /* TODO: Implement reordering logic */ }
                } else null
            )
        }
    }
}

/**
 * Extension function to get category icon
 */
private fun WidgetCategory.getIcon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        WidgetCategory.METRICS -> Icons.Default.Speed
        WidgetCategory.CHARTS -> Icons.Default.BarChart
        WidgetCategory.PROGRESS -> Icons.Default.TrendingUp
        WidgetCategory.ANALYTICS -> Icons.Default.Analytics
    }
}

/**
 * Preview for WidgetToggleSection
 */
@Preview(showBackground = true)
@Composable
private fun WidgetToggleSectionPreview() {
    LiftrixTheme {
        val sampleWidgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.WorkoutStreak
        )
        
        val samplePreferences = WidgetPreferences(
            userId = "test_user",
            visibleWidgets = setOf("total_volume", "workout_frequency"),
            widgetOrder = listOf("total_volume", "workout_frequency"),
            dashboardLayout = DashboardLayoutMode.AUTO,
            userLevel = UserLevel.BEGINNER
        )
        
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WidgetToggleSection(
                category = WidgetCategory.METRICS,
                widgets = sampleWidgets,
                preferences = samplePreferences,
                onToggle = { },
                onReorder = { }
            )
        }
    }
}