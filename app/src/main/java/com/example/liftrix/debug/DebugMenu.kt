package com.example.liftrix.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Debug menu composable for accessing debugging features
 * Only available in debug builds
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenu(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDebugReport by remember { mutableStateOf(false) }
    var debugReportContent by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🔧 Debug Menu",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.Info,
                            title = "Log Memory Usage",
                            description = "Force log current memory usage"
                        ) {
                            LiftrixDebugger.logMemoryUsage(force = true)
                        }
                    }
                    
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.Build,
                            title = "Validate Build",
                            description = "Run build configuration validation"
                        ) {
                            scope.launch {
                                val result = LiftrixDebugger.validateBuildConfiguration()
                                LiftrixDebugger.info("Build validation: ${if (result.isValid) "PASSED" else "FAILED"}")
                            }
                        }
                    }
                    
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.Check,
                            title = "Validate App State",
                            description = "Run comprehensive app state validation"
                        ) {
                            scope.launch {
                                val result = LiftrixDebugger.validateApplicationState()
                                LiftrixDebugger.info("App validation: ${if (result.isValid) "PASSED" else "FAILED"}")
                            }
                        }
                    }
                    
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.Delete,
                            title = "Clear Debug Events",
                            description = "Clear all stored debug events"
                        ) {
                            LiftrixDebugger.clearDebugEvents()
                        }
                    }
                    
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.Assignment,
                            title = "Generate Debug Report",
                            description = "Create comprehensive debug report"
                        ) {
                            scope.launch {
                                debugReportContent = LiftrixDebugger.exportDebugSession()
                                showDebugReport = true
                            }
                        }
                    }
                    
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.PlayArrow,
                            title = "Test Animation Tracking",
                            description = "Simulate animation performance tracking"
                        ) {
                            scope.launch {
                                val startTime = LiftrixDebugger.trackAnimationStart("debug_test_animation")
                                kotlinx.coroutines.delay(100) // Simulate animation
                                LiftrixDebugger.trackAnimationEnd("debug_test_animation", startTime)
                            }
                        }
                    }
                    
                    item {
                        DebugMenuButton(
                            icon = Icons.Default.Refresh,
                            title = "Test Composition Tracking",
                            description = "Simulate composition performance tracking"
                        ) {
                            LiftrixDebugger.trackCompositionPerformance("DebugMenuComposable", 5)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
    
    // Debug report dialog
    if (showDebugReport) {
        DebugReportDialog(
            content = debugReportContent,
            onDismiss = { showDebugReport = false }
        )
    }
}

@Composable
private fun DebugMenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DebugReportDialog(
    content: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 Debug Report",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        item {
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Debug floating action button for easy access to debug menu
 */
@Composable
fun DebugFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (com.example.liftrix.BuildConfig.DEBUG) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Debug Menu"
            )
        }
    }
}