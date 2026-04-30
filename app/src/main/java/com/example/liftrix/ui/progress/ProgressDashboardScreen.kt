package com.example.liftrix.ui.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Timer
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.state.AsyncData
import com.example.liftrix.domain.repository.ProgressSummary
import com.example.liftrix.ui.components.layouts.GridSystem
import com.example.liftrix.ui.progress.components.ProgressSummaryCards
import com.example.liftrix.ui.progress.components.GlobalTimeRangeSelector
import com.example.liftrix.ui.progress.components.VolumeCalendarWidget
import com.example.liftrix.ui.progress.components.WorkoutDurationChart
import com.example.liftrix.ui.progress.components.WorkoutVolumeHeatmap
import com.example.liftrix.ui.progress.components.WorkoutVolumeChart
import com.example.liftrix.ui.progress.components.charts.ModernVolumeChart
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.analytics.VolumeDataPoint as AnalyticsVolumeDataPoint
import com.example.liftrix.domain.repository.VolumeDataPoint as RepositoryVolumeDataPoint
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.core.formatting.WeightFormatter
import com.example.liftrix.ui.progress.components.WidgetContainer
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.progress.components.ResponsiveDashboardLayout
import com.example.liftrix.ui.progress.components.DashboardLayoutMode
import com.example.liftrix.ui.common.rememberWindowSizeClass
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
import com.example.liftrix.ui.common.validation.ViewModelValidator
import com.example.liftrix.core.extensions.collectAsOptimizedState
import com.example.liftrix.ui.progress.NavigationCallbacks
import com.example.liftrix.ui.progress.NavigationEvent

/**
 * Maps domain DashboardLayoutMode to UI DashboardLayoutMode
 */
