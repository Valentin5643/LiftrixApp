# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Essential Build Commands
```bash
# Build and compile
./gradlew assembleDebug              # Build debug APK
./gradlew compileDebugKotlin        # Compile Kotlin code only
./gradlew build                     # Full build with tests

### Common Development Tasks
- **Gradle sync issues**: Run `./gradlew --stop` then rebuild
- **Compilation errors**: Check `./gradlew compileDebugKotlin` for detailed errors
- **Run on device**: `./gradlew installDebug` after building

## Project Architecture

### High-Level System Design
```
UI Layer (Jetpack Compose)
    ↓ StateFlow<UiState<T>>
ViewModel Layer (BaseViewModel<S,E>)
    ↓ LiftrixResult<T>
Use Case Layer (80+ use cases)
    ↓ LiftrixResult<T>
Repository Layer (14 interfaces)
    ↓ Flow<Entity>
DAO Layer (23 DAOs with user scoping)
    ↓ SQL
Room Database (22 entities, v43)
    ↓ Background sync
Firebase (8 services)
```

### Critical Architectural Rules

#### 1. User Scoping (MANDATORY)
**ALL database operations MUST filter by userId to prevent data leakage:**
```kotlin
// ✅ CORRECT - Always include userId
@Query("SELECT * FROM workouts WHERE user_id = :userId")
suspend fun getWorkoutsForUser(userId: String): List<WorkoutEntity>

// ❌ WRONG - Missing user scoping causes data leaks
@Query("SELECT * FROM workouts")
suspend fun getAllWorkouts(): List<WorkoutEntity>
```

#### 2. Error Handling Pattern
**Use LiftrixResult<T> for all domain operations:**
```kotlin
suspend fun invoke(request: Request): LiftrixResult<Response> = liftrixCatching(
    errorMapper = { throwable -> 
        LiftrixError.BusinessLogicError(
            errorMessage = "Operation failed",
            operation = "OPERATION_NAME",
            analyticsContext = mapOf("user_id" to userId)
        )
    }
) {
    // Business logic here
}
```

#### 3. Navigation (Type-Safe)
**Use @Serializable sealed classes for routes:**
```kotlin
@Serializable
sealed class LiftrixRoute {
    @Serializable data object Home : LiftrixRoute()
    @Serializable data class WorkoutDetail(val workoutId: String) : LiftrixRoute()
}
```

## Code Organization

### Module Structure
- **core/**: Cross-cutting concerns (network, design system, common)
- **data/**: Repository implementations, DAOs, entities
- **domain/**: Use cases, repository interfaces, domain models
- **ui/**: Compose screens, ViewModels, navigation
- **di/**: 22 Hilt modules for dependency injection

### Key Components & Their Roles

#### Domain Layer
- **Use Cases**: Single responsibility business operations (80+ use cases)
- **Models**: Domain entities with business logic
- **Repository Interfaces**: Contracts for data operations

#### Data Layer  
- **Repositories**: Implement domain interfaces with error handling
- **DAOs**: Room database access with mandatory user scoping
- **Entities**: Database models with sync metadata

#### UI Layer
- **Screens**: Jetpack Compose UI with Material 3
- **ViewModels**: MVI pattern with BaseViewModel<S,E>
- **Navigation**: Type-safe with Navigation Compose

## Design System

### Color System (V2 - Production)
**Always use LiftrixColorsV2 for new development:**
- Primary: Teal (#20C9B7)
- Secondary: Indigo (#2A3B7D)
- Surface colors for semantic UI

### Component Hierarchy
- **UnifiedWorkoutCard**: Base card component (12dp radius)
- **ModernActionButton**: Three-tier buttons (Primary/Secondary/Tertiary)
- **LiftrixSpacing**: Semantic spacing (16dp/12dp/8dp)

### Deprecated Items
**Widget IDs to filter out:**
`calories_burned`, `daily_calories`, `weekly_calorie_trend`, `duration_chart`, `set_completion_rate`

## Debug & Extend Hot Zones

### Common Debug Points
1. **Authentication Issues**: `GetCurrentUserIdUseCase` - Check cold-start handling
2. **Sync Problems**: `WorkoutSyncWorker` - Check conflict resolution
3. **Performance Issues**: `GetWidgetDataUseCase` - Check sequential fetching
4. **Navigation Errors**: `UnifiedNavigationContainer` - Check route registration

### Adding New Features

#### New Screen Checklist
1. Add route to `LiftrixRoute` sealed class
2. Create ViewModel extending `BaseViewModel<S,E>`
3. Register in `UnifiedNavigationContainer`
4. Use existing UI components from design system

#### New Database Entity Checklist
1. Create entity with `user_id` field (mandatory)
2. Add `is_synced` and `sync_version` fields
3. Create DAO with user-scoped queries
4. Add migration to increment database version
5. Register in `LiftrixDatabase` and Hilt modules

## Known Issues & Workarounds

### Critical Issues
1. **Sequential Widget Fetching**: Causes UI blocking in `GetWidgetDataUseCase`
   - **Workaround**: Implement parallel async/await fetching
   
2. **Debug Logging in Production**: Found in authentication flows
   - **Fix**: Remove Timber.d() calls from production code

3. **Legacy Result<T> Methods**: Maintaining dual error handling
   - **Migration**: Use LiftrixResult<T> for all new code

### Performance Bottlenecks
- Some analytics calculations still on main thread (use Dispatchers.IO)
- Missing composite indexes for social queries
- UnifiedWorkoutSessionManager potential memory retention

## Testing Strategy

### Unit Tests
- ViewModels with MockK
- Use cases with fake repositories
- Repository tests with in-memory Room

### Integration Tests  
- Firestore/Room sync flows
- Authentication flows with Firebase emulator
- Navigation flows with Compose testing

### UI Tests
- Compose UI tests only (no XML)
- Emulator-based testing (no device sensors)
- CI-compatible for GitHub Actions

## Firebase Integration

### Services Used
- **Authentication**: Email/Google/Anonymous
- **Firestore**: Offline-first with conflict resolution
- **Storage**: Profile images with 30s upload timeout
- **Analytics/Performance/Crashlytics**: Monitoring
- **Remote Config**: Feature flags
- **AI**: Workout insights

### Security Rules
- User-level document ownership enforced
- Privacy settings respected for social features
- Server-side validation for all writes

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

### Widget System (15 Total Widgets)
**Active Widgets** (12 displayed):
- `strength_progress` - 1RM tracking with PR markers
- `volume_chart` - ModernVolumeChart with bezier curves
- `frequency_chart` - Workout frequency heatmap
- `muscle_group_chart` - Muscle distribution analysis
- `exercise_ranking` - Top performing exercises
- `workout_duration` - Session duration trends
- `one_rm_progression` - Strength progression over time
- `recent_achievements` - Latest personal records
- `recovery_metrics` - Rest and recovery analysis
- `consistency_score` - Workout consistency tracking
- `overtraining_risk` - Overtraining detection
- `progressive_overload` - Progressive overload analysis

**Deprecated Widgets** (3 hidden, kept for compatibility):
- `workout_frequency`, `total_volume`, `volume_calendar`

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
    OneRmProgressionDetail(
        exerciseIds = listOf("1", "2"),
        timeRange = TimeRange.SIX_MONTHS
    )
)

// Available detail routes
VolumeAnalysisDetail
OneRmDetail
MuscleGroupDetail  
WorkoutFrequencyDetail
ExerciseRankingDetail
```

