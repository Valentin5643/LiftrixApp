package com.example.liftrix.ui.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Progress dashboard screen with comprehensive analytics and charts.
 * 
 * This screen displays workout progress analytics including volume charts,
 * duration tracking, frequency heatmaps, and summary statistics. It now uses
 * the new ProgressDashboardScreen with modern clean architecture.
 * 
 * Features:
 * - Modern clean architecture with 6 focused ViewModels + coordinator
 * - Enhanced Material3 design with professional styling
 * - Comprehensive analytics and widget management
 * - Improved performance and maintainability
 * 
 * @param modifier Modifier for styling the screen
 */
@Composable
fun ProgressScreen(
    modifier: Modifier = Modifier
) {
    // Delegate to the new ProgressDashboardScreen with modern architecture
    ProgressDashboardScreen(modifier = modifier)
}