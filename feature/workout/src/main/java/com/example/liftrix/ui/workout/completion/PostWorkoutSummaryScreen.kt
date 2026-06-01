package com.example.liftrix.ui.workout.completion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.service.WeightUnitManager
import com.example.liftrix.feature.workout.ui.rememberWeightUnitManager
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Enhanced post-workout summary screen showing comprehensive workout statistics
 * and multiple sharing options, similar to apps like Hevy.
 * 
 * Follows SPEC-20250113-content-sharing-media requirements for post-workout flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostWorkoutSummaryScreen(
    workoutId: String,
    navController: NavController,
    onNavigateToWorkoutDetails: (String) -> Unit = {},
    onNavigateToPostCreation: (String) -> Unit = {},
    onNavigateToShareWorkout: (String) -> Unit = {},
    onNavigateHome: () -> Unit = { navController.navigateUp() },
    viewModel: PostWorkoutSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val weightUnitManager = rememberWeightUnitManager()
    weightUnitManager?.currentUnit?.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    
    LaunchedEffect(workoutId) {
        viewModel.loadWorkoutSummary(workoutId)
        // Animate cards appearing
        delay(100)
        showDetails = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is PostWorkoutUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                }
            }
            
            is PostWorkoutUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero Summary Card with Workout Image
                    item {
                        HeroSummaryCard(
                            workoutName = state.workoutName,
                            workoutDate = state.workoutDate,
                            duration = state.duration,
                            imageUrl = state.workoutImageUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Key Metrics Grid
                    item {
                        AnimatedVisibility(
                            visible = showDetails,
                            enter = slideInVertically() + fadeIn()
                        ) {
                            KeyMetricsGrid(
                                totalVolume = state.totalVolume,
                                totalSets = state.totalSets,
                                totalReps = state.totalReps,
                                prsCount = state.prsCount,
                                weightUnitManager = weightUnitManager
                            )
                        }
                    }
                    
                    // Personal Records Section (if any)
                    if (state.personalRecords.isNotEmpty()) {
                        item {
                            PersonalRecordsSection(
                                records = state.personalRecords,
                                weightUnitManager = weightUnitManager
                            )
                        }
                    }
                    
                    // Exercise Summary (Expandable)
                    item {
                        ExerciseSummaryCard(
                            exercises = state.exercises,
                            onClick = {
                                // Navigate to detailed view
                                onNavigateToWorkoutDetails(workoutId)
                            }
                        )
                    }
                    
                    // Share Actions
                    item {
                        ShareActionsCard(
                            onShareWorkout = {
                                onNavigateToShareWorkout(workoutId)
                            },
                            onShareToSocialFeed = {
                                onNavigateToPostCreation(workoutId)
                            }
                        )
                    }
                    
                    // Discard Action - Subtle text button without background
                    item {
                        TextButton(
                            onClick = { 
                                // Discard the workout from database and navigate home
                                viewModel.discardWorkout(workoutId) {
                                    onNavigateHome()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        ) {
                            Text(
                                "Discard Workout",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
            
            is PostWorkoutUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.loadWorkoutSummary(workoutId) },
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(
    workoutName: String,
    workoutDate: java.time.LocalDate,
    duration: Duration,
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    // Beautiful gradient card for post-workout celebration
    Card(
        modifier = modifier
            .height(120.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            LiftrixColorsV2.Teal,
                            LiftrixColorsV2.TealDark
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Workout info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Workout Complete",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = workoutName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = "Duration",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatDuration(duration),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Workout date",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                                    .format(workoutDate),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                
                // Right side - Completion indicator
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyMetricsGrid(
    totalVolume: Int,
    totalSets: Int,
    totalReps: Int,
    prsCount: Int,
    weightUnitManager: WeightUnitManager?
) {
    val volumeText = weightUnitManager?.formatWeightCompact(totalVolume.toDouble(), WeightUnit.KILOGRAMS)
        ?: WeightUnit.KILOGRAMS.formatWeight(totalVolume.toDouble(), precision = 0)
    val volumeParts = volumeText.split(" ", limit = 2)

    // Compact dark theme 2x2 grid matching reference design
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Total Volume card - Dark with subtle accent
            DarkMetricCard(
                icon = Icons.Default.FitnessCenter,
                value = volumeParts.firstOrNull() ?: "0",
                unit = volumeParts.getOrNull(1) ?: WeightUnit.KILOGRAMS.symbol,
                label = "Total Volume",
                iconTint = LiftrixColorsV2.Teal,
                modifier = Modifier.weight(1f)
            )
            // Sets Completed card - Dark with subtle accent
            DarkMetricCard(
                icon = Icons.Default.CheckCircle,
                value = totalSets.toString(),
                unit = "sets",
                label = "Sets Completed", 
                iconTint = LiftrixColorsV2.DataViz.Positive,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Total Reps card - Dark with subtle accent
            DarkMetricCard(
                icon = Icons.Default.Repeat,
                value = totalReps.toString(),
                unit = "reps",
                label = "Total Reps",
                iconTint = Color(0xFFE91E63),
                modifier = Modifier.weight(1f)
            )
            if (prsCount > 0) {
                DarkMetricCard(
                    icon = Icons.Default.EmojiEvents,
                    value = prsCount.toString(),
                    unit = "PRs",
                    label = "Personal Records",
                    iconTint = LiftrixColorsV2.DataViz.Warning,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DarkMetricCard(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    // Compact dark card design matching reference exactly
    Card(
        modifier = modifier
            .height(85.dp), // Much more compact height
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: Icon and label together
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Normal
                )
            }
            
            // Bottom: Value and unit
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonalRecordsSection(
    records: List<PersonalRecord>,
    weightUnitManager: WeightUnitManager?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.DataViz.Warning.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, LiftrixColorsV2.DataViz.Warning.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = "Personal records",
                    tint = LiftrixColorsV2.DataViz.Warning,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Personal Records! 🎯",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            records.forEach { record ->
                PRItem(
                    record = record,
                    weightUnitManager = weightUnitManager
                )
                if (record != records.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PRItem(
    record: PersonalRecord,
    weightUnitManager: WeightUnitManager?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = record.exerciseName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = record.type,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = weightUnitManager?.formatWeightText(record.value) ?: record.value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = LiftrixColorsV2.DataViz.Warning
        )
    }
}

@Composable
private fun ExerciseSummaryCard(
    exercises: List<ExerciseSummary>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exercise Summary",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LiftrixColorsV2.Teal.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "View details",
                        tint = LiftrixColorsV2.Teal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Exercise list with modern styling
            exercises.forEachIndexed { index, exercise ->
                if (index < 4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = exercise.name,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${exercise.sets} × ${exercise.reps}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = LiftrixColorsV2.Teal
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(LiftrixColorsV2.Teal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add set",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (index < exercises.size - 1 && index < 3) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            if (exercises.size > 4) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LiftrixColorsV2.Teal.copy(alpha = 0.1f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ ${exercises.size - 4} more exercises",
                        fontSize = 14.sp,
                        color = LiftrixColorsV2.Teal,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareActionsCard(
    onShareWorkout: () -> Unit,
    onShareToSocialFeed: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onShareToSocialFeed,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = LiftrixColorsV2.Teal,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Complete",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Complete")
        }
        
        OutlinedButton(
            onClick = onShareWorkout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LiftrixColorsV2.Teal
            ),
            border = BorderStroke(1.dp, LiftrixColorsV2.Teal)
        ) {
            Icon(
                Icons.Default.ShareLocation,
                contentDescription = "Share externally",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Externally")
        }
    }
}


@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Go Back")
            }
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val seconds = duration.toSecondsPart()
    
    return when {
        hours > 0 -> String.format("%dh %02dm", hours, minutes)
        minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
}

data class PersonalRecord(
    val exerciseName: String,
    val type: String,
    val value: String
)

data class ExerciseSummary(
    val name: String,
    val sets: Int,
    val reps: String
)

enum class ShareMethod {
    InstagramStory,
    CopyLink,
    WhatsApp,
    SaveImage
}
