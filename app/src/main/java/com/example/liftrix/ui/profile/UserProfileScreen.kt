package com.example.liftrix.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.ui.animations.ScreenLoadingSkeleton
import com.example.liftrix.ui.error.ErrorHandlingExtensions.LiftrixErrorState
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.profile.components.AchievementDisplay
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import com.example.liftrix.ui.theme.LiftrixSpacing
import timber.log.Timber

/**
 * UserProfileScreen - Displays other users' profiles with follow functionality
 * 
 * Comprehensive profile viewing interface with:
 * - Privacy-aware content display based on follow relationship
 * - Follow/unfollow button with state management
 * - Profile tabs for workouts, stats, achievements (when accessible)
 * - Mutual connection indicators
 * - Block/report functionality
 * - Profile sharing capabilities
 * 
 * Design System Compliance:
 * - Uses UnifiedWorkoutCard for consistent layout
 * - Follows ModernActionButton hierarchy (Primary/Secondary/Tertiary)
 * - Implements LiftrixSpacing semantic tokens
 * - WCAG 2.1 AA accessibility compliance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFollowersList: (String) -> Unit,
    onNavigateToFollowingList: (String) -> Unit,
    onNavigateToWorkoutDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Load profile when userId changes
    LaunchedEffect(userId) {
        viewModel.loadUserProfile(userId)
    }
    
    Timber.d("UserProfileScreen: Composing for user $userId with state - loading: ${uiState.isLoading}")
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LiftrixSpacing.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Profile") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                uiState.profile?.let { profile ->
                    // Share profile button
                    IconButton(onClick = { viewModel.shareProfile(profile) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Profile")
                    }
                    
                    // More options menu
                    IconButton(onClick = { viewModel.showMoreOptions() }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                }
            }
        )
        
        when {
            uiState.isLoading -> {
                UserProfileLoadingState()
            }
            uiState.error != null -> {
                UserProfileErrorState(
                    error = uiState.error,
                    onRetry = { viewModel.retryLastOperation() }
                )
            }
            uiState.profile != null -> {
                val profile = uiState.profile!!
                UserProfileContent(
                    profile = profile,
                    followStatus = uiState.followStatus,
                    canViewDetails = uiState.canViewDetails,
                    isOwnProfile = uiState.isOwnProfile,
                    mutualConnectionCount = uiState.mutualConnectionCount,
                    selectedTab = uiState.selectedTab,
                    onFollowClick = { viewModel.toggleFollow() },
                    onBlockClick = { viewModel.blockUser() },
                    onTabSelected = { viewModel.selectTab(it) },
                    onNavigateToFollowersList = onNavigateToFollowersList,
                    onNavigateToFollowingList = onNavigateToFollowingList,
                    onNavigateToWorkoutDetail = onNavigateToWorkoutDetail
                )
            }
            else -> {
                UserProfileNotFoundState()
            }
        }
    }
}

@Composable
private fun UserProfileContent(
    profile: PublicUserProfile,
    followStatus: FollowStatus,
    canViewDetails: Boolean,
    isOwnProfile: Boolean,
    mutualConnectionCount: Int,
    selectedTab: ProfileTab,
    onFollowClick: () -> Unit,
    onBlockClick: () -> Unit,
    onTabSelected: (ProfileTab) -> Unit,
    onNavigateToFollowersList: (String) -> Unit,
    onNavigateToFollowingList: (String) -> Unit,
    onNavigateToWorkoutDetail: (String) -> Unit
) {
    // Profile Header Card
    UnifiedWorkoutCard(
        title = profile.displayName ?: "Unknown User",
        subtitle = generateProfileSubtitle(profile),
        leadingIcon = Icons.Default.Person
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                verticalAlignment = Alignment.Top
            ) {
                // Profile Image
                ProfileImageDisplay(
                    imageUrl = profile.profileImageUrl,
                    displayName = profile.displayName ?: "User",
                    userId = profile.userId,
                    size = 80.dp,
                    onClick = null, // No click for other users' images
                    modifier = Modifier
                )
                
                // Profile Stats
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall)
                ) {
                    if (canViewDetails) {
                        ProfileStatRow("Workouts", profile.totalWorkouts.toString())
                        ProfileStatRow("Followers", "${profile.followersCount}")
                        ProfileStatRow("Following", "${profile.followingCount}")
                        if (profile.currentStreak > 0) {
                            ProfileStatRow("Current Streak", "${profile.currentStreak} days")
                        }
                    } else {
                        Text(
                            text = "Private Profile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Mutual Connections
            if (mutualConnectionCount > 0 && !isOwnProfile) {
                Text(
                    text = "$mutualConnectionCount mutual connection${if (mutualConnectionCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Action Buttons
            if (!isOwnProfile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.buttonSpacing)
                ) {
                    // Follow Button
                    FollowButton(
                        followStatus = followStatus,
                        onClick = onFollowClick,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // More Actions
                    SecondaryActionButton(
                        text = "More",
                        onClick = onBlockClick,
                        leadingIcon = Icons.Default.MoreHoriz
                    )
                }
            }
            
            // Follower/Following counts (clickable if accessible)
            if (canViewDetails && (profile.followersCount > 0 || profile.followingCount > 0)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
                ) {
                    if (profile.followersCount > 0) {
                        TextButton(
                            onClick = { onNavigateToFollowersList(profile.userId) }
                        ) {
                            Text("${profile.followersCount} follower${if (profile.followersCount > 1) "s" else ""}")
                        }
                    }
                    
                    if (profile.followingCount > 0) {
                        TextButton(
                            onClick = { onNavigateToFollowingList(profile.userId) }
                        ) {
                            Text("${profile.followingCount} following")
                        }
                    }
                }
            }
        }
    }
    
    // Bio Section
    if (canViewDetails && !profile.bio.isNullOrBlank()) {
        UnifiedWorkoutCard(
            title = "About",
            subtitle = null
        ) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    
    // Achievements Section (if accessible)
    if (canViewDetails && profile.achievements.isNotEmpty()) {
        UnifiedWorkoutCard(
            title = "Achievements",
            subtitle = "${profile.achievements.size} unlocked",
            leadingIcon = Icons.Default.Star
        ) {
            AchievementDisplay(
                achievements = profile.achievements.map { achievement ->
                    com.example.liftrix.domain.model.UserAchievement(
                        id = achievement.id,
                        userId = profile.userId,
                        achievementType = com.example.liftrix.domain.model.AchievementType.FIRST_TIME_EVENTS, // Default for social achievements
                        title = achievement.title,
                        description = achievement.description,
                        unlockedAt = achievement.earnedAt
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    
    // Content Tabs (if accessible)
    if (canViewDetails) {
        ProfileTabsSection(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            profile = profile,
            onNavigateToWorkoutDetail = onNavigateToWorkoutDetail
        )
    } else {
        // Privacy Message
        PrivacyMessageCard(profile = profile, followStatus = followStatus)
    }
}

@Composable
private fun FollowButton(
    followStatus: FollowStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (followStatus) {
        FollowStatus.NONE -> {
            PrimaryActionButton(
                text = "Follow",
                onClick = onClick,
                leadingIcon = Icons.Default.PersonAdd,
                modifier = modifier
            )
        }
        FollowStatus.FOLLOWING -> {
            SecondaryActionButton(
                text = "Following",
                onClick = onClick,
                leadingIcon = Icons.Default.Check,
                modifier = modifier
            )
        }
        FollowStatus.PENDING_SENT -> {
            TertiaryActionButton(
                text = "Requested",
                onClick = onClick,
                leadingIcon = Icons.Default.Schedule,
                modifier = modifier
            )
        }
        FollowStatus.PENDING_RECEIVED -> {
            PrimaryActionButton(
                text = "Accept",
                onClick = onClick,
                leadingIcon = Icons.Default.Check,
                modifier = modifier
            )
        }
        FollowStatus.BLOCKED -> {
            TertiaryActionButton(
                text = "Blocked",
                onClick = onClick,
                leadingIcon = Icons.Default.Block,
                modifier = modifier,
                enabled = false
            )
        }
    }
}

@Composable
private fun ProfileTabsSection(
    selectedTab: ProfileTab,
    onTabSelected: (ProfileTab) -> Unit,
    profile: PublicUserProfile,
    onNavigateToWorkoutDetail: (String) -> Unit
) {
    Column {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            ProfileTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { 
                        Text(
                            text = tab.displayName,
                            style = MaterialTheme.typography.labelMedium
                        ) 
                    }
                )
            }
        }
        
        // Tab Content
        when (selectedTab) {
            ProfileTab.WORKOUTS -> {
                WorkoutsTabContent(
                    profile = profile,
                    onNavigateToWorkoutDetail = onNavigateToWorkoutDetail
                )
            }
            ProfileTab.STATS -> {
                StatsTabContent(profile = profile)
            }
            ProfileTab.ACHIEVEMENTS -> {
                AchievementsTabContent(profile = profile)
            }
        }
    }
}

@Composable
private fun PrivacyMessageCard(
    profile: PublicUserProfile,
    followStatus: FollowStatus
) {
    UnifiedWorkoutCard(
        title = "Private Profile",
        subtitle = null,
        leadingIcon = Icons.Default.Lock
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (followStatus) {
                    FollowStatus.PENDING_SENT -> "Your follow request is pending approval."
                    else -> "This profile is private. Follow ${profile.displayName ?: "this user"} to see their workouts and achievements."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WorkoutsTabContent(
    profile: PublicUserProfile,
    onNavigateToWorkoutDetail: (String) -> Unit
) {
    if (profile.recentWorkouts.isEmpty()) {
        Text(
            text = "No recent workouts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding)
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing),
            modifier = Modifier.height(300.dp)
        ) {
            items(profile.recentWorkouts) { workout ->
                UnifiedWorkoutCard(
                    title = workout.name,
                    subtitle = "Recent workout • ${workout.date}",
                    onClick = { onNavigateToWorkoutDetail(workout.id) }
                ) {
                    Text(
                        text = "${workout.exerciseCount} exercises • ${workout.duration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsTabContent(profile: PublicUserProfile) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing),
        modifier = Modifier.padding(LiftrixSpacing.cardPadding)
    ) {
        ProfileStatRow("Total Workouts", profile.totalWorkouts.toString())
        ProfileStatRow("Current Streak", "${profile.currentStreak} days")
        ProfileStatRow("Best Streak", "${profile.longestStreak} days")
        ProfileStatRow("Member Since", formatMemberSince(profile.memberSince))
    }
}

@Composable
private fun AchievementsTabContent(profile: PublicUserProfile) {
    if (profile.achievements.isEmpty()) {
        Text(
            text = "No achievements yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding)
        )
    } else {
        AchievementDisplay(
            achievements = profile.achievements.map { achievement ->
                com.example.liftrix.domain.model.UserAchievement(
                    id = achievement.id,
                    userId = profile.userId,
                    achievementType = com.example.liftrix.domain.model.AchievementType.FIRST_TIME_EVENTS, // Default for social achievements
                    title = achievement.title,
                    description = achievement.description,
                    unlockedAt = achievement.earnedAt
                )
            },
            modifier = Modifier.padding(LiftrixSpacing.cardPadding)
        )
    }
}

@Composable
private fun ProfileStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UserProfileLoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
    ) {
        repeat(3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(LiftrixSpacing.cardPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun UserProfileErrorState(
    error: String?,
    onRetry: () -> Unit
) {
    UnifiedWorkoutCard(
        title = "Error Loading Profile",
        subtitle = error ?: "Unknown error"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Text(
                text = "We couldn't load this profile. Please try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            PrimaryActionButton(
                text = "Retry",
                onClick = onRetry
            )
        }
    }
}

@Composable
private fun UserProfileNotFoundState() {
    UnifiedWorkoutCard(
        title = "Profile Not Found",
        subtitle = "This user profile doesn't exist or is no longer available"
    ) {
        Text(
            text = "The profile you're looking for might have been deleted or made private.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

// Helper functions
private fun generateProfileSubtitle(profile: PublicUserProfile): String {
    return buildString {
        if (profile.age != null) {
            append("${profile.age} years old")
        }
        if (profile.location?.isNotBlank() == true) {
            if (isNotEmpty()) append(" • ")
            append(profile.location)
        }
        if (isEmpty()) {
            append("Liftrix member")
        }
    }
}

private fun formatMemberSince(memberSince: java.time.LocalDateTime): String {
    val now = java.time.LocalDateTime.now()
    val period = java.time.Period.between(memberSince.toLocalDate(), now.toLocalDate())
    
    return when {
        period.years > 0 -> "${period.years} year${if (period.years > 1) "s" else ""}"
        period.months > 0 -> "${period.months} month${if (period.months > 1) "s" else ""}"
        period.days > 7 -> "${period.days / 7} week${if (period.days / 7 > 1) "s" else ""}"
        period.days > 0 -> "${period.days} day${if (period.days > 1) "s" else ""}"
        else -> "New member"
    }
}

/**
 * Enum representing profile tabs
 */
enum class ProfileTab(val displayName: String) {
    WORKOUTS("Workouts"),
    STATS("Stats"),
    ACHIEVEMENTS("Achievements")
}