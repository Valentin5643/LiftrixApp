# SPEC-20250626-workout-ui-consolidation

## Executive Summary
**Feature**: Consolidate multiple workout creation UIs into single optimized flow with compact set input and exercise library integration  
**Impact**: Eliminate user confusion from multiple workout creation paths, improve development velocity through reduced code duplication, enhance UX consistency  
**Effort**: 6-8 developer days focused on UI component consolidation and navigation simplification  
**Risk**: Low - primarily UI refactoring with existing proven components  
**Dependencies**: SPEC-20250626-exercise-data-model-refactor must complete first for proper domain model integration  

## Product Specifications

### Elevator Pitch
A unified workout creation experience that consolidates the best features from multiple existing UIs into a single, intuitive flow optimized for rapid set input and exercise selection.

### Target Users
- **Primary**: All app users creating workouts (eliminates current UI confusion between 3 different creation flows)
- **Secondary**: Development team (reduced maintenance burden, cleaner codebase)

### Core Goals
1. **Simplification**: Replace 3 different workout creation UIs with 1 optimized flow
2. **Usability**: Prioritize rapid set input with minimal visual space per exercise
3. **Consistency**: Ensure all workout creation uses standardized exercise library
4. **Performance**: Maintain fast interaction times with efficient component architecture

### Functional Requirements

- **FR-001**: Single Workout Creation Entry Point
  - **Given**: User wants to create a workout
  - **When**: User taps any workout creation button
  - **Then**: Navigate to unified WorkoutCreationScreen
  - **Acceptance**: Verified by navigation test `test_single_entry_point`

- **FR-002**: Compact Exercise Display
  - **Given**: User has selected multiple exercises
  - **When**: Exercises are displayed in workout
  - **Then**: Each exercise uses minimal vertical space while showing exercise name, thumbnail, and inline set inputs
  - **Acceptance**: Verified by UI test `test_compact_exercise_layout`

- **FR-003**: Inline Set Management
  - **Given**: User wants to add sets to an exercise
  - **When**: User taps on exercise or "Add Set" button
  - **Then**: Set input fields appear inline without navigation to separate screen
  - **Acceptance**: Verified by interaction test `test_inline_set_input`

- **FR-004**: Clickable Exercise Names (Animation Hook)
  - **Given**: Exercise is displayed in workout
  - **When**: User taps on exercise name
  - **Then**: Exercise name responds to touch (prepared for future animation/details)
  - **Acceptance**: Verified by touch test `test_exercise_name_clickable`

- **FR-005**: Weight Field Conditional Display
  - **Given**: Exercise type determines weight support
  - **When**: Sets are displayed for exercise
  - **Then**: Weight field only appears for exercises that support weight tracking
  - **Acceptance**: Verified by conditional display test `test_weight_field_conditional`

### User Stories

- **US-001**: As a user, I want a single workout creation flow so that I don't get confused by multiple different interfaces
  - **Acceptance Criteria**:
    1. Only one "Create Workout" button exists
    2. All workout creation uses same UI components
    3. Navigation is consistent regardless of entry point
    4. User can create both "from scratch" and "from template" in same interface

- **US-002**: As a user logging multiple exercises, I want compact exercise cards so that I can see more exercises on screen without scrolling
  - **Acceptance Criteria**:
    1. Exercise card height <80dp when collapsed
    2. Multiple exercises visible on standard phone screen
    3. Exercise thumbnail clearly visible but small
    4. Exercise name readable and clickable

- **US-003**: As a user inputting sets, I want inline editing so that I can quickly add multiple sets without extra navigation
  - **Acceptance Criteria**:
    1. Set input appears when exercise is selected
    2. Can add multiple sets with single taps
    3. Can edit existing sets inline
    4. Can delete sets with swipe or button

### Non-Goals
- **Exercise thumbnails/images** - Use placeholders for now, image integration deferred
- **Advanced exercise animations** - Clickable names prepared for future, no animations in V1
- **Workout templates UI** - Focus on creation, template selection is separate feature
- **Set reordering** - Sets added in chronological order, reordering not required

## Technical Specifications

### System Architecture
- **Pattern**: Single Activity with Jetpack Compose navigation
- **State Management**: Unified ViewModel with comprehensive UI state
- **Component Design**: Reusable, composable UI components with clear responsibilities

### Component Hierarchy Design

#### Unified WorkoutCreationScreen
```kotlin
@Composable
fun WorkoutCreationScreen(
    state: WorkoutCreationUiState,
    onEvent: (WorkoutCreationEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item { WorkoutHeaderSection(state.workout, onEvent) }
        items(state.exercises) { exercise ->
            ExerciseCard(
                exercise = exercise,
                isExpanded = state.expandedExerciseId == exercise.id,
                onEvent = onEvent
            )
        }
        item { AddExerciseButton(onClick = { onEvent(ShowExerciseSelector) }) }
    }
    
    if (state.showExerciseSelector) {
        ExerciseSelector(
            exercises = state.availableExercises,
            onExerciseSelected = { onEvent(SelectExercise(it)) },
            onDismiss = { onEvent(HideExerciseSelector) }
        )
    }
}
```

