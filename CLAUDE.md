# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Liftrix Android App - Claude Development Context

## 🧠 Project Mental Model

Liftrix is a fitness tracking Android app with **Clean Architecture** and **offline-first design**:

### Core System Layers
- **Domain Layer**: Business logic with 60+ use cases, LiftrixResult<T> error handling
- **Data Layer**: Room database (v37) with Firebase sync, user-scoped operations
- **UI Layer**: 100% Jetpack Compose with type-safe navigation and Material 3
- **DI Layer**: 19 specialized Hilt modules for clean dependency management
- **Profile Layer**: Complete profile management with achievements, images, and social features
- **Social Layer**: User discovery with QR codes, privacy-aware profiles, connection management

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
- **Database Migrations** - 37 migration files with social/profile schema updates (v11→v37)
- **Firebase Sync Workers** - Background synchronization with conflict resolution
- **Analytics Engine** - Performance-critical calculations with caching
- **Progress Dashboard System** - 15 strength-focused widgets with 2-column mobile grid and interactive detail views
- **Widget Management System** - Complex preference handling and real-time updates
- **Profile Management System** - Complete profile workflow with achievements, images, and privacy
- **Social Discovery System** - User search with privacy controls, QR code generation, connection management
- **Achievement Calculation Engine** - Real-time achievement detection with streak calculations
- **Profile Image Pipeline** - Upload, crop, cache, and sync with error recovery
- **Chart Modernization System** - Interactive charts with bezier curves, gradients, and tap interactions

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

## 🔍 Quick Reference

### Key Classes to Know
- `UnifiedWorkoutSessionManager` - Central session state
- `LiftrixResult<T>` - Error handling wrapper
- `BaseViewModel<S, E>` - ViewModel base class
- `UiState<T>` - UI state management
- `LiftrixRoute` - Type-safe navigation
- `UnifiedWorkoutCard` - Foundational card component for workout screens
- `ModernActionButton` - Three-tier button system (Primary/Secondary/Tertiary)
- `LiftrixSpacing` - Semantic spacing tokens for consistent layouts
- `ProgressDashboardScreen` - Main Progress tab with 2-column mobile grid layout
- `ResponsiveDashboardLayout` - Adaptive grid layout (2-col mobile, 3-col tablet, 4-col desktop)
- `AdaptiveWidgetGrid` - LazyVerticalGrid with dynamic column calculation and card spanning
- `AnalyticsDetailScreen` - Reusable template for detail views with consistent scaffolding
- `OneRmProgressionDetailScreen` - Interactive 1RM detail view with filtering and time ranges
- `MuscleGroupDetailScreen` - Muscle group analysis with distribution charts and balance recommendations
- `ModernVolumeChart` - Enhanced chart with bezier curves, gradients, and PR markers
- `GlobalTimeRangeSelector` - Synchronized time selector affecting all charts simultaneously
- `ProfileViewModel` - Enhanced profile management with achievements and image upload
- `UserProfile` - Complete profile model with social features and achievements
- `UserSearchRepository` - Social discovery with privacy filtering and QR code generation
- `QRCodeService` - ZXing-based QR code generation and profile sharing
- `CalculateAchievementsUseCase` - Automatic achievement detection and assignment
- `ProfileImageManager` - Profile image upload, crop, and cache management

### Common Patterns
- All database operations are user-scoped with `WHERE user_id = :userId`
- Navigation uses @Serializable sealed classes (LiftrixRoute)
- Error handling uses LiftrixResult<T> pattern with recovery strategies
- UI state follows Loading/Success/Error/Empty pattern
- UI components use UnifiedWorkoutCard for consistent card layouts
- Button hierarchy follows Primary > Secondary > Tertiary pattern
- Spacing uses LiftrixSpacing semantic tokens
- Background sync uses WorkManager with retry policies
- Social features use privacy-aware data with viewer context
- QR codes follow deep linking pattern with web fallback
- Profile management uses enhanced ProfileViewModel with real-time updates
- Achievement system runs automatically after workout completion
- Image uploads include progress tracking and error recovery
- Privacy settings sync immediately with proper user feedback
- Progress dashboard uses 15 strength-focused widgets with deprecated widgets filtered out
- Grid layout adapts based on screen width: 2-col (<600dp), 3-col (600-767dp), 4-col (768dp+)
- Detail views use type-safe @Serializable navigation with parameter passing
- Charts implement bezier curves, gradient fills, and tap interactions with haptic feedback
- Time range changes synchronize across all charts with 300ms transitions

### Performance Targets
- 60fps UI rendering with optimized animations
- <100ms database queries
- <5s sync operations
- 150ms component interactions with haptic feedback
- WCAG 2.1 AA accessibility compliance

## 📚 Documentation

For comprehensive guidance on Liftrix systems:

### UI/UX Design System
- **[UI Redesign Guide](docs/ui-redesign-guide.md)** - Complete visual redesign overview and implementation details
- **[Component Library](docs/component-library.md)** - Detailed documentation of unified UI components with usage examples

### Progress Dashboard System
- **Grid-Based Layout**: 2-column mobile layout with responsive breakpoints (2/3/4 columns)
- **15 Strength-Focused Widgets**: Curated widget set with deprecated calorie/duration widgets filtered out
- **Interactive Detail Views**: 4 detail screens (1RM Progression, Volume Analysis, Muscle Groups, Exercise Rankings)
- **Type-Safe Navigation**: @Serializable routes with parameter passing (OneRmProgressionDetail, VolumeAnalysisDetail, etc.)
- **Modern Chart System**: Bezier curves, gradient fills, interactive markers, and synchronized time selectors
- **Performance Optimized**: <500ms detail screen loading, 60fps chart interactions, <100ms layout calculations

