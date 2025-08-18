package com.example.liftrix.ui.settings.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.usecase.export.ExportFormat
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton

/**
 * Data portability screen for importing and exporting workout data.
 * 
 * This screen provides a user-friendly interface for data portability features,
 * allowing users to export their workout data in various formats and import
 * data from other fitness applications.
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
    viewModel: DataPortabilityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        modifier = modifier,
        topBar = {
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
                    subtitle = "Download your workout data in various formats",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Export your workout history, routines, and progress data to use in other applications or for backup purposes.",
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
                    subtitle = "Import workout data from other applications",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Import workout data from JSON or CSV files. Your existing data will be preserved.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        PrimaryActionButton(
                            text = "Select File to Import",
                            onClick = { 
                                // File selection would be handled through platform-specific APIs
                                // For now, just show a message
                            },
                            leadingIcon = Icons.Default.FileUpload,
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
                                    contentDescription = null,
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