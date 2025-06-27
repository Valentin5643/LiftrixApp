# SPEC-20250625-workout-data-architecture

## Executive Summary
**Feature**: Simple Workout Data Architecture
**Impact**: Establish a clean, scalable data foundation for simple workout tracking that coexists with the existing complex workout system while supporting future AI functionality integration.
**Effort**: 3-4 developer days  
**Risk**: Low - creates new isolated data models without modifying existing complex workout architecture
**Dependencies**: None - standalone data architecture supporting the workout creation and display features

## Product Specifications

### Elevator Pitch
Design and implement a simplified data architecture that supports basic workout tracking with a clean domain model, efficient persistence, and seamless synchronization, providing the perfect foundation for AI-powered workout analysis and recommendations.

### Target Users
- **Primary**: New users creating their first simple workouts (daily usage)
- **Secondary**: Developers building AI functionality on top of workout data (ongoing development)

### Core Goals
1. **Simplicity**: Clean data model that's easy to understand and extend for AI processing
2. **Performance**: Efficient queries and minimal storage overhead for workout data
3. **Scalability**: Architecture that supports future AI features without major refactoring
4. **Isolation**: Completely separate from complex workout system to avoid interference

### Functional Requirements

- **FR-001**: Simple workout domain model with validation
  - **Given**: Developer creates new workout with name, description, and exercises
  - **When**: Domain model validates input data
  - **Then**: Ensure data integrity with business rules and constraints
  - **Acceptance**: Verified by unit test `test_simple_workout_validation`

- **FR-002**: Exercise data model with tracking fields
  - **Given**: Exercise includes name, reps, RPE, sets, weight, and future photo support
  - **When**: Exercise model is created and validated
  - **Then**: All fields follow proper constraints and support future extensibility
  - **Acceptance**: Verified by unit test `test_simple_exercise_validation`

- **FR-003**: Local database persistence with relationships
  - **Given**: Workout and exercise data needs local storage
  - **When**: Data is saved to Room database
  - **Then**: Proper foreign key relationships and indexing for performance
  - **Acceptance**: Verified by integration test `test_workout_persistence`

- **FR-004**: User-scoped data access and sync
  - **Given**: Multiple users use the app with their own workout data
  - **When**: Data is accessed or synchronized
  - **Then**: Ensure proper user isolation and conflict resolution
  - **Acceptance**: Verified by integration test `test_user_scoped_access`

- **FR-005**: Future photo/animation support structure
  - **Given**: Exercise will eventually support photo/animation media
  - **When**: Database schema is designed
  - **Then**: Include optional media fields that can be populated later
  - **Acceptance**: Verified by migration test `test_media_field_addition`

### User Stories

- **US-001**: As a developer, I want a clean data model so that I can easily build AI features on top of workout data.
  - **Acceptance Criteria**:
    1. Domain models are well-documented with clear business rules
    2. Data structures are normalized and queryable for AI analysis
    3. JSON serialization supports easy export for ML processing
    4. Future media support is designed into the schema

- **US-002**: As a user, I want my workout data to be stored reliably so that I never lose my progress.
  - **Acceptance Criteria**:
    1. Data is stored locally first (offline-first approach)
    2. Automatic background sync to cloud storage
    3. Conflict resolution handles simultaneous edits gracefully
    4. Data migration preserves all historical workout information

### Non-Goals
- **Migration from existing workout system** - **Reason**: Keeping simple and complex systems separate
- **Advanced workout relationships** (supersets, circuits) - **Reason**: Maintaining simplicity for V1
- **Real-time collaborative editing** - **Reason**: Single-user focus for simple workout creation

## Technical Specifications

### System Architecture
- **Pattern**: Clean Architecture with Domain-driven Design principles
- **Flow**: Domain Models → Repository Interface → Local/Remote Data Sources → External Storage
- **Security**: User-scoped access with Firebase Authentication integration
- **Sync**: Offline-first with eventual consistency via Firebase Firestore

