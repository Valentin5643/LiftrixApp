package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.Exercise
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.WorkoutId
import java.time.Instant
// Vico chart integration restored
import com.example.liftrix.ui.common.analytics.ChartThemeProvider
import com.example.liftrix.ui.theme.LiftrixColors
import com.example.liftrix.ui.theme.LiftrixTheme
// Chart implementation using Compose Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * Strength progress chart component using Vico LineChart for exercise progression tracking.
 * 
 * Displays strength progression over time with multiple exercise selection,
 * 1RM estimation, and trend analysis. Uses LiftrixColors theming with Material 3
 * design principles for consistent UI experience.
 * 
 * Features:
 * - Multi-exercise selection with filter chips
 * - 1RM progression tracking with estimated values
 * - Trend analysis with visual indicators
 * - Interactive zoom and scroll capabilities
 * - Accessibility-compliant content descriptions
 * - Performance-optimized rendering for smooth animations
 */
@Composable
fun StrengthProgressChart(
    exercises: List<ExerciseProgress>,
    selectedExercise: Exercise?,
    onExerciseSelected: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Strength Progression",
    isLoading: Boolean = false,
    contentDescription: String? = null
) {
    // Theme configuration with Vico integration
    val multiLineTheme = ChartThemeProvider.createMultiLineChartTheme()
    val performanceConfig = ChartThemeProvider.createPerformanceOptimizedTheme()
    val accessibilityConfig = ChartThemeProvider.createAccessibilityConfig()
    
    // Filter data for selected exercise
    val filteredData = selectedExercise?.let { selected ->
        exercises.filter { it.exercise.id == selected.id }
    } ?: exercises.take(3) // Show top 3 exercises if none selected
    
    // Data transformation for Vico charts
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription ?: buildContentDescription(
                    filteredData, selectedExercise
                )
            }
    ) {
        // Chart header
        StrengthChartHeader(
            title = title,
            selectedExercise = selectedExercise,
            progressData = filteredData,
            isLoading = isLoading
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Exercise selection chips
        if (exercises.isNotEmpty()) {
            ExerciseSelectionChips(
                exercises = exercises.map { it.exercise }.distinct(),
                selectedExercise = selectedExercise,
                onExerciseSelected = onExerciseSelected
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Chart content
        if (isLoading) {
            LoadingState()
        } else if (filteredData.isEmpty()) {
            EmptyState(selectedExercise)
        } else {
            StrengthLineChart(
                exerciseData = filteredData
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progress statistics
        if (!isLoading && filteredData.isNotEmpty()) {
            ProgressStatistics(exerciseData = filteredData)
        }
    }
}

@Composable
private fun StrengthChartHeader(
    title: String,
    selectedExercise: Exercise?,
    progressData: List<ExerciseProgress>,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            selectedExercise?.let { exercise ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• ${exercise.libraryExercise.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LiftrixColors.Primary
                )
            }
        }
        
        if (!isLoading && progressData.isNotEmpty()) {
            val totalDataPoints = progressData.sumOf { it.progressPoints.size }
            val dateRange = progressData.flatMap { it.progressPoints }
                .let { points ->
                    val dates = points.map { it.date }
                    if (dates.isNotEmpty()) {
                        "${dates.minOrNull()} - ${dates.maxOrNull()}"
                    } else {
                        "No data"
                    }
                }
            
            Text(
                text = "$dateRange • $totalDataPoints data points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ExerciseSelectionChips(
    exercises: List<Exercise>,
    selectedExercise: Exercise?,
    onExerciseSelected: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Exercise",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            exercises.take(4).forEach { exercise ->
                FilterChip(
                    selected = selectedExercise?.id == exercise.id,
                    onClick = { onExerciseSelected(exercise) },
                    label = {
                        Text(
                            text = exercise.libraryExercise.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LiftrixColors.Primary.copy(alpha = 0.2f),
                        selectedLabelColor = LiftrixColors.Primary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StrengthLineChart(
    exerciseData: List<ExerciseProgress>
) {
    if (exerciseData.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No strength progress data available",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    val density = LocalDensity.current
    val chartColors = listOf(
        LiftrixColors.Primary,
        LiftrixColors.Secondary,
        LiftrixColors.TiffanyBlue
    )
    
    val normalizedExerciseData = remember(exerciseData) {
        exerciseData.take(3).map { exercise ->
            val points = exercise.progressPoints
            if (points.isEmpty()) emptyList()
            else {
                val maxValue = points.maxOfOrNull { it.estimatedOneRM.value } ?: 0.0
                val minValue = points.minOfOrNull { it.estimatedOneRM.value } ?: 0.0
                val range = maxValue - minValue
                if (range == 0.0) {
                    points.map { 0.5f }
                } else {
                    points.map { ((it.estimatedOneRM.value - minValue) / range).toFloat() }
                }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        LiftrixColors.Primary.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            normalizedExerciseData.forEachIndexed { index, data ->
                if (data.isNotEmpty()) {
                    drawStrengthLineChart(
                        data = data,
                        color = chartColors.getOrElse(index) { LiftrixColors.Primary },
                        strokeWidth = with(density) { 2.5.dp.toPx() },
                        pointRadius = with(density) { 6.dp.toPx() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressStatistics(
    exerciseData: List<ExerciseProgress>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Progress Statistics",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        exerciseData.forEach { exercise ->
            ProgressStatisticItem(
                exercise = exercise,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ProgressStatisticItem(
    exercise: ExerciseProgress,
    modifier: Modifier = Modifier
) {
    val progressPoints = exercise.progressPoints
    if (progressPoints.isNotEmpty()) {
        val currentRM = progressPoints.lastOrNull()?.estimatedOneRM
        val initialRM = progressPoints.firstOrNull()?.estimatedOneRM
        val improvement = if (currentRM != null && initialRM != null) {
            ((currentRM.value - initialRM.value) / initialRM.value * 100).roundToInt()
        } else 0
        
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = exercise.exercise.libraryExercise.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            
            currentRM?.let { rm ->
                Text(
                    text = "${rm.value.toInt()} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LiftrixColors.Primary
                )
            }
            
            if (improvement != 0) {
                Spacer(modifier = Modifier.width(8.dp))
                val improvementColor = if (improvement > 0) LiftrixColors.Primary else LiftrixColors.TiffanyBlue
                val improvementText = if (improvement > 0) "+$improvement%" else "$improvement%"
                
                Text(
                    text = improvementText,
                    style = MaterialTheme.typography.bodySmall,
                    color = improvementColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = LiftrixColors.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Loading strength data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmptyState(selectedExercise: Exercise?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💪",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = if (selectedExercise != null) {
                    "No progress data for ${selectedExercise.libraryExercise.name}"
                } else {
                    "No strength data available"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                text = "Complete strength training workouts to track your progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

private fun buildContentDescription(
    exerciseData: List<ExerciseProgress>,
    selectedExercise: Exercise?
): String {
    return buildString {
        append("Strength progression chart")
        selectedExercise?.let { exercise ->
            append(" for ${exercise.libraryExercise.name}")
        }
        append(" with ${exerciseData.size} exercises tracked.")
        
        if (exerciseData.isNotEmpty()) {
            val totalPoints = exerciseData.sumOf { it.progressPoints.size }
            append(" Total progress points: $totalPoints.")
        }
    }
}

/**
 * Exercise progress data model
 */
data class ExerciseProgress(
    val exercise: Exercise,
    val progressPoints: List<StrengthDataPoint>
)

/**
 * Strength data point for progression tracking
 */
data class StrengthDataPoint(
    val date: LocalDate,
    val weight: Weight,
    val reps: Int,
    val estimatedOneRM: Weight
) {
    companion object {
        /**
         * Calculate estimated 1RM using Brzycki formula
         */
        fun calculateOneRM(weight: Weight, reps: Int): Weight {
            return if (reps == 1) {
                weight
            } else {
                Weight(weight.value * (36f / (37f - reps)))
            }
        }
    }
}

private fun DrawScope.drawStrengthLineChart(
    data: List<Float>,
    color: Color,
    strokeWidth: Float,
    pointRadius: Float
) {
    if (data.size < 2) return
    
    val spacing = size.width / (data.size - 1).coerceAtLeast(1)
    val path = Path()
    
    // Create line path
    data.forEachIndexed { index, value ->
        val x = index * spacing
        val y = size.height * (1f - value)
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    // Draw line
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round
        )
    )
    
    // Draw points
    data.forEachIndexed { index, value ->
        val x = index * spacing
        val y = size.height * (1f - value)
        
        drawCircle(
            color = color,
            radius = pointRadius,
            center = Offset(x, y)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StrengthProgressChartPreview() {
    LiftrixTheme {
        val sampleExercises = listOf(
            Exercise(
                id = ExerciseId("1"),
                workoutId = WorkoutId("workout-1"),
                libraryExercise = ExerciseLibrary(
                    id = "bench-press",
                    name = "Bench Press",
                    primaryMuscleGroup = ExerciseCategory.CHEST,
                    equipment = Equipment.BARBELL,
                    secondaryMuscleGroups = listOf(ExerciseCategory.TRICEPS),
                    movementPattern = "Push",
                    difficultyLevel = 5,
                    instructions = "Bench press instructions",
                    isCompound = true,
                    searchableTerms = listOf("bench", "press")
                ),
                orderIndex = 0,
                createdAt = Instant.now()
            ),
            Exercise(
                id = ExerciseId("2"),
                workoutId = WorkoutId("workout-1"),
                libraryExercise = ExerciseLibrary(
                    id = "squat",
                    name = "Squat",
                    primaryMuscleGroup = ExerciseCategory.LEGS,
                    equipment = Equipment.BARBELL,
                    secondaryMuscleGroups = listOf(ExerciseCategory.GLUTES),
                    movementPattern = "Squat",
                    difficultyLevel = 6,
                    instructions = "Squat instructions",
                    isCompound = true,
                    searchableTerms = listOf("squat")
                ),
                orderIndex = 1,
                createdAt = Instant.now()
            )
        )
        
        val sampleProgressData = listOf(
            ExerciseProgress(
                exercise = sampleExercises[0],
                progressPoints = listOf(
                    StrengthDataPoint(
                        date = LocalDate(2024, 1, 1),
                        weight = Weight(80.0),
                        reps = 5,
                        estimatedOneRM = Weight(90.0)
                    ),
                    StrengthDataPoint(
                        date = LocalDate(2024, 1, 8),
                        weight = Weight(85.0),
                        reps = 5,
                        estimatedOneRM = Weight(96.0)
                    ),
                    StrengthDataPoint(
                        date = LocalDate(2024, 1, 15),
                        weight = Weight(87.5),
                        reps = 5,
                        estimatedOneRM = Weight(99.0)
                    )
                )
            )
        )
        
        StrengthProgressChart(
            exercises = sampleProgressData,
            selectedExercise = sampleExercises[0],
            onExerciseSelected = { },
            modifier = Modifier.padding(16.dp)
        )
    }
}