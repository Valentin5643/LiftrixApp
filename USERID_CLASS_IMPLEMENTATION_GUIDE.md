# UserId Class Implementation Guide

**Purpose**: Step-by-step guide to introduce a type-safe `UserId` class into the Liftrix codebase.

**Scope**: Complete conversion from `String` to `UserId` across the UI, Navigation, and ViewModel layers.

---

## Phase 1: Create the UserId Class

### 1.1 File Location
Create new file: `app/src/main/java/com/example/liftrix/domain/model/UserId.kt`

### 1.2 Implementation

```kotlin
package com.example.liftrix.domain.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * Type-safe wrapper for Firebase User IDs.
 *
 * Enforces format validation:
 * - Must not be blank
 * - Must be 28 characters (Firebase UID standard length)
 *
 * This class prevents accidental mixing of different ID types
 * and enables compile-time type checking across the application.
 *
 * @param value The Firebase UID string (28 characters)
 *
 * @throws IllegalArgumentException if value doesn't match Firebase UID format
 */
@Serializable
@Stable
data class UserId(val value: String) {

    init {
        require(value.isNotBlank()) {
            "UserId cannot be blank"
        }
        require(value.length == 28) {
            "Invalid UserId format: expected 28 characters, got ${value.length}. " +
            "Firebase UIDs must be exactly 28 characters long. " +
            "Received: '$value'"
        }
    }

    /**
     * Returns the string value of this UserId.
     * Useful for logging and Firebase API calls.
     */
    override fun toString(): String = value

    /**
     * Returns the Firebase UID string.
     * Explicitly named for clarity in code.
     */
    fun toFirebaseUid(): String = value

    companion object {
        /**
         * Safely creates a UserId from a string.
         * Returns a Result that contains the error if validation fails.
         *
         * @param value The Firebase UID string to wrap
         * @return Result.success(UserId) if valid, Result.failure(...) if invalid
         */
        fun fromString(value: String): Result<UserId> =
            runCatching { UserId(value) }

        /**
         * Creates a UserId, throwing exception if invalid.
         * Use when you're confident the value is valid.
         *
         * @param value The Firebase UID string
         * @return UserId instance
         * @throws IllegalArgumentException if value is invalid
         */
        fun of(value: String): UserId = UserId(value)
    }
}
```

### 1.3 Add Extension Functions

Add to same file or create `UserIdExtensions.kt`:

```kotlin
/**
 * Converts a String to UserId, throwing if invalid.
 * @throws IllegalArgumentException if not a valid UserId
 */
fun String.toUserId(): UserId = UserId(this)

/**
 * Safely converts to UserId, returning null if invalid.
 */
fun String.toUserIdOrNull(): UserId? =
    UserId.fromString(this).getOrNull()

/**
 * Safely converts to UserId with default fallback.
 */
fun String.toUserIdOrDefault(default: UserId): UserId =
    UserId.fromString(this).getOrElse { default }
```

### 1.4 Tests

Create: `app/src/test/java/com/example/liftrix/domain/model/UserIdTest.kt`

```kotlin
package com.example.liftrix.domain.model

import junit.framework.TestCase
import org.junit.Test

class UserIdTest : TestCase() {

    @Test
    fun validUserIdCreation() {
        val validId = "12345678901234567890123456"  // 28 chars
        val userId = UserId(validId)
        assertEquals(validId, userId.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidUserIdTooShort() {
        UserId("12345")  // Too short
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidUserIdTooLong() {
        UserId("123456789012345678901234567890")  // 30 chars
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidUserIdBlank() {
        UserId("")
    }

    @Test
    fun toStringReturnsValue() {
        val id = "12345678901234567890123456"
        val userId = UserId(id)
        assertEquals(id, userId.toString())
    }

    @Test
    fun fromStringSuccess() {
        val id = "12345678901234567890123456"
        val result = UserId.fromString(id)
        assertTrue(result.isSuccess)
        assertEquals(id, result.getOrNull()?.value)
    }

    @Test
    fun fromStringFailure() {
        val result = UserId.fromString("invalid")
        assertTrue(result.isFailure)
    }

    @Test
    fun toUserIdExtension() {
        val id = "12345678901234567890123456"
        val userId = id.toUserId()
        assertEquals(id, userId.value)
    }

    @Test
    fun toUserIdOrNullValid() {
        val id = "12345678901234567890123456"
        assertEquals(id, id.toUserIdOrNull()?.value)
    }

    @Test
    fun toUserIdOrNullInvalid() {
        assertNull("invalid".toUserIdOrNull())
    }
}
```

