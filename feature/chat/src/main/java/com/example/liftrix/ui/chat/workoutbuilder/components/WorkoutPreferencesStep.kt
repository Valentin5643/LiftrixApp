package com.example.liftrix.ui.chat.workoutbuilder.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ai.*
import com.example.liftrix.ui.chat.workoutbuilder.validationErrors

private val ControlShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutPreferencesStep(
    preferences: WorkoutGenerationPreferences,
    onChange: (WorkoutGenerationPreferences) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Text(
                "Build around your life",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
        item {
            ChoiceSection("Goal", Icons.Outlined.TrackChanges) {
                ChoiceGrid(columns = 3) {
                    WorkoutProgramGoal.entries.forEach { goal ->
                        SelectableControl(
                            text = goal.displayLabel(),
                            selected = preferences.goal == goal,
                            onClick = { onChange(preferences.copy(goal = goal)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            ChoiceSection("Experience", Icons.Outlined.QueryStats) {
                ChoiceGrid(columns = 3) {
                    WorkoutProgramLevel.entries.forEach { level ->
                        SelectableControl(
                            text = level.displayLabel(),
                            selected = preferences.level == level,
                            showSelectionIcon = false,
                            onClick = { onChange(preferences.copy(level = level)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            ChoiceSection("Equipment", Icons.Outlined.FitnessCenter) {
                ChoiceGrid(columns = 3) {
                    Equipment.entries.forEach { equipment ->
                        SelectableControl(
                            text = equipment.displayName,
                            selected = equipment in preferences.availableEquipment,
                            onClick = {
                                val next = preferences.availableEquipment.toMutableSet().apply {
                                    if (!add(equipment)) remove(equipment)
                                }
                                onChange(preferences.copy(availableEquipment = next))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            ChoiceSection("Training days", Icons.Outlined.CalendarMonth) {
                ChoiceGrid(columns = 5) {
                    WorkoutTrainingDay.entries.forEach { day ->
                        SelectableControl(
                            text = day.name.take(3).lowercase().replaceFirstChar(Char::uppercase),
                            selected = day in preferences.trainingDays,
                            compact = true,
                            onClick = {
                                val next = (if (day in preferences.trainingDays) {
                                    preferences.trainingDays.filterNot { it == day }
                                } else {
                                    preferences.trainingDays + day
                                }).distinct().sortedBy { it.ordinal }
                                onChange(preferences.copy(trainingDays = next))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(Icons.Outlined.Schedule, "Session: ${preferences.sessionDurationMinutes} minutes")
                Slider(
                    value = preferences.sessionDurationMinutes.toFloat(),
                    onValueChange = {
                        onChange(preferences.copy(sessionDurationMinutes = ((it / 5).toInt() * 5).coerceIn(5, 90)))
                    },
                    valueRange = 5f..90f,
                    steps = 16,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        activeTickColor = colors.onPrimary.copy(alpha = .65f),
                        inactiveTrackColor = colors.surfaceVariant,
                        inactiveTickColor = colors.onSurfaceVariant.copy(alpha = .3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            BoundedTextArea(
                value = preferences.limitations,
                onValueChange = { onChange(preferences.copy(limitations = it.take(500))) },
                label = "Limitations (optional)",
                icon = Icons.Outlined.Notes,
                footer = "${preferences.limitations.length}/500 — not medical advice"
            )
        }
        item {
            BoundedTextArea(
                value = preferences.additionalPreferences,
                onValueChange = { onChange(preferences.copy(additionalPreferences = it.take(500))) },
                label = "Other preferences (optional)",
                icon = Icons.Outlined.ChatBubbleOutline,
                footer = "${preferences.additionalPreferences.length}/500"
            )
        }
        val errors = preferences.validationErrors()
        if (errors.isNotEmpty()) {
            item { Text(errors.joinToString("\n"), color = colors.error, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Button(
                onClick = onReview,
                enabled = errors.isEmpty(),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp)
            ) {
                Text("Review preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ChoiceSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(icon, title)
        content()
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGrid(columns: Int, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun SelectableControl(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showSelectionIcon: Boolean = !compact
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        selected = selected,
        shape = ControlShape,
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .28f) else colors.outlineVariant),
        shadowElevation = if (selected) 1.dp else 2.dp,
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected && showSelectionIcon) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text,
                textAlign = TextAlign.Center,
                maxLines = if (compact || !showSelectionIcon) 1 else 2,
                softWrap = !(compact || !showSelectionIcon),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BoundedTextArea(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, footer: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        supportingText = { Text(footer) },
        minLines = 3,
        maxLines = 5,
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun WorkoutProgramGoal.displayLabel() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun WorkoutProgramLevel.displayLabel() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
