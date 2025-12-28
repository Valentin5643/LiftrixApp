# Room Database Schema & TypeConverter Analysis

## Project Status
- **Location**: C:\Users\Administrator\LiftrixApp
- **Database Class**: LiftrixDatabase.kt
- **Database Version**: 3 (exportSchema = true)
- **Total Entities**: 61 entities in @Database annotation
- **Total DAOs**: 66 DAO interfaces
- **Entities with userId**: 61 files found with user_id/userId references

## 1. Room Database Setup

### Database Class Location
**File**: `C:\Users\Administrator\LiftrixApp\app\src\main\java\com\example\liftrix\data\local\LiftrixDatabase.kt`

### Database Configuration
```kotlin
@Database(
    entities = [ /* 61 entity classes */ ],
    version = 3,
    exportSchema = true
)
@TypeConverters(
    DateTimeConverters::class,
    WorkoutConverters::class,
    UserProfileConverters::class,
    ExerciseConverters::class,
    SubscriptionConverters::class,
    WeightUnitConverter::class
)
abstract class LiftrixDatabase : RoomDatabase() {
    // 66 abstract fun methods for DAO access
}
```

## 2. Existing TypeConverters

### Location
`C:\Users\Administrator\LiftrixApp\app\src\main\java\com\example\liftrix\data\local\converter`

### Available Converters (6 total)
1. **DateTimeConverters.kt** - Date/time type handling
2. **ExerciseConverters.kt** - Exercise-related types
3. **SubscriptionConverters.kt** - Subscription-related types
4. **UserProfileConverters.kt** - User profile types
5. **WeightUnitConverter.kt** - Enum converter for WeightUnit
6. **WorkoutConverters.kt** - Workout-related types

### TypeConverter Pattern Example

#### DateTimeConverters Pattern
```kotlin
class DateTimeConverters @Inject constructor() {
    
    @TypeConverter
    fun fromInstant(instant: Instant?): String? {
        return instant?.toString()
    }
    
    @TypeConverter
    fun toInstant(instantString: String?): Instant? {
        return instantString?.let { timeString ->
            try {
                Instant.parse(timeString)  // ISO format
            } catch (e: Exception) {
                try {
                    Instant.ofEpochMilli(timeString.toLong())  // Epoch millis fallback
                } catch (e2: Exception) {
                    Timber.e("Failed to parse timestamp...")
                    null
                }
            }
        }
    }
}
```

#### WeightUnitConverter Pattern (Enum)
```kotlin
class WeightUnitConverter {
    
    @TypeConverter
    fun fromWeightUnit(weightUnit: WeightUnit): String {
        return weightUnit.name
    }
    
    @TypeConverter
    fun toWeightUnit(value: String): WeightUnit {
        return try {
            WeightUnit.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            WeightUnit.fromSymbol(value) ?: WeightUnit.getSystemDefault()
        }
    }
}
```

## 3. userId Field Patterns

### Current Implementation Pattern

All 61 entities follow this pattern:

```kotlin
@Entity(
    tableName = "table_name",
    indices = [
        Index(value = ["user_id"], ...) // userId is indexed
    ]
)
data class EntityName(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,  // Always present
    
    // ... other fields
)
```

### Entities with userId Field (61 total)
1. WorkoutEntity
2. PersonalRecordEntity
3. SyncQueueEntity
4. DeadLetterQueueEntity
5. ChatHistoryEntity
6. ChatPreferencesEntity
7. SyncPreferencesEntity
8. UserProfileCacheEntity
9. PRReactionEntity
10. PRNotificationPreferencesEntity
11. CustomExerciseEntity
12. WorkoutPostEntity
13. FeedCacheEntity
14. AnomalyDetectionSettingsEntity
15. SupportTicketMessageEntity
16. FollowRelationshipEntity
17. SocialProfileEntity
18. QRCodeSessionEntity
19. ProgressPhotoEntity
20. SavedPostEntity
21. PostCommentEntity
22. PostLikeEntity
23. BlockedUserEntity
24. SettingsEntity
25. SettingsAuditEntity
26. SupportTicketEntity
27. DataImportEntity
28. DataExportEntity
29. UserAccountEntity
30. ContentReportEntity
31. GymBuddyEntity
32. GymBuddyActivityEntity
33. PRNotificationEntity
34. ExternalShareEntity
35. SharedRoutineEntity
36. MediaItemEntity
37. NotificationMuteEntity
38. NotificationPreferenceEntity
39. NotificationHistoryEntity
40. NotificationQueueEntity
41. FCMTokenEntity
42. FollowRequestEntity
43. ProfileViewEntity
44. SocialPrivacySettingsEntity
45. DashboardConfigurationEntity
46. WorkoutTemplateEntity
47. WorkoutAnomalyEntity
48. WidgetPreferenceEntity
49. UserSearchCacheEntity
50. UserProfileEntity
51. UserAchievementEntity
52. SubscriptionEntity
53. QRCodeMappingEntity
54. PrivacySettingsEntity
55. GuestSessionEntity
56. FriendEntity
57. FolderEntity
58. ExerciseWeightMemoryEntity
59. ExerciseUsageHistoryEntity
60. ExerciseHistoryEntity
61. AnalyticsCacheEntity

## 4. DAO User-Scoping Patterns

