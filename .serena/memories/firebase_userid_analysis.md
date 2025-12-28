# Firebase Integration & UserId Serialization Analysis

## UserId Type Definition
**File**: `app/src/main/java/com/example/liftrix/domain/model/UserId.kt`

```kotlin
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "User ID cannot be blank" }
    }
    companion object {
        fun fromString(value: String): UserId = UserId(value)
    }
    override fun toString(): String = value
}
```

**Key Characteristics:**
- Inline value class (zero-runtime overhead)
- NOT currently @Serializable (CRITICAL: Needs serialization support for Firebase/network)
- Has `toString()` override for logging/conversion
- Used throughout codebase but no native Gson/Kotlinx Serialization support

## Firebase Integration Points

### 1. FirebaseDataSource Interface & Implementation
**Files**: 
- `app/src/main/java/com/example/liftrix/data/remote/FirebaseDataSource.kt` (interface)
- `app/src/main/java/com/example/liftrix/data/remote/FirebaseDataSourceImpl.kt` (impl)

**UserId Usage Pattern:**
```kotlin
interface FirebaseDataSource {
    suspend fun create(userId: String, entityType: String, entityId: String, data: String): ProcessResult
    suspend fun update(userId: String, entityType: String, entityId: String, data: String): ProcessResult
    suspend fun delete(userId: String, entityType: String, entityId: String): ProcessResult
    suspend fun fetch(userId: String, entityType: String, entityId: String): ProcessResult
    suspend fun fetchAll(userId: String, entityType: String): ProcessResult
}
```

**Current Implementation:**
- Receives userId as String (not UserId type)
- Uses Kotlinx Serialization Json for JSON conversion: `json.parseToJsonElement(jsonString)`
- Converts Firestore Map to JSON via `mapToJsonString()`: handles Timestamp, GeoPoint, DocumentReference
- Validates userId against Firebase Auth: `currentUser?.uid == userId`
- User-scoped collection structure: `users/{userId}/workouts/{workoutId}`

### 2. SyncRepository
**File**: `app/src/main/java/com/example/liftrix/data/repository/SyncRepositoryImpl.kt`

**Key Points:**
- Injected with Kotlinx Serialization `Json` instance
- Uses `json.encodeToString()` for serialization
- Passes userId as String to FirebaseDataSource
- No UserId type wrapping in public API

### 3. Sync Workers (WorkManager Integration)
**Base Worker**: `app/src/main/java/com/example/liftrix/sync/BaseSyncWorker.kt`

**UserId Passing Pattern:**
```kotlin
companion object {
    const val KEY_USER_ID = "userId"
    const val KEY_SYNC_COUNT = "sync_count"
}

val userId = inputData.getString(KEY_USER_ID)
```

**Workers Using UserId:**
- MasterSyncWorker
- WorkoutSyncWorker
- TemplateSyncWorker
- ProfileSyncWorker
- AchievementSyncWorker
- SocialProfileSyncWorker
- FollowRelationshipSyncWorker
- WorkoutPostSyncWorker
- GymBuddySyncWorker
- SettingsSyncWorkerV2

**Pattern**: Pass userId via `workDataOf(KEY_USER_ID to userId)` to WorkManager

### 4. Firebase Firestore Collection Schema
```
/users/{userId}
  /workouts/{workoutId}
  /templates/{templateId}
  /achievements/{achievementId}
  /profiles
/social_profiles/{userId}
/follow_relationships/{id}
/workout_posts/{postId}
```

## Serialization Configuration

### JSON Library Setup (FirebaseModule)
**File**: `app/src/main/java/com/example/liftrix/di/FirebaseModule.kt`

```kotlin
@Provides
@Singleton
fun provideJson(): Json {
    return Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        prettyPrint = false
        coerceInputValues = true
    }
}

@Provides
@Singleton
fun provideGson(): Gson {
    return GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .setLenient()
        .registerTypeAdapter(ExerciseSet::class.java, ExerciseSetDeserializer())
        .registerTypeAdapter(Exercise::class.java, ExerciseDeserializer())
        .create()
}
```

### Serialization Libraries
- **Kotlinx Serialization**: Primary (JSON via kotlinx-serialization-json)
- **Gson**: Secondary (for legacy compatibility and custom type adapters)

