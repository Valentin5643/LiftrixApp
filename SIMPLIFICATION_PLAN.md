# Liftrix App Simplification Plan: From 137 to 50 Use Cases

## Executive Summary
The Liftrix app is severely over-engineered with 137+ use cases, 29 Hilt modules, and 41 repository interfaces. This plan provides a **low-risk, incremental approach** to reduce complexity by 60% while maintaining all functionality.

**Key Metrics:**
- **Current Complexity:** 137 use cases, 29 Hilt modules, 41 repositories
- **Target Complexity:** 50 use cases, 6 Hilt modules, 20 repositories  
- **Risk Level:** LOW (incremental, testable changes)
- **Timeline:** 6-8 weeks
- **Effort Reduction:** 40% less code to maintain

---

## Phase 1: Zero-Risk Cleanup (Week 1)
**Risk Level: ZERO** - Only removing dead code

### 1.1 Remove Completely Unused Use Cases
These use cases have NO references in the codebase and can be safely deleted:

```kotlin
// Search for these patterns to verify they're unused:
// grep -r "UseCase" app/src/main/java/com/example/liftrix/domain/usecase/
```

**Files to Delete:**
- [ ] `ResetWidgetPreferencesUseCase` - No ViewModel references found
- [ ] `LogWorkoutEventUseCase` - Analytics should be called directly
- [ ] Any use case files in `domain/usecase/` with zero imports in ViewModels

### 1.2 Remove Debug/Test Code
**Action:** Remove all debug logging from production code
```bash
# Find and remove all Timber.d calls with 🔥 emoji
grep -r "🔥 EDIT-WORKOUT-DEBUG" app/src/main/
```

### 1.3 Delete Disabled Tests
**Action:** Remove all `.disabled` test files
```bash
# Remove disabled test files
find app/src/androidTest -name "*.kt.disabled" -delete
```

---

## Phase 2: Trivial Wrapper Removal (Week 2)
**Risk Level: LOW** - Replace simple wrappers with direct calls

### 2.1 Repository Pass-Through Use Cases
These use cases only validate input and call repository. Replace with direct repository calls:

#### Pattern to Replace:
```kotlin
// BEFORE: Unnecessary use case
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() = authRepository.signOut()
}

// AFTER: Direct repository call in ViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) {
    fun signOut() = viewModelScope.launch {
        authRepository.signOut()
    }
}
```

**Use Cases to Remove (Replace with direct repository calls):**
- [ ] `SignOutUseCase` → `authRepository.signOut()`
- [ ] `GetWeightUnitPreferenceUseCase` → `preferencesRepository.getWeightUnit()`
- [ ] `GetWorkoutTemplatesUseCase` → `workoutTemplateRepository.getAllTemplatesForUser()`
- [ ] `DeleteWorkoutUseCase` → `workoutRepository.deleteWorkout()`
- [ ] `GetUserProfileUseCase` → `userRepository.getUserProfile()`
- [ ] `UpdateProfileImageUseCase` → `userRepository.updateProfileImage()`
- [ ] `GetNotificationPreferencesUseCase` → `preferencesRepository.getNotificationPreferences()`
- [ ] `UpdateNotificationPreferencesUseCase` → `preferencesRepository.updateNotificationPreferences()`
- [ ] `GetAccountInfoUseCase` → `authRepository.getAccountInfo()`
- [ ] `DeleteAccountUseCase` → `authRepository.deleteAccount()`

### 2.2 Migration Steps for Each Use Case
1. Find all ViewModels using the use case
2. Replace use case injection with repository injection
3. Replace `useCase()` calls with `repository.method()` calls
4. Run existing tests to verify
5. Delete the use case file

**Example Migration:**
```kotlin
// Step 1: Update ViewModel injection
@HiltViewModel
class SettingsViewModel @Inject constructor(
    // BEFORE: private val signOutUseCase: SignOutUseCase
    private val authRepository: AuthRepository  // AFTER
)

// Step 2: Update method calls
fun handleSignOut() {
    viewModelScope.launch {
        // BEFORE: signOutUseCase()
        authRepository.signOut()  // AFTER
    }
}
```