### Location
`C:\Users\Administrator\LiftrixApp\app\src\main\java\com\example\liftrix\data\local\dao`

### Total DAOs: 66

### Standard DAO Query Pattern (from WorkoutDao.kt example)
```kotlin
@Dao
interface WorkoutDao {
    
    // Flow-based queries with user scoping
    @Query("""
        SELECT * FROM workouts 
        WHERE user_id = :userId 
        ORDER BY updated_at DESC, date DESC, created_at DESC
    """)
    fun getAllWorkoutsForUser(userId: String): Flow<List<WorkoutEntity>>
    
    // Suspend queries with user scoping
    @Query("SELECT * FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun getWorkoutByIdForUser(id: String, userId: String): WorkoutEntity?
    
    // Update with user scoping
    @Query("UPDATE workouts SET is_synced = 0 WHERE user_id = :userId")
    suspend fun markAllWorkoutsAsUnsyncedForUser(userId: String): Int
    
    // Delete with user scoping
    @Query("DELETE FROM workouts WHERE id = :id AND user_id = :userId")
    suspend fun deleteWorkoutByIdForUser(id: String, userId: String): Int
}
```

### User-Scoping Enforcement Pattern
**All DAO methods follow this pattern:**
- CRUD parameter includes `:userId`
- All WHERE clauses filter by `user_id = :userId`
- DELETE operations require both `id` and `userId` matching
- Prevents cross-user data leakage

## 5. Build Dependencies

### Serialization Libraries in Use

From app/build.gradle.kts:

1. **Kotlin Serialization Plugin**: `alias(libs.plugins.kotlin.serialization)`
2. **Kotlinx Coroutines**: 1.8.1
3. **Room Database**: (specified in libs version catalog)
4. **Kotlin Parcelize**: `id("kotlin-parcelize")`

### TypeConverter Injection Pattern
- Converters use `@Inject constructor()` (Hilt dependency injection)
- Non-Hilt converters (like WeightUnitConverter) use default constructor
- All converters are registered in @TypeConverters annotation on database class

## 6. Database Indexing Strategy

### User-Scoped Index Examples from WorkoutEntity
```kotlin
Index(value = ["user_id", "date", "status"], name = "idx_workout_analytics")
Index(value = ["user_id", "created_at"], name = "idx_workout_user_created")
Index(value = ["user_id", "status"], name = "idx_workout_user_status")
Index(value = ["is_synced", "sync_version"], name = "idx_workout_sync")
Index(value = ["user_id", "is_synced", "updated_at"], name = "idx_workout_sync_operations")
Index(value = ["user_id", "updated_at", "date", "created_at"], name = "idx_workout_history_optimized")
```

**Pattern**: Composite indexes always start with `user_id` for partition efficiency

## 7. Sync Metadata Fields

### All Syncable Entities Include
```kotlin
@ColumnInfo(name = "is_synced", defaultValue = "0")
val isSynced: Boolean = false

@ColumnInfo(name = "sync_version", defaultValue = "0")
val syncVersion: Long = 0L
```

### Purpose
- Track sync status to Firebase
- Enable conflict resolution (last-write-wins via syncVersion)
- Queue operations for background sync workers

## 8. TypeConverter Integration Points

### Database Registration
- TypeConverters specified in `@TypeConverters(...)` annotation on @Database class
- Currently 6 converters registered
- New converters must be added to this list

### Entity-Level TypeConverters
- Entities can override with `@TypeConverters(...)` on class
- Example: `WorkoutEntity` uses DateTimeConverters + WorkoutConverters

### Recommended for UserId TypeConverter
1. Create: `C:\Users\Administrator\LiftrixApp\app\src\main\java\com\example\liftrix\data\local\converter\UserIdConverter.kt`
2. Add to @Database `@TypeConverters(UserIdConverter::class, ...)`
3. Optional: Add `@TypeConverters(UserIdConverter::class)` to specific entities if needed

## 9. Key Observations for UserId Inline Value Class Integration

### Current State
- userId stored as `String` in all 61 entities
- ColumnInfo name is always "user_id"
- No type safety - easily confused with other strings

### TypeConverter Path for UserId
When introducing `UserId` inline value class:
```kotlin
// New converter pattern
class UserIdConverter {
    @TypeConverter
    fun fromUserId(userId: UserId?): String? {
        return userId?.value  // Assuming UserId has .value property
    }
    
    @TypeConverter
    fun toUserId(value: String?): UserId? {
        return value?.let { UserId(it) }
    }
}
```

### Migration Strategy
1. Add UserIdConverter without database schema change (pure type wrapper)
2. Register in @Database @TypeConverters annotation
3. Update entity properties: `val userId: String` → `val userId: UserId`
4. Room will use TypeConverter automatically for serialization
5. No database migration needed (storage format unchanged)

## 10. Import Dependencies

### Room & TypeConverter Imports
```kotlin
import androidx.room.TypeConverter
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
```

### Injection Pattern
```kotlin
import javax.inject.Inject  // For converters if using Hilt
```

### Coroutines for DAO
```kotlin
import kotlinx.coroutines.flow.Flow
```

## Database Schema Version History
- Current: v3
- exportSchema: true (migrations saved to git)
- No schema migration needed for TypeConverter addition