private fun mapDomainLayoutModeToUI(
    domainMode: com.example.liftrix.domain.model.analytics.DashboardLayoutMode?
): DashboardLayoutMode? {
    return DashboardLayoutMode.fromDomain(domainMode)
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
    coordinator: ProgressDashboardCoordinator = hiltViewModel(),
    onNavigateToVolumeDetail: () -> Unit = {},
    onNavigateToOneRmDetail: () -> Unit = {},
    onNavigateToMuscleGroupDetail: () -> Unit = {},
    onNavigateToFrequencyDetail: () -> Unit = {},
    onNavigateToDashboardCustomization: () -> Unit = {}
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

    val chartsState by chartsViewModel.uiState.collectAsStateWithLifecycle()
    val widgetState by widgetViewModel.uiState.collectAsStateWithLifecycle() 
    val preferencesState by preferencesViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val calorieState by calorieViewModel.uiState.collectAsStateWithLifecycle()
    val featuresState by featuresViewModel.uiState.collectAsStateWithLifecycle()
    val coordinatorState by coordinator.uiState.collectAsStateWithLifecycle()
    
    
    
    // REMOVED: Screen-level timeout monitoring - now handled properly in ViewModel
    // The ViewModel now monitors timeout from actual fetch start, not screen init

    // Set up navigation callbacks for AnalyticsWidgetViewModel
    // Use remember to create stable callbacks that don't change on every recomposition
    val stableCallbacks = remember {
        NavigationCallbacks(
            onNavigateToVolumeDetail = onNavigateToVolumeDetail,
            onNavigateToOneRmDetail = onNavigateToOneRmDetail,
            onNavigateToMuscleGroupDetail = onNavigateToMuscleGroupDetail,
            onNavigateToFrequencyDetail = onNavigateToFrequencyDetail,
            onNavigateToDashboardCustomization = onNavigateToDashboardCustomization
        )
    }
    
    LaunchedEffect(stableCallbacks) {
        widgetViewModel.setNavigationCallbacks(stableCallbacks)
    }
    
    // Handle fallback navigation events when callbacks fail
    LaunchedEffect(Unit) {
        widgetViewModel.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToOneRmDetail -> {
                    onNavigateToOneRmDetail()
                }
                is NavigationEvent.NavigateToVolumeDetail -> {
                    onNavigateToVolumeDetail()
                }
                is NavigationEvent.NavigateToMuscleGroupDetail -> {
                    onNavigateToMuscleGroupDetail()
                }
                is NavigationEvent.NavigateToFrequencyDetail -> {
                    onNavigateToFrequencyDetail()
                }
            }
        }
    }
    
    // Connect Coordinator events to ViewModels
    // RACE CONDITION FIX: Use Unit as key to prevent LaunchedEffect re-triggering on recomposition
    LaunchedEffect(Unit) {
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
        val extractedChartsState = when (val currentChartsState = chartsState) {
            is UiState.Success -> currentChartsState.data
            is UiState.Loading -> ProgressChartsState()
            is UiState.Error -> ProgressChartsState()
            null -> ProgressChartsState()
            else -> ProgressChartsState()
        }
        
        ProgressDashboardContent(
            chartsState = extractedChartsState,
            widgetState = (widgetState as? UiState.Success)?.data ?: AnalyticsWidgetState(),
            preferencesState = (preferencesState as? UiState.Success)?.data ?: UserPreferencesState(),
            summaryState = (summaryState as? UiState.Success)?.data ?: ProgressSummaryState(),
            calorieState = (calorieState as? UiState.Success)?.data ?: CalorieTrackingState(),
            featuresState = (featuresState as? UiState.Success)?.data ?: FeatureConfigurationState(),
            coordinatorState = (coordinatorState as? UiState.Success)?.data ?: CoordinatorState(),
            coordinator = coordinator,
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
    coordinator: ProgressDashboardCoordinator,
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
    
    
    // Migration notification state
    var showMigrationNotification by remember { mutableStateOf(false) }
    
    // Check if migration notification should be shown
    LaunchedEffect(rawPreferencesState) {
        if (rawPreferencesState is UiState.Success) {
            val preferencesState = rawPreferencesState.data
            val widgetPreferences = preferencesState.getPreferences()
            showMigrationNotification = widgetPreferences?.hasSeenWidgetMigrationNotice == false
        }
    }
    
    // Get unit conversion service for widgets
    val unitConversionService = coordinator.getUnitConversionService()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp) // Tighter spacing (FR-005)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Modern screen header with enhanced typography and export functionality
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
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


        // Time period selector with modern styling
        item {
            GlobalTimeRangeSelector(
                selectedTimeRange = chartsState.currentTimeRange.type,
                onTimeRangeChange = { newTimeRangeType ->
                    val newTimeRange = when (newTimeRangeType) {
                        com.example.liftrix.domain.model.analytics.TimeRangeType.MONTH -> com.example.liftrix.domain.model.analytics.TimeRange.lastMonth()
                        com.example.liftrix.domain.model.analytics.TimeRangeType.SIX_MONTHS -> com.example.liftrix.domain.model.analytics.TimeRange.lastSixMonths()
                        com.example.liftrix.domain.model.analytics.TimeRangeType.ALL_TIME -> com.example.liftrix.domain.model.analytics.TimeRange.allTime()
                    }
                    onCoordinatorEvent(CoordinatorEvent.TimePeriodChanged(newTimeRange))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // DISABLED: Analytics onboarding and migration notifications
        // Test user requested removal of all "Analytics improvement" popups
        // 
        // if (featuresState.showOnboarding && featuresState.analyticsEnabled) {
        //     item {
        //         AnalyticsOnboardingCard(
        //             onDismiss = { onFeaturesEvent(FeatureConfigurationEvent.DismissOnboarding) },
        //             modifier = Modifier.fillMaxWidth()
        //         )
        //     }
        // }
        // 
        // if (featuresState.showMigrationNotification && featuresState.analyticsEnabled) {
        //     item {
        //         AnalyticsMigrationNotificationCard(
        //             onDismiss = { onFeaturesEvent(FeatureConfigurationEvent.DismissMigrationNotification) },
        //             modifier = Modifier
        //                 .fillMaxWidth()
        //                 .animateContentSize(animationSpec = tween(300))
        //         )
        //     }
        // }

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
                    coordinatorPreferences = coordinatorState.coordinatorPreferences,
                    unitConversionService = coordinator.getUnitConversionService(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Charts section with enhanced debugging
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // DISABLED: Migration notification dialog  
    // Test user requested removal of all "Analytics improvement" popups
    //
    // if (showMigrationNotification) {
    //     AlertDialog(
    //         onDismissRequest = {
    //             showMigrationNotification = false
    //             onPreferencesEvent(UserPreferencesEvent.MarkMigrationNoticeSeen)
    //         },
    //         title = {
    //             Text(
    //                 text = "📊 Analytics Improvements",
    //                 style = MaterialTheme.typography.headlineSmall
    //             )
    //         },
    //         text = {
    //             Text(
    //                 text = "We've streamlined your dashboard to focus on strength training metrics for better performance and clarity. Some older analytics widgets have been removed to improve your experience.",
    //                 style = MaterialTheme.typography.bodyMedium
    //             )
    //         },
    //         confirmButton = {
    //             TextButton(
    //                 onClick = {
    //                     showMigrationNotification = false
    //                     onPreferencesEvent(UserPreferencesEvent.MarkMigrationNoticeSeen)
    //                 }
    //             ) {
    //                 Text("Got it")
    //             }
    //         }
    //     )
    // }
}

/**
 * Enhanced chart container matching mock design with improved visual hierarchy
 */
@Composable
private fun ChartContainer(
    title: String,
    icon: ImageVector,
    isLoading: Boolean,
    hasData: Boolean,
    isWaitingForAuth: Boolean = false,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    Card( // Specialized chart container card
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Chart header with enhanced styling
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (!hasData && !isLoading) {
                    Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart content with better spacing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        LoadingChartPlaceholder(isWaitingForAuth = isWaitingForAuth)
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
 * Loading state for charts with authentication awareness
 */
@Composable
private fun LoadingChartPlaceholder(
    isWaitingForAuth: Boolean = false
) {
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
            text = if (isWaitingForAuth) {
                "Waiting for user authentication..."
            } else {
                "Loading chart data..."
            },
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
 * Modern error state content with enhanced visual design matching mock
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
        Card( // Specialized error display card
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error loading progress",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                
                Text(
                    text = "Error Loading Progress",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    // Extract chartsState to local variable to enable smart cast throughout function
    val currentChartsState = chartsState
    

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // REMOVED: Volume Progress and Workout Duration charts as requested
        // These graphs were not being used and appeared as empty placeholders
        
        // Volume chart - REMOVED (unused graph that appeared as placeholder)
        // Duration chart - REMOVED (unused graph that appeared as placeholder)
        
        // Frequency chart - PROGRESSIVE loading: show individual chart state  
        // STABILITY FIX: Add userId null guard for frequency chart
        if (currentChartsState.userId == null) {
            // Show "Please log in" state, not loading
            ChartContainer(
                title = "Workout Frequency", 
                icon = Icons.Default.TableChart,
                isLoading = false,
                hasData = false,
                isWaitingForAuth = true,
                onRefresh = { /* Handle refresh */ }
            ) {
                // Show login required message instead of infinite loading
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(
                        text = "Please log in to view charts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            val frequencyChartData = (currentChartsState.frequencyChart as? AsyncData.Success)?.data ?: emptyList()
            val isFrequencyLoading = currentChartsState.frequencyChart is AsyncData.Loading
            
            // Check if we're waiting for authentication before loading data
            val isFrequencyWaitingForAuth = currentChartsState.isWaitingForAuth() && currentChartsState.frequencyChart is AsyncData.NotAsked
            
            
            // Show authentication-aware loading state
            if (isFrequencyWaitingForAuth) {
                ChartContainer(
                    title = "Volume Calendar", 
                    icon = Icons.Default.FitnessCenter,
                    isLoading = true,
                    hasData = false,
                    isWaitingForAuth = true,
                    onRefresh = { /* Handle refresh */ }
                ) { /* Empty content when waiting for auth */ }
            } else {
                // PROGRESSIVE LOADING: Always show volume heatmap (replacing frequency)
                WorkoutVolumeHeatmap(
                    data = when (chartsState.volumeCalendar) {
                        is AsyncData.Success -> chartsState.volumeCalendar.data
                        else -> null
                    },
                    isLoading = chartsState.volumeCalendar is AsyncData.Loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
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
    coordinator: ProgressDashboardCoordinator,
    modifier: Modifier = Modifier
) {
    // Get coordinator preferences for unit conversion
    var coordinatorPreferences by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    val unitConversionService = coordinator.getUnitConversionService()
    
    LaunchedEffect(coordinator) {
        coordinatorPreferences = coordinator.getCurrentPreferences()
    }
    
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
            coordinatorPreferences = coordinatorPreferences,
            unitConversionService = unitConversionService,
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
    coordinatorPreferences: Map<String, Any> = emptyMap(),
    unitConversionService: com.example.liftrix.domain.service.UnitConversionService? = null,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = rememberWindowSizeClass()
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter out deprecated widgets for modern focused experience
        val filteredWidgets = widgetState.activeWidgets.filterNot { widget ->
            widget.isDeprecated
        }
        
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
                    val configurableCount = filteredWidgets.count { 
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
        
        
        // Only show widgets if we have configuration and non-deprecated widgets available
        if (filteredWidgets.isNotEmpty() && widgetState.configuration != null) {
            ResponsiveDashboardLayout(
                widgets = filteredWidgets,
                configuration = widgetState.configuration,
                layoutMode = run {
                    val userPreference = mapDomainLayoutModeToUI(widgetState.preferences?.dashboardLayout)
                    
                    // Convert UI enum result back to domain enum for ResponsiveDashboardLayout
                    val uiLayoutMode = DashboardLayoutMode.getOptimalMode(
                        screenWidthDp = windowSizeClass.widthDp.value.toInt(),
                        widgetCount = filteredWidgets.size,
                        userPreference = userPreference
                    )
                    val result = DashboardLayoutMode.toDomain(uiLayoutMode)
                    
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
                windowSizeClass = windowSizeClass,
                coordinatorPreferences = coordinatorPreferences,
                unitConversionService = unitConversionService
            )
        } else {
            // Empty state with customization prompt
            Card( // Specialized empty state card
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
                        contentDescription = "No active widgets",
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
 * Helper function to create default widget data - Clean zero-state display (FR-004)
 */
private fun createDefaultWidgetData(widget: com.example.liftrix.domain.model.analytics.AnalyticsWidget): WidgetData {
    // Clean zero-state display with appropriate units per FR-004
    val (defaultValue, defaultUnit) = when (widget) {
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.TotalVolume -> "0" to WeightUnit.getSystemDefault().symbol
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.WorkoutStreak -> "0" to "days"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.AverageDuration -> "0" to "min"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.WorkoutFrequency -> "0" to "workouts"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.StrengthProgress -> "0" to WeightUnit.getSystemDefault().symbol
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.PersonalRecords -> "0" to "records"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.OneRMProgression -> "0" to WeightUnit.getSystemDefault().symbol
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.MuscleGroupDistribution -> "0" to "%"
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.VolumeLoadProgression -> "0" to WeightUnit.getSystemDefault().symbol
        com.example.liftrix.domain.model.analytics.AnalyticsWidget.RecoveryMetrics -> "0" to "h"
        else -> "0" to ""
    }
    
    return MetricWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        primaryValue = defaultValue,
        secondaryValue = null, // Remove repetitive messaging
        unit = defaultUnit,
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
    Card( // Specialized calorie insights card
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
            contentDescription = label,
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
            contentDescription = "Calorie analytics",
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
    Card( // Specialized onboarding card
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
 * Analytics migration notification card component with smooth entrance animation
 */
@Composable
private fun AnalyticsMigrationNotificationCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animationProgress.animateTo(1f, animationSpec = tween(300))
    }
    
    Card( // Specialized migration notification card
        modifier = modifier
            .graphicsLayer(
                alpha = animationProgress.value,
                translationY = (1 - animationProgress.value) * 50.dp.value
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 Analytics Improvements",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We've focused your analytics experience on strength training insights. Enjoy modern charts, improved performance, and a cleaner dashboard tailored for serious lifters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SecondaryActionButton(
                    text = "Understood",
                    onClick = onDismiss
                )
            }
        }
    }
}


 