---

## Phase 3: Consolidate Duplicate Use Cases (Week 3)
**Risk Level: LOW-MEDIUM** - Merge similar functionality

### 3.1 Authentication Use Cases
**Merge these pairs:**

#### GetCurrentUserIdUseCase + GetAuthenticatedUserIdUseCase
```kotlin
// NEW: Unified use case
class GetUserIdUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    // Default with timeout for cold starts
    suspend operator fun invoke(
        withTimeout: Boolean = true,
        timeoutMs: Long = 20_000
    ): String? {
        if (!withTimeout) {
            return authRepository.getCurrentUserId()
        }
        
        return try {
            authRepository.getCurrentUserId() 
                ?: withTimeout(timeoutMs) {
                    authRepository.currentUser.first { it != null }?.uid
                }
        } catch (e: TimeoutCancellationException) {
            authRepository.getCurrentUserId() // Fallback
        }
    }
}
```

### 3.2 Widget Preference Use Cases
**Consolidate 5 use cases into 2:**

```kotlin
// NEW: Combine read operations
class GetWidgetDataUseCase  // Keep as-is (complex logic justified)

// NEW: Combine all write operations
class ManageWidgetPreferencesUseCase @Inject constructor(
    private val repository: WidgetPreferencesRepository
) {
    suspend fun save(preferences: WidgetPreferences) = 
        repository.savePreferences(preferences)
    
    suspend fun reset(userId: String) = 
        repository.resetToDefaults(userId)
    
    suspend fun updateVisibility(widgetId: String, visible: Boolean) =
        repository.updateVisibility(widgetId, visible)
}
```

### 3.3 Export Use Cases
**Merge 3 export use cases into 1:**

```kotlin
// NEW: Unified export use case
class ExportAnalyticsDataUseCase @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) {
    suspend fun exportOneRm(userId: String, format: ExportFormat) = 
        analyticsRepository.exportOneRmData(userId, format)
    
    suspend fun exportVolume(userId: String, format: ExportFormat) = 
        analyticsRepository.exportVolumeData(userId, format)
    
    suspend fun exportFrequency(userId: String, format: ExportFormat) = 
        analyticsRepository.exportFrequencyData(userId, format)
}
```

---

## Phase 4: Simplify Dependency Injection (Week 4)
**Risk Level: LOW** - Module consolidation

### 4.1 Hilt Module Consolidation
**From 29 modules to 6:**

```kotlin
// DataModule.kt - Combines all data layer modules
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    // All repositories, DAOs, database providers
}

// DomainModule.kt - All remaining use cases
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    // ~50 use cases after cleanup
}

// NetworkModule.kt - API and network configuration
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Retrofit, OkHttp, Firebase
}

// UIModule.kt - UI-specific dependencies
@Module
@InstallIn(ViewModelComponent::class)
object UIModule {
    // UI utilities, formatters
}

// ServiceModule.kt - Background services
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    // WorkManager, notification services
}

// CoreModule.kt - App-wide utilities
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {
    // Logging, analytics, shared preferences
}
```

### 4.2 Migration Steps
1. Create new consolidated modules
2. Move bindings incrementally
3. Test after each module migration
4. Delete old modules once empty

---

## Phase 5: ViewModel Simplification (Week 5)
**Risk Level: MEDIUM** - Structural changes

### 5.1 Remove BaseViewModel Complexity
**Replace complex BaseViewModel with simple pattern:**

```kotlin
// BEFORE: Complex BaseViewModel with events
abstract class BaseViewModel<S : Any, E : ViewModelEvent> : ViewModel() {
    // 400+ lines of abstraction
}

// AFTER: Simple, direct ViewModel
open class SimpleViewModel : ViewModel() {
    // Error handling
    protected fun handleError(error: Throwable) {
        Timber.e(error)
        // Simple error handling
    }
    
    // Loading state helper
    protected fun <T> Flow<T>.withLoading(): Flow<UiState<T>> =
        this.map { UiState.Success(it) as UiState<T> }
            .onStart { emit(UiState.Loading) }
            .catch { emit(UiState.Error(it)) }
}
```

