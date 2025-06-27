# SPEC-20250625-workout-display-screen

## Executive Summary
**Feature**: Simple Workout Results Display Screen
**Impact**: Provide immediate feedback and satisfaction to users after creating their workout, with clear presentation of workout name, description, and exercise details to encourage continued app usage.
**Effort**: 2-3 developer days
**Risk**: Low - straightforward UI implementation following established display patterns
**Dependencies**: SPEC-20250625-simple-workout-creation.md (workout creation flow)

## Product Specifications

### Elevator Pitch
Create a dedicated results screen that displays the user's newly created workout in a clean, organized format that clearly shows the workout name, description, and all exercise details, providing immediate gratification and a foundation for future workout management features.

### Target Users
- **Primary**: Users who have just completed workout creation (immediate usage)
- **Secondary**: Users reviewing their workout library (weekly usage)

### Core Goals
1. **User Satisfaction**: Provide immediate visual confirmation of successful workout creation
2. **Information Clarity**: Display all workout data in an easily scannable, hierarchical format
3. **Performance**: Screen loads in <200ms with smooth animations
4. **Navigation**: Clear path forward to either create another workout or return to main screen

### Functional Requirements

- **FR-001**: Display workout header information
  - **Given**: User has successfully created a workout and navigated to results screen
  - **When**: Screen loads with workout data
  - **Then**: Display workout name prominently, description below, creation timestamp
  - **Acceptance**: Verified by UI test `test_workout_header_display`

- **FR-002**: Display exercise list with complete details
  - **Given**: Workout contains multiple exercises with tracking data
  - **When**: User views the results screen
  - **Then**: Show each exercise as a card with name, sets, reps, weight, RPE in organized layout
  - **Acceptance**: Verified by UI test `test_exercise_details_display`

- **FR-003**: Provide navigation actions
  - **Given**: User is viewing workout results
  - **When**: User wants to take next action
  - **Then**: Provide "Create Another Workout" and "Back to Workouts" action buttons
  - **Acceptance**: Verified by UI test `test_results_navigation_actions`

- **FR-004**: Handle empty states gracefully
  - **Given**: Workout has minimal data (no description, no RPE values)
  - **When**: Results screen displays
  - **Then**: Show available data cleanly without empty sections or placeholder text
  - **Acceptance**: Verified by unit test `test_empty_state_handling`

- **FR-005**: Support accessibility requirements
  - **Given**: User is using screen reader or accessibility tools
  - **When**: Navigating the results screen
  - **Then**: All content has semantic descriptions and proper navigation structure
  - **Acceptance**: Verified by accessibility test `test_results_accessibility`

### User Stories

- **US-001**: As a user who just created my first workout, I want to see my workout displayed clearly so that I can confirm what I created and feel accomplished.
  - **Acceptance Criteria**:
    1. I can see my workout name displayed prominently at the top
    2. My workout description appears below the name (if I entered one)
    3. All my exercises are listed with their details clearly visible
    4. The screen loads quickly and feels responsive
    5. I feel confident that my workout was saved correctly

- **US-002**: As a user viewing my workout results, I want clear next steps so that I can continue using the app productively.
  - **Acceptance Criteria**:
    1. I can easily create another workout without starting over
    2. I can return to the main workout screen to see my workouts
    3. The action buttons are clearly labeled and easily accessible
    4. The navigation feels natural and intuitive

### Non-Goals
- **Workout editing capabilities** - **Reason**: Results screen is for display/confirmation only, editing deferred to future iteration
- **Social sharing features** - **Reason**: Focus on core display functionality first
- **Workout analytics/stats** - **Reason**: Simple results display without advanced metrics

## Technical Specifications

### System Architecture
- **Pattern**: Jetpack Compose screen with ViewModel for state management
- **Flow**: WorkoutCreation → Results Screen → Navigation to WorkoutList or NewWorkout
- **Security**: Display user-scoped data only, no data modification capabilities

### Component Design

**UI State Management:**

```kotlin
data class WorkoutResultsUiState(
    val workout: SimpleWorkout? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val isDataAvailable: Boolean get() = workout != null && !isLoading
}

sealed class WorkoutResultsEvent {
    object CreateAnotherWorkout : WorkoutResultsEvent()
    object BackToWorkouts : WorkoutResultsEvent()
    object RetryLoading : WorkoutResultsEvent()
}
```

**Screen Components:**

```kotlin
@Composable
fun SimpleWorkoutResultsScreen(
    workoutId: String,
    onCreateAnother: () -> Unit,
    onBackToWorkouts: () -> Unit,
    viewModel: WorkoutResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }
    
    Scaffold(
        topBar = {
            WorkoutResultsTopBar(
                onBackClick = onBackToWorkouts
            )
        },
        bottomBar = {
            WorkoutResultsBottomBar(
                onCreateAnother = onCreateAnother,
                onBackToWorkouts = onBackToWorkouts
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingContent()
            uiState.errorMessage != null -> ErrorContent(
                message = uiState.errorMessage,
                onRetry = { viewModel.onEvent(WorkoutResultsEvent.RetryLoading) }
            )
            uiState.isDataAvailable -> WorkoutResultsContent(
                workout = uiState.workout!!,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
```