---

## Phase 2: Update AuthQueryUseCase

### 2.1 Current Implementation

**File**: `domain/usecase/auth/AuthQueryUseCase.kt`

```kotlin
// BEFORE
class AuthQueryUseCase @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(waitForAuth: Boolean = true): LiftrixResult<String> {
        // Returns: LiftrixResult<String>
    }
}
```

### 2.2 Updated Implementation

```kotlin
// AFTER
class AuthQueryUseCase @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(waitForAuth: Boolean = true): LiftrixResult<UserId> {
        // ... existing logic

        // When returning success:
        val firebaseUid = getCurrentUserUid()  // String from Firebase
        return if (firebaseUid != null) {
            LiftrixResult.Success(UserId(firebaseUid))  // Wrap in UserId
        } else {
            LiftrixResult.Error(...)
        }
    }
}
```

### 2.3 Breaking Changes

Other code that uses `AuthQueryUseCase`:

```kotlin
// BEFORE
val result = authQueryUseCase(waitForAuth = false)
result.fold(
    onSuccess = { userId: String → ... },  // String
    onFailure = { error → ... }
)

// AFTER
val result = authQueryUseCase(waitForAuth = false)
result.fold(
    onSuccess = { userId: UserId → ... },  // UserId
    onFailure = { error → ... }
)
```

---

## Phase 3: Update Navigation Routes

### 3.1 File: `LiftrixRoute.kt`

Replace all `userId: String` with `userId: UserId`:

```kotlin
@Serializable
sealed class LiftrixRoute {

    // BEFORE
    @Serializable
    data class PublicProfile(
        val userId: String,
        val initialAction: String? = null
    ) : LiftrixRoute()

    // AFTER
    @Serializable
    data class PublicProfile(
        val userId: UserId,
        val initialAction: String? = null
    ) : LiftrixRoute()

    // BEFORE
    @Serializable
    data class QRCodeDisplay(val userId: String? = null) : LiftrixRoute()

    // AFTER
    @Serializable
    data class QRCodeDisplay(val userId: UserId? = null) : LiftrixRoute()

    // BEFORE
    @Serializable
    data class FollowersList(
        val userId: String,
        val listType: String = "FOLLOWERS"
    ) : LiftrixRoute()

    // AFTER
    @Serializable
    data class FollowersList(
        val userId: UserId,
        val listType: String = "FOLLOWERS"
    ) : LiftrixRoute()

    // Similar updates for:
    // - FollowingList
    // - Profile (optional userId)
}
```

---

## Phase 4: Update Navigation Extension Functions

### 4.1 File: `NavigationExtensions.kt`

Update all navigation helper functions:

```kotlin
// BEFORE
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(userId))
}

fun NavController.navigateToFollowersList(userId: String, listType: String = "FOLLOWERS") {
    navigate(LiftrixRoute.FollowersList(userId, listType))
}

// AFTER
fun NavController.navigateToPublicProfile(userId: UserId) {
    navigate(LiftrixRoute.PublicProfile(userId))
}

fun NavController.navigateToFollowersList(userId: UserId, listType: String = "FOLLOWERS") {
    navigate(LiftrixRoute.FollowersList(userId, listType))
}

// OPTIONAL: Backward compatibility overloads
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(UserId(userId)))
}

fun NavController.navigateToFollowersList(userId: String, listType: String = "FOLLOWERS") {
    navigate(LiftrixRoute.FollowersList(UserId(userId), listType))
}
```

