# userId Flow Diagrams and Architecture

## 1. Current userId (String) Flow Through UI Layer

### 1.1 Current User Profile Load Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ ProfileScreen (Compose)                                          │
│   - No userId parameter (viewing current user)                  │
└────────────────────┬────────────────────────────────────────────┘
                     │ hiltViewModel()
                     │
┌────────────────────▼────────────────────────────────────────────┐
│ ProfileViewModel                                                 │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ AuthQueryUseCase(waitForAuth = false)                    │  │
│  │   ↓                                                       │  │
│  │ LiftrixResult<String>                                    │  │
│  │   ↓                                                       │  │
│  │ fold { userId: String -> emit(userId) }                 │  │
│  │   ↓ (with retries on cold start)                        │  │
│  │ StateFlow<String?> currentUserId                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ currentUserId                                             │  │
│  │   ↓ flatMapLatest                                         │  │
│  │ profileRepository.getProfile(userId)                      │  │
│  │   ↓                                                       │  │
│  │ Flow<UserProfile?>                                        │  │
│  │   ↓                                                       │  │
│  │ StateFlow<ProfileDataState>                              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ProfileUiState {                                                │
│    userId: String? = null                                       │
│    profileState: ProfileLoadingState                            │
│    achievements: List<UserAchievement>                          │
│    isLoading: Boolean                                           │
│  }                                                               │
└────────────────────┬────────────────────────────────────────────┘
                     │ StateFlow<ProfileUiState>
                     │
┌────────────────────▼────────────────────────────────────────────┐
│ ProfileScreen Recomposition                                      │
│   uiState.userId ──→ Timber.d("userId: $userId")               │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Public Profile Load Flow (Viewing Other User)

```
┌──────────────────────────────────────────────────────────────────┐
│ NavController.navigate(LiftrixRoute.PublicProfile(userId))       │
│   userId: String                                                 │
└───────────────────────┬────────────────────────────────────────┘
                        │ Serialized to JSON
                        │
┌───────────────────────▼────────────────────────────────────────┐
│ UnifiedNavigationContainer                                       │
│                                                                 │
│  composable<LiftrixRoute.PublicProfile> { backStackEntry ->     │
│    val route = backStackEntry.toRoute<LiftrixRoute.PublicProfile>()
│    val userId = route.userId  // String extracted                │
│    ↓                                                            │
│    UserProfileScreen(                                           │
│      userId = userId,  // Pass to Compose                       │
│      ...                                                        │
│    )                                                            │
│  }                                                              │
└───────────────────────┬────────────────────────────────────────┘
                        │
┌───────────────────────▼────────────────────────────────────────┐
│ UserProfileScreen(userId: String)                               │
│   └─ Gets ViewModel: PublicProfileViewModel                     │
└───────────────────────┬────────────────────────────────────────┘
                        │ LaunchedEffect(userId) {
                        │   viewModel.handleEvent(
                        │     PublicProfileEvent.LoadProfile(userId)
                        │   )
                        │ }
                        │
┌───────────────────────▼────────────────────────────────────────┐
│ PublicProfileViewModel                                          │
│                                                                 │
│  fun loadProfile(userId: String) {                              │
│    socialProfileQueryUseCase.getPublicProfile(                  │
│      GetPublicProfileRequest(profileUserId = userId, ...)       │
│    ).fold { result →                                            │
│      PublicProfileUiState {                                     │
│        profile: PublicUserProfile                               │
│        currentUserId: String? = authQueryUseCase()              │
│      }                                                          │
│    }                                                            │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 Follower List Flow

```
┌──────────────────────────────────────────────────────────────┐
│ onNavigateToFollowersList(userId: String)                    │
│   Called from UserProfileScreen                              │
└───────────────┬────────────────────────────────────────────┘
                │
┌───────────────▼────────────────────────────────────────────┐
│ navController.navigate(                                     │
│   LiftrixRoute.FollowersList(                               │
│     userId = userId,      // String parameter               │
│     listType = "FOLLOWERS"                                  │
│   )                                                         │
│ )                                                           │
└───────────────┬────────────────────────────────────────────┘
                │
┌───────────────▼────────────────────────────────────────────┐
│ composable<LiftrixRoute.FollowersList> { backStackEntry →   │
│   val route = backStackEntry.toRoute<...>()                │
│   val userId = route.userId  // String                      │
│   val listType = route.listType                             │
│   ↓                                                         │
│   FollowerListScreen(                                       │
│     userId = userId,                                        │
│     listType = listType                                     │
│   )                                                         │
│ }                                                           │
└───────────────┬────────────────────────────────────────────┘
                │
