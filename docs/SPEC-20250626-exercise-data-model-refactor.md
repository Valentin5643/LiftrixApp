# SPEC-20250626-exercise-data-model-refactor

## Executive Summary
**Feature**: Refactor exercise data models to support time/distance tracking with optional weight fields and remove free-text exercise system  
**Impact**: Enable comprehensive exercise tracking (weight, time, distance) while maintaining data integrity through standardized exercise vocabulary  
**Effort**: 4-5 developer days focused on database schema changes and domain model refactoring  
**Risk**: High - involves database migrations and removal of existing SimpleWorkout system with 46+ dependent files  
**Dependencies**: Must complete before SPEC-20250626-workout-creation-redesign UI changes  

## Product Specifications

### Elevator Pitch
A flexible exercise data model that supports all exercise types (weight-based, time-based, distance-based) while enforcing exercise library standards for consistent data quality.

### Target Users
- **Primary**: All app users who log different types of exercises (bodyweight, cardio, strength training)
- **Secondary**: Analytics systems that process exercise data for insights and recommendations

### Core Goals
1. **Flexibility**: Support weight, time, and distance tracking for all exercise types
2. **Data Integrity**: Eliminate free-text exercise names through enforced library usage
3. **Performance**: Maintain fast workout logging with minimal validation overhead
4. **Backwards Compatibility**: Preserve existing workout data during migration

### Functional Requirements

- **FR-001**: Exercise Type Support
  - **Given**: User is logging different exercise types
  - **When**: User adds exercise to workout
  - **Then**: System supports weight (strength), time (cardio), distance (running), or combination tracking
  - **Acceptance**: Verified by unit test `test_exercise_type_support`

