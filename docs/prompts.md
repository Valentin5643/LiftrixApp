# Liftrix AI Prompts and Development Guidelines

## Development Prompts

### Code Generation Prompts
When generating code for Liftrix, use these guidelines:

1. **Follow Clean Architecture**: Always separate concerns into presentation, domain, and data layers
2. **Use MVI Pattern**: Implement unidirectional data flow with sealed classes for states
3. **Firebase Integration**: Use repository pattern for all Firebase operations
4. **Offline-First**: Implement Room database as single source of truth
5. **Jetpack Compose**: Use modern declarative UI patterns

### AI Assistant Context
```
You are developing Liftrix, a fitness tracking app using:
- Kotlin with Jetpack Compose
- Firebase (Auth, Firestore, Storage, Analytics)
- Clean Architecture with MVI pattern
- Offline-first approach with Room database
- Hilt for dependency injection
- Coroutines and Flow for async operations
```

### Feature Development Template
When implementing new features:

1. **Domain Layer First**: Define use cases and domain models
2. **Repository Interface**: Create data contract in domain
3. **Repository Implementation**: Implement with Firebase and Room
4. **ViewModel**: Handle UI state with MVI pattern
5. **Compose UI**: Create declarative UI components
6. **Testing**: Add unit tests for business logic

### Firebase-Specific Prompts

#### Firestore Operations
```kotlin
// Always use repository pattern
class WorkoutRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val workoutDao: WorkoutDao
) {
    // Implement offline-first operations
}
```

#### Authentication Flow
```kotlin
// Centralize auth operations
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    // Handle sign-in, sign-out, and user state
}
```

### UI Development Guidelines

#### Compose Components
- Use `@Composable` functions for reusable UI
- Implement proper state management
- Handle loading, success, and error states
- Follow Material Design 3 principles

#### Navigation
- Use Navigation Compose
- Implement deep linking
- Handle back stack management

### Testing Prompts

#### Unit Testing
```kotlin
// Test use cases and ViewModels
@Test
fun `should return success when workout is saved`() {
    // Given, When, Then pattern
}
```

#### Integration Testing
```kotlin
// Test repository implementations
@Test
fun `should sync data with Firebase correctly`() {
    // Use Firebase emulator for testing
}
```

### Performance Optimization

#### Memory Management
- Use lazy loading for large lists
- Implement proper image caching
- Monitor memory usage in AI summaries

#### Background Processing
- Use WorkManager for sync operations
- Implement proper error handling
- Handle network connectivity changes

### Security Considerations

#### Data Protection
- Validate all user inputs
- Use Firebase Security Rules
- Encrypt sensitive local data
- Implement proper authentication flows

### AI Integration Guidelines

#### Workout Analysis
- Process workout data efficiently
- Generate meaningful insights
- Handle AI service errors gracefully
- Cache AI responses when appropriate

#### Prompt Engineering for Fitness AI
```
Analyze this workout data and provide:
1. Performance trends
2. Improvement suggestions
3. Potential injury prevention tips
4. Motivation insights
```

### Code Review Checklist

Before submitting code:
- [ ] Follows Clean Architecture principles
- [ ] Implements offline-first approach
- [ ] Uses proper error handling
- [ ] Includes unit tests
- [ ] Follows Kotlin coding standards
- [ ] Implements proper logging
- [ ] Handles edge cases
- [ ] Optimizes for performance

### Common Patterns

#### State Management
```kotlin
sealed class WorkoutUiState {
    object Loading : WorkoutUiState()
    data class Success(val workouts: List<Workout>) : WorkoutUiState()
    data class Error(val message: String) : WorkoutUiState()
}
```

#### Repository Pattern
```kotlin
interface WorkoutRepository {
    fun getWorkouts(): Flow<List<Workout>>
    suspend fun saveWorkout(workout: Workout)
    suspend fun syncWithFirebase()
}
```

### Troubleshooting Common Issues

#### Firebase Connection
- Check network connectivity
- Verify Firebase configuration
- Handle offline scenarios

#### Compose Performance
- Use `remember` for expensive calculations
- Implement proper key handling in LazyColumn
- Avoid unnecessary recompositions

#### Background Sync
- Handle WorkManager constraints
- Implement exponential backoff
- Monitor sync status and provide user feedback 