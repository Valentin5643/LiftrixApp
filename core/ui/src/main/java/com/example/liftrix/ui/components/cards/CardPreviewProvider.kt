package com.example.liftrix.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Preview parameter provider for card content examples
 */
class CardContentProvider : PreviewParameterProvider<CardContentExample> {
    override val values = sequenceOf(
        CardContentExample.StatExample,
        CardContentExample.ActivityExample,
        CardContentExample.CompactExample,
        CardContentExample.ElevatedExample
    )
}

/**
 * Example content for card previews
 */
sealed class CardContentExample {
    object StatExample : CardContentExample()
    object ActivityExample : CardContentExample()
    object CompactExample : CardContentExample()
    object ElevatedExample : CardContentExample()
}

/**
 * Comprehensive preview of LiftrixCard base component
 */
@Preview(showBackground = true, name = "LiftrixCard - Basic")
@Composable
fun LiftrixCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Basic LiftrixCard",
                style = MaterialTheme.typography.titleMedium
            )
            
            LiftrixCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* Preview action */ }
            ) {
                Text(
                    text = "This is a basic LiftrixCard with 24dp border radius and 8pt grid spacing.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Preview of card variants
 */
@Preview(showBackground = true, name = "Card Variants")
@Composable
fun CardVariantsPreview() {
    LiftrixTheme {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Card Variants",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            item {
                Text("Compact Card", style = MaterialTheme.typography.titleMedium)
                CompactLiftrixCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* Preview action */ }
                ) {
                    Text("Compact card with reduced padding", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            item {
                Text("Elevated Card", style = MaterialTheme.typography.titleMedium)
                ElevatedLiftrixCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* Preview action */ }
                ) {
                    Text("Elevated card with 8dp shadow", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            item {
                Text("Gradient Card", style = MaterialTheme.typography.titleMedium)
                GradientLiftrixCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* Preview action */ }
                ) {
                    Text("Gradient card with brand colors", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Preview of StatCard components
 */
@Preview(showBackground = true, name = "Stat Cards Grid")
@Composable
fun StatCardsGridPreview() {
    LiftrixTheme {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "StatCard Examples",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            item {
                StatCard(
                    title = "Weekly Workouts",
                    value = "12",
                    subtitle = "This week",
                    trend = Trend.Positive(15.2f),
                    icon = Icons.Default.FitnessCenter,
                    onClick = { /* Preview action */ }
                )
            }
            
            item {
                StatCard(
                    title = "Total Weight Lifted",
                    value = "2,450 lbs",
                    subtitle = "This month",
                    trend = Trend.Positive(23.1f),
                    icon = Icons.Default.MonitorWeight,
                    onClick = { /* Preview action */ }
                )
            }
            
            item {
                StatCard(
                    title = "Average Session",
                    value = "45 min",
                    subtitle = "Last 30 days",
                    trend = Trend.Neutral(),
                    icon = Icons.Default.Timer,
                    onClick = { /* Preview action */ }
                )
            }
            
            item {
                StatCard(
                    title = "Calories Burned",
                    value = "3,240",
                    subtitle = "This week",
                    trend = Trend.Negative(8.5f, "decrease"),
                    icon = Icons.Default.LocalFireDepartment,
                    onClick = { /* Preview action */ }
                )
            }
        }
    }
}

/**
 * Preview of compact StatCard layout
 */
@Preview(showBackground = true, name = "Compact StatCards")
@Composable
fun CompactStatCardPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Compact StatCards",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatCard(
                    title = "Workouts",
                    value = "12",
                    trend = Trend.Positive(15.2f),
                    modifier = Modifier.weight(1f),
                    onClick = { /* Preview action */ }
                )
                
                CompactStatCard(
                    title = "Weight",
                    value = "2.4K lbs",
                    trend = Trend.Positive(23.1f),
                    modifier = Modifier.weight(1f),
                    onClick = { /* Preview action */ }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatCard(
                    title = "Duration",
                    value = "45m",
                    trend = Trend.Neutral(),
                    modifier = Modifier.weight(1f),
                    onClick = { /* Preview action */ }
                )
                
                CompactStatCard(
                    title = "Calories",
                    value = "3.2K",
                    trend = Trend.Negative(8.5f),
                    modifier = Modifier.weight(1f),
                    onClick = { /* Preview action */ }
                )
            }
        }
    }
}

/**
 * Preview of ActivityCard components
 */
@Preview(showBackground = true, name = "Activity Cards List")
@Composable
fun ActivityCardsListPreview() {
    LiftrixTheme {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "ActivityCard Examples",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            
            item {
                ActivityCard(
                    title = "Morning Workout",
                    subtitle = "Push Day - 45 minutes",
                    icon = Icons.Default.FitnessCenter,
                    trailing = "Today at 7:00 AM",
                    onClick = { /* Preview action */ }
                )
            }
            
            item {
                ActivityCard(
                    title = "Personal Record",
                    subtitle = "Bench Press - 225 lbs",
                    icon = Icons.Default.TrendingUp,
                    trailing = "2 hours ago",
                    onClick = { /* Preview action */ }
                )
            }
            
            item {
                ActivityCard(
                    title = "Cardio Session",
                    subtitle = "Running - 3.2 miles",
                    icon = Icons.Default.DirectionsRun,
                    trailing = "Yesterday",
                    onClick = { /* Preview action */ }
                )
            }
            
            item {
                ActivityCard(
                    title = "Rest Day",
                    subtitle = "Recovery and stretching",
                    icon = Icons.Default.Schedule,
                    trailing = "2 days ago",
                    onClick = { /* Preview action */ }
                )
            }
        }
    }
}