### Social Discovery System  
- **Privacy-First Architecture**: All profile data respects user privacy settings with viewer context
- **QR Code Integration**: ZXing-powered profile sharing with deep linking and web fallback
- **Search Performance**: <500ms cached results, intelligent user indexing with debounced queries
- **Connection Management**: Complete social graph with pending/accepted states and mutual discovery
- **Achievement System**: Automatic achievement detection with milestone tracking and badge display
- **Profile Completion**: Smart completion percentage calculation with user guidance

## 🎨 Liftrix 5-Color Design System

Liftrix uses a minimal 5-color palette achieving 98%+ app coverage:

### Core Colors
- **Night** (`#131515`) - Dark primary for text and true black backgrounds
- **Jet** (`#2B2C28`) - Dark secondary for surfaces and secondary text
- **Persian Green** (`#339989`) - Brand primary for actions and branding
- **Tiffany Blue** (`#7DE2D1`) - Brand secondary for highlights and selections
- **Snow** (`#FFFAFB`) - Light primary for backgrounds and surfaces
- **Exception**: Error states use red colors (only deviation from 5-color rule)

### Usage Guidelines
- Always use Material 3 color roles (`MaterialTheme.colorScheme.*`)
- Persian Green for primary actions, Tiffany Blue for secondary
- Snow/Night/Jet provide 90%+ surface coverage
- All combinations exceed WCAG 2.1 AA accessibility standards
- Error states are the only exception using red colors

### Component Integration
- `UnifiedWorkoutCard` uses semantic surface colors automatically
- `ModernActionButton` follows Persian Green (primary) / Tiffany Blue (secondary) hierarchy
- Navigation and form elements use appropriate brand color assignments
- All components maintain accessibility compliance through semantic color roles

## 🎨 UI Component Guidelines

### Using the Modern Component System

#### UnifiedWorkoutCard - Foundation Component
```kotlin
UnifiedWorkoutCard(
    title = "Push Day Workout",
    subtitle = "6 exercises",
    onClick = { /* Navigate to workout */ }
) {
    Text("Workout content and details")
    
    // Actions slot - uses Persian Green/Tiffany Blue hierarchy
    Row {
        SecondaryActionButton("Edit", onClick = { })      // Tiffany Blue
        PrimaryActionButton("Start", onClick = { })       // Persian Green
    }
}
```

#### ModernActionButton - Three-Tier System
```kotlin
// Primary actions (highest priority) - Persian Green
PrimaryActionButton("Start Workout", onClick = { })

// Secondary actions (medium priority) - Tiffany Blue
SecondaryActionButton("Edit Workout", onClick = { })

// Tertiary actions (lowest priority) - Persian Green with alpha
TertiaryActionButton("Learn More", onClick = { })
```

#### LiftrixSpacing - Semantic Layout
```kotlin
Column(
    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing),
    modifier = Modifier.padding(LiftrixSpacing.screenPadding)
) {
    UnifiedWorkoutCard(title = "Workout 1") { /* Content */ }
    UnifiedWorkoutCard(title = "Workout 2") { /* Content */ }
}
```

### Component Integration Rules
- **Always use UnifiedWorkoutCard** for workout-related content layouts
- **Follow button hierarchy** - Primary > Secondary > Tertiary with Persian Green/Tiffany Blue
- **Use semantic spacing tokens** from LiftrixSpacing object
- **Include accessibility** - components have built-in WCAG 2.1 AA compliance
- **Leverage animations** - components include 150ms press feedback and haptic response
- **Use Material 3 color roles** - Never reference colors directly, always use `MaterialTheme.colorScheme.*`

### Progress Dashboard Integration Rules
- **Use AdaptiveWidgetGrid** for responsive grid layouts with automatic column calculation
- **Extend AnalyticsDetailScreen** for consistent detail view scaffolding and navigation
- **Filter deprecated widgets** using `DEPRECATED_WIDGET_IDS` list (removes 13 calorie/duration widgets)
- **Implement bezier curves** in charts using `cubicTo()` with proper control point calculations
- **Add gradient fills** under chart lines using `Brush.verticalGradient()` with Persian Green/Tiffany Blue
- **Support full-width cards** using `GridItemSpan(columns)` for complex visualizations
- **Use TimeRange synchronization** across all charts with `GlobalTimeRangeSelector`

## 👤 Profile & Social System

### Key Components
- **ProfileScreen** - Main profile display with achievements and privacy controls
- **ProfileEditScreen** - Form-based editing with validation
- **ProfileViewModel** - State management with image upload and achievement integration
- **UserSearchRepository** - Privacy-aware user search with caching
- **QRCodeService** - Profile sharing via QR codes
- **CalculateAchievementsUseCase** - Automatic achievement detection

### Database Tables
- **user_profiles** - Enhanced with bio, privacy, streaks (Migration 35→37)
- **user_achievements** - Achievement tracking with display flags
- **user_search_cache** - Search result caching for performance
- **qr_code_mappings** - QR code to profile mapping

### Critical Integration Points
- Navigation routes need registration in UnifiedNavigationContainer
- New entities need registration in LiftrixDatabase
- DAOs need providers in DatabaseModule
- Achievement system may need activation in ProfileViewModel

This context provides the foundation for understanding Liftrix's architecture and safely extending its functionality while maintaining code quality and performance standards.[byterover-mcp]

# important 
always use byterover-retrive-knowledge tool to get the related context before any tasks 
always use byterover-store-knowledge to store all the critical informations after sucessful tasks