package com.example.liftrix.ui.progress.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Base widget composable template providing consistent behavior across all widgets.
 * 
 * Handles loading states, error states, and content display with Material 3 design.
 * All analytics widgets should use this base template for consistency.
 * 
 * Features:
 * - Loading states with skeleton animations
 * - Error handling with retry functionality
 * - Consistent Material 3 styling
 * - Accessibility support
 * - Click handling for navigation
 */
@Composable
fun BaseWidget(
    title: String,
    isLoading: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onClick: () -> Unit = {},
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isLoading && error == null) { onClick() }
            .semantics {
                contentDescription = buildString {
                    append(title)
                    subtitle?.let { append(", $it") }
                    when {
                        isLoading -> append(", loading")
                        error != null -> append(", error: $error")
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColors.SurfaceLight,
            contentColor = LiftrixColors.OnSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            WidgetHeader(
                title = title,
                subtitle = subtitle
            )
            
            // Content area
            when {
                error != null -> {
                    WidgetErrorState(
                        error = error,
                        onRetry = onRefresh
                    )
                }
                isLoading -> {
                    WidgetLoadingState()
                }
                else -> {
                    content()
                }
            }
        }
    }
}

/**
 * Widget header with title and optional subtitle
 */
@Composable
private fun WidgetHeader(
    title: String,
    subtitle: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = LiftrixColors.OnSurface,
            fontWeight = FontWeight.SemiBold
        )
        
        subtitle?.let { sub ->
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Loading state with skeleton animation placeholder
 */
@Composable
private fun WidgetLoadingState() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Main content skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                )
        )
        
        // Secondary info skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .background(
                        color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

/**
 * Error state with retry functionality
 */
@Composable
private fun WidgetErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = LiftrixColors.OnSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp)
        )
        
        Text(
            text = "Unable to load data",
            style = MaterialTheme.typography.bodyMedium,
            color = LiftrixColors.OnSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColors.OnSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Retry",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Compact version of BaseWidget for smaller layouts
 */
@Composable
fun CompactBaseWidget(
    title: String,
    isLoading: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
            containerColor = LiftrixColors.SurfaceLight,
            contentColor = LiftrixColors.OnSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = LiftrixColors.OnSurface,
                fontWeight = FontWeight.Medium
            )
            
            when {
                error != null -> {
                    CompactErrorState(onRetry = onRefresh)
                }
                isLoading -> {
                    CompactLoadingState()
                }
                else -> {
                    content()
                }
            }
        }
    }
}

/**
 * Compact loading state
 */
@Composable
private fun CompactLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            )
    )
}

/**
 * Compact error state
 */
@Composable
private fun CompactErrorState(
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Error loading",
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
        )
        
        TextButton(
            onClick = onRetry,
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = "Retry",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}