package com.example.liftrix.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.progress.components.ProgressSummaryCards
import com.example.liftrix.ui.progress.components.TimePeriodSelector
import com.example.liftrix.ui.progress.components.VolumeCalendarWidget
import com.example.liftrix.ui.progress.components.WorkoutDurationChart
import com.example.liftrix.ui.progress.components.WorkoutFrequencyHeatmap
import com.example.liftrix.ui.progress.components.WorkoutVolumeChart
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.core.formatting.WeightFormatter
import com.example.liftrix.ui.progress.components.WidgetContainer
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.progress.components.ResponsiveDashboardLayout
import com.example.liftrix.ui.progress.components.DashboardLayoutMode
import com.example.liftrix.ui.common.rememberWindowSizeClass
import com.example.liftrix.ui.progress.components.CaloriesBurnedCard
import com.example.liftrix.ui.progress.components.DailyCaloriesCard
import com.example.liftrix.ui.progress.components.WeeklyCalorieTrendCard
import com.example.liftrix.ui.export.ExportBottomSheet
import com.example.liftrix.ui.export.ExportConfiguration
import com.example.liftrix.service.export.ExportProgress
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.MetricWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.CalorieSummary
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPriority
import com.example.liftrix.domain.usecase.analytics.CalorieAnalyticsUseCase
import com.example.liftrix.ui.common.validation.ViewModelValidator
import com.example.liftrix.core.extensions.collectAsOptimizedState
import timber.log.Timber

/**
 * Maps domain DashboardLayoutMode to UI DashboardLayoutMode
 */
private fun mapDomainLayoutModeToUI(
    domainMode: com.example.liftrix.domain.model.analytics.DashboardLayoutMode?
): DashboardLayoutMode? {
    return when (domainMode) {
        com.example.liftrix.domain.model.analytics.DashboardLayoutMode.GRID -> DashboardLayoutMode.GRID
        com.example.liftrix.domain.model.analytics.DashboardLayoutMode.SECTIONS -> DashboardLayoutMode.SECTIONS
        com.example.liftrix.domain.model.analytics.DashboardLayoutMode.LIST -> DashboardLayoutMode.LIST
        com.example.liftrix.domain.model.analytics.DashboardLayoutMode.CUSTOM -> DashboardLayoutMode.CUSTOM
        null -> null
    }
}

/**
 * Modern progress dashboard screen with data dashboard styling.
 * 
 * Features enhanced Material 3 design with LiftrixCard system, 8pt grid spacing,
 * and professional athletic interface. Displays comprehensive analytics including
 * volume charts, duration tracking, frequency heatmaps, and summary statistics.
 * 
 * Key improvements:
 * - Modern card-based layout with 24dp border radius
 * - 8pt grid system for consistent spacing
 * - Enhanced visual hierarchy with brand colors
 * - Responsive breakpoints for different screen sizes
 * - Professional data dashboard styling
 * - Multiple specialized ViewModels for better separation of concerns
 * 
 * @param modifier Modifier for styling the screen
 * @param chartsViewModel ViewModel for chart data management
 * @param widgetViewModel ViewModel for widget management
 * @param preferencesViewModel ViewModel for user preferences
 * @param summaryViewModel ViewModel for summary statistics
 * @param calorieViewModel ViewModel for calorie tracking
 * @param featuresViewModel ViewModel for feature configuration
 * @param coordinator Coordinator for inter-ViewModel communication
 */
