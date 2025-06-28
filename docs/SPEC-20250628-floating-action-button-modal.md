# SPEC-20250628-floating-action-button-modal

## Executive Summary
**Feature**: Floating Action Button with Workout Creation Modal
**Impact**: Centralize workout creation access with a prominent, always-accessible floating action button that presents users with two distinct workout creation paths.
**Effort**: 2-3 developer-days
**Risk**: Low - Uses existing BottomSheet patterns and workout creation flows
**Dependencies**: SPEC-20250628-main-navigation-architecture.md (navigation container), existing workout creation screens

## Product Specifications

### Elevator Pitch
A floating action button positioned above the bottom navigation provides instant access to workout creation with a modal presenting two clear options: quick template selection or freeform workout building.

### Target Users
- **Primary**: Active fitness users who start workouts multiple times per week
- **Secondary**: New users discovering workout creation capabilities

### Core Goals
1. **Accessibility**: Always-visible workout creation access from any tab
2. **Clarity**: Clear distinction between template-based and custom workout creation
3. **Performance**: Modal appears within 100ms of FAB tap, smooth animations

### Functional Requirements
- **FR-001**: Floating Action Button Display
  - **Given**: User is on any tab in the main navigation
  - **When**: Screen loads
  - **Then**: Circular "+" FAB appears slightly above bottom navigation, centered horizontally
  - **Acceptance**: Verified by UI test `test_fab_displays_on_all_tabs`

- **FR-002**: Modal Trigger
  - **Given**: FAB is visible
  - **When**: User taps the FAB
  - **Then**: Modal bottom sheet appears with two workout creation options
  - **Acceptance**: Verified by UI test `test_fab_opens_workout_creation_modal`

- **FR-003**: Template Workout Path
  - **Given**: Modal is open
  - **When**: User selects "Use a pre-made workout model"
  - **Then**: Modal closes and user navigates to workout template selection
  - **Acceptance**: Verified by integration test `test_template_workout_navigation`

- **FR-004**: Custom Workout Path
  - **Given**: Modal is open
  - **When**: User selects "Create a workout from scratch"
  - **Then**: Modal closes and active workout session begins with timer
  - **Acceptance**: Verified by integration test `test_custom_workout_creation_with_timer`

### User Stories
- **US-001**: As a user, I want quick access to workout creation from anywhere in the app so that I can start exercising without navigating through multiple screens.
  - **Acceptance Criteria**:
    1. FAB visible and accessible from all main navigation tabs
    2. Single tap opens workout creation options
    3. FAB positioned for easy thumb access on mobile devices
    4. FAB uses branded colors and smooth animations

- **US-002**: As a user, I want to choose between using an existing workout template or creating a custom workout so that I can match my current fitness goals and time constraints.
  - **Acceptance Criteria**:
    1. Modal clearly explains both options with descriptive text
    2. Template option shows quick preview of available templates
    3. Custom option indicates live timing will begin
    4. Modal can be dismissed by tapping outside or back gesture

### Non-Goals
- **FAB customization or positioning options** - Reason: Fixed positioning ensures consistent UX
- **Additional workout creation paths beyond the two specified** - Reason: Keeps modal simple and focused
- **Advanced FAB animations beyond Material 3 defaults** - Reason: Standard animations sufficient for V1

## Technical Specifications

### System Architecture
- **Pattern**: Floating Action Button with Modal Bottom Sheet, integrated with existing navigation
- **Flow**: Any Tab → FAB Tap → Modal → Template Selection OR Active Workout Creation
- **Security**: Maintains existing authentication requirements, no additional security considerations

### Database Design
No database schema changes required - leverages existing workout and template entities.

### API Specifications
No new API endpoints required - uses existing repository patterns:
- `WorkoutRepository.getAllTemplatesForUser()` for template selection
- `WorkoutRepository.createActiveWorkout()` for custom workout creation

### Component Design

#### WorkoutCreationFab
```kotlin
@Composable
fun WorkoutCreationFab(
    onCreateWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onCreateWorkout,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Start workout",
            modifier = Modifier.size(24.dp)
        )
    }
}
```

