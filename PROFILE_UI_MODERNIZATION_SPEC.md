# Liftrix Profile UI Modernization Specification

## Executive Summary
Complete specification for modernizing the Liftrix profile UI to achieve a clean, modern design with enhanced visual hierarchy, improved statistics display, achievement progress visualization, and recent activity tracking. This spec provides 95%+ implementation confidence with clear technical requirements and design patterns.

## 1. VISUAL DESIGN TRANSFORMATION

### 1.1 Layout Architecture
```
┌─────────────────────────────────────────────┐
│  ProfileHeader                              │
│  ┌──────────────────────────────────────┐   │
│  │  [Profile Image - 100dp]              │   │
│  │  DisplayName                          │   │
│  │  @username • Bio                      │   │
│  └──────────────────────────────────────┘   │
│                                             │
│  ┌─────────┬─────────┬─────────┐          │
│  │   24    │   156   │    89   │          │
│  │ Workouts│Followers│Following│          │
│  └─────────┴─────────┴─────────┘          │
│                                             │
│  [Follow Button]  [Message]                │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  ThisWeeksProgress                         │
│  ┌──────────────────────────────────────┐   │
│  │  Workouts: 3/5        ██████░░ 60%   │   │
│  │  Volume: 12,450 lbs    ████████ 85%  │   │
│  │  Streak: 7 days        ██████░░ 70%  │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  Achievements                               │
│  ┌──────────────────────────────────────┐   │
│  │  🏆 First Workout      ██████ 1/1    │   │
│  │  💪 Volume Master       ████░░ 4/5    │   │
│  │  🔥 Streak Champion     ██░░░░ 2/10   │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│  RecentActivity                             │
│  ┌──────────────────────────────────────┐   │
│  │  🏋️ Upper Body Workout                │   │
│  │     Today • 45 min • 8 exercises      │   │
│  │                                        │   │
│  │  🏋️ Leg Day                            │   │
│  │     Yesterday • 1h 2min • 6 exercises │   │
│  │                                        │   │
│  │  [See All Activity →]                  │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### 1.2 Component Specifications

#### ProfileHeaderCard
```kotlin
data class ProfileHeaderSpec(
    val imageSize: Dp = 100.dp,
    val imageShape: Shape = CircleShape,
    val imageBorder: BorderStroke = BorderStroke(3.dp, LiftrixColorsV2.Teal),
    val nameFontSize: TextUnit = 24.sp,
    val usernameFontSize: TextUnit = 14.sp,
    val bioMaxLines: Int = 2,
    val cardRadius: Dp = 16.dp,
    val cardElevation: Dp = 2.dp,
    val contentPadding: Dp = 20.dp
)
```

#### StatsDisplay
```kotlin
data class StatsItemSpec(
    val valueFontSize: TextUnit = 28.sp,
    val valueFontWeight: FontWeight = FontWeight.Bold,
    val labelFontSize: TextUnit = 12.sp,
    val labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    val spacing: Dp = 4.dp,
    val clickable: Boolean = true
)
```

#### ProgressCard
```kotlin
data class ProgressItemSpec(
    val showPercentage: Boolean = true,
    val progressBarHeight: Dp = 8.dp,
    val progressBarRadius: Dp = 4.dp,
    val progressColor: Color = Color(0xFF4ADE80), // Green
    val backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    val animationDuration: Int = 600,
    val labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    val valueStyle: TextStyle = MaterialTheme.typography.titleMedium
)
```

## 2. COLOR SYSTEM ENHANCEMENTS

### 2.1 Extended Color Palette
```kotlin
object ProfileColors {
    // Achievement Progress Colors
    val ProgressGreen = Color(0xFF4ADE80)
    val ProgressGreenDark = Color(0xFF22C55E)
    val ProgressGreenLight = Color(0xFF86EFAC)
    val ProgressBackground = Color(0xFFE5E7EB)
    
    // Stats Colors
    val StatsValuePrimary = LiftrixColorsV2.Teal
    val StatsLabelSecondary = Color(0xFF6B7280)
    
