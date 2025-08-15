package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData

/**
 * Small widget card component for 1x1 metric cards in the grid layout.
 * 
 * Created for SPEC-20250205-progress-tab-ui-redesign to standardize
 * small metric cards in the 2-column mobile grid layout.
 * 
 * Features:
 * - Consistent 120dp minimum height for grid alignment
 * - 20dp internal padding for comfortable content spacing
 * - Material 3 card design with subtle elevation
 * - Accessibility support with semantic descriptions
 * - Loading state support
 * 
 * @param widget Analytics widget to display
 * @param widgetData Data for the widget
 * @param onClick Click handler for widget interactions
 * @param isLoading Loading state for the widget
 * @param modifier Modifier for styling
 */
@Composable
fun SmallWidgetCard(
    widget: AnalyticsWidget,
    widgetData: WidgetData,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(105.dp)  // More rectangular aspect ratio like top widgets
            .semantics {
                contentDescription = "${widget.displayName} widget"
                role = Role.Button
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),  // Internal padding per spec
            contentAlignment = Alignment.Center
        ) {
            // Use SimpleWidgetRenderer to render the appropriate widget content
            val displayData = widgetData.getDisplayData()
            val primaryValue = when (displayData) {
                is com.example.liftrix.domain.model.analytics.DisplayData.Metric -> displayData.primaryValue
                is com.example.liftrix.domain.model.analytics.DisplayData.Chart -> "${displayData.dataPoints.size} data points"
                is com.example.liftrix.domain.model.analytics.DisplayData.Progress -> "${displayData.progressPercentage.toInt()}%"
                is com.example.liftrix.domain.model.analytics.DisplayData.Analytics -> displayData.metrics.values.firstOrNull() ?: "No data"
            }
            
            SimpleWidgetRenderer(
                widget = widget,
                primaryValue = primaryValue,
                isLoading = isLoading
            )
        }
    }
}