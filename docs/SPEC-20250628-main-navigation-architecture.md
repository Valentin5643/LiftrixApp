# SPEC-20250628-main-navigation-architecture

## Executive Summary
**Feature**: Main Navigation Architecture Refactor
**Impact**: Transform Liftrix from single-flow workout creation to modern tab-based navigation, improving user experience and app structure for future feature expansion.
**Effort**: 3-4 developer-days
**Risk**: Low - Uses well-established Android navigation patterns with Material 3 components
**Dependencies**: None - self-contained refactor of existing MainActivity and navigation patterns

## Product Specifications

### Elevator Pitch
A comprehensive navigation overhaul that replaces the current single-flow workout creation with a modern bottom navigation system, providing users with dedicated spaces for workouts, progress tracking, and future AI coaching features.

### Target Users
- **Primary**: All Liftrix users, daily usage - provides the main app navigation framework
- **Secondary**: Future users discovering the app - improves onboarding and feature discoverability

### Core Goals
1. **User Experience**: Replace immediate workout creation flow with intuitive tab-based navigation
2. **Performance**: Maintain sub-200ms navigation transitions with smooth animations
3. **Scalability**: Create foundation for future features (AI coach, social features, advanced analytics)

### Functional Requirements
- **FR-001**: Main Navigation Container
  - **Given**: User is authenticated and launches the app
  - **When**: App loads after authentication
  - **Then**: User sees bottom navigation with 4 tabs: Home, Workout, Progress, Coach
  - **Acceptance**: Verified by UI test `test_main_navigation_displays_all_tabs`

- **FR-002**: Tab Navigation
  - **Given**: User is on any tab in the main navigation
  - **When**: User taps a different tab
  - **Then**: App navigates to selected tab with appropriate content and updates selected state
  - **Acceptance**: Verified by UI test `test_tab_navigation_updates_content_and_state`

- **FR-003**: Home Tab Content
  - **Given**: User is on Home tab
  - **When**: Tab content loads
  - **Then**: User sees recent workouts list with option for empty state
  - **Acceptance**: Verified by integration test `test_home_tab_shows_recent_workouts`

- **FR-004**: Authentication Integration
  - **Given**: Current authentication flow in MainActivity
  - **When**: User authentication state changes
  - **Then**: Navigation properly handles authenticated/unauthenticated states
  - **Acceptance**: Verified by integration test `test_auth_state_navigation_integration`

### User Stories
- **US-001**: As a returning user, I want to see my recent workouts immediately when I open the app so that I can quickly access my fitness progress.
  - **Acceptance Criteria**:
    1. Home tab displays last 5 completed workouts
    2. Each workout shows date, duration, and exercise count
    3. Tapping a workout shows full workout details
    4. Empty state shows motivational message and workout creation prompt

- **US-002**: As a user, I want to navigate between different app sections so that I can access workouts, track progress, and get coaching guidance.
  - **Acceptance Criteria**:
    1. Bottom navigation always visible and accessible
    2. Each tab maintains its own navigation stack
    3. Tab switching preserves scroll position and form state
    4. Visual feedback shows currently selected tab

### Non-Goals
- **Deep linking to specific tabs** - Reason: Deferred to V2 to focus on core navigation functionality
- **Tab customization or reordering** - Reason: Fixed tab order provides consistent user experience
- **Advanced tab animations beyond Material 3 defaults** - Reason: Standard animations sufficient for V1

## Technical Specifications

### System Architecture
- **Pattern**: Single-Activity architecture with Bottom Navigation using Navigation Compose
- **Flow**: MainActivity → MainNavigationContainer → Tab-specific NavGraphs → Feature screens
- **Security**: Maintains existing authentication flow, navigation gated by auth state

### Database Design
No database schema changes required - this is purely a UI/navigation refactor.

### API Specifications
No new API endpoints required - uses existing repository patterns for data loading.

### Component Design

#### MainNavigationContainer
```kotlin
@Composable
fun MainNavigationContainer(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainNavigationItem.entries.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { 
                            it.route == item.route 
                        } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = MainNavigationItem.HOME.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            homeGraph(onNavigateToAuth)
            workoutGraph(onNavigateToAuth)
            progressGraph(onNavigateToAuth)
            coachGraph(onNavigateToAuth)
        }
    }
}
```

#### Navigation Items Enum
```kotlin
enum class MainNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    HOME("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    WORKOUT("workout", "Workout", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    PROGRESS("progress", "Progress", Icons.Outlined.TrendingUp, Icons.Filled.TrendingUp),
    COACH("coach", "Coach", Icons.Outlined.Psychology, Icons.Filled.Psychology)
}
```