    // Activity Colors
    val ActivityIconTint = LiftrixColorsV2.TealDark
    val ActivityTimeColor = Color(0xFF9CA3AF)
    
    // Achievement Badge Colors
    val BadgeGold = Color(0xFFFBBF24)
    val BadgeSilver = Color(0xFFCBD5E1)
    val BadgeBronze = Color(0xFFF97316)
}
```

### 2.2 Theme Integration
```kotlin
@Composable
fun ProfileTheme.colors(): ProfileColorScheme {
    val isDark = isSystemInDarkTheme()
    return ProfileColorScheme(
        progressBar = ProfileColors.ProgressGreen,
        progressBackground = if (isDark) 
            Color(0xFF374151) else ProfileColors.ProgressBackground,
        statsValue = if (isDark) 
            LiftrixColorsV2.TealLight else LiftrixColorsV2.Teal,
        statsLabel = if (isDark) 
            Color(0xFF9CA3AF) else Color(0xFF6B7280),
        cardBackground = if (isDark) 
            LiftrixColorsV2.Dark.BackgroundSecondary 
            else LiftrixColorsV2.Light.BackgroundSecondary
    )
}
```

## 3. DATA ARCHITECTURE

### 3.1 Enhanced UI State
```kotlin
data class ModernProfileUiState(
    // Existing fields
    val profile: PublicUserProfile? = null,
    val followStatus: FollowStatus = FollowStatus.NONE,
    val canViewDetails: Boolean = false,
    val isOwnProfile: Boolean = false,
    
    // New fields for modern UI
    val weeklyProgress: WeeklyProgressData? = null,
    val achievementProgress: List<AchievementProgressItem> = emptyList(),
    val recentActivities: List<ActivityItem> = emptyList(),
    val profileStats: ProfileStatsData? = null,
    val isRefreshing: Boolean = false,
    
    // Layout preferences
    val selectedView: ProfileViewType = ProfileViewType.OVERVIEW,
    val showAllActivities: Boolean = false,
    val expandedAchievements: Boolean = false,
    
    // Loading states
    val isLoadingProgress: Boolean = false,
    val isLoadingActivities: Boolean = false,
    val isLoadingAchievements: Boolean = false,
    
    // Error states
    val error: LiftrixError? = null
)

data class WeeklyProgressData(
    val workoutsCompleted: Int,
    val workoutsGoal: Int,
    val totalVolume: Float,
    val volumeGoal: Float,
    val currentStreak: Int,
    val streakGoal: Int,
    val weekStartDate: LocalDate,
    val weekEndDate: LocalDate
)

data class AchievementProgressItem(
    val id: String,
    val title: String,
    val icon: String, // Emoji or icon identifier
    val currentProgress: Int,
    val maxProgress: Int,
    val progressPercentage: Float,
    val category: AchievementCategory,
    val nextMilestone: String?,
    val isCompleted: Boolean
)

data class ActivityItem(
    val id: String,
    val type: ActivityType,
    val title: String,
    val timestamp: LocalDateTime,
    val duration: Duration?,
    val exerciseCount: Int?,
    val volumeLifted: Float?,
    val personalRecords: List<String>?,
    val icon: String
)

enum class ProfileViewType {
    OVERVIEW,
    WORKOUTS,
    ACHIEVEMENTS,
    STATISTICS
}

enum class ActivityType {
    WORKOUT_COMPLETED,
    ACHIEVEMENT_UNLOCKED,
    PERSONAL_RECORD,
    STREAK_MILESTONE
}
```

### 3.2 Repository Extensions
```kotlin
interface EnhancedProfileRepository : ProfileRepository {
    // Weekly Progress
    suspend fun getWeeklyProgress(
        userId: String,
        weekStartDate: LocalDate
    ): LiftrixResult<WeeklyProgressData>
    
    // Achievement Progress
    suspend fun getAchievementProgress(
        userId: String
    ): Flow<List<AchievementProgressItem>>
    