### Database Design

**Entity Relationship Diagram:**
```
SimpleWorkout (1) ─────── (many) SimpleExercise
     │                           │
     │                           │
     ├─ id (PK)                  ├─ id (PK)
     ├─ user_id                  ├─ workout_id (FK)
     ├─ name                     ├─ name
     ├─ description              ├─ reps
     ├─ created_at               ├─ rpe
     ├─ updated_at               ├─ sets
     ├─ is_synced                ├─ weight_kg
     └─ sync_version             ├─ order_index
                                 ├─ photo_url (future)
                                 ├─ animation_url (future)
                                 └─ created_at
```

**Database Schema:**

```sql
-- Simple workout container table
CREATE TABLE simple_workouts (
    id TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK(length(name) BETWEEN 3 AND 100),
    description TEXT CHECK(description IS NULL OR length(description) <= 500),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    is_synced INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Exercise data table
CREATE TABLE simple_exercises (
    id TEXT PRIMARY KEY NOT NULL,
    workout_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK(length(name) BETWEEN 2 AND 100),
    reps INTEGER NOT NULL CHECK(reps BETWEEN 1 AND 999),
    rpe INTEGER CHECK(rpe IS NULL OR rpe BETWEEN 1 AND 10),
    sets INTEGER NOT NULL CHECK(sets BETWEEN 1 AND 50),
    weight_kg REAL CHECK(weight_kg IS NULL OR (weight_kg >= 0 AND weight_kg <= 999.9)),
    order_index INTEGER NOT NULL CHECK(order_index >= 0),
    photo_url TEXT,     -- Future: URL to exercise photo
    animation_url TEXT, -- Future: URL to exercise animation/GIF
    created_at TEXT NOT NULL,
    CONSTRAINT fk_workout_id FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
);

-- Performance indexes
CREATE INDEX idx_simple_workouts_user_created ON simple_workouts(user_id, created_at DESC);
CREATE INDEX idx_simple_workouts_sync ON simple_workouts(user_id, is_synced, sync_version);
CREATE INDEX idx_simple_exercises_workout_order ON simple_exercises(workout_id, order_index);
CREATE INDEX idx_simple_exercises_name ON simple_exercises(name); -- For AI analysis queries

-- Sync tracking table for conflict resolution
CREATE TABLE simple_workout_sync_log (
    id TEXT PRIMARY KEY NOT NULL,
    workout_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK(operation IN ('CREATE', 'UPDATE', 'DELETE')),
    sync_timestamp TEXT NOT NULL,
    local_version INTEGER NOT NULL,
    remote_version INTEGER,
    conflict_resolved INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_sync_workout_id FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
);
```