---

## Phase 5: Update Compose Screens

### 5.1 Example: UserProfileScreen

**File**: `ui/profile/UserProfileScreen.kt`

```kotlin
// BEFORE
@Composable
fun UserProfileScreen(
    userId: String,  // String parameter
    onNavigateBack: () -> Unit,
    onNavigateToFollowersList: (userId: String) -> Unit,
    // ...
) {
    // ...
}

// AFTER
@Composable
fun UserProfileScreen(
    userId: UserId,  // UserId parameter - type-safe!
    onNavigateBack: () -> Unit,
    onNavigateToFollowersList: (userId: UserId) -> Unit,
    // ...
) {
    LaunchedEffect(userId) {
        // userId is now a UserId, not String
        viewModel.handleEvent(
            PublicProfileEvent.LoadProfile(userId)
        )
    }
    // ...
}
```

### 5.2 Affected Screens

Update these Compose screens to accept `UserId`:

1. `UserProfileScreen(userId: UserId)`
2. `FollowerListScreen(userId: UserId)`
3. `UserSearchScreen()` - callbacks return `UserId`
4. `QRCodeDisplayScreen(userId: UserId)`
5. `SocialViewModel` - navigation callbacks
6. All screens that navigate to user profiles

---

## Phase 6: Update ViewModels and UiState

### 6.1 Example: PublicProfileViewModel

**File**: `ui/social/PublicProfileViewModel.kt`

```kotlin
// BEFORE
data class PublicProfileUiState(
    val profile: PublicUserProfile?,
    val isLoading: Boolean,
    val error: LiftrixError?,
    val isConnectionLoading: Boolean,
    val currentUserId: String? = null,  // String
    val workoutPosts: Flow<PagingData<WorkoutPost>> = flowOf(PagingData.empty()),
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet()
)

// AFTER
data class PublicProfileUiState(
    val profile: PublicUserProfile?,
    val isLoading: Boolean,
    val error: LiftrixError?,
    val isConnectionLoading: Boolean,
    val currentUserId: UserId? = null,  // UserId - type-safe!
    val workoutPosts: Flow<PagingData<WorkoutPost>> = flowOf(PagingData.empty()),
    val likedPosts: Set<String> = emptySet(),
    val savedPosts: Set<String> = emptySet()
)
```

### 6.2 Example: ProfileViewModel

**File**: `ui/profile/ProfileViewModel.kt`

```kotlin
// BEFORE
private val currentUserId = flow {
    val result = authQueryUseCase(waitForAuth = false)
    result.fold(
        onSuccess = { userId: String → emit(userId) },  // String
        onFailure = { ... }
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.Eagerly,
    initialValue = null
)

// AFTER
private val currentUserId = flow {
    val result = authQueryUseCase(waitForAuth = false)
    result.fold(
        onSuccess = { userId: UserId → emit(userId) },  // UserId!
        onFailure = { ... }
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.Eagerly,
    initialValue = null
)

// UiState gets updated:
data class ProfileUiState(
    val userId: UserId? = null,  // Type-safe!
    val profileState: ProfileLoadingState = ProfileLoadingState.Loading,
    val achievements: List<UserAchievement> = emptyList(),
    val isLoading: Boolean = false,
    val error: ProfileError? = null
)
```

---

## Phase 7: Update Deep Link Handler

### 7.1 File: `DeepLinkHandler.kt`