    // Recent Activities
    suspend fun getRecentActivities(
        userId: String,
        limit: Int = 10,
        includeWorkouts: Boolean = true,
        includeAchievements: Boolean = true,
        includePRs: Boolean = true
    ): Flow<List<ActivityItem>>
    
    // Public Workout Details
    suspend fun getPublicWorkoutDetails(
        workoutId: String,
        viewerId: String
    ): LiftrixResult<PublicWorkoutDetail>
}
```

### 3.3 Use Cases
```kotlin
class GetWeeklyProgressUseCase @Inject constructor(
    private val repository: EnhancedProfileRepository,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) {
    suspend operator fun invoke(
        profileUserId: String
    ): LiftrixResult<WeeklyProgressData> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "WEEKLY_PROGRESS_FETCH_FAILED",
                errorMessage = "Failed to fetch weekly progress",
                analyticsContext = mapOf("user_id" to profileUserId)
            )
        }
    ) {
        val viewerId = getCurrentUserIdUseCase()
        
        // Check privacy permissions
        if (!canViewProgress(profileUserId, viewerId)) {
            return@liftrixCatching WeeklyProgressData(
                workoutsCompleted = 0,
                workoutsGoal = 0,
                totalVolume = 0f,
                volumeGoal = 0f,
                currentStreak = 0,
                streakGoal = 0,
                weekStartDate = LocalDate.now().with(DayOfWeek.MONDAY),
                weekEndDate = LocalDate.now().with(DayOfWeek.SUNDAY)
            )
        }
        
        repository.getWeeklyProgress(
            userId = profileUserId,
            weekStartDate = LocalDate.now().with(DayOfWeek.MONDAY)
        )
    }
}

