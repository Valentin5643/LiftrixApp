# SPEC-20250629-social-features-integration

## Executive Summary
**Feature**: Social Features Integration - Friend System and Workout Sharing
**Impact**: Increase user retention by 30% through social accountability and motivation. Drive feature engagement through friend connections and shared achievements.
**Effort**: 6-8 developer days for basic social functionality
**Risk**: Medium - New social features require careful privacy and real-time update considerations
**Dependencies**: SPEC-20250629-home-screen-dashboard.md (social feed displays on home screen)

## Product Specifications

### Elevator Pitch
Build meaningful fitness connections by finding friends, sharing workout achievements, and staying motivated through a supportive community that respects your privacy while celebrating your progress.

### Target Users
- **Primary**: Social fitness enthusiasts who stay motivated through community accountability (3+ workouts/week)
- **Secondary**: Casual users seeking motivation through friend connections and shared achievements (1-2 workouts/week)

### Core Goals
1. **Performance**: Friend status updates within 30 seconds, friend discovery loads <1s
2. **Usability**: Add friend in 2 taps, share workout in 3 taps from any workout view
3. **Scale**: Support 50+ friends per user with real-time presence updates

### Functional Requirements

**FR-001: Friend Management System**
- **Given**: User wants to connect with others
- **When**: User searches for friends by username/email or browses suggestions
- **Then**: Display search results with send friend request option
- **Acceptance**: Friend requests sent successfully with proper notification

**FR-002: Privacy-First Presence System**
- **Given**: User wants to control visibility
- **When**: User accesses privacy settings
- **Then**: Allow opt-in/opt-out for online status visibility with granular controls
- **Acceptance**: Status visibility respects user preferences across all features

**FR-003: Friend Status Display**
- **Given**: User has friends with various activity states
- **When**: User views friends list or home screen social section
- **Then**: Show friend status (online, working out, idle) with simple visual indicators
- **Acceptance**: Status updates within 30 seconds of friend activity changes

**FR-004: Workout Sharing**
- **Given**: User completes a workout
- **When**: User chooses to share workout
- **Then**: Allow sharing to selected friends or general friend feed with privacy controls
- **Acceptance**: Shared workouts appear in friends' social feeds with accurate data

**FR-005: Friend Workout Feed**
- **Given**: User has active friends sharing workouts
- **When**: User views home screen social section
- **Then**: Display recent friend workout activities in chronological order
- **Acceptance**: Feed updates show friend name, workout summary, and timestamp

### User Stories

**US-001**: As a fitness enthusiast, I want to find and add friends so that we can motivate each other through shared workout progress.
- **Acceptance Criteria**:
  1. Search friends by username, email, or phone number
  2. Send friend requests with optional personal message
  3. Accept/decline incoming friend requests
  4. View friend profiles with shared workout stats

**US-002**: As a privacy-conscious user, I want to control who sees my online status and workout activity so that I maintain appropriate boundaries.
- **Acceptance Criteria**:
  1. Toggle online status visibility (all friends, close friends, none)
  2. Control workout sharing default (auto-share, ask each time, private)
  3. Block/unblock specific users
  4. Report inappropriate behavior

**US-003**: As a social fitness user, I want to see what my friends are doing so that I stay motivated and engaged with my fitness routine.
- **Acceptance Criteria**:
  1. Friend feed shows recent workout completions
  2. Visual indicators for friends currently working out
  3. Quick congratulations or encouragement interactions
  4. Filter feed by activity type or friend groups

### Non-Goals
- **Public Social Network**: No public feeds or discovery - **Reason**: V1 focuses on close friend connections only
- **Comments/Likes System**: No detailed social interactions - **Reason**: Deferred to V2 to maintain simplicity
- **Competition Features**: No leaderboards or challenges - **Reason**: V1 focuses on support, not competition
- **External Social Integration**: No Facebook/Instagram sharing - **Reason**: V1 focuses on in-app community

## Technical Specifications

### System Architecture
- **Pattern**: Clean Architecture with Repository pattern, following existing user-scoped design
- **Flow**: SocialViewModel → SocialRepository → FriendDao + Firebase Presence → Room Database + Firestore
- **Security**: User-scoped operations with privacy controls, encrypted friend connections

### Database Design

