package com.example.liftrix.ui.chat.workoutbuilder.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.ai.WorkoutGenerationPreferences
import com.example.liftrix.ui.components.cards.LiftrixCard

@Composable
fun WorkoutPreferenceSummary(preferences: WorkoutGenerationPreferences, online: Boolean, onEdit: () -> Unit, onGenerate: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Review your training brief", style = MaterialTheme.typography.headlineSmall)
        LiftrixCard(Modifier.fillMaxWidth(), contentDescription = "Workout preference summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${preferences.goal.name} · ${preferences.level.name}", style = MaterialTheme.typography.titleMedium)
                Text("${preferences.trainingDays.joinToString { it.name.take(3) }} · ${preferences.sessionDurationMinutes} min")
                Text("Equipment: ${preferences.availableEquipment.joinToString { it.displayName }}")
                if (preferences.limitations.isNotBlank()) Text("Limitations: ${preferences.limitations}")
                if (preferences.additionalPreferences.isNotBlank()) Text("Preferences: ${preferences.additionalPreferences}")
            }
        }
        if (!online) Text("Connect to the internet to generate this plan.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Edit") }
            Button(onClick = onGenerate, enabled = online, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Generate plan") }
        }
    }
}
