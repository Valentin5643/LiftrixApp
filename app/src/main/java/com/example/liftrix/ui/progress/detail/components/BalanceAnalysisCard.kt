package com.example.liftrix.ui.progress.detail.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.progress.detail.MuscleGroupDetailViewModel

/**
 * Balance Analysis Card
 * 
 * Displays muscle group balance analysis including balance score, overtraining risks,
 * undertraining risks, and visual balance indicators. Provides insights into training
 * distribution and helps identify muscle imbalances.
 */
@Composable
fun BalanceAnalysisCard(
    balanceAnalysis: MuscleGroupDetailViewModel.BalanceAnalysis,
    modifier: Modifier = Modifier
) {
    LiftrixCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with balance score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Balance Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                BalanceScoreIndicator(
                    score = balanceAnalysis.balanceScore
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Risk indicators
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overtraining risk
                if (balanceAnalysis.overtrainingRisk.isNotEmpty()) {
                    RiskSection(
                        title = "Overtraining Risk",
                        muscleGroups = balanceAnalysis.overtrainingRisk,
                        riskColor = Color.Red,
                        icon = "⚠️"
                    )
                }
                
                // Undertraining risk
                if (balanceAnalysis.undertrainingRisk.isNotEmpty()) {
                    RiskSection(
                        title = "Undertraining Risk",
                        muscleGroups = balanceAnalysis.undertrainingRisk,
                        riskColor = MaterialTheme.colorScheme.tertiary,
                        icon = "⬇️"
                    )
                }
                
                // Balance summary
                BalanceSummary(
                    score = balanceAnalysis.balanceScore,
                    recommendationCount = balanceAnalysis.recommendations.size
                )
            }
        }
    }
}

/**
 * Balance score indicator
 */
@Composable
private fun BalanceScoreIndicator(
    score: Float
) {
    val (color, status) = when {
        score >= 80f -> Color.Green to "Excellent"
        score >= 60f -> MaterialTheme.colorScheme.primary to "Good"
        score >= 40f -> Color.Yellow to "Fair"
        else -> Color.Red to "Poor"
    }
    
    Column(
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = "${score.toInt()}/100",
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

/**
 * Risk section for over/undertraining
 */
@Composable
private fun RiskSection(
    title: String,
    muscleGroups: List<com.example.liftrix.domain.model.MuscleGroup>,
    riskColor: Color,
    icon: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleSmall
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = riskColor
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(muscleGroups) { muscleGroup ->
                MuscleGroupChip(
                    muscleGroup = muscleGroup,
                    color = riskColor
                )
            }
        }
    }
}

/**
 * Muscle group chip
 */
@Composable
private fun MuscleGroupChip(
    muscleGroup: com.example.liftrix.domain.model.MuscleGroup,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = muscleGroup.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Balance summary section
 */
@Composable
private fun BalanceSummary(
    score: Float,
    recommendationCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Balance Status",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = getBalanceStatusText(score),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$recommendationCount",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Recommendations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get balance status description based on score
 */
private fun getBalanceStatusText(score: Float): String {
    return when {
        score >= 80f -> "Your training is well-balanced across muscle groups"
        score >= 60f -> "Minor imbalances present, easy to address"
        score >= 40f -> "Some imbalances need attention"
        else -> "Significant imbalances require immediate focus"
    }
}