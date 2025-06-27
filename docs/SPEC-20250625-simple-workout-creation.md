# SPEC-20250625-simple-workout-creation

## Executive Summary
**Feature**: Simple Workout Creation Flow
**Impact**: Enable users to create their first workout through an intuitive guided flow, increasing user engagement and establishing the foundation for AI-powered workout recommendations.
**Effort**: 5-7 developer days
**Risk**: Low - follows established UI patterns and creates new simple data model alongside existing complex system
**Dependencies**: None - self-contained feature using existing infrastructure

## Product Specifications

### Elevator Pitch
Transform the non-functional "Create your First Workout" button into a complete workout creation experience that guides users through building a simple workout with exercises, then displays their creation in a dedicated results screen.

### Target Users
- **Primary**: New Liftrix users creating their first workout (daily usage expected)
- **Secondary**: Existing users wanting to quickly log simple workouts (weekly usage)

### Core Goals
1. **User Experience**: Reduce friction for new users to create their first workout from 0% success rate to 80%+ completion rate
2. **Performance**: Form submission and navigation < 500ms, results screen load < 200ms
3. **Scale**: Support up to 20 exercises per workout, 50 sets per exercise

### Functional Requirements

- **FR-001**: Replace "Create your First Workout" button with functional workout creation flow
  - **Given**: User is on empty WorkoutScreen with "Create your First Workout" button visible
  - **When**: User taps the "Create Workout Template" button
  - **Then**: Navigate to workout creation form instead of placeholder screen
  - **Acceptance**: Verified by UI test `test_workout_creation_navigation`

- **FR-002**: Workout creation form with name and description
  - **Given**: User is on workout creation screen
  - **When**: User enters workout name (required, max 100 chars) and description (optional, max 500 chars)
  - **Then**: Form validates inputs with real-time feedback and enables "Next" button when valid
  - **Acceptance**: Verified by unit test `test_workout_form_validation`

- **FR-003**: Exercise addition with tracking fields
  - **Given**: User has completed workout details and navigated to exercise section
  - **When**: User adds exercises with name, reps (1-999), RPE (1-10), sets (1-50), weight (0-999.9 kg)
  - **Then**: Each exercise is added to workout with proper validation and ordering
  - **Acceptance**: Verified by integration test `test_exercise_addition_flow`

- **FR-004**: Workout creation persistence
  - **Given**: User has completed workout creation with valid data
  - **When**: User taps "Save Workout" button
  - **Then**: Workout is saved to local database, queued for sync, and user navigates to results screen
  - **Acceptance**: Verified by integration test `test_workout_save_and_sync`

- **FR-005**: Results screen display
  - **Given**: User has successfully created a workout
  - **When**: User is navigated to results screen
  - **Then**: Display workout name, description, exercise list with all tracking data clearly visible
  - **Acceptance**: Verified by UI test `test_workout_results_display`

### User Stories

- **US-001**: As a new user, I want to create my first workout so that I can start tracking my fitness journey.
  - **Acceptance Criteria**:
    1. I can tap "Create your First Workout" and see a form instead of placeholder
    2. I can enter a workout name and optional description with validation feedback
    3. I can add multiple exercises with reps, sets, weight, and RPE tracking
    4. I can save my workout and see it displayed clearly on a results screen
    5. The entire flow completes in under 2 minutes for a 3-exercise workout

- **US-002**: As a user, I want immediate feedback when filling out the workout form so that I can correct errors quickly.
  - **Acceptance Criteria**:
    1. Field validation happens in real-time with visual feedback
    2. Error messages are clear and actionable (e.g., "Name must be 3-100 characters")
    3. Successfully validated fields show checkmark indicators
    4. Save button is disabled until all required fields are valid

### Non-Goals
- **Advanced workout features** (supersets, rest timers, progress photos) - **Reason**: Keeping initial implementation simple to validate core workflow
- **Workout template library** - **Reason**: Deferred to V2 to focus on creation flow
- **Social sharing features** - **Reason**: Not in scope for core workout creation functionality

## Technical Specifications

### System Architecture
- **Pattern**: Clean Architecture with simplified data models alongside existing complex workout system
- **Flow**: UI → ViewModel → Use Case → Repository → Room Database → Firebase Sync
- **Security**: User-scoped data access with Firebase Authentication integration

### Database Design

**New Entities for Simple Workout Flow:**

