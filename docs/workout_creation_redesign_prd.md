# Product Requirements Document: Workout Creation Screen Redesign

## Elevator Pitch
Redesign the workout creation screen to streamline exercise selection and set tracking by replacing free-text input with a structured exercise selector and inline set management, reducing workout creation time by 40% while improving data accuracy.

## Target Users
- **Primary User**: Fitness enthusiasts using the Liftrix app for workout tracking
- **Use Cases**: Creating custom workouts with predefined exercises, tracking multiple sets with reps/RPE/weight data
- **Pain Points**: Current two-step form creates friction; free-text exercise input leads to inconsistent data and typos

## Core Goals (3-5 Measurable Outcomes)
- **Goal 1**: Reduce workout creation time from 5+ minutes to under 3 minutes
- **Goal 2**: Increase exercise data consistency by 95% through predefined exercise selection
- **Goal 3**: Improve user retention in workout creation flow by 25%
- **Goal 4**: Enable structured set tracking with <200ms response time per set addition

## Functional Requirements with Acceptance Criteria

### FR-001: Single-Screen Layout
- Workout name and description fields positioned at top of screen
- Exercise selection and set input displayed inline below workout details
- Vertical scrolling layout with maximum 44dp touch targets
- Form validation with real-time error feedback

### FR-002: Exercise Selection from Library
- Replace free-text input with searchable exercise selector
- Display exercise thumbnail image and clickable name
- Filter exercises from existing exercise_library.json asset
- Support exercise search by name and muscle group

### FR-003: Structured Set Input
- Each selected exercise displays multiple set input rows
- Each set contains: reps (required), RPE (optional), weight (conditional)
- Weight field only shown for exercises that support it (exclude bodyweight exercises)
- Compact, vertically stackable set components

### FR-004: Weight Field Logic
- Hide weight input for BODYWEIGHT_ONLY equipment exercises (Push-ups, Pull-ups, etc.)
- Show weight input for BARBELL, DUMBBELLS, CABLE_MACHINE equipment
- Validate weight values (0-999.9 kg range)

## User Specs

### User Flow
1. User opens workout creation screen
2. Enters workout name and description at top
3. Taps "Add Exercise" to open exercise selector
4. Searches/selects exercise from predefined list
5. Exercise appears with thumbnail and name
6. User adds sets by inputting reps, RPE, and weight (if applicable)
7. Repeats for additional exercises
8. Taps "Save Workout" to create workout

### UI/UX Principles
- Material 3 design system with primary color scheme
- Minimize visual complexity while maintaining usability
- Prioritize speed of input over visual flair
- Progressive disclosure for set input
- Clear visual hierarchy with proper spacing

### Layout Structure
```
[Workout Name Input]
[Description Input]
[Exercise 1 with thumbnail]
  [Set 1: Reps | RPE | Weight]
  [Set 2: Reps | RPE | Weight]
  [+ Add Set]
[Exercise 2 with thumbnail]
  [Sets...]
[+ Add Exercise]
[Save Workout Button]
```

### Core Components
- **WorkoutHeaderForm**: Name and description inputs
- **ExerciseSelector**: Searchable exercise library picker
- **ExerciseCard**: Exercise display with thumbnail and name
- **SetInputRow**: Compact set input with reps/RPE/weight fields
- **AddExerciseButton**: Primary CTA for exercise selection

### Visual Design Elements
- 4dp grid system spacing
- Material 3 color tokens (primary, surface, outline)
- Typography scale using Material 3 type system
- 56dp floating action button for primary actions
- Card elevation for exercise grouping

## Technical Specifications

### System Architecture
- Clean Architecture: UI → ViewModel → UseCase → Repository
- MVI pattern for ViewModel state management
- Single Activity with Compose navigation
- Hilt dependency injection

### Frontend Tech
- Jetpack Compose UI with Material 3
- StateFlow for reactive state management
- Navigation Component for screen transitions
- Coil for exercise thumbnail loading

### Database, Backend and APIs
- Room database for local storage
- Firebase Firestore for cloud sync
- Exercise library loaded from JSON asset
- Repository pattern for data abstraction

### Data Flow
```
User Input → ViewModel Events → State Updates → UI Recomposition
Exercise Library → Repository → UseCase → ViewModel → UI
Set Data → Validation → State → Database → Cloud Sync
```

### Technology Stack
- **Language**: Kotlin 1.9.0
- **Framework**: Jetpack Compose 1.5.0
- **Database**: Room 2.5.0 with Firestore sync
- **DI**: Hilt 2.47

### Implementation Approach

#### File Structure
```
ui/workout/creation/
├── RedesignedWorkoutCreationScreen.kt
├── RedesignedWorkoutCreationViewModel.kt
├── components/
│   ├── WorkoutHeaderForm.kt
│   ├── ExerciseSelector.kt
│   ├── ExerciseCard.kt
│   ├── SetInputRow.kt
│   └── AddExerciseButton.kt
└── model/
    ├── WorkoutCreationState.kt
    ├── SelectedExercise.kt
    └── SetInput.kt
```

#### Data Model Changes
```kotlin
data class SelectedExercise(
    val libraryExercise: ExerciseLibraryItem,
    val sets: List<SetInput>,
    val orderIndex: Int
)

data class SetInput(
    val reps: String = "",
    val rpe: String = "",
    val weight: String = "",
    val isWeightSupported: Boolean = true
)

data class WorkoutCreationState(
    val workoutName: String = "",
    val workoutDescription: String = "",
    val selectedExercises: List<SelectedExercise> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
```

### Quality Standards
- **Performance**: <200ms response time for set additions
- **Accessibility**: Full TalkBack support with content descriptions
- **Reliability**: Form validation prevents invalid data submission
- **Testing**: 90% code coverage with unit and UI tests

## Validation Gates
- ✅ Single-screen layout with name/description at top
- ✅ Exercise selection from predefined library
- ✅ Structured set input with conditional weight fields
- ✅ Material 3 design compliance
- ✅ MVI pattern implementation
- ✅ Accessibility standards met
- ✅ Performance benchmarks achieved

## Success Metrics
- Workout creation completion time: <3 minutes target
- Form abandonment rate: <15% target
- Exercise data consistency: >95% accuracy
- User satisfaction score: >4.2/5.0 