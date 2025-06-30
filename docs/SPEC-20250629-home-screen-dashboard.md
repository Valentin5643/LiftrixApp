# SPEC-20250629-home-screen-dashboard

## Executive Summary
**Feature**: Home Screen Dashboard with Workout History and Social Feed
**Impact**: Increase user engagement by 25% through personalized workout insights and social motivation features. Reduce onboarding drop-off by providing immediate value on first screen.
**Effort**: 8-10 developer days for core functionality
**Risk**: Low - Leverages existing Clean Architecture patterns with Room + Firebase integration
**Dependencies**: None - builds entirely on existing infrastructure

## Product Specifications

### Elevator Pitch
A personalized fitness dashboard that shows your recent workout history, progress insights, and social connections in one unified home screen to keep you motivated and engaged with your fitness journey.

### Target Users
- **Primary**: Active fitness enthusiasts who complete 2+ workouts per week and want quick progress visibility
- **Secondary**: Social fitness users who stay motivated through community connections and shared achievements

### Core Goals
1. **Performance**: Home screen loads in <500ms with cached data, refresh completes in <2s
2. **Usability**: Reduce navigation clicks to view recent workouts from 3+ to 0 (immediately visible)  
3. **Scale**: Support display of 7 recent workouts with full exercise summaries without performance degradation

### Functional Requirements

**FR-001: Recent Workout Display**
- **Given**: User has completed workouts in their history
- **When**: User opens the home screen
- **Then**: Display last 7 completed workouts with title, date, duration, and exercise count
- **Acceptance**: Verified by UI test `test_recent_workouts_display`

**FR-002: Workout Summary Cards**
- **Given**: A workout has been completed with exercises and sets
- **When**: Workout is displayed in recent history
- **Then**: Show workout title, completion date/time, total duration, and top 2 exercises with set/rep summary
- **Acceptance**: Each workout card displays all required summary data correctly

**FR-003: Quick Actions**
- **Given**: User views a recent workout on home screen
- **When**: User taps on workout card
- **Then**: Navigate to detailed workout view with full exercise breakdown
- **Acceptance**: Navigation preserves workout context and loads detailed view

**FR-004: Empty State Handling**
- **Given**: New user with no completed workouts
- **When**: User opens home screen
- **Then**: Display welcome message with "Start Your First Workout" CTA
- **Acceptance**: CTA navigates to workout creation flow

**FR-005: Progress Insights**
- **Given**: User has workout history
- **When**: Home screen loads
- **Then**: Display key stats (total workouts, current streak, weekly volume)
- **Acceptance**: Stats accurately reflect user's workout data

### User Stories

**US-001**: As a fitness enthusiast, I want to see my last 7 workouts immediately when I open the app so that I can quickly track my consistency and progress.
- **Acceptance Criteria**:
  1. Recent workouts load within 500ms on app launch
  2. Each workout shows: name, date, duration, exercise count
  3. Workouts are ordered by completion date (most recent first)
  4. Tapping a workout opens detailed view

**US-002**: As a user returning to fitness, I want to see motivational insights about my progress so that I stay encouraged to continue.
- **Acceptance Criteria**:
  1. Progress cards show workout streak, weekly totals, and improvements
  2. Visual indicators highlight positive trends
  3. Encouragement messaging for milestone achievements

**US-003**: As a new user, I want clear guidance on getting started so that I don't feel overwhelmed by an empty screen.
- **Acceptance Criteria**:
  1. Empty state shows friendly welcome message
  2. Primary CTA leads to workout creation
  3. Optional secondary action for exploring templates

### Non-Goals
- **Detailed Exercise Analytics**: Deferred to dedicated Progress screen - **Reason**: Home screen focuses on quick overview, not deep analysis
- **Workout Editing**: Deferred to dedicated workout screens - **Reason**: Home screen is for viewing/navigation, not editing
- **Social Comments/Likes**: Deferred to V2 - **Reason**: V1 focuses on basic social presence and sharing

## Technical Specifications