```kotlin
// BEFORE
if (path.startsWith("/profile/")) {
    val userId = path.removePrefix("/profile/").takeIf { it.isNotBlank() }
    if (userId != null && userId.length == 28) {
        navController.navigateFromDeepLink(
            LiftrixRoute.PublicProfile(userId)  // String
        )
        return true
    }
}

// AFTER
if (path.startsWith("/profile/")) {
    val userIdString = path.removePrefix("/profile/").takeIf { it.isNotBlank() }
    if (userIdString != null && userIdString.length == 28) {
        val userId = UserId(userIdString)  // Wrap in UserId
        navController.navigateFromDeepLink(
            LiftrixRoute.PublicProfile(userId)  // UserId
        )
        return true
    }
}

// Or using extension function:
if (path.startsWith("/profile/")) {
    val userIdString = path.removePrefix("/profile/").takeIf { it.isNotBlank() }
    val userId = userIdString?.toUserIdOrNull()
    if (userId != null) {
        navController.navigateFromDeepLink(
            LiftrixRoute.PublicProfile(userId)
        )
        return true
    }
}
```

---

## Phase 8: Update Event Classes

### 8.1 Event Classes Using userId

**File**: `ui/social/PublicProfileEvent.kt`

```kotlin
// BEFORE
sealed class PublicProfileEvent : ViewModelEvent {
    data class LoadProfile(val userId: String) : PublicProfileEvent()
    // ...
}

// AFTER
sealed class PublicProfileEvent : ViewModelEvent {
    data class LoadProfile(val userId: UserId) : PublicProfileEvent()
    // ...
}

// In ViewModel:
fun handleEvent(event: PublicProfileEvent) {
    when (event) {
        is PublicProfileEvent.LoadProfile -> {
            loadProfile(event.userId)  // Now UserId
        }
    }
}
```

---

## Phase 9: Update Test Files

### 9.1 ViewModel Tests

```kotlin
// BEFORE
@Test
fun testLoadProfile() {
    val userId = "test_user_123456789012345678"  // String
    viewModel.handleEvent(PublicProfileEvent.LoadProfile(userId))
    // ...
}

// AFTER
@Test
fun testLoadProfile() {
    val userId = UserId("test_user_123456789012345678")  // UserId
    viewModel.handleEvent(PublicProfileEvent.LoadProfile(userId))
    // ...
}
```

### 9.2 Navigation Tests

```kotlin
// BEFORE
@Test
fun testNavigateToPublicProfile() {
    val userId = "test_user_123456789012345678"
    navController.navigateToPublicProfile(userId)
    // Verify...
}

// AFTER
@Test
fun testNavigateToPublicProfile() {
    val userId = UserId("test_user_123456789012345678")
    navController.navigateToPublicProfile(userId)
    // Verify...
}
```

### 9.3 Mock AuthQueryUseCase

```kotlin
// BEFORE
whenever(authQueryUseCase(waitForAuth = false)).thenReturn(
    LiftrixResult.Success("test_user_123456789012345678")
)

// AFTER
whenever(authQueryUseCase(waitForAuth = false)).thenReturn(
    LiftrixResult.Success(UserId("test_user_123456789012345678"))
)
```

---

## Phase 10: Rollout Plan

### 10.1 Implementation Order

1. **Week 1**: Create UserId class and tests
   - [ ] Create `UserId.kt` with validation
   - [ ] Create `UserIdExtensions.kt`
   - [ ] Create tests for UserId class
   - [ ] Update AuthQueryUseCase

2. **Week 2**: Update Navigation layer
   - [ ] Update `LiftrixRoute.kt`
   - [ ] Update `NavigationExtensions.kt`
   - [ ] Update `DeepLinkHandler.kt`
   - [ ] Verify route serialization

3. **Week 3**: Update Compose layer
   - [ ] Update Compose screen signatures
   - [ ] Update ViewModels
   - [ ] Update UiState classes
   - [ ] Update Event classes

4. **Week 4**: Testing and cleanup
   - [ ] Update all tests
   - [ ] Test deep links
   - [ ] Test navigation
   - [ ] Remove backward compatibility if used
   - [ ] Build and verify no compile errors