class GetAchievementProgressUseCase @Inject constructor(
    private val repository: EnhancedProfileRepository,
    private val achievementCalculator: AchievementProgressCalculator
) {
    operator fun invoke(
        userId: String
    ): Flow<List<AchievementProgressItem>> {
        return repository.getAchievementProgress(userId)
            .map { achievements ->
                achievements.map { achievement ->
                    achievementCalculator.calculateProgress(achievement)
                }
            }
    }
}
```

## 4. COMPONENT IMPLEMENTATION

### 4.1 ModernProfileHeader
```kotlin
@Composable
fun ModernProfileHeader(
    profile: PublicUserProfile,
    stats: ProfileStatsData,
    followStatus: FollowStatus,
    isOwnProfile: Boolean,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onStatsClick: (StatType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image with border
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = profile.profileImageUrl,
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(3.dp, LiftrixColorsV2.Teal),
                            CircleShape
                        )
                )
                
                if (profile.isVerified) {
                    VerifiedBadge(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Name and username
            Text(
                text = profile.displayName ?: profile.username,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "@${profile.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Bio
            profile.bio?.let { bio ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatsItem(
                    value = stats.workoutCount.toString(),
                    label = "Workouts",
                    onClick = { onStatsClick(StatType.WORKOUTS) }
                )
                
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                StatsItem(
                    value = formatCount(stats.followersCount),
                    label = "Followers",
                    onClick = { onStatsClick(StatType.FOLLOWERS) }
                )
                
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                StatsItem(
                    value = formatCount(stats.followingCount),
                    label = "Following",
                    onClick = { onStatsClick(StatType.FOLLOWING) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isOwnProfile) {
                    ModernActionButton(
                        text = "Edit Profile",
                        onClick = { /* Navigate to edit */ },
                        modifier = Modifier.weight(1f),
                        type = ActionButtonType.PRIMARY
                    )
                } else {
                    FollowButton(
                        followStatus = followStatus,
                        onClick = onFollowClick,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ModernActionButton(
                        text = "Message",
                        onClick = onMessageClick,
                        modifier = Modifier.weight(1f),
                        type = ActionButtonType.SECONDARY,
                        leadingIcon = Icons.Default.Message
                    )
                }
            }
        }
    }
}
```

### 4.2 WeeklyProgressCard
```kotlin
@Composable
fun WeeklyProgressCard(
    progressData: WeeklyProgressData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "This Week's Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Workouts Progress
            ProgressItem(
                label = "Workouts",
                current = progressData.workoutsCompleted,
                total = progressData.workoutsGoal,
                color = ProfileColors.ProgressGreen
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Volume Progress
            ProgressItem(
                label = "Volume",
                currentText = formatVolume(progressData.totalVolume),
                percentage = (progressData.totalVolume / progressData.volumeGoal * 100).toInt(),
                color = ProfileColors.ProgressGreen
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Streak Progress
            ProgressItem(
                label = "Streak",
                currentText = "${progressData.currentStreak} days",
                percentage = (progressData.currentStreak.toFloat() / progressData.streakGoal * 100).toInt(),
                color = ProfileColors.ProgressGreen
            )
        }
    }
}

@Composable
private fun ProgressItem(
    label: String,
    current: Int? = null,
    total: Int? = null,
    currentText: String? = null,
    percentage: Int? = null,
    color: Color
) {
    val actualPercentage = percentage ?: if (current != null && total != null) {
        ((current.toFloat() / total) * 100).toInt()
    } else 0
    
    val animatedProgress by animateFloatAsState(
        targetValue = actualPercentage / 100f,
        animationSpec = tween(600),
        label = "progress"
    )
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = currentText ?: "$current/$total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "$actualPercentage%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = ProfileColors.ProgressBackground
        )
    }
}
```

### 4.3 AchievementProgressCard
```kotlin
@Composable
fun AchievementProgressCard(
    achievements: List<AchievementProgressItem>,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onSeeAllClick) {
                    Text("See All")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            achievements.take(3).forEach { achievement ->
                AchievementProgressRow(
                    achievement = achievement,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (achievement != achievements.take(3).last()) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AchievementProgressRow(
    achievement: AchievementProgressItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Text(
            text = achievement.icon,
            fontSize = 24.sp,
            modifier = Modifier.width(32.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Title and Progress
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = achievement.progressPercentage / 100f,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (achievement.isCompleted) 
                        ProfileColors.ProgressGreen 
                        else ProfileColors.ProgressGreen.copy(alpha = 0.8f),
                    trackColor = ProfileColors.ProgressBackground
                )
                
                Text(
                    text = "${achievement.currentProgress}/${achievement.maxProgress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

### 4.4 RecentActivityCard
```kotlin
@Composable
fun RecentActivityCard(
    activities: List<ActivityItem>,
    onActivityClick: (ActivityItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onSeeAllClick) {
                    Text("See All")
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (activities.isEmpty()) {
                EmptyActivityState()
            } else {
                activities.take(5).forEach { activity ->
                    ActivityRow(
                        activity = activity,
                        onClick = { onActivityClick(activity) }
                    )
                    
                    if (activity != activities.take(5).last()) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    activity: ActivityItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Activity Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = LiftrixColorsV2.Teal.copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = activity.icon,
                fontSize = 20.sp
            )
        }
        
        // Activity Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatActivityTime(activity.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = ProfileColors.ActivityTimeColor
                )
                
                activity.duration?.let { duration ->
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileColors.ActivityTimeColor
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileColors.ActivityTimeColor
                    )
                }
                
                activity.exerciseCount?.let { count ->
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileColors.ActivityTimeColor
                    )
                    Text(
                        text = "$count exercises",
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileColors.ActivityTimeColor
                    )
                }
            }
            
            // Personal Records Badge
            activity.personalRecords?.let { prs ->
                if (prs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        Badge(
                            containerColor = ProfileColors.ProgressGreen.copy(alpha = 0.1f),
                            contentColor = ProfileColors.ProgressGreenDark
                        ) {
                            Text(
                                text = "🏆 ${prs.size} PR${if (prs.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
```

## 5. VIEW MODEL ENHANCEMENTS

### 5.1 ModernProfileViewModel
```kotlin
@HiltViewModel
class ModernProfileViewModel @Inject constructor(
    private val getPublicProfileUseCase: GetPublicProfileUseCase,
    private val getWeeklyProgressUseCase: GetWeeklyProgressUseCase,
    private val getAchievementProgressUseCase: GetAchievementProgressUseCase,
    private val getRecentActivitiesUseCase: GetRecentActivitiesUseCase,
    private val followUserUseCase: FollowUserUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    errorHandler: ErrorHandler
) : BaseViewModel<ModernProfileUiState, ProfileEvent>(errorHandler) {
    
    override val _uiState = MutableStateFlow(ModernProfileUiState())
    
    fun loadProfile(userId: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true) }
            
            // Load profile data in parallel
            val profileDeferred = async { getPublicProfileUseCase(GetPublicProfileRequest(userId)) }
            val progressDeferred = async { getWeeklyProgressUseCase(userId) }
            
            // Handle profile result
            profileDeferred.await().fold(
                onSuccess = { profileResult ->
                    updateState { 
                        it.copy(
                            profile = profileResult.profile,
                            canViewDetails = profileResult.canInteract,
                            isOwnProfile = profileResult.isOwnProfile,
                            followStatus = determineFollowStatus(profileResult.profile)
                        )
                    }
                    
                    // Load additional data if viewable
                    if (profileResult.canInteract) {
                        loadAdditionalData(userId)
                    }
                },
                onFailure = { error ->
                    updateState { it.copy(error = error, isLoading = false) }
                }
            )
            
            // Handle progress result
            progressDeferred.await().fold(
                onSuccess = { progress ->
                    updateState { it.copy(weeklyProgress = progress) }
                },
                onFailure = { /* Log error but don't show to user */ }
            )
            
            updateState { it.copy(isLoading = false) }
        }
    }
    
    private fun loadAdditionalData(userId: String) {
        // Load achievements
        viewModelScope.launch {
            getAchievementProgressUseCase(userId)
                .catch { /* Handle error */ }
                .collect { achievements ->
                    updateState { it.copy(achievementProgress = achievements) }
                }
        }
        
        // Load recent activities
        viewModelScope.launch {
            getRecentActivitiesUseCase(userId)
                .catch { /* Handle error */ }
                .collect { activities ->
                    updateState { it.copy(recentActivities = activities) }
                }
        }
    }
    
    fun refreshProfile() {
        val currentProfile = _uiState.value.profile ?: return
        
        viewModelScope.launch {
            updateState { it.copy(isRefreshing = true) }
            loadProfile(currentProfile.userId)
            updateState { it.copy(isRefreshing = false) }
        }
    }
    
    fun toggleFollowStatus() {
        // Implementation with optimistic updates
        val currentStatus = _uiState.value.followStatus
        val optimisticStatus = when (currentStatus) {
            FollowStatus.NONE -> FollowStatus.FOLLOWING
            FollowStatus.FOLLOWING -> FollowStatus.NONE
            else -> currentStatus
        }
        
        updateState { it.copy(followStatus = optimisticStatus) }
        
        viewModelScope.launch {
            val profile = _uiState.value.profile ?: return@launch
            
            followUserUseCase(
                targetUserId = profile.userId,
                action = determineFollowAction(currentStatus),
                context = "PROFILE_VIEW"
            ).fold(
                onSuccess = { newStatus ->
                    updateState { it.copy(followStatus = newStatus) }
                },
                onFailure = { error ->
                    // Revert optimistic update
                    updateState { 
                        it.copy(
                            followStatus = currentStatus,
                            error = error
                        )
                    }
                }
            )
        }
    }
}
```

## 6. NAVIGATION & ROUTING

### 6.1 Enhanced Navigation Routes
```kotlin
@Serializable
sealed class ProfileRoute : LiftrixRoute() {
    @Serializable
    data class UserProfile(
        val userId: String,
        val initialTab: ProfileTab = ProfileTab.OVERVIEW
    ) : ProfileRoute()
    
    @Serializable
    data class WorkoutDetail(
        val workoutId: String,
        val isPublicView: Boolean = false
    ) : ProfileRoute()
    
    @Serializable
    data class AchievementList(
        val userId: String
    ) : ProfileRoute()
    
    @Serializable
    data class ActivityHistory(
        val userId: String,
        val filterType: ActivityType? = null
    ) : ProfileRoute()
    
    @Serializable
    data class FollowersList(
        val userId: String
    ) : ProfileRoute()
    
    @Serializable
    data class FollowingList(
        val userId: String
    ) : ProfileRoute()
}

enum class ProfileTab {
    OVERVIEW,
    WORKOUTS,
    ACHIEVEMENTS,
    STATISTICS
}
```

## 7. PRIVACY & PERMISSIONS

### 7.1 Privacy-Aware Data Loading
```kotlin
class PrivacyAwareDataLoader @Inject constructor(
    private val privacyService: PrivacyEnforcementService,
    private val repository: EnhancedProfileRepository
) {
    suspend fun loadProfileData(
        profileUserId: String,
        viewerId: String?
    ): ProfileDataPackage {
        val canViewDetails = privacyService.canViewProfile(profileUserId, viewerId)
        val canViewWorkouts = privacyService.canViewWorkouts(profileUserId, viewerId)
        val canViewAchievements = privacyService.canViewAchievements(profileUserId, viewerId)
        
        return ProfileDataPackage(
            profile = repository.getPublicProfile(profileUserId, viewerId),
            weeklyProgress = if (canViewDetails) {
                repository.getWeeklyProgress(profileUserId)
            } else null,
            achievements = if (canViewAchievements) {
                repository.getAchievementProgress(profileUserId)
            } else emptyFlow(),
            recentActivities = if (canViewWorkouts) {
                repository.getRecentActivities(profileUserId)
            } else emptyFlow()
        )
    }
}
```

## 8. PERFORMANCE OPTIMIZATIONS

### 8.1 Lazy Loading Strategy
```kotlin
class LazyProfileDataLoader {
    private val loadingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun loadProfileInStages(userId: String) {
        // Stage 1: Critical data (immediate)
        loadingScope.launch {
            loadCriticalData(userId) // Profile, stats, follow status
        }
        
        // Stage 2: Above the fold (delayed 100ms)
        loadingScope.launch {
            delay(100)
            loadAboveFoldData(userId) // Weekly progress, first 3 achievements
        }
        
        // Stage 3: Below the fold (delayed 300ms)
        loadingScope.launch {
            delay(300)
            loadBelowFoldData(userId) // Recent activities, remaining achievements
        }
    }
}
```

### 8.2 Caching Strategy
```kotlin
class ProfileCacheManager @Inject constructor(
    private val cache: ReactiveViewModelCache
) {
    private val profileCache = mutableMapOf<String, CachedProfileData>()
    
    fun getCachedProfile(userId: String): CachedProfileData? {
        val cached = profileCache[userId]
        return if (cached != null && !cached.isStale()) {
            cached
        } else null
    }
    
    fun cacheProfile(userId: String, data: ProfileDataPackage) {
        profileCache[userId] = CachedProfileData(
            data = data,
            timestamp = System.currentTimeMillis()
        )
    }
}

data class CachedProfileData(
    val data: ProfileDataPackage,
    val timestamp: Long,
    val ttl: Long = 5 * 60 * 1000 // 5 minutes
) {
    fun isStale(): Boolean = System.currentTimeMillis() - timestamp > ttl
}
```

## 9. ANIMATIONS & TRANSITIONS

### 9.1 Animation Specifications
```kotlin
object ProfileAnimations {
    val progressBarAnimation = tween<Float>(
        durationMillis = 600,
        easing = FastOutSlowInEasing
    )
    
    val cardEnterAnimation = fadeIn(
        animationSpec = tween(300)
    ) + expandVertically(
        animationSpec = tween(350)
    )
    
    val statsCounterAnimation = animateIntAsState(
        targetValue = value,
        animationSpec = tween(
            durationMillis = 1000,
            easing = LinearOutSlowInEasing
        )
    )
    
    val followButtonAnimation = animateColorAsState(
        targetValue = followColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
}
```

## 10. ACCESSIBILITY

### 10.1 Accessibility Requirements
```kotlin
object ProfileAccessibility {
    // Content Descriptions
    const val PROFILE_IMAGE = "User profile image"
    const val FOLLOW_BUTTON = "Follow user"
    const val STATS_WORKOUTS = "Total workouts completed: %d"
    const val STATS_FOLLOWERS = "Total followers: %d"
    const val PROGRESS_BAR = "Progress: %d percent complete"
    const val ACHIEVEMENT_PROGRESS = "Achievement %s: %d of %d completed"
    
    // Semantic Properties
    fun applySemantics(modifier: Modifier, role: Role, description: String): Modifier {
        return modifier.semantics {
            this.role = role
            contentDescription = description
            stateDescription = description
        }
    }
    
    // Touch Target Sizes
    const val MIN_TOUCH_TARGET = 48.dp
    const val PREFERRED_TOUCH_TARGET = 56.dp
}
```

## 11. TESTING STRATEGY

### 11.1 Unit Tests
```kotlin
class ModernProfileViewModelTest {
    @Test
    fun `loading profile with privacy restrictions shows limited data`() {
        // Given
        val privateProfile = createPrivateProfile()
        
        // When
        viewModel.loadProfile(privateProfile.userId)
        
        // Then
        assertThat(viewModel.uiState.value.canViewDetails).isFalse()
        assertThat(viewModel.uiState.value.weeklyProgress).isNull()
        assertThat(viewModel.uiState.value.recentActivities).isEmpty()
    }
    
    @Test
    fun `achievement progress calculation is accurate`() {
        // Given
        val achievement = createInProgressAchievement(current = 3, max = 5)
        
        // When
        val progress = calculator.calculateProgress(achievement)
        
        // Then
        assertThat(progress.progressPercentage).isEqualTo(60f)
        assertThat(progress.isCompleted).isFalse()
    }
}
```

### 11.2 UI Tests
```kotlin
class ProfileScreenTest {
    @Test
    fun `weekly progress card displays correct percentages`() {
        composeTestRule.setContent {
            WeeklyProgressCard(
                progressData = WeeklyProgressData(
                    workoutsCompleted = 3,
                    workoutsGoal = 5,
                    totalVolume = 12450f,
                    volumeGoal = 15000f,
                    currentStreak = 7,
                    streakGoal = 10
                )
            )
        }
        
        composeTestRule
            .onNodeWithText("60%")
            .assertExists()
        
        composeTestRule
            .onNodeWithText("3/5")
            .assertExists()
    }
}
```

## 12. MIGRATION PLAN

### 12.1 Phased Rollout
```
Phase 1 (Week 1-2):
- Implement data layer changes (repositories, use cases)
- Add new UI state fields
- Create progress calculation services

Phase 2 (Week 3-4):
- Build new UI components (cards, progress bars)
- Integrate with existing navigation
- Add animations and transitions

Phase 3 (Week 5):
- Feature flag deployment (10% users)
- A/B testing metrics collection
- Performance monitoring

Phase 4 (Week 6):
- Full rollout based on metrics
- Legacy code cleanup
- Documentation update
```

### 12.2 Feature Flags
```kotlin
object ProfileFeatureFlags {
    const val MODERN_PROFILE_UI = "modern_profile_ui"
    const val WEEKLY_PROGRESS = "weekly_progress_card"
    const val ACHIEVEMENT_PROGRESS = "achievement_progress_bars"
    const val RECENT_ACTIVITY = "recent_activity_feed"
    
    fun isModernProfileEnabled(): Boolean {
        return RemoteConfig.getBoolean(MODERN_PROFILE_UI)
    }
}
```

## 13. SUCCESS METRICS

### 13.1 Key Performance Indicators
- **Engagement Rate**: 25% increase in profile views
- **Interaction Rate**: 30% increase in follow actions
- **Retention**: 15% increase in return visits to profiles
- **Performance**: <100ms card render time
- **Accessibility**: 100% WCAG 2.1 AA compliance

### 13.2 Analytics Events
```kotlin
object ProfileAnalytics {
    fun trackProfileView(userId: String, viewerId: String?, source: String) {
        Analytics.track("profile_viewed", mapOf(
            "profile_user_id" to userId,
            "viewer_id" to viewerId,
            "source" to source,
            "ui_version" to "modern"
        ))
    }
    
    fun trackProgressCardInteraction(type: String) {
        Analytics.track("progress_card_interaction", mapOf(
            "card_type" to type,
            "action" to "tap"
        ))
    }
    
    fun trackAchievementView(achievementId: String, progress: Float) {
        Analytics.track("achievement_viewed", mapOf(
            "achievement_id" to achievementId,
            "progress_percentage" to progress
        ))
    }
}
```

## 14. HANDOFF PROTOCOL

### 14.1 Design Assets Required
- [ ] Profile header component specs (Figma)
- [ ] Progress bar color specifications
- [ ] Achievement icon set (24 icons minimum)
- [ ] Activity type icons (8 icons)
- [ ] Loading state skeletons
- [ ] Empty state illustrations

### 14.2 Backend Requirements
- [ ] Weekly progress calculation endpoint
- [ ] Achievement progress tracking
- [ ] Activity aggregation service
- [ ] Public workout details API
- [ ] Privacy permission matrix

### 14.3 Code Review Checklist
- [ ] All components use LiftrixColorsV2
- [ ] User scoping applied to all queries
- [ ] LiftrixResult<T> error handling
- [ ] Accessibility annotations complete
- [ ] Performance metrics logged
- [ ] Feature flags configured
- [ ] Unit test coverage >80%
- [ ] UI test coverage for critical paths

## 15. DEPENDENCIES

### 15.1 Required Libraries
```gradle
dependencies {
    // Existing dependencies maintained
    
    // New additions for modern profile
    implementation "androidx.compose.animation:animation-graphics:$compose_version"
    implementation "io.coil-kt:coil-compose:2.5.0"
    implementation "com.airbnb.android:lottie-compose:6.3.0"
}
```

### 15.2 Architecture Components
- ViewModel with StateFlow
- Paging 3 for activity feed
- WorkManager for background sync
- Room for offline caching
- Hilt for dependency injection

## APPENDIX A: Component Inventory

### New Components to Create
1. `ModernProfileHeader.kt`
2. `WeeklyProgressCard.kt`
3. `AchievementProgressCard.kt`
4. `RecentActivityCard.kt`
5. `StatsDisplay.kt`
6. `ProfileColorSystem.kt`
7. `ProfileAnimations.kt`

### Modified Components
1. `UserProfileScreen.kt` → `ModernProfileScreen.kt`
2. `UserProfileViewModel.kt` → `ModernProfileViewModel.kt`
3. `ProfileRepository.kt` → `EnhancedProfileRepository.kt`

### Deprecated Components
1. `ProfileTabsSection` (replaced by unified view)
2. `ProfileStatRow` (replaced by StatsDisplay)
3. `AchievementDisplay` (enhanced with progress bars)

## APPENDIX B: Error States

### Error Handling Matrix
| Error Type | User Message | Recovery Action |
|------------|--------------|-----------------|
| Network Error | "Unable to load profile" | Retry button |
| Privacy Restricted | "This profile is private" | Show follow prompt |
| User Not Found | "Profile not found" | Navigate back |
| Rate Limited | "Too many requests" | Auto-retry after delay |

## CONCLUSION

This specification provides a complete blueprint for modernizing the Liftrix profile UI with:
- Enhanced visual hierarchy with larger profile images and cleaner layouts
- Progress visualization with animated green progress bars
- Achievement progress tracking with milestone indicators
- Recent activity feed with workout history
- Improved statistics display with prominent numeric values
- Privacy-aware data loading and display
- Performance optimizations through lazy loading and caching
- Complete accessibility compliance
- Comprehensive testing strategy

Implementation confidence: **95%+**

All technical decisions align with existing Liftrix architecture patterns while introducing modern UI enhancements that improve user engagement and visual appeal.