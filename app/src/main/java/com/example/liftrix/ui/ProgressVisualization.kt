package com.example.liftrix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.animations.AnimatedProgressRingWithGradient
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.PrimaryGradient

@Composable
fun LineChartProgress() {
    // TODO: Implement line chart for progress visualization
    // TODO: Apply teal-indigo gradient to chart
}

/**
 * Radial progress indicator with teal-indigo gradient
 * Perfect for displaying workout completion, goal progress, and statistics
 * 
 * @param progress Progress value between 0.0 and 1.0
 * @param modifier Modifier for styling the component
 * @param size Diameter of the progress indicator
 * @param label Optional label to display below the progress ring
 * @param centerContent Optional composable to display in the center of the ring
 */
@Composable
fun RadialProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    label: String? = null,
    centerContent: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Animated progress ring with Persian Green to Tiffany Blue gradient
                AnimatedProgressRingWithGradient(
                    progress = progress,
                    progressBrush = PrimaryGradient,
                    size = size,
                    strokeWidth = (size.value * 0.08f).dp // 8% of size for proportional stroke
                )
                
                // Center content (percentage, icon, etc.)
                if (centerContent != null) {
                    centerContent()
                } else {
                    // Default percentage display
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Optional label below the ring
            label?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 