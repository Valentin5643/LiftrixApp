# SPEC-20250626-workout-creation-redesign

## Executive Summary
**Feature**: Redesigned workout creation screen with exercise library integration and inline set management  
**Impact**: Eliminate data inconsistency from free-text exercise input, improve user experience with guided exercise selection, and enable better analytics through standardized exercise tracking  
**Effort**: 8-10 developer days across database, backend, and frontend layers  
**Risk**: Medium - requires careful migration of existing SimpleWorkout data and removal of 46+ files  
**Dependencies**: None - self-contained feature redesign  

## Product Specifications

### Elevator Pitch
A streamlined workout creation experience that guides users through exercise selection from a curated library while enabling rapid set input for efficient workout building.

### Target Users
- **Primary**: Fitness enthusiasts who create custom workouts regularly (daily usage)
- **Secondary**: Casual users who occasionally log workouts (weekly usage)

### Core Goals
1. **Data Quality**: Eliminate free-text exercise inconsistencies, enforce standardized exercise vocabulary
2. **User Experience**: Reduce workout creation time by 30% through improved exercise selection and set input
3. **Analytics**: Enable accurate exercise popularity tracking and workout pattern analysis

### Functional Requirements

- **FR-001**: Exercise Library Selection
  - **Given**: User wants to add an exercise to their workout
  - **When**: User taps "Add Exercise" button
  - **Then**: Modal exercise selector opens with searchable library of predefined exercises
  - **Acceptance**: Verified by UI test `test_exercise_selector_modal_opens`

- **FR-002**: Exercise Filtering and Search
  - **Given**: Exercise selector is open with 100+ exercises
  - **When**: User types in search field or selects equipment filter
  - **Then**: Exercise list filters in real-time with fuzzy matching
  - **Acceptance**: Verified by unit test `test_exercise_fuzzy_search`

- **FR-003**: Exercise Metadata Display
  - **Given**: User is viewing exercise in selector
  - **When**: Exercise is displayed in list
  - **Then**: Show exercise name, primary muscle group, equipment type, and difficulty level
  - **Acceptance**: Verified by UI test `test_exercise_metadata_displayed`

- **FR-004**: Inline Set Management
  - **Given**: User has selected an exercise
  - **When**: Exercise is added to workout
  - **Then**: Show compact set input fields (reps, RPE, optional weight) with "Add Set" button
  - **Acceptance**: Verified by UI test `test_inline_set_input`

- **FR-005**: Weight Memory System
  - **Given**: User has previously logged weight for an exercise
  - **When**: User adds the same exercise to a new workout
  - **Then**: Previous weight value pre-populates in weight field
  - **Acceptance**: Verified by integration test `test_weight_memory_persistence`

- **FR-006**: Custom Exercise Creation
  - **Given**: User cannot find desired exercise in library
  - **When**: User taps "Create Custom Exercise" option
  - **Then**: Navigate to custom exercise creation screen with image upload and categorization
  - **Acceptance**: Verified by navigation test `test_custom_exercise_navigation`

### User Stories

- **US-001**: As a fitness enthusiast, I want to quickly select exercises from a curated library so that I can build workouts without typing exercise names
  - **Acceptance Criteria**:
    1. Exercise selector opens in <500ms
    2. Search results appear within 200ms of typing
    3. Can select exercise with single tap
    4. Selected exercise appears in workout immediately

- **US-002**: As a user logging sets, I want my previous weights to be remembered so that I don't have to re-enter the same values every workout
  - **Acceptance Criteria**:
    1. Weight field pre-populates with last used value for that exercise
    2. User can modify pre-populated weight
    3. New weight becomes the remembered value for next time
    4. Weight memory persists across app sessions

- **US-003**: As a user creating custom workouts, I want to add multiple sets quickly so that I can log my entire workout efficiently
  - **Acceptance Criteria**:
    1. Can add new set with single tap
    2. Set input fields are accessible and easy to navigate
    3. Can delete individual sets
    4. Set order can be reordered if needed

### Non-Goals
- **Exercise video integration** - Deferred to V2 to focus on core functionality
- **Workout templates from exercises** - Out of scope, handled by separate workout template system
- **Exercise performance analytics** - Deferred to dedicated analytics feature
- **Social sharing of exercises** - Not part of core workout creation flow

