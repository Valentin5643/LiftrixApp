package com.example.liftrix.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Layout mode selector component for choosing dashboard layout preferences.
 * 
 * Features:
 * - Visual preview of each layout mode
 * - Single selection with radio button behavior
 * - Accessibility support with semantic descriptions
 * - Material 3 design with proper theming
 * - Loading state support
 * 
 * @param selectedMode Currently selected dashboard layout mode
 * @param onModeSelected Callback when a layout mode is selected
 * @param enabled Whether the selector is enabled for interaction
 * @param modifier Modifier for styling the component
 */
@Composable
fun LayoutModeSelector(
    selectedMode: DashboardLayoutMode,
    onModeSelected: (DashboardLayoutMode) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Stable callback to prevent unnecessary recompositions
    val stableOnModeSelected = remember(onModeSelected) { onModeSelected }
    
    ElevatedLiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Dashboard layout mode selector. Currently selected: ${selectedMode.displayName}"
            },
        contentDescription = "Layout mode selector"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Dashboard Layout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Choose how widgets are arranged on your dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Layout mode options
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardLayoutMode.values().forEach { mode ->
                    LayoutModeOption(
                        mode = mode,
                        isSelected = mode == selectedMode,
                        enabled = enabled,
                        onSelected = { stableOnModeSelected(mode) }
                    )
                }
            }
        }
    }
}

/**
 * Individual layout mode option with preview and description.
 * 
 * @param mode The dashboard layout mode
 * @param isSelected Whether this mode is currently selected
 * @param enabled Whether this option is enabled for interaction
 * @param onSelected Callback when this mode is selected
 */
@Composable
private fun LayoutModeOption(
    mode: DashboardLayoutMode,
    isSelected: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit
) {
    val stableOnSelected = remember(onSelected) { onSelected }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = isSelected,
                onClick = stableOnSelected,
                enabled = enabled,
                role = Role.RadioButton
            )
            .semantics {
                contentDescription = "${mode.displayName} layout mode. ${mode.description}"
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Layout preview
            LayoutPreview(
                mode = mode,
                isSelected = isSelected
            )
            
            // Mode information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
                
                // Additional info based on mode
                when (mode) {
                    DashboardLayoutMode.AUTO -> {
                        Text(
                            text = "Smart layout based on your level",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.COMPACT -> {
                        Text(
                            text = "Recommended for beginners",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.EXPANDED -> {
                        Text(
                            text = "Ideal for detailed analysis",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.CUSTOM -> {
                        Text(
                            text = "For advanced users",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.GRID -> {
                        Text(
                            text = "Traditional grid layout",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.LIST -> {
                        Text(
                            text = "Linear scrolling layout",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.SECTIONS -> {
                        Text(
                            text = "Organized with categories",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DashboardLayoutMode.DEFAULT -> {
                        Text(
                            text = "Standard layout mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Selection indicator
            RadioButton(
                selected = isSelected,
                onClick = null, // Handled by parent selectable
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Visual preview of the layout mode.
 * 
 * @param mode The dashboard layout mode to preview
 * @param isSelected Whether this mode is currently selected
 */
@Composable
private fun LayoutPreview(
    mode: DashboardLayoutMode,
    isSelected: Boolean
) {
    val previewColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            DashboardLayoutMode.AUTO -> {
                GridLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.COMPACT -> {
                SectionsLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.EXPANDED -> {
                ListLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.CUSTOM -> {
                CustomLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.GRID -> {
                GridLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.LIST -> {
                ListLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.SECTIONS -> {
                SectionsLayoutPreview(color = previewColor)
            }
            DashboardLayoutMode.DEFAULT -> {
                GridLayoutPreview(color = previewColor)
            }
        }
    }
}

/**
 * Grid layout preview visualization.
 */
@Composable
private fun GridLayoutPreview(color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * Sections layout preview visualization.
 */
@Composable
private fun SectionsLayoutPreview(color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Section header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(color, RoundedCornerShape(1.dp))
        )
        // Section content
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 6.dp)
                    .background(color.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 8.dp, height = 6.dp)
                    .background(color.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            )
        }
        // Another section header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

/**
 * List layout preview visualization.
 */
@Composable
private fun ListLayoutPreview(color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(color.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * Custom layout preview visualization.
 */
@Composable
private fun CustomLayoutPreview(color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 6.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .background(color.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .background(color.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .background(color, RoundedCornerShape(1.dp))
            )
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .background(color.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * Preview for LayoutModeSelector component
 */
@Preview(showBackground = true)
@Composable
private fun LayoutModeSelectorPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LayoutModeSelector(
                selectedMode = DashboardLayoutMode.COMPACT,
                onModeSelected = { }
            )
        }
    }
}