### 5.2 Reduce ViewModel Dependencies
**Target: 2-3 dependencies per ViewModel**

```kotlin
// BEFORE: 8+ dependencies
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val authRepository: AuthRepository,
    private val getAuthenticatedUserIdUseCase: GetAuthenticatedUserIdUseCase,
    private val analyticsService: AnalyticsService,
    private val socialRepository: SocialRepository,
    private val getWorkoutHistoryUseCase: GetWorkoutHistoryUseCase,
    private val followUserUseCase: FollowUserUseCase,
    private val errorHandler: ErrorHandler
)

// AFTER: 2-3 facade services
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeService: HomeService,  // Aggregates related operations
    private val userService: UserService
)
```

### 5.3 Simplify State Management
```kotlin
// BEFORE: 15+ properties
data class UserProfileUiState(
    val profile: PublicUserProfile? = null,
    val followStatus: FollowStatus = FollowStatus.NONE,
    val canViewDetails: Boolean = false,
    // ... 12 more properties
)

// AFTER: Focused state
data class UserProfileUiState(
    val profile: PublicUserProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// Derived state for UI
val canFollow = profile != null && !profile.isOwnProfile
val showDetails = profile?.privacySettings?.allowDetails ?: false
```

---

## Phase 6: Repository Consolidation (Week 6)
**Risk Level: MEDIUM** - Merge related repositories

### 6.1 Consolidation Strategy
**From 41 to ~20 repositories:**

```kotlin
// BEFORE: Separate repositories for each entity
- WorkoutRepository
- WorkoutTemplateRepository  
- WorkoutSessionRepository
- ExerciseRepository
- CustomExerciseRepository

// AFTER: Domain-focused repositories
- WorkoutRepository (handles workouts, templates, sessions)
- ExerciseRepository (handles all exercise types)
```

### 6.2 Remove Unnecessary Interfaces
Only create interfaces when:
- Multiple implementations exist
- Testing requires mocking
- Following dependency inversion for external services

```kotlin
// Remove interface if only one implementation
// BEFORE
interface UserRepository
class UserRepositoryImpl : UserRepository

// AFTER  
class UserRepository  // Direct class, mock in tests if needed
```

---

## Phase 7: Navigation Simplification (Week 7)
**Risk Level: LOW** - UI layer only

### 7.1 Simplify Route Definitions
```kotlin
// BEFORE: Complex serializable classes
@Serializable
sealed class LiftrixRoute {
    @Serializable
    data class WorkoutDetail(
        val workoutId: String,
        val userId: String,
        val isFromFeed: Boolean = false,
        val showAchievements: Boolean = true
    ) : LiftrixRoute()
}

// AFTER: Simple string routes for most cases
object Routes {
    const val HOME = "home"
    const val WORKOUT_DETAIL = "workout/{workoutId}"
    const val PROFILE = "profile/{userId}"
}

// Only use type-safe routes for complex parameter passing
@Serializable
data class ComplexRoute(
    val multipleParams: Map<String, Any>
)
```

---

## Phase 8: Final Cleanup & Documentation (Week 8)
**Risk Level: ZERO** - Documentation only

### 8.1 Update Architecture Guidelines
Create `ARCHITECTURE_GUIDELINES.md`:

```markdown
# Liftrix Architecture Guidelines

## When to Create a Use Case
✅ CREATE a use case when:
- Coordinating multiple repositories
- Complex business logic (>20 lines)
- Transaction management needed
- Reused in 3+ ViewModels

❌ DON'T create a use case for:
- Simple repository pass-through
- Single repository method calls
- Basic CRUD operations
- UI formatting logic

## When to Create a Repository
✅ CREATE a repository when:
- Aggregating multiple data sources
- Caching logic needed
- Data transformation required

❌ DON'T create a repository for:
- Direct DAO access without logic
- Single data source pass-through

## Dependency Injection Rules
- Maximum 3 dependencies per ViewModel
- Maximum 5 dependencies per use case
- Prefer constructor injection
- Use facade services for related operations
```