## Technical Specifications

### System Architecture
- **Pattern**: MVVM with Clean Architecture principles
- **Flow**: UI → ViewModel → Use Cases → Repository → Data Sources (Room + Assets)
- **Security**: User-scoped data access with authentication validation for all workout operations

### Database Design

#### Schema Changes
```sql
-- Remove SimpleWorkout entities (Migration 6→7)
DROP TABLE IF EXISTS simple_workouts;
DROP TABLE IF EXISTS simple_exercises;

-- Enhance Exercise entity for weight memory
ALTER TABLE exercises ADD COLUMN last_used_weight_kg REAL DEFAULT NULL;
ALTER TABLE exercises ADD COLUMN last_used_at INTEGER DEFAULT NULL;

-- Add exercise usage tracking
CREATE TABLE exercise_usage_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    exercise_id TEXT NOT NULL,
    weight_kg REAL,
    reps INTEGER,
    rpe INTEGER,
    used_at INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_exercise_usage_user_exercise (user_id, exercise_id),
    INDEX idx_exercise_usage_recent (used_at)
);
```

#### Migration Approach
- **Zero-downtime migration**: Preserve existing workout data during SimpleWorkout removal
- **Data conversion**: Migrate SimpleWorkout records to regular Workout format where possible
- **Cleanup strategy**: Remove unused SimpleWorkout-related tables and indexes

### API Specifications

#### Exercise Library Service
```kotlin
interface ExerciseLibraryService {
    suspend fun searchExercises(
        query: String,
        equipment: Set<Equipment>? = null,
        muscleGroups: Set<ExerciseCategory>? = null
    ): Result<List<ExerciseLibrary>>
    
    suspend fun getExerciseById(id: String): Result<ExerciseLibrary?>
    
    suspend fun getRecentExercises(userId: String, limit: Int = 10): Result<List<ExerciseLibrary>>
}
```

#### Weight Memory Service
```kotlin
interface WeightMemoryService {
    suspend fun getLastUsedWeight(userId: String, exerciseId: String): Result<Float?>
    
    suspend fun updateExerciseWeight(
        userId: String, 
        exerciseId: String, 
        weight: Float
    ): Result<Unit>
}
```

### Component Design

#### UI Components
- **ExerciseSelector**: Modal bottom sheet with search, filters, and exercise list
- **ExerciseCard**: Compact exercise display with inline set management
- **SetInputRow**: Individual set input with reps, RPE, weight fields
- **ExerciseSearchField**: Debounced search input with clear button
- **EquipmentFilterChips**: Multi-select equipment filtering

#### Service Components
- **WorkoutCreationService**: Coordinates workout building and validation
- **ExerciseMemoryService**: Manages weight memory and usage history
- **ExerciseLibraryService**: Handles exercise search and retrieval

### Testing Strategy

#### Test Scenarios
1. **Exercise Selection Flow**: Verify complete flow from exercise search to workout addition
2. **Weight Memory Persistence**: Confirm weight values are remembered across sessions
3. **Custom Exercise Integration**: Test custom exercise creation and usage
4. **Performance Testing**: Validate search response times and UI responsiveness
5. **Data Migration**: Ensure SimpleWorkout data migrates correctly
6. **Offline Functionality**: Verify exercise library works without network connection

#### Test Coverage Requirements
- Unit tests: >90% coverage for use cases and repositories
- Integration tests: Complete workout creation flows
- UI tests: All user interactions and navigation paths
- Performance tests: Search latency <200ms, UI interactions <100ms

## Implementation Plan

### Task Breakdown