```sql
-- Simple workout container
CREATE TABLE simple_workouts (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK(length(name) >= 3 AND length(name) <= 100),
    description TEXT CHECK(length(description) <= 500),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    is_synced INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0
);

-- Exercise within simple workout
CREATE TABLE simple_exercises (
    id TEXT PRIMARY KEY NOT NULL,
    workout_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK(length(name) >= 2 AND length(name) <= 100),
    reps INTEGER NOT NULL CHECK(reps >= 1 AND reps <= 999),
    rpe INTEGER CHECK(rpe >= 1 AND rpe <= 10),
    sets INTEGER NOT NULL CHECK(sets >= 1 AND sets <= 50),
    weight_kg REAL CHECK(weight_kg >= 0 AND weight_kg <= 999.9),
    order_index INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_simple_workouts_user_date ON simple_workouts(user_id, created_at);
CREATE INDEX idx_simple_exercises_workout ON simple_exercises(workout_id, order_index);
```

**Migration Approach:**
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create simple workout tables
        database.execSQL(CREATE_SIMPLE_WORKOUTS_TABLE)
        database.execSQL(CREATE_SIMPLE_EXERCISES_TABLE)
        database.execSQL(CREATE_SIMPLE_WORKOUTS_INDEX)
        database.execSQL(CREATE_SIMPLE_EXERCISES_INDEX)
    }
}
```

### API Specifications

**Domain Models:**

```kotlin
data class SimpleWorkout(
    val id: SimpleWorkoutId,
    val userId: String,
    val name: String,
    val description: String?,
    val exercises: List<SimpleExercise>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.length in 3..100) { "Name must be 3-100 characters" }
        require(description?.length ?: 0 <= 500) { "Description cannot exceed 500 characters" }
        require(exercises.size <= 20) { "Cannot exceed 20 exercises per workout" }
    }
}