### System Architecture
- **Pattern**: Clean Architecture with MVVM using Compose UI
- **Flow**: HomeViewModel → WorkoutRepository → WorkoutDao → Room Database
- **Security**: User-scoped queries ensure data isolation between users

### Database Design
**No Schema Changes Required** - Leverages existing entities:
```kotlin
// Existing WorkoutEntity supports all requirements
data class WorkoutEntity(
    val id: String,
    val userId: String,
    val name: String?,
    val date: LocalDate,
    val startTime: Instant?,
    val endTime: Instant?,
    val exercisesJson: String, // Contains exercise summary data
    val status: WorkoutStatus
)

// Existing query pattern for recent workouts
@Query("SELECT * FROM workouts WHERE user_id = :userId AND status = 'COMPLETED' ORDER BY date DESC, created_at DESC LIMIT :limit")
fun getRecentCompletedWorkouts(userId: String, limit: Int): Flow<List<WorkoutEntity>>
```

### API Specifications
**Repository Extension:**
```kotlin
interface WorkoutRepository {
    // Extend existing interface
    fun getRecentWorkouts(userId: String, limit: Int = 7): Flow<List<Workout>>
    fun getWorkoutStats(userId: String): Flow<WorkoutStats>
}

// Implementation leverages existing patterns
class WorkoutRepositoryImpl : WorkoutRepository {
    override fun getRecentWorkouts(userId: String, limit: Int): Flow<List<Workout>> =
        workoutDao.getRecentCompletedWorkouts(userId, limit)
            .map { entities -> entities.map { workoutMapper.toDomain(it) } }
}
```

### Component Design

**HomeScreen Architecture:**
```kotlin
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.error != null -> ErrorState(error = uiState.error, onRetry = { onEvent(HomeEvent.Retry) })
            uiState.isEmpty -> EmptyState(onStartWorkout = { onEvent(HomeEvent.StartWorkout) })
            else -> HomeContent(uiState = uiState, onEvent = onEvent)
        }
        
        if (uiState.isRefreshing) {
            RefreshIndicator()
        }
    }
}

@Composable
private fun HomeContent(uiState: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        item { WelcomeHeader(userName = uiState.userName) }
        item { ProgressInsightsCards(stats = uiState.workoutStats) }
        item { RecentWorkoutsSection(workouts = uiState.recentWorkouts, onWorkoutClick = { onEvent(HomeEvent.OpenWorkout(it)) }) }
    }
}
```

**State Management:**
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val userId = authRepository.getCurrentUserId()
                combine(
                    workoutRepository.getRecentWorkouts(userId, 7),
                    workoutRepository.getWorkoutStats(userId)
                ) { workouts, stats ->
                    HomeUiState(
                        recentWorkouts = workouts,
                        workoutStats = stats,
                        userName = authRepository.getCurrentUser()?.displayName,
                        isEmpty = workouts.isEmpty(),
                        isLoading = false
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.RefreshData -> refreshData()
            is HomeEvent.OpenWorkout -> {
                analyticsService.logEvent("home_workout_opened", mapOf("workout_id" to event.workoutId))
                // Navigation handled by screen
            }
            is HomeEvent.StartWorkout -> {
                analyticsService.logEvent("home_start_workout_clicked")
            }
        }
    }
}

data class HomeUiState(
    val recentWorkouts: List<Workout> = emptyList(),
    val workoutStats: WorkoutStats? = null,
    val userName: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEmpty: Boolean = false,
    val error: String? = null
)

sealed class HomeEvent {
    object RefreshData : HomeEvent()
    data class OpenWorkout(val workoutId: String) : HomeEvent()
    object StartWorkout : HomeEvent()
    object Retry : HomeEvent()
}
```

### Testing Strategy
**Test Scenarios:**
1. **Happy Path**: User with workout history sees recent workouts with correct data
2. **Empty State**: New user sees welcome message and start workout CTA
3. **Loading State**: Proper loading indicators during data fetch
4. **Error State**: Network error displays retry option
5. **Refresh**: Pull-to-refresh updates workout data
6. **Navigation**: Tapping workout opens detailed view
7. **Analytics**: Home screen interactions are properly tracked

## Implementation Plan

### Task Breakdown

#### Database Layer (DB-XXX)
- [ ] **DB-001**: Verify existing workout queries support home screen requirements [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/dao/WorkoutDao.kt`
  - **Details**: Confirm `getRecentCompletedWorkouts` query exists and performs well with LIMIT 7