@Composable
fun ProgressDashboardScreen(
    modifier: Modifier = Modifier,
    chartsViewModel: ProgressChartsViewModel = hiltViewModel(),
    widgetViewModel: AnalyticsWidgetViewModel = hiltViewModel(),
    preferencesViewModel: UserPreferencesViewModel = hiltViewModel(),
    summaryViewModel: ProgressSummaryViewModel = hiltViewModel(),
    calorieViewModel: CalorieTrackingViewModel = hiltViewModel(),
    featuresViewModel: FeatureConfigurationViewModel = hiltViewModel(),
    coordinator: ProgressDashboardCoordinator = hiltViewModel()
) {
    // Validate ViewModels for debugging (only in debug builds)
    LaunchedEffect(Unit) {
        val validationResult = ViewModelValidator.validateViewModels(
            "ProgressChartsViewModel" to chartsViewModel.uiState,
            "AnalyticsWidgetViewModel" to widgetViewModel.uiState,
            "UserPreferencesViewModel" to preferencesViewModel.uiState,
            "ProgressSummaryViewModel" to summaryViewModel.uiState,
            "CalorieTrackingViewModel" to calorieViewModel.uiState,
            "FeatureConfigurationViewModel" to featuresViewModel.uiState,
            "ProgressDashboardCoordinator" to coordinator.uiState
        )
        validationResult.logResult()
    }

    // Optimized StateFlow collection with performance monitoring
    val chartsState by chartsViewModel.uiState.collectAsOptimizedState()
    val widgetState by widgetViewModel.uiState.collectAsOptimizedState() 
    val preferencesState by preferencesViewModel.uiState.collectAsOptimizedState()
    val summaryState by summaryViewModel.uiState.collectAsOptimizedState()
    val calorieState by calorieViewModel.uiState.collectAsOptimizedState()
    val featuresState by featuresViewModel.uiState.collectAsOptimizedState()
    val coordinatorState by coordinator.uiState.collectAsOptimizedState()
    
    
    // Add timeout handling for stuck loading states
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(20000) // 20 second timeout
        if (chartsState is UiState.Loading || widgetState is UiState.Loading || calorieState is UiState.Loading) {
            Timber.w("Analytics modules timeout - forcing refresh")
            coordinator.handleEvent(CoordinatorEvent.RefreshAllData)
        }
    }

    // Connect Coordinator events to ViewModels
    LaunchedEffect(coordinator) {
        coordinator.coordinatorEvents.collect { event ->
            // Forward coordinator events to all ViewModels that handle them
            chartsViewModel.handleCoordinatorEvent(event)
            widgetViewModel.handleCoordinatorEvent(event)
            preferencesViewModel.handleCoordinatorEvent(event)
            summaryViewModel.handleCoordinatorEvent(event)
            calorieViewModel.handleCoordinatorEvent(event)
            featuresViewModel.handleCoordinatorEvent(event)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Extract data from UiState wrappers, using default states for loading/error cases
        ProgressDashboardContent(
            chartsState = (chartsState as? UiState.Success)?.data ?: ProgressChartsState(),
            widgetState = (widgetState as? UiState.Success)?.data ?: AnalyticsWidgetState(),
            preferencesState = (preferencesState as? UiState.Success)?.data ?: UserPreferencesState(),
            summaryState = (summaryState as? UiState.Success)?.data ?: ProgressSummaryState(),
            calorieState = (calorieState as? UiState.Success)?.data ?: CalorieTrackingState(),
            featuresState = (featuresState as? UiState.Success)?.data ?: FeatureConfigurationState(),
            coordinatorState = (coordinatorState as? UiState.Success)?.data ?: CoordinatorState(),
            onChartsEvent = chartsViewModel::handleEvent,
            onWidgetEvent = widgetViewModel::handleEvent,
            onPreferencesEvent = preferencesViewModel::handleEvent,
            onSummaryEvent = summaryViewModel::handleEvent,
            onCalorieEvent = calorieViewModel::handleEvent,
            onFeaturesEvent = featuresViewModel::handleEvent,
            onCoordinatorEvent = coordinator::handleEvent,
            // Pass raw UiState for debugging
            rawChartsState = chartsState ?: UiState.Loading,
            rawWidgetState = widgetState ?: UiState.Loading,
            rawPreferencesState = preferencesState ?: UiState.Loading,
            rawSummaryState = summaryState ?: UiState.Loading,
            rawCalorieState = calorieState ?: UiState.Loading,
            rawFeaturesState = featuresState ?: UiState.Loading,
            rawCoordinatorState = coordinatorState ?: UiState.Loading
        )
    }
}

/**
 * Main dashboard content with modern card-based layout and 8pt grid spacing
 */