/**
 * Preview showing asymmetrical composition support
 */
@Preview(showBackground = true, name = "Asymmetrical Layout")
@Composable
fun AsymmetricalCardLayoutPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Asymmetrical Card Layout",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Today's Goal",
                    value = "75%",
                    subtitle = "3 of 4 exercises",
                    trend = Trend.Positive(25f),
                    modifier = Modifier.weight(2f),
                    onClick = { /* Preview action */ }
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactStatCard(
                        title = "Sets",
                        value = "24",
                        onClick = { /* Preview action */ }
                    )
                    
                    CompactStatCard(
                        title = "Reps",
                        value = "180",
                        onClick = { /* Preview action */ }
                    )
                }
            }
            
            ActivityCard(
                title = "Current Workout",
                subtitle = "Push Day - Exercise 3 of 4",
                icon = Icons.Default.FitnessCenter,
                trailing = "Started 25 minutes ago",
                onClick = { /* Preview action */ }
            )
        }
    }
}

/**
 * Preview with different spacing demonstrations
 */
@Preview(showBackground = true, name = "8pt Grid Spacing")
@Composable
fun GridSpacingPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "8pt Grid Spacing System",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = "XXS (4dp) - XS (8dp) - S (12dp) - M (16dp) - L (24dp) - XL (32dp) - XXL (40dp)",
                style = MaterialTheme.typography.bodySmall
            )
            
            LiftrixCard(
                contentPadding = CardSpacing.XXS.let { androidx.compose.foundation.layout.PaddingValues(it) }
            ) {
                Text("XXS Padding (4dp)", style = MaterialTheme.typography.bodySmall)
            }
            
            LiftrixCard(
                contentPadding = CardSpacing.XS.let { androidx.compose.foundation.layout.PaddingValues(it) }
            ) {
                Text("XS Padding (8dp)", style = MaterialTheme.typography.bodySmall)
            }
            
            LiftrixCard(
                contentPadding = CardSpacing.M.let { androidx.compose.foundation.layout.PaddingValues(it) }
            ) {
                Text("M Padding (16dp) - Default", style = MaterialTheme.typography.bodyMedium)
            }
            
            LiftrixCard(
                contentPadding = CardSpacing.L.let { androidx.compose.foundation.layout.PaddingValues(it) }
            ) {
                Text("L Padding (24dp)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Preview parameter based card showcase
 */
@Preview(showBackground = true, name = "Card Content Examples")
@Composable
fun CardContentExamplePreview(
    @PreviewParameter(CardContentProvider::class) example: CardContentExample
) {
    LiftrixTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (example) {
                CardContentExample.StatExample -> {
                    Text("Stat Card Example", style = MaterialTheme.typography.titleMedium)
                    StatCard(
                        title = "Weekly Progress",
                        value = "87%",
                        subtitle = "5 of 6 workouts completed",
                        trend = Trend.Positive(12.3f),
                        icon = Icons.Default.TrendingUp
                    )
                }
                
                CardContentExample.ActivityExample -> {
                    Text("Activity Card Example", style = MaterialTheme.typography.titleMedium)
                    ActivityCard(
                        title = "Leg Day Complete",
                        subtitle = "Squats, Deadlifts, Lunges",
                        icon = Icons.Default.FitnessCenter,
                        trailing = "Just now"
                    )
                }
                
                CardContentExample.CompactExample -> {
                    Text("Compact Cards Example", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactStatCard(
                            title = "PR's",
                            value = "3",
                            trend = Trend.Positive(100f),
                            modifier = Modifier.weight(1f)
                        )
                        CompactStatCard(
                            title = "Time",
                            value = "52m",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                CardContentExample.ElevatedExample -> {
                    Text("Elevated Card Example", style = MaterialTheme.typography.titleMedium)
                    ElevatedLiftrixCard {
                        Column {
                            Text(
                                "Achievement Unlocked!",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "You've completed 10 workouts this month. Keep it up!",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}