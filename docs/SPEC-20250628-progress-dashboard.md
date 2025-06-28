# SPEC-20250628-progress-dashboard

## Executive Summary
**Feature**: Progress Dashboard with Workout Statistics
**Impact**: Provide users with comprehensive workout analytics including volume trends, duration charts, and frequency heatmaps to motivate continued fitness engagement.
**Effort**: 3-4 developer-days
**Risk**: Medium - Requires chart library integration and complex data aggregation logic
**Dependencies**: Chart library selection (Vico or similar), existing workout data models

## Product Specifications

### Elevator Pitch
A comprehensive statistics dashboard that transforms raw workout data into meaningful insights through interactive charts and visualizations, helping users track their fitness progress over time.

### Target Users
- **Primary**: Regular fitness users who want to track progress and identify patterns, weekly usage
- **Secondary**: Goal-oriented users who need data-driven feedback for fitness planning

### Core Goals
1. **Motivation**: Visual progress representation to encourage consistent workout habits
2. **Insights**: Clear patterns in workout frequency, volume, and duration trends
3. **Performance**: Charts load within 500ms, smooth interactions on scroll/zoom

### Functional Requirements
- **FR-001**: Workout Volume Trends Chart
  - **Given**: User has completed workouts with tracked weights/reps
  - **When**: User opens Progress tab
  - **Then**: Line chart displays total volume (weight × reps) over time with trend line
  - **Acceptance**: Verified by UI test `test_volume_chart_displays_trend_data`

- **FR-002**: Duration Statistics Chart
  - **Given**: User has workout history with duration data
  - **When**: User views duration section
  - **Then**: Bar chart shows average workout duration by week/month with target line
  - **Acceptance**: Verified by UI test `test_duration_chart_shows_weekly_averages`

- **FR-003**: Frequency Heatmap
  - **Given**: User has workout history spanning multiple weeks
  - **When**: User scrolls to frequency section
  - **Then**: Calendar heatmap shows workout frequency with color intensity per day
  - **Acceptance**: Verified by UI test `test_frequency_heatmap_shows_workout_days`

- **FR-004**: Time Period Selection
  - **Given**: User is viewing any chart
  - **When**: User selects different time period (1M, 3M, 6M, 1Y)
  - **Then**: Charts update to show data for selected period with smooth transition
  - **Acceptance**: Verified by integration test `test_time_period_updates_all_charts`

### User Stories
- **US-001**: As a fitness enthusiast, I want to see my strength progression over time so that I can validate my training is effective.
  - **Acceptance Criteria**:
    1. Volume chart shows clear upward trend when user is progressing
    2. Chart displays both raw data points and smoothed trend line
    3. User can tap data points to see specific workout details
    4. Chart handles missing data gracefully with interpolation or gaps

- **US-002**: As a user trying to build consistency, I want to visualize my workout frequency so that I can identify patterns and improve adherence.
  - **Acceptance Criteria**:
    1. Heatmap clearly shows active vs inactive days
    2. Different intensities/colors for single vs multiple workouts per day
    3. Current week highlighted for immediate feedback
    4. Streak counter shows longest and current workout streaks

### Non-Goals
- **Advanced analytics like 1RM calculations** - Reason: Deferred to V2, focus on basic trends first
- **Comparison with other users or social features** - Reason: Not requested, keeps dashboard personal
- **Export functionality for charts** - Reason: Not essential for V1, can be added later

## Technical Specifications

### System Architecture
- **Pattern**: Repository pattern with data aggregation layer, using Flow-based reactive updates
- **Flow**: Repository → DataAggregator → ViewModel → Chart Components → UI
- **Security**: User-scoped data queries, no additional security requirements

### Database Design
No schema changes required - uses existing workout, exercise, and set entities for aggregation:
- `WorkoutEntity` for duration and frequency data
- `ExerciseEntity` and `ExerciseSetEntity` for volume calculations
- Time-based queries with efficient indexing on date fields

### Component Design

#### ProgressDashboardScreen
```kotlin
@Composable
fun ProgressDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            TimePeriodSelector(
                selectedPeriod = uiState.selectedTimePeriod,
                onPeriodSelected = { viewModel.onEvent(ProgressEvent.SelectTimePeriod(it)) }
            )
        }
        
        item {
            WorkoutVolumeChart(
                data = uiState.volumeData,
                isLoading = uiState.isLoadingVolume,
                modifier = Modifier.height(240.dp)
            )
        }
        
        item {
            WorkoutDurationChart(
                data = uiState.durationData,
                isLoading = uiState.isLoadingDuration,
                modifier = Modifier.height(240.dp)
            )
        }
        
        item {
            WorkoutFrequencyHeatmap(
                data = uiState.frequencyData,
                isLoading = uiState.isLoadingFrequency,
                modifier = Modifier.height(200.dp)
            )
        }
        
        item {
            ProgressSummaryCards(
                totalWorkouts = uiState.summaryStats.totalWorkouts,
                currentStreak = uiState.summaryStats.currentStreak,
                longestStreak = uiState.summaryStats.longestStreak,
                averageDuration = uiState.summaryStats.averageDuration
            )
        }
    }
}
```

