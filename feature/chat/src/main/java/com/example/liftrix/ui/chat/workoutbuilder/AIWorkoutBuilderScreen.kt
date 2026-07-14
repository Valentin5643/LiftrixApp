package com.example.liftrix.ui.chat.workoutbuilder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.ui.chat.workoutbuilder.components.*

@Composable
fun AIWorkoutBuilderScreen(
    onNavigateBack: () -> Unit,
    onReturnToConversation: () -> Unit,
    onStartWorkout: (String) -> Unit,
    onEditWorkout: (String) -> Unit,
    viewModel: AIWorkoutBuilderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEdit by remember { mutableStateOf(false) }
    var confirmRegenerate by remember { mutableStateOf(false) }

    BackHandler {
        if (state.dirty || state.step == BuilderStep.GENERATING) viewModel.requestDiscard() else onNavigateBack()
    }
    Box(Modifier.fillMaxSize()) {
        when (state.step) {
            BuilderStep.FORM -> WorkoutPreferencesStep(state.draft, viewModel::updateDraft, viewModel::review, Modifier.fillMaxSize())
            BuilderStep.REVIEW -> WorkoutPreferenceSummary(state.draft, state.isOnline, viewModel::editPreferences) { viewModel.generate() }
            BuilderStep.GENERATING -> WorkoutGenerationLoading(state.generationStage)
            BuilderStep.PREVIEW, BuilderStep.SAVING, BuilderStep.PARTIAL, BuilderStep.SAVED -> state.result?.let { result ->
                GeneratedWorkoutPlanPreview(
                    result = result,
                    expandedDays = state.expandedDays,
                    savedDays = state.savedDays,
                    actionsEnabled = state.activeAction == null && state.isOnline,
                    onToggleDay = viewModel::toggleDay,
                    onEdit = { showEdit = true },
                    onReplace = viewModel::replaceExercise,
                    onRegenerateDay = viewModel::regenerateDay,
                    onRegeneratePlan = { confirmRegenerate = true },
                    onSave = viewModel::save,
                    onStart = onStartWorkout,
                    onEditSaved = onEditWorkout,
                    onReturnToChat = onReturnToConversation
                )
            }
        }
        state.error?.let { message ->
            Snackbar(Modifier.align(Alignment.BottomCenter), action = { TextButton(onClick = viewModel::dismissError) { Text("Dismiss") } }) { Text(message) }
        }
        if (!state.isOnline) AssistChip(onClick = {}, enabled = false, label = { Text("Offline — AI actions are unavailable") }, modifier = Modifier.align(Alignment.TopCenter))
    }
    if (showEdit) state.result?.let { result -> EditGeneratedWorkoutDialog(result.program, { showEdit = false }) { viewModel.applyLocalEdit(it); showEdit = false } }
    if (confirmRegenerate) AlertDialog(
        onDismissRequest = { confirmRegenerate = false },
        title = { Text("Regenerate complete plan?") },
        text = { Text("This uses another AI call. Previously saved workout days will not be deleted.") },
        confirmButton = { TextButton(onClick = { confirmRegenerate = false; viewModel.generate(forceRefresh = true) }) { Text("Regenerate") } },
        dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text("Cancel") } }
    )
    if (state.showDiscardDialog) AlertDialog(
        onDismissRequest = viewModel::dismissDiscard,
        title = { Text("Discard unsaved plan?") },
        text = { Text("Your reviewed preferences and unsaved preview changes will be discarded. Saved workout days stay saved.") },
        confirmButton = { TextButton(onClick = onNavigateBack) { Text("Discard") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDiscard) { Text("Keep editing") } }
    )
}