### @Serializable Usage in Codebase
**Payload Classes:**
- `SyncPayload` (sealed class with WorkoutPayload, ProfilePayload, etc.)
- `ExerciseDto`, `SetDto`, `WorkoutSyncDto`
- `PRNotificationData`, `GymBuddyWelcomeData`
- Navigation routes (`LiftrixRoute` and all @Serializable subclasses)

**Key Pattern**: All network/sync DTOs are @Serializable marked

## Network Layer Integration

### HTTP Client Configuration
**File**: `app/src/main/java/com/example/liftrix/core/network/CertificatePinningConfig.kt`

```kotlin
fun createSecureOkHttpClient(): OkHttpClient {
    // Certificate pinning for Firebase endpoints
    // Timeout configurations
    // No custom userId serialization (passed as String)
}
```

### Firebase Auth Integration
- Direct `FirebaseAuth.getInstance()` usage
- userId extracted as `currentUser?.uid` (String)
- No custom serialization needed (Firebase handles Auth tokens)

## Critical Findings - UserId Serialization Gaps

### 1. UserId Type NOT @Serializable
- Inline value class cannot be easily serialized by Kotlinx
- Gson has no custom deserializer for UserId
- All Firebase operations use String, not UserId type

### 2. Sync Operation UserId Handling
- WorkManager Data uses String: `workDataOf(KEY_USER_ID to userId)`
- No type safety at sync layer
- Validation only at FirebaseDataSourceImpl: `isUserAuthenticated(userId)`

### 3. Payload Serialization
- SyncPayload uses String userId, not UserId type
- Example: `data class ProfilePayload(val userId: String, ...)`
- No automatic conversion from UserId to String

## Network Layer Patterns

### No Retrofit/HTTP Client with UserId
- Firebase directly uses Auth tokens, not custom userId headers
- UserId passed as:
  - Firestore document path component
  - WorkManager Data strings
  - JSON object fields as String

### Firebase Storage Integration
- 30s timeout for uploads
- UserId passed as String in paths: `users/{userId}/profile_image`
- No serialization configuration needed

## Recommendations for UserId Serialization

### Option 1: Add @Serializable to UserId (RECOMMENDED)
```kotlin
@Serializable
@JvmInline
value class UserId(val value: String) { ... }
```

**Pros**: Type-safe, automatic serialization
**Cons**: Requires Kotlinx Serialization setup for value classes

### Option 2: Custom Gson Adapter
```kotlin
class UserIdDeserializer : JsonDeserializer<UserId> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserId {
        return UserId(json.asString)
    }
}
```

### Option 3: Keep String in Serialization Layer
**Current approach**: UserId converted to String at domain/data boundary
**Risk**: Less type safety, but compatible with Firebase

## Database (Room) Integration

### UserId in Entity Classes
- Stored as String in Room entities (no serialization needed)
- User scoping: All queries filter by `user_id` field
- Example: `SELECT * FROM workouts WHERE user_id = :userId`

### Migration Path
- Room uses String columns for user IDs
- No serialization configuration in Room
- UserId.toString() provides conversion

## Summary of UserId Serialization Points

| Component | UserId Type | Serialization | Notes |
|-----------|------------|---------------|-------|
| FirebaseDataSource | String | Json/Map conversion | No UserId type |
| SyncWorkers | String | WorkManager Data | Passed as String |
| Sync Payloads | String | @Serializable DTOs | Not type-safe |
| Room Entities | String | Native Room support | No serialization |
| Navigation Routes | String (in URLs) | @Serializable sealed class | Type-safe routing |
| Firebase Auth | Token-based | Firebase internal | No custom serialization |
| Firestore Paths | String | Path component | Direct string interpolation |
| FCM Tokens | String | JSON fields | In notification payloads |

## Implementation Status

### ✅ Working
- Firebase authentication (token-based)
- Firestore document operations (String userId in paths)
- WorkManager sync operations (String userId in Data)
- Room database queries (String user_id)

### ⚠️ Needs Enhancement
- UserId type NOT @Serializable (should be for type safety)
- No custom Gson deserializer for UserId
- Sync payload uses String, not UserId type
- Network headers don't use custom UserId serialization

### 🔴 Potential Issues
- Type safety lost at serialization boundary
- No compile-time checking for userId format
- Validation only at runtime (isUserAuthenticated)