### Performance Optimizations
- **AdaptiveWidgetGrid**: Memory-aware with automatic degradation
- **ModernVolumeChart**: 60fps bezier rendering with gradient fills
- **ResponsiveDashboardLayout**: 2-col mobile, 3-col tablet, 4-col desktop
- **GlobalTimeRangeSelector**: Single source of truth for time filtering
- **Widget virtualization**: Limits to 10 widgets under memory pressure

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
- Use Cases: `*UseCase.kt` in `domain/usecase/`
- Repositories: `*RepositoryImpl.kt` in `data/repository/`
- DAOs: `*Dao.kt` in `data/local/dao/`
- Entities: `*Entity.kt` in `data/local/entity/`

### Validation Patterns
- Use validation DSL for chainable rules
- Validate at repository level before database operations
- Return LiftrixError.ValidationError with field details

### Async Patterns
- Use coroutines with proper scope management
- Collect Flows with lifecycle awareness
- Handle cancellation in long-running operations

## Important Reminders

1. **NEVER** create database queries without user_id filtering
2. **ALWAYS** use LiftrixResult<T> for error handling in domain/data layers
3. **PREFER** editing existing files over creating new ones
4. **USE** V2 color system (LiftrixColorsV2) for all UI work
5. **FOLLOW** SOLID principles and Clean Architecture boundaries
6. **KEEP** functions under 20 instructions, classes under 200 instructions
7. **TEST** with emulator-based tests only (no device sensors)
8. **NEVER** read directly from Firebase in UI layer - always use Room as source of truth
9. **ALWAYS** extend BaseViewModel<S, E> with UiState<T> pattern for ViewModels
10. **USE** UnifiedWorkoutSessionManager for all session state management