- **FR-002**: Optional Field Validation
  - **Given**: Exercise has specific tracking requirements (e.g., push-ups don't need weight)
  - **When**: User inputs set data
  - **Then**: Only required fields are validated, optional fields can be null
  - **Acceptance**: Verified by validation test `test_optional_field_validation`

- **FR-003**: Weight Memory Per Exercise
  - **Given**: User has previously logged weight for an exercise
  - **When**: Same exercise is added to new workout
  - **Then**: Previous weight value is suggested but can be overridden
  - **Acceptance**: Verified by integration test `test_weight_memory_persistence`

- **FR-004**: Bilateral/Unilateral Exercise Storage
  - **Given**: User performs unilateral exercise (single arm/leg)
  - **When**: Exercise is logged
  - **Then**: System stores same as bilateral (user responsible for rep conversion)
  - **Acceptance**: Verified by domain test `test_unilateral_bilateral_storage`

- **FR-005**: SimpleWorkout System Removal
  - **Given**: System contains SimpleWorkout entities and free-text exercises
  - **When**: Migration executes
  - **Then**: All SimpleWorkout data migrated to standard format, free-text components removed
  - **Acceptance**: Verified by migration test `test_simple_workout_migration`

### User Stories

- **US-001**: As a strength athlete, I want to log weight-based exercises with sets/reps/weight so that I can track progressive overload
  - **Acceptance Criteria**:
    1. Weight field accepts decimal values (0.5kg increments)
    2. Weight is remembered for next workout
    3. RPE scale (1-10) optional but encouraged
    4. Time field available for rest-pause sets

- **US-002**: As a cardio enthusiast, I want to log time and distance for running/cycling so that I can track endurance progress
  - **Acceptance Criteria**:
    1. Time field accepts hours:minutes:seconds format
    2. Distance field accepts decimal values with unit selection
    3. Weight field hidden for cardio exercises
    4. Average pace calculated automatically

- **US-003**: As a bodyweight trainer, I want to log exercises without weight fields so that unnecessary inputs don't clutter the interface
  - **Acceptance Criteria**:
    1. Weight field hidden for bodyweight exercises
    2. Time field available for static holds (planks, wall sits)
    3. Reps field accepts high values (100+ for high-rep exercises)
    4. RPE particularly important for bodyweight progression

### Non-Goals
- **Exercise library expansion** - Using existing JSON library, not adding new exercises
- **Automatic unit conversion** - User responsible for consistent unit usage
- **Advanced exercise analytics** - Focus on data capture, not analysis
- **Exercise instruction integration** - Deferred to dedicated exercise education feature

## Technical Specifications

### System Architecture
- **Pattern**: Domain-Driven Design with Rich Domain Models
- **Flow**: UI → Domain Models → Repository → Database Entities
- **Validation**: Domain model validation with business rule enforcement

### Database Design

#### Enhanced Exercise Entity Schema
```sql
-- Updated Exercise Entity (version 7)
CREATE TABLE exercises (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    workout_id INTEGER NOT NULL,
    exercise_library_id TEXT NOT NULL, -- Links to exercise library
    order_index INTEGER NOT NULL,
    target_sets INTEGER,
    target_reps INTEGER,
    target_weight_kg REAL,
    target_time_seconds INTEGER,
    target_distance_meters REAL,
    notes TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (workout_id) REFERENCES workouts(id) ON DELETE CASCADE,
    FOREIGN KEY (exercise_library_id) REFERENCES exercise_library(id),
    INDEX idx_exercises_workout_order (workout_id, order_index)
);

-- Set tracking with flexible metrics
CREATE TABLE exercise_sets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    exercise_id INTEGER NOT NULL,
    set_number INTEGER NOT NULL,
    reps INTEGER,
    weight_kg REAL,
    time_seconds INTEGER,
    distance_meters REAL,
    rpe INTEGER CHECK (rpe >= 1 AND rpe <= 10),
    completed_at INTEGER,
    notes TEXT,
    FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE CASCADE,
    INDEX idx_sets_exercise_number (exercise_id, set_number)
);

-- Exercise weight memory
CREATE TABLE exercise_weight_memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    exercise_library_id TEXT NOT NULL,
    last_weight_kg REAL NOT NULL,
    last_used_at INTEGER NOT NULL,
    usage_count INTEGER DEFAULT 1,
    PRIMARY KEY (user_id, exercise_library_id),
    FOREIGN KEY (exercise_library_id) REFERENCES exercise_library(id)
);
```

#### Migration Strategy (6→7)
```sql
-- Migration 6 to 7: Remove SimpleWorkout system, enhance Exercise tracking
BEGIN TRANSACTION;

-- 1. Migrate SimpleWorkout data to regular Workout format
INSERT INTO workouts (user_id, name, description, created_at, status)
SELECT user_id, name, description, created_at, 'COMPLETED'
FROM simple_workouts;

-- 2. Migrate SimpleExercise data to regular Exercise format
INSERT INTO exercises (workout_id, exercise_library_id, order_index, created_at, updated_at)
SELECT 
    w.id,
    COALESCE(el.id, 'bodyweight-general'), -- Default for unmapped exercises
    se.order_index,
    se.created_at,
    se.created_at
FROM simple_exercises se
JOIN simple_workouts sw ON se.simple_workout_id = sw.id
JOIN workouts w ON w.user_id = sw.user_id AND w.name = sw.name
LEFT JOIN exercise_library el ON LOWER(el.name) = LOWER(se.name);

-- 3. Migrate SimpleExercise sets to exercise_sets
INSERT INTO exercise_sets (exercise_id, set_number, reps, weight_kg, rpe)
SELECT 
    e.id,
    1, -- SimpleExercise only had one "set" summary
    se.reps,
    se.weight_kg,
    se.rpe
FROM simple_exercises se
JOIN simple_workouts sw ON se.simple_workout_id = sw.id
JOIN workouts w ON w.user_id = sw.user_id AND w.name = sw.name
JOIN exercises e ON e.workout_id = w.id AND e.order_index = se.order_index;

-- 4. Drop SimpleWorkout tables
DROP TABLE simple_exercises;
DROP TABLE simple_workouts;

-- 5. Update database version
PRAGMA user_version = 7;

COMMIT;
```

### Domain Model Design

#### Exercise Domain Model
```kotlin
data class Exercise(
    val id: ExerciseId,
    val workoutId: WorkoutId,
    val libraryExercise: ExerciseLibrary,
    val orderIndex: Int,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeight: Weight? = null,
    val targetTime: Duration? = null,
    val targetDistance: Distance? = null,
    val sets: List<ExerciseSet> = emptyList(),
    val notes: String? = null,
    val createdAt: Instant
) {
    init {
        require(orderIndex >= 0) { "Order index must be non-negative" }
        require(targetSets == null || targetSets > 0) { "Target sets must be positive" }
        require(targetReps == null || targetReps > 0) { "Target reps must be positive" }
        require(sets.size <= 50) { "Maximum 50 sets per exercise" }
    }
    
    val isWeightBased: Boolean = libraryExercise.equipment.requiresWeight
    val isTimeBased: Boolean = libraryExercise.movementPattern.isTimeBased
    val isDistanceBased: Boolean = libraryExercise.movementPattern.isDistanceBased
    
    fun addSet(set: ExerciseSet): Exercise {
        validateSetCompatibility(set)
        return copy(sets = sets + set)
    }
    
    private fun validateSetCompatibility(set: ExerciseSet) {
        if (!isWeightBased && set.weight != null) {
            throw IllegalArgumentException("Weight not supported for ${libraryExercise.name}")
        }
        if (!isTimeBased && set.time != null) {
            throw IllegalArgumentException("Time not supported for ${libraryExercise.name}")
        }
        if (!isDistanceBased && set.distance != null) {
            throw IllegalArgumentException("Distance not supported for ${libraryExercise.name}")
        }
    }
}
```

#### ExerciseSet Domain Model
```kotlin
data class ExerciseSet(
    val id: ExerciseSetId,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Weight? = null,
    val time: Duration? = null,
    val distance: Distance? = null,
    val rpe: RPE? = null,
    val completedAt: Instant? = null,
    val notes: String? = null
) {
    init {
        require(setNumber > 0) { "Set number must be positive" }
        require(reps == null || reps > 0) { "Reps must be positive" }
        require(hasAtLeastOneMetric()) { "Set must have at least one metric (reps, time, or distance)" }
    }
    
    private fun hasAtLeastOneMetric(): Boolean = 
        reps != null || time != null || distance != null
    
    val isCompleted: Boolean = completedAt != null
}
```

#### Value Objects
```kotlin
@JvmInline
value class Weight(val kilograms: Float) {
    init {
        require(kilograms >= 0) { "Weight cannot be negative" }
        require(kilograms <= 1000) { "Weight cannot exceed 1000kg" }
    }
}

@JvmInline
value class RPE(val value: Int) {
    init {
        require(value in 1..10) { "RPE must be between 1 and 10" }
    }
}

@JvmInline
value class Distance(val meters: Float) {
    init {
        require(meters > 0) { "Distance must be positive" }
        require(meters <= 100000) { "Distance cannot exceed 100km" }
    }
}
```

### Repository Interface Changes

#### Enhanced Exercise Repository
```kotlin
interface ExerciseRepository {
    suspend fun saveExercise(exercise: Exercise): Result<Exercise>
    suspend fun getExercisesByWorkout(workoutId: WorkoutId): Result<List<Exercise>>
    suspend fun updateExercise(exercise: Exercise): Result<Exercise>
    suspend fun deleteExercise(exerciseId: ExerciseId): Result<Unit>
    
    // Weight memory functionality
    suspend fun getLastUsedWeight(userId: UserId, exerciseLibraryId: String): Result<Weight?>
    suspend fun updateWeightMemory(userId: UserId, exerciseLibraryId: String, weight: Weight): Result<Unit>
    
    // Exercise history for analytics
    suspend fun getExerciseHistory(userId: UserId, exerciseLibraryId: String, limit: Int = 10): Result<List<ExerciseSet>>
}
```

### Testing Strategy

#### Domain Model Tests
1. **Exercise Validation**: Test all domain model validation rules
2. **Set Compatibility**: Verify weight/time/distance field validation per exercise type
3. **Value Object Constraints**: Test Weight, RPE, Distance boundary conditions
4. **Business Logic**: Test exercise creation, set addition, completion tracking

#### Migration Tests
1. **Data Preservation**: Verify all SimpleWorkout data migrates correctly
2. **Schema Validation**: Confirm new schema supports all required operations
3. **Performance**: Ensure migration completes within acceptable time
4. **Rollback Strategy**: Test migration rollback procedures

## Implementation Plan

### Task Breakdown

#### Database Layer (DB-XXX)
- [ ] **DB-001**: Create enhanced Exercise and ExerciseSet entities [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/entity/ExerciseEntity.kt`, `app/src/main/java/com/example/liftrix/data/local/entity/ExerciseSetEntity.kt`
  - **Details**: New entities with optional weight/time/distance fields and proper foreign key relationships

- [ ] **DB-002**: Implement Migration 6→7 [Estimate: 6hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/migration/Migration_6_to_7.kt`
  - **Details**: Complex migration to preserve SimpleWorkout data while removing system

- [ ] **DB-003**: Create ExerciseWeightMemory entity and DAO [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/entity/ExerciseWeightMemoryEntity.kt`, `app/src/main/java/com/example/liftrix/data/local/dao/ExerciseWeightMemoryDao.kt`
  - **Details**: Weight memory tracking with user scoping

- [ ] **DB-004**: Update Exercise and ExerciseSet DAOs [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/dao/ExerciseDao.kt`, `app/src/main/java/com/example/liftrix/data/local/dao/ExerciseSetDao.kt`
  - **Details**: Enhanced DAOs with weight memory queries and flexible metric support

#### Domain Layer (DOM-XXX)
- [ ] **DOM-001**: Implement enhanced Exercise domain model [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/model/Exercise.kt`
  - **Details**: Rich domain model with validation, set management, and exercise type detection

- [ ] **DOM-002**: Create ExerciseSet domain model [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/model/ExerciseSet.kt`
  - **Details**: Flexible set model supporting weight/time/distance combinations

- [ ] **DOM-003**: Implement value objects (Weight, RPE, Distance) [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/model/Weight.kt`, etc.
  - **Details**: Type-safe value objects with validation

- [ ] **DOM-004**: Create exercise type detection logic [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/model/ExerciseType.kt`
  - **Details**: Logic to determine required/optional fields based on exercise library metadata

#### Repository Layer (REP-XXX)
- [ ] **REP-001**: Implement enhanced ExerciseRepository [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/repository/ExerciseRepositoryImpl.kt`
  - **Details**: Repository with weight memory, flexible metrics, and exercise history

- [ ] **REP-002**: Create entity-to-domain mappers [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/mapper/ExerciseMapper.kt`
  - **Details**: Bidirectional mapping between entities and domain models

- [ ] **REP-003**: Remove SimpleWorkout repository and related code [Estimate: 3hr]
  - **Files**: Remove `app/src/main/java/com/example/liftrix/data/repository/SimpleWorkoutRepositoryImpl.kt` and 20+ related files
  - **Details**: Clean removal of SimpleWorkout system components

#### Testing (TEST-XXX)
- [ ] **TEST-001**: Domain model unit tests [Estimate: 4hr]
  - **Files**: `app/src/test/java/com/example/liftrix/domain/model/ExerciseTest.kt`, `app/src/test/java/com/example/liftrix/domain/model/ExerciseSetTest.kt`
  - **Details**: Comprehensive validation and business logic testing

- [ ] **TEST-002**: Migration integration tests [Estimate: 3hr]
  - **Files**: `app/src/androidTest/java/com/example/liftrix/data/local/migration/Migration_6_to_7_Test.kt`
  - **Details**: Test data preservation and schema correctness

- [ ] **TEST-003**: Repository integration tests [Estimate: 3hr]
  - **Files**: `app/src/androidTest/java/com/example/liftrix/data/repository/ExerciseRepositoryTest.kt`
  - **Details**: Test weight memory, exercise history, and CRUD operations

### Dependencies
- DB-001, DB-002, DB-003, DB-004 must complete before DOM-XXX tasks (schema ready for domain models)
- DOM-XXX tasks must complete before REP-XXX tasks (domain models ready for repository implementation)
- REP-003 can run in parallel with other REP tasks (independent cleanup)
- All TEST tasks depend on their corresponding implementation tasks

## Success Metrics
- **Migration Success Rate**: 100% of SimpleWorkout data successfully migrated
- **Data Integrity**: 0% data loss during migration process
- **Performance**: Exercise creation <100ms, set logging <50ms
- **Validation Coverage**: >95% of invalid inputs properly rejected
- **Weight Memory Accuracy**: >98% of weight suggestions are correct

## Timeline
**Total Effort**: 38 hours (5 developer days)  
**Critical Path**: DB-002 → DOM-001 → REP-001 (14 hours minimum)  
**Risk Mitigation**: Extensive testing of Migration 6→7 required before production deployment