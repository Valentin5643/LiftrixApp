# UI Layer userId Usage and Navigation Patterns

**Document Purpose**: Map out how `userId` flows through the Compose UI layer, ViewModels, and navigation system to support the introduction of a type-safe `UserId` class.

**Analysis Date**: November 29, 2025
**Codebase Version**: Latest (Master branch)

---

## Executive Summary

The Liftrix app uses **three primary mechanisms** for userId flow in the UI layer:

1. **Dependency Injection via AuthQueryUseCase** - Most common, centralized pattern
2. **Navigation Route Parameters** - Type-safe with @Serializable, userId passed as String
3. **UI State Storage** - ViewModels hold userId in StateFlow or in UiState classes

**Current Pattern**: userId is consistently passed as `String` across all layers. There are **NO Parcelize bundles** or @Assisted injection patterns for userId in Compose screens.

---

## 1. ViewModel userId Patterns

### 1.1 BaseViewModel Architecture

**File**: `ModernBaseViewModel.kt`

```kotlin
abstract class ModernBaseViewModel<S : Any>(
    initialState: S
) : ViewModel() {
    protected val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected fun updateState(transform: (S) -> S) {
        _uiState.update(transform)
    }
}
```

**Key Pattern**:
- No forced userId in base class
- Each ViewModel manages userId according to its needs
- State is generic `S` - can include userId if needed

### 1.2 How ViewModels Receive userId

**Pattern A: AuthQueryUseCase (Most Common)**

ViewModels inject `AuthQueryUseCase` to fetch current userId at runtime:

```kotlin
// ProfileViewModel.kt - EXAMPLE
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authQueryUseCase: AuthQueryUseCase,
    // ... other dependencies
) : ModernBaseViewModel<ProfileUiState>(initialState = ProfileUiState(...)) {

    // Current user ID flow with enhanced error handling
    private val currentUserId = flow {
        var retryCount = 0
        val maxRetries = 3

        while (retryCount <= maxRetries) {
            try {
                val result = authQueryUseCase(waitForAuth = false)
                result.fold(
                    onSuccess = { userId ->
                        emit(userId)
                        return@flow
                    },
                    onFailure = { error ->
                        if (retryCount < maxRetries) {
                            val delayMs = (1000L * (retryCount + 1))
                            kotlinx.coroutines.delay(delayMs)
                        }
                    }
                )
            } catch (e: Exception) {
                // Error handling
            }
            retryCount++
        }
        emit(null) // Final failure
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
}
```

**Key Characteristics**:
- userId flows through a `StateFlow<String?>`
- Can retry on cold start (Firebase auth initialization delays)
- Returns null if authentication fails
- Used in `flatMapLatest` chains for reactive updates

---

**Pattern B: Navigation Parameter Injection (For Social Screens)**

For screens displaying another user's data:

```kotlin
// PublicProfileViewModel.kt - EXAMPLE
@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val socialProfileQueryUseCase: SocialProfileQueryUseCase,
    // ... other dependencies
) : ModernBaseViewModel<PublicProfileUiState>(
    initialState = PublicProfileUiState(...)
) {
    // Called explicitly from Compose screen with route userId
    fun handleEvent(event: PublicProfileEvent) {
        when (event) {
            is PublicProfileEvent.LoadProfile -> {
                loadProfile(event.userId)  // userId passed as parameter
            }
        }
    }

    private fun loadProfile(userId: String) {
        // userId used directly for API calls
    }
}
```

**Key Characteristics**:
- userId comes from navigation route parameters
- Passed explicitly to event handlers
- No automatic userId injection - requires explicit passing
- Used in UseCase invocations with request objects

---

**Pattern C: Optional Current userId in UiState**

ViewModels sometimes store current userId in their UI state:

```kotlin
// PublicProfileUiState.kt - EXAMPLE
data class PublicProfileUiState(
    val profile: PublicUserProfile?,
    val isLoading: Boolean,
    val error: LiftrixError?,
    val isConnectionLoading: Boolean,
    val currentUserId: String? = null,  // <-- Current user stored here
    val workoutPosts: Flow<PagingData<WorkoutPost>> = flowOf(PagingData.empty()),
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet()
)
```