**Workout Display Components:**

```kotlin
@Composable
private fun WorkoutResultsContent(
    workout: SimpleWorkout,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WorkoutHeaderCard(workout = workout)
        }
        
        item {
            ExercisesSectionHeader(exerciseCount = workout.exercises.size)
        }
        
        items(
            items = workout.exercises,
            key = { it.id.value }
        ) { exercise ->
            ExerciseResultCard(exercise = exercise)
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp)) // Bottom bar padding
        }
    }
}

@Composable
private fun WorkoutHeaderCard(
    workout: SimpleWorkout,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { 
                contentDescription = "Workout: ${workout.name}"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Workout Created!",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = workout.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            if (!workout.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = workout.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Created ${workout.createdAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ExerciseResultCard(
    exercise: SimpleExercise,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Exercise: ${exercise.name}, ${exercise.sets} sets, ${exercise.reps} reps"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ExerciseStatChip(
                    label = "Sets",
                    value = exercise.sets.toString(),
                    icon = Icons.Default.FitnessCenter
                )
                
                ExerciseStatChip(
                    label = "Reps",
                    value = exercise.reps.toString(),
                    icon = Icons.Default.Repeat
                )
                
                if (exercise.weightKg != null) {
                    ExerciseStatChip(
                        label = "Weight",
                        value = "${exercise.weightKg} kg",
                        icon = Icons.Default.Scale
                    )
                }
                
                if (exercise.rpe != null) {
                    ExerciseStatChip(
                        label = "RPE",
                        value = exercise.rpe.toString(),
                        icon = Icons.Default.Speed
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseStatChip(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.semantics {
            contentDescription = "$label: $value"
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
```

**Navigation Components:**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutResultsTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "Workout Created",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.semantics {
                    contentDescription = "Back to workouts"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun WorkoutResultsBottomBar(
    onCreateAnother: () -> Unit,
    onBackToWorkouts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBackToWorkouts,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Back to workouts list"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Workouts")
            }
            
            Button(
                onClick = onCreateAnother,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = "Create another workout"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Another")
            }
        }
    }
}
```

### Testing Strategy

**Test Scenarios:**
1. **Data Display**: Verify all workout and exercise data renders correctly
2. **Empty States**: Test handling of missing optional data (description, RPE, weight)
3. **Loading States**: Verify loading and error state handling
4. **Navigation**: Test all navigation actions work correctly
5. **Accessibility**: Screen reader compatibility and keyboard navigation
6. **Performance**: Screen load time and animation smoothness

## Implementation Plan

### Task Breakdown

#### Frontend Components (FE-001 to FE-004)
- [ ] **FE-001**: Results screen ViewModel and state management [Estimate: 3hr]
  - **Files**: `ui/workout/results/WorkoutResultsViewModel.kt`
  - **Details**: Load workout data, handle loading/error states, navigation events

- [ ] **FE-002**: Main results screen layout [Estimate: 4hr]
  - **Files**: `ui/workout/results/SimpleWorkoutResultsScreen.kt`
  - **Details**: Scaffold setup, screen orchestration, state handling

- [ ] **FE-003**: Workout display components [Estimate: 5hr]
  - **Files**: `ui/workout/results/components/WorkoutHeaderCard.kt`, `ExerciseResultCard.kt`
  - **Details**: Workout header, exercise cards, stat chips, Material 3 styling

- [ ] **FE-004**: Navigation integration [Estimate: 2hr]
  - **Files**: Update `ui/workout/WorkoutScreen.kt`, navigation routes
  - **Details**: Route to results screen, back navigation, action handling

#### Testing (TEST-001)
- [ ] **TEST-001**: UI and accessibility testing [Estimate: 3hr]
  - **Files**: `androidTest/ui/workout/results/`
  - **Details**: Component tests, navigation tests, accessibility compliance

### Dependencies
- **FE-001 depends on**: Simple workout domain models from creation spec
- **FE-002 depends on FE-001**: Screen needs ViewModel
- **FE-003 depends on FE-001**: Components need state data
- **FE-004 depends on FE-002**: Navigation needs screen implementation
- **TEST-001 depends on FE-002, FE-003**: Tests need complete implementation

## Success Metrics
- **User Satisfaction**: 90%+ of users who view results screen take a follow-up action (create another, view workouts)
- **Performance**: Results screen loads in <200ms, smooth 60fps animations
- **Accessibility**: 100% compliance with WCAG 2.1 AA standards
- **User Flow**: <5% drop-off rate between workout creation and results viewing

## Timeline
**Total Effort**: 17 hours (2-3 developer days)
**Critical Path**: FE-001 → FE-002 → FE-003 → FE-004 → TEST-001 (17 hours)