#### WorkoutCreationModal
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutCreationModal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onUseTemplate: () -> Unit,
    onCreateCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Start a Workout",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                WorkoutCreationOption(
                    title = "Use a pre-made workout model",
                    description = "Choose from your saved templates or community workouts",
                    icon = Icons.Default.Assignment,
                    onClick = onUseTemplate
                )
                
                WorkoutCreationOption(
                    title = "Create a workout from scratch",
                    description = "Start with an empty workout and add exercises as you go",
                    icon = Icons.Default.Create,
                    onClick = onCreateCustom
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WorkoutCreationOption(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
```

#### FAB Integration in MainNavigationContainer
```kotlin
@Composable
fun MainNavigationContainer(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainNavigationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                // Navigation items...
            }
        },
        floatingActionButton = {
            WorkoutCreationFab(
                onCreateWorkout = { viewModel.onEvent(MainNavigationEvent.ShowWorkoutModal) },
                modifier = Modifier.offset(y = (-80).dp) // Position above bottom nav
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = MainNavigationItem.HOME.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Navigation graphs...
        }
        
        WorkoutCreationModal(
            isVisible = uiState.isWorkoutModalVisible,
            onDismiss = { viewModel.onEvent(MainNavigationEvent.HideWorkoutModal) },
            onUseTemplate = { 
                viewModel.onEvent(MainNavigationEvent.NavigateToTemplates)
                navController.navigate("workout/templates")
            },
            onCreateCustom = {
                viewModel.onEvent(MainNavigationEvent.StartCustomWorkout)
                navController.navigate("workout/active")
            }
        )
    }
}
```

#### MainNavigationViewModel Updates
```kotlin
@HiltViewModel
class MainNavigationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainNavigationUiState())
    val uiState: StateFlow<MainNavigationUiState> = _uiState.asStateFlow()
    
    fun onEvent(event: MainNavigationEvent) {
        when (event) {
            MainNavigationEvent.ShowWorkoutModal -> {
                _uiState.update { it.copy(isWorkoutModalVisible = true) }
            }
            MainNavigationEvent.HideWorkoutModal -> {
                _uiState.update { it.copy(isWorkoutModalVisible = false) }
            }
            MainNavigationEvent.NavigateToTemplates -> {
                _uiState.update { it.copy(isWorkoutModalVisible = false) }
            }
            MainNavigationEvent.StartCustomWorkout -> {
                _uiState.update { it.copy(isWorkoutModalVisible = false) }
                startCustomWorkout()
            }
        }
    }
    
    private fun startCustomWorkout() {
        viewModelScope.launch {
            try {
                workoutRepository.createActiveWorkout()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

data class MainNavigationUiState(
    val isWorkoutModalVisible: Boolean = false,
    val authState: AuthenticationState = AuthenticationState.Loading
)

sealed class MainNavigationEvent {
    object ShowWorkoutModal : MainNavigationEvent()
    object HideWorkoutModal : MainNavigationEvent()
    object NavigateToTemplates : MainNavigationEvent()
    object StartCustomWorkout : MainNavigationEvent()
}
```

### Testing Strategy
- **Test Scenarios**:
  1. "Verify FAB appears on all navigation tabs with correct positioning"
  2. "Confirm FAB tap opens modal with both workout creation options"
  3. "Validate template selection navigates to template screen and closes modal"
  4. "Test custom workout creation starts active workout session with timer"
  5. "Verify modal can be dismissed via outside tap or back gesture"

## Implementation Plan

### Task Breakdown

#### UI Components (UI-XXX)
- [ ] **UI-001**: Create WorkoutCreationFab component [Estimate: 2hr]
  - **Files**: `ui/components/WorkoutCreationFab.kt`
  - **Details**: Implement Material 3 FAB with proper styling and positioning

- [ ] **UI-002**: Create WorkoutCreationModal component [Estimate: 4hr]
  - **Files**: `ui/components/WorkoutCreationModal.kt`
  - **Details**: Implement modal bottom sheet with two workout creation options

- [ ] **UI-003**: Create WorkoutCreationOption component [Estimate: 2hr]
  - **Files**: `ui/components/WorkoutCreationOption.kt` (or inline in modal)
  - **Details**: Reusable card component for modal options

#### Integration (INT-XXX)
- [ ] **INT-001**: Integrate FAB into MainNavigationContainer [Estimate: 3hr]
  - **Files**: `ui/navigation/MainNavigationContainer.kt`
  - **Details**: Add FAB to Scaffold with proper positioning and modal state management

- [ ] **INT-002**: Update MainNavigationViewModel [Estimate: 3hr]
  - **Files**: `ui/navigation/MainNavigationViewModel.kt`
  - **Details**: Add modal state management and workout creation event handling

- [ ] **INT-003**: Replace existing QuickWorkoutFab usage [Estimate: 2hr]
  - **Files**: Multiple workout screens
  - **Details**: Remove old FAB implementations and update navigation flows

#### Navigation (NAV-XXX)
- [ ] **NAV-001**: Create workout template selection screen [Estimate: 4hr]
  - **Files**: `ui/workout/templates/WorkoutTemplateSelectionScreen.kt`
  - **Details**: Screen for browsing and selecting workout templates

- [ ] **NAV-002**: Create active workout screen foundation [Estimate: 4hr]
  - **Files**: `ui/workout/active/ActiveWorkoutScreen.kt`
  - **Details**: Empty workout screen with timer integration (depends on timer service spec)

#### Testing (TEST-XXX)
- [ ] **TEST-001**: FAB component tests [Estimate: 2hr]
  - **Files**: `ui/components/WorkoutCreationFabTest.kt`
  - **Details**: Test FAB appearance, click behavior, and styling

- [ ] **TEST-002**: Modal component tests [Estimate: 3hr]
  - **Files**: `ui/components/WorkoutCreationModalTest.kt`
  - **Details**: Test modal visibility, option selection, and dismissal

- [ ] **TEST-003**: Navigation integration tests [Estimate: 4hr]
  - **Files**: `ui/navigation/MainNavigationIntegrationTest.kt`
  - **Details**: Test complete flow from FAB tap to workout creation

### Dependencies
- INT-001 depends on UI-001, UI-002
- INT-002 depends on INT-001
- NAV-001 depends on INT-001, INT-002
- NAV-002 depends on timer service implementation (SPEC-20250628-timer-service-integration)
- TEST-003 depends on INT-001, INT-002, NAV-001

## Success Metrics
- **Performance**: Modal opens within 100ms of FAB tap (measured via frame metrics)
- **User Engagement**: 80% of workout sessions started via FAB within first month (measured via analytics)
- **Task Completion**: 95% of users successfully navigate to workout creation from modal (measured via user flow analytics)

## Timeline
**Total Effort**: 24 hours (3 developer-days)
**Critical Path**: UI-001 → UI-002 → INT-001 → INT-002 → Testing (minimum 2 days)