#### MainNavigationViewModel
```kotlin
@HiltViewModel
class MainNavigationViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    val authState: StateFlow<AuthenticationState> = authRepository.getAuthState()
        .map { user ->
            if (user != null) {
                AuthenticationState.Authenticated(user)
            } else {
                AuthenticationState.Unauthenticated
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthenticationState.Loading
        )
    
    sealed class AuthenticationState {
        object Loading : AuthenticationState()
        object Unauthenticated : AuthenticationState()
        data class Authenticated(val user: User) : AuthenticationState()
    }
}
```

### Testing Strategy
- **Test Scenarios**:
  1. "Verify navigation displays all 4 tabs with correct icons and labels"
  2. "Confirm tab selection updates content and visual state"
  3. "Validate authentication state properly gates navigation access"
  4. "Test navigation state preservation during configuration changes"
  5. "Verify each tab loads appropriate content and maintains independent navigation stacks"

## Implementation Plan

### Task Breakdown

#### UI Components (UI-XXX)
- [ ] **UI-001**: Create MainNavigationContainer composable [Estimate: 4hr]
  - **Files**: `ui/navigation/MainNavigationContainer.kt`
  - **Details**: Implement bottom navigation with Scaffold and NavHost integration

- [ ] **UI-002**: Create MainNavigationItem enum [Estimate: 1hr]
  - **Files**: `ui/navigation/MainNavigationItem.kt`
  - **Details**: Define navigation destinations with icons and routes

- [ ] **UI-003**: Implement navigation graphs for each tab [Estimate: 6hr]
  - **Files**: `ui/navigation/HomeNavGraph.kt`, `ui/navigation/WorkoutNavGraph.kt`, `ui/navigation/ProgressNavGraph.kt`, `ui/navigation/CoachNavGraph.kt`
  - **Details**: Create separate navigation graphs for each tab with appropriate start destinations

#### ViewModels (VM-XXX)
- [ ] **VM-001**: Create MainNavigationViewModel [Estimate: 2hr]
  - **Files**: `ui/navigation/MainNavigationViewModel.kt`
  - **Details**: Handle authentication state and navigation logic

#### Integration (INT-XXX)
- [ ] **INT-001**: Refactor MainActivity entry point [Estimate: 3hr]
  - **Files**: `MainActivity.kt`
  - **Details**: Replace WorkoutNavigation with MainNavigationContainer, maintain auth flow

- [ ] **INT-002**: Update dependency injection [Estimate: 1hr]
  - **Files**: `di/NavigationModule.kt` (new)
  - **Details**: Add DI bindings for new navigation components

#### Home Tab Implementation (HOME-XXX)
- [ ] **HOME-001**: Create HomeScreen composable [Estimate: 4hr]
  - **Files**: `ui/home/HomeScreen.kt`
  - **Details**: Implement recent workouts list with empty state handling

- [ ] **HOME-002**: Create HomeViewModel [Estimate: 3hr]
  - **Files**: `ui/home/HomeViewModel.kt`
  - **Details**: Load and manage recent workouts data with repository integration

#### Placeholder Implementations (PLACEHOLDER-XXX)
- [ ] **PLACEHOLDER-001**: Create ProgressScreen placeholder [Estimate: 1hr]
  - **Files**: `ui/progress/ProgressScreen.kt`
  - **Details**: Simple placeholder screen with "Coming Soon" message

- [ ] **PLACEHOLDER-002**: Create CoachScreen placeholder [Estimate: 1hr]
  - **Files**: `ui/coach/CoachScreen.kt`
  - **Details**: AI coaching placeholder with feature description

- [ ] **PLACEHOLDER-003**: Update WorkoutScreen for tab integration [Estimate: 2hr]
  - **Files**: `ui/workout/WorkoutScreen.kt`
  - **Details**: Adapt existing WorkoutScreen for tab navigation context

#### Testing (TEST-XXX)
- [ ] **TEST-001**: Navigation component tests [Estimate: 4hr]
  - **Files**: `ui/navigation/MainNavigationContainerTest.kt`
  - **Details**: Test navigation state, tab switching, and authentication integration

- [ ] **TEST-002**: HomeScreen integration tests [Estimate: 3hr]
  - **Files**: `ui/home/HomeScreenTest.kt`
  - **Details**: Test recent workouts loading, empty states, and user interactions

### Dependencies
- INT-001 depends on UI-001, UI-002, UI-003
- HOME-002 depends on HOME-001
- TEST-001 depends on UI-001, UI-002, UI-003, VM-001
- TEST-002 depends on HOME-001, HOME-002

## Success Metrics
- **Navigation Performance**: Tab switching completes within 200ms (measured via Android profiler)
- **User Experience**: 95% crash-free navigation sessions (measured via Firebase Crashlytics)
- **Feature Adoption**: 100% of users navigate between tabs within first session (measured via analytics)

## Timeline
**Total Effort**: 32 hours (4 developer-days)
**Critical Path**: UI-001 → UI-003 → INT-001 → Testing (minimum 2.5 days)