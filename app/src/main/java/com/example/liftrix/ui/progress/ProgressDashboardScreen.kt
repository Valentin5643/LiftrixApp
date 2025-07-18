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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.example.liftrix.ui.progress.components.WidgetContainer
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import com.example.liftrix.ui.progress.components.CaloriesBurnedCard
import com.example.liftrix.ui.progress.components.DailyCaloriesCard
import com.example.liftrix.ui.progress.components.WeeklyCalorieTrendCard
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.BasicWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.CalorieSummary
import com.example.liftrix.domain.usecase.analytics.CalorieAnalyticsUseCase
import com.example.liftrix.ui.common.validation.ViewModelValidator
import timber.log.Timber

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

    // Proper StateFlow collection with lifecycle awareness and timeout handling
    val chartsState by chartsViewModel.uiState.collectAsStateWithLifecycle()
    val widgetState by widgetViewModel.uiState.collectAsStateWithLifecycle()
    val preferencesState by preferencesViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val calorieState by calorieViewModel.uiState.collectAsStateWithLifecycle()
    val featuresState by featuresViewModel.uiState.collectAsStateWithLifecycle()
    val coordinatorState by coordinator.uiState.collectAsStateWithLifecycle()
    
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
            onCoordinatorEvent = coordinator::handleEvent
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
    onCoordinatorEvent: (CoordinatorEvent) -> Unit
) {
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
                
                // Feature flag controlled export dropdown
                if (featuresState.exportEnabled) {
                    ExportDropdown(
                        onExportPdf = { onCoordinatorEvent(CoordinatorEvent.ExportToPdf) },
                        onExportCsv = { onCoordinatorEvent(CoordinatorEvent.ExportToCsv) }
                    )
                }
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Unified Calorie Analytics Section
        item {
            CalorieSection(
                calorieState = calorieState,
                onEvent = onCalorieEvent,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Widget-based analytics dashboard
        if (featuresState.analyticsEnabled) {
            item {
                WidgetsSection(
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
                    Button(
                        onClick = onRetry
                    ) {
                        Text("Retry")
                    }
                    OutlinedButton(
                        onClick = onDismiss
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

// EmptyStateContent removed - charts now always show with zero values

/**
 * Export dropdown menu for PDF and CSV export options
 */
@Composable
private fun ExportDropdown(
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(
            onClick = { expanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = "Export data",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as PDF")
                    }
                },
                onClick = {
                    expanded = false
                    onExportPdf()
                }
            )
            
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as CSV")
                    }
                },
                onClick = {
                    expanded = false
                    onExportCsv()
                }
            )
        }
    }
}

/**
 * Charts section composable
 */
@Composable
private fun ChartsSection(
    chartsState: ProgressChartsState,
    onEvent: (ProgressChartsEvent) -> Unit,
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
    if (widgetState.activeWidgets.isNotEmpty() && widgetState.dashboardConfiguration != null) {
        WidgetContainer(
            widgets = widgetState.activeWidgets,
            configuration = widgetState.dashboardConfiguration,
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
 * Summary section composable
 */
@Composable
private fun SummarySection(
    summaryState: ProgressSummaryState,
    onEvent: (ProgressSummaryEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when (val summary = summaryState.summaryData) {
        is AsyncData.Success -> {
            ProgressSummaryCards(
                summaryData = summary.data,
                isLoading = summaryState.isRefreshing
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
                OutlinedButton(
                    onClick = { onEvent(CalorieTrackingEvent.NavigateToCalorieGoalSettings) }
                ) {
                    Text("Goals")
                }
                
                Button(
                    onClick = { onEvent(CalorieTrackingEvent.NavigateToDetailedCalorieAnalytics) }
                ) {
                    Text("View All")
                }
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
    return BasicWidgetData(
        widgetType = widget,
        lastUpdated = kotlinx.datetime.Clock.System.now(),
        value = "0",
        subtitle = "Start working out to see data",
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
        
        Button(onClick = onRefresh) {
            Text("Refresh Data")
        }
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
                OutlinedButton(onClick = onDismiss) {
                    Text("Got it!")
                }
            }
        }
    }
} 