**Usage in ViewModel**:
```kotlin
private fun loadCurrentUserId() {
    viewModelScope.launch {
        val currentUserId = authQueryUseCase(waitForAuth = false).fold(
            onSuccess = { it },
            onFailure = { null }
        )
        updateState { it.copy(currentUserId = currentUserId) }
    }
}
```

---

## 2. Navigation Route Patterns

### 2.1 Routes with userId Parameters

**File**: `LiftrixRoute.kt`

Routes using userId follow this pattern:

```kotlin
@Serializable
sealed class LiftrixRoute {

    /**
     * Public profile display - userId required
     */
    @Serializable
    data class PublicProfile(
        val userId: String,
        val initialAction: String? = null
    ) : LiftrixRoute()

    /**
     * QR code display - userId optional (defaults to current user)
     */
    @Serializable
    data class QRCodeDisplay(val userId: String? = null) : LiftrixRoute()

    /**
     * Followers list - userId required to specify which user's followers
     */
    @Serializable
    data class FollowersList(
        val userId: String,
        val listType: String = "FOLLOWERS"
    ) : LiftrixRoute()

    /**
     * Following list - userId required
     */
    @Serializable
    data class FollowingList(
        val userId: String,
        val listType: String = "FOLLOWING"
    ) : LiftrixRoute()

    /**
     * Profile edit - no userId needed (always current user)
     */
    @Serializable
    data object ProfileEdit : LiftrixRoute()

    /**
     * Profile view - optional userId (null = current user)
     */
    @Serializable
    data class Profile(val userId: String? = null) : LiftrixRoute()
}
```

### 2.2 Route Composition in NavHost

**File**: `UnifiedNavigationContainer.kt`

Routes are composed using `toRoute<T>()` extension:

```kotlin
// PublicProfile route composition
composable<LiftrixRoute.PublicProfile> { backStackEntry ->
    val route = backStackEntry.toRoute<LiftrixRoute.PublicProfile>()
    com.example.liftrix.ui.profile.UserProfileScreen(
        userId = route.userId,  // <-- userId extracted from route
        onNavigateBack = {
            navController.popBackStackSafely()
        },
        onNavigateToFollowersList = { userId ->
            navController.navigate(
                LiftrixRoute.FollowersList(userId = userId, listType = "FOLLOWERS")
            )
        },
        onNavigateToFollowingList = { userId ->
            navController.navigate(
                LiftrixRoute.FollowingList(userId = userId, listType = "FOLLOWING")
            )
        },
        onNavigateToWorkoutDetail = { workoutId ->
            navController.navigate(LiftrixRoute.WorkoutDetails(workoutId))
        }
    )
}

// FollowersList route composition
composable<LiftrixRoute.FollowersList> { backStackEntry ->
    val route = backStackEntry.toRoute<LiftrixRoute.FollowersList>()
    val listType = when (route.listType) {
        "FOLLOWERS" -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWERS
        "FOLLOWING" -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWING
        "PENDING_REQUESTS" -> com.example.liftrix.ui.profile.FollowerListType.PENDING_REQUESTS
        else -> com.example.liftrix.ui.profile.FollowerListType.FOLLOWERS
    }

    com.example.liftrix.ui.profile.FollowerListScreen(
        userId = route.userId,  // <-- userId passed directly to Compose
        listType = listType,
        onNavigateBack = {
            navController.popBackStackSafely()
        }
    )
}

// QRCodeDisplay route composition with null handling
composable<LiftrixRoute.QRCodeDisplay> { backStackEntry ->
    val route = backStackEntry.toRoute<LiftrixRoute.QRCodeDisplay>()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(route.userId) {
        if (route.userId == null) {
            // Fetch current user ID if not provided
            coroutineScope.launch {
                val currentUserId = viewModel.getCurrentUserId()
                if (currentUserId == null) {
                    navController.navigateAndReplace(LiftrixRoute.AuthSignIn)
                    return@launch
                }
                navController.navigateAndReplace(
                    LiftrixRoute.QRCodeDisplay(currentUserId)
                )
            }
        }
    }

    // Only render if we have userId
    route.userId?.let { userId ->
        com.example.liftrix.ui.social.QRCodeDisplayScreen(
            userId = userId,
            onNavigateBack = {
                navController.popBackStackSafely()
            }
        )
    }
}
```

