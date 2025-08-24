package com.example.liftrix.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.liftrix.R
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.ui.animations.ScreenLoadingSkeleton
import com.example.liftrix.ui.error.ErrorHandlingExtensions.LiftrixErrorState
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.profile.components.ProfileImageDisplay
import com.example.liftrix.ui.profile.components.StatType
import com.example.liftrix.ui.profile.components.ProfileStatsData
import com.example.liftrix.ui.profile.components.ModernProfileHeader
import com.example.liftrix.ui.theme.ProfileColors
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.workout.components.SecondaryActionButton
import com.example.liftrix.ui.workout.components.TertiaryActionButton
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import com.example.liftrix.ui.components.ReportContentBottomSheet
import com.example.liftrix.domain.model.social.ContentType
import com.example.liftrix.domain.model.social.ReportReason
import android.content.Intent
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
 * - Uses ModernProfileHeader for consistent modern layout
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
    viewModel: UserProfileViewModel = hiltViewModel(),
    topBarActions: (@Composable () -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // State for report bottom sheet
    var showReportBottomSheet by remember { mutableStateOf(false) }
    
    // Load profile only when userId changes or it's the first load
    LaunchedEffect(userId) {
        // Only load if we don't have a profile or if the userId changed
        if (uiState.profile == null || uiState.profile?.userId != userId) {
            viewModel.loadUserProfile(userId, forceRefresh = false)
        }
    }
    
    // Handle share message when it's set - now uses enhanced PlatformShareAdapter
    LaunchedEffect(uiState.shareMessage) {
        if (!uiState.shareMessage.isNullOrBlank()) {
            // Enhanced sharing: if shareableContent is available, use platform chooser
            // Otherwise, fall back to basic text sharing
            uiState.shareableContent?.let { content ->
                // Show platform selection dialog or use default sharing
                // For now, use default Android chooser with enhanced content
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    val shareMessage = content.metadata["shareMessage"] as? String ?: content.title
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                    putExtra(Intent.EXTRA_SUBJECT, content.title)
                    // Add hashtags if supported by the platform
                    val hashtags = content.metadata["hashtags"] as? List<*>
                    if (!hashtags.isNullOrEmpty()) {
                        val hashtagText = hashtags.filterIsInstance<String>().joinToString(" ")
                        putExtra(Intent.EXTRA_TEXT, "$shareMessage\n\n$hashtagText")
                    }
                }
                val chooserIntent = Intent.createChooser(shareIntent, "Share Profile")
                context.startActivity(chooserIntent)
            } ?: run {
                // Fallback to basic text sharing
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, uiState.shareMessage)
                    putExtra(Intent.EXTRA_SUBJECT, "Check out this profile on Liftrix!")
                }
                val chooserIntent = Intent.createChooser(shareIntent, "Share Profile")
                context.startActivity(chooserIntent)
            }
            
            // Clear the share message after sharing
            viewModel.clearShareMessage()
        }
    }
    
    Timber.d("UserProfileScreen: Composing for user $userId with state - loading: ${uiState.isLoading}, profile: ${uiState.profile?.userId}")
    
    when {
        uiState.isLoading -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(LiftrixSpacing.screenPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
            ) {
                UserProfileLoadingState()
            }
        }
        uiState.error != null -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(LiftrixSpacing.screenPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
            ) {
                UserProfileErrorState(
                    error = uiState.error,
                    onRetry = { viewModel.retryLastOperation() }
                )
            }
        }
        uiState.profile != null -> {
            val profile = uiState.profile!!
            
            // Modern profile layout using LazyColumn internally - no parent scrolling needed
            ModernUserProfileContent(
                uiState = uiState,
                profile = profile,
                onFollowClick = { viewModel.toggleFollow() },
                onMessageClick = { viewModel.showMoreOptions() },
                onActivityClick = { workoutPost -> viewModel.handleActivityClick(workoutPost) },
                onSeeAllActivitiesClick = { viewModel.showAllActivities() },
                onSeeAllAchievementsClick = { viewModel.showAllAchievements() },
                modifier = modifier
            )
        }
        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(LiftrixSpacing.screenPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.cardSpacing)
            ) {
                UserProfileNotFoundState()
            }
        }
    }
    
    // Settings Menu Overlay - render when showMoreOptions is true
    if (uiState.showMoreOptions && uiState.profile != null) {
        ProfileSettingsMenu(
            profile = uiState.profile!!,
            isOwnProfile = uiState.isOwnProfile,
            onDismiss = { viewModel.hideMoreOptions() },
            onBlockUser = { viewModel.blockUser() },
            onReportUser = { 
                viewModel.hideMoreOptions()
                showReportBottomSheet = true 
            },
            onShareProfile = { viewModel.shareProfile(uiState.profile!!) }
        )
    }
    
    // Report Bottom Sheet
    if (showReportBottomSheet && uiState.profile != null) {
        ReportContentBottomSheet(
            contentType = ContentType.PROFILE,
            contentId = uiState.profile!!.userId,
            onDismiss = { showReportBottomSheet = false },
            onReport = { reason, description ->
                viewModel.reportUser(reason, description)
                showReportBottomSheet = false
            }
        )
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
        FollowStatus.MUTUAL_FOLLOW -> {
            SecondaryActionButton(
                text = "Mutual",
                onClick = onClick,
                leadingIcon = Icons.Default.Favorite,
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
    error: com.example.liftrix.domain.model.error.LiftrixError?,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Text(
                text = "Error Loading Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = error?.message ?: "Unknown error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Profile Not Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This user profile doesn't exist or is no longer available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The profile you're looking for might have been deleted or made private.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}



/**
 * Privacy message card for private profiles
 */
@Composable
private fun PrivacyMessageCard(
    profile: PublicUserProfile,
    followStatus: FollowStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🔒",
                fontSize = 32.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "This profile is private",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when (followStatus) {
                    FollowStatus.NONE -> "Follow ${profile.displayName ?: profile.username} to see their activity"
                    FollowStatus.PENDING_SENT -> "Your follow request is pending approval"
                    FollowStatus.BLOCKED -> "You have been blocked by this user"
                    else -> "You can see this profile's public information only"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Modern profile content using card-based layout with existing data
 */
@Composable
private fun ModernUserProfileContent(
    uiState: UserProfileUiState,
    profile: PublicUserProfile,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onActivityClick: (com.example.liftrix.domain.model.social.WorkoutPost) -> Unit,
    onSeeAllActivitiesClick: () -> Unit,
    onSeeAllAchievementsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Modern Profile Header with stats
        item {
            ModernProfileHeader(
                profile = profile,
                stats = ProfileStatsData(
                    workoutCount = profile.totalWorkouts,
                    followersCount = profile.followersCount,
                    followingCount = profile.followingCount
                ),
                followStatus = uiState.followStatus,
                isOwnProfile = uiState.isOwnProfile,
                onFollowClick = onFollowClick,
                onMessageClick = onMessageClick, // Trigger settings menu
                onStatsClick = { /* Handle stats click */ },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Weekly Progress Card (simplified using existing data)
        if (uiState.canViewDetails && profile.currentStreak > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "This Week's Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Current streak with green progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Streak",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${profile.currentStreak} days",
                                style = MaterialTheme.typography.titleMedium,
                                color = ProfileColors.ProgressGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = (profile.currentStreak.toFloat() / 7f).coerceAtMost(1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = ProfileColors.ProgressGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Achievement Progress Card (using existing achievements)
        if (uiState.canViewDetails && profile.achievements.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
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
                            TextButton(onClick = onSeeAllAchievementsClick) {
                                Text("See All")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Progress indicator
                        val achievementCount = profile.achievements.size
                        Text(
                            text = "$achievementCount/10 completed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = (achievementCount.toFloat() / 10f).coerceAtMost(1f),
                            modifier = Modifier.fillMaxWidth(),
                            color = ProfileColors.ProgressGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Recent Activity Section - Shows workout posts in feed style
        if (uiState.canViewDetails && profile.recentWorkoutPosts.isNotEmpty()) {
            // Section header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Workouts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onSeeAllActivitiesClick) {
                        Text("See All")
                    }
                }
            }
            
            // Display workout posts in feed style
            items(profile.recentWorkoutPosts.size) { index ->
                val post = profile.recentWorkoutPosts[index]
                WorkoutPostCard(
                    post = post,
                    isLiked = false, // TODO: Get actual like status
                    isSaved = false, // TODO: Get actual save status
                    onLikeClick = { /* TODO: Handle like */ },
                    onCommentClick = { /* TODO: Handle comment */ },
                    onShareClick = { /* TODO: Handle share */ },
                    onSaveClick = { /* TODO: Handle save */ },
                    onProfileClick = { /* Already on profile */ },
                    onWorkoutCopyClick = { /* TODO: Handle copy */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        } else if (uiState.canViewDetails && profile.recentWorkouts.isNotEmpty()) {
            // Convert regular workouts to feed-style display when no posts available
            // Section header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Workouts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onSeeAllActivitiesClick) {
                        Text("See All")
                    }
                }
            }
            
            // Display workouts as feed-style cards by converting them to WorkoutPost format
            items(profile.recentWorkouts.size) { index ->
                val workout = profile.recentWorkouts[index]
                // Create a simplified WorkoutPost from the regular workout data
                val syntheticPost = com.example.liftrix.domain.model.social.WorkoutPost(
                    id = workout.id,
                    userId = profile.userId,
                    workoutId = workout.id,
                    authorUsername = profile.username,
                    authorDisplayName = profile.displayName ?: profile.username,
                    authorProfilePhotoUrl = profile.profileImageUrl,
                    caption = workout.name,
                    exercisesCount = workout.exerciseCount,
                    workoutDuration = workout.duration.replace("min", "").trim().toIntOrNull(),
                    totalVolume = null,
                    prsCount = 0,
                    mediaItems = emptyList(),
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    saveCount = 0,
                    visibility = com.example.liftrix.domain.model.social.PostVisibility.PUBLIC,
                    createdAt = try {
                        // Parse date string and convert to timestamp
                        val dateTime = java.time.LocalDateTime.parse(workout.date + "T00:00:00")
                        dateTime.toEpochSecond(java.time.ZoneOffset.UTC) * 1000
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    },
                    updatedAt = System.currentTimeMillis()
                )
                
                WorkoutPostCard(
                    post = syntheticPost,
                    isLiked = false,
                    isSaved = false,
                    onLikeClick = { /* Not available for synthetic posts */ },
                    onCommentClick = { /* Not available for synthetic posts */ },
                    onShareClick = { /* Not available for synthetic posts */ },
                    onSaveClick = { /* Not available for synthetic posts */ },
                    onProfileClick = { /* Already on profile */ },
                    onWorkoutCopyClick = { /* TODO: Handle copy */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
        
        // Privacy message if profile is private
        if (!uiState.canViewDetails) {
            item {
                PrivacyMessageCard(
                    profile = profile, 
                    followStatus = uiState.followStatus
                )
            }
        }
    }
}


/**
 * Profile settings menu overlay with block/report/share options
 */
@Composable
private fun ProfileSettingsMenu(
    profile: PublicUserProfile,
    isOwnProfile: Boolean,
    onDismiss: () -> Unit,
    onBlockUser: () -> Unit,
    onReportUser: () -> Unit,
    onShareProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Background overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        // Settings menu card
        Card(
            modifier = modifier
                .align(Alignment.Center)
                .width(280.dp)
                .clickable { }, // Prevent click-through
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Text(
                    text = if (isOwnProfile) "Profile Options" else "User Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Share Profile option
                ProfileMenuOption(
                    icon = Icons.Default.Share,
                    text = "Share Profile",
                    onClick = {
                        onShareProfile()
                        onDismiss()
                    }
                )
                
                // Options for other users' profiles
                if (!isOwnProfile) {
                    ProfileMenuOption(
                        icon = Icons.Default.Block,
                        text = "Block User",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            onBlockUser()
                            onDismiss()
                        }
                    )
                    
                    ProfileMenuOption(
                        icon = Icons.Default.Report,
                        text = "Report User",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = {
                            onReportUser()
                            onDismiss()
                        }
                    )
                }
                
                // Cancel option
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                ProfileMenuOption(
                    icon = Icons.Default.Close,
                    text = "Cancel",
                    onClick = onDismiss
                )
            }
        }
    }
}

/**
 * Individual menu option in profile settings
 */
@Composable
private fun ProfileMenuOption(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}