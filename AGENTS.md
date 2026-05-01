# Repository Guidelines

This document serves as the contributor guide for this repository.

Last source audit: 2026-05-01. When this guide conflicts with source, trust the source and update this file.

## Build & Development Commands

### Essential Build Commands
```bash
# Build and compile
./gradlew assembleDebug              # Build debug APK
./gradlew compileDebugKotlin        # Compile Kotlin code only
./gradlew build                     # Full build with tests
./gradlew validateRoomQueries       # Custom Room query validation task
./gradlew lintFull                  # Comprehensive lint, including custom architecture checks
```

### Common Development Tasks
- Gradle sync issues: Run `./gradlew --stop` then rebuild
- Compilation errors: Check `./gradlew compileDebugKotlin` for detailed errors
- Run on device: `./gradlew installDebug` after building
- Force sync: Trigger immediate sync with `SyncCoordinator.triggerImmediateSync(userId)`; it is a no-op while `StartupRestoreGate` has not completed
- Check sync queue: Query `SELECT * FROM sync_queue WHERE user_id = ?` to see pending operations

## Project Architecture

### High-Level System Design
```
UI Layer (Jetpack Compose)
    ↓ StateFlow<UiState<T>>
ViewModel Layer (ModernBaseViewModel<S> or direct ViewModel)
    ↓ LiftrixResult<T>
Use Case Layer (80 use case files)
    ↓ LiftrixResult<T>
Repository Layer (47 repository contracts)
    ↓ Flow<Entity>
DAO Layer (68 Room DAO accessors with user scoping)
    ↓ SQL
Room Database (69 registered entities, version 9)
    ↓ Background sync via WorkManager
Firebase services + Firestore/Storage/FCM/AI sync integrations
```

### Critical Architectural Rules

#### 1. User Scoping (MANDATORY)
ALL user-owned database operations MUST filter by userId to prevent data leakage:
```kotlin
// CORRECT - Always include userId
@Query("SELECT * FROM workouts WHERE user_id = :userId")
suspend fun getWorkoutsForUser(userId: String): List<WorkoutEntity>

// WRONG - Missing user scoping causes data leaks
@Query("SELECT * FROM workouts")
suspend fun getAllWorkouts(): List<WorkoutEntity>
```

#### 2. Error Handling Pattern
Use `LiftrixResult<T>` for domain/data operations. In current source, `LiftrixResult<T>` is a typealias to Kotlin `Result<T>`, so use `fold`, `onSuccess`, `onFailure`, `getOrElse`, `exceptionOrNull`, and project helpers such as `liftrixCatching`, `liftrixSuccess`, and `liftrixFailure`.
```kotlin
suspend fun invoke(request: Request): LiftrixResult<Response> = liftrixCatching(
    errorMapper = { throwable ->
        LiftrixError.BusinessLogicError(
            code = "OPERATION_FAILED",
            errorMessage = "Operation failed",
            analyticsContext = mapOf("operation" to "OPERATION_NAME")
        )
    }
) {
    // Business logic here
}
```

#### 3. Navigation (Type-Safe)
Use @Serializable sealed classes for routes:
```kotlin
@Serializable
sealed class LiftrixRoute {
    @Serializable data object Home : LiftrixRoute()
    @Serializable data class WorkoutDetails(val workoutId: String) : LiftrixRoute()
}
```

Active routing is in `UnifiedNavigationContainer`. Before adding links, verify the destination is registered there. Known source-audit issues: duplicate `WorkoutDetails`, placeholder `AuthSignUp`/`AuthSignIn`, and `Needs verification` routes such as `DashboardCustomization`, `SocialOnboarding`, `PrivacySettings`, and calorie detail routes.

## Code Organization

### Module Structure
- core/: Cross-cutting concerns (network, design system, common)
- data/: Repository implementations, DAOs, entities
- domain/: Use cases, repository interfaces, domain models
- ui/: Compose screens, ViewModels, navigation
- di/: 8 active Hilt modules plus non-Hilt `TimerModule.kt` helper

### Key Components & Their Roles

#### Domain Layer
- Use Cases: Single responsibility business operations (80 use case files, including consolidated and specialized use cases)
- Models: Domain entities with business logic
- Repository Interfaces: Contracts for data operations

#### Data Layer
- Repositories: Implement domain interfaces with error handling
- DAOs: Room database access with mandatory user scoping
- Entities: Database models with sync metadata

#### UI Layer
- Screens: Jetpack Compose UI with Material 3
- ViewModels: StateFlow-based classes, commonly extending `ModernBaseViewModel<S>` or AndroidX `ViewModel` directly
- Navigation: Type-safe with Navigation Compose

## Design System