┌───────────────▼────────────────────────────────────────────┐
│ FollowerListScreen(userId: String, listType: ...)          │
│   └─ FollowerListViewModel.load(userId, listType)          │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. UserId Class Integration Points

### 2.1 After UserId Class Introduction

```
┌──────────────────────────────────────────────────────────────┐
│ UserId Class (NEW)                                            │
│                                                              │
│ @Serializable                                                │
│ @Stable                                                      │
│ data class UserId(val value: String) {                       │
│   init {                                                     │
│     require(value.length == 28)  // Firebase UID format     │
│   }                                                          │
│ }                                                            │
└────────────────────┬──────────────────────────────────────┘
                     │
                     ├── Router Routes Updated
                     ├──AuthQueryUseCase Updated
                     ├──ViewModel Parameters Updated
                     └──Compose Screen Signatures Updated
```

### 2.2 Updated ProfileViewModel Flow

```
┌─────────────────────────────────────────────────────────────┐
│ ProfileViewModel (UPDATED)                                   │
│                                                              │
│  AuthQueryUseCase(waitForAuth = false)                       │
│    ↓ LiftrixResult<UserId>  ← Changed from String           │
│  .fold { userId: UserId → ... }                              │
│    ↓                                                        │
│  StateFlow<UserId?> currentUserId  ← Now type-safe!        │
│    ↓ flatMapLatest                                          │
│  profileRepository.getProfile(userId)                        │
│    ↓                                                        │
│  ProfileUiState {                                            │
│    userId: UserId? = null  ← Type-safe!                    │
│    profileState: ProfileLoadingState                        │
│    achievements: List<UserAchievement>                      │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 Updated Route Serialization

```
Before (String-based):
┌────────────────────────────────────────────────┐
│ @Serializable                                  │
│ data class PublicProfile(                      │
│   val userId: String,  ← String ID            │
│   val initialAction: String? = null            │
│ )                                              │
│                                                │
│ Navigation: PublicProfile("user123abc...")    │
│ JSON: {"userId": "user123abc..."}             │
└────────────────────────────────────────────────┘

After (UserId-based):
┌────────────────────────────────────────────────┐
│ @Serializable                                  │
│ data class PublicProfile(                      │
│   val userId: UserId,  ← Type-safe UserId    │
│   val initialAction: String? = null            │
│ )                                              │
│                                                │
│ Navigation: PublicProfile(UserId("user..."))  │
│ JSON: {"userId": "user123abc..."}  ← Same!   │
└────────────────────────────────────────────────┘
```

---

## 3. Deep Link Parsing

### 3.1 Current Flow (String-based)

```
Deep Link: liftrix://profile/XyZ123abc456...

┌─────────────────────────────────────────────┐
│ DeepLinkHandler.parseDeepLink(uri)          │
│                                              │
│ path = "/profile/XyZ123abc456..."           │
│   ↓                                          │
│ userId = path.removePrefix("/profile/")     │
│   ↓                                          │
│ if (userId.length == 28) {  // Validate    │
│   navController.navigateFromDeepLink(       │
│     LiftrixRoute.PublicProfile(             │
│       userId  // String passed directly      │
│     )                                        │
│   )                                          │
│ }                                            │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│ NavHost routes userId: String to             │
│ UserProfileScreen(userId = "XyZ123...")      │
└──────────────────────────────────────────────┘
```

### 3.2 Updated Flow (UserId-based)

```
Deep Link: liftrix://profile/XyZ123abc456...

┌─────────────────────────────────────────────┐
│ DeepLinkHandler.parseDeepLink(uri)  (UPDATED)│
│                                              │
│ path = "/profile/XyZ123abc456..."           │
│   ↓                                          │
│ userIdString = path.removePrefix("/profile/")│
│   ↓                                          │
│ if (userIdString.length == 28) {            │
│   val userId = UserId(userIdString)  ← NEW │
│   navController.navigateFromDeepLink(       │
│     LiftrixRoute.PublicProfile(             │
│       userId  // UserId wrapped              │
│     )                                        │
│   )                                          │
│ }                                            │
└──────────────────────┬──────────────────────┘
                       │
