package com.example.liftrix.ui.anomaly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.AnomalyDetectionSettings
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.components.cards.ElevatedLiftrixCard
import com.example.liftrix.ui.progress.components.ProgressToggleItem

/**
 * Settings screen for configuring anomaly detection sensitivity and preferences
 * 
 * Features:
 * - Toggle anomaly detection on/off
 * - Adjust sensitivity thresholds for different metrics
 * - Configure minimum values for detection
 * - Enable/disable learning from user feedback
 * - Reset settings to default values
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnomalySettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnomalySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Anomaly detection settings screen"
            }
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                LoadingState(
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is UiState.Error -> {
                ErrorState(
                    error = state.error,
                    onRetry = { viewModel.loadSettings() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is UiState.Success -> {
                AnomalySettingsContent(
                    settings = state.data,
                    onUpdateSettings = { updatedSettings ->
                        viewModel.updateSettings(updatedSettings)
                    },
                    onResetToDefaults = {
                        viewModel.resetToDefaults()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            is UiState.Empty -> {
                // This shouldn't happen for settings, but handle gracefully
                EmptyState(
                    onRetry = { viewModel.loadSettings() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Fallback case - should never reach here
                EmptyState(
                    onRetry = { viewModel.loadSettings() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Main content for anomaly settings
 */
@Composable
private fun AnomalySettingsContent(
    settings: AnomalyDetectionSettings,
    onUpdateSettings: (AnomalyDetectionSettings) -> Unit,
    onResetToDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Detection Status Card
        item {
            DetectionStatusCard(
                isEnabled = true, // This would come from settings when implemented
                onToggle = { enabled ->
                    // Update global detection setting
                }
            )
        }
        
        // Sensitivity Settings
        item {
            Text(
                text = "Detection Sensitivity",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            SensitivitySettingsCard(
                settings = settings,
                onUpdateSettings = onUpdateSettings
            )
        }
        
        // Removed Minimum Thresholds and Learning Settings sections as per requirements
        
        // Reset Button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onResetToDefaults,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset to defaults",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Defaults")
            }
        }
    }
}

/**
 * Card showing detection status and global toggle
 */
@Composable
private fun DetectionStatusCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedLiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Anomaly Detection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEnabled) "Active - Monitoring your workouts" else "Disabled - No anomaly detection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
            
            if (isEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Detection info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "We'll help catch potential data entry errors during your workouts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Card for sensitivity threshold settings
 */
@Composable
private fun SensitivitySettingsCard(
    settings: AnomalyDetectionSettings,
    onUpdateSettings: (AnomalyDetectionSettings) -> Unit
) {
    ElevatedLiftrixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Detection Sensitivity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Adjust how sensitive the detection is to changes in your workout data",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Weight spike threshold
            ThresholdSlider(
                label = "Weight Increase Sensitivity",
                value = settings.weightSpikeThreshold,
                valueRange = 1.5f..5.0f,
                steps = 6,
                onValueChange = { newValue ->
                    onUpdateSettings(settings.copy(weightSpikeThreshold = newValue))
                },
                formatValue = { "${String.format("%.1f", it)}x increase" }
            )
            
            
            // Reps spike threshold
            ThresholdSlider(
                label = "Reps Increase Sensitivity",
                value = settings.repsSpikeThreshold,
                valueRange = 1.5f..3.0f,
                steps = 5,
                onValueChange = { newValue ->
                    onUpdateSettings(settings.copy(repsSpikeThreshold = newValue))
                },
                formatValue = { "${String.format("%.1f", it)}x increase" }
            )
            
        }
    }
}

// Removed MinimumThresholdsCard and LearningSettingsCard as per requirements

/**
 * Reusable threshold slider component
 */
@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    formatValue: (Float) -> String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

/**
 * Loading state component
 */
@Composable
private fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading settings...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Error state component
 */
@Composable
private fun ErrorState(
    error: LiftrixError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Failed to Load Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Empty state component (shouldn't happen for settings but handle gracefully)
 */
@Composable
private fun EmptyState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "No Settings Found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Unable to load anomaly detection settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