data class SimpleExercise(
    val id: SimpleExerciseId,
    val name: String,
    val reps: Int,
    val rpe: Int?,
    val sets: Int,
    val weightKg: Double?,
    val orderIndex: Int
) {
    init {
        require(name.length in 2..100) { "Exercise name must be 2-100 characters" }
        require(reps in 1..999) { "Reps must be 1-999" }
        require(rpe == null || rpe in 1..10) { "RPE must be 1-10 if specified" }
        require(sets in 1..50) { "Sets must be 1-50" }
        require(weightKg == null || weightKg >= 0) { "Weight must be non-negative" }
    }
}
```

### Component Design

**Service Components:**

```kotlin
// Use Case for workout creation
class CreateSimpleWorkoutUseCase @Inject constructor(
    private val simpleWorkoutRepository: SimpleWorkoutRepository,
    private val authRepository: AuthRepository,
    private val analyticsService: AnalyticsService
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        exercises: List<SimpleExerciseInput>
    ): Result<SimpleWorkout> {
        return try {
            val userId = authRepository.getCurrentUserId()
                ?: return Result.failure(CreateWorkoutException.UserNotAuthenticated)
            
            val workout = SimpleWorkout(
                id = SimpleWorkoutId.generate(),
                userId = userId,
                name = name.trim(),
                description = description?.trim(),
                exercises = exercises.mapIndexed { index, input ->
                    SimpleExercise(
                        id = SimpleExerciseId.generate(),
                        name = input.name.trim(),
                        reps = input.reps,
                        rpe = input.rpe,
                        sets = input.sets,
                        weightKg = input.weightKg,
                        orderIndex = index
                    )
                },
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            simpleWorkoutRepository.createWorkout(workout)
                .onSuccess { 
                    analyticsService.logWorkoutCreated(workout)
                }
        } catch (e: Exception) {
            Timber.e(e, "Error creating simple workout")
            Result.failure(CreateWorkoutException.UnknownError(e))
        }
    }
}
```

**UI Components:**

```kotlin
// Main workout creation screen
@Composable
fun SimpleWorkoutCreationScreen(
    onNavigateBack: () -> Unit,
    onWorkoutCreated: (SimpleWorkout) -> Unit,
    viewModel: SimpleWorkoutCreationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when (uiState.currentStep) {
        WorkoutCreationStep.Details -> {
            WorkoutDetailsForm(
                name = uiState.workoutName,
                description = uiState.workoutDescription,
                onNameChange = { viewModel.onEvent(UpdateWorkoutName(it)) },
                onDescriptionChange = { viewModel.onEvent(UpdateWorkoutDescription(it)) },
                onNext = { viewModel.onEvent(ProceedToExercises) }
            )
        }
        WorkoutCreationStep.Exercises -> {
            ExerciseListForm(
                exercises = uiState.exercises,
                onAddExercise = { viewModel.onEvent(AddExercise) },
                onUpdateExercise = { index, exercise -> 
                    viewModel.onEvent(UpdateExercise(index, exercise)) 
                },
                onSaveWorkout = { viewModel.onEvent(SaveWorkout) }
            )
        }
    }
}
```

### Testing Strategy

**Test Scenarios:**
1. **Happy Path Flow**: User creates workout with name, description, and 3 exercises successfully
2. **Form Validation**: Test all input validation rules with edge cases and boundary conditions  
3. **Error Handling**: Network failures, database errors, authentication failures
4. **Accessibility**: Screen reader navigation, semantic descriptions, keyboard navigation
5. **Performance**: Form responsiveness, save operation timing, navigation speed

## Implementation Plan

### Task Breakdown

#### Database Layer (DB-001 to DB-003)
- [ ] **DB-001**: Create simple workout entity and DAO [Estimate: 4hr]
  - **Files**: `data/local/entity/SimpleWorkoutEntity.kt`, `data/local/dao/SimpleWorkoutDao.kt`
  - **Details**: Room entity with proper constraints, DAO with user-scoped queries, migration script

- [ ] **DB-002**: Create simple exercise entity and relationships [Estimate: 3hr]
  - **Files**: `data/local/entity/SimpleExerciseEntity.kt`, update `SimpleWorkoutDao.kt`
  - **Details**: Foreign key relationship, composite queries for workout with exercises

- [ ] **DB-003**: Database migration and testing [Estimate: 2hr]
  - **Files**: `data/local/migration/Migration_5_to_6.kt`, integration tests
  - **Details**: Migration script, database testing, rollback validation

#### Backend Services (BE-001 to BE-004)
- [ ] **BE-001**: Domain models and value objects [Estimate: 3hr]
  - **Files**: `domain/model/SimpleWorkout.kt`, `domain/model/SimpleExercise.kt`
  - **Details**: Domain models with validation, value objects for IDs, business methods

- [ ] **BE-002**: Repository interface and implementation [Estimate: 4hr]
  - **Files**: `domain/repository/SimpleWorkoutRepository.kt`, `data/repository/SimpleWorkoutRepositoryImpl.kt`
  - **Details**: Repository pattern with user-scoped methods, offline-first implementation

- [ ] **BE-003**: Create workout use case [Estimate: 3hr]
  - **Files**: `domain/usecase/CreateSimpleWorkoutUseCase.kt`
  - **Details**: Business logic, validation, analytics integration, error handling

- [ ] **BE-004**: Data mappers and converters [Estimate: 2hr]
  - **Files**: `data/mapper/SimpleWorkoutMapper.kt`, update `DateTimeConverters.kt`
  - **Details**: Entity-domain mapping, type converters, JSON serialization

#### Frontend Components (FE-001 to FE-005)
- [ ] **FE-001**: Workout creation ViewModel [Estimate: 4hr]
  - **Files**: `ui/workout/simple/SimpleWorkoutCreationViewModel.kt`
  - **Details**: MVI pattern, state management, form validation, event handling

- [ ] **FE-002**: Workout details form screen [Estimate: 5hr]
  - **Files**: `ui/workout/simple/WorkoutDetailsForm.kt`
  - **Details**: Name/description inputs, real-time validation, Material 3 styling

- [ ] **FE-003**: Exercise list form screen [Estimate: 6hr]
  - **Files**: `ui/workout/simple/ExerciseListForm.kt`, `ui/workout/simple/ExerciseInputCard.kt`
  - **Details**: Dynamic exercise list, input validation, add/remove exercises

- [ ] **FE-004**: Navigation integration [Estimate: 3hr]
  - **Files**: Update `ui/workout/WorkoutScreen.kt`, `ui/navigation/WorkoutNavigation.kt`
  - **Details**: Replace placeholder, extend navigation state, screen transitions

- [ ] **FE-005**: Results display screen [Estimate: 4hr]
  - **Files**: `ui/workout/simple/SimpleWorkoutResultsScreen.kt`
  - **Details**: Workout summary display, exercise details, navigation actions

#### Integration (INT-001 to INT-002)
- [ ] **INT-001**: Firebase sync integration [Estimate: 3hr]
  - **Files**: Update `sync/WorkoutSyncWorker.kt`, Firestore rules
  - **Details**: Sync simple workouts to Firestore, conflict resolution

- [ ] **INT-002**: Analytics integration [Estimate: 2hr]
  - **Files**: Update `domain/usecase/analytics/LogWorkoutEventUseCase.kt`
  - **Details**: Track workout creation events, user engagement metrics

#### Testing (TEST-001 to TEST-002)
- [ ] **TEST-001**: Unit and integration tests [Estimate: 6hr]
  - **Files**: `test/`, `androidTest/` directories
  - **Details**: ViewModel tests, use case tests, repository tests, validation tests

- [ ] **TEST-002**: UI and accessibility tests [Estimate: 4hr]
  - **Files**: `androidTest/ui/workout/simple/`
  - **Details**: End-to-end flow tests, accessibility compliance, form validation

### Dependencies
- **BE-001 depends on DB-001**: Domain models need entity structure
- **BE-002 depends on DB-002**: Repository needs DAO implementation  
- **FE-001 depends on BE-003**: ViewModel needs use case
- **INT-001 depends on BE-002**: Sync needs repository implementation
- **TEST-001 depends on BE-001, BE-002, BE-003**: Tests need business logic
- **TEST-002 depends on FE-001, FE-002, FE-003**: UI tests need components

## Success Metrics
- **User Engagement**: 80%+ completion rate for workout creation flow (vs 0% current)
- **Performance**: <500ms form submission, <200ms screen transitions
- **User Retention**: 60%+ of users who create first workout return within 7 days
- **Error Rate**: <2% failed workout creations due to technical issues

## Timeline
**Total Effort**: 45 hours (5-7 developer days)
**Critical Path**: DB-001 → BE-001 → BE-002 → BE-003 → FE-001 → FE-002 → FE-003 → TEST-002 (30 hours)

## Execution Log

### Layer Completed: Database Layer
- **Status**: Completed
- **Timestamp**: 2025-01-26 14:30:00
- **Tasks Completed**:
  - `DB-001`: Create simple workout entity and DAO
  - `DB-002`: Create simple exercise entity and relationships
  - `DB-003`: Database migration and testing
- **Summary**: Database schema created with SimpleWorkoutEntity and SimpleExerciseEntity, including foreign key relationships, user-scoped DAOs, migration script from version 5 to 6, and dependency injection setup in DatabaseModule.

### Layer Completed: Backend Services Layer
- **Status**: Completed
- **Timestamp**: 2025-01-26 15:15:00
- **Tasks Completed**:
  - `BE-001`: Domain models and value objects
  - `BE-002`: Repository interface and implementation
  - `BE-003`: Create workout use case
  - `BE-004`: Data mappers and converters
- **Summary**: Domain models with comprehensive validation created (SimpleWorkout, SimpleExercise), value objects for type safety (SimpleWorkoutId, SimpleExerciseId), repository pattern with offline-first implementation, CreateSimpleWorkoutUseCase with analytics integration, and SimpleWorkoutMapper for entity-domain conversion and Firestore serialization.

### Layer Completed: Frontend Components Layer
- **Status**: Completed
- **Timestamp**: 2025-01-27 10:45:00
- **Tasks Completed**:
  - `FE-001`: Workout creation ViewModel (SimpleWorkoutCreationViewModel with MVI pattern)
  - `FE-002`: Workout details form screen (WorkoutDetailsForm with validation)
  - `FE-003`: Exercise list form screen (ExerciseListForm and ExerciseInputCard)
  - `FE-004`: Navigation integration (WorkoutScreen updated, placeholder button replaced)
  - `FE-005`: Results display screen (SimpleWorkoutResultsScreen and SimpleWorkoutResultsViewModel)
- **Summary**: Complete UI flow implemented with Material 3 design. SimpleWorkoutCreationScreen coordinates multi-step creation process, WorkoutScreen navigation updated to route "Create Your First Workout" button to actual creation flow, SimpleWorkoutResultsScreen displays completed workout with celebration UI and action buttons. All components follow MVI pattern with proper state management and error handling.