### 2.3 Navigation Extension Functions

Navigation is wrapped in extension functions for cleaner API:

```kotlin
// NavigationExtensions.kt examples
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(userId))
}

fun NavController.navigateToFollowersList(userId: String, listType: String = "FOLLOWERS") {
    navigate(LiftrixRoute.FollowersList(userId = userId, listType = listType))
}

fun NavController.navigateToProfile(userId: String? = null) {
    navigate(LiftrixRoute.Profile(userId = userId))
}
```

### 2.4 Deep Linking with userId

**File**: `DeepLinkHandler.kt`

Deep links parse userId from URLs:

```kotlin
// Profile deep link: liftrix://profile/{userId}
if (path.startsWith("/profile/")) {
    val userId = path.removePrefix("/profile/").takeIf { it.isNotBlank() }
    if (userId != null && userId.length == 28) { // Firebase UID length
        navController.navigateFromDeepLink(LiftrixRoute.PublicProfile(userId))
        return true
    }
}

// QR code deep link: liftrix://user/{userId}
if (path.startsWith("/user/")) {
    val userId = path.removePrefix("/user/").takeIf { it.isNotBlank() }
    if (userId != null && userId.length == 28) {
        navController.navigateFromDeepLink(LiftrixRoute.PublicProfile(userId))
        return true
    }
}
```

---

## 3. Compose Integration Patterns

### 3.1 Passing userId to Composables

**Pattern A: Direct Parameter**

```kotlin
// Most common pattern - userId passed as String parameter
@Composable
fun UserProfileScreen(
    userId: String,  // <-- Direct userId parameter
    onNavigateBack: () -> Unit,
    onNavigateToFollowersList: (userId: String) -> Unit,
    // ...
)

// Usage in navigation
com.example.liftrix.ui.profile.UserProfileScreen(
    userId = route.userId,
    onNavigateBack = { ... }
)
```

**Pattern B: Via ViewModel (For Current User)**

```kotlin
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // userId available from uiState
    val userId = (uiState as? ProfileUiState)?.userId
    // ... render based on state
}
```

**Pattern C: From Navigation Arguments**

```kotlin
@Composable
fun SocialScreen(
    navController: NavHostController,
    initialTab: String? = null
) {
    // Screens that navigate to other userId routes
    val onProfileClick = { userId: String ->
        navController.navigateToPublicProfile(userId)
    }
}
```

### 3.2 No Parcelize Usage

**Finding**: The codebase does **NOT** use `@Parcelize` for userId or navigation parameters.

Checked files:
- `Equipment.kt` - uses Parcelize but not for userId
- `ExerciseLibrary.kt` - uses Parcelize but not for userId
- All route objects use `@Serializable` instead of `@Parcelize`

This is the correct approach because:
- `@Serializable` is the modern Compose Navigation standard
- Routes are serialized to JSON, not Bundles
- No Bundle/Intent overhead in Compose Navigation

### 3.3 No @Assisted Injection

**Finding**: The codebase does **NOT** use `@Assisted` factory patterns for userId injection.

Instead:
- Navigation passes userId as direct route parameters
- ViewModels fetch current userId via AuthQueryUseCase
- Compose screens receive userId as regular function parameters

---

## 4. State Management Patterns

### 4.1 UiState Pattern with userId

**File**: `UiState.kt`

Generic pattern for UI state (userId not forced into base):

```kotlin
// Generic Success state
@Stable
data class Success<T>(
    val data: T,
    val isRefreshing: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : UiState<T>()
```

