package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Simple widget renderer for basic analytics widgets
 * 
 * Provides a clean, consistent rendering approach for dashboard widgets
 * that display key metrics with minimal visual complexity.
 */
@Composable
fun SimpleWidgetRenderer(
    widget: AnalyticsWidget,
    primaryValue: String? = null,
    secondaryValue: String? = null,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            
            error != null -> {
                SimpleErrorState(error = error)
            }
            
            else -> {
                SimpleWidgetContent(
                    widget = widget,
                    primaryValue = primaryValue,
                    secondaryValue = secondaryValue
                )
            }
        }
    }
}

/**
 * Main content renderer for simple widgets
 */
@Composable
private fun SimpleWidgetContent(
    widget: AnalyticsWidget,
    primaryValue: String? = null,
    secondaryValue: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Widget title
        Text(
            text = widget.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        // Primary value display
        primaryValue?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Secondary value
        secondaryValue?.let { value ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}


/**
 * Simple error state for widgets
 */
@Composable
private fun SimpleErrorState(
    error: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(LiftrixSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Unable to load",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Widget renderer variant for icon-based widgets
 */
@Composable
fun IconWidgetRenderer(
    widget: AnalyticsWidget,
    icon: ImageVector,
    primaryValue: String? = null,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            
            error != null -> {
                SimpleErrorState(error = error)
            }
            
            else -> {
                IconWidgetContent(
                    widget = widget,
                    icon = icon,
                    primaryValue = primaryValue
                )
            }
        }
    }
}

/**
 * Content renderer for icon-based widgets
 */
@Composable
private fun IconWidgetContent(
    widget: AnalyticsWidget,
    icon: ImageVector,
    primaryValue: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.cardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = widget.displayName,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(LiftrixSpacing.small))
        
        // Primary value
        primaryValue?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Widget title
        Text(
            text = widget.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}