#### Data Models
```kotlin
data class ProgressDashboardUiState(
    val selectedTimePeriod: TimePeriod = TimePeriod.THREE_MONTHS,
    val volumeData: List<VolumeDataPoint> = emptyList(),
    val durationData: List<DurationDataPoint> = emptyList(),
    val frequencyData: Map<LocalDate, Int> = emptyMap(),
    val summaryStats: ProgressSummaryStats = ProgressSummaryStats(),
    val isLoadingVolume: Boolean = false,
    val isLoadingDuration: Boolean = false,
    val isLoadingFrequency: Boolean = false,
    val error: String? = null
)

data class VolumeDataPoint(
    val date: LocalDate,
    val totalVolume: Double, // weight × reps across all exercises
    val workoutCount: Int
)

data class DurationDataPoint(
    val weekStart: LocalDate,
    val averageDuration: Duration,
    val workoutCount: Int
)

data class ProgressSummaryStats(
    val totalWorkouts: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val averageDuration: Duration = Duration.ZERO
)

enum class TimePeriod(val days: Int, val label: String) {
    ONE_MONTH(30, "1M"),
    THREE_MONTHS(90, "3M"),
    SIX_MONTHS(180, "6M"),
    ONE_YEAR(365, "1Y")
}
```

#### ProgressDashboardViewModel
```kotlin
@HiltViewModel
class ProgressDashboardViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val progressStatsRepository: ProgressStatsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ProgressDashboardUiState())
    val uiState: StateFlow<ProgressDashboardUiState> = _uiState.asStateFlow()
    
    init {
        observeAuthenticatedUser()
    }
    
    private fun observeAuthenticatedUser() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collect { user ->
                if (user != null) {
                    loadProgressData(user.uid)
                }
            }
        }
    }
    
    fun onEvent(event: ProgressEvent) {
        when (event) {
            is ProgressEvent.SelectTimePeriod -> {
                _uiState.update { it.copy(selectedTimePeriod = event.period) }
                loadProgressData()
            }
            ProgressEvent.Refresh -> loadProgressData()
        }
    }
    
    private fun loadProgressData(userId: String? = null) {
        val user = userId ?: authRepository.getCurrentUser().value?.uid ?: return
        val period = _uiState.value.selectedTimePeriod
        
        loadVolumeData(user, period)
        loadDurationData(user, period)
        loadFrequencyData(user, period)
        loadSummaryStats(user, period)
    }
    
    private fun loadVolumeData(userId: String, period: TimePeriod) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVolume = true) }
            try {
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(period.days.toLong())
                
                progressStatsRepository.getVolumeData(userId, startDate, endDate)
                    .collect { volumeData ->
                        _uiState.update { 
                            it.copy(
                                volumeData = volumeData,
                                isLoadingVolume = false
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingVolume = false,
                        error = "Failed to load volume data: ${e.message}"
                    )
                }
            }
        }
    }
    
    // Similar methods for duration, frequency, and summary data...
}

sealed class ProgressEvent {
    data class SelectTimePeriod(val period: TimePeriod) : ProgressEvent()
    object Refresh : ProgressEvent()
}
```

#### Chart Components (Using Vico Library)
```kotlin
@Composable
fun WorkoutVolumeChart(
    data: List<VolumeDataPoint>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Workout Volume Trend",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (data.isEmpty()) {
                EmptyChartState(
                    message = "No volume data available",
                    modifier = Modifier.height(160.dp)
                )
            } else {
                AndroidView(
                    factory = { context ->
                        ChartView(context).apply {
                            // Configure Vico chart
                            chart = lineChart {
                                line(
                                    lineColor = Color.BLUE,
                                    lineWidth = 2.dp
                                )
                            }
                        }
                    },
                    update = { chartView ->
                        // Update chart data
                        val entries = data.map { point ->
                            entryOf(
                                x = point.date.toEpochDay().toFloat(),
                                y = point.totalVolume.toFloat()
                            )
                        }
                        chartView.setModel(entryModelOf(entries))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
        }
    }
}

@Composable
fun WorkoutFrequencyHeatmap(
    data: Map<LocalDate, Int>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Workout Frequency",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7), // 7 days per week
                    modifier = Modifier.height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(84) { index -> // 12 weeks
                        val date = LocalDate.now().minusDays(83 - index.toLong())
                        val workoutCount = data[date] ?: 0
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(
                                    color = when (workoutCount) {
                                        0 -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}
```

