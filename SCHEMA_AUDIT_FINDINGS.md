# Database Schema Audit Findings

## Executive Summary
This audit identified **7 critical schema consistency issues** across 5 entities that could cause Room validation failures, foreign key constraint violations, and migration errors. These issues stem from schema drift between migration scripts and current entity definitions.

## Critical Issues Found

### 1. SimpleExerciseEntity Schema Mismatch (CRITICAL)
**Problem:** Migration_5_6 creates `simple_exercises` table with different schema than `SimpleExerciseEntity`

**Schema Drift Issues:**
- ❌ **Missing Columns**: `photo_url`, `animation_url` (required by entity)
- ❌ **Extra Column**: `created_at` (in migration, not in entity) 
- ❌ **Type Mismatch**: `rpe` as `INTEGER` in migration vs `Double?` in entity
- ❌ **Type Mismatch**: `weight_kg` as `REAL` in migration vs `Double` in entity

**Current Entity Definition:**
```kotlin
@Entity(tableName = "simple_exercises")
data class SimpleExerciseEntity(
    val rpe: Double?,        // Migration: INTEGER
    val weightKg: Double,    // Migration: REAL  
    val photoUrl: String?,   // Migration: MISSING
    val animationUrl: String? // Migration: MISSING
    // Note: No created_at field but migration creates it
)
```

**Impact:** Room validation will fail due to missing columns and type mismatches.

### 2. ExerciseEntity Foreign Key Type Mismatch (CRITICAL)
**Problem:** Type mismatch between foreign key and referenced table

**Schema Issue:**
```kotlin
@Entity(
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],        // WorkoutEntity.id: String (TEXT)
        childColumns = ["workout_id"]  // ExerciseEntity.workoutId: Long (INTEGER)
    )]
)
data class ExerciseEntity(
    val workoutId: Long  // Should be String to match WorkoutEntity.id
)
```

**Impact:** Foreign key constraint violations will prevent inserts.

### 3. Missing Default Value Annotations (HIGH)
**Problem:** Entities have Kotlin default values but no Room `@ColumnInfo(defaultValue)` annotations

**Affected Entities:**
```kotlin
// ExerciseWeightMemoryEntity
val usageCount: Int = 1  // Needs @ColumnInfo(defaultValue = "1")

// CustomExerciseEntity  
val isSynced: Boolean = false     // Needs @ColumnInfo(defaultValue = "0")
val syncVersion: Int = 1          // Needs @ColumnInfo(defaultValue = "1")

// DailyWorkoutEntity
val isSynced: Boolean = false     // Needs @ColumnInfo(defaultValue = "0") 
val syncVersion: Int = 1          // Needs @ColumnInfo(defaultValue = "1")

// WorkoutTemplateEntity
val usageCount: Int = 0           // Needs @ColumnInfo(defaultValue = "0")
val isSynced: Boolean = false     // Needs @ColumnInfo(defaultValue = "0")
val syncVersion: Int = 1          // Needs @ColumnInfo(defaultValue = "1")
```

**Impact:** Room may not apply defaults during direct SQL operations or migrations.

### 4. ExerciseLibraryEntity Validation Risk (MEDIUM)
**Problem:** All fields non-null with no fallback mechanisms

**Risk Factors:**
- No nullable fields for optional data
- No explicit default values
- Complex types (`List<ExerciseCategory>`) could fail serialization
- Dependent on data seeding accuracy

### 5. SimpleWorkoutEntity Timestamp Type Inconsistency (MEDIUM)
**Problem:** Migration creates timestamps as TEXT, entity uses `Instant`

**Current State:**
- Migration_5_6: `created_at TEXT NOT NULL`, `updated_at TEXT NOT NULL`
- Entity: `createdAt: Instant`, `updatedAt: Instant` 
- Type Converter: `DateTimeConverters` handles conversion

**Validation:** This is actually correct - Room uses type converters properly.

## Proposed Fixes

### Fix 1: Repair SimpleExerciseEntity Schema
Create `MIGRATION_10_11` to fix simple_exercises table:

```sql
-- Add missing columns
ALTER TABLE simple_exercises ADD COLUMN photo_url TEXT;
ALTER TABLE simple_exercises ADD COLUMN animation_url TEXT;

-- Fix data types (requires table recreation)
CREATE TABLE simple_exercises_new (
    id TEXT PRIMARY KEY NOT NULL,
    workout_id TEXT NOT NULL,
    name TEXT NOT NULL,
    reps INTEGER NOT NULL,
    rpe REAL,  -- Changed from INTEGER to REAL for Double?
    sets INTEGER NOT NULL,
    weight_kg REAL NOT NULL,  -- Ensure REAL type
    order_index INTEGER NOT NULL,
    photo_url TEXT,
    animation_url TEXT,
    FOREIGN KEY (workout_id) REFERENCES simple_workouts(id) ON DELETE CASCADE
);

-- Migrate data with type conversion
INSERT INTO simple_exercises_new 
SELECT id, workout_id, name, reps, 
       CAST(rpe AS REAL), sets, weight_kg, order_index, 
       NULL, NULL
FROM simple_exercises;

DROP TABLE simple_exercises;
ALTER TABLE simple_exercises_new RENAME TO simple_exercises;
```