**New Entities Required:**
```kotlin
@Entity(
    tableName = "friends",
    primaryKeys = ["user_id", "friend_user_id"],
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["user_id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["friend_user_id"]),
        Index(value = ["status"]),
        Index(value = ["created_at"])
    ]
)
data class FriendEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "friend_user_id") val friendUserId: String,
    @ColumnInfo(name = "status") val status: FriendStatus, // PENDING, ACCEPTED, BLOCKED
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false
)

@Entity(
    tableName = "user_privacy_settings",
    foreignKeys = [
        ForeignKey(entity = UserProfileEntity::class, parentColumns = ["user_id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class UserPrivacySettingsEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "online_status_visibility") val onlineStatusVisibility: PrivacyLevel, // ALL_FRIENDS, CLOSE_FRIENDS, NONE
    @ColumnInfo(name = "workout_sharing_default") val workoutSharingDefault: SharingDefault, // AUTO_SHARE, ASK_EACH_TIME, PRIVATE
    @ColumnInfo(name = "allow_friend_requests") val allowFriendRequests: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)

enum class FriendStatus { PENDING, ACCEPTED, BLOCKED }
enum class PrivacyLevel { ALL_FRIENDS, CLOSE_FRIENDS, NONE }
enum class SharingDefault { AUTO_SHARE, ASK_EACH_TIME, PRIVATE }
```

**Migration Strategy:**
```kotlin
// Migration 16 to 17: Add social features
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create friends table
        database.execSQL("""
            CREATE TABLE friends (
                user_id TEXT NOT NULL,
                friend_user_id TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_synced INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (user_id, friend_user_id),
                FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
            )
        """)
        
        // Create privacy settings table
        database.execSQL("""
            CREATE TABLE user_privacy_settings (
                user_id TEXT PRIMARY KEY NOT NULL,
                online_status_visibility TEXT NOT NULL DEFAULT 'ALL_FRIENDS',
                workout_sharing_default TEXT NOT NULL DEFAULT 'ASK_EACH_TIME',
                allow_friend_requests INTEGER NOT NULL DEFAULT 1,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES user_profiles(user_id) ON DELETE CASCADE
            )
        """)
        
        // Create indexes for performance
        database.execSQL("CREATE INDEX index_friends_user_id ON friends(user_id)")
        database.execSQL("CREATE INDEX index_friends_friend_user_id ON friends(friend_user_id)")
        database.execSQL("CREATE INDEX index_friends_status ON friends(status)")
    }
}
```

### API Specifications

**Social Repository Interface:**
```kotlin
interface SocialRepository {
    // Friend Management
    fun searchUsers(query: String): Flow<List<User>>
    suspend fun sendFriendRequest(friendUserId: String): Result<Unit>
    suspend fun respondToFriendRequest(friendUserId: String, accept: Boolean): Result<Unit>
    fun getFriends(userId: String): Flow<List<Friend>>
    fun getPendingFriendRequests(userId: String): Flow<List<FriendRequest>>
    
    // Privacy & Settings
    suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit>
    fun getPrivacySettings(userId: String): Flow<PrivacySettings>
    
    // Workout Sharing
    suspend fun shareWorkout(workoutId: String, friendIds: List<String>): Result<Unit>
    fun getFriendWorkoutFeed(userId: String): Flow<List<SharedWorkout>>
    
    // Presence System
    fun getFriendPresence(friendIds: List<String>): Flow<Map<String, UserPresence>>
    suspend fun updateUserPresence(status: PresenceStatus): Result<Unit>
}

data class Friend(
    val userId: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val status: FriendStatus,
    val presence: UserPresence?,
    val friendSince: Instant
)

data class UserPresence(
    val status: PresenceStatus, // ONLINE, WORKING_OUT, IDLE, OFFLINE
    val lastActive: Instant,
    val currentWorkoutId: String? = null
)

data class SharedWorkout(
    val id: String,
    val friendUserId: String,
    val friendDisplayName: String,
    val workoutName: String,
    val completedAt: Instant,
    val duration: Duration,
    val exerciseCount: Int,
    val sharedAt: Instant
)
```

### Component Design

**Social Features UI Architecture:**
```kotlin
// Add to HomeScreen social section
@Composable
fun SocialFeedSection(
    socialUiState: SocialUiState,
    onEvent: (SocialEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionHeader(
            title = "Friends Activity",
            action = { onEvent(SocialEvent.ViewAllFriends) }
        )
        
        when {
            socialUiState.isLoading -> LoadingState()
            socialUiState.friendWorkouts.isEmpty() -> EmptyFriendsState(onAddFriends = { onEvent(SocialEvent.FindFriends) })
            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(socialUiState.friendWorkouts) { sharedWorkout ->
                        FriendWorkoutCard(
                            sharedWorkout = sharedWorkout,
                            onViewWorkout = { onEvent(SocialEvent.ViewFriendWorkout(it.id)) },
                            onCongratulate = { onEvent(SocialEvent.CongratulateFriend(it.friendUserId)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendWorkoutCard(
    sharedWorkout: SharedWorkout,
    onViewWorkout: (SharedWorkout) -> Unit,
    onCongratulate: (SharedWorkout) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onViewWorkout(sharedWorkout) },
        modifier = modifier.width(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Friend info with avatar and name
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = sharedWorkout.friendDisplayName, size = 24.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sharedWorkout.friendDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Workout summary
            Text(
                text = sharedWorkout.workoutName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${sharedWorkout.duration.toMinutes()}min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${sharedWorkout.exerciseCount} exercises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Quick action button
            OutlinedIconButton(
                onClick = { onCongratulate(sharedWorkout) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Congratulate",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
```

