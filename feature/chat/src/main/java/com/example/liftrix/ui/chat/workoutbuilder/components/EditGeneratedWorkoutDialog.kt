package com.example.liftrix.ui.chat.workoutbuilder.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import com.example.liftrix.domain.model.ai.GeneratedWorkoutProgram

@Composable
fun EditGeneratedWorkoutDialog(program: GeneratedWorkoutProgram, onDismiss: () -> Unit, onConfirm: (GeneratedWorkoutProgram) -> Unit) {
    var title by remember(program) { mutableStateOf(program.workoutName) }
    var description by remember(program) { mutableStateOf(program.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit plan details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Plan title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(500) }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(program.copy(workoutName = title.trim(), description = description.trim())) }, enabled = title.isNotBlank() && description.isNotBlank()) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