## Redesigned Workout System Architecture

### Workout Component Redesign (2024)
The workout system has been redesigned with new UI patterns and improved UX:

#### Core Redesigned Components
- **RedesignedEditWorkoutScreen**: Modern edit interface with Material 3 design
- **RedesignedExerciseCard**: Enhanced exercise cards with context-aware behavior
- **RedesignedSetData**: Unified data structure for set management
- **RedesignedPrimaryButton**: Consistent button styling across workout flows
- **RedesignedWorkoutHeader**: Standardized header component

#### Exercise Card Context System
```kotlin
enum class ExerciseCardContext {
    ACTIVE_WORKOUT,     // Show completion checkboxes, focus on actual values
    TEMPLATE_CREATION   // Hide completion, focus on target values
}
```

#### Key Redesign Features
1. **Context-Aware UI**: Exercise cards adapt behavior based on context (active vs template)
2. **Unified Input System**: Consistent input fields with proper keyboard types
3. **Enhanced Set Management**: 
   - Previous/current value comparison
   - Visual completion tracking
   - Inline editing capabilities
4. **Improved Exercise Management**:
   - Contextual menu system (reorder, change, remove)
   - Drag-to-reorder dialog
   - Notes integration
5. **Modern Visual Design**:
   - 12dp rounded corners
   - LiftrixColorsV2 color system
   - Semantic spacing
   - Proper text hierarchy

#### Implementation Guidelines
- **Always use RedesignedExerciseCard** for new workout screens
- **Pass appropriate ExerciseCardContext** based on screen purpose
- **Follow the RedesignedSetData structure** for consistent state management
- **Use RedesignedPrimaryButton** for primary actions
- **Implement exercise options menu** for exercise management

#### Migration Pattern
```kotlin
// ✅ New redesigned pattern
RedesignedExerciseCard(
    exerciseName = exercise.name,
    exerciseSubtitle = exercise.muscleGroup,
    sets = exercise.sets.map { RedesignedSetData(...) },
    context = ExerciseCardContext.ACTIVE_WORKOUT,
    onAddSet = { /* handle */ },
    onUpdateSet = { index, setData -> /* handle */ }
)

// ❌ Old pattern - avoid for new screens
LegacyExerciseCard(...)
```

## 🚨 Critical Gotchas

### Session State Management
```kotlin
// ✅ Use UnifiedWorkoutSessionManager for session operations
// ❌ Never create multiple session state sources
```

### Firebase Sync Priority
```kotlin
// ✅ Read from Room, sync to Firebase in background
// ❌ Never read directly from Firebase in UI layer
```

### Social Privacy Controls
```kotlin
// ✅ Always include viewer context for profile access
userSearchRepository.getPublicProfile(profileUserId, viewerId)

// ❌ Never expose profile data without privacy filtering
userSearchRepository.getPublicProfile(profileUserId, null)
```

### Progress Dashboard Navigation
```kotlin
// ✅ Use type-safe navigation with @Serializable data classes
navController.navigate(OneRmProgressionDetail(exerciseIds = listOf("1", "2"), timeRange = TimeRange.SIX_MONTHS))

// ❌ Don't use string-based navigation for detail views
navController.navigate("oneRmDetail/1,2/SIX_MONTHS")
```

### Chart Performance Optimization
```kotlin
// ✅ Use remember() for expensive chart calculations
val chartData = remember(rawData, timeRange) {
    processChartData(rawData, timeRange)
}

// ❌ Don't recalculate chart data on every recomposition
val chartData = processChartData(rawData, timeRange)
```

## Key Classes & Components

### Core System Classes
- `UnifiedWorkoutSessionManager` - Central session state management
- `LiftrixResult<T>` - Error handling wrapper with recovery strategies
- `BaseViewModel<S, E>` - ViewModel base class for MVI pattern
- `UiState<T>` - UI state management (Loading/Success/Error/Empty)
- `LiftrixRoute` - Type-safe navigation with @Serializable

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

## Performance Targets
- **60fps UI rendering** with optimized animations
- **<100ms database queries** with proper indexing
- **<5s sync operations** with exponential backoff
- **150ms component interactions** with haptic feedback
- **WCAG 2.1 AA accessibility** compliance
```