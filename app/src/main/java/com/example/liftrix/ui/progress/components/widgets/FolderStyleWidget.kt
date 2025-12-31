package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.ui.theme.LiftrixColors

/**
 * Folder-style widget matching the workout folder design pattern.
 * Displays a subtle icon and title without values or graphs.
 * Adapts aspect ratio based on layout context - rectangular for single column, square for multi-column.
 */
@Composable
fun FolderStyleWidget(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    aspectRatio: Float = 1.1f  // Default aspect ratio, can be overridden for responsive layouts
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)  // Dynamic aspect ratio based on layout context
            .clip(RoundedCornerShape(16.dp))  // Matching workout folder corner radius
            .clickable(enabled = !isLoading && error == null) { onClick() }
            .semantics {
                contentDescription = buildString {
                    append(title)
                    when {
                        isLoading -> append(", loading")
                        error != null -> append(", error: $error")
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        when {
            error != null -> {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            isLoading -> {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                }
            }
            else -> {
                // Normal content - matching workout folder layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Icon in top-left, subtle like workout folders
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    
                    // Title at bottom
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Compact folder-style widget for smaller layouts.
 * Even more minimal design with icon and abbreviated title.
 * Adapts aspect ratio based on layout context.
 */
@Composable
fun CompactFolderStyleWidget(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    aspectRatio: Float = 1.2f  // Default aspect ratio for compact mode
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)  // Dynamic aspect ratio based on layout context
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !isLoading && error == null) { onClick() }
            .semantics {
                contentDescription = title
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                error != null -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
