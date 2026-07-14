package com.example.liftrix.ui.settings.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.usecase.export.ExportFormat
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.components.actions.UnifiedWorkoutCard
import com.example.liftrix.ui.components.actions.PrimaryActionButton
import com.example.liftrix.ui.components.actions.SecondaryActionButton

/**
 * Data portability screen for exporting workout data and showing import availability.
 * 
 * This screen provides a user-friendly interface for data portability features,
 * allowing users to export their workout data in various formats. Import remains
 * visible as an unavailable capability until persistence is implemented.
 * 
 * @param onNavigateBack Callback to navigate back to previous screen
 * @param modifier Modifier for styling the screen
 * @param viewModel DataPortabilityViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataPortabilityScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    viewModel: DataPortabilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Observe share export events
    LaunchedEffect(viewModel) {
        viewModel.shareExportEvent.collect { shareEvent ->
            try {
                val exportResult = shareEvent.exportResult
                val file = exportResult.file
                
                // Copy file to cache directory for sharing
                val shareFile = java.io.File(context.cacheDir, "exports/${file.name}")
                shareFile.parentFile?.mkdirs()
                file.copyTo(shareFile, overwrite = true)
                
                // Create file URI using FileProvider for sharing
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                
                // Create share intent
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    type = shareEvent.shareIntent.mimeType
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, shareEvent.shareIntent.fileName)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                // Launch share chooser
                context.startActivity(
                    android.content.Intent.createChooser(
                        shareIntent,
                        "Share Workout Export"
                    )
                )
            } catch (e: Exception) {
                // Show error toast or snackbar
                android.widget.Toast.makeText(
                    context,
                    "Failed to share export: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                title = { 
                    Text(
                        text = "Data Portability",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export Section
            item {
                UnifiedWorkoutCard(
                    title = "Export Data",
                    subtitle = "Download your workout history",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Export the workout records currently supported by this build as JSON or CSV.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryActionButton(
                                text = "Export as JSON",
                                onClick = { 
                                    viewModel.handleEvent(DataPortabilityEvent.SelectExportFormat(ExportFormat.JSON))
                                    viewModel.handleEvent(DataPortabilityEvent.StartExport)
                                },
                                leadingIcon = Icons.Default.FileDownload,
                                modifier = Modifier.weight(1f)
                            )
                            
                            SecondaryActionButton(
                                text = "Export as CSV",
                                onClick = { 
                                    viewModel.handleEvent(DataPortabilityEvent.SelectExportFormat(ExportFormat.CSV))
                                    viewModel.handleEvent(DataPortabilityEvent.StartExport)
                                },
                                leadingIcon = Icons.Default.FileDownload,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Import Section
            item {
                UnifiedWorkoutCard(
                    title = "Import Data",
                    subtitle = "Import is not available in this build",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Workout import is still being prepared and cannot be used in this build.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        PrimaryActionButton(
                            text = "Import unavailable",
                            onClick = {},
                            leadingIcon = Icons.Default.FileUpload,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Data Management Info
            item {
                UnifiedWorkoutCard(
                    title = "Data Management",
                    subtitle = "Information about your data",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Data retention",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Indefinite",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Backup frequency",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Daily",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Cloud sync",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Enabled",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Progress indicator if exporting/importing
            when (val exportState = uiState.exportState) {
                is UiState.Loading -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Exporting data...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Export failed",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Export failed. Please try again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                else -> {
                    // No special UI needed for success or empty states
                }
            }
        }
    }
}
