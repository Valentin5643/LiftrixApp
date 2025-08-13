package com.example.liftrix.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.FitnessLevel
import com.example.liftrix.domain.model.social.PublicUserProfile
import com.example.liftrix.domain.model.social.PublicWorkoutStats
import com.example.liftrix.ui.components.buttons.LiftrixButton
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.theme.LiftrixTheme
import com.example.liftrix.ui.workout.components.PrimaryActionButton
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Screen for displaying public user profiles with privacy-aware information
 * 
 * Shows user profile information based on their privacy settings, connection status,
 * and displays appropriate actions for connecting or interacting with the user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onNavigateToQRCode: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.handleEvent(PublicProfileEvent.LoadProfile(userId))
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top app bar
        ProfileTopBar(
            onNavigateBack = onNavigateBack,
            onQRCodeClick = { onNavigateToQRCode(userId) },
            isLoading = uiState.isLoading,
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
                ProfileContent(
                    profile = profile,
                    onConnectClick = { 
                        viewModel.handleEvent(PublicProfileEvent.ToggleConnection)
                    },
                    onMessageClick = {
                        // Handle message action
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Top app bar with navigation and QR code actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    onNavigateBack: () -> Unit,
    onQRCodeClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
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
                IconButton(onClick = onQRCodeClick) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Show QR code"
                    )
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Main profile content with user information and actions
 */
@Composable
private fun ProfileContent(
    profile: PublicUserProfile,
    onConnectClick: () -> Unit,
    onMessageClick: () -> Unit,
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
                onMessageClick = onMessageClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (!profile.bio.isNullOrBlank()) {
            item {
                ProfileBioSection(
                    bio = profile.bio,
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
        
        // TODO: Add equipment to profile model when available
        /*if (!profile.availableEquipment.isNullOrEmpty()) {
            item {
                EquipmentSection(
                    equipment = profile.availableEquipment,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }*/
        
        // TODO: Add fitness goals to profile model when available
        /*if (!profile.fitnessGoals.isNullOrEmpty()) {
            item {
                GoalsSection(
                    goals = profile.fitnessGoals,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }*/
        
        if (profile.achievements.isNotEmpty()) {
            item {
                AchievementsSection(
                    achievements = profile.achievements.map { it.title },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        if (profile.publicWorkoutStats != null) {
            item {
                WorkoutStatsSection(
                    stats = profile.publicWorkoutStats,
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
private fun ProfileHeader(
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
                    value = profile.memberSince.format(DateTimeFormatter.ofPattern("MMM yyyy")),
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
private fun ProfileAvatar(
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
                contentDescription = null,
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
private fun ConnectionActions(
    connectionStatus: ConnectionStatus,
    onConnectClick: () -> Unit,
    onMessageClick: () -> Unit,
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
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect")
                }
                
                OutlinedButton(
                    onClick = onMessageClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
            }
            
            ConnectionStatus.PENDING_SENT -> {
                OutlinedButton(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pending")
                }
                
                OutlinedButton(
                    onClick = onMessageClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
            }
            
            ConnectionStatus.PENDING_RECEIVED -> {
                LiftrixButton(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
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
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decline")
                }
            }
            
            ConnectionStatus.CONNECTED -> {
                LiftrixButton(
                    onClick = onMessageClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
                
                OutlinedButton(
                    onClick = { /* Handle view workouts */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Workouts")
                }
            }
            
            ConnectionStatus.MUTUAL_FOLLOW -> {
                LiftrixButton(
                    onClick = onMessageClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
                
                OutlinedButton(
                    onClick = { /* Handle view workouts */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Workouts")
                }
            }
            
            ConnectionStatus.GYM_BUDDY -> {
                LiftrixButton(
                    onClick = onMessageClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }
                
                OutlinedButton(
                    onClick = { /* Handle gym buddy features */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
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
        }
    }
}

/**
 * Bio section
 */
@Composable
private fun ProfileBioSection(
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
private fun FitnessInfoSection(
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
                    value = profile.fitnessLevel?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Not specified",
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
private fun EquipmentSection(
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
                                text = item.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
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
private fun GoalsSection(
    goals: List<FitnessGoal>,
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
                                text = goal.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
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
private fun AchievementsSection(
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
                            contentDescription = null,
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
private fun WorkoutStatsSection(
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
private fun StatsItem(
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
            contentDescription = null,
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
private fun StatItem(
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
            contentDescription = null,
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
private fun InfoItem(
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
            contentDescription = null,
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
private fun LoadingProfileState(
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
private fun ErrorProfileState(
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
            contentDescription = null,
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
private fun getFitnessLevelColor(level: FitnessLevel): androidx.compose.ui.graphics.Color {
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
            onNavigateBack = { },
            onNavigateToQRCode = { }
        )
    }
}