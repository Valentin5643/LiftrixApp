package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.common.components.TrendIndicator

/**
 * UI component for displaying daily calories burned from workouts
 * 
 * Shows current day's calorie burn with trend visualization and goal progress.
 * Follows Material 3 design system with accessibility support.
 */
@Composable
fun CaloriesBurnedCard(
    caloriesBurned: Int,
    subtitle: String = "Today",
    trend: TrendDirection? = null,
    isLoading: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardColor = LiftrixColors.SurfaceLight
    val contentColor = LiftrixColors.OnSurface
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .semantics {
                contentDescription = "Calories burned: $caloriesBurned calories $subtitle"
            },
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (isLoading) {
                CaloriesBurnedLoadingContent()
            } else {
                CaloriesBurnedContent(
                    caloriesBurned = caloriesBurned,
                    subtitle = subtitle,
                    trend = trend
                )
            }
        }
    }
}

@Composable
private fun CaloriesBurnedContent(
    caloriesBurned: Int,
    subtitle: String,
    trend: TrendDirection?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Calories burned",
                    tint = LiftrixColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Calories Burned",
                    style = MaterialTheme.typography.titleMedium,
                    color = LiftrixColors.OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Trend indicator
            trend?.let { trendDirection ->
                TrendIndicator(
                    trend = trendDirection,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Main value display
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${caloriesBurned}",
                style = MaterialTheme.typography.headlineLarge,
                color = LiftrixColors.Primary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "calories",
                style = MaterialTheme.typography.bodyMedium,
                color = LiftrixColors.OnSurface.copy(alpha = 0.8f)
            )
        }
        
        // Subtitle
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = LiftrixColors.OnSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CaloriesBurnedLoadingContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(20.dp)
                        .background(
                            color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        // Value skeleton
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(40.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        
        // Subtitle skeleton
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(16.dp)
                .background(
                    color = LiftrixColors.OnSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CaloriesBurnedCardPreview() {
    LiftrixTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            CaloriesBurnedCard(
                caloriesBurned = 347,
                subtitle = "Today",
                trend = TrendDirection.UP
            )
            
            CaloriesBurnedCard(
                caloriesBurned = 0,
                subtitle = "No workouts today",
                trend = null
            )
            
            CaloriesBurnedCard(
                caloriesBurned = 0,
                subtitle = "Loading...",
                isLoading = true
            )
        }
    }
}