#### Backend Services (BE-XXX)
- [ ] **BE-001**: Extend WorkoutRepository with home screen methods [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/repository/WorkoutRepository.kt`, `app/src/main/java/com/example/liftrix/data/repository/WorkoutRepositoryImpl.kt`
  - **Details**: Add `getRecentWorkouts()` and `getWorkoutStats()` methods following existing patterns

- [ ] **BE-002**: Create WorkoutStats domain model [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/model/WorkoutStats.kt`
  - **Details**: Define stats model with workout count, streak, weekly volume calculations

#### Frontend Components (FE-XXX)
- [ ] **FE-001**: Create HomeScreen composable [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/home/HomeScreen.kt`
  - **Details**: Implement main screen layout with LazyColumn, progress cards, and workout list

- [ ] **FE-002**: Build HomeViewModel with state management [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/home/HomeViewModel.kt`
  - **Details**: Implement ViewModel with StateFlow, event handling, and analytics integration

- [ ] **FE-003**: Create WorkoutSummaryCard component [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/home/components/WorkoutSummaryCard.kt`
  - **Details**: Build reusable card component showing workout title, date, duration, exercise count

- [ ] **FE-004**: Build ProgressInsightsCards component [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/home/components/ProgressInsightsCards.kt`
  - **Details**: Create cards showing workout streak, weekly stats, achievements

- [ ] **FE-005**: Implement EmptyState component [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/home/components/EmptyState.kt`
  - **Details**: Welcome screen for new users with start workout CTA

#### Integration (INT-XXX)
- [ ] **INT-001**: Integrate HomeScreen into main navigation [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/navigation/MainNavigationContainer.kt`
  - **Details**: Add HomeScreen to existing bottom navigation, set as default destination

- [ ] **INT-002**: Add home screen analytics events [Estimate: 30min]
  - **Files**: `app/src/main/java/com/example/liftrix/service/AnalyticsService.kt`
  - **Details**: Add tracking events for home screen interactions (workout opened, refresh, CTA clicks)

#### Testing (TEST-XXX)
- [ ] **TEST-001**: HomeViewModel unit tests [Estimate: 3hr]
  - **Files**: `app/src/test/java/com/example/liftrix/ui/home/HomeViewModelTest.kt`
  - **Details**: Test state management, data loading, error handling, and event processing

- [ ] **TEST-002**: HomeScreen UI tests [Estimate: 2hr]
  - **Files**: `app/src/test/java/com/example/liftrix/ui/home/HomeScreenTest.kt`
  - **Details**: Test rendering with different states, user interactions, and navigation

- [ ] **TEST-003**: WorkoutRepository integration tests [Estimate: 1hr]
  - **Files**: `app/src/test/java/com/example/liftrix/data/repository/WorkoutRepositoryImplTest.kt`
  - **Details**: Test new repository methods with mock data and database interactions

### Dependencies
- BE-001 depends on DB-001
- FE-002 depends on BE-001, BE-002
- FE-001 depends on FE-002, FE-003, FE-004, FE-005
- INT-001 depends on FE-001
- TEST-001 depends on BE-001, BE-002, FE-002
- TEST-002 depends on FE-001
- TEST-003 depends on BE-001

## Success Metrics
- **User Engagement**: 25% increase in session duration within first 30 days
- **Feature Adoption**: 90% of users interact with home screen within first week
- **Performance**: Home screen load time <500ms for 95th percentile users
- **Retention**: 15% reduction in day-1 drop-off rate for new users

## Timeline
**Total Effort**: 22.5 hours (approximately 3-4 developer days)
**Critical Path**: DB-001 → BE-001 → BE-002 → FE-002 → FE-001 → INT-001 (11.5 hours minimum)