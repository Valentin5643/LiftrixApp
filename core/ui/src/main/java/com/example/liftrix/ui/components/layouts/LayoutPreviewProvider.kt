package com.example.liftrix.ui.components.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Preview provider for layout components showcasing different screen sizes and layouts
 */
class LayoutPreviewProvider : PreviewParameterProvider<LayoutPreviewData> {
    override val values = sequenceOf(
        LayoutPreviewData(
            screenSize = ScreenSize.Compact,
            title = "Compact Layout",
            description = "Phone portrait layout with single column"
        ),
        LayoutPreviewData(
            screenSize = ScreenSize.Medium,
            title = "Medium Layout", 
            description = "Tablet or phone landscape with two columns"
        ),
        LayoutPreviewData(
            screenSize = ScreenSize.Expanded,
            title = "Expanded Layout",
            description = "Large tablet or foldable with three columns"
        )
    )
}

data class LayoutPreviewData(
    val screenSize: ScreenSize,
    val title: String,
    val description: String
)

/**
 * Preview composable for ResponsiveContainer component
 */
@Preview(name = "ResponsiveContainer - Compact", widthDp = 360)
@Preview(name = "ResponsiveContainer - Medium", widthDp = 700)
@Preview(name = "ResponsiveContainer - Expanded", widthDp = 1000)
@Composable
private fun ResponsiveContainerPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ResponsiveContainer(
                modifier = Modifier.padding(GridSystem.spacing3)
            ) { screenSize ->
                Column {
                    Text(
                        text = "Screen Size: ${screenSize.name}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = when (screenSize) {
                            ScreenSize.Compact -> "Single column layout for phones"
                            ScreenSize.Medium -> "Two column layout for tablets"
                            ScreenSize.Expanded -> "Three column layout for large screens"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = GridSystem.spacing2)
                    )
                    
                    // Sample content
                    repeat(3) { index ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = GridSystem.spacing2),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = "Sample Card ${index + 1}",
                                modifier = Modifier.padding(GridSystem.spacing3),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview composable for AsymmetricalLayout component
 */
@Preview(name = "AsymmetricalLayout - Compact", widthDp = 360, heightDp = 640)
@Preview(name = "AsymmetricalLayout - Medium", widthDp = 700, heightDp = 480)
@Preview(name = "AsymmetricalLayout - Expanded", widthDp = 1000, heightDp = 600)
@Composable
private fun AsymmetricalLayoutPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AsymmetricalLayout(
                modifier = Modifier.padding(GridSystem.spacing3),
                primaryContent = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Primary Content",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                },
                secondaryContent = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Secondary Content",
                                color = MaterialTheme.colorScheme.onSecondary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                },
                tertiaryContent = {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tertiary Content",
                                color = MaterialTheme.colorScheme.onTertiary,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * Preview composable for AdaptiveGrid component
 */
@Preview(name = "AdaptiveGrid - Compact", widthDp = 360)
@Preview(name = "AdaptiveGrid - Medium", widthDp = 700)
@Preview(name = "AdaptiveGrid - Expanded", widthDp = 1000)
@Composable
private fun AdaptiveGridPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AdaptiveGrid(
                modifier = Modifier.padding(GridSystem.spacing3),
                compactColumns = 1,
                mediumColumns = 2,
                expandedColumns = 3
            ) { columns, screenSize ->
                Text(
                    text = "Grid Layout - $columns columns (${screenSize.name})",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = GridSystem.spacing3)
                )
                
                // Create grid items
                val items = (1..6).toList()
                items.chunked(columns).forEach { rowItems ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { item ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(GridSystem.spacing1)
                                    .height(100.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Item $item",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        
                        // Fill remaining space if row is not complete
                        repeat(columns - rowItems.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview composable for GridSystem spacing values
 */
@Preview(name = "GridSystem Spacing", widthDp = 360, heightDp = 640)
@Composable
private fun GridSystemSpacingPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(GridSystem.spacing3)
            ) {
                Text(
                    text = "Grid System Spacing",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = GridSystem.spacing4)
                )
                
                val spacingValues = listOf(
                    "spacing1" to GridSystem.spacing1,
                    "spacing2" to GridSystem.spacing2,
                    "spacing3" to GridSystem.spacing3,
                    "spacing4" to GridSystem.spacing4,
                    "spacing5" to GridSystem.spacing5,
                    "spacing6" to GridSystem.spacing6
                )
                
                spacingValues.forEach { (name, value) ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(vertical = GridSystem.spacing1),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$name (${value.value}dp)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(width = value, height = 16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview composable for responsive padding modifier
 */
@Preview(name = "Responsive Padding", widthDp = 360)
@Preview(name = "Responsive Padding - Medium", widthDp = 700)
@Preview(name = "Responsive Padding - Expanded", widthDp = 1000)
@Composable
private fun ResponsivePaddingPreview() {
    LiftrixTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.responsivePadding()
            ) {
                Text(
                    text = "Responsive Padding",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    text = "This content uses responsive padding that adjusts based on screen size",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = GridSystem.spacing2)
                )
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = GridSystem.spacing3),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Sample card content with responsive container padding",
                        modifier = Modifier.padding(GridSystem.spacing3),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
} 