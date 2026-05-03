package com.example.liftrix.ui.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSharedWithYouScreen(
    shareId: String,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutSharedWithYouViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(shareId) {
        viewModel.load(shareId)
    }

    LaunchedEffect(uiState.saveComplete) {
        if (uiState.saveComplete) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Shared With You") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onNavigateBack) { Text("Back") }
                }
            }
            else -> {
                val preview = uiState.preview
                val template = preview?.template
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = template?.name ?: "Workout deleted",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Shared by ${preview?.senderName ?: "Gym Buddy"}")
                        template?.estimatedDurationMinutes?.let { Text("$it minutes") }
                        template?.difficultyLevel?.let { Text("Difficulty $it") }
                    }

                    if (template == null) {
                        item {
                            Text(
                                text = "The sender deleted this workout before you saved it.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        items(template.exercises, key = { "${it.exerciseId.value}-${it.orderIndex}" }) { exercise ->
                            ListItem(
                                headlineContent = { Text(exercise.name) },
                                supportingContent = {
                                    Text(
                                        "${exercise.targetSets ?: 0} sets" +
                                            (exercise.targetReps?.let { ", ${it.count} reps" } ?: "") +
                                            (exercise.targetWeight?.let { ", ${it.format()}" } ?: "")
                                    )
                                }
                            )
                        }

                        item {
                            Button(
                                onClick = { viewModel.saveToMyWorkouts(shareId) },
                                enabled = !uiState.isSaving,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Text("Save to My Workouts")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