#### Database Layer (DB-XXX)
- [ ] **DB-001**: Create Migration 6→7 for SimpleWorkout removal [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/migration/Migration_6_to_7.kt`
  - **Details**: Drop SimpleWorkout tables, migrate data to regular Workout format, add weight memory fields

- [ ] **DB-002**: Update LiftrixDatabase configuration [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/LiftrixDatabase.kt`
  - **Details**: Remove SimpleWorkout entities, add migration, update version to 7

- [ ] **DB-003**: Create ExerciseUsageHistory entity and DAO [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/entity/ExerciseUsageHistoryEntity.kt`, `app/src/main/java/com/example/liftrix/data/local/dao/ExerciseUsageHistoryDao.kt`
  - **Details**: Entity for tracking exercise usage patterns and weight memory

#### Backend Services (BE-XXX)
- [ ] **BE-001**: Implement WeightMemoryService [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/service/WeightMemoryService.kt`, `app/src/main/java/com/example/liftrix/data/service/WeightMemoryServiceImpl.kt`
  - **Details**: Service for managing exercise weight memory with user scoping

- [ ] **BE-002**: Enhance ExerciseLibraryService [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/service/ExerciseLibraryService.kt`
  - **Details**: Add recent exercises query and improve search performance

- [ ] **BE-003**: Create unified WorkoutCreationUseCase [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/usecase/CreateWorkoutUseCase.kt`
  - **Details**: Replace SimpleWorkout use cases with unified workout creation logic

#### Frontend Components (FE-XXX)
- [ ] **FE-001**: Redesign WorkoutCreationScreen [Estimate: 8hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/WorkoutCreationScreen.kt`
  - **Details**: Single-screen layout with header form, exercise cards, and inline set management

- [ ] **FE-002**: Create enhanced ExerciseSelector component [Estimate: 6hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/components/ExerciseSelector.kt`
  - **Details**: Modal with search, equipment filtering, recent exercises, and custom exercise option

- [ ] **FE-003**: Build ExerciseCard with inline sets [Estimate: 6hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/components/ExerciseCard.kt`
  - **Details**: Compact exercise display with expandable set input, weight memory integration

- [ ] **FE-004**: Implement SetInputRow component [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/creation/components/SetInputRow.kt`
  - **Details**: Individual set input fields with validation and weight pre-population

#### Integration (INT-XXX)
- [ ] **INT-001**: Update navigation routing [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/WorkoutScreen.kt`
  - **Details**: Route workout creation buttons to new unified screen

- [ ] **INT-002**: Remove SimpleWorkout UI components [Estimate: 4hr]
  - **Files**: Remove 15+ SimpleWorkout UI files from `app/src/main/java/com/example/liftrix/ui/workout/simple/`
  - **Details**: Clean removal of unused SimpleWorkout screens and components

- [ ] **INT-003**: Update analytics integration [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/service/AnalyticsServiceImpl.kt`
  - **Details**: Replace SimpleWorkout analytics events with unified workout events

#### Testing (TEST-XXX)
- [ ] **TEST-001**: Unit tests for weight memory service [Estimate: 3hr]
  - **Files**: `app/src/test/java/com/example/liftrix/domain/service/WeightMemoryServiceTest.kt`
  - **Details**: Test weight persistence, user scoping, and edge cases

- [ ] **TEST-002**: Integration tests for workout creation flow [Estimate: 4hr]
  - **Files**: `app/src/androidTest/java/com/example/liftrix/ui/workout/creation/WorkoutCreationFlowTest.kt`
  - **Details**: End-to-end testing of exercise selection, set input, and workout saving

- [ ] **TEST-003**: UI tests for ExerciseSelector component [Estimate: 3hr]
  - **Files**: `app/src/androidTest/java/com/example/liftrix/ui/workout/creation/components/ExerciseSelectorTest.kt`
  - **Details**: Test search functionality, filtering, and exercise selection

### Dependencies
- DB-001 must complete before BE-003 (database schema ready for new use cases)
- BE-001, BE-002 must complete before FE-001 (services available for UI integration)
- FE-001, FE-002, FE-003, FE-004 can be developed in parallel once services are ready
- INT-001, INT-002 depend on FE-001 completion
- All TEST tasks depend on their corresponding implementation tasks

## Success Metrics
- **Exercise Selection Time**: <5 seconds average time from "Add Exercise" to exercise selected
- **Set Input Speed**: <3 seconds per set input on average
- **Weight Memory Accuracy**: >95% of weight fields pre-populate correctly
- **User Satisfaction**: >4.5/5 rating for workout creation experience
- **Data Quality**: 0% free-text exercise entries in new workouts
- **Search Performance**: <200ms average search response time

## Timeline
**Total Effort**: 64 hours (8 developer days)  
**Critical Path**: DB-001 → BE-003 → FE-001 → INT-001 (18 hours minimum)  
**Parallel Development**: UI components can be built simultaneously once backend services are ready  
**Risk Mitigation**: Database migration testing should be prioritized to identify issues early