@Composable
private fun ProgressDashboardContent(
    chartsState: ProgressChartsState,
    widgetState: AnalyticsWidgetState,
    preferencesState: UserPreferencesState,
    summaryState: ProgressSummaryState,
    calorieState: CalorieTrackingState,
    featuresState: FeatureConfigurationState,
    coordinatorState: CoordinatorState,
    onChartsEvent: (ProgressChartsEvent) -> Unit,
    onWidgetEvent: (AnalyticsWidgetEvent) -> Unit,
    onPreferencesEvent: (UserPreferencesEvent) -> Unit,
    onSummaryEvent: (ProgressSummaryEvent) -> Unit,
    onCalorieEvent: (CalorieTrackingEvent) -> Unit,
    onFeaturesEvent: (FeatureConfigurationEvent) -> Unit,
    onCoordinatorEvent: (CoordinatorEvent) -> Unit,
    // Raw UiState for debugging
    rawChartsState: UiState<ProgressChartsState>,
    rawWidgetState: UiState<AnalyticsWidgetState>,
    rawPreferencesState: UiState<UserPreferencesState>,
    rawSummaryState: UiState<ProgressSummaryState>,
    rawCalorieState: UiState<CalorieTrackingState>,
    rawFeaturesState: UiState<FeatureConfigurationState>,
    rawCoordinatorState: UiState<CoordinatorState>
) {
    // Extract weight unit from coordinator preferences with fallback to system default
    val weightUnit = remember(coordinatorState.coordinatorPreferences) {
        coordinatorState.coordinatorPreferences["weightUnit"] as? WeightUnit 
            ?: WeightUnit.getSystemDefault()
    }
    val weightFormatter = remember { WeightFormatter() }
    
    // Debug panel state
    var showDebugPanel by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GridSystem.screenPadding),
        verticalArrangement = Arrangement.spacedBy(GridSystem.gapMedium)
    ) {
        item {
            Spacer(modifier = Modifier.height(GridSystem.spacing2))
        }

        // Modern screen header with enhanced typography and export functionality
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = GridSystem.spacing2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Debug panel toggle button
                    IconButton(
                        onClick = { showDebugPanel = !showDebugPanel }
                    ) {
                        Icon(
                            imageVector = if (showDebugPanel) Icons.Default.VisibilityOff else Icons.Default.BugReport,
                            contentDescription = if (showDebugPanel) "Hide Debug Panel" else "Show Debug Panel",
                            tint = if (showDebugPanel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Feature flag controlled export button
                    if (featuresState.exportEnabled) {
                        var showExportBottomSheet by remember { mutableStateOf(false) }
                        
                        IconButton(
                            onClick = { showExportBottomSheet = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export data",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Export Bottom Sheet
                        ExportBottomSheet(
                            isVisible = showExportBottomSheet,
                            onDismiss = { showExportBottomSheet = false },
                            onStartExport = { configuration ->
                                // Handle export configuration
                                when (configuration.type) {
                                    com.example.liftrix.ui.export.ExportType.ANALYTICS -> {
                                        if (configuration.analyticsFormat == com.example.liftrix.service.export.ExportFormat.PDF) {
                                            onCoordinatorEvent(CoordinatorEvent.ExportToPdf)
                                        } else {
                                            onCoordinatorEvent(CoordinatorEvent.ExportToCsv)
                                        }
                                    }
                                    com.example.liftrix.ui.export.ExportType.RAW_DATA -> {
                                        // Handle raw data export through coordinator
                                        onCoordinatorEvent(CoordinatorEvent.ExportRawData(configuration))
                                    }
                                }
                                showExportBottomSheet = false
                            },
                            exportProgress = coordinatorState.exportProgress,
                            onCancelExport = { onCoordinatorEvent(CoordinatorEvent.CancelExport) }
                        )
                    }
                }
            }
        }

        // Debug Panel - shows all ViewModel states and debugging info
        if (showDebugPanel) {
            item {
                DebugPanel(
                    rawChartsState = rawChartsState,
                    rawWidgetState = rawWidgetState,
                    rawPreferencesState = rawPreferencesState,
                    rawSummaryState = rawSummaryState,
                    rawCalorieState = rawCalorieState,
                    rawFeaturesState = rawFeaturesState,
                    rawCoordinatorState = rawCoordinatorState,
                    calorieState = calorieState,
                    coordinatorState = coordinatorState,
                    onRefresh = { onCoordinatorEvent(CoordinatorEvent.RefreshAllData) },
                    onCalorieRefresh = { onCalorieEvent(CalorieTrackingEvent.RefreshAllData) },
                    onSummaryRefresh = { onSummaryEvent(ProgressSummaryEvent.RefreshSummary) },
                    onWidgetMigrate = { 
                        // Force show all 23 widgets
                        onWidgetEvent(AnalyticsWidgetEvent.ForceAllWidgets())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Time period selector with modern styling
        item {
            TimePeriodSelector(
                selectedPeriod = chartsState.currentTimeRange,
                onPeriodSelected = { onChartsEvent(ProgressChartsEvent.TimePeriodChanged(it)) }
            )
        }

        // Analytics onboarding (feature flag controlled)
        if (featuresState.showOnboarding && featuresState.analyticsEnabled) {
            item {
                AnalyticsOnboardingCard(
                    onDismiss = { onFeaturesEvent(FeatureConfigurationEvent.DismissOnboarding) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Enhanced summary stats with modern card design
        item {
            SummarySection(
                summaryState = summaryState,
                onEvent = onSummaryEvent,
                weightUnit = weightUnit,
                weightFormatter = weightFormatter,
                modifier = Modifier.fillMaxWidth()
            )
        }


        // Widget-based analytics dashboard with responsive layout
        if (featuresState.analyticsEnabled) {
            item {
                ResponsiveWidgetsSection(
                    widgetState = widgetState,
                    onEvent = onWidgetEvent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Charts section
        item {
            ChartsSection(
                chartsState = chartsState,
                onEvent = onChartsEvent,
                weightUnit = weightUnit,
                weightFormatter = weightFormatter,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(GridSystem.spacing3))
        }
    }
}

/**
 * Reusable container for chart sections
 */
@Composable
private fun ChartContainer(
    title: String,
    icon: ImageVector,
    isLoading: Boolean,
    hasData: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chart header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh $title",
                        tint = if (isLoading) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        LoadingChartPlaceholder()
                    }
                    hasData -> {
                        content()
                    }
                    else -> {
                        NoDataPlaceholder()
                    }
                }
            }
        }
    }
}

/**
 * Loading state for charts
 */
@Composable
private fun LoadingChartPlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Loading chart data...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Placeholder when no data is available for a chart
 * Note: This should no longer be used as charts always show with zero values
 */
@Composable
private fun NoDataPlaceholder() {
    // This composable is deprecated - charts should always show with zero values
    // Keeping for backwards compatibility but should not be displayed
    Box(modifier = Modifier.fillMaxSize())
}



/**
 * Volume chart placeholder
 */
@Composable
private fun VolumeChartPlaceholder(dataPoints: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Volume Chart",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$dataPoints data points available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Chart visualization coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Duration chart placeholder
 */
@Composable
private fun DurationChartPlaceholder(dataPoints: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Duration Chart",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$dataPoints data points available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Chart visualization coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Frequency heatmap placeholder
 */
@Composable
private fun FrequencyHeatmapPlaceholder(dataPoints: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Frequency Heatmap",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$dataPoints data points available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Heatmap visualization coming soon",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Loading state content with progress indicator
 */
@Composable
private fun LoadingStateContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Modern error state content with enhanced visual design
 */
@Composable
private fun ErrorStateContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LiftrixCard(
            modifier = Modifier.padding(GridSystem.screenPadding),
            contentDescription = "Error loading progress data"
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(GridSystem.iconXLarge)
                )
                Text(
                    text = "Error Loading Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
                ) {
                    PrimaryActionButton(
                        text = "Retry",
                        onClick = onRetry
                    )
                    SecondaryActionButton(
                        text = "Dismiss",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

// EmptyStateContent removed - charts now always show with zero values


/**
 * Charts section composable
 */
@Composable
private fun ChartsSection(
    chartsState: ProgressChartsState,
    onEvent: (ProgressChartsEvent) -> Unit,
    weightUnit: WeightUnit,
    weightFormatter: WeightFormatter,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
    ) {
        // Volume chart - always show chart with data or zero values
        WorkoutVolumeChart(
            data = (chartsState.volumeChart as? AsyncData.Success)?.data ?: emptyList(),
            isLoading = chartsState.volumeChart is AsyncData.Loading,
            weightUnit = weightUnit,
            weightFormatter = weightFormatter,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Duration chart - always show chart with data or zero values
        WorkoutDurationChart(
            data = (chartsState.durationChart as? AsyncData.Success)?.data ?: emptyList(),
            isLoading = chartsState.durationChart is AsyncData.Loading,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Frequency chart - always show chart with data or zero values
        WorkoutFrequencyHeatmap(
            data = (chartsState.frequencyChart as? AsyncData.Success)?.data ?: emptyList(),
            isLoading = chartsState.frequencyChart is AsyncData.Loading,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Note: Charts now always show with zero values instead of empty states
    }
}

/**
 * Widgets section composable
 */
@Composable
private fun WidgetsSection(
    widgetState: AnalyticsWidgetState,
    onEvent: (AnalyticsWidgetEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show widgets if we have configuration and widgets available
    if (widgetState.activeWidgets.isNotEmpty() && widgetState.configuration != null) {
        WidgetContainer(
            widgets = widgetState.activeWidgets,
            configuration = widgetState.configuration,
            layoutMode = WidgetLayoutMode.SECTIONS,
            onWidgetClick = { widget ->
                onEvent(AnalyticsWidgetEvent.WidgetClicked(widget))
            },
            widgetDataProvider = { widget ->
                widgetState.widgetDataMap[widget] ?: createDefaultWidgetData(widget)
            },
            isLoading = widgetState.isLoading,
            enableCollapsibleSections = true,
            modifier = modifier
        )
    }
    // No else clause - just don't show widgets section if no data
}

/**
 * Responsive widgets section with adaptive layout
 */
@Composable
private fun ResponsiveWidgetsSection(
    widgetState: AnalyticsWidgetState,
    onEvent: (AnalyticsWidgetEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = rememberWindowSizeClass()
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Widget customization button (only for Intermediate/Advanced users)
        val userLevel = widgetState.preferences?.userLevel ?: UserLevel.BEGINNER
        if (userLevel != UserLevel.BEGINNER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val configurableCount = widgetState.activeWidgets.count { 
                        it.priority != WidgetPriority.FIXED_BEGINNER 
                    }
                    Text(
                        text = "$configurableCount configurable widgets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    IconButton(
                        onClick = { onEvent(AnalyticsWidgetEvent.NavigateToDashboardCustomization) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Customize dashboard",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Debug widget visibility conditions
        Timber.d("=== UI DEBUG: activeWidgets.size = ${widgetState.activeWidgets.size}")
        Timber.d("=== UI DEBUG: configuration = ${widgetState.configuration?.javaClass?.simpleName ?: "null"}")
        Timber.d("=== UI DEBUG: activeWidgets = ${widgetState.activeWidgets.map { it.displayName }}")
        
        // Only show widgets if we have configuration and widgets available
        if (widgetState.activeWidgets.isNotEmpty() && widgetState.configuration != null) {
            ResponsiveDashboardLayout(
                widgets = widgetState.activeWidgets,
                configuration = widgetState.configuration,
                layoutMode = run {
                    val userPreference = mapDomainLayoutModeToUI(widgetState.preferences?.dashboardLayout)
                    Timber.d("🔍 DEBUG LAYOUT: preferences=${widgetState.preferences}")
                    Timber.d("🔍 DEBUG LAYOUT: dashboardLayout=${widgetState.preferences?.dashboardLayout}")
                    Timber.d("🔍 DEBUG LAYOUT: userPreference=$userPreference")
                    Timber.d("🔍 DEBUG LAYOUT: screenWidth=${windowSizeClass.widthDp.value.toInt()}")
                    Timber.d("🔍 DEBUG LAYOUT: widgetCount=${widgetState.activeWidgets.size}")
                    
                    val result = DashboardLayoutMode.getOptimalMode(
                        screenWidthDp = windowSizeClass.widthDp.value.toInt(),
                        widgetCount = widgetState.activeWidgets.size,
                        userPreference = userPreference
                    )
                    
                    Timber.d("🔍 DEBUG LAYOUT: FINAL RESULT=$result")
                    result
                },
                onWidgetClick = { widget ->
                    onEvent(AnalyticsWidgetEvent.WidgetClicked(widget))
                },
                onWidgetReorder = { from, to ->
                    onEvent(AnalyticsWidgetEvent.WidgetReordered(from, to))
                },
                widgetDataProvider = { widget ->
                    widgetState.widgetDataMap[widget] ?: createDefaultWidgetData(widget)
                },
                isLoading = widgetState.isLoading,
                enableDragAndDrop = mapDomainLayoutModeToUI(widgetState.preferences?.dashboardLayout) == DashboardLayoutMode.CUSTOM || windowSizeClass.supportsDragAndDrop,
                windowSizeClass = windowSizeClass
            )
        } else {
            // Empty state with customization prompt
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dashboard,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "No Active Widgets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "Customize your dashboard to add analytics widgets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    PrimaryActionButton(
                        text = "Customize Dashboard",
                        onClick = { onEvent(AnalyticsWidgetEvent.NavigateToDashboardCustomization) },
                        modifier = Modifier.padding(top = 8.dp),
                        leadingIcon = Icons.Default.Add
                    )
                }
            }
        }
    }
}

/**
 * Summary section composable
 */
@Composable
private fun SummarySection(
    summaryState: ProgressSummaryState,
    onEvent: (ProgressSummaryEvent) -> Unit,
    weightUnit: WeightUnit,
    weightFormatter: WeightFormatter,
    modifier: Modifier = Modifier
) {
    when (val summary = summaryState.summaryData) {
        is AsyncData.Success -> {
            ProgressSummaryCards(
                summaryData = summary.data,
                isLoading = summaryState.isRefreshing,
                weightUnit = weightUnit,
                weightFormatter = weightFormatter
            )
        }
        is AsyncData.Loading -> {
            LoadingStateContent()
        }
        is AsyncData.Failure -> {
            ErrorStateContent(
                error = summary.error.message,
                onRetry = { onEvent(ProgressSummaryEvent.RefreshSummary) },
                onDismiss = { onEvent(ProgressSummaryEvent.ClearError) }
            )
        }
        is AsyncData.NotAsked -> {
            // Summary section is optional - don't show anything if not asked
            Box(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Calorie section composable
 */
@Composable
private fun CalorieSection(
    calorieState: CalorieTrackingState,
    onEvent: (CalorieTrackingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calorie Analytics",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing1)
            ) {
                SecondaryActionButton(
                    text = "Goals",
                    onClick = { onEvent(CalorieTrackingEvent.NavigateToCalorieGoalSettings) }
                )
                
                PrimaryActionButton(
                    text = "View All",
                    onClick = { onEvent(CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics) }
                )
            }
        }
        
        if (calorieState.isAnyDataLoading()) {
            CalorieAnalyticsLoadingState()
        } else {
            if (calorieState.calorieSummary is AsyncData.Success) {
                val summaryData = calorieState.calorieSummary.data
                    val domainSummary = com.example.liftrix.domain.model.analytics.CalorieSummary(
                        averageDailyCalories = summaryData.averageDailyCalories,
                        totalCaloriesThisWeek = summaryData.currentWeekCalories,
                        goalProgress = if (summaryData.previousWeekCalories > 0) summaryData.currentWeekCalories.toFloat() / summaryData.previousWeekCalories.toFloat() else 0f,
                        currentStreak = summaryData.totalWorkouts,
                        highestDayCalories = summaryData.highestDailyCalories
                    )
                    
                    // Daily calories card
                    DailyCaloriesCard(
                        caloriesInToday = domainSummary.averageDailyCalories,
                        dailyGoal = 400,
                        workoutCount = 1,
                        trend = calculateCalorieTrend(domainSummary),
                        isLoading = calorieState.calorieSummary is AsyncData.Loading,
                        onClick = { onEvent(CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Summary cards row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
                    ) {
                        CaloriesBurnedCard(
                            caloriesBurned = domainSummary.averageDailyCalories,
                            subtitle = "Daily Average",
                            trend = calculateCalorieTrend(domainSummary),
                            isLoading = calorieState.calorieSummary is AsyncData.Loading,
                            onClick = { onEvent(CalorieTrackingEvent.NavigateToCalorieHistory) },
                            modifier = Modifier.weight(1f)
                        )
                        
                        WeeklyCalorieTrendCard(
                            weeklyCalories = getWeeklyCaloriesList(domainSummary),
                            averageCalories = domainSummary.averageDailyCalories * 7,
                            trend = calculateCalorieTrend(domainSummary),
                            trendPercentage = calculateWeeklyTrendPercentage(domainSummary),
                            isLoading = calorieState.calorieSummary is AsyncData.Loading,
                            onClick = { onEvent(CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics) },
                            modifier = Modifier.weight(1f)
                        )
                    }
            } else {
                CalorieAnalyticsEmptyState(
                    onRefresh = { onEvent(CalorieTrackingEvent.RefreshCalories) }
                )
            }
        }
    }
}

/**
 * Helper function to create default widget data
 */
private fun createDefaultWidgetData(widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget): WidgetData {
    return MetricWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = "0",
        secondaryValue = "Start working out to see data",
        unit = "",
        trend = com.example.liftrix.domain.model.analytics.TrendDirection.STABLE
    )
}

// Removed default widget helper functions - widgets section is optional



/**
 * Calorie insights summary card with key metrics
 */
@Composable
private fun CalorieInsightsSummary(
    calorieSummary: CalorieSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(GridSystem.spacing3),
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
        ) {
            Text(
                text = "Weekly Summary",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InsightMetric(
                    label = "This Week",
                    value = "${calorieSummary.totalCaloriesThisWeek} cal",
                    icon = Icons.Default.Timeline
                )
                
                InsightMetric(
                    label = "Highest Day",
                    value = "${calorieSummary.highestDayCalories} cal",
                    icon = Icons.Default.ShowChart
                )
                
                InsightMetric(
                    label = "Streak",
                    value = "${calorieSummary.currentStreak} days",
                    icon = Icons.Default.BarChart
                )
            }
        }
    }
}

/**
 * Individual insight metric component
 */
@Composable
private fun InsightMetric(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Loading state for calorie analytics
 */
@Composable
private fun CalorieAnalyticsLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Loading calorie analytics...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty state for calorie analytics
 */
@Composable
private fun CalorieAnalyticsEmptyState(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2)
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        
        Text(
            text = "Calorie Analytics",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = "Your calorie data will appear here as you complete workouts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        PrimaryActionButton(
            text = "Refresh Data",
            onClick = onRefresh,
            leadingIcon = Icons.Default.Refresh
        )
    }
}

/**
 * Helper functions for calorie analytics calculations
 */
private fun calculateCalorieTrend(calorieSummary: CalorieSummary): TrendDirection {
    return when {
        calorieSummary.goalProgress >= 1.0f -> TrendDirection.UP
        calorieSummary.goalProgress >= 0.7f -> TrendDirection.STABLE
        else -> TrendDirection.DOWN
    }
}

private fun getWeeklyCaloriesList(calorieSummary: CalorieSummary): List<Int> {
    // Simplified weekly data - in production this should return actual weekly data
    return listOf(
        calorieSummary.totalCaloriesThisWeek,
        (calorieSummary.totalCaloriesThisWeek * 0.9).toInt(),
        (calorieSummary.totalCaloriesThisWeek * 1.1).toInt(),
        calorieSummary.totalCaloriesThisWeek
    )
}

private fun calculateWeeklyTrendPercentage(calorieSummary: CalorieSummary): Float {
    // Simplified trend calculation
    return if (calorieSummary.goalProgress > 0.8f) 12.5f else -5.2f
}

/**
 * Analytics onboarding card component
 */
@Composable
private fun AnalyticsOnboardingCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🎉 New Analytics Features!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Discover detailed insights about your workouts with our new analytics dashboard. Track your progress, volume trends, and consistency patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SecondaryActionButton(
                    text = "Got it!",
                    onClick = onDismiss
                )
            }
        }
    }
}

/**
 * Debug Panel - Visual debugging for Progress Dashboard
 */
@Composable
private fun DebugPanel(
    rawChartsState: UiState<ProgressChartsState>,
    rawWidgetState: UiState<AnalyticsWidgetState>,
    rawPreferencesState: UiState<UserPreferencesState>,
    rawSummaryState: UiState<ProgressSummaryState>,
    rawCalorieState: UiState<CalorieTrackingState>,
    rawFeaturesState: UiState<FeatureConfigurationState>,
    rawCoordinatorState: UiState<CoordinatorState>,
    calorieState: CalorieTrackingState,
    coordinatorState: CoordinatorState,
    onRefresh: () -> Unit,
    onCalorieRefresh: () -> Unit,
    onSummaryRefresh: () -> Unit,
    onWidgetMigrate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Debug Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🐛 DEBUG PANEL",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TertiaryActionButton(
                        text = "Refresh All",
                        onClick = onRefresh,
                        modifier = Modifier.height(32.dp)
                    )
                    
                    TertiaryActionButton(
                        text = "Refresh Calories",
                        onClick = onCalorieRefresh,
                        modifier = Modifier.height(32.dp)
                    )
                    
                    TertiaryActionButton(
                        text = "Refresh Summary",
                        onClick = { onSummaryRefresh() },
                        modifier = Modifier.height(32.dp)
                    )
                    
                    TertiaryActionButton(
                        text = "Fix Widgets",
                        onClick = onWidgetMigrate,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Volume Chart Debug Section (PRIMARY ISSUE)
            DebugSection(
                title = "🔥 VOLUME CHART DEBUG (PRIMARY ISSUE)",
                content = {
                    DebugStateRow("Charts State", rawChartsState)
                    
                    if (rawChartsState is UiState.Success) {
                        val chartsState = rawChartsState.data
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Volume chart specific debugging
                        when (val volumeChart = chartsState.volumeChart) {
                            is AsyncData.Success -> {
                                val volumeData = volumeChart.data
                                DebugInfoRow("📊 Volume Data Points", volumeData.size.toString())
                                
                                if (volumeData.isNotEmpty()) {
                                    val totalVolume = volumeData.sumOf { it.totalVolume.toDouble() }.toFloat()
                                    val maxVolume = volumeData.maxOfOrNull { it.totalVolume } ?: 0f
                                    val nonZeroPoints = volumeData.count { it.totalVolume > 0f }
                                    
                                    DebugInfoRow("📈 Total Volume", "${totalVolume}kg", isError = totalVolume == 0f)
                                    DebugInfoRow("📈 Max Volume", "${maxVolume}kg", isError = maxVolume == 0f)
                                    DebugInfoRow("📈 Non-Zero Points", "$nonZeroPoints/${volumeData.size}", 
                                        isError = nonZeroPoints == 0)
                                    DebugInfoRow("📈 Exercise Count", volumeData.sumOf { it.exerciseCount }.toString())
                                    
                                    // Show recent data points
                                    volumeData.takeLast(3).forEachIndexed { index, point ->
                                        DebugInfoRow("Recent ${index + 1}", "${point.date}: ${point.totalVolume}kg", 
                                            isError = point.totalVolume == 0f)
                                    }
                                } else {
                                    DebugInfoRow("Volume Data", "EMPTY DATASET", isError = true)
                                }
                            }
                            is AsyncData.Loading -> {
                                DebugInfoRow("Volume Chart", "Loading...", isError = false)
                            }
                            is AsyncData.Failure -> {
                                DebugInfoRow("Volume Chart", "Failed: ${volumeChart.error.message}", isError = true)
                            }
                            is AsyncData.NotAsked -> {
                                DebugInfoRow("Volume Chart", "Not Asked", isError = true)
                            }
                        }
                        
                        DebugInfoRow("User ID", chartsState.userId ?: "null")
                        DebugInfoRow("Time Range", chartsState.currentTimeRange.toString())
                    }
                    
                    if (rawChartsState is UiState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DebugInfoRow("Charts Error", rawChartsState.error.message, isError = true)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Summary ViewModel Debug (Secondary Issue)
            DebugSection(
                title = "📊 SUMMARY DEBUG",
                content = {
                    DebugStateRow("Summary State", rawSummaryState)
                    
                    // Show summary data details if successful
                    if (rawSummaryState is UiState.Success) {
                        val summaryState = rawSummaryState.data
                        Spacer(modifier = Modifier.height(8.dp))
                        DebugInfoRow("User ID", summaryState.userId ?: "null")
                        DebugInfoRow("Time Range", summaryState.currentTimeRange.toString())
                        
                        // Check if summary data is available
                        when (val summaryData = summaryState.summaryData) {
                            is AsyncData.Success -> {
                                val data = summaryData.data
                                DebugInfoRow("📊 Total Volume", "${data.totalVolume}kg", 
                                    isError = data.totalVolume == 0f)
                                DebugInfoRow("📊 Total Workouts", data.totalWorkouts.toString(),
                                    isError = data.totalWorkouts == 0)
                                DebugInfoRow("📊 Active Time", "${data.totalActiveTime}min")
                                DebugInfoRow("📊 Current Streak", data.currentStreak.toString())
                                DebugInfoRow("📊 Average Duration", "${data.averageDuration}min")
                            }
                            is AsyncData.Loading -> {
                                DebugInfoRow("Summary Data", "Loading...", isError = false)
                            }
                            is AsyncData.Failure -> {
                                DebugInfoRow("Summary Data", "Failed: ${summaryData.error.message}", isError = true)
                            }
                            is AsyncData.NotAsked -> {
                                DebugInfoRow("Summary Data", "Not Asked", isError = true)
                            }
                        }
                    }
                    
                    if (rawSummaryState is UiState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DebugInfoRow("Error Message", rawSummaryState.error.message, isError = true)
                        DebugInfoRow("Error Type", rawSummaryState.error::class.simpleName ?: "Unknown", isError = true)
                        DebugInfoRow("Is Recoverable", rawSummaryState.error.isRecoverable.toString())
                        rawSummaryState.error.analyticsContext?.let { context ->
                            context.forEach { (key, value) ->
                                DebugInfoRow("Context: $key", value.toString())
                            }
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Widget Debug Section
            DebugSection(
                title = "🧩 WIDGETS DEBUG",
                content = {
                    DebugStateRow("Widget State", rawWidgetState)
                    
                    if (rawWidgetState is UiState.Success) {
                        val widgetState = rawWidgetState.data
                        Spacer(modifier = Modifier.height(8.dp))
                        DebugInfoRow("Active Widgets Count", widgetState.activeWidgets.size.toString())
                        DebugInfoRow("Visible Widgets", widgetState.preferences?.visibleWidgets?.size?.toString() ?: "null")
                        DebugInfoRow("Has Preferences", (widgetState.preferences != null).toString())
                        
                        if (widgetState.activeWidgets.isNotEmpty()) {
                            DebugInfoRow("Active Widget Names", widgetState.activeWidgets.map { it.displayName }.joinToString(", "))
                        } else {
                            DebugInfoRow("Active Widgets", "EMPTY - No widgets to display!", isError = true)
                        }
                        
                        if (widgetState.preferences?.visibleWidgets?.isNotEmpty() == true) {
                            DebugInfoRow("Preference Widget Names", widgetState.preferences.visibleWidgets.joinToString(", "))
                        } else {
                            DebugInfoRow("Visible Widgets in Prefs", "EMPTY or NULL", isError = true)
                        }
                        
                        DebugInfoRow("Has Configuration", (widgetState.configuration != null).toString(), 
                            isError = widgetState.configuration == null)
                        
                        // Check the condition that determines if widgets section is shown
                        val shouldShowWidgets = widgetState.activeWidgets.isNotEmpty() && widgetState.configuration != null
                        DebugInfoRow("WIDGETS SECTION VISIBLE", shouldShowWidgets.toString(), 
                            isError = !shouldShowWidgets)
                        
                        if (widgetState.hasWidgetErrors()) {
                            DebugInfoRow("Widget Errors", widgetState.widgetErrors.size.toString(), isError = true)
                            widgetState.widgetErrors.forEach { (widgetId, error) ->
                                DebugInfoRow("Error: $widgetId", error.message, isError = true)
                            }
                        }
                    }
                    
                    if (rawWidgetState is UiState.Error) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DebugInfoRow("Widget Error", rawWidgetState.error.message, isError = true)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // User & Time Range Info
            DebugSection(
                title = "User & Time Range",
                content = {
                    DebugInfoRow("User ID", coordinatorState.getCurrentUserId() ?: "null")
                    DebugInfoRow("Time Range", calorieState.getTimeRangeDisplayText())
                    DebugInfoRow("Data Fresh", calorieState.isDataFresh().toString())
                }
            )
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DebugStateRow(
    name: String,
    state: UiState<*>
) {
    val (statusText, statusColor) = when (state) {
        is UiState.Loading -> "Loading" to MaterialTheme.colorScheme.primary
        is UiState.Success -> "Success" to MaterialTheme.colorScheme.tertiary
        is UiState.Error -> "Error" to MaterialTheme.colorScheme.error
        is UiState.Empty -> "Empty" to MaterialTheme.colorScheme.outline
        else -> "Unknown" to MaterialTheme.colorScheme.outline
    }
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
        
        // Show error details if in error state
        if (state is UiState.Error) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Error: ${state.error.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Composable
private fun DebugInfoRow(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DebugAsyncDataRow(
    label: String,
    asyncData: AsyncData<*>
) {
    val (statusText, statusColor) = when (asyncData) {
        is AsyncData.NotAsked -> "Not Asked" to MaterialTheme.colorScheme.outline
        is AsyncData.Loading -> "Loading" to MaterialTheme.colorScheme.primary
        is AsyncData.Success -> "Success (${asyncData.data?.let { "Data Available" } ?: "Null Data"})" to MaterialTheme.colorScheme.tertiary
        is AsyncData.Failure -> "Failed: ${asyncData.error.message}" to MaterialTheme.colorScheme.error
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
} 