#### Compact ExerciseCard Component
```kotlin
@Composable
fun ExerciseCard(
    exercise: SelectedExercise,
    isExpanded: Boolean,
    onEvent: (WorkoutCreationEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Compact exercise header (always visible)
            ExerciseHeader(
                exercise = exercise,
                isExpanded = isExpanded,
                onToggleExpand = { onEvent(ToggleExerciseExpansion(exercise.id)) },
                onNameClick = { onEvent(ExerciseNameClicked(exercise.id)) }
            )
            
            // Expandable set management section
            AnimatedVisibility(visible = isExpanded) {
                SetManagementSection(
                    sets = exercise.sets,
                    exerciseType = exercise.libraryExercise.type,
                    onEvent = onEvent
                )
            }
        }
    }
}
```

#### Inline Set Input Component
```kotlin
@Composable
fun SetInputRow(
    set: ExerciseSet,
    exerciseType: ExerciseType,
    onSetUpdated: (ExerciseSet) -> Unit,
    onDeleteSet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Set number
        Text(
            text = "${set.setNumber}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp)
        )
        
        // Reps input (always visible)
        SetInputField(
            value = set.reps?.toString() ?: "",
            onValueChange = { reps -> onSetUpdated(set.copy(reps = reps.toIntOrNull())) },
            label = "Reps",
            modifier = Modifier.weight(1f)
        )
        
        // Weight input (conditional)
        if (exerciseType.supportsWeight) {
            SetInputField(
                value = set.weight?.kilograms?.toString() ?: "",
                onValueChange = { weight -> 
                    onSetUpdated(set.copy(weight = weight.toFloatOrNull()?.let(::Weight)))
                },
                label = "Weight",
                modifier = Modifier.weight(1f)
            )
        }
        
        // RPE input (always available)
        SetInputField(
            value = set.rpe?.value?.toString() ?: "",
            onValueChange = { rpe -> onSetUpdated(set.copy(rpe = rpe.toIntOrNull()?.let(::RPE))) },
            label = "RPE",
            modifier = Modifier.width(60.dp)
        )
        
        // Delete button
        IconButton(onClick = onDeleteSet) {
            Icon(Icons.Default.Delete, contentDescription = "Delete set")
        }
    }
}
```

### State Management Design

#### Unified UI State
```kotlin
data class WorkoutCreationUiState(
    val workout: WorkoutDraft = WorkoutDraft.empty(),
    val exercises: List<SelectedExercise> = emptyList(),
    val expandedExerciseId: ExerciseId? = null,
    val showExerciseSelector: Boolean = false,
    val availableExercises: List<ExerciseLibrary> = emptyList(),
    val searchQuery: String = "",
    val selectedEquipment: Set<Equipment> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val canSave: Boolean = workout.name.isNotBlank() && exercises.isNotEmpty()
}
```

#### Event Handling
```kotlin
sealed class WorkoutCreationEvent {
    // Workout events
    data class UpdateWorkoutName(val name: String) : WorkoutCreationEvent()
    data class UpdateWorkoutDescription(val description: String) : WorkoutCreationEvent()
    
    // Exercise events
    object ShowExerciseSelector : WorkoutCreationEvent()
    object HideExerciseSelector : WorkoutCreationEvent()
    data class SelectExercise(val exercise: ExerciseLibrary) : WorkoutCreationEvent()
    data class RemoveExercise(val exerciseId: ExerciseId) : WorkoutCreationEvent()
    data class ToggleExerciseExpansion(val exerciseId: ExerciseId) : WorkoutCreationEvent()
    data class ExerciseNameClicked(val exerciseId: ExerciseId) : WorkoutCreationEvent()
    
    // Set events
    data class AddSet(val exerciseId: ExerciseId) : WorkoutCreationEvent()
    data class UpdateSet(val exerciseId: ExerciseId, val set: ExerciseSet) : WorkoutCreationEvent()
    data class DeleteSet(val exerciseId: ExerciseId, val setId: ExerciseSetId) : WorkoutCreationEvent()
    
    // Search events
    data class UpdateSearchQuery(val query: String) : WorkoutCreationEvent()
    data class ToggleEquipmentFilter(val equipment: Equipment) : WorkoutCreationEvent()
    
    // Action events
    object SaveWorkout : WorkoutCreationEvent()
    object DiscardWorkout : WorkoutCreationEvent()
}
```

### UI Component Specifications

#### Layout Requirements
- **Exercise Card Height**: Maximum 72dp when collapsed, expandable to show sets
- **Set Input Row Height**: 48dp for consistent touch targets
- **Exercise Thumbnail**: 40x40dp placeholder (prepared for future images)
- **Minimum Touch Targets**: 44x44dp for all interactive elements
- **Spacing**: 8dp between set inputs, 4dp between exercise cards

#### Accessibility Features
- **Semantic Content Description**: All interactive elements properly labeled
- **Screen Reader Support**: Logical reading order for exercise and set information
- **High Contrast**: Sufficient color contrast for all text elements
- **Large Text**: Scalable text that works with device accessibility settings

### Integration Specifications