┌──────────────────────▼──────────────────────┐
│ NavHost deserializes JSON to UserId         │
│ UserProfileScreen(userId = UserId(...))     │
│   ← Type-safe routing!                      │
└──────────────────────────────────────────────┘
```

---

## 4. ViewModel Dependency Injection Patterns

### 4.1 Pattern A: Current User (AuthQueryUseCase)

```
┌────────────────────────────────────┐
│ @HiltViewModel                     │
│ class ProfileViewModel @Inject(     │
│   authQueryUseCase: AuthQueryUseCase│
│ ) : ModernBaseViewModel<...>       │
│                                    │
│ private val currentUserId = flow { │
│   authQueryUseCase()               │
│     .fold(                         │
│       onSuccess = { userId →       │
│         emit(userId)  // String   │
│       }, ...                       │
│     )                              │
│ }.stateIn(...)                     │
│                                    │
│ // After UserId introduction:      │
│ // AuthQueryUseCase returns UserId │
│ // currentUserId: StateFlow<UserId?>│
└────────────────────────────────────┘
```

### 4.2 Pattern B: Navigation Parameter (No Injection)

```
┌────────────────────────────────────┐
│ @HiltViewModel                     │
│ class PublicProfileViewModel @Inject(
│   socialProfileQueryUseCase: ...,  │
│   authQueryUseCase: AuthQueryUseCase│
│ ) : ModernBaseViewModel<...>       │
│                                    │
│ fun handleEvent(                   │
│   event: PublicProfileEvent        │
│ ) {                                │
│   when (event) {                   │
│     is PublicProfileEvent.LoadProfile → {
│       loadProfile(event.userId)    │
│       // userId: String parameter  │
│     }                              │
│   }                                │
│ }                                  │
│                                    │
│ // After UserId introduction:      │
│ // Event carries UserId not String │
│ // loadProfile(userId: UserId)     │
└────────────────────────────────────┘
```

**Key**: NO @Assisted injection for userId - it comes from navigation routes or use cases.

---

## 5. State Management Hierarchy

### 5.1 Complete State Flow

```
┌──────────────────────────────────────────────────┐
│ Compose Screen                                    │
│   @Composable fun ProfileScreen(...)             │
└────────────────┬─────────────────────────────────┘
                 │ hiltViewModel()
                 │
┌────────────────▼─────────────────────────────────┐
│ ViewModel (ModernBaseViewModel<S>)                │
│                                                   │
│  private val _uiState = MutableStateFlow(...)    │
│  val uiState: StateFlow<S> = _uiState.asStateFlow│
│                                                   │
│  protected fun updateState(transform: (S) → S)   │
└────────────────┬─────────────────────────────────┘
                 │
┌────────────────▼─────────────────────────────────┐
│ UiState<T> (for complex screens)                 │
│                                                   │
│  sealed class UiState<T> {                       │
│    object Loading : UiState<Nothing>()           │
│    data class Success<T>(...) : UiState<T>()     │
│    data class Error<T>(...) : UiState<T>()       │
│    data class Empty(...) : UiState<Nothing>()    │
│  }                                               │
│                                                   │
│  ↓ Map to domain data                            │
│  Custom UiState: data class ProfileUiState(...) │
│    - userId: String?                             │
│    - profileState: ProfileLoadingState           │
│    - achievements: List<...>                     │
└──────────────────────────────────────────────────┘
```

### 5.2 Custom UiState with userId

```
Screens typically define custom UiState subclass:

┌──────────────────────────────────────────────┐
│ ProfileUiState : ModernBaseViewModel output  │
│                                              │
│ @Stable data class ProfileUiState(           │
│   val userId: String? = null,    ← stored  │
│   val profileState: ProfileLoadingState,     │
│   val achievements: List<...>,               │
│   val isLoading: Boolean = false,            │
│   val error: ProfileError? = null            │
│ )                                            │
│                                              │
│ // After UserId class:                       │
│ @Stable data class ProfileUiState(           │
│   val userId: UserId? = null,    ← typed   │
│   val profileState: ProfileLoadingState,     │
│   val achievements: List<...>,               │
│   val isLoading: Boolean = false,            │
│   val error: ProfileError? = null            │
│ )                                            │
└──────────────────────────────────────────────┘
```

---

## 6. Navigation Extension Function Patterns

### 6.1 Before UserId Class

```kotlin
// NavigationExtensions.kt

fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(userId))
}

fun NavController.navigateToFollowersList(
    userId: String,
    listType: String = "FOLLOWERS"
) {
    navigate(LiftrixRoute.FollowersList(userId, listType))
}

