package com.example.liftrix.ui.workout.details

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.ui.common.components.WeightDisplay
import com.example.liftrix.ui.navigation.LiftrixRoute
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.theme.rememberWeightUnitManager
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Detailed workout view screen showing all exercises and sets from a completed workout.
 * Provides comprehensive view of the workout with sharing capabilities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailsScreen(
    workoutId: String,
    navController: NavController,
    viewModel: WorkoutDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(workoutId) {
        viewModel.loadWorkoutDetails(workoutId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Workout Details",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(
                                LiftrixRoute.ShareWorkout(workoutId)
                            )
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is WorkoutDetailsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LiftrixColorsV2.Teal)
                }
            }
            
            is WorkoutDetailsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Workout Header
                    item {
                        WorkoutHeaderCard(
                            workoutName = state.workoutName,
                            date = state.date,
                            duration = state.duration,
                            totalVolume = state.totalVolume
                        )
                    }
                    
                    // Quick Stats
                    item {
                        QuickStatsRow(
                            totalSets = state.totalSets,
                            totalReps = state.totalReps,
                            avgRestTime = state.avgRestTime,
                            prsCount = state.prsCount
                        )
                    }
                    
                    // Exercise List Header
                    item {
                        Text(
                            text = "Exercises (${state.exercises.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiftrixColorsV2.Dark.TextPrimary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    // Exercise Details
                    itemsIndexed(state.exerciseDataWithPRs) { index, exerciseWithPRData ->
                        ExerciseDetailCard(
                            exercise = exerciseWithPRData.exercise,
                            exerciseNumber = exerciseWithPRData.exerciseNumber,
                            setsWithPRData = exerciseWithPRData.setsWithPRData,
                            totalPRsInExercise = exerciseWithPRData.totalPRsInExercise
                        )
                    }
                    
                    // Notes Section (if any)
                    if (state.notes.isNotBlank()) {
                        item {
                            NotesCard(notes = state.notes)
                        }
                    }
                    
                    // Action Buttons
                    item {
                        ActionButtonsRow(
                            onRepeatWorkout = {
                                // Start new workout with same template
                                viewModel.repeatWorkout(workoutId)
                            },
                            onEditWorkout = {
                                navController.navigate(
                                    LiftrixRoute.EditWorkout(workoutId)
                                )
                            },
                            onShareWorkout = {
                                navController.navigate(
                                    LiftrixRoute.ShareWorkout(workoutId)
                                )
                            }
                        )
                    }
                }
            }
            
            is WorkoutDetailsUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.loadWorkoutDetails(workoutId) },
                    onBack = { navController.navigateUp() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun WorkoutHeaderCard(
    workoutName: String,
    date: String,
    duration: Duration,
    totalVolume: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                LiftrixColorsV2.Teal.copy(alpha = 0.8f),
                                LiftrixColorsV2.TealDark.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = workoutName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = date,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = formatDuration(duration),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${totalVolume / 1000.0} tons",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Total Volume",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatsRow(
    totalSets: Int,
    totalReps: Int,
    avgRestTime: Duration?,
    prsCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(
            icon = Icons.Default.CheckCircle,
            value = totalSets.toString(),
            label = "Sets",
            modifier = Modifier.weight(1f)
        )
        StatChip(
            icon = Icons.Default.Repeat,
            value = totalReps.toString(),
            label = "Reps",
            modifier = Modifier.weight(1f)
        )
        avgRestTime?.let {
            StatChip(
                icon = Icons.Default.Timer,
                value = formatRestTime(it),
                label = "Avg Rest",
                modifier = Modifier.weight(1f)
            )
        }
        if (prsCount > 0) {
            StatChip(
                icon = Icons.Default.Star,
                value = prsCount.toString(),
                label = "PRs",
                modifier = Modifier.weight(1f),
                highlight = true
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    // Compact dark card matching the simplified metric cards
    Card(
        modifier = modifier
            .height(60.dp), // Much smaller height
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) LiftrixColorsV2.Dark.Warning else LiftrixColorsV2.Teal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ExerciseDetailCard(
    exercise: Exercise,
    exerciseNumber: Int,
    setsWithPRData: List<SetWithPRData>,
    totalPRsInExercise: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.SurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Exercise Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#$exerciseNumber",
                        fontSize = 12.sp,
                        color = LiftrixColorsV2.Dark.TextSecondary
                    )
                    Text(
                        text = exercise.libraryExercise.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = LiftrixColorsV2.Dark.TextPrimary
                    )
                    Text(
                        text = exercise.libraryExercise.primaryMuscleGroup.name,
                        fontSize = 13.sp,
                        color = LiftrixColorsV2.Dark.TextSecondary
                    )
                }
                
                // Exercise Stats with PR count
                Column(horizontalAlignment = Alignment.End) {
                    val weightUnitManager = rememberWeightUnitManager()
                    val totalVolumeInKg = exercise.sets.sumOf { set ->
                        val weight = set.weight?.kilograms ?: 0.0
                        val reps = set.reps?.count ?: 0
                        weight * reps
                    }
                    
                    // Use reactive weight display for volume
                    val volumeText = weightUnitManager?.formatWeightCompact(totalVolumeInKg, WeightUnit.KILOGRAMS)
                        ?: "${totalVolumeInKg.toInt()}kg"
                    
                    Text(
                        text = volumeText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = LiftrixColorsV2.Teal
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "volume",
                            fontSize = 11.sp,
                            color = LiftrixColorsV2.Dark.TextSecondary
                        )
                        if (totalPRsInExercise > 0) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "PRs",
                                tint = LiftrixColorsV2.Dark.Warning,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = totalPRsInExercise.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiftrixColorsV2.Dark.Warning
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Set",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.Dark.TextSecondary,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    text = "Previous",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.Dark.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Weight",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.Dark.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Reps",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.Dark.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Rest",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LiftrixColorsV2.Dark.TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = LiftrixColorsV2.Dark.TextSecondary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            setsWithPRData.forEachIndexed { index, setWithPRData ->
                SetRow(
                    setNumber = index + 1,
                    set = setWithPRData.set,
                    isPersonalRecord = setWithPRData.isPersonalRecord,
                    previousSetData = setWithPRData.previousSetData,
                    restTime = setWithPRData.restTime
                )
                if (index < setsWithPRData.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            
            // Exercise Notes
            exercise.notes?.let { notes ->
                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = LiftrixColorsV2.Dark.TextSecondary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = LiftrixColorsV2.Dark.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = notes,
                            fontSize = 13.sp,
                            color = LiftrixColorsV2.Dark.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    set: ExerciseSet,
    isPersonalRecord: Boolean,
    previousSetData: com.example.liftrix.domain.usecase.workout.PreviousSetData?,
    restTime: Duration?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = setNumber.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (set.completedAt != null) {
                        LiftrixColorsV2.Dark.TextPrimary
                    } else {
                        LiftrixColorsV2.Dark.TextSecondary
                    }
                )
                if (isPersonalRecord) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "PR",
                        tint = LiftrixColorsV2.Dark.Warning,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        
        // Previous
        Text(
            text = previousSetData?.formatForDisplay() ?: "-",
            fontSize = 13.sp,
            color = LiftrixColorsV2.Dark.TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        
        // Actual Weight - Use reactive weight display
        val weightUnitManager = rememberWeightUnitManager()
        val weightText = set.weight?.kilograms?.let { weightValue ->
            weightUnitManager?.formatWeightCompact(weightValue, WeightUnit.KILOGRAMS) ?: "${weightValue.toInt()}kg"
        } ?: "-"
        
        Text(
            text = weightText,
            fontSize = 14.sp,
            fontWeight = if (set.completedAt != null) FontWeight.Medium else FontWeight.Normal,
            color = if (set.completedAt != null) {
                LiftrixColorsV2.Dark.TextPrimary
            } else {
                LiftrixColorsV2.Dark.TextSecondary
            },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        
        // Actual Reps
        Text(
            text = set.reps?.count?.toString() ?: "-",
            fontSize = 14.sp,
            fontWeight = if (set.completedAt != null) FontWeight.Medium else FontWeight.Normal,
            color = if (set.completedAt != null) {
                LiftrixColorsV2.Dark.TextPrimary
            } else {
                LiftrixColorsV2.Dark.TextSecondary
            },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        
        // Rest Time
        Text(
            text = restTime?.let { formatRestTime(it) } ?: "-",
            fontSize = 13.sp,
            color = LiftrixColorsV2.Dark.TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun NotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LiftrixColorsV2.Dark.SurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Notes,
                    contentDescription = null,
                    tint = LiftrixColorsV2.Dark.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Workout Notes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LiftrixColorsV2.Dark.TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notes,
                fontSize = 14.sp,
                color = LiftrixColorsV2.Dark.TextPrimary
            )
        }
    }
}

@Composable
private fun ActionButtonsRow(
    onRepeatWorkout: () -> Unit,
    onEditWorkout: () -> Unit,
    onShareWorkout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onRepeatWorkout,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LiftrixColorsV2.Teal
            )
        ) {
            Icon(
                Icons.Default.Repeat,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Repeat", fontSize = 14.sp)
        }
        
        OutlinedButton(
            onClick = onEditWorkout,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = LiftrixColorsV2.Dark.TextPrimary
            )
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit", fontSize = 14.sp)
        }
        
        Button(
            onClick = onShareWorkout,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = LiftrixColorsV2.Teal
            )
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share", fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = LiftrixColorsV2.Dark.Error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Unable to load workout",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = LiftrixColorsV2.Dark.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = LiftrixColorsV2.Dark.TextSecondary,
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
        hours > 0 -> String.format("%dh %02dm %02ds", hours, minutes, seconds)
        minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
}

private fun formatRestTime(duration: Duration): String {
    val minutes = duration.toMinutes()
    val seconds = duration.toSecondsPart()
    
    return when {
        minutes > 0 -> String.format("%d:%02d", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
}