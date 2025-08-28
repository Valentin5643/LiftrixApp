# Contributing to Liftrix

First off, thank you for considering contributing to Liftrix! It's people like you that make Liftrix such a great fitness platform.

## Code of Conduct

This project and everyone participating in it is governed by the [Liftrix Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues as you might find that you don't need to create one. When you are creating a bug report, please include as many details as possible:

* **Use a clear and descriptive title**
* **Describe the exact steps to reproduce the problem**
* **Provide specific examples to demonstrate the steps**
* **Describe the behavior you observed and expected**
* **Include screenshots and animated GIFs if possible**
* **Include crash reports with stack traces**

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

* **Use a clear and descriptive title**
* **Provide a detailed description of the proposed enhancement**
* **Provide specific use cases and examples**
* **Explain why this enhancement would be useful**
* **List any potential drawbacks or considerations**

### Pull Requests

* Fill in the required template
* Do not include issue numbers in the PR title
* Follow the Kotlin style guide
* Include comprehensive tests
* Update documentation as needed
* End all files with a newline

## Development Process

### 1. Setup Your Environment

```bash
# Fork and clone the repository
git clone https://github.com/yourusername/liftrix.git
cd liftrix

# Create a feature branch
git checkout -b feature/your-feature-name

# Install dependencies
./gradlew dependencies
```

### 2. Make Your Changes

#### Code Standards

* **Functions**: Keep under 20 instructions
* **Classes**: Keep under 200 instructions
* **Error Handling**: Use `LiftrixResult<T>` pattern
* **Database Queries**: ALWAYS include `user_id` filtering
* **UI Components**: Use Material 3 and LiftrixColorsV2
* **Navigation**: Use type-safe serializable routes

#### Critical Rules

1. **NEVER** create database queries without user_id filtering
2. **ALWAYS** use LiftrixResult<T> for error handling
3. **PREFER** editing existing files over creating new ones
4. **USE** existing UI components from the design system
5. **TEST** with emulator-based tests only

### 3. Test Your Changes

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests
./gradlew connectedDebugAndroidTest

# Check compilation
./gradlew compileDebugKotlin

# Run lint checks
./gradlew lint
```

### 4. Commit Your Changes

```bash
# Stage your changes
git add .

# Commit with conventional commit message
git commit -m "feat: add new workout template feature"
```

#### Commit Message Format

* **feat**: A new feature
* **fix**: A bug fix
* **docs**: Documentation only changes
* **style**: Code style changes (formatting, etc)
* **refactor**: Code refactoring
* **test**: Adding or updating tests
* **chore**: Maintenance tasks

### 5. Push and Create PR

```bash
# Push to your fork
git push origin feature/your-feature-name

# Create pull request via GitHub
```

## Architecture Guidelines

### Clean Architecture Compliance

```kotlin
// ✅ CORRECT - Following clean architecture
class GetWorkoutsUseCase @Inject constructor(
    private val repository: WorkoutRepository
) {
    suspend operator fun invoke(userId: String): LiftrixResult<List<Workout>>
}

// ❌ WRONG - Skipping layers
class WorkoutViewModel : ViewModel() {
    // Don't access DAOs directly from ViewModels
}
```

### User Scoping Requirements

```kotlin
// ✅ CORRECT - User-scoped query
@Query("SELECT * FROM workouts WHERE user_id = :userId")
suspend fun getWorkoutsForUser(userId: String): List<WorkoutEntity>

// ❌ WRONG - Missing user scoping
@Query("SELECT * FROM workouts")
suspend fun getAllWorkouts(): List<WorkoutEntity>
```

### Error Handling Pattern

```kotlin
// ✅ CORRECT - Using LiftrixResult
suspend fun createWorkout(request: CreateWorkoutRequest): LiftrixResult<Workout> = 
    liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "WORKOUT_CREATION_FAILED",
                errorMessage = "Failed to create workout"
            )
        }
    ) {
        // Business logic here
    }
```

## Testing Requirements

### Unit Tests

* Minimum 80% coverage for new features
* Use MockK for mocking dependencies
* Test all edge cases and error scenarios

### Integration Tests

* Test complete user workflows
* Verify sync operations
* Test privacy controls

### UI Tests

* Use Compose testing framework
* Test navigation flows
* Verify accessibility compliance

## Documentation

* Update README.md if adding major features
* Document complex algorithms and business logic
* Update API documentation for new endpoints
* Add code comments for non-obvious implementations

## Review Process

1. **Automated Checks**: CI/CD runs tests and lint checks
2. **Code Review**: At least one maintainer review required
3. **Testing**: Manual testing on different devices/API levels
4. **Documentation**: Ensure all docs are updated
5. **Merge**: Squash and merge to maintain clean history

## Questions?

Feel free to ask questions in:
* GitHub Issues for specific problems
* Discord server for general discussion
* Email maintainers for sensitive issues

Thank you for contributing to Liftrix! 💪