**Social State Management:**
```kotlin
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val analyticsService: AnalyticsService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    fun loadFriendFeed() {
        viewModelScope.launch {
            socialRepository.getFriendWorkoutFeed(getCurrentUserId())
                .catch { error -> _uiState.update { it.copy(error = error.message) } }
                .collect { workouts ->
                    _uiState.update { 
                        it.copy(
                            friendWorkouts = workouts.take(5), // Show 5 most recent
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onEvent(event: SocialEvent) {
        when (event) {
            is SocialEvent.CongratulateFriend -> {
                analyticsService.logEvent("friend_congratulated", mapOf("friend_user_id" to event.friendUserId))
                // Show congratulations sent message
            }
            is SocialEvent.ViewFriendWorkout -> {
                analyticsService.logEvent("friend_workout_viewed", mapOf("workout_id" to event.workoutId))
            }
            // Handle other events...
        }
    }
}

data class SocialUiState(
    val friendWorkouts: List<SharedWorkout> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val pendingRequests: List<FriendRequest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class SocialEvent {
    object FindFriends : SocialEvent()
    object ViewAllFriends : SocialEvent()
    data class ViewFriendWorkout(val workoutId: String) : SocialEvent()
    data class CongratulateFriend(val friendUserId: String) : SocialEvent()
    data class SendFriendRequest(val userId: String) : SocialEvent()
    data class RespondToFriendRequest(val userId: String, val accept: Boolean) : SocialEvent()
}
```

### Real-time Presence System

**Firebase Integration:**
```kotlin
@Singleton
class FirebasePresenceService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    companion object {
        private const val PRESENCE_COLLECTION = "user_presence"
        private const val PRESENCE_TIMEOUT = 5 * 60 * 1000L // 5 minutes
    }
    
    fun startPresenceTracking() {
        val userId = auth.currentUser?.uid ?: return
        val presenceRef = firestore.collection(PRESENCE_COLLECTION).document(userId)
        
        // Set user as online
        presenceRef.set(mapOf(
            "status" to "online",
            "last_active" to FieldValue.serverTimestamp(),
            "user_id" to userId
        ))
        
        // Set up offline detection
        presenceRef.onDisconnect().update(mapOf(
            "status" to "offline",
            "last_active" to FieldValue.serverTimestamp()
        ))
    }
    
    fun updateWorkoutStatus(workoutId: String?) {
        val userId = auth.currentUser?.uid ?: return
        val status = if (workoutId != null) "working_out" else "online"
        
        firestore.collection(PRESENCE_COLLECTION).document(userId)
            .update(mapOf(
                "status" to status,
                "current_workout_id" to workoutId,
                "last_active" to FieldValue.serverTimestamp()
            ))
    }
    
    fun observeFriendsPresence(friendIds: List<String>): Flow<Map<String, UserPresence>> {
        return firestore.collection(PRESENCE_COLLECTION)
            .whereIn("user_id", friendIds)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toUserPresence()?.let { presence ->
                        doc.id to presence
                    }
                }.toMap()
            }
            .flowOn(Dispatchers.IO)
    }
}
```

### Testing Strategy
**Test Scenarios:**
1. **Friend Management**: Send/accept/decline friend requests work correctly
2. **Privacy Controls**: Status visibility respects user preferences  
3. **Workout Sharing**: Shared workouts appear in correct friend feeds
4. **Presence Updates**: Online status updates within acceptable timeframe
5. **Feed Display**: Friend workout feed shows correct chronological order
6. **Error Handling**: Network failures gracefully handled with retry options
7. **Performance**: Large friend lists don't impact app performance

## Implementation Plan

### Task Breakdown

