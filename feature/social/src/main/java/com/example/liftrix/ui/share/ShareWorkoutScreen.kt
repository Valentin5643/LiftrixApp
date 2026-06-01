package com.example.liftrix.ui.share

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareWorkoutScreen(
    state: ShareWorkoutViewModel.ShareWorkoutUiState,
    effects: Flow<WorkoutShareEffect>,
    onNavigateBack: () -> Unit,
    onTemplateSelected: (String) -> Unit,
    onActionSelected: (WorkoutShareAction) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is WorkoutShareEffect.LaunchShare -> {
                    runCatching { context.startActivity(effect.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { snackbarHostState.showSnackbar("No app can handle this share action") }
                }
                is WorkoutShareEffect.SaveSucceeded -> snackbarHostState.showSnackbar("Story saved to device")
                is WorkoutShareEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Share Workout") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingState(paddingValues)
            state.errorMessage != null -> ErrorState(paddingValues, state.errorMessage)
            state.storyStats != null -> StoryContent(
                paddingValues = paddingValues,
                state = state,
                onTemplateSelected = onTemplateSelected,
                onActionSelected = onActionSelected
            )
        }
    }
}

@Composable
private fun LoadingState(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    paddingValues: PaddingValues,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(LiftrixSpacing.large),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun StoryContent(
    paddingValues: PaddingValues,
    state: ShareWorkoutViewModel.ShareWorkoutUiState,
    onTemplateSelected: (String) -> Unit,
    onActionSelected: (WorkoutShareAction) -> Unit
) {
    val stats = state.storyStats ?: return
    val selectedTemplate = state.selectedTemplate ?: WorkoutShareTemplateCatalog.defaultTemplate

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(LiftrixSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.large)
    ) {
        item {
            WorkoutShareStoryPreview(
                stats = stats,
                template = selectedTemplate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 34.dp)
            )
        }

        item {
            TemplateSelector(
                templates = state.templates,
                selectedTemplateId = selectedTemplate.id,
                onTemplateSelected = onTemplateSelected
            )
        }

        item {
            ShareActions(
                isGenerating = state.isGenerating,
                activeAction = state.activeAction,
                onActionSelected = onActionSelected
            )
        }
    }
}

@Composable
private fun TemplateSelector(
    templates: List<WorkoutShareTemplate>,
    selectedTemplateId: String,
    onTemplateSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)) {
        Text(
            text = "Template",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)) {
            items(templates) { template ->
                TemplateChip(
                    template = template,
                    isSelected = template.id == selectedTemplateId,
                    onClick = { onTemplateSelected(template.id) }
                )
            }
        }
    }
}

@Composable
private fun TemplateChip(
    template: WorkoutShareTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(width = 126.dp, height = 58.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                LiftrixColorsV2.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) LiftrixColorsV2.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = template.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) LiftrixColorsV2.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShareActions(
    isGenerating: Boolean,
    activeAction: WorkoutShareAction?,
    onActionSelected: (WorkoutShareAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)) {
        Text(
            text = "Export",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)) {
            ActionButton(
                label = "Instagram",
                icon = Icons.Default.IosShare,
                action = WorkoutShareAction.InstagramStory,
                isGenerating = isGenerating,
                activeAction = activeAction,
                modifier = Modifier.weight(1f),
                onActionSelected = onActionSelected
            )
            ActionButton(
                label = "WhatsApp",
                icon = Icons.Default.Send,
                action = WorkoutShareAction.WhatsApp,
                isGenerating = isGenerating,
                activeAction = activeAction,
                modifier = Modifier.weight(1f),
                onActionSelected = onActionSelected
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)) {
            ActionButton(
                label = "Save",
                icon = Icons.Default.Download,
                action = WorkoutShareAction.Save,
                isGenerating = isGenerating,
                activeAction = activeAction,
                modifier = Modifier.weight(1f),
                onActionSelected = onActionSelected
            )
            ActionButton(
                label = "Share",
                icon = Icons.Default.MoreHoriz,
                action = WorkoutShareAction.NativeShare,
                isGenerating = isGenerating,
                activeAction = activeAction,
                modifier = Modifier.weight(1f),
                onActionSelected = onActionSelected
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    action: WorkoutShareAction,
    isGenerating: Boolean,
    activeAction: WorkoutShareAction?,
    modifier: Modifier = Modifier,
    onActionSelected: (WorkoutShareAction) -> Unit
) {
    val showSpinner = isGenerating && activeAction == action
    Button(
        onClick = { onActionSelected(action) },
        enabled = !isGenerating,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