### Color System (V2 - Production)
Always use LiftrixColorsV2 for new development:
- Primary: Teal (#20C9B7)
- Secondary: Indigo (#2A3B7D)
- Surface colors for semantic UI

### Component Hierarchy
- UnifiedWorkoutCard: Base card component (12dp radius)
- ModernActionButton: Three-tier buttons (Primary/Secondary/Tertiary)
- LiftrixSpacing: Semantic spacing (16dp/12dp/8dp)

### Deprecated Items
Widget IDs to filter out are defined in `AnalyticsWidget.DEPRECATED_WIDGET_IDS`. Current removed IDs include:
`calories_burned`, `daily_calories`, `weekly_calorie_trend`, `consistency_streak`, `duration_chart`, `set_completion_rate`, `exercise_variety`, `training_intensity`, `goal_achievement`, `weekly_trends`, `time_of_day_analysis`, `recovery_patterns`, `performance_analysis`

## Debug & Extend Hot Zones

### Common Debug Points
1. Authentication Issues: `AuthQueryUseCase` and auth state gates - Check cold-start handling and profile-existence checks
2. Sync Problems: `SyncCoordinator`, `UnifiedSyncWorker`, startup worker chain, and `OfflineQueueManager` - Check restore gate, queue state, and conflict resolution
3. Performance Issues: `GetWidgetDataUseCase`, analytics services, and chart/widget ViewModels - Check per-widget calculation, caching, and dispatcher usage
4. Navigation Errors: `UnifiedNavigationContainer` - Check route registration, duplicate `WorkoutDetails`, and `Needs verification` routes documented in `docs/architecture.md`

### Adding New Features

#### New Screen Checklist
1. Add route to `LiftrixRoute` sealed class
2. Create ViewModel extending `ModernBaseViewModel<S>` when the shared state helper fits, or AndroidX `ViewModel` directly when the feature needs a custom pattern
3. Register in `UnifiedNavigationContainer`
4. Use existing UI components from design system

#### New Database Entity Checklist
1. Create entity with `user_id` field for user-owned data; global/reference entities need explicit justification and should not expose user data
2. Add sync metadata fields when the entity is synced (`isSynced`, `syncVersion`, `lastModified`, dirty/failure fields as appropriate)
3. Create DAO with user-scoped queries and sync status methods
4. Update database version if adding new entities (currently version 9)
5. Register in `LiftrixDatabase` and Hilt modules
6. Decide whether the entity needs `SyncableEntity`; source currently mixes interface-based and field-based sync metadata, so verify the local pattern before adding the interface
7. Wire sync through `SyncCoordinator`/`UnifiedSyncWorker` or a specialized worker only when the entity requires background sync

## Known Issues & Workarounds

### Critical Issues
1. Navigation Registration Drift: `UnifiedNavigationContainer` contains duplicate `WorkoutDetails`, placeholder auth routes, and some defined routes without active registrations
   - Fix: Verify active graph registration before adding links or new destinations

2. Debug Logging in Production: Found in authentication flows
   - Fix: Remove Timber.d() calls from production code

3. Room Migration Risk: Database is version 9 and `fallbackToDestructiveMigration()` is configured
   - Fix: Add explicit migrations and review production data preservation before schema changes

### Performance Bottlenecks
- Some analytics calculations still on main thread (use Dispatchers.IO)
- Missing composite indexes for social queries
- UnifiedWorkoutSessionManager potential memory retention

## Critical Compilation Error Prevention

### Mandatory Pre-Commit Checks
Always run before committing social/UI features:
```bash
./gradlew compileDebugKotlin  # MUST pass before any commits
```

### LiftrixError Constructor Patterns (CRITICAL)
ALL error instantiations MUST use correct constructor parameters:
```kotlin
// CORRECT - ValidationError pattern
LiftrixError.ValidationError(
    field = "username",
    violations = listOf("Failed to check availability"),
    analyticsContext = mapOf("operation" to "CHECK_USERNAME_AVAILABILITY")
)

// CORRECT - BusinessLogicError pattern
LiftrixError.BusinessLogicError(
    code = "OPERATION_FAILED",
    errorMessage = "Operation failed",
    analyticsContext = mapOf("operation" to "OPERATION_NAME")
)

// WRONG - Using non-existent parameters
LiftrixError.ValidationError(
    errorMessage = "Failed to check username availability",
    operation = "CHECK_USERNAME_AVAILABILITY"  // Wrong parameter
)
```

### Common Error Patterns & Fixes

#### 1. LiftrixResult<T> Handling
`LiftrixResult<T>` is Kotlin `Result<T>` in current source. Do not use sealed `Success`/`Error` branches:
```kotlin
// CORRECT - Use LiftrixResult fold pattern
val result = repository.getData(userId)
val data = result.fold(
    onSuccess = { it },
    onFailure = { error ->
        Timber.e(error, "Failed to get data")
        emptyList()
    }
)

// WRONG - LiftrixResult is not a custom sealed class
when (result) {
    is Success -> result.data  // Missing imports
    is Error -> emptyList()    // Type not found
}
```

#### 2. Missing Compose Dependencies
Required imports for social features:
```kotlin
// Paging Compose - MANDATORY for feed screens
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

// Pull Refresh - MANDATORY for feed refresh
import eu.bambooapps.material3.pullrefresh.PullRefreshIndicator
import eu.bambooapps.material3.pullrefresh.pullRefresh
import eu.bambooapps.material3.pullrefresh.rememberPullRefreshState
```

#### 3. UI State Completeness
All UI state data classes MUST include these properties:
```kotlin
data class FeatureUiState(
    val isLoading: Boolean = false,
    val error: LiftrixError? = null,
    // Feature-specific state properties
    val selectedTab: TabType = TabType.DEFAULT,
    val cachedData: Set<String> = emptySet()
)
```

#### 4. Event Type Alignment
Event handlers must match exact event types:
```kotlin
// CORRECT - Proper event wrapping
onInteraction = { interaction ->
    viewModel.handleEvent(FeatureEvent.UserInteraction(interaction))
}

// WRONG - Direct event causes type mismatch
onInteraction = { interaction ->
    viewModel.handleEvent(interaction)  // Type error
}
```

#### 5. PagingData Type Inference
Explicit type parameters required:
```kotlin
// CORRECT - Explicit generic types
posts.map<EntityType, DomainType> { entity ->
    mapper.toDomain(entity)
}

// WRONG - Compiler cannot infer types
posts.map { entity -> mapper.toDomain(entity) }
```

#### 6. Result Handling
Use `fold`, `getOrElse`, `onFailure`, or `exceptionOrNull()` for `LiftrixResult<T>`:
```kotlin
// CORRECT
val data = result.getOrElse { throwable ->
    Timber.e(throwable, "Operation failed")
    defaultValue
}
```

#### 7. Flow<Result<T>> Return Type Fixes
UseCase methods must return correct Flow patterns:
```kotlin
// CORRECT - Direct Flow<Result<T>> return
suspend fun invoke(): Flow<Result<Int>> {
    return repository.getCount()
        .catch { throwable ->
            emit(Result.failure(
                LiftrixError.BusinessLogicError(
                    code = "COUNT_FETCH_FAILED",
                    errorMessage = "Failed to get count"
                )
            ))
        }
}

// WRONG - Double-wrapped Result
suspend fun invoke(): Flow<Result<Int>> = liftrixCatching {
    repository.getCount()  // Returns Result<Flow<Result<Int>>>
}
```

#### 8. Import Path Validation
Always use correct import paths for LiftrixError and utilities:
```kotlin
// CORRECT imports
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure

// WRONG - Common incorrect paths that cause compilation errors
import com.example.liftrix.core.common.liftrixCatching  // Wrong path
import com.example.liftrix.domain.model.common.LiftrixError  // Wrong path
```

## Testing Strategy

### Unit Tests
- ViewModels with MockK
- Use cases with fake repositories
- Repository tests with in-memory Room
- Current JVM test coverage lives under `app/src/test/java`

### Integration Tests
- Firestore/Room sync flows
- Authentication flows with Firebase emulator
- Navigation flows with Compose testing
- Current instrumented coverage lives under `app/src/androidTest/java`

### UI Tests
- Compose UI tests only (no XML)
- Emulator-based testing (no device sensors)
- CI-compatible for GitHub Actions
- Some retained test files use a `.disabled` suffix and are not part of normal Gradle test discovery; review before assuming a flow is covered

## Firebase Integration

### Services Used
- Authentication: Email/Google/Anonymous
- Firestore: Offline-first with conflict resolution
- Storage: Profile images with 30s upload timeout
- Analytics/Performance/Crashlytics: Monitoring
- Remote Config: Feature flags
- AI: Workout insights
- FCM: Push notification token and message handling
- Functions: Backend integrations provided through DI
- App Check: Early app startup and Firebase AI readiness

### Security Rules
- User-level document ownership enforced
- Privacy settings respected for social features
- Server-side validation for all writes

## Firebase Sync Architecture

### Sync Infrastructure Overview
```kotlin
// Offline-first sync pattern
Room Database (source of truth)
    ↓ SyncQueueEntity (offline operations)
SyncRepository & OfflineQueueManager
    ↓ WorkManager (background sync)
UnifiedSyncWorker / startup-specialized workers
    ↓ FirebaseDataSource
Firestore (network persistence)
    ↓ RealtimeSyncService (live updates)
```

### Core Sync Components

#### 1. Sync Repository Pattern
```kotlin
// Some entities implement SyncableEntity. Others use sync metadata fields only.
// Verify the local entity pattern before adding this interface.
interface SyncableEntity {
    val isSynced: Boolean
    val syncVersion: Long
    val lastModified: Long
}

// Repository provides sync operations
interface SyncRepository {
    suspend fun syncAll(userId: String): LiftrixResult<SyncResult>
    suspend fun syncWorkouts(userId: String): LiftrixResult<Unit>
    fun observeRealtimeWorkout(workoutId: String): Flow<WorkoutUpdate>
    fun observeSyncStatus(): Flow<SyncStatus>
}
```

#### 2. Conflict Resolution
```kotlin
// Last-write-wins strategy
ConflictResolver.resolve(local, remote) →
    if (local.lastModified > remote.lastModified) local else remote
```

#### 3. Sync Workers
- UnifiedSyncWorker: Current default for periodic, immediate, and entity-specific sync
- MasterSyncWorker: Legacy/fallback orchestrator, not the primary periodic path while USE_UNIFIED_SYNC is true
- Startup sync chain: Workout restore → template restore → profile/user public/follow/workout post/achievement workers
- WorkoutSyncWorker: Batch processes workouts (20 per batch)
- TemplateSyncWorker: Syncs workout templates
- AchievementSyncWorker: Syncs unlocked achievements
- ProfileSyncWorker: Syncs user profiles with goals priority
- SocialProfileSyncWorker: Syncs social data
- FollowRelationshipSyncWorker: Bidirectional follow sync
- WorkoutPostSyncWorker: Feed posts with engagement
- GymBuddySyncWorker: Buddy connections (5 max)
- SettingsSyncWorkerV2: Unit preferences (kg/lbs)

#### 4. Real-time Services
```kotlin
// Real-time workout updates during active sessions
RealtimeSyncService.startRealtimeWorkoutSync(workoutId, userId)
// Real-time engagement metrics
EngagementRealtimeSyncService.startListeningToPost(postId)
// Real-time follow counts
FollowRealtimeService.startListeningToProfile(userId)
```

### Sync Triggers & Scheduling
```kotlin
// Periodic sync (background)
SyncCoordinator.schedulePeriodicSync(userId) // Every 15 minutes, uses UnifiedSyncWorker when enabled

// Immediate sync (user-triggered, blocked as no-op until StartupRestoreGate completes)
SyncCoordinator.triggerImmediateSync(userId)

// Entity-specific sync
workoutRepository.queueSync(userId)

// Real-time sync (active sessions)
syncCoordinator.enableRealtimeWorkoutSync(workoutId, userId)
```

### Firestore Collection Schema
```
/users/{userId}                     # User profile with goals priority
  /workouts/{workoutId}             # Workout data with exercises
  /templates/{templateId}           # Workout templates
  /achievements/{achievementId}     # Unlocked achievements
  /gym_buddies/{buddyId}           # Gym buddy connections (max 5)
  /settings/preferences            # Unit preferences (kg/lbs)
/social_profiles/{userId}          # Social profile data
/follow_relationships/{id}         # Follow connections
/workout_posts/{postId}           # Feed posts with engagement
```

### Sync Debug Points
1. Sync Queue: Check `sync_queue` table for pending operations
2. Sync Status: Monitor `SyncStatusRepository.syncStatus` flow
3. Worker Status: Use WorkManager debug tools to check worker state
4. Conflict Resolution: Debug in `LastWriteWinsResolver`
5. Real-time Issues: Check listener lifecycle in `RealtimeSyncService`

### Common Sync Issues & Solutions
- Sync not triggering: Check WorkManager constraints (network, battery)
- Data not appearing: Verify `isSynced` flag and `syncVersion`
- Conflicts: Review `lastModified` timestamps
- Real-time delays: Check Firestore listener registration
- Partial sync: Review batch processing in sync workers

## Social Feed Engagement Architecture

### Feed System Core Components
```kotlin
// Active Home/feed architecture pattern
FeedScreen (Compose UI with pull-to-refresh)
    ↓ collectAsLazyPagingItems()
FeedViewModel (MVI with optimistic updates)
    ↓ Flow<PagingData<WorkoutPost>>
FeedRepositoryImpl (Paging3 + Room DAO paging; FeedRemoteMediator is not active in current feed queries)
    ↓ Room + Firestore sync
FeedCacheService (Performance layer)

// FeedGeneratorUseCase still exists for relevance scoring/privacy filtering paths,
// but the active Home feed path does not call it directly.
```

### Critical Social Patterns

#### 1. Privacy Enforcement (MANDATORY)
ALL social content MUST validate viewer permissions:
```kotlin
// CORRECT - Always check privacy before displaying
val canView = privacyService.canViewPost(viewerId, post)
if (!canView) return@filter false

// WRONG - Exposing private content
posts.map { post -> displayPost(post) }  // No privacy check
```

#### 2. Optimistic Updates Pattern
Use immediate UI updates with background sync:
```kotlin
// CORRECT - Optimistic + revert on failure
_likedPosts.value = _likedPosts.value + postId
val result = engagementRepository.toggleLike(postId, userId)
result.onFailure {
    _likedPosts.value = _likedPosts.value - postId  // Revert
}
```

#### 3. Paging3 Implementation
Required imports and pattern for feed screens:
```kotlin
// MANDATORY imports for social feeds
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

// Standard paging setup
val posts = viewModel.feedPosts.collectAsLazyPagingItems()
```

### Social Debug Hot Zones

#### Critical Debug Points
1. Privacy Violations: `PrivacyEnforcementService.canViewPost()` - Check all social content filtering
2. Optimistic Update Failures: Engagement operations - Verify revert logic
3. Feed Cache Staleness: `FeedCacheService` - Check invalidation on follows/unfollows
4. Real-time Sync Issues: `CommentSyncService` - Monitor Firestore listener lifecycle
5. Sequential DB Operations: Use batch operations for multiple engagement updates

#### Common Social Issues
- Memory Leaks: Firestore listeners not removed in `onDestroy`
- User Scoping Violations: Social queries missing `viewer_id` filtering
- Feed Algorithm Bias: Relevance scoring favoring certain content types
- Privacy Context Missing: Profile access without proper viewer context

### Media Upload Pipeline
```kotlin
// Media processing flow
MediaUploadServiceImpl
    → Firebase Storage (30s timeout)
    → Image compression + thumbnails
    → CDN distribution
    → Background upload with progress tracking
```

### Social Privacy Levels
- PUBLIC: Visible to everyone, appears in discovery feed
- FOLLOWERS: Only visible to followers, filtered by relationship
- PRIVATE: Only visible to author (no social sharing)

## Gym Buddy System Architecture

### Gym Buddy Core Components
```kotlin
// Gym Buddy System (Max 5 buddies per user)
GymBuddyScreen (QR display & scanner)
    ↓ GymBuddyViewModel
QRCodeService (ZXing integration)
    ↓ Time-limited tokens (5 min expiry)
GymBuddyRepository
    ↓ Mutual connections with limit enforcement
PRDetectionService
    ↓ Comprehensive PR algorithms
```

### QR Code Pairing Pattern
```kotlin
// QR code generation with expiration
val pairingToken = "liftrix://gym-buddy/${userId}?token=${timestamp}"
val qrResult = qrCodeService.generateQRCode(pairingToken, size = 300)

// Validate scan and create mutual connection
if (System.currentTimeMillis() > payload.expiresAt) {
    throw QRExpiredException("QR code expired")
}
gymBuddyRepository.createMutualConnection(userId1, userId2, viaQr = true)
```

### PR Detection System
- PR Types: ONE_RM, VOLUME, REPS, MAX_WEIGHT
- Detection Algorithms: Epley formula for 1RM estimation
- Significance Levels: EXCEPTIONAL (10%+), MAJOR (5%+), MODERATE (2%+), MINOR (<2%)
- Notification Cooldown: 1 PR notification per buddy per day

## Notification System Architecture

### FCM Integration Pattern
```kotlin
// Notification routing with intelligent batching
NotificationRouter
    → Priority check (HIGH/NORMAL/LOW)
    → Privacy filter (blocks, mutes, preferences)
    → Quiet hours enforcement (10 PM - 8 AM default)
    → Batch processor (hourly for social)
    → FCM delivery
```

### Notification Categories & Controls
- Master Toggle: Global on/off
- Category Controls: Workout, Social, Achievement, Reminder
- Social Subcategories: Gym buddy PRs, Follow requests, Post likes/comments
- Delivery Frequency: IMMEDIATE, HOURLY, DAILY
- User Mutes: Per-user or category with optional duration

### Notification Action Service
```kotlin
// Handle notification actions without opening app
NotificationActionService
    → CELEBRATE_PR: Record celebration for gym buddy
    → ACCEPT_FOLLOW: Accept follow request directly
    → DECLINE_FOLLOW: Decline follow request directly
```

### Critical Notification Patterns

#### 1. Token Management (MANDATORY)
```kotlin
// CORRECT - Update FCM token on refresh
override fun onNewToken(token: String) {
    tokenRepository.updateToken(token, deviceId, platform)
}

// WRONG - Not handling token refresh
// Notifications will fail after token expires
```

#### 2. Batch Processing for Performance
```kotlin
// CORRECT - Batch similar notifications
val grouped = notifications.groupBy { it.batchKey }
grouped.forEach { (key, items) ->
    if (items.size > 1) sendInboxStyle(items)
    else sendSingle(items.first())
}

// WRONG - Sending individual notifications
notifications.forEach { fcmSender.send(it) }  // Causes notification spam
```

## AI Chatbot System

### AI Architecture Pattern
```kotlin
// Firebase AI with Gemini 2.5 Flash Lite
ChatbotViewModel → SendChatMessageUseCase → AIChatService
    ↓ AbusePreventionService (fitness-aware jailbreak detection)
    ↓ RateLimitingService (100 msg/day, 10k tokens/month, $1/hr)
```

### Critical AI Implementation Rules
```kotlin
// MANDATORY chat entity user scoping
@Query("SELECT * FROM chat_history WHERE user_id = :userId")
suspend fun getChatHistory(userId: String): List<ChatHistoryEntity>

// Three-tier rate limiting
if (limits.dailyMessagesRemaining <= 0) throw QuotaExceededException()
if (limits.monthlyTokensRemaining <= 0) throw QuotaExceededException()
if (estimatedHourlyCost > 1.0) throw CostThresholdException()

// Durable usage accounting for every paid model response
// Any Firebase AI/Gemini call must record token usage in the same quota source
// used by RateLimitingService, including repair attempts and responses that fail
// downstream parsing, validation, or UI persistence.
recordAiUsage(userId, tokensUsed)

// Fitness context reduces jailbreak score by 50%
if (containsFitnessKeywords(message)) score *= 0.5f

// Language auto-detection for English/Romanian
if (message.contains("ă", "â", "î", "ș", "ț")) Language.ROMANIAN
```

### AI Debug Hot Zones
1. Jailbreak false positives: `AbusePreventionService` - Fitness keywords like "pretend to be doing squats" trigger detection
2. Token overflow: `ChatHistoryEntity.tokenCount` - Monitor monthly usage approaching 10k limit
3. Language conflicts: Auto-detection overrides user preference - check `ChatPreferencesEntity.autoDetectLanguage`
4. Context loss: Only last 10 messages included - check `conversationContext.recentMessages.takeLast(10)`

## Progress Dashboard Architecture

### Dashboard Component Hierarchy
```
ProgressDashboardScreen (Main container)
    ↓ Coordinator: ProgressDashboardCoordinator
ProgressSummaryCards (Top metrics overview)
    ↓ GlobalTimeRangeSelector (Synchronized time control)
ResponsiveDashboardLayout (Adaptive container)
    ↓ AdaptiveWidgetGrid (Memory-aware grid)
        ↓ WidgetContainer (Individual widgets)
            ↓ Analytics/Chart/Metric Widgets
```

### Widget System
Active widgets returned by `AnalyticsWidget.getAllWidgets()`:
- `strength_analytics` - Unified strength analysis
- `volume_analytics` - Unified volume analysis
- `frequency_chart` - Workout frequency patterns
- `progress_chart` - Comprehensive progress visualization
- `monthly_summary` - Monthly performance overview
- `recovery_metrics` - Rest and recovery analysis
- `muscle_group_distribution` - Muscle distribution analysis
- `exercise_ranking` - Top performing exercises
- `workout_duration` - Session duration trends
- `recent_achievements` - Latest personal records
- `consistency_score` - Workout consistency tracking
- `progressive_overload` - Progressive overload analysis

Deprecated/hidden widgets are kept for migration lookup and should not be newly surfaced:
- Hidden summary widgets: `workout_frequency`, `total_volume`, `volume_calendar`, `workout_streak`, `average_duration`
- Replaced widgets: `strength_progress`, `personal_records`, `one_rm_progression`, `volume_chart`, `volume_trends`, `volume_load_progression`
- Removed deprecated IDs are listed in `AnalyticsWidget.DEPRECATED_WIDGET_IDS`

### ViewModels & Coordination Pattern
```kotlin
// Coordinator manages inter-ViewModel communication
ProgressDashboardCoordinator
    → Broadcasts CoordinatorEvents via SharedFlow
    → Manages global state (auth, time range, preferences)

// Specialized ViewModels observe coordinator events
AnalyticsWidgetViewModel
ProgressChartsViewModel
ProgressSummaryViewModel
    → React to CoordinatorEvent.TimePeriodChanged
    → Handle widget-specific data loading
```

### Detail Screen Navigation
```kotlin
// Type-safe navigation to detail screens
navController.navigate(
    LiftrixRoute.OneRmDetail
)

// Available detail routes
VolumeAnalysisDetail
OneRmDetail
MuscleGroupDetail
WorkoutFrequencyDetail
ExerciseRankingDetail
```

### Performance Optimizations
- AdaptiveWidgetGrid: Memory-aware with automatic degradation
- ModernVolumeChart: 60fps bezier rendering with gradient fills
- ResponsiveDashboardLayout: 2-col mobile, 3-col tablet, 4-col desktop
- GlobalTimeRangeSelector: Single source of truth for time filtering
- Widget virtualization: Limits to 10 widgets under memory pressure

### Chart Implementation Standards
```kotlin
// All charts follow this pattern
@Composable
fun ModernChart(
    data: List<DataPoint>,
    timeRange: TimeRangeType,
    modifier: Modifier = Modifier,
    onDataPointSelected: ((DataPoint) -> Unit)? = null,
    showPersonalRecords: Boolean = true,
    animationDuration: Int = 300
)
```

## Quick Reference

### File Patterns
- ViewModels: `*ViewModel.kt` in `ui/` subdirectories
- Use Cases: `*UseCase.kt` in `domain/usecase/` (80 use case files were present during the 2026-05-01 source audit)
- Repositories: `*RepositoryImpl.kt` in `data/repository/`
- DAOs: `*Dao.kt` in `data/local/dao/`
- Entities: `*Entity.kt` in `data/local/entity/`
- AI Components: `ui/chat/ChatbotViewModel.kt`, `domain/service/AIChatService.kt`, `data/service/AbusePreventionService.kt`

### Consolidated Use Cases

By Domain:
- Auth: AuthQueryUseCase, AuthCommandUseCase
- Profile: ProfileQueryUseCase, ProfileCommandUseCase, ProfileImageOperationsUseCase, CalculateAchievementsUseCase
- Analytics: AnalyticsQueryUseCase, AnalyticsExportUseCase, DashboardCommandUseCase, WidgetPreferencesUseCase, WidgetMigrationUseCase
- Templates & Folders: TemplateQueryUseCase, TemplateCommandUseCase, FolderOperationsUseCase
- Social: SocialProfileQueryUseCase, SocialProfileCommandUseCase, SocialRelationshipUseCase, PostEngagementUseCase, SocialSearchUseCase
- Workouts: WorkoutQueryUseCase, WorkoutCommandUseCase
- Account: AccountQueryUseCase, AccountCommandUseCase
- Settings: SettingsQueryUseCase, SettingsCommandUseCase
- Other: ChatOperationsUseCase, ExerciseQueryUseCase, NotificationPreferencesUseCase, DataImportUseCase, SessionOperationsUseCase

### Domain Services
- AnalyticsCalculationService, WidgetOperationsService (calculation logic)

### Validation Patterns
- Use validation DSL for chainable rules
- Validate at repository level before database operations
- Return LiftrixError.ValidationError with field details

### Async Patterns
- Use coroutines with proper scope management
- Collect Flows with lifecycle awareness
- Handle cancellation in long-running operations

## Important Reminders

1. NEVER create user-owned database queries without user_id filtering
2. ALWAYS use `LiftrixResult<T>`/Kotlin `Result<T>` helpers for error handling in domain/data layers
3. PREFER editing existing files over creating new ones
4. USE V2 color system (LiftrixColorsV2) for all UI work
5. FOLLOW SOLID principles and Clean Architecture boundaries
6. KEEP functions under 20 instructions, classes under 200 instructions
7. TEST with emulator-based tests only (no device sensors)
8. NEVER read directly from Firebase in UI layer - always use Room as source of truth
9. Use `ModernBaseViewModel<S>` when it fits, or AndroidX `ViewModel` directly when a feature has custom state needs
10. USE UnifiedWorkoutSessionManager for all session state management
11. Add sync metadata for synced entities and verify whether the local pattern uses `SyncableEntity` or field-based metadata
12. USE SyncCoordinator for triggering sync operations, not direct Firebase calls
13. APPLY optimistic updates for social interactions then revert on sync failure
14. BATCH Firestore operations in sync workers using the configured batch sizes (`BaseSyncWorker` default 20; `SyncOperationManager` has entity-specific sizes)

## Redesigned Workout System Architecture

### Workout Component Redesign (2024)
The workout system has been redesigned with new UI patterns and improved UX:

#### Core Redesigned Components
- RedesignedEditWorkoutScreen: Modern edit interface with Material 3 design
- RedesignedExerciseCard: Enhanced exercise cards with context-aware behavior
- RedesignedSetData: Unified data structure for set management
- RedesignedPrimaryButton: Consistent button styling across workout flows
- RedesignedWorkoutHeader: Standardized header component

#### Exercise Card Context System
```kotlin
enum class ExerciseCardContext {
    ACTIVE_WORKOUT,     // Show completion checkboxes, focus on actual values
    TEMPLATE_CREATION   // Hide completion, focus on target values
}
```

#### Key Redesign Features
1. Context-Aware UI: Exercise cards adapt behavior based on context (active vs template)
2. Unified Input System: Consistent input fields with proper keyboard types
3. Enhanced Set Management:
   - Previous/current value comparison
   - Visual completion tracking
   - Inline editing capabilities
4. Improved Exercise Management:
   - Contextual menu system (reorder, change, remove)
   - Drag-to-reorder dialog
   - Notes integration
5. Modern Visual Design:
   - 12dp rounded corners
   - LiftrixColorsV2 color system
   - Semantic spacing
   - Proper text hierarchy

#### Implementation Guidelines
- Always use RedesignedExerciseCard for new workout screens
- Pass appropriate ExerciseCardContext based on screen purpose
- Follow the RedesignedSetData structure for consistent state management
- Use RedesignedPrimaryButton for primary actions
- Implement exercise options menu for exercise management

#### Migration Pattern
```kotlin
// New redesigned pattern
RedesignedExerciseCard(
    exerciseName = exercise.name,
    exerciseSubtitle = exercise.muscleGroup,
    sets = exercise.sets.map { RedesignedSetData(...) },
    context = ExerciseCardContext.ACTIVE_WORKOUT,
    onAddSet = { /* handle */ },
    onUpdateSet = { index, setData -> /* handle */ }
)

// Old pattern - avoid for new screens
LegacyExerciseCard(...)
```

## Critical Gotchas

### Session State Management
```kotlin
// Use UnifiedWorkoutSessionManager for session operations
// Never create multiple session state sources
```

### Firebase Sync Priority
```kotlin
// Read from Room, sync to Firebase in background
// Never read directly from Firebase in UI layer
```

### Social Privacy Controls
```kotlin
// Always include viewer context for profile access
userSearchRepository.getPublicProfile(profileUserId, viewerId)

// Never expose profile data without privacy filtering
userSearchRepository.getPublicProfile(profileUserId, null)
```

### Gym Buddy QR Code Gotchas
```kotlin
// Enforce 5 buddy limit and validate QR expiration
if (buddies.size >= 5) throw BuddyLimitException("Maximum 5 buddies")
if (System.currentTimeMillis() > expiresAt) throw QRExpiredException()

// Never allow unlimited buddies or expired QR codes
gymBuddyRepository.createConnection(userId1, userId2)  // No validation
```

### PR Notification Gotchas
```kotlin
// Enforce daily cooldown per buddy
val cooldownKey = "$userId:$buddyId:${LocalDate.now()}"
if (!prRepository.hasSentToday(cooldownKey)) {
    // Send notification
}

// Never spam PR notifications
buddies.forEach { sendPRNotification(it) }  // No cooldown
```

### Social Feed Engagement Gotchas
```kotlin
// Use optimistic updates with proper error handling
_likedPosts.value = _likedPosts.value + postId
val result = engagementRepository.toggleLike(postId, userId)
result.onFailure {
    _likedPosts.value = _likedPosts.value - postId  // Must revert
}

// Never update engagement without optimistic UI feedback
val result = engagementRepository.toggleLike(postId, userId)
result.onSuccess {
    _likedPosts.value = _likedPosts.value + postId  // Too slow
}
```

### Feed Caching Performance
```kotlin
// Use relevance-based caching with proper invalidation
feedCacheService.invalidateCache(userId, InvalidationReason.NEW_FOLLOW)

// Don't cache feeds without proper invalidation strategy
feedCache.put(userId, posts)  // Cache will become stale
```

### Progress Dashboard Navigation
```kotlin
// Use type-safe navigation with @Serializable data classes
navController.navigate(LiftrixRoute.OneRmDetail)

// Don't use string-based navigation for detail views
navController.navigate("oneRmDetail/1,2/SIX_MONTHS")
```

### Chart Performance Optimization
```kotlin
// Use remember() for expensive chart calculations
val chartData = remember(rawData, timeRange) {
    processChartData(rawData, timeRange)
}

// Don't recalculate chart data on every recomposition
val chartData = processChartData(rawData, timeRange)
```

## Key Classes & Components

### Core System Classes
- `UnifiedWorkoutSessionManager` - Central session state management
- `LiftrixResult<T>` - Typealias to Kotlin `Result<T>` with Liftrix-specific helper extensions
- `ModernBaseViewModel<S>` - Minimal shared StateFlow ViewModel base used by many UI ViewModels
- `UiState<T>` - UI state management (Loading/Success/Error/Empty)
- `LiftrixRoute` - Type-safe navigation with @Serializable

### Sync Infrastructure Classes
- `SyncRepository` - Core sync operations contract
- `SyncCoordinator` - Schedules periodic, immediate, startup, entity-specific, and realtime sync operations
- `UnifiedSyncWorker` - Current default for periodic, immediate, and entity-specific sync when `USE_UNIFIED_SYNC` is true
- `MasterSyncWorker` - Legacy/fallback coordinator, not the primary periodic path while unified sync is enabled
- `OfflineQueueManager` - Manages offline operations queue
- `ConflictResolver` - Last-write-wins conflict resolution
- `RealtimeSyncService` - Firestore real-time listeners
- `FirebaseDataSource` - Firebase CRUD operations

### UI Components
- `UnifiedWorkoutCard` - Foundation card component (12dp radius, haptic feedback)
- `ModernActionButton` - Three-tier button system (Primary/Secondary/Tertiary)
- `LiftrixSpacing` - Semantic spacing tokens (16dp/12dp/8dp)
- `ResponsiveDashboardLayout` - Adaptive grid (2-col mobile, 3-col tablet, 4-col desktop)
- `AdaptiveWidgetGrid` - LazyVerticalGrid with dynamic columns and card spanning
- `ModernVolumeChart` - Bezier curves, gradient fills, PR markers
- `GlobalTimeRangeSelector` - Synchronized time selector for all charts

### Social & Profile System
- `ProfileViewModel` - Enhanced profile management with achievements
- `UserSearchRepository` - Social discovery with privacy filtering
- `QRCodeService` - ZXing-based QR code generation
- `CalculateAchievementsUseCase` - Automatic achievement detection
- `ProfileImageManager` - Image upload, crop, and cache management
- `UnitConversionService` - Dynamic kg/lbs and km/miles conversion based on user settings

### Social Feed & Engagement System
- `FeedRepositoryImpl` - Paging3 integration using Room DAO paging; `FeedRemoteMediator` exists but is not active in current feed queries
- `EngagementRepositoryImpl` - Optimistic updates for likes, comments, saves with error recovery
- `FeedGeneratorUseCase` - Intelligent relevance scoring/privacy filtering path; active Home feed currently calls `FeedRepositoryImpl` directly through `FeedViewModel`
- `FeedCacheService` - Performance layer with relevance-based caching and smart invalidation
- `CommentSyncService` - Real-time Firestore listeners for live comment updates
- `MediaUploadServiceImpl` - Firebase Storage integration with compression and CDN
- `PrivacyEnforcementService` - Content filtering based on privacy levels and user relationships

### Gym Buddy & PR System
- `GymBuddyViewModel` - QR code generation and buddy management
- `GymBuddyRepository` - Mutual connections with 5 buddy limit
- `PRDetectionServiceImpl` - Comprehensive PR detection algorithms
- `NotificationActionService` - Handle notification actions without opening app
- `NotificationRouter` - Intelligent routing with batching and quiet hours

## Performance Targets
- 60fps UI rendering with optimized animations
- <100ms database queries with proper indexing
- <5s sync operations with exponential backoff
- 150ms component interactions with haptic feedback
- WCAG 2.1 AA accessibility compliance