**Custom UiState Examples**:

```kotlin
// ProfileUiState - holds current userId in state
data class ProfileUiState(
    val userId: String? = null,  // Current user ID
    val profileState: ProfileLoadingState = ProfileLoadingState.Loading,
    val achievements: List<UserAchievement> = emptyList(),
    val isLoading: Boolean = false,
    val error: ProfileError? = null,
    val successMessage: String? = null
)

// PublicProfileUiState - holds both current user and viewed user
data class PublicProfileUiState(
    val profile: PublicUserProfile?,  // User being viewed
    val isLoading: Boolean,
    val error: LiftrixError?,
    val isConnectionLoading: Boolean,
    val currentUserId: String? = null,  // Viewer's user ID
    val workoutPosts: Flow<PagingData<WorkoutPost>> = flowOf(PagingData.empty()),
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet()
)
```

### 4.2 StateFlow Patterns with userId

```kotlin
// Profile ViewModel - userId in a dedicated StateFlow
private val currentUserId = flow { ... }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

// Reactive update based on userId changes
private val profileLoadingState = currentUserId
    .debounce(300)  // Let userId stabilize
    .flatMapLatest { userId ->
        when {
            userId == null -> flowOf(ProfileDataState.NoAuth)
            else -> flow {
                emit(ProfileDataState.Loading)
                // Load profile for this userId
                profileRepository.getProfile(userId)
                    .collect { profile -> emit(ProfileDataState.Loaded(profile)) }
            }
        }
    }
    .stateIn(...)
```

---

## 5. Critical Integration Points for UserId Class Conversion

When introducing a type-safe `UserId` class, these are the critical points that need updates:

### 5.1 Navigation Routes

**Current**:
```kotlin
@Serializable
data class PublicProfile(
    val userId: String,
    val initialAction: String? = null
) : LiftrixRoute()
```

**After UserId Class**:
```kotlin
@Serializable
data class PublicProfile(
    val userId: UserId,  // Type-safe instead of String
    val initialAction: String? = null
) : LiftrixRoute()
```

**Serialization Requirement**: UserId must implement `@Serializable` with custom serializer for JSON round-tripping.

### 5.2 Compose Screen Signatures

**Current**:
```kotlin
@Composable
fun UserProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    // ...
)
```

**After UserId Class**:
```kotlin
@Composable
fun UserProfileScreen(
    userId: UserId,
    onNavigateBack: () -> Unit,
    // ...
)
```

### 5.3 ViewModel UiState

**Current**:
```kotlin
data class PublicProfileUiState(
    val currentUserId: String? = null,
    val profile: PublicUserProfile?,
    // ...
)
```

**After UserId Class**:
```kotlin
data class PublicProfileUiState(
    val currentUserId: UserId? = null,
    val profile: PublicUserProfile?,
    // ...
)
```

**Stability Consideration**: UiState classes use `@Stable` annotation. UserId class should also be `@Stable` or wrapped appropriately.

### 5.4 AuthQueryUseCase Return Type

**Current**:
```kotlin
// AuthQueryUseCase returns String
val result = authQueryUseCase(waitForAuth = false)
result.fold(
    onSuccess = { userId: String -> ... },
    onFailure = { ... }
)
```

**After UserId Class**:
```kotlin
// AuthQueryUseCase should return UserId
val result = authQueryUseCase(waitForAuth = false)
result.fold(
    onSuccess = { userId: UserId -> ... },
    onFailure = { ... }
)
```

### 5.5 Navigation Extension Functions

**Current**:
```kotlin
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(userId))
}
```

**After UserId Class**:
```kotlin
fun NavController.navigateToPublicProfile(userId: UserId) {
    navigate(LiftrixRoute.PublicProfile(userId))
}
```

### 5.6 Deep Link Parsing

**Current**:
```kotlin
if (path.startsWith("/profile/")) {
    val userId = path.removePrefix("/profile/").takeIf { it.isNotBlank() }
    if (userId != null && userId.length == 28) {
        navController.navigateFromDeepLink(
            LiftrixRoute.PublicProfile(userId)  // String
        )
    }
}
```

