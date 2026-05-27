package com.example.liftrix.ui.progress.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.model.analytics.StrengthExerciseForecast
import com.example.liftrix.domain.model.analytics.StrengthForecastPoint
import com.example.liftrix.domain.model.analytics.StrengthForecastPointType
import com.example.liftrix.ui.progress.StrengthForecastEvent
import com.example.liftrix.ui.progress.StrengthForecastState
import kotlinx.datetime.daysUntil
import kotlin.math.abs

@Composable
fun StrengthForecastCard(
    state: StrengthForecastState,
    weightUnit: WeightUnit,
    onEvent: (StrengthForecastEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Strength Forecast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            val forecast = state.forecast
            val selected = state.selectedExercise
            when {
                state.errorMessage != null -> Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                forecast == null || forecast.isEmpty -> Text("Not enough data to generate forecast", color = MaterialTheme.colorScheme.onSurfaceVariant)
                selected == null -> Text("Not enough data to generate forecast", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    ExerciseSelector(
                        forecasts = forecast.exercises,
                        selectedExerciseId = selected.exerciseId,
                        onSelected = { onEvent(StrengthForecastEvent.SelectExercise(it)) }
                    )
                    ForecastChart(selected, weightUnit)
                    Text(
                        text = selected.summary.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    selected.summary.projectedChangeKg?.let { change ->
                        val sign = if (change >= 0.0) "+" else "-"
                        Text(
                            text = "$sign${weightUnit.formatWeight(abs(change), 1)} projected in 14 days",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (change >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseSelector(
    forecasts: List<StrengthExerciseForecast>,
    selectedExerciseId: String,
    onSelected: (String) -> Unit
) {
    if (forecasts.size <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        forecasts.take(3).forEach { forecast ->
            OutlinedButton(
                onClick = { onSelected(forecast.exerciseId) },
                enabled = forecast.exerciseId != selectedExerciseId,
                modifier = Modifier.weight(1f)
            ) {
                Text(forecast.exerciseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ForecastChart(
    forecast: StrengthExerciseForecast,
    weightUnit: WeightUnit
) {
    val points = remember(forecast, weightUnit) { forecast.allPoints }
    var selectedPoint by remember(forecast) { mutableStateOf<StrengthForecastPoint?>(null) }
    val historicalColor = MaterialTheme.colorScheme.primary
    val forecastColor = Color(0xFF20C9B7)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .drawWithCache {
                    val padding = 22.dp.toPx()
                    val chartWidth = (size.width - padding * 2).coerceAtLeast(1f)
                    val chartHeight = (size.height - padding * 2).coerceAtLeast(1f)
                    val values = points.map { weightUnit.convertFromKilograms(it.estimatedOneRmKg) }
                    val minValue = values.minOrNull()?.let { it - 2.5 } ?: 0.0
                    val maxValue = values.maxOrNull()?.let { it + 2.5 } ?: 1.0
                    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
                    val firstDate = points.minOfOrNull { it.date }
                    val timelineValues = points.map { point ->
                        point.timelineDay ?: (firstDate?.daysUntil(point.date)?.toDouble() ?: 0.0)
                    }
                    val firstTimeline = timelineValues.minOrNull() ?: 0.0
                    val timelineSpan = ((timelineValues.maxOrNull() ?: firstTimeline) - firstTimeline).takeIf { it > 0.0 } ?: 1.0
                    fun position(point: StrengthForecastPoint): Offset {
                        val timelineOffset = (point.timelineDay ?: (firstDate?.daysUntil(point.date)?.toDouble() ?: 0.0)) - firstTimeline
                        val x = padding + chartWidth * (timelineOffset.toFloat() / timelineSpan.toFloat())
                        val converted = weightUnit.convertFromKilograms(point.estimatedOneRmKg)
                        val y = padding + chartHeight * (1f - ((converted - minValue) / range).toFloat())
                        return Offset(x, y)
                    }
                    val historicalPath = Path()
                    points.filter { it.type == StrengthForecastPointType.HISTORICAL }.forEachIndexed { index, point ->
                        val offset = position(point)
                        if (index == 0) historicalPath.moveTo(offset.x, offset.y) else historicalPath.lineTo(offset.x, offset.y)
                    }
                    val historicalPoints = points.filter { it.type == StrengthForecastPointType.HISTORICAL }
                    val forecastPoints = points.filter { it.type == StrengthForecastPointType.FORECAST }
                    val forecastSeries = buildList {
                        historicalPoints.lastOrNull()?.let(::add)
                        addAll(forecastPoints)
                    }
                    val forecastPath = Path()
                    forecastSeries.forEachIndexed { index, point ->
                        val offset = position(point)
                        if (index == 0) forecastPath.moveTo(offset.x, offset.y) else forecastPath.lineTo(offset.x, offset.y)
                    }
                    onDrawBehind {
                        drawLine(axisColor, Offset(padding, padding), Offset(padding, size.height - padding), 1.dp.toPx())
                        drawLine(axisColor, Offset(padding, size.height - padding), Offset(size.width - padding, size.height - padding), 1.dp.toPx())
                        drawPath(historicalPath, historicalColor, style = Stroke(width = 3.dp.toPx()))
                        drawPath(
                            forecastPath,
                            forecastColor,
                            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                        )
                        points.forEach { point ->
                            val offset = position(point)
                            if (point.type == StrengthForecastPointType.HISTORICAL) {
                                drawCircle(historicalColor, 4.dp.toPx(), offset)
                            } else {
                                drawCircle(forecastColor, 4.dp.toPx(), offset, style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                    }
                }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        selectedPoint = points.minByOrNull { point ->
                            val index = points.indexOf(point).coerceAtLeast(0)
                            abs(tap.x - (size.width * index.toFloat() / points.lastIndex.coerceAtLeast(1).toFloat()))
                        }
                    }
                }
        )
        selectedPoint?.let {
            Text(
                text = "${it.date}: ${weightUnit.formatWeight(it.estimatedOneRmKg, 1)}",
                style = MaterialTheme.typography.labelMedium,
                color = labelColor
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(historicalColor, filled = true, label = "History")
            LegendDot(forecastColor, filled = false, label = "Forecast")
        }
    }
}

@Composable
private fun LegendDot(color: Color, filled: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (filled) color else Color.Transparent, CircleShape)
                .border(1.5.dp, color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