### 10.2 Commit Strategy

```bash
# Commit 1: Create UserId class
git commit -m "feat: introduce type-safe UserId class

- Create UserId data class with 28-char validation
- Add extension functions for safe conversion
- Add comprehensive unit tests
- Update AuthQueryUseCase to return UserId"

# Commit 2: Update navigation
git commit -m "refactor: migrate LiftrixRoute to use UserId

- Update all route definitions to accept UserId
- Update navigation extension functions
- Update deep link handler
- Update route serialization tests"

# Commit 3: Update UI and ViewModels
git commit -m "refactor: migrate UI layer to type-safe UserId

- Update Compose screen signatures
- Update ViewModel UiState classes
- Update event classes
- Update UI tests"

# Commit 4: Final cleanup
git commit -m "test: update all tests for UserId migration

- Update unit tests
- Update integration tests
- Update navigation tests
- Verify deep link functionality"
```

### 10.3 Breaking Changes

Users of the code must update:

1. **AuthQueryUseCase calls**
   - Return type changed from `LiftrixResult<String>` to `LiftrixResult<UserId>`

2. **Navigation calls**
   - All navigation functions now accept `UserId` instead of `String`

3. **Compose signatures**
   - Screens accepting userId now accept `UserId` type

4. **ViewModel constructors**
   - No constructor changes (UserId not injected), but return types change

5. **Test mocks**
   - AuthQueryUseCase mocks must return `UserId`

---

## Backward Compatibility Options

### Option 1: Gradual Migration with Overloads

Keep backward compatibility during transition:

```kotlin
// Old API - will be deprecated
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(UserId(userId)))
}

// New API
fun NavController.navigateToPublicProfile(userId: UserId) {
    navigate(LiftrixRoute.PublicProfile(userId))
}
```

Mark old version with `@Deprecated`:

```kotlin
@Deprecated(
    message = "Use navigateToPublicProfile(UserId) instead",
    replaceWith = ReplaceWith("navigateToPublicProfile(UserId(userId))"),
    level = DeprecationLevel.WARNING
)
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(UserId(userId)))
}
```

### Option 2: Clean Break (Recommended)

Update all call sites at once. Since this is a codebase-internal refactoring:
- All call sites are in this repo
- Can be updated in single PR
- No external API breakage
- Cleaner long-term

---

## Verification Checklist

After implementation, verify:

- [ ] `gradlew compileDebugKotlin` passes with no errors
- [ ] All navigation tests pass
- [ ] All ViewModel tests pass
- [ ] Deep link parsing works (test with actual URI)
- [ ] QR code links resolve to correct profiles
- [ ] Profile navigation between users works
- [ ] Follower/Following list navigation works
- [ ] No accidental `String` userId in route parameters
- [ ] UserId class validates format correctly
- [ ] Serialization round-trips work (JSON serialization)
- [ ] No type confusion between different ID types

---

## Success Criteria

The migration is complete when:

1. **Compilation**: No Kotlin compilation errors or warnings related to userId
2. **Type Safety**: All userId parameters are `UserId` type, not `String`
3. **Serialization**: Routes serialize/deserialize correctly with `UserId`
4. **Deep Links**: Deep links parse and create `UserId` correctly
5. **Tests**: All tests pass with `UserId` type
6. **Runtime**: App runs without crashes, navigation works
7. **Performance**: No performance regression (UserId is lightweight wrapper)

---

## Summary

This implementation introduces compile-time type safety for Firebase User IDs throughout the UI layer, preventing accidental string misuse while maintaining backward-compatible JSON serialization. The migration is straightforward because userId is consistently used as a String parameter across all layers.

**Total Impact**: ~50-100 files updated across navigation, ViewModels, Compose screens, and tests.
**Estimated Effort**: 3-4 weeks for comprehensive rollout and testing.
**Risk Level**: Low (all changes are in UI/navigation layer, no database/network changes).