**After UserId Class**:
```kotlin
if (path.startsWith("/profile/")) {
    val userIdString = path.removePrefix("/profile/").takeIf { it.isNotBlank() }
    if (userIdString != null && userIdString.length == 28) {
        navController.navigateFromDeepLink(
            LiftrixRoute.PublicProfile(UserId(userIdString))  // Wrapped
        )
    }
}
```

---

## 6. Routes Using userId - Complete List

Routes that include userId parameters:

| Route | userId Field | Optional? | Usage |
|-------|--------------|-----------|-------|
| `PublicProfile` | `userId: String` | No | View other user's profile |
| `QRCodeDisplay` | `userId: String?` | Yes | Display current user's QR (null = current) |
| `FollowersList` | `userId: String` | No | List followers of specified user |
| `FollowingList` | `userId: String` | No | List following of specified user |
| `Profile` | `userId: String?` | Yes | Profile view (null = current user) |

---

## 7. Compose-Specific Behaviors

### 7.1 No Bundle-based Parameters

The app uses **Compose Navigation with kotlinx.serialization**, NOT the older Fragment-based navigation with Bundles.

This means:
- No `Bundle` objects for passing userId
- No `@Parcelize` annotations needed
- Direct type-safe route classes using `@Serializable`
- JSON serialization for deep links

### 7.2 LaunchedEffect for Reactive userId

Pattern for responding to userId changes:

```kotlin
LaunchedEffect(userId) {  // Re-run when userId changes
    // Load data for new userId
    viewModel.loadProfile(userId)
}
```

### 7.3 Null Checks and Safe Navigation

Defensive pattern for optional userId:

```kotlin
composable<LiftrixRoute.QRCodeDisplay> { backStackEntry ->
    val route = backStackEntry.toRoute<LiftrixRoute.QRCodeDisplay>()

    // First check: if userId is null, fetch current user
    LaunchedEffect(route.userId) {
        if (route.userId == null) {
            val currentUserId = viewModel.getCurrentUserId()
            if (currentUserId == null) {
                navController.navigateAndReplace(LiftrixRoute.AuthSignIn)
                return@launch
            }
            navController.navigateAndReplace(
                LiftrixRoute.QRCodeDisplay(currentUserId)
            )
        }
    }

    // Second check: only render if we have userId
    route.userId?.let { userId ->
        QRCodeDisplayScreen(userId = userId)
    }
}
```

---

## 8. Recommendations for UserId Class Implementation

### 8.1 Required Traits

```kotlin
@Serializable
@Stable
data class UserId(
    val value: String
) {
    // Validation: Firebase UIDs are 28 characters
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
        require(value.length == 28) { "Invalid UserId format: expected 28 chars, got ${value.length}" }
    }

    override fun toString(): String = value

    // Optional: String conversion helpers
    fun toFirebaseUid(): String = value
    companion object {
        fun fromString(value: String): Result<UserId> =
            runCatching { UserId(value) }
    }
}
```

**Key Requirements**:
- `@Serializable` for navigation routes
- `@Stable` for Compose optimization (avoid recompositions)
- Immutable (data class with val)
- Validation in constructor (fail fast)
- Firebase UID format validation (28 chars)

### 8.2 Serialization Approach

For navigation routes serialization:

```kotlin
@Serializable
data class PublicProfile(
    val userId: UserId,  // Serializable wrapper
    val initialAction: String? = null
) : LiftrixRoute()
```

The `@Serializable` on UserId class handles JSON serialization automatically via kotlinx.serialization.

### 8.3 Backward Compatibility Path

Consider gradual migration:

```kotlin
// Intermediate: Function overloads accepting String
fun NavController.navigateToPublicProfile(userId: String) {
    navigateToPublicProfile(UserId(userId))
}

fun NavController.navigateToPublicProfile(userId: UserId) {
    navigate(LiftrixRoute.PublicProfile(userId))
}
```