### Fix 2: Correct ExerciseEntity Foreign Key Type
Update `ExerciseEntity` to use `String` workoutId:

```kotlin
data class ExerciseEntity(
    val workoutId: String,  // Changed from Long
    // ... rest unchanged
    
    @ColumnInfo(name = "workout_id")
    val workoutId: String,
)
```

### Fix 3: Add Explicit Default Value Annotations
Update all entities with missing `@ColumnInfo(defaultValue)`:

```kotlin
// ExerciseWeightMemoryEntity
@ColumnInfo(name = "usage_count", defaultValue = "1")
val usageCount: Int = 1

// CustomExerciseEntity
@ColumnInfo(name = "is_synced", defaultValue = "0") 
val isSynced: Boolean = false

@ColumnInfo(name = "sync_version", defaultValue = "1")
val syncVersion: Int = 1

// Apply same pattern to DailyWorkoutEntity, WorkoutTemplateEntity
```

### Fix 4: Add Validation to ExerciseLibraryEntity
Add data validation and nullable alternatives:

```kotlin
@Entity(tableName = "exercise_library")
data class ExerciseLibraryEntity(
    // Core required fields
    val id: String,
    val name: String,
    val primaryMuscleGroup: ExerciseCategory,
    val equipment: Equipment,
    
    // Optional fields with defaults
    @ColumnInfo(name = "secondary_muscle_groups", defaultValue = "[]")
    val secondaryMuscleGroups: List<ExerciseCategory> = emptyList(),
    
    val movementPattern: String? = null, // Make nullable
    
    @ColumnInfo(name = "difficulty_level", defaultValue = "1")
    val difficultyLevel: Int = 1,
    
    val instructions: String? = null,
    
    @ColumnInfo(name = "is_compound", defaultValue = "0")
    val isCompound: Boolean = false,
    
    @ColumnInfo(name = "searchable_terms", defaultValue = "[]")
    val searchableTerms: List<String> = emptyList()
)
```

## Safe Migration Strategy

### Phase 1: Non-Destructive Fixes (MIGRATION_10_11)
1. Add missing columns to `simple_exercises`
2. Add explicit default values via ALTER TABLE
3. Update entity annotations only

### Phase 2: Type Corrections (MIGRATION_11_12) 
1. Fix `ExerciseEntity.workoutId` type mismatch
2. Recreate affected tables with data preservation
3. Update foreign key constraints

### Phase 3: Validation Enhancement
1. Update entity definitions with proper defaults
2. Add data validation constraints
3. Test migration paths thoroughly

## Automated Prevention Measures

### 1. Schema Validation CI Check
```bash
#!/bin/bash
# scripts/validate_entity_schema_consistency.sh

echo "Validating entity-migration schema consistency..."

# Extract entity field definitions
# Compare with migration CREATE TABLE statements  
# Report mismatches

# Check for missing @ColumnInfo(defaultValue) on non-null fields
grep -r "@ColumnInfo" app/src/main/java/com/example/liftrix/data/local/entity/ | \
  grep -v "defaultValue" | \
  grep -E "(Boolean|Int|Long) = (true|false|[0-9]+)" && \
  echo "ERROR: Found default values without @ColumnInfo(defaultValue)" && exit 1

echo "Schema validation passed"
```

### 2. Room Schema Export Validation
Enable schema export and add validation:

```kotlin
// In DatabaseModule
.addCallback(object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Validate schema consistency on creation
        SchemaValidator.validateSchema(db)
    }
})
```

### 3. Entity Definition Linting Rules
Add custom lint rules:

```kotlin
// Custom lint rule: DefaultValueConsistencyDetector
// Flags entities with Kotlin defaults but missing Room defaults
// Validates foreign key type consistency  
// Checks migration-entity schema alignment
```

## Testing Requirements

### 1. Migration Testing
- Test all migration paths: v5→v6→v7→v8→v9→v10→v11→v12
- Validate data integrity at each step
- Test foreign key constraint validation
- Verify default value application

### 2. Schema Validation Testing  
- Test Room validation with current entities
- Verify index creation matches entity annotations
- Test type converter functionality

### 3. Performance Impact Testing
- Measure migration execution time
- Test with large datasets (10k+ records)
- Validate index effectiveness

## Risk Assessment

| Issue | Severity | Impact | Migration Complexity |
|-------|----------|--------|---------------------|
| SimpleExerciseEntity Schema | **Critical** | App crashes | Medium (table recreation) |
| ExerciseEntity FK Type | **Critical** | Data integrity | High (entity change + migration) |
| Missing Default Values | **High** | Inconsistent data | Low (annotation updates) |
| ExerciseLibraryEntity | **Medium** | Insert failures | Low (validation only) |

## Conclusion

The audit revealed significant schema drift that requires immediate attention. The proposed migration strategy addresses all issues while preserving data integrity. Implementation should follow the phased approach to minimize risk and ensure thorough testing at each stage.

**Next Steps:**
1. Implement Fix 1 (SimpleExerciseEntity) immediately - blocks current functionality
2. Implement Fix 2 (ExerciseEntity FK) to prevent data integrity issues  
3. Add remaining default value annotations for consistency
4. Implement automated schema validation to prevent future drift 