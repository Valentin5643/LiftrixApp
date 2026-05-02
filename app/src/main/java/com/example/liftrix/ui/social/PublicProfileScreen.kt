package com.example.liftrix.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.social.ModernCard
import com.example.liftrix.ui.social.ModernStatCard
import com.example.liftrix.ui.social.ModernStatItem
import com.example.liftrix.ui.social.ModernConnectionActions
import com.example.liftrix.ui.social.formatDuration
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.PublicWorkoutStats
import com.example.liftrix.ui.components.buttons.LiftrixButton
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import com.example.liftrix.ui.feed.components.WorkoutPostCard
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Modern Public Profile Screen with premium fitness app experience
 * 
 * Features:
 * - 80px avatar with gradient background
 * - 2x2 grid stats layout with card system  
 * - Elevated dark card surfaces (#2D2D2D)
 * - Consistent 16px border radius
 * - 24px major section gaps
 * - Teal accent color (#00BCD4)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Use LiftrixColorsV2 design system
    val backgroundColor = LiftrixColorsV2.Dark.BackgroundPrimary
    val cardBackgroundColor = LiftrixColorsV2.Dark.BackgroundSecondary
    val primaryTeal = LiftrixColorsV2.Teal
    val textPrimary = LiftrixColorsV2.Dark.TextPrimary
    val textSecondary = LiftrixColorsV2.Dark.TextSecondary

    LaunchedEffect(userId) {
        viewModel.handleEvent(PublicProfileEvent.LoadProfile(userId))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top app bar
            ProfileTopBar(
                onNavigateBack = onNavigateBack,
                isLoading = uiState.isLoading,
                onBlockUser = { viewModel.handleEvent(PublicProfileEvent.BlockUser) },
                onReportProfile = { viewModel.handleEvent(PublicProfileEvent.ReportProfile) },
                isOwnProfile = uiState.profile?.userId == uiState.currentUserId,
                modifier = Modifier.fillMaxWidth()
            )

            // Profile content
            when {
                uiState.isLoading -> {
                    LoadingProfileState(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }

                uiState.error != null -> {
                    val error = uiState.error!!
                    ErrorProfileState(
                        error = error,
                        onRetry = { viewModel.handleEvent(PublicProfileEvent.RetryLoad) },
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }

                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    val workoutPosts = uiState.workoutPosts.collectAsLazyPagingItems()
                    
                    // Debug logging
                    LaunchedEffect(workoutPosts.itemCount) {
                        timber.log.Timber.d("PublicProfileScreen: Workout posts count = ${workoutPosts.itemCount}")
                    }
                    
                    ModernProfileContent(
                        profile = profile,
                        workoutPosts = workoutPosts,
                        likedPosts = uiState.likedPosts,
                        savedPosts = uiState.savedPosts,
                        onConnectClick = {
                            viewModel.handleEvent(PublicProfileEvent.ToggleConnection)
                        },
                        onLikeClick = { postId ->
                            viewModel.handleEvent(PublicProfileEvent.ToggleLike(postId))
                        },
                        onSaveClick = { postId ->
                            viewModel.handleEvent(PublicProfileEvent.ToggleSave(postId))
                        },
                        onPostClick = { postId ->
                            viewModel.handleEvent(PublicProfileEvent.OpenPostDetail(postId))
                        }
                    )
                }
            }

        }
        
        // Floating Action Button
        FloatingActionButton(
            onClick = { /* Handle action */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp),
            containerColor = primaryTeal,
            contentColor = backgroundColor,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Top app bar with navigation and QR code actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar(
        onNavigateBack: () -> Unit,
        isLoading: Boolean,
        modifier: Modifier = Modifier,
        onBlockUser: () -> Unit = {},
        onReportProfile: () -> Unit = {},
        isOwnProfile: Boolean = false
    ) {
        var showOptionsMenu by remember { mutableStateOf(false) }

        TopAppBar(
            title = { Text("Profile") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back"
                    )
                }
            },
            actions = {
                if (!isLoading) {
                    // More options menu (only for other users' profiles)
                    if (!isOwnProfile) {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Block User") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = "Block user")
                                },
                                onClick = {
                                    onBlockUser()
                                    showOptionsMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report Profile") },
                                leadingIcon = {
                                    Icon(Icons.Default.Flag, contentDescription = "Report profile")
                                },
                                onClick = {
                                    onReportProfile()
                                    showOptionsMenu = false
                                }
                            )
                        }
                    }
                }
            },
            modifier = modifier
        )
    }

/**
 * Modern profile content with premium design
 */
