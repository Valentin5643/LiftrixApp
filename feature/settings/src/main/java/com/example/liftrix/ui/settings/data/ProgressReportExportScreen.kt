package com.example.liftrix.ui.settings.data

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.export.ProgressReportDateRange
import com.example.liftrix.domain.model.export.ProgressReportIncludeOptions
import com.example.liftrix.domain.model.export.ProgressReportPrivacyOptions
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton
import com.example.liftrix.ui.components.actions.UnifiedWorkoutCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressReportExportScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    viewModel: ProgressReportExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.fileActionEvents.collect { event ->
            when (event) {
                is ProgressReportFileActionEvent.Open -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(event.uriString), event.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, "No PDF viewer available.", Toast.LENGTH_LONG).show()
                        }
                }
                is ProgressReportFileActionEvent.Share -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(event.uriString))
                        putExtra(Intent.EXTRA_SUBJECT, event.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Progress Report"))
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text("Liftrix Progress Report", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        TextButton(onClick = onNavigateBack) {
                            Text("Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                UnifiedWorkoutCard(
                    title = "Liftrix Progress Report",
                    subtitle = "Generate a local PDF from your training history",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RangeSelector(uiState, viewModel::onEvent)
                        if (uiState.selectedRange is ProgressReportDateRange.Custom) {
                            CustomRangeFields(uiState, viewModel::onEvent)
                        }
                    }
                }
            }

            item {
                UnifiedWorkoutCard(
                    title = "Sections",
                    subtitle = "Choose report content",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IncludeOptions(uiState.includeOptions, viewModel::onEvent)
                }
            }

            item {
                UnifiedWorkoutCard(
                    title = "Privacy",
                    subtitle = "Sensitive fields are hidden by default",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PrivacyOptions(uiState.privacyOptions, viewModel::onEvent)
                }
            }

            item {
                PrimaryActionButton(
                    text = if (uiState.isGenerating) "Generating..." else "Generate PDF",
                    onClick = { viewModel.onEvent(ProgressReportExportEvent.GeneratePdf) },
                    enabled = !uiState.isGenerating && !uiState.isLoadingUser,
                    leadingIcon = Icons.Default.PictureAsPdf,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.onEvent(ProgressReportExportEvent.DismissError) }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }

            val successMessage = uiState.successMessage
            val result = uiState.result
            if (successMessage != null && result != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = successMessage,
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${result.fileName} (${formatFileSize(result.fileSizeBytes)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryActionButton(
                                    text = "Open PDF",
                                    onClick = { viewModel.onEvent(ProgressReportExportEvent.OpenPdf) },
                                    leadingIcon = Icons.Default.FileDownload,
                                    modifier = Modifier.weight(1f)
                                )
                                SecondaryActionButton(
                                    text = "Share",
                                    onClick = { viewModel.onEvent(ProgressReportExportEvent.SharePdf) },
                                    leadingIcon = Icons.Default.Share,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = { viewModel.onEvent(ProgressReportExportEvent.SaveToDownloads) },
                                enabled = !uiState.isSavingToDownloads,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isSavingToDownloads) {
                                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                }
                                Text("Save to Downloads")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(
    state: ProgressReportExportState,
    onEvent: (ProgressReportExportEvent) -> Unit
) {
    val options = listOf(
        "Last 30 days" to ProgressReportDateRange.Last30Days,
        "Last 6 months" to ProgressReportDateRange.Last6Months,
        "All time" to ProgressReportDateRange.AllTime,
        "Custom range" to ProgressReportDateRange.Custom(
            LocalDate.now().minusDays(29),
            LocalDate.now()
        )
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.take(2).forEach { (label, range) ->
            FilterChip(
                selected = state.selectedRange::class == range::class,
                onClick = { onEvent(ProgressReportExportEvent.SelectRange(range)) },
                label = { Text(label) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.drop(2).forEach { (label, range) ->
            FilterChip(
                selected = state.selectedRange::class == range::class,
                onClick = { onEvent(ProgressReportExportEvent.SelectRange(range)) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun CustomRangeFields(
    state: ProgressReportExportState,
    onEvent: (ProgressReportExportEvent) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.customStartDateText,
            onValueChange = { onEvent(ProgressReportExportEvent.UpdateCustomStartDate(it)) },
            label = { Text("Start") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.customEndDateText,
            onValueChange = { onEvent(ProgressReportExportEvent.UpdateCustomEndDate(it)) },
            label = { Text("End") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IncludeOptions(
    includeOptions: ProgressReportIncludeOptions,
    onEvent: (ProgressReportExportEvent) -> Unit
) {
    ToggleRow("Workout summary", includeOptions.workoutSummary) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(workoutSummary = it)))
    }
    ToggleRow("Strength progress", includeOptions.strengthProgress) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(strengthProgress = it)))
    }
    ToggleRow("Volume analysis", includeOptions.volumeAnalysis) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(volumeAnalysis = it)))
    }
    ToggleRow("Personal records", includeOptions.personalRecords) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(personalRecords = it)))
    }
    ToggleRow("Consistency summary", includeOptions.consistencySummary) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(consistencySummary = it)))
    }
    ToggleRow("AI coach insights", includeOptions.aiCoachInsights) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(aiCoachInsights = it)))
    }
    ToggleRow("Detailed workout list", includeOptions.detailedWorkoutList) {
        onEvent(ProgressReportExportEvent.UpdateIncludeOptions(includeOptions.copy(detailedWorkoutList = it)))
    }
}

@Composable
private fun PrivacyOptions(
    privacyOptions: ProgressReportPrivacyOptions,
    onEvent: (ProgressReportExportEvent) -> Unit
) {
    ToggleRow("Hide bodyweight", privacyOptions.hideBodyweight) {
        onEvent(ProgressReportExportEvent.UpdatePrivacyOptions(privacyOptions.copy(hideBodyweight = it)))
    }
    ToggleRow("Hide personal notes", privacyOptions.hidePersonalNotes) {
        onEvent(ProgressReportExportEvent.UpdatePrivacyOptions(privacyOptions.copy(hidePersonalNotes = it)))
    }
    ToggleRow("Hide email / account info", privacyOptions.hideEmailAccountInfo) {
        onEvent(ProgressReportExportEvent.UpdatePrivacyOptions(privacyOptions.copy(hideEmailAccountInfo = it)))
    }
    AssistChip(
        onClick = { },
        label = { Text("Social data and notes are never included") }
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}