**Migration Strategy:**

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create simple workout tables
        database.execSQL("""
            CREATE TABLE simple_workouts (
                id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL CHECK(length(name) BETWEEN 3 AND 100),
                description TEXT CHECK(description IS NULL OR length(description) <= 500),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                sync_version INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        database.execSQL("""
            CREATE TABLE simple_exercises (
                id TEXT PRIMARY KEY NOT NULL,
                workout_id TEXT NOT NULL,
                name TEXT NOT NULL CHECK(length(name) BETWEEN 2 AND 100),
                reps INTEGER NOT NULL CHECK(reps BETWEEN 1 AND 999),
                rpe INTEGER CHECK(rpe IS NULL OR rpe BETWEEN 1 AND 10),
                sets INTEGER NOT NULL CHECK(sets BETWEEN 1 AND 50),
                weight_kg REAL CHECK(weight_kg IS NULL OR (weight_kg >= 0 AND weight_kg <= 999.9)),
                order_index INTEGER NOT NULL CHECK(order_index >= 0),
                photo_url TEXT,
                animation_url TEXT,
                created_at TEXT NOT NULL,
                CONSTRAINT fk_workout_id FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
            )
        """)
        
        // Create performance indexes
        database.execSQL("CREATE INDEX idx_simple_workouts_user_created ON simple_workouts(user_id, created_at DESC)")
        database.execSQL("CREATE INDEX idx_simple_exercises_workout_order ON simple_exercises(workout_id, order_index)")
        database.execSQL("CREATE INDEX idx_simple_exercises_name ON simple_exercises(name)")
    }
}
```

### Domain Model Design

**Core Domain Models:**

```kotlin
/**
 * Simple workout domain model for basic workout tracking
 * Designed for AI analysis and future extensibility
 */
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
        require(name.length in 3..100) { "Workout name must be 3-100 characters" }
        require(description?.length ?: 0 <= 500) { "Description cannot exceed 500 characters" }
        require(exercises.size <= 20) { "Cannot exceed 20 exercises per workout" }
        require(exercises.map { it.orderIndex }.sorted() == exercises.indices.toList()) {
            "Exercise order indexes must be sequential starting from 0"
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_EXERCISES = 20
    }
    
    /**
     * Calculates total workout volume for AI analysis
     */
    fun calculateTotalVolume(): Double {
        return exercises.sumOf { exercise ->
            (exercise.weightKg ?: 0.0) * exercise.sets * exercise.reps
        }
    }
    
    /**
     * Gets all unique exercise names for pattern analysis
     */
    fun getExerciseNames(): Set<String> = exercises.map { it.name.lowercase() }.toSet()
    
    /**
     * Calculates average RPE across all exercises
     */
    fun getAverageRPE(): Double? {
        val rpeValues = exercises.mapNotNull { it.rpe }
        return if (rpeValues.isNotEmpty()) rpeValues.average() else null
    }
    
    /**
     * Exports workout data for AI processing
     */
    fun toAIAnalysisData(): WorkoutAIData = WorkoutAIData(
        workoutId = id.value,
        userId = userId,
        totalVolume = calculateTotalVolume(),
        exerciseCount = exercises.size,
        averageRPE = getAverageRPE(),
        exerciseTypes = getExerciseNames(),
        duration = null, // Future: calculate from start/end times
        workoutDate = createdAt
    )
}

/**
 * Simple exercise model optimized for AI analysis
 */
data class SimpleExercise(
    val id: SimpleExerciseId,
    val name: String,
    val reps: Int,
    val rpe: Int?,
    val sets: Int,
    val weightKg: Double?,
    val orderIndex: Int,
    val photoUrl: String? = null,     // Future: exercise demonstration photo
    val animationUrl: String? = null  // Future: exercise animation/GIF
) {
    init {
        require(name.length in 2..100) { "Exercise name must be 2-100 characters" }
        require(reps in 1..999) { "Reps must be between 1 and 999" }
        require(rpe == null || rpe in 1..10) { "RPE must be between 1 and 10 if specified" }
        require(sets in 1..50) { "Sets must be between 1 and 50" }
        require(weightKg == null || weightKg >= 0) { "Weight must be non-negative if specified" }
        require(orderIndex >= 0) { "Order index must be non-negative" }
    }
    
    companion object {
        const val MAX_NAME_LENGTH = 100
        const val MIN_REPS = 1
        const val MAX_REPS = 999
        const val MIN_RPE = 1
        const val MAX_RPE = 10
        const val MIN_SETS = 1
        const val MAX_SETS = 50
        const val MAX_WEIGHT_KG = 999.9
    }
    
    /**
     * Calculates exercise volume for AI analysis
     */
    fun calculateVolume(): Double = (weightKg ?: 0.0) * sets * reps
    
    /**
     * Determines if exercise has complete tracking data
     */
    fun hasCompleteData(): Boolean = weightKg != null && rpe != null
    
    /**
     * Exports exercise data for AI processing
     */
    fun toAIAnalysisData(): ExerciseAIData = ExerciseAIData(
        exerciseId = id.value,
        name = name.lowercase().trim(),
        volume = calculateVolume(),
        intensity = rpe,
        sets = sets,
        reps = reps,
        hasMedia = photoUrl != null || animationUrl != null
    )
}

/**
 * Value objects for type safety
 */
@JvmInline
value class SimpleWorkoutId(val value: String) {
    companion object {
        fun generate(): SimpleWorkoutId = SimpleWorkoutId("sw_${UUID.randomUUID()}")
        fun fromString(value: String): SimpleWorkoutId = SimpleWorkoutId(value)
    }
}

@JvmInline 
value class SimpleExerciseId(val value: String) {
    companion object {
        fun generate(): SimpleExerciseId = SimpleExerciseId("se_${UUID.randomUUID()}")
        fun fromString(value: String): SimpleExerciseId = SimpleExerciseId(value)
    }
}

/**
 * AI analysis data structures
 */
data class WorkoutAIData(
    val workoutId: String,
    val userId: String,
    val totalVolume: Double,
    val exerciseCount: Int,
    val averageRPE: Double?,
    val exerciseTypes: Set<String>,
    val duration: Duration?,
    val workoutDate: Instant
)

data class ExerciseAIData(
    val exerciseId: String,
    val name: String,
    val volume: Double,
    val intensity: Int?,
    val sets: Int,
    val reps: Int,
    val hasMedia: Boolean
)
```

### Repository Interface Design

```kotlin
/**
 * Repository interface for simple workout data access
 */
interface SimpleWorkoutRepository {
    /**
     * Creates a new workout for the specified user
     */
    suspend fun createWorkout(workout: SimpleWorkout): Result<Unit>
    
    /**
     * Retrieves a specific workout by ID (user-scoped)
     */
    suspend fun getWorkout(workoutId: SimpleWorkoutId, userId: String): Result<SimpleWorkout?>
    
    /**
     * Gets all workouts for a user, ordered by creation date
     */
    fun getWorkoutsForUser(userId: String): Flow<List<SimpleWorkout>>
    
    /**
     * Updates an existing workout (user-scoped)
     */
    suspend fun updateWorkout(workout: SimpleWorkout): Result<Unit>
    
    /**
     * Deletes a workout (user-scoped)
     */
    suspend fun deleteWorkout(workoutId: SimpleWorkoutId, userId: String): Result<Unit>
    
    /**
     * Gets workouts requiring sync for a user
     */
    suspend fun getUnsyncedWorkouts(userId: String): Result<List<SimpleWorkout>>
    
    /**
     * Marks workouts as synced after successful upload
     */
    suspend fun markWorkoutsSynced(workoutIds: List<SimpleWorkoutId>, syncVersion: Long): Result<Unit>
    
    /**
     * Gets workout data optimized for AI analysis
     */
    suspend fun getWorkoutAIData(userId: String, limit: Int = 100): Result<List<WorkoutAIData>>
    
    /**
     * Searches workouts by exercise name (for AI pattern analysis)
     */
    suspend fun findWorkoutsByExercise(userId: String, exerciseName: String): Result<List<SimpleWorkout>>
}
```

### Data Access Objects (DAOs)

```kotlin
/**
 * Room DAO for simple workout data access
 */
@Dao
interface SimpleWorkoutDao {
    @Transaction
    @Query("SELECT * FROM simple_workouts WHERE id = :workoutId AND user_id = :userId")
    suspend fun getWorkoutWithExercises(workoutId: String, userId: String): WorkoutWithExercises?
    
    @Query("SELECT * FROM simple_workouts WHERE user_id = :userId ORDER BY created_at DESC")
    fun getWorkoutsForUser(userId: String): Flow<List<SimpleWorkoutEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: SimpleWorkoutEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<SimpleExerciseEntity>)
    
    @Transaction
    suspend fun insertWorkoutWithExercises(workout: SimpleWorkoutEntity, exercises: List<SimpleExerciseEntity>) {
        insertWorkout(workout)
        insertExercises(exercises)
    }
    
    @Update
    suspend fun updateWorkout(workout: SimpleWorkoutEntity): Int
    
    @Query("DELETE FROM simple_workouts WHERE id = :workoutId AND user_id = :userId")
    suspend fun deleteWorkout(workoutId: String, userId: String): Int
    
    @Query("SELECT * FROM simple_workouts WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedWorkouts(userId: String): List<SimpleWorkoutEntity>
    
    @Query("UPDATE simple_workouts SET is_synced = 1, sync_version = :syncVersion WHERE id IN (:workoutIds)")
    suspend fun markWorkoutsSynced(workoutIds: List<String>, syncVersion: Long): Int
    
    // AI analysis queries
    @Query("""
        SELECT sw.*, se.* FROM simple_workouts sw 
        LEFT JOIN simple_exercises se ON sw.id = se.workout_id 
        WHERE sw.user_id = :userId 
        ORDER BY sw.created_at DESC 
        LIMIT :limit
    """)
    suspend fun getWorkoutDataForAI(userId: String, limit: Int): List<WorkoutWithExercises>
    
    @Query("""
        SELECT DISTINCT sw.* FROM simple_workouts sw 
        JOIN simple_exercises se ON sw.id = se.workout_id 
        WHERE sw.user_id = :userId AND LOWER(se.name) LIKE '%' || LOWER(:exerciseName) || '%'
        ORDER BY sw.created_at DESC
    """)
    suspend fun findWorkoutsByExerciseName(userId: String, exerciseName: String): List<WorkoutWithExercises>
}

/**
 * Room relationship data class
 */
data class WorkoutWithExercises(
    @Embedded val workout: SimpleWorkoutEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workout_id"
    )
    val exercises: List<SimpleExerciseEntity>
)
```

### Synchronization Strategy

**Sync Worker Implementation:**

```kotlin
@HiltWorker
class SimpleWorkoutSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val simpleWorkoutRepository: SimpleWorkoutRepository,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            val userId = authRepository.getCurrentUserId() 
                ?: return Result.failure()
            
            // Get unsynced workouts
            val unsyncedWorkouts = simpleWorkoutRepository.getUnsyncedWorkouts(userId)
                .getOrElse { return Result.retry() }
            
            if (unsyncedWorkouts.isEmpty()) {
                return Result.success()
            }
            
            // Sync to Firestore
            val syncVersion = System.currentTimeMillis()
            val workoutIds = mutableListOf<SimpleWorkoutId>()
            
            firestore.runBatch { batch ->
                unsyncedWorkouts.forEach { workout ->
                    val docRef = firestore
                        .collection("users")
                        .document(userId)
                        .collection("simple_workouts")
                        .document(workout.id.value)
                    
                    batch.set(docRef, workout.toFirestoreData(syncVersion))
                    workoutIds.add(workout.id)
                }
            }.await()
            
            // Mark as synced
            simpleWorkoutRepository.markWorkoutsSynced(workoutIds, syncVersion)
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Simple workout sync failed")
            Result.retry()
        }
    }
    
    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): SimpleWorkoutSyncWorker
    }
}
```

### Testing Strategy

**Test Scenarios:**
1. **Domain Model Validation**: Test all business rules and constraints
2. **Repository Operations**: Test CRUD operations with user scoping
3. **Database Integrity**: Test foreign key constraints and cascading deletes
4. **Sync Functionality**: Test offline/online sync with conflict resolution
5. **AI Data Export**: Test workout data conversion for AI analysis
6. **Performance**: Test query performance with large datasets

## Implementation Plan

### Task Breakdown

#### Database Layer (DB-001 to DB-004)
- [ ] **DB-001**: Create Room entities and relationships [Estimate: 4hr]
  - **Files**: `data/local/entity/SimpleWorkoutEntity.kt`, `SimpleExerciseEntity.kt`
  - **Details**: Room entities with constraints, relationships, future media fields

- [ ] **DB-002**: Implement DAO with user-scoped queries [Estimate: 4hr]
  - **Files**: `data/local/dao/SimpleWorkoutDao.kt`
  - **Details**: CRUD operations, complex queries for AI analysis, performance indexes

- [ ] **DB-003**: Database migration and configuration [Estimate: 3hr]
  - **Files**: `data/local/migration/Migration_5_to_6.kt`, update `LiftrixDatabase.kt`
  - **Details**: Migration script, database version update, index creation

- [ ] **DB-004**: Repository implementation [Estimate: 5hr]
  - **Files**: `data/repository/SimpleWorkoutRepositoryImpl.kt`
  - **Details**: Repository pattern, user scoping, offline-first implementation

#### Domain Layer (DM-001 to DM-003)
- [ ] **DM-001**: Core domain models [Estimate: 4hr]
  - **Files**: `domain/model/SimpleWorkout.kt`, `SimpleExercise.kt`
  - **Details**: Domain models with validation, business methods, AI data export

- [ ] **DM-002**: Value objects and AI data structures [Estimate: 2hr]
  - **Files**: `domain/model/SimpleWorkoutId.kt`, `WorkoutAIData.kt`
  - **Details**: Type-safe IDs, AI analysis data structures

- [ ] **DM-003**: Repository interface [Estimate: 2hr]
  - **Files**: `domain/repository/SimpleWorkoutRepository.kt`
  - **Details**: Repository contract with AI analysis methods

#### Integration Layer (INT-001 to INT-002)
- [ ] **INT-001**: Data mappers [Estimate: 3hr]
  - **Files**: `data/mapper/SimpleWorkoutMapper.kt`
  - **Details**: Entity-domain mapping, Firestore serialization

- [ ] **INT-002**: Sync worker implementation [Estimate: 4hr]
  - **Files**: `sync/SimpleWorkoutSyncWorker.kt`, Firestore rules
  - **Details**: Background sync, conflict resolution, error handling

#### Testing (TEST-001 to TEST-003)
- [ ] **TEST-001**: Domain model unit tests [Estimate: 3hr]
  - **Files**: `test/domain/model/`
  - **Details**: Validation tests, business logic tests, AI data export tests

- [ ] **TEST-002**: Repository integration tests [Estimate: 4hr]
  - **Files**: `androidTest/data/repository/`
  - **Details**: Database operations, user scoping, sync functionality

- [ ] **TEST-003**: Performance and stress tests [Estimate: 2hr]
  - **Files**: `androidTest/performance/`
  - **Details**: Large dataset queries, AI analysis performance

### Dependencies
- **DB-002 depends on DB-001**: DAO needs entities
- **DB-003 depends on DB-001**: Migration needs entity definitions
- **DB-004 depends on DB-002**: Repository needs DAO
- **DM-003 depends on DM-001**: Repository interface needs domain models
- **INT-001 depends on DM-001, DB-001**: Mappers need both domain and entity models
- **INT-002 depends on DB-004**: Sync worker needs repository
- **All TEST tasks depend on their respective implementation tasks**

## Success Metrics
- **Data Integrity**: 0% data corruption in production usage
- **Query Performance**: <50ms for workout list queries, <100ms for AI analysis queries
- **Sync Reliability**: 99%+ successful sync rate with automatic retry
- **Scalability**: Support 1000+ workouts per user without performance degradation
- **AI Ready**: Export workout data in <200ms for AI analysis processing

## Timeline
**Total Effort**: 40 hours (3-4 developer days)
**Critical Path**: DB-001 → DB-002 → DB-003 → DB-004 → DM-001 → INT-001 → TEST-002 (25 hours)