#### Navigation Updates
```kotlin
// Remove multiple navigation destinations
// OLD: SimpleWorkoutCreationScreen, RedesignedWorkoutCreationScreen, CustomExerciseCreationScreen
// NEW: Single WorkoutCreationScreen

sealed class WorkoutDestination {
    object WorkoutList : WorkoutDestination()
    object WorkoutCreation : WorkoutDestination() // Single destination
    data class WorkoutDetails(val workoutId: WorkoutId) : WorkoutDestination()
}
```

#### Files to Remove/Consolidate
- **Remove**: `/ui/workout/simple/` directory (15+ files)
- **Remove**: `/ui/workout/creation/RedesignedWorkoutCreationScreen.kt`
- **Consolidate**: Merge best patterns from removed screens into unified screen
- **Update**: Navigation graphs to use single destination

## Implementation Plan

### Task Breakdown

#### UI Component Consolidation (UI-XXX)
- [ ] **UI-001**: Create unified WorkoutCreationScreen [Estimate: 8hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/WorkoutCreationScreen.kt`
  - **Details**: Single screen combining header form, exercise list, and inline set management

- [ ] **UI-002**: Implement compact ExerciseCard component [Estimate: 6hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/components/ExerciseCard.kt`
  - **Details**: Expandable card with exercise info and inline set management

- [ ] **UI-003**: Build SetInputRow with conditional fields [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/components/SetInputRow.kt`
  - **Details**: Inline set input with weight field conditional on exercise type

- [ ] **UI-004**: Create unified WorkoutCreationViewModel [Estimate: 6hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/WorkoutCreationViewModel.kt`
  - **Details**: Comprehensive state management replacing multiple ViewModels

- [ ] **UI-005**: Implement ExerciseHeader with clickable name [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/components/ExerciseHeader.kt`
  - **Details**: Compact header with thumbnail, name (clickable), and expand toggle

#### Navigation Consolidation (NAV-XXX)
- [ ] **NAV-001**: Update navigation graph [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/navigation/WorkoutNavigation.kt`
  - **Details**: Remove multiple workout creation destinations, use single route

- [ ] **NAV-002**: Update entry point routing [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/WorkoutScreen.kt`
  - **Details**: Route all workout creation buttons to unified screen

- [ ] **NAV-003**: Remove obsolete navigation destinations [Estimate: 1hr]
  - **Files**: Navigation configuration cleanup
  - **Details**: Clean removal of SimpleWorkout and RedesignedWorkout routes

#### File Cleanup (CLEAN-XXX)
- [ ] **CLEAN-001**: Remove SimpleWorkout UI components [Estimate: 2hr]
  - **Files**: Delete `/ui/workout/simple/` directory (15 files)
  - **Details**: Clean removal of SimpleWorkoutCreationScreen and related components

- [ ] **CLEAN-002**: Remove RedesignedWorkout UI components [Estimate: 2hr]
  - **Files**: Delete `RedesignedWorkoutCreationScreen.kt` and `RedesignedWorkoutCreationViewModel.kt`
  - **Details**: Remove redundant implementation, preserve useful patterns in unified screen

- [ ] **CLEAN-003**: Update dependency injection modules [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/di/ViewModelModule.kt`
  - **Details**: Remove obsolete ViewModel bindings, add unified ViewModel

#### Testing (TEST-XXX)
- [ ] **TEST-001**: UI component tests for unified screen [Estimate: 4hr]
  - **Files**: `app/src/androidTest/java/com/example/liftrix/ui/workout/creation/WorkoutCreationScreenTest.kt`
  - **Details**: Comprehensive UI testing for exercise selection, set input, and workout saving

- [ ] **TEST-002**: ViewModel unit tests [Estimate: 3hr]
  - **Files**: `app/src/test/java/com/example/liftrix/ui/workout/creation/WorkoutCreationViewModelTest.kt`
  - **Details**: Test state management, event handling, and business logic

- [ ] **TEST-003**: Navigation integration tests [Estimate: 2hr]
  - **Files**: `app/src/androidTest/java/com/example/liftrix/navigation/WorkoutNavigationTest.kt`
  - **Details**: Verify single entry point navigation and proper screen transitions

### Dependencies
- UI-004 (ViewModel) must complete before UI-001 (Screen) can be fully implemented
- NAV-001, NAV-002 depend on UI-001 completion (screen available for navigation)
- CLEAN-XXX tasks can run in parallel with other development (independent cleanup)
- All TEST tasks depend on their corresponding implementation tasks

## Success Metrics
- **Code Reduction**: >50% reduction in workout creation UI code
- **Navigation Simplicity**: Single entry point for all workout creation
- **User Task Completion**: <30 seconds average time for 3-exercise workout creation
- **Touch Target Accessibility**: 100% compliance with 44x44dp minimum touch targets
- **Performance**: Screen load time <300ms, set input response <100ms

## Timeline
**Total Effort**: 43 hours (6 developer days)  
**Critical Path**: UI-004 → UI-001 → NAV-001 (16 hours minimum)  
**Parallel Development**: Component creation (UI-002, UI-003) can run alongside ViewModel development