#### Database Layer (DB-XXX)
- [ ] **DB-001**: Create Migration_16_to_17 with social tables [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/migration/Migration_16_to_17.kt`
  - **Details**: Add FriendEntity, UserPrivacySettingsEntity tables with proper indexes and foreign keys

- [ ] **DB-002**: Create FriendDao and PrivacySettingsDao [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/data/local/dao/FriendDao.kt`, `app/src/main/java/com/example/liftrix/data/local/dao/PrivacySettingsDao.kt`
  - **Details**: Implement CRUD operations with user-scoped queries following existing patterns

#### Backend Services (BE-XXX)
- [ ] **BE-001**: Implement SocialRepository interface and implementation [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/repository/SocialRepository.kt`, `app/src/main/java/com/example/liftrix/data/repository/SocialRepositoryImpl.kt`
  - **Details**: Friend management, privacy settings, workout sharing functionality

- [ ] **BE-002**: Create Firebase Presence Service [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/service/FirebasePresenceService.kt`
  - **Details**: Real-time presence tracking with offline detection and status updates

- [ ] **BE-003**: Create social domain models [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/domain/model/Friend.kt`, `app/src/main/java/com/example/liftrix/domain/model/UserPresence.kt`, etc.
  - **Details**: Define social data models with proper validation

#### Frontend Components (FE-XXX)
- [ ] **FE-001**: Create SocialViewModel with state management [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/social/SocialViewModel.kt`
  - **Details**: Handle social events, friend management, and real-time presence updates

- [ ] **FE-002**: Build SocialFeedSection for HomeScreen [Estimate: 3hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/home/components/SocialFeedSection.kt`
  - **Details**: Display friend workout feed with horizontal scrolling cards

- [ ] **FE-003**: Create FriendWorkoutCard component [Estimate: 2hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/social/components/FriendWorkoutCard.kt`
  - **Details**: Individual friend workout display with quick action buttons

- [ ] **FE-004**: Build FriendsScreen for friend management [Estimate: 4hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/social/FriendsScreen.kt`
  - **Details**: Full friends list, search, requests, and privacy settings

#### Integration (INT-XXX)
- [ ] **INT-001**: Integrate presence service with workout flow [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/workout/active/ActiveWorkoutViewModel.kt`
  - **Details**: Update presence status when starting/ending workouts

- [ ] **INT-002**: Add social events to analytics [Estimate: 30min]
  - **Files**: `app/src/main/java/com/example/liftrix/service/AnalyticsService.kt`
  - **Details**: Track friend interactions, sharing events, and social engagement

- [ ] **INT-003**: Add social navigation to main app [Estimate: 1hr]
  - **Files**: `app/src/main/java/com/example/liftrix/ui/navigation/MainNavigationContainer.kt`
  - **Details**: Add friends screen to navigation, handle deep links for friend requests

#### Testing (TEST-XXX)
- [ ] **TEST-001**: SocialRepository unit and integration tests [Estimate: 3hr]
  - **Files**: `app/src/test/java/com/example/liftrix/data/repository/SocialRepositoryImplTest.kt`
  - **Details**: Test friend management, privacy controls, and workout sharing

- [ ] **TEST-002**: SocialViewModel unit tests [Estimate: 2hr]
  - **Files**: `app/src/test/java/com/example/liftrix/ui/social/SocialViewModelTest.kt`
  - **Details**: Test state management, event handling, and presence updates

- [ ] **TEST-003**: Social UI component tests [Estimate: 2hr]
  - **Files**: `app/src/test/java/com/example/liftrix/ui/social/FriendsScreenTest.kt`
  - **Details**: Test social feed rendering, friend interactions, and navigation

- [ ] **TEST-004**: Firebase Presence Service tests [Estimate: 1hr]
  - **Files**: `app/src/test/java/com/example/liftrix/service/FirebasePresenceServiceTest.kt`
  - **Details**: Mock Firebase interactions and test presence state management

### Dependencies
- BE-001 depends on DB-001, DB-002
- BE-002 depends on BE-003
- FE-001 depends on BE-001, BE-002
- FE-002 depends on FE-001, FE-003
- FE-004 depends on FE-001
- INT-001 depends on BE-002
- All TEST tasks depend on their respective implementation tasks

## Success Metrics
- **Social Engagement**: 40% of users add at least 1 friend within first month
- **Retention Impact**: 30% increase in 7-day retention for users with 3+ friends
- **Feature Usage**: 60% of friend-connected users interact with social feed weekly
- **Performance**: Presence updates delivered within 30 seconds for 95% of interactions

## Timeline
**Total Effort**: 41 hours (approximately 5-6 developer days)
**Critical Path**: DB-001 → DB-002 → BE-001 → FE-001 → FE-002 → Integration (17.5 hours minimum)