// Usage in code:
onProfileClick = { userId: String →
    navController.navigateToPublicProfile(userId)
}
```

### 6.2 After UserId Class

```kotlin
// NavigationExtensions.kt (UPDATED)

fun NavController.navigateToPublicProfile(userId: UserId) {
    navigate(LiftrixRoute.PublicProfile(userId))
}

fun NavController.navigateToFollowersList(
    userId: UserId,
    listType: String = "FOLLOWERS"
) {
    navigate(LiftrixRoute.FollowersList(userId, listType))
}

// Backward compatibility (optional):
fun NavController.navigateToPublicProfile(userId: String) {
    navigate(LiftrixRoute.PublicProfile(UserId(userId)))
}

// Usage in code (NOW TYPE-SAFE):
onProfileClick = { userId: UserId →
    navController.navigateToPublicProfile(userId)
}
```

---

## 7. Critical Data Flow Points for UserId Conversion

```
┌─────────────────────────────────────────────────────────────┐
│ 1. AuthQueryUseCase (Primary Source of Current UserId)      │
│    ├─ Return: LiftrixResult<String>  →  LiftrixResult<UserId>
│    └─ Impact: All current-user-dependent ViewModels         │
├─────────────────────────────────────────────────────────────┤
│ 2. Navigation Routes (LiftrixRoute sealed class)            │
│    ├─ PublicProfile.userId: String  →  UserId              │
│    ├─ QRCodeDisplay.userId: String?  →  UserId?            │
│    ├─ FollowersList.userId: String  →  UserId              │
│    └─ FollowingList.userId: String  →  UserId              │
├─────────────────────────────────────────────────────────────┤
│ 3. Compose Screen Parameters                                │
│    ├─ UserProfileScreen(userId: String)  →  UserProfileScreen(userId: UserId)
│    ├─ FollowerListScreen(userId: String)  →  FollowerListScreen(userId: UserId)
│    └─ Impact: ~15 social/profile screens                    │
├─────────────────────────────────────────────────────────────┤
│ 4. ViewModel UiState Classes                                │
│    ├─ ProfileUiState.userId: String?  →  UserId?           │
│    ├─ PublicProfileUiState.currentUserId: String?  →  UserId?
│    └─ Impact: UI state reactive updates                     │
├─────────────────────────────────────────────────────────────┤
│ 5. Deep Link Handler                                        │
│    ├─ Parse: "liftrix://profile/..."  →  UserId(...)       │
│    └─ Impact: Deep link navigation                          │
├─────────────────────────────────────────────────────────────┤
│ 6. Navigation Extension Functions                           │
│    ├─ navigateToPublicProfile(userId: String)  →  UserId   │
│    └─ Impact: ~20 navigation helper functions               │
└─────────────────────────────────────────────────────────────┘
```

---

## 8. Serialization Format Compatibility

### 8.1 JSON Format Remains Same

```
Route: LiftrixRoute.PublicProfile(userId)

Before (String-based):
{
  "userId": "XyZ123abc456...",  // 28 character Firebase UID
  "initialAction": null
}

After (UserId-based):
{
  "userId": "XyZ123abc456...",  // Same! Serialization handles unwrapping
  "initialAction": null
}

Why this works:
- UserId(value: String) is a simple wrapper
- @Serializable handles the wrapping/unwrapping
- JSON representation identical
- Deep links parse same URL format
```

### 8.2 Serialization Implementation

```kotlin
@Serializable
@Stable
data class UserId(val value: String) {
    // kotlinx.serialization automatically:
    // 1. Serializes to: "value_content"
    // 2. Deserializes from: "value_content"
    // 3. Works with NavController route serialization

    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
        require(value.length == 28) { "Invalid UserId format" }
    }
}

// Works in routes without custom serializers:
@Serializable
data class PublicProfile(
    val userId: UserId,  // Auto-serializable!
    val initialAction: String? = null
) : LiftrixRoute()
```

---

## Conclusion: Clean Migration Path

The current architecture's consistent use of `String` for userId makes migration straightforward:

1. **Type Safety**: Compile-time validation at route level
2. **No Serialization Changes**: JSON format identical
3. **No Breaking Changes**: Deep links work same way
4. **Consistent Patterns**: All ViewModels affected uniformly
5. **Future-Proof**: Enables stronger validation (UID format, length, etc.)

The introduction of `UserId` class will prevent:
- Accidental mixing of different ID types
- Invalid UID formats being passed to APIs
- Type confusion between different string identifiers
- Silent userId mismatches in multi-user scenarios
