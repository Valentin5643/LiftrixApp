package com.example.liftrix.ui.chat.workoutbuilder.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.ai.*
import com.example.liftrix.ui.components.cards.LiftrixCard

@Composable
fun GeneratedWorkoutPlanPreview(
    result: WorkoutGenerationResult,
    expandedDays: Set<Int> = setOf(0),
    savedDays: List<SavedGeneratedWorkoutDay> = emptyList(),
    compact: Boolean = false,
    actionsEnabled: Boolean = true,
    onToggleDay: (Int) -> Unit = {},
    onEdit: () -> Unit = {},
    onReplace: (Int, String) -> Unit = { _, _ -> },
    onRegenerateDay: (Int) -> Unit = {},
    onRegeneratePlan: () -> Unit = {},
    onSave: () -> Unit = {},
    onStart: (String) -> Unit = {},
    onEditSaved: (String) -> Unit = {},
    onReturnToChat: () -> Unit = {}
) {
    val program = result.program
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(program.workoutName, style = MaterialTheme.typography.headlineSmall)
            Text(program.description)
            Text(program.days.joinToString(" · ") { "${it.scheduledDay?.name?.take(3) ?: "Day"}: ${it.focus}" })
        }
        items(program.days.size, key = { "${result.previewId}-$it" }) { index ->
            val day = program.days[index]
            val saved = savedDays.firstOrNull { it.dayIndex == index }
            LiftrixCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onToggleDay(index) },
                contentDescription = "${day.dayName}, ${if (index in expandedDays) "expanded" else "collapsed"}"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${day.scheduledDay?.name ?: "DAY"} · ${day.dayName}", style = MaterialTheme.typography.titleMedium)
                    Text("${day.focus} · ${day.estimatedDurationMinutes} min")
                    if (index in expandedDays || compact) {
                        Phase("Warm-up", day.warmUp)
                        day.exercises.forEach { exercise ->
                            HorizontalDivider()
                            Text(exercise.exerciseName, style = MaterialTheme.typography.titleSmall)
                            Text("${exercise.sets} sets · ${exercise.prescription()} · ${exercise.restSeconds}s rest")
                            Text("${exercise.equipment.displayName} · ${exercise.primaryMuscle.displayName}")
                            exercise.notes?.takeIf(String::isNotBlank)?.let { Text(it) }
                            if (!compact) TextButton(onClick = { onReplace(index, exercise.exerciseId) }, enabled = actionsEnabled) { Text("Replace") }
                        }
                        Phase("Cooldown", day.coolDown)
                        if (!compact) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onRegenerateDay(index) }, enabled = actionsEnabled) { Text("Regenerate day") }
                                saved?.let {
                                    Button(onClick = { onStart(it.template.id.value) }) { Text("Start") }
                                    TextButton(onClick = { onEditSaved(it.template.id.value) }) { Text("Edit saved") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!compact) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = actionsEnabled) { Text("Edit details") }
                    OutlinedButton(onClick = onRegeneratePlan, enabled = actionsEnabled) { Text("Regenerate plan") }
                }
                Button(onClick = onSave, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (savedDays.isEmpty()) "Save plan" else "Retry remaining days") }
                TextButton(onClick = onReturnToChat, modifier = Modifier.fillMaxWidth()) { Text("Return to conversation") }
            }
        }
    }
}

@Composable private fun Phase(title: String, phase: GeneratedWorkoutPhase) {
    Text("$title · ${phase.durationMinutes} min", style = MaterialTheme.typography.titleSmall)
    phase.steps.forEach { Text("• $it") }
}

private fun GeneratedWorkoutExercise.prescription(): String = when (type) {
    GeneratedPrescriptionType.REPS -> "${repsMin}-${repsMax} reps"
    GeneratedPrescriptionType.TIME -> "${durationSeconds}s"
}