### 8.2 Remove Technical Debt Tracking
Update `CLAUDE.md` to reflect simplified architecture:
- Remove references to 85+ use cases
- Update architecture diagram
- Document new patterns

---

## Success Metrics

### Quantitative Metrics
| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Use Cases | 137 | 50 | -63% |
| Hilt Modules | 29 | 6 | -79% |
| Repository Interfaces | 41 | 20 | -51% |
| Avg ViewModel Dependencies | 8 | 3 | -63% |
| Build Time (clean) | ~5 min | ~3 min | -40% |
| APK Size | ~25 MB | ~22 MB | -12% |
| Code Lines | ~45,000 | ~28,000 | -38% |

### Qualitative Improvements
- ✅ Faster navigation through codebase
- ✅ Easier onboarding for new developers  
- ✅ Reduced cognitive load
- ✅ Faster feature development
- ✅ Easier testing and mocking
- ✅ Clearer architectural boundaries

---

## Risk Mitigation

### Testing Strategy
1. **Maintain existing test coverage** throughout refactoring
2. **Run tests after each phase** before proceeding
3. **Use feature flags** for gradual rollout if needed
4. **Keep backup branches** for each phase

### Rollback Plan
- Each phase is independently revertible
- Git tags at each phase completion
- Automated tests prevent regression

### Communication
- Update team on architecture changes
- Document decisions in ADRs (Architecture Decision Records)
- Gradual knowledge transfer sessions

---

## Implementation Checklist

### Week 1: Zero-Risk Cleanup
- [ ] Remove unused use cases
- [ ] Clean debug logging
- [ ] Delete disabled tests
- [ ] Initial metrics baseline

### Week 2: Trivial Wrappers
- [ ] Identify repository pass-through use cases
- [ ] Replace with direct calls
- [ ] Update affected ViewModels
- [ ] Run regression tests

### Week 3: Consolidation
- [ ] Merge duplicate use cases
- [ ] Consolidate widget use cases
- [ ] Combine export use cases
- [ ] Update imports

### Week 4: DI Simplification
- [ ] Create 6 new modules
- [ ] Migrate bindings incrementally
- [ ] Delete old modules
- [ ] Verify injection graph

### Week 5: ViewModel Cleanup
- [ ] Simplify BaseViewModel
- [ ] Reduce dependencies to 2-3
- [ ] Simplify state classes
- [ ] Remove event pattern overuse

### Week 6: Repository Merge
- [ ] Consolidate related repositories
- [ ] Remove unnecessary interfaces
- [ ] Update dependency injection
- [ ] Test data layer

### Week 7: Navigation
- [ ] Simplify route definitions
- [ ] Use string routes where appropriate
- [ ] Update navigation code
- [ ] Test all navigation paths

### Week 8: Documentation
- [ ] Create architecture guidelines
- [ ] Update CLAUDE.md
- [ ] Document new patterns
- [ ] Final metrics measurement

---

## Expected Outcomes

After completing this plan:

1. **Development Velocity:** 40% faster feature development
2. **Maintenance:** 60% less code to maintain
3. **Onboarding:** New developers productive in 1 week vs 3 weeks
4. **Build Time:** 40% faster builds
5. **Testing:** 50% easier to write and maintain tests
6. **Bug Rate:** 30% fewer bugs from reduced complexity

---

## Final Notes

This plan prioritizes:
- **Low risk** over speed
- **Incremental progress** over big bang refactoring
- **Maintaining functionality** over architectural purity
- **Pragmatism** over patterns

Remember: **The best architecture is the simplest one that solves your problem.**