#### ProgressStatsRepository
```kotlin
interface ProgressStatsRepository {
    fun getVolumeData(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<VolumeDataPoint>>
    fun getDurationData(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DurationDataPoint>>
    fun getFrequencyData(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<Map<LocalDate, Int>>
    fun getSummaryStats(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<ProgressSummaryStats>
}

@Singleton
class ProgressStatsRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val exerciseSetDao: ExerciseSetDao
) : ProgressStatsRepository {
    
    override fun getVolumeData(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<VolumeDataPoint>> {
        return flow {
            val workouts = workoutDao.getWorkoutsInDateRange(userId, startDate, endDate)
            
            val volumeData = workouts.groupBy { it.createdAt.toLocalDate() }
                .map { (date, dayWorkouts) ->
                    val totalVolume = dayWorkouts.sumOf { workout ->
                        calculateWorkoutVolume(workout.id)
                    }
                    VolumeDataPoint(
                        date = date,
                        totalVolume = totalVolume,
                        workoutCount = dayWorkouts.size
                    )
                }
                .sortedBy { it.date }
            
            emit(volumeData)
        }
    }
    
    private suspend fun calculateWorkoutVolume(workoutId: String): Double {
        val exercises = exerciseDao.getExercisesForWorkout(workoutId)
        return exercises.sumOf { exercise ->
            val sets = exerciseSetDao.getSetsForExercise(exercise.id)
            sets.sumOf { set ->
                (set.weight ?: 0.0) * (set.reps ?: 0)
            }
        }
    }
}
```

### Testing Strategy
- **Test Scenarios**:
  1. "Verify volume chart displays trend data with proper scaling and axes"
  2. "Confirm duration chart shows weekly averages with accurate calculations"
  3. "Validate frequency heatmap color-codes workout intensity correctly"
  4. "Test time period selection updates all charts with smooth transitions"
  5. "Verify empty states display appropriate messages and call-to-actions"

## Implementation Plan

### Task Breakdown

#### Repository Layer (REPO-XXX)
- [ ] **REPO-001**: Create ProgressStatsRepository interface [Estimate: 2hr]
  - **Files**: `domain/repository/ProgressStatsRepository.kt`
  - **Details**: Define data aggregation interfaces for charts

- [ ] **REPO-002**: Implement ProgressStatsRepositoryImpl [Estimate: 6hr]
  - **Files**: `data/repository/ProgressStatsRepositoryImpl.kt`
  - **Details**: Complex SQL queries for volume, duration, and frequency calculations

#### UI Components (UI-XXX)
- [ ] **UI-001**: Create ProgressDashboardScreen [Estimate: 4hr]
  - **Files**: `ui/progress/ProgressDashboardScreen.kt`
  - **Details**: Main screen layout with lazy column and chart containers

- [ ] **UI-002**: Create chart components (Volume, Duration, Frequency) [Estimate: 8hr]
  - **Files**: `ui/progress/components/WorkoutVolumeChart.kt`, `ui/progress/components/WorkoutDurationChart.kt`, `ui/progress/components/WorkoutFrequencyHeatmap.kt`
  - **Details**: Implement charts using Vico library with proper data binding

- [ ] **UI-003**: Create TimePeriodSelector component [Estimate: 2hr]
  - **Files**: `ui/progress/components/TimePeriodSelector.kt`
  - **Details**: Segmented control for time period selection

- [ ] **UI-004**: Create ProgressSummaryCards component [Estimate: 3hr]
  - **Files**: `ui/progress/components/ProgressSummaryCards.kt`
  - **Details**: Stats cards for total workouts, streaks, average duration

#### ViewModels (VM-XXX)
- [ ] **VM-001**: Create ProgressDashboardViewModel [Estimate: 5hr]
  - **Files**: `ui/progress/ProgressDashboardViewModel.kt`
  - **Details**: State management, data loading, and event handling

#### External Dependencies (DEP-XXX)
- [ ] **DEP-001**: Add Vico chart library dependency [Estimate: 1hr]
  - **Files**: `app/build.gradle.kts`
  - **Details**: Add chart library and configure ProGuard rules

- [ ] **DEP-002**: Configure chart library setup [Estimate: 2hr]
  - **Files**: `di/ChartModule.kt` (new)
  - **Details**: DI setup for chart configurations and themes

#### Testing (TEST-XXX)
- [ ] **TEST-001**: Repository unit tests [Estimate: 4hr]
  - **Files**: `data/repository/ProgressStatsRepositoryImplTest.kt`
  - **Details**: Test data aggregation logic with mock data

- [ ] **TEST-002**: ViewModel unit tests [Estimate: 3hr]
  - **Files**: `ui/progress/ProgressDashboardViewModelTest.kt`
  - **Details**: Test state management and data loading flows

- [ ] **TEST-003**: UI component tests [Estimate: 4hr]
  - **Files**: `ui/progress/ProgressDashboardScreenTest.kt`
  - **Details**: Test chart rendering, empty states, and user interactions

### Dependencies
- REPO-002 depends on REPO-001
- VM-001 depends on REPO-001
- UI-001 depends on VM-001
- UI-002 depends on DEP-001, DEP-002
- TEST-001 depends on REPO-002
- TEST-002 depends on VM-001
- TEST-003 depends on UI-001, UI-002

## Success Metrics
- **Performance**: Charts load and render within 500ms (measured via systrace)
- **Data Accuracy**: 100% accurate volume calculations compared to manual verification (tested with known datasets)
- **User Engagement**: 60% of users view Progress tab within first week (measured via analytics)

## Timeline
**Total Effort**: 40 hours (5 developer-days, reduced to 4 days with parallel work)
**Critical Path**: REPO-001 → REPO-002 → VM-001 → UI-001 → Testing (minimum 3 days)