### 8.4 Deep Link Parsing Integration

```kotlin
fun String.toUserIdOrNull(): UserId? =
    if (this.length == 28) UserId(this) else null

// Usage in deep link handler
val userId = path.removePrefix("/profile/")
    .takeIf { it.isNotBlank() }
    ?.toUserIdOrNull()

if (userId != null) {
    navController.navigateFromDeepLink(
        LiftrixRoute.PublicProfile(userId)
    )
}
```

---

## 9. Affected ViewModel Files

ViewModels that inject or use userId:

| ViewModel | Pattern | Critical Changes |
|-----------|---------|------------------|
| `ProfileViewModel` | AuthQueryUseCase + StateFlow | Return type of currentUserId flow |
| `PublicProfileViewModel` | Navigation parameter + state | Route parameter + UiState field |
| `FollowerListViewModel` | Navigation parameter | Route parameter type |
| `UserProfileViewModel` | Navigation parameter | Route parameter type |
| `SyncSettingsViewModel` | AuthQueryUseCase | Return type of userId |
| `QRCodeDisplayViewModel` | Optional navigation param | Optional route parameter |
| `FeedViewModel` | Current user for filtering | ViewerUserId in paging source |

---

## 10. Testing Implications

### 10.1 Navigation Testing

```kotlin
// Before
composeTestRule.onNode(isDisplayed()).performClick()
// Verify navigation with String userId
navController.currentBackStackEntry?.arguments?.getString("userId")

// After
composeTestRule.onNode(isDisplayed()).performClick()
// Verify navigation with UserId type
val route = navController.currentBackStackEntry?.destination?.route
```

### 10.2 ViewModel Testing

```kotlin
// Before
val userId = "test_user_123" // String

// After
val userId = UserId("test_user_123") // Type-safe

// Mock AuthQueryUseCase return
whenever(authQueryUseCase(waitForAuth = false)).thenReturn(
    LiftrixResult.Success(userId)
)
```

### 10.3 Deep Link Testing

```kotlin
// Before
deepLinkUri = "liftrix://profile/test_user_123"

// After - Serialization handles parsing automatically
deepLinkUri = "liftrix://profile/test_user_123"
// NavController.navigate() will deserialize to UserId(value="test_user_123")
```

---

## Summary: UserId Migration Checklist

- [ ] Create `UserId` class with `@Serializable` and `@Stable`
- [ ] Update `LiftrixRoute` sealed class: use `UserId` instead of `String`
- [ ] Update `AuthQueryUseCase` return type: `String` → `UserId`
- [ ] Update Compose screen signatures: accept `UserId` parameter
- [ ] Update ViewModel UiState classes: use `UserId` for userId fields
- [ ] Update navigation extension functions: accept `UserId` parameters
- [ ] Update deep link handler: convert to `UserId` during parsing
- [ ] Update ProfileViewModel's currentUserId flow: emit `UserId`
- [ ] Add validation tests for UserId (28 char Firebase format)
- [ ] Update navigation tests to expect `UserId` type
- [ ] Update ViewModel tests to mock `UserId`
- [ ] Add serialization tests for route parameters
- [ ] Document serialization format for network/database sync

---

## Conclusion

The Liftrix codebase has a clean, modern Compose Navigation architecture that's well-suited for adopting a type-safe `UserId` class. The main integration points are:

1. **Navigation Routes** (`LiftrixRoute` sealed class)
2. **ViewModel UiState** (ProfileUiState, PublicProfileUiState, etc.)
3. **AuthQueryUseCase** return type
4. **Compose screen parameters** (direct passing)
5. **Navigation extension functions**
6. **Deep link parsing** (DeepLinkHandler)

All these points already use `String` for userId consistently, making the migration straightforward. The key requirement is ensuring `UserId` is `@Serializable` for navigation routes and `@Stable` for Compose optimization.

**No Parcelize, no @Assisted injection, no Bundles**: This is a modern Compose codebase that will benefit significantly from compile-time type safety on userId.
