# Liftrix Android App - Claude Development Context

## 🧠 Project Mental Model

Liftrix is a fitness tracking Android app with **Clean Architecture** and **offline-first design**:

### Core System Layers
- **Domain Layer**: Business logic with 60+ use cases, LiftrixResult<T> error handling
- **Data Layer**: Room database (v30) with Firebase sync, user-scoped operations
- **UI Layer**: 100% Jetpack Compose with type-safe navigation and Material 3
- **DI Layer**: 19 specialized Hilt modules for clean dependency management

### Data Flow Pattern
```
UI Event → ViewModel → Use Case → Repository → Room Database
                                       ↓
                              Firebase Sync (Background)
```

### Key Architecture Principles
- **Offline-First**: Room database is single source of truth
- **User Scoping**: ALL database operations filter by userId
- **Type Safety**: Sealed classes for navigation, state, and errors
- **Error Handling**: Comprehensive LiftrixResult<T> with recovery strategies

## 🧩 File Roles & Structure

### Core File Types
- **Entities**: `*Entity.kt` - Room database models with user scoping
- **DAOs**: `*Dao.kt` - Database access objects with user filtering
- **Repositories**: `*Repository.kt` (interfaces in domain, impls in data)
- **Use Cases**: `*UseCase.kt` - Business logic operations
- **ViewModels**: `*ViewModel.kt` - UI state management with BaseViewModel<S,E>
- **Screens**: `*Screen.kt` - Compose UI screens
- **Routes**: `LiftrixRoute.kt` - Type-safe navigation with @Serializable

### Key Entry Points
- **MainActivity**: Single activity with Compose navigation
- **LiftrixApplication**: Hilt application class
- **UnifiedWorkoutSessionManager**: Central session state management
- **LiftrixDatabase**: Room database with migration chain
- **Firebase Configuration**: Authentication, Firestore, Analytics

### Directory Structure
```
app/src/main/java/com/example/liftrix/
├── core/           # Error handling, extensions, utilities
├── data/           # Room entities, DAOs, repositories
├── di/             # 19 Hilt modules
├── domain/         # Use cases, models, repository interfaces
├── service/        # Business services and background workers
└── ui/             # Compose screens, navigation, theme
```

## ⚙️ Debug/Extend Hot Zones

### Most Common Touch Points
1. **ViewModels** (`ui/*/ViewModel.kt`) - State management and user interactions
2. **Use Cases** (`domain/usecase/*/`) - Business logic and validation
3. **Repositories** (`data/repository/*/`) - Data access patterns
4. **Navigation** (`ui/navigation/`) - Screen transitions and routing
5. **Database Entities** (`data/local/entity/`) - Data model changes

### Fragile/Complex Modules
- **UnifiedWorkoutSessionManager** - Session state management (recently refactored)
- **Database Migrations** - 19 migration files (v11→v30)
- **Firebase Sync Workers** - Background synchronization with conflict resolution
- **Analytics Engine** - Performance-critical calculations with caching

### Common Debug Scenarios
- **User Data Leakage**: Check all queries include `WHERE user_id = :userId`
- **State Management Issues**: Verify StateFlow sharing strategies
- **Navigation Problems**: Ensure @Serializable data classes
- **Sync Conflicts**: Check timestamp-based conflict resolution
- **Performance**: Monitor 60fps target with recomposition optimization

### Debug Tools
- **LiftrixResult<T>**: Error handling with context and recovery info
- **ErrorHandler**: Centralized error mapping and logging
- **Timber**: Structured logging (cleanup debug logs before production)
- **LeakCanary**: Memory leak detection

## 🧱 Architectural Rules

### Android Architecture Rules
- **Classes < 200 instructions, < 10 public methods/properties**
- **No static context leaks** - Use Hilt for dependency injection
- **Single Activity Architecture** with Compose navigation
- **Proper lifecycle management** with ViewModels and StateFlow

### Database Rules
- **NEVER mutate schemas directly** - Always create migration files
- **User scoping mandatory** - All entities must have userId field
- **Repository pattern** - Abstract all queries through repository interfaces
- **Sync metadata required** - Include is_synced and sync_version fields

### UI/UX Rules
- **Components < 200 lines** - Small, testable, layout-independent
- **Accessibility mandatory** - Content descriptions and WCAG 2.1 AA compliance
- **60fps target** - Monitor performance with custom metrics
- **Material 3 compliance** - Use LiftrixTheme and semantic colors

### Clean Architecture Rules
- **Layer separation** - Domain has zero Android/framework dependencies
- **Dependency inversion** - Interfaces in domain, implementations in data
- **Single responsibility** - Each use case handles one business operation
- **Error handling** - All operations return LiftrixResult<T>

## 🛠️ Feature Extension Guidelines

### Adding New Features
1. **Navigation**: Add to `LiftrixRoute` sealed class with @Serializable
2. **ViewModel**: Extend `BaseViewModel<S, E>` with UiState<T> pattern
3. **Use Cases**: Create in `domain/usecase/` returning LiftrixResult<T>
4. **Repository**: Interface in domain, implementation in data with user scoping
5. **Database**: Add entity with userId, create migration, update DAO
6. **DI**: Add to appropriate Hilt module or create new feature module

### Safe Extension Points
- **New Screens**: Follow `ui/*/Screen.kt` pattern with proper ViewModels
- **New Entities**: Add to `data/local/entity/` with user scoping and migrations
- **New Services**: Add to `service/` and register in ServiceModule
- **New Utilities**: Add to `ui/common/` or `core/extensions/`

### Testing Requirements
- **Unit Tests**: Use cases and repositories with MockK
- **UI Tests**: Compose tests for critical user flows
- **Integration Tests**: Database operations and sync workflows
- **Performance Tests**: 60fps validation and memory usage

### Registration Checklist
- [ ] Add to appropriate Hilt module
- [ ] Create migration file for database changes
- [ ] Update Firestore security rules for new data
- [ ] Add navigation extensions for new screens
- [ ] Implement proper error handling with LiftrixResult<T>
- [ ] Add accessibility support with content descriptions
- [ ] Create comprehensive unit and UI tests

### Performance Considerations
- **Database**: Use composite indexes for complex queries
- **UI**: Implement lazy loading and proper recomposition optimization
- **Network**: Batch Firebase operations and implement exponential backoff
- **Memory**: Monitor with LeakCanary and target <100ms for standard operations

## 🚨 Critical Gotchas

### Database User Scoping
```kotlin
// ✅ Correct - Always filter by userId
@Query("SELECT * FROM workouts WHERE user_id = :userId")

// ❌ Incorrect - Will cause data leakage
@Query("SELECT * FROM workouts")
```

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

### ViewModel Pattern
```kotlin
// ✅ Always extend BaseViewModel<S, E> with UiState<T>
// ❌ Don't create custom state management patterns
```

## 🔍 Quick Reference

### Key Classes to Know
- `UnifiedWorkoutSessionManager` - Central session state
- `LiftrixResult<T>` - Error handling wrapper
- `BaseViewModel<S, E>` - ViewModel base class
- `UiState<T>` - UI state management
- `LiftrixRoute` - Type-safe navigation

### Common Patterns
- All database operations are user-scoped
- Navigation uses @Serializable sealed classes
- Error handling uses LiftrixResult<T> pattern
- UI state follows Loading/Success/Error/Empty pattern
- Background sync uses WorkManager with retry policies

### Performance Targets
- 60fps UI rendering
- <100ms database queries
- <5s sync operations
- WCAG 2.1 AA accessibility compliance

This context provides the foundation for understanding Liftrix's architecture and safely extending its functionality while maintaining code quality and performance standards.