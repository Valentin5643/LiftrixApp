package com.example.liftrix.ui.workout.creation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.cards.CardSpacing
import com.example.liftrix.ui.theme.LiftrixTheme
import java.time.format.DateTimeFormatter

/**
 * Modern template card design for the professional training interface
 * Shows template details with athletic styling and action buttons
 */
@Composable
fun TemplateGridItem(
    template: WorkoutTemplate,
    modifier: Modifier = Modifier,
    onStartWorkout: () -> Unit = {},
    onEditTemplate: () -> Unit = {},
    onDuplicateTemplate: () -> Unit = {},
    onShareWithGymBuddy: () -> Unit = {},
    onDeleteTemplate: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onTemplateClick: () -> Unit = {},
    showActions: Boolean = true
) {
    var showDropdownMenu by remember { mutableStateOf(false) }
    
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("Template: ${template.name}")
                    append(", ${template.exercises.size} exercises")
                    append(", ${template.getTotalSets()} sets")
                    template.estimatedDurationMinutes?.let { append(", $it minutes") }
                    // Favorited functionality not yet implemented
                }
            },
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CardSpacing.M),
        onClick = onTemplateClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
        ) {
            // Header with name, favorite, and menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    template.description?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                if (showActions) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Favorite button
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(32.dp)
                                .semantics {
                                    contentDescription = "Toggle favorite (not implemented)"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarBorder,
                                contentDescription = "Toggle favorite",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        // More actions menu
                        IconButton(
                            onClick = { showDropdownMenu = true },
                            modifier = Modifier
                                .size(32.dp)
                                .semantics {
                                    contentDescription = "More template actions"
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More template actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showDropdownMenu,
                            onDismissRequest = { showDropdownMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Template") },
                                onClick = {
                                    showDropdownMenu = false
                                    onEditTemplate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                onClick = {
                                    showDropdownMenu = false
                                    onDuplicateTemplate()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share with Gym Buddy") },
                                onClick = {
                                    showDropdownMenu = false
                                    onShareWithGymBuddy()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showDropdownMenu = false
                                    onDeleteTemplate()
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(CardSpacing.XS))
            
            // Template statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side stats
                Column {
                    TemplateStatChip(
                        icon = Icons.Default.FitnessCenter,
                        text = "${template.exercises.size} exercises"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateStatChip(
                        icon = Icons.Default.FitnessCenter,
                        text = "${template.getTotalSets()} sets"
                    )
                }
                
                // Right side stats
                Column(horizontalAlignment = Alignment.End) {
                    template.estimatedDurationMinutes?.let { duration ->
                        TemplateStatChip(
                            icon = Icons.Default.AccessTime,
                            text = "${duration}min"
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    template.difficultyLevel?.let { difficulty ->
                        TemplateStatChip(
                            icon = Icons.Default.Star,
                            text = "Level $difficulty"
                        )
                    }
                }
            }
            
            // Usage statistics
            if (template.usageCount > 0) {
                Spacer(modifier = Modifier.height(CardSpacing.XS))
                UsageStatsRow(template = template)
            }
            
            // Tags feature not implemented yet
            // if (template.tags.isNotEmpty()) {
            //     Spacer(modifier = Modifier.height(CardSpacing.XS))
            //     TemplateTagsRow(tags = template.tags.toList())
            // }
            
            Spacer(modifier = Modifier.height(CardSpacing.M))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing.XS)
            ) {
                PrimaryActionButton(
                    text = "Start",
                    onClick = onStartWorkout,
                    leadingIcon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f)
                )
                
                SecondaryActionButton(
                    text = "Edit",
                    onClick = onEditTemplate,
                    leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Workflow.Edit,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Compact template grid item for smaller displays
 */
@Composable
fun CompactTemplateGridItem(
    template: WorkoutTemplate,
    modifier: Modifier = Modifier,
    onStartWorkout: () -> Unit = {},
    onEditTemplate: () -> Unit = {}
) {
    LiftrixCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Template: ${template.name}, ${template.exercises.size} exercises"
            },
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CardSpacing.S)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CardSpacing.XS)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "${template.exercises.size} exercises • ${template.getTotalSets()} sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Favorite indicator removed - feature not implemented
                if (false) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorited",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardSpacing.XS)
            ) {
                PrimaryActionButton(
                    text = "",
                    onClick = onStartWorkout,
                    leadingIcon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f)
                )
                
                SecondaryActionButton(
                    text = "Edit",
                    onClick = onEditTemplate,
                    leadingIcon = com.example.liftrix.ui.icons.LiftrixIcons.Workflow.Edit,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Template statistic chip component
 */
@Composable
private fun TemplateStatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Usage statistics row
 */
@Composable
private fun UsageStatsRow(
    template: WorkoutTemplate,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Used ${template.usageCount} times",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        
        template.lastUsedAt?.let { lastUsed ->
            Text(
                text = "Last: ${lastUsed.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Template tags row
 */
@Composable
private fun TemplateTagsRow(
    tags: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.take(3).forEach { tag ->
            Surface(
                modifier = Modifier.clip(CircleShape),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        if (tags.size > 3) {
            Surface(
                modifier = Modifier.clip(CircleShape),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = "+${tags.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TemplateGridItemPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Full template grid item
            TemplateGridItem(
                template = WorkoutTemplate(
                    id = com.example.liftrix.domain.model.WorkoutTemplateId("1"),
                    userId = "user1",
                    name = "Push Day Strength",
                    description = "Upper body pushing movements for strength building",
                    exercises = listOf(
                        // Mock exercises would go here
                    ),
                    folderId = "uncategorized_user1",
                    estimatedDurationMinutes = 60,
                    difficultyLevel = 3,
                    usageCount = 12,
                    lastUsedAt = java.time.Instant.now().minusSeconds(86400),
                    createdAt = java.time.Instant.now(),
                    updatedAt = java.time.Instant.now()
                )
            )
            
            // Compact template grid item
            CompactTemplateGridItem(
                template = WorkoutTemplate(
                    id = com.example.liftrix.domain.model.WorkoutTemplateId("2"),
                    userId = "user1",
                    name = "Quick Cardio",
                    exercises = listOf(),
                    folderId = "uncategorized_user1",
                    estimatedDurationMinutes = 20,
                    createdAt = java.time.Instant.now(),
                    updatedAt = java.time.Instant.now()
                )
            )
        }
    }
} 