@Composable
fun ModernProfileContent(
        profile: PublicUserProfile,
        workoutPosts: LazyPagingItems<com.example.liftrix.domain.model.social.WorkoutPost>,
        likedPosts: Set<String>,
        savedPosts: Set<String>,
        onConnectClick: () -> Unit,
        onLikeClick: (String) -> Unit,
        onSaveClick: (String) -> Unit,
        onPostClick: (String) -> Unit
    ) {
        val cardBackgroundColor = LiftrixColorsV2.Dark.BackgroundSecondary
        val primaryTeal = LiftrixColorsV2.Teal
        val textPrimary = LiftrixColorsV2.Dark.TextPrimary
        val textSecondary = LiftrixColorsV2.Dark.TextSecondary

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Profile Header Section with Gradient Background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryTeal.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Avatar - 80px diameter with consistent fallback handling
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(primaryTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        // Handle both null AND empty string consistently
                        val hasValidImageUrl = !profile.profileImageUrl.isNullOrBlank()
                        timber.log.Timber.d("Profile avatar for ${profile.username}: imageUrl='${profile.profileImageUrl}', hasValid=$hasValidImageUrl")
                        
                        if (hasValidImageUrl) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profile.profileImageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile picture of ${profile.displayName ?: profile.username}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onError = { error ->
                                    val httpCode = if (error.result.throwable?.message?.contains("403") == true) "HTTP 403" else "Unknown"
                                    timber.log.Timber.e("Failed to load profile image: ${profile.profileImageUrl} for user: ${profile.username} | Error=$httpCode | ${error.result.throwable?.message}")
                                }
                            )
                        } else {
                            // Fallback to initials when image is null or empty
                            Text(
                                text = (profile.displayName ?: profile.username).take(2)
                                    .uppercase(),
                                color = LiftrixColorsV2.Dark.BackgroundPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Profile Name
                    Text(
                        text = profile.displayName ?: profile.username,
                        color = textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Level Badge (smaller)
                    profile.fitnessLevel?.let { level ->
                        Surface(
                            modifier = Modifier.padding(top = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = primaryTeal
                        ) {
                            Text(
                                text = level.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = LiftrixColorsV2.Dark.BackgroundPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Stats Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernStatItem(
                            icon = Icons.Default.FitnessCenter,
                            value = "${profile.publicWorkoutStats?.totalWorkouts ?: 0}",
                            label = "Workouts",
                            inline = true
                        )

                        Spacer(modifier = Modifier.width(32.dp))

                        ModernStatItem(
                            icon = Icons.Default.CalendarToday,
                            value = profile.memberSince?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "Unknown",
                            label = "Member Since",
                            inline = true
                        )
                    }
                }
            }

            // Action Buttons Row
            ModernConnectionActions(
                connectionStatus = profile.connectionStatus,
                onConnectClick = onConnectClick
            )

            // About Section Card
            val bio = profile.bio
            if (!bio.isNullOrBlank()) {
                ModernCard(title = "About") {
                    Text(
                        text = bio,
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // Fitness Overview - 2x2 Grid
            val workoutStats = profile.publicWorkoutStats
            if (workoutStats != null) {
                ModernCard(title = "Fitness Overview") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModernStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Timer,
                                value = formatDuration(workoutStats.totalWorkoutTime.toInt()),
                                label = "TOTAL TIME",
                                iconColor = primaryTeal
                            )
                            ModernStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.AvTimer,
                                value = formatDuration(workoutStats.averageWorkoutTime.toInt()),
                                label = "AVG SESSION",
                                iconColor = primaryTeal
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModernStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.TrendingUp,
                                value = "${workoutStats.currentStreak} days",
                                label = "CURRENT STREAK",
                                iconColor = primaryTeal
                            )
                            ModernStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.EmojiEvents,
                                value = "${workoutStats.longestStreak} days",
                                label = "BEST STREAK",
                                iconColor = primaryTeal
                            )
                        }
                    }
                }
            }

            // Recent Activity Section with workout posts
            // Always show the section to avoid UI jumping
            ModernCard(title = "Recent Activity") {
                when {
                    // Loading state
                    workoutPosts.loadState.refresh is LoadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = primaryTeal,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // Has posts
                    workoutPosts.itemCount > 0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Display up to 3 posts
                            for (index in 0 until minOf(workoutPosts.itemCount, 3)) {
                                workoutPosts[index]?.let { post ->
                                    WorkoutPostCard(
                                        post = post,
                                        isLiked = likedPosts.contains(post.id),
                                        isSaved = savedPosts.contains(post.id),
                                        onLikeClick = { onLikeClick(post.id) },
                                        onCommentClick = { onPostClick(post.id) },
                                        onShareClick = { /* Handle share */ },
                                        onSaveClick = { onSaveClick(post.id) },
                                        onProfileClick = { /* Already on profile */ },
                                        onWorkoutCopyClick = { /* Handle copy workout */ },
                                        modifier = Modifier.fillMaxWidth(),
                                        isOwnPost = false
                                    )
                                }
                            }
                            
                            // Show "View All" button if there are more than 3 posts
                            if (workoutPosts.itemCount > 3) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { /* Navigate to all posts */ }
                                        .padding(vertical = 12.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = primaryTeal.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "View All ${workoutPosts.itemCount} Workouts",
                                        modifier = Modifier.padding(16.dp),
                                        color = primaryTeal,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    
                    // No posts
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = "No workouts",
                                    modifier = Modifier.size(48.dp),
                                    tint = textSecondary.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "No workouts yet",
                                    color = textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Add bottom spacing for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

/**
 * Main profile content with user information and actions (Original - kept for compatibility)
 */
@Composable
fun ProfileContent(
        profile: PublicUserProfile,
        onConnectClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHeader(
                    profile = profile,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ConnectionActions(
                    connectionStatus = profile.connectionStatus,
                    onConnectClick = onConnectClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val bio = profile.bio
            if (!bio.isNullOrBlank()) {
                item {
                    ProfileBioSection(
                        bio = bio,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                FitnessInfoSection(
                    profile = profile,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Equipment section handled by enhanced profile model
            /*if (!profile.availableEquipment.isNullOrEmpty()) {
            item {
                EquipmentSection(
                    equipment = profile.availableEquipment,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }*/

            // Fitness goals section - Added fitness goals to profile model
            val fitnessGoals = profile.fitnessGoals
            if (!fitnessGoals.isNullOrEmpty()) {
                item {
                    GoalsSection(
                        goals = fitnessGoals,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val workoutStats = profile.publicWorkoutStats
            if (workoutStats != null) {
                item {
                    WorkoutStatsSection(
                        stats = workoutStats,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

/**
 * Profile header with avatar, name, and basic stats
 */
@Composable
fun ProfileHeader(
        profile: PublicUserProfile,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile image
                ProfileAvatar(
                    imageUrl = profile.profileImageUrl,
                    displayName = profile.displayName ?: profile.username,
                    modifier = Modifier.size(100.dp)
                )

                // User name and level
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.displayName ?: profile.username,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    profile.fitnessLevel?.let { level ->
                        Badge(
                            containerColor = getFitnessLevelColor(level),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = level.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        value = "${profile.publicWorkoutStats?.totalWorkouts ?: 0}",
                        label = "Workouts",
                        icon = Icons.Default.FitnessCenter
                    )

                    StatItem(
                        value = profile.memberSince?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "Unknown",
                        label = "Member Since",
                        icon = Icons.Default.CalendarToday
                    )
                }
            }
        }
    }

/**
 * Profile avatar with fallback to initials
 */
@Composable
fun ProfileAvatar(
        imageUrl: String?,
        displayName: String,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .semantics {
                    contentDescription = "$displayName profile picture"
                },
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "$displayName profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = displayName
                        .split(' ')
                        .take(2)
                        .mapNotNull { it.firstOrNull() }
                        .joinToString("")
                        .uppercase()

                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

/**
 * Connection action buttons
 */
@Composable
fun ConnectionActions(
        connectionStatus: ConnectionStatus,
        onConnectClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (connectionStatus) {
                ConnectionStatus.NONE -> {
                    LiftrixButton(
                        onClick = onConnectClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Connect",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect")
                    }

                    // Message functionality removed
                }

                ConnectionStatus.PENDING_SENT -> {
                    OutlinedButton(
                        onClick = onConnectClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Pending",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pending")
                    }

                    // Message functionality removed
                }

                ConnectionStatus.PENDING_RECEIVED -> {
                    LiftrixButton(
                        onClick = onConnectClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Accept",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Accept")
                    }

                    OutlinedButton(
                        onClick = { /* Handle decline */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Decline",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decline")
                    }
                }

                ConnectionStatus.CONNECTED -> {
                    // Connected users - message functionality removed

                    OutlinedButton(
                        onClick = { /* Handle view workouts */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Workouts",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Workouts")
                    }
                }

                ConnectionStatus.GYM_BUDDY -> {
                    // Gym buddy relationship
                    OutlinedButton(
                        onClick = { /* Handle gym buddy features */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Gym buddy",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gym Buddy")
                    }
                }

                ConnectionStatus.MUTUAL_FOLLOW -> {
                    // Mutual followers - message functionality removed

                    OutlinedButton(
                        onClick = { /* Handle view workouts */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = "Workouts",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Workouts")
                    }
                }

                ConnectionStatus.CONNECTED -> {
                    // Gym buddies - message functionality removed

                    OutlinedButton(
                        onClick = { /* Handle gym buddy features */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Gym buddy",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gym Buddy")
                    }
                }

                ConnectionStatus.BLOCKED -> {
                    Text(
                        text = "This user has been blocked",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                ConnectionStatus.SELF -> {
                    // No action buttons when viewing own profile
                    // This should be handled at a higher level but adding for compilation
                }
            }
        }
    }

/**
 * Bio section
 */
@Composable
fun ProfileBioSection(
        bio: String,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

/**
 * Fitness information section
 */
@Composable
fun FitnessInfoSection(
        profile: PublicUserProfile,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Fitness Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoItem(
                        label = "Level",
                        value = profile.fitnessLevel?.name?.lowercase()
                            ?.replaceFirstChar { it.uppercase() } ?: "Not specified",
                        icon = Icons.Default.TrendingUp
                    )

                    InfoItem(
                        label = "Workouts",
                        value = "${profile.publicWorkoutStats?.totalWorkouts ?: 0}",
                        icon = Icons.Default.FitnessCenter
                    )
                }
            }
        }
    }

/**
 * Equipment section
 */
@Composable
fun EquipmentSection(
        equipment: List<Equipment>,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Equipment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(equipment) { item ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = item.name.lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = "Equipment: ${item.name}",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

/**
 * Goals section
 */
@Composable
fun GoalsSection(
        goals: List<String>,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Fitness Goals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(goals) { goal ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = goal,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Goal: $goal",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

/**
 * Achievements section
 */
@Composable
fun AchievementsSection(
        achievements: List<String>,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    achievements.take(5).forEach { achievement ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Achievement",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )

                            Text(
                                text = achievement,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (achievements.size > 5) {
                        Text(
                            text = "+${achievements.size - 5} more achievements",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 28.dp)
                        )
                    }
                }
            }
        }
    }

/**
 * Workout stats section
 */
@Composable
fun WorkoutStatsSection(
        stats: PublicWorkoutStats,
        modifier: Modifier = Modifier
    ) {
        LiftrixCard(
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Workout Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Primary stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatsItem(
                        label = "Total Time",
                        value = "${stats.totalWorkoutTime / 60}h ${stats.totalWorkoutTime % 60}m",
                        icon = Icons.Default.Timer
                    )

                    StatsItem(
                        label = "Avg Time",
                        value = "${stats.averageWorkoutTime}min",
                        icon = Icons.Default.Schedule
                    )
                }

                // Streak stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatsItem(
                        label = "Current Streak",
                        value = "${stats.currentStreak} days",
                        icon = Icons.Default.Whatshot
                    )

                    StatsItem(
                        label = "Best Streak",
                        value = "${stats.longestStreak} days",
                        icon = Icons.Default.EmojiEvents
                    )
                }

                // Favorite exercises
                if (stats.favoriteExercises.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Favorite Exercises",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(stats.favoriteExercises.take(5)) { exercise ->
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            text = exercise,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * Stats item component
 */
@Composable
fun StatsItem(
        label: String,
        value: String,
        icon: ImageVector,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }

/**
 * Helper composables
 */
@Composable
fun StatItem(
        value: String,
        label: String,
        icon: ImageVector,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

@Composable
fun InfoItem(
        label: String,
        value: String,
        icon: ImageVector,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }

/**
 * Loading state
 */
@Composable
fun LoadingProfileState(
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Loading profile...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

/**
 * Error state
 */
@Composable
fun ErrorProfileState(
        error: com.example.liftrix.domain.model.error.LiftrixError,
        onRetry: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Failed to load profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryActionButton(
                text = "Try Again",
                onClick = onRetry
            )
        }
    }

/**
 * Gets color for fitness level badge
 */
@Composable
fun getFitnessLevelColor(level: FitnessLevel): androidx.compose.ui.graphics.Color {
        return when (level) {
            FitnessLevel.BEGINNER -> MaterialTheme.colorScheme.secondary
            FitnessLevel.INTERMEDIATE -> MaterialTheme.colorScheme.primary
            FitnessLevel.ADVANCED -> MaterialTheme.colorScheme.tertiary
            FitnessLevel.EXPERT -> MaterialTheme.colorScheme.error
        }
    }

@Preview(showBackground = true)
@Composable
private fun PublicProfileScreenPreview() {
        LiftrixTheme {
            PublicProfileScreen(
                userId = "1",
                onNavigateBack = { }
            )
        }
    }
