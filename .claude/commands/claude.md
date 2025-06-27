# Liftrix Android App - Claude Code Development Rules

## Project Overview

**Liftrix** is a fitness tracking Android application built with modern Android development practices:
- **Architecture**: Clean Architecture with MVVM pattern (UI/Domain/Data layers)
- **UI Framework**: Jetpack Compose with Material 3 design system
- **Dependency Injection**: Dagger Hilt
- **Database**: Room (SQLite) with offline-first approach
- **Backend**: Firebase (Auth, Firestore, Analytics, Crashlytics, Performance)
- **Background Processing**: WorkManager for data synchronization
- **Language**: Kotlin with modern features (value classes, inline classes, coroutines)
- **Testing**: JUnit4, MockK, Espresso, Compose UI tests
- **Build System**: Gradle with Kotlin DSL and version catalogs

## Fundamental Principles

- Start with uncertainty, build to 95%+ confidence through systematic analysis
- Confidence-Driven Development - whenever unsure, do not hesitate to ask the user specific questions
- Read before write: Always understand existing context before modifications
- Root cause over symptoms: Fix underlying issues, never just patch symptoms
- Incremental precision: Make minimal changes to achieve requirements

## SOLID Foundation

- Single Responsibility: Classes have one reason to change
- Open/Closed: Open for extension, closed for modification
- Liskov Substitution: Subtypes must be substitutable for base types
- Interface Segregation: Clients shouldn't depend on unused interfaces
- Dependency Inversion: Depend on abstractions, not concretions

## Project-Specific Code Quality Standards

### Architecture Patterns
- **Clean Architecture**: Follow domain/data/ui layer separation strictly
- **Repository Pattern**: All data access goes through repository interfaces
- **Use Cases**: Business logic encapsulated in focused use case classes
- **Dependency Injection**: Use Hilt modules for dependency management
- **State Management**: Combine StateFlow/LiveData with Compose state properly

### Naming Conventions
- **Package Names**: `com.example.liftrix.{layer}.{feature}` (e.g., `data.repository`, `domain.usecase.auth`)
- **Classes**: PascalCase with descriptive suffixes (`UserProfileRepository`, `SaveWorkoutUseCase`)
- **Value Classes**: Domain models as value classes when appropriate (`Weight`, `Reps`, `WorkoutId`)
- **Compose Components**: PascalCase function names (`AuthTextField`, `OnboardingScreenTemplate`)
- **Database Entities**: Suffix with `Entity` (`UserProfileEntity`)
- **DTOs**: Suffix with `Dto` (`UserProfileDto`)
- **Workers**: Suffix with `Worker` (`ProfileSyncWorker`)

### Kotlin-Specific Patterns
- **Value Classes**: Use `@JvmInline value class` for type-safe primitives
- **Extension Functions**: Add domain-specific extensions to existing types
- **Sealed Classes**: Model state and results with sealed hierarchies
- **Coroutines**: Prefer `suspend` functions over callback patterns
- **Null Safety**: Leverage Kotlin's null safety, avoid `!!` operator

### Android-Specific Patterns
- **Compose**: Stateless composables with clear parameter contracts
- **ViewModels**: Use `@HiltViewModel` with proper lifecycle management
- **Workers**: Background sync with proper retry/error handling mechanisms
- **Navigation**: Single activity architecture with Compose Navigation
- **Resources**: Use semantic color names in theme (`LiftrixColors.Primary`)

## Technology Stack Rules

### Build Configuration
- **Gradle**: Use Kotlin DSL exclusively (`build.gradle.kts`)
- **Version Catalogs**: Centralize dependencies in `libs.versions.toml`
- **Build Variants**: Support debug/release with proper ProGuard rules
- **Compile SDK**: Target latest stable (currently 35)
- **Min SDK**: Support API 26+ (Android 8.0)

### Dependencies Management
- **Hilt**: All dependency injection through Hilt modules
- **Room**: Database migrations for schema changes
- **Firebase**: Proper error handling for network operations
- **WorkManager**: Constraint-based background work scheduling
- **Timber**: Structured logging throughout the application

### Testing Strategy
- **Unit Tests**: Use MockK for mocking, test use cases and repositories
- **Integration Tests**: Test Room DAOs and repository implementations
- **UI Tests**: Compose UI tests for critical user flows
- **Test Structure**: Follow Given/When/Then pattern in test naming

### Design System Implementation
- **Colors**: Use semantic naming (`LiftrixColors.Primary` = #20C9B7)
- **Typography**: Poppins Bold (headlines), Inter Medium (body), Roboto Mono Light (data)
- **Components**: Reusable Compose components with consistent theming
- **Accessibility**: Support TalkBack and large text sizes

# Explore-Plan-Code-Commit Workflow

- Read relevant source files to understand current state
- Analyze existing patterns, naming conventions, architectures  
- Identify integration points and dependencies
- Add meaningful comments for complex logic
- Maintain backward compatibility unless explicitly changing interface

## Liftrix Development Commands

### Build & Testing
```bash
./gradlew test                    # Run all tests
./gradlew build                   # Build the project
./gradlew assembleDebug          # Build debug APK
./gradlew lint                   # Run lint checks
```

### Database Operations
- **Migrations**: Create incremental migration files in `data/local/migration/`
- **Schema Export**: Enable schema export in Room configuration

# Commit rules

- Commit frequently with logical, atomic changes
- Write clear, descriptive commit messages
- Follow conventional commit format when applicable
- Reference issues/tickets in commit messages
- Ensure clean git history without work-in-progress commits

### Commit Message Format (Conventional Commits): 

type(scope): brief description (max 50 chars)
- Why the change was made
- What specific behavior changed
- Any side effects or considerations
- Refs: #123, #456 